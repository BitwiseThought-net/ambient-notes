package com.ambientnotes.app.recognition

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeProvider(
    override val id: String,
    private val configured: Boolean = true,
    private val result: RecognitionResult = RecognitionResult.noMatch(id),
    private val throwOnConfigCheck: Boolean = false,
    private val throwOnRecognize: Boolean = false,
) : RecognitionProvider {
    override val displayName: String = id
    var recognizeCallCount = 0
        private set

    override suspend fun isConfigured(): Boolean {
        if (throwOnConfigCheck) throw IllegalStateException("boom")
        return configured
    }

    override suspend fun recognize(pcm16Audio: ByteArray, sampleRateHz: Int): RecognitionResult {
        recognizeCallCount++
        if (throwOnRecognize) throw IllegalStateException("boom")
        return result
    }
}

class RecognitionOrchestratorTest {

    @Test
    fun `first confident match short-circuits remaining providers`() = runTest {
        val p1 = FakeProvider("p1", result = RecognitionResult(matched = true, title = "A", confidence = 0.9f))
        val p2 = FakeProvider("p2", result = RecognitionResult(matched = true, title = "B", confidence = 0.9f))
        val orchestrator = RecognitionOrchestrator(listOf(p1, p2), minConfidence = 0.6f)

        val outcome = orchestrator.recognize(ByteArray(0), 16000, listOf("p1", "p2"))

        assertEquals("A", outcome.result.title)
        assertEquals(listOf("p1"), outcome.providersTried)
        assertEquals(0, p2.recognizeCallCount)
    }

    @Test
    fun `low confidence falls through to next provider`() = runTest {
        val p1 = FakeProvider("p1", result = RecognitionResult(matched = true, title = "Weak", confidence = 0.2f))
        val p2 = FakeProvider("p2", result = RecognitionResult(matched = true, title = "Strong", confidence = 0.95f))
        val orchestrator = RecognitionOrchestrator(listOf(p1, p2), minConfidence = 0.6f)

        val outcome = orchestrator.recognize(ByteArray(0), 16000, listOf("p1", "p2"))

        assertEquals("Strong", outcome.result.title)
        assertEquals(listOf("p1", "p2"), outcome.providersTried)
    }

    @Test
    fun `unconfigured provider is skipped and not counted as tried`() = runTest {
        val p1 = FakeProvider("p1", configured = false)
        val p2 = FakeProvider("p2", result = RecognitionResult(matched = true, title = "X", confidence = 0.9f))
        val orchestrator = RecognitionOrchestrator(listOf(p1, p2), minConfidence = 0.6f)

        val outcome = orchestrator.recognize(ByteArray(0), 16000, listOf("p1", "p2"))

        assertTrue(outcome.result.matched)
        assertEquals(listOf("p2"), outcome.providersTried)
    }

    @Test
    fun `exception during recognize does not abort the chain`() = runTest {
        val p1 = FakeProvider("p1", throwOnRecognize = true)
        val p2 = FakeProvider("p2", result = RecognitionResult(matched = true, title = "X", confidence = 0.9f))
        val orchestrator = RecognitionOrchestrator(listOf(p1, p2), minConfidence = 0.6f)

        val outcome = orchestrator.recognize(ByteArray(0), 16000, listOf("p1", "p2"))

        assertTrue(outcome.result.matched)
        assertEquals(listOf("p1", "p2"), outcome.providersTried)
    }

    @Test
    fun `exception during isConfigured skips that provider`() = runTest {
        val p1 = FakeProvider("p1", throwOnConfigCheck = true)
        val p2 = FakeProvider("p2", result = RecognitionResult(matched = true, title = "X", confidence = 0.9f))
        val orchestrator = RecognitionOrchestrator(listOf(p1, p2), minConfidence = 0.6f)

        val outcome = orchestrator.recognize(ByteArray(0), 16000, listOf("p1", "p2"))

        assertTrue(outcome.result.matched)
        assertEquals(listOf("p2"), outcome.providersTried)
    }

    @Test
    fun `no providers match returns unmatched with full tried list`() = runTest {
        val p1 = FakeProvider("p1")
        val p2 = FakeProvider("p2")
        val orchestrator = RecognitionOrchestrator(listOf(p1, p2), minConfidence = 0.6f)

        val outcome = orchestrator.recognize(ByteArray(0), 16000, listOf("p1", "p2"))

        assertFalse(outcome.result.matched)
        assertEquals(listOf("p1", "p2"), outcome.providersTried)
    }

    @Test
    fun `enabledProviderIdsInOrder filters and orders the chain`() = runTest {
        val p1 = FakeProvider("p1", result = RecognitionResult(matched = true, title = "A", confidence = 0.9f))
        val p2 = FakeProvider("p2", result = RecognitionResult(matched = true, title = "B", confidence = 0.9f))
        val orchestrator = RecognitionOrchestrator(listOf(p1, p2), minConfidence = 0.6f)

        val outcome = orchestrator.recognize(ByteArray(0), 16000, listOf("p2"))

        assertEquals("B", outcome.result.title)
        assertEquals(listOf("p2"), outcome.providersTried)
    }

    @Test
    fun `unknown provider id in enabled list is silently ignored`() = runTest {
        val p1 = FakeProvider("p1", result = RecognitionResult(matched = true, title = "A", confidence = 0.9f))
        val orchestrator = RecognitionOrchestrator(listOf(p1), minConfidence = 0.6f)

        val outcome = orchestrator.recognize(ByteArray(0), 16000, listOf("does-not-exist", "p1"))

        assertEquals("A", outcome.result.title)
    }
}
