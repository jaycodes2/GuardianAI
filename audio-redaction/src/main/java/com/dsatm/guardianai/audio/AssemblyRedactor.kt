package com.dsatm.guardianai.audio

import android.media.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONObject
import java.io.File
import java.io.IOException
import kotlin.math.PI
import kotlin.math.sin

class AssemblyRedactor(private val apiKey: String) {
    private val client = OkHttpClient()
    private val base = "https://api.assemblyai.com"

    // -----------------------------
    // 1. Upload file to AssemblyAI
    // -----------------------------
    private fun uploadFile(file: File): String {
        val body = RequestBody.create("application/octet-stream".toMediaTypeOrNull(), file)
        val req = Request.Builder()
            .url("$base/v2/upload")
            .header("authorization", apiKey)
            .post(body)
            .build()

        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("Upload failed: ${resp.message}")
            val json = JSONObject(resp.body!!.string())
            return json.getString("upload_url")
        }
    }

    // -----------------------------
    // 2. Request transcript
    // -----------------------------
    private fun requestTranscript(uploadUrl: String): String {
        val json = """
            {
              "audio_url": "$uploadUrl",
              "redact_pii": true,
              "redact_pii_policies": ["person_name", "organization", "occupation"],
              "redact_pii_sub": "hash",
              "entity_detection": true
            }
        """.trimIndent()

        val body = RequestBody.create("application/json".toMediaTypeOrNull(), json)
        val req = Request.Builder()
            .url("$base/v2/transcript")
            .header("authorization", apiKey)
            .post(body)
            .build()

        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("Transcript request failed")
            val jsonResp = JSONObject(resp.body!!.string())
            return jsonResp.getString("id")
        }
    }

    // -----------------------------
    // 3. Poll for transcript
    // -----------------------------
    private fun pollTranscript(transcriptId: String): JSONObject {
        val url = "$base/v2/transcript/$transcriptId"
        while (true) {
            val req = Request.Builder()
                .url(url)
                .header("authorization", apiKey)
                .get()
                .build()

            client.newCall(req).execute().use { resp ->
                val json = JSONObject(resp.body!!.string())
                val status = json.getString("status")
                when (status) {
                    "completed" -> return json
                    "error" -> throw IOException("Transcription error: ${json.getString("error")}")
                }
            }
            Thread.sleep(3000)
        }
    }

    // -----------------------------
    // 4. Generate beep PCM
    // -----------------------------
    private fun generateBeep(durationMs: Int, freq: Double, sampleRate: Int = 44100): ShortArray {
        val numSamples = durationMs * sampleRate / 1000
        val buffer = ShortArray(numSamples)
        for (i in buffer.indices) {
            val angle = 2.0 * PI * i.toDouble() * freq / sampleRate
            buffer[i] = (sin(angle) * 32767).toInt().toShort()
        }
        return buffer
    }

    /**
     * Redacts PII from an audio file and saves the redacted version.
     * @param originalFile The input audio file to be redacted.
     * @param outputDirectory The directory where the redacted file should be saved.
     * @return A Pair containing the redacted transcript text and the File object of the redacted audio.
     */
    fun redactAndReplace(originalFile: File, outputDirectory: File): Pair<String, File> {
        val uploadUrl = uploadFile(originalFile)
        val transcriptId = requestTranscript(uploadUrl)
        val result = pollTranscript(transcriptId)

        val transcriptText = result.optString("text")
        println("Redacted Transcript: $transcriptText")

        val entities = result.optJSONArray("entities")
        if (entities == null || entities.length() == 0) {
            println("⚠️ No PII entities detected. Nothing to beep.")
            // Create a copy of the original file if no changes were made.
            val outFile = File(outputDirectory, "redacted_${originalFile.nameWithoutExtension}.m4a")
            originalFile.copyTo(outFile, overwrite = true)
            return Pair(transcriptText, outFile)
        }

        // Decode audio → PCM
        val extractor = MediaExtractor()
        extractor.setDataSource(originalFile.absolutePath)
        val format = extractor.getTrackFormat(0)
        extractor.selectTrack(0)

        val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

        val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
        codec.configure(format, null, null, 0)
        codec.start()

        val decodedPcm = mutableListOf<Short>()
        val inputBuffers = codec.inputBuffers
        val outputBuffers = codec.outputBuffers
        val bufferInfo = MediaCodec.BufferInfo()

        var sawInputEOS = false
        var sawOutputEOS = false

        while (!sawOutputEOS) {
            if (!sawInputEOS) {
                val inputBufIndex = codec.dequeueInputBuffer(10000)
                if (inputBufIndex >= 0) {
                    val inputBuf = inputBuffers[inputBufIndex]
                    val sampleSize = extractor.readSampleData(inputBuf, 0)
                    if (sampleSize < 0) {
                        codec.queueInputBuffer(inputBufIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        sawInputEOS = true
                    } else {
                        val presentationTimeUs = extractor.sampleTime
                        codec.queueInputBuffer(inputBufIndex, 0, sampleSize, presentationTimeUs, 0)
                        extractor.advance()
                    }
                }
            }

            val outputBufIndex = codec.dequeueOutputBuffer(bufferInfo, 10000)
            if (outputBufIndex >= 0) {
                val outputBuf = outputBuffers[outputBufIndex]
                val chunk = ByteArray(bufferInfo.size)
                outputBuf.get(chunk)
                outputBuf.clear()

                val shortBuffer = java.nio.ByteBuffer.wrap(chunk).order(java.nio.ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                val shorts = ShortArray(shortBuffer.remaining())
                shortBuffer.get(shorts)
                decodedPcm.addAll(shorts.toList())

                codec.releaseOutputBuffer(outputBufIndex, false)

                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    sawOutputEOS = true
                }
            }
        }

        codec.stop()
        codec.release()
        extractor.release()

        // Apply redaction: replace PCM ranges with beeps
        for (i in 0 until entities.length()) {
            val ent = entities.getJSONObject(i)
            val startMs = ent.getInt("start")
            val endMs = ent.getInt("end")
            val durationMs = endMs - startMs

            val startSample = (startMs * sampleRate / 1000) * channelCount
            val endSample = (endMs * sampleRate / 1000) * channelCount

            val beep = generateBeep(durationMs, 1000.0, sampleRate)

            for (j in startSample until endSample step channelCount) {
                val beepIndex = (j - startSample) / channelCount
                val beepValue = if (beepIndex < beep.size) beep[beepIndex] else 0
                for (c in 0 until channelCount) {
                    if (j + c < decodedPcm.size) {
                        decodedPcm[j + c] = beepValue
                    }
                }
            }
        }

        // Encode PCM back → AAC (.m4a)
        val outFile = File(outputDirectory, "redacted_${originalFile.nameWithoutExtension}.m4a")
        val muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        val outFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channelCount)
        outFormat.setInteger(MediaFormat.KEY_BIT_RATE, 128000)
        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        encoder.configure(outFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder.start()

        val inputBuffersEnc = encoder.inputBuffers
        val outputBuffersEnc = encoder.outputBuffers
        val bufferInfoEnc = MediaCodec.BufferInfo()
        var trackIndex = -1
        var muxerStarted = false

        val pcmBuffer = java.nio.ByteBuffer.allocate(decodedPcm.size * 2)
        pcmBuffer.order(java.nio.ByteOrder.LITTLE_ENDIAN)
        for (s in decodedPcm) pcmBuffer.putShort(s)
        pcmBuffer.rewind()

        var offset = 0
        while (offset < pcmBuffer.limit()) {
            val inputBufIndex = encoder.dequeueInputBuffer(10000)
            if (inputBufIndex >= 0) {
                val inputBuf = inputBuffersEnc[inputBufIndex]
                inputBuf.clear()
                val size = minOf(inputBuf.limit(), pcmBuffer.remaining())
                if (size <= 0) {
                    encoder.queueInputBuffer(inputBufIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    break
                }
                val chunk = ByteArray(size)
                pcmBuffer.get(chunk)
                inputBuf.put(chunk)
                encoder.queueInputBuffer(inputBufIndex, 0, size, 0, 0)
                offset += size
            }

            var outputBufIndex = encoder.dequeueOutputBuffer(bufferInfoEnc, 10000)
            while (outputBufIndex >= 0) {
                val encodedData = outputBuffersEnc[outputBufIndex]
                if (bufferInfoEnc.size > 0) {
                    encodedData.position(bufferInfoEnc.offset)
                    encodedData.limit(bufferInfoEnc.offset + bufferInfoEnc.size)

                    if (!muxerStarted) {
                        trackIndex = muxer.addTrack(encoder.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                    muxer.writeSampleData(trackIndex, encodedData, bufferInfoEnc)
                }
                encoder.releaseOutputBuffer(outputBufIndex, false)
                outputBufIndex = encoder.dequeueOutputBuffer(bufferInfoEnc, 0)
            }
        }

        encoder.stop()
        encoder.release()
        muxer.stop()
        muxer.release()

        println("Redacted audio saved to: ${outFile.absolutePath}")
        return Pair(transcriptText, outFile)
    }
}