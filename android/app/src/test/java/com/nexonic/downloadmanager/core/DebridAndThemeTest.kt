package com.nexonic.downloadmanager.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class DebridAndThemeTest {
    @Test fun debrid() {
        assertTrue(Debrid.applies("https://www.filehost.example/f/1", DebridService.REAL_DEBRID, "k", "filehost.example, other.example"))
        assertFalse(Debrid.applies("https://example.com/x", DebridService.REAL_DEBRID, "k", "filehost.example"))
        assertFalse(Debrid.applies("https://filehost.example/x", DebridService.NONE, "k", "filehost.example"))
        assertEquals("https://dl.example/x.bin", Debrid.parseAnswer(DebridService.REAL_DEBRID, """{"download":"https://dl.example/x.bin"}"""))
        assertEquals("https://ad.example/x", Debrid.parseAnswer(DebridService.ALL_DEBRID, """{"status":"success","data":{"link":"https://ad.example/x"}}"""))
        assertEquals("https://pm.example/x", Debrid.parseAnswer(DebridService.PREMIUMIZE, """{"status":"success","content":[{"link":"https://pm.example/x"}]}"""))
        val r = Debrid.request(DebridService.REAL_DEBRID, "k", "https://filehost.example/f")
        assertEquals("k", r.bearer)
        assertEquals("link=https%3A%2F%2Ffilehost.example%2Ff", Debrid.formBody(r.form))
        try { Debrid.parseAnswer(DebridService.REAL_DEBRID, """{"error":"bad_token"}"""); throw AssertionError() } catch (e: IOException) { assertTrue(e.message!!.contains("bad_token")) }
    }

    @Test fun colorSchemes() {
        assertEquals(listOf("Ocean", "Forest", "Midnight", "High Contrast"), ColorSchemeChoice.builtIn.map { it.name })
        assertNull(ColorSchemeChoice.named("Nope"))
    }
}
