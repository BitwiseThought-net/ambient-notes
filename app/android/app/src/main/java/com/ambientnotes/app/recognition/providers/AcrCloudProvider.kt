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
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Talks directly to the user's own ACRCloud project (https://www.acrcloud.com).
 * The user supplies their host/access key/access secret in Settings -- this
 * app never bundles or shares ACRCloud credentials; it's strictly BYO-account,
 * per the project requirements.
 */
class AcrCloudProvider(
    private val credentialsProvider: suspend () -> AcrCloudCredentials?,
    private val httpClient: OkHttpClient = OkHttpClient(),
) : RecognitionProvider {

    override val id: String = "acrcloud"
    override val displayName: String = "ACRCloud"

    override suspend fun isConfigured(): Boolean = credentialsProvider() != null

    override suspend fun recognize(pcm16Audio: ByteArray, sampleRateHz: Int): RecognitionResult =
        withContext(Dispatchers.IO) {
            val creds = credentialsProvider()
                ?: return@withContext RecognitionResult.noMatch(id)

            val timestamp = (System.currentTimeMillis() / 1000).toString()
            val signature = buildSignature(creds.accessSecret, creds.accessKey, timestamp)

            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("access_key", creds.accessKey)
                .addFormDataPart("sample_bytes", pcm16Audio.size.toString())
                .addFormDataPart("timestamp", timestamp)
                .addFormDataPart("signature", signature)
                .addFormDataPart("data_type", "audio")
                .addFormDataPart("signature_version", "1")
                .addFormDataPart(
                    "sample", "sample.wav",
                    pcm16Audio.toRequestBody("audio/wav".toMediaType())
                )
                .build()

            val request = Request.Builder()
                .url(buildIdentifyUrl(creds.host))
                .post(body)
                .build()

            val response = try {
                httpClient.newCall(request).execute()
            } catch (e: java.io.IOException) {
                throw RecognitionProviderException(id, "Network error contacting ACRCloud", e)
            }

            response.use {
                if (!it.isSuccessful) {
                    throw RecognitionProviderException(id, "ACRCloud HTTP ${it.code}")
                }
                val json = JSONObject(it.body?.string().orEmpty())
                parseResponse(json)
            }
        }

    /** Builds the identify endpoint URL. `host` is normally a bare hostname
     * (e.g. "identify-eu-west-1.acrcloud.com"), in which case we assume TLS.
     * A host already containing a scheme (used by tests, pointed at a local
     * MockWebServer over plain HTTP) is used as-is. */
    internal fun buildIdentifyUrl(host: String): String =
        if (host.startsWith("http://") || host.startsWith("https://")) {
            "${host.trimEnd('/')}/v1/identify"
        } else {
            "https://$host/v1/identify"
        }

    internal fun parseResponse(json: JSONObject): RecognitionResult {
        val statusCode = json.optJSONObject("status")?.optInt("code", -1) ?: -1
        if (statusCode != 0) return RecognitionResult.noMatch(id)

        val music = json.optJSONObject("metadata")
            ?.optJSONArray("music")
            ?.optJSONObject(0) ?: return RecognitionResult.noMatch(id)

        val artists = music.optJSONArray("artists")
        val artistNames = buildList {
            if (artists != null) {
                for (i in 0 until artists.length()) add(artists.optJSONObject(i)?.optString("name").orEmpty())
            }
        }.filter { it.isNotBlank() }.joinToString(", ")

        val externalIds = mutableMapOf<String, String>()
        music.optJSONObject("external_ids")?.let { ids ->
            ids.keys().forEach { key -> externalIds[key] = ids.optString(key) }
        }

        return RecognitionResult(
            matched = true,
            title = music.optString("title").takeIf { it.isNotBlank() },
            artist = artistNames.takeIf { it.isNotBlank() },
            album = music.optJSONObject("album")?.optString("name"),
            releaseDate = music.optString("release_date").takeIf { it.isNotBlank() },
            confidence = (music.optDouble("score", 0.0) / 100.0).toFloat(),
            providerName = id,
            externalIds = externalIds,
        )
    }

    private fun buildSignature(secret: String, accessKey: String, timestamp: String): String {
        val stringToSign = listOf("POST", "/v1/identify", accessKey, "audio", "1", timestamp)
            .joinToString("\n")
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA1"))
        return Base64.getEncoder().encodeToString(mac.doFinal(stringToSign.toByteArray(Charsets.UTF_8)))
    }
}

data class AcrCloudCredentials(
    val host: String,
    val accessKey: String,
    val accessSecret: String,
)
