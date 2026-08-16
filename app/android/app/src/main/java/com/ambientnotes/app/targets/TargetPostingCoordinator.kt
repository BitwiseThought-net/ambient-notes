package com.ambientnotes.app.targets

import android.util.Log
import com.ambientnotes.app.recognition.RecognitionResult

/**
 * Fans a successful [RecognitionResult] out to every enabled, configured
 * target. Targets are independent -- one failing (bad token, rate limit,
 * network blip) never prevents the others from being attempted. Returns a
 * per-target result map so the caller can log/display outcomes.
 */
class TargetPostingCoordinator(private val targetFactory: (PostTargetConfig) -> PostTarget = PostTargetFactory::build) {

    suspend fun postToAll(
        configs: List<PostTargetConfig>,
        result: RecognitionResult,
    ): Map<String, PostResult> {
        val outcomes = mutableMapOf<String, PostResult>()
        for (config in configs.filter { it.enabled }) {
            val target = try {
                targetFactory(config)
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to build target ${config.id}", t)
                outcomes[config.id] = PostResult.Failure("Target misconfigured: ${t.message}")
                continue
            }
            outcomes[config.id] = try {
                target.post(result)
            } catch (t: Throwable) {
                Log.w(TAG, "Target ${config.id} threw unexpectedly", t)
                PostResult.Failure("Unexpected error: ${t.message}")
            }
        }
        return outcomes
    }

    companion object {
        private const val TAG = "TargetPosting"
    }
}
