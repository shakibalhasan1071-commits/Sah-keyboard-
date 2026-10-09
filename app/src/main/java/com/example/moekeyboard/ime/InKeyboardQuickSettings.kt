package com.example.moekeyboard.ime

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.security.SecureRandom

@Composable
fun InKeyboardQuickSettings(
    isDarkTheme: Boolean,
    height: Dp,
    onClose: () -> Unit,
    onRegisterInputHandler: (((char: String?, specialKey: SpecialKeyType?) -> Unit)?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("moe_keyboard_prefs", Context.MODE_PRIVATE) }

    // Tab 0: 🔑 পাসওয়ার্ড, Tab 1: 📧 ইমেল, Tab 2: 📥 বাল্ক ইমপোর্ট, Tab 3: ⚡ ৩-ধাপ অটোফিল
    var selectedTab by remember { mutableStateOf(0) }

    // Search queries
    var passwordSearchQuery by remember { mutableStateOf("") }
    var emailSearchQuery by remember { mutableStateOf("") }
    var emailFilterStatus by remember { mutableStateOf("all") } // "all", "fresh", "used"

    // Password State
    var newPasswordInput by remember { mutableStateOf("") }
    var isNewPasswordVisible by remember { mutableStateOf(false) }
    var showPasswordGeneratorSheet by remember { mutableStateOf(false) }
    var editingPasswordOriginal by remember { mutableStateOf<String?>(null) }
    var editingPasswordInput by remember { mutableStateOf("") }

    var savedPasswordsList by remember {
        val raw = prefs.getString("saved_user_passwords_list", "") ?: ""
        val list = if (raw.isNotBlank()) {
            raw.split("|||").map { it.trim() }.filter { it.isNotBlank() }
        } else {
            val oldSingle = prefs.getString("quick_saved_password", "") ?: ""
            if (oldSingle.isNotBlank()) listOf(oldSingle) else emptyList()
        }
        mutableStateOf(list)
    }

    var defaultPassword by remember {
        mutableStateOf(prefs.getString("quick_saved_password", "") ?: "")
    }

    var visiblePasswordsSet by remember { mutableStateOf(setOf<String>()) }

    // Email State
    var newEmailInput by remember { mutableStateOf("") }
    var editingEmailOriginal by remember { mutableStateOf<String?>(null) }
    var editingEmailInput by remember { mutableStateOf("") }

    var savedEmailsList by remember {
        val raw = prefs.getString("saved_user_emails_list", "") ?: ""
        val list = raw.split(",").map { it.trim() }.filter { it.isNotBlank() }
        mutableStateOf(list)
    }

    var usedEmailsSet by remember {
        val raw = prefs.getString("used_user_emails_set", "") ?: ""
        val set = if (raw.isNotBlank()) raw.split("|||").map { it.trim() }.toSet() else emptySet()
        mutableStateOf(set)
    }

    // Bulk Import input text
    var bulkImportText by remember { mutableStateOf("") }

    // 3-Step Autofill input & manager
    val autofillManager = remember { SmartCredentialAutofillManager.getInstance(context) }
    var autofillGroupsList by remember { mutableStateOf(autofillManager.getSavedGroups()) }
    var autofillRawInput by remember { mutableStateOf("") }

    DisposableEffect(Unit) {
        val listener = object : SmartCredentialAutofillManager.AutofillUpdateListener {
            override fun onAutofillStateChanged() {
                autofillGroupsList = autofillManager.getSavedGroups()
            }
        }
        autofillManager.addListener(listener)
        onDispose { autofillManager.removeListener(listener) }
    }

    // Active in-keyboard input target ("password", "email", "bulk", "autofill", "edit_password", "edit_email")
    var activeInputTarget by remember { mutableStateOf("password") }

    // Helper functions
    fun persistPasswords(newList: List<String>, newDefault: String? = null) {
        val unique = newList.distinct().filter { it.isNotBlank() }
        savedPasswordsList = unique
        val def = newDefault ?: if (unique.contains(defaultPassword)) defaultPassword else unique.lastOrNull() ?: ""
        defaultPassword = def
        prefs.edit()
            .putString("saved_user_passwords_list", unique.joinToString("|||"))
            .putString("quick_saved_password", def)
            .apply()
    }

    fun persistEmails(newList: List<String>) {
        val unique = newList.distinct().filter { it.isNotBlank() }
        savedEmailsList = unique
        prefs.edit()
            .putString("saved_user_emails_list", unique.joinToString(","))
            .apply()
    }

    fun persistUsedEmails(newSet: Set<String>) {
        usedEmailsSet = newSet
        prefs.edit()
            .putString("used_user_emails_set", newSet.joinToString("|||"))
            .apply()
    }

    fun copyToClipboard(text: String, label: String = "কপি করা হয়েছে") {
        try {
            val clipMgr = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = ClipData.newPlainText("Copied Text", text)
            clipMgr?.setPrimaryClip(clip)
            Toast.makeText(context, "$label: $text", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {}
    }

    fun generateStrongPassword(length: Int = 12, style: String = "strong"): String {
        val random = SecureRandom()
        return when (style) {
            "pin" -> {
                (1..6).map { random.nextInt(10) }.joinToString("")
            }
            "memorable" -> {
                val words = listOf("Sakib", "Habib", "Smart", "Secure", "Login", "Star", "Cloud", "Tiger", "Swift", "Super")
                val word = words[random.nextInt(words.size)]
                val digits = 1000 + random.nextInt(9000)
                val sym = listOf("!", "@", "#", "$", "*")[random.nextInt(5)]
                "$word$sym$digits"
            }
            else -> {
                val upper = "ABCDEFGHJKLMNPQRSTUVWXYZ"
                val lower = "abcdefghijkmnopqrstuvwxyz"
                val digits = "23456789"
                val symbols = "!@#$%^&*"
                val all = upper + lower + digits + symbols
                val sb = StringBuilder()
                sb.append(upper[random.nextInt(upper.length)])
                sb.append(lower[random.nextInt(lower.length)])
                sb.append(digits[random.nextInt(digits.length)])
                sb.append(symbols[random.nextInt(symbols.length)])
                for (i in sb.length until length) {
                    sb.append(all[random.nextInt(all.length)])
                }
                val chars = sb.toString().toCharArray()
                for (i in chars.indices) {
                    val j = random.nextInt(chars.size)
                    val temp = chars[i]
                    chars[i] = chars[j]
                    chars[j] = temp
                }
                String(chars)
            }
        }
    }

    fun processCombinedEntry(input: String): Boolean {
        val trimmed = input.trim()
        val separator = when {
            trimmed.contains(",") -> ","
            trimmed.contains(":") -> ":"
            else -> null
        }
        if (separator != null) {
            val parts = trimmed.split(separator, limit = 2).map { it.trim() }
            if (parts.size == 2 && parts[0].contains("@")) {
                val email = parts[0]
                val pwd = parts[1]
                var addedEmail = false
                var addedPwd = false

                if (email.isNotBlank() && !savedEmailsList.contains(email)) {
                    persistEmails(savedEmailsList + email)
                    addedEmail = true
                }
                if (pwd.isNotBlank()) {
                    if (!savedPasswordsList.contains(pwd)) {
                        persistPasswords(savedPasswordsList + pwd, pwd)
                        addedPwd = true
                    } else {
                        persistPasswords(savedPasswordsList, pwd)
                    }
                }

                val msg = when {
                    addedEmail && addedPwd -> "ইমেল ও পাসওয়ার্ড দুটোই সেভ হয়েছে!"
                    addedEmail -> "ইমেল সেভ হয়েছে (পাসওয়ার্ড আগেই ছিল)"
                    addedPwd -> "পাসওয়ার্ড সেভ হয়েছে (ইমেল আগেই ছিল)"
                    else -> "ইমেল ও পাসওয়ার্ড দুটোই ইতিমধ্যে সেভ আছে"
                }
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                return true
            }
        }
        return false
    }

    fun addSavedPassword() {
        val trimmed = newPasswordInput.trim()
        if (trimmed.isBlank()) {
            Toast.makeText(context, "পাসওয়ার্ড লিখুন", Toast.LENGTH_SHORT).show()
            return
        }

        if (processCombinedEntry(trimmed)) {
            newPasswordInput = ""
            return
        }

        if (savedPasswordsList.contains(trimmed)) {
            defaultPassword = trimmed
            prefs.edit().putString("quick_saved_password", trimmed).apply()
            Toast.makeText(context, "পাসওয়ার্ডটি ইতিমধ্যে আছে (ডিফল্ট হিসেবে সেট করা হলো)", Toast.LENGTH_SHORT).show()
        } else {
            persistPasswords(savedPasswordsList + trimmed, trimmed)
            Toast.makeText(context, "পাসওয়ার্ড যোগ করা হয়েছে!", Toast.LENGTH_SHORT).show()
        }
        newPasswordInput = ""
    }

    fun saveEditedPassword() {
        val orig = editingPasswordOriginal ?: return
        val newPwd = editingPasswordInput.trim()
        if (newPwd.isBlank()) {
            Toast.makeText(context, "পাসওয়ার্ড ফাঁকা রাখা যাবে না", Toast.LENGTH_SHORT).show()
            return
        }
        val idx = savedPasswordsList.indexOf(orig)
        if (idx != -1) {
            val mutable = savedPasswordsList.toMutableList()
            mutable[idx] = newPwd
            val newDef = if (defaultPassword == orig) newPwd else defaultPassword
            persistPasswords(mutable, newDef)
            Toast.makeText(context, "পাসওয়ার্ড আপডেট করা হয়েছে", Toast.LENGTH_SHORT).show()
        }
        editingPasswordOriginal = null
        editingPasswordInput = ""
        activeInputTarget = "password"
    }

    fun removeSavedPassword(pwdToRemove: String) {
        val updated = savedPasswordsList.filter { it != pwdToRemove }
        persistPasswords(updated, if (defaultPassword == pwdToRemove) updated.lastOrNull() ?: "" else defaultPassword)
        Toast.makeText(context, "পাসওয়ার্ড মুছে ফেলা হয়েছে", Toast.LENGTH_SHORT).show()
    }

    fun setDefaultPassword(pwd: String) {
        defaultPassword = pwd
        prefs.edit().putString("quick_saved_password", pwd).apply()
        Toast.makeText(context, "ডিফল্ট পাসওয়ার্ড সেট করা হলো", Toast.LENGTH_SHORT).show()
    }

    fun addSavedEmail() {
        val trimmed = newEmailInput.trim()
        if (trimmed.isBlank()) {
            Toast.makeText(context, "ইমেল লিখুন", Toast.LENGTH_SHORT).show()
            return
        }

        if (processCombinedEntry(trimmed)) {
            newEmailInput = ""
            return
        }

        if (savedEmailsList.contains(trimmed)) {
            Toast.makeText(context, "এই ইমেলটি ইতিমধ্যে তালিকায় রয়েছে", Toast.LENGTH_SHORT).show()
        } else {
            persistEmails(savedEmailsList + trimmed)
            Toast.makeText(context, "ইমেল যোগ করা হয়েছে!", Toast.LENGTH_SHORT).show()
        }
        newEmailInput = ""
    }

    fun saveEditedEmail() {
        val orig = editingEmailOriginal ?: return
        val newMail = editingEmailInput.trim()
        if (newMail.isBlank()) {
            Toast.makeText(context, "ইমেল ফাঁকা রাখা যাবে না", Toast.LENGTH_SHORT).show()
            return
        }
        val idx = savedEmailsList.indexOf(orig)
        if (idx != -1) {
            val mutable = savedEmailsList.toMutableList()
            mutable[idx] = newMail
            persistEmails(mutable)
            if (usedEmailsSet.contains(orig)) {
                persistUsedEmails((usedEmailsSet - orig) + newMail)
            }
            Toast.makeText(context, "ইমেল আপডেট করা হয়েছে", Toast.LENGTH_SHORT).show()
        }
        editingEmailOriginal = null
        editingEmailInput = ""
        activeInputTarget = "email"
    }

    fun removeSavedEmail(emailToRemove: String) {
        val updated = savedEmailsList.filter { it != emailToRemove }
        persistEmails(updated)
        persistUsedEmails(usedEmailsSet - emailToRemove)
        Toast.makeText(context, "ইমেল মুছে ফেলা হয়েছে", Toast.LENGTH_SHORT).show()
    }

    fun toggleEmailUsedStatus(email: String) {
        val isUsed = usedEmailsSet.contains(email)
        val updatedSet = if (isUsed) usedEmailsSet - email else usedEmailsSet + email
        persistUsedEmails(updatedSet)
        val statusText = if (!isUsed) "ব্যবহৃত (Used)" else "অব্যবহৃত (Fresh)"
        Toast.makeText(context, "$email চিহ্নিত: $statusText", Toast.LENGTH_SHORT).show()
    }

    fun clearAllUsedEmails() {
        val freshOnly = savedEmailsList.filter { !usedEmailsSet.contains(it) }
        persistEmails(freshOnly)
        persistUsedEmails(emptySet())
        Toast.makeText(context, "সকল ব্যবহৃত ইমেল মুছে ফেলা হয়েছে", Toast.LENGTH_SHORT).show()
    }

    fun resetAllEmailsToFresh() {
        persistUsedEmails(emptySet())
        Toast.makeText(context, "সকল ইমেল অব্যবহৃত (Fresh) হিসেবে রিসেট করা হলো", Toast.LENGTH_SHORT).show()
    }

    fun processBulkImport() {
        val text = bulkImportText.trim()
        if (text.isBlank()) {
            Toast.makeText(context, "ইমপোর্ট করার জন্য টেক্সট দিন", Toast.LENGTH_SHORT).show()
            return
        }

        val lines = text.split("\n", "\r").map { it.trim() }.filter { it.isNotBlank() }
        var newEmailsCount = 0
        var newPasswordsCount = 0
        val currentEmails = savedEmailsList.toMutableList()
        val currentPasswords = savedPasswordsList.toMutableList()
        var lastExtractedPwd = defaultPassword

        for (line in lines) {
            val separator = when {
                line.contains(",") -> ","
                line.contains(":") -> ":"
                line.contains("|") -> "|"
                line.contains("\t") -> "\t"
                line.contains(" ") && line.contains("@") -> " "
                else -> null
            }

            if (separator != null) {
                val parts = line.split(separator, limit = 2).map { it.trim() }
                if (parts.size == 2) {
                    val part1 = parts[0]
                    val part2 = parts[1]
                    val email = if (part1.contains("@")) part1 else if (part2.contains("@")) part2 else null
                    val pwd = if (email == part1) part2 else if (email == part2) part1 else null

                    if (email != null && email.isNotBlank() && !currentEmails.contains(email)) {
                        currentEmails.add(email)
                        newEmailsCount++
                    }
                    if (pwd != null && pwd.isNotBlank() && !currentPasswords.contains(pwd)) {
                        currentPasswords.add(pwd)
                        lastExtractedPwd = pwd
                        newPasswordsCount++
                    }
                }
            } else {
                val trimmedLine = line.trim()
                if (trimmedLine.contains("@") && !currentEmails.contains(trimmedLine)) {
                    currentEmails.add(trimmedLine)
                    newEmailsCount++
                } else if (!trimmedLine.contains("@") && trimmedLine.length >= 2 && !currentPasswords.contains(trimmedLine)) {
                    currentPasswords.add(trimmedLine)
                    lastExtractedPwd = trimmedLine
                    newPasswordsCount++
                }
            }
        }

        persistEmails(currentEmails)
        persistPasswords(currentPasswords, lastExtractedPwd)
        bulkImportText = ""

        val summary = if (newEmailsCount > 0 || newPasswordsCount > 0) {
            "সফলভাবে $newEmailsCount টি নতুন ইমেল ও $newPasswordsCount টি পাসওয়ার্ড সেভ হয়েছে!"
        } else {
            "সবগুলো ইমেল ও পাসওয়ার্ড আগেই তালিকায় সেভ করা ছিল।"
        }
        Toast.makeText(context, summary, Toast.LENGTH_LONG).show()
    }

    fun pasteFromClipboard(target: String) {
        try {
            val clipMgr = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = clipMgr?.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0)?.coerceToText(context)?.toString() ?: ""
                if (text.isNotBlank()) {
                    when (target) {
                        "password" -> newPasswordInput = text
                        "email" -> newEmailInput = text
                        "bulk" -> bulkImportText = text
                        "autofill" -> autofillRawInput = text
                    }
                    Toast.makeText(context, "ক্লিপবোর্ড থেকে পেস্ট করা হয়েছে", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "ক্লিপবোর্ডে কোনো লেখা নেই", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(context, "ক্লিপবোর্ডে কোনো লেখা নেই", Toast.LENGTH_SHORT).show()
            }
        } catch (_: Exception) {}
    }

    // Register in-keyboard typing handler
    DisposableEffect(activeInputTarget, newPasswordInput, newEmailInput, bulkImportText, autofillRawInput, editingPasswordInput, editingEmailInput) {
        val handler: (char: String?, specialKey: SpecialKeyType?) -> Unit = { char, specialKey ->
            when (activeInputTarget) {
                "password" -> {
                    if (char != null) newPasswordInput += char
                    else if (specialKey != null) {
                        when (specialKey) {
                            SpecialKeyType.BACKSPACE -> if (newPasswordInput.isNotEmpty()) newPasswordInput = newPasswordInput.dropLast(1)
                            SpecialKeyType.SPACE -> newPasswordInput += " "
                            SpecialKeyType.ENTER -> addSavedPassword()
                            else -> {}
                        }
                    }
                }
                "edit_password" -> {
                    if (char != null) editingPasswordInput += char
                    else if (specialKey != null) {
                        when (specialKey) {
                            SpecialKeyType.BACKSPACE -> if (editingPasswordInput.isNotEmpty()) editingPasswordInput = editingPasswordInput.dropLast(1)
                            SpecialKeyType.SPACE -> editingPasswordInput += " "
                            SpecialKeyType.ENTER -> saveEditedPassword()
                            else -> {}
                        }
                    }
                }
                "email" -> {
                    if (char != null) newEmailInput += char
                    else if (specialKey != null) {
                        when (specialKey) {
                            SpecialKeyType.BACKSPACE -> if (newEmailInput.isNotEmpty()) newEmailInput = newEmailInput.dropLast(1)
                            SpecialKeyType.SPACE -> newEmailInput += " "
                            SpecialKeyType.ENTER -> addSavedEmail()
                            else -> {}
                        }
                    }
                }
                "edit_email" -> {
                    if (char != null) editingEmailInput += char
                    else if (specialKey != null) {
                        when (specialKey) {
                            SpecialKeyType.BACKSPACE -> if (editingEmailInput.isNotEmpty()) editingEmailInput = editingEmailInput.dropLast(1)
                            SpecialKeyType.SPACE -> editingEmailInput += " "
                            SpecialKeyType.ENTER -> saveEditedEmail()
                            else -> {}
                        }
                    }
                }
                "bulk" -> {
                    if (char != null) bulkImportText += char
                    else if (specialKey != null) {
                        when (specialKey) {
                            SpecialKeyType.BACKSPACE -> if (bulkImportText.isNotEmpty()) bulkImportText = bulkImportText.dropLast(1)
                            SpecialKeyType.SPACE -> bulkImportText += " "
                            SpecialKeyType.ENTER -> bulkImportText += "\n"
                            else -> {}
                        }
                    }
                }
                "autofill" -> {
                    if (char != null) autofillRawInput += char
                    else if (specialKey != null) {
                        when (specialKey) {
                            SpecialKeyType.BACKSPACE -> if (autofillRawInput.isNotEmpty()) autofillRawInput = autofillRawInput.dropLast(1)
                            SpecialKeyType.SPACE -> autofillRawInput += " "
                            SpecialKeyType.ENTER -> autofillRawInput += "\n"
                            else -> {}
                        }
                    }
                }
            }
        }
        onRegisterInputHandler(handler)
        onDispose { onRegisterInputHandler(null) }
    }

    val bgColor = if (isDarkTheme) Color(0xFF0D0E12) else Color(0xFFF0F2F5)
    val headerBg = if (isDarkTheme) Color(0xFF161820) else Color(0xFFE2E6EC)
    val cardBg = if (isDarkTheme) Color(0xFF1E212B) else Color(0xFFFFFFFF)
    val textColor = if (isDarkTheme) Color(0xFFFFFFFF) else Color(0xFF1F2937)
    val subTextColor = if (isDarkTheme) Color(0xFFA0A5B5) else Color(0xFF6B7280)
    val accentColor = Color(0xFF007AFF)
    val passwordColor = Color(0xFFFF9800)
    val emailColor = Color(0xFF7C4DFF)
    val bulkColor = Color(0xFF00BFA5)
    val freshColor = Color(0xFF00C853)
    val usedColor = Color(0xFFFF5252)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(bgColor)
    ) {
        // Top Header
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = headerBg,
            shadowElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(if (selectedTab == 0) passwordColor else if (selectedTab == 1) emailColor else if (selectedTab == 2) bulkColor else Color(0xFFFF6D00)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (selectedTab) {
                                0 -> Icons.Default.Lock
                                1 -> Icons.Default.AlternateEmail
                                2 -> Icons.Default.Download
                                else -> Icons.Default.FormatListNumbered
                            },
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(7.dp))
                    Text(
                        text = when (selectedTab) {
                            0 -> "পাসওয়ার্ড ম্যানেজার"
                            1 -> "ইমেল অ্যাকাউন্ট ম্যানেজার"
                            2 -> "বাল্ক ইমপোর্ট টুল"
                            else -> "৩-ধাপ স্মার্ট অটোফিল"
                        },
                        color = textColor,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "বন্ধ করুন",
                        tint = subTextColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // 4 Modern Navigation Tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (isDarkTheme) Color(0xFF14161D) else Color(0xFFE8EBF0))
                .padding(horizontal = 4.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            // Tab 0: Passwords
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        selectedTab = 0
                        activeInputTarget = "password"
                    },
                color = if (selectedTab == 0) passwordColor else Color.Transparent
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 5.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = if (selectedTab == 0) Color.White else subTextColor,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "পাসওয়ার্ড (${savedPasswordsList.size})",
                        color = if (selectedTab == 0) Color.White else subTextColor,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }

            // Tab 1: Emails
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        selectedTab = 1
                        activeInputTarget = "email"
                    },
                color = if (selectedTab == 1) emailColor else Color.Transparent
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 5.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AlternateEmail,
                        contentDescription = null,
                        tint = if (selectedTab == 1) Color.White else subTextColor,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "ইমেল (${savedEmailsList.size})",
                        color = if (selectedTab == 1) Color.White else subTextColor,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }

            // Tab 2: Bulk Import
            Surface(
                modifier = Modifier
                    .weight(0.9f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        selectedTab = 2
                        activeInputTarget = "bulk"
                    },
                color = if (selectedTab == 2) bulkColor else Color.Transparent
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 5.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        tint = if (selectedTab == 2) Color.White else subTextColor,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = "বাল্ক ইমপোর্ট",
                        color = if (selectedTab == 2) Color.White else subTextColor,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }

            // Tab 3: 3-Step Autofill
            Surface(
                modifier = Modifier
                    .weight(1.05f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        selectedTab = 3
                        activeInputTarget = "autofill"
                    },
                color = if (selectedTab == 3) Color(0xFFFF6D00) else Color.Transparent
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 5.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.FormatListNumbered,
                        contentDescription = null,
                        tint = if (selectedTab == 3) Color.White else subTextColor,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = "৩-ধাপ অটোফিল",
                        color = if (selectedTab == 3) Color.White else subTextColor,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }

        // TAB 0: 🔑 PASSWORD MANAGER (Full Upgraded Experience)
        if (selectedTab == 0) {
            val filteredPasswords = remember(savedPasswordsList, passwordSearchQuery) {
                if (passwordSearchQuery.isBlank()) savedPasswordsList
                else savedPasswordsList.filter { it.contains(passwordSearchQuery, ignoreCase = true) }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // 1. Default Password Highlight Banner
                if (defaultPassword.isNotBlank()) {
                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp)),
                            color = if (isDarkTheme) Color(0xFF2A2216) else Color(0xFFFFF3E0),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFB74D).copy(alpha = 0.6f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = "Default Password",
                                        tint = Color(0xFFFFB74D),
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = "ডিফল্ট পাসওয়ার্ড: ",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isDarkTheme) Color(0xFFFFD54F) else Color(0xFFE65100)
                                    )
                                    Text(
                                        text = if (visiblePasswordsSet.contains(defaultPassword)) defaultPassword else "●".repeat(defaultPassword.length.coerceAtMost(8)),
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = textColor
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { copyToClipboard(defaultPassword, "পাসওয়ার্ড কপি হয়েছে") },
                                        modifier = Modifier.size(22.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "Copy",
                                            tint = Color(0xFFFFB74D),
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. Add Password & Generator Card
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.AddModerator,
                                        contentDescription = null,
                                        tint = passwordColor,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "নতুন পাসওয়ার্ড যোগ বা জেনারেট করুন",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = textColor
                                    )
                                }

                                if (activeInputTarget == "password") {
                                    Surface(
                                        color = passwordColor.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text(
                                            text = "● ইনপুট সক্রিয়",
                                            color = passwordColor,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(5.dp))

                            // Password Input Box
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(
                                            width = if (activeInputTarget == "password") 1.8.dp else 1.dp,
                                            color = if (activeInputTarget == "password") passwordColor else subTextColor.copy(alpha = 0.35f),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .background(if (isDarkTheme) Color(0xFF14161F) else Color(0xFFF9FAFB))
                                        .clickable { activeInputTarget = "password" }
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = if (newPasswordInput.isEmpty()) "পাসওয়ার্ড লিখুন..." else if (isNewPasswordVisible) newPasswordInput else "●".repeat(newPasswordInput.length),
                                            color = if (newPasswordInput.isEmpty()) subTextColor else textColor,
                                            fontSize = 11.5.sp,
                                            fontWeight = if (newPasswordInput.isEmpty()) FontWeight.Normal else FontWeight.Bold,
                                            fontFamily = if (newPasswordInput.isNotEmpty()) FontFamily.Monospace else FontFamily.Default,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (newPasswordInput.isNotEmpty()) {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "Clear",
                                                    tint = subTextColor,
                                                    modifier = Modifier
                                                        .size(15.dp)
                                                        .clickable { newPasswordInput = "" }
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                            }

                                            Icon(
                                                imageVector = if (isNewPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                contentDescription = "Show/Hide Password",
                                                tint = subTextColor,
                                                modifier = Modifier
                                                    .size(15.dp)
                                                    .clickable { isNewPasswordVisible = !isNewPasswordVisible }
                                            )
                                        }
                                    }
                                }

                                // Quick Paste Button
                                Button(
                                    onClick = { pasteFromClipboard("password") },
                                    colors = ButtonDefaults.buttonColors(containerColor = if (isDarkTheme) Color(0xFF2C2F3A) else Color(0xFFE2E6EC)),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Icon(Icons.Default.ContentPaste, contentDescription = "Paste", tint = textColor, modifier = Modifier.size(13.dp))
                                }

                                // Add Button
                                Button(
                                    onClick = { addSavedPassword() },
                                    colors = ButtonDefaults.buttonColors(containerColor = passwordColor),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Text("যোগ", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Password Generator Quick Bar
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "🎲 তৈরি:",
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = subTextColor
                                )

                                Surface(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable {
                                            newPasswordInput = generateStrongPassword(8, "strong")
                                            isNewPasswordVisible = true
                                        },
                                    color = if (isDarkTheme) Color(0xFF2B2215) else Color(0xFFFFF3E0),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "৮ অক্ষরের",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = passwordColor,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                    )
                                }

                                Surface(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable {
                                            newPasswordInput = generateStrongPassword(12, "strong")
                                            isNewPasswordVisible = true
                                        },
                                    color = if (isDarkTheme) Color(0xFF2B2215) else Color(0xFFFFF3E0),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "১২ অক্ষর (শক্তিশালী)",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = passwordColor,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                    )
                                }

                                Surface(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable {
                                            newPasswordInput = generateStrongPassword(10, "memorable")
                                            isNewPasswordVisible = true
                                        },
                                    color = if (isDarkTheme) Color(0xFF2B2215) else Color(0xFFFFF3E0),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "স্মরণীয়",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = passwordColor,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                    )
                                }

                                Surface(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable {
                                            newPasswordInput = generateStrongPassword(6, "pin")
                                            isNewPasswordVisible = true
                                        },
                                    color = if (isDarkTheme) Color(0xFF2B2215) else Color(0xFFFFF3E0),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "৬-ডিজিট পিন",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = passwordColor,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. Search Bar
                if (savedPasswordsList.size > 2) {
                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(30.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            color = cardBg
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Search, contentDescription = "Search", tint = subTextColor, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(modifier = Modifier.weight(1f)) {
                                    if (passwordSearchQuery.isEmpty()) {
                                        Text("পাসওয়ার্ড খুঁজুন...", fontSize = 11.sp, color = subTextColor)
                                    }
                                    androidx.compose.foundation.text.BasicTextField(
                                        value = passwordSearchQuery,
                                        onValueChange = { passwordSearchQuery = it },
                                        singleLine = true,
                                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, color = textColor),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                if (passwordSearchQuery.isNotEmpty()) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Clear",
                                        tint = subTextColor,
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clickable { passwordSearchQuery = "" }
                                    )
                                }
                            }
                        }
                    }
                }

                // 4. In-Place Password Edit Box
                if (editingPasswordOriginal != null) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = if (isDarkTheme) Color(0xFF28231A) else Color(0xFFFFF8E1)),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.2.dp, passwordColor),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    text = "পাসওয়ার্ড এডিট করুন:",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = passwordColor
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isDarkTheme) Color(0xFF161820) else Color.White)
                                            .border(1.dp, passwordColor, RoundedCornerShape(6.dp))
                                            .clickable { activeInputTarget = "edit_password" }
                                            .padding(horizontal = 8.dp, vertical = 5.dp)
                                    ) {
                                        Text(
                                            text = if (editingPasswordInput.isEmpty()) "নতুন পাসওয়ার্ড..." else editingPasswordInput,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = textColor
                                        )
                                    }
                                    Button(
                                        onClick = { saveEditedPassword() },
                                        colors = ButtonDefaults.buttonColors(containerColor = passwordColor),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.height(30.dp)
                                    ) {
                                        Text("সেভ", fontSize = 10.5.sp, color = Color.White)
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            editingPasswordOriginal = null
                                            editingPasswordInput = ""
                                            activeInputTarget = "password"
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.height(30.dp)
                                    ) {
                                        Text("বাতিল", fontSize = 10.5.sp, color = subTextColor)
                                    }
                                }
                            }
                        }
                    }
                }

                // 5. Password Items List
                if (filteredPasswords.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (passwordSearchQuery.isNotEmpty()) "কোনো রেজাল্ট পাওয়া যায়নি" else "কোনো পাসওয়ার্ড সেভ করা নেই। ওপর থেকে যোগ বা তৈরি করুন।",
                                color = subTextColor,
                                fontSize = 11.sp
                            )
                        }
                    }
                } else {
                    items(filteredPasswords) { pwd ->
                        val isVisible = visiblePasswordsSet.contains(pwd)
                        val isDefault = (pwd == defaultPassword)
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isDefault) (if (isDarkTheme) Color(0xFF262016) else Color(0xFFFFFDE7)) else cardBg
                            ),
                            shape = RoundedCornerShape(8.dp),
                            border = if (isDefault) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFB74D).copy(alpha = 0.7f)) else null,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = if (isDefault) Icons.Default.Star else Icons.Default.StarBorder,
                                        contentDescription = "Default",
                                        tint = if (isDefault) Color(0xFFFFB74D) else subTextColor.copy(alpha = 0.6f),
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clickable { setDefaultPassword(pwd) }
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = if (isVisible) pwd else "●".repeat(pwd.length.coerceAtMost(10)),
                                                color = textColor,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (isDefault) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Surface(
                                                    color = Color(0xFFFFB74D).copy(alpha = 0.2f),
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        text = "ডিফল্ট",
                                                        color = Color(0xFFFFB74D),
                                                        fontSize = 8.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    // Visibility Toggle
                                    IconButton(
                                        onClick = {
                                            visiblePasswordsSet = if (isVisible) visiblePasswordsSet - pwd else visiblePasswordsSet + pwd
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = "Show",
                                            tint = subTextColor,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }

                                    // Copy Action
                                    IconButton(
                                        onClick = { copyToClipboard(pwd, "পাসওয়ার্ড কপি হয়েছে") },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "Copy",
                                            tint = passwordColor,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }

                                    // Edit Action
                                    IconButton(
                                        onClick = {
                                            editingPasswordOriginal = pwd
                                            editingPasswordInput = pwd
                                            activeInputTarget = "edit_password"
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Edit",
                                            tint = subTextColor,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }

                                    // Delete Action
                                    IconButton(
                                        onClick = { removeSavedPassword(pwd) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete",
                                            tint = Color(0xFFEA4335),
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // TAB 1: 📧 EMAIL MANAGER (Full Upgraded Experience)
        else if (selectedTab == 1) {
            val freshCount = remember(savedEmailsList, usedEmailsSet) {
                savedEmailsList.count { !usedEmailsSet.contains(it) }
            }
            val usedCount = remember(savedEmailsList, usedEmailsSet) {
                savedEmailsList.count { usedEmailsSet.contains(it) }
            }

            val filteredEmails = remember(savedEmailsList, emailSearchQuery, emailFilterStatus, usedEmailsSet) {
                var list = savedEmailsList
                if (emailFilterStatus == "fresh") {
                    list = list.filter { !usedEmailsSet.contains(it) }
                } else if (emailFilterStatus == "used") {
                    list = list.filter { usedEmailsSet.contains(it) }
                }
                if (emailSearchQuery.isNotBlank()) {
                    list = list.filter { it.contains(emailSearchQuery, ignoreCase = true) }
                }
                list
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // 1. Stats Counter & Filter Chips Bar
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            // All Chip
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { emailFilterStatus = "all" },
                                color = if (emailFilterStatus == "all") emailColor else cardBg,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = "সব (${savedEmailsList.size})",
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (emailFilterStatus == "all") Color.White else textColor,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                )
                            }

                            // Fresh Chip
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { emailFilterStatus = "fresh" },
                                color = if (emailFilterStatus == "fresh") freshColor else cardBg,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = "🟢 অব্যবহৃত ($freshCount)",
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (emailFilterStatus == "fresh") Color.White else textColor,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                )
                            }

                            // Used Chip
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { emailFilterStatus = "used" },
                                color = if (emailFilterStatus == "used") usedColor else cardBg,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = "🔴 ব্যবহৃত ($usedCount)",
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (emailFilterStatus == "used") Color.White else textColor,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                )
                            }
                        }

                        // Reset all to fresh button
                        if (usedCount > 0) {
                            Text(
                                text = "রিসেট",
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = emailColor,
                                modifier = Modifier
                                    .clickable { resetAllEmailsToFresh() }
                                    .padding(4.dp)
                            )
                        }
                    }
                }

                // 2. Add Email Card with Quick Domain Chips
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.AlternateEmail, contentDescription = null, tint = emailColor, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(text = "নতুন ইমেল যোগ করুন", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = textColor)
                                }

                                if (activeInputTarget == "email") {
                                    Surface(
                                        color = emailColor.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text(
                                            text = "● ইনপুট সক্রিয়",
                                            color = emailColor,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(5.dp))

                            // Email Input Box
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(
                                            width = if (activeInputTarget == "email") 1.8.dp else 1.dp,
                                            color = if (activeInputTarget == "email") emailColor else subTextColor.copy(alpha = 0.35f),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .background(if (isDarkTheme) Color(0xFF14161F) else Color(0xFFF9FAFB))
                                        .clickable { activeInputTarget = "email" }
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = if (newEmailInput.isEmpty()) "ইমেল অ্যাড্রেস লিখুন..." else newEmailInput,
                                            color = if (newEmailInput.isEmpty()) subTextColor else textColor,
                                            fontSize = 11.5.sp,
                                            fontWeight = if (newEmailInput.isEmpty()) FontWeight.Normal else FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )

                                        if (newEmailInput.isNotEmpty()) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Clear",
                                                tint = subTextColor,
                                                modifier = Modifier
                                                    .size(15.dp)
                                                    .clickable { newEmailInput = "" }
                                            )
                                        }
                                    }
                                }

                                // Quick Paste Button
                                Button(
                                    onClick = { pasteFromClipboard("email") },
                                    colors = ButtonDefaults.buttonColors(containerColor = if (isDarkTheme) Color(0xFF2C2F3A) else Color(0xFFE2E6EC)),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Icon(Icons.Default.ContentPaste, contentDescription = "Paste", tint = textColor, modifier = Modifier.size(13.dp))
                                }

                                // Add Button
                                Button(
                                    onClick = { addSavedEmail() },
                                    colors = ButtonDefaults.buttonColors(containerColor = emailColor),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Text("যোগ", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Fast Domain Insertion Chips
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                listOf("@gmail.com", "@outlook.com", "@yahoo.com", "@hotmail.com", "@icloud.com").forEach { domain ->
                                    Surface(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable {
                                                if (newEmailInput.contains("@")) {
                                                    newEmailInput = newEmailInput.substringBefore("@") + domain
                                                } else {
                                                    newEmailInput += domain
                                                }
                                            },
                                        color = if (isDarkTheme) Color(0xFF271F36) else Color(0xFFEDE7F6),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = domain,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = emailColor,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. Search Bar
                if (savedEmailsList.size > 2) {
                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(30.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            color = cardBg
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Search, contentDescription = "Search", tint = subTextColor, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(modifier = Modifier.weight(1f)) {
                                    if (emailSearchQuery.isEmpty()) {
                                        Text("ইমেল খুঁজুন...", fontSize = 11.sp, color = subTextColor)
                                    }
                                    androidx.compose.foundation.text.BasicTextField(
                                        value = emailSearchQuery,
                                        onValueChange = { emailSearchQuery = it },
                                        singleLine = true,
                                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, color = textColor),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                if (emailSearchQuery.isNotEmpty()) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Clear",
                                        tint = subTextColor,
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clickable { emailSearchQuery = "" }
                                    )
                                }
                            }
                        }
                    }
                }

                // 4. In-Place Email Edit Box
                if (editingEmailOriginal != null) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = if (isDarkTheme) Color(0xFF261D36) else Color(0xFFF3E5F5)),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.2.dp, emailColor),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    text = "ইমেল এডিট করুন:",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = emailColor
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isDarkTheme) Color(0xFF161820) else Color.White)
                                            .border(1.dp, emailColor, RoundedCornerShape(6.dp))
                                            .clickable { activeInputTarget = "edit_email" }
                                            .padding(horizontal = 8.dp, vertical = 5.dp)
                                    ) {
                                        Text(
                                            text = if (editingEmailInput.isEmpty()) "নতুন ইমেল..." else editingEmailInput,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = textColor
                                        )
                                    }
                                    Button(
                                        onClick = { saveEditedEmail() },
                                        colors = ButtonDefaults.buttonColors(containerColor = emailColor),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.height(30.dp)
                                    ) {
                                        Text("সেভ", fontSize = 10.5.sp, color = Color.White)
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            editingEmailOriginal = null
                                            editingEmailInput = ""
                                            activeInputTarget = "email"
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.height(30.dp)
                                    ) {
                                        Text("বাতিল", fontSize = 10.5.sp, color = subTextColor)
                                    }
                                }
                            }
                        }
                    }
                }

                // 5. Emails Items List
                if (filteredEmails.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (emailSearchQuery.isNotEmpty()) "কোনো রেজাল্ট পাওয়া যায়নি" else "কোনো ইমেল সেভ করা নেই। ওপরে টাইপ করে যোগ করুন।",
                                color = subTextColor,
                                fontSize = 11.sp
                            )
                        }
                    }
                } else {
                    items(filteredEmails) { email ->
                        val isUsed = usedEmailsSet.contains(email)
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isUsed) (if (isDarkTheme) Color(0xFF171920) else Color(0xFFF3F4F6)) else cardBg
                            ),
                            shape = RoundedCornerShape(8.dp),
                            border = if (!isUsed) androidx.compose.foundation.BorderStroke(1.dp, freshColor.copy(alpha = 0.35f)) else null,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { toggleEmailUsedStatus(email) }
                                ) {
                                    Surface(
                                        color = if (isUsed) Color(0xFF374151) else freshColor.copy(alpha = 0.18f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = if (isUsed) "Used" else "Fresh",
                                            color = if (isUsed) Color(0xFFFF8A80) else freshColor,
                                            fontSize = 8.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = email,
                                        color = if (isUsed) subTextColor else textColor,
                                        fontSize = 11.5.sp,
                                        fontWeight = if (isUsed) FontWeight.Normal else FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    // Toggle Status Button
                                    IconButton(
                                        onClick = { toggleEmailUsedStatus(email) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isUsed) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                            contentDescription = "Toggle Used",
                                            tint = if (isUsed) usedColor else freshColor,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }

                                    // Copy Action
                                    IconButton(
                                        onClick = { copyToClipboard(email, "ইমেল কপি হয়েছে") },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "Copy",
                                            tint = emailColor,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }

                                    // Edit Action
                                    IconButton(
                                        onClick = {
                                            editingEmailOriginal = email
                                            editingEmailInput = email
                                            activeInputTarget = "edit_email"
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Edit",
                                            tint = subTextColor,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }

                                    // Delete Action
                                    IconButton(
                                        onClick = { removeSavedEmail(email) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete",
                                            tint = Color(0xFFEA4335),
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 6. Clear Used Emails Footer Action
                if (usedCount > 0) {
                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { clearAllUsedEmails() },
                            color = if (isDarkTheme) Color(0xFF2E1A1A) else Color(0xFFFFEBEE),
                            border = androidx.compose.foundation.BorderStroke(1.dp, usedColor.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = usedColor, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "সকল ব্যবহৃত (Used) $usedCount টি ইমেল মুছে ফেলুন",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = usedColor
                                )
                            }
                        }
                    }
                }
            }
        }

        // TAB 2: 📥 BULK IMPORT (email,password per line)
        else if (selectedTab == 2) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.LibraryAdd,
                                    contentDescription = null,
                                    tint = bulkColor,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "ইমেল ও পাসওয়ার্ড তালিকা ইমপোর্ট",
                                    color = textColor,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Button(
                                onClick = { pasteFromClipboard("bulk") },
                                colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentPaste,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("ক্লিপবোর্ড থেকে পেস্ট", fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Text(
                            text = "ফরম্যাট: প্রতি লাইনে `email,password` দিন। কমা (,) এর আগের অংশ ইমেল এবং পরের অংশ পাসওয়ার্ড হিসেবে আলাদা সেভ হবে। ডুপ্লিকেট হবে না!",
                            color = subTextColor,
                            fontSize = 10.sp,
                            lineHeight = 14.sp,
                            modifier = Modifier.padding(top = 4.dp, bottom = 6.dp)
                        )

                        // Multi-line input display box
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 90.dp, max = 130.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(
                                    width = if (activeInputTarget == "bulk") 2.dp else 1.dp,
                                    color = if (activeInputTarget == "bulk") bulkColor else subTextColor.copy(alpha = 0.35f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .background(if (isDarkTheme) Color(0xFF15171F) else Color(0xFFF9FAFB))
                                .clickable { activeInputTarget = "bulk" }
                                .padding(8.dp)
                        ) {
                            Text(
                                text = if (bulkImportText.isEmpty()) "উদাহরণ:\nlaura.garcia48@dbker.com,Habib22\nlinda.moore806@dbker.com,Habib22\nmichael.miller626@dbker.com,Habib22" else bulkImportText,
                                color = if (bulkImportText.isEmpty()) subTextColor.copy(alpha = 0.6f) else textColor,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 15.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (bulkImportText.isNotEmpty()) {
                                OutlinedButton(
                                    onClick = { bulkImportText = "" },
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Text("মুছে ফেলুন", fontSize = 10.sp, color = Color(0xFFEA4335))
                                }
                            } else {
                                Spacer(modifier = Modifier.width(1.dp))
                            }

                            Button(
                                onClick = { processBulkImport() },
                                colors = ButtonDefaults.buttonColors(containerColor = bulkColor),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "ইমপোর্ট সম্পন্ন করুন",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }

        // TAB 3: ⚡ 3-STEP CREDENTIAL AUTOFILL
        else if (selectedTab == 3) {
            val activeGrp = remember(autofillGroupsList) { autofillManager.getActiveGroup() }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Section 1: Active Account Banner
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isDarkTheme) Color(0xFF2E2419) else Color(0xFFFFF3E0)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.FormatListNumbered,
                                        contentDescription = null,
                                        tint = Color(0xFFFF6D00),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "বর্তমান সক্রিয় অটোফিল একাউন্ট",
                                        color = if (isDarkTheme) Color(0xFFFFB74D) else Color(0xFFE65100),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                if (activeGrp != null) {
                                    TextButton(
                                        onClick = {
                                            autofillManager.resetActiveGroupStep()
                                            Toast.makeText(context, "১ম ধাপে রিসেট করা হয়েছে", Toast.LENGTH_SHORT).show()
                                        },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                        modifier = Modifier.height(24.dp)
                                    ) {
                                        Text("১ম ধাপে রিসেট", fontSize = 10.sp, color = Color(0xFFFF9100), fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            if (activeGrp != null) {
                                Text(
                                    text = activeGrp.title,
                                    color = textColor,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    activeGrp.steps.forEachIndexed { idx, step ->
                                        val isCurrent = idx == activeGrp.currentStepIndex
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (isCurrent) Color(0xFFFF6D00).copy(alpha = 0.25f) else Color(0xFF14161F),
                                            modifier = Modifier
                                                .border(
                                                    width = if (isCurrent) 1.2.dp else 0.5.dp,
                                                    color = if (isCurrent) Color(0xFFFF6D00) else Color(0xFF333745),
                                                    shape = RoundedCornerShape(6.dp)
                                                )
                                                .clickable {
                                                    autofillManager.setCurrentStepIndex(idx)
                                                    Toast.makeText(context, "ধাপ ${idx + 1} (${step.label}) সক্রিয় করা হয়েছে", Toast.LENGTH_SHORT).show()
                                                }
                                                .padding(horizontal = 8.dp, vertical = 5.dp)
                                        ) {
                                            Column {
                                                Text(
                                                    text = "ধাপ ${idx + 1}: ${step.label}",
                                                    color = if (isCurrent) Color(0xFFFF9100) else subTextColor,
                                                    fontSize = 9.5.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = if (step.label.lowercase().contains("pass")) "••••••••" else step.value,
                                                    color = Color.White,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                Text(
                                    text = "কোনো একাউন্ট সেভ করা নেই। আপনি যেকোনো অ্যাপ বা মেসেজ থেকে First name, Login, Password ফরম্যাটে লেখা কপি করলেই অটোমেটিক এখানে ৩-ধাপে সেভ হয়ে যাবে!",
                                    color = subTextColor,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }
                }

                // Section 2: Manual Paste & Parse Box
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "মেসেজ টেক্সট থেকে ৩-ধাপে সেভ করুন",
                                color = textColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "উদাহরণ: First name: Wojtek \\n Login: wostekv_der_ \\n Password: tOwXiYDf2GNr",
                                color = subTextColor,
                                fontSize = 10.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(64.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isDarkTheme) Color(0xFF14161E) else Color(0xFFF1F3F6))
                                    .border(
                                        width = if (activeInputTarget == "autofill") 1.2.dp else 0.5.dp,
                                        color = if (activeInputTarget == "autofill") Color(0xFFFF6D00) else Color(0xFF333745),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { activeInputTarget = "autofill" }
                                    .padding(8.dp)
                            ) {
                                if (autofillRawInput.isEmpty()) {
                                    Text(
                                        text = "এখানে মেসেজ টেক্সট পেস্ট বা টাইপ করুন...",
                                        color = subTextColor.copy(alpha = 0.6f),
                                        fontSize = 11.sp
                                    )
                                } else {
                                    Text(
                                        text = autofillRawInput,
                                        color = textColor,
                                        fontSize = 11.sp,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = { pasteFromClipboard("autofill") },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2F3A)),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Icon(Icons.Default.ContentPaste, contentDescription = null, tint = Color(0xFFFF9100), modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("ক্লিপবোর্ড পেস্ট", fontSize = 10.5.sp, color = Color.White)
                                }

                                Button(
                                    onClick = {
                                        if (autofillRawInput.isNotBlank()) {
                                            val success = autofillManager.processCopiedText(autofillRawInput, isManualImport = true, isNewCopy = true)
                                            if (success) {
                                                Toast.makeText(context, "৩-ধাপ একাউন্ট সফলভাবে সংরক্ষিত হয়েছে!", Toast.LENGTH_SHORT).show()
                                                autofillRawInput = ""
                                            } else {
                                                Toast.makeText(context, "ফরম্যাট শনাক্ত করা যায়নি! সঠিক ফরম্যাটে লিখুন।", Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            Toast.makeText(context, "টেক্সট লিখুন বা পেস্ট করুন", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6D00)),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("৩-ধাপে সেভ করুন", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }
                    }
                }

                // Section 3: Saved Accounts History
                val allGroups = autofillGroupsList
                if (allGroups.isNotEmpty()) {
                    item {
                        Text(
                            text = "সংরক্ষিত একাউন্টসমূহ (${allGroups.size} টি)",
                            color = textColor,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    items(allGroups) { grp ->
                        val isActive = activeGrp?.id == grp.id
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isActive) Color(0xFF2E2419) else cardBg
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    width = if (isActive) 1.dp else 0.dp,
                                    color = if (isActive) Color(0xFFFF6D00) else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = grp.title,
                                        color = if (isActive) Color(0xFFFF9100) else textColor,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${grp.steps.size} টি ধাপ: ${grp.steps.joinToString(" ➔ ") { it.label }}",
                                        color = subTextColor,
                                        fontSize = 10.sp
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (!isActive) {
                                        Button(
                                            onClick = {
                                                autofillManager.setActiveGroup(grp, resetStep = true)
                                                Toast.makeText(context, "${grp.title} সক্রিয় করা হয়েছে", Toast.LENGTH_SHORT).show()
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6D00)),
                                            shape = RoundedCornerShape(6.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                            modifier = Modifier.height(26.dp)
                                        ) {
                                            Text("সক্রিয় করুন", fontSize = 10.sp, color = Color.White)
                                        }
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }

                                    IconButton(
                                        onClick = {
                                            autofillManager.deleteGroup(grp.id)
                                            Toast.makeText(context, "মুছে ফেলা হয়েছে", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "মুছুন",
                                            tint = Color(0xFFEA4335),
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

/**
 * Popover Strip for Quick Password selection (displayed above toolbar on long press)
 */
@Composable
fun PasswordPopoverStrip(
    passwords: List<String>,
    isDarkTheme: Boolean,
    onSelectPassword: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("moe_keyboard_prefs", Context.MODE_PRIVATE) }
    val defaultPwd = prefs.getString("quick_saved_password", "") ?: ""

    val bgColor = if (isDarkTheme) Color(0xFF1E212B) else Color(0xFFE2E6EC)
    val textColor = if (isDarkTheme) Color.White else Color(0xFF1F2937)
    val subTextColor = if (isDarkTheme) Color(0xFFA0A5B5) else Color(0xFF6B7280)
    val passwordColor = Color(0xFFFF9800)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp)),
        color = bgColor,
        shadowElevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = passwordColor,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "পাসওয়ার্ড:",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
            Spacer(modifier = Modifier.width(6.dp))

            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(passwords) { pwd ->
                    val isDefault = (pwd == defaultPwd)
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onSelectPassword(pwd) },
                        color = if (isDefault) Color(0xFFFF9800).copy(alpha = 0.3f) else passwordColor.copy(alpha = 0.18f),
                        border = if (isDefault) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFB74D)) else null,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            if (isDefault) {
                                Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFB74D), modifier = Modifier.size(11.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                            }
                            Text(
                                text = pwd,
                                color = if (isDefault) Color.White else Color(0xFFFFB74D),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = subTextColor,
                    modifier = Modifier.size(14.dp)
                )
            }

            IconButton(
                onClick = onClose,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = subTextColor,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

/**
 * Popover Strip for Quick Email selection (displayed above toolbar on long press)
 */
@Composable
fun EmailPopoverStrip(
    emails: List<String>,
    isDarkTheme: Boolean,
    onSelectEmail: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("moe_keyboard_prefs", Context.MODE_PRIVATE) }
    val usedRaw = prefs.getString("used_user_emails_set", "") ?: ""
    val usedSet = remember(usedRaw) {
        if (usedRaw.isNotBlank()) usedRaw.split("|||").map { it.trim() }.toSet() else emptySet()
    }

    var showOnlyFresh by remember { mutableStateOf(false) }

    val displayedEmails = remember(emails, usedSet, showOnlyFresh) {
        if (showOnlyFresh) emails.filter { !usedSet.contains(it) } else emails
    }

    val bgColor = if (isDarkTheme) Color(0xFF1E212B) else Color(0xFFE2E6EC)
    val textColor = if (isDarkTheme) Color.White else Color(0xFF1F2937)
    val subTextColor = if (isDarkTheme) Color(0xFFA0A5B5) else Color(0xFF6B7280)
    val emailColor = Color(0xFF7C4DFF)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp)),
        color = bgColor,
        shadowElevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.AlternateEmail,
                contentDescription = null,
                tint = emailColor,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "ইমেল:",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
            Spacer(modifier = Modifier.width(6.dp))

            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(displayedEmails) { email ->
                    val isUsed = usedSet.contains(email)
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onSelectEmail(email) },
                        color = if (isUsed) Color(0xFF323544) else emailColor.copy(alpha = 0.22f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = email,
                                color = if (isUsed) subTextColor else Color(0xFFB388FF),
                                fontSize = 11.sp,
                                fontWeight = if (isUsed) FontWeight.Normal else FontWeight.Bold,
                                maxLines = 1
                            )
                            if (isUsed) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Surface(
                                    color = Color(0xFF20222B),
                                    shape = RoundedCornerShape(3.dp)
                                ) {
                                    Text(
                                        text = "Used",
                                        color = Color(0xFFFF8A80),
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = subTextColor,
                    modifier = Modifier.size(14.dp)
                )
            }

            IconButton(
                onClick = onClose,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = subTextColor,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

/**
 * Popover Strip for Smart 3-Step Credential Autofill (First name -> Login -> Password)
 */
@Composable
fun CredentialAutofillPopoverStrip(
    isDarkTheme: Boolean,
    onSelectStep: (String) -> Unit,
    onResetStep: () -> Unit,
    onOpenSettings: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val manager = remember { SmartCredentialAutofillManager.getInstance(context) }
    var activeGroup by remember { mutableStateOf(manager.getActiveGroup()) }

    DisposableEffect(Unit) {
        val listener = object : SmartCredentialAutofillManager.AutofillUpdateListener {
            override fun onAutofillStateChanged() {
                activeGroup = manager.getActiveGroup()
            }
        }
        manager.addListener(listener)
        onDispose { manager.removeListener(listener) }
    }

    val bgColor = if (isDarkTheme) Color(0xFF1E212B) else Color(0xFFE2E6EC)
    val textColor = if (isDarkTheme) Color.White else Color(0xFF1F2937)
    val subTextColor = if (isDarkTheme) Color(0xFFA0A5B5) else Color(0xFF6B7280)
    val accentColor = Color(0xFFFF6D00)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp)),
        color = bgColor,
        shadowElevation = 3.dp
    ) {
        val group = activeGroup
        if (group == null || group.steps.isEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.FormatListNumbered,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "কোনো ৩-ধাপ ক্রেডেনশিয়াল নেই (কপি করলেই অটো সেভ হবে)",
                        fontSize = 11.sp,
                        color = subTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onOpenSettings, modifier = Modifier.size(24.dp)) {
                        Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings", tint = subTextColor, modifier = Modifier.size(14.dp))
                    }
                    IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = subTextColor, modifier = Modifier.size(14.dp))
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left badge & Reset icon
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(accentColor.copy(alpha = 0.2f))
                        .clickable { onResetStep() }
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FormatListNumbered,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "${group.currentStepIndex + 1}/${group.steps.size}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Scrollable Steps List
                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(group.steps.mapIndexed { idx, s -> Pair(idx, s) }) { (idx, step) ->
                        val isCurrent = idx == group.currentStepIndex
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .border(
                                    width = if (isCurrent) 1.2.dp else 0.dp,
                                    color = if (isCurrent) accentColor else Color.Transparent,
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .clickable {
                                    val isLast = (idx >= group.steps.size - 1)
                                    if (isLast) {
                                        manager.clearActiveGroup()
                                    } else {
                                        manager.setCurrentStepIndex(idx + 1)
                                    }
                                    onSelectStep(step.value)
                                },
                            color = if (isCurrent) accentColor.copy(alpha = 0.25f) else Color(0xFF2C2F3A),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "${idx + 1}. ${step.label}: ",
                                    color = if (isCurrent) accentColor else subTextColor,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (step.label.lowercase().contains("pass")) "••••••" else step.value,
                                    color = if (isCurrent) Color.White else textColor,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = subTextColor,
                        modifier = Modifier.size(14.dp)
                    )
                }

                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = subTextColor,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

/**
 * Popover Strip for Pinned Notes / Messages from Clipboard (displayed above toolbar on long press of Clipboard button)
 */
@Composable
fun PinnedNotesPopoverStrip(
    pinnedNotes: List<String>,
    isDarkTheme: Boolean,
    onSelectNote: (String) -> Unit,
    onOpenClipboard: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = if (isDarkTheme) Color(0xFF1E212B) else Color(0xFFE2E6EC)
    val textColor = if (isDarkTheme) Color.White else Color(0xFF1F2937)
    val subTextColor = if (isDarkTheme) Color(0xFFA0A5B5) else Color(0xFF6B7280)
    val pinColor = Color(0xFF1A73E8)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp)),
        color = bgColor,
        shadowElevation = 3.dp
    ) {
        if (pinnedNotes.isEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onOpenClipboard() }
                ) {
                    Icon(
                        imageVector = Icons.Default.PushPin,
                        contentDescription = null,
                        tint = pinColor,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "কোনো পিন করা মেসেজ নেই (ক্লিপবোর্ডে পিন করুন)",
                        fontSize = 11.sp,
                        color = subTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onOpenClipboard, modifier = Modifier.size(24.dp)) {
                        Icon(painter = androidx.compose.ui.res.painterResource(id = com.example.R.drawable.ic_tool_clipboard), contentDescription = "Clipboard", tint = subTextColor, modifier = Modifier.size(14.dp))
                    }
                    IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = subTextColor, modifier = Modifier.size(14.dp))
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.PushPin,
                    contentDescription = null,
                    tint = pinColor,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "পিন নোটস:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
                Spacer(modifier = Modifier.width(6.dp))

                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(pinnedNotes) { note ->
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { onSelectNote(note) },
                            color = pinColor.copy(alpha = 0.18f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = note,
                                    color = if (isDarkTheme) Color(0xFF90CAF9) else Color(0xFF1565C0),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                IconButton(
                    onClick = onOpenClipboard,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        painter = androidx.compose.ui.res.painterResource(id = com.example.R.drawable.ic_tool_clipboard),
                        contentDescription = "Clipboard",
                        tint = subTextColor,
                        modifier = Modifier.size(14.dp)
                    )
                }

                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = subTextColor,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

