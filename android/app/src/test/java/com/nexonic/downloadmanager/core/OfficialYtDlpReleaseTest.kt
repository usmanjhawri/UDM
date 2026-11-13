package com.nexonic.downloadmanager.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfficialYtDlpReleaseTest {
    @Test fun matchesOnlyOfficialSizeAndSha() {
        val correct = OfficialYtDlpRelease.SHA256.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        assertTrue(OfficialYtDlpRelease.valid(3073072, correct))
        assertFalse(OfficialYtDlpRelease.valid(3073071, correct))
        assertFalse(OfficialYtDlpRelease.valid(3073073, correct))
    }

    @Test fun neverAcceptsOtherCode() {
        assertFalse(OfficialYtDlpRelease.valid(3073072, ByteArray(32)))
        assertFalse(OfficialYtDlpRelease.valid(0, ByteArray(32)))
    }
}
