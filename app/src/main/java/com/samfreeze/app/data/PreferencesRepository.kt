package com.samfreeze.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "samfreeze_prefs")

/**
 * Lightweight local persistence: favorites and settings. No accounts, no
 * passwords, no root credentials — this only ever stores package-name
 * strings and small flags. There is no user-facing "protected packages"
 * concept — the hidden safety list (see
 * [com.samfreeze.app.util.ProtectedPackages]) is enforced unconditionally
 * and isn't exposed for editing.
 */
class PreferencesRepository(private val context: Context) {

    private object Keys {
        val FAVORITES = stringSetPreferencesKey("favorites")
        val THEME = stringPreferencesKey("theme") // "system" | "light" | "dark"
        val SHOW_PACKAGE_NAMES = booleanPreferencesKey("show_package_names")
        val SHOW_RISK_DOTS = booleanPreferencesKey("show_risk_dots")
        val CONFIRM_BEFORE_FREEZE = booleanPreferencesKey("confirm_before_freeze")
        val REFRESH_ON_RESUME = booleanPreferencesKey("refresh_on_resume")
        val ADVANCED_UNINSTALL_ENABLED = booleanPreferencesKey("advanced_uninstall_enabled")
        val AUTO_CLEAR_CACHE_ENABLED = booleanPreferencesKey("auto_clear_cache_enabled")
        val AUTO_CLEAR_CACHE_INTERVAL_MINUTES = intPreferencesKey("auto_clear_cache_interval_minutes")
        val QUICK_STOP_LIST = stringSetPreferencesKey("quick_stop_list")
    }

    val favorites: Flow<Set<String>> = context.dataStore.data.map { it[Keys.FAVORITES] ?: emptySet() }

    suspend fun toggleFavorite(pkg: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.FAVORITES] ?: emptySet()
            prefs[Keys.FAVORITES] = if (pkg in current) current - pkg else current + pkg
        }
    }

    suspend fun setFavorites(pkgs: Set<String>) {
        context.dataStore.edit { prefs -> prefs[Keys.FAVORITES] = pkgs }
    }

    val theme: Flow<String> = context.dataStore.data.map { it[Keys.THEME] ?: "system" }
    suspend fun setTheme(value: String) {
        context.dataStore.edit { it[Keys.THEME] = value }
    }

    val showPackageNames: Flow<Boolean> = context.dataStore.data.map { it[Keys.SHOW_PACKAGE_NAMES] ?: true }
    suspend fun setShowPackageNames(value: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_PACKAGE_NAMES] = value }
    }

    /** Small colored dot per app showing its UAD-ng risk category. Default ON. */
    val showRiskDots: Flow<Boolean> = context.dataStore.data.map { it[Keys.SHOW_RISK_DOTS] ?: true }
    suspend fun setShowRiskDots(value: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_RISK_DOTS] = value }
    }

    /** Default OFF — the user asked for this to work, but not nag by default. */
    val confirmBeforeFreeze: Flow<Boolean> = context.dataStore.data.map { it[Keys.CONFIRM_BEFORE_FREEZE] ?: false }
    suspend fun setConfirmBeforeFreeze(value: Boolean) {
        context.dataStore.edit { it[Keys.CONFIRM_BEFORE_FREEZE] = value }
    }

    val refreshOnResume: Flow<Boolean> = context.dataStore.data.map { it[Keys.REFRESH_ON_RESUME] ?: true }
    suspend fun setRefreshOnResume(value: Boolean) {
        context.dataStore.edit { it[Keys.REFRESH_ON_RESUME] = value }
    }

    /** Advanced settings: gates the (soft) uninstall button on the details screen. Default OFF. */
    val advancedUninstallEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.ADVANCED_UNINSTALL_ENABLED] ?: false }
    suspend fun setAdvancedUninstallEnabled(value: Boolean) {
        context.dataStore.edit { it[Keys.ADVANCED_UNINSTALL_ENABLED] = value }
    }

    /** Scheduled cache clearing (all apps), via WorkManager. Default OFF, default interval 1 day. */
    val autoClearCacheEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.AUTO_CLEAR_CACHE_ENABLED] ?: false }
    suspend fun setAutoClearCacheEnabled(value: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_CLEAR_CACHE_ENABLED] = value }
    }

    val autoClearCacheIntervalMinutes: Flow<Int> =
        context.dataStore.data.map { it[Keys.AUTO_CLEAR_CACHE_INTERVAL_MINUTES] ?: (24 * 60) }
    suspend fun setAutoClearCacheIntervalMinutes(value: Int) {
        context.dataStore.edit { it[Keys.AUTO_CLEAR_CACHE_INTERVAL_MINUTES] = value }
    }

    /**
     * A user-curated set of packages meant for one-tap force-stopping — the
     * whole point being you build this list once (Settings > Quick Stop
     * List) instead of hand-picking apps to kill every single time. Read
     * both from the app (the "Force Stop List" button) and from the
     * home-screen widget's broadcast receiver.
     */
    val quickStopList: Flow<Set<String>> = context.dataStore.data.map { it[Keys.QUICK_STOP_LIST] ?: emptySet() }

    suspend fun toggleQuickStop(pkg: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.QUICK_STOP_LIST] ?: emptySet()
            prefs[Keys.QUICK_STOP_LIST] = if (pkg in current) current - pkg else current + pkg
        }
    }

    suspend fun setQuickStopList(pkgs: Set<String>) {
        context.dataStore.edit { prefs -> prefs[Keys.QUICK_STOP_LIST] = pkgs }
    }
}
