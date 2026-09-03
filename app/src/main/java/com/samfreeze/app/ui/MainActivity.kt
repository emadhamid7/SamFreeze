package com.samfreeze.app.ui

import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.launch
import com.samfreeze.app.SamFreezeApp
import com.samfreeze.app.R
import com.samfreeze.app.model.AppFilter
import com.samfreeze.app.model.AppInfo
import com.samfreeze.app.model.AppState
import com.samfreeze.app.model.SortOrder
import com.samfreeze.app.ui.theme.SamFreezeTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val app = application as SamFreezeApp
                @Suppress("UNCHECKED_CAST")
                return MainViewModel(app.packageRepository, app.preferencesRepository, app.rootShell, app.statsRepository, app.uadListRepository) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val app = application as SamFreezeApp
            val appTheme = com.samfreeze.app.ui.theme.rememberAppTheme(app.preferencesRepository)
            SamFreezeTheme(appTheme = appTheme) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    MainScreen(viewModel = viewModel, onOpenLevels = {
                        startActivity(Intent(this, LevelsActivity::class.java))
                    }, onOpenDetails = { pkg ->
                        startActivity(
                            Intent(this, AppDetailsActivity::class.java)
                                .putExtra(AppDetailsActivity.EXTRA_PACKAGE_NAME, pkg)
                        )
                    })
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }
}

private enum class MainTab { FREEZE, QUICK_STOP, SETTINGS }

/** Mirrors the Freeze tab's User/System filters, plus "Running" (apps with live
 *  background activity) and "Selected" (apps currently checked into the persisted
 *  Quick Stop list). No "All" here — space is tight and Running covers that need. */
private enum class QuickStopFilter { USER, SYSTEM, RUNNING, SELECTED }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onOpenLevels: () -> Unit,
    onOpenDetails: (String) -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val app = context.applicationContext as SamFreezeApp
    val showPackageNames by app.preferencesRepository.showPackageNames.collectAsStateWithLifecycle(initialValue = true)
    val showRiskDots by app.preferencesRepository.showRiskDots.collectAsStateWithLifecycle(initialValue = true)
    val confirmBeforeFreeze by app.preferencesRepository.confirmBeforeFreeze.collectAsStateWithLifecycle(initialValue = false)
    val quickStopList by app.preferencesRepository.quickStopList.collectAsStateWithLifecycle(initialValue = emptySet())
    val scope = rememberCoroutineScope()

    var pendingFreezeConfirm by remember { mutableStateOf<AppInfo?>(null) }
    var showSortMenu by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf(MainTab.FREEZE) }
    var quickStopQuery by remember { mutableStateOf("") }
    var quickStopFilter by remember { mutableStateOf(QuickStopFilter.USER) }
    var quickStopSort by remember { mutableStateOf(SortOrder.NAME_ASC) }
    var showQuickStopSortMenu by remember { mutableStateOf(false) }
    var confirmForceStopList by remember { mutableStateOf(false) }

    Scaffold(
        bottomBar = {
            if (state.selectionMode && tab == MainTab.FREEZE) {
                BottomSelectionBar(
                    count = state.selectedPackages.size,
                    onFreeze = { viewModel.freezeSelected() },
                    onUnfreeze = { viewModel.unfreezeSelected() },
                    onCancel = { viewModel.clearSelection() }
                )
            } else {
                MainBottomNav(tab = tab, onTabSelected = { tab = it })
            }
        },
        floatingActionButton = {
            if (tab == MainTab.QUICK_STOP && !state.selectionMode) {
                ExtendedFloatingActionButton(
                    onClick = { confirmForceStopList = true },
                    icon = { Icon(Icons.Default.PowerSettingsNew, contentDescription = null) },
                    text = { Text(stringResource(R.string.force_stop_list)) },
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (tab == MainTab.SETTINGS) {
                // Settings doesn't need root to be useful (theme, backups,
                // community links all work without it), so it isn't gated
                // behind the root check the way Freeze/Quick Stop are.
                SettingsTabContent(viewModel = viewModel)
            } else {
            when (state.rootState) {
                RootState.CHECKING -> LoadingBlock(stringResource(R.string.checking_root))
                RootState.UNAVAILABLE -> RootUnavailableBlock(onRetry = { viewModel.checkRoot() })
                RootState.AVAILABLE -> {
                    // Search bar up top; Freeze Levels sits right next to it,
                    // only on the Freeze tab (Quick Stop has nothing to level).
                    Row(
                        modifier = Modifier.fillMaxWidth().statusBarsPadding(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SearchBar(
                            query = if (tab == MainTab.FREEZE) state.query else quickStopQuery,
                            onQueryChange = { if (tab == MainTab.FREEZE) viewModel.setQuery(it) else quickStopQuery = it },
                            modifier = Modifier.weight(1f)
                        )
                        if (tab == MainTab.FREEZE) {
                            IconButton(onClick = onOpenLevels) {
                                Icon(Icons.Default.Layers, contentDescription = stringResource(R.string.freeze_levels))
                            }
                        } else {
                            Spacer(Modifier.width(8.dp))
                        }
                    }

                    when (tab) {
                        MainTab.FREEZE -> {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                FilterRow(selected = state.filter, onSelect = viewModel::setFilter)
                                Box {
                                    IconButton(onClick = { showSortMenu = true }) {
                                        Icon(Icons.Default.Sort, contentDescription = stringResource(R.string.sort_by))
                                    }
                                    DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                                        val options = listOf(
                                            SortOrder.NAME_ASC to stringResource(R.string.sort_name_az),
                                            SortOrder.NAME_DESC to stringResource(R.string.sort_name_za)
                                        )
                                        options.forEach { (order, label) ->
                                            DropdownMenuItem(
                                                text = { Text(label) },
                                                onClick = { showSortMenu = false; viewModel.setSortOrder(order) }
                                            )
                                        }
                                    }
                                }
                            }

                            when {
                                state.isLoading -> LoadingBlock(stringResource(R.string.loading_apps))
                                state.visibleApps.isEmpty() -> EmptyBlock(state.query, state.filter)
                                else -> AppList(
                                    apps = state.visibleApps,
                                    showPackageNames = showPackageNames,
                                    showRiskDots = showRiskDots,
                                    selectionMode = state.selectionMode,
                                    selectedPackages = state.selectedPackages,
                                    sortOrder = state.sortOrder,
                                    filter = state.filter,
                                    onClick = { app ->
                                        if (state.selectionMode) viewModel.toggleSelected(app.packageName)
                                        else onOpenDetails(app.packageName)
                                    },
                                    onLongClick = { app ->
                                        if (!state.selectionMode) viewModel.setSelectionMode(true)
                                        viewModel.toggleSelected(app.packageName)
                                    },
                                    onToggleFreeze = { app ->
                                        if (!app.isFrozen && confirmBeforeFreeze) {
                                            pendingFreezeConfirm = app
                                        } else {
                                            viewModel.toggleFreeze(app)
                                        }
                                    }
                                )
                            }
                        }

                        MainTab.QUICK_STOP -> {
                            Text(
                                stringResource(R.string.quick_stop_list_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                            )
                            Text(
                                stringResource(R.string.n_in_quick_stop_list, quickStopList.size),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                QuickStopFilterRow(selected = quickStopFilter, onSelect = { quickStopFilter = it })
                                Box {
                                    IconButton(onClick = { showQuickStopSortMenu = true }) {
                                        Icon(Icons.Default.Sort, contentDescription = stringResource(R.string.sort_by))
                                    }
                                    DropdownMenu(expanded = showQuickStopSortMenu, onDismissRequest = { showQuickStopSortMenu = false }) {
                                        val options = listOf(
                                            SortOrder.NAME_ASC to stringResource(R.string.sort_name_az),
                                            SortOrder.NAME_DESC to stringResource(R.string.sort_name_za)
                                        )
                                        options.forEach { (order, label) ->
                                            DropdownMenuItem(
                                                text = { Text(label) },
                                                onClick = { showQuickStopSortMenu = false; quickStopSort = order }
                                            )
                                        }
                                    }
                                }
                            }

                            if (state.isLoading) {
                                LoadingBlock(stringResource(R.string.loading_apps))
                            } else {
                                // Already-frozen apps have no background activity to stop,
                                // so they're excluded from Quick Stop entirely, regardless
                                // of which filter is selected.
                                val filteredForStop = remember(state.apps, quickStopQuery, quickStopFilter, quickStopSort, quickStopList) {
                                    var list = state.apps.filter { !it.isFrozen }
                                    list = when (quickStopFilter) {
                                        QuickStopFilter.USER -> list.filter { !it.isSystemApp }
                                        QuickStopFilter.SYSTEM -> list.filter { it.isSystemApp }
                                        QuickStopFilter.RUNNING -> list.filter { it.isRunning }
                                        QuickStopFilter.SELECTED -> list.filter { it.packageName in quickStopList }
                                    }
                                    if (quickStopQuery.isNotBlank()) {
                                        list = list.filter {
                                            it.label.contains(quickStopQuery, ignoreCase = true) ||
                                                it.packageName.contains(quickStopQuery, ignoreCase = true)
                                        }
                                    }
                                    when (quickStopSort) {
                                        SortOrder.NAME_DESC -> list.sortedByDescending { it.label.lowercase() }
                                        else -> list.sortedBy { it.label.lowercase() }
                                    }
                                }
                                if (filteredForStop.isEmpty()) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text(
                                            if (quickStopQuery.isNotBlank()) stringResource(R.string.no_results_for, quickStopQuery)
                                            else stringResource(R.string.no_apps_found),
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        items(filteredForStop, key = { it.packageName }) { appInfo ->
                                            QuickStopRow(
                                                app = appInfo,
                                                checked = appInfo.packageName in quickStopList,
                                                showRiskDots = showRiskDots,
                                                onToggle = { scope.launch { app.preferencesRepository.toggleQuickStop(appInfo.packageName) } }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        MainTab.SETTINGS -> Unit
                    }
                }
            }
            }
        }
    }

    pendingFreezeConfirm?.let { app ->
        AlertDialog(
            onDismissRequest = { pendingFreezeConfirm = null },
            title = { Text(stringResource(R.string.freeze_confirm_title)) },
            text = { Text(stringResource(R.string.freeze_confirm_body, app.label)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.toggleFreeze(app)
                    pendingFreezeConfirm = null
                }) { Text(stringResource(R.string.freeze_application_short)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingFreezeConfirm = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (confirmForceStopList) {
        AlertDialog(
            onDismissRequest = { confirmForceStopList = false },
            title = { Text(stringResource(R.string.force_stop_list)) },
            text = { Text(stringResource(R.string.force_stop_list_confirm, quickStopList.size)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmForceStopList = false
                    viewModel.forceStopSet(quickStopList)
                }) { Text(stringResource(R.string.force_stop_list)) }
            },
            dismissButton = { TextButton(onClick = { confirmForceStopList = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    state.lastError?.let { error ->
        FreezeErrorDialog(error = error, onDismiss = { viewModel.clearError() })
    }

    state.batchProgress?.let { progress ->
        BatchProgressDialog(progress)
    }

    state.batchResult?.let { (success, failed) ->
        BatchResultDialog(success, failed, onDismiss = { viewModel.clearBatchResult() })
    }
}

@Composable
private fun MainBottomNav(tab: MainTab, onTabSelected: (MainTab) -> Unit) {
    // OneUI-style: a rounded slab anchored to the bottom instead of a flat
    // edge-to-edge Material bar, sized a bit taller than before so icon +
    // label actually have room to breathe.
    Surface(
        tonalElevation = 3.dp,
        shadowElevation = 2.dp,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(72.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CompactNavItem(
                selected = tab == MainTab.FREEZE,
                icon = Icons.Default.AcUnit,
                label = stringResource(R.string.tab_freeze),
                onClick = { onTabSelected(MainTab.FREEZE) }
            )
            CompactNavItem(
                selected = tab == MainTab.QUICK_STOP,
                icon = Icons.Default.PowerSettingsNew,
                label = stringResource(R.string.tab_quick_stop),
                onClick = { onTabSelected(MainTab.QUICK_STOP) }
            )
            CompactNavItem(
                selected = tab == MainTab.SETTINGS,
                icon = Icons.Default.Settings,
                label = stringResource(R.string.settings),
                onClick = { onTabSelected(MainTab.SETTINGS) }
            )
        }
    }
}

@Composable
private fun RowScope.CompactNavItem(
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable(
                onClick = onClick,
                indication = null,
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(23.dp))
        Spacer(Modifier.height(3.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1)
    }
}

@Composable
private fun QuickStopRow(app: AppInfo, checked: Boolean, showRiskDots: Boolean = true, onToggle: () -> Unit) {
    val context = LocalContext.current
    var icon by remember(app.packageName) { mutableStateOf<Drawable?>(null) }
    val appRepo = (context.applicationContext as SamFreezeApp).packageRepository

    LaunchedEffect(app.packageName) {
        icon = appRepo.loadIcon(app.packageName)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onToggle),
        color = if (checked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(icon, riskColor = if (showRiskDots) app.uadInfo?.let { com.samfreeze.app.ui.theme.riskColorFor(it.freezeLevel) } else null)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    app.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Text(
                    app.packageName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
            Checkbox(checked = checked, onCheckedChange = { onToggle() })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SearchBar(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        placeholder = { Text(stringResource(R.string.search_hint)) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        singleLine = true,
        shape = RoundedCornerShape(14.dp)
    )
}

@Composable
private fun FilterRow(selected: AppFilter, onSelect: (AppFilter) -> Unit) {
    val filters = listOf(
        AppFilter.USER to stringResource(R.string.filter_user),
        AppFilter.SYSTEM to stringResource(R.string.filter_system),
        AppFilter.ALL to stringResource(R.string.filter_all),
        AppFilter.FROZEN to stringResource(R.string.filter_frozen)
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        filters.forEach { (filter, label) ->
            FilterChip(
                selected = selected == filter,
                onClick = { onSelect(filter) },
                label = { Text(label) }
            )
        }
    }
}

@Composable
private fun QuickStopFilterRow(selected: QuickStopFilter, onSelect: (QuickStopFilter) -> Unit) {
    val filters = listOf(
        QuickStopFilter.RUNNING to stringResource(R.string.filter_running),
        QuickStopFilter.USER to stringResource(R.string.filter_user),
        QuickStopFilter.SYSTEM to stringResource(R.string.filter_system),
        QuickStopFilter.SELECTED to stringResource(R.string.filter_selected)
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        filters.forEach { (filter, label) ->
            FilterChip(
                selected = selected == filter,
                onClick = { onSelect(filter) },
                label = { Text(label) }
            )
        }
    }
}

@Composable
private fun BottomSelectionBar(count: Int, onFreeze: () -> Unit, onUnfreeze: () -> Unit, onCancel: () -> Unit) {
    Surface(tonalElevation = 3.dp, color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onCancel) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel))
                }
                Text(stringResource(R.string.n_selected, count), style = MaterialTheme.typography.bodyMedium)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onUnfreeze, enabled = count > 0, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) {
                    Text(stringResource(R.string.unfreeze_selected), maxLines = 1)
                }
                Button(onClick = onFreeze, enabled = count > 0, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) {
                    Text(stringResource(R.string.freeze_selected), maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun AppList(
    apps: List<AppInfo>,
    showPackageNames: Boolean,
    showRiskDots: Boolean = true,
    selectionMode: Boolean,
    selectedPackages: Set<String>,
    sortOrder: SortOrder,
    filter: AppFilter,
    onClick: (AppInfo) -> Unit,
    onLongClick: (AppInfo) -> Unit,
    onToggleFreeze: (AppInfo) -> Unit
) {
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    // Jump back to the top whenever sort order or filter changes, so a new
    // sort is immediately visible instead of leaving the scroll position
    // wherever it happened to be — that was making sorting look broken.
    LaunchedEffect(sortOrder, filter) {
        listState.scrollToItem(0)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        items(apps, key = { it.packageName }) { app ->
            AppRow(
                app = app,
                showPackageName = showPackageNames,
                showRiskDots = showRiskDots,
                selectionMode = selectionMode,
                selected = app.packageName in selectedPackages,
                onClick = { onClick(app) },
                onLongClick = { onLongClick(app) },
                onToggleFreeze = { onToggleFreeze(app) }
            )
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun AppRow(
    app: AppInfo,
    showPackageName: Boolean,
    selectionMode: Boolean,
    selected: Boolean,
    showRiskDots: Boolean = true,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleFreeze: () -> Unit
) {
    val context = LocalContext.current
    var icon by remember(app.packageName) { mutableStateOf<Drawable?>(null) }
    val appRepo = (context.applicationContext as SamFreezeApp).packageRepository

    LaunchedEffect(app.packageName) {
        icon = appRepo.loadIcon(app.packageName)
    }

    // Flat One UI-style row: frozen apps get a blue tint across the whole
    // tile so they're easy to spot at a glance; the FROZEN tag itself gets
    // its own dark-tinted chip on top of that so the tag text still pops
    // instead of blending into the blue.
    val tileColor = if (app.state == AppState.FROZEN) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        color = tileColor
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectionMode) {
                Checkbox(checked = selected, onCheckedChange = { onClick() })
                Spacer(Modifier.width(4.dp))
            }

            AppIcon(icon, riskColor = if (showRiskDots) app.uadInfo?.let { com.samfreeze.app.ui.theme.riskColorFor(it.freezeLevel) } else null)
            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        app.label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    if (app.isRunning) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(RoundedCornerShape(50))
                                .background(MaterialTheme.colorScheme.tertiary)
                        )
                    }
                }
                if (showPackageName) {
                    Text(
                        app.packageName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            if (!selectionMode) {
                StatusPill(state = app.state)
                Spacer(Modifier.width(4.dp))
                // Toggle ON = frozen (disabled), toggle OFF = active/unfrozen.
                // Scaled down a touch to free up horizontal room for the
                // package name now that the source label row is gone.
                Switch(
                    checked = app.state == AppState.FROZEN,
                    onCheckedChange = { onToggleFreeze() },
                    modifier = Modifier.scale(0.85f)
                )
            }
        }
    }
}

@Composable
internal fun AppIcon(icon: Drawable?, riskColor: androidx.compose.ui.graphics.Color? = null) {
    Box(modifier = Modifier.size(40.dp)) {
        Box(
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (icon != null) {
                val bitmap = remember(icon) { icon.toBitmap(120, 120) }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
        if (riskColor != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(11.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(riskColor)
                )
            }
        }
    }
}

private fun Drawable.toBitmap(width: Int, height: Int): android.graphics.Bitmap? = try {
    val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    bitmap
} catch (t: Throwable) {
    null
}

@Composable
private fun StatusPill(state: AppState) {
    val (text, containerColor, contentColor) = when (state) {
        AppState.ACTIVE -> Triple(stringResource(R.string.active), MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
        // FROZEN tiles are already tinted primaryContainer (blue), so this tag
        // needs a genuinely dark chip behind it, not another theme container
        // color, or it just blends into the tile instead of popping.
        AppState.FROZEN -> Triple(stringResource(R.string.frozen), MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f), androidx.compose.ui.graphics.Color.White)
        AppState.UNKNOWN -> Triple(stringResource(R.string.unknown_state), MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Surface(shape = RoundedCornerShape(50), color = containerColor) {
        Text(
            text,
            color = contentColor,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun LoadingBlock(text: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun RootUnavailableBlock(onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Lock,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.root_required_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.root_required_body),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRetry) { Text(stringResource(R.string.try_again)) }
    }
}

@Composable
private fun EmptyBlock(query: String, filter: AppFilter) {
    val message = when {
        query.isNotBlank() -> stringResource(R.string.no_results_for, query)
        filter == AppFilter.FROZEN -> stringResource(R.string.no_frozen_apps)
        else -> stringResource(R.string.no_apps_found)
    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FreezeErrorDialog(error: FreezeOutcome, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(error.label) },
        text = { Text(error.message ?: stringResource(R.string.unknown_error)) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } }
    )
}

@Composable
internal fun BatchProgressDialog(progress: BatchProgress) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.processing)) },
        text = {
            Column {
                Text(stringResource(R.string.processing_n_of_n, progress.current, progress.total))
                Spacer(Modifier.height(8.dp))
                Text(progress.label, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { progress.current.toFloat() / progress.total.toFloat() },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {}
    )
}

@Composable
internal fun BatchResultDialog(success: Int, failed: Int, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.batch_complete)) },
        text = {
            Column {
                Text(stringResource(R.string.apps_processed, success + failed))
                Text(stringResource(R.string.n_successful, success))
                Text(stringResource(R.string.n_failed, failed))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) } }
    )
}
