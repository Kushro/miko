package eu.kanade.tachiyomi.util.waifu2x

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RemoteUpscalerFormatTest {

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }
    private fun ascii(text: String) = text.toByteArray(Charsets.US_ASCII)

    @Test
    fun `formats the server decodes are sent as-is`() {
        assertTrue(RemoteUpscaler.isServerDecodable(bytes(0xFF, 0xD8, 0xFF, 0xE0, 0, 0x10)))
        assertTrue(RemoteUpscaler.isServerDecodable(bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)))
        assertTrue(RemoteUpscaler.isServerDecodable(ascii("GIF89a")))
        assertTrue(RemoteUpscaler.isServerDecodable(ascii("RIFF") + bytes(0x24, 0, 0, 0) + ascii("WEBPVP8 ")))
        assertTrue(RemoteUpscaler.isServerDecodable(ascii("BM") + bytes(0x36, 0, 0, 0)))
    }

    @Test
    fun `AVIF, HEIF, JXL and unknown data need converting`() {
        // ISO-BMFF: 4-byte box size, then "ftyp" and the brand.
        assertFalse(RemoteUpscaler.isServerDecodable(bytes(0, 0, 0, 0x20) + ascii("ftypavif")))
        assertFalse(RemoteUpscaler.isServerDecodable(bytes(0, 0, 0, 0x18) + ascii("ftypheic")))
        assertFalse(RemoteUpscaler.isServerDecodable(bytes(0xFF, 0x0A, 0xFA)))
        assertFalse(RemoteUpscaler.isServerDecodable(bytes(0, 0, 0, 0x0C) + ascii("JXL ") + bytes(0x0D, 0x0A, 0x87, 0x0A)))
        // RIFF that isn't WebP (e.g. WAV)
        assertFalse(RemoteUpscaler.isServerDecodable(ascii("RIFF") + bytes(0x24, 0, 0, 0) + ascii("WAVE")))
        assertFalse(RemoteUpscaler.isServerDecodable(ByteArray(0)))
        assertFalse(RemoteUpscaler.isServerDecodable(ascii("<html>")))
    }

    @Test
    fun `sniffed formats use the server's input_formats names`() {
        assertEquals("jpeg", RemoteUpscaler.sniffFormat(bytes(0xFF, 0xD8, 0xFF, 0xDB)))
        assertEquals("tiff", RemoteUpscaler.sniffFormat(ascii("II*") + bytes(0, 8, 0)))
        assertEquals("tiff", RemoteUpscaler.sniffFormat(ascii("MM") + bytes(0) + ascii("*")))
        assertEquals("avif", RemoteUpscaler.sniffFormat(bytes(0, 0, 0, 0x1C) + ascii("ftypavif") + bytes(0, 0, 0, 0) + ascii("avifmif1miaf")))
        // Generic HEIF major brand with AVIF listed as compatible is still AVIF.
        assertEquals("avif", RemoteUpscaler.sniffFormat(bytes(0, 0, 0, 0x18) + ascii("ftypmif1") + bytes(0, 0, 0, 0) + ascii("avif")))
        assertEquals("heif", RemoteUpscaler.sniffFormat(bytes(0, 0, 0, 0x18) + ascii("ftypheic") + bytes(0, 0, 0, 0) + ascii("mif1")))
        assertEquals("jxl", RemoteUpscaler.sniffFormat(bytes(0xFF, 0x0A, 0xFA)))
        assertNull(RemoteUpscaler.sniffFormat(bytes(0, 0, 0, 0x18) + ascii("ftypisom")))
    }

    @Test
    fun `server-listed formats are sent as-is`() {
        val avif = bytes(0, 0, 0, 0x1C) + ascii("ftypavif") + bytes(0, 0, 0, 0) + ascii("avifmif1miaf")
        val formats = RemoteUpscaler.parseInputFormats(listOf("PNG", "jpeg", "webp", "gif", "bmp", "tiff", "avif"))!!
        assertTrue("png" in formats)
        assertTrue(RemoteUpscaler.isServerDecodable(avif, formats))
        assertFalse(RemoteUpscaler.isServerDecodable(avif))
        assertNull(RemoteUpscaler.parseInputFormats(null))
        assertNull(RemoteUpscaler.parseInputFormats("png"))
    }

    @Test
    fun `a failed status lookup is retried after the expiry, a reported list is kept`() {
        var now = 1_000L
        val realClock = RemoteUpscaler.nowMs
        RemoteUpscaler.nowMs = { now }
        try {
            val key = "test-expiry:8282"
            assertTrue(RemoteUpscaler.claimStatusLookup(key))
            // /status failed: the stand-in is the built-in list, and nobody asks again yet.
            assertEquals(RemoteUpscaler.DEFAULT_INPUT_FORMATS, RemoteUpscaler.inputFormatsFor(key))
            now += RemoteUpscaler.STATUS_RETRY_MS - 1
            assertFalse(RemoteUpscaler.claimStatusLookup(key))
            now += 1
            assertTrue(RemoteUpscaler.claimStatusLookup(key))

            // /status answered: its list is used and never expires.
            RemoteUpscaler.recordInputFormats(key, mapOf("input_formats" to listOf("png", "avif")))
            assertEquals(setOf("png", "avif"), RemoteUpscaler.inputFormatsFor(key))
            now += 10 * RemoteUpscaler.STATUS_RETRY_MS
            assertFalse(RemoteUpscaler.claimStatusLookup(key))
            assertEquals(setOf("png", "avif"), RemoteUpscaler.inputFormatsFor(key))
        } finally {
            RemoteUpscaler.nowMs = realClock
        }
    }
}
