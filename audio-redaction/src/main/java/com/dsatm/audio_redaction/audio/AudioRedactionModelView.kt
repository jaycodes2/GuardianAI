package com.dsatm.audio_redaction.audio


import android.app.Application
import android.net.Uri
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dsatm.audio_redaction.RedactionManager
import com.dsatm.core.vosk.VoskTranscriber
import com.dsatm.ner.MobileBertAnalyzer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AudioRedactionViewModel(application: Application) : AndroidViewModel(application) {

    val status = mutableStateOf("Ready")
    val isLoading = mutableStateOf(false)

    private val voskTranscriber = VoskTranscriber(application.applicationContext)
    private val mobileBertAnalyzer = MobileBertAnalyzer(application.applicationContext)
    private val redactionManager = RedactionManager(application.applicationContext)

    fun processAudio(audioUri: Uri?) {
        if (audioUri == null) {
            status.value = "No audio file selected."
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            isLoading.value = true
            status.value = "Transcribing audio..."

            // 1. Transcribe Audio
            val transcriptionResult = voskTranscriber.transcribe(audioUri)

            if (transcriptionResult == null) {
                status.value = "Transcription failed."
                isLoading.value = false
                return@launch
            }

            // 2. Analyze for PII
            status.value = "Analyzing text for PII..."
            val piiEntities = mobileBertAnalyzer.analyze(transcriptionResult.fullText)

            if (piiEntities.isEmpty()) {
                status.value = "No PII found. Audio is clean."
                isLoading.value = false
                return@launch
            }

            // 3. Redact Audio
            status.value = "Redacting audio..."
            val redactedAudioUri = redactionManager.redactAudio(audioUri, piiEntities, transcriptionResult)

            // 4. Update UI with result
            if (redactedAudioUri != null) {
                status.value = "Audio redacted successfully! Saved to: ${redactedAudioUri.path}"
            } else {
                status.value = "Audio redaction failed."
            }

            isLoading.value = false
        }
    }
}
