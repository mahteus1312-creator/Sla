package com.hibernador.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Lista editável de proteção (bancos, segurança, extras). */
data class ProtectionRules(
    val protectGames: Boolean,
    val bankPackages: List<String>,
    val bankKeywords: List<String>,
    val securityPackages: List<String>,
    val securityKeywords: List<String>,
    val extraProtectedPackages: List<String>,
) {
    companion object {
        fun parse(json: String): ProtectionRules {
            val o = JSONObject(json)
            return ProtectionRules(
                protectGames = o.optBoolean("protectGames", true),
                bankPackages = o.strings("bankPackages"),
                bankKeywords = o.strings("bankKeywords"),
                securityPackages = o.strings("securityPackages"),
                securityKeywords = o.strings("securityKeywords"),
                extraProtectedPackages = o.strings("extraProtectedPackages"),
            )
        }

        private fun JSONObject.strings(key: String): List<String> {
            val arr: JSONArray = optJSONArray(key) ?: return emptyList()
            return (0 until arr.length()).mapNotNull { arr.optString(it).trim().ifEmpty { null } }
        }
    }
}

/**
 * Arquivo de configuração: o padrão fica em assets/protection_rules.json; a
 * versão editada pelo usuário é salva em files/protection_rules.json.
 */
object RulesRepository {
    private const val ASSET = "protection_rules.json"
    private lateinit var appContext: Context
    private val userFile: File get() = File(appContext.filesDir, ASSET)

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun defaultText(): String = appContext.assets.open(ASSET).bufferedReader().use { it.readText() }

    fun currentText(): String = if (userFile.exists()) userFile.readText() else defaultText()

    fun load(): ProtectionRules = try {
        ProtectionRules.parse(currentText())
    } catch (e: Exception) {
        ProtectionRules.parse(defaultText())
    }

    /** Valida e salva. Retorna mensagem de erro ou null em caso de sucesso. */
    fun save(text: String): String? = try {
        ProtectionRules.parse(text)
        userFile.writeText(text)
        null
    } catch (e: Exception) {
        "JSON inválido: ${e.message}"
    }

    fun resetToDefault() {
        userFile.delete()
    }
}
