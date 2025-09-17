package com.dsatm.core.vosk

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

// Data classes are unchanged
data class WordResult(
    val word: String,
    val start: Double,
    val end: Double,
    val conf: Double
)

data class TranscriptionResult(
    val fullText: String,
    val words: List<WordResult>
)

class VoskTranscriber(private val context: Context) {

    // Hold the model as a nullable property. It will be initialized once.
    private var model: Model? = null
    // A flag to ensure we only try to unpack once.
    private var isInitializing = false

    // This function will be the single entry point to get the model.
    // It's a suspend function to handle the async nature of unpacking.
    private suspend fun getModel(): Model {
        // If model is already loaded, return it.
        if (model != null) return model!!

        // If another coroutine is already initializing, wait for it to finish.
        // This is a simple lock to prevent multiple unpack attempts.
        while (isInitializing) {
            kotlinx.coroutines.delay(100)
        }

        // Check again in case another coroutine finished while we were waiting.
        if (model != null) return model!!

        // We are the first, claim the lock.
        isInitializing = true

        return withContext(Dispatchers.IO) {
            try {
                val modelPath = unpackModelFromAssets()
                val loadedModel = Model(modelPath)
                model = loadedModel // Store it for next time
                loadedModel
            } catch (e: Exception) {
                // To propagate the error, we re-throw it.
                throw IOException("CRITICAL: Failed to unpack or load Vosk model. Error: ${e.message}", e)
            } finally {
                // Release the lock
                isInitializing = false
            }
        }
    }

    // This is the new, manual unpacking logic. It replaces StorageService completely.
    @Throws(IOException::class)
    private fun unpackModelFromAssets(): String {
        val modelName = "vosk-model-en-us-0.22-lgraph"
        val targetDir = File(context.filesDir, "vosk-model-store") // A dedicated, clean directory
        val modelPath = File(targetDir, modelName)

        // Use a marker file to check if unpacking is already done.
        val marker = File(modelPath, ".unpacked")
        if (marker.exists()) {
            return modelPath.absolutePath
        }

        // If not done, clean up any previous failed attempts and start fresh.
        targetDir.deleteRecursively()
        targetDir.mkdirs()

        // THE FIX: The root destination directory must exist before we copy files into it.
        modelPath.mkdirs()

        // Manually copy all files and directories from the asset folder.
        val assetManager = context.assets
        val assetPaths = assetManager.list(modelName)
            ?: throw IOException("Asset folder '$modelName' not found.")

        for (path in assetPaths) {
            copyAssetRecursively("$modelName/$path", File(modelPath, path))
        }

        // Create the marker file to signify success.
        marker.createNewFile()
        return modelPath.absolutePath
    }

    // A helper function to copy files and directories from assets.
    private fun copyAssetRecursively(assetPath: String, destFile: File) {
        try {
            // Try to list assets. If it works and is not empty, it's a directory.
            val subAssets = context.assets.list(assetPath)
            if (!subAssets.isNullOrEmpty()) {
                destFile.mkdirs()
                for (subAsset in subAssets) {
                    copyAssetRecursively("$assetPath/$subAsset", File(destFile, subAsset))
                }
            } else { // It's a file.
                context.assets.open(assetPath).use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }
        } catch (e: IOException) {
            // This can happen if list() is called on a file. In that case, treat it as a file.
            context.assets.open(assetPath).use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
        }
    }


    // The public API.
    suspend fun transcribe(audioUri: Uri): TranscriptionResult? {
        val currentModel: Model
        try {
            // Get the model using our new suspend function.
            currentModel = getModel()
        } catch (e: Exception) {
            // If getModel() threw an exception, report it clearly.
            return TranscriptionResult(e.message ?: "CRITICAL: Unknown error loading model.", emptyList())
        }

        // The rest of the transcription logic is the same and is correct.
        val rec = Recognizer(currentModel, 16000.0f)
        rec.setWords(true)

        return context.contentResolver.openInputStream(audioUri)?.use { ais ->
            val b = ByteArray(4096)
            var nbytes: Int
            while (ais.read(b).also { nbytes = it } != -1) {
                rec.acceptWaveForm(b, nbytes)
            }

            val finalResultJson = rec.finalResult
            val jsonObject = JSONObject(finalResultJson)

            if (!jsonObject.has("text") || jsonObject.getString("text").isNullOrEmpty()) {
                return@use null
            }

            val fullText = jsonObject.getString("text")
            val wordResults = mutableListOf<WordResult>()

            if (jsonObject.has("result")) {
                val resultArray = jsonObject.getJSONArray("result")
                for (i in 0 until resultArray.length()) {
                    val wordJson = resultArray.getJSONObject(i)
                    wordResults.add(
                        WordResult(
                            word = wordJson.getString("word"),
                            start = wordJson.getDouble("start"),
                            end = wordJson.getDouble("end"),
                            conf = wordJson.getDouble("conf")
                        )
                    )
                }
            }

            TranscriptionResult(fullText, wordResults)
        }
    }
}
