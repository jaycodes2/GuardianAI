package com.dsatm.guardianai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
//import com.chaquo.python.Python
//import com.chaquo.python.android.AndroidPlatform
import com.dsatm.guardianai.ui.screens.SplashScreen
import com.dsatm.guardianai.ui.screens.MainContentScreen
import com.dsatm.guardianai.ui.theme.GuardianAITheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

//        // Initialize Chaquopy once
//        if (!Python.isStarted()) {
//            Python.start(AndroidPlatform(this))
//        }

        setContent {
            GuardianAITheme {
                // State to switch between splash and main content
                var showSplash by remember { mutableStateOf(true) }

                if (showSplash) {
                    SplashScreen(
                        onTimeout = { showSplash = false }
                    )
                } else {
                    MainContentScreen()
                }
            }
        }
    }
}
