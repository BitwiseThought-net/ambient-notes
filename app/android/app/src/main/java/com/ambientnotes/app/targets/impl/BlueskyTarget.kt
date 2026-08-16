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
import java.time.Instant

/**
 * Posts to Bluesky via the AT Protocol (https://docs.bsky.app). Bluesky uses
 * app passwords, not OAuth: the user creates one under
 * Settings -> App Passwords on bsky.app and enters it here (never their main
 * account password) -- see docs/CONFIGURATION.md#bluesky.
 *
 * Required settings: "identifier" (handle or email), "appPassword",
 * "pdsBaseUrl" (defaults to https://bsky.social if unset).
 *
 * This target authenticates fresh on every post via com.atproto.server.createSession
 * rather than caching a session token, trading a little latency for not
 * having to manage token refresh/expiry in app storage.
 */
class BlueskyTarget(
    override val config: PostTargetConfig,
    private val httpClient: OkHttpClient = OkHttpClient(),
) : PostTarget {

    override suspend fun post(result: RecognitionResult): PostResult = withContext(Dispatchers.IO) {
        val identifier = config.settings["identifier"]
            ?: return@withContext PostResult.Failure("Bluesky target missing identifier")
        val appPassword = config.settings["appPassword"]
            ?: return@withContext PostResult.Failure("Bluesky target missing appPassword")
        val pdsBaseUrl = (config.settings["pdsBaseUrl"] ?: "https://bsky.social").trimEnd('/')

        val session = try {
            createSession(pdsBaseUrl, identifier, appPassword)
        } catch (e: Exception) {
            return@withContext PostResult.Failure("Bluesky auth failed: ${e.message}")
        }

        val text = TemplateEngine.render(config.bodyTemplate, result, PayloadFormat.PLAIN_TEXT)
        val record = JSONObject().apply {
            put("collection", "app.bsky.feed.post")
            put("repo", session.did)
            put(
                "record",
                JSONObject()
                    .put("text", text)
                    .put("createdAt", Instant.now().toString())
                    .put("\$type", "app.bsky.feed.post"),
            )
        }

        val request = Request.Builder()
            .url("$pdsBaseUrl/xrpc/com.atproto.repo.createRecord")
            .addHeader("Authorization", "Bearer ${session.accessJwt}")
            .post(record.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = try {
            httpClient.newCall(request).execute()
        } catch (e: java.io.IOException) {
            return@withContext PostResult.Failure("Network error: ${e.message}")
        }

        response.use {
            if (it.isSuccessful) PostResult.Success
            else PostResult.Failure("Bluesky HTTP ${it.code}", it.code)
        }
    }

    private fun createSession(pdsBaseUrl: String, identifier: String, appPassword: String): BlueskySession {
        val body = JSONObject().put("identifier", identifier).put("password", appPassword).toString()
        val request = Request.Builder()
            .url("$pdsBaseUrl/xrpc/com.atproto.server.createSession")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("createSession HTTP ${response.code}")
            val json = JSONObject(response.body?.string().orEmpty())
            return BlueskySession(did = json.getString("did"), accessJwt = json.getString("accessJwt"))
        }
    }

    private data class BlueskySession(val did: String, val accessJwt: String)
}
