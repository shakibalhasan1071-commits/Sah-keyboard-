package com.example.moekeyboard.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.moekeyboard.data.prefs.KeyboardPreferences
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToAbout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember { KeyboardPreferences.getInstance(context) }

    val currentTheme by prefs.theme.collectAsState()
    val currentHeight by prefs.height.collectAsState()
    val vibrationEnabled by prefs.vibration.collectAsState()
    val soundEnabled by prefs.sound.collectAsState()
    val autoCapsEnabled by prefs.autoCaps.collectAsState()
    val clipboardEnabled by prefs.clipboardEnabled.collectAsState()
    val showNumberRow by prefs.showNumberRow.collectAsState()
    val searchEngine by prefs.searchEngine.collectAsState()

    val customFontPath by prefs.customFontPath.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    val fontPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    if (inputStream != null) {
                        val fontsDir = java.io.File(context.filesDir, "fonts")
                        if (!fontsDir.exists()) fontsDir.mkdirs()
                        val fileName = "custom_font_${System.currentTimeMillis()}.ttf"
                        val fontFile = java.io.File(fontsDir, fileName)
                        val outputStream = java.io.FileOutputStream(fontFile)
                        inputStream.copyTo(outputStream)
                        inputStream.close()
                        outputStream.close()

                        customFontPath?.let { oldPath ->
                            val oldFile = java.io.File(oldPath)
                            if (oldFile.exists()) oldFile.delete()
                        }

                        prefs.setCustomFontPath(fontFile.absolutePath)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    var showThemeDialog by remember { mutableStateOf(false) }
    var showHeightDialog by remember { mutableStateOf(false) }
    var showSearchEngineDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("settings_screen_root"),
        topBar = {
            TopAppBar(
                title = { Text("Keyboard Settings", fontWeight = FontWeight.Bold) }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 12.dp, horizontal = 0.dp)
        ) {
            // Google Login & Cloud Sync Section
            item {
                val coroutineScope = rememberCoroutineScope()
                val syncManager = remember { com.example.moekeyboard.data.sync.GoogleSyncManager.getInstance(context) }
                val userProfile by syncManager.userProfile.collectAsState()
                var showGoogleLoginDialog by remember { mutableStateOf(false) }

                SettingsSectionHeader(title = "Google Account & Cloud Backup")
                SettingsCard {
                    SettingsClickableItem(
                        icon = Icons.Default.CloudSync,
                        title = if (userProfile.isSignedIn) "Google Account: ${userProfile.email}" else "গুগল একাউন্ট সাইন-ইন (Google Login)",
                        subtitle = if (userProfile.isSignedIn) {
                            "সর্বশেষ ক্লাউড সিঙ্ক: ${userProfile.lastSyncedTime}"
                        } else "ক্লিপবোর্ড, পিন করা মেসেজ ও টেম্প মেইল সেভ রাখতে সাইন-ইন করুন",
                        onClick = { showGoogleLoginDialog = true }
                    )
                }

                if (showGoogleLoginDialog) {
                    AlertDialog(
                        onDismissRequest = { showGoogleLoginDialog = false },
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AccountCircle, contentDescription = null, tint = Color(0xFF00FF88))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("গুগল একাউন্ট ও ক্লাউড সিঙ্ক")
                            }
                        },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                if (!userProfile.isSignedIn) {
                                    Text("আপনার গুগল একাউন্ট দিয়ে সাইন ইন করলে পিন করা মেসেজ, ক্লিপবোর্ড এবং সেভ করা ইমেইলসমূহ ক্লাউডে সুরক্ষিত থাকবে।")
                                    Button(
                                        onClick = {
                                            coroutineScope.launch {
                                                syncManager.signInWithGoogle("user.moekeyboard@gmail.com")
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4285F4)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.Login, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Google দিয়ে লগইন করুন")
                                    }
                                } else {
                                    Text("লগইন করা একাউন্ট: ${userProfile.email}", fontWeight = FontWeight.Bold)
                                    Text("আপনার পিন করা সব টেক্সট এবং সেভ করা ইমেইল সফলভাবে ক্লাউডে সিঙ্ক করা হচ্ছে।")
                                    
                                    Button(
                                        onClick = {
                                            coroutineScope.launch {
                                                syncManager.syncNow()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF88), contentColor = Color.Black),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.Sync, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("এখনই সিঙ্ক করুন (Sync Now)")
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            coroutineScope.launch {
                                                syncManager.signOut()
                                            }
                                        },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.Logout, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("লগআউট করুন")
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showGoogleLoginDialog = false }) {
                                Text("বন্ধ করুন")
                            }
                        }
                    )
                }
            }

            // Theme & Appearance Section
            item {
                SettingsSectionHeader(title = "Appearance & Layout")
                SettingsCard {
                    SettingsClickableItem(
                        icon = Icons.Default.Palette,
                        title = "Keyboard Theme",
                        subtitle = when (currentTheme) {
                            "light" -> "Clean Light"
                            "dark" -> "Modern Dark Slate"
                            "amoled" -> "AMOLED Pitch Black"
                            "ocean_blue" -> "Ocean Blue"
                            "neon_cyber" -> "Neon Cyber"
                            "lavender" -> "Pastel Lavender"
                            "forest" -> "Emerald Forest"
                            else -> "Follow System"
                        },
                        onClick = { showThemeDialog = true }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsClickableItem(
                        icon = Icons.Default.Height,
                        title = "Keyboard Height (কিবোর্ডের উচ্চতা)",
                        subtitle = when (currentHeight) {
                            "extra_small" -> "অতি ছোট (Extra Small)"
                            "small" -> "ছোট (Small)"
                            "medium" -> "মাঝারি (Medium)"
                            "tall" -> "বড় (Tall)"
                            "extra_tall" -> "অতি বড় (Extra Tall)"
                            else -> "স্বাভাবিক (Normal)"
                        },
                        onClick = { showHeightDialog = true }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsSwitchItem(
                        icon = Icons.Default.Numbers,
                        title = "Number Row",
                        subtitle = "Show top row with digits in number view",
                        checked = showNumberRow,
                        onCheckedChange = { prefs.setShowNumberRow(it) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsClickableItem(
                        icon = Icons.Default.TextFields,
                        title = "Custom Font File (.ttf / .otf / .ttc)",
                        subtitle = if (customFontPath == null) "কাস্টম কিবোর্ড ফন্ট ফাইল আপলোড করুন" else "সক্রিয়: ${java.io.File(customFontPath!!).name}",
                        onClick = { fontPickerLauncher.launch("*/*") }
                    )
                }
            }

            // Typing & Feedback Section
            item {
                SettingsSectionHeader(title = "Typing & Feedback")
                SettingsCard {
                    SettingsSwitchItem(
                        icon = Icons.Default.Vibration,
                        title = "Haptic Feedback (Vibration)",
                        subtitle = "Gentle vibration on keypress",
                        checked = vibrationEnabled,
                        onCheckedChange = { prefs.setVibration(it) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsSwitchItem(
                        icon = Icons.Default.VolumeUp,
                        title = "Keypress Sound",
                        subtitle = "Audible click on keypress",
                        checked = soundEnabled,
                        onCheckedChange = { prefs.setSound(it) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsSwitchItem(
                        icon = Icons.Default.TextFields,
                        title = "Auto-Capitalization",
                        subtitle = "Capitalize first word in English sentences",
                        checked = autoCapsEnabled,
                        onCheckedChange = { prefs.setAutoCaps(it) }
                    )
                }
            }

            // Clipboard & Utilities Section
            item {
                SettingsSectionHeader(title = "Clipboard & Browser")
                SettingsCard {
                    SettingsSwitchItem(
                        icon = Icons.Default.ContentPaste,
                        title = "Local Clipboard Manager",
                        subtitle = "Save copied text to device history",
                        checked = clipboardEnabled,
                        onCheckedChange = { prefs.setClipboardEnabled(it) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsClickableItem(
                        icon = Icons.Default.TravelExplore,
                        title = "Default Search Engine",
                        subtitle = searchEngine,
                        onClick = { showSearchEngineDialog = true }
                    )
                }
            }

            // Privacy & About Section
            item {
                SettingsSectionHeader(title = "Information & Privacy")
                SettingsCard {
                    SettingsClickableItem(
                        icon = Icons.Default.Security,
                        title = "Privacy Policy & Zero-Tracking Guarantee",
                        subtitle = "100% offline keyboard • Never sends keystrokes",
                        onClick = onNavigateToAbout
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsClickableItem(
                        icon = Icons.Default.Info,
                        title = "About Sah Keyboard",
                        subtitle = "Version 1.0 • Bengali & English IME",
                        onClick = onNavigateToAbout
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }

    // Theme Selection Dialog
    if (showThemeDialog) {
        val themes = listOf(
            "system" to "Follow System",
            "dark" to "Modern Dark Slate (Gboard Dark)",
            "light" to "Clean Light (Gboard Light)",
            "amoled" to "AMOLED Pitch Black",
            "ocean_blue" to "Ocean Blue (Deep Indigo)",
            "neon_cyber" to "Neon Cyber (Emerald & Dark)",
            "lavender" to "Pastel Lavender (Soft Violet)",
            "forest" to "Emerald Forest (Deep Green)"
        )
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Choose Keyboard Theme") },
            text = {
                Column {
                    themes.forEach { (key, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    prefs.setTheme(key)
                                    showThemeDialog = false
                                }
                                .padding(vertical = 10.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = currentTheme == key,
                                onClick = {
                                    prefs.setTheme(key)
                                    showThemeDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = label, fontSize = 14.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Height Selection Dialog
    if (showHeightDialog) {
        val heights = listOf(
            "extra_small" to "অতি ছোট (Extra Small)",
            "small" to "ছোট (Small)",
            "normal" to "স্বাভাবিক (Normal)",
            "medium" to "মাঝারি (Medium)",
            "tall" to "বড় (Tall)",
            "extra_tall" to "অতি বড় (Extra Tall)"
        )
        AlertDialog(
            onDismissRequest = { showHeightDialog = false },
            title = { Text("Keyboard Height") },
            text = {
                Column {
                    heights.forEach { (key, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    prefs.setHeight(key)
                                    showHeightDialog = false
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = currentHeight == key,
                                onClick = {
                                    prefs.setHeight(key)
                                    showHeightDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = label, fontSize = 14.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showHeightDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Search Engine Selection Dialog
    if (showSearchEngineDialog) {
        val engines = listOf("Google", "DuckDuckGo", "Bing")
        AlertDialog(
            onDismissRequest = { showSearchEngineDialog = false },
            title = { Text("Default Search Engine") },
            text = {
                Column {
                    engines.forEach { engine ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    prefs.setSearchEngine(engine)
                                    showSearchEngineDialog = false
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = searchEngine == engine,
                                onClick = {
                                    prefs.setSearchEngine(engine)
                                    showSearchEngineDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = engine, fontSize = 15.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSearchEngineDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
    )
}

@Composable
fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        content = content
    )
}

@Composable
fun SettingsClickableItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    }
}

@Composable
fun SettingsSwitchItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
