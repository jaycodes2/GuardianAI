package com.dsatm.guardianai.security

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * A service class for handling file encryption and decryption using AndroidX Security Crypto.
 * It uses a MasterKey to secure the files.
 */
class EncryptedFileService(
    private val context: Context,
    private val masterKey: MasterKey
) {

    // Tag for logging messages
    private val TAG = "EncryptedFileService"

    // Helper function to create an EncryptedFile object with a specific file and master key
    private fun getEncryptedFile(file: File): EncryptedFile {
        return EncryptedFile.Builder(
            context,
            file,
            masterKey,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
        ).build()
    }

    /**
     * Encrypts a file selected via a Uri and saves it to a new, encrypted file in the app's private internal storage.
     * @param sourceUri The Uri of the file to be encrypted.
     * @param encryptedFileName The desired name of the output file.
     * @return The File object of the newly created encrypted file.
     */
    fun encryptFile(sourceUri: Uri, encryptedFileName: String): File {
        Log.d(TAG, "Attempting to encrypt file from Uri: $sourceUri")
        // Read the raw bytes from the source Uri
        val dataToEncrypt = context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
            inputStream.readBytes()
        } ?: throw Exception("Could not read from source Uri.")
        Log.d(TAG, "Read ${dataToEncrypt.size} bytes from source Uri.")

        // Use the EncryptedFile API to encrypt the bytes and write them to a new file in private storage
        val targetFile = File(context.filesDir, encryptedFileName)
        getEncryptedFile(targetFile).openFileOutput().use { outputStream ->
            outputStream.write(dataToEncrypt)
        }
        Log.d(TAG, "Encryption complete. Saved to app private storage: ${targetFile.absolutePath}")
        // Added log to explicitly show the location
        Log.d(TAG, "Encrypted file is located at: ${targetFile.absolutePath}")

        // Return the File object for the newly created encrypted file
        return targetFile
    }

    /**
     * Decrypts a file from the app's private internal storage and returns the decrypted byte array.
     * @param sourceFile The encrypted File object to read from (from internal storage).
     * @return The decrypted byte array.
     */
    fun decryptFile(sourceFile: File): ByteArray {
        Log.d(TAG, "Attempting to decrypt file from internal storage: ${sourceFile.absolutePath}")

        try {
            // Get the EncryptedFile instance for the source file. This is crucial for matching the key.
            val encryptedFile = getEncryptedFile(sourceFile)
            Log.d(TAG, "Created EncryptedFile instance for decryption.")

            // Use the EncryptedFile's input stream to decrypt the data.
            val decryptedData = encryptedFile.openFileInput().use { inputStream ->
                Log.d(TAG, "Opened EncryptedFile input stream. Starting decryption...")
                inputStream.readBytes()
            }
            Log.d(TAG, "Decryption successful. Decrypted ${decryptedData.size} bytes.")

            return decryptedData
        } catch (e: Exception) {
            Log.e(TAG, "Decryption failed. Error: ${e.message}")
            throw e
        }
    }
}
