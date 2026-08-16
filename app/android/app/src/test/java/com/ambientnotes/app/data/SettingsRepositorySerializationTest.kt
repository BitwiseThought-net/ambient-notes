package com.ambientnotes.app.data

import android.content.Context
import com.ambientnotes.app.targets.PayloadFormat
import com.ambientnotes.app.targets.PostTargetConfig
import com.ambientnotes.app.targets.TargetType
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Exercises the pure JSON (de)serialization helpers on SettingsRepository
 * without touching a real Context/DataStore -- these two functions never
 * read `context`, so a relaxed mock is enough to construct the repository.
 */
class SettingsRepositorySerializationTest {

    private val repository = SettingsRepository(mockk<Context>(relaxed = true))

    @Test
    fun `round-trips a list of target configs through JSON`() {
        val configs = listOf(
            PostTargetConfig(
                id = "wh1", type = TargetType.WEBHOOK, displayName = "My webhook",
                enabled = true, bodyTemplate = """{"t":"{{title}}"}""", payloadFormat = PayloadFormat.JSON,
                settings = mapOf("url" to "https://example.com/hook", "header:X-Auth" to "secret"),
            ),
            PostTargetConfig(
                id = "md1", type = TargetType.MASTODON, displayName = "Mastodon",
                enabled = false, bodyTemplate = "Now playing {{title}}", payloadFormat = PayloadFormat.PLAIN_TEXT,
                settings = mapOf("instanceBaseUrl" to "https://mastodon.social", "accessToken" to "tok"),
            ),
        )

        val json = repository.encodeTargetConfigs(configs)
        val decoded = repository.decodeTargetConfigs(json)

        assertEquals(configs, decoded)
    }

    @Test
    fun `decodeTargetConfigs of blank string returns empty list`() {
        assertEquals(emptyList<PostTargetConfig>(), repository.decodeTargetConfigs(null))
        assertEquals(emptyList<PostTargetConfig>(), repository.decodeTargetConfigs(""))
    }

    @Test
    fun `decodeStringList round-trips a provider order list`() {
        val ids = listOf("selfhosted", "acrcloud", "audd")
        val json = org.json.JSONArray(ids).toString()

        assertEquals(ids, repository.decodeStringList(json))
    }

    @Test
    fun `decodeStringList of null returns empty list`() {
        assertEquals(emptyList<String>(), repository.decodeStringList(null))
    }
}
