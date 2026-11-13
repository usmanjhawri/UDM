package com.nexonic.downloadmanager.video

import android.content.Context
import android.util.Log
import com.nexonic.downloadmanager.core.OfficialYtDlpRelease
import com.nexonic.downloadmanager.core.VerifiedReleasePolicy
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest

/**
 * Ships one known-good yt-dlp version inside the APK for first-run recovery even
 * when GitHub is unreachable from the app. The binary is added by CI with a pinned SHA.
 */
object BundledYtDlpBootstrap {
    private const val ASSET = "verified-yt-dlp-2026.10.26"
    private const val TAG = "NexonicBundledYtDlp"

    fun installIfNeeded(context: Context): Boolean {
        val prefs = context.getSharedPreferences("youtubedl-android", Context.MODE_PRIVATE)
        val current = prefs.getString("dlpVersion", null)
        if (!VerifiedReleasePolicy.isNewer(OfficialYtDlpRelease.TAG, current)) return false

        val dir = File(context.noBackupFilesDir, "youtubedl-android/yt-dlp")
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("Cannot create yt-dlp directory")
        val target = File(dir, "yt-dlp")
        val stage = File(dir, "yt-dlp.bundled-stage")
        val backup = File(dir, "yt-dlp.bundled-backup")
        val previousName = prefs.getString("dlpVersionName", null)
        var backedUp = false
        var switched = false
        try {
            if (backup.exists() && !target.exists() && !backup.renameTo(target)) {
                throw IOException("Cannot restore previous yt-dlp binary")
            }
            if (stage.exists() && !stage.delete()) throw IOException("Cannot clear temporary binary")
            val digest = MessageDigest.getInstance("SHA-256")
            var size = 0L
            context.assets.open(ASSET).use { source ->
                FileOutputStream(stage).use { output ->
                    val buffer = ByteArray(32 * 1024)
                    while (true) {
                        val count = source.read(buffer)
                        if (count < 0) break
                        size += count
                        if (size > OfficialYtDlpRelease.MAX_BYTES) throw IOException("Bundled engine too large")
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                    }
                    output.fd.sync()
                }
            }
            if (!OfficialYtDlpRelease.valid(size, digest.digest())) {
                throw IOException("Bundled yt-dlp checksum does not match release")
            }
            if (backup.exists() && !backup.delete()) throw IOException("Cannot clear previous backup")
            if (target.exists()) {
                if (!target.renameTo(backup)) throw IOException("Cannot back up existing yt-dlp")
                backedUp = true
            }
            if (!stage.renameTo(target)) throw IOException("Cannot install verified bundled yt-dlp")
            switched = true
            if (!prefs.edit()
                    .putString("dlpVersion", OfficialYtDlpRelease.TAG)
                    .putString("dlpVersionName", "yt-dlp " + OfficialYtDlpRelease.TAG)
                    .commit()) throw IOException("Cannot save bundled engine version")
            val now = System.currentTimeMillis()
            context.getSharedPreferences("video-engine", Context.MODE_PRIVATE)
                .edit().putLong("updated", now).putLong("lastAttempt", now).apply()
            if (backedUp) backup.delete()
            Log.i(TAG, "Installed verified bundled yt-dlp " + OfficialYtDlpRelease.TAG)
            return true
        } catch (e: Exception) {
            if (switched) target.delete()
            if (backedUp && backup.exists() && !backup.renameTo(target)) {
                Log.e(TAG, "Could not restore previous yt-dlp", e)
            }
            if (switched) {
                prefs.edit().putString("dlpVersion", current)
                    .putString("dlpVersionName", previousName).commit()
            }
            throw e
        } finally {
            stage.delete()
        }
    }
}
