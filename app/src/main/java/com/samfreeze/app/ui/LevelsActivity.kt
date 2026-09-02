package com.samfreeze.app.ui

import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.CreationExtras
import com.samfreeze.app.R
import com.samfreeze.app.SamFreezeApp
import com.samfreeze.app.model.AppInfo
import com.samfreeze.app.model.AppState
import com.samfreeze.app.model.FreezeLevel
import com.samfreeze.app.ui.theme.SamFreezeTheme
import com.samfreeze.app.ui.theme.riskColorFor

class LevelsActivity : ComponentActivity() {

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
            val appCtx = application as SamFreezeApp
            val appTheme = com.samfreeze.app.ui.theme.rememberAppTheme(appCtx.preferencesRepository)
            SamFreezeTheme(appTheme = appTheme) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    LevelsScreen(viewModel = viewModel, onBack = { finish() })
                }
            }
        }
    }
}

private data class LevelTabInfo(
    val level: FreezeLevel,
    val title: String,
    val description: String
)

/**
 * Freeze Levels screen: each tab is one of UAD-ng's removal-safety
 * categories. The apps shown are computed on the fly by cross-referencing
 * the device's actually-installed packages against the bundled UAD-ng
 * list — nothing is bulk-applied; every app gets its own toggle, exactly
 * like the main Freeze list, so you can freeze all of them or just one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LevelsScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val app = context.applicationContext as SamFreezeApp
    val showRiskDots by app.preferencesRepository.showRiskDots.collectAsStateWithLifecycle(initialValue = true)

    val tabs = listOf(
        LevelTabInfo(FreezeLevel.RECOMMENDED, stringResource(R.string.level_recommended), stringResource(R.string.level_recommended_desc)),
        LevelTabInfo(FreezeLevel.ADVANCED, stringResource(R.string.level_advanced), stringResource(R.string.level_advanced_desc)),
        LevelTabInfo(FreezeLevel.EXPERT, stringResource(R.string.level_expert), stringResource(R.string.level_expert_desc)),
        LevelTabInfo(FreezeLevel.UNSAFE, stringResource(R.string.level_unsafe), stringResource(R.string.level_unsafe_desc))
    )
    var selectedTabIndex by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    var descriptionApp by remember { mutableStateOf<AppInfo?>(null) }

    val selectedTab = tabs[selectedTabIndex]
    val appsForLevel = remember(state.apps, selectedTab.level) { viewModel.appsForLevel(selectedTab.level) }
    val visible = if (query.isBlank()) appsForLevel else appsForLevel.filter {
        it.label.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.freeze_levels)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            ScrollableTabRow(selectedTabIndex = selectedTabIndex, edgePadding = 12.dp) {
                tabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(riskColorFor(tab.level))
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(tab.title)
                            }
                        }
                    )
                }
            }

            Text(
                selectedTab.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.search_hint)) }
            )

            Text(
                stringResource(R.string.level_apps_matched, appsForLevel.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            if (visible.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (query.isBlank()) stringResource(R.string.level_no_matches)
                        else stringResource(R.string.level_no_matches_search, query),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(32.dp)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(visible, key = { it.packageName }) { appInfo ->
                        LevelAppRow(
                            app = appInfo,
                            level = selectedTab.level,
                            showRiskDot = showRiskDots,
                            onInfoClick = { descriptionApp = appInfo },
                            onToggleFreeze = { viewModel.setFrozen(appInfo.packageName, it) }
                        )
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }

    descriptionApp?.let { appInfo ->
        val info = appInfo.uadInfo
        AlertDialog(
            onDismissRequest = { descriptionApp = null },
            title = { Text(appInfo.label) },
            text = {
                Column {
                    if (info != null) {
                        Text(
                            stringResource(R.string.level_source, info.list),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(info.description.ifBlank { stringResource(R.string.level_description_title) })
                    }
                }
            },
            confirmButton = { TextButton(onClick = { descriptionApp = null }) { Text(stringResource(R.string.ok)) } }
        )
    }

    state.batchProgress?.let { progress ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.processing)) },
            text = {
                Column {
                    Text(stringResource(R.string.processing_n_of_n, progress.current, progress.total))
                    Spacer(Modifier.height(8.dp))
                    Text(progress.label, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {}
        )
    }

    state.batchResult?.let { (success, failed) ->
        AlertDialog(
            onDismissRequest = { viewModel.clearBatchResult() },
            title = { Text(stringResource(R.string.batch_complete)) },
            text = {
                Column {
                    Text(stringResource(R.string.apps_processed, success + failed))
                    Text(stringResource(R.string.n_successful, success))
                    Text(stringResource(R.string.n_failed, failed))
                }
            },
            confirmButton = { TextButton(onClick = { viewModel.clearBatchResult() }) { Text(stringResource(R.string.ok)) } }
        )
    }
}

@Composable
private fun LevelAppRow(
    app: AppInfo,
    level: FreezeLevel,
    showRiskDot: Boolean,
    onInfoClick: () -> Unit,
    onToggleFreeze: (Boolean) -> Unit
) {
    val context = LocalContext.current
    var icon by remember(app.packageName) { mutableStateOf<Drawable?>(null) }
    val appRepo = (context.applicationContext as SamFreezeApp).packageRepository

    LaunchedEffect(app.packageName) {
        icon = appRepo.loadIcon(app.packageName)
    }

    val tileColor = if (app.state == AppState.FROZEN) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }

    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onInfoClick),
        color = tileColor
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(icon, riskColor = if (showRiskDot) riskColorFor(level) else null)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    app.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    app.uadInfo?.description?.lineSequence()?.firstOrNull().orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = app.state == AppState.FROZEN,
                onCheckedChange = onToggleFreeze
            )
        }
    }
}
