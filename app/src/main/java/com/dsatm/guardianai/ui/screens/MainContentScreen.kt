// File: MainContentScreen.kt
package com.dsatm.guardianai.ui.screens

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dsatm.guardianai.audio.AudioRedactionScreen
import com.dsatm.guardianai.image.FolderRedactionScreen
import com.dsatm.guardianai.text.TextRedactionScreen
import com.dsatm.guardianai.ui.components.ModuleSelectorBar
import com.dsatm.guardianai.ui.components.TopAppBarWithLogo

@Composable
fun MainContentScreen() {
    var selectedModule by remember { mutableStateOf(0) }
    val modules = listOf("Image", "Audio", "Text")

    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        TopAppBarWithLogo(onMenuClick = { /* TODO */ })

        ModuleSelectorBar(
            modules = modules,
            selectedIndex = selectedModule,
            onModuleSelected = { selectedModule = it }
        )

        // The content area now correctly fills the remaining space
        Column(
            modifier = Modifier
                // Use weight to make this column take up all the remaining vertical space
                .weight(1f)
                .padding(horizontal = 16.dp, vertical = 20.dp)
        ) {
            when (selectedModule) {
                0 -> FolderRedactionScreen()
                1 -> AudioRedactionScreen()
                2 -> TextRedactionScreen()
            }
        }
    }
}