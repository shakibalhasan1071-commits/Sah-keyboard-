package com.example.moekeyboard.ime

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ImportantNotesPopoverStrip(
    notes: List<String>,
    activeStarredNote: String?,
    isDarkTheme: Boolean,
    onSelectNote: (String) -> Unit,
    onSetStarred: (String) -> Unit,
    onDeleteNote: (String) -> Unit,
    onAddNoteDirectly: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val bgColor = if (isDarkTheme) Color(0xFF1E1E1E) else Color(0xFFF5F5F5)
    val itemBg = if (isDarkTheme) Color(0xFF2C2C2C) else Color(0xFFFFFFFF)
    val activeItemBg = if (isDarkTheme) Color(0xFF3E2333) else Color(0xFFFCE4EC)
    val textColor = if (isDarkTheme) Color.White else Color.Black
    val textMuted = if (isDarkTheme) Color(0xFF9E9E9E) else Color(0xFF757575)
    val accentColor = Color(0xFFE91E63)

    var isAddingNote by remember { mutableStateOf(false) }
    var newNoteText by remember { mutableStateOf("") }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(bgColor, RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isAddingNote) {
            // Inline Add Mode
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(18.dp)
            )

            Spacer(Modifier.width(6.dp))

            BasicTextField(
                value = newNoteText,
                onValueChange = { newNoteText = it },
                textStyle = TextStyle(color = textColor, fontSize = 12.sp),
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isDarkTheme) Color(0xFF2D2D2D) else Color(0xFFE0E0E0))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                decorationBox = { innerTextField ->
                    if (newNoteText.isEmpty()) {
                        Text(
                            text = "গুরুত্বপূর্ণ লেখা লিখুন বা পেস্ট করুন...",
                            color = textMuted,
                            fontSize = 11.sp
                        )
                    }
                    innerTextField()
                }
            )

            // Paste from clipboard button
            IconButton(
                onClick = {
                    try {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        val clip = cm?.primaryClip?.getItemAt(0)?.text?.toString()
                        if (!clip.isNullOrBlank()) {
                            newNoteText = clip.trim()
                        } else {
                            Toast.makeText(context, "ক্লিপবোর্ড খালি", Toast.LENGTH_SHORT).show()
                        }
                    } catch (_: Exception) {}
                },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(Icons.Default.ContentPaste, contentDescription = "Paste", tint = accentColor, modifier = Modifier.size(16.dp))
            }

            // Confirm / Save button
            IconButton(
                onClick = {
                    val trimmed = newNoteText.trim()
                    if (trimmed.isNotBlank()) {
                        onAddNoteDirectly(trimmed)
                        newNoteText = ""
                        isAddingNote = false
                    } else {
                        Toast.makeText(context, "লেখা লিখুন বা পেস্ট করুন", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = "Save", tint = Color(0xFF4CAF50), modifier = Modifier.size(18.dp))
            }

            // Cancel button
            IconButton(
                onClick = {
                    newNoteText = ""
                    isAddingNote = false
                },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Cancel", tint = textMuted, modifier = Modifier.size(16.dp))
            }
        } else {
            // Normal List Mode
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(18.dp)
            )

            Spacer(Modifier.width(6.dp))

            IconButton(
                onClick = { isAddingNote = true },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(Icons.Default.Add, "Add Note", tint = accentColor, modifier = Modifier.size(18.dp))
            }

            Spacer(Modifier.width(6.dp))

            if (notes.isEmpty()) {
                Text(
                    "কোনো স্টার নোট নেই, '+' চাপুন",
                    color = textColor.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    modifier = Modifier.weight(1f)
                )
            } else {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    notes.forEach { note ->
                        val isStarred = (note == activeStarredNote)
                        Surface(
                            modifier = Modifier
                                .height(32.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { onSelectNote(note) },
                            color = if (isStarred) activeItemBg else itemBg,
                            tonalElevation = 2.dp,
                            shadowElevation = 1.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Star toggle icon: sets this item as active default
                                Icon(
                                    imageVector = if (isStarred) Icons.Default.Star else Icons.Outlined.StarBorder,
                                    contentDescription = "Set Starred",
                                    tint = if (isStarred) accentColor else textMuted,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable { onSetStarred(note) }
                                )

                                Spacer(Modifier.width(4.dp))

                                Text(
                                    text = note,
                                    color = if (isStarred) accentColor else textColor,
                                    fontSize = 11.sp,
                                    fontWeight = if (isStarred) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = 120.dp)
                                )

                                Spacer(Modifier.width(6.dp))

                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = Color.Red.copy(alpha = 0.6f),
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clickable { onDeleteNote(note) }
                                )
                            }
                        }
                    }
                }
            }

            IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Close, null, tint = textColor.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
            }
        }
    }
}
