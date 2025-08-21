package com.dsatm.guardianai.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import coil.compose.AsyncImage
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.regex.Pattern
import kotlin.math.abs

@Composable
fun FolderRedactionScreen() {
    val context = LocalContext.current
    var folderUri by remember { mutableStateOf<Uri?>(null) }
    var processing by remember { mutableStateOf(false) }
    var progressText by remember { mutableStateOf("") }
    var processedFiles by remember { mutableStateOf(listOf<File>()) }
    val coroutineScope = rememberCoroutineScope()

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        folderUri = uri
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "GuardianAi: Batch Sensitive Data Redaction",
            style = MaterialTheme.typography.titleLarge
        )

        Button(
            onClick = { folderPickerLauncher.launch(null) },
            enabled = !processing
        ) { Text(if (folderUri == null) "Select Folder" else "Change Folder") }

        folderUri?.let { uri ->
            Button(
                onClick = {
                    coroutineScope.launch(Dispatchers.IO) {
                        processing = true
                        progressText = "Scanning images..."
                        val imgDocs = getAllImagesInFolder(context, uri)
                        val outputDir = File(context.getExternalFilesDir("images"), "redacted")
                        outputDir.mkdirs()
                        val results = mutableListOf<File>()
                        for ((i, inputDoc) in imgDocs.withIndex()) {
                            progressText = "Processing image ${i+1} of ${imgDocs.size}"
                            val redacted = redactSensitiveInImage(context, inputDoc, outputDir)
                            if (redacted != null) results.add(redacted)
                        }
                        processedFiles = results
                        progressText = "Completed: ${results.size} redacted images saved to ${outputDir.absolutePath}"
                        processing = false
                    }
                },
                enabled = folderUri != null && !processing
            ) {
                Text("Scan & Redact All Images")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(progressText, style = MaterialTheme.typography.bodyMedium)

        // **UPDATED UI FOR SCROLLABLE IMAGES**
        if (processedFiles.isNotEmpty()) {
            Text(
                text = "Redacted Images:",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp)
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(processedFiles) { file ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "File: ${file.name}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            AsyncImage(
                                model = Uri.fromFile(file),
                                contentDescription = "Redacted Image",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 300.dp), // Height constraint
                                contentScale = ContentScale.Fit // Ensures the image fits without cropping
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun getAllImagesInFolder(context: Context, treeUri: Uri): List<DocumentFile> {
    val documents = mutableListOf<DocumentFile>()
    val rootDocument = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()

    fun findImages(document: DocumentFile) {
        if (document.isDirectory) {
            document.listFiles().forEach { child ->
                findImages(child)
            }
        } else if (document.isFile && document.type?.startsWith("image/") == true) {
            documents.add(document)
        }
    }
    findImages(rootDocument)
    return documents
}

private suspend fun redactSensitiveInImage(context: Context, inputDoc: DocumentFile, outputDir: File): File? {
    val inputStream = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(inputDoc.uri)
    } ?: return null

    val bitmap = BitmapFactory.decodeStream(inputStream)
    val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)

    val inputImage = InputImage.fromFilePath(context, inputDoc.uri)
    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    val visionText = try { recognizer.process(inputImage).await() } catch (e: Exception) { e.printStackTrace(); null }

    visionText?.let { doc ->
        val canvas = Canvas(mutableBitmap)
        val paint = Paint().apply { isAntiAlias = true }

        for (block in doc.textBlocks) {
            for (line in block.lines) {
                if (line.confidence > 0.7 && line.boundingBox != null) {
                    val region = line.boundingBox!!
                    val blurred = blurBitmapRegion(mutableBitmap, region)
                    val sourceRect = Rect(0, 0, blurred.width, blurred.height)
                    canvas.drawBitmap(blurred, sourceRect, region, paint)
                }
            }
        }
    }
    val outFile = File(outputDir, inputDoc.name + "_redacted.jpg")
    FileOutputStream(outFile).use { fos ->
        mutableBitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos)
    }
    return outFile
}

private fun blurBitmapRegion(src: Bitmap, rect: Rect, radius: Int = 20): Bitmap {
    val width = rect.width().coerceAtLeast(1)
    val height = rect.height().coerceAtLeast(1)
    val cropped = Bitmap.createBitmap(src, rect.left, rect.top, width, height)

    val blurred = cropped.copy(cropped.config ?: Bitmap.Config.ARGB_8888, true)
    val w = blurred.width
    val h = blurred.height
    val pixels = IntArray(w * h)
    blurred.getPixels(pixels, 0, w, 0, 0, w, h)

    val newPixels = pixels.copyOf()

    val div = radius * 2 + 1
    val dv = IntArray(256 * div)
    for (i in 0 until 256 * div) {
        dv[i] = i / div
    }

    // Horizontal Pass
    for (y in 0 until h) {
        var rSum = 0
        var gSum = 0
        var bSum = 0

        for (i in -radius..radius) {
            val xClamp = (0 + i).coerceIn(0, w - 1)
            val p = pixels[y * w + xClamp]
            rSum += (p shr 16) and 0xFF
            gSum += (p shr 8) and 0xFF
            bSum += p and 0xFF
        }

        for (x in 0 until w) {
            newPixels[y * w + x] = (0xFF shl 24) or (dv[rSum] shl 16) or (dv[gSum] shl 8) or dv[bSum]

            val pIn = pixels[y * w + (x + radius + 1).coerceIn(0, w - 1)]
            val pOut = pixels[y * w + (x - radius).coerceIn(0, w - 1)]

            rSum += (pIn shr 16) and 0xFF
            gSum += (pIn shr 8) and 0xFF
            bSum += pIn and 0xFF

            rSum -= (pOut shr 16) and 0xFF
            gSum -= (pOut shr 8) and 0xFF
            bSum -= pOut and 0xFF
        }
    }

    // Vertical Pass
    for (x in 0 until w) {
        var rSum = 0
        var gSum = 0
        var bSum = 0

        for (i in -radius..radius) {
            val yClamp = (0 + i).coerceIn(0, h - 1)
            val p = newPixels[yClamp * w + x]
            rSum += (p shr 16) and 0xFF
            gSum += (p shr 8) and 0xFF
            bSum += p and 0xFF
        }

        for (y in 0 until h) {
            val pIn = newPixels[(y + radius + 1).coerceIn(0, h - 1) * w + x]
            val pOut = newPixels[(y - radius).coerceIn(0, h - 1) * w + x]

            rSum += (pIn shr 16) and 0xFF
            gSum += (pIn shr 8) and 0xFF
            bSum += pIn and 0xFF

            rSum -= (pOut shr 16) and 0xFF
            gSum -= (pOut shr 8) and 0xFF
            bSum -= pOut and 0xFF

            pixels[y * w + x] = (0xFF shl 24) or (dv[rSum] shl 16) or (dv[gSum] shl 8) or dv[bSum]
        }
    }

    blurred.setPixels(pixels, 0, w, 0, 0, w, h)
    return blurred
}