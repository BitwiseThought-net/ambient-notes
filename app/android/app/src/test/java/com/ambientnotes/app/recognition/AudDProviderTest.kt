package com.ambientnotes.app.recognition

import com.ambientnotes.app.recognition.providers.AudDProvider
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudDProviderTest {

    @Test
    fun `not configured when token blank`() = runTest {
        val provider = AudDProvider(apiTokenProvider = { "" })
        assertFalse(provider.isConfigured())
    }

    @Test
    fun `configured when token present`() = runTest {
        val provider = AudDProvider(apiTokenProvider = { "tok" })
        assertTrue(provider.isConfigured())
    }

    @Test
    fun `parseResponse maps a successful match`() {
        val provider = AudDProvider(apiTokenProvider = { null })
        val json = JSONObject(
            """
            {"status":"success","result":{
              "title":"Test Song","artist":"Test Artist","album":"Test Album",
              "release_date":"2020-01-01",
              "spotify":{"id":"abc123"},
              "apple_music":{"url":"https://music.apple.com/x"}
            }}
            """.trimIndent(),
        )

        val result = provider.parseResponse(json)

        assertTrue(result.matched)
        assertEquals("Test Song", result.title)
        assertEquals("abc123", result.externalIds["spotify"])
        assertEquals("https://music.apple.com/x", result.externalIds["apple_music"])
    }

    @Test
    fun `parseResponse returns no match when result is null`() {
        val provider = AudDProvider(apiTokenProvider = { null })
        val json = JSONObject("""{"status":"success","result":null}""")

        val result = provider.parseResponse(json)

        assertFalse(result.matched)
    }

    @Test
    fun `parseResponse returns no match on non-success status`() {
        val provider = AudDProvider(apiTokenProvider = { null })
        val json = JSONObject("""{"status":"error"}""")

        val result = provider.parseResponse(json)

        assertFalse(result.matched)
    }
}
