package com.example.moekeyboard.ime

enum class KeyboardMode {
    BENGALI,
    ENGLISH,
    NUMBERS,
    SYMBOLS,
    EMOJI
}

enum class BengaliPage {
    PAGE_1, // Primary consonants
    PAGE_2  // Vowels and secondary letters
}

enum class ShiftState {
    OFF,
    ONCE,
    LOCKED
}

data class KeyAction(
    val label: String,
    val subLabel: String? = null,
    val commitText: String = label,
    val isSpecial: Boolean = false,
    val specialType: SpecialKeyType? = null,
    val weight: Float = 1.0f
)

enum class SpecialKeyType {
    SHIFT,
    BACKSPACE,
    ENTER,
    SPACE,
    MODE_SWITCH,
    LANGUAGE_SWITCH,
    EMOJI,
    BENGALI_PAGE_TOGGLE,
    HIDE_KEYBOARD,
    MOVE_CURSOR_LEFT,
    MOVE_CURSOR_RIGHT
}

object KeyboardLayouts {
    // Exact Gboard Bengali Layout (as in user screenshot)
    // Row 0: Top Vowels / Dynamic Kars row (10 keys)
    val BENGALI_VOWELS_ROW = listOf("অ", "আ", "ই", "ঈ", "উ", "ঊ", "এ", "ঐ", "ও", "ঔ")
    val BENGALI_VOWEL_KARS = listOf("", "া", "ি", "ী", "ু", "ূ", "ে", "ৈ", "ো", "ৌ")

    // Row 1: Consonants 1-10 (10 keys)
    val BENGALI_ROW1 = listOf("ক", "খ", "গ", "ঘ", "ঙ", "চ", "ছ", "জ", "ঝ", "ঞ")

    // Row 2: Consonants 11-20 (10 keys)
    val BENGALI_ROW2 = listOf("ট", "ঠ", "ড", "ঢ", "ণ", "ত", "থ", "দ", "ধ", "ন")

    // Row 3: Consonants 21-30 (10 keys)
    val BENGALI_ROW3 = listOf("প", "ফ", "ব", "ভ", "ম", "য", "র", "ল", "শ", "ষ")

    // Row 4: Consonants 31-39 + Backspace (9 keys + Backspace = 10 keys)
    val BENGALI_ROW4 = listOf("স", "হ", "ড়", "ঢ়", "য়", "ৎ", "্য", "স্ব", "্")

    // Aliases for backwards compatibility
    val BENGALI_KARS_STRIP = BENGALI_VOWEL_KARS.filter { it.isNotEmpty() }
    val BENGALI_P1_ROW1 = BENGALI_ROW1
    val BENGALI_P1_ROW2 = BENGALI_ROW2
    val BENGALI_P1_ROW3 = BENGALI_ROW3
    val BENGALI_P2_ROW1 = BENGALI_VOWELS_ROW
    val BENGALI_P2_ROW2 = BENGALI_ROW3
    val BENGALI_P2_ROW3 = BENGALI_ROW4

    // English QWERTY
    val ENGLISH_ROW1 = listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p")
    val ENGLISH_ROW2 = listOf("a", "s", "d", "f", "g", "h", "j", "k", "l")
    val ENGLISH_ROW3 = listOf("z", "x", "c", "v", "b", "n", "m")

    // Numbers & Bengali Digits
    val NUMBERS_ROW_EN = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
    val NUMBERS_ROW_BN = listOf("১", "২", "৩", "৪", "৫", "৬", "৭", "৮", "৯", "০")

    // Primary Symbols & Numbers Page (Exact match to Screenshot 2)
    val SYMBOLS_PAGE1_ROW1 = listOf("@", "#", "৳", "_", "&", "-", "+", "(", ")", "/")
    val SYMBOLS_PAGE1_ROW2 = listOf("*", "\"", "'", ":", ";", "!", "?")

    // Secondary Extended Symbols Page (=/<)
    val SYMBOLS_PAGE2_ROW1 = listOf("~", "`", "|", "•", "√", "π", "÷", "×", "§", "∆")
    val SYMBOLS_PAGE2_ROW2 = listOf("£", "€", "¥", "¢", "^", "°", "=", "{", "}")
    val SYMBOLS_PAGE2_ROW3 = listOf("\\", "%", "©", "®", "™", "[", "]", "<", ">")

    // English / Bengali numbers page
    val NUMBERS_PAGE_ROW1 = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
    val NUMBERS_PAGE_ROW2 = listOf("১", "২", "৩", "৪", "৫", "৬", "৭", "৮", "৯", "০")
    val NUMBERS_PAGE_EXTRA_ROW = listOf("@", "#", "&", "/", "(", ")", "?", "!", ":")
    val NUMBERS_PAGE_ROW3 = listOf("+", "-", "×", "÷", "=", "%", "/", "$", "৳", "€")

    val SYMBOLS_PAGE_ROW1 = SYMBOLS_PAGE1_ROW1
    val SYMBOLS_PAGE_ROW2 = SYMBOLS_PAGE1_ROW2
    val SYMBOLS_PAGE_ROW3 = SYMBOLS_PAGE2_ROW3

    // Emojis grouped by categories
    val EMOJI_CATEGORIES = mapOf(
        "Smileys" to listOf(
            "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣", "🥲", "☺️", "😊", "😇",
            "🙂", "🙃", "😉", "😌", "😍", "🥰", "😘", "😗", "😙", "😚", "😋", "😛",
            "😜", "🤪", "😝", "🤑", "🤗", "🤭", "🤫", "🤔", "🤐", "🤨", "😐", "😑",
            "😶", "😏", "😒", "🙄", "😬", "🤥", "😌", "😔", "😪", "🤤", "😴", "😷",
            "🤒", "🤕", "🤢", "🤮", "🤧", "🥵", "🥶", "🥴", "😵", "🤯", "🤠", "🥳",
            "😎", "🤓", "🧐", "😕", "😟", "🙁", "😮", "😯", "😲", "😳", "🥺", "😦",
            "😧", "😨", "😰", "😥", "😢", "😭", "😱", "😖", "😣", "😞", "😓", "😩",
            "😫", "🥱", "😤", "😡", "😠", "🤬", "😈", "👿", "💀", "☠️", "💩", "🤡"
        ),
        "Gestures & Hearts" to listOf(
            "👋", "🤚", "🖐️", "✋", "🖖", "👌", "🤌", "🤏", "✌️", "🤞", "🫰", "🤟",
            "🤘", "🤙", "👈", "👉", "👆", "🖕", "👇", "☝️", "👍", "👎", "✊", "👊",
            "🤛", "🤜", "👏", "🙌", "👐", "🤲", "🤝", "🙏", "✍️", "💅", "🤳", "💪",
            "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "🤎", "💔", "❣️", "💕",
            "💞", "💓", "💗", "💖", "💘", "💝", "❤️‍🔥", "❤️‍🩹", "💋", "💯", "💢", "💥"
        ),
        "Objects & Nature" to listOf(
            "🌸", "🌺", "🌹", "🌷", "🌻", "🌼", "💐", "🌿", "🍀", "🍁", "🍂", "🍃",
            "🔥", "✨", "🌟", "⭐", "🌈", "☀️", "🌙", "☁️", "⚡", "❄️", "🌊", "🎉",
            "🎊", "🎁", "🎈", "🏆", "🥇", "📱", "💻", "💡", "💰", "💵", "✉️", "📦",
            "🇧🇩", "🇮🇳", "🇺🇸", "🇬🇧", "🇨🇦", "🇦🇺", "🇯🇵", "🇸🇦", "🇵🇰", "🇦🇪"
        )
    )
}
