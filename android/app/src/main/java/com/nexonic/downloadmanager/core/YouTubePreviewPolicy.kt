package com.nexonic.downloadmanager.core

import java.net.URI
import java.util.Locale

/**
 * Deliberately narrow video-only fast path. Don't apply request-skipping to live,
 * music, playlists, embed, clips, signed or non-YouTube links.
 */
object YouTubePreviewPolicy {
    private val videoId = Regex("[A-Za-z0-9_-]{11}")
    const val FAST_EXTRACTOR_ARGS = "youtube:player_skip=initial_data"

    /** Stable identity without watch tracking params or expiring stream URLs. */
    fun canonicalId(raw: String): String? {
        if (raw.length > 4096) return null
        val uri = runCatching { URI(raw.trim()) }.getOrNull() ?: return null
        if (uri.scheme?.lowercase(Locale.ROOT) !in setOf("https", "http")) return null
        if (uri.userInfo != null) return null
        if (uri.port !in -1..0 && uri.port != 443 && uri.port != 80) return null
        val host = uri.host?.lowercase(Locale.ROOT)?.removePrefix("www.")?.removePrefix("m.")
            ?: return null
        val path = uri.path.orEmpty()
        val id = when (host) {
            "youtu.be" -> path.removePrefix("/").takeIf { '/' !in it }
            "youtube.com" -> when {
                path == "/watch" -> uri.rawQuery.orEmpty().split('&')
                    .firstOrNull { it.startsWith("v=") }?.substringAfter("v=")
                path.startsWith("/shorts/") -> path.removePrefix("/shorts/").takeIf { '/' !in it }
                else -> null
            }
            else -> null
        }
        return id?.takeIf { videoId.matches(it) }
    }
}

/** In-memory only: title + quality selectors, never signed media URLs, cookies, or auth. */
class PreviewMemoryCache<T>(
    private val maxEntries: Int = 12,
    private val ttlMs: Long = 120_000L,
    private val clockMs: () -> Long = { System.nanoTime() / 1_000_000L },
) {
    private data class Entry<T>(val storedAt: Long, val value: T)
    private val data = LinkedHashMap<String, Entry<T>>(16, 0.75f, true)

    @Synchronized fun find(key: String): T? {
        val found = data[key] ?: return null
        if (clockMs() - found.storedAt >= ttlMs) {
            data.remove(key)
            return null
        }
        return found.value
    }

    @Synchronized fun save(key: String, value: T) {
        data[key] = Entry(clockMs(), value)
        while (data.size > maxEntries) {
            data.remove(data.keys.first())
        }
    }

    @Synchronized fun clear() = data.clear()
}
