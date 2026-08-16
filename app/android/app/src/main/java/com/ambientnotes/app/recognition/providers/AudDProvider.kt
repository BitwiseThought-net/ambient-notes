package com.ambientnotes.app.recognition.providers

import com.ambientnotes.app.recognition.RecognitionProvider
import com.ambientnotes.app.recognition.RecognitionProviderException
import com.ambientnotes.app.recognition.RecognitionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Talks to the user's own AudD (https://audd.io) account using their API
 * token, entered in Settings. BYO-account, same as ACRCloud.
 */
class AudDProvider(
    private val apiTokenProvider: suspend () -> String?,
    private val httpClient: OkHttpClient = OkHttpClient(),
) : RecognitionProvider {

    override val id: String = "audd"
    override val displayName: String = "AudD"

    override suspend fun isConfigured(): Boolean = !apiTokenProvider().isNullOrBlank()

    override suspend fun recognize(pcm16Audio: ByteArray, sampleRateHz: Int): RecognitionResult =
        withContext(Dispatchers.IO) {
            val token = apiTokenProvider().takeUnless { it.isNullOrBlank() }
                ?: return@withContext RecognitionResult.noMatch(id)

            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("api_token", token)
                .addFormDataPart("return", "apple_music,spotify")
                .addFormDataPart(
                    "file", "sample.wav",
                    pcm16Audio.toRequestBody("audio/wav".toMediaType())
                )
                .build()

            val request = Request.Builder().url("https://api.audd.io/").post(body).build()

            val response = try {
                httpClient.newCall(request).execute()
            } catch (e: java.io.IOException) {
                throw RecognitionProviderException(id, "Network error contacting AudD", e)
            }

            response.use {
                if (!it.isSuccessful) throw RecognitionProviderException(id, "AudD HTTP ${it.code}")
                parseResponse(JSONObject(it.body?.string().orEmpty()))
            }
        }

    internal fun parseResponse(json: JSONObject): RecognitionResult {
        if (json.optString("status") != "success" || json.isNull("result")) {
            return RecognitionResult.noMatch(id)
        }
        val result = json.optJSONObject("result") ?: return RecognitionResult.noMatch(id)

        val externalIds = mutableMapOf<String, String>()
        result.optJSONObject("spotify")?.optString("id")?.let { if (it.isNotBlank()) externalIds["spotify"] = it }
        result.optJSONObject("apple_music")?.optString("url")?.let { if (it.isNotBlank()) externalIds["apple_music"] = it }

        return RecognitionResult(
            matched = true,
            title = result.optString("title").takeIf { it.isNotBlank() },
            artist = result.optString("artist").takeIf { it.isNotBlank() },
            album = result.optString("album").takeIf { it.isNotBlank() },
            releaseDate = result.optString("release_date").takeIf { it.isNotBlank() },
            confidence = 0.9f, // AudD does not return a numeric confidence score
            providerName = id,
            externalIds = externalIds,
        )
    }
}
