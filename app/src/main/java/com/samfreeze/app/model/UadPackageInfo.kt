package com.samfreeze.app.model

/**
 * A single entry from the UAD-ng (Universal Android Debloater Next
 * Generation) package list — see
 * https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation
 *
 * [list] is UAD's rough origin/category tag (Google, Oem, Aosp, Carrier,
 * Misc, Pending — used only as a small info label here).
 * [removal] is UAD's safety rating for removing/disabling the package and
 * is what [FreezeLevel] is derived from.
 */
data class UadPackageInfo(
    val packageName: String,
    val list: String,
    val description: String,
    val removal: String
) {
    val freezeLevel: FreezeLevel get() = FreezeLevel.fromUadRemoval(removal)
}
