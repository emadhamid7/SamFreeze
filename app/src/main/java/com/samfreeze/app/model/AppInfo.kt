package com.samfreeze.app.model

import android.graphics.drawable.Drawable

enum class AppState {
    ACTIVE,
    FROZEN,
    UNKNOWN
}

/** Rough origin classification, used only as a small UI label — not a hard filter. */
enum class AppSource {
    SAMSUNG, GOOGLE, AOSP, OTHER
}

/**
 * Snapshot of a single installed application, combining data from
 * PackageManager. Icon is loaded lazily/off the main thread and cached
 * by the repository — this class just holds a reference once loaded.
 *
 * [sizeBytes] is populated lazily (on demand, e.g. when the user asks to
 * sort by size) rather than for every app on every load, since computing
 * it requires an extra root call.
 */
data class AppInfo(
    val packageName: String,
    val label: String,
    val isSystemApp: Boolean,
    val state: AppState,
    val versionName: String?,
    val uid: Int,
    val source: AppSource = AppSource.OTHER,
    val isFavorite: Boolean = false,
    val icon: Drawable? = null,
    val isRunning: Boolean = false,
    val sizeBytes: Long? = null,
    /** Directory the APK is installed under, e.g. /system/priv-app/Foo — null if it couldn't be resolved. */
    val apkPath: String? = null,
    /** Non-null when this package is known to the bundled UAD-ng list — drives the risk dot and Freeze Levels screen. */
    val uadInfo: UadPackageInfo? = null
) {
    val isFrozen: Boolean get() = state == AppState.FROZEN
}

enum class AppFilter {
    USER, SYSTEM, ALL, FROZEN
}

enum class SortOrder {
    NAME_ASC, NAME_DESC, PACKAGE_ASC, PACKAGE_DESC
}
