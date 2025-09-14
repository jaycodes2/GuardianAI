package com.dsatm.text_redaction.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dsatm.ner.BertNerOnnxManager
import com.dsatm.ner.PiiEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

sealed class UiState {
    object InitialLoading : UiState()
    object Ready : UiState()
    object Processing : UiState()
    data class Result(val redactedText: String, val entities: List<PiiEntity>) : UiState()
    data class Error(val message: String) : UiState()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextRedactionScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val nerManager = remember { BertNerOnnxManager(context) }

    var inputText by remember { mutableStateOf("Good afternoon, Tom Spence. We're from Assisi Insurance. We need to validate your account details. Your registered address is 6,774 Darling Loaf. West Nicole, Minnesota 24956. Right?") }
    var uiState by remember { mutableStateOf<UiState>(UiState.InitialLoading) }

    // This LaunchedEffect will only run ONCE to initialize the model.
    LaunchedEffect(Unit) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                nerManager.initialize()
                uiState = UiState.Ready
            } catch (e: Exception) {
                uiState = UiState.Error("Initialization Error: ${e.message}")
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Text Redaction",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(24.dp))

                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    label = { Text("Enter text to redact") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        // The Button is only enabled when the model is ready
                        if (uiState is UiState.Ready) {
                            coroutineScope.launch(Dispatchers.IO) {
                                uiState = UiState.Processing
                                try {
                                    val entities = nerManager.detectPii(inputText)
                                    val redactedText = nerManager.postProcessor.redactTextWithLabels(inputText, entities)
                                    uiState = UiState.Result(redactedText, entities)
                                } catch (e: Exception) {
                                    uiState = UiState.Error("Detection Error: ${e.message}")
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = uiState is UiState.Ready
                ) {
                    Text("Redact Text")
                }

                Spacer(modifier = Modifier.height(24.dp))

                when (val currentState = uiState) {
                    is UiState.InitialLoading -> {
                        CircularProgressIndicator()
                        Text("Loading model...")
                    }
                    is UiState.Ready -> {
                        // The UI is ready for user input
                        Text("Model is ready. Please enter text and press 'Redact'.")
                    }
                    is UiState.Processing -> {
                        CircularProgressIndicator()
                        Text("Processing text...")
                    }
                    is UiState.Result -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Redacted Text:",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = currentState.redactedText, style = MaterialTheme.typography.bodyMedium)

                            Spacer(modifier = Modifier.height(24.dp))

                            if (currentState.entities.isNotEmpty()) {
                                Text(
                                    text = "Detected Entities:",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                currentState.entities.forEach { entity ->
                                    Text(text = "${entity.label}: ${entity.text}")
                                }
                            } else {
                                Text(text = "No PII entities detected.")
                            }
                        }
                    }
                    is UiState.Error -> {
                        Text(text = currentState.message, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

