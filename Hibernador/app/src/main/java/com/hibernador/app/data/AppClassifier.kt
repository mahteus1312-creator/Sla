package com.hibernador.app.data

import android.app.WallpaperManager
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Process
import android.provider.Settings
import android.provider.Telephony
import android.telecom.TelecomManager
import android.view.inputmethod.InputMethodManager
import com.hibernador.app.shizuku.ShizukuManager
import java.text.Normalizer
import java.util.Locale

enum class ProtectionReason(val label: String) {
    SELF("Este app"),
    LAUNCHER("Launcher"),
    KEYBOARD("Teclado"),
    PHONE_SMS("Telefone/SMS"),
    SYSTEM_ESSENTIAL("Sistema essencial"),
    ACTIVE_SERVICE("Serviço ativo"),
    BANK("Banco/carteira"),
    SECURITY("Segurança"),
    GAME("Jogo"),
    USER_RULE("Lista do usuário"),
}

/**
 * Decide se um app pode ser hibernado. Retorna o motivo de proteção, ou null
 * se o app pode ser parado.
 */
class AppClassifier(context: Context, private val rules: ProtectionRules) {

    private val pm = context.packageManager
    private val selfPackage = context.packageName

    private val launchers: Set<String> = packagesFor(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))

    private val keyboards: Set<String> = buildSet {
        runCatching {
            context.getSystemService(InputMethodManager::class.java)
                ?.inputMethodList?.forEach { add(it.packageName) }
        }
        Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.substringBefore('/')?.takeIf { it.isNotEmpty() }?.let { add(it) }
    }

    private val phoneSms: Set<String> = buildSet {
        runCatching { context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage }
            .getOrNull()?.let { add(it) }
        runCatching { Telephony.Sms.getDefaultSmsPackage(context) }.getOrNull()?.let { add(it) }
        addAll(packagesFor(Intent(Intent.ACTION_DIAL)))
        addAll(packagesFor(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"))))
        addAll(PHONE_SMS_PACKAGES)
    }

    private val activeServices: Set<String> = buildSet {
        val cr = context.contentResolver
        for (key in listOf("enabled_accessibility_services", "enabled_notification_listeners")) {
            Settings.Secure.getString(cr, key)?.split(':')?.forEach { comp ->
                comp.substringBefore('/').trim().takeIf { it.isNotEmpty() }?.let { add(it) }
            }
        }
        runCatching {
            context.getSystemService(DevicePolicyManager::class.java)?.activeAdmins
                ?.forEach { add(it.packageName) }
        }
        runCatching { WallpaperManager.getInstance(context).wallpaperInfo?.packageName }
            .getOrNull()?.let { add(it) }
    }

    @Suppress("DEPRECATION")
    fun classify(app: ApplicationInfo, label: String): ProtectionReason? {
        val pkg = app.packageName
        val text = normalize("$pkg $label")
        return when {
            pkg == selfPackage || pkg == ShizukuManager.SHIZUKU_PACKAGE -> ProtectionReason.SELF
            pkg in launchers -> ProtectionReason.LAUNCHER
            pkg in keyboards -> ProtectionReason.KEYBOARD
            pkg in phoneSms -> ProtectionReason.PHONE_SMS
            app.uid < Process.FIRST_APPLICATION_UID ||
                (app.flags and ApplicationInfo.FLAG_PERSISTENT) != 0 ||
                matchesAny(pkg, ESSENTIAL_PACKAGES) -> ProtectionReason.SYSTEM_ESSENTIAL
            pkg in activeServices -> ProtectionReason.ACTIVE_SERVICE
            matchesAny(pkg, rules.bankPackages) || containsAny(text, rules.bankKeywords) -> ProtectionReason.BANK
            matchesAny(pkg, rules.securityPackages) || containsAny(text, rules.securityKeywords) -> ProtectionReason.SECURITY
            rules.protectGames && (app.category == ApplicationInfo.CATEGORY_GAME ||
                (app.flags and ApplicationInfo.FLAG_IS_GAME) != 0) -> ProtectionReason.GAME
            matchesAny(pkg, rules.extraProtectedPackages) -> ProtectionReason.USER_RULE
            else -> null
        }
    }

    @Suppress("DEPRECATION")
    private fun packagesFor(intent: Intent): Set<String> = runCatching {
        pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .mapNotNull { it.activityInfo?.packageName }
            .toSet()
    }.getOrDefault(emptySet())

    companion object {
        /** Serviços essenciais que nunca são tocados (não editável, por segurança). */
        val ESSENTIAL_PACKAGES = listOf(
            "android",
            "com.android.systemui",
            "com.android.phone",
            "com.android.server.telecom",
            "com.android.shell",
            "com.android.bluetooth",
            "com.android.nfc",
            "com.android.se",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
            "com.android.externalstorage",
            "com.android.location.fused",
            "com.android.keychain",
            "com.android.inputdevices",
            "com.android.vending",
            "com.android.ext.services",
            "com.google.android.ext.services",
            "com.google.android.ext.shared",
            "com.google.android.gsf",
            "com.google.android.gms*",
            "com.google.android.gsf*",
            "com.android.providers.*",
            "com.android.networkstack*",
            "com.google.android.networkstack*",
            "com.android.wifi*",
            "com.google.android.wifi*",
            "com.android.cellbroadcast*",
            "com.google.android.cellbroadcast*",
            "com.android.ims*",
            "com.android.carrierconfig",
            "com.android.stk",
            "com.android.emergency",
            "com.google.android.apps.work.oobconfig",
            "com.qualcomm.*",
            "com.qti.*",
            "com.mediatek.*",
            "vendor.*",
            "com.sec.imsservice",
            "com.samsung.android.providers.*",
        )

        val PHONE_SMS_PACKAGES = setOf(
            "com.android.dialer",
            "com.google.android.dialer",
            "com.android.incallui",
            "com.android.contacts",
            "com.google.android.contacts",
            "com.android.mms",
            "com.google.android.apps.messaging",
            "com.samsung.android.dialer",
            "com.samsung.android.incallui",
            "com.samsung.android.messaging",
            "com.samsung.android.app.contacts",
        )

        fun normalize(s: String): String =
            Normalizer.normalize(s, Normalizer.Form.NFD)
                .replace(Regex("\\p{Mn}+"), "")
                .lowercase(Locale.ROOT)

        /** Exato (inclui subpacotes "entrada.*") ou prefixo quando termina com "*". */
        fun matchesAny(pkg: String, entries: List<String>): Boolean = entries.any { e ->
            if (e.endsWith("*")) pkg.startsWith(e.dropLast(1))
            else pkg == e || pkg.startsWith("$e.")
        }

        fun containsAny(text: String, keywords: List<String>): Boolean =
            keywords.any { k -> normalize(k).takeIf { it.isNotBlank() }?.let { text.contains(it) } == true }
    }
}
