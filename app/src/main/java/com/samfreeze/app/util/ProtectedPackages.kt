package com.samfreeze.app.util

/**
 * Fixed, non-editable safety list. Packages matched here are excluded
 * entirely from every list the app shows (they never render as a row,
 * locked or otherwise) and are hard-blocked, client-side, from ever
 * reaching a root freeze/unfreeze/uninstall command — regardless of
 * which freeze level or "freeze everything" mode is applied.
 *
 * Kept intentionally small and pattern-based rather than an enormous
 * hard-coded Samsung app list: Samsung package names vary by One UI
 * version / device / CSC / region, so this protects via a short list of
 * well-known critical IDs plus a couple of narrow prefix patterns for
 * launcher/systemui-shaped packages, rather than trying to enumerate
 * every OEM package.
 */
object ProtectedPackages {

    /** Always-protected by default, cannot silently be freed without a warning dialog. */
    val CRITICAL_DEFAULTS: Set<String> = setOf(
        "android",
        "com.android.systemui",
        "com.android.settings",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.android.shell",
        "com.android.providers.settings"
    )

    /**
     * Narrow substring patterns used only to flag likely launcher packages
     * as protected-by-default. This is intentionally conservative (a few
     * well-known fragments) rather than a giant enumerated Samsung list.
     */
    private val LAUNCHER_PATTERNS = listOf(
        "android.launcher",
        "sec.android.app.launcher",
        "samsung.android.launcher"
    )

    fun isCriticalDefault(pkg: String): Boolean = pkg in CRITICAL_DEFAULTS

    fun isLikelyLauncher(pkg: String): Boolean =
        LAUNCHER_PATTERNS.any { pkg.contains(it, ignoreCase = true) }

    /** Default protected set = critical defaults + anything that looks like a launcher. */
    fun defaultProtectedSet(installedPackages: Collection<String>): Set<String> {
        val result = LinkedHashSet<String>(CRITICAL_DEFAULTS)
        installedPackages.forEach { pkg ->
            if (isLikelyLauncher(pkg)) result.add(pkg)
        }
        return result
    }
}
