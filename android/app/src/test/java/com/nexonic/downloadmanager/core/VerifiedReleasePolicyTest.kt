package com.nexonic.downloadmanager.core

import org.junit.Assert.*
import org.junit.Test

class VerifiedReleasePolicyTest {
    @Test fun comparesDateVersionsNumerically() {
        assertTrue(VerifiedReleasePolicy.isNewer("2026.10.26", "2026.9.30"))
        assertFalse(VerifiedReleasePolicy.isNewer("2026.10.26", "2026.10.26"))
        assertFalse(VerifiedReleasePolicy.isNewer("2025.12.31", "2026.10.26"))
        assertFalse(VerifiedReleasePolicy.isNewer("javascript:alert(1)", "2026.10.26"))
    }
    @Test fun requiresExactTrustedAssetAndDigest() {
        val tag = "2026.10.26"
        val good = "https://github.com/yt-dlp/yt-dlp/releases/download/$tag/yt-dlp"
        val digest = "sha256:" + "a".repeat(64)
        VerifiedReleasePolicy.validateAsset(tag, good, 3073072L, digest)
        assertEquals("a".repeat(64), VerifiedReleasePolicy.expectedDigest(digest))
        assertNull(VerifiedReleasePolicy.expectedDigest("sha256:NOT-A-HASH"))
        assertThrows(IllegalArgumentException::class.java) {
            VerifiedReleasePolicy.validateAsset(tag, "https://evil.example/yt-dlp", 3073072L, digest)
        }
        assertThrows(IllegalArgumentException::class.java) {
            VerifiedReleasePolicy.validateAsset(tag, good, 3073072L, null)
        }
        assertThrows(IllegalArgumentException::class.java) {
            VerifiedReleasePolicy.validateAsset(tag, good, 100L * 1024 * 1024, digest)
        }
    }
}
