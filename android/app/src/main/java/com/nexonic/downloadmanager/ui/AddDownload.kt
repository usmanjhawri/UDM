@file:OptIn(ExperimentalMaterial3Api::class)

package com.nexonic.downloadmanager.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexonic.downloadmanager.browser.Offer
import com.nexonic.downloadmanager.core.CurlCommand
import com.nexonic.downloadmanager.core.tr
import com.nexonic.downloadmanager.core.BatchPattern
import com.nexonic.downloadmanager.core.DownloadRequest
import com.nexonic.downloadmanager.core.FileCategory
import com.nexonic.downloadmanager.core.FileNames
import com.nexonic.downloadmanager.core.Format
import com.nexonic.downloadmanager.core.Http
import com.nexonic.downloadmanager.core.HttpStatusException
import com.nexonic.downloadmanager.core.Links
import com.nexonic.downloadmanager.core.ProbeResult
import com.nexonic.downloadmanager.data.Downloads
import com.nexonic.downloadmanager.data.NewDownload
import com.nexonic.downloadmanager.data.Secrets
import com.nexonic.downloadmanager.core.VideoChoice
import com.nexonic.downloadmanager.core.HandoffPreference
import com.nexonic.downloadmanager.core.HandoffQualityPolicy
import com.nexonic.downloadmanager.core.HandoffFormatDisplay
import com.nexonic.downloadmanager.core.VideoPages
import com.nexonic.downloadmanager.video.VideoEngine
import com.nexonic.downloadmanager.video.VideoPage
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.RadioButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * The download window (like the desktop "Download File Info"): file name, size, type and whether it can resume,
 * with Start Download, Download Later or Cancel. Also takes pasted or shared links, several at once, and batch patterns.
 */
@Composable
fun AddDownloadSheet(given: Offer?, text: String?, compactHandoff: Boolean = false,
    onHandoffQueued: () -> Unit = {}, onDone: (String?) -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    var input by remember { mutableStateOf(given?.url ?: text.orEmpty()) }
    var batch by remember { mutableStateOf(false) }
    var keepOnly by remember { mutableStateOf("") }
    var name by remember { mutableStateOf(given?.fileName.orEmpty()) }
    var nameEdited by remember { mutableStateOf(false) }
    var probe by remember { mutableStateOf<ProbeResult?>(null) }
    var probing by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    var showLogin by remember { mutableStateOf(false) }
    var user by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    // Video pages (YouTube, Facebook, Instagram, TikTok…): the video engine reads the page instead of saving it.
    var isVideo by remember { mutableStateOf(false) }
    var reading by remember { mutableStateOf(false) }
    var page by remember { mutableStateOf<VideoPage?>(null) }
    var pick by remember { mutableStateOf(0) }
    var audioTab by remember { mutableStateOf(false) }
    var submitted by remember { mutableStateOf(false) }
    var advanced by remember { mutableStateOf(false) }
    var inspectionAttempt by remember { mutableStateOf(0) }

    // A browser's "Copy as cURL" pasted here: one download with the command's referrer, cookies, browser name and login.
    val curl = remember(input) { if (given == null && CurlCommand.looks(input)) CurlCommand.parse(input) else null }
    val offer: Offer? = given ?: curl?.request?.let { Offer(url = it.url, referer = it.referrer, userAgent = it.userAgent, cookies = it.cookies) }
    LaunchedEffect(curl) {
        curl?.request?.let { c -> if (c.username != null) { user = c.username; password = c.password.orEmpty() } }
    }

    val links: List<String> = remember(input, batch, curl, keepOnly) {
        if (curl != null) return@remember listOfNotNull(curl.request?.url)
        // Any pasted text works: when it is not just links, the links in it are found (Link Grabber).
        val l = if (batch) com.nexonic.downloadmanager.core.LinkFilter.apply(
                Links.split(input).flatMap { BatchPattern.expand(it, 2000) }.filter { Links.isWebLink(it) }, keepOnly)
            else Links.split(input).takeIf { s -> s.all { Links.isWebLink(it) || it.startsWith("magnet:", true) } }
                ?: com.nexonic.downloadmanager.core.LinkGrabber.find(input)
        l.distinct()
    }
    val single = links.singleOrNull()
    // A link that is already in the list (or was downloaded before) is pointed out; adding it again saves a new copy.
    val repeat = remember(links) {
        val list = Downloads.items.value
        links.mapNotNull { u -> list.firstOrNull { com.nexonic.downloadmanager.core.Duplicates.same(it.url, u) } }
    }

    // Ask the server for the name and size of a single link.
    LaunchedEffect(single, user, password, inspectionAttempt) {
        probe = null; problem = null; page = null; isVideo = false; pick = 0; audioTab = false
        if (single == null) return@LaunchedEffect
        if (!compactHandoff) delay(350)
        if (VideoPages.isVideoSite(single)) {
            isVideo = true
        } else {
            probing = true
            val req = DownloadRequest(single, offer?.cookies?.takeIf { single == offer?.url }, offer?.referer, offer?.userAgent, user.ifEmpty { null }, password.ifEmpty { null })
            val result = withContext(Dispatchers.IO) { runCatching { Http.probe(req) } }
            probing = false
            result.onSuccess { p ->
                // A web page is never saved as a file: look for the video on it instead.
                if (VideoPages.isWebPage(p.contentType)) isVideo = true
                else { probe = p; if (!nameEdited) name = p.fileName }
            }.onFailure { e ->
                problem = when {
                    e is HttpStatusException && (e.code == 401 || e.code == 403) -> "The server asks for a login or refused the link (${e.code})."
                    e is HttpStatusException -> "The server answered ${e.code}."
                    else -> "Could not reach the server. You can still add it and try later."
                }
                if (!nameEdited && name.isEmpty()) name = FileNames.fromUrl(single)
            }
        }
        if (isVideo) {
            reading = true
            val read = withContext(Dispatchers.IO) { runCatching { VideoEngine.read(single, offer?.userAgent, offer?.referer) } }
            reading = false
            read.onSuccess { p ->
                page = p
                if (compactHandoff) {
                    val suggested = HandoffPreference.pick(context, p.choices)
                    if (suggested in p.choices.indices) pick = suggested
                }
                audioTab = p.choices.getOrNull(pick)?.audioOnly == true
                if (!nameEdited) p.choices.getOrNull(pick)?.let { name = VideoPages.fileName(p.title, it.ext) }
            }.onFailure { problem = (it as? com.nexonic.downloadmanager.video.VideoException)?.message ?: VideoPages.engineError(it.javaClass.simpleName, it.message) }
        }
    }

    fun choose(i: Int) {
        val p = page ?: return
        pick = i
        if (!nameEdited) name = VideoPages.fileName(p.title, p.choices[i].ext)
        else name = name.substringBeforeLast('.', name) + "." + p.choices[i].ext
    }

    // A video can be added right away: while the page is still being read it downloads in the best quality and is named
    // after the video; problems then show on the download itself. Only a page that has no video can't be added.
    val canAdd = links.isNotEmpty() &&
        !(single != null && isVideo && page == null && !reading) &&
        (!compactHandoff || !page?.choices.isNullOrEmpty())

    fun add(startNow: Boolean) {
        if (links.isEmpty() || submitted) return
        submitted = true
        val secrets = Secrets(cookies = offer?.cookies, user = user.ifEmpty { null }, password = password.ifEmpty { null })
        val choice: VideoChoice? = page?.choices?.getOrNull(pick)
        links.forEach { u ->
            val same = single != null
            // Several links: video pages get the best quality, named after the video.
            val video = if (same && isVideo) choice ?: VideoPages.best else if (VideoPages.isVideoSite(u)) VideoPages.best else null
            Downloads.add(
                NewDownload(
                    url = u,
                    fileName = if (same && name.isNotBlank() && (video == null || page != null)) name else if (video != null) VideoPages.AUTO_NAME else FileNames.fromUrl(u),
                    size = if (video != null) video.size else if (same) probe?.size ?: offer?.size?.takeIf { it >= 0 } else null,
                    resumable = if (same) probe?.resumable ?: false else false,
                    referer = offer?.referer, userAgent = offer?.userAgent,
                    secrets = if (u == offer?.url || offer == null) secrets else Secrets(),
                    mime = if (same) probe?.contentType ?: offer?.mime else null,
                    quality = offer?.quality,
                    video = video,
                ),
                startNow,
            )
        }
        if (compactHandoff && choice != null) HandoffPreference.remember(context, choice)
        val what = if (links.size == 1) "Download" else "${links.size} downloads"
        onDone(if (startNow) "$what started" else "$what saved for later")
        if (compactHandoff && startNow) onHandoffQueued()
    }

    ModalBottomSheet(onDismissRequest = { onDone(null) }, sheetState = sheet,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
          Column(
            Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (compactHandoff) {
                Row(Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(46.dp).clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Download, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(24.dp))
                    }
                    Column {
                        Text("Save from YouTube", style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold)
                        Text("Choose a format. Download continues in the background.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                Text(tr(if (isVideo) "Download video" else "Download file"),
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            }

            if (given == null && !(compactHandoff && single != null)) {
                OutlinedTextField(
                    value = input, onValueChange = { input = it },
                    label = { Text(tr("Links (one or more)")) },
                    placeholder = { Text(tr("https://…")) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    trailingIcon = {
                        IconButton(onClick = {
                            val clip = context.getSystemService(android.content.ClipboardManager::class.java)
                                ?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                            if (clip.isNotBlank()) input = clip
                        }) { Icon(Icons.Outlined.ContentPaste, tr("Paste")) }
                    },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("Batch pattern"), style = MaterialTheme.typography.bodyMedium)
                        Text(tr("file[01-50].jpg makes 50 links; {jpg,png} tries each"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = batch, onCheckedChange = { batch = it })
                }
                if (batch) OutlinedTextField(
                    value = keepOnly, onValueChange = { keepOnly = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    label = { Text(tr("Keep only (optional)")) },
                    supportingText = { Text(tr("* and ? are wildcards; ! leaves links out. Example: *.jpg !*thumb*") + " · ${links.size}") },
                )
            } else if (!compactHandoff) {
                Text((given?.url ?: single).orEmpty(), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (repeat.isNotEmpty()) {
                val r = repeat.first()
                Text(
                    if (links.size > 1) tr("Some of these links are already in the list:") + " ${repeat.size}"
                    else if (r.status == com.nexonic.downloadmanager.data.Status.COMPLETE) tr("This file was downloaded before:") + " ${r.fileName}"
                    else tr("This link is already in the list:") + " ${r.fileName}",
                    color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodySmall,
                )
            }
            curl?.problem?.let { Text(tr(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            curl?.request?.ignored?.takeIf { it.isNotEmpty() }?.let {
                Text(tr("Not repeated from the cURL command:") + " " + it.joinToString(", "), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            when {
                single != null && isVideo -> {
                    if (compactHandoff && !advanced) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(page?.title?.takeIf { it.isNotBlank() } ?: "YouTube video",
                                modifier = Modifier.padding(14.dp),
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    } else {
                        OutlinedTextField(
                            value = name, onValueChange = { name = it; nameEdited = true },
                            label = { Text(tr("File name")) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    page?.let { p ->
                        Text("Format", fontWeight = FontWeight.SemiBold)
                        val hasVideo = p.choices.any { !it.audioOnly }
                        val hasAudio = p.choices.any { it.audioOnly }
                        if (hasVideo && hasAudio) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(modifier = Modifier.heightIn(min = 48.dp), selected = !audioTab, onClick = {
                                    audioTab = false
                                    p.choices.indexOfFirst { !it.audioOnly }.takeIf { it >= 0 }?.let { choose(it) }
                                }, label = { Text("Video") })
                                FilterChip(modifier = Modifier.heightIn(min = 48.dp), selected = audioTab, onClick = {
                                    audioTab = true
                                    p.choices.indexOfFirst { it.audioOnly }.takeIf { it >= 0 }?.let { choose(it) }
                                }, label = { Text("Audio") })
                            }
                        }
                        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            p.choices.forEachIndexed { i, c ->
                                if (c.audioOnly != audioTab) return@forEachIndexed
                                val selected = pick == i
                                Row(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                                        .background(if (selected) MaterialTheme.colorScheme.primaryContainer
                                            else MaterialTheme.colorScheme.surfaceVariant)
                                        .selectable(selected = selected, role = Role.RadioButton, onClick = { choose(i) })
                                        .heightIn(min = 56.dp).padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RadioButton(selected = selected, onClick = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(HandoffFormatDisplay.label(c), modifier = Modifier.weight(1f),
                                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                            else MaterialTheme.colorScheme.onSurface,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    c.size?.let {
                                        Text("≈ " + Format.size(it),
                                            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                        val audio = p.choices.getOrNull(pick)?.audioOnly == true
                        val folder = if (audio) "Music" else "Video"
                        if (!compactHandoff || advanced) {
                            InfoRow("Saved to", "Downloads/Nexonic" + if (Downloads.settings.categoryFolders) "/$folder" else "")
                            InfoRow("Resume", "Best effort (yt-dlp)")
                        }
                    }
                    if (reading) Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp))
                        Text(if (compactHandoff) "Checking available formats…" else tr("Finding the video… You can start now: it downloads in the best quality."), style = MaterialTheme.typography.bodySmall)
                    }
                    problem?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        if (compactHandoff && !reading) OutlinedButton(
                            onClick = { inspectionAttempt++ }, modifier = Modifier.heightIn(min = 48.dp)
                        ) { Text("Retry inspection") }
                    }
                    if (compactHandoff) {
                        TextButton(onClick = { advanced = !advanced }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(if (advanced) "Fewer options" else "More options")
                        }
                    }
                }
                single != null -> {
                    OutlinedTextField(
                        value = name, onValueChange = { name = it; nameEdited = true },
                        label = { Text(tr("File name")) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                    val category = FileCategory.of(name)
                    InfoRow("Size", when {
                        probing -> "Checking…"
                        probe?.size != null -> Format.size(probe!!.size!!)
                        (offer?.size ?: -1) >= 0 -> Format.size(offer!!.size)
                        else -> "Unknown"
                    })
                    InfoRow("Type", category.label)
                    InfoRow("Saved to", "Downloads/Nexonic" + (if (Downloads.settings.categoryFolders) category.folder?.let { "/$it" } ?: "" else ""))
                    InfoRow("Resume", when { probing -> "…"; probe?.resumable == true -> "Yes"; probe != null -> "No (starts over if stopped)"; else -> "Unknown" })
                    offer?.quality?.let { InfoRow("Quality", it) }
                    if (probing) Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp))
                        Text(tr("Asking the server…"), style = MaterialTheme.typography.bodySmall)
                    }
                    problem?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
                links.size > 1 -> {
                    Text("${links.size} links", fontWeight = FontWeight.SemiBold)
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(10.dp)) {
                        links.take(8).forEach { Text(FileNames.fromUrl(it), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        if (links.size > 8) Text("and ${links.size - 8} more", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                input.isNotBlank() -> Text(tr("No web link found. Links start with http:// or https://"), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            if (!compactHandoff || advanced) TextButton(onClick = { showLogin = !showLogin }) { Text(tr(if (showLogin) "Hide login" else "Needs a login?")) }
            if (showLogin && (!compactHandoff || advanced)) {
                OutlinedTextField(user, { user = it }, label = { Text(tr("User name")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(password, { password = it }, label = { Text(tr("Password")) }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                Text(tr("Sent only to this site, and kept encrypted on this phone."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

          } // End independently scrollable content
          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
          Row(horizontalArrangement = Arrangement.spacedBy(12.dp),
              modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
              verticalAlignment = Alignment.CenterVertically) {
              TextButton(onClick = { onDone(null) }, modifier = Modifier.heightIn(min = 52.dp)) { Text(tr("Cancel")) }
              if (compactHandoff) {
                  Button(onClick = { add(true) }, enabled = canAdd && !submitted,
                      modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                      shape = MaterialTheme.shapes.medium) {
                      Text(if (reading) "Checking formats…" else "Download")
                  }
              } else {
                  Spacer(Modifier.weight(1f))
                  OutlinedButton(onClick = { add(false) }, enabled = canAdd && !submitted,
                      modifier = Modifier.heightIn(min = 48.dp)) { Text(tr("Later")) }
                  Button(onClick = { add(true) }, enabled = canAdd && !submitted,
                      modifier = Modifier.heightIn(min = 48.dp)) { Text(tr("Start download")) }
              }
          }
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(tr(label), color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.36f))
        Text(tr(value), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.64f))
    }
}
