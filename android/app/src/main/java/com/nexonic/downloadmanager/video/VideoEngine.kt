package com.nexonic.downloadmanager.video

import android.content.Context
import android.net.Uri
import android.util.Log
import com.nexonic.downloadmanager.core.Job
import com.nexonic.downloadmanager.core.StreamFormat
import com.nexonic.downloadmanager.core.VideoChoice
import com.nexonic.downloadmanager.core.VideoPages
import com.nexonic.downloadmanager.core.Playlists
import com.nexonic.downloadmanager.core.EngineUpdateDiagnostics
import com.nexonic.downloadmanager.core.OfficialYtDlpRelease
import com.nexonic.downloadmanager.data.Downloads
import com.nexonic.downloadmanager.data.Status
import com.nexonic.downloadmanager.core.VideoSubtitles
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

/** What the video engine found on a page. */
data class VideoPage(val title: String?, val choices: List<VideoChoice>, val durationSeconds: Int)

class VideoException(message: String) : Exception(message)

/**
 * The video engine (yt-dlp with ffmpeg, the same engine as the Mac and Windows apps). It reads video pages from
 * YouTube, Facebook, Instagram, TikTok and 1,000+ other sites and saves the video itself, never the web page.
 */
object VideoEngine {
    private val lock = Any()
    // The library must never update its Python/yt-dlp files concurrently with another update.
    private val updateLock = Any()
    @Volatile private var ready = false
    private lateinit var app: Context
    @Volatile private var updateError: String? = null
    @Volatile private var initError: String? = null
    @Volatile private var bootstrapWarning: String? = null

    /** Last human-readable status; no URLs, cookies, or raw credentials are included. */
    fun lastProblem(): String? = updateError ?: initError ?: bootstrapWarning

    /** Warm the runtime asynchronously; failure is reported in Settings instead of being swallowed. */
    fun init(context: Context) {
        app = context.applicationContext
        Thread({
            runCatching { ensure(); updateIfDue() }.onFailure {
                updateError = EngineUpdateDiagnostics.describe(it)
                Log.w(TAG, "yt-dlp startup/update check failed", it)
            }
        }, "nexonic-video-init").start()
    }

    private fun ensure() {
        if (ready) return
        synchronized(lock) {
            if (ready) return
            try {
                YoutubeDL.getInstance().init(app)
                // Fresh installs get a verified working engine even when the release
                // API or asset CDN is blocked from Android's HTTP stack.
                try {
                    BundledYtDlpBootstrap.installIfNeeded(app)
                    bootstrapWarning = null
                } catch (e: java.io.FileNotFoundException) {
                    // A locally-built APK might not contain the CI-generated asset.
                    // Keep yt-dlp-android initialized so users can import the verified
                    // binary manually; one absent optional asset must not kill the engine.
                    bootstrapWarning = "Bundled yt-dlp asset missing. Import the official verified file in Settings."
                    Log.w(TAG, "Optional bundled yt-dlp asset is missing; manual import available", e)
                } catch (e: Exception) {
                    // A failed bootstrap must never block rollback or manual recovery.
                    bootstrapWarning = "Bundled yt-dlp could not be installed: " +
                        EngineUpdateDiagnostics.describe(e)
                    Log.w(TAG, "Optional yt-dlp bootstrap failed; keeping library available", e)
                }
                // Old native FFmpeg packages are no longer used: the APK ships its own FFmpeg.
                File(app.noBackupFilesDir, "youtubedl-android/packages/ffmpeg")
                    .takeIf { it.exists() }?.deleteRecursively()
                ready = true
                initError = null
            } catch (e: Throwable) {
                val message = VideoPages.engineError(e.javaClass.simpleName, e.message)
                initError = EngineUpdateDiagnostics.describe(e)
                Log.e(TAG, "video engine start failed", e)
                throw VideoException(message)
            }
        }
    }

    /** Automated stable update: no competing updates, no repeat attempt on every app reopen. */
    private fun updateIfDue() = synchronized(updateLock) {
        ensure()
        val prefs = app.getSharedPreferences("video-engine", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val lastSuccess = prefs.getLong("updated", 0)
        if (now - lastSuccess < 24L * 3600 * 1000) return@synchronized
        // A failed background check may be retried later; manual checks ignore this cooldown.
        val lastAttempt = prefs.getLong("lastAttempt", 0)
        if (now - lastAttempt < 60L * 60 * 1000) return@synchronized
        prefs.edit().putLong("lastAttempt", now).apply()
        val result = VerifiedYtDlpUpdater.update(app, YoutubeDL.getInstance().version(app))
        if (result == "DEFERRED_ACTIVE_DOWNLOADS") return@synchronized
        prefs.edit().putLong("updated", System.currentTimeMillis()).apply()
        updateError = null
    }

    /** Initialize before version lookup and serialize with the update operation. */
    fun version(): String? = runCatching {
        synchronized(updateLock) {
            ensure()
            YoutubeDL.getInstance().version(app)
        }
    }.onFailure {
        initError = EngineUpdateDiagnostics.describe(it)
        Log.w(TAG, "yt-dlp version unavailable", it)
    }.getOrNull()

    /**
     * A user-triggered check, stable by default. Nightly is an explicit opt-in when stable
     * cannot fix a site. Never silently replace the installed runtime with a nightly build.
     */
    fun updateNow(nightly: Boolean = false): String = synchronized(updateLock) {
        try {
            ensure()
            if (nightly) {
                throw VideoException("Nightly updates are disabled until independent release verification is available.")
            }
            val result = VerifiedYtDlpUpdater.update(app, YoutubeDL.getInstance().version(app))
            if (result != "DEFERRED_ACTIVE_DOWNLOADS") {
                app.getSharedPreferences("video-engine", Context.MODE_PRIVATE).edit()
                    .putLong("updated", System.currentTimeMillis())
                    .putLong("lastAttempt", System.currentTimeMillis()).apply()
                updateError = null
                bootstrapWarning = null
            }
            result
        } catch (e: Throwable) {
            updateError = EngineUpdateDiagnostics.describe(e)
            Log.w(TAG, "yt-dlp update failed", e)
            throw VideoException("Update failed: $updateError")
        }
    }

    /**
     * Offline recovery for the user-downloaded official yt-dlp 2026.10.26 binary.
     * Only the byte-for-byte SHA-256-pinned release is accepted.
     * A backup and same-directory rename protect the previous executable.
     */
    fun importVerifiedRelease(uri: Uri): String = synchronized(updateLock) {
        ensure()
        if (Downloads.items.value.any { it.status == Status.DOWNLOADING }) {
            throw VideoException("Pause active downloads before installing a video engine.")
        }
        val release = OfficialYtDlpRelease
        val dir = File(app.noBackupFilesDir, "youtubedl-android/yt-dlp")
        if (!dir.isDirectory && !dir.mkdirs()) {
            throw VideoException("Cannot open the private yt-dlp folder.")
        }
        val target = File(dir, "yt-dlp")
        val staged = File(dir, "yt-dlp.stage")
        val backup = File(dir, "yt-dlp.backup")
        var backupMade = false
        var installed = false
        try {
            if (backup.exists() && !target.exists() && !backup.renameTo(target)) {
                throw IOException("Could not recover previous engine backup")
            }
            if (staged.exists() && !staged.delete()) throw IOException("Cannot clear old import staging file")
            val sha = MessageDigest.getInstance("SHA-256")
            var length = 0L
            val input = app.contentResolver.openInputStream(uri) ?: throw IOException("Cannot read selected file")
            input.use { source ->
                FileOutputStream(staged).use { destination ->
                    val buffer = ByteArray(32768)
                    while (true) {
                        val count = source.read(buffer)
                        if (count < 0) break
                        length += count
                        if (length > release.MAX_BYTES) throw IOException("Selected file is too large")
                        sha.update(buffer, 0, count)
                        destination.write(buffer, 0, count)
                    }
                    destination.fd.sync()
                }
            }
            if (!release.valid(length, sha.digest())) {
                throw IOException("File does not match official yt-dlp 2026.10.26 (SHA-256 mismatch)")
            }
            if (backup.exists() && !backup.delete()) throw IOException("Cannot clear old backup")
            if (target.exists()) {
                if (!target.renameTo(backup)) throw IOException("Cannot back up current engine")
                backupMade = true
            }
            if (!staged.renameTo(target)) throw IOException("Cannot activate verified engine")
            installed = true
            val prefs = app.getSharedPreferences("youtubedl-android", Context.MODE_PRIVATE)
            if (!prefs.edit()
                    .putString("dlpVersion", release.TAG)
                    .putString("dlpVersionName", "yt-dlp " + release.TAG)
                    .commit()) throw IOException("Could not save engine version")
            val now = System.currentTimeMillis()
            app.getSharedPreferences("video-engine", Context.MODE_PRIVATE).edit()
                .putLong("updated", now)
                .putLong("lastAttempt", now)
                .apply()
            updateError = null
            initError = null
            bootstrapWarning = null
            if (backupMade) backup.delete()
            return@synchronized release.TAG
        } catch (e: Exception) {
            if (installed) target.delete()
            if (backupMade && backup.exists() && !backup.renameTo(target)) {
                Log.e(TAG, "Unable to restore previous yt-dlp backup")
            }
            updateError = EngineUpdateDiagnostics.describe(e)
            Log.w(TAG, "yt-dlp offline import failed", e)
            throw VideoException("Offline import failed: $updateError")
        } finally {
            staged.delete()
        }
    }

    /** Public media links are extracted directly by yt-dlp; the embedded browser was removed. */
    private fun base(url: String, userAgent: String?, referer: String?) = YoutubeDLRequest(url).apply {
        addOption("--no-playlist")
        com.nexonic.downloadmanager.data.Vpn.sourceAddress()?.let { addOption("--source-address", it) }
        addOption("--no-mtime")
        addOption("--socket-timeout", "30")
        userAgent?.let { addOption("--user-agent", it) }
        referer?.let { addOption("--referer", it) }
    }

    /** Reads the page: title and quality choices. Throws [VideoException] with a readable message. */
    fun read(url: String, userAgent: String? = null, referer: String? = null): VideoPage {
        ensure()
        val target = resolve(url)
        val info = try {
            YoutubeDL.getInstance().getInfo(base(target, userAgent, referer))
        } catch (e: Exception) {
            Log.w(TAG, "read failed", e)
            throw VideoException(VideoPages.friendlyError(e.message))
        } catch (e: Throwable) {
            Log.e(TAG, "read failed", e)
            throw VideoException(VideoPages.engineError(e.javaClass.simpleName, e.message))
        }
        val formats = info.formats.orEmpty().map { f ->
            StreamFormat(
                height = f.height,
                // Some sites (Instagram, Facebook) leave the codecs out: a format with a height is a video then.
                hasVideo = if (f.vcodec == null) f.height > 0 else f.vcodec != "none",
                hasAudio = if (f.acodec == null) f.vcodec == null else f.acodec != "none",
                ext = f.ext,
                size = f.fileSize.takeIf { it > 0 } ?: f.fileSizeApproximate.takeIf { it > 0 },
            )
        }
        return VideoPage(info.title, VideoPages.choices(formats), info.duration)
    }

    /**
     * Downloads [choice] into [dir] (an empty folder of its own). Calls [progress] with 0–100. Returns the finished file.
     * yt-dlp keeps its .part files, so a paused download continues where it stopped.
     */
    fun download(id: String, url: String, choice: VideoChoice, dir: File, rateKB: Long, userAgent: String?, referer: String?,
                 subtitles: Boolean = false, audioFormat: String = "original", progress: (Float, Long, Long?, Long?) -> Unit): File {
        ensure()
        dir.mkdirs()
        val target = resolve(url)
        val req = base(target, userAgent, referer).apply {
            addOption("-f", choice.selector)
            // Sites whose videos all have the same title ("Video by name"): named after the caption, with the video's id
            // so two videos never share a name (the engine would skip the second as "already downloaded").
            val captioned = runCatching { java.net.URI(url).host?.lowercase().orEmpty() }.getOrDefault("").let { h ->
                listOf("instagram.com", "facebook.com", "fb.watch", "x.com", "twitter.com", "snapchat.com", "threads.net", "threads.com").any { h == it || h.endsWith(".$it") }
            }
            if (captioned) {
                addOption("--parse-metadata", "%(description,title)s:(?s)(?P<title>.+)")
                addOption("-o", File(dir, "%(title).100B [%(id)s].%(ext)s").absolutePath)
            } else addOption("-o", File(dir, "%(title).100B.%(ext)s").absolutePath)
            addOption("--concurrent-fragments", "4")
            if (choice.audioOnly) addOption("--no-keep-video") else addOption("--merge-output-format", "mp4")
            // Title, artist, date, description and chapters stay inside the saved file (as on the desktop apps).
            addOption("--embed-metadata")
            addOption("--embed-chapters")
            if (!choice.audioOnly && subtitles) VideoSubtitles.arguments().forEach { addOption(it) }
            // Sound only: convert to MP3, Opus or FLAC when chosen in Settings (the bundled FFmpeg does it).
            if (choice.audioOnly && audioFormat in setOf("mp3", "opus", "flac")) {
                addOption("-x")
                addOption("--audio-format", audioFormat)
                if (audioFormat == "mp3") addOption("--audio-quality", "0")
            }
            if (rateKB > 0) addOption("--limit-rate", "${rateKB}K")
        }
        try {
            YoutubeDL.getInstance().execute(req, id, false) { p, eta, line ->
                if (p >= 0) { val (total, speed) = VideoPages.parseProgress(line); progress(p, eta, total, speed) }
            }
        } catch (e: YoutubeDL.CanceledException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "download failed", e)
            throw VideoException(VideoPages.friendlyError(e.message))
        } catch (e: Throwable) {
            Log.e(TAG, "download failed", e)
            throw VideoException(VideoPages.engineError(e.javaClass.simpleName, e.message))
        }
        return dir.listFiles()?.filter { it.isFile && !it.name.endsWith(".part") && !it.name.endsWith(".ytdl") && it.length() > 0 }
            ?.maxByOrNull { it.length() }
            ?: throw VideoException("The video could not be saved.")
    }

    /**
     * Lists the videos of a playlist or channel without downloading them (up to [Playlists.MAX_ENTRIES], two minutes at
     * most). Throws [VideoException] with a readable message.
     */
    fun readPlaylist(url: String): Playlists.Result {
        ensure()
        val target = Playlists.normalize(url)
        val req = YoutubeDLRequest(target).apply {
            com.nexonic.downloadmanager.data.Vpn.sourceAddress()?.let { addOption("--source-address", it) }
            addOption("--flat-playlist")
            addOption("-J")
            addOption("--playlist-end", Playlists.MAX_ENTRIES.toString())
            addOption("--socket-timeout", "30")
        }
        val pid = "playlist-" + System.nanoTime()
        val timer = Thread({
            try { Thread.sleep(120_000); cancel(pid) } catch (e: InterruptedException) { }
        }, "nexonic-playlist-timer").apply { isDaemon = true; start() }
        val out = try {
            YoutubeDL.getInstance().execute(req, pid).out
        } catch (e: Exception) {
            Log.w(TAG, "playlist read failed", e)
            throw VideoException(VideoPages.friendlyError(e.message))
        } catch (e: Throwable) {
            Log.e(TAG, "playlist read failed", e)
            throw VideoException(VideoPages.engineError(e.javaClass.simpleName, e.message))
        } finally {
            timer.interrupt()
        }
        val r = Playlists.parse(out) ?: throw VideoException("The list of videos could not be read.")
        if (r.entries.isEmpty()) throw VideoException("No videos were found at this link.")
        return r
    }

    /**
     * Profile Batch: lists the videos at [url] without any login or cookies (the site's pages are followed by the engine),
     * from video number [start]. [line] gets each printed entry as it comes. Throws [VideoException] with a readable reason.
     */
    fun discover(url: String, start: Int, max: Int, stopId: String, line: (String) -> Unit): String {
        ensure()
        val req = YoutubeDLRequest(Playlists.normalize(url)).apply {
            com.nexonic.downloadmanager.data.Vpn.sourceAddress()?.let { addOption("--source-address", it) }
            com.nexonic.downloadmanager.core.ProfileBatch.options(start, max).forEach { (k, v) -> if (v == null) addOption(k) else addOption(k, v) }
            plugins()?.let { addOption("--plugin-dirs", it) }
        }
        lastNote = ""
        return try {
            val r = YoutubeDL.getInstance().execute(req, stopId, false) { _, _, l -> l.lineSequence().forEach(line) }
            lastNote = com.nexonic.downloadmanager.core.ProfileBatch.note(r.err)
            r.out
        } catch (e: YoutubeDL.CanceledException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "profile scan failed", e)
            throw VideoException(VideoPages.friendlyError(e.message))
        } catch (e: Throwable) {
            Log.e(TAG, "profile scan failed", e)
            throw VideoException(VideoPages.engineError(e.javaClass.simpleName, e.message))
        }
    }

    /** What the last profile scan's site said about the list (for example that visitors see only the latest posts). */
    @Volatile var lastNote = ""
        private set

    /**
     * The profile listers Nexonic adds to the video engine (engine/nexonic_profiles.py, shipped as an app asset): they only read what
     * a site shows visitors who are not signed in. Copied to the app's files when missing or changed.
     */
    private fun plugins(): String? = runCatching {
        val rel = "nexonic/yt_dlp_plugins/extractor/nexonic_profiles.py"
        val bytes = app.assets.open("nexonic_profiles.py").use { it.readBytes() }
        val root = java.io.File(app.filesDir, "engine-plugins")
        val file = java.io.File(root, rel)
        if (!file.exists() || !file.readBytes().contentEquals(bytes)) { file.parentFile?.mkdirs(); file.writeBytes(bytes) }
        root.absolutePath
    }.getOrNull()

    /** Short share links (facebook.com/share/…, fb.watch) point to the real video page: follow them first. */
    private fun resolve(url: String): String {
        if (!VideoPages.isShareLink(url)) return url
        var current = url
        repeat(5) {
            val conn = (java.net.URL(current).let { com.nexonic.downloadmanager.core.Net.opener?.invoke(it) ?: it.openConnection() } as java.net.HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 15000; readTimeout = 15000
                setRequestProperty("User-Agent", DESKTOP_UA)
            }
            val next = runCatching { if (conn.responseCode in 300..399) conn.getHeaderField("Location") else null }.getOrNull()
            conn.disconnect()
            if (next.isNullOrBlank()) return current
            current = java.net.URL(java.net.URL(current), next).toString()
            if (VideoPages.isLoginPage(current)) return url
        }
        return current
    }

    private const val TAG = "NexonicVideo"
    private const val DESKTOP_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36"

    fun cancel(id: String) { runCatching { YoutubeDL.getInstance().destroyProcessById(id) } }
}

/** Runs one video download on its own thread, like [com.nexonic.downloadmanager.core.Transfer] does for files. */
class VideoJob(
    private val id: Long,
    private val url: String,
    private val choice: VideoChoice,
    private val dir: File,
    private val rateKB: Long,
    private val userAgent: String?,
    private val referer: String?,
    /** Percent, seconds left, total bytes and bytes per second (when yt-dlp reports them). */
    private val onProgress: (Float, Long, Long?, Long?) -> Unit,
    private val onDone: (File) -> Unit,
    private val onFail: (String) -> Unit,
) : Job {
    private val stopped = AtomicBoolean(false)

    override fun start() {
        Thread({
            try {
                val file = VideoEngine.download(id.toString(), url, choice, dir, rateKB, userAgent, referer,
                    com.nexonic.downloadmanager.data.Downloads.settings.videoSubtitles,
                    com.nexonic.downloadmanager.data.Downloads.settings.audioFormat) { p, eta, total, speed ->
                    if (!stopped.get()) onProgress(p, eta, total, speed)
                }
                if (!stopped.get()) onDone(file)
            } catch (e: Throwable) {
                if (!stopped.get()) onFail(e.message ?: "The video could not be saved.")
            }
        }, "nexonic-video-$id").start()
    }

    override fun pause() {
        stopped.set(true)
        VideoEngine.cancel(id.toString())
    }
}
