package com.ambientnotes.app.recognition

import com.ambientnotes.app.data.SettingsRepository
import com.ambientnotes.app.recognition.providers.AcrCloudCredentials
import com.ambientnotes.app.recognition.providers.AcrCloudProvider
import com.ambientnotes.app.recognition.providers.AudDProvider
import com.ambientnotes.app.recognition.providers.SelfHostedConnection
import com.ambientnotes.app.recognition.providers.SelfHostedServiceProvider
import com.ambientnotes.app.recognition.providers.ShazamKitProvider

/**
 * Builds the list of [RecognitionProvider] instances the app knows about,
 * wired to read live credentials from [SettingsRepository]. Order here
 * defines the default try-order in [RecognitionOrchestrator]; the user can
 * reorder/enable/disable individual providers in Settings, which is applied
 * by [RecognitionOrchestrator] filtering this list, not by rebuilding it.
 */
class RecognitionProviderFactory(private val settingsRepository: SettingsRepository) {

    fun buildAll(): List<RecognitionProvider> = listOf(
        AcrCloudProvider(credentialsProvider = {
            val creds = settingsRepository.acrCloudCredentialsSnapshot()
            if (creds.host.isNullOrBlank() || creds.accessKey.isNullOrBlank() || creds.accessSecret.isNullOrBlank()) {
                null
            } else {
                AcrCloudCredentials(creds.host, creds.accessKey, creds.accessSecret)
            }
        }),
        AudDProvider(apiTokenProvider = { settingsRepository.audDApiTokenSnapshot() }),
        ShazamKitProvider(),
        SelfHostedServiceProvider(connectionProvider = {
            val conn = settingsRepository.selfHostedConnectionSnapshot()
            if (conn.baseUrl.isNullOrBlank() || conn.apiKey.isNullOrBlank()) {
                null
            } else {
                SelfHostedConnection(conn.baseUrl, conn.apiKey, conn.deviceId)
            }
        }),
    )
}
