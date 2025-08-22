// File: TextRedactionViewModel.kt
package com.dsatm.guardianai.text

import android.content.Context
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.regex.Pattern

class TextRedactionViewModel : ViewModel() {

    private val _inputText = MutableLiveData("")
    val inputText: LiveData<String> = _inputText

    private val _outputText = MutableLiveData("")
    val outputText: LiveData<String> = _outputText

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    fun onInputTextChange(newText: String) {
        _inputText.value = newText
    }

    fun redactText() {
        val currentText = _inputText.value ?: return
        viewModelScope.launch {
            _loading.value = true
            _outputText.value = withContext(Dispatchers.IO) {
                maskSensitiveData(currentText)
            }
            _loading.value = false
        }
    }

    fun onFileSelected(context: Context, uri: Uri) {
        viewModelScope.launch {
            _loading.value = true
            val content = withContext(Dispatchers.IO) {
                readTextFromUri(context, uri)
            }
            if (content != null) {
                _inputText.value = content
                _outputText.value = withContext(Dispatchers.IO) {
                    maskSensitiveData(content)
                }
            } else {
                _inputText.value = "Error reading file."
                _outputText.value = ""
            }
            _loading.value = false
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

    // This function is still part of the logic since it's a utility for the ViewModel
    private fun readTextFromUri(context: Context, uri: Uri): String? {
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
}