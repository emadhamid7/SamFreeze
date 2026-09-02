package com.samfreeze.app.root

import com.samfreeze.app.util.PackageUtils

/**
 * The ONLY place in the app that builds shell command strings.
 *
 * Every function here validates its package-name argument against
 * [PackageUtils.isValidPackageName] and throws [IllegalArgumentException]
 * if it doesn't match. There is deliberately no generic "run this string"
 * function — every command SamFreeze can ever issue is enumerated here.
 */
object Commands {

    private fun requireValidPackage(pkg: String): String {
        require(PackageUtils.isValidPackageName(pkg)) { "Invalid package name: $pkg" }
        return pkg
    }

    fun checkRootId(): String = "id"

    fun disableUser(pkg: String, userId: Int = 0): String {
        val safe = requireValidPackage(pkg)
        return "pm disable-user --user $userId $safe"
    }

    fun enable(pkg: String, userId: Int = 0): String {
        val safe = requireValidPackage(pkg)
        return "pm enable --user $userId $safe"
    }

    fun forceStop(pkg: String): String {
        val safe = requireValidPackage(pkg)
        return "am force-stop $safe"
    }

    /** Soft uninstall for the current user only. Never touches /system or any partition. */
    fun uninstallForUser(pkg: String, userId: Int = 0): String {
        val safe = requireValidPackage(pkg)
        return "pm uninstall --user $userId $safe"
    }

    /**
     * Queries the actual enabled-state string reported by the package
     * manager, used as a cross-check against the PackageManager API result.
     */
    fun getEnabledState(pkg: String, userId: Int = 0): String {
        val safe = requireValidPackage(pkg)
        return "pm get-application-enabled-setting --user $userId $safe"
    }

    /** Lists currently running process names, one per line — used to flag "running" apps. */
    fun listRunningProcesses(): String = "ps -A -o NAME"

    /**
     * Builds a single shell script that sums each package's data + code
     * directory sizes (KB) in one su round trip, printing
     * "<package> <sizeKb>" per line. All package names are validated first.
     */
    fun batchDataSizes(packages: List<String>): String {
        val safe = packages.map { requireValidPackage(it) }
        if (safe.isEmpty()) return "true"
        val lines = safe.joinToString("\n") { pkg ->
            "echo -n \"$pkg \"; du -sk /data/data/$pkg /data/app/*/$pkg*/*.apk 2>/dev/null | awk '{s+=\$1} END {print s+0}'"
        }
        return lines
    }

    /**
     * Clears one app's cache directories — internal cache, code cache, and
     * (critically) its external/sdcard cache dir, which is where most of
     * an app's "Cache" size actually lives for anything media-heavy. Never
     * touches its actual data or the APK.
     */
    fun clearAppCache(pkg: String): String {
        val safe = requireValidPackage(pkg)
        return "rm -rf /data/data/$safe/cache /data/data/$safe/code_cache " +
            "/sdcard/Android/data/$safe/cache 2>/dev/null; true"
    }

    /**
     * Clears cache directories for every installed app in one pass, plus a
     * few safe, always-rebuildable system caches (PackageManager's parsed
     * manifest cache, tombstone crash dumps, dropbox logs). Fixed command,
     * no user input.
     *
     * Deliberately does NOT touch /data/dalvik-cache or app oat/ dirs —
     * that's live ART-compiled code, not "cache" in the everyday sense;
     * wiping it system-wide forces a full recompile of every app on next
     * boot and can leave the device sluggish or unstable for a while. Also
     * skips the /cache partition (backup/fota/lpm/recovery) since its
     * layout is too OEM/device-specific to touch blindly and it isn't part
     * of what Android's own per-app storage screen counts as "Cache".
     */
    fun clearAllCaches(): String =
        "rm -rf /data/data/*/cache /data/data/*/code_cache /sdcard/Android/data/*/cache " +
            "/data/system/package_cache/* /data/tombstones/* /data/system/dropbox/* 2>/dev/null; true"

    /**
     * Sums the on-disk size (KB) of every path [clearAllCaches] is about to
     * touch. Run once before and once after the clear so we can tell the
     * user how much was actually freed, instead of a boolean that's true
     * even when nothing changed.
     */
    fun cacheFootprintKb(): String =
        "du -sck /data/data/*/cache /data/data/*/code_cache /sdcard/Android/data/*/cache " +
            "/data/system/package_cache /data/tombstones /data/system/dropbox 2>/dev/null " +
            "| tail -1 | awk '{print \$1}'"
}
