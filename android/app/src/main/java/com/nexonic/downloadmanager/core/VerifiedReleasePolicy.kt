package com.nexonic.downloadmanager.core

import java.util.Locale

/** Conservative release selection; network JSON is untrusted until validated. */
object VerifiedReleasePolicy {
    private val versionPattern = Regex("""\d{4}\.\d{1,2}\.\d{1,2}""")
    private val shaPattern = Regex("""sha256:[0-9a-fA-F]{64}""")
    const val MAX_RELEASE_BYTES = 12L * 1024 * 1024

    fun validVersion(tag: String) = versionPattern.matches(tag)

    fun isNewer(tag: String, installed: String?): Boolean {
        if (!validVersion(tag)) return false
        val old = installed.orEmpty().trim().removePrefix("yt-dlp ").trim()
        if (!validVersion(old)) return true // The bundled unversioned engine may be updated.
        val nextParts = tag.split('.').map(String::toInt)
        val oldParts = old.split('.').map(String::toInt)
        for (i in 0..2) {
            if (nextParts[i] != oldParts[i]) return nextParts[i] > oldParts[i]
        }
        return false
    }

    fun expectedDigest(raw: String?): String? {
        if (raw == null || !shaPattern.matches(raw)) return null
        return raw.substringAfter(':').lowercase(Locale.ROOT)
    }

    fun validateAsset(tag: String, url: String, size: Long, digest: String?) {
        require(validVersion(tag)) { "Unexpected yt-dlp release version" }
        require(size in 1..MAX_RELEASE_BYTES) { "Invalid yt-dlp release size" }
        require(expectedDigest(digest) != null) { "GitHub release has no verified SHA-256 digest" }
        require(url == "https://github.com/yt-dlp/yt-dlp/releases/download/$tag/yt-dlp") {
            "Unexpected yt-dlp release download address"
        }
    }
}
