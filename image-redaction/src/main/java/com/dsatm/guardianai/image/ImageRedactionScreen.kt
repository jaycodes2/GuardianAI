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
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import kotlin.math.abs

// This Composable function has been updated to use a CoroutineScope
@Composable
fun ImageRedactionScreen() {
    val context = LocalContext.current
    var pickedFile by remember { mutableStateOf<File?>(null) }
    var redactedFile by remember { mutableStateOf<File?>(null) }
    val coroutineScope = rememberCoroutineScope()

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            pickedFile = copyUriToFile(context, it)
            redactedFile = null // Reset redacted image when a new image is picked
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Image Redaction")

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            Button(onClick = { imagePickerLauncher.launch("image/*") }) {
                Text("Pick Image")
            }

            // The new Reset Button
            Button(
                onClick = {
                    pickedFile = null
                    redactedFile = null
                }
            ) {
                Text("Reset")
            }
        }

        pickedFile?.let { file ->
            AsyncImage(
                model = Uri.fromFile(file),
                contentDescription = "Original Image",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            )
        } ?: Text("No image selected")

        Button(
            onClick = {
                pickedFile?.let { file ->
                    coroutineScope.launch {
                        redactedFile = redactImage(context, file)
                    }
                }
            },
            enabled = pickedFile != null
        ) {
            Text("Redact Image")
        }

        redactedFile?.let { file ->
            Text("Redacted Result:")
            AsyncImage(
                model = Uri.fromFile(file),
                contentDescription = "Redacted Image",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            )
        }
    }
}

// Copy content URI to a file
fun copyUriToFile(context: Context, uri: Uri): File {
    val inputStream: InputStream = context.contentResolver.openInputStream(uri)!!
    val outputFile = File(context.getExternalFilesDir("images"), "input_image.jpg")
    val outputStream: OutputStream = outputFile.outputStream()
    inputStream.copyTo(outputStream)
    inputStream.close()
    outputStream.close()
    return outputFile
}

// Main redaction function updated to be a suspend function and use the new blur logic
suspend fun redactImage(context: Context, imageFile: File): File {
    val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
    val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)

    val inputImage = InputImage.fromFilePath(context, Uri.fromFile(imageFile))
    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    val visionText = try {
        recognizer.process(inputImage).await()
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }

    visionText?.let {
        val canvas = Canvas(mutableBitmap)
        for (block in it.textBlocks) {
            for (line in block.lines) {
                val rect = line.boundingBox ?: continue
                val paint = Paint().apply {
                    setARGB(255, 0, 0, 0) // Black color
                }
                canvas.drawRect(rect, paint)
            }
        }
    }

    val outputFile = File(context.getExternalFilesDir("images"), "input_image_redacted.jpg")
    FileOutputStream(outputFile).use { fos ->
        mutableBitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos)
    }

    return outputFile
}

// Alternative for blurring a bitmap region.
// This is a simple, pure Kotlin/Java Box Blur algorithm.
fun blurBitmapRegion(bitmap: Bitmap, rect: Rect): Bitmap {
    val width = rect.width()
    val height = rect.height()
    val radius = 25
    val newBitmap = Bitmap.createBitmap(bitmap, rect.left, rect.top, width, height)

    val w = newBitmap.width
    val h = newBitmap.height
    val pix = IntArray(w * h)
    newBitmap.getPixels(pix, 0, w, 0, 0, w, h)

    val wm = w - 1
    val hm = h - 1
    val div = radius + radius + 1

    val r = IntArray(w * h)
    val g = IntArray(w * h)
    val b = IntArray(w * h)

    var rsum: Int
    var gsum: Int
    var bsum: Int

    var x: Int
    var y: Int

    var i: Int
    var p: Int
    var yp: Int
    var yi: Int

    val vmin = IntArray(maxOf(w, h))
    val vmax = IntArray(maxOf(w, h))

    var yw: Int = 0
    val dv = IntArray(256 * div)

    for (j in 0 until 256 * div) {
        dv[j] = j / div
    }

    for (y in 0 until h) {
        rsum = 0
        gsum = 0
        bsum = 0

        for (i in -radius..radius) {
            p = pix[yw + abs(i)]
            rsum += (p and 0xff0000) shr 16
            gsum += (p and 0x00ff00) shr 8
            bsum += p and 0x0000ff
        }

        for (x in 0 until w) {
            r[y * w + x] = dv[rsum]
            g[y * w + x] = dv[gsum]
            b[y * w + x] = dv[bsum]

            if (y == 0) {
                vmin[x] = minOf(x + radius + 1, wm)
                vmax[x] = maxOf(x - radius, 0)
            }

            p = pix[yw + vmin[x]]
            rsum += (p and 0xff0000) shr 16
            gsum += (p and 0x00ff00) shr 8
            bsum += p and 0x0000ff

            p = pix[yw + vmax[x]]
            rsum -= (p and 0xff0000) shr 16
            gsum -= (p and 0x00ff00) shr 8
            bsum -= p and 0x0000ff
        }
        yw += w
    }

    for (x in 0 until w) {
        rsum = 0
        gsum = 0
        bsum = 0

        yp = -radius * w
        for (i in -radius..radius) {
            yi = maxOf(0, yp + abs(i) * w)
            rsum += r[yi + x]
            gsum += g[yi + x]
            bsum += b[yi + x]
        }

        yi = x
        for (y in 0 until h) {
            pix[yi] = 0xff000000.toInt() or (dv[rsum] shl 16) or (dv[gsum] shl 8) or dv[bsum]

            if (x == 0) {
                vmin[y] = minOf(y + radius + 1, hm) * w
                vmax[y] = maxOf(y - radius, 0) * w
            }

            p = x + vmin[y]
            rsum += r[p]
            gsum += g[p]
            bsum += b[p]

            p = x + vmax[y]
            rsum -= r[p]
            gsum -= g[p]
            bsum -= b[p]

            yi += w
        }
    }

    newBitmap.setPixels(pix, 0, w, 0, 0, w, h)
    return newBitmap
}