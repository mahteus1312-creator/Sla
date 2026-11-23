package com.hibernador.app.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.IBinder
import com.hibernador.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import rikka.shizuku.Shizuku
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Estado do Shizuku + acesso ao shell privilegiado. */
object ShizukuManager {

    enum class Status { NOT_INSTALLED, NOT_RUNNING, UNSUPPORTED, NO_PERMISSION, READY }

    const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    private const val REQUEST_CODE = 4242

    private lateinit var appContext: Context
    private val _status = MutableStateFlow(Status.NOT_RUNNING)
    val status: StateFlow<Status> = _status.asStateFlow()

    private val mutex = Mutex()
    @Volatile private var client: ShellClient? = null
    private var connection: ServiceConnection? = null

    private val serviceArgs by lazy {
        Shizuku.UserServiceArgs(ComponentName(appContext.packageName, ShellService::class.java.name))
            .daemon(false)
            .processNameSuffix("hibernador_shell")
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE)
    }

    fun init(context: Context) {
        appContext = context.applicationContext
        Shizuku.addBinderReceivedListenerSticky { refresh() }
        Shizuku.addBinderDeadListener {
            client = null
            connection = null
            refresh()
        }
        Shizuku.addRequestPermissionResultListener { _, _ -> refresh() }
        refresh()
    }

    fun refresh() {
        _status.value = try {
            when {
                !Shizuku.pingBinder() -> if (isInstalled()) Status.NOT_RUNNING else Status.NOT_INSTALLED
                Shizuku.isPreV11() -> Status.UNSUPPORTED
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> Status.READY
                else -> Status.NO_PERMISSION
            }
        } catch (t: Throwable) {
            Status.NOT_RUNNING
        }
    }

    fun isInstalled(): Boolean = try {
        appContext.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    /** true quando o usuário marcou "não perguntar novamente". */
    fun isPermissionPermanentlyDenied(): Boolean = try {
        Shizuku.pingBinder() && Shizuku.shouldShowRequestPermissionRationale()
    } catch (t: Throwable) {
        false
    }

    fun requestPermission() {
        try {
            if (Shizuku.pingBinder()) Shizuku.requestPermission(REQUEST_CODE)
        } catch (_: Throwable) {
        }
        refresh()
    }

    fun openShizukuApp(context: Context) {
        val launch = context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)
        if (launch != null) {
            context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } else {
            openStore(context)
        }
    }

    fun openStore(context: Context) {
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$SHIZUKU_PACKAGE"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(market)
        } catch (_: Exception) {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    /** Retorna um shell privilegiado, iniciando o UserService se necessário. */
    suspend fun shell(): ShellClient = mutex.withLock {
        client?.takeIf { it.isAlive }?.let { return it }
        refresh()
        check(_status.value == Status.READY) { "Shizuku não está ativo ou sem permissão." }
        withTimeout(15_000) {
            withContext(Dispatchers.Main) {
                suspendCancellableCoroutine { cont ->
                    val conn = object : ServiceConnection {
                        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                            if (binder != null && binder.pingBinder()) {
                                val c = ShellClient(binder)
                                client = c
                                if (cont.isActive) cont.resume(c)
                            } else if (cont.isActive) {
                                cont.resumeWithException(IllegalStateException("Serviço do Shizuku inválido."))
                            }
                        }

                        override fun onServiceDisconnected(name: ComponentName?) {
                            client = null
                        }
                    }
                    connection = conn
                    try {
                        Shizuku.bindUserService(serviceArgs, conn)
                    } catch (t: Throwable) {
                        if (cont.isActive) cont.resumeWithException(t)
                    }
                }
            }
        }
    }
}
