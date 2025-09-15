package com.dsatm.audio_redaction.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dsatm.audio_redaction.audio.AudioRecorder
import com.dsatm.audio_redaction.viewModel.AudioRedactionViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AudioRedactionScreen(
    viewModel: AudioRedactionViewModel = viewModel()
) {
    var isRecording by remember { mutableStateOf(false) }
    var audioRecorder by remember { mutableStateOf<AudioRecorder?>(null) }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val status by viewModel.status.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val transcriptionText by viewModel.transcriptionText.collectAsState()
    val redactedText by viewModel.redactedText.collectAsState()

    // Initialize AudioRecorder
    LaunchedEffect(Unit) {
        audioRecorder = AudioRecorder(context)
    }

    // File picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.processAudioFile(it)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Title
        Text(
            text = "Audio Redaction Tool",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // Status Section
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Status",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = status)

                if (isLoading) {
                    Spacer(modifier = Modifier.height(16.dp))
                    CircularProgressIndicator()
                }
            }
        }

        // Control Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // Record Button
            Button(
                onClick = {
                    if (isRecording) {
                        // Stop recording
                        audioRecorder?.let { recorder ->
                            val audioUri = recorder.stopRecording()
                            isRecording = false
                            // Launch a coroutine to add a delay before processing
                            coroutineScope.launch {
                                delay(500) // 500ms delay to allow file to be finalized
                                audioUri?.let { viewModel.processAudioFile(it) }
                            }
                        }
                    } else {
                        // Start recording
                        audioRecorder?.let { recorder ->
                            recorder.startRecording()
                            isRecording = true
                            viewModel.updateStatus("Recording audio...")
                        }
                    }
                },
                enabled = !isLoading,
                modifier = Modifier.weight(1f)
            ) {
                Text(text = if (isRecording) "Stop Recording" else "Start Recording")
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Select File Button
            Button(
                onClick = {
                    filePickerLauncher.launch("audio/*")
                },
                enabled = !isLoading && !isRecording,
                modifier = Modifier.weight(1f)
            ) {
                Text(text = "Select Audio File")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Transcription Results
        if (transcriptionText.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Original Transcription",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = transcriptionText,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        // Redacted Results
        if (redactedText.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Redacted Text",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = redactedText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Recording indicator
        if (isRecording) {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    text = "Recording in progress...",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
