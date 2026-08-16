package com.ambientnotes.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ambientnotes.app.audio.AudioCaptureService
import com.ambientnotes.app.ui.screens.HistoryScreen
import com.ambientnotes.app.ui.screens.HomeScreen
import com.ambientnotes.app.ui.screens.SettingsScreen
import com.ambientnotes.app.ui.theme.AmbientNotesTheme

class MainActivity : ComponentActivity() {

    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { /* handled via UI state */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AmbientNotesTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "home") {
                    composable("home") {
                        HomeScreen(
                            onRequestPermissions = ::requestRequiredPermissions,
                            onStartListening = ::startListeningService,
                            onStopListening = ::stopListeningService,
                            onNavigateToSettings = { navController.navigate("settings") },
                            onNavigateToHistory = { navController.navigate("history") },
                        )
                    }
                    composable("settings") { SettingsScreen(onBack = { navController.popBackStack() }) }
                    composable("history") { HistoryScreen(onBack = { navController.popBackStack() }) }
                }
            }
        }
    }

    private fun requestRequiredPermissions() {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions += Manifest.permission.POST_NOTIFICATIONS
        }
        val notGranted = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (notGranted.isNotEmpty()) requestPermissionsLauncher.launch(notGranted.toTypedArray())
    }

    private fun startListeningService() {
        ContextCompat.startForegroundService(this, Intent(this, AudioCaptureService::class.java))
    }

    private fun stopListeningService() {
        stopService(Intent(this, AudioCaptureService::class.java))
    }
}
