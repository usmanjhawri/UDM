package com.nexonic.downloadmanager.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.UnknownHostException
import java.net.SocketTimeoutException

class EngineUpdateDiagnosticsTest {
    @Test fun dnsErrorHasActionableMessage() {
        val message = EngineUpdateDiagnostics.describe(UnknownHostException("api.github.com"))
        assertTrue(message.contains("DNS"))
        assertFalse(message.contains("api.github.com"))
    }

    @Test fun timeoutRemainsDistinctFromAccessDenial() {
        val message = EngineUpdateDiagnostics.describe(SocketTimeoutException("timed out"))
        assertTrue(message.contains("timed out"))
        assertFalse(message.contains("HTTP 403"))
    }

    @Test fun genericFailureRedactsSignedLinksAndTokens() {
        val message = EngineUpdateDiagnostics.describe(
            IOException("Failed fetching https://example.com/file?token=secret123 signature=secret456"))
        assertFalse(message.contains("secret123"))
        assertFalse(message.contains("secret456"))
        assertTrue(message.contains("[link]"))
    }

    @Test fun nestedCausesAreClassified() {
        val message = EngineUpdateDiagnostics.describe(IllegalStateException("Download failed",
            IOException("HTTP Error 403")))
        assertTrue(message.contains("403"))
    }
}
