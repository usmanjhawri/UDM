package com.nexonic.downloadmanager.core

import org.junit.Assert.*
import org.junit.Test

class YouTubePreviewPolicyTest {
    @Test fun canonicalIdIgnoresTrackingAndShortLinkVariants() {
        val id = "ABC123abc_9"
        assertEquals(id, YouTubePreviewPolicy.canonicalId("https://www.youtube.com/watch?v=$id&t=91"))
        assertEquals(id, YouTubePreviewPolicy.canonicalId("https://m.youtube.com/shorts/$id?feature=share"))
        assertEquals(id, YouTubePreviewPolicy.canonicalId("https://youtu.be/$id?si=sharetoken"))
    }

    @Test fun noFastPathForUnsafeOrSpecialSources() {
        val id = "ABC123abc_9"
        val rejected = listOf(
            "https://youtube.com/live/$id", "https://music.youtube.com/watch?v=$id",
            "https://youtube.com/embed/$id", "https://youtube.com/clip/$id",
            "https://youtube.com/watch?v=bad", "https://youtube.com.evil.test/watch?v=$id",
            "https://youtube.com@evil.test/watch?v=$id", "https://example.com/watch?v=$id",
            "https://youtube.com/playlist?list=PL123", "file:///watch?v=$id"
        )
        rejected.forEach { assertNull("Unexpected fast path: $it", YouTubePreviewPolicy.canonicalId(it)) }
    }

    @Test fun metadataCacheExpiresAndEvicts() {
        var now = 1_000L
        val cache = PreviewMemoryCache<String>(maxEntries = 2, ttlMs = 120L, clockMs = { now })
        cache.save("a", "720p")
        cache.save("b", "360p")
        assertEquals("720p", cache.find("a"))
        cache.save("c", "1080p")
        assertNull(cache.find("b"))
        now += 121
        assertNull(cache.find("a"))
        assertNull(cache.find("c"))
        cache.save("d", "audio")
        cache.clear()
        assertNull(cache.find("d"))
    }
}
