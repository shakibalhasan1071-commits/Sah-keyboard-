package com.example.moekeyboard.ime

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.lifecycleScope
import com.example.MainActivity
import com.example.moekeyboard.data.db.ClipboardItem
import com.example.moekeyboard.data.db.MoeDatabase
import com.example.moekeyboard.data.prefs.KeyboardPreferences
import com.example.moekeyboard.floating.FloatingBrowserService
import com.example.moekeyboard.tempmail.TempMailManager
import com.example.moekeyboard.ui.MiniBrowserActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MoeInputMethodService : LifecycleInputMethodService() {

    private lateinit var database: MoeDatabase
    private lateinit var prefs: KeyboardPreferences
    private var clipboardManager: ClipboardManager? = null
    private var audioManager: AudioManager? = null
    private var vibrator: Vibrator? = null

    // Keyboard state flows
    private val keyboardModeState = mutableStateOf(KeyboardMode.BENGALI)
    private val bengaliPageState = mutableStateOf(BengaliPage.PAGE_1)
    private val shiftState = mutableStateOf(ShiftState.OFF)
    private var previousLanguageMode = KeyboardMode.BENGALI
    private val isClipboardPanelOpenState = mutableStateOf(false)
    private val clipboardItemsState = mutableStateOf<List<ClipboardItem>>(emptyList())
    private val customFontPathState = mutableStateOf<String?>(null)
    private val inputSessionId = mutableStateOf(0)

    private var lastCopiedTextInMemory: String? = null

    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        checkAndRecordClipboard(currentInputEditorInfo, isDirectClipEvent = true)
    }

    override fun onCreate() {
        super.onCreate()
        SmartSuggestionEngine.init(this)
        TempMailWebBridge.init(this)
        database = MoeDatabase.getDatabase(this)
        prefs = KeyboardPreferences.getInstance(this)
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        try {
            clipboardManager?.addPrimaryClipChangedListener(clipboardListener)
        } catch (_: Exception) {}
        audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager

        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

        lifecycleScope.launch {
            database.clipboardDao().getAllItems().collectLatest { items ->
                clipboardItemsState.value = items
            }
        }

        lifecycleScope.launch {
            prefs.customFontPath.collectLatest { path ->
                customFontPathState.value = path
            }
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val mgr = TempMailManager.getInstance(this@MoeInputMethodService)
                if (mgr.currentEmail.isBlank() || mgr.currentToken.isBlank()) {
                    mgr.generateNewAccount()
                }
            } catch (_: Exception) {}
        }
    }

    override fun onCreateInputView(): View {
        val composeView = ComposeView(this)
        setupComposeTreeOwners(composeView)
        composeView.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool
        )

        composeView.setContent {
            val systemDark = isSystemInDarkTheme()
            val themePref by prefs.theme.collectAsState()
            val isDark = when (themePref) {
                "light", "lavender" -> false
                "dark", "amoled", "ocean_blue", "neon_cyber", "forest" -> true
                else -> systemDark
            }

            val keyboardHeight by prefs.height.collectAsState()
            val mode by keyboardModeState
            val bPage by bengaliPageState
            val shift by shiftState
            val isClipOpen by isClipboardPanelOpenState
            val clips by clipboardItemsState
            val customFontPath by customFontPathState
            val sessionId by inputSessionId

            val imeAction = currentInputEditorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)
                ?: EditorInfo.IME_ACTION_NONE

            MoeKeyboardView(
                mode = mode,
                bengaliPage = bPage,
                shiftState = shift,
                themeMode = themePref,
                isDarkTheme = isDark,
                keyboardHeight = keyboardHeight,
                imeAction = imeAction,
                clipboardItems = clips,
                isClipboardPanelOpen = isClipOpen,
                onKeyClick = { text -> handleRegularKey(text) },
                onSpecialKeyClick = { type -> handleSpecialKey(type) },
                onReplacePrefixAndInsert = { prefix, replacement ->
                    replacePrefixAndInsert(prefix, replacement)
                },
                onToggleClipboardPanel = {
                    isClipboardPanelOpenState.value = !isClipboardPanelOpenState.value
                },
                onOpenBrowser = { launchBrowser() },
                onOpenSettings = { launchSettings() },
                onModeChange = { newMode ->
                    if (newMode == KeyboardMode.BENGALI || newMode == KeyboardMode.ENGLISH) previousLanguageMode = newMode
                    keyboardModeState.value = newMode
                    isClipboardPanelOpenState.value = false
                },
                onBengaliPageToggle = {
                    bengaliPageState.value = if (bengaliPageState.value == BengaliPage.PAGE_1) {
                        BengaliPage.PAGE_2
                    } else {
                        BengaliPage.PAGE_1
                    }
                },
                onPasteClipboardItem = { text ->
                    commitText(text)
                },
                onDeleteClipboardItem = { item ->
                    lifecycleScope.launch(Dispatchers.IO) {
                        database.clipboardDao().delete(item)
                    }
                },
                onClearClipboard = {
                    lifecycleScope.launch(Dispatchers.IO) {
                        database.clipboardDao().clearUnpinned()
                    }
                },
                customFontPath = customFontPath,
                inputSessionId = sessionId
            )
        }

        return composeView
    }

    override fun onEvaluateInputViewShown(): Boolean {
        super.onEvaluateInputViewShown()
        return true
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        inputSessionId.value += 1
        // Reset shift if starting new field unless caps lock
        if (shiftState.value == ShiftState.ONCE) {
            shiftState.value = ShiftState.OFF
        }
        isClipboardPanelOpenState.value = false

        // Automatically yield floating window focus when user begins input in another Android app
        if (info?.packageName != null && info.packageName != packageName) {
            FloatingBrowserService.onExternalAppInputStarted()
        }

        // Check clipboard safely when input view opens
        checkAndRecordClipboard(info, isDirectClipEvent = false)
    }

    private fun checkAndRecordClipboard(info: EditorInfo?, isDirectClipEvent: Boolean = false) {
        // Privacy protection: Never inspect or save clipboard for password fields!
        if (info != null) {
            val variation = info.inputType and InputType.TYPE_MASK_VARIATION
            if (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
            ) {
                return
            }
        }

        if (!prefs.clipboardEnabled.value) return

        try {
            val clip = clipboardManager?.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val firstText = clip.getItemAt(0)?.coerceToText(this)?.toString()?.trim()
                val isNewCopy = isDirectClipEvent || (firstText != null && firstText != lastCopiedTextInMemory)
                if (!firstText.isNullOrEmpty()) {
                    lastCopiedTextInMemory = firstText
                }
                for (i in 0 until clip.itemCount) {
                    val text = clip.getItemAt(i)?.coerceToText(this)?.toString()?.trim()
                    if (!text.isNullOrEmpty()) {
                        // Automatically check & parse multi-step credentials (e.g. First name, Login, Password)
                        try {
                            SmartCredentialAutofillManager.getInstance(this@MoeInputMethodService)
                                .processCopiedText(text, isManualImport = false, isNewCopy = isNewCopy)
                        } catch (_: Exception) {}

                        SmartSuggestionEngine.learnWordsFromText(text)
                        lifecycleScope.launch(Dispatchers.IO) {
                            val existing = database.clipboardDao().findByText(text)
                            val now = System.currentTimeMillis()
                            if (existing == null) {
                                database.clipboardDao().insert(ClipboardItem(text = text, timestamp = now))
                            } else {
                                database.clipboardDao().update(existing.copy(timestamp = now))
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Clipboard access might be restricted on some Android versions when not in focus
        }
    }

    private fun handleRegularKey(text: String) {
        commitText(text)

        // Reset one-time shift state after entering letter
        if (shiftState.value == ShiftState.ONCE) {
            shiftState.value = ShiftState.OFF
        }
        performFeedback()
    }

    private fun handleSpecialKey(type: SpecialKeyType) {
        performFeedback()

        when (type) {
            SpecialKeyType.BACKSPACE -> {
                val ic = currentInputConnection ?: return
                BengaliTypingHelper.handleBackspace(ic)
            }
            SpecialKeyType.ENTER -> {
                val ic = currentInputConnection ?: return
                val beforeText = ic.getTextBeforeCursor(40, 0)?.toString() ?: ""
                val lastWord = beforeText.split(" ", "\n", "\t").lastOrNull() ?: ""
                if (lastWord.isNotBlank()) {
                    SmartSuggestionEngine.learnWord(lastWord)
                }
                val action = currentInputEditorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)
                    ?: EditorInfo.IME_ACTION_NONE
                if (action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
                    ic.performEditorAction(action)
                } else {
                    ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                    ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
                }
            }
            SpecialKeyType.SPACE -> {
                val ic = currentInputConnection ?: return
                val beforeText = ic.getTextBeforeCursor(40, 0)?.toString() ?: ""
                val lastWord = beforeText.split(" ", "\n", "\t").lastOrNull() ?: ""
                if (lastWord.isNotEmpty()) {
                    if (prefs.autoCorrect.value) {
                        val corrected = SmartSuggestionEngine.checkAutoCorrection(lastWord)
                        if (corrected != null) {
                            ic.deleteSurroundingText(lastWord.length, 0)
                            BengaliTypingHelper.commitTextSafely(ic, corrected + " ")
                            SmartSuggestionEngine.learnWord(corrected, boost = 2)
                            return
                        }
                    }
                    SmartSuggestionEngine.learnWord(lastWord)
                }
                ic.commitText(" ", 1)
            }
            SpecialKeyType.SHIFT -> {
                shiftState.value = when (shiftState.value) {
                    ShiftState.OFF -> ShiftState.ONCE
                    ShiftState.ONCE -> ShiftState.LOCKED
                    ShiftState.LOCKED -> ShiftState.OFF
                }
            }
            SpecialKeyType.MODE_SWITCH -> {
                keyboardModeState.value = if (keyboardModeState.value == KeyboardMode.NUMBERS || keyboardModeState.value == KeyboardMode.SYMBOLS) {
                    previousLanguageMode
                } else {
                    KeyboardMode.NUMBERS
                }
            }
            SpecialKeyType.LANGUAGE_SWITCH -> {
                val nextMode = if (keyboardModeState.value == KeyboardMode.BENGALI) KeyboardMode.ENGLISH else KeyboardMode.BENGALI
                previousLanguageMode = nextMode
                keyboardModeState.value = nextMode
            }
            SpecialKeyType.EMOJI -> {
                keyboardModeState.value = if (keyboardModeState.value == KeyboardMode.EMOJI) {
                    previousLanguageMode
                } else {
                    KeyboardMode.EMOJI
                }
            }
            SpecialKeyType.BENGALI_PAGE_TOGGLE -> {
                bengaliPageState.value = if (bengaliPageState.value == BengaliPage.PAGE_1) {
                    BengaliPage.PAGE_2
                } else {
                    BengaliPage.PAGE_1
                }
            }
            SpecialKeyType.HIDE_KEYBOARD -> {
                requestHideSelf(0)
            }
            SpecialKeyType.MOVE_CURSOR_LEFT -> {
                val ic = currentInputConnection ?: return
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT))
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_LEFT))
            }
            SpecialKeyType.MOVE_CURSOR_RIGHT -> {
                val ic = currentInputConnection ?: return
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT))
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT))
            }
        }
    }

    private fun commitText(text: String) {
        val ic = currentInputConnection ?: return
        BengaliTypingHelper.commitTextSafely(ic, text)
    }

    private fun replacePrefixAndInsert(prefix: String, replacement: String) {
        val ic = currentInputConnection ?: return
        if (prefix.isNotEmpty()) {
            ic.deleteSurroundingText(prefix.length, 0)
        }
        BengaliTypingHelper.commitTextSafely(ic, replacement)
        SmartSuggestionEngine.learnWord(replacement.trim(), boost = 3)
        performFeedback()
    }

    private fun performFeedback() {
        // Haptic feedback
        if (prefs.vibration.value) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(
                        VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(20)
                }
            } catch (_: Exception) {}
        }

        // Sound feedback
        if (prefs.sound.value) {
            try {
                audioManager?.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, 0.5f)
            } catch (_: Exception) {}
        }
    }

    private fun launchBrowser() {
        val intent = Intent(this, MiniBrowserActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
    }

    private fun launchSettings() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra("initial_tab", "settings")
        }
        startActivity(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            clipboardManager?.removePrimaryClipChangedListener(clipboardListener)
        } catch (_: Exception) {}
    }
}
