package com.dsatm.audio_redaction.audio

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Environment
import android.util.Log
import androidx.annotation.RequiresPermission
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

class Recorder {

    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

    private var audioRecord: AudioRecord? = null
    private var isRecording = false

    var filePath: String = ""

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start() {
        val outputDir = Environment.getExternalStorageDirectory()
        val outputFile = File(outputDir, "recorded_audio.wav")
        filePath = outputFile.absolutePath

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSize
        )

        audioRecord?.startRecording()
        isRecording = true

        Thread {
            writeAudioDataToFile(outputFile)
        }.start()
    }

    private fun writeAudioDataToFile(file: File) {
        val data = ByteArray(bufferSize)
        FileOutputStream(file).use { os ->
            // Write WAV header placeholder, will overwrite later
            writeWavHeader(os, sampleRate, 1, 16, 0)

            var totalAudioLen: Long = 0
            while (isRecording) {
                val read = audioRecord?.read(data, 0, data.size) ?: 0
                if (read > 0) {
                    os.write(data, 0, read)
                    totalAudioLen += read
                }
            }

            // Now overwrite wav header with correct sizes
            rewriteWavHeader(file, totalAudioLen)
        }
    }

    private fun writeWavHeader(out: FileOutputStream, sampleRate: Int, channels: Int, bitsPerSample: Int, dataLength: Int) {
        val header = ByteArray(44)

        // RIFF chunk descriptor
        header[0] = 'R'.toByte()
        header[1] = 'I'.toByte()
        header[2] = 'F'.toByte()
        header[3] = 'F'.toByte()

        // Chunk size placeholder
        header[4] = 0; header[5] = 0; header[6] = 0; header[7] = 0

        // Format
        header[8] = 'W'.toByte()
        header[9] = 'A'.toByte()
        header[10] = 'V'.toByte()
        header[11] = 'E'.toByte()

        // Subchunk1ID "fmt "
        header[12] = 'f'.toByte()
        header[13] = 'm'.toByte()
        header[14] = 't'.toByte()
        header[15] = ' '.toByte()

        // Subchunk1Size 16 for PCM
        header[16] = 16
        header[17] = 0
        header[18] = 0
        header[19] = 0

        // Audio format 1 PCM
        header[20] = 1
        header[21] = 0

        // Number of channels
        header[22] = channels.toByte()
        header[23] = 0

        // Sample rate
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = ((sampleRate shr 8) and 0xff).toByte()
        header[26] = ((sampleRate shr 16) and 0xff).toByte()
        header[27] = ((sampleRate shr 24) and 0xff).toByte()

        // Byte rate = SampleRate * NumChannels * BitsPerSample/8
        val byteRate = sampleRate * channels * bitsPerSample / 8
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()

        // Block align = NumChannels * BitsPerSample/8
        val blockAlign = (channels * bitsPerSample / 8)
        header[32] = blockAlign.toByte()
        header[33] = 0

        // Bits per sample
        header[34] = bitsPerSample.toByte()
        header[35] = 0

        // Subchunk2ID "data"
        header[36] = 'd'.toByte()
        header[37] = 'a'.toByte()
        header[38] = 't'.toByte()
        header[39] = 'a'.toByte()

        // Subchunk2Size placeholder
        header[40] = 0
        header[41] = 0
        header[42] = 0
        header[43] = 0

        out.write(header, 0, 44)
    }

    private fun rewriteWavHeader(file: File, totalAudioLen: Long) {
        val totalDataLen = totalAudioLen + 36
        val byteRate = sampleRate * 1 * 16 / 8

        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(4)
            raf.write(intToByteArrayLE(totalDataLen.toInt()), 0, 4)
            raf.seek(40)
            raf.write(intToByteArrayLE(totalAudioLen.toInt()), 0, 4)
        }
    }

    private fun intToByteArrayLE(value: Int): ByteArray {
        return byteArrayOf(
            (value and 0xff).toByte(),
            ((value shr 8) and 0xff).toByte(),
            ((value shr 16) and 0xff).toByte(),
            ((value shr 24) and 0xff).toByte()
        )
    }

    fun stop() {
        isRecording = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }
}
