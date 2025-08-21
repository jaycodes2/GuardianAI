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
            .padding(bottom = 16.dp)
    ) {
        TopAppBarWithLogo(onMenuClick = { /* TODO */ })

        ModuleSelectorBar(
            modules = modules,
            selectedIndex = selectedModule,
            onModuleSelected = { selectedModule = it }
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            when (selectedModule) {
                0 -> FolderRedactionScreen()      // Directly use the Compose screen with ML Kit
                1 -> AudioRedactionScreen()      // Unchanged
                2 -> TextRedactionScreen()       // Unchanged
            }
        }
    }
}
