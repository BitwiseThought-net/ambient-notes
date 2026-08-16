package com.ambientnotes.app.targets

import com.ambientnotes.app.recognition.RecognitionResult
import com.ambientnotes.app.targets.impl.WebhookTarget
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WebhookTargetTest {

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

    private val result = RecognitionResult(matched = true, title = "T", artist = "A", confidence = 0.9f)

    @Test
    fun `posts rendered json body with correct content type`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        val config = PostTargetConfig(
            id = "wh1", type = TargetType.WEBHOOK, displayName = "Test webhook",
            bodyTemplate = """{"song":"{{title}}"}""", payloadFormat = PayloadFormat.JSON,
            settings = mapOf("url" to server.url("/hook").toString()),
        )
        val target = WebhookTarget(config)

        val outcome = target.post(result)

        assertTrue(outcome is PostResult.Success)
        val recorded = server.takeRequest()
        assertEquals("application/json; charset=utf-8", recorded.getHeader("Content-Type"))
        assertEquals("""{"song":"T"}""", recorded.body.readUtf8())
    }

    @Test
    fun `includes custom headers from settings`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        val config = PostTargetConfig(
            id = "wh1", type = TargetType.WEBHOOK, displayName = "Test webhook",
            bodyTemplate = "hello", payloadFormat = PayloadFormat.PLAIN_TEXT,
            settings = mapOf("url" to server.url("/hook").toString(), "header:X-Auth" to "secret-token"),
        )
        val target = WebhookTarget(config)

        target.post(result)

        val recorded = server.takeRequest()
        assertEquals("secret-token", recorded.getHeader("X-Auth"))
    }

    @Test
    fun `returns failure result on non-2xx response`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        val config = PostTargetConfig(
            id = "wh1", type = TargetType.WEBHOOK, displayName = "Test webhook",
            bodyTemplate = "hello", payloadFormat = PayloadFormat.PLAIN_TEXT,
            settings = mapOf("url" to server.url("/hook").toString()),
        )
        val target = WebhookTarget(config)

        val outcome = target.post(result)

        assertTrue(outcome is PostResult.Failure)
        assertEquals(500, (outcome as PostResult.Failure).httpStatus)
    }

    @Test
    fun `returns failure when url missing from settings`() = runTest {
        val config = PostTargetConfig(
            id = "wh1", type = TargetType.WEBHOOK, displayName = "Test webhook",
            bodyTemplate = "hello", payloadFormat = PayloadFormat.PLAIN_TEXT, settings = emptyMap(),
        )
        val target = WebhookTarget(config)

        val outcome = target.post(result)

        assertTrue(outcome is PostResult.Failure)
    }

    @Test
    fun `soap payload sends soapAction header`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        val config = PostTargetConfig(
            id = "wh1", type = TargetType.WEBHOOK, displayName = "SOAP hook",
            bodyTemplate = "<Envelope/>", payloadFormat = PayloadFormat.SOAP,
            settings = mapOf("url" to server.url("/hook").toString(), "soapAction" to "urn:song:notify"),
        )
        val target = WebhookTarget(config)

        target.post(result)

        val recorded = server.takeRequest()
        assertEquals("urn:song:notify", recorded.getHeader("SOAPAction"))
    }
}
