package com.dsatm.guardianai

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.dsatm.guardianai.ui.screens.SplashScreen
import com.dsatm.guardianai.ui.screens.MainContentScreen
import com.dsatm.guardianai.ui.theme.GuardianAITheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize Chaquopy (only once)
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }

        try {
            val py = Python.getInstance()

            // ---- Test 2: guardian_ai/image/redact.py ----
            val imageModule: PyObject = py.getModule("guardian_ai.image.redact")
            val redactionResult: PyObject = imageModule.callAttr(
                "redact_image_stub",
                "/storage/emulated/0/Download/test.jpg"
            )
            Log.d("GuardianAI", "Python redact.py says: ${redactionResult.toString()}")

        } catch (e: Exception) {
            Log.e("GuardianAI", "Python error", e)
        }

        setContent {
            GuardianAITheme {
                // State to switch between splash and main content
                var showSplash by remember { mutableStateOf(true) }

                if (showSplash) {
                    SplashScreen(
                        onTimeout = {
                            showSplash = false
                        }
                    )
                } else {
                    MainContentScreen()
                }
            }
        }
    }
}
