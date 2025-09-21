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

class AudioRecorder(private val context: Context) {
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var audioFile: File? = null
    private var recordingThread: Thread? = null

    companion object {
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

        private fun getBufferSize(): Int {
            val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            if (minBufferSize == AudioRecord.ERROR_BAD_VALUE || minBufferSize == AudioRecord.ERROR) {
                throw IOException("Unable to get minimum buffer size, check audio format support on this device.")
            }
            // Use a buffer size that is a multiple of the minimum size, and not too small.
            return minBufferSize * 2
        }
    }

    /**
     * Starts the audio recording.
     *
     * NOTE: The caller is responsible for ensuring the android.permission.RECORD_AUDIO
     * permission has been granted before calling this method. Failure to do so will result
     * in a SecurityException.
     *
     * @throws IOException if the recorder fails to initialize or start.
     * @throws SecurityException if the RECORD_AUDIO permission is not granted.
     */
    @SuppressLint("MissingPermission")
    fun startRecording() {
        if (isRecording) return

        try {
            val bufferSize = getBufferSize()
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                throw IOException("AudioRecord initialization failed. Check if the microphone is in use by another app.")
            }

            audioFile = File(context.externalCacheDir, "temp_recording.pcm")

            audioRecord?.startRecording()
            isRecording = true

            recordingThread = Thread { writeAudioDataToFile(bufferSize) }
            recordingThread?.start()

        } catch (e: Exception) {
            // Catch any exception during setup (SecurityException, IOException, etc.)
            release() // Release any resources that might have been allocated
            // Re-throw a more informative exception.
            throw IOException("Failed to start recording: ${e.message}", e)
        }
    }

    /**
     * Stops the audio recording and returns a URI to the final WAV file.
     *
     * @return A content URI for the saved .wav file, or null if recording was not active or an error occurred.
     */
    fun stopRecording(): Uri? {
        if (!isRecording) return null

        release()

        val pcmFile = audioFile ?: return null
        audioFile = null // Clear the file reference

        return try {
            val wavFile = File(context.externalCacheDir, "final_recording.wav")
            addWavHeader(pcmFile, wavFile)
            pcmFile.delete() // Delete the raw PCM file
            Uri.fromFile(wavFile)
        } catch (e: IOException) {
            e.printStackTrace()
            // Fallback: return the raw PCM file if WAV conversion fails.
            if (pcmFile.exists()) Uri.fromFile(pcmFile) else null
        }
    }

    /**
     * Releases all recording resources.
     */
    private fun release() {
        isRecording = false
        // The stop() and release() calls can block, so it's better to do the thread join first.
        try {
            recordingThread?.join()
        } catch (e: InterruptedException) {
            e.printStackTrace()
            Thread.currentThread().interrupt() // Preserve interrupted status
        }
        recordingThread = null

        audioRecord?.apply {
            if (state == AudioRecord.STATE_INITIALIZED) {
                stop()
            }
            release()
        }
        audioRecord = null
    }

    private fun writeAudioDataToFile(bufferSize: Int) {
        val data = ByteArray(bufferSize)
        val file = audioFile ?: return // Local reference for safety

        try {
            FileOutputStream(file).use { fileOutputStream ->
                while (isRecording) {
                    val read = audioRecord?.read(data, 0, data.size) ?: 0
                    if (read > 0 && read != AudioRecord.ERROR_INVALID_OPERATION) {
                        try {
                            fileOutputStream.write(data, 0, read)
                        } catch (e: IOException) {
                            e.printStackTrace()
                            break // Stop writing on error
                        }
                    }
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    @Throws(IOException::class)
    private fun addWavHeader(pcmFile: File, wavFile: File) {
        val pcmData = pcmFile.readBytes()
        FileOutputStream(wavFile).use { wavOutputStream ->
            val totalDataLen = pcmData.size + 36
            val sampleRate = SAMPLE_RATE.toLong()
            val channels = 1
            val bitsPerSample = 16
            val byteRate = sampleRate * channels * bitsPerSample / 8

            val header = ByteArray(44)
            // RIFF header
            header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte(); header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte()
            header[4] = (totalDataLen and 0xff).toByte(); header[5] = (totalDataLen shr 8 and 0xff).toByte(); header[6] = (totalDataLen shr 16 and 0xff).toByte(); header[7] = (totalDataLen shr 24 and 0xff).toByte()
            header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte(); header[10] = 'V'.code.toByte(); header[11] = 'E'.code.toByte()

            // "fmt " subchunk
            header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte(); header[14] = 't'.code.toByte(); header[15] = ' '.code.toByte()
            header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0 // Subchunk1Size (16 for PCM)
            header[20] = 1; header[21] = 0 // AudioFormat (1 for PCM)
            header[22] = channels.toByte(); header[23] = 0
            header[24] = (sampleRate and 0xff).toByte(); header[25] = (sampleRate shr 8 and 0xff).toByte(); header[26] = (sampleRate shr 16 and 0xff).toByte(); header[27] = (sampleRate shr 24 and 0xff).toByte()
            header[28] = (byteRate and 0xff).toByte(); header[29] = (byteRate shr 8 and 0xff).toByte(); header[30] = (byteRate shr 16 and 0xff).toByte(); header[31] = (byteRate shr 24 and 0xff).toByte()
            header[32] = (channels * bitsPerSample / 8).toByte(); header[33] = 0 // BlockAlign
            header[34] = bitsPerSample.toByte(); header[35] = 0 // BitsPerSample

            // "data" subchunk
            header[36] = 'd'.code.toByte(); header[37] = 'a'.code.toByte(); header[38] = 't'.code.toByte(); header[39] = 'a'.code.toByte()
            header[40] = (pcmData.size and 0xff).toByte(); header[41] = (pcmData.size shr 8 and 0xff).toByte(); header[42] = (pcmData.size shr 16 and 0xff).toByte(); header[43] = (pcmData.size shr 24 and 0xff).toByte()

            wavOutputStream.write(header, 0, 44)
            wavOutputStream.write(pcmData)
        }
    }
}
