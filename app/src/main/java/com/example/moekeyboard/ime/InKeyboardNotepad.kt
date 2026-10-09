package com.example.moekeyboard.ime

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import com.example.ui.theme.HindSiliguri
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.moekeyboard.tempmail.TempMailManager

data class KeyboardNote(
    val id: String,
    val title: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false
)

@Composable
fun InKeyboardNotepad(
    isDarkTheme: Boolean,
    height: Dp,
    onInsertText: (String) -> Unit,
    onClose: () -> Unit,
    onRegisterInputHandler: (((String?, SpecialKeyType?) -> Unit) -> Unit)? = null,
    isForImportantNote: Boolean = false,
    onImportantNoteSaved: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val tempMailManager = remember { TempMailManager.getInstance(context) }
    val bgColor = if (isDarkTheme) Color(0xFF1B1C1E) else Color(0xFFF3F4F6)
    val cardBg = if (isDarkTheme) Color(0xFF282A2E) else Color(0xFFFFFFFF)
    val inputBg = if (isDarkTheme) Color(0xFF1E1F22) else Color(0xFFE5E7EB)
    val textColor = if (isDarkTheme) Color.White else Color.Black
    val textMuted = if (isDarkTheme) Color(0xFF9CA3AF) else Color(0xFF6B7280)
    val accentColor = Color(0xFF10B981) // Modern Emerald Green accent for Secret Notepad

    var notes by remember { mutableStateOf(emptyList<KeyboardNote>()) }
    var searchQuery by remember { mutableStateOf("") }
    var isEditing by remember { mutableStateOf(false) }
    var currentNoteId by remember { mutableStateOf<String?>(null) }
    var inputTitle by remember { mutableStateOf("") }
    var inputContent by remember { mutableStateOf("") }
    var activeField by remember { mutableStateOf("content") } // "title", "content", or "search"

    fun loadAllNotes() {
        notes = loadNotesFromPrefs(context)
    }

    LaunchedEffect(Unit) {
        loadAllNotes()
    }

    // Register input handler so keyboard soft keys directly type into active note fields
    LaunchedEffect(isEditing, activeField) {
        onRegisterInputHandler?.invoke { char, special ->
            if (char != null) {
                if (isEditing) {
                    if (activeField == "title") {
                        inputTitle += char
                    } else {
                        inputContent += char
                    }
                } else {
                    searchQuery += char
                }
            }
            if (special != null) {
                when (special) {
                    SpecialKeyType.BACKSPACE -> {
                        if (isEditing) {
                            if (activeField == "title" && inputTitle.isNotEmpty()) {
                                inputTitle = inputTitle.dropLast(1)
                            } else if (inputContent.isNotEmpty()) {
                                inputContent = inputContent.dropLast(1)
                            }
                        } else if (searchQuery.isNotEmpty()) {
                            searchQuery = searchQuery.dropLast(1)
                        }
                    }
                    SpecialKeyType.SPACE -> {
                        if (isEditing) {
                            if (activeField == "title") inputTitle += " " else inputContent += " "
                        } else {
                            searchQuery += " "
                        }
                    }
                    SpecialKeyType.ENTER -> {
                        if (isEditing) {
                            if (activeField == "title") {
                                activeField = "content"
                            } else {
                                inputContent += "\n"
                            }
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    val filteredNotes = remember(notes, searchQuery) {
        val list = if (searchQuery.isBlank()) notes else notes.filter {
            it.title.contains(searchQuery, ignoreCase = true) || it.content.contains(searchQuery, ignoreCase = true)
        }
        list.sortedWith(compareByDescending<KeyboardNote> { it.isPinned }.thenByDescending { it.timestamp })
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(bgColor)
            .padding(8.dp)
    ) {
        // Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = "Notepad Icon",
                    tint = accentColor,
                    modifier = Modifier.size(20.dp).padding(end = 4.dp)
                )
                Text(
                    text = "সিক্রেট নোটপ্যাড",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    fontFamily = HindSiliguri
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (!isEditing) {
                    // Prominent Plus Button for New Note (+)
                    Button(
                        onClick = {
                            isEditing = true
                            currentNoteId = null
                            inputTitle = ""
                            inputContent = ""
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "New Note",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "নতুন নোট",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Panel",
                        tint = textMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        if (isEditing) {
            // Note Editor View
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(cardBg, RoundedCornerShape(12.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Title Field
                BasicTextField(
                    value = inputTitle,
                    onValueChange = { inputTitle = it },
                    textStyle = TextStyle(color = textColor, fontSize = 15.sp, fontWeight = FontWeight.Bold),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(inputBg)
                        .border(
                            width = if (activeField == "title") 1.5.dp else 0.dp,
                            color = if (activeField == "title") accentColor else Color.Transparent,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { activeField = "title" }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    decorationBox = { innerTextField ->
                        if (inputTitle.isEmpty()) {
                            Text("নোটের শিরোনাম (Title)...", color = textMuted.copy(alpha = 0.6f), fontSize = 15.sp)
                        }
                        innerTextField()
                    }
                )

                // Content Field
                BasicTextField(
                    value = inputContent,
                    onValueChange = { inputContent = it },
                    textStyle = TextStyle(color = textColor, fontSize = 14.sp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(inputBg)
                        .border(
                            width = if (activeField == "content") 1.5.dp else 0.dp,
                            color = if (activeField == "content") accentColor else Color.Transparent,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { activeField = "content" }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    decorationBox = { innerTextField ->
                        if (inputContent.isEmpty()) {
                            Text("এখানে আপনার সাধারণ বা প্রয়োজনীয় নোট লিখুন...", color = textMuted.copy(alpha = 0.6f), fontSize = 14.sp)
                        }
                        innerTextField()
                    }
                )

                // Editor Bottom Action Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(
                            onClick = {
                                if (inputContent.isNotBlank()) {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    val clip = android.content.ClipData.newPlainText("note", inputContent)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "নোট কপি করা হয়েছে", Toast.LENGTH_SHORT).show()
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(16.dp), tint = textMuted)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("কপি", color = textMuted, fontSize = 12.sp)
                        }

                        TextButton(
                            onClick = {
                                if (inputContent.isNotBlank()) {
                                    onInsertText(inputContent)
                                    Toast.makeText(context, "কিবোর্ডে পেস্ট করা হয়েছে", Toast.LENGTH_SHORT).show()
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.Input, contentDescription = "Insert", modifier = Modifier.size(16.dp), tint = accentColor)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("পেস্ট", color = accentColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { isEditing = false },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                        ) {
                            Text("বাতিল", color = textMuted, fontSize = 13.sp)
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        Button(
                            onClick = {
                                if (inputContent.isBlank()) {
                                    Toast.makeText(context, "নোটের বিবরণ খালি রাখা যাবে না", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                if (isForImportantNote) {
                                    tempMailManager.saveImportantNote(inputContent)
                                    onImportantNoteSaved?.invoke()
                                    onClose()
                                    return@Button
                                }
                                val id = currentNoteId ?: System.currentTimeMillis().toString()
                                val title = inputTitle.ifBlank { "শিরোনামহীন নোট" }
                                val existingNote = notes.find { it.id == id }
                                val isPinned = existingNote?.isPinned ?: false
                                val newNote = KeyboardNote(id, title, inputContent, System.currentTimeMillis(), isPinned)

                                val list = notes.toMutableList()
                                if (currentNoteId != null) {
                                    val idx = list.indexOfFirst { it.id == currentNoteId }
                                    if (idx != -1) list[idx] = newNote
                                } else {
                                    list.add(0, newNote)
                                }

                                saveNotesToPrefs(context, list)
                                notes = list
                                isEditing = false
                                Toast.makeText(context, "নোট সংরক্ষিত হয়েছে", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("সংরক্ষণ", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            // Notes List View
            if (notes.isNotEmpty()) {
                // Quick Search Bar
                BasicTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    textStyle = TextStyle(color = textColor, fontSize = 13.sp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                        .background(cardBg, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    decorationBox = { innerTextField ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Search, contentDescription = "Search", tint = textMuted, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(modifier = Modifier.weight(1f)) {
                                if (searchQuery.isEmpty()) {
                                    Text("নোট খুঁজুন...", color = textMuted.copy(alpha = 0.6f), fontSize = 13.sp)
                                }
                                innerTextField()
                            }
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(20.dp)) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear", tint = textMuted, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                )
            }

            if (filteredNotes.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.NoteAdd,
                            contentDescription = "No Notes",
                            tint = accentColor.copy(alpha = 0.7f),
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (searchQuery.isEmpty()) "কোনো নোট সংরক্ষিত নেই।" else "কোনো নোট পাওয়া যায়নি।",
                            color = textColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "নতুন সাধারণ নোট তৈরি করতে নিচের '+' বাটন চাপুন।",
                            color = textMuted,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                isEditing = true
                                currentNoteId = null
                                inputTitle = ""
                                inputContent = ""
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("নতুন নোট তৈরি করুন (+)")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filteredNotes, key = { it.id }) { note ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    onInsertText(note.content)
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = if (note.isPinned) accentColor.copy(alpha = 0.12f) else cardBg
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (note.isPinned) {
                                            Icon(
                                                imageVector = Icons.Default.PushPin,
                                                contentDescription = "Pinned",
                                                tint = accentColor,
                                                modifier = Modifier.size(13.dp).padding(end = 3.dp)
                                            )
                                        }
                                        Text(
                                            text = note.title,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = textColor,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = note.content,
                                        fontSize = 12.sp,
                                        color = textMuted,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = SimpleDateFormat("d MMM, yyyy • HH:mm", Locale.getDefault()).format(Date(note.timestamp)),
                                        fontSize = 10.sp,
                                        color = textMuted.copy(alpha = 0.7f)
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                                         // Star (Important Note) button
                                                                         IconButton(
                                                                             onClick = {
                                                                                 tempMailManager.saveImportantNote(note.content)
                                                                                 Toast.makeText(context, "স্টার (ইম্পর্টেন্ট নোট) লিস্টে যোগ করা হয়েছে!", Toast.LENGTH_SHORT).show()
                                                                             },
                                                                             modifier = Modifier.size(28.dp)
                                                                         ) {
                                                                             Icon(
                                                                                 imageVector = Icons.Default.Star,
                                                                                 contentDescription = "Add to Important Notes",
                                                                                 tint = Color(0xFFE91E63),
                                                                                 modifier = Modifier.size(16.dp)
                                                                             )
                                                                         }

                                    // Pin button
                                    IconButton(
                                        onClick = {
                                            val list = notes.toMutableList()
                                            val idx = list.indexOfFirst { it.id == note.id }
                                            if (idx != -1) {
                                                list[idx] = note.copy(isPinned = !note.isPinned)
                                                saveNotesToPrefs(context, list)
                                                notes = list
                                            }
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PushPin,
                                            contentDescription = "Pin Note",
                                            tint = if (note.isPinned) accentColor else textMuted.copy(alpha = 0.4f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    // Edit button
                                    IconButton(
                                        onClick = {
                                            currentNoteId = note.id
                                            inputTitle = note.title
                                            inputContent = note.content
                                            isEditing = true
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Edit Note",
                                            tint = accentColor,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    // Delete button
                                    IconButton(
                                        onClick = {
                                            val list = notes.toMutableList()
                                            list.removeAll { it.id == note.id }
                                            saveNotesToPrefs(context, list)
                                            notes = list
                                            Toast.makeText(context, "নোট মুছে ফেলা হয়েছে", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete Note",
                                            tint = Color(0xFFEF4444),
                                            modifier = Modifier.size(16.dp)
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

private fun loadNotesFromPrefs(context: Context): List<KeyboardNote> {
    val prefs = context.getSharedPreferences("moe_keyboard_notes", Context.MODE_PRIVATE)
    val raw = prefs.getString("notes_data", "") ?: ""
    if (raw.isBlank()) return emptyList()
    return raw.split("##NOTE_SEP##").mapNotNull { block ->
        val parts = block.split("##FIELD_SEP##")
        if (parts.size >= 3) {
            val id = parts[0]
            val title = parts[1]
            val content = parts[2]
            val timestamp = parts.getOrNull(3)?.toLongOrNull() ?: System.currentTimeMillis()
            val isPinned = parts.getOrNull(4)?.toBooleanStrictOrNull() ?: false
            KeyboardNote(id = id, title = title, content = content, timestamp = timestamp, isPinned = isPinned)
        } else null
    }
}

private fun saveNotesToPrefs(context: Context, notes: List<KeyboardNote>) {
    val prefs = context.getSharedPreferences("moe_keyboard_notes", Context.MODE_PRIVATE)
    val serialized = notes.joinToString("##NOTE_SEP##") { note ->
        "${note.id}##FIELD_SEP##${note.title}##FIELD_SEP##${note.content}##FIELD_SEP##${note.timestamp}##FIELD_SEP##${note.isPinned}"
    }
    prefs.edit().putString("notes_data", serialized).apply()
}

