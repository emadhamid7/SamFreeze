package com.samfreeze.app.ui

import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
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
import kotlinx.coroutines.launch

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

@Composable
private fun levelTabLabel(level: FreezeLevel): String = when (level) {
    FreezeLevel.RECOMMENDED -> stringResource(R.string.level_recommended)
    FreezeLevel.ADVANCED -> stringResource(R.string.level_advanced)
    FreezeLevel.EXPERT -> stringResource(R.string.level_expert)
    FreezeLevel.UNSAFE -> stringResource(R.string.level_unsafe)
}

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
    var descriptionApp by remember { mutableStateOf<AppInfo?>(null) }
    var uadDownloadInProgress by remember { mutableStateOf(false) }
    var uadDownloadResult by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val selectedTab = tabs[selectedTabIndex]
    val appsForLevel = remember(state.apps, selectedTab.level) { viewModel.appsForLevel(selectedTab.level) }
    val visible = appsForLevel

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.freeze_levels), style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(
                        enabled = !uadDownloadInProgress,
                        onClick = {
                            uadDownloadInProgress = true
                            uadDownloadResult = null
                            scope.launch {
                                val result = app.uadListRepository.downloadLatest()
                                uadDownloadInProgress = false
                                uadDownloadResult = result.fold(
                                    onSuccess = { count -> context.getString(R.string.debloat_list_update_success, count) },
                                    onFailure = { context.getString(R.string.debloat_list_update_failed) }
                                )
                                if (result.isSuccess) viewModel.loadApps()
                                android.widget.Toast.makeText(context, uadDownloadResult, android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        if (uadDownloadInProgress) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.update_debloat_list))
                        }
                    }
                },
                windowInsets = WindowInsets(0, 0, 0, 0),
                modifier = Modifier.height(52.dp)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                tabs.forEachIndexed { index, tab ->
                    FilterChip(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        label = { Text(tab.title) },
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(riskColorFor(tab.level))
                            )
                        }
                    )
                }
            }

            Text(
                selectedTab.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
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
                        stringResource(R.string.level_no_matches),
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(riskColorFor(info.freezeLevel))
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                levelTabLabel(info.freezeLevel),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.level_source, info.list),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                        if (info.description.isNotBlank()) {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                stringResource(R.string.level_description_title),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(info.description)
                        }
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
