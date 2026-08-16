package com.ambientnotes.app.targets

import com.ambientnotes.app.recognition.RecognitionResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TargetPostingCoordinatorTest {

    private val matchedResult = RecognitionResult(matched = true, title = "T", confidence = 0.9f)

    private fun config(id: String, enabled: Boolean = true) = PostTargetConfig(
        id = id, type = TargetType.WEBHOOK, displayName = id, enabled = enabled, bodyTemplate = "t",
    )

    @Test
    fun `posts to every enabled target independently`() = runTest {
        val fakeTargets = mapOf(
            "a" to FakeTarget(config("a"), PostResult.Success),
            "b" to FakeTarget(config("b"), PostResult.Failure("nope")),
        )
        val coordinator = TargetPostingCoordinator(targetFactory = { cfg -> fakeTargets.getValue(cfg.id) })

        val outcomes = coordinator.postToAll(fakeTargets.values.map { it.config }, matchedResult)

        assertEquals(PostResult.Success, outcomes["a"])
        assertTrue(outcomes["b"] is PostResult.Failure)
    }

    @Test
    fun `skips disabled targets entirely`() = runTest {
        val calledIds = mutableListOf<String>()
        val coordinator = TargetPostingCoordinator(targetFactory = { cfg ->
            FakeTarget(cfg, PostResult.Success) { calledIds += cfg.id }
        })

        coordinator.postToAll(listOf(config("a", enabled = false), config("b", enabled = true)), matchedResult)

        assertEquals(listOf("b"), calledIds)
    }

    @Test
    fun `one target throwing does not prevent others from being attempted`() = runTest {
        val coordinator = TargetPostingCoordinator(targetFactory = { cfg ->
            if (cfg.id == "a") throw IllegalStateException("boom") else FakeTarget(cfg, PostResult.Success)
        })

        val outcomes = coordinator.postToAll(listOf(config("a"), config("b")), matchedResult)

        assertTrue(outcomes.getValue("a") is PostResult.Failure)
        assertEquals(PostResult.Success, outcomes["b"])
    }

    private class FakeTarget(
        override val config: PostTargetConfig,
        private val result: PostResult,
        private val onPost: () -> Unit = {},
    ) : PostTarget {
        override suspend fun post(result: RecognitionResult): PostResult {
            onPost()
            return this.result
        }
    }
}
