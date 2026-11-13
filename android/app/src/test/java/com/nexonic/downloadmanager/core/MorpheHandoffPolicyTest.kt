package com.nexonic.downloadmanager.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MorpheHandoffPolicyTest {
    @Test fun allowedSourcePages() {
        val allowed = listOf(
            "https://www.youtube.com/watch?v=ABC123abc12",
            "https://youtu.be/ABC123abc12?t=10",
            "https://m.youtube.com/shorts/ABC123abc12",
            "https://music.youtube.com/watch?v=ABC123abc12",
            "https://youtube.com/live/ABC123abc12",
            "https://youtube.com/clip/ABC123abc12"
        )
        allowed.forEach { assertEquals(it, MorpheHandoff.acceptedYouTubePage(it)) }
    }
    @Test fun disallowNonYouTubeOrDangerousLinks() {
        val blocked = listOf<String?>(
            null, "", "javascript:alert(1)", "file:///etc/passwd", "https://youtube.com/",
            "https://youtube.com/@channel", "https://youtube.com/watch", "https://youtube.com/watch?v=",
            "https://youtube.com.evil.org/watch?v=ABC123abc12",
            "https://youtu.be.evil.org/ABC123abc12",
            "https://youtube.com@evil.com/watch?v=ABC123abc12",
            "https://googlevideo.com/path", "https://youtube.com/shorts/",
            "https://example.com/shorts/ABC123abc12"
        )
        blocked.forEach { assertNull(MorpheHandoff.acceptedYouTubePage(it)) }
    }
}
