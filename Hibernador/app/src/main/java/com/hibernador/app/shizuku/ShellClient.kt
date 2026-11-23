package com.hibernador.app.shizuku

import android.os.IBinder
import android.os.Parcel

/** Lado do app que conversa com o [ShellService] remoto. */
class ShellClient(private val binder: IBinder) {

    data class Result(val exitCode: Int, val output: String) {
        val ok: Boolean get() = exitCode == 0
    }

    val isAlive: Boolean get() = binder.pingBinder()

    /** Executa um comando de shell (bloqueante — chame fora da main thread). */
    fun exec(command: String): Result {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(ShellService.DESCRIPTOR)
            data.writeString(command)
            binder.transact(ShellService.TRANSACTION_EXEC, data, reply, 0)
            reply.readException()
            val raw = reply.readString().orEmpty()
            val nl = raw.indexOf('\n')
            if (nl < 0) {
                Result(raw.toIntOrNull() ?: -1, "")
            } else {
                Result(raw.substring(0, nl).toIntOrNull() ?: -1, raw.substring(nl + 1))
            }
        } finally {
            data.recycle()
            reply.recycle()
        }
    }
}
