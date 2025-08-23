// file: src/main/java/com/dsatm/guardianai/MainActivity.kt

package com.dsatm.guardianai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.dsatm.audio_redaction.ui.AudioRedactionScreen
import com.dsatm.guardianai.ui.theme.GuardianAITheme
import com.dsatm.image_redaction.ui.ImageRedactionScreen
import com.dsatm.text_redaction.ui.TextRedactionScreen

// Sealed class to define our navigation destinations in a type-safe way.
sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Image : Screen("image_screen", "Image", Icons.Default.Image)
    object Audio : Screen("audio_screen", "Audio", Icons.Default.Mic)
    object Text : Screen("text_screen", "Text", Icons.Default.TextFields)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GuardianAITheme {
                MainScreen()
            }
        }
    }
}

@Composable
fun MainScreen() {
    // The NavController manages the app's navigation state.
    val navController = rememberNavController()

    // The Scaffold provides the basic visual structure for the app.
    Scaffold(
        bottomBar = {
            BottomNavigationBar(navController)
        }
    ) { innerPadding ->
        // The NavHost is where we define our navigation graph.
        NavHost(
            navController = navController,
            startDestination = Screen.Image.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            // Define the composable for each screen.
            composable(Screen.Image.route) {
                ImageRedactionScreen()
            }
            composable(Screen.Audio.route) {
                AudioRedactionScreen()
            }
            composable(Screen.Text.route) {
                TextRedactionScreen()
            }
        }
    }
}

@Composable
fun BottomNavigationBar(navController: NavHostController) {
    // List of screens to show in the navigation bar.
    val items = listOf(Screen.Image, Screen.Audio, Screen.Text)

    // Get the current back stack entry to highlight the selected item.
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    NavigationBar {
        items.forEach { screen ->
            val isSelected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
            NavigationBarItem(
                icon = {
                    Icon(
                        imageVector = screen.icon,
                        contentDescription = screen.label
                    )
                },
                label = { Text(screen.label) },
                selected = isSelected,
                onClick = {
                    // Navigate to the selected screen.
                    navController.navigate(screen.route) {
                        // Avoid building a large back stack by popping up to the start destination.
                        popUpTo(navController.graph.startDestinationId) {
                            saveState = true
                        }
                        // Avoid creating multiple copies of the same destination.
                        launchSingleTop = true
                        // Restore state when re-selecting a previously selected item.
                        restoreState = true
                    }
                }
            )
        }
    }
}
