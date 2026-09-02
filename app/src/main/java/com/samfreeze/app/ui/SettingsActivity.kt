package com.samfreeze.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.samfreeze.app.R
import com.samfreeze.app.SamFreezeApp
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private const val REPO_URL = "https://github.com/emadhamid7/SamFreeze"
private const val TELEGRAM_URL = "https://t.me/Samfreeze"
private const val GITHUB_PROFILE_URL = "https://github.com/emadhamid7"
private const val UAD_REPO_URL = "https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation"

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
    val favorites by prefs.favorites.collectAsStateWithLifecycle(initialValue = emptySet())

    var importStatus by remember { mutableStateOf<String?>(null) }
    var confirmUnfreezeAll by remember { mutableStateOf(false) }
    var showCreditsDialog by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            scope.launch {
                val json = buildBackupJson(favorites, theme, showPackageNames, showRiskDots, confirmBeforeFreeze, refreshOnResume, advancedUninstallEnabled)
                try {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                } catch (t: Throwable) { /* silent — SAF write failures aren't actionable here */ }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    if (text != null) {
                        val applied = applyBackupJson(text, context, prefs)
                        importStatus = context.getString(R.string.import_success, applied)
                    }
                } catch (t: Throwable) {
                    importStatus = context.getString(R.string.import_failed)
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Same-style header as the Freeze/Quick Stop top row, but with the
        // app logo + name instead of a search bar — no back arrow needed
        // since this is a tab, not a separate screen, and the bottom nav
        // stays put the whole time.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(R.drawable.logo),
                contentDescription = null,
                modifier = Modifier.size(32.dp).clip(RoundedCornerShape(9.dp))
            )
            Spacer(Modifier.width(12.dp))
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {

            item { SectionHeader(stringResource(R.string.appearance)) }
            item {
                SettingsGroup {
                    ThemeSelector(selected = theme, onSelect = { scope.launch { prefs.setTheme(it) } })
                }
            }

            item { SectionHeader(stringResource(R.string.app_display)) }
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
                }
            }

            item { SectionHeader(stringResource(R.string.behavior)) }
            item {
                SettingsGroup {
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

            item { SectionHeader(stringResource(R.string.backup_settings)) }
            item {
                SettingsGroup {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.backup_export)) },
                        supportingContent = { Text(stringResource(R.string.backup_export_desc)) },
                        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                        modifier = Modifier.clickable { exportLauncher.launch("samfreeze-backup.json") }
                    )
                    GroupDivider()
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.backup_import)) },
                        supportingContent = { Text(importStatus ?: stringResource(R.string.backup_import_desc)) },
                        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                        modifier = Modifier.clickable { importLauncher.launch(arrayOf("application/json")) }
                    )
                }
            }

            item { SectionHeader(stringResource(R.string.advanced_settings)) }
            item {
                SettingsGroup {
                    SwitchRow(
                        title = stringResource(R.string.enable_uninstall_option),
                        checked = advancedUninstallEnabled,
                        onCheckedChange = { scope.launch { prefs.setAdvancedUninstallEnabled(it) } }
                    )
                }
            }

            item { SectionHeader(stringResource(R.string.bulk_actions)) }
            item {
                SettingsGroup {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Button(
                            onClick = { confirmUnfreezeAll = true },
                            modifier = Modifier.fillMaxWidth(),
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

            item { SectionHeader(stringResource(R.string.community)) }
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
                        modifier = Modifier.clickable {
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
                        modifier = Modifier.clickable {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(TELEGRAM_URL)))
                        }
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
                        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
                    )
                }
            }

            item { SectionHeader(stringResource(R.string.credits)) }
            item {
                SettingsGroup {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.credits)) },
                        supportingContent = { Text(stringResource(R.string.credits_subtitle)) },
                        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                        modifier = Modifier.clickable { showCreditsDialog = true }
                    )
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
            item {
                Text(
                    "Made with 🤍 by Emad",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_PROFILE_URL)))
                        }
                        .padding(24.dp)
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
                        stringResource(R.string.uad_credit_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        stringResource(R.string.uad_credit_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(20.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = {
                            showCreditsDialog = false
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(UAD_REPO_URL)))
                        }) { Text(stringResource(R.string.view_on_github)) }
                        TextButton(onClick = { showCreditsDialog = false }) { Text(stringResource(R.string.ok)) }
                    }
                }
            }
        }
    }
}

/** One UI-style rounded group container: a single rounded pill holding one or more related rows. */
@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(22.dp),
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
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
    )
}

@Composable
private fun ThemeSelector(selected: String, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf("system" to stringResource(R.string.theme_system),
               "light" to stringResource(R.string.theme_light),
               "dark" to stringResource(R.string.theme_dark)).forEach { (value, label) ->
            FilterChip(selected = selected == value, onClick = { onSelect(value) }, label = { Text(label) })
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String? = null, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = onCheckedChange) },
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
    )
}

private fun buildBackupJson(
    favorites: Set<String>,
    theme: String,
    showPackageNames: Boolean,
    showRiskDots: Boolean,
    confirmBeforeFreeze: Boolean,
    refreshOnResume: Boolean,
    advancedUninstallEnabled: Boolean
): String {
    val root = JSONObject()
    root.put("favorites", JSONArray(favorites.toList()))
    val settings = JSONObject()
    settings.put("theme", theme)
    settings.put("showPackageNames", showPackageNames)
    settings.put("showRiskDots", showRiskDots)
    settings.put("confirmBeforeFreeze", confirmBeforeFreeze)
    settings.put("refreshOnResume", refreshOnResume)
    settings.put("advancedUninstallEnabled", advancedUninstallEnabled)
    root.put("settings", settings)
    return root.toString(2)
}

/** Applies an exported backup, verifying package existence before restoring lists. Returns applied-item count. */
private suspend fun applyBackupJson(
    text: String,
    context: Context,
    prefs: com.samfreeze.app.data.PreferencesRepository
): Int {
    val root = JSONObject(text)
    val pm = context.packageManager
    var applied = 0

    fun installedOnly(arr: JSONArray?): Set<String> {
        if (arr == null) return emptySet()
        val out = mutableSetOf<String>()
        for (i in 0 until arr.length()) {
            val pkg = arr.optString(i, "")
            if (pkg.isBlank()) continue
            val exists = try { pm.getApplicationInfo(pkg, 0); true } catch (t: Throwable) { false }
            if (exists) out.add(pkg)
        }
        return out
    }

    val favorites = installedOnly(root.optJSONArray("favorites"))
    prefs.setFavorites(favorites)
    applied += favorites.size

    root.optJSONObject("settings")?.let { settings ->
        if (settings.has("theme")) { prefs.setTheme(settings.getString("theme")); applied++ }
        if (settings.has("showPackageNames")) { prefs.setShowPackageNames(settings.getBoolean("showPackageNames")); applied++ }
        if (settings.has("showRiskDots")) { prefs.setShowRiskDots(settings.getBoolean("showRiskDots")); applied++ }
        if (settings.has("confirmBeforeFreeze")) { prefs.setConfirmBeforeFreeze(settings.getBoolean("confirmBeforeFreeze")); applied++ }
        if (settings.has("refreshOnResume")) { prefs.setRefreshOnResume(settings.getBoolean("refreshOnResume")); applied++ }
        if (settings.has("advancedUninstallEnabled")) { prefs.setAdvancedUninstallEnabled(settings.getBoolean("advancedUninstallEnabled")); applied++ }
    }

    return applied
}
