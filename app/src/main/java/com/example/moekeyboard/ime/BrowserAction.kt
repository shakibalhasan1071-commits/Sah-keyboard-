package com.example.moekeyboard.ime

sealed class BrowserAction {
    data class TypeChar(val char: String) : BrowserAction()
    object Backspace : BrowserAction()
    object Enter : BrowserAction()
}
