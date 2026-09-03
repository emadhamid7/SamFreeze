package com.samfreeze.app.data

import android.content.Context
import com.samfreeze.app.model.FreezeLevel
import com.samfreeze.app.model.UadPackageInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Loads the Universal Android Debloater Next Generation (UAD-NG) package
 * list, a community-maintained database of Android system and OEM
 * packages, each tagged with a safety rating for removal (Recommended,
 * Advanced, Expert, Unsafe) and a human-readable description of what the
 * package does.
 *
 * Source: https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation
 * (resources/assets/uad_lists.json)
 *
 * On first run this app has a small bundled snapshot at assets/uad_lists.json
 * so the Freeze Levels screen and risk dots work immediately, offline. When
 * the device has internet, [downloadLatest] pulls the current full list from
 * GitHub and caches it in the app's private files directory, so future
 * launches use the complete, up to date debloat list instead of the bundled
 * snapshot. Nothing is auto-downloaded in the background; it only happens
 * when the user taps "Update debloat list" in Settings.
 */
class UadListRepository(private val context: Context) {

    @Volatile
    private var entries: Map<String, UadPackageInfo> = emptyMap()

    @Volatile
    private var loadedFromDownloadedCache: Boolean = false

    init {
        entries = loadBestAvailable()
    }

    private fun cacheFile() = java.io.File(context.filesDir, CACHE_FILE_NAME)

    private fun loadBestAvailable(): Map<String, UadPackageInfo> {
        val cache = cacheFile()
        if (cache.exists()) {
            val parsed = runCatching { parse(cache.readText()) }.getOrNull()
            if (parsed != null && parsed.isNotEmpty()) {
                loadedFromDownloadedCache = true
                return parsed
            }
        }
        loadedFromDownloadedCache = false
        return runCatching {
            val text = context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
            parse(text)
        }.getOrDefault(emptyMap())
    }

    private fun parse(text: String): Map<String, UadPackageInfo> {
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
        return out
    }

    /**
     * Downloads the current full uad_lists.json from GitHub, validates it,
     * caches it to disk, and swaps it in as the active list. Returns the
     * number of packages loaded on success.
     */
    suspend fun downloadLatest(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val connection = URL(REMOTE_URL).openConnection() as HttpURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.requestMethod = "GET"
            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                connection.disconnect()
                return@withContext Result.failure(Exception("Server returned ${connection.responseCode}"))
            }

            val text = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()

            val parsed = parse(text)
            if (parsed.isEmpty()) {
                return@withContext Result.failure(Exception("Downloaded list was empty or invalid"))
            }

            cacheFile().writeText(text)
            entries = parsed
            loadedFromDownloadedCache = true
            Result.success(parsed.size)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    /** True once the full list has been downloaded at least once; false while still on the small bundled snapshot. */
    val isFullListDownloaded: Boolean get() = loadedFromDownloadedCache

    /** Total number of packages currently loaded, shown in Settings. */
    val packageCount: Int get() = entries.size

    fun infoFor(packageName: String): UadPackageInfo? = entries[packageName]

    /** All known package names tagged with the given freeze/removal level. */
    fun packagesForLevel(level: FreezeLevel): Set<String> =
        entries.values.filter { it.freezeLevel == level }.mapTo(mutableSetOf()) { it.packageName }

    companion object {
        private const val ASSET_NAME = "uad_lists.json"
        private const val CACHE_FILE_NAME = "uad_lists_downloaded.json"
        private const val REMOTE_URL =
            "https://raw.githubusercontent.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation/main/resources/assets/uad_lists.json"
    }
}
