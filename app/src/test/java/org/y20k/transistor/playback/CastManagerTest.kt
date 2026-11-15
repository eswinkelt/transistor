package org.y20k.transistor.playback

import org.junit.Assert.assertEquals
import org.junit.Test
import org.y20k.transistor.Keys
import org.y20k.transistor.core.Station


/**
 * Unit tests for the MIME type mapping that Cast receivers rely on.
 *
 * The Default Media Receiver does not probe a stream: a wrong content type makes it reject the
 * station, which is why the mapping is tested independently from any Cast device.
 */
class CastManagerTest {

    private fun station(url: String, content: String = Keys.MIME_TYPE_UNSUPPORTED): Station =
        Station(name = "Test", streamUris = mutableListOf(url), streamContent = content)


    @Test
    fun `mpeg stream keeps its declared type`() {
        assertEquals(
            Keys.CAST_MIME_TYPE_MPEG,
            CastManager.contentTypeOf(station("http://example.org/live", Keys.MIME_TYPE_MPEG))
        )
    }


    @Test
    fun `aac stream maps to audio aac`() {
        assertEquals(
            Keys.CAST_MIME_TYPE_AAC,
            CastManager.contentTypeOf(station("http://example.org/live", "audio/aac"))
        )
        assertEquals(
            Keys.CAST_MIME_TYPE_AAC,
            CastManager.contentTypeOf(station("http://example.org/live.aac"))
        )
    }


    @Test
    fun `ogg and opus streams map to audio ogg`() {
        assertEquals(
            Keys.CAST_MIME_TYPE_OGG,
            CastManager.contentTypeOf(station("http://example.org/live", "application/ogg"))
        )
        assertEquals(
            Keys.CAST_MIME_TYPE_OGG,
            CastManager.contentTypeOf(station("http://example.org/live.opus"))
        )
    }


    @Test
    fun `hls streams map to mpegurl`() {
        assertEquals(
            Keys.CAST_MIME_TYPE_HLS,
            CastManager.contentTypeOf(station("http://example.org/live.m3u8", Keys.MIME_TYPE_HLS))
        )
        assertEquals(
            Keys.CAST_MIME_TYPE_HLS,
            CastManager.contentTypeOf(station("http://example.org/live.m3u8"))
        )
    }


    @Test
    fun `flac stream maps to audio flac`() {
        assertEquals(
            Keys.CAST_MIME_TYPE_FLAC,
            CastManager.contentTypeOf(station("http://example.org/live.flac"))
        )
    }


    @Test
    fun `unknown stream falls back to audio mpeg`() {
        assertEquals(
            Keys.CAST_MIME_TYPE_MPEG,
            CastManager.contentTypeOf(station("http://example.org/listen"))
        )
    }
}
