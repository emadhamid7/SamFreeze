package com.samfreeze.app.ui

import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
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
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
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
                AppBackdrop {
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

@OptIn(
    ExperimentalMaterial3Api::class,
    androidx.compose.material.ExperimentalMaterialApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)
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
    var tab by remember { mutableStateOf(MainTab.FREEZE) }
    var quickStopQuery by remember { mutableStateOf("") }
    var quickStopFilter by remember { mutableStateOf(QuickStopFilter.USER) }
    var quickStopRefreshing by remember { mutableStateOf(false) }
    val quickStopPullState = rememberPullRefreshState(
        refreshing = quickStopRefreshing,
        onRefresh = {
            scope.launch {
                quickStopRefreshing = true
                viewModel.refreshRunning()
                quickStopRefreshing = false
            }
        }
    )
    var confirmForceStopList by remember { mutableStateOf(false) }

    Scaffold(
        // Header rows apply statusBarsPadding() themselves and the floating bar
        // applies navigationBarsPadding(), so Scaffold adds no insets. The bottom
        // bar + FAB are overlaid (not Scaffold slots) so lists scroll beneath them.
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    // Slide direction follows the bottom nav order (Freeze →
                    // Quick Stop → Settings) so switching tabs reads like
                    // moving between panels, not just a cross-fade.
                    val forward = targetState.ordinal > initialState.ordinal
                    val slideDistance = { fullWidth: Int -> fullWidth / 4 }
                    if (forward) {
                        (slideInHorizontally(tween(220)) { slideDistance(it) } + fadeIn(tween(220))) togetherWith
                            (slideOutHorizontally(tween(180)) { -slideDistance(it) } + fadeOut(tween(140)))
                    } else {
                        (slideInHorizontally(tween(220)) { -slideDistance(it) } + fadeIn(tween(220))) togetherWith
                            (slideOutHorizontally(tween(180)) { slideDistance(it) } + fadeOut(tween(140)))
                    }
                },
                label = "tab_content",
                modifier = Modifier.fillMaxSize()
            ) { currentTab ->
            // AnimatedContent lays its content out in a Box internally, so
            // everything below needs its own Column to keep the header
            // (search bar / filters) stacked above the list like before —
            // without this they'd overlap instead of stacking vertically.
            Column(modifier = Modifier.fillMaxSize()) {
            if (currentTab == MainTab.SETTINGS) {
                // Settings doesn't need root to be useful (theme, backups,
                // community links all work without it), so it isn't gated
                // behind the root check the way Freeze/Quick Stop are.
                SettingsTabContent(viewModel = viewModel)
            } else {
            when (state.rootState) {
                RootState.CHECKING -> LoadingBlock(stringResource(R.string.checking_root))
                RootState.UNAVAILABLE -> RootUnavailableBlock(onRetry = { viewModel.checkRoot() })
                RootState.AVAILABLE -> {
                    CollapsingHeaderLayout(
                        modifier = Modifier.fillMaxSize(),
                        header = {
                            // Search bar up top; Freeze Levels sits right next to it,
                            // only on the Freeze tab (Quick Stop has nothing to level).
                            Row(
                                modifier = Modifier.fillMaxWidth().statusBarsPadding(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                SearchBar(
                                    query = if (currentTab == MainTab.FREEZE) state.query else quickStopQuery,
                                    onQueryChange = { if (currentTab == MainTab.FREEZE) viewModel.setQuery(it) else quickStopQuery = it },
                                    modifier = Modifier.weight(1f)
                                )
                                if (currentTab == MainTab.FREEZE) {
                                    IconButton(onClick = onOpenLevels) {
                                        Icon(Icons.Default.Layers, contentDescription = stringResource(R.string.freeze_levels))
                                    }
                                } else {
                                    Spacer(Modifier.width(8.dp))
                                }
                            }

                            when (currentTab) {
                                MainTab.FREEZE -> FilterPills(
                                    options = listOf(
                                        AppFilter.USER to stringResource(R.string.filter_user),
                                        AppFilter.SYSTEM to stringResource(R.string.filter_system),
                                        AppFilter.ALL to stringResource(R.string.filter_all),
                                        AppFilter.FROZEN to stringResource(R.string.filter_frozen)
                                    ),
                                    selected = state.filter,
                                    onSelect = viewModel::setFilter
                                )
                                MainTab.QUICK_STOP -> {
                                    Text(
                                        stringResource(R.string.quick_stop_list_desc),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)
                                    )
                                    SectionCaption(
                                        stringResource(R.string.n_in_quick_stop_list, quickStopList.size),
                                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 2.dp)
                                    )
                                    FilterPills(
                                        options = listOf(
                                            QuickStopFilter.RUNNING to stringResource(R.string.filter_running),
                                            QuickStopFilter.USER to stringResource(R.string.filter_user),
                                            QuickStopFilter.SYSTEM to stringResource(R.string.filter_system),
                                            QuickStopFilter.SELECTED to stringResource(R.string.filter_selected)
                                        ),
                                        selected = quickStopFilter,
                                        onSelect = { quickStopFilter = it }
                                    )
                                }
                                MainTab.SETTINGS -> Unit
                            }
                        }
                    ) { headerState, headerHeight ->
                        when (currentTab) {
                            MainTab.FREEZE -> {
                            when {
                                state.isLoading -> Box(Modifier.fillMaxSize().padding(top = headerHeight)) {
                                    LaunchedEffect(Unit) { headerState.offsetPx = 0f }
                                    LoadingBlock(stringResource(R.string.loading_apps))
                                }
                                state.visibleApps.isEmpty() -> Box(Modifier.fillMaxSize().padding(top = headerHeight)) {
                                    LaunchedEffect(Unit) { headerState.offsetPx = 0f }
                                    EmptyBlock(state.query, state.filter)
                                }
                                else -> AppList(
                                    apps = state.visibleApps,
                                    showPackageNames = showPackageNames,
                                    showRiskDots = showRiskDots,
                                    selectionMode = state.selectionMode,
                                    selectedPackages = state.selectedPackages,
                                    sortOrder = state.sortOrder,
                                    filter = state.filter,
                                    headerState = headerState,
                                    headerHeight = headerHeight,
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
                            if (state.isLoading) {
                                Box(Modifier.fillMaxSize().padding(top = headerHeight)) {
                                    LaunchedEffect(Unit) { headerState.offsetPx = 0f }
                                    LoadingBlock(stringResource(R.string.loading_apps))
                                }
                            } else {
                                // Already-frozen apps have no background activity to stop,
                                // so they're excluded from Quick Stop entirely, regardless
                                // of which filter is selected.
                                val filteredForStop = remember(state.apps, quickStopQuery, quickStopFilter, quickStopList) {
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
                                    list.sortedBy { it.label.lowercase() }
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .pullRefresh(quickStopPullState)
                                ) {
                                    if (filteredForStop.isEmpty()) {
                                        LaunchedEffect(Unit) { headerState.offsetPx = 0f }
                                        Box(modifier = Modifier.fillMaxSize().padding(top = headerHeight), contentAlignment = Alignment.Center) {
                                            Text(
                                                if (quickStopQuery.isNotBlank()) stringResource(R.string.no_results_for, quickStopQuery)
                                                else stringResource(R.string.no_apps_found),
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    } else {
                                        val qsListState = androidx.compose.foundation.lazy.rememberLazyListState()
                                        SyncHeaderWithList(headerState, qsListState)
                                        LazyColumn(
                                            state = qsListState,
                                            modifier = Modifier.fillMaxSize(),
                                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = headerHeight + 4.dp, bottom = FloatingBarClearance + 64.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            items(filteredForStop, key = { it.packageName }) { appInfo ->
                                                QuickStopRow(
                                                    app = appInfo,
                                                    checked = appInfo.packageName in quickStopList,
                                                    showRiskDots = showRiskDots,
                                                    onToggle = { scope.launch { app.preferencesRepository.toggleQuickStop(appInfo.packageName) } },
                                                    modifier = Modifier.animateItemPlacement(tween(220))
                                                )
                                            }
                                        }
                                    }
                                    PullRefreshIndicator(
                                        refreshing = quickStopRefreshing,
                                        state = quickStopPullState,
                                        modifier = Modifier.align(Alignment.TopCenter).padding(top = headerHeight),
                                        backgroundColor = MaterialTheme.colorScheme.surface,
                                        contentColor = MaterialTheme.colorScheme.primary
                                    )
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
            }
        }
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            horizontalAlignment = Alignment.End
        ) {
            if (tab == MainTab.QUICK_STOP && !state.selectionMode) {
                ExtendedFloatingActionButton(
                    onClick = { confirmForceStopList = true },
                    icon = { Icon(Icons.Default.PowerSettingsNew, contentDescription = null) },
                    text = { Text(stringResource(R.string.force_stop_list)) },
                    modifier = Modifier.padding(end = 16.dp, bottom = 4.dp),
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            AnimatedContent(
                targetState = state.selectionMode && tab == MainTab.FREEZE,
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                label = "bottom_bar"
            ) { showSelectionBar ->
                if (showSelectionBar) {
                    BottomSelectionBar(
                        count = state.selectedPackages.size,
                        onFreeze = { viewModel.freezeSelected() },
                        onUnfreeze = { viewModel.unfreezeSelected() },
                        onCancel = { viewModel.clearSelection() }
                    )
                } else {
                    MainBottomNav(tab = tab, onTabSelected = { tab = it })
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

/** Floating, fully rounded nav pill; the active tab gets a tinted capsule behind icon + label. */
@Composable
private fun MainBottomNav(tab: MainTab, onTabSelected: (MainTab) -> Unit) {
    FloatingBarContainer {
        Row(
            modifier = Modifier.fillMaxWidth().padding(5.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavPillItem(
                selected = tab == MainTab.FREEZE,
                icon = Icons.Default.AcUnit,
                label = stringResource(R.string.tab_freeze),
                onClick = { onTabSelected(MainTab.FREEZE) }
            )
            NavPillItem(
                selected = tab == MainTab.QUICK_STOP,
                icon = Icons.Default.PowerSettingsNew,
                label = stringResource(R.string.tab_quick_stop),
                onClick = { onTabSelected(MainTab.QUICK_STOP) }
            )
            NavPillItem(
                selected = tab == MainTab.SETTINGS,
                icon = Icons.Default.Settings,
                label = stringResource(R.string.settings),
                onClick = { onTabSelected(MainTab.SETTINGS) }
            )
        }
    }
}

/**
 * Shared floating "glass" container for the bottom nav and selection bar:
 * translucent gradient fill + hairline highlight edge, floating with margins.
 * (Fake glass — content isn't blurred, only see-through.)
 */
@Composable
private fun FloatingBarContainer(content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.5f
    val shape = RoundedCornerShape(32.dp)
    val edgeTop = if (dark) Color.White.copy(alpha = 0.20f) else Color.Black.copy(alpha = 0.10f)
    val edgeBottom = if (dark) Color.White.copy(alpha = 0.04f) else Color.Black.copy(alpha = 0.03f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 28.dp, vertical = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(
                    Brush.verticalGradient(
                        listOf(scheme.surface.copy(alpha = 0.80f), scheme.surface.copy(alpha = 0.60f))
                    )
                )
                .border(1.dp, Brush.verticalGradient(listOf(edgeTop, edgeBottom)), shape)
        ) {
            content()
        }
    }
}

@Composable
private fun RowScope.NavPillItem(
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    val color by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(180),
        label = "nav_item_color"
    )
    val capsule by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.Transparent,
        animationSpec = tween(180),
        label = "nav_item_capsule"
    )
    Column(
        modifier = Modifier
            .weight(1f)
            .height(48.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(capsule)
            .clickable(
                onClick = onClick,
                indication = null,
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(1.dp))
        Text(
            label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = color,
            maxLines = 1
        )
    }
}

@Composable
private fun QuickStopRow(
    app: AppInfo,
    checked: Boolean,
    showRiskDots: Boolean = true,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var icon by remember(app.packageName) { mutableStateOf<Drawable?>(null) }
    val appRepo = (context.applicationContext as SamFreezeApp).packageRepository

    LaunchedEffect(app.packageName) {
        icon = appRepo.loadIcon(app.packageName)
    }

    val rowColor by animateColorAsState(
        targetValue = if (checked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        animationSpec = tween(220),
        label = "quick_stop_row_color"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onToggle),
        color = rowColor
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

/** One UI-style search pill: filled, fully rounded, muted bold "Search" hint, no mic. */
@Composable
internal fun SearchBar(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val fill = if (isDark) Color(0xFF232323) else Color(0xFFEDEDEF)
    val muted = if (isDark) Color(0xFF9B9BA0) else Color(0xFF77777C)
    val focusManager = LocalFocusManager.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(48.dp)
            .clip(CircleShape)
            .background(fill)
            .padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Search, contentDescription = null, tint = muted, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                Text(
                    stringResource(R.string.search_hint),
                    color = muted,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 17.sp),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (query.isNotEmpty()) {
            IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, contentDescription = null, tint = muted, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun BottomSelectionBar(count: Int, onFreeze: () -> Unit, onUnfreeze: () -> Unit, onCancel: () -> Unit) {
    FloatingBarContainer {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
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
                OutlinedButton(onClick = onUnfreeze, enabled = count > 0, shape = CircleShape, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) {
                    Text(stringResource(R.string.unfreeze_selected), maxLines = 1)
                }
                Button(onClick = onFreeze, enabled = count > 0, shape = CircleShape, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) {
                    Text(stringResource(R.string.freeze_selected), maxLines = 1)
                }
            }
        }
    }
}

@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun AppList(
    apps: List<AppInfo>,
    showPackageNames: Boolean,
    showRiskDots: Boolean = true,
    selectionMode: Boolean,
    selectedPackages: Set<String>,
    sortOrder: SortOrder,
    filter: AppFilter,
    headerState: CollapsingHeaderState,
    headerHeight: androidx.compose.ui.unit.Dp,
    onClick: (AppInfo) -> Unit,
    onLongClick: (AppInfo) -> Unit,
    onToggleFreeze: (AppInfo) -> Unit
) {
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    SyncHeaderWithList(headerState, listState)

    // Jump back to the top whenever sort order or filter changes, so a new
    // sort is immediately visible instead of leaving the scroll position
    // wherever it happened to be — that was making sorting look broken.
    LaunchedEffect(sortOrder, filter) {
        listState.scrollToItem(0)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = headerHeight + 4.dp, bottom = FloatingBarClearance + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
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
                onToggleFreeze = { onToggleFreeze(app) },
                // Smoothly reflows rows into their new spot when the list is
                // filtered, sorted, or searched, instead of a hard jump-cut.
                modifier = Modifier.animateItemPlacement(tween(220))
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
    onToggleFreeze: () -> Unit,
    modifier: Modifier = Modifier
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
    // instead of blending into the blue. Animated so freezing/unfreezing
    // eases into the new color instead of snapping.
    val tileColor by animateColorAsState(
        targetValue = if (app.state == AppState.FROZEN) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        animationSpec = tween(220),
        label = "tile_color"
    )
    Surface(
        modifier = modifier
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
                SamsungSwitch(
                    checked = app.state == AppState.FROZEN,
                    onCheckedChange = { onToggleFreeze() }
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
