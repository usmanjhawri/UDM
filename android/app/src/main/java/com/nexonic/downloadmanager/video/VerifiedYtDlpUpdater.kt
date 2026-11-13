package com.nexonic.downloadmanager.video

import android.content.Context
import android.util.Log
import com.nexonic.downloadmanager.core.VerifiedReleasePolicy
import com.nexonic.downloadmanager.data.Downloads
import com.nexonic.downloadmanager.data.Status
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Stable yt-dlp updater used by Nexonic Personal.
 *
 * The release JSON is fetched from the fixed GitHub API endpoint over HTTPS.
 * It must name the exact public official yt-dlp asset and include a SHA-256 digest.
 * The entire candidate is verified before any existing binary is moved.
 *
 * Important: GitHub API digest verification depends on HTTPS and the integrity of GitHub's
 * release metadata. It is NOT an independent publisher signature.
 */
object VerifiedYtDlpUpdater {
    private const val API = "https://api.github.com/repos/yt-dlp/yt-dlp/releases/latest"
    private const val META_LIMIT = 1024 * 1024
    private const val UA = "NexonicPersonal/0.6 Android yt-dlp-updater"

    private data class Candidate(val tag: String, val url: String, val size: Long, val digest: String)

    fun update(context: Context, installedVersion: String?): String {
        val candidate = latest()
        if (!VerifiedReleasePolicy.isNewer(candidate.tag, installedVersion)) return "ALREADY_UP_TO_DATE"
        if (Downloads.items.value.any { it.status == Status.DOWNLOADING }) {
            return "DEFERRED_ACTIVE_DOWNLOADS"
        }
        install(context, candidate)
        return "DONE"
    }

    private fun latest(): Candidate {
        val connection = connect(API, "application/vnd.github+json")
        val json = try {
            val payload = connection.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (out.size() + count > META_LIMIT) throw IOException("GitHub response exceeds safe size")
                    out.write(buffer, 0, count)
                }
                out.toByteArray()
            }
            JSONObject(String(payload, Charsets.UTF_8))
        } finally {
            connection.disconnect()
        }
        if (json.optBoolean("draft", true) || json.optBoolean("prerelease", true)) {
            throw IOException("GitHub latest release is not stable")
        }
        val tag = json.optString("tag_name")
        val assets = json.getJSONArray("assets")
        var asset: JSONObject? = null
        for (i in 0 until assets.length()) {
            val item = assets.getJSONObject(i)
            if (item.optString("name") == "yt-dlp") { asset = item; break }
        }
        val binary = asset ?: throw IOException("Official yt-dlp asset missing from release")
        val url = binary.optString("browser_download_url")
        val size = binary.optLong("size", -1)
        val rawDigest = binary.optString("digest")
        VerifiedReleasePolicy.validateAsset(tag, url, size, rawDigest)
        val digest = VerifiedReleasePolicy.expectedDigest(rawDigest)
            ?: throw IOException("Release SHA-256 digest unavailable")
        return Candidate(tag, url, size, digest)
    }

    /** Only HTTPS GitHub-controlled origins and its release CDN are permitted. */
    private fun connect(url: String, accept: String): HttpURLConnection {
        var current = URL(url)
        repeat(6) {
            val host = current.host.lowercase()
            if (current.protocol != "https" || current.userInfo != null ||
                (current.port != -1 && current.port != 443) ||
                !(host == "api.github.com" || host == "github.com" ||
                    host.endsWith(".githubusercontent.com"))) {
                throw IOException("Untrusted release server")
            }
            val request = (current.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 12000
                readTimeout = 30000
                setRequestProperty("User-Agent", UA)
                setRequestProperty("Accept", accept)
                setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            }
            val code = request.responseCode
            if (code in setOf(301, 302, 303, 307, 308)) {
                val location = request.getHeaderField("Location")
                request.disconnect()
                if (location.isNullOrBlank()) throw IOException("GitHub release redirect has no location")
                current = URL(current, location)
            } else {
                if (code != 200) {
                    request.disconnect()
                    throw IOException("GitHub release server returned HTTP $code")
                }
                return request
            }
        }
        throw IOException("Too many GitHub release redirects")
    }

    private fun install(context: Context, candidate: Candidate) {
        val dir = File(context.noBackupFilesDir, "youtubedl-android/yt-dlp")
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("Cannot open yt-dlp directory")
        val target = File(dir, "yt-dlp")
        val stage = File(dir, "yt-dlp.net-stage")
        val backup = File(dir, "yt-dlp.net-backup")
        val prefs = context.getSharedPreferences("youtubedl-android", Context.MODE_PRIVATE)
        val oldVersion = prefs.getString("dlpVersion", null)
        val oldName = prefs.getString("dlpVersionName", null)
        var movedOld = false
        var movedNew = false
        try {
            if (backup.exists() && !target.exists() && !backup.renameTo(target)) {
                throw IOException("Cannot restore prior yt-dlp backup")
            }
            if (stage.exists() && !stage.delete()) throw IOException("Cannot clear previous temporary download")
            val connection = connect(candidate.url, "application/octet-stream")
            try {
                val digest = MessageDigest.getInstance("SHA-256")
                var written = 0L
                connection.inputStream.use { stream ->
                    FileOutputStream(stage).use { out ->
                        val buffer = ByteArray(32 * 1024)
                        while (true) {
                            val count = stream.read(buffer)
                            if (count < 0) break
                            written += count
                            if (written > VerifiedReleasePolicy.MAX_RELEASE_BYTES ||
                                written > candidate.size) throw IOException("Downloaded engine exceeds expected size")
                            digest.update(buffer, 0, count)
                            out.write(buffer, 0, count)
                        }
                        out.fd.sync()
                    }
                }
                val observed = digest.digest().joinToString("") {
                    "%02x".format(it.toInt() and 0xff)
                }
                if (written != candidate.size || !observed.equals(candidate.digest, ignoreCase = true)) {
                    throw IOException("Release checksum or byte count mismatch; old engine kept")
                }
            } finally {
                connection.disconnect()
            }
            // Do not swap while a video is actively downloading.
            if (Downloads.items.value.any { it.status == Status.DOWNLOADING }) {
                throw IOException("Downloads started during update; retry when idle")
            }
            if (backup.exists() && !backup.delete()) throw IOException("Cannot clear prior backup")
            if (target.exists()) {
                if (!target.renameTo(backup)) throw IOException("Cannot back up current engine")
                movedOld = true
            }
            if (!stage.renameTo(target)) throw IOException("Cannot activate verified engine")
            movedNew = true
            if (!prefs.edit().putString("dlpVersion", candidate.tag)
                    .putString("dlpVersionName", "yt-dlp " + candidate.tag).commit()) {
                throw IOException("Unable to save installed version")
            }
            if (movedOld && !backup.delete()) {
                Log.w("NexonicYtDlpUpdater", "Old backup remains for later recovery")
            }
        } catch (e: Exception) {
            if (movedNew) target.delete()
            if (movedOld && backup.exists() && !backup.renameTo(target)) {
                Log.e("NexonicYtDlpUpdater", "Unable to restore yt-dlp after update failure")
            }
            // Restore metadata if anything failed after the new binary was activated.
            if (movedNew) {
                prefs.edit().putString("dlpVersion", oldVersion)
                    .putString("dlpVersionName", oldName).commit()
            }
            throw e
        } finally {
            stage.delete()
        }
    }
}
