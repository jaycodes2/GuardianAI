package com.dsatm.guardianai.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dsatm.guardianai.audio.AudioRedactionScreen
import com.dsatm.guardianai.image.ImageRedactionScreen
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
        // Top App Bar
        TopAppBarWithLogo(
            onMenuClick = { /* TODO: Open menu */ }
        )

        // Module selector (pills)
        ModuleSelectorBar(
            modules = modules,
            selectedIndex = selectedModule,
            onModuleSelected = { selectedModule = it }
        )

        // Content area
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            when (selectedModule) {
                0 -> ImageRedactionScreen()
                1 -> AudioRedactionScreen()
                2 -> TextRedactionScreen()
            }
        }
    }
}
