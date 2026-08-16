package com.ambientnotes.app.di

import android.content.Context
import com.ambientnotes.app.data.AppDatabase
import com.ambientnotes.app.data.SettingsRepository
import com.ambientnotes.app.recognition.RecognitionOrchestrator
import com.ambientnotes.app.recognition.RecognitionProviderFactory
import com.ambientnotes.app.targets.TargetPostingCoordinator

/**
 * Minimal, hand-rolled dependency container -- deliberately not Hilt/Dagger
 * to keep the project buildable without an extra annotation-processor setup
 * for reviewers just checking the code out. Swap in Hilt if the app grows
 * enough screens/features to want @Inject-based wiring; the seams here
 * (constructor injection everywhere) make that a mechanical refactor.
 */
class AppContainer(context: Context) {
    val settingsRepository = SettingsRepository.getInstance(context)
    val database = AppDatabase.getInstance(context)
    val providerFactory = RecognitionProviderFactory(settingsRepository)
    val orchestrator = RecognitionOrchestrator(providerFactory.buildAll())
    val targetPostingCoordinator = TargetPostingCoordinator()
}
