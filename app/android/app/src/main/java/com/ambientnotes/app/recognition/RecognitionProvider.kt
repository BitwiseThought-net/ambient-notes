package com.ambientnotes.app.recognition

/**
 * Contract every audio recognition backend implements: cloud SDKs
 * (ACRCloud, AudD, ShazamKit) and the self-hosted AmbientNotesService
 * client alike. New providers can be added by implementing this interface
 * and registering with [RecognitionProviderFactory] -- nothing else in the
 * app needs to change.
 */
interface RecognitionProvider {
    /** Short, stable, user-facing-safe identifier, e.g. "acrcloud". */
    val id: String

    /** Human-readable name shown in Settings, e.g. "ACRCloud". */
    val displayName: String

    /**
     * Whether this provider is currently configured and ready to use
     * (e.g. an API key / account is present). The app skips unconfigured
     * providers rather than treating them as a failed match.
     */
    suspend fun isConfigured(): Boolean

    /**
     * Attempt to identify the song from a short raw PCM16 mono audio clip.
     * Implementations must return [RecognitionResult.noMatch] rather than
     * throwing when the audio simply doesn't match anything; exceptions are
     * reserved for real errors (network, auth, malformed config).
     */
    suspend fun recognize(pcm16Audio: ByteArray, sampleRateHz: Int): RecognitionResult
}
