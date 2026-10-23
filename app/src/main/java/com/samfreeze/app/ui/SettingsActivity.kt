package com.samfreeze.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.samfreeze.app.R
import com.samfreeze.app.SamFreezeApp
import com.samfreeze.app.model.AppState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private const val REPO_URL = "https://github.com/emadhamid7/SamFreeze"
private const val TELEGRAM_URL = "https://t.me/Samfreeze"
private const val GITHUB_PROFILE_URL = "https://github.com/emadhamid7"
private const val UAD_REPO_URL = "https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation"

// Shared layout constants so every card/row in Settings has identical
// margins, corner radius, and height — pure visual rhythm, no behavior here.
private val SettingsHorizontalMargin = 12.dp
private val SettingsCardCorner = 24.dp
private val SettingsRowMinHeight = 56.dp

/**
 * Settings now lives as a tab in MainScreen (same as Freeze / Quick Stop)
 * instead of its own Activity, so the bottom nav stays visible and the
 * user never has to "back out" of it — see MainActivity's MainTab.SETTINGS.
 */
@Composable
fun SettingsTabContent(viewModel: MainViewModel) {
    val context = LocalContext.current
    val app = context.applicationContext as SamFreezeApp
    val prefs = app.preferencesRepository
    val scope = rememberCoroutineScope()
    val state by viewModel.state.collectAsStateWithLifecycle()

    val theme by prefs.theme.collectAsStateWithLifecycle(initialValue = "system")
    val showPackageNames by prefs.showPackageNames.collectAsStateWithLifecycle(initialValue = true)
    val showRiskDots by prefs.showRiskDots.collectAsStateWithLifecycle(initialValue = true)
    val confirmBeforeFreeze by prefs.confirmBeforeFreeze.collectAsStateWithLifecycle(initialValue = false)
    val refreshOnResume by prefs.refreshOnResume.collectAsStateWithLifecycle(initialValue = true)
    val advancedUninstallEnabled by prefs.advancedUninstallEnabled.collectAsStateWithLifecycle(initialValue = false)
    val systemDark = isSystemInDarkTheme()

    var importStatus by remember { mutableStateOf<String?>(null) }
    var confirmUnfreezeAll by remember { mutableStateOf(false) }
    var showCreditsDialog by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            scope.launch {
                val frozen = viewModel.state.value.apps
                    .filter { it.state == AppState.FROZEN }
                    .map { it.packageName }
                    .toSet()
                val quickStop = prefs.quickStopList.first()
                val json = buildBackupJson(frozen, quickStop)
                val ok = try {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(json.toByteArray()) } != null
                    }
                } catch (t: Throwable) { false }
                android.widget.Toast.makeText(
                    context,
                    if (ok) context.getString(R.string.backup_saved, frozen.size, quickStop.size)
                    else context.getString(R.string.backup_write_failed),
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val text = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    }
                    if (text != null) {
                        val applied = applyBackupJson(text, viewModel, prefs)
                        importStatus = if (applied < 0) context.getString(R.string.import_failed)
                                       else context.getString(R.string.import_success, applied)
                    }
                } catch (t: Throwable) {
                    importStatus = context.getString(R.string.import_failed)
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Samsung-style title: compact, bold, aligned with the row text.
        Text(
            stringResource(R.string.settings),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = SettingsHorizontalMargin + 16.dp, end = 28.dp, top = 16.dp, bottom = 8.dp)
        )

        LazyColumn(modifier = Modifier.fillMaxSize()) {

            item { SectionHeader(stringResource(R.string.appearance)) }
            item {
                SettingsGroup {
                    val effectiveDark = when (theme) { "dark" -> true; "light" -> false; else -> systemDark }
                    SamsungThemeSelector(
                        isDark = effectiveDark,
                        onSelect = { dark -> scope.launch { prefs.setTheme(if (dark) "dark" else "light") } },
                        lightLabel = stringResource(R.string.theme_light),
                        darkLabel = stringResource(R.string.theme_dark)
                    )
                    GroupDivider()
                    SwitchRow(
                        title = stringResource(R.string.match_device_theme),
                        checked = theme == "system",
                        onCheckedChange = { on ->
                            scope.launch { prefs.setTheme(if (on) "system" else if (systemDark) "dark" else "light") }
                        }
                    )
                }
            }

            item { SectionHeader(stringResource(R.string.app_display_and_behavior)) }
            item {
                SettingsGroup {
                    SwitchRow(
                        title = stringResource(R.string.show_package_names),
                        checked = showPackageNames,
                        onCheckedChange = { scope.launch { prefs.setShowPackageNames(it) } }
                    )
                    GroupDivider()
                    SwitchRow(
                        title = stringResource(R.string.show_risk_dots),
                        checked = showRiskDots,
                        onCheckedChange = { scope.launch { prefs.setShowRiskDots(it) } }
                    )
                    GroupDivider()
                    SwitchRow(
                        title = stringResource(R.string.confirm_before_freeze),
                        checked = confirmBeforeFreeze,
                        onCheckedChange = { scope.launch { prefs.setConfirmBeforeFreeze(it) } }
                    )
                    GroupDivider()
                    SwitchRow(
                        title = stringResource(R.string.refresh_on_resume),
                        checked = refreshOnResume,
                        onCheckedChange = { scope.launch { prefs.setRefreshOnResume(it) } }
                    )
                }
            }

            item { SectionHeader(stringResource(R.string.advanced_and_bulk)) }
            item {
                SettingsGroup {
                    SwitchRow(
                        title = stringResource(R.string.enable_uninstall_option),
                        checked = advancedUninstallEnabled,
                        onCheckedChange = { scope.launch { prefs.setAdvancedUninstallEnabled(it) } }
                    )
                    GroupDivider()
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Button(
                            onClick = { confirmUnfreezeAll = true },
                            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        ) {
                            Text(stringResource(R.string.unfreeze_all))
                        }
                    }
                }
            }

            item { SectionHeader(stringResource(R.string.backup_settings)) }
            item {
                SettingsGroup {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.backup_export)) },
                        supportingContent = { Text(stringResource(R.string.backup_export_desc)) },
                        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                        modifier = Modifier
                            .defaultMinSize(minHeight = SettingsRowMinHeight)
                            .clickable {
                                if (state.isLoading || state.rootState != RootState.AVAILABLE) {
                                    android.widget.Toast.makeText(context, context.getString(R.string.backup_not_ready), android.widget.Toast.LENGTH_SHORT).show()
                                } else exportLauncher.launch("samfreeze-backup.json")
                            }
                    )
                    GroupDivider()
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.backup_import)) },
                        supportingContent = { Text(importStatus ?: stringResource(R.string.backup_import_desc)) },
                        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                        modifier = Modifier
                            .defaultMinSize(minHeight = SettingsRowMinHeight)
                            .clickable {
                                if (state.isLoading || state.rootState != RootState.AVAILABLE) {
                                    android.widget.Toast.makeText(context, context.getString(R.string.backup_not_ready), android.widget.Toast.LENGTH_SHORT).show()
                                } else importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                            }
                    )
                }
            }

            item { SectionHeader(stringResource(R.string.credits)) }
            item {
                SettingsGroup {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.github_repo)) },
                        leadingContent = {
                            Icon(
                                painter = painterResource(R.drawable.ic_github),
                                contentDescription = null
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                        modifier = Modifier
                            .defaultMinSize(minHeight = SettingsRowMinHeight)
                            .clickable {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(REPO_URL)))
                            }
                    )
                    GroupDivider()
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.telegram_channel)) },
                        leadingContent = {
                            Icon(
                                painter = painterResource(R.drawable.ic_telegram),
                                contentDescription = null
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                        modifier = Modifier
                            .defaultMinSize(minHeight = SettingsRowMinHeight)
                            .clickable {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(TELEGRAM_URL)))
                            }
                    )
                    GroupDivider()
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.credits)) },
                        supportingContent = { Text(stringResource(R.string.credits_subtitle)) },
                        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                        modifier = Modifier
                            .defaultMinSize(minHeight = SettingsRowMinHeight)
                            .clickable { showCreditsDialog = true }
                    )
                }
            }

            item { SectionHeader(stringResource(R.string.root)) }
            item {
                SettingsGroup {
                    // Root is checked automatically (on app/screen launch) — no
                    // manual "test" action needed, this just reflects live state.
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.root_status)) },
                        supportingContent = {
                            Text(
                                when (state.rootState) {
                                    RootState.AVAILABLE -> stringResource(R.string.root_access)
                                    RootState.UNAVAILABLE -> stringResource(R.string.root_unavailable)
                                    RootState.CHECKING -> stringResource(R.string.checking_root)
                                }
                            )
                        },
                        trailingContent = {
                            if (state.rootState == RootState.CHECKING) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                        modifier = Modifier.defaultMinSize(minHeight = SettingsRowMinHeight)
                    )
                }
            }

            item { Spacer(Modifier.height(20.dp)) }
            item {
                Text(
                    "Made with 🤍 by Emad",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 20.dp)
                )
            }
        }
    }

    if (confirmUnfreezeAll) {
        AlertDialog(
            onDismissRequest = { confirmUnfreezeAll = false },
            title = { Text(stringResource(R.string.unfreeze_all)) },
            text = { Text(stringResource(R.string.unfreeze_all_confirm)) },
            confirmButton = {
                TextButton(onClick = { confirmUnfreezeAll = false; viewModel.unfreezeAll() }) {
                    Text(stringResource(R.string.unfreeze_all))
                }
            },
            dismissButton = { TextButton(onClick = { confirmUnfreezeAll = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    if (showCreditsDialog) {
        Dialog(onDismissRequest = { showCreditsDialog = false }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        stringResource(R.string.credits_header),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(16.dp))

                    CreditEntry(
                        name = stringResource(R.string.credit_emad_name),
                        role = stringResource(R.string.credit_emad_role),
                        onClick = {
                            showCreditsDialog = false
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_PROFILE_URL)))
                        }
                    )
                    Spacer(Modifier.height(14.dp))
                    CreditEntry(
                        name = stringResource(R.string.credit_uad_name),
                        role = stringResource(R.string.credit_uad_role),
                        onClick = {
                            showCreditsDialog = false
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(UAD_REPO_URL)))
                        }
                    )
                    Spacer(Modifier.height(20.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showCreditsDialog = false }) { Text(stringResource(R.string.ok)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CreditEntry(name: String, role: String, onClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
        Text(role, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** One UI-style rounded group container: a single rounded pill holding one or more related rows. */
@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SettingsHorizontalMargin),
        shape = RoundedCornerShape(SettingsCardCorner),
        color = MaterialTheme.colorScheme.surface,
        content = { Column(content = content) }
    )
}

@Composable
private fun GroupDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        thickness = 0.6.dp,
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
    )
}

@Composable
private fun SectionHeader(text: String) {
    // More space above than below: binds the caption to the card that
    // follows it, while clearly separating it from the section before.
    SectionCaption(
        text,
        modifier = Modifier.padding(start = SettingsHorizontalMargin + 16.dp, end = SettingsHorizontalMargin, top = 18.dp, bottom = 6.dp)
    )
}

@Composable
private fun SwitchRow(title: String, subtitle: String? = null, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = { SamsungSwitch(checked = checked, onCheckedChange = onCheckedChange) },
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        modifier = Modifier.defaultMinSize(minHeight = SettingsRowMinHeight)
    )
}

private fun buildBackupJson(frozen: Set<String>, quickStop: Set<String>): String {
    val root = JSONObject()
    root.put("version", 2)
    root.put("frozenApps", JSONArray(frozen.sorted()))
    root.put("quickStopList", JSONArray(quickStop.sorted()))
    return root.toString(2)
}

/**
 * Restores a backup: merges the Quick Stop list (installed apps only) and
 * re-freezes backed-up apps that are installed but currently active.
 * Returns the number of restored items, or -1 if the file isn't a valid backup.
 */
private suspend fun applyBackupJson(
    text: String,
    viewModel: MainViewModel,
    prefs: com.samfreeze.app.data.PreferencesRepository
): Int {
    val root = JSONObject(text)
    val frozenArr = root.optJSONArray("frozenApps")
    val quickArr = root.optJSONArray("quickStopList")
    if (frozenArr == null && quickArr == null) return -1

    val installed = viewModel.state.value.apps.associateBy { it.packageName }

    fun toSet(arr: JSONArray?): Set<String> {
        if (arr == null) return emptySet()
        val out = mutableSetOf<String>()
        for (i in 0 until arr.length()) {
            val pkg = arr.optString(i, "")
            if (pkg.isNotBlank() && pkg in installed) out.add(pkg)
        }
        return out
    }

    val quick = toSet(quickArr)
    if (quick.isNotEmpty()) prefs.setQuickStopList(prefs.quickStopList.first() + quick)

    val toFreeze = toSet(frozenArr).filter { installed[it]?.state != AppState.FROZEN }.toSet()
    if (toFreeze.isNotEmpty()) viewModel.freezeSet(toFreeze)

    return quick.size + toFreeze.size
}
