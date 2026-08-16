package com.ambientnotes.app.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ambientnotes.app.targets.PayloadFormat
import com.ambientnotes.app.targets.PostTargetConfig
import com.ambientnotes.app.targets.TargetType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore by preferencesDataStore(name = "ambient_notes_settings")

/**
 * Single source of truth for user-configured settings: provider credentials,
 * the enabled provider order, and the list of post targets.
 *
 * NOTE ON SECRETS: DataStore Preferences stores values in a plaintext XML
 * file under app-private storage, which is adequate for most threat models
 * (the app sandbox already protects it from other apps on a non-rooted
 * device) but is NOT encrypted at rest. For a stronger guarantee, wrap the
 * values written here with androidx.security's EncryptedSharedPreferences /
 * Jetpack Security (already a project dependency) before persisting --
 * left as a follow-up rather than done here so the storage format stays
 * simple to reason about and test. See docs/SECURITY.md.
 */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val ACRCLOUD_HOST = stringPreferencesKey("acrcloud_host")
        val ACRCLOUD_ACCESS_KEY = stringPreferencesKey("acrcloud_access_key")
        val ACRCLOUD_ACCESS_SECRET = stringPreferencesKey("acrcloud_access_secret")
        val AUDD_API_TOKEN = stringPreferencesKey("audd_api_token")
        val SELFHOSTED_BASE_URL = stringPreferencesKey("selfhosted_base_url")
        val SELFHOSTED_API_KEY = stringPreferencesKey("selfhosted_api_key")
        val SELFHOSTED_DEVICE_ID = stringPreferencesKey("selfhosted_device_id")
        val ENABLED_PROVIDER_ORDER = stringPreferencesKey("enabled_provider_order") // JSON array of ids
        val TARGET_CONFIGS = stringPreferencesKey("target_configs") // JSON array of PostTargetConfig
        val LISTENING_ENABLED = stringPreferencesKey("listening_enabled") // "true"/"false"
        val LISTENING_INTERVAL_SECONDS = stringPreferencesKey("listening_interval_seconds")
    }

    // --- ACRCloud ---------------------------------------------------------
    data class AcrCloudCredentialsSnapshot(val host: String?, val accessKey: String?, val accessSecret: String?)

    suspend fun acrCloudCredentialsSnapshot(): AcrCloudCredentialsSnapshot {
        val prefs = context.dataStore.data.first()
        return AcrCloudCredentialsSnapshot(prefs[Keys.ACRCLOUD_HOST], prefs[Keys.ACRCLOUD_ACCESS_KEY], prefs[Keys.ACRCLOUD_ACCESS_SECRET])
    }

    suspend fun setAcrCloudCredentials(host: String, accessKey: String, accessSecret: String) {
        context.dataStore.edit {
            it[Keys.ACRCLOUD_HOST] = host
            it[Keys.ACRCLOUD_ACCESS_KEY] = accessKey
            it[Keys.ACRCLOUD_ACCESS_SECRET] = accessSecret
        }
    }

    // --- AudD --------------------------------------------------------------
    suspend fun audDApiTokenSnapshot(): String? = context.dataStore.data.first()[Keys.AUDD_API_TOKEN]

    suspend fun setAudDApiToken(token: String) {
        context.dataStore.edit { it[Keys.AUDD_API_TOKEN] = token }
    }

    // --- Self-hosted service -------------------------------------------------
    data class SelfHostedConnectionSnapshot(val baseUrl: String?, val apiKey: String?, val deviceId: String?)

    suspend fun selfHostedConnectionSnapshot(): SelfHostedConnectionSnapshot {
        val prefs = context.dataStore.data.first()
        return SelfHostedConnectionSnapshot(
            prefs[Keys.SELFHOSTED_BASE_URL],
            prefs[Keys.SELFHOSTED_API_KEY],
            prefs[Keys.SELFHOSTED_DEVICE_ID],
        )
    }

    suspend fun setSelfHostedConnection(baseUrl: String, apiKey: String, deviceId: String?) {
        context.dataStore.edit {
            it[Keys.SELFHOSTED_BASE_URL] = baseUrl
            it[Keys.SELFHOSTED_API_KEY] = apiKey
            if (deviceId != null) it[Keys.SELFHOSTED_DEVICE_ID] = deviceId
        }
    }

    // --- Provider order ------------------------------------------------------
    fun observeEnabledProviderOrder(): Flow<List<String>> =
        context.dataStore.data.map { decodeStringList(it[Keys.ENABLED_PROVIDER_ORDER]) }

    suspend fun enabledProviderOrderSnapshot(): List<String> =
        decodeStringList(context.dataStore.data.first()[Keys.ENABLED_PROVIDER_ORDER])

    suspend fun setEnabledProviderOrder(providerIds: List<String>) {
        context.dataStore.edit { it[Keys.ENABLED_PROVIDER_ORDER] = JSONArray(providerIds).toString() }
    }

    // --- Post targets --------------------------------------------------------
    fun observeTargetConfigs(): Flow<List<PostTargetConfig>> =
        context.dataStore.data.map { decodeTargetConfigs(it[Keys.TARGET_CONFIGS]) }

    suspend fun targetConfigsSnapshot(): List<PostTargetConfig> =
        decodeTargetConfigs(context.dataStore.data.first()[Keys.TARGET_CONFIGS])

    suspend fun setTargetConfigs(configs: List<PostTargetConfig>) {
        context.dataStore.edit { it[Keys.TARGET_CONFIGS] = encodeTargetConfigs(configs) }
    }

    // --- Listening toggle / interval ------------------------------------------
    fun observeListeningEnabled(): Flow<Boolean> =
        context.dataStore.data.map { (it[Keys.LISTENING_ENABLED] ?: "false").toBoolean() }

    suspend fun setListeningEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.LISTENING_ENABLED] = enabled.toString() }
    }

    suspend fun listeningIntervalSecondsSnapshot(): Int =
        (context.dataStore.data.first()[Keys.LISTENING_INTERVAL_SECONDS] ?: "60").toIntOrNull() ?: 60

    suspend fun setListeningIntervalSeconds(seconds: Int) {
        context.dataStore.edit { it[Keys.LISTENING_INTERVAL_SECONDS] = seconds.toString() }
    }

    // --- (De)serialization helpers, package-visible for testing --------------
    internal fun decodeStringList(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        val arr = JSONArray(raw)
        return List(arr.length()) { arr.getString(it) }
    }

    internal fun encodeTargetConfigs(configs: List<PostTargetConfig>): String {
        val arr = JSONArray()
        configs.forEach { config ->
            arr.put(
                JSONObject().apply {
                    put("id", config.id)
                    put("type", config.type.name)
                    put("displayName", config.displayName)
                    put("enabled", config.enabled)
                    put("bodyTemplate", config.bodyTemplate)
                    put("payloadFormat", config.payloadFormat.name)
                    put("settings", JSONObject(config.settings))
                },
            )
        }
        return arr.toString()
    }

    internal fun decodeTargetConfigs(raw: String?): List<PostTargetConfig> {
        if (raw.isNullOrBlank()) return emptyList()
        val arr = JSONArray(raw)
        return List(arr.length()) { i ->
            val obj = arr.getJSONObject(i)
            val settingsObj = obj.optJSONObject("settings") ?: JSONObject()
            val settingsMap = settingsObj.keys().asSequence().associateWith { key -> settingsObj.getString(key) }
            PostTargetConfig(
                id = obj.getString("id"),
                type = TargetType.valueOf(obj.getString("type")),
                displayName = obj.getString("displayName"),
                enabled = obj.optBoolean("enabled", true),
                bodyTemplate = obj.getString("bodyTemplate"),
                payloadFormat = PayloadFormat.valueOf(obj.optString("payloadFormat", PayloadFormat.JSON.name)),
                settings = settingsMap,
            )
        }
    }

    companion object {
        @Volatile private var instance: SettingsRepository? = null
        fun getInstance(context: Context): SettingsRepository =
            instance ?: synchronized(this) {
                instance ?: SettingsRepository(context.applicationContext).also { instance = it }
            }
    }
}
