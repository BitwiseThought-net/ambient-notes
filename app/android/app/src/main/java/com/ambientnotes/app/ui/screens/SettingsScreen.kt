package com.ambientnotes.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Reference settings screen listing recognition-source and target sections.
 * Each row is a placeholder navigation target for a dedicated credential-entry
 * form (e.g. "ACRCloud" -> host/key/secret fields backed by
 * [com.ambientnotes.app.data.SettingsRepository.setAcrCloudCredentials]).
 * Wiring every provider/target's full edit form is mechanical repetition of
 * the same pattern -- see docs/CONFIGURATION.md for the field list per
 * provider/target and ViewModel + form pairing conventions to follow when
 * extending this screen.
 */
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val recognitionSources = listOf("ACRCloud", "AudD", "ShazamKit (unavailable on Android)", "Self-hosted service")
    val targetTypes = listOf(
        "Webhook", "Mastodon", "Bluesky", "X / Twitter", "Threads", "Facebook", "Reddit", "LinkedIn", "Tumblr",
    )

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item { SectionHeader("Recognition sources") }
            items(recognitionSources) { name -> ListItem(headlineContent = { Text(name) }) }
            item { SectionHeader("Post targets") }
            items(targetTypes) { name -> ListItem(headlineContent = { Text(name) }) }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(16.dp),
    )
}
