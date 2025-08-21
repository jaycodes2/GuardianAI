package com.dsatm.guardianai.text

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.regex.Pattern

@Composable
fun TextRedactionScreen() {
    val context = LocalContext.current
    var inputText by remember { mutableStateOf("") }
    var outputText by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val textFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        result.data?.data?.let { uri ->
            coroutineScope.launch(Dispatchers.IO) {
                loading = true
                val content = readTextFromUri(context, uri)
                if (content != null) {
                    inputText = content
                    outputText = maskSensitiveData(content)
                } else {
                    inputText = "Error reading file."
                    outputText = ""
                }
                loading = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "GuardianAi: Text Redaction",
            style = MaterialTheme.typography.titleLarge
        )

        OutlinedTextField(
            value = inputText,
            onValueChange = { inputText = it },
            label = { Text("Enter text here or select a file") },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 150.dp, max = 300.dp),
            maxLines = Int.MAX_VALUE
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
        ) {
            Button(
                onClick = {
                    coroutineScope.launch {
                        loading = true
                        outputText = maskSensitiveData(inputText)
                        loading = false
                    }
                },
                enabled = !loading
            ) {
                Text("Redact Text")
            }

            Button(
                onClick = {
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "text/plain"
                    }
                    textFileLauncher.launch(intent)
                },
                enabled = !loading
            ) {
                Text("Select .txt File")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (loading) {
            CircularProgressIndicator()
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 150.dp, max = 300.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Redacted Output:",
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                SelectionContainer {
                    Text(
                        text = outputText,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

/**
 * A comprehensive function to mask sensitive data using a variety of robust regex patterns.
 * This is an alternative to using an ML model for on-device redaction.
 */
private fun maskSensitiveData(text: String): String {
    var maskedText = text
    val patterns = mapOf(
        // ====== PRIMARY INDIAN IDENTIFIERS ======
        // Aadhaar Number (12 digits, optional spaces or hyphens after every 4 digits)
        Pattern.compile("\\b[2-9]{1}[0-9]{3}[\\s-]?[0-9]{4}[\\s-]?[0-9]{4}\\b") to "[AADHAAR]",

        // Permanent Account Number (PAN) - 10 char alphanumeric, specific format (5 letters, 4 numbers, 1 letter)
        Pattern.compile("\\b[A-Z]{5}[0-9]{4}[A-Z]{1}\\b") to "[PAN]",

        // Indian Phone Numbers (with country code +91 or 0 prefix)
        // Matches: +91 1234567890, 091 1234567890, 01234567890, 1234567890
        Pattern.compile("(?:(?:\\+91|091|0)[\\s-]?)?[6-9]{1}[0-9]{4}[\\s-]?[0-9]{5}") to "[IN_PHONE]",

        // Indian Vehicle Registration Number
        // Format: AA 11 AA 1111 (state code, district code, letters, numbers)
        Pattern.compile("\\b[A-Z]{2}[\\s-]?[0-9]{1,2}[\\s-]?[A-Z]{1,2}[\\s-]?[0-9]{4}\\b") to "[IN_VEHICLE]",

        // Indian Postal Code (PIN Code - 6 digits)
        Pattern.compile("\\b[1-9]{1}[0-9]{2}[\\s-]?[0-9]{3}\\b") to "[IN_POSTAL_CODE]",

        // Universal Account Number (UAN) - 12 digits
        Pattern.compile("\\b[0-9]{12}\\b") to "[UAN]",

        // ====== GLOBAL FINANCIAL & IDENTIFIERS (Also prevalent in India) ======
        // Credit Card Numbers (13-19 digits, supports major brands)
        Pattern.compile("\\b(?:4[0-9]{12}(?:[0-9]{3})?|5[1-5][0-9]{14}|6(?:011|5[0-9][0-9])[0-9]{12}|3[47][0-9]{13}|3(?:0[0-5]|[68][0-9])[0-9]{11}|(?:2131|1800|35\\d{3})\\d{11})\\b") to "[CREDIT_CARD]",

        // Debit Card Numbers (Indian banks often use 16 digits, but it's not a fixed standard)
        Pattern.compile("\\b[0-9]{16}\\b") to "[POSSIBLE_DEBIT_CARD]",

        // IFSC Code (11 char alphanumeric, format: AAAA0XXXXXX)
        Pattern.compile("\\b[A-Z]{4}0[0-9A-Z]{6}\\b") to "[IFSC]",

        // SWIFT/BIC Code (8 or 11 characters)
        Pattern.compile("\\b[A-Z]{6}[A-Z0-9]{2}([A-Z0-9]{3})?\\b") to "[SWIFT_BIC]",

        // ====== PERSONAL IDENTIFIERS ======
        // Email Addresses (Global)
        Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b") to "[EMAIL]",

        // Passport Number (Generic for many countries, including India: alphanumeric, 8-9 chars)
        // Note: Indian passport numbers start with a letter, followed by 7-8 numbers.
        Pattern.compile("\\b[A-Z]{1}[0-9]{7,8}\\b") to "[PASSPORT]",

        // ====== DIGITAL & NETWORK IDENTIFIERS ======
        // IP Addresses (IPv4)
        Pattern.compile("\\b(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\b") to "[IP_ADDRESS_V4]",

        // IP Addresses (IPv6 - basic pattern)
        Pattern.compile("\\b(?:[0-9A-Fa-f]{1,4}:){7}[0-9A-Fa-f]{1,4}\\b") to "[IP_ADDRESS_V6]",

        // URLs (HTTP, HTTPS, FTP)
        Pattern.compile("(?:https?|ftp)://[^\\s/$.?#].[^\\s]*") to "[URL]",

        // ====== OTHER SENSITIVE PATTERNS ======
        // Dates (DD/MM/YYYY or YYYY-MM-DD - common in India)
        // This is a common format but high false positive rate. Use cautiously.
        Pattern.compile("\\b(0[1-9]|[12][0-9]|3[01])[-/](0[1-9]|1[0-2])[-/](19|20)\\d{2}\\b") to "[DATE]",

        // Generic 10+ digit numbers (could be account numbers, customer IDs, etc.)
        Pattern.compile("\\b\\d{10,}\\b") to "[LONG_NUMBER_ID]"
    )

    for ((pattern, replacement) in patterns) {
        val matcher = pattern.matcher(maskedText)
        val stringBuilder = StringBuilder()
        var lastIndex = 0
        while (matcher.find()) {
            stringBuilder.append(maskedText.substring(lastIndex, matcher.start()))
            stringBuilder.append(replacement)
            lastIndex = matcher.end()
        }
        stringBuilder.append(maskedText.substring(lastIndex))
        maskedText = stringBuilder.toString()
    }

    return maskedText
}

private fun readTextFromUri(context: android.content.Context, uri: Uri): String? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri)
        val reader = BufferedReader(InputStreamReader(inputStream))
        val stringBuilder = StringBuilder()
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            stringBuilder.append(line).append("\n")
        }
        inputStream?.close()
        stringBuilder.toString()
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}