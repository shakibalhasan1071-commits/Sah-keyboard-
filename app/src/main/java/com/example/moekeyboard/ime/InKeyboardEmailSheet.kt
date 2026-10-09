package com.example.moekeyboard.ime

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Input
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.moekeyboard.tempmail.TempMailManager
import com.example.moekeyboard.tempmail.SavedTempMailAccount

@Composable
fun InKeyboardEmailSheet(
    isDarkTheme: Boolean,
    height: Dp,
    onInsertText: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val tempMailManager = remember { TempMailManager.getInstance(context) }
    var sheetAccountsList by remember { mutableStateOf(tempMailManager.getSheetAccounts()) }

    val bgColor = Color(0xFF0D0E12)
    val cardBg = Color(0xFF222530)
    val accentGreen = Color(0xFF00E676)
    val textColor = Color(0xFFFFFFFF)
    val subTextColor = Color(0xFFA0A5B5)

    fun handleDeleteSheetItem(email: String) {
        tempMailManager.deleteSheetAccount(email)
        sheetAccountsList = tempMailManager.getSheetAccounts()
    }

    fun handleCopyAllSheet() {
        if (sheetAccountsList.isEmpty()) return
        val all = sheetAccountsList.joinToString("\n") { it.email }
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("All Emails", all))
        Toast.makeText(context, "সবগুলো ইমেল একসাথে কপি করা হয়েছে", Toast.LENGTH_SHORT).show()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(bgColor)
    ) {
        // Toolbar for Sheet
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.TableChart,
                    contentDescription = null,
                    tint = Color(0xFF64B5F6),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "সেভ করা ইমেল শীট (${sheetAccountsList.size})",
                    color = textColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { handleCopyAllSheet() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2)),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, null, tint = Color.White, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("সব কপি", fontSize = 10.sp, color = Color.White)
                }
                
                IconButton(
                    onClick = {
                        tempMailManager.clearSheet()
                        sheetAccountsList = emptyList()
                        Toast.makeText(context, "শীট ক্লিয়ার করা হয়েছে", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.DeleteSweep, "Clear all", tint = Color(0xFFEF9A9A), modifier = Modifier.size(18.dp))
                }
                
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.Close, "Close", tint = subTextColor, modifier = Modifier.size(18.dp))
                }
            }
        }

        if (sheetAccountsList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("শীটে কোনো ইমেল সেভ করা নেই", color = subTextColor, fontSize = 12.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(sheetAccountsList) { acc ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = cardBg.copy(alpha = 0.7f)),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(acc.email, color = textColor, fontSize = 12.sp, fontWeight = FontWeight.Medium, fontFamily = FontFamily.Monospace)
                                Text(acc.createdAt, color = subTextColor, fontSize = 10.sp)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { 
                                    onInsertText(acc.email)
                                    Toast.makeText(context, "ইমেল বসানো হয়েছে", Toast.LENGTH_SHORT).show()
                                }, modifier = Modifier.size(26.dp)) {
                                    Icon(Icons.AutoMirrored.Filled.Input, "Insert", tint = accentGreen, modifier = Modifier.size(16.dp))
                                }
                                IconButton(onClick = { handleDeleteSheetItem(acc.email) }, modifier = Modifier.size(26.dp)) {
                                    Icon(Icons.Default.DeleteOutline, "Delete", tint = Color(0xFFEF5350), modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
