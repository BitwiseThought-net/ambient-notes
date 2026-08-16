package com.ambientnotes.app.recognition.providers

import com.ambientnotes.app.recognition.RecognitionProvider
import com.ambientnotes.app.recognition.RecognitionProviderException
import com.ambientnotes.app.recognition.RecognitionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * Client for a user's self-hosted AmbientNotesService instance
 * (see ../../../../../../../../ambient-notes-service). Calls
 * POST {baseUrl}/api/v1/recognize with a bearer API key.
 */
class SelfHostedServiceProvider(
    private val connectionProvider: suspend () -> SelfHostedConnection?,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build(),
) : RecognitionProvider {

    override val id: String = "selfhosted"
    override val displayName: String = "Self-hosted service"

    override suspend fun isConfigured(): Boolean = connectionProvider() != null

    override suspend fun recognize(pcm16Audio: ByteArray, sampleRateHz: Int): RecognitionResult =
        withContext(Dispatchers.IO) {
            val connection = connectionProvider() ?: return@withContext RecognitionResult.noMatch(id)

            val payload = JSONObject().apply {
                put("audio_base64", Base64.getEncoder().encodeToString(pcm16Audio))
                put("audio_format", "pcm16_16k")
                put("sample_rate_hz", sampleRateHz)
                put("device_id", connection.deviceId)
            }

            val request = Request.Builder()
                .url("${connection.baseUrl.trimEnd('/')}/api/v1/recognize")
                .addHeader("Authorization", "Bearer ${connection.apiKey}")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = try {
                httpClient.newCall(request).execute()
            } catch (e: java.io.IOException) {
                throw RecognitionProviderException(id, "Network error contacting self-hosted service", e)
            }

            response.use {
                when (it.code) {
                    401 -> throw RecognitionProviderException(id, "Self-hosted service rejected the API key (401)")
                    503 -> throw RecognitionProviderException(id, "Self-hosted service is not configured with an API key (503)")
                }
                if (!it.isSuccessful) throw RecognitionProviderException(id, "Self-hosted service HTTP ${it.code}")
                parseResponse(JSONObject(it.body?.string().orEmpty()))
            }
        }

    internal fun parseResponse(json: JSONObject): RecognitionResult {
        val result = json.optJSONObject("result") ?: return RecognitionResult.noMatch(id)
        if (!result.optBoolean("matched", false)) return RecognitionResult.noMatch(id)

        val externalIds = mutableMapOf<String, String>()
        result.optJSONObject("external_ids")?.let { ids ->
            ids.keys().forEach { key -> externalIds[key] = ids.optString(key) }
        }

        return RecognitionResult(
            matched = true,
            title = result.optString("title").takeIf { it.isNotBlank() },
            artist = result.optString("artist").takeIf { it.isNotBlank() },
            album = result.optString("album").takeIf { it.isNotBlank() },
            releaseDate = result.optString("release_date").takeIf { it.isNotBlank() },
            confidence = result.optDouble("confidence", 0.0).toFloat(),
            providerName = "selfhosted:" + result.optString("provider", "unknown"),
            externalIds = externalIds,
        )
    }
}

data class SelfHostedConnection(
    val baseUrl: String,
    val apiKey: String,
    val deviceId: String? = null,
)
