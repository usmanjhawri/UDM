package com.nexonic.downloadmanager.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HandoffQualityPolicyTest {
    private val options = listOf(
        HandoffFormatHint(false, 1080), HandoffFormatHint(false, 720),
        HandoffFormatHint(false, 480), HandoffFormatHint(true, null)
    )

    @Test fun rememberedChoiceMapping() {
        val cases = listOf(
            Triple(false, 720, 1), Triple(false, 900, 1),
            Triple(false, 480, 2), Triple(false, 240, 2),
            Triple(true, null, 3), Triple(false, null, 0)
        )
        cases.forEach { (audio, height, expected) ->
            assertEquals(expected, HandoffQualityPolicy.choose(options, audio, height))
        }
        assertEquals(0, HandoffQualityPolicy.choose(options, null, null))
        assertEquals(0, HandoffQualityPolicy.choose(options.take(3), true, null))
        assertEquals(-1, HandoffQualityPolicy.choose(emptyList(), false, 720))
    }

    @Test fun parseResolutionsOnly() {
        assertEquals(1080, HandoffQualityPolicy.heightFromLabel("Full HD (1080p)"))
        assertEquals(720, HandoffQualityPolicy.heightFromLabel("HD (720p)"))
        assertNull(HandoffQualityPolicy.heightFromLabel("Sound only (M4A)"))
    }
}
