package com.hibernador.app.engine

import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Process
import com.hibernador.app.data.AppClassifier
import com.hibernador.app.data.LastResultStore
import com.hibernador.app.data.MemoryReader
import com.hibernador.app.data.RulesRepository
import com.hibernador.app.shizuku.ShellClient
import com.hibernador.app.shizuku.ShizukuManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlin.coroutines.coroutineContext

enum class Phase { IDLE, SCANNING, RUNNING, DONE, CANCELLED, ERROR }
enum class Step { STOPPING, CLEARING_CACHE }

data class TargetApp(val packageName: String, val label: String)

data class AppResult(
    val app: TargetApp,
    val stopped: Boolean,
    /** null = não suportado neste Android. */
    val cacheCleared: Boolean?,
    val ramFreed: Long,
    val cacheFreed: Long,
)

data class HibernationState(
    val phase: Phase = Phase.IDLE,
    val total: Int = 0,
    val done: Int = 0,
    val stepFraction: Float = 0f,
    val current: TargetApp? = null,
    val step: Step = Step.STOPPING,
    val completed: List<AppResult> = emptyList(),
    val protectedCount: Int = 0,
    val ramFreed: Long = 0,
    val cacheFreed: Long = 0,
    val cacheSupported: Boolean = true,
    val error: String? = null,
) {
    val progress: Float
        get() = when {
            phase == Phase.DONE -> 1f
            total == 0 -> 0f
            else -> ((done + stepFraction) / total).coerceIn(0f, 1f)
        }
    val isActive: Boolean get() = phase == Phase.SCANNING || phase == Phase.RUNNING
}

/**
 * Executa a hibernação numa corotina com escopo do app e publica o progresso
 * em um [StateFlow]. Sobrevive à navegação entre telas.
 */
object HibernationEngine {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    /** Identifica a execução atual; execuções antigas (canceladas) não alteram o estado. */
    @Volatile private var generation = 0

    private val _state = MutableStateFlow(HibernationState())
    val state: StateFlow<HibernationState> = _state.asStateFlow()

    /** Limpeza de cache por app só é segura no Android 14+ (antes, a flag
     *  --cache-only era ignorada e "pm clear" apagaria TODOS os dados). */
    private val CACHE_CLEAR_SUPPORTED = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE

    fun start(context: Context) {
        if (job?.isActive == true) return
        val app = context.applicationContext
        val gen = ++generation
        _state.value = HibernationState(phase = Phase.SCANNING, cacheSupported = CACHE_CLEAR_SUPPORTED)
        job = scope.launch { run(app, gen) }
    }

    /** Interrompe imediatamente (o comando em andamento termina e nada mais roda). */
    fun cancel() {
        generation++
        job?.cancel()
        job = null
        _state.update { if (it.isActive) it.copy(phase = Phase.CANCELLED, current = null) else it }
    }

    fun reset() {
        if (job?.isActive != true) _state.value = HibernationState()
    }

    private suspend fun run(context: Context, gen: Int) {
        fun set(transform: (HibernationState) -> HibernationState) {
            _state.update { if (gen == generation) transform(it) else it }
        }
        try {
            val shell = ShizukuManager.shell()
            val userId = Process.myUid() / PER_USER_RANGE

            // Permite ler o tamanho do cache de outros apps (StorageStatsManager).
            exec(shell, "appops set ${context.packageName} GET_USAGE_STATS allow")

            val (targets, protectedCount) = findTargets(context, shell, userId)
            coroutineContext.ensureActive()

            val memStart = MemoryReader.read(context).available
            set { it.copy(phase = Phase.RUNNING, total = targets.size, protectedCount = protectedCount) }

            for ((index, target) in targets.withIndex()) {
                coroutineContext.ensureActive()
                val (app, info) = target
                set { it.copy(current = app, step = Step.STOPPING, done = index, stepFraction = 0f) }

                val before = MemoryReader.read(context).available
                val stopped = exec(shell, "am force-stop --user $userId ${app.packageName}").ok
                exec(shell, "am set-inactive --user $userId ${app.packageName} true")
                delay(STEP_DELAY_MS)
                val ramFreed = (MemoryReader.read(context).available - before).coerceAtLeast(0)

                coroutineContext.ensureActive()
                set { it.copy(step = Step.CLEARING_CACHE, stepFraction = 0.5f) }

                var cacheCleared: Boolean? = null
                var cacheFreed = 0L
                if (CACHE_CLEAR_SUPPORTED) {
                    val cacheBefore = cacheBytes(context, info)
                    cacheCleared = exec(shell, "pm clear --user $userId --cache-only ${app.packageName}").ok
                    val cacheAfter = cacheBytes(context, info)
                    if (cacheBefore != null && cacheAfter != null) {
                        cacheFreed = (cacheBefore - cacheAfter).coerceAtLeast(0)
                    }
                }
                delay(STEP_DELAY_MS)

                val result = AppResult(app, stopped, cacheCleared, ramFreed, cacheFreed)
                set {
                    it.copy(
                        done = index + 1,
                        stepFraction = 0f,
                        completed = it.completed + result,
                        cacheFreed = it.cacheFreed + cacheFreed,
                        ramFreed = (MemoryReader.read(context).available - memStart).coerceAtLeast(0),
                    )
                }
            }

            // Dá um tempo para o sistema contabilizar a memória devolvida.
            delay(SETTLE_DELAY_MS)
            val totalFreed = (MemoryReader.read(context).available - memStart).coerceAtLeast(0)
            if (gen != generation) return
            LastResultStore.save(totalFreed)
            set { it.copy(phase = Phase.DONE, current = null, ramFreed = totalFreed) }
        } catch (e: CancellationException) {
            set { it.copy(phase = Phase.CANCELLED, current = null) }
            throw e
        } catch (t: Throwable) {
            set { it.copy(phase = Phase.ERROR, current = null, error = t.message ?: t.javaClass.simpleName) }
        }
    }

    /** Apps em execução (via `ps`), menos os protegidos. */
    private suspend fun findTargets(
        context: Context,
        shell: ShellClient,
        userId: Int,
    ): Pair<List<Pair<TargetApp, ApplicationInfo>>, Int> {
        val pm = context.packageManager
        val classifier = AppClassifier(context, RulesRepository.load())

        var ps = exec(shell, "ps -A -o USER,NAME")
        if (!ps.ok || ps.output.isBlank()) ps = exec(shell, "ps -A")
        check(ps.ok) { "Não foi possível listar os processos: ${ps.output.take(200)}" }

        val installed = pm.getInstalledApplications(0).associateBy { it.packageName }
        val running = mutableSetOf<String>()
        val userRegex = Regex("^u(\\d+)_a(\\d+)$")
        for (line in ps.output.lineSequence()) {
            val parts = line.trim().split(Regex("\\s+"))
            if (parts.size < 2) continue
            val user = parts.first()
            val name = parts.last().substringBefore(':')
            if (name in installed) running += name
            userRegex.matchEntire(user)?.let { m ->
                if (m.groupValues[1].toInt() == userId) {
                    val uid = userId * PER_USER_RANGE + FIRST_APP_UID + m.groupValues[2].toInt()
                    pm.getPackagesForUid(uid)?.let { running.addAll(it) }
                }
            }
        }

        var protectedApps = 0
        val targets = running.mapNotNull { pkg ->
            val info = installed[pkg] ?: return@mapNotNull null
            if (!info.enabled) return@mapNotNull null
            val label = runCatching { pm.getApplicationLabel(info).toString() }.getOrDefault(pkg)
            if (classifier.classify(info, label) != null) {
                protectedApps++
                null
            } else {
                TargetApp(pkg, label) to info
            }
        }.sortedBy { it.first.label.lowercase() }
        return targets to protectedApps
    }

    private fun cacheBytes(context: Context, info: ApplicationInfo): Long? = try {
        val ssm = context.getSystemService(StorageStatsManager::class.java) ?: return null
        ssm.queryStatsForPackage(info.storageUuid, info.packageName, Process.myUserHandle()).cacheBytes
    } catch (t: Throwable) {
        null
    }

    private suspend fun exec(shell: ShellClient, cmd: String): ShellClient.Result =
        runInterruptible(Dispatchers.IO) { shell.exec(cmd) }

    private const val PER_USER_RANGE = 100_000
    private const val FIRST_APP_UID = 10_000
    private const val STEP_DELAY_MS = 150L
    private const val SETTLE_DELAY_MS = 800L
}
