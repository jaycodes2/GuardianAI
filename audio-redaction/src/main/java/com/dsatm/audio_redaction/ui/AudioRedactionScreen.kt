package com.dsatm.audio_redaction.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dsatm.audio_redaction.viewModel.AudioRedactionViewModel
import androidx.compose.runtime.collectAsState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioRedactionScreen(
    viewModel: AudioRedactionViewModel = viewModel()
) {
    val context = LocalContext.current

    val status by viewModel.status.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val transcriptionText by viewModel.transcriptionText.collectAsState()
    val redactedText by viewModel.redactedText.collectAsState()
    val piiEntities by viewModel.piiEntities.collectAsState()

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.processAudio(it)
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Audio Redaction Tool") }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Status: $status",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Button(
                onClick = { filePickerLauncher.launch("audio/*") },
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Select Audio File")
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (transcriptionText.isNotBlank()) {
                Text(
                    "Transcription:",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    transcriptionText,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                )
            }

            if (piiEntities.isNotEmpty()) {
                Text(
                    "Identified PII Entities:",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth()
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    piiEntities.forEach { entity ->
                        Text(
                            "${entity.label}: ${entity.text}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            if (redactedText.isNotBlank()) {
                Text(
                    "Redacted Text:",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    redactedText,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (isLoading) {
                Spacer(modifier = Modifier.height(24.dp))
                CircularProgressIndicator()
            }
        }
    }
}
