package com.ambientnotes.app.targets

import com.ambientnotes.app.recognition.RecognitionResult
import org.json.JSONObject

/**
 * Renders a user-configured template string against a [RecognitionResult].
 *
 * Supported placeholders (case-sensitive): {{title}}, {{artist}}, {{album}},
 * {{releaseDate}}, {{confidence}}, {{provider}}, {{recognizedAt}} (ISO-8601),
 * and {{externalIds.spotify}} / {{externalIds.apple_music}} / etc. for any
 * key present in externalIds.
 *
 * Values are escaped appropriately for [PayloadFormat.JSON] / [PayloadFormat.XML]
 * / [PayloadFormat.SOAP] so a song title with quotes or angle brackets can't
 * break the payload. [PayloadFormat.PLAIN_TEXT] performs no escaping.
 *
 * Example JSON template:
 * ```
 * {"text": "Now playing: {{title}} by {{artist}}", "confidence": {{confidence}}}
 * ```
 */
object TemplateEngine {

    private val PLACEHOLDER_REGEX = Regex("""\{\{\s*([a-zA-Z0-9_.]+)\s*}}""")

    fun render(template: String, result: RecognitionResult, format: PayloadFormat): String {
        val values = fieldValues(result)
        return PLACEHOLDER_REGEX.replace(template) { match ->
            val key = match.groupValues[1]
            val raw = resolve(key, values, result)
            escape(raw, format)
        }
    }

    private fun fieldValues(result: RecognitionResult): Map<String, String> = mapOf(
        "title" to (result.title ?: ""),
        "artist" to (result.artist ?: ""),
        "album" to (result.album ?: ""),
        "releaseDate" to (result.releaseDate ?: ""),
        "confidence" to result.confidence.toString(),
        "provider" to (result.providerName ?: ""),
        "recognizedAt" to java.time.Instant.ofEpochMilli(result.recognizedAtEpochMs).toString(),
    )

    private fun resolve(key: String, values: Map<String, String>, result: RecognitionResult): String {
        if (key.startsWith("externalIds.")) {
            val idKey = key.removePrefix("externalIds.")
            return result.externalIds[idKey] ?: ""
        }
        return values[key] ?: ""
    }

    private fun escape(value: String, format: PayloadFormat): String = when (format) {
        PayloadFormat.JSON -> {
            // Reuse org.json's string escaping by round-tripping through a
            // JSONObject, then stripping the outer quotes/braces it adds.
            val quoted = JSONObject.quote(value)
            quoted.substring(1, quoted.length - 1)
        }
        PayloadFormat.XML, PayloadFormat.SOAP -> value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
        PayloadFormat.PLAIN_TEXT -> value
    }
}
