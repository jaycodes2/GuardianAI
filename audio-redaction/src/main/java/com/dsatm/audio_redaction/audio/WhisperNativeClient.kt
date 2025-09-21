package com.dsatm.audio_redaction.audio

class WhisperNativeClient {

    companion object {
        init {
            System.loadLibrary("whisper")
        }
    }

    external fun loadModel(modelPath: String, vocabPath: String): Boolean

    external fun transcribe(pathToWaveFile: String): String
}
