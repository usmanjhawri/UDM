@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.nexonic.downloadmanager.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings as SystemSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nexonic.downloadmanager.BuildConfig
import com.nexonic.downloadmanager.core.AppLanguage
import com.nexonic.downloadmanager.core.Translations
import com.nexonic.downloadmanager.core.tr
import com.nexonic.downloadmanager.data.Downloads
import com.nexonic.downloadmanager.service.Scheduler
import com.nexonic.downloadmanager.video.VideoEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Everyday settings stay visible; infrequent controls are behind Advanced. No embedded browser. */
@Composable
fun SettingsScreen(message: (String) -> Unit) {
    val s = Downloads.settings
    val context = LocalContext.current
    var appearance by remember { mutableStateOf(s.appearance) }
    var wifiOnly by remember { mutableStateOf(s.wifiOnly) }
    var folders by remember { mutableStateOf(s.categoryFolders) }
    var subtitles by remember { mutableStateOf(s.videoSubtitles) }
    var audio by remember { mutableStateOf(s.audioFormat) }
    var active by remember { mutableFloatStateOf(s.maxActive.toFloat().coerceIn(1f, 6f)) }
    var notifyDone by remember { mutableStateOf(s.notifyFinished) }
    var notifyFail by remember { mutableStateOf(s.notifyFailed) }
    var advanced by remember { mutableStateOf(false) }
    var connections by remember { mutableFloatStateOf(s.connections.toFloat().coerceIn(1f, 32f)) }
    var limit by remember { mutableStateOf(s.speedLimitKB.takeIf { it > 0 }?.toString().orEmpty()) }
    var vpnOnly by remember { mutableStateOf(s.vpnOnly) }
    var schedule by remember { mutableStateOf(s.scheduleEnabled) }
    var capMB by remember { mutableStateOf(s.dataCapMB.takeIf { it > 0 }?.toString().orEmpty()) }
    var customFolder by remember { mutableStateOf(s.saveTreeName) }
    var engineVersion by remember { mutableStateOf<String?>(null) }
    var engineVersionChecked by remember { mutableStateOf(false) }
    var engineProblem by remember { mutableStateOf<String?>(null) }
    var updating by remember { mutableStateOf(false) }
    var showLicenses by remember { mutableStateOf(false) }
    var languageDialog by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            val kept = runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }.isSuccess
            if (!kept) message("The selected folder cannot be used.")
            else {
                s.saveTree?.let { old ->
                    if (old != uri.toString()) runCatching {
                        context.contentResolver.releasePersistableUriPermission(Uri.parse(old), flags)
                    }
                }
                s.saveTree = uri.toString()
                val name = Uri.decode(uri.lastPathSegment.orEmpty()).substringAfterLast(':').ifBlank { "Chosen folder" }
                s.saveTreeName = name
                customFolder = name
            }
        }
    }

    LaunchedEffect(Unit) {
        val status = withContext(Dispatchers.IO) { VideoEngine.version() to VideoEngine.lastProblem() }
        engineVersion = status.first
        engineProblem = status.second
        engineVersionChecked = true
    }

    fun runEngineUpdate(nightly: Boolean) {
        if (updating) return
        updating = true
        Thread {
            val result = runCatching { VideoEngine.updateNow(nightly) }
            val version = VideoEngine.version()
            val problem = VideoEngine.lastProblem()
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                updating = false
                engineVersion = version
                engineVersionChecked = true
                engineProblem = problem
                message(if (result.isSuccess) {
                    if (result.getOrNull() == "ALREADY_UP_TO_DATE") "yt-dlp is up to date"
                    else "yt-dlp update successful"
                } else "yt-dlp update failed. See the reason under Video engine.")
            }
        }.start()
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Settings", fontWeight = FontWeight.SemiBold) })
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SlimSection("Appearance")
            SlimChoices("Theme", listOf("system" to "System", "light" to "Light", "dark" to "Dark"), appearance) {
                appearance = it; s.appearance = it; Appearance.choice = it
            }

            SlimSection("Downloads")
            SlimToggle("Wi-Fi only", "Use Wi-Fi or another unmetered connection.", wifiOnly) {
                wifiOnly = it; s.wifiOnly = it; Downloads.pump()
            }
            SlimToggle("Organize files by type", "Save videos, audio and documents into separate folders.", folders) {
                folders = it; s.categoryFolders = it
            }
            Text("Simultaneous downloads: " + active.toInt(), style = MaterialTheme.typography.bodyMedium)
            Slider(value = active, onValueChange = { active = it }, valueRange = 1f..6f, steps = 4,
                onValueChangeFinished = { s.maxActive = active.toInt(); Downloads.pump() })

            SlimSection("Video and audio")
            SlimChoices("Audio-only format",
                listOf("original" to "Original", "mp3" to "MP3", "opus" to "Opus", "flac" to "FLAC"),
                audio) { audio = it; s.audioFormat = it }
            SlimToggle("Save subtitles", "Embed subtitles when available.", subtitles) {
                subtitles = it; s.videoSubtitles = it
            }

            SlimSection("Notifications")
            SlimToggle("Download completed", "Notify when a file is ready.", notifyDone) {
                notifyDone = it; s.notifyFinished = it
            }
            SlimToggle("Download failed", "Notify when a download needs attention.", notifyFail) {
                notifyFail = it; s.notifyFailed = it
            }

            SlimSection("Video engine")
            Text("yt-dlp " + (engineVersion ?: if (engineVersionChecked) "Version unavailable" else "Checking version…"),
                style = MaterialTheme.typography.bodyMedium)
            Text("Updates independently of the APK. If YouTube rejects a stream, update yt-dlp and refresh the download link.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            engineProblem?.let { problem ->
                Text("Engine status: $problem", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error)
                TextButton(onClick = {
                    copy(context, "Nexonic Personal v0.5.1 | yt-dlp: " +
                        (engineVersion ?: "unavailable") + " | " + problem)
                    message("Diagnostic copied")
                }) { Text("Copy safe diagnostic") }
            }
            OutlinedButton(enabled = !updating, onClick = { runEngineUpdate(false) },
                modifier = Modifier.heightIn(min = 48.dp)) {
                Text(if (updating) "Updating…" else "Check for updates")
            }

            SlimSection("Advanced")
            TextButton(onClick = { advanced = !advanced }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(if (advanced) "Hide advanced options" else "Show advanced options")
                Spacer(Modifier.width(6.dp))
                Icon(if (advanced) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null)
            }
            if (advanced) {
                TextButton(enabled = !updating, onClick = { runEngineUpdate(true) },
                    modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Try yt-dlp nightly (experimental)")
                }
                Text("Nightly can help when stable is outdated, but may introduce regressions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Connections per download: " + s.connections,
                    style = MaterialTheme.typography.bodyMedium)
                Slider(value = connections, onValueChange = { connections = it },
                    valueRange = 1f..32f, steps = 30,
                    onValueChangeFinished = { s.connections = connections.toInt() })
                OutlinedTextField(
                    value = limit, onValueChange = {
                        limit = it.filter(Char::isDigit).take(9)
                        Downloads.setSpeedLimit(limit.toLongOrNull() ?: 0L)
                    }, label = { Text("Speed limit (KB/s, blank = unlimited)") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth())
                SlimToggle("VPN only", "Pause transfers when no VPN is connected.", vpnOnly) {
                    vpnOnly = it; Downloads.setVpnOnly(it)
                }
                OutlinedTextField(
                    value = capMB, onValueChange = {
                        capMB = it.filter(Char::isDigit).take(7)
                        s.dataCapMB = capMB.toIntOrNull() ?: 0
                        Downloads.releaseCap()
                    },
                    label = { Text("Data cap (MB, blank = unlimited)") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth())
                SlimToggle("Daily scheduled downloads", "Use the previously saved start time.", schedule) {
                    schedule = it; s.scheduleEnabled = it; Scheduler.apply(context)
                }
                if (s.speedSchedule.isNotBlank()) {
                    Text("An older scheduled speed limit is active.",
                        style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = {
                        s.speedSchedule = ""; Downloads.applySpeedLimit(); message("Speed schedule cleared")
                    }) { Text("Clear speed schedule") }
                }
                OutlinedButton(onClick = { pickFolder.launch(null) },
                    modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(if (customFolder.isBlank()) "Choose a download folder" else "Folder: " + customFolder)
                }
                if (s.saveTree != null) {
                    TextButton(onClick = {
                        s.saveTree?.let { old ->
                            runCatching {
                                context.contentResolver.releasePersistableUriPermission(
                                    Uri.parse(old), Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                            }
                        }
                        s.saveTree = null; s.saveTreeName = ""; customFolder = ""
                    }) { Text("Use default Downloads folder") }
                }
                val currentLanguage = AppLanguage.chosen(context)
                TextButton(onClick = { languageDialog = true }) {
                    Text("Language: " + (Translations.names[currentLanguage] ?: "System"))
                }
                TextButton(onClick = { confirmClear = true }) { Text("Clear finished items from list") }
                TextButton(onClick = {
                    runCatching {
                        context.startActivity(Intent(SystemSettings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(SystemSettings.EXTRA_APP_PACKAGE, context.packageName))
                    }
                }) { Text("Android notification settings") }
                TextButton(onClick = {
                    val report = com.nexonic.downloadmanager.core.CrashReports.latest(
                        java.io.File(context.filesDir, "crash-reports"))
                    if (report == null) message("No crash report found.")
                    else runCatching {
                        context.startActivity(Intent.createChooser(
                            Intent(Intent.ACTION_SEND).setType("text/plain")
                                .putExtra(Intent.EXTRA_TEXT, report.readText()), "Share crash report"))
                    }
                }) { Text("Share latest crash report") }
            }

            SlimSection("About")
            Text("Nexonic Personal v0.5.1 · Core " + BuildConfig.VERSION_NAME,
                style = MaterialTheme.typography.bodyMedium)
            Text("Based on Nexonic Download Manager. Free and open source.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { showLicenses = true }) { Text("Open-source licenses") }
            Spacer(Modifier.heightIn(min = 28.dp))
        }
    }

    if (showLicenses) {
        AlertDialog(
            onDismissRequest = { showLicenses = false },
            title = { Text("Open-source licenses") },
            text = { Text(licenseTexts(context),
                modifier = Modifier.verticalScroll(rememberScrollState()),
                style = MaterialTheme.typography.bodySmall) },
            confirmButton = { TextButton(onClick = { showLicenses = false }) { Text("Close") } },
        )
    }
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text("Clear finished downloads?") },
        text = { Text("The downloaded files are kept.") },
        confirmButton = {
            TextButton(onClick = {
                Downloads.clearFinished(); confirmClear = false; message("Finished items cleared")
            }) { Text("Clear list") }
        },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
    )
    if (languageDialog) AlertDialog(
        onDismissRequest = { languageDialog = false },
        title = { Text("Language") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                val options = listOf("" to "System") + Translations.names.toList()
                options.forEach { (code, label) ->
                    TextButton(onClick = {
                        languageDialog = false
                        (context as? android.app.Activity)?.let { AppLanguage.choose(it, code) }
                    }) { Text(label) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { languageDialog = false }) { Text("Close") } },
    )
}

@Composable
private fun SlimSection(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 20.dp, bottom = 4.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun SlimChoices(title: String, items: List<Pair<String, String>>,
    selected: String, onChange: (String) -> Unit) {
    Text(title, style = MaterialTheme.typography.bodyMedium)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { (code, label) ->
            FilterChip(
                selected = selected == code, onClick = { onChange(code) },
                label = { Text(label) }, modifier = Modifier.heightIn(min = 48.dp))
        }
    }
}

@Composable
private fun SlimToggle(title: String, subtitle: String,
    checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .heightIn(min = 56.dp).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** License attributions remain available after removing the embedded browser. */
private fun licenseTexts(context: android.content.Context): String {
    val names = runCatching { context.assets.list("licenses")?.toList() }.getOrNull().orEmpty()
        .sortedWith(compareBy({ it != "NOTICES.txt" }, { it }))
    return names.joinToString("\n\n" + "=".repeat(40) + "\n\n") { name ->
        runCatching { context.assets.open("licenses/$name").bufferedReader().use { it.readText() } }.getOrDefault("")
    }
}

/** Shared low-emphasis description used by other configuration screens. */
@Composable
internal fun Hint(text: String) {
    Text(tr(text), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}
