package com.ambientnotes.app.recognition

import android.util.Log

/**
 * Tries each configured, enabled provider (in the user's chosen order) until
 * one returns a confident match. Mirrors the fallback semantics of
 * AmbientNotesService's `RecognitionOrchestrator` on the server side, so
 * behavior is consistent whether recognition happens on-device against
 * cloud SDKs or is delegated to the self-hosted service (which does its own
 * internal fallback chain).
 */
class RecognitionOrchestrator(
    private val allProviders: List<RecognitionProvider>,
    private val minConfidence: Float = 0.55f,
) {
    /**
     * @param enabledProviderIdsInOrder user-configured order/subset of
     *   provider ids to try, e.g. ["selfhosted", "acrcloud"]. Providers not
     *   listed are never used, even if configured.
     */
    suspend fun recognize(
        pcm16Audio: ByteArray,
        sampleRateHz: Int,
        enabledProviderIdsInOrder: List<String>,
    ): RecognitionOutcome {
        val chain = enabledProviderIdsInOrder.mapNotNull { id -> allProviders.find { it.id == id } }
        val triedIds = mutableListOf<String>()

        for (provider in chain) {
            val configured = try {
                provider.isConfigured()
            } catch (t: Throwable) {
                Log.w(TAG, "isConfigured() threw for ${provider.id}, skipping", t)
                continue
            }
            if (!configured) {
                Log.d(TAG, "${provider.id} not configured, skipping")
                continue
            }

            triedIds += provider.id
            val result = try {
                provider.recognize(pcm16Audio, sampleRateHz)
            } catch (t: Throwable) {
                Log.w(TAG, "${provider.id}.recognize() failed, trying next provider", t)
                continue
            }

            if (result.matched && result.confidence >= minConfidence) {
                return RecognitionOutcome(result, triedIds)
            }
        }

        return RecognitionOutcome(RecognitionResult(matched = false), triedIds)
    }

    companion object {
        private const val TAG = "RecognitionOrch"
    }
}

data class RecognitionOutcome(
    val result: RecognitionResult,
    val providersTried: List<String>,
)
