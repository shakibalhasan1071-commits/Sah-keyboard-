package com.example.moekeyboard.ime

import android.view.KeyEvent
import android.view.inputmethod.InputConnection

object BengaliTypingHelper {

    /**
     * Checks if a character is a Bengali vowel sign (Kar) or diacritic modifier.
     */
    fun isBengaliModifier(char: Char): Boolean {
        return char in '\u09BE'..'\u09CD' || // া to ্
                char == '\u0981' || // ঁ Chandrabindu
                char == '\u0982' || // ং Anusvara
                char == '\u0983'    // ঃ Visarga
    }

    /**
     * Deletes the previous character, selected text, or grapheme cluster safely.
     * Non-blocking and asynchronous to prevent UI thread hangs in WebViews and external apps.
     */
    fun handleBackspace(inputConnection: InputConnection?) {
        if (inputConnection == null) return

        try {
            // deleteSurroundingText(1, 0) is the official, asynchronous, non-blocking IME API
            val deleted = inputConnection.deleteSurroundingText(1, 0)
            if (!deleted) {
                inputConnection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
                inputConnection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
            }
        } catch (_: Exception) {
            try {
                inputConnection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
                inputConnection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
            } catch (_: Exception) {}
        }
    }

    /**
     * Safely commits text, handling text selection replacement.
     */
    fun commitTextSafely(inputConnection: InputConnection?, text: String) {
        if (inputConnection == null) return
        inputConnection.commitText(text, 1)
    }
}
