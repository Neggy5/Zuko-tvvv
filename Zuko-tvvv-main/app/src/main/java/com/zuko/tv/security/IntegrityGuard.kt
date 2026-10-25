package com.zuko.tv.security

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Debug
import com.zuko.tv.BuildConfig
import java.io.File
import java.security.MessageDigest

/**
 * Lightweight integrity / anti-RE checks for release builds.
 * Does not claim to be unbreakable — raises the cost of casual reverse engineering.
 */
object IntegrityGuard {

    /** Call early in Application.onCreate. Returns false if a suspicious environment is detected. */
    fun check(context: Context): Boolean {
        if (BuildConfig.DEBUG) return true
        return !isDebuggerAttached() &&
            !isDebugBuildFlag(context) &&
            !hasFridaOrXposedHints() &&
            !isEmulatorObvious()
    }

    private fun isDebuggerAttached(): Boolean {
        return Debug.isDebuggerConnected() || Debug.waitingForDebugger()
    }

    private fun isDebugBuildFlag(context: Context): Boolean {
        return (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

    private fun hasFridaOrXposedHints(): Boolean {
        val suspects = listOf(
            "/data/local/tmp/frida-server",
            "/data/local/tmp/re.frida.server",
            "/system/lib/libxposed_art.so",
            "/system/lib64/libxposed_art.so",
            "/system/framework/XposedBridge.jar",
        )
        if (suspects.any { File(it).exists() }) return true
        return try {
            val maps = File("/proc/self/maps").readText()
            maps.contains("frida") || maps.contains("xposed") || maps.contains("substrate")
        } catch (_: Exception) {
            false
        }
    }

    private fun isEmulatorObvious(): Boolean {
        val fp = listOf(
            Build.FINGERPRINT,
            Build.MODEL,
            Build.MANUFACTURER,
            Build.PRODUCT,
            Build.HARDWARE,
            Build.DEVICE,
        ).joinToString("|").lowercase()
        // Soft signal only — many real devices trip hard emulator lists.
        // Used as a secondary hint, not a hard block by itself.
        return fp.contains("generic") && fp.contains("sdk")
    }

    /** SHA-256 of the signing cert — compare offline against your release keystore fingerprint. */
    fun signingCertSha256(context: Context): String? {
        return try {
            val pm = context.packageManager
            val pkg = if (Build.VERSION.SDK_INT >= 28) {
                pm.getPackageInfo(context.packageName, android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, android.content.pm.PackageManager.GET_SIGNATURES)
            }
            val sig = if (Build.VERSION.SDK_INT >= 28) {
                pkg.signingInfo?.apkContentsSigners?.firstOrNull()
            } else {
                @Suppress("DEPRECATION")
                pkg.signatures?.firstOrNull()
            } ?: return null
            val digest = MessageDigest.getInstance("SHA-256").digest(sig.toByteArray())
            digest.joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            null
        }
    }
}
