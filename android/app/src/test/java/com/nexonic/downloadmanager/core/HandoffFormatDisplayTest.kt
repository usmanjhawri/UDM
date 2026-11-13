package com.nexonic.downloadmanager.core

import org.junit.Assert.assertEquals
import org.junit.Test

class HandoffFormatDisplayTest {
    @Test fun videoQualitiesAreLimitsNotPromises() {
        assertEquals("Up to 720p · MP4",
            HandoffFormatDisplay.label(VideoChoice("HD (720p)", "bv*[height<=720]+ba/b", "mp4", null)))
        assertEquals("Up to 1080p · MP4",
            HandoffFormatDisplay.label(VideoChoice("Full HD (1080p)", "bv*[height<=1080]+ba/b", "mp4", null)))
    }
    @Test fun audioAndAutoHaveFactualLabels() {
        assertEquals("M4A",
            HandoffFormatDisplay.label(VideoChoice("Sound only (M4A)", "ba", "m4a", null, audioOnly = true)))
        assertEquals("Auto · MP4",
            HandoffFormatDisplay.label(VideoChoice("Best quality", "bv*+ba/b", "mp4", null)))
    }
}
