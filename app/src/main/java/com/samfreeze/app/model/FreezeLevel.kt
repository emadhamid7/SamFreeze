package com.samfreeze.app.model

/**
 * Freeze levels, mirroring the removal-safety categories used by the
 * Universal Android Debloater (UAD-ng) project's package list — see
 * [com.samfreeze.app.data.UadListRepository]. Each level maps 1:1 to the
 * "removal" tag on a UAD package entry (Recommended/Advanced/Expert/Unsafe),
 * so apps are bucketed into these automatically by cross-referencing the
 * device's installed packages against that list — nothing is hardcoded.
 */
enum class FreezeLevel {
    RECOMMENDED, ADVANCED, EXPERT, UNSAFE;

    companion object {
        /** Maps a UAD "removal" string onto our enum. Unknown values fall back to UNSAFE (safest default). */
        fun fromUadRemoval(removal: String?): FreezeLevel = when (removal?.lowercase()) {
            "recommended" -> RECOMMENDED
            "advanced" -> ADVANCED
            "expert" -> EXPERT
            "unsafe" -> UNSAFE
            else -> UNSAFE
        }
    }
}
