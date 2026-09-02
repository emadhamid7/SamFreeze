package com.samfreeze.app.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.Log
import com.samfreeze.app.model.AppInfo
import com.samfreeze.app.model.AppSource
import com.samfreeze.app.model.AppState
import com.samfreeze.app.root.Commands
import com.samfreeze.app.root.RootShell
import com.samfreeze.app.util.ProtectedPackages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "SamFreeze"

/**
 * Discovers installed applications and their real enabled/disabled state
 * directly from PackageManager (the authoritative source — never trusted
 * from a local cache), and performs freeze/unfreeze operations through
 * [RootShell], always re-querying actual state afterward so the UI can
 * never show a state that doesn't match reality.
 *
 * Packages in the hidden safety list ([ProtectedPackages]) are filtered
 * out of every result here — they never reach the UI at all, not even as
 * a disabled/locked row, per design: nothing about them is user-editable.
 */
class PackageRepository(
    private val context: Context,
    private val rootShell: RootShell = RootShell.getInstance()
) {
    private val pm: PackageManager get() = context.packageManager

    /**
     * In-memory icon cache. This is the fix for the scroll stutter: without
     * it, every row re-fetched its icon from PackageManager (a real decode,
     * not free) every single time it scrolled back into view, since
     * LazyColumn recycles and recomposes rows constantly while scrolling.
     * Icons never change during a session, so caching them once is safe.
     */
    private val iconCache = ConcurrentHashMap<String, Drawable>()

    /**
     * Loads the full installed-app list (not just launchable apps) with
     * their current enabled state, minus the hidden safety-list packages.
     * Icons are NOT loaded here — see [loadIcon] which callers should
     * invoke lazily per-row off the main thread (e.g. as items become visible).
     */
    suspend fun loadInstalledApps(): List<AppInfo> = withContext(Dispatchers.Default) {
        val apps: List<ApplicationInfo> = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.getInstalledApplications(0)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "getInstalledApplications failed: ${t.javaClass.simpleName}: ${t.message}")
            emptyList()
        }

        val hidden = ProtectedPackages.defaultProtectedSet(apps.map { it.packageName })

        apps.filter { it.packageName !in hidden }
            .map { appInfo ->
                val label = try {
                    pm.getApplicationLabel(appInfo).toString()
                } catch (t: Throwable) {
                    appInfo.packageName
                }
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val versionName = try {
                    pm.getPackageInfo(appInfo.packageName, 0).versionName
                } catch (t: Throwable) {
                    null
                }
                AppInfo(
                    packageName = appInfo.packageName,
                    label = label,
                    isSystemApp = isSystem,
                    state = resolveState(appInfo.packageName),
                    versionName = versionName,
                    uid = appInfo.uid,
                    source = classifySource(appInfo.packageName)
                )
            }.sortedBy { it.label.lowercase() }
    }

    private fun classifySource(pkg: String): AppSource = when {
        pkg.startsWith("com.samsung.") || pkg.startsWith("com.sec.") -> AppSource.SAMSUNG
        pkg.startsWith("com.google.") -> AppSource.GOOGLE
        pkg == "android" || pkg.startsWith("com.android.") -> AppSource.AOSP
        else -> AppSource.OTHER
    }

    /** Loads a single app's icon off the calling (background) thread, cached after first fetch. */
    suspend fun loadIcon(packageName: String): Drawable? = withContext(Dispatchers.IO) {
        iconCache[packageName]?.let { return@withContext it }
        try {
            val icon = pm.getApplicationIcon(packageName)
            iconCache[packageName] = icon
            icon
        } catch (t: Throwable) {
            null
        }
    }

    /**
     * Resolves the authoritative current state using the PackageManager
     * API (not a root command, not a cache) — this covers reboot, another
     * package manager, system updates, etc.
     */
    fun resolveState(packageName: String): AppState {
        return try {
            when (pm.getApplicationEnabledSetting(packageName)) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> AppState.ACTIVE

                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED -> AppState.FROZEN

                else -> AppState.UNKNOWN
            }
        } catch (t: Throwable) {
            AppState.UNKNOWN
        }
    }

    suspend fun refreshState(packageName: String): AppState = withContext(Dispatchers.Default) {
        resolveState(packageName)
    }

    sealed class OpResult {
        data class Success(val newState: AppState) : OpResult()
        data class Failure(val message: String, val details: String) : OpResult()
    }

    /** Hard client-side gate: never even attempt a root command for a hidden-list package. */
    fun isHidden(packageName: String): Boolean = ProtectedPackages.isCriticalDefault(packageName) ||
        ProtectedPackages.isLikelyLauncher(packageName)

    suspend fun freeze(packageName: String): OpResult = withContext(Dispatchers.IO) {
        if (isHidden(packageName)) {
            return@withContext OpResult.Failure("This package is protected and cannot be frozen.", "")
        }
        val result = rootShell.execute(Commands.disableUser(packageName))
        val actual = resolveState(packageName)
        if (result.success && actual == AppState.FROZEN) {
            OpResult.Success(actual)
        } else {
            OpResult.Failure(
                message = "Unable to freeze this application.",
                details = if (result.stderr.isNotBlank()) result.stderr else result.stdout
            )
        }
    }

    suspend fun unfreeze(packageName: String): OpResult = withContext(Dispatchers.IO) {
        val result = rootShell.execute(Commands.enable(packageName))
        val actual = resolveState(packageName)
        if (result.success && actual == AppState.ACTIVE) {
            OpResult.Success(actual)
        } else {
            OpResult.Failure(
                message = "Unable to unfreeze this application.",
                details = if (result.stderr.isNotBlank()) result.stderr else result.stdout
            )
        }
    }

    suspend fun forceStop(packageName: String): Boolean = withContext(Dispatchers.IO) {
        rootShell.execute(Commands.forceStop(packageName)).success
    }

    /**
     * Soft uninstall for the current user only (`pm uninstall --user 0`).
     * Never touches /system, /product, or any partition — fully restorable
     * by reinstalling. Gated behind the Advanced Settings toggle in the UI.
     */
    suspend fun uninstallForUser(packageName: String): OpResult = withContext(Dispatchers.IO) {
        if (isHidden(packageName)) {
            return@withContext OpResult.Failure("This package is protected and cannot be uninstalled.", "")
        }
        val result = rootShell.execute(Commands.uninstallForUser(packageName), timeoutMs = 30000)
        if (result.success) {
            OpResult.Success(AppState.UNKNOWN)
        } else {
            OpResult.Failure(
                message = "Unable to uninstall this application.",
                details = if (result.stderr.isNotBlank()) result.stderr else result.stdout
            )
        }
    }

    fun launchIntent(packageName: String) = pm.getLaunchIntentForPackage(packageName)

    fun canLaunch(packageName: String): Boolean = launchIntent(packageName) != null

    /** Clears one app's cache only — never its data, never the APK. */
    suspend fun clearCache(packageName: String): Boolean = withContext(Dispatchers.IO) {
        rootShell.execute(Commands.clearAppCache(packageName), timeoutMs = 20000).success
    }

    /**
     * Clears cache for every installed app in one root round trip, and
     * reports how many KB were actually freed (measured before/after, not
     * just "the shell command exited 0" — that used to be true even when
     * the underlying rm matched nothing or silently failed, since the
     * command intentionally swallows individual rm errors).
     */
    suspend fun clearAllCaches(): CacheClearResult = withContext(Dispatchers.IO) {
        val before = footprintKb()
        val ran = rootShell.execute(Commands.clearAllCaches(), timeoutMs = 90000).success
        val after = footprintKb()
        CacheClearResult(ranOk = ran, freedKb = (before - after).coerceAtLeast(0))
    }

    private suspend fun footprintKb(): Long {
        val result = rootShell.execute(Commands.cacheFootprintKb(), timeoutMs = 30000)
        return result.stdout.trim().toLongOrNull() ?: 0L
    }
}

data class CacheClearResult(val ranOk: Boolean, val freedKb: Long)
