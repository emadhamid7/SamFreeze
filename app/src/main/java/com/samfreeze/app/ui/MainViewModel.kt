package com.samfreeze.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.samfreeze.app.data.PackageRepository
import com.samfreeze.app.data.PreferencesRepository
import com.samfreeze.app.data.StatsRepository
import com.samfreeze.app.data.UadListRepository
import com.samfreeze.app.model.AppFilter
import com.samfreeze.app.model.AppInfo
import com.samfreeze.app.model.AppState
import com.samfreeze.app.model.FreezeLevel
import com.samfreeze.app.model.SortOrder
import com.samfreeze.app.root.RootShell
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class RootState { CHECKING, AVAILABLE, UNAVAILABLE }

data class FreezeOutcome(
    val packageName: String,
    val label: String,
    val success: Boolean,
    val message: String? = null
)

data class BatchProgress(val current: Int, val total: Int, val label: String)

data class MainUiState(
    val rootState: RootState = RootState.CHECKING,
    val apps: List<AppInfo> = emptyList(),
    val visibleApps: List<AppInfo> = emptyList(),
    val isLoading: Boolean = true,
    val query: String = "",
    val filter: AppFilter = AppFilter.USER,
    val sortOrder: SortOrder = SortOrder.NAME_ASC,
    val selectionMode: Boolean = false,
    val selectedPackages: Set<String> = emptySet(),
    val lastError: FreezeOutcome? = null,
    val batchProgress: BatchProgress? = null,
    val batchResult: Pair<Int, Int>? = null // successCount, failCount
)

class MainViewModel(
    private val packageRepository: PackageRepository,
    private val preferencesRepository: PreferencesRepository,
    private val rootShell: RootShell,
    private val statsRepository: StatsRepository = StatsRepository(rootShell),
    private val uadListRepository: UadListRepository? = null
) : ViewModel() {

    private val _state = MutableStateFlow(MainUiState())
    val state: StateFlow<MainUiState> = _state.asStateFlow()

    init {
        checkRoot()
        observeFavorites()
    }

    private fun observeFavorites() {
        viewModelScope.launch {
            preferencesRepository.favorites.collect { favorites ->
                val newState = _state.value.copy(
                    apps = _state.value.apps.map { app -> app.copy(isFavorite = app.packageName in favorites) }
                )
                _state.value = newState.copy(visibleApps = applyFilters(newState))
            }
        }
    }

    fun checkRoot() {
        _state.value = _state.value.copy(rootState = RootState.CHECKING)
        viewModelScope.launch {
            val ok = rootShell.checkRoot()
            _state.value = _state.value.copy(rootState = if (ok) RootState.AVAILABLE else RootState.UNAVAILABLE)
            if (ok) loadApps()
        }
    }

    fun loadApps() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            val favorites = preferencesRepository.favorites.first()

            val loaded = packageRepository.loadInstalledApps()
            val running = try {
                statsRepository.runningPackages()
            } catch (t: Throwable) {
                emptySet()
            }

            val enriched = loaded.map { app ->
                app.copy(
                    isFavorite = app.packageName in favorites,
                    isRunning = app.packageName in running,
                    uadInfo = uadListRepository?.infoFor(app.packageName)
                )
            }

            val newState = _state.value.copy(apps = enriched, isLoading = false)
            _state.value = newState.copy(visibleApps = applyFilters(newState))
        }
    }

    /** Re-queries actual state for all currently loaded apps (used on resume/pull-refresh). */
    fun refresh() {
        viewModelScope.launch {
            val current = _state.value.apps
            val updated = current.map { it.copy(state = packageRepository.resolveState(it.packageName)) }
            val newState = _state.value.copy(apps = updated)
            _state.value = newState.copy(visibleApps = applyFilters(newState))
        }
    }

    fun setQuery(query: String) {
        val newState = _state.value.copy(query = query)
        _state.value = newState.copy(visibleApps = applyFilters(newState))
    }

    fun setFilter(filter: AppFilter) {
        val newState = _state.value.copy(filter = filter)
        _state.value = newState.copy(visibleApps = applyFilters(newState))
    }

    fun setSortOrder(order: SortOrder) {
        val newState = _state.value.copy(sortOrder = order)
        _state.value = newState.copy(visibleApps = applyFilters(newState))
    }

    private fun applyFilters(s: MainUiState): List<AppInfo> {
        var list = s.apps

        list = when (s.filter) {
            AppFilter.USER -> list.filter { !it.isSystemApp }
            AppFilter.SYSTEM -> list.filter { it.isSystemApp }
            AppFilter.ALL -> list
            AppFilter.FROZEN -> list.filter { it.state == AppState.FROZEN }
        }

        if (s.query.isNotBlank()) {
            val q = s.query.trim().lowercase()
            list = list.filter {
                it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q)
            }
        }

        list = when (s.sortOrder) {
            SortOrder.NAME_ASC -> list.sortedBy { it.label.lowercase() }
            SortOrder.NAME_DESC -> list.sortedByDescending { it.label.lowercase() }
            SortOrder.PACKAGE_ASC -> list.sortedBy { it.packageName }
            SortOrder.PACKAGE_DESC -> list.sortedByDescending { it.packageName }
        }

        return list
    }

    // ---- Single app freeze/unfreeze ----

    fun toggleFreeze(app: AppInfo) {
        viewModelScope.launch {
            val result = if (app.isFrozen) {
                packageRepository.unfreeze(app.packageName)
            } else {
                packageRepository.freeze(app.packageName)
            }
            applySingleResult(app, result)
        }
    }

    private fun applySingleResult(app: AppInfo, result: PackageRepository.OpResult) {
        when (result) {
            is PackageRepository.OpResult.Success -> {
                val newState = _state.value.copy(
                    apps = _state.value.apps.map {
                        if (it.packageName == app.packageName) it.copy(state = result.newState) else it
                    },
                    lastError = null
                )
                _state.value = newState.copy(visibleApps = applyFilters(newState))
            }
            is PackageRepository.OpResult.Failure -> {
                // Always re-sync actual state so the UI never shows a false status.
                refresh()
                _state.value = _state.value.copy(
                    lastError = FreezeOutcome(
                        packageName = app.packageName,
                        label = app.label,
                        success = false,
                        message = result.message + (if (result.details.isNotBlank()) "\n\n${result.details}" else "")
                    )
                )
            }
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(lastError = null)
    }

    fun toggleFavorite(app: AppInfo) {
        viewModelScope.launch { preferencesRepository.toggleFavorite(app.packageName) }
    }

    // ---- Multi-select ----

    fun setSelectionMode(enabled: Boolean) {
        _state.value = _state.value.copy(
            selectionMode = enabled,
            selectedPackages = if (enabled) _state.value.selectedPackages else emptySet()
        )
    }

    /** Toggles one package's selection. Auto-exits selection mode once nothing is left selected. */
    fun toggleSelected(packageName: String) {
        val current = _state.value.selectedPackages
        val updated = if (packageName in current) current - packageName else current + packageName
        _state.value = _state.value.copy(
            selectedPackages = updated,
            selectionMode = updated.isNotEmpty()
        )
    }

    fun clearSelection() {
        _state.value = _state.value.copy(selectedPackages = emptySet(), selectionMode = false)
    }

    fun freezeSelected() = runBatch(freeze = true, targets = null)
    fun unfreezeSelected() = runBatch(freeze = false, targets = null)

    /** Force-stops whatever's currently multi-selected in the list — a one-off action, independent of the persisted Quick Stop list. */
    fun forceStopSelected() = runForceStopBatch(_state.value.selectedPackages)

    /** Force-stops an explicit package set — used by the Quick Stop List screen's "Force Stop List" button. */
    fun forceStopSet(packages: Set<String>) = runForceStopBatch(packages)

    /** Freezes every currently frozen app back to active — the panic-button "Unfreeze All". */
    fun unfreezeAll() {
        val targets = _state.value.apps.filter { it.state == AppState.FROZEN }.map { it.packageName }.toSet()
        runBatch(freeze = false, targets = targets)
    }

    /** Installed apps that the bundled UAD-ng list tags with the given freeze level, sorted by label. */
    fun appsForLevel(level: FreezeLevel): List<AppInfo> =
        _state.value.apps.filter { it.uadInfo?.freezeLevel == level }.sortedBy { it.label.lowercase() }

    /** Freezes or unfreezes a single package — used by the per-app toggle on the Freeze Levels screen. */
    fun setFrozen(packageName: String, freeze: Boolean) {
        runBatch(freeze = freeze, targets = setOf(packageName))
    }

    private fun runBatch(freeze: Boolean, targets: Set<String>?) {
        val installed = _state.value.apps.associateBy { it.packageName }
        val candidatePackages = targets ?: _state.value.selectedPackages
        val ops = candidatePackages
            .mapNotNull { installed[it] }
            .filter { !packageRepository.isHidden(it.packageName) }
            .filter { if (freeze) it.state != AppState.FROZEN else it.state == AppState.FROZEN }

        if (ops.isEmpty()) {
            _state.value = _state.value.copy(
                selectionMode = false,
                selectedPackages = emptySet(),
                batchResult = 0 to 0
            )
            return
        }

        viewModelScope.launch {
            var success = 0
            var failed = 0
            ops.forEachIndexed { index, app ->
                _state.value = _state.value.copy(
                    batchProgress = BatchProgress(index + 1, ops.size, app.label)
                )
                val result = if (freeze) packageRepository.freeze(app.packageName)
                             else packageRepository.unfreeze(app.packageName)

                when (result) {
                    is PackageRepository.OpResult.Success -> {
                        success++
                        _state.value = _state.value.copy(
                            apps = _state.value.apps.map {
                                if (it.packageName == app.packageName) it.copy(state = result.newState) else it
                            }
                        )
                    }
                    is PackageRepository.OpResult.Failure -> failed++
                }
            }
            val finalState = _state.value.copy(
                batchProgress = null,
                batchResult = success to failed,
                selectionMode = false,
                selectedPackages = emptySet()
            )
            _state.value = finalState.copy(visibleApps = applyFilters(finalState))
        }
    }

    fun clearBatchResult() {
        _state.value = _state.value.copy(batchResult = null)
    }

    /**
     * `am force-stop` for a set of packages, reusing the same progress/result
     * UI as freeze/unfreeze batches. Doesn't require the app to be currently
     * known-running — force-stopping an already-stopped app is a harmless
     * no-op, and re-checking liveness here would just risk acting on stale
     * data from whenever this screen's app list was last loaded.
     */
    private fun runForceStopBatch(packages: Set<String>) {
        val installed = _state.value.apps.associateBy { it.packageName }
        val ops = packages
            .mapNotNull { installed[it] }
            .filter { !packageRepository.isHidden(it.packageName) }

        if (ops.isEmpty()) {
            _state.value = _state.value.copy(
                selectionMode = false,
                selectedPackages = emptySet(),
                batchResult = 0 to 0
            )
            return
        }

        viewModelScope.launch {
            var success = 0
            var failed = 0
            ops.forEachIndexed { index, app ->
                _state.value = _state.value.copy(
                    batchProgress = BatchProgress(index + 1, ops.size, app.label)
                )
                val ok = packageRepository.forceStop(app.packageName)
                if (ok) {
                    success++
                    _state.value = _state.value.copy(
                        apps = _state.value.apps.map {
                            if (it.packageName == app.packageName) it.copy(isRunning = false) else it
                        }
                    )
                } else {
                    failed++
                }
            }
            val finalState = _state.value.copy(
                batchProgress = null,
                batchResult = success to failed,
                selectionMode = false,
                selectedPackages = emptySet()
            )
            _state.value = finalState.copy(visibleApps = applyFilters(finalState))
        }
    }
}
