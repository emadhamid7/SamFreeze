package com.samfreeze.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.CreationExtras
import com.samfreeze.app.R
import com.samfreeze.app.SamFreezeApp
import com.samfreeze.app.data.PackageRepository
import com.samfreeze.app.model.AppState
import com.samfreeze.app.ui.theme.SamFreezeTheme
import kotlinx.coroutines.launch

class AppDetailsActivity : ComponentActivity() {

    companion object {
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
    }

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
        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: run { finish(); return }

        setContent {
            val appCtx = application as SamFreezeApp
            val appTheme = com.samfreeze.app.ui.theme.rememberAppTheme(appCtx.preferencesRepository)
            SamFreezeTheme(appTheme = appTheme) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    AppDetailsScreen(
                        packageName = packageName,
                        viewModel = viewModel,
                        onBack = { finish() }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailsScreen(packageName: String, viewModel: MainViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val appCtx = context.applicationContext as SamFreezeApp
    val repo = appCtx.packageRepository
    val advancedUninstallEnabled by appCtx.preferencesRepository.advancedUninstallEnabled.collectAsStateWithLifecycle(initialValue = false)

    val app = state.apps.firstOrNull { it.packageName == packageName }

    // This screen runs its own MainViewModel instance (not shared with the
    // list screen), so app.sizeBytes here is always null unless we fetch it
    // ourselves — otherwise the Storage size row never appears at all.
    var localSizeBytes by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(packageName) {
        if (app?.sizeBytes == null) {
            val kb = appCtx.statsRepository.dataSizesKb(listOf(packageName))[packageName]
            if (kb != null) localSizeBytes = kb * 1024
        }
    }

    var confirmForceStop by remember { mutableStateOf(false) }
    var confirmUninstall by remember { mutableStateOf(false) }
    var confirmClearCache by remember { mutableStateOf(false) }
    var clearCacheResult by remember { mutableStateOf<String?>(null) }
    var uninstallResult by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(app?.label ?: packageName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (app != null) {
                        IconButton(onClick = { viewModel.toggleFavorite(app) }) {
                            Icon(
                                if (app.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = stringResource(R.string.favorite)
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (app == null) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.no_apps_found))
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            Text(app.label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(app.packageName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            app.uadInfo?.let { info ->
                Spacer(Modifier.height(12.dp))
                Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .background(
                                        com.samfreeze.app.ui.theme.riskColorFor(info.freezeLevel),
                                        androidx.compose.foundation.shape.RoundedCornerShape(50)
                                    )
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                levelLabel(info.freezeLevel),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.level_source, info.list),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (info.description.isNotBlank()) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                stringResource(R.string.level_description_title),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(info.description, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            DetailRow(stringResource(R.string.status), if (app.state == AppState.FROZEN) stringResource(R.string.frozen) else stringResource(R.string.active))
            DetailRow(stringResource(R.string.type), if (app.isSystemApp) stringResource(R.string.system_application) else stringResource(R.string.user_application))
            DetailRow(stringResource(R.string.version), app.versionName ?: "N/A")
            DetailRow(stringResource(R.string.uid), app.uid.toString())
            DetailRow(stringResource(R.string.currently_running), if (app.isRunning) stringResource(R.string.yes) else stringResource(R.string.no))
            (app.sizeBytes ?: localSizeBytes)?.let { DetailRow(stringResource(R.string.storage_size), formatBytes(it)) }
            app.apkPath?.let { path ->
                Spacer(Modifier.height(6.dp))
                ApkPathRow(path)
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = { viewModel.toggleFreeze(app) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (app.state == AppState.FROZEN) stringResource(R.string.unfreeze_application) else stringResource(R.string.freeze_application))
            }

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = {
                    val launch = repo.launchIntent(app.packageName)
                    if (launch != null) context.startActivity(launch)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = app.state == AppState.ACTIVE && repo.canLaunch(app.packageName)
            ) {
                Text(stringResource(R.string.open_application))
            }

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = {
                    val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:${app.packageName}")
                    }
                    context.startActivity(i)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.app_info))
            }

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = { confirmForceStop = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text(stringResource(R.string.force_stop))
            }
            Text(
                stringResource(R.string.force_stop_disclaimer),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = { confirmClearCache = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.clear_cache))
            }
            clearCacheResult?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }

            if (advancedUninstallEnabled) {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = { confirmUninstall = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.uninstall_soft))
                }
                Text(
                    stringResource(R.string.uninstall_soft_disclaimer),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
                uninstallResult?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }

    if (confirmForceStop) {
        AlertDialog(
            onDismissRequest = { confirmForceStop = false },
            title = { Text(stringResource(R.string.force_stop)) },
            text = { Text(stringResource(R.string.force_stop_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmForceStop = false
                    app?.let { a -> scope.launch { repo.forceStop(a.packageName) } }
                }) { Text(stringResource(R.string.force_stop)) }
            },
            dismissButton = { TextButton(onClick = { confirmForceStop = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    if (confirmClearCache) {
        AlertDialog(
            onDismissRequest = { confirmClearCache = false },
            title = { Text(stringResource(R.string.clear_cache)) },
            text = { Text(stringResource(R.string.clear_cache_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClearCache = false
                    app?.let { a ->
                        scope.launch {
                            val ok = repo.clearCache(a.packageName)
                            clearCacheResult = if (ok) context.getString(R.string.cache_cleared) else context.getString(R.string.cache_clear_failed)
                        }
                    }
                }) { Text(stringResource(R.string.clear_cache)) }
            },
            dismissButton = { TextButton(onClick = { confirmClearCache = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    if (confirmUninstall) {
        AlertDialog(
            onDismissRequest = { confirmUninstall = false },
            title = { Text(stringResource(R.string.uninstall_soft)) },
            text = { Text(stringResource(R.string.uninstall_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmUninstall = false
                    app?.let { a ->
                        scope.launch {
                            when (val result = repo.uninstallForUser(a.packageName)) {
                                is PackageRepository.OpResult.Success -> onBack()
                                is PackageRepository.OpResult.Failure -> uninstallResult = result.message
                            }
                        }
                    }
                }) { Text(stringResource(R.string.uninstall_soft)) }
            },
            dismissButton = { TextButton(onClick = { confirmUninstall = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

@Composable
private fun levelLabel(level: com.samfreeze.app.model.FreezeLevel): String = when (level) {
    com.samfreeze.app.model.FreezeLevel.RECOMMENDED -> stringResource(R.string.level_recommended)
    com.samfreeze.app.model.FreezeLevel.ADVANCED -> stringResource(R.string.level_advanced)
    com.samfreeze.app.model.FreezeLevel.EXPERT -> stringResource(R.string.level_expert)
    com.samfreeze.app.model.FreezeLevel.UNSAFE -> stringResource(R.string.level_unsafe)
}

internal fun formatBytes(bytes: Long): String {
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.0f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    return "%.2f GB".format(mb / 1024.0)
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Medium)
    }
}

/** Apk path gets its own row (label above value) since paths are long and
 *  would collide with the label in the usual side-by-side DetailRow. */
@Composable
private fun ApkPathRow(path: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                val clipboard = context.getSystemService(ClipboardManager::class.java)
                clipboard?.setPrimaryClip(ClipData.newPlainText("apk_path", path))
            }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.apk_path),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    path,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Default.ContentCopy,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
