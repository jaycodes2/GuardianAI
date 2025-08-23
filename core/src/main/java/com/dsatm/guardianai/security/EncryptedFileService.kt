package com.dsatm.guardianai.security

import android.content.Context
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets

class EncryptedFileService(private val context: Context) {

    // This is the key that EncryptedFile will use.
    // It's the same key managed securely by your SecurityManager.
    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    /**
     * Encrypts and writes data to a secure file.
     * @param fileName The name of the file to create.
     * @param data The text data to encrypt.
     */
    fun encryptTextToFile(fileName: String, data: String) {
        val encryptedFile = EncryptedFile.Builder(
            context,
            File(context.filesDir, fileName),
            masterKey,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
        ).build()

        encryptedFile.openFileOutput().use { outputStream ->
            outputStream.write(data.toByteArray(StandardCharsets.UTF_8))
        }
    }

    /**
     * Reads and decrypts data from a secure file.
     * @param fileName The name of the file to read.
     * @return The decrypted text data.
     */
    fun decryptTextFromFile(fileName: String): String {
        val encryptedFile = EncryptedFile.Builder(
            context,
            File(context.filesDir, fileName),
            masterKey,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
        ).build()

        return encryptedFile.openFileInput().bufferedReader(StandardCharsets.UTF_8).use {
            it.readText()
        }
    }
}