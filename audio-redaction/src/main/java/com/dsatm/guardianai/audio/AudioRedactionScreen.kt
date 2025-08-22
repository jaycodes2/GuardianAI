package com.dsatm.guardianai.audio

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import android.util.Log
import java.io.InputStream
import java.io.FileOutputStream
import java.util.UUID

@Composable
fun AudioRedactionScreen() {
    val context = LocalContext.current
    var originalAudioUri by remember { mutableStateOf<Uri?>(null) }
    var originalAudioText by remember { mutableStateOf("") }
    var redactedAudioFile by remember { mutableStateOf<File?>(null) }
    var loading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("Select a file to begin redaction.") }
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val apiKey = "af895efd77eb41a5967954d631373396"
    val assemblyRedactor = remember { AssemblyRedactor(apiKey) }

    val audioFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        result.data?.data?.let { uri ->
            originalAudioUri = uri
            redactedAudioFile = null
            statusMessage = "File selected. Processing..."
            coroutineScope.launch {
                loading = true
                try {
                    val originalFile = getFileFromUri(context, uri)
                    if (originalFile != null) {
                        withContext(Dispatchers.Main) { statusMessage = "Uploading file to AssemblyAI..." }

                        // We now pass the correct, persistent output directory
                        val outputDirectory = getRedactionOutputDirectory(context)

                        val redactedResult = withContext(Dispatchers.IO) {
                            assemblyRedactor.redactAndReplace(originalFile, outputDirectory)
                        }

                        originalAudioText = redactedResult.first
                        redactedAudioFile = redactedResult.second
                        statusMessage = "Audio successfully redacted and saved."
                    } else {
                        statusMessage = "Error: Could not read audio file."
                    }
                } catch (e: Exception) {
                    Log.e("GuardianAi", "Error during audio redaction", e)
                    statusMessage = "Error: ${e.message ?: "An unknown error occurred."}"
                } finally {
                    loading = false
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "GuardianAi: Audio Redaction",
            style = MaterialTheme.typography.titleLarge
        )

        Button(
            onClick = {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "audio/*"
                }
                audioFileLauncher.launch(intent)
            },
            enabled = !loading
        ) {
            Text("Select Audio File")
        }

        if (loading) {
            CircularProgressIndicator()
        } else {
            Text(statusMessage, style = MaterialTheme.typography.bodyMedium)
        }

        if (originalAudioUri != null) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Selected File:", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(originalAudioUri?.path ?: "Path not found")
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp)) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Transcription:", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))
                Text(originalAudioText)
            }
        }

        redactedAudioFile?.let { file ->
            Button(
                onClick = {
                    try {
                        val mediaPlayer = MediaPlayer.create(context, Uri.fromFile(file))
                        mediaPlayer?.start()
                    } catch (e: Exception) {
                        Log.e("GuardianAi", "Error playing audio", e)
                        // Handle error, e.g., show a Toast
                    }
                }
            ) {
                Text("Play Redacted Audio")
            }
            Text("Redacted audio saved to: ${file.absolutePath}")
        }
    }
}

/**
 * Creates a temporary file in the app's cache directory from a given URI's input stream.
 * The filename is sanitized to avoid issues with content provider URIs.
 */
suspend fun getFileFromUri(context: Context, uri: Uri): File? {
    return withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val sanitizedFileName = uri.lastPathSegment?.let {
                it.replace(Regex("[^a-zA-Z0-9.\\-]"), "_")
            } ?: "temp_audio_${UUID.randomUUID()}"
            val tempFile = File(context.cacheDir, sanitizedFileName)

            inputStream?.use { input: InputStream ->
                FileOutputStream(tempFile).use { output: FileOutputStream ->
                    input.copyTo(output)
                }
            }
            tempFile
        } catch (e: Exception) {
            Log.e("GuardianAi", "Error getting file from URI", e)
            null
        }
    }
}

/**
 * Gets the correct, persistent directory for saving audio files.
 * This corresponds to /Android/data/com.dsatm.guardianai/files/Music/
 */
fun getRedactionOutputDirectory(context: Context): File {
    // getExternalFilesDir() returns the app-specific directory on external storage.
    // We pass Environment.DIRECTORY_MUSIC to get the music subdirectory.
    val outputDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
    // If the directory doesn't exist, we create it.
    if (outputDir != null && !outputDir.exists()) {
        outputDir.mkdirs()
    }
    return outputDir ?: context.filesDir
}
