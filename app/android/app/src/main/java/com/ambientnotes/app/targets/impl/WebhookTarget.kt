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

/**
 * Generic webhook target: POSTs the rendered template body to a
 * user-configured URL, with a content-type matching the chosen
 * [PayloadFormat] (JSON, XML, SOAP, or plain text). Optional extra headers
 * (e.g. a shared-secret auth header) come from `config.settings["header:X"]`.
 *
 * Required settings key: "url".
 * Optional: any "header:<Name>" entries, "soapAction" for SOAP payloads.
 */
class WebhookTarget(
    override val config: PostTargetConfig,
    private val httpClient: OkHttpClient = OkHttpClient(),
) : PostTarget {

    override suspend fun post(result: RecognitionResult): PostResult = withContext(Dispatchers.IO) {
        val url = config.settings["url"]
            ?: return@withContext PostResult.Failure("Webhook target '${config.displayName}' has no URL configured")

        val body = TemplateEngine.render(config.bodyTemplate, result, config.payloadFormat)
        val contentType = when (config.payloadFormat) {
            PayloadFormat.JSON -> "application/json"
            PayloadFormat.XML -> "application/xml"
            PayloadFormat.SOAP -> "text/xml; charset=utf-8"
            PayloadFormat.PLAIN_TEXT -> "text/plain"
        }

        val requestBuilder = Request.Builder()
            .url(url)
            .post(body.toRequestBody(contentType.toMediaType()))

        config.settings.entries
            .filter { it.key.startsWith("header:") }
            .forEach { (key, value) -> requestBuilder.addHeader(key.removePrefix("header:"), value) }

        if (config.payloadFormat == PayloadFormat.SOAP) {
            config.settings["soapAction"]?.let { requestBuilder.addHeader("SOAPAction", it) }
        }

        val response = try {
            httpClient.newCall(requestBuilder.build()).execute()
        } catch (e: java.io.IOException) {
            return@withContext PostResult.Failure("Network error: ${e.message}")
        }

        response.use {
            if (it.isSuccessful) PostResult.Success
            else PostResult.Failure("HTTP ${it.code}: ${it.message}", it.code)
        }
    }
}
