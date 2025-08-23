package com.dsatm.audio_redaction.ui // Corrected package name

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun AudioRedactionScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(text = "Audio Redaction Module Loaded!")
    }
}

@Preview
@Composable
fun AudioRedactionScreenPreview() {
    AudioRedactionScreen()
}