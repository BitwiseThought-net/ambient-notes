package com.ambientnotes.app.recognition

/**
 * Provider-agnostic result of an audio recognition attempt. Every
 * [RecognitionProvider] implementation maps its own API's response onto this
 * shape so the rest of the app (logging, target posting) never needs to know
 * which backend produced a match.
 */
data class RecognitionResult(
    val matched: Boolean,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val releaseDate: String? = null,
    val confidence: Float = 0f,
    val providerName: String? = null,
    val externalIds: Map<String, String> = emptyMap(),
    val recognizedAtEpochMs: Long = System.currentTimeMillis(),
) {
    companion object {
        fun noMatch(providerName: String) = RecognitionResult(matched = false, providerName = providerName)
    }
}

/** Thrown by providers for real transport/auth/config failures. A "no match"
 * is NOT an exception -- it's a [RecognitionResult] with matched = false. */
class RecognitionProviderException(
    val providerName: String,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
