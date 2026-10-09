package com.example.moekeyboard.ime

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class ShortcutItem(
    val code: String,
    val expansion: String
)

@Composable
fun InKeyboardTextExpansion(
    isDarkTheme: Boolean,
    height: Dp,
    onInsertText: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("moe_shortcuts_prefs", Context.MODE_PRIVATE) }

    var shortcuts by remember {
        mutableStateOf(loadShortcuts(prefs))
    }

    var newCode by remember { mutableStateOf("") }
    var newExpansion by remember { mutableStateOf("") }
    var isAdding by remember { mutableStateOf(false) }

    val bgColor = if (isDarkTheme) Color(0xFF0D0E11) else Color(0xFFECEFF1)
    val cardBg = if (isDarkTheme) Color(0xFF1E2024) else Color(0xFFFFFFFF)
    val textColor = if (isDarkTheme) Color(0xFFF2F3F5) else Color(0xFF1F2937)
    val neonGreen = Color(0xFF00FF88)

    fun saveAndReload() {
        saveShortcuts(prefs, shortcuts)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = textColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.ContentCut,
                    contentDescription = null,
                    tint = neonGreen,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "কুইক টেক্সট শর্টকাট",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
            }

            Button(
                onClick = { isAdding = !isAdding },
                colors = ButtonDefaults.buttonColors(containerColor = neonGreen, contentColor = Color.Black),
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier.height(26.dp)
            ) {
                Icon(if (isAdding) Icons.Default.Close else Icons.Default.Add, contentDescription = null, modifier = Modifier.size(12.dp))
                Spacer(modifier = Modifier.width(3.dp))
                Text(if (isAdding) "বন্ধ" else "নতুন শর্টকাট", fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (isAdding) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .border(1.dp, neonGreen.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedTextField(
                            value = newCode,
                            onValueChange = { newCode = it.lowercase().trim() },
                            placeholder = { Text("শর্ট কোড (যেমন: acc)", fontSize = 10.sp) },
                            modifier = Modifier.weight(1f).height(46.dp),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = newExpansion,
                            onValueChange = { newExpansion = it },
                            placeholder = { Text("পুরো লেখা/বিকাশ নম্বর", fontSize = 10.sp) },
                            modifier = Modifier.weight(2f).height(46.dp),
                            singleLine = true
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Button(
                        onClick = {
                            if (newCode.isNotBlank() && newExpansion.isNotBlank()) {
                                shortcuts = shortcuts.filter { it.code != newCode } + ShortcutItem(newCode, newExpansion)
                                saveAndReload()
                                newCode = ""
                                newExpansion = ""
                                isAdding = false
                                Toast.makeText(context, "শর্টকাট সেভ হয়েছে!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = neonGreen, contentColor = Color.Black),
                        modifier = Modifier.fillMaxWidth().height(28.dp),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text("সেভ করুন", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // List of Shortcuts
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(shortcuts) { item ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            onInsertText(item.expansion)
                            Toast.makeText(context, "${item.code} পেস্ট করা হয়েছে!", Toast.LENGTH_SHORT).show()
                        },
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(neonGreen.copy(alpha = 0.2f))
                                    .border(1.dp, neonGreen, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = item.code,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = neonGreen
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = item.expansion,
                                fontSize = 12.sp,
                                color = textColor,
                                maxLines = 1
                            )
                        }

                        IconButton(
                            onClick = {
                                shortcuts = shortcuts.filter { it.code != item.code }
                                saveAndReload()
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

private fun loadShortcuts(prefs: android.content.SharedPreferences): List<ShortcutItem> {
    val defaultList = listOf(
        ShortcutItem("acc", "bKash/Nagad: 01700000000"),
        ShortcutItem("addr", "বাসা নম্বর ১২, রোড ৫, ধানমন্ডি, ঢাকা"),
        ShortcutItem("email", "user@gmail.com")
    )
    val saved = prefs.getString("shortcuts_data", null) ?: return defaultList
    return try {
        saved.split(";;;").mapNotNull {
            val parts = it.split("|||")
            if (parts.size == 2) ShortcutItem(parts[0], parts[1]) else null
        }
    } catch (_: Exception) {
        defaultList
    }
}

private fun saveShortcuts(prefs: android.content.SharedPreferences, list: List<ShortcutItem>) {
    val serialized = list.joinToString(";;;") { "${it.code}|||${it.expansion}" }
    prefs.edit().putString("shortcuts_data", serialized).apply()
}
