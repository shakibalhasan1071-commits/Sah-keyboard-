package com.example.moekeyboard.ime

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap

data class PinnedAppInfo(
    val name: String,
    val packageName: String,
    val iconResVector: ImageVector,
    val themeBg: Color
)

data class KeyboardAppItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val iconBgColor: Color,
    val onClick: () -> Unit
)

@Composable
fun KeyboardAppsPanel(
    isDarkTheme: Boolean,
    height: Dp,
    pinnedToolbarShortcuts: List<String> = emptyList(),
    onTogglePinShortcut: (String) -> Unit = {},
    onOpenBrowser: () -> Unit,
    onOpenTempMail: () -> Unit,
    onOpenVoiceTyping: () -> Unit,
    onOpenClipboard: () -> Unit,
    onOpenEmoji: () -> Unit,
    onOpenCalculator: () -> Unit = {},
    onOpenAiAssistant: () -> Unit = {},
    onOpenTextExpansion: () -> Unit = {},
    onOpenStickersMemes: () -> Unit = {},
    onOpenNotepad: () -> Unit = {},
    onOpenResize: () -> Unit = {},
    onOpenCustomizer: () -> Unit = {},
    onOpenSettings: () -> Unit,
    onReturnToKeys: () -> Unit,
    onHideKeyboard: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val bgColor = if (isDarkTheme) Color(0xFF0D0E11) else Color(0xFFECEFF1)
    val cardBg = if (isDarkTheme) Color(0xFF1E2024) else Color(0xFFFFFFFF)
    val textColor = if (isDarkTheme) Color(0xFFF2F3F5) else Color(0xFF1F2937)
    val accentColor = Color(0xFF00FF88)

    // Load all phone installed launchable apps dynamically
    data class PhoneAppItem(val name: String, val packageName: String, val iconBitmap: androidx.compose.ui.graphics.ImageBitmap?)

    var phoneApps by remember { mutableStateOf<List<PhoneAppItem>>(emptyList()) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val mainIntent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
                val resolveInfos = context.packageManager.queryIntentActivities(mainIntent, 0)
                val list = resolveInfos.map { ri ->
                    val pkg = ri.activityInfo.packageName
                    val name = ri.loadLabel(context.packageManager).toString()
                    val bitmap = try {
                        ri.loadIcon(context.packageManager)?.toBitmap()?.asImageBitmap()
                    } catch (_: Exception) {
                        null
                    }
                    PhoneAppItem(name, pkg, bitmap)
                }.distinctBy { it.packageName }.sortedBy { it.name }
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    phoneApps = list
                }
            } catch (_: Exception) {}
        }
    }

    fun launchApp(pkg: String, name: String) {
        try {
            val pm = context.packageManager
            var intent = pm.getLaunchIntentForPackage(pkg)
            if (intent == null) {
                val queryIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(pkg)
                val list = pm.queryIntentActivities(queryIntent, 0)
                if (list.isNotEmpty()) {
                    val act = list[0].activityInfo
                    intent = Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_LAUNCHER)
                        component = android.content.ComponentName(act.packageName, act.name)
                    }
                }
            }
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                context.startActivity(intent)
            } else {
                Toast.makeText(context, "$name অ্যাপটি ওপেন করা সম্ভব হয়নি", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "$name ওপেন করার সময় ত্রুটি: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    val appItems = listOf(
        KeyboardAppItem(
            id = "ai_assistant",
            title = "Gemini AI অনুবাদ",
            subtitle = "অনুবাদ ও স্মার্ট রিরাইট",
            icon = Icons.Default.AutoAwesome,
            iconBgColor = Color(0xFFA142F4),
            onClick = onOpenAiAssistant
        ),
        KeyboardAppItem(
            id = "shortcuts",
            title = "কুইক শর্টকাট",
            subtitle = "acc, addr টেক্সট এক্সপানশন",
            icon = Icons.Default.ContentCut,
            iconBgColor = Color(0xFFFF9800),
            onClick = onOpenTextExpansion
        ),
        KeyboardAppItem(
            id = "stickers_memes",
            title = "বাংলা স্টিকার ও মিম",
            subtitle = "ফানি বাংলা ডায়ালগ",
            icon = Icons.Default.InsertEmoticon,
            iconBgColor = Color(0xFFE91E63),
            onClick = onOpenStickersMemes
        ),
        KeyboardAppItem(
            id = "calculator",
            title = "ক্যালকুলেটর ও কারেন্সি",
            subtitle = "হিসাব ও টাকা/ডলার রূপান্তর",
            icon = Icons.Default.Calculate,
            iconBgColor = Color(0xFF00C853),
            onClick = onOpenCalculator
        ),
        KeyboardAppItem(
            id = "customizer",
            title = "কাস্টমাইজেশন",
            subtitle = "থিম, কালার ও লেআউট",
            icon = Icons.Default.Palette,
            iconBgColor = Color(0xFF9C27B0),
            onClick = onOpenCustomizer
        ),
        KeyboardAppItem(
            id = "resize",
            title = "কিবোর্ড রিসাইজ",
            subtitle = "আকার ছোট বা বড় করুন",
            icon = Icons.Default.AspectRatio,
            iconBgColor = Color(0xFF00897B),
            onClick = onOpenResize
        ),
        KeyboardAppItem(
            id = "notepad",
            title = "নোটপ্যাড (Notepad)",
            subtitle = "দ্রুত নোট লিখে রাখুন",
            icon = Icons.Default.EditNote,
            iconBgColor = Color(0xFF455A64),
            onClick = onOpenNotepad
        ),
        KeyboardAppItem(
            id = "browser",
            title = "মিনি ব্রাউজার",
            subtitle = "কিবোর্ডের ভেতরেই সার্চ",
            icon = Icons.Default.Language,
            iconBgColor = Color(0xFF1A73E8),
            onClick = onOpenBrowser
        ),
        KeyboardAppItem(
            id = "temp_mail",
            title = "Mail.tm Temp Mail",
            subtitle = "১০০% ওয়ান-টাইম OTP",
            icon = Icons.Default.MarkEmailRead,
            iconBgColor = Color(0xFF0F9D58),
            onClick = onOpenTempMail
        ),
        KeyboardAppItem(
            id = "voice",
            title = "ভয়েস টাইপিং",
            subtitle = "অনবরত স্পিচ টাইপিং",
            icon = Icons.Default.Mic,
            iconBgColor = Color(0xFFEA4335),
            onClick = onOpenVoiceTyping
        ),
        KeyboardAppItem(
            id = "clipboard",
            title = "ক্লিপবোর্ড ও পিন",
            subtitle = "কপি ও পিন করা মেসেজ",
            icon = Icons.Default.ContentPaste,
            iconBgColor = Color(0xFFF9AB00),
            onClick = onOpenClipboard
        ),
        KeyboardAppItem(
            id = "emoji",
            title = "ইমোজি ও স্টিকার",
            subtitle = "স্মাইলি ও ইমোটিকন",
            icon = Icons.Default.SentimentSatisfied,
            iconBgColor = Color(0xFFA142F4),
            onClick = onOpenEmoji
        ),
        KeyboardAppItem(
            id = "settings",
            title = "সেটিংস",
            subtitle = "ক্লাউড ব্যাকআপ ও গুগল লগইন",
            icon = Icons.Default.Settings,
            iconBgColor = Color(0xFF5F6368),
            onClick = onOpenSettings
        )
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp, start = 2.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Apps,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "কিবোর্ড অ্যাপস ও টুলস",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(
                    onClick = onReturnToKeys,
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor, contentColor = Color.Black),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 1.dp),
                    modifier = Modifier.height(24.dp)
                ) {
                    Icon(Icons.Default.Keyboard, contentDescription = null, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("কিবোর্ড", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }

                FilledTonalButton(
                    onClick = onHideKeyboard,
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 1.dp),
                    modifier = Modifier.height(24.dp)
                ) {
                    Icon(Icons.Default.KeyboardHide, contentDescription = "Hide Keyboard", modifier = Modifier.size(12.dp))
                }
            }
        }

        // Phone Installed Apps Quick Launch Bar
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            shape = RoundedCornerShape(10.dp),
            color = cardBg
        ) {
            Column(modifier = Modifier.padding(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "📱 ফোনের সকল অ্যাপস শর্টকাট (Phone Apps)",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    phoneApps.forEach { app ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .width(52.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { launchApp(app.packageName, app.name) }
                                .padding(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .background(accentColor.copy(alpha = 0.15f), CircleShape)
                                    .border(1.dp, accentColor.copy(alpha = 0.4f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                if (app.iconBitmap != null) {
                                    Image(
                                        bitmap = app.iconBitmap,
                                        contentDescription = app.name,
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Apps,
                                        contentDescription = app.name,
                                        tint = accentColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = app.name,
                                fontSize = 9.sp,
                                color = textColor,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        // Feature Grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(appItems, key = { it.id }) { item ->
                val isPinned = pinnedToolbarShortcuts.contains(item.id)
                val isPinnable = item.id != "settings" && item.id != "resize"

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .border(
                            width = if (isPinned) 1.5.dp else 0.dp,
                            color = if (isPinned) Color(0xFFFFB300) else Color.Transparent,
                            shape = RoundedCornerShape(10.dp)
                        )
                        .clickable { item.onClick() },
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg)
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        // Top-right Pin icon toggle
                        if (isPinnable) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(if (isPinned) Color(0xFFFFB300).copy(alpha = 0.25f) else Color.Transparent)
                                    .clickable { onTogglePinShortcut(item.id) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PushPin,
                                    contentDescription = "Pin to Toolbar",
                                    tint = if (isPinned) Color(0xFFFFB300) else textColor.copy(alpha = 0.35f),
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp, horizontal = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(item.iconBgColor, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.title,
                                    tint = Color.White,
                                    modifier = Modifier.size(17.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = item.title,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = textColor,
                                textAlign = TextAlign.Center,
                                maxLines = 1
                            )

                            Text(
                                text = if (isPinned) "📌 পিন করা আছে" else item.subtitle,
                                fontSize = 8.sp,
                                color = if (isPinned) Color(0xFFFFB300) else textColor.copy(alpha = 0.6f),
                                textAlign = TextAlign.Center,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}
