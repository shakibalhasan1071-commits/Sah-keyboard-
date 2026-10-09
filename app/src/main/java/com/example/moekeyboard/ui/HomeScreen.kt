package com.example.moekeyboard.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.moekeyboard.data.db.MoeDatabase
import com.example.moekeyboard.data.prefs.KeyboardPreferences
import com.example.moekeyboard.ime.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToCustomize: () -> Unit = {},
    onNavigateToClipboard: () -> Unit,
    onNavigateToBrowser: () -> Unit,
    onNavigateToTempMail: () -> Unit = {},
    onNavigateToAbout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val database = remember { MoeDatabase.getDatabase(context) }
    val prefs = remember { KeyboardPreferences.getInstance(context) }

    var isKeyboardEnabled by remember { mutableStateOf(false) }
    var isKeyboardSelected by remember { mutableStateOf(false) }
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasAudioPermission = isGranted
    }

    var hasOverlayPermission by remember {
        mutableStateOf(Settings.canDrawOverlays(context))
    }

    var testInputText by remember { mutableStateOf("") }
    var showInteractiveKeyboard by remember { mutableStateOf(true) }

    // Live keyboard states for instant in-app typing & testing
    var keyboardMode by remember { mutableStateOf(KeyboardMode.BENGALI) }
    var bengaliPage by remember { mutableStateOf(BengaliPage.PAGE_1) }
    var shiftState by remember { mutableStateOf(ShiftState.OFF) }
    var isClipboardPanelOpen by remember { mutableStateOf(false) }

    val themePref by prefs.theme.collectAsState()
    val keyboardHeight by prefs.height.collectAsState()
    val systemDark = isSystemInDarkTheme()
    val isDark = when (themePref) {
        "light", "lavender" -> false
        "dark", "amoled", "ocean_blue", "neon_cyber", "forest" -> true
        else -> systemDark
    }

    val clipboardItems by database.clipboardDao().getRecentItems(30).collectAsState(initial = emptyList())

    fun refreshStatus() {
        try {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            if (imm != null) {
                val enabledList = imm.enabledInputMethodList
                isKeyboardEnabled = enabledList.any { it.packageName == context.packageName }
            }
        } catch (_: Exception) {
            isKeyboardEnabled = false
        }

        try {
            val defaultIme = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.DEFAULT_INPUT_METHOD
            )
            isKeyboardSelected = defaultIme?.contains(context.packageName) == true
        } catch (_: Exception) {
            isKeyboardSelected = false
        }

        hasAudioPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        hasOverlayPermission = Settings.canDrawOverlays(context)
    }

    // Refresh when screen resumes
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        refreshStatus()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("home_screen_root"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Card: MOE Keyboard Branding & Introduction
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Keyboard,
                        contentDescription = "Sah Keyboard Logo",
                        tint = Color.White,
                        modifier = Modifier.size(38.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Sah Keyboard",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )

                Text(
                    text = "স্মার্ট বাংলা ও ইংরেজি কিবোর্ড",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Fast Bengali typing, smart Kar vowels strip, local device clipboard, and built-in mini browser.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }

        // Setup Steps / Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "Setup & Activation",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Step 1: Enable
                SetupStepRow(
                    stepNumber = 1,
                    title = "Enable Sah Keyboard",
                    subtitle = if (isKeyboardEnabled) "Enabled in Android Settings" else "Turn on Sah Keyboard in Manage Keyboards",
                    isCompleted = isKeyboardEnabled,
                    buttonText = if (isKeyboardEnabled) "Settings" else "Enable Now",
                    onAction = {
                        val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
                        context.startActivity(intent)
                    }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                // Step 2: Select
                SetupStepRow(
                    stepNumber = 2,
                    title = "Select as Active Keyboard",
                    subtitle = if (isKeyboardSelected) "Sah Keyboard is currently active" else "Choose Sah Keyboard from input picker",
                    isCompleted = isKeyboardSelected,
                    buttonText = if (isKeyboardSelected) "Switch" else "Select Now",
                    enabled = true,
                    onAction = {
                        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                        imm?.showInputMethodPicker()
                    }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                // Step 3: Voice Typing Audio Permission
                SetupStepRow(
                    stepNumber = 3,
                    title = "ভয়েস টাইপিং মাইক্রোফোন পারমিশন",
                    subtitle = if (hasAudioPermission) "মাইক্রোফোন পারমিশন সচল আছে (Voice Typing Ready)" else "ভয়েস দিয়ে টাইপ করতে অডিও পারমিশন দিন",
                    isCompleted = hasAudioPermission,
                    buttonText = if (hasAudioPermission) "Granted" else "অনুমতি দিন",
                    enabled = !hasAudioPermission,
                    onAction = {
                        try {
                            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        } catch (_: Exception) {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        }
                    }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                // Step 4: Floating Window (Telegram / Bot Overlay) Permission
                SetupStepRow(
                    stepNumber = 4,
                    title = "ফ্লোটিং উইন্ডো পারমিশন (Overlay)",
                    subtitle = if (hasOverlayPermission) "ফ্লোটিং উইন্ডো পারমিশন সক্রিয় আছে (Telegram / Bot Ready)" else "যেকোনো অ্যাপের উপর ড্র্যাগেবল উইন্ডো চালাতে Overlay পারমিশন দিন",
                    isCompleted = hasOverlayPermission,
                    buttonText = if (hasOverlayPermission) "Active" else "অনুমতি দিন",
                    enabled = !hasOverlayPermission,
                    onAction = {
                        try {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                            context.startActivity(intent)
                        }
                    }
                )
            }
        }

        // Interactive Live Test & Practice Area
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.EditNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Test & Practice Keyboard",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    FilterChip(
                        selected = showInteractiveKeyboard,
                        onClick = { showInteractiveKeyboard = !showInteractiveKeyboard },
                        label = {
                            Text(
                                if (showInteractiveKeyboard) "Hide Keyboard" else "Show Keyboard",
                                fontSize = 11.sp
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = if (showInteractiveKeyboard) Icons.Default.KeyboardHide else Icons.Default.Keyboard,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "নিচে সরাসরি MOE Keyboard দিয়ে বাংলা ও ইংরেজি টাইপ করুন অথবা সিস্টেম কিবোর্ড চালু করুন:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = testInputText,
                    onValueChange = { testInputText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("interactive_test_input"),
                    placeholder = { Text("এখানে বাংলা বা ইংরেজি লিখুন...") },
                    shape = RoundedCornerShape(12.dp),
                    trailingIcon = {
                        if (testInputText.isNotEmpty()) {
                            IconButton(onClick = { testInputText = "" }) {
                                Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                            imm?.showInputMethodPicker()
                        },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.KeyboardAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("System Keyboard Picker", fontSize = 12.sp)
                    }

                    if (testInputText.isNotEmpty()) {
                        Text(
                            text = "${testInputText.length} chars",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                AnimatedVisibility(visible = showInteractiveKeyboard) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                            .clip(RoundedCornerShape(16.dp))
                    ) {
                        MoeKeyboardView(
                            mode = keyboardMode,
                            bengaliPage = bengaliPage,
                            shiftState = shiftState,
                            themeMode = themePref,
                            isDarkTheme = isDark,
                            keyboardHeight = keyboardHeight,
                            imeAction = 0,
                            clipboardItems = clipboardItems,
                            isClipboardPanelOpen = isClipboardPanelOpen,
                            onKeyClick = { key ->
                                testInputText += key
                                if (shiftState == ShiftState.ONCE) {
                                    shiftState = ShiftState.OFF
                                }
                            },
                            onReplacePrefixAndInsert = { prefix, replacement ->
                                if (prefix.isNotEmpty() && testInputText.endsWith(prefix)) {
                                    testInputText = testInputText.dropLast(prefix.length) + replacement
                                } else {
                                    testInputText += replacement
                                }
                            },
                            onSpecialKeyClick = { type ->
                                when (type) {
                                    SpecialKeyType.BACKSPACE -> {
                                        if (testInputText.isNotEmpty()) {
                                            testInputText = testInputText.dropLast(1)
                                        }
                                    }
                                    SpecialKeyType.SPACE -> {
                                        testInputText += " "
                                    }
                                    SpecialKeyType.ENTER -> {
                                        testInputText += "\n"
                                    }
                                    SpecialKeyType.SHIFT -> {
                                        shiftState = when (shiftState) {
                                            ShiftState.OFF -> ShiftState.ONCE
                                            ShiftState.ONCE -> ShiftState.LOCKED
                                            ShiftState.LOCKED -> ShiftState.OFF
                                        }
                                    }
                                    SpecialKeyType.LANGUAGE_SWITCH -> {
                                        keyboardMode = if (keyboardMode == KeyboardMode.BENGALI) {
                                            KeyboardMode.ENGLISH
                                        } else {
                                            KeyboardMode.BENGALI
                                        }
                                        isClipboardPanelOpen = false
                                    }
                                    SpecialKeyType.BENGALI_PAGE_TOGGLE -> {
                                        bengaliPage = if (bengaliPage == BengaliPage.PAGE_1) {
                                            BengaliPage.PAGE_2
                                        } else {
                                            BengaliPage.PAGE_1
                                        }
                                    }
                                    SpecialKeyType.MODE_SWITCH -> {
                                        keyboardMode = if (keyboardMode == KeyboardMode.NUMBERS || keyboardMode == KeyboardMode.SYMBOLS) {
                                            KeyboardMode.BENGALI
                                        } else {
                                            KeyboardMode.NUMBERS
                                        }
                                    }
                                    SpecialKeyType.EMOJI -> {
                                        keyboardMode = KeyboardMode.EMOJI
                                    }
                                    SpecialKeyType.HIDE_KEYBOARD -> {
                                        showInteractiveKeyboard = false
                                    }
                                    SpecialKeyType.MOVE_CURSOR_LEFT -> {}
                                    SpecialKeyType.MOVE_CURSOR_RIGHT -> {}
                                }
                            },
                            onToggleClipboardPanel = {
                                isClipboardPanelOpen = !isClipboardPanelOpen
                            },
                            onOpenBrowser = onNavigateToBrowser,
                            onOpenSettings = onNavigateToSettings,
                            onModeChange = { newMode ->
                                keyboardMode = newMode
                                isClipboardPanelOpen = false
                            },
                            onBengaliPageToggle = {
                                bengaliPage = if (bengaliPage == BengaliPage.PAGE_1) {
                                    BengaliPage.PAGE_2
                                } else {
                                    BengaliPage.PAGE_1
                                }
                            },
                            onPasteClipboardItem = { text ->
                                testInputText += text
                            },
                            onDeleteClipboardItem = { item ->
                                coroutineScope.launch(Dispatchers.IO) {
                                    database.clipboardDao().delete(item)
                                }
                            },
                            onClearClipboard = {
                                coroutineScope.launch(Dispatchers.IO) {
                                    database.clipboardDao().clearAll()
                                }
                            }
                        )
                    }
                }
            }
        }

        // Quick Navigation Hub
        Text(
            text = "Features & Tools",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp, top = 4.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FeatureActionCard(
                icon = Icons.Default.Palette,
                title = "Customize",
                subtitle = "Themes & Heights",
                modifier = Modifier.weight(1f),
                onClick = onNavigateToCustomize
            )

            FeatureActionCard(
                icon = Icons.Default.ContentPaste,
                title = "Clipboard",
                subtitle = "Local History",
                modifier = Modifier.weight(1f),
                onClick = onNavigateToClipboard
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FeatureActionCard(
                icon = Icons.Default.MarkEmailRead,
                title = "Temp Mail",
                subtitle = "Disposable Inbox",
                modifier = Modifier.weight(1f),
                onClick = onNavigateToTempMail
            )

            FeatureActionCard(
                icon = Icons.Default.Language,
                title = "Browser",
                subtitle = "Mini Web",
                modifier = Modifier.weight(1f),
                onClick = onNavigateToBrowser
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FeatureActionCard(
                icon = Icons.AutoMirrored.Filled.OpenInNew,
                title = "Floating Window",
                subtitle = "Telegram & Bot Overlay",
                modifier = Modifier.weight(1f),
                onClick = {
                    com.example.moekeyboard.floating.FloatingBrowserService.startOrToggle(context)
                }
            )

            FeatureActionCard(
                icon = Icons.Default.Tune,
                title = "Settings",
                subtitle = "Preferences",
                modifier = Modifier.weight(1f),
                onClick = onNavigateToSettings
            )
        }

        Spacer(modifier = Modifier.height(30.dp))
    }
}

@Composable
fun SetupStepRow(
    stepNumber: Int,
    title: String,
    subtitle: String,
    isCompleted: Boolean,
    buttonText: String,
    enabled: Boolean = true,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(
                    if (isCompleted) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primaryContainer
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isCompleted) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Completed",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Text(
                    text = "$stepNumber",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontSize = 15.sp
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Button(
            onClick = onAction,
            enabled = enabled,
            colors = if (isCompleted) {
                ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            } else {
                ButtonDefaults.buttonColors()
            },
            shape = RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(buttonText, fontSize = 12.sp)
        }
    }
}

@Composable
fun FeatureActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
