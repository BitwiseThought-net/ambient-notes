package com.ambientnotes.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Landing screen: toggle ambient listening on/off, quick links to Settings
 * (recognition sources + targets) and History (past matches).
 */
@Composable
fun HomeScreen(
    onRequestPermissions: () -> Unit,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToHistory: () -> Unit,
) {
    var listening by remember { mutableStateOf(false) }

    Scaffold(topBar = { TopAppBar(title = { Text("AmbientNotes") }) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.Top,
        ) {
            Text(
                text = if (listening) "Listening for ambient music\u2026" else "Not listening",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(16.dp))
            Switch(
                checked = listening,
                onCheckedChange = { checked ->
                    listening = checked
                    if (checked) {
                        onRequestPermissions()
                        onStartListening()
                    } else {
                        onStopListening()
                    }
                },
            )
            Spacer(Modifier.height(32.dp))
            Button(onClick = onNavigateToSettings) { Text("Recognition sources & targets") }
            Spacer(Modifier.height(8.dp))
            Button(onClick = onNavigateToHistory) { Text("History") }
        }
    }
}
