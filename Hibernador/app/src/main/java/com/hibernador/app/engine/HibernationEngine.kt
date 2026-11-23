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
    /** Resumo da detecção (exibido quando nada é encontrado). */
    val diagnostics: String? = null,
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

            val scan = findTargets(context, shell, userId)
            val targets = scan.targets
            coroutineContext.ensureActive()

            val memStart = MemoryReader.read(context).available
            set {
                it.copy(
                    phase = Phase.RUNNING,
                    total = targets.size,
                    protectedCount = scan.protectedCount,
                    diagnostics = scan.diagnostics,
                )
            }

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

    private class Scan(
        val targets: List<Pair<TargetApp, ApplicationInfo>>,
        val protectedCount: Int,
        val diagnostics: String,
    )

    /**
     * Apps em execução, menos os protegidos. Usa duas fontes:
     * `dumpsys activity lru` (lista de processos do ActivityManager) e `ps -A`.
     */
    private suspend fun findTargets(context: Context, shell: ShellClient, userId: Int): Scan {
        val pm = context.packageManager
        val classifier = AppClassifier(context, RulesRepository.load())
        @Suppress("DEPRECATION")
        val installed = pm.getInstalledApplications(0).associateBy { it.packageName }
        val running = mutableSetOf<String>()

        fun addUid(uid: Int) {
            if (uid / PER_USER_RANGE != userId) return
            runCatching { pm.getPackagesForUid(uid) }.getOrNull()?.let { running.addAll(it) }
        }

        fun addName(processName: String) {
            val base = processName.substringBefore(':')
            if (base in installed) running += base
        }

        // 1) ActivityManager: linhas como "#12: cch+ 5 CEM ---- 1234:com.app/u0a150".
        val amRegex = Regex("""\b\d+:([A-Za-z][\w.]*(?::[\w.\-]+)?)/(?:u(\d+)a(\d+)|(\d+))""")
        var amCount = 0
        for (cmd in listOf("dumpsys activity lru", "dumpsys activity processes")) {
            val r = exec(shell, cmd)
            if (!r.ok) continue
            val before = running.size
            for (m in amRegex.findAll(r.output)) {
                addName(m.groupValues[1])
                val uid = when {
                    m.groupValues[2].isNotEmpty() ->
                        m.groupValues[2].toInt() * PER_USER_RANGE + FIRST_APP_UID + m.groupValues[3].toInt()
                    else -> m.groupValues[4].toIntOrNull()
                }
                if (uid != null) addUid(uid)
            }
            amCount = running.size - before
            if (amCount > 0) break
        }

        // 2) ps: usuário "u0_a150" (ou uid numérico) + nome do processo.
        val sizeBeforePs = running.size
        var ps = exec(shell, "ps -A -o USER,NAME")
        if (!ps.ok || ps.output.lines().size < 3) ps = exec(shell, "ps -A")
        val userRegex = Regex("^u(\\d+)_a(\\d+)$")
        var psLines = 0
        if (ps.ok) {
            for (line in ps.output.lineSequence()) {
                val parts = line.trim().split(Regex("\\s+"))
                if (parts.size < 2) continue
                psLines++
                addName(parts.last())
                val user = parts.first()
                val m = userRegex.matchEntire(user)
                when {
                    m != null -> addUid(
                        m.groupValues[1].toInt() * PER_USER_RANGE + FIRST_APP_UID + m.groupValues[2].toInt()
                    )
                    user.all { it.isDigit() } -> user.toIntOrNull()?.let { addUid(it) }
                }
            }
        }
        val psCount = running.size - sizeBeforePs

        if (running.isEmpty() && !ps.ok) {
            error("Não foi possível listar os processos: ${ps.output.take(200)}")
        }

        val reasons = mutableMapOf<String, Int>()
        val targets = running.mapNotNull { pkg ->
            val info = installed[pkg] ?: return@mapNotNull null
            if (!info.enabled) return@mapNotNull null
            val label = runCatching { pm.getApplicationLabel(info).toString() }.getOrDefault(pkg)
            val reason = classifier.classify(info, label)
            if (reason != null) {
                reasons[reason.label] = (reasons[reason.label] ?: 0) + 1
                null
            } else {
                TargetApp(pkg, label) to info
            }
        }.sortedBy { it.first.label.lowercase() }

        val diagnostics = buildString {
            append("Em execução: ${running.size} (sistema: $amCount, ps: $psCount de $psLines processos)")
            if (reasons.isNotEmpty()) {
                append("\nProtegidos: ")
                append(reasons.entries.sortedByDescending { it.value }.joinToString(", ") { "${it.key} ${it.value}" })
            }
            append("\nShizuku uid: ${runCatching { rikka.shizuku.Shizuku.getUid() }.getOrDefault(-1)}")
        }
        return Scan(targets, reasons.values.sum(), diagnostics)
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
