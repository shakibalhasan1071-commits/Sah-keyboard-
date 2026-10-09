package com.example.moekeyboard.data.prefs

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class KeyboardPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("moe_keyboard_prefs", Context.MODE_PRIVATE)

    companion object {
        const val KEY_THEME = "theme_mode" // "system", "light", "dark", "amoled", "ocean_blue", "neon_cyber", "lavender", "forest"
        const val KEY_KEYBOARD_HEIGHT = "keyboard_height" // "extra_small", "small", "normal", "medium", "tall", "extra_tall"
        const val KEY_VIBRATION = "vibration_enabled"
        const val KEY_SOUND = "sound_enabled"
        const val KEY_AUTO_CAPS = "auto_caps"
        const val KEY_CLIPBOARD_ENABLED = "clipboard_history_enabled"
        const val KEY_SEARCH_ENGINE = "search_engine" // "Google", "DuckDuckGo", "Bing"
        const val KEY_SHOW_NUMBER_ROW = "show_number_row"
        const val KEY_WORD_SUGGESTIONS = "word_suggestions_enabled"
        const val KEY_AUTO_CORRECT = "auto_correct_enabled"
        const val KEY_CUSTOM_FONT_PATH = "custom_font_path"

        @Volatile
        private var INSTANCE: KeyboardPreferences? = null

        fun getInstance(context: Context): KeyboardPreferences {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: KeyboardPreferences(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val _theme = MutableStateFlow(prefs.getString(KEY_THEME, "system") ?: "system")
    val theme: StateFlow<String> = _theme.asStateFlow()

    private val _height = MutableStateFlow(prefs.getString(KEY_KEYBOARD_HEIGHT, "normal") ?: "normal")
    val height: StateFlow<String> = _height.asStateFlow()

    private val _vibration = MutableStateFlow(prefs.getBoolean(KEY_VIBRATION, true))
    val vibration: StateFlow<Boolean> = _vibration.asStateFlow()

    private val _sound = MutableStateFlow(prefs.getBoolean(KEY_SOUND, false))
    val sound: StateFlow<Boolean> = _sound.asStateFlow()

    private val _autoCaps = MutableStateFlow(prefs.getBoolean(KEY_AUTO_CAPS, true))
    val autoCaps: StateFlow<Boolean> = _autoCaps.asStateFlow()

    private val _clipboardEnabled = MutableStateFlow(prefs.getBoolean(KEY_CLIPBOARD_ENABLED, true))
    val clipboardEnabled: StateFlow<Boolean> = _clipboardEnabled.asStateFlow()

    private val _showNumberRow = MutableStateFlow(prefs.getBoolean(KEY_SHOW_NUMBER_ROW, true))
    val showNumberRow: StateFlow<Boolean> = _showNumberRow.asStateFlow()

    private val _wordSuggestions = MutableStateFlow(prefs.getBoolean(KEY_WORD_SUGGESTIONS, true))
    val wordSuggestions: StateFlow<Boolean> = _wordSuggestions.asStateFlow()

    private val _autoCorrect = MutableStateFlow(prefs.getBoolean(KEY_AUTO_CORRECT, true))
    val autoCorrect: StateFlow<Boolean> = _autoCorrect.asStateFlow()

    private val _customFontPath = MutableStateFlow(prefs.getString(KEY_CUSTOM_FONT_PATH, null))
    val customFontPath: StateFlow<String?> = _customFontPath.asStateFlow()

    private val _searchEngine = MutableStateFlow(prefs.getString(KEY_SEARCH_ENGINE, "Google") ?: "Google")
    val searchEngine: StateFlow<String> = _searchEngine.asStateFlow()

    private val _syncHeights = MutableStateFlow(prefs.getBoolean("sync_heights", true))
    val syncHeights: StateFlow<Boolean> = _syncHeights.asStateFlow()

    private val _bengaliRowHeight = MutableStateFlow(prefs.getFloat("bengali_row_height", 38f))
    val bengaliRowHeight: StateFlow<Float> = _bengaliRowHeight.asStateFlow()

    private val _englishRowHeight = MutableStateFlow(prefs.getFloat("english_row_height", 48f))
    val englishRowHeight: StateFlow<Float> = _englishRowHeight.asStateFlow()

    fun setTheme(mode: String) {
        prefs.edit().putString(KEY_THEME, mode).apply()
        _theme.value = mode
    }

    fun setHeight(height: String) {
        prefs.edit().putString(KEY_KEYBOARD_HEIGHT, height).apply()
        _height.value = height
    }

    fun setVibration(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_VIBRATION, enabled).apply()
        _vibration.value = enabled
    }

    fun setSound(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SOUND, enabled).apply()
        _sound.value = enabled
    }

    fun setAutoCaps(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_CAPS, enabled).apply()
        _autoCaps.value = enabled
    }

    fun setClipboardEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CLIPBOARD_ENABLED, enabled).apply()
        _clipboardEnabled.value = enabled
    }

    fun setShowNumberRow(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_NUMBER_ROW, enabled).apply()
        _showNumberRow.value = enabled
    }

    fun setWordSuggestions(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WORD_SUGGESTIONS, enabled).apply()
        _wordSuggestions.value = enabled
    }

    fun setAutoCorrect(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_CORRECT, enabled).apply()
        _autoCorrect.value = enabled
    }

    fun setSearchEngine(engine: String) {
        prefs.edit().putString(KEY_SEARCH_ENGINE, engine).apply()
        _searchEngine.value = engine
    }

    fun setSyncHeights(sync: Boolean) {
        prefs.edit().putBoolean("sync_heights", sync).apply()
        _syncHeights.value = sync
    }

    fun setBengaliRowHeight(height: Float) {
        prefs.edit().putFloat("bengali_row_height", height).apply()
        _bengaliRowHeight.value = height
    }

    fun setEnglishRowHeight(height: Float) {
        prefs.edit().putFloat("english_row_height", height).apply()
        _englishRowHeight.value = height
    }

    fun setCustomFontPath(path: String?) {
        prefs.edit().putString(KEY_CUSTOM_FONT_PATH, path).apply()
        _customFontPath.value = path
    }
}
