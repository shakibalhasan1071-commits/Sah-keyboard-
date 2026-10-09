package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import com.example.moekeyboard.data.prefs.KeyboardPreferences
import com.example.moekeyboard.ui.*
import com.example.ui.theme.MyApplicationTheme

enum class MainNavTab(val title: String, val icon: ImageVector) {
    HOME("Home", Icons.Default.Home),
    CUSTOMIZE("Customize", Icons.Default.Palette),
    TEMP_MAIL("Temp Mail", Icons.Default.MarkEmailRead),
    CLIPBOARD("Clipboard", Icons.Default.ContentPaste),
    BROWSER("Browser", Icons.Default.Language),
    SETTINGS("Settings", Icons.Default.Settings)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val initialTabParam = intent.getStringExtra("initial_tab")
        val initialTab = when (initialTabParam) {
            "customize" -> MainNavTab.CUSTOMIZE
            "clipboard" -> MainNavTab.CLIPBOARD
            "browser" -> MainNavTab.BROWSER
            "settings" -> MainNavTab.SETTINGS
            else -> MainNavTab.HOME
        }

        val prefs = KeyboardPreferences.getInstance(this)
        com.example.moekeyboard.ime.SmartSuggestionEngine.init(this)

        setContent {
            val themePref by prefs.theme.collectAsState()
            val darkTheme = when (themePref) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }

            MyApplicationTheme(darkTheme = darkTheme) {
                MainAppScreen(initialTab = initialTab)
            }
        }
    }
}

@Composable
fun MainAppScreen(initialTab: MainNavTab) {
    var currentTab by remember { mutableStateOf(initialTab) }
    var showingAboutScreen by remember { mutableStateOf(false) }

    if (showingAboutScreen) {
        AboutPrivacyScreen(
            onBack = { showingAboutScreen = false }
        )
    } else {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .testTag("main_activity_root"),
            bottomBar = {
                NavigationBar {
                    MainNavTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = currentTab == tab,
                            onClick = { currentTab = tab },
                            icon = { Icon(imageVector = tab.icon, contentDescription = tab.title) },
                            label = { Text(tab.title, maxLines = 1) },
                            modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                        )
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (currentTab) {
                    MainNavTab.HOME -> {
                        HomeScreen(
                            onNavigateToSettings = { currentTab = MainNavTab.SETTINGS },
                            onNavigateToCustomize = { currentTab = MainNavTab.CUSTOMIZE },
                            onNavigateToClipboard = { currentTab = MainNavTab.CLIPBOARD },
                            onNavigateToBrowser = { currentTab = MainNavTab.BROWSER },
                            onNavigateToTempMail = { currentTab = MainNavTab.TEMP_MAIL },
                            onNavigateToAbout = { showingAboutScreen = true }
                        )
                    }
                    MainNavTab.CUSTOMIZE -> {
                        CustomizeScreen()
                    }
                    MainNavTab.TEMP_MAIL -> {
                        TempMailScreen()
                    }
                    MainNavTab.CLIPBOARD -> {
                        ClipboardScreen()
                    }
                    MainNavTab.BROWSER -> {
                        BrowserScreen(onClose = null)
                    }
                    MainNavTab.SETTINGS -> {
                        SettingsScreen(
                            onNavigateToAbout = { showingAboutScreen = true }
                        )
                    }
                }
            }
        }
    }
}
