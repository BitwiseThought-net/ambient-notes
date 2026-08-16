package com.ambientnotes.app.targets

import com.ambientnotes.app.recognition.RecognitionResult

/**
 * A destination the app can report a successfully-identified song to:
 * a social network or a generic webhook. Every target renders the same
 * [RecognitionResult] through a user-configurable [TemplateEngine] template,
 * so adding a new target type is: implement this interface, register it in
 * [PostTargetFactory], done.
 */
interface PostTarget {
    val config: PostTargetConfig

    /** Attempts to publish the result. Never throws for expected failures
     * (auth errors, rate limits) -- returns [PostResult.Failure] instead, so
     * one failing target never blocks the others. */
    suspend fun post(result: RecognitionResult): PostResult
}

sealed interface PostResult {
    data object Success : PostResult
    data class Failure(val message: String, val httpStatus: Int? = null) : PostResult
}

enum class TargetType {
    WEBHOOK,
    MASTODON,
    BLUESKY,
    TWITTER_X,
    THREADS,
    FACEBOOK,
    REDDIT,
    LINKEDIN,
    TUMBLR,
}

enum class PayloadFormat { JSON, XML, SOAP, PLAIN_TEXT }

/**
 * Persisted, user-editable configuration for one target instance. Multiple
 * instances of the same [type] are allowed (e.g. two different webhooks).
 */
data class PostTargetConfig(
    val id: String,
    val type: TargetType,
    val displayName: String,
    val enabled: Boolean = true,
    /** Mustache-style {{field}} template; see TemplateEngine.kt for supported fields. */
    val bodyTemplate: String,
    val payloadFormat: PayloadFormat = PayloadFormat.JSON,
    /** Target-specific settings: webhook URL, instance base URL, access token, etc.
     * Kept as a flat string map so new target types don't require schema changes. */
    val settings: Map<String, String> = emptyMap(),
)
