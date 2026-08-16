package com.ambientnotes.app.recognition

import com.ambientnotes.app.recognition.providers.AcrCloudCredentials
import com.ambientnotes.app.recognition.providers.AcrCloudProvider
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AcrCloudProviderTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `not configured when credentials missing`() = runTest {
        val provider = AcrCloudProvider(credentialsProvider = { null })
        assertFalse(provider.isConfigured())
        val result = provider.recognize(ByteArray(4), 16000)
        assertFalse(result.matched)
    }

    @Test
    fun `parseResponse maps a successful match`() {
        val provider = AcrCloudProvider(credentialsProvider = { null })
        val json = JSONObject(
            """
            {
              "status": {"code": 0},
              "metadata": {"music": [{
                "title": "Test Song",
                "artists": [{"name": "Artist A"}, {"name": "Artist B"}],
                "album": {"name": "Test Album"},
                "release_date": "2021-05-01",
                "score": 92,
                "external_ids": {"isrc": "US1234567890"}
              }]}
            }
            """.trimIndent(),
        )

        val result = provider.parseResponse(json)

        assertTrue(result.matched)
        assertEquals("Test Song", result.title)
        assertEquals("Artist A, Artist B", result.artist)
        assertEquals("Test Album", result.album)
        assertEquals(0.92f, result.confidence, 0.001f)
        assertEquals("US1234567890", result.externalIds["isrc"])
    }

    @Test
    fun `parseResponse returns no match for non-zero status code`() {
        val provider = AcrCloudProvider(credentialsProvider = { null })
        val json = JSONObject("""{"status": {"code": 1001, "msg": "No result"}}""")

        val result = provider.parseResponse(json)

        assertFalse(result.matched)
    }

    @Test
    fun `recognize returns match from live mocked HTTP call`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"status":{"code":0},"metadata":{"music":[{"title":"Mocked","artists":[],"score":80}]}}""",
            ),
        )
        val host = "http://" + server.hostName + ":" + server.port
        val provider = AcrCloudProvider(
            credentialsProvider = { AcrCloudCredentials(host, "key", "secret") },
        )

        val result = provider.recognize("audio-bytes".toByteArray(), 16000)

        assertTrue(result.matched)
        assertEquals("Mocked", result.title)
        val recordedRequest = server.takeRequest()
        assertEquals("POST", recordedRequest.method)
        assertEquals("/v1/identify", recordedRequest.path)
    }
}
