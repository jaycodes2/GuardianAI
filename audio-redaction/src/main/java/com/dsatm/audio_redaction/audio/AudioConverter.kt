package com.dsatm.audio_redaction.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AudioConverter(private val context: Context) {

    companion object {
        private const val TARGET_SAMPLE_RATE = 16000
        private const val TARGET_CHANNEL_CONFIG = 1 // Mono
        private const val BITS_PER_SAMPLE = 16
        private const val BYTES_PER_SAMPLE = BITS_PER_SAMPLE / 8
    }

    suspend fun convertToWav(inputUri: Uri): File? = withContext(Dispatchers.IO) {
        val tempWavFile = File.createTempFile("converted_audio", ".wav", context.cacheDir)
        var extractor: MediaExtractor? = null
        var codec: MediaCodec? = null

        try {
            extractor = MediaExtractor()
            context.contentResolver.openFileDescriptor(inputUri, "r")?.use { pfd ->
                extractor.setDataSource(pfd.fileDescriptor)
            }

            val trackIndex = findAudioTrack(extractor)
            if (trackIndex == -1) return@withContext null
            extractor.selectTrack(trackIndex)

            val inputFormat = extractor.getTrackFormat(trackIndex)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: return@withContext null
            val sourceSampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val sourceChannelCount = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(inputFormat, null, null, 0)
            codec.start()

            val bufferInfo = MediaCodec.BufferInfo()
            val rawPcmOutput = ByteArrayOutputStream()

            var isEos = false
            while (!isEos) {
                val inputBufferIndex = codec.dequeueInputBuffer(10000L)
                if (inputBufferIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inputBufferIndex)!!
                    val sampleSize = extractor.readSampleData(inputBuffer, 0)
                    if (sampleSize < 0) {
                        codec.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        isEos = true
                    } else {
                        codec.queueInputBuffer(inputBufferIndex, 0, sampleSize, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }

                var outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 10000L)
                while (outputBufferIndex >= 0) {
                    val outputBuffer = codec.getOutputBuffer(outputBufferIndex)!!

                    val chunk = ByteArray(bufferInfo.size)
                    outputBuffer.get(chunk, 0, bufferInfo.size)

                    rawPcmOutput.write(chunk)

                    codec.releaseOutputBuffer(outputBufferIndex, false)
                    outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
                }
            }

            var pcmData = rawPcmOutput.toByteArray()
            rawPcmOutput.close()

            if (sourceChannelCount != TARGET_CHANNEL_CONFIG) {
                pcmData = convertToMono(pcmData, sourceChannelCount)
            }

            if (sourceSampleRate != TARGET_SAMPLE_RATE) {
                pcmData = resamplePcm(pcmData, sourceSampleRate, TARGET_SAMPLE_RATE)
            }

            writeWavFile(tempWavFile, pcmData)
            tempWavFile

        } catch (e: Exception) {
            e.printStackTrace()
            tempWavFile.delete()
            null
        } finally {
            extractor?.release()
            codec?.stop()
            codec?.release()
        }
    }

    private fun findAudioTrack(extractor: MediaExtractor): Int {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME)
            if (mime?.startsWith("audio/") == true) {
                return i
            }
        }
        return -1
    }

    private fun convertToMono(stereoPcm: ByteArray, sourceChannelCount: Int): ByteArray {
        if (sourceChannelCount == 1) return stereoPcm
        val monoPcm = ByteArray(stereoPcm.size / sourceChannelCount)
        for (i in monoPcm.indices) {
            var mixedSample = 0
            for (c in 0 until sourceChannelCount) {
                val sampleIndex = (i * sourceChannelCount + c) * BYTES_PER_SAMPLE
                val sample = (stereoPcm[sampleIndex].toInt() and 0xff) or (stereoPcm[sampleIndex + 1].toInt() shl 8)
                mixedSample += sample
            }
            mixedSample /= sourceChannelCount
            monoPcm[i * 2] = (mixedSample and 0xff).toByte()
            monoPcm[i * 2 + 1] = (mixedSample shr 8 and 0xff).toByte()
        }
        return monoPcm
    }


    private fun resamplePcm(pcmData: ByteArray, fromRate: Int, toRate: Int): ByteArray {
        val numSamples = pcmData.size / BYTES_PER_SAMPLE
        val resampledNumSamples = (numSamples.toLong() * toRate / fromRate).toInt()
        val resampledPcm = ByteArray(resampledNumSamples * BYTES_PER_SAMPLE)

        val shortBuffer = ByteBuffer.wrap(pcmData).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val resampledShortBuffer = ByteBuffer.wrap(resampledPcm).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()

        for (i in 0 until resampledNumSamples) {
            val position = i.toFloat() * fromRate / toRate
            val index = position.toInt()
            val fraction = position - index

            if (index + 1 < numSamples) {
                val sample1 = shortBuffer.get(index)
                val sample2 = shortBuffer.get(index + 1)
                val interpolatedSample = (sample1 + fraction * (sample2 - sample1)).toInt().toShort()
                resampledShortBuffer.put(i, interpolatedSample)
            } else {
                resampledShortBuffer.put(i, shortBuffer.get(index))
            }
        }
        return resampledPcm
    }


    private fun writeWavFile(file: File, pcmData: ByteArray) {
        val audioDataLength = pcmData.size
        val riffDataLength = audioDataLength + 36

        FileOutputStream(file).use { out ->
            val header = createWavHeader(riffDataLength, audioDataLength)
            out.write(header)
            out.write(pcmData)
        }
    }

    private fun createWavHeader(totalDataLen: Int, audioDataLen: Int): ByteArray {
        val header = ByteArray(44)
        val sampleRate = TARGET_SAMPLE_RATE
        val channels = TARGET_CHANNEL_CONFIG
        val bitsPerSample = BITS_PER_SAMPLE
        val byteRate = sampleRate * channels * bitsPerSample / 8

        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = (totalDataLen shr 8 and 0xff).toByte()
        header[6] = (totalDataLen shr 16 and 0xff).toByte()
        header[7] = (totalDataLen shr 24 and 0xff).toByte()
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = (sampleRate shr 8 and 0xff).toByte()
        header[26] = (sampleRate shr 16 and 0xff).toByte()
        header[27] = (sampleRate shr 24 and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = (byteRate shr 8 and 0xff).toByte()
        header[30] = (byteRate shr 16 and 0xff).toByte()
        header[31] = (byteRate shr 24 and 0xff).toByte()
        header[32] = (channels * bitsPerSample / 8).toByte()
        header[33] = 0
        header[34] = bitsPerSample.toByte()
        header[35] = 0
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (audioDataLen and 0xff).toByte()
        header[41] = (audioDataLen shr 8 and 0xff).toByte()
        header[42] = (audioDataLen shr 16 and 0xff).toByte()
        header[43] = (audioDataLen shr 24 and 0xff).toByte()

        return header
    }
}
