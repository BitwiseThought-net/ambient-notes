package com.ambientnotes.app.targets.impl

import com.ambientnotes.app.recognition.RecognitionResult
import com.ambientnotes.app.targets.PayloadFormat
import com.ambientnotes.app.targets.PostResult
import com.ambientnotes.app.targets.PostTarget
import com.ambientnotes.app.targets.PostTargetConfig
import com.ambientnotes.app.targets.TemplateEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Posts to a Mastodon (or any Mastodon-API-compatible server, e.g.
 * Pleroma/Akkoa) instance via POST {instanceBaseUrl}/api/v1/statuses.
 *
 * Required settings: "instanceBaseUrl" (e.g. "https://mastodon.social"),
 * "accessToken" (a personal access token created under
 * Settings -> Development on the user's instance -- see
 * docs/CONFIGURATION.md#mastodon for the exact steps).
 *
 * The template's rendered output (config.payloadFormat should be PLAIN_TEXT)
 * becomes the toot/post text directly; this target wraps it into Mastodon's
 * expected JSON body itself.
 */
class MastodonTarget(
    override val config: PostTargetConfig,
    private val httpClient: OkHttpClient = OkHttpClient(),
) : PostTarget {

    override suspend fun post(result: RecognitionResult): PostResult = withContext(Dispatchers.IO) {
        val baseUrl = config.settings["instanceBaseUrl"]
            ?: return@withContext PostResult.Failure("Mastodon target missing instanceBaseUrl")
        val token = config.settings["accessToken"]
            ?: return@withContext PostResult.Failure("Mastodon target missing accessToken")

        val statusText = TemplateEngine.render(config.bodyTemplate, result, PayloadFormat.PLAIN_TEXT)
        val jsonBody = JSONObject().put("status", statusText).toString()

        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/api/v1/statuses")
            .addHeader("Authorization", "Bearer $token")
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()

        val response = try {
            httpClient.newCall(request).execute()
        } catch (e: java.io.IOException) {
            return@withContext PostResult.Failure("Network error: ${e.message}")
        }

        response.use {
            if (it.isSuccessful) PostResult.Success
            else PostResult.Failure("Mastodon HTTP ${it.code}", it.code)
        }
    }
}
