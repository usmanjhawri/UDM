package com.nexonic.downloadmanager.core

import java.net.URI
import java.util.Locale

/** Validates only YouTube source-page URLs. yt-dlp remains the sole extractor. */
object MorpheHandoff {
    fun acceptedYouTubePage(raw: String?): String? {
        if (raw.isNullOrBlank() || raw.length > 8192) return null
        val value = raw.trim()
        val uri = runCatching { URI(value) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        if (scheme != "https" && scheme != "http") return null
        if (!uri.userInfo.isNullOrEmpty()) return null
        val host = uri.host?.lowercase(Locale.ROOT)?.removePrefix("www.")
            ?.removePrefix("m.") ?: return null
        val path = uri.path.orEmpty()
        val supported = when (host) {
            "youtu.be" -> path.trim('/').isNotEmpty()
            "youtube.com", "music.youtube.com" -> when {
                path == "/watch" -> uri.rawQuery.orEmpty().split('&').any { it.startsWith("v=") && it.length > 2 }
                path.startsWith("/shorts/") || path.startsWith("/live/") ||
                    path.startsWith("/embed/") || path.startsWith("/clip/") -> path.substringAfterLast('/').isNotEmpty()
                else -> false
            }
            else -> false
        }
        return value.takeIf { supported }
    }
}
