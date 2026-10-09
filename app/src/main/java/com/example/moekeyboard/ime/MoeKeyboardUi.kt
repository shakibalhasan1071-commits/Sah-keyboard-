package com.example.moekeyboard.ime
// Trivial change to force re-parse

import android.content.Context
import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import android.view.inputmethod.EditorInfo
import com.example.moekeyboard.tempmail.TempMailManager
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.automirrored.filled.KeyboardTab
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import com.example.R
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.example.moekeyboard.data.db.ClipboardItem
import com.example.moekeyboard.data.prefs.KeyboardPreferences
import com.example.ui.theme.HindSiliguri
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

data class KeyboardThemeColors(
    val bg: Color,
    val keyBg: Color,
    val specialKeyBg: Color,
    val accent: Color,
    val text: Color,
    val subText: Color,
    val keyShadow: Color,
    val isDark: Boolean
)

fun resolveKeyboardTheme(themeMode: String, isDarkTheme: Boolean): KeyboardThemeColors {
    return when (themeMode) {
        "light" -> KeyboardThemeColors(
            bg = Color(0xFFCFD3D9), // Sleek iOS Metallic Light
            keyBg = Color(0xFFFFFFFF), // Pure white keycaps
            specialKeyBg = Color(0xFFA8B0BC), // iOS Function keys
            accent = Color(0xFF007AFF), // Apple iOS Blue
            text = Color(0xFF000000),
            subText = Color(0xFF4B5563),
            keyShadow = Color(0x668E8E93),
            isDark = false
        )
        "dark" -> KeyboardThemeColors(
            bg = Color(0xFF131315), // Deep iOS Glass Dark
            keyBg = Color(0xFF2C2C2E), // Curved iOS keycaps
            specialKeyBg = Color(0xFF1C1C1E), // Matte functional keys
            accent = Color(0xFF0A84FF), // Vivid iOS Electric Blue
            text = Color(0xFFFFFFFF),
            subText = Color(0xFF9EA3B0),
            keyShadow = Color(0x99000000),
            isDark = true
        )
        "amoled" -> KeyboardThemeColors(
            bg = Color(0xFF000000),
            keyBg = Color(0xFF18181B),
            specialKeyBg = Color(0xFF0E0E10),
            accent = Color(0xFF00E5FF),
            text = Color(0xFFFFFFFF),
            subText = Color(0xFFAAAAAA),
            keyShadow = Color(0xCC000000),
            isDark = true
        )
        "ocean_blue" -> KeyboardThemeColors(
            bg = Color(0xFF0F172A),
            keyBg = Color(0xFF1E293B),
            specialKeyBg = Color(0xFF334155),
            accent = Color(0xFF38BDF8),
            text = Color(0xFFF8FAFC),
            subText = Color(0xFF94A3B8),
            keyShadow = Color(0x88020617),
            isDark = true
        )
        "neon_cyber" -> KeyboardThemeColors(
            bg = Color(0xFF0B0F19),
            keyBg = Color(0xFF161B26),
            specialKeyBg = Color(0xFF212838),
            accent = Color(0xFF00FF88),
            text = Color(0xFFE6EDF3),
            subText = Color(0xFF8B949E),
            keyShadow = Color(0xAA000000),
            isDark = true
        )
        "lavender" -> KeyboardThemeColors(
            bg = Color(0xFFEADBFF),
            keyBg = Color(0xFFFFFFFF),
            specialKeyBg = Color(0xFFD0BCFF),
            accent = Color(0xFF6750A4),
            text = Color(0xFF21005D),
            subText = Color(0xFF625B71),
            keyShadow = Color(0x447D5260),
            isDark = false
        )
        "forest" -> KeyboardThemeColors(
            bg = Color(0xFF06281E),
            keyBg = Color(0xFF0B3B2D),
            specialKeyBg = Color(0xFF114F3D),
            accent = Color(0xFF2DD4BF),
            text = Color(0xFFECFDF5),
            subText = Color(0xFF6EE7B7),
            keyShadow = Color(0x88000000),
            isDark = true
        )
        else -> {
            if (isDarkTheme) {
                KeyboardThemeColors(
                    bg = Color(0xFF131315),
                    keyBg = Color(0xFF2C2C2E),
                    specialKeyBg = Color(0xFF1C1C1E),
                    accent = Color(0xFF0A84FF),
                    text = Color(0xFFFFFFFF),
                    subText = Color(0xFF9EA3B0),
                    keyShadow = Color(0x99000000),
                    isDark = true
                )
            } else {
                KeyboardThemeColors(
                    bg = Color(0xFFCFD3D9),
                    keyBg = Color(0xFFFFFFFF),
                    specialKeyBg = Color(0xFFA8B0BC),
                    accent = Color(0xFF007AFF),
                    text = Color(0xFF000000),
                    subText = Color(0xFF4B5563),
                    keyShadow = Color(0x668E8E93),
                    isDark = false
                )
            }
        }
    }
}

val LocalKeyboardFont = staticCompositionLocalOf { HindSiliguri }

fun launchPhoneApp(context: Context, pkg: String) {
    try {
        val pm = context.packageManager
        var intent = pm.getLaunchIntentForPackage(pkg)
        if (intent == null) {
            val queryIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(pkg)
            val list = pm.queryIntentActivities(queryIntent, 0)
            if (list.isNotEmpty()) {
                val act = list[0].activityInfo
                intent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    component = android.content.ComponentName(act.packageName, act.name)
                }
            }
        }
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            context.startActivity(intent)
        } else {
            Toast.makeText(context, "অ্যাপটি ওপেন করা সম্ভব হয়নি", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        Toast.makeText(context, "ত্রুটি: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}



@Composable
fun MoeKeyboardView(
    mode: KeyboardMode,
    bengaliPage: BengaliPage,
    shiftState: ShiftState,
    themeMode: String = "system",
    isDarkTheme: Boolean,
    keyboardHeight: String,
    imeAction: Int,
    clipboardItems: List<ClipboardItem>,
    isClipboardPanelOpen: Boolean,
    onKeyClick: (String) -> Unit,
    onSpecialKeyClick: (SpecialKeyType) -> Unit,
    onReplacePrefixAndInsert: ((String, String) -> Unit)? = null,
    onToggleClipboardPanel: () -> Unit,
    onOpenBrowser: () -> Unit,
    onOpenSettings: () -> Unit,
    onModeChange: (KeyboardMode) -> Unit,
    onBengaliPageToggle: () -> Unit,
    onPasteClipboardItem: (String) -> Unit,
    onDeleteClipboardItem: (ClipboardItem) -> Unit,
    onClearClipboard: () -> Unit,
    customFontPath: String? = null,
    inputSessionId: Int = 0,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("moe_keyboard_prefs", android.content.Context.MODE_PRIVATE) }
    val theme = resolveKeyboardTheme(themeMode, isDarkTheme)
    val bgColor = theme.bg
    val keyBgColor = theme.keyBg
    val specialKeyBg = theme.specialKeyBg
    val accentColor = theme.accent
    val textColor = theme.text

    var isAppsDrawerOpen by remember { mutableStateOf(false) }
    var isVoiceListening by remember { mutableStateOf(false) }
    var isBrowserPanelOpen by remember { mutableStateOf(false) }
    var browserUrlInput by remember { mutableStateOf("https://www.google.com") }
    var browserPanelHeight by remember { mutableStateOf(320.dp) }
    var isTempMailPanelOpen by remember { mutableStateOf(false) }
    var tempMailPanelHeight by remember { mutableStateOf(280.dp) }
    var isEmailSheetPanelOpen by remember { mutableStateOf(false) }
    var emailSheetPanelHeight by remember { mutableStateOf(280.dp) }
    var isNotepadPanelOpen by remember { mutableStateOf(false) }
    var notepadText by remember { mutableStateOf(prefs.getString("saved_note", "") ?: "") }
    var notepadPanelHeight by remember { mutableStateOf(240.dp) }
    var isSearchPanelOpen by remember { mutableStateOf(false) }
    var searchPanelHeight by remember { mutableStateOf(240.dp) }
    var searchQuery by remember { mutableStateOf("") }
    var isCalcPanelOpen by remember { mutableStateOf(false) }
    var calcText by remember { mutableStateOf("") }
    var calcPanelHeight by remember { mutableStateOf(280.dp) }
    var isQuickSettingsPanelOpen by remember { mutableStateOf(false) }
    var quickSettingsPanelHeight by remember { mutableStateOf(270.dp) }
    var isPasswordPopoverOpen by remember { mutableStateOf(false) }
    var isEmailPopoverOpen by remember { mutableStateOf(false) }
    var isAutofillPopoverOpen by remember { mutableStateOf(false) }
    var isPinnedNotesPopoverOpen by remember { mutableStateOf(false) }
    var isImportantNotesPopoverOpen by remember { mutableStateOf(false) }
    var isAiPanelOpen by remember { mutableStateOf(false) }
    var aiPanelHeight by remember { mutableStateOf(300.dp) }
    var isShortcutsPanelOpen by remember { mutableStateOf(false) }
    var isStickersPanelOpen by remember { mutableStateOf(false) }
    var isResizePanelOpen by remember { mutableStateOf(false) }
    var isCustomizerOpen by remember { mutableStateOf(false) }
    var isShortcutPickerOpen by remember { mutableStateOf(false) }
    var previousLanguage by remember { mutableStateOf(KeyboardMode.BENGALI) }
    var browserActionHandler: ((BrowserAction) -> Unit)? by remember { mutableStateOf(null) }
    var activePanelInputHandler by remember { mutableStateOf<((String?, SpecialKeyType?) -> Unit)?>(null) }

    val tempMailManager = remember { TempMailManager.getInstance(context) }
    var importantNotesList by remember { mutableStateOf(tempMailManager.getImportantNotes()) }
    var activeStarredNote by remember { mutableStateOf(tempMailManager.getActiveStarredNote()) }
    LaunchedEffect(Unit) {
        TempMailWebBridge.init(context)
    }
    val keyboardCoroutineScope = rememberCoroutineScope()
    var activeTempEmail by remember { mutableStateOf(tempMailManager.currentEmail) }
    var isAddingImportantNote by remember { mutableStateOf(false) }
    var latestDetectedOtp by remember { mutableStateOf<String?>(null) }
    var previousPastedOtp by remember { mutableStateOf<String?>(null) }
    var otpPasteCount by remember { mutableIntStateOf(0) }
    var isGeneratingMail by remember { mutableStateOf(false) }
    var isCheckingOtp by remember { mutableStateOf(false) }
    var otpPollingJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var lastMailClickTime by remember { mutableStateOf(0L) }
    var lastOtpClickTime by remember { mutableStateOf(0L) }

    DisposableEffect(Unit) {
        val bridgeListener = object : TempMailWebBridge.TempMailEventListener {
            override fun onEmailReceived(email: String) {
                activeTempEmail = email
            }
            override fun onOtpReceived(otp: String, subject: String) {
                latestDetectedOtp = otp
            }
        }
        TempMailWebBridge.addListener(bridgeListener)
        onDispose {
            TempMailWebBridge.removeListener(bridgeListener)
        }
    }

    fun closeAllPanelsExcept(target: String = "") {
        if (target != "apps") isAppsDrawerOpen = false
        if (target != "voice") isVoiceListening = false
        if (target != "browser") isBrowserPanelOpen = false
        if (target != "temp_mail") isTempMailPanelOpen = false
        if (target != "email_sheet") isEmailSheetPanelOpen = false
        if (target != "notepad") isNotepadPanelOpen = false
        if (target != "search") {
            isSearchPanelOpen = false
            searchQuery = ""
        }
        if (target != "calc") isCalcPanelOpen = false
        if (target != "quick_settings") isQuickSettingsPanelOpen = false
        isPasswordPopoverOpen = false
        isEmailPopoverOpen = false
        isAutofillPopoverOpen = false
        isPinnedNotesPopoverOpen = false
        isImportantNotesPopoverOpen = false
        if (target != "ai") isAiPanelOpen = false
        if (target != "shortcuts") isShortcutsPanelOpen = false
        if (target != "stickers") isStickersPanelOpen = false
        if (target != "resize") isResizePanelOpen = false
        if (target != "customizer") isCustomizerOpen = false
        if (target != "picker") isShortcutPickerOpen = false
        if (target != "clipboard" && isClipboardPanelOpen) onToggleClipboardPanel()
    }

    val handleQuickInsertMail: () -> Unit = {
        val currentTime = System.currentTimeMillis()
        if (isGeneratingMail || (currentTime - lastMailClickTime < 2500)) {
            // Do nothing, reject rapid double clicks
        } else {
            isGeneratingMail = true
            lastMailClickTime = currentTime

            // 1. Open the Temp Mail window briefly (1-1.5s) as requested by user
            closeAllPanelsExcept("temp_mail")
            isTempMailPanelOpen = true

            keyboardCoroutineScope.launch {
                try {
                    latestDetectedOtp = null
                    previousPastedOtp = null
                    otpPasteCount = 0
                    tempMailManager.clearActiveOtpState()

                    // Ensure background web bridge is initialized
                    TempMailWebBridge.getOrCreatePersistentWebView(context)

                    // Small delay to ensure WebView is attached to window and active
                    kotlinx.coroutines.delay(350)

                    // 1. Trigger Delete / New Address on the active website ONCE on single click
                    var targetEmail = ""
                    val newWeb = kotlin.coroutines.suspendCoroutine<String> { cont ->
                        TempMailWebBridge.deleteAndGetNewEmail { res ->
                            cont.resumeWith(Result.success(res))
                        }
                    }
                    if (newWeb.isNotBlank() && newWeb.contains("@")) {
                        targetEmail = newWeb
                    }

                    // Fallback to API if WebView failed to produce an address
                    val currentSite = TempMailWebBridge.getCurrentSite(context)
                    if (targetEmail.isBlank() || !targetEmail.contains("@")) {
                        targetEmail = if (currentSite.id == "tempmail_lol") {
                            tempMailManager.generateNewTempMailLolAccount()
                        } else {
                            tempMailManager.generateNewAccount()
                        }
                    }

                    // 2. Update states
                    TempMailWebBridge.lastInsertedEmail = targetEmail
                    TempMailWebBridge.latestWebEmail = targetEmail
                    tempMailManager.updateCurrentAccountFromWeb(targetEmail, provider = currentSite.id)
                    activeTempEmail = targetEmail

                    // 3. Insert directly into target input field and clipboard
                    onPasteClipboardItem(targetEmail)
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Temp Mail", targetEmail))
                    val toastMsg = if (currentSite.id == "tempmail_lol") {
                        "ডাটা ক্লিয়ার করে নতুন ইমেল কপি ও পেস্ট হয়েছে: $targetEmail"
                    } else {
                        "ডিলিট করে নতুন ইমেল কপি ও পেস্ট হয়েছে: $targetEmail"
                    }
                    Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()

                    // Automatically close the window after a brief 1-second view
                    kotlinx.coroutines.delay(800)
                    isTempMailPanelOpen = false

                    // 4. Start background polling for fresh OTPs (supports 1st and 2nd OTP!)
                    otpPollingJob?.cancel()
                    otpPollingJob = launch {
                        var attempts = 0
                        var lastSeenOtp: String? = null
                        var otpArrivalCount = 0
                        while (attempts < 60) {
                            kotlinx.coroutines.delay(2000)
                            attempts++
                            TempMailWebBridge.openMessageAndExtractOtp()
                            val webOtp = kotlin.coroutines.suspendCoroutine<String> { cont ->
                                TempMailWebBridge.extractOtpFromWebPage(excludeOtp = lastSeenOtp) { res ->
                                    cont.resumeWith(Result.success(res))
                                }
                            }
                            val finalOtp = if (webOtp.isNotBlank()) webOtp else tempMailManager.extractLatestOtp(excludeOtp = lastSeenOtp).first
                            if (!finalOtp.isNullOrBlank() && finalOtp != lastSeenOtp) {
                                lastSeenOtp = finalOtp
                                latestDetectedOtp = finalOtp
                                otpArrivalCount++
                                val countText = if (otpArrivalCount == 1) "১ম" else if (otpArrivalCount == 2) "২য়" else "${otpArrivalCount}ম"
                                Toast.makeText(context, "$countText ওটিপি কোড এসেছে: $finalOtp (কোড আইকনে চাপুন)", Toast.LENGTH_LONG).show()
                                // Keep polling if only 1 OTP arrived, as the user needs 2 OTPs!
                                if (otpArrivalCount >= 2) break
                            }
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "ইমেল আনতে সমস্যা হয়েছে: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    isTempMailPanelOpen = false
                } finally {
                    isGeneratingMail = false
                }
            }
        }
    }

    val handleQuickInsertOtp: () -> Unit = {
        val currentTime = System.currentTimeMillis()
        if (isCheckingOtp || (currentTime - lastOtpClickTime < 2000)) {
            // Do nothing, reject rapid double clicks
        } else {
            isCheckingOtp = true
            lastOtpClickTime = currentTime

            // Open the Temp Mail window briefly so webview is active and user sees inbox
            closeAllPanelsExcept("temp_mail")
            isTempMailPanelOpen = true

            keyboardCoroutineScope.launch {
                try {
                    val searchMsg = if (otpPasteCount >= 1) "২য় ওটিপি কোড খোঁজা হচ্ছে..." else "নতুন ওটিপি কোড খোঁজা হচ্ছে..."
                    Toast.makeText(context, searchMsg, Toast.LENGTH_SHORT).show()

                    kotlinx.coroutines.delay(350)

                    // Trigger message opening in WebView (clicks incoming message at bottom on tempmail.lol)
                    TempMailWebBridge.openMessageAndExtractOtp()
                    kotlinx.coroutines.delay(650)

                    // Extract OTP directly from current active WebView page
                    var freshOtp = kotlin.coroutines.suspendCoroutine<String> { cont ->
                        TempMailWebBridge.extractOtpFromWebPage(excludeOtp = previousPastedOtp) { res ->
                            cont.resumeWith(Result.success(res))
                        }
                    }

                    // If not in WebView, check API inbox for current email address (excluding previous OTP)
                    if (freshOtp.isBlank()) {
                        val (apiOtp, _) = tempMailManager.extractLatestOtp(excludeOtp = previousPastedOtp)
                        if (!apiOtp.isNullOrBlank()) {
                            freshOtp = apiOtp
                        }
                    }

                    // If still blank, check latestDetectedOtp
                    if (freshOtp.isBlank() && !latestDetectedOtp.isNullOrBlank() && latestDetectedOtp != previousPastedOtp) {
                        freshOtp = latestDetectedOtp!!
                    }

                    // Fallback to latestDetectedOtp if needed
                    if (freshOtp.isBlank() && !latestDetectedOtp.isNullOrBlank()) {
                        freshOtp = latestDetectedOtp!!
                    }

                    if (freshOtp.isNotBlank()) {
                        otpPasteCount++
                        previousPastedOtp = freshOtp
                        latestDetectedOtp = freshOtp
                        onPasteClipboardItem(freshOtp)
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("OTP Code", freshOtp))

                        val countLabel = if (otpPasteCount == 1) "১ম" else if (otpPasteCount == 2) "২য়" else "${otpPasteCount}ম"
                        Toast.makeText(context, "$countLabel ওটিপি কোড কপি ও পেস্ট হয়েছে: $freshOtp", Toast.LENGTH_SHORT).show()

                        kotlinx.coroutines.delay(700)
                        isTempMailPanelOpen = false

                        // If user needs 2 OTPs and this was the 1st one, keep polling actively for the 2nd OTP!
                        if (otpPasteCount < 2) {
                            otpPollingJob?.cancel()
                            otpPollingJob = launch {
                                var attempts = 0
                                while (attempts < 60) {
                                    kotlinx.coroutines.delay(2000)
                                    attempts++
                                    TempMailWebBridge.openMessageAndExtractOtp()
                                    val web2 = kotlin.coroutines.suspendCoroutine<String> { cont ->
                                        TempMailWebBridge.extractOtpFromWebPage(excludeOtp = freshOtp) { res ->
                                            cont.resumeWith(Result.success(res))
                                        }
                                    }
                                    val final2 = if (web2.isNotBlank()) web2 else tempMailManager.extractLatestOtp(excludeOtp = freshOtp).first
                                    if (!final2.isNullOrBlank() && final2 != freshOtp) {
                                        latestDetectedOtp = final2
                                        Toast.makeText(context, "২য় ওটিপি কোড এসেছে: $final2 (কোড আইকনে চাপুন)", Toast.LENGTH_LONG).show()
                                        break
                                    }
                                }
                            }
                        }
                    } else {
                        Toast.makeText(context, "কোনো নতুন ওটিপি কোড এখনও আসেনি। কয়েক সেকেন্ড পর আবার চেষ্টা করুন।", Toast.LENGTH_SHORT).show()
                        kotlinx.coroutines.delay(1000)
                        isTempMailPanelOpen = false
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "ওটিপি আনতে সমস্যা হয়েছে: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    isTempMailPanelOpen = false
                } finally {
                    isCheckingOtp = false
                }
            }
        }
    }


    val handleOpenSheet: () -> Unit = {
        closeAllPanelsExcept("email_sheet")
        isEmailSheetPanelOpen = true
    }

    val currentFontFamily = remember(customFontPath) {
        if (customFontPath.isNullOrEmpty()) {
            com.example.ui.theme.HindSiliguri
        } else {
            try {
                // Use the file path to create a font family
                val file = java.io.File(customFontPath)
                if (file.exists()) {
                    FontFamily(androidx.compose.ui.text.font.Font(file))
                } else {
                    com.example.ui.theme.HindSiliguri
                }
            } catch (e: Exception) {
                com.example.ui.theme.HindSiliguri
            }
        }
    }

    LaunchedEffect(Unit) {
        SmartSuggestionEngine.init(context)
    }

    val wordSuggestionsEnabled by KeyboardPreferences.getInstance(context).wordSuggestions.collectAsState()
    var currentWordPrefix by remember { mutableStateOf("") }
    var previousWordText by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(inputSessionId) {
        currentWordPrefix = ""
        previousWordText = null
    }

    val currentSuggestions = remember(currentWordPrefix, previousWordText, mode, wordSuggestionsEnabled) {
        if (!wordSuggestionsEnabled) {
            emptyList()
        } else {
            SmartSuggestionEngine.getSuggestions(
                currentWord = currentWordPrefix,
                previousWord = previousWordText,
                isBengali = (mode == KeyboardMode.BENGALI)
            )
        }
    }

    fun handleKeyInput(char: String) {
        if (isSearchPanelOpen || isBrowserPanelOpen || isNotepadPanelOpen || isCalcPanelOpen || isTempMailPanelOpen || isAiPanelOpen || isQuickSettingsPanelOpen) {
            if (activePanelInputHandler != null) {
                activePanelInputHandler?.invoke(char, null)
                return
            }
        }
        if (isSearchPanelOpen) {
            searchQuery += char
            return
        }
        if (isBrowserPanelOpen) {
            browserActionHandler?.invoke(BrowserAction.TypeChar(char))
            return
        }
        if (isNotepadPanelOpen) {
            notepadText += char
            prefs.edit().putString("saved_note", notepadText).apply()
            return
        }
        if (isCalcPanelOpen) {
            if (char.all { it.isDigit() || it == '.' || "+-*/".contains(it) }) {
                calcText += char
                return
            }
        }
        if (char.contains(" ") || char.contains("\n") || char == "। " || char == "," || char == "." || char.length > 5) {
            if (currentWordPrefix.isNotBlank() && char.length == 1) {
                val wordToLearn = currentWordPrefix.trim()
                SmartSuggestionEngine.learnWord(wordToLearn)
                if (!previousWordText.isNullOrBlank()) {
                    SmartSuggestionEngine.learnTransition(previousWordText!!, wordToLearn, boost = 2)
                    SmartSuggestionEngine.learnPhrase("${previousWordText!!} $wordToLearn", boost = 2)
                }
                val corrected = SmartSuggestionEngine.AUTO_CORRECTIONS[currentWordPrefix.lowercase()]
                if (corrected != null) {
                    // Backspace the mistyped prefix and insert corrected word
                    for (i in 0 until currentWordPrefix.length) {
                        onSpecialKeyClick(SpecialKeyType.BACKSPACE)
                    }
                    onKeyClick(corrected)
                    SmartSuggestionEngine.learnWord(corrected, boost = 2)
                    if (!previousWordText.isNullOrBlank()) {
                        SmartSuggestionEngine.learnTransition(previousWordText!!, corrected, boost = 2)
                        SmartSuggestionEngine.learnPhrase("${previousWordText!!} $corrected", boost = 2)
                    }
                    previousWordText = corrected
                } else {
                    previousWordText = wordToLearn
                }
            } else if (char.length > 1) {
                // Multi-character input (e.g. voice typing or pasted text or emoji): extract words and learn them
                SmartSuggestionEngine.learnWordsFromText(char)
                SmartSuggestionEngine.learnPhrase(char)
                val trimmed = char.trim()
                if (trimmed.isNotEmpty()) {
                    if (!previousWordText.isNullOrBlank()) {
                        SmartSuggestionEngine.learnTransition(previousWordText!!, trimmed, boost = 3)
                        SmartSuggestionEngine.learnPhrase("${previousWordText!!} $trimmed", boost = 3)
                    }
                    previousWordText = trimmed.substringAfterLast(" ").trim()
                }
            }
            currentWordPrefix = ""
        } else {
            currentWordPrefix += char
        }
        onKeyClick(char)
    }

    val handleInsertCredential: () -> Unit = {
        val creds = tempMailManager.getSavedCredentials()
        if (creds.isNotEmpty()) {
            val last = creds.first()
            handleKeyInput(last.login)
            handleKeyInput(" ")
            handleKeyInput(last.password)
            Toast.makeText(context, "লগইন ও পাসওয়ার্ড বসানো হয়েছে", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "কোনো ক্রেডেনশিয়াল সেভ করা নেই।", Toast.LENGTH_SHORT).show()
        }
    }

    val handleInsertImportantNote: () -> Unit = {
        val targetNote = activeStarredNote ?: tempMailManager.getActiveStarredNote() ?: importantNotesList.firstOrNull()
        if (targetNote != null) {
            handleKeyInput(targetNote)
            Toast.makeText(context, "স্টার নোট বসানো হয়েছে", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "কোনো স্টার আইটেম নেই। লং প্রেস করে যোগ করুন।", Toast.LENGTH_SHORT).show()
            isImportantNotesPopoverOpen = true
        }
    }

    val handleLongPressImportantNote: () -> Unit = {
        isImportantNotesPopoverOpen = !isImportantNotesPopoverOpen
    }

    val handleInsertQuickPassword: () -> Unit = {
        val pwd = prefs.getString("quick_saved_password", "") ?: ""
        if (pwd.isNotBlank()) {
            handleKeyInput(pwd)
            Toast.makeText(context, "পাসওয়ার্ড বসানো হয়েছে", Toast.LENGTH_SHORT).show()
        } else {
            val raw = prefs.getString("saved_user_passwords_list", "") ?: ""
            val list = if (raw.isNotBlank()) raw.split("|||").map { it.trim() }.filter { it.isNotBlank() } else emptyList()
            if (list.isNotEmpty()) {
                val lastPwd = list.last()
                handleKeyInput(lastPwd)
                Toast.makeText(context, "পাসওয়ার্ড বসানো হয়েছে", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "পাসওয়ার্ড সেট করা নেই! সেটিং থেকে সেট করুন", Toast.LENGTH_SHORT).show()
                closeAllPanelsExcept("quick_settings")
                isQuickSettingsPanelOpen = true
            }
        }
        isPasswordPopoverOpen = false
    }

    val handleLongPressQuickPassword: () -> Unit = {
        val raw = prefs.getString("saved_user_passwords_list", "") ?: ""
        val pwds = if (raw.isNotBlank()) raw.split("|||").map { it.trim() }.filter { it.isNotBlank() } else emptyList()
        if (pwds.isNotEmpty()) {
            isPasswordPopoverOpen = !isPasswordPopoverOpen
            isEmailPopoverOpen = false
        } else {
            Toast.makeText(context, "কোনো পাসওয়ার্ড সেভ করা নেই। সেটিং থেকে যোগ করুন", Toast.LENGTH_SHORT).show()
            closeAllPanelsExcept("quick_settings")
            isQuickSettingsPanelOpen = true
        }
    }

    val handleLongPressSavedEmail: () -> Unit = {
        val raw = prefs.getString("saved_user_emails_list", "") ?: ""
        val emails = if (raw.isNotBlank()) raw.split(",").map { it.trim() }.filter { it.isNotBlank() } else emptyList()
        if (emails.isNotEmpty()) {
            isEmailPopoverOpen = !isEmailPopoverOpen
            isPasswordPopoverOpen = false
            isAutofillPopoverOpen = false
        } else {
            Toast.makeText(context, "কোনো ইমেল সেভ করা নেই। সেটিং থেকে যোগ করুন", Toast.LENGTH_SHORT).show()
            closeAllPanelsExcept("quick_settings")
            isQuickSettingsPanelOpen = true
        }
    }

    val handleInsertAutofillStep: () -> Unit = {
        val manager = SmartCredentialAutofillManager.getInstance(context)
        val res = manager.getNextStepAndAdvance()
        if (res != null) {
            handleKeyInput(res.step.value)
            val stepBangla = when (res.stepNumber) {
                1 -> "১ম"
                2 -> "২য়"
                3 -> "৩য়"
                4 -> "৪র্থ"
                5 -> "৫ম"
                else -> "${res.stepNumber}ম"
            }
            val valDisplay = if (res.step.label.lowercase().contains("pass")) "••••••••" else res.step.value
            val msg = if (res.isCompleted) {
                "$stepBangla ধাপ: ${res.step.label} ($valDisplay) বসানো হয়েছে - ৩টি ধাপ সম্পন্ন!"
            } else {
                "$stepBangla ধাপ: ${res.step.label} ($valDisplay) বসানো হয়েছে ➔ পরবর্তী: ${res.nextStepLabel}"
            }
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        } else {
            // Check if clipboard has anything that can be parsed right now
            var parsed = false
            try {
                val sysClipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                val clip = sysClipboard?.primaryClip
                if (clip != null && clip.itemCount > 0) {
                    val clipText = clip.getItemAt(0)?.coerceToText(context)?.toString()?.trim()
                    if (!clipText.isNullOrBlank()) {
                        parsed = manager.processCopiedText(clipText, isManualImport = false, isNewCopy = true)
                    }
                }
            } catch (_: Exception) {}

            if (parsed) {
                val retryRes = manager.getNextStepAndAdvance()
                if (retryRes != null) {
                    handleKeyInput(retryRes.step.value)
                    Toast.makeText(context, "১ম ধাপ: ${retryRes.step.label} (${retryRes.step.value}) বসানো হয়েছে", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(context, "কোনো ৩-ধাপ ক্রেডেনশিয়াল নেই। নতুন টেক্সট কপি করলেই ১/৩ শুরু হবে।", Toast.LENGTH_SHORT).show()
                isAutofillPopoverOpen = true
                isPasswordPopoverOpen = false
                isEmailPopoverOpen = false
            }
        }
    }

    val handleLongPressAutofill: () -> Unit = {
        isAutofillPopoverOpen = !isAutofillPopoverOpen
        isPasswordPopoverOpen = false
        isEmailPopoverOpen = false
        isPinnedNotesPopoverOpen = false
    }

    val handleLongPressClipboard: () -> Unit = {
        isPinnedNotesPopoverOpen = !isPinnedNotesPopoverOpen
        isPasswordPopoverOpen = false
        isEmailPopoverOpen = false
        isAutofillPopoverOpen = false
    }

    val handleInsertLastCopy: () -> Unit = {
        var latestText: String? = null
        try {
            val sysClipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = sysClipboard?.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val sysText = clip.getItemAt(0)?.coerceToText(context)?.toString()
                if (!sysText.isNullOrBlank()) {
                    latestText = sysText
                }
            }
        } catch (_: Exception) {}

        if (latestText.isNullOrBlank() && clipboardItems.isNotEmpty()) {
            latestText = clipboardItems.maxByOrNull { it.timestamp }?.text
        }

        if (!latestText.isNullOrBlank()) {
            handleKeyInput(latestText)
            Toast.makeText(context, "সর্বশেষ কপি করা লেখা পেস্ট হয়েছে", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "ক্লিপবোর্ডে কোনো কপি করা লেখা পাওয়া যায়নি", Toast.LENGTH_SHORT).show()
        }
    }

    val handleInsertSavedEmail: () -> Unit = {
        val rawList = prefs.getString("saved_user_emails_list", "") ?: ""
        val emailList = if (rawList.isNotBlank()) rawList.split(",").map { it.trim() }.filter { it.isNotBlank() } else emptyList()
        if (emailList.isNotEmpty()) {
            val usedRaw = prefs.getString("used_user_emails_set", "") ?: ""
            val usedSet = if (usedRaw.isNotBlank()) usedRaw.split("|||").map { it.trim() }.filter { it.isNotBlank() }.toSet() else emptySet()

            // Find first email that is NOT used yet
            val unusedEmail = emailList.firstOrNull { !usedSet.contains(it) }
            val emailToInsert = unusedEmail ?: emailList.first()

            val updatedUsedSet = if (unusedEmail != null) {
                usedSet + emailToInsert
            } else {
                setOf(emailToInsert)
            }
            prefs.edit().putString("used_user_emails_set", updatedUsedSet.joinToString("|||")).apply()

            handleKeyInput(emailToInsert)
            val remainingUnused = emailList.count { !updatedUsedSet.contains(it) }
            if (unusedEmail != null) {
                Toast.makeText(context, "ইমেল: $emailToInsert (বাকি অব্যবহৃত: $remainingUnused টি)", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "সব ইমেল ব্যবহৃত হয়েছিল, পুনরায় শুরু হলো: $emailToInsert", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "কোনো ইমেল সেভ করা নেই! সেটিং থেকে ইমেল যোগ করুন", Toast.LENGTH_SHORT).show()
            closeAllPanelsExcept("quick_settings")
            isQuickSettingsPanelOpen = true
        }
    }

    val handleOpenQuickSettings: () -> Unit = {
        val next = !isQuickSettingsPanelOpen
        closeAllPanelsExcept("quick_settings")
        isQuickSettingsPanelOpen = next
    }

    fun handleSpecialInput(type: SpecialKeyType) {
        if (type == SpecialKeyType.LANGUAGE_SWITCH || type == SpecialKeyType.MODE_SWITCH || type == SpecialKeyType.EMOJI || type == SpecialKeyType.HIDE_KEYBOARD) {
            onSpecialKeyClick(type)
            return
        }
        if (isSearchPanelOpen || isBrowserPanelOpen || isNotepadPanelOpen || isCalcPanelOpen || isTempMailPanelOpen || isAiPanelOpen || isQuickSettingsPanelOpen) {
            if (activePanelInputHandler != null) {
                activePanelInputHandler?.invoke(null, type)
                return
            }
        }
        if (isSearchPanelOpen) {
            when (type) {
                SpecialKeyType.BACKSPACE -> {
                    if (searchQuery.isNotEmpty()) {
                        searchQuery = searchQuery.dropLast(1)
                    }
                }
                SpecialKeyType.SPACE -> {
                    searchQuery += " "
                }
                SpecialKeyType.ENTER -> {
                    // Enter can optionally trigger action or insert search link
                }
                else -> {
                    onSpecialKeyClick(type)
                }
            }
            return
        }
        if (isBrowserPanelOpen) {
            when (type) {
                SpecialKeyType.BACKSPACE -> browserActionHandler?.invoke(BrowserAction.Backspace)
                SpecialKeyType.SPACE -> browserActionHandler?.invoke(BrowserAction.TypeChar(" "))
                SpecialKeyType.ENTER -> browserActionHandler?.invoke(BrowserAction.Enter)
                else -> {}
            }
            return
        }
        if (isNotepadPanelOpen) {
            when (type) {
                SpecialKeyType.BACKSPACE -> {
                    if (notepadText.isNotEmpty()) {
                        notepadText = notepadText.dropLast(1)
                        prefs.edit().putString("saved_note", notepadText).apply()
                    }
                }
                SpecialKeyType.SPACE -> {
                    notepadText += " "
                    prefs.edit().putString("saved_note", notepadText).apply()
                }
                SpecialKeyType.ENTER -> {
                    notepadText += "\n"
                    prefs.edit().putString("saved_note", notepadText).apply()
                }
                else -> {
                    onSpecialKeyClick(type)
                }
            }
            return
        }
        if (isCalcPanelOpen) {
            when (type) {
                SpecialKeyType.BACKSPACE -> {
                    if (calcText.isNotEmpty()) {
                        calcText = calcText.dropLast(1)
                    }
                }
                SpecialKeyType.ENTER -> {
                    // Trigger calculation logic if needed
                }
                SpecialKeyType.LANGUAGE_SWITCH, SpecialKeyType.MODE_SWITCH, SpecialKeyType.EMOJI, SpecialKeyType.HIDE_KEYBOARD -> {
                    onSpecialKeyClick(type)
                }
                else -> {
                    onSpecialKeyClick(type)
                }
            }
            return
        }
        if (type == SpecialKeyType.BACKSPACE) {
            if (currentWordPrefix.isNotEmpty()) {
                currentWordPrefix = currentWordPrefix.dropLast(1)
            } else if (previousWordText != null) {
                currentWordPrefix = previousWordText ?: ""
                previousWordText = null
            }
        } else if (type == SpecialKeyType.ENTER) {
            if (currentWordPrefix.isNotBlank()) {
                val wordToLearn = currentWordPrefix.trim()
                SmartSuggestionEngine.learnWord(wordToLearn)
                if (!previousWordText.isNullOrBlank()) {
                    SmartSuggestionEngine.learnTransition(previousWordText!!, wordToLearn, boost = 2)
                    SmartSuggestionEngine.learnPhrase("${previousWordText!!} $wordToLearn", boost = 2)
                }
            }
            currentWordPrefix = ""
            previousWordText = null
        } else if (type == SpecialKeyType.SPACE) {
            if (currentWordPrefix.isNotBlank()) {
                val wordToLearn = currentWordPrefix.trim()
                SmartSuggestionEngine.learnWord(wordToLearn)
                if (!previousWordText.isNullOrBlank()) {
                    SmartSuggestionEngine.learnTransition(previousWordText!!, wordToLearn, boost = 2)
                    SmartSuggestionEngine.learnPhrase("${previousWordText!!} $wordToLearn", boost = 2)
                }
                val corrected = SmartSuggestionEngine.AUTO_CORRECTIONS[currentWordPrefix.lowercase()]
                if (corrected != null) {
                    for (i in 0 until currentWordPrefix.length) {
                        onSpecialKeyClick(SpecialKeyType.BACKSPACE)
                    }
                    onKeyClick(corrected)
                    SmartSuggestionEngine.learnWord(corrected, boost = 2)
                    if (!previousWordText.isNullOrBlank()) {
                        SmartSuggestionEngine.learnTransition(previousWordText!!, corrected, boost = 2)
                        SmartSuggestionEngine.learnPhrase("${previousWordText!!} $corrected", boost = 2)
                    }
                    previousWordText = corrected
                } else {
                    previousWordText = wordToLearn
                }
            }
            currentWordPrefix = ""
        }
        onSpecialKeyClick(type)
    }

    // Key height scaling: Bengali has 6 rows, English/Numbers/Symbols have 4 rows
    val keyboardPrefs = remember { KeyboardPreferences.getInstance(context) }
    val syncHeights by keyboardPrefs.syncHeights.collectAsState()
    val bengaliRowHeightPref by keyboardPrefs.bengaliRowHeight.collectAsState()
    val englishRowHeightPref by keyboardPrefs.englishRowHeight.collectAsState()

    val bengaliKeyHeight: Dp = bengaliRowHeightPref.dp
    val englishKeyHeight: Dp = englishRowHeightPref.dp
    val defaultKeyHeight: Dp = englishKeyHeight

    fun applyStringHeight(newHeightStr: String) {
        keyboardPrefs.setHeight(newHeightStr)
        when (newHeightStr) {
            "extra_small" -> {
                keyboardPrefs.setBengaliRowHeight(32f)
                keyboardPrefs.setEnglishRowHeight(50f)
            }
            "small" -> {
                keyboardPrefs.setBengaliRowHeight(35f)
                keyboardPrefs.setEnglishRowHeight(55f)
            }
            "normal" -> {
                keyboardPrefs.setBengaliRowHeight(38f)
                keyboardPrefs.setEnglishRowHeight(59f)
            }
            "medium" -> {
                keyboardPrefs.setBengaliRowHeight(41f)
                keyboardPrefs.setEnglishRowHeight(64f)
            }
            "tall" -> {
                keyboardPrefs.setBengaliRowHeight(44f)
                keyboardPrefs.setEnglishRowHeight(68f)
            }
            "extra_tall" -> {
                keyboardPrefs.setBengaliRowHeight(49f)
                keyboardPrefs.setEnglishRowHeight(76f)
            }
        }
    }

    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    var clipboardTextText by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        try {
            if (clipboardManager.hasText()) {
                val text = clipboardManager.getText()?.text
                if (!text.isNullOrBlank()) {
                    clipboardTextText = text
                }
            }
        } catch (_: Exception) {}
    }

    LaunchedEffect(mode) {
        if (mode == KeyboardMode.BENGALI || mode == KeyboardMode.ENGLISH) {
            previousLanguage = mode
        }
    }

    var pinnedToolbarShortcuts by remember {
        val saved = prefs.getString("pinned_toolbar_shortcuts", "") ?: ""
        val initialList = if (saved.isNotBlank() && saved != "ai_assistant,temp_mail,calculator") {
            saved.split(",").filter { it.isNotBlank() }
        } else {
            listOf("ai_assistant", "temp_mail", "calculator")
        }
        mutableStateOf(initialList)
    }

    LaunchedEffect(Unit) {
        val saved = prefs.getString("pinned_toolbar_shortcuts", "") ?: ""
        if (saved.isBlank() || saved == "ai_assistant,temp_mail,calculator") {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val mainIntent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
                    val resolveInfos = context.packageManager.queryIntentActivities(mainIntent, 0)
                    val apps = resolveInfos.map { "app:" + it.activityInfo.packageName }
                        .distinct()
                        .take(3)
                    if (apps.isNotEmpty()) {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            pinnedToolbarShortcuts = apps
                        }
                    }
                } catch (_: Exception) {}
            }
        }
    }

    fun togglePinShortcut(id: String) {
        val current = pinnedToolbarShortcuts.toMutableList()
        if (current.contains(id)) {
            current.remove(id)
        } else {
            if (current.size >= 4) {
                current.removeAt(0)
            }
            current.add(id)
        }
        pinnedToolbarShortcuts = current
        prefs.edit().putString("pinned_toolbar_shortcuts", current.joinToString(",")).apply()
    }

    CompositionLocalProvider(LocalKeyboardFont provides currentFontFamily) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .background(bgColor)
                .testTag("moe_keyboard_root"),
            color = bgColor
        ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 7.dp, vertical = 2.dp)
                .padding(bottom = 32.dp)
        ) {
            // 1. Top Bar: Voice Strip (matching Screenshot) OR Standard Toolbar
            if (isVoiceListening) {
                // Direct Gboard-style in-keyboard voice strip (matches user's screenshot directly without changing page!)
                InKeyboardVoiceStrip(
                    isDarkTheme = theme.isDark,
                    currentKeyboardMode = mode,
                    onInsertText = { text ->
                        handleKeyInput(text)
                    },
                    onClose = {
                        isVoiceListening = false
                    },
                    modifier = Modifier.padding(bottom = 3.dp)
                )
            } else if (!isResizePanelOpen && !isCustomizerOpen) {
                Column {
                    // Unified Draggable Panels Container (placed ABOVE toolbar and keys)
                    if (isSearchPanelOpen) {
                        DraggablePanelContainer(
                            height = searchPanelHeight,
                            onHeightChange = { searchPanelHeight = it },
                            onClose = { isSearchPanelOpen = false },
                            isDarkTheme = theme.isDark
                        ) {
                            InKeyboardSearch(
                                isDarkTheme = theme.isDark,
                                height = searchPanelHeight - 28.dp,
                                query = searchQuery,
                                onQueryChange = { searchQuery = it },
                                onInsertText = onKeyClick,
                                onClose = { isSearchPanelOpen = false }
                            )
                        }
                    } else if (isBrowserPanelOpen) {
                        DraggablePanelContainer(
                            height = browserPanelHeight,
                            onHeightChange = { browserPanelHeight = it },
                            onClose = { isBrowserPanelOpen = false },
                            isDarkTheme = theme.isDark
                        ) {
                            InKeyboardBrowser(
                                isDarkTheme = theme.isDark,
                                height = browserPanelHeight - 28.dp,
                                initialUrl = browserUrlInput,
                                onUrlChange = { browserUrlInput = it },
                                onAction = { browserActionHandler = it },
                                onClose = { isBrowserPanelOpen = false }
                            )
                        }
                    } else if (isTempMailPanelOpen) {
                        DraggablePanelContainer(
                            height = tempMailPanelHeight,
                            onHeightChange = { tempMailPanelHeight = it },
                            onClose = { isTempMailPanelOpen = false },
                            isDarkTheme = theme.isDark
                        ) {
                            InKeyboardTempMail(
                                isDarkTheme = theme.isDark,
                                height = tempMailPanelHeight - 28.dp,
                                onInsertText = { handleKeyInput(it) },
                                onClose = { isTempMailPanelOpen = false }
                            )
                        }
                    } else if (isEmailSheetPanelOpen) {
                        DraggablePanelContainer(
                            height = emailSheetPanelHeight,
                            onHeightChange = { emailSheetPanelHeight = it },
                            onClose = { isEmailSheetPanelOpen = false },
                            isDarkTheme = theme.isDark
                        ) {
                            InKeyboardEmailSheet(
                                isDarkTheme = theme.isDark,
                                height = emailSheetPanelHeight - 28.dp,
                                onInsertText = { handleKeyInput(it) },
                                onClose = { isEmailSheetPanelOpen = false }
                            )
                        }
                    } else if (isNotepadPanelOpen) {
                        DraggablePanelContainer(
                            height = notepadPanelHeight,
                            onHeightChange = { notepadPanelHeight = it },
                            onClose = { isNotepadPanelOpen = false },
                            isDarkTheme = theme.isDark
                        ) {
                            InKeyboardNotepad(
                                isDarkTheme = theme.isDark,
                                height = notepadPanelHeight - 28.dp,
                                onInsertText = { handleKeyInput(it) },
                                onClose = {
                                    isNotepadPanelOpen = false
                                    isAddingImportantNote = false
                                },
                                onRegisterInputHandler = { handler ->
                                    activePanelInputHandler = handler
                                },
                                isForImportantNote = isAddingImportantNote,
                                onImportantNoteSaved = {
                                    isAddingImportantNote = false
                                    importantNotesList = tempMailManager.getImportantNotes()
                                }
                            )
                        }
                    } else if (isCalcPanelOpen) {
                        DraggablePanelContainer(
                            height = calcPanelHeight,
                            onHeightChange = { calcPanelHeight = it },
                            onClose = { isCalcPanelOpen = false },
                            isDarkTheme = theme.isDark
                        ) {
                            InKeyboardCalculator(
                                isDarkTheme = theme.isDark,
                                height = calcPanelHeight - 28.dp,
                                onInsertText = { handleKeyInput(it) },
                                onClose = { isCalcPanelOpen = false }
                            )
                        }
                    } else if (isQuickSettingsPanelOpen) {
                        DraggablePanelContainer(
                            height = quickSettingsPanelHeight,
                            onHeightChange = { quickSettingsPanelHeight = it },
                            onClose = { isQuickSettingsPanelOpen = false },
                            isDarkTheme = theme.isDark
                        ) {
                            InKeyboardQuickSettings(
                                isDarkTheme = theme.isDark,
                                height = quickSettingsPanelHeight - 28.dp,
                                onClose = { isQuickSettingsPanelOpen = false },
                                onRegisterInputHandler = { handler ->
                                    activePanelInputHandler = handler
                                }
                            )
                        }
                    } else if (isAiPanelOpen) {
                        DraggablePanelContainer(
                            height = aiPanelHeight,
                            onHeightChange = { aiPanelHeight = it },
                            onClose = { isAiPanelOpen = false },
                            isDarkTheme = theme.isDark
                        ) {
                            InKeyboardAiAssistant(
                                isDarkTheme = theme.isDark,
                                height = aiPanelHeight - 28.dp,
                                onInsertText = { handleKeyInput(it) },
                                onClose = { isAiPanelOpen = false }
                            )
                        }
                    }

                    if (isPasswordPopoverOpen) {
                        val raw = prefs.getString("saved_user_passwords_list", "") ?: ""
                        val pwds = if (raw.isNotBlank()) raw.split("|||").map { it.trim() }.filter { it.isNotBlank() } else emptyList()
                        PasswordPopoverStrip(
                            passwords = pwds,
                            isDarkTheme = theme.isDark,
                            onSelectPassword = { pwd ->
                                handleKeyInput(pwd)
                                isPasswordPopoverOpen = false
                                Toast.makeText(context, "পাসওয়ার্ড বসানো হয়েছে", Toast.LENGTH_SHORT).show()
                            },
                            onOpenSettings = {
                                isPasswordPopoverOpen = false
                                closeAllPanelsExcept("quick_settings")
                                isQuickSettingsPanelOpen = true
                            },
                            onClose = { isPasswordPopoverOpen = false },
                            modifier = Modifier.padding(bottom = 3.dp)
                        )
                    } else if (isEmailPopoverOpen) {
                        val raw = prefs.getString("saved_user_emails_list", "") ?: ""
                        val emails = if (raw.isNotBlank()) raw.split(",").map { it.trim() }.filter { it.isNotBlank() } else emptyList()
                        EmailPopoverStrip(
                            emails = emails,
                            isDarkTheme = theme.isDark,
                            onSelectEmail = { email ->
                                val usedRaw = prefs.getString("used_user_emails_set", "") ?: ""
                                val usedSet = if (usedRaw.isNotBlank()) usedRaw.split("|||").map { it.trim() }.filter { it.isNotBlank() }.toSet() else emptySet()
                                val updatedUsedSet = usedSet + email
                                prefs.edit().putString("used_user_emails_set", updatedUsedSet.joinToString("|||")).apply()

                                handleKeyInput(email)
                                isEmailPopoverOpen = false
                                Toast.makeText(context, "ইমেল বসানো হয়েছে: $email", Toast.LENGTH_SHORT).show()
                            },
                            onOpenSettings = {
                                isEmailPopoverOpen = false
                                closeAllPanelsExcept("quick_settings")
                                isQuickSettingsPanelOpen = true
                            },
                            onClose = { isEmailPopoverOpen = false },
                            modifier = Modifier.padding(bottom = 3.dp)
                        )
                    } else if (isAutofillPopoverOpen) {
                        CredentialAutofillPopoverStrip(
                            isDarkTheme = theme.isDark,
                            onSelectStep = { value ->
                                handleKeyInput(value)
                                isAutofillPopoverOpen = false
                                Toast.makeText(context, "বসানো হয়েছে: $value", Toast.LENGTH_SHORT).show()
                            },
                            onResetStep = {
                                SmartCredentialAutofillManager.getInstance(context).resetActiveGroupStep()
                                Toast.makeText(context, "১ম ধাপে রিসেট করা হয়েছে", Toast.LENGTH_SHORT).show()
                            },
                            onOpenSettings = {
                                isAutofillPopoverOpen = false
                                closeAllPanelsExcept("quick_settings")
                                isQuickSettingsPanelOpen = true
                            },
                            onClose = { isAutofillPopoverOpen = false },
                            modifier = Modifier.padding(bottom = 3.dp)
                        )
                    } else if (isPinnedNotesPopoverOpen) {
                        val pinnedList = clipboardItems.filter { it.isPinned }.map { it.text }
                        PinnedNotesPopoverStrip(
                            pinnedNotes = pinnedList,
                            isDarkTheme = theme.isDark,
                            onSelectNote = { note ->
                                handleKeyInput(note)
                                isPinnedNotesPopoverOpen = false
                                Toast.makeText(context, "পিন নোট পেস্ট করা হয়েছে", Toast.LENGTH_SHORT).show()
                            },
                            onOpenClipboard = {
                                isPinnedNotesPopoverOpen = false
                                closeAllPanelsExcept("clipboard")
                                onToggleClipboardPanel()
                            },
                            onClose = { isPinnedNotesPopoverOpen = false },
                            modifier = Modifier.padding(bottom = 3.dp)
                        )
                    } else if (isImportantNotesPopoverOpen) {
                        ImportantNotesPopoverStrip(
                            notes = importantNotesList,
                            activeStarredNote = activeStarredNote,
                            isDarkTheme = theme.isDark,
                            onSelectNote = { note ->
                                handleKeyInput(note)
                                tempMailManager.setActiveStarredNote(note)
                                activeStarredNote = note
                                isImportantNotesPopoverOpen = false
                            },
                            onSetStarred = { note ->
                                tempMailManager.setActiveStarredNote(note)
                                activeStarredNote = note
                                Toast.makeText(context, "ডিফল্ট স্টার সেট হয়েছে", Toast.LENGTH_SHORT).show()
                            },
                            onDeleteNote = { note ->
                                tempMailManager.deleteImportantNote(note)
                                importantNotesList = tempMailManager.getImportantNotes()
                                activeStarredNote = tempMailManager.getActiveStarredNote()
                                Toast.makeText(context, "নোট ডিলিট হয়েছে", Toast.LENGTH_SHORT).show()
                            },
                            onAddNoteDirectly = { text ->
                                tempMailManager.saveImportantNote(text)
                                tempMailManager.setActiveStarredNote(text)
                                importantNotesList = tempMailManager.getImportantNotes()
                                activeStarredNote = text
                                Toast.makeText(context, "স্টার আইটেম যুক্ত হয়েছে", Toast.LENGTH_SHORT).show()
                            },
                            onClose = { isImportantNotesPopoverOpen = false },
                            modifier = Modifier.padding(bottom = 3.dp)
                        )
                    }

                    KeyboardToolbar(
                        mode = mode,
                        isDarkTheme = theme.isDark,
                        isAppsDrawerOpen = isAppsDrawerOpen,
                        isVoicePanelOpen = isVoiceListening,
                        isClipboardPanelOpen = isClipboardPanelOpen,
                        isBrowserPanelOpen = isBrowserPanelOpen,
                        isTempMailPanelOpen = isTempMailPanelOpen,
                        isNotepadPanelOpen = isNotepadPanelOpen,
                        isSearchPanelOpen = isSearchPanelOpen,
                        isCalcPanelOpen = isCalcPanelOpen,
                        isAiPanelOpen = isAiPanelOpen,
                        isShortcutsPanelOpen = isShortcutsPanelOpen,
                        isStickersPanelOpen = isStickersPanelOpen,
                        isCustomizerOpen = isCustomizerOpen,
                        pinnedToolbarShortcuts = pinnedToolbarShortcuts,
                        onQuickInsertMail = handleQuickInsertMail,
                        onQuickInsertOtp = handleQuickInsertOtp,
                        onInsertLastCopy = handleInsertLastCopy,
                        onInsertAutofillStep = handleInsertAutofillStep,
                        onLongPressAutofill = handleLongPressAutofill,
                        onLongPressClipboard = handleLongPressClipboard,
                        onInsertImportantNote = handleInsertImportantNote,
                        onLongPressImportantNote = handleLongPressImportantNote,
                        onToggleFloatingBrowser = {
                            com.example.moekeyboard.floating.FloatingBrowserService.startOrToggle(context)
                        },
                        latestDetectedOtp = latestDetectedOtp,
                        isGeneratingMail = isGeneratingMail,
                        onToggleAppsDrawer = {
                            val next = !isAppsDrawerOpen
                            closeAllPanelsExcept("apps")
                            isAppsDrawerOpen = next
                        },
                        onToggleVoice = {
                            val next = !isVoiceListening
                            closeAllPanelsExcept("voice")
                            isVoiceListening = next
                        },
                        onToggleClipboard = {
                            closeAllPanelsExcept("clipboard")
                            onToggleClipboardPanel()
                        },
                        onToggleBrowser = {
                            val next = !isBrowserPanelOpen
                            closeAllPanelsExcept("browser")
                            isBrowserPanelOpen = next
                        },
                        onToggleTempMail = {
                            val next = !isTempMailPanelOpen
                            closeAllPanelsExcept("temp_mail")
                            isTempMailPanelOpen = next
                        },
                        onToggleNotepad = {
                            val next = !isNotepadPanelOpen
                            closeAllPanelsExcept("notepad")
                            isNotepadPanelOpen = next
                        },
                        onToggleSearch = {
                            val next = !isSearchPanelOpen
                            closeAllPanelsExcept("search")
                            isSearchPanelOpen = next
                        },
                        onToggleCalculator = {
                            val next = !isCalcPanelOpen
                            closeAllPanelsExcept("calc")
                            isCalcPanelOpen = next
                        },
                        onToggleAiAssistant = {
                            val next = !isAiPanelOpen
                            closeAllPanelsExcept("ai")
                            isAiPanelOpen = next
                        },
                        onToggleTextExpansion = {
                            val next = !isShortcutsPanelOpen
                            closeAllPanelsExcept("shortcuts")
                            isShortcutsPanelOpen = next
                        },
                        onToggleStickersMemes = {
                            val next = !isStickersPanelOpen
                            closeAllPanelsExcept("stickers")
                            isStickersPanelOpen = next
                        },
                        onToggleCustomizer = {
                            val next = !isCustomizerOpen
                            closeAllPanelsExcept("customizer")
                            isCustomizerOpen = next
                        },
                        onToggleEmoji = {
                            closeAllPanelsExcept()
                            if (mode == KeyboardMode.EMOJI) {
                                onModeChange(previousLanguage)
                            } else {
                                onModeChange(KeyboardMode.EMOJI)
                            }
                        },
                        onSwitchLanguage = {
                            val next = if (mode == KeyboardMode.BENGALI) KeyboardMode.ENGLISH else KeyboardMode.BENGALI
                            onModeChange(next)
                        },
                        onOpenSettings = onOpenSettings,
                        onHideKeyboard = { onSpecialKeyClick(SpecialKeyType.HIDE_KEYBOARD) }
                    )

                    val showSuggestions = (mode == KeyboardMode.BENGALI || mode == KeyboardMode.ENGLISH || mode == KeyboardMode.NUMBERS || mode == KeyboardMode.SYMBOLS)
                    if (showSuggestions) {
                        KeyboardSuggestionStrip(
                            suggestions = currentSuggestions,
                            isDarkTheme = theme.isDark,
                            onSelectSuggestion = { suggestion ->
                                if (currentSuggestions.isNotEmpty()) {
                                    val wordInserted = suggestion.trim()
                                    val textToInsert = if (suggestion.endsWith(" ")) suggestion else "$suggestion "
                                    if (onReplacePrefixAndInsert != null) {
                                        onReplacePrefixAndInsert(currentWordPrefix, textToInsert)
                                    } else {
                                        onKeyClick(textToInsert)
                                    }
                                    if (!previousWordText.isNullOrBlank()) {
                                        SmartSuggestionEngine.learnTransition(previousWordText!!, wordInserted, boost = 3)
                                        SmartSuggestionEngine.learnPhrase("${previousWordText!!} $wordInserted", boost = 3)
                                    }
                                    SmartSuggestionEngine.learnWord(wordInserted, boost = 3)
                                    previousWordText = wordInserted
                                    currentWordPrefix = ""
                                } else {
                                    handleKeyInput(suggestion)
                                }
                            },
                            isBengali = (mode == KeyboardMode.BENGALI),
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }
                }
            }

            // (Removed duplicate DraggablePanelContainer calls from here)
            if (isResizePanelOpen) {
                KeyboardResizeOverlay(
                    currentHeightPref = keyboardHeight,
                    isDarkTheme = theme.isDark,
                    syncHeights = syncHeights,
                    bengaliHeight = bengaliRowHeightPref,
                    englishHeight = englishRowHeightPref,
                    onSyncChanged = { keyboardPrefs.setSyncHeights(it) },
                    onBengaliHeightChanged = { keyboardPrefs.setBengaliRowHeight(it) },
                    onEnglishHeightChanged = { keyboardPrefs.setEnglishRowHeight(it) },
                    onHeightSelected = { newHeight ->
                        applyStringHeight(newHeight)
                    },
                    onReset = {
                        applyStringHeight("normal")
                    },
                    onDone = {
                        isResizePanelOpen = false
                    }
                )
            } else if (isShortcutPickerOpen) {
                // In-Keyboard Quick Shortcut Customizer Sheet
                InKeyboardShortcutPicker(
                    isDarkTheme = theme.isDark,
                    pinnedShortcuts = pinnedToolbarShortcuts,
                    onTogglePin = { id -> togglePinShortcut(id) },
                    onClose = { isShortcutPickerOpen = false }
                )
            } else if (isCustomizerOpen) {
                // 3. In-Keyboard Customizer Studio
                InKeyboardCustomizer(
                    isDarkTheme = theme.isDark,
                    height = defaultKeyHeight * 5.4f,
                    onClose = { isCustomizerOpen = false }
                )
            } else if (isAppsDrawerOpen) {
                // 4. In-Keyboard Apps Drawer / Tools Grid
                KeyboardAppsPanel(
                    isDarkTheme = theme.isDark,
                    height = defaultKeyHeight * 5.4f,
                    pinnedToolbarShortcuts = pinnedToolbarShortcuts,
                    onTogglePinShortcut = { id -> togglePinShortcut(id) },
                    onOpenBrowser = {
                        isAppsDrawerOpen = false
                        isBrowserPanelOpen = true
                    },
                    onOpenTempMail = {
                        isAppsDrawerOpen = false
                        isTempMailPanelOpen = true
                    },
                    onOpenVoiceTyping = {
                        isAppsDrawerOpen = false
                        isVoiceListening = true
                    },
                    onOpenClipboard = {
                        isAppsDrawerOpen = false
                        onToggleClipboardPanel()
                    },
                    onOpenEmoji = {
                        isAppsDrawerOpen = false
                        onModeChange(KeyboardMode.EMOJI)
                    },
                    onOpenCalculator = {
                        isAppsDrawerOpen = false
                        isCalcPanelOpen = true
                    },
                    onOpenAiAssistant = {
                        isAppsDrawerOpen = false
                        isAiPanelOpen = true
                    },
                    onOpenTextExpansion = {
                        isAppsDrawerOpen = false
                        isShortcutsPanelOpen = true
                    },
                    onOpenStickersMemes = {
                        isAppsDrawerOpen = false
                        isStickersPanelOpen = true
                    },
                    onOpenNotepad = {
                        isAppsDrawerOpen = false
                        isNotepadPanelOpen = true
                    },
                    onOpenResize = {
                        isAppsDrawerOpen = false
                        isResizePanelOpen = true
                    },
                    onOpenCustomizer = {
                        isAppsDrawerOpen = false
                        isCustomizerOpen = true
                    },
                    onOpenSettings = {
                        isAppsDrawerOpen = false
                        onOpenSettings()
                    },
                    onReturnToKeys = {
                        isAppsDrawerOpen = false
                    },
                    onHideKeyboard = {
                        onSpecialKeyClick(SpecialKeyType.HIDE_KEYBOARD)
                    }
                )
            } else if (isClipboardPanelOpen) {
                // 7. Clipboard Panel
                KeyboardClipboardPanel(
                    items = clipboardItems,
                    isDarkTheme = theme.isDark,
                    onPaste = { text ->
                        onPasteClipboardItem(text)
                        onToggleClipboardPanel()
                    },
                    onDelete = onDeleteClipboardItem,
                    onClearAll = onClearClipboard,
                    onClose = onToggleClipboardPanel,
                    keyHeight = bengaliKeyHeight * 6.5f,
                    fontFamily = LocalKeyboardFont.current
                )
            } else {
                // 8. Main Keyboard Grids (Bengali / English / Numbers / Symbols / Emoji)
                when (mode) {
                    KeyboardMode.EMOJI -> {
                        KeyboardEmojiPanel(
                            isDarkTheme = theme.isDark,
                            onEmojiSelected = ::handleKeyInput,
                            onClose = { onModeChange(previousLanguage) },
                            onBackspace = { onSpecialKeyClick(SpecialKeyType.BACKSPACE) },
                            height = bengaliKeyHeight * 6.5f
                        )
                    }
                    KeyboardMode.BENGALI -> {
                        BengaliKeyGrid(
                            keyHeight = bengaliKeyHeight,
                            textColor = textColor,
                            keyBg = keyBgColor,
                            specialKeyBg = specialKeyBg,
                            accentColor = accentColor,
                            imeAction = imeAction,
                            onKeyClick = ::handleKeyInput,
                            onSpecialClick = ::handleSpecialInput
                        )
                    }
                    KeyboardMode.ENGLISH -> {
                        EnglishKeyGrid(
                            shiftState = shiftState,
                            keyHeight = englishKeyHeight,
                            textColor = textColor,
                            keyBg = keyBgColor,
                            specialKeyBg = specialKeyBg,
                            accentColor = accentColor,
                            imeAction = imeAction,
                            onKeyClick = ::handleKeyInput,
                            onSpecialClick = ::handleSpecialInput
                        )
                    }
                    KeyboardMode.NUMBERS -> {
                        NumbersKeyGrid(
                            isSymbolsPage = false,
                            keyHeight = defaultKeyHeight,
                            textColor = textColor,
                            keyBg = keyBgColor,
                            specialKeyBg = specialKeyBg,
                            accentColor = accentColor,
                            imeAction = imeAction,
                            onKeyClick = ::handleKeyInput,
                            onSpecialClick = ::handleSpecialInput,
                            onSwitchToSymbols = { onModeChange(KeyboardMode.SYMBOLS) },
                            onReturnToLetters = { onModeChange(previousLanguage) }
                        )
                    }
                    KeyboardMode.SYMBOLS -> {
                        NumbersKeyGrid(
                            isSymbolsPage = true,
                            keyHeight = defaultKeyHeight,
                            textColor = textColor,
                            keyBg = keyBgColor,
                            specialKeyBg = specialKeyBg,
                            accentColor = accentColor,
                            imeAction = imeAction,
                            onKeyClick = ::handleKeyInput,
                            onSpecialClick = ::handleSpecialInput,
                            onSwitchToSymbols = { onModeChange(KeyboardMode.NUMBERS) },
                            onReturnToLetters = { onModeChange(previousLanguage) }
                        )
                    }
                }
            }
        }
    }
}
}

private data class ToolbarItemInfo(
    val icon: ImageVector,
    val name: String,
    val color: Color,
    val isOpen: Boolean,
    val onClick: () -> Unit
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun KeyboardToolbar(
    mode: KeyboardMode,
    isDarkTheme: Boolean,
    isAppsDrawerOpen: Boolean,
    isVoicePanelOpen: Boolean,
    isClipboardPanelOpen: Boolean,
    isBrowserPanelOpen: Boolean,
    isTempMailPanelOpen: Boolean,
    isNotepadPanelOpen: Boolean = false,
    isSearchPanelOpen: Boolean = false,
    isCalcPanelOpen: Boolean = false,
    isAiPanelOpen: Boolean = false,
    isShortcutsPanelOpen: Boolean = false,
    isStickersPanelOpen: Boolean = false,
    isCustomizerOpen: Boolean = false,
    pinnedToolbarShortcuts: List<String> = listOf("calculator", "temp_mail"),
    onQuickInsertMail: () -> Unit = {},
    onQuickInsertOtp: () -> Unit = {},
    onInsertLastCopy: () -> Unit = {},
    onInsertAutofillStep: () -> Unit = {},
    onLongPressAutofill: () -> Unit = {},
    onLongPressClipboard: () -> Unit = {},
    onInsertImportantNote: () -> Unit = {},
    onLongPressImportantNote: () -> Unit = {},
    onToggleFloatingBrowser: () -> Unit = {},
    latestDetectedOtp: String? = null,
    isGeneratingMail: Boolean = false,
    onToggleAppsDrawer: () -> Unit,
    onToggleVoice: () -> Unit,
    onToggleClipboard: () -> Unit,
    onToggleBrowser: () -> Unit,
    onToggleTempMail: () -> Unit,
    onToggleNotepad: () -> Unit = {},
    onToggleSearch: () -> Unit = {},
    onToggleCalculator: () -> Unit = {},
    onToggleAiAssistant: () -> Unit = {},
    onToggleTextExpansion: () -> Unit = {},
    onToggleStickersMemes: () -> Unit = {},
    onToggleCustomizer: () -> Unit = {},
    onToggleEmoji: () -> Unit,
    onSwitchLanguage: () -> Unit,
    onOpenSettings: () -> Unit,
    onHideKeyboard: () -> Unit
) {
    val context = LocalContext.current
    val iconTint = if (isDarkTheme) Color(0xFFCFD3DC) else Color(0xFF434752)
    val activeTint = if (isDarkTheme) Color(0xFF8AB4F8) else Color(0xFF1A73E8)
    val micActiveColor = Color(0xFFEA4335)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left section: Apps Drawer Grid
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            IconButton(
                onClick = onToggleAppsDrawer,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Apps,
                    contentDescription = "কিবোর্ড অ্যাপস ও ফিচারস",
                    tint = if (isAppsDrawerOpen) activeTint else iconTint,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Center section: Scrollable shortcuts bar containing all requested tools
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .clip(RoundedCornerShape(14.dp))
                .background(if (isDarkTheme) Color(0xFF22242B) else Color(0xFFE2E6EC))
                .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            // 1. Temp Mail
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(if (isTempMailPanelOpen) Color(0xFF2E7D32) else Color(0xFF0F9D58))
                    .combinedClickable(
                        enabled = !isGeneratingMail,
                        onClick = { onQuickInsertMail() },
                        onLongClick = { onToggleTempMail() }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isGeneratingMail) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 1.8.dp,
                        modifier = Modifier.size(13.dp)
                    )
                } else {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_tool_temp_mail),
                        contentDescription = "টেম্প মেইল",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // 2. Code / OTP
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFF4B400).copy(alpha = 0.22f))
                    .clickable { onQuickInsertOtp() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_tool_otp_code),
                    contentDescription = "কোড/ওটিপি",
                    tint = Color(0xFFF4B400),
                    modifier = Modifier.size(16.dp)
                )
            }

            // 4. Important Note
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE91E63).copy(alpha = 0.22f))
                    .combinedClickable(
                        onClick = { onInsertImportantNote() },
                        onLongClick = { onLongPressImportantNote() }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = "ইম্পর্টেন্ট নোট",
                    tint = Color(0xFFF06292),
                    modifier = Modifier.size(16.dp)
                )
            }

            // 5. Last Copy
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF00BCD4).copy(alpha = 0.22f))
                    .clickable { onInsertLastCopy() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_tool_last_paste),
                    contentDescription = "সর্বশেষ কপি পেস্ট",
                    tint = Color(0xFF4DD0E1),
                    modifier = Modifier.size(16.dp)
                )
            }

            // 4. Autofill
            val autofillManager = remember { SmartCredentialAutofillManager.getInstance(context) }
            var activeAutofillGroup by remember { mutableStateOf(autofillManager.getActiveGroup()) }
            DisposableEffect(Unit) {
                val listener = object : SmartCredentialAutofillManager.AutofillUpdateListener {
                    override fun onAutofillStateChanged() {
                        activeAutofillGroup = autofillManager.getActiveGroup()
                    }
                }
                autofillManager.addListener(listener)
                onDispose { autofillManager.removeListener(listener) }
            }
            val hasActiveAutofill = activeAutofillGroup != null && activeAutofillGroup!!.steps.isNotEmpty()
            val currentStepIdx = activeAutofillGroup?.currentStepIndex ?: 0
            val currentStepNum = currentStepIdx + 1
            val totalSteps = activeAutofillGroup?.steps?.size ?: 3

            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(if (hasActiveAutofill) Color(0xFFFF6D00) else Color(0xFFFF6D00).copy(alpha = 0.22f))
                    .combinedClickable(
                        onClick = { onInsertAutofillStep() },
                        onLongClick = { onLongPressAutofill() }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (hasActiveAutofill) {
                    Text(
                        text = "$currentStepNum/$totalSteps",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace
                    )
                } else {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_tool_smart_autofill),
                        contentDescription = "অটোফিল",
                        tint = Color(0xFFFFB74D),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // 5. Clipboard Icon
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(if (isClipboardPanelOpen) Color(0xFF1A73E8).copy(alpha = 0.25f) else Color.Transparent)
                    .combinedClickable(
                        onClick = { onToggleClipboard() },
                        onLongClick = { onLongPressClipboard() }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_tool_clipboard),
                    contentDescription = "ক্লিপবোর্ড",
                    tint = if (isClipboardPanelOpen) Color(0xFF4285F4) else Color(0xFF1A73E8).copy(alpha = 0.9f),
                    modifier = Modifier.size(16.dp)
                )
            }

            // 6. Floating Mini Window / Bot Browser
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF009688).copy(alpha = 0.22f))
                    .clickable { onToggleFloatingBrowser() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = "ফ্লোটিং উইন্ডো (টেলিগ্রাম / বট)",
                    tint = Color(0xFF4DB6AC),
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Right section: Voice Mic shortcut (Far right)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            IconButton(
                onClick = onToggleVoice,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_tool_voice_typing),
                    contentDescription = "ভয়েস টাইপিং",
                    tint = if (isVoicePanelOpen) micActiveColor else iconTint,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun BengaliKeyGrid(
    keyHeight: Dp,
    textColor: Color,
    keyBg: Color,
    specialKeyBg: Color,
    accentColor: Color,
    imeAction: Int,
    onKeyClick: (String) -> Unit,
    onSpecialClick: (SpecialKeyType) -> Unit
) {
    var activeConsonant by remember { mutableStateOf<String?>(null) }

    val vowels = KeyboardLayouts.BENGALI_VOWELS_ROW
    val kars = KeyboardLayouts.BENGALI_VOWEL_KARS
    val row1 = KeyboardLayouts.BENGALI_ROW1
    val row2 = KeyboardLayouts.BENGALI_ROW2
    val row3 = KeyboardLayouts.BENGALI_ROW3
    val row4 = KeyboardLayouts.BENGALI_ROW4

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Row 0: Top Vowels / Dynamic Kars Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            for (i in 0 until 10) {
                val label = if (activeConsonant == null) {
                    vowels.getOrElse(i) { "" }
                } else {
                    if (i == 0) {
                        activeConsonant!!
                    } else {
                        activeConsonant!! + kars.getOrElse(i) { "" }
                    }
                }

                NormalKey(
                    label = label,
                    textColor = if (activeConsonant != null) accentColor else textColor,
                    bgColor = keyBg,
                    height = keyHeight,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (activeConsonant == null) {
                            onKeyClick(label)
                        } else {
                            val kar = kars.getOrElse(i) { "" }
                            if (kar.isNotEmpty()) {
                                onKeyClick(kar)
                            }
                            activeConsonant = null
                        }
                    }
                )
            }
        }

        // Row 1: ক খ গ ঘ ঙ চ ছ জ ঝ ঞ
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            row1.forEach { char ->
                NormalKey(
                    label = char,
                    textColor = textColor,
                    bgColor = keyBg,
                    height = keyHeight,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onKeyClick(char)
                        activeConsonant = char
                    }
                )
            }
        }

        // Row 2: ট ঠ ড ঢ ণ ত থ দ ধ ন
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            row2.forEach { char ->
                NormalKey(
                    label = char,
                    textColor = textColor,
                    bgColor = keyBg,
                    height = keyHeight,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onKeyClick(char)
                        activeConsonant = char
                    }
                )
            }
        }

        // Row 3: প ফ ব ভ ম য র ল শ ষ
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            row3.forEach { char ->
                NormalKey(
                    label = char,
                    textColor = textColor,
                    bgColor = keyBg,
                    height = keyHeight,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onKeyClick(char)
                        activeConsonant = char
                    }
                )
            }
        }

        // Row 4: স হ ড় ঢ় য় ৎ ্য স্ব ্ + Repeating Backspace
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            row4.forEach { char ->
                NormalKey(
                    label = char,
                    textColor = textColor,
                    bgColor = keyBg,
                    height = keyHeight,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onKeyClick(char)
                        if (char in listOf("স", "হ", "ড়", "ঢ়", "য়")) {
                            activeConsonant = char
                        } else {
                            activeConsonant = null
                        }
                    }
                )
            }

            // Gboard-style continuous repeat Backspace Key
            BackspaceKey(
                bgColor = specialKeyBg,
                tint = textColor,
                height = keyHeight,
                modifier = Modifier.weight(1.15f),
                onBackspace = {
                    activeConsonant = null
                    onSpecialClick(SpecialKeyType.BACKSPACE)
                }
            )
        }

        // Row 5: Bottom Row (?১২৩, 😊, 🌐, বাংলা Space, ।, ↵)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SpecialTextKey(
                label = "?১২৩",
                bgColor = specialKeyBg,
                textColor = textColor,
                height = keyHeight,
                modifier = Modifier.weight(1.35f),
                onClick = {
                    activeConsonant = null
                    onSpecialClick(SpecialKeyType.MODE_SWITCH)
                }
            )

            SpecialIconKey(
                icon = Icons.Outlined.EmojiEmotions,
                text = null,
                bgColor = specialKeyBg,
                tint = textColor,
                height = keyHeight,
                modifier = Modifier.weight(0.9f),
                onLongClick = {
                    activeConsonant = null
                    onKeyClick(",")
                },
                onClick = {
                    activeConsonant = null
                    onSpecialClick(SpecialKeyType.EMOJI)
                }
            )

            SpecialIconKey(
                icon = Icons.Default.Language,
                text = null,
                bgColor = specialKeyBg,
                tint = textColor,
                height = keyHeight,
                modifier = Modifier.weight(0.9f),
                onClick = {
                    activeConsonant = null
                    onSpecialClick(SpecialKeyType.LANGUAGE_SWITCH)
                }
            )

            SpaceKey(
                label = "বাংলা",
                textColor = textColor.copy(alpha = 0.7f),
                bgColor = keyBg,
                height = keyHeight,
                modifier = Modifier.weight(3.8f),
                onClick = {
                    activeConsonant = null
                    onSpecialClick(SpecialKeyType.SPACE)
                }
            )

            SpecialTextKey(
                label = "।",
                bgColor = specialKeyBg,
                textColor = textColor,
                height = keyHeight,
                modifier = Modifier.weight(0.9f),
                onClick = {
                    activeConsonant = null
                    onKeyClick("।")
                }
            )

            EnterKey(
                imeAction = imeAction,
                bgColor = specialKeyBg,
                tint = textColor,
                height = keyHeight,
                modifier = Modifier.weight(1.35f),
                onClick = {
                    activeConsonant = null
                    onSpecialClick(SpecialKeyType.ENTER)
                }
            )
        }
    }
}

@Composable
fun EnglishKeyGrid(
    shiftState: ShiftState,
    keyHeight: Dp,
    textColor: Color,
    keyBg: Color,
    specialKeyBg: Color,
    accentColor: Color,
    imeAction: Int,
    onKeyClick: (String) -> Unit,
    onSpecialClick: (SpecialKeyType) -> Unit
) {
    val isUpper = shiftState != ShiftState.OFF

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Row 0: Numbers 1234567890
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0").forEach { num ->
                NormalKey(
                    label = num,
                    textColor = textColor,
                    bgColor = keyBg,
                    height = keyHeight,
                    modifier = Modifier.weight(1f),
                    onClick = { onKeyClick(num) }
                )
            }
        }

        // Row 1: QWERTYUIOP
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            KeyboardLayouts.ENGLISH_ROW1.forEach { char ->
                val display = if (isUpper) char.uppercase(Locale.US) else char
                NormalKey(
                    label = display,
                    textColor = textColor,
                    bgColor = keyBg,
                    height = keyHeight,
                    modifier = Modifier.weight(1f),
                    onClick = { onKeyClick(display) }
                )
            }
        }

        // Row 2: ASDFGHJKL
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            KeyboardLayouts.ENGLISH_ROW2.forEach { char ->
                val display = if (isUpper) char.uppercase(Locale.US) else char
                NormalKey(
                    label = display,
                    textColor = textColor,
                    bgColor = keyBg,
                    height = keyHeight,
                    modifier = Modifier.weight(1f),
                    onClick = { onKeyClick(display) }
                )
            }
        }

        // Row 3: Shift + ZXCVBNM + Repeating Backspace
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val shiftTint = if (shiftState != ShiftState.OFF) accentColor else textColor
            SpecialIconKey(
                icon = if (shiftState == ShiftState.LOCKED) Icons.Default.Lock else Icons.Default.ArrowUpward,
                text = null,
                bgColor = specialKeyBg,
                tint = shiftTint,
                height = keyHeight,
                modifier = Modifier.weight(1.5f),
                onClick = { onSpecialClick(SpecialKeyType.SHIFT) }
            )

            KeyboardLayouts.ENGLISH_ROW3.forEach { char ->
                val display = if (isUpper) char.uppercase(Locale.US) else char
                NormalKey(
                    label = display,
                    textColor = textColor,
                    bgColor = keyBg,
                    height = keyHeight,
                    modifier = Modifier.weight(1f),
                    onClick = { onKeyClick(display) }
                )
            }

            // Continuous repeating Backspace
            BackspaceKey(
                bgColor = specialKeyBg,
                tint = textColor,
                height = keyHeight,
                modifier = Modifier.weight(1.5f),
                onBackspace = { onSpecialClick(SpecialKeyType.BACKSPACE) }
            )
        }

        // Bottom Row (?123, 😊, Globe, Space, Period, Enter)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SpecialTextKey(
                label = "?123",
                bgColor = specialKeyBg,
                textColor = textColor,
                height = keyHeight,
                modifier = Modifier.weight(1.35f),
                onClick = { onSpecialClick(SpecialKeyType.MODE_SWITCH) }
            )

            SpecialIconKey(
                icon = Icons.Outlined.EmojiEmotions,
                text = null,
                bgColor = specialKeyBg,
                tint = textColor,
                height = keyHeight,
                modifier = Modifier.weight(0.9f),
                onLongClick = { onKeyClick(",") },
                onClick = { onSpecialClick(SpecialKeyType.EMOJI) }
            )

            SpecialIconKey(
                icon = Icons.Default.Language,
                text = null,
                bgColor = specialKeyBg,
                tint = textColor,
                height = keyHeight,
                modifier = Modifier.weight(0.9f),
                onClick = { onSpecialClick(SpecialKeyType.LANGUAGE_SWITCH) }
            )

            SpaceKey(
                label = "English",
                textColor = textColor.copy(alpha = 0.7f),
                bgColor = keyBg,
                height = keyHeight,
                modifier = Modifier.weight(3.8f),
                onClick = { onSpecialClick(SpecialKeyType.SPACE) }
            )

            SpecialTextKey(
                label = ".",
                bgColor = specialKeyBg,
                textColor = textColor,
                height = keyHeight,
                modifier = Modifier.weight(0.9f),
                onClick = { onKeyClick(".") }
            )

            EnterKey(
                imeAction = imeAction,
                bgColor = specialKeyBg,
                tint = textColor,
                height = keyHeight,
                modifier = Modifier.weight(1.35f),
                onClick = { onSpecialClick(SpecialKeyType.ENTER) }
            )
        }
    }
}

@Composable
fun NumbersKeyGrid(
    isSymbolsPage: Boolean,
    keyHeight: Dp,
    textColor: Color,
    keyBg: Color,
    specialKeyBg: Color,
    accentColor: Color,
    imeAction: Int,
    onKeyClick: (String) -> Unit,
    onSpecialClick: (SpecialKeyType) -> Unit,
    onSwitchToSymbols: () -> Unit,
    onReturnToLetters: () -> Unit
) {
    val row1 = if (!isSymbolsPage) KeyboardLayouts.NUMBERS_PAGE_ROW1 else KeyboardLayouts.SYMBOLS_PAGE_ROW1
    val row2 = if (!isSymbolsPage) KeyboardLayouts.NUMBERS_PAGE_ROW2 else KeyboardLayouts.SYMBOLS_PAGE_ROW2
    val extraRow = KeyboardLayouts.NUMBERS_PAGE_EXTRA_ROW
    val row3 = if (!isSymbolsPage) KeyboardLayouts.NUMBERS_PAGE_ROW3 else KeyboardLayouts.SYMBOLS_PAGE_ROW3

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Row 1
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            row1.forEach { char ->
                NormalKey(
                    label = char,
                    textColor = textColor,
                    bgColor = keyBg,
                    height = keyHeight,
                    modifier = Modifier.weight(1f),
                    onClick = { onKeyClick(char) }
                )
            }
        }

        // Row 2
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            row2.forEach { char ->
                NormalKey(
                    label = char,
                    textColor = textColor,
                    bgColor = keyBg,
                    height = keyHeight,
                    modifier = Modifier.weight(1f),
                    onClick = { onKeyClick(char) }
                )
            }
        }

        // New Extra Row (requested: @#&/()?!:)
        if (!isSymbolsPage) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                extraRow.forEach { char ->
                    NormalKey(
                        label = char,
                        textColor = textColor,
                        bgColor = keyBg,
                        height = keyHeight,
                        modifier = Modifier.weight(1f),
                        onClick = { onKeyClick(char) }
                    )
                }
            }
        }

        // Row 3
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SpecialTextKey(
                label = if (!isSymbolsPage) "=\\<" else "?123",
                bgColor = specialKeyBg,
                textColor = textColor,
                height = keyHeight,
                modifier = Modifier.weight(1.5f),
                onClick = onSwitchToSymbols
            )

            row3.forEach { char ->
                NormalKey(
                    label = char,
                    textColor = textColor,
                    bgColor = keyBg,
                    height = keyHeight,
                    modifier = Modifier.weight(1f),
                    onClick = { onKeyClick(char) }
                )
            }

            // Continuous repeating Backspace
            BackspaceKey(
                bgColor = specialKeyBg,
                tint = textColor,
                height = keyHeight,
                modifier = Modifier.weight(1.5f),
                onBackspace = { onSpecialClick(SpecialKeyType.BACKSPACE) }
            )
        }

        // Bottom Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SpecialTextKey(
                label = "ABC",
                bgColor = specialKeyBg,
                textColor = accentColor,
                height = keyHeight,
                modifier = Modifier.weight(1.35f),
                onClick = onReturnToLetters
            )

            SpecialIconKey(
                icon = Icons.Outlined.EmojiEmotions,
                text = null,
                bgColor = specialKeyBg,
                tint = textColor,
                height = keyHeight,
                modifier = Modifier.weight(0.9f),
                onLongClick = { onKeyClick(",") },
                onClick = { onSpecialClick(SpecialKeyType.EMOJI) }
            )

            SpecialIconKey(
                icon = Icons.Default.Language,
                text = null,
                bgColor = specialKeyBg,
                tint = textColor,
                height = keyHeight,
                modifier = Modifier.weight(0.9f),
                onClick = { onSpecialClick(SpecialKeyType.LANGUAGE_SWITCH) }
            )

            SpaceKey(
                label = "",
                textColor = textColor,
                bgColor = keyBg,
                height = keyHeight,
                modifier = Modifier.weight(3.8f),
                onClick = { onSpecialClick(SpecialKeyType.SPACE) }
            )

            SpecialTextKey(
                label = "।",
                bgColor = specialKeyBg,
                textColor = textColor,
                height = keyHeight,
                modifier = Modifier.weight(0.9f),
                onClick = { onKeyClick("।") }
            )

            EnterKey(
                imeAction = imeAction,
                bgColor = specialKeyBg,
                tint = textColor,
                height = keyHeight,
                modifier = Modifier.weight(1.35f),
                onClick = { onSpecialClick(SpecialKeyType.ENTER) }
            )
        }
    }
}

/**
 * Gboard-style continuous repeating Backspace Key
 * Deletes 1 char immediately on tap, and continuously deletes rapidly while held down!
 */
@Composable
fun BackspaceKey(
    bgColor: Color,
    tint: Color,
    height: Dp,
    modifier: Modifier = Modifier,
    onBackspace: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }

    LaunchedEffect(isPressed) {
        if (isPressed) {
            onBackspace() // Initial single delete
            delay(400) // Initial hold threshold before rapid repeat
            while (isPressed) {
                onBackspace() // Rapid continuous delete
                delay(55)
            }
        }
    }

    Box(
        modifier = modifier
            .height(height)
            .shadow(
                elevation = if (isPressed) 0.5.dp else 1.8.dp,
                shape = RoundedCornerShape(8.5.dp),
                clip = false
            )
            .clip(RoundedCornerShape(8.5.dp))
            .background(if (isPressed) bgColor.copy(alpha = 0.8f) else bgColor)
            .border(
                width = 0.5.dp,
                color = Color.White.copy(alpha = 0.12f),
                shape = RoundedCornerShape(8.5.dp)
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        tryAwaitRelease()
                        isPressed = false
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Backspace,
            contentDescription = "Backspace",
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun NormalKey(
    label: String,
    subLabel: String? = null,
    textColor: Color,
    bgColor: Color,
    height: Dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val animatedBg = if (isPressed) bgColor.copy(alpha = 0.82f) else bgColor

    Box(
        modifier = modifier
            .height(height)
            .shadow(
                elevation = 1.dp,
                shape = RoundedCornerShape(8.5.dp),
                clip = false
            )
            .clip(RoundedCornerShape(8.5.dp))
            .background(animatedBg)
            .border(
                width = 0.5.dp,
                color = Color.White.copy(alpha = 0.15f),
                shape = RoundedCornerShape(8.5.dp)
            )
            .clickable(interactionSource = interactionSource, indication = null) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (!subLabel.isNullOrEmpty()) {
                Text(
                    text = subLabel,
                    fontSize = 9.sp,
                    fontFamily = LocalKeyboardFont.current,
                    color = textColor.copy(alpha = 0.65f),
                    lineHeight = 10.sp
                )
            }
            Text(
                text = label,
                fontSize = when {
                    label.length > 3 -> 12.sp
                    label.length > 1 -> 15.sp
                    else -> 21.sp
                },
                fontFamily = LocalKeyboardFont.current,
                fontWeight = FontWeight.SemiBold,
                color = textColor,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun SpaceKey(
    label: String,
    textColor: Color,
    bgColor: Color,
    height: Dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val animatedBg = if (isPressed) bgColor.copy(alpha = 0.82f) else bgColor

    Box(
        modifier = modifier
            .height(height)
            .shadow(
                elevation = 1.dp,
                shape = RoundedCornerShape(8.5.dp),
                clip = false
            )
            .clip(RoundedCornerShape(8.5.dp))
            .background(animatedBg)
            .border(
                width = 0.5.dp,
                color = Color.White.copy(alpha = 0.15f),
                shape = RoundedCornerShape(8.5.dp)
            )
            .clickable(interactionSource = interactionSource, indication = null) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontFamily = LocalKeyboardFont.current,
            fontWeight = FontWeight.Medium,
            color = textColor
        )
    }
}

@Composable
fun SpecialTextKey(
    label: String,
    subLabel: String? = null,
    subIcon: String? = null, // For emoji hint
    showHideIcon: Boolean = false, // For hide keyboard hint
    bgColor: Color,
    textColor: Color,
    height: Dp,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onHideClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val animatedBg = if (isPressed) bgColor.copy(alpha = 0.8f) else bgColor

    Box(
        modifier = modifier
            .height(height)
            .shadow(
                elevation = if (isPressed) 0.5.dp else 1.8.dp,
                shape = RoundedCornerShape(8.5.dp),
                clip = false
            )
            .clip(RoundedCornerShape(8.5.dp))
            .background(animatedBg)
            .border(
                width = 0.5.dp,
                color = Color.White.copy(alpha = 0.12f),
                shape = RoundedCornerShape(8.5.dp)
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = {
                        if (onLongClick != null) {
                            onLongClick()
                        } else {
                            onClick()
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // Emoji subIcon hint (top right)
        if (!subIcon.isNullOrEmpty()) {
            Text(
                text = subIcon,
                fontSize = 11.sp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 4.dp, end = 5.dp)
            )
        }

        // Hide icon hint (bottom center)
        if (showHideIcon) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Hide",
                tint = textColor.copy(alpha = 0.65f),
                modifier = Modifier
                    .size(14.dp)
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 2.dp)
                    .clickable { onHideClick?.invoke() }
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (!subLabel.isNullOrEmpty()) {
                Text(
                    text = subLabel,
                    fontSize = 9.sp,
                    fontFamily = LocalKeyboardFont.current,
                    color = textColor.copy(alpha = 0.65f),
                    lineHeight = 10.sp
                )
            }
            Text(
                text = label,
                fontSize = when {
                    label.length > 3 -> 12.sp
                    label.length > 1 -> 14.sp
                    else -> 19.sp
                },
                fontFamily = LocalKeyboardFont.current,
                fontWeight = FontWeight.Bold,
                color = textColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(
                    top = if (!subIcon.isNullOrEmpty()) 4.dp else 0.dp,
                    bottom = if (showHideIcon) 6.dp else 0.dp
                )
            )
        }
    }
}

@Composable
fun SpecialIconKey(
    icon: ImageVector,
    text: String?,
    bgColor: Color,
    tint: Color,
    height: Dp,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val animatedBg = if (isPressed) bgColor.copy(alpha = 0.8f) else bgColor

    Box(
        modifier = modifier
            .height(height)
            .shadow(
                elevation = if (isPressed) 0.5.dp else 1.8.dp,
                shape = RoundedCornerShape(8.5.dp),
                clip = false
            )
            .clip(RoundedCornerShape(8.5.dp))
            .background(animatedBg)
            .border(
                width = 0.5.dp,
                color = Color.White.copy(alpha = 0.12f),
                shape = RoundedCornerShape(8.5.dp)
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = {
                        if (onLongClick != null) {
                            onLongClick()
                        } else {
                            onClick()
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 2.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = text ?: "key",
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
            if (!text.isNullOrEmpty()) {
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = text,
                    fontSize = 11.sp,
                    fontFamily = LocalKeyboardFont.current,
                    fontWeight = FontWeight.Bold,
                    color = tint
                )
            }
        }
    }
}

@Composable
fun EnterKey(
    imeAction: Int,
    bgColor: Color,
    tint: Color,
    height: Dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val animatedBg = if (isPressed) bgColor.copy(alpha = 0.8f) else bgColor

    val icon = when (imeAction) {
        EditorInfo.IME_ACTION_SEARCH -> Icons.Default.Search
        EditorInfo.IME_ACTION_SEND -> Icons.AutoMirrored.Filled.Send
        EditorInfo.IME_ACTION_GO -> Icons.AutoMirrored.Filled.ArrowForward
        EditorInfo.IME_ACTION_NEXT -> Icons.AutoMirrored.Filled.KeyboardTab
        EditorInfo.IME_ACTION_DONE -> Icons.Default.Check
        else -> Icons.AutoMirrored.Filled.KeyboardReturn
    }

    Box(
        modifier = modifier
            .height(height)
            .shadow(
                elevation = if (isPressed) 0.5.dp else 1.8.dp,
                shape = RoundedCornerShape(8.5.dp),
                clip = false
            )
            .clip(RoundedCornerShape(8.5.dp))
            .background(animatedBg)
            .border(
                width = 0.5.dp,
                color = Color.White.copy(alpha = 0.12f),
                shape = RoundedCornerShape(8.5.dp)
            )
            .clickable(interactionSource = interactionSource, indication = null) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = "Enter",
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun KeyboardClipboardPanel(
    items: List<ClipboardItem>,
    isDarkTheme: Boolean,
    onPaste: (String) -> Unit,
    onDelete: (ClipboardItem) -> Unit,
    onClearAll: () -> Unit,
    onClose: () -> Unit,
    keyHeight: Dp,
    fontFamily: FontFamily,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val database = remember { com.example.moekeyboard.data.db.MoeDatabase.getDatabase(context) }

    val bgColor = if (isDarkTheme) Color(0xFF131418) else Color(0xFFE5E8EC)
    val cardBg = if (isDarkTheme) Color(0xFF1E2028) else Color(0xFFFFFFFF)
    val textColor = if (isDarkTheme) Color(0xFFF3F4F6) else Color(0xFF111827)
    val subTextColor = if (isDarkTheme) Color(0xFF9CA3AF) else Color(0xFF6B7280)
    val iosBlue = Color(0xFF007AFF)
    val pinGold = Color(0xFFFFB300)

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("all") } // "all" or "pinned"
    var showAddNoteDialog by remember { mutableStateOf(false) }
    var customNoteInput by remember { mutableStateOf("") }

    // Separate pinned items into dedicated Pinned Folder vs All (unpinned) items
    val filteredItems = remember(items, searchQuery, selectedFilter) {
        var list = items.sortedByDescending { it.timestamp }
        if (selectedFilter == "pinned") {
            list = list.filter { it.isPinned }
        } else {
            list = list.filter { !it.isPinned }
        }
        if (searchQuery.isNotBlank()) {
            list = list.filter { it.text.contains(searchQuery, ignoreCase = true) }
        }
        list
    }

    val pinnedCount = remember(items) { items.count { it.isPinned } }

    var isFullEditorOpen by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<ClipboardItem?>(null) }
    var fullEditorText by remember { mutableStateOf("") }

    if (isFullEditorOpen) {
        // Full Panel Note Editor (Professional Type)
        Column(
            modifier = modifier
                .fillMaxWidth()
                .height(keyHeight)
                .background(bgColor)
                .padding(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { isFullEditorOpen = false }) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel", tint = textColor)
                }
                Text(
                    text = if (editingItem == null) "নতুন নোট" else "নোট এডিট করুন",
                    fontFamily = LocalKeyboardFont.current,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
                TextButton(onClick = {
                    if (fullEditorText.isNotBlank()) {
                        coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            if (editingItem == null) {
                                database.clipboardDao().insert(ClipboardItem(text = fullEditorText.trim(), isPinned = true))
                            } else {
                                database.clipboardDao().update(editingItem!!.copy(text = fullEditorText.trim()))
                            }
                        }
                        isFullEditorOpen = false
                        fullEditorText = ""
                        editingItem = null
                    }
                }) {
                    Text("সেভ", fontWeight = FontWeight.Bold, color = iosBlue)
                }
            }
            
            OutlinedTextField(
                value = fullEditorText,
                onValueChange = { fullEditorText = it },
                modifier = Modifier.fillMaxSize(),
                placeholder = { Text("এখানে আপনার বিস্তারিত নোট লিখুন...", fontFamily = LocalKeyboardFont.current) },
                textStyle = TextStyle(fontFamily = LocalKeyboardFont.current, color = textColor, fontSize = 14.sp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = iosBlue,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = cardBg,
                    unfocusedContainerColor = cardBg
                )
            )
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .height(keyHeight)
                .background(bgColor)
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
        // Top Toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(if (isDarkTheme) Color(0xFF2B2D36) else Color(0xFFD1D5DB))
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = textColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "ক্লিপবোর্ড ও নোটস",
                        fontSize = 13.sp,
                        fontFamily = LocalKeyboardFont.current,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                    Text(
                        text = "আনলিমিটেড সেভ করা মেসেজ",
                        fontSize = 9.sp,
                        fontFamily = LocalKeyboardFont.current,
                        color = subTextColor
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Add Quick Note button
                Box(
                    modifier = Modifier
                        .height(26.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(iosBlue)
                        .clickable { 
                            editingItem = null
                            fullEditorText = ""
                            isFullEditorOpen = true 
                        }
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add",
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "নোট",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                if (items.isNotEmpty()) {
                    IconButton(
                        onClick = onClearAll,
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Clear All",
                            tint = Color(0xFFFF453A),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Add Note Inline Input Box (removed in favor of full panel editor)

        // Search & Filter Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Search Input Field
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(28.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isDarkTheme) Color(0xFF252732) else Color(0xFFDCDFE5))
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = subTextColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Box(modifier = Modifier.fillMaxWidth()) {
                        if (searchQuery.isEmpty()) {
                            Text("ক্লিপবোর্ডে খুঁজুন...", fontSize = 11.sp, color = subTextColor)
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontSize = 11.sp,
                                color = textColor
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Filter Chips
            Box(
                modifier = Modifier
                    .height(28.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (selectedFilter == "all") iosBlue else (if (isDarkTheme) Color(0xFF252732) else Color(0xFFDCDFE5)))
                    .clickable { selectedFilter = "all" }
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "সব",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (selectedFilter == "all") Color.White else textColor
                )
            }

            Box(
                modifier = Modifier
                    .height(28.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (selectedFilter == "pinned") pinGold else (if (isDarkTheme) Color(0xFF252732) else Color(0xFFDCDFE5)))
                    .clickable { selectedFilter = "pinned" }
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "📌 পিন ($pinnedCount)",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (selectedFilter == "pinned") Color.Black else textColor
                    )
                }
            }
        }

        // Clipboard List Items
        if (filteredItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.ContentPasteOff,
                        contentDescription = "Empty",
                        tint = subTextColor.copy(alpha = 0.5f),
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (searchQuery.isNotBlank()) "কোনো রেজাল্ট পাওয়া যায়নি" else "ক্লিপবোর্ডে কোনো সেভ করা টেক্সট নেই",
                        fontSize = 11.sp,
                        color = subTextColor
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(filteredItems, key = { it.id }) { item ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(
                                elevation = 1.5.dp,
                                shape = RoundedCornerShape(10.dp),
                                clip = false
                            )
                            .clip(RoundedCornerShape(10.dp))
                            .border(
                                width = if (item.isPinned) 1.dp else 0.5.dp,
                                color = if (item.isPinned) pinGold else Color.White.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { onPaste(item.text) },
                        color = cardBg
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            if (item.isPinned) {
                                Box(
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(pinGold.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PushPin,
                                        contentDescription = "Pinned",
                                        tint = pinGold,
                                        modifier = Modifier.size(11.dp)
                                    )
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.text,
                                    fontSize = 12.sp,
                                    fontFamily = LocalKeyboardFont.current,
                                    color = textColor,
                                    fontWeight = if (item.isPinned) FontWeight.SemiBold else FontWeight.Normal,
                                    maxLines = 4,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Edit action
                                IconButton(
                                    onClick = {
                                        editingItem = item
                                        fullEditorText = item.text
                                        isFullEditorOpen = true
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit",
                                        tint = subTextColor.copy(alpha = 0.7f),
                                        modifier = Modifier.size(15.dp)
                                    )
                                }

                                // Pin/Unpin action
                                IconButton(
                                    onClick = {
                                        coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                            database.clipboardDao().update(item.copy(isPinned = !item.isPinned))
                                        }
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PushPin,
                                        contentDescription = "Toggle Pin",
                                        tint = if (item.isPinned) pinGold else subTextColor.copy(alpha = 0.5f),
                                        modifier = Modifier.size(15.dp)
                                    )
                                }

                                 // Delete action
                                IconButton(
                                    onClick = { onDelete(item) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Delete",
                                        tint = subTextColor.copy(alpha = 0.7f),
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
}
