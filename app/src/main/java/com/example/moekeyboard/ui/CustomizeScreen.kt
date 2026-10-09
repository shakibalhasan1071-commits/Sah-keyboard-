package com.example.moekeyboard.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.moekeyboard.data.prefs.KeyboardPreferences
import com.example.moekeyboard.ime.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomizeScreen(
    onNavigateBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember { KeyboardPreferences.getInstance(context) }

    val themePref by prefs.theme.collectAsState()
    val heightPref by prefs.height.collectAsState()
    val vibrationEnabled by prefs.vibration.collectAsState()
    val soundEnabled by prefs.sound.collectAsState()
    val numberRowEnabled by prefs.showNumberRow.collectAsState()
    val wordSuggestionsEnabled by prefs.wordSuggestions.collectAsState()
    val autoCorrectEnabled by prefs.autoCorrect.collectAsState()
    val customFontPath by prefs.customFontPath.collectAsState()

    val coroutineScope = rememberCoroutineScope()

    val fontPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    if (inputStream != null) {
                        val fontsDir = File(context.filesDir, "fonts")
                        if (!fontsDir.exists()) fontsDir.mkdirs()
                        val fileName = "custom_font_${System.currentTimeMillis()}.ttf"
                        val fontFile = File(fontsDir, fileName)
                        val outputStream = FileOutputStream(fontFile)
                        inputStream.copyTo(outputStream)
                        inputStream.close()
                        outputStream.close()
                        
                        // Delete old font if exists
                        customFontPath?.let { oldPath ->
                            val oldFile = File(oldPath)
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

    val systemDark = isSystemInDarkTheme()
    val isDark = when (themePref) {
        "light", "lavender" -> false
        "dark", "amoled", "ocean_blue", "neon_cyber", "forest" -> true
        else -> systemDark
    }

    var previewMode by remember { mutableStateOf(KeyboardMode.BENGALI) }
    var previewBengaliPage by remember { mutableStateOf(BengaliPage.PAGE_1) }
    var previewShiftState by remember { mutableStateOf(ShiftState.OFF) }
    var previewText by remember { mutableStateOf("") }

    val themes = listOf(
        ThemeOption("system", "সিস্টেম", Color(0xFF1E1F22), Color(0xFFECEFF1)),
        ThemeOption("dark", "ডার্ক (Slate)", Color(0xFF1E1F22), Color(0xFF8AB4F8)),
        ThemeOption("light", "লাইট (Chalk)", Color(0xFFFFFFFF), Color(0xFF1A73E8)),
        ThemeOption("amoled", "AMOLED Black", Color(0xFF000000), Color(0xFF00E5FF)),
        ThemeOption("ocean_blue", "Ocean Blue", Color(0xFF0F172A), Color(0xFF38BDF8)),
        ThemeOption("neon_cyber", "Neon Cyber", Color(0xFF0D1117), Color(0xFF00E676)),
        ThemeOption("lavender", "Lavender", Color(0xFFF3E8FF), Color(0xFF9333EA)),
        ThemeOption("forest", "Emerald Forest", Color(0xFF06281E), Color(0xFF2DD4BF))
    )

    val heights = listOf(
        Pair("extra_small", "অতি ছোট"),
        Pair("small", "ছোট"),
        Pair("normal", "স্বাভাবিক"),
        Pair("medium", "মাঝারি"),
        Pair("tall", "বড়"),
        Pair("extra_tall", "অতি বড়")
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "কিবোর্ড কাস্টমাইজেশন",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    if (onNavigateBack != null) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    } else {
                        Icon(
                            Icons.Default.Palette,
                            contentDescription = "Customize",
                            modifier = Modifier.padding(start = 16.dp, end = 8.dp)
                        )
                    }
                },
                actions = {
                    TextButton(onClick = {
                        prefs.setTheme("system")
                        prefs.setHeight("normal")
                        prefs.setWordSuggestions(true)
                        prefs.setAutoCorrect(true)
                        prefs.setVibration(true)
                        prefs.setSound(false)
                        prefs.setShowNumberRow(true)
                    }) {
                        Text("রিসেট", fontWeight = FontWeight.SemiBold)
                    }
                }
            )
        },
        modifier = modifier.testTag("customize_screen_root")
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Live Interactive Preview Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Preview,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "লাইভ কিবোর্ড প্রিভিউ",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        AssistChip(
                            onClick = {
                                previewMode = if (previewMode == KeyboardMode.BENGALI) KeyboardMode.ENGLISH else KeyboardMode.BENGALI
                            },
                            label = { Text(if (previewMode == KeyboardMode.BENGALI) "বাংলা" else "English") },
                            leadingIcon = { Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        )
                    }

                    if (previewText.isNotEmpty()) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = previewText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { previewText = "" }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Embedded live keyboard
                    MoeKeyboardView(
                        mode = previewMode,
                        bengaliPage = previewBengaliPage,
                        shiftState = previewShiftState,
                        themeMode = themePref,
                        isDarkTheme = isDark,
                        keyboardHeight = heightPref,
                        imeAction = 0,
                        clipboardItems = emptyList(),
                        isClipboardPanelOpen = false,
                        onKeyClick = { previewText += it },
                        onSpecialKeyClick = { type ->
                            when (type) {
                                SpecialKeyType.BACKSPACE -> if (previewText.isNotEmpty()) previewText = previewText.dropLast(1)
                                SpecialKeyType.SPACE -> previewText += " "
                                SpecialKeyType.SHIFT -> previewShiftState = if (previewShiftState == ShiftState.OFF) ShiftState.ONCE else ShiftState.OFF
                                SpecialKeyType.LANGUAGE_SWITCH -> previewMode = if (previewMode == KeyboardMode.BENGALI) KeyboardMode.ENGLISH else KeyboardMode.BENGALI
                                SpecialKeyType.MODE_SWITCH -> previewMode = if (previewMode == KeyboardMode.NUMBERS) KeyboardMode.BENGALI else KeyboardMode.NUMBERS
                                SpecialKeyType.EMOJI -> previewMode = if (previewMode == KeyboardMode.EMOJI) KeyboardMode.BENGALI else KeyboardMode.EMOJI
                                SpecialKeyType.BENGALI_PAGE_TOGGLE -> previewBengaliPage = if (previewBengaliPage == BengaliPage.PAGE_1) BengaliPage.PAGE_2 else BengaliPage.PAGE_1
                                else -> {}
                            }
                        },
                        onToggleClipboardPanel = {},
                        onOpenBrowser = {},
                        onOpenSettings = {},
                        onModeChange = { previewMode = it },
                        onBengaliPageToggle = {
                            previewBengaliPage = if (previewBengaliPage == BengaliPage.PAGE_1) BengaliPage.PAGE_2 else BengaliPage.PAGE_1
                        },
                        onPasteClipboardItem = { previewText += it },
                        onDeleteClipboardItem = {},
                        onClearClipboard = {},
                        customFontPath = customFontPath,
                        modifier = Modifier.clip(RoundedCornerShape(12.dp))
                    )
                }
            }

            // 2. Themes Selection Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ColorLens, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "কিবোর্ড থিম ও কালার",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        themes.forEach { item ->
                            val isSelected = themePref == item.id
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { prefs.setTheme(item.id) }
                                    .border(
                                        width = if (isSelected) 2.5.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(14.dp)
                                    )
                                    .padding(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(CircleShape)
                                        .background(item.color)
                                        .border(2.dp, item.accent, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = item.accent,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = item.name,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            // 3. Keyboard Height Adjustment
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Height, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "কিবোর্ড উচ্চতা (Height)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        heights.forEach { (id, label) ->
                            val isSelected = heightPref == id
                            FilterChip(
                                selected = isSelected,
                                onClick = { prefs.setHeight(id) },
                                label = { Text(label, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                leadingIcon = if (isSelected) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                } else null
                            )
                        }
                    }
                }
            }

            // 4. Smart Typing Features (Prediction & Auto-Correct)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "স্মার্ট টাইপিং ও স্বয়ংক্রিয় সংশোধন",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Word Suggestions / Predictions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = "শব্দ সাজেশন ও পরবর্তী শব্দ প্রেডিকশন",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "যেমন \"I\" লিখলে \"love\" বা বেশি টাইপ করা শব্দগুলো উপরে সাজেস্ট করবে",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = wordSuggestionsEnabled,
                            onCheckedChange = { prefs.setWordSuggestions(it) }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    // Bangla Typo Auto-Correct
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = "বাংলা ভুল বানান সংশোধন (Auto-Correct)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "টাইপের সময় ভুল হলে (যেমন \"কাছ\" লিখলে স্বয়ংক্রিয়ভাবে \"কাজ\") ঠিক করে দেবে",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = autoCorrectEnabled,
                            onCheckedChange = { prefs.setAutoCorrect(it) }
                        )
                    }
                }
            }

            // 5. Custom Font Selection
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.TextFields, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "কিবোর্ড ফন্ট সেটআপ",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = if (customFontPath == null) "ডিফল্ট ফন্ট ব্যবহার হচ্ছে" else "কাস্টম ফন্ট সক্রিয় আছে",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (customFontPath == null) "আপনার পছন্দের .ttf/.otf ফন্ট ফাইল আপলোড করুন" else "ফাইল: ${File(customFontPath!!).name}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (customFontPath != null) {
                                IconButton(onClick = {
                                    val file = File(customFontPath!!)
                                    if (file.exists()) file.delete()
                                    prefs.setCustomFontPath(null)
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Remove Font", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                            
                            Button(
                                onClick = { fontPickerLauncher.launch("*/*") },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(if (customFontPath == null) "ফন্ট বেছে নিন" else "পরিবর্তন করুন")
                            }
                        }
                    }
                }
            }

            // 6. General Feedback & Settings
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "ফিডব্যাক ও অন্যান্য",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Vibration
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "হ্যাপটিক ভাইব্রেশন", style = MaterialTheme.typography.bodyMedium)
                            Text(text = "কী চাপলে মৃদু কম্পন অনুভব হবে", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = vibrationEnabled, onCheckedChange = { prefs.setVibration(it) })
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                    // Sound
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "কী-প্রেস সাউন্ড", style = MaterialTheme.typography.bodyMedium)
                            Text(text = "কী চাপলে মিষ্টি ক্লিকের শব্দ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = soundEnabled, onCheckedChange = { prefs.setSound(it) })
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                    // Dedicated Number Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "টপ নাম্বার রো (১ ২ ৩ ৪ / 1 2 3 4)", style = MaterialTheme.typography.bodyMedium)
                            Text(text = "কিবোর্ডের উপরে সার্বক্ষণিক সংখ্যা সারি দেখাবে", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = numberRowEnabled, onCheckedChange = { prefs.setShowNumberRow(it) })
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

data class ThemeOption(
    val id: String,
    val name: String,
    val color: Color,
    val accent: Color
)
