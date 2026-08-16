package com.ambientnotes.app.recognition.providers

import com.ambientnotes.app.recognition.RecognitionProvider
import com.ambientnotes.app.recognition.RecognitionResult

/**
 * ShazamKit integration point.
 *
 * IMPORTANT PLATFORM NOTE: Apple's ShazamKit ships official SDKs for iOS,
 * macOS, and web (via a JS shim) -- there is **no official Android SDK** as
 * of this writing. Apps on Android reach Shazam-equivalent recognition
 * either through Apple Music/Shazam's separate consumer APIs (not a
 * developer-facing recognition API), or not at all.
 *
 * This class exists so ShazamKit shows up as a selectable option in
 * Settings (per the project requirement to let users choose it), and so a
 * real implementation can be dropped in later without touching any other
 * part of the app -- but out of the box it always reports itself as
 * unconfigured, and `recognize()` throws rather than silently pretending
 * to work. If Apple publishes an Android SDK, or the user wants to bridge
 * to it via a companion iOS device / server-side shim, implement that here.
 *
 * See docs/CONFIGURATION.md#shazamkit for current status and workarounds
 * (e.g. routing ShazamKit requests through AmbientNotesService's
 * `selfhosted_peer`-style architecture from a Mac you own).
 */
class ShazamKitProvider : RecognitionProvider {

    override val id: String = "shazamkit"
    override val displayName: String = "ShazamKit"

    override suspend fun isConfigured(): Boolean = false

    override suspend fun recognize(pcm16Audio: ByteArray, sampleRateHz: Int): RecognitionResult {
        // Deliberately not a "no match" result -- this tells the caller
        // (RecognitionOrchestrator) plainly that this provider cannot run at
        // all on Android today, rather than implying it tried and failed.
        error(
            "ShazamKit has no public Android SDK. This provider is a placeholder; " +
                "see docs/CONFIGURATION.md#shazamkit for details and alternatives."
        )
    }
}
