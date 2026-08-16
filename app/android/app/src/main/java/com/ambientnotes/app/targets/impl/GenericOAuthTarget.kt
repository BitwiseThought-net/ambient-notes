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
 * Base for platforms that authenticate posts with a bearer/OAuth2 access
 * token obtained *outside this app* (each platform's own developer portal
 * and consent flow) and accept a single JSON-ish "create a post" call.
 *
 * AmbientNotes deliberately does NOT implement the interactive OAuth
 * authorization-code dance for every platform in-app -- each of Twitter/X,
 * Threads, Facebook, Reddit, LinkedIn, and Tumblr has its own app-review
 * process, scopes, and redirect URI requirements that are the user's
 * responsibility to set up once (see docs/CONFIGURATION.md for a per-platform
 * walkthrough and links to each developer portal). What this app needs from
 * the user afterward is just a long-lived (or refreshable, refreshed
 * out-of-band) access token pasted into Settings.
 *
 * Required settings for every subclass: "accessToken".
 * Concrete subclasses fill in [endpointUrl] and [extraHeaders], and the
 * user's [PostTargetConfig.bodyTemplate] supplies the platform-specific JSON
 * body shape (see docs/CONFIGURATION.md for a working template per platform).
 */
abstract class GenericOAuthTarget(
    override val config: PostTargetConfig,
    private val httpClient: OkHttpClient = OkHttpClient(),
) : PostTarget {

    protected abstract fun endpointUrl(settings: Map<String, String>): String
    protected open fun extraHeaders(settings: Map<String, String>): Map<String, String> = emptyMap()

    override suspend fun post(result: RecognitionResult): PostResult = withContext(Dispatchers.IO) {
        val token = config.settings["accessToken"]
            ?: return@withContext PostResult.Failure("${config.displayName}: missing accessToken")

        val body = TemplateEngine.render(config.bodyTemplate, result, config.payloadFormat)
        val contentType = if (config.payloadFormat == PayloadFormat.JSON) "application/json" else "text/plain"

        val requestBuilder = Request.Builder()
            .url(endpointUrl(config.settings))
            .addHeader("Authorization", "Bearer $token")
            .post(body.toRequestBody(contentType.toMediaType()))

        extraHeaders(config.settings).forEach { (k, v) -> requestBuilder.addHeader(k, v) }

        val response = try {
            httpClient.newCall(requestBuilder.build()).execute()
        } catch (e: java.io.IOException) {
            return@withContext PostResult.Failure("Network error: ${e.message}")
        }

        response.use {
            if (it.isSuccessful) PostResult.Success
            else PostResult.Failure("${config.displayName} HTTP ${it.code}", it.code)
        }
    }
}

/** POST https://api.twitter.com/2/tweets - template should render {"text": "..."} */
class TwitterXTarget(config: PostTargetConfig) : GenericOAuthTarget(config) {
    override fun endpointUrl(settings: Map<String, String>) = "https://api.twitter.com/2/tweets"
}

/** Threads Graph API: POST to the user's media-publish endpoint. The numeric
 * user id is required in settings["threadsUserId"]; see CONFIGURATION.md. */
class ThreadsTarget(config: PostTargetConfig) : GenericOAuthTarget(config) {
    override fun endpointUrl(settings: Map<String, String>): String {
        val userId = settings["threadsUserId"] ?: "me"
        return "https://graph.threads.net/v1.0/$userId/threads"
    }
}

/** Facebook Graph API: POST to a Page's /feed endpoint. Page id required in
 * settings["facebookPageId"]. */
class FacebookTarget(config: PostTargetConfig) : GenericOAuthTarget(config) {
    override fun endpointUrl(settings: Map<String, String>): String {
        val pageId = settings["facebookPageId"] ?: error("FacebookTarget requires facebookPageId")
        return "https://graph.facebook.com/v19.0/$pageId/feed"
    }
}

/** Reddit's API requires a User-Agent header identifying the app in addition
 * to the bearer token; subreddit comes from settings["subreddit"]. */
class RedditTarget(config: PostTargetConfig) : GenericOAuthTarget(config) {
    override fun endpointUrl(settings: Map<String, String>) = "https://oauth.reddit.com/api/submit"
    override fun extraHeaders(settings: Map<String, String>) = mapOf(
        "User-Agent" to (settings["userAgent"] ?: "android:com.ambientnotes.app:1.0.0"),
    )
}

/** LinkedIn UGC Posts API. Author URN required in settings["linkedinAuthorUrn"]
 * (e.g. "urn:li:person:abc123"). */
class LinkedInTarget(config: PostTargetConfig) : GenericOAuthTarget(config) {
    override fun endpointUrl(settings: Map<String, String>) = "https://api.linkedin.com/v2/ugcPosts"
    override fun extraHeaders(settings: Map<String, String>) = mapOf(
        "X-Restli-Protocol-Version" to "2.0.0",
    )
}

/** Tumblr API v2: POST to a specific blog's /posts endpoint. Blog identifier
 * required in settings["tumblrBlogIdentifier"] (e.g. "myblog.tumblr.com"). */
class TumblrTarget(config: PostTargetConfig) : GenericOAuthTarget(config) {
    override fun endpointUrl(settings: Map<String, String>): String {
        val blog = settings["tumblrBlogIdentifier"] ?: error("TumblrTarget requires tumblrBlogIdentifier")
        return "https://api.tumblr.com/v2/blog/$blog/posts"
    }
}
