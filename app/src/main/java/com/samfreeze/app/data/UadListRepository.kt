package com.samfreeze.app.data

import android.content.Context
import com.samfreeze.app.model.FreezeLevel
import com.samfreeze.app.model.UadPackageInfo
import org.json.JSONObject

/**
 * Loads the bundled Universal Android Debloater Next Generation (UAD-ng)
 * package list — a curated, community-maintained database of Android
 * system/OEM packages, each tagged with a safety rating for removal
 * (Recommended / Advanced / Expert / Unsafe) and a human-readable
 * description of what the package does.
 *
 * Source: https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation
 * (resources/assets/uad_lists.json)
 *
 * SamFreeze ships a bundled snapshot at assets/uad_lists.json and reads it
 * entirely offline — this app intentionally has no INTERNET permission, so
 * there is no network refresh. The snapshot uses UAD-ng's exact schema
 * (packageName -> {list, description, removal}), so it can be swapped out
 * for a newer/fuller export from the same upstream file at any time by
 * replacing that one asset.
 *
 * Everything here is read-only, in-memory, and computed once on first use —
 * matching apps against this list is just a map lookup by package name, so
 * it's cheap enough to do for every installed app on every load.
 */
class UadListRepository(private val context: Context) {

    private val entries: Map<String, UadPackageInfo> by lazy { loadFromAssets() }

    private fun loadFromAssets(): Map<String, UadPackageInfo> {
        return try {
            val text = context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
            val root = JSONObject(text)
            val out = LinkedHashMap<String, UadPackageInfo>(root.length())
            val keys = root.keys()
            while (keys.hasNext()) {
                val pkg = keys.next()
                val obj = root.optJSONObject(pkg) ?: continue
                out[pkg] = UadPackageInfo(
                    packageName = pkg,
                    list = obj.optString("list", "Misc"),
                    description = obj.optString("description", ""),
                    removal = obj.optString("removal", "Unsafe")
                )
            }
            out
        } catch (t: Throwable) {
            emptyMap()
        }
    }

    /** Total number of packages known to the bundled list — shown in Settings credits. */
    val packageCount: Int get() = entries.size

    fun infoFor(packageName: String): UadPackageInfo? = entries[packageName]

    /** All known package names tagged with the given freeze/removal level. */
    fun packagesForLevel(level: FreezeLevel): Set<String> =
        entries.values.filter { it.freezeLevel == level }.mapTo(mutableSetOf()) { it.packageName }

    companion object {
        private const val ASSET_NAME = "uad_lists.json"
    }
}
