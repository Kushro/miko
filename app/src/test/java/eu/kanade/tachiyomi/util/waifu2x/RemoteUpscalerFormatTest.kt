package eu.kanade.tachiyomi.util.waifu2x

import org.junit.jupiter.api.Assertions.assertFalse
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
}
