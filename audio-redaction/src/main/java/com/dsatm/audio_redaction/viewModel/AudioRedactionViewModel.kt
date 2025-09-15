
package com.dsatm.audio_redaction.viewModel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dsatm.audio_redaction.RedactionManager
import com.dsatm.audio_redaction.audio.AudioConverter
import com.dsatm.core.vosk.VoskTranscriber
import com.dsatm.ner.MobileBertAnalyzer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class AudioRedactionViewModel(application: Application) : AndroidViewModel(application) {

    // --- StateFlows for UI state ---
    private val _status = MutableStateFlow("Ready")
    val status = _status.asStateFlow()
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()
    private val _transcriptionText = MutableStateFlow("")
    val transcriptionText = _transcriptionText.asStateFlow()
    private val _redactedText = MutableStateFlow("")
    val redactedText = _redactedText.asStateFlow()

    // --- Service instances ---
    private val voskTranscriber = VoskTranscriber(application)
    private val mobileBertAnalyzer = MobileBertAnalyzer(application)
    private val redactionManager = RedactionManager(application)
    private val audioConverter = AudioConverter(application)

    fun processAudioFile(audioUri: Uri?) {
        if (audioUri == null) {
            _status.value = "Error: Audio file not found."
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _status.value = "Starting audio processing..."
            _transcriptionText.value = ""
            _redactedText.value = ""
            var tempConvertedFile: File? = null

            try {
                // --- THE DEFINITIVE FIX: Convert audio if it'''s not in the correct format ---
                val uriToTranscribe: Uri = if (audioUri.scheme == "content") {
                    _status.value = "Converting audio file to required WAV format..."
                    val convertedFile = withContext(Dispatchers.IO) {
                        audioConverter.convertToWav(audioUri)
                    }
                    if (convertedFile == null) {
                        _status.value = "Audio conversion failed. The file format may be unsupported."
                        _isLoading.value = false
                        return@launch
                    }
                    tempConvertedFile = convertedFile // Track for cleanup
                    Uri.fromFile(convertedFile)
                } else {
                    // Audio is from our own recorder and is already in the correct format
                    audioUri
                }

                _status.value = "Transcribing audio..."
                val transcriptionResult = withContext(Dispatchers.IO) {
                    voskTranscriber.transcribe(uriToTranscribe)
                }

                if (transcriptionResult == null || transcriptionResult.fullText.isBlank()) {
                    _status.value = "Transcription failed. The audio may be silent or unclear."
                    _transcriptionText.value = "Could not transcribe audio."
                    return@launch
                }

                _status.value = "Transcription complete. Analyzing for PII..."
                _transcriptionText.value = transcriptionResult.fullText

                val piiEntities = withContext(Dispatchers.IO) {
                    mobileBertAnalyzer.analyze(transcriptionResult.fullText)
                }

                _status.value = "PII analysis complete. Generating redacted text..."
                var redactedDisplayText = transcriptionResult.fullText
                piiEntities.forEach { entity ->
                    redactedDisplayText = redactedDisplayText.replace(entity.text, "[REDACTED]")
                }
                _redactedText.value = redactedDisplayText

                // Future audio redaction call would go here

                _status.value = "Processing complete."

            } catch (e: Exception) {
                _status.value = "An error occurred: ${e.message}"
                e.printStackTrace()
            } finally {
                _isLoading.value = false
                // Clean up the temporary converted file
                tempConvertedFile?.delete()
            }
        }
    }

    fun updateStatus(newStatus: String) {
        _status.value = newStatus
    }
}
