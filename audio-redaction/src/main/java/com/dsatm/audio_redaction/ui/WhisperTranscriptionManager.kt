package com.dsatm.audio_redaction.ui

import android.content.Context
import android.util.Log
import com.dsatm.audio_redaction.audio.Recorder
import com.dsatm.audio_redaction.audio.WhisperNativeClient
import java.io.File

class WhisperTranscriptionManager(private val context: Context) {

    private val whisperClient = WhisperNativeClient()

    // Prepare and load model files (run once)
    private fun prepareModelFiles(): Boolean {
        val modelFile = File(context.filesDir, "whisper-tiny.tflite")
        val vocabFile = File(context.filesDir, "filters_vocab_multilingual.bin")

        if (!modelFile.exists()) {
            context.assets.open("whisper-tiny.tflite").use { input ->
                modelFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
        if (!vocabFile.exists()) {
            context.assets.open("filters_vocab_multilingual.bin").use { input ->
                vocabFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }

        val loaded = whisperClient.loadModel(modelFile.absolutePath, vocabFile.absolutePath)
        if (!loaded) {
            Log.e("WhisperTranscriptionManager", "Failed to load Whisper model")
        }

        return loaded
    }

    /**
     * Transcribes an existing audio file located at [filePath].
     * Returns the transcription string or null on failure.
     */
    fun transcribeAudioFile(filePath: String): String? {
        if (!prepareModelFiles()) return null

        Log.d("WhisperTranscriptionManager", "Transcribing audio file: $filePath")
        val transcript = whisperClient.transcribe(filePath)
        Log.d("WhisperTranscriptionManager", "Transcript: $transcript")

        return transcript
    }

    /**
     * Records audio for the given [durationMillis], then returns the transcription.
     *
     * Warning: This function blocks the calling thread while recording.
     * You should call this from a background thread.
     */
    fun transcribeAudioRecording(durationMillis: Long = 5000L): String? {
        if (!prepareModelFiles()) return null

        val recorder = Recorder()
        recorder.start()
        try {
            Thread.sleep(durationMillis)
        } catch (e: InterruptedException) {
            Log.e("WhisperTranscriptionManager", "Recording interrupted", e)
            recorder.stop()
            return null
        }
        recorder.stop()

        return transcribeAudioFile(recorder.filePath)
    }
}
