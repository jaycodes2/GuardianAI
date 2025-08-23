package com.dsatm.guardianai.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.OpenableColumns
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.dsatm.guardianai.security.EncryptedFileService
import com.dsatm.guardianai.security.SecurityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

// Tag for logging messages in Logcat
private const val TAG = "CryptoDemoScreen"

@Composable
fun CryptoDemoScreen(activity: FragmentActivity) {
    // Instantiate the services. The MasterKey is generated on the first access.
    val context: Context = LocalContext.current
    val securityManager = remember { SecurityManager(context) }
    val encryptedFileService = remember { EncryptedFileService(context, securityManager.masterKey) }

    // State for managing UI text and status updates
    var statusText by remember { mutableStateOf("Ready to encrypt/decrypt files.") }
    var encryptedFile by remember { mutableStateOf<File?>(null) }
    var originalFileName by remember { mutableStateOf<String?>(null) }
    var decryptedDataForSave by remember { mutableStateOf<ByteArray?>(null) }
    var decryptedFileNameForSave by remember { mutableStateOf<String?>(null) }

    val coroutineScope = rememberCoroutineScope()

    // Launcher for requesting storage permissions
    val requestPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.d(TAG, "Storage permission granted. Proceeding with file save.")
            decryptedDataForSave?.let { data ->
                decryptedFileNameForSave?.let { name ->
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            saveByteArrayToExternalStorage(context, data, name)
                            statusText = "File saved to Downloads as '$name'."
                            Log.d(TAG, "Successfully saved decrypted file to external storage.")
                        } catch (e: Exception) {
                            statusText = "Failed to save decrypted file: ${e.message}"
                            Log.e(TAG, "Failed to save file", e)
                        } finally {
                            // Clear state variables after the operation is complete
                            decryptedDataForSave = null
                            decryptedFileNameForSave = null
                        }
                    }
                }
            }
        } else {
            statusText = "Permission denied. Cannot save file to public storage."
            Log.w(TAG, "Storage permission denied by user.")
        }
    }

    // File picker launcher for encryption
    val encryptFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? ->
            Log.d(TAG, "File picker result received for encryption. Uri: $uri")
            if (uri != null) {
                coroutineScope.launch {
                    statusText = "Encrypting file... Please authenticate."
                    withContext(Dispatchers.IO) {
                        try {
                            val name = getFileName(context, uri)
                            val nameForEncryptedFile = "$name.encrypted"
                            Log.d(TAG, "Selected file: $name. Encrypted file name will be: $nameForEncryptedFile")

                            // Use the service to encrypt the file's contents and save it to app's internal storage
                            // The service method will now save to a subfolder
                            val encrypted = encryptedFileService.encryptFile(uri, nameForEncryptedFile)

                            // Store the File object and original name for later decryption
                            encryptedFile = encrypted
                            originalFileName = name

                            statusText = "File '$name' encrypted and saved to app's private storage at: ${encrypted.absolutePath}"
                            Log.d(TAG, "Encryption complete. File object stored for decryption: ${encryptedFile?.absolutePath}")
                        } catch (e: Exception) {
                            statusText = "Encryption failed: ${e.message}"
                            Log.e(TAG, "Encryption failed", e)
                            e.printStackTrace()
                        }
                    }
                }
            } else {
                statusText = "No file selected for encryption."
                Log.d(TAG, "User canceled file selection for encryption.")
            }
        }
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = statusText, modifier = Modifier.padding(bottom = 16.dp))

        Button(
            onClick = { encryptFileLauncher.launch("*/*") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Select File to Encrypt")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                encryptedFile?.let { file ->
                    Log.d(TAG, "Decrypt button clicked. Encrypted file path: ${file.absolutePath}")
                    coroutineScope.launch {
                        statusText = "Decrypting file... Please authenticate."
                        withContext(Dispatchers.IO) {
                            try {
                                val decryptedFileName = originalFileName ?: "decrypted_file"
                                Log.d(TAG, "Starting decryption of file: ${file.name}. Decrypted name will be: $decryptedFileName")

                                // Decrypt the file, which now returns a ByteArray
                                val decryptedData = encryptedFileService.decryptFile(file)

                                // Store the data and file name in state for the permission launcher callback
                                decryptedDataForSave = decryptedData
                                decryptedFileNameForSave = decryptedFileName

                                // Check for permissions before saving
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q || ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                                    Log.d(TAG, "Permissions already granted or not needed. Saving file directly.")
                                    // Save the decrypted data to external storage
                                    saveByteArrayToExternalStorage(context, decryptedData, decryptedFileName)
                                    statusText = "File '${file.name}' decrypted and saved to Downloads as '$decryptedFileName'."
                                    decryptedDataForSave = null
                                    decryptedFileNameForSave = null
                                } else {
                                    Log.d(TAG, "Permissions needed. Requesting now.")
                                    statusText = "Permission needed to save file. Please grant permission."
                                    // Request permissions via the launcher
                                    withContext(Dispatchers.Main) {
                                        requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                                    }
                                }
                            } catch (e: Exception) {
                                statusText = "Decryption failed: ${e.message}"
                                Log.e(TAG, "Decryption failed", e)
                                e.printStackTrace()
                            }
                        }
                    }
                } ?: run {
                    statusText = "No encrypted file to decrypt. Please encrypt one first."
                    Log.d(TAG, "Decryption attempted but no encrypted file was found.")
                }
            },
            enabled = encryptedFile != null,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Decrypt File")
        }
    }
}

/**
 * A utility function to get the file name from a Uri.
 */
private fun getFileName(context: Context, uri: Uri): String {
    Log.d(TAG, "Attempting to get file name from Uri: $uri")
    var result: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    result = it.getString(nameIndex)
                }
            }
        }
    }
    if (result == null) {
        result = uri.path?.split('/')?.last()
    }
    val finalName = result ?: "unnamed_file"
    Log.d(TAG, "Resolved file name: $finalName")
    return finalName
}

/**
 * A utility function to save a byte array to the device's public Downloads directory.
 */
private fun saveByteArrayToExternalStorage(context: Context, data: ByteArray, newFileName: String) {
    Log.d(TAG, "Starting to save decrypted byte array to external storage: $newFileName")
    // Get the directory for the user's public downloads and create a "decrypted" subfolder
    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    val decryptedDir = File(downloadsDir, "decrypted")
    if (!decryptedDir.exists()) {
        decryptedDir.mkdirs()
    }
    val outputFile = File(decryptedDir, newFileName)

    // Write the byte array to the public file
    FileOutputStream(outputFile).use { output ->
        output.write(data)
    }
    Log.d(TAG, "Successfully saved file to external storage at: ${outputFile.absolutePath}")
}
