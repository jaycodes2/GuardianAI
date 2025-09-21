package com.dsatm.core.vosk

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaCodec
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.*

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

    private var model: Model? = null
    private var isInitializing = false

    private suspend fun getModel(): Model {
        if (model != null) return model!!
        while (isInitializing) { kotlinx.coroutines.delay(100) }
        if (model != null) return model!!
        isInitializing = true

        return withContext(Dispatchers.IO) {
            try {
                // load model
                val modelPath = unpackModelFromAssets("vosk-model-en-us-0.22-lgraph")
                val loadedModel = Model(modelPath)
                model = loadedModel
                loadedModel
            } finally {
                isInitializing = false
            }
        }
    }

    @Throws(IOException::class)
    private fun unpackModelFromAssets(modelName: String): String {
        val targetDir = File(context.filesDir, "vosk-model-store")
        val modelPath = File(targetDir, modelName)
        val marker = File(modelPath, ".unpacked")
        if (marker.exists()) return modelPath.absolutePath

        targetDir.deleteRecursively()
        targetDir.mkdirs()
        modelPath.mkdirs()

        val assetManager = context.assets
        val assetPaths = assetManager.list(modelName)
            ?: throw IOException("Asset folder '$modelName' not found.")
        for (path in assetPaths) {
            copyAssetRecursively("$modelName/$path", File(modelPath, path))
        }
        marker.createNewFile()
        return modelPath.absolutePath
    }

    private fun copyAssetRecursively(assetPath: String, destFile: File) {
        try {
            val subAssets = context.assets.list(assetPath)
            if (!subAssets.isNullOrEmpty()) {
                destFile.mkdirs()
                for (subAsset in subAssets) {
                    copyAssetRecursively("$assetPath/$subAsset", File(destFile, subAsset))
                }
            } else {
                context.assets.open(assetPath).use { input ->
                    FileOutputStream(destFile).use { output -> input.copyTo(output) }
                }
            }
        } catch (e: IOException) {
            context.assets.open(assetPath).use { input ->
                FileOutputStream(destFile).use { output -> input.copyTo(output) }
            }
        }
    }

    // Converts any input file to 16kHz PCM mono WAV in cacheDir
    private suspend fun convertToPcmWav(inputUri: Uri): File = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        extractor.setDataSource(context, inputUri, null)
        val numTracks = extractor.trackCount
        var audioTrackIndex = -1
        for (i in 0 until numTracks) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("audio/")) {
                audioTrackIndex = i
                break
            }
        }
        if (audioTrackIndex < 0) throw IOException("No audio track found")

        extractor.selectTrack(audioTrackIndex)
        val format = extractor.getTrackFormat(audioTrackIndex)
        val mime = format.getString(MediaFormat.KEY_MIME)!!

        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()

        val outFile = File(context.cacheDir, "converted_input.wav")
        FileOutputStream(outFile).use { fos ->
            // placeholder WAV header
            val header = ByteArray(44)
            fos.write(header)
            val bufferInfo = MediaCodec.BufferInfo()

            var totalPcmBytes = 0
            while (true) {
                val inIndex = codec.dequeueInputBuffer(10000)
                if (inIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inIndex)!!
                    val sampleSize = extractor.readSampleData(inputBuffer, 0)
                    if (sampleSize < 0) {
                        codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    } else {
                        val presentationTimeUs = extractor.sampleTime
                        codec.queueInputBuffer(inIndex, 0, sampleSize, presentationTimeUs, 0)
                        extractor.advance()
                    }
                }

                val outIndex = codec.dequeueOutputBuffer(bufferInfo, 10000)
                if (outIndex >= 0) {
                    val outputBuffer = codec.getOutputBuffer(outIndex)!!
                    val pcmBytes = ByteArray(bufferInfo.size)
                    outputBuffer.get(pcmBytes)
                    fos.write(pcmBytes)
                    totalPcmBytes += pcmBytes.size
                    codec.releaseOutputBuffer(outIndex, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
            }

            codec.stop()
            codec.release()
            extractor.release()

            fos.flush()
            fos.channel.position(0)
            fos.write(createWavHeader(totalPcmBytes, 16000, 1))
        }

        outFile
    }

    private fun createWavHeader(pcmDataLength: Int, sampleRate: Int, channels: Int): ByteArray {
        val totalDataLen = pcmDataLength + 36
        val byteRate = sampleRate * channels * 2
        return ByteArrayOutputStream().apply {
            write("RIFF".toByteArray())
            write(intToLE(totalDataLen))
            write("WAVE".toByteArray())
            write("fmt ".toByteArray())
            write(intToLE(16))
            write(shortToLE(1)) // PCM
            write(intToLE(channels))
            write(intToLE(sampleRate))
            write(intToLE(byteRate))
            write(shortToLE((channels * 2).toShort()))
            write(shortToLE(16)) // bits per sample
            write("data".toByteArray())
            write(intToLE(pcmDataLength))
        }.toByteArray()
    }

    private fun intToLE(value: Int): ByteArray = byteArrayOf(
        (value and 0xff).toByte(),
        (value shr 8 and 0xff).toByte(),
        (value shr 16 and 0xff).toByte(),
        (value shr 24 and 0xff).toByte()
    )

    private fun shortToLE(value: Short): ByteArray = byteArrayOf(
        (value.toInt() and 0xff).toByte(),
        (value.toInt() shr 8 and 0xff).toByte()
    )

    // Convert spelled numbers to digits
    private fun normalizeNumbers(text: String): String {
        val map = mapOf(
            "zero" to "0", "one" to "1", "two" to "2", "three" to "3",
            "four" to "4", "five" to "5", "six" to "6", "seven" to "7",
            "eight" to "8", "nine" to "9"
        )
        return text.split(" ").joinToString(" ") { word ->
            map[word.lowercase()] ?: word
        }
    }

    suspend fun transcribe(audioUri: Uri): TranscriptionResult? {
        val currentModel: Model = getModel()

        val wavFile = convertToPcmWav(audioUri)

        val rec = Recognizer(currentModel, 16000.0f)
        rec.setWords(true)

        return withContext(Dispatchers.IO) {
            FileInputStream(wavFile).use { ais ->
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

                var fullText = jsonObject.getString("text")
                fullText = normalizeNumbers(fullText)

                val wordResults = mutableListOf<WordResult>()

                if (jsonObject.has("result")) {
                    val resultArray = jsonObject.getJSONArray("result")
                    for (i in 0 until resultArray.length()) {
                        val wordJson = resultArray.getJSONObject(i)
                        var w = wordJson.getString("word")
                        // normalize numbers per word
                        w = normalizeNumbers(w)
                        wordResults.add(
                            WordResult(
                                word = w,
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
}
