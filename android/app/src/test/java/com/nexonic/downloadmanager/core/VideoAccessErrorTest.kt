package com.nexonic.downloadmanager.core

import org.junit.Assert.assertTrue
import org.junit.Test

class VideoAccessErrorTest {
    @Test fun media403IsNotMisreportedAsResumeFailure() {
        val message = VideoPages.friendlyError("ERROR: unable to download video data: HTTP Error 403: Forbidden")
        assertTrue(message.contains("HTTP 403"))
        assertTrue(message.contains("Update the video engine"))
    }

    @Test fun unrelatedServerFailureIsNotMislabeled403() {
        val message = VideoPages.friendlyError("ERROR: unable to download video data: HTTP Error 503: Service Unavailable")
        assertTrue(!message.contains("HTTP 403"))
    }
}
