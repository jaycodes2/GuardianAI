package com.dsatm.audio_redaction.audio

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dsatm.audio_redaction.RedactionManager
import com.dsatm.audio_redaction.ui.WhisperTranscriptionManager
import com.dsatm.ner.MobileBertAnalyzer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class AudioRedactionViewModel(application: Application) : AndroidViewModel(application) {

    val status = mutableStateOf("Ready")
    val isLoading = mutableStateOf(false)

    val transcriptionText = mutableStateOf("")
    val redactedText = mutableStateOf("")
    val piiEntities = mutableStateOf(listOf<Any>())  // Replace Any with your PII entity data class

    private val redactionManager = RedactionManager(application.applicationContext)
    private val mobileBertAnalyzer = MobileBertAnalyzer(application.applicationContext)

    private val whisperTranscriptionManager =
        WhisperTranscriptionManager(application.applicationContext)

    /**
     * Call this method with a File path string to audio file recorded or picked.
     * Transcribes using Whisper, analyzes and redacts.
     */
    fun processAudioFilePath(filePath: String) {
        viewModelScope.launch(Dispatchers.IO) {
            isLoading.value = true
            status.value = "Transcribing audio with Whisper..."

            // 1. Transcribe using Whisper
            val transcriptionResult = whisperTranscriptionManager.transcribeAudioFile(filePath)

            if (transcriptionResult == null) {
                status.value = "Transcription failed."
                isLoading.value = false
                return@launch
            }

            transcriptionText.value = transcriptionResult

            // 2. Analyze for PII
            status.value = "Analyzing text for PII..."
            val entities = mobileBertAnalyzer.analyze(transcriptionResult)
            piiEntities.value = entities

            if (entities.isEmpty()) {
                status.value = "No PII found. Audio is clean."
                redactedText.value = transcriptionResult
                isLoading.value = false
                return@launch
            }

            // 3. Redact
            status.value = "Redacting audio and text..."
            // Assuming redactionManager can accept a file path or Uri, update as needed
            val originalUri = Uri.fromFile(File(filePath))
            val redactedUri = redactionManager.redactAudio(originalUri, entities, null) // Adjust params as per your method

            // 4. Update UI with redacted text and status
            if (redactedUri != null) {
                status.value = "Audio redacted successfully! Saved to: ${redactedUri.path}"
                // Optionally, update redactedText.value if your redaction manages textual output
            } else {
                status.value = "Audio redaction failed."
            }

            isLoading.value = false
        }
    }

    // Methods for Compose UI direct use, if needed:
    fun updateTranscription(text: String) {
        transcriptionText.value = text
    }

    fun processTranscriptionForRedaction(text: String) {
        viewModelScope.launch(Dispatchers.IO) {
            status.value = "Analyzing text for PII..."
            val entities = mobileBertAnalyzer.analyze(text)
            piiEntities.value = entities

            if (entities.isEmpty()) {
                status.value = "No PII found."
                redactedText.value = text
            } else {
                status.value = "Redacting text..."
                // Call existing text/audio redaction methods
                val redacted = redactionManager.redactText(text, entities)  // Example: adapt if you have this
                redactedText.value = redacted
                status.value = "Redaction complete."
            }
        }
    }
}
