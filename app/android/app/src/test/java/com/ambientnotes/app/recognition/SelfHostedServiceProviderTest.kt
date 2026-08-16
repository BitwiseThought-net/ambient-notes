package com.ambientnotes.app.recognition

import com.ambientnotes.app.recognition.providers.SelfHostedConnection
import com.ambientnotes.app.recognition.providers.SelfHostedServiceProvider
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

class SelfHostedServiceProviderTest {

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
    fun `not configured when connection missing`() = runTest {
        val provider = SelfHostedServiceProvider(connectionProvider = { null })
        assertFalse(provider.isConfigured())
        assertFalse(provider.recognize(ByteArray(2), 16000).matched)
    }

    @Test
    fun `recognize sends bearer auth and parses successful match`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"result":{"matched":true,"title":"Peer Song","artist":"Peer Artist","confidence":0.88,"provider":"dejavu","external_ids":{}}}""",
            ),
        )
        val baseUrl = server.url("/").toString().trimEnd('/')
        val provider = SelfHostedServiceProvider(
            connectionProvider = { SelfHostedConnection(baseUrl, "my-api-key", "device-1") },
        )

        val result = provider.recognize("audio".toByteArray(), 16000)

        assertTrue(result.matched)
        assertEquals("Peer Song", result.title)
        assertEquals("selfhosted:dejavu", result.providerName)

        val recorded = server.takeRequest()
        assertEquals("Bearer my-api-key", recorded.getHeader("Authorization"))
        assertEquals("/api/v1/recognize", recorded.path)
    }

    @Test
    fun `recognize throws on 401 so orchestrator logs and moves on`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        val baseUrl = server.url("/").toString().trimEnd('/')
        val provider = SelfHostedServiceProvider(
            connectionProvider = { SelfHostedConnection(baseUrl, "bad-key") },
        )

        try {
            provider.recognize("audio".toByteArray(), 16000)
            org.junit.Assert.fail("expected RecognitionProviderException")
        } catch (e: RecognitionProviderException) {
            assertEquals("selfhosted", e.providerName)
        }
    }

    @Test
    fun `parseResponse returns no match when matched is false`() {
        val provider = SelfHostedServiceProvider(connectionProvider = { null })
        val json = JSONObject("""{"result":{"matched":false}}""")
        val result = provider.parseResponse(json)
        assertFalse(result.matched)
    }
}
