package com.hibernador.app.shizuku

import android.content.Context
import android.os.Binder
import android.os.Parcel
import kotlin.system.exitProcess

/**
 * "UserService" do Shizuku: roda em um processo separado com privilégios de
 * shell (uid 2000) ou root, iniciado pelo próprio Shizuku. Recebe comandos via
 * Binder e devolve "<código de saída>\n<saída>".
 *
 * O Binder é implementado à mão (sem AIDL) para manter o projeto simples.
 */
class ShellService() : Binder() {

    @Suppress("UNUSED_PARAMETER")
    constructor(context: Context) : this()

    init {
        attachInterface(null, DESCRIPTOR)
    }

    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        return when (code) {
            TRANSACTION_DESTROY -> {
                exitProcess(0)
            }
            TRANSACTION_EXEC -> {
                data.enforceInterface(DESCRIPTOR)
                val command = data.readString().orEmpty()
                val result = run(command)
                reply?.writeNoException()
                reply?.writeString(result)
                true
            }
            else -> super.onTransact(code, data, reply, flags)
        }
    }

    private fun run(command: String): String = try {
        val process = ProcessBuilder("/system/bin/sh", "-c", command)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        val exit = process.waitFor()
        "$exit\n$output"
    } catch (t: Throwable) {
        "-1\n${t.javaClass.simpleName}: ${t.message}"
    }

    companion object {
        const val DESCRIPTOR = "com.hibernador.app.IShellService"
        const val TRANSACTION_EXEC = FIRST_CALL_TRANSACTION

        /** Código reservado pelo Shizuku para encerrar o UserService. */
        const val TRANSACTION_DESTROY = 16777114
    }
}
