package com.example.vosktranscribe

import android.content.Context
import android.net.Uri
import android.util.Log
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object VoskTranscriber {

    private var model: Model? = null

    /**
     * Initialize Vosk model from assets/models/
     * e.g. assets/models/model-en-us
     */
    fun initModel(context: Context, assetModelPath: String = "vosk-model-en-us-0.22-lgraph") {
        if (model == null) {
            // Copy model folder from assets to internal storage
            val modelDir = File(context.filesDir, "vosk-model")
            if (!modelDir.exists()) {
                copyAssetFolder(context, assetModelPath, modelDir.absolutePath)
            }
            model = Model(modelDir.absolutePath)
        }
    }

    /**
     * Transcribe audio file from a Uri (WAV/PCM recommended)
     */
    fun transcribe(context: Context, audioUri: Uri): String {
        if (model == null) {
            initModel(context)
        }

        val tempFile = copyToTemp(context, audioUri)
        val audioBytes = tempFile.readBytes()

        // Create recognizer
        val recognizer = Recognizer(model, 16000f)
        recognizer.setWords(true)

        // Feed audio as little-endian PCM
        val bb = ByteBuffer.wrap(audioBytes).order(ByteOrder.LITTLE_ENDIAN)
        val buffer = ByteArray(4096)
        while (bb.hasRemaining()) {
            val len = minOf(buffer.size, bb.remaining())
            bb.get(buffer, 0, len)
            recognizer.acceptWaveForm(buffer, len)
        }

        val result = recognizer.finalResult
        Log.d("VoskTranscriber", "Result: $result")
        recognizer.close()

        // Extract text from JSON result
        return Regex("\"text\"\\s*:\\s*\"(.*?)\"").find(result)?.groupValues?.get(1) ?: ""
    }

    private fun copyToTemp(context: Context, uri: Uri): File {
        val tempFile = File(context.cacheDir, "temp_audio.wav")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        }
        return tempFile
    }

    private fun copyAssetFolder(context: Context, assetPath: String, destPath: String) {
        val destDir = File(destPath)
        if (!destDir.exists()) destDir.mkdirs()

        val assetManager = context.assets
        val files = assetManager.list(assetPath) ?: return

        for (file in files) {
            val assetFilePath = "$assetPath/$file"
            val destFile = File(destDir, file)
            val subFiles = assetManager.list(assetFilePath)
            if (subFiles != null && subFiles.isNotEmpty()) {
                copyAssetFolder(context, assetFilePath, destFile.absolutePath)
            } else {
                assetManager.open(assetFilePath).use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }
}
