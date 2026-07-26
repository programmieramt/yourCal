package com.example.calorietracker.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
) {
    val settingsStore = viewModel.settingsStore
    val currentApiKey by settingsStore.apiKeyFlow.collectAsState()
    val currentGoal by settingsStore.weeklyGoalFlow.collectAsState()

    var apiKeyInput by rememberSaveable(currentApiKey) { mutableStateOf(currentApiKey.orEmpty()) }
    var goalInput by rememberSaveable(currentGoal) { mutableStateOf(currentGoal.toString()) }
    var showApiKey by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Einstellungen") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            Text(
                "Anthropic API-Key",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Wird verschlüsselt nur auf diesem Gerät gespeichert und ausschließlich " +
                    "an api.anthropic.com gesendet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = apiKeyInput,
                onValueChange = { apiKeyInput = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("API-Key (sk-ant-...)") },
                singleLine = true,
                visualTransformation = if (showApiKey) {
                    VisualTransformation.None
                } else {
                    VisualTransformation { text ->
                        TransformedText(
                            AnnotatedString("•".repeat(text.text.length)),
                            OffsetMapping.Identity,
                        )
                    }
                },
                trailingIcon = {
                    Text(
                        if (showApiKey) "Verbergen" else "Anzeigen",
                        modifier = Modifier.padding(end = 8.dp),
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
            )
            Spacer(modifier = Modifier.height(4.dp))
            Button(onClick = { showApiKey = !showApiKey }) {
                Text(if (showApiKey) "Key verbergen" else "Key anzeigen")
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text("Wochenziel", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = goalInput,
                onValueChange = { input -> goalInput = input.filter { it.isDigit() } },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Kalorien pro Woche") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    settingsStore.apiKey = apiKeyInput
                    goalInput.toIntOrNull()?.let { settingsStore.weeklyGoalCalories = it }
                    onBack()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Speichern")
            }
        }
    }
}
