package com.dsatm.audio_redaction.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile

class AudioRecorder(private val context: Context) {
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private lateinit var audioFile: File
    private var recordingThread: Thread? = null

    companion object {
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private fun getBufferSize() = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
    }

    @SuppressLint("MissingPermission")
    fun startRecording() {
        if (isRecording) return

        audioFile = File(context.externalCacheDir, "temp_recording.pcm")
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            getBufferSize()
        )

        audioRecord?.let {
            it.startRecording()
            isRecording = true
            recordingThread = Thread {
                writeAudioDataToFile()
            }
            recordingThread?.start()
        }
    }

    fun stopRecording(): Uri? {
        if (!isRecording) return null

        isRecording = false
        audioRecord?.apply {
            stop()
            release()
        }
        audioRecord = null
        recordingThread?.join() // Wait for recording thread to finish

        try {
            val wavFile = File(context.externalCacheDir, "final_recording.wav")
            addWavHeader(audioFile, wavFile)
            audioFile.delete() // Clean up the raw PCM file
            return Uri.fromFile(wavFile)
        } catch (e: IOException) {
            e.printStackTrace()
            // In case of error, try to return the raw file for debugging
            if(audioFile.exists()) return Uri.fromFile(audioFile)
        }
        return null
    }

    private fun writeAudioDataToFile() {
        val data = ByteArray(getBufferSize())
        val fileOutputStream = FileOutputStream(audioFile)
        while (isRecording) {
            val read = audioRecord?.read(data, 0, data.size) ?: 0
            if (read != AudioRecord.ERROR_INVALID_OPERATION) {
                try {
                    fileOutputStream.write(data, 0, read)
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
        fileOutputStream.close()
    }

    @Throws(IOException::class)
    private fun addWavHeader(pcmFile: File, wavFile: File) {
        val pcmData = pcmFile.readBytes()
        val wavOutputStream = FileOutputStream(wavFile)
        val totalDataLen = pcmData.size + 36
        val sampleRate = SAMPLE_RATE.toLong()
        val channels = 1
        val byteRate = (SAMPLE_RATE * 16 * channels / 8).toLong()

        val header = ByteArray(44)
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
        header[32] = (2 * 16 / 8).toByte()
        header[33] = 0
        header[34] = 16
        header[35] = 0
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (pcmData.size and 0xff).toByte()
        header[41] = (pcmData.size shr 8 and 0xff).toByte()
        header[42] = (pcmData.size shr 16 and 0xff).toByte()
        header[43] = (pcmData.size shr 24 and 0xff).toByte()

        wavOutputStream.write(header, 0, 44)
        wavOutputStream.write(pcmData)
        wavOutputStream.close()
    }
}
