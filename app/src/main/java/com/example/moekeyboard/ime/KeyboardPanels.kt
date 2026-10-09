package com.example.moekeyboard.ime

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap

@Composable
fun DraggablePanelContainer(
    height: Dp,
    onHeightChange: (Dp) -> Unit,
    onClose: () -> Unit,
    isDarkTheme: Boolean,
    content: @Composable () -> Unit
) {
    val bgColor = if (isDarkTheme) Color(0xFF1E1F22) else Color(0xFFF0F2F5)
    val dragHandleColor = if (isDarkTheme) Color.White.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.25f)
    val accentColor = Color(0xFF1A73E8)
    val textColor = if (isDarkTheme) Color.White else Color.Black
    val subTextColor = if (isDarkTheme) Color(0xFFA0A0A0) else Color(0xFF606060)
    
    val density = LocalDensity.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .background(bgColor)
    ) {
        // Drag Handle & Quick Height Controls Area
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp)
                .background(bgColor)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left Spacer to balance the right icons for perfect horizontal centering of the handle
            Spacer(modifier = Modifier.width(52.dp))

            // Center: Drag Bar Handle (Interactive Draggable with dynamic height state)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .pointerInput(height) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val deltaDp = with(density) { dragAmount.y.toDp() }
                            val newHeight = (height - deltaDp).coerceIn(160.dp, 540.dp)
                            onHeightChange(newHeight)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(48.dp)
                        .height(5.dp)
                        .clip(CircleShape)
                        .background(dragHandleColor)
                )
            }

            // Right: Toggle Expand & Close
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                IconButton(
                    onClick = {
                        val next = if (height >= 450.dp) 260.dp else 480.dp
                        onHeightChange(next)
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = if (height >= 450.dp) Icons.Default.UnfoldLess else Icons.Default.OpenInFull,
                        contentDescription = "Toggle Height",
                        tint = textColor.copy(alpha = 0.7f),
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
                        tint = textColor.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
        
        Box(modifier = Modifier.fillMaxSize()) {
            content()
        }
    }
}

@Composable
fun HeightSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float>,
    isDarkTheme: Boolean
) {
    val textColor = if (isDarkTheme) Color.White else Color.Black
    val accentColor = if (isDarkTheme) Color(0xFF8AB4F8) else Color(0xFF1A73E8)

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, fontSize = 11.sp, color = textColor.copy(alpha = 0.7f))
            Text(text = "${value.toInt()}dp", fontSize = 11.sp, color = accentColor, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = accentColor,
                activeTrackColor = accentColor,
                inactiveTrackColor = accentColor.copy(alpha = 0.2f)
            ),
            modifier = Modifier.height(24.dp)
        )
    }
}

@Composable
fun KeyboardResizeOverlay(
    currentHeightPref: String,
    isDarkTheme: Boolean,
    syncHeights: Boolean,
    bengaliHeight: Float,
    englishHeight: Float,
    onSyncChanged: (Boolean) -> Unit,
    onBengaliHeightChanged: (Float) -> Unit,
    onEnglishHeightChanged: (Float) -> Unit,
    onHeightSelected: (String) -> Unit,
    onReset: () -> Unit,
    onDone: () -> Unit
) {
    val cardBg = if (isDarkTheme) Color(0xFF25272C) else Color(0xFFFFFFFF)
    val textColor = if (isDarkTheme) Color(0xFFF1F2F6) else Color(0xFF1F2937)
    val accentColor = if (isDarkTheme) Color(0xFF8AB4F8) else Color(0xFF1A73E8)
    val greenButtonColor = Color(0xFF0F9D58)

    val heightOptions = listOf(
        Pair("extra_small", "অতি ছোট"),
        Pair("small", "ছোট"),
        Pair("normal", "স্বাভাবিক"),
        Pair("medium", "মাঝারি"),
        Pair("tall", "বড়"),
        Pair("extra_tall", "অতি বড়")
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val density = LocalDensity.current
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp) // Larger drag area
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val deltaDp = with(density) { dragAmount.y.toDp().value }
                        val multiplier = 1.0f // Faster sensitivity
                        val newBengali = (bengaliHeight - (deltaDp * multiplier)).coerceIn(25f, 100f)
                        val newEnglish = (englishHeight - (deltaDp * multiplier * 1.5f)).coerceIn(40f, 120f)
                        if (syncHeights) {
                            onBengaliHeightChanged(newBengali)
                            onEnglishHeightChanged(newEnglish)
                        } else {
                            onBengaliHeightChanged(newBengali)
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .height(5.dp)
                    .clip(CircleShape)
                    .background(accentColor)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(2.dp, accentColor, RoundedCornerShape(12.dp)),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = cardBg)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.OpenWith,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "কিবোর্ড আকার সমন্বয় করুন",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = textColor
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("একসাথে পরিবর্তন", fontSize = 11.sp, color = textColor)
                    Switch(
                        checked = syncHeights,
                        onCheckedChange = onSyncChanged,
                        modifier = Modifier.scale(0.7f),
                        colors = SwitchDefaults.colors(checkedThumbColor = accentColor)
                    )
                }

                HeightSlider("বাংলা কী উচ্চতা", bengaliHeight, onBengaliHeightChanged, 30f..80f, isDarkTheme)
                HeightSlider("ইংরেজি কী উচ্চতা", englishHeight, onEnglishHeightChanged, 45f..100f, isDarkTheme)

                Spacer(modifier = Modifier.height(2.dp))

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(heightOptions) { opt ->
                        FilterChip(
                            selected = currentHeightPref == opt.first,
                            onClick = { onHeightSelected(opt.first) },
                            label = { Text(opt.second, fontSize = 10.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = accentColor,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onReset,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("রিসেট", fontSize = 11.sp)
                    }
                    Button(
                        onClick = onDone,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = greenButtonColor),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("সম্পন্ন", fontSize = 11.sp, color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun KeyboardSuggestionStrip(
    suggestions: List<String>,
    isDarkTheme: Boolean,
    onSelectSuggestion: (String) -> Unit,
    isBengali: Boolean = true,
    modifier: Modifier = Modifier
) {
    // Pure Black permanent suggestion bar as requested by user
    val bgColor = Color(0xFF000000)
    val textColor = Color(0xFFFFFFFF)
    val subTextColor = Color(0xFF9E9E9E)
    val dividerColor = Color(0x33FFFFFF)
    val chipBg = Color(0xFF18191C)
    val firstChipBg = Color(0xFF23262D)
    val accentColor = Color(0xFF00E5FF)

    // Fallback list of quick symbols, punctuations and quick emojis when not actively predicting words
    val fallbackItems = if (isBengali) {
        listOf("।", ",", ".", "?", "!", "@", "❤️", "👍", "😂", "🔥", "✨", "😊", "—", ":)")
    } else {
        listOf("'", ",", ".", "?", "!", "@", "❤️", "👍", "😂", "🔥", "✨", "😊", "—", ":)")
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp),
        color = bgColor
    ) {
        val displayItems = if (suggestions.isNotEmpty()) suggestions else fallbackItems

        LazyRow(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            contentPadding = PaddingValues(horizontal = 6.dp)
        ) {
            itemsIndexed(displayItems) { index, item ->
                val isRealWord = suggestions.isNotEmpty()
                val isFirst = isRealWord && index == 0
                Box(
                    modifier = Modifier
                        .height(26.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isFirst) firstChipBg else (if (isRealWord) chipBg else Color.Transparent))
                        .clickable { onSelectSuggestion(item) }
                        .padding(horizontal = if (isRealWord) 10.dp else 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = item,
                        color = if (isFirst) accentColor else (if (isRealWord) textColor else subTextColor),
                        fontSize = if (isRealWord) 13.5.sp else 14.sp,
                        fontWeight = if (isFirst) FontWeight.Bold else FontWeight.Medium,
                        fontFamily = LocalKeyboardFont.current,
                        maxLines = 1
                    )
                }

                if (index < displayItems.size - 1) {
                    VerticalDivider(
                        modifier = Modifier
                            .height(14.dp)
                            .padding(horizontal = 2.dp),
                        color = dividerColor
                    )
                }
            }
        }
    }
}

data class ShortcutPhoneApp(
    val name: String,
    val packageName: String,
    val iconBitmap: androidx.compose.ui.graphics.ImageBitmap?
)

@Composable
fun InKeyboardShortcutPicker(
    isDarkTheme: Boolean,
    pinnedShortcuts: List<String>,
    onTogglePin: (String) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val bgColor = if (isDarkTheme) Color(0xFF131418) else Color(0xFFF1F3F5)
    val cardBg = if (isDarkTheme) Color(0xFF1E2026) else Color(0xFFFFFFFF)
    val textColor = if (isDarkTheme) Color.White else Color(0xFF111827)
    val subTextColor = if (isDarkTheme) Color(0xFFA0A3AD) else Color(0xFF6B7280)
    val accentColor = Color(0xFF00E5FF)
    val pinGold = Color(0xFFFFB300)

    var selectedTab by remember { mutableStateOf(0) } // 0 = Phone Apps, 1 = Keyboard Tools
    var searchQuery by remember { mutableStateOf("") }
    var phoneApps by remember { mutableStateOf<List<ShortcutPhoneApp>>(emptyList()) }
    var isLoadingApps by remember { mutableStateOf(true) }

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
                    ShortcutPhoneApp(name, pkg, bitmap)
                }.distinctBy { it.packageName }.sortedBy { it.name }
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    phoneApps = list
                    isLoadingApps = false
                }
            } catch (_: Exception) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    isLoadingApps = false
                }
            }
        }
    }

    val availableTools = listOf(
        Triple("browser", "মিনি ব্রাউজার", Icons.Default.Language),
        Triple("temp_mail", "টেম্প মেইল", Icons.Default.MarkEmailRead),
        Triple("notepad", "সিক্রেট নোটপ্যাড", Icons.Default.NoteAlt),
        Triple("clipboard", "ক্লিপবোর্ড ও পিন", Icons.Default.ContentPaste),
        Triple("calculator", "ক্যালকুলেটর", Icons.Default.Calculate),
        Triple("ai_assistant", "এআই অনুবাদ", Icons.Default.AutoAwesome),
        Triple("text_expansion", "টেক্সট শর্টকাট", Icons.Default.ContentCut),
        Triple("stickers", "স্টিকার ও মিম", Icons.Default.InsertEmoticon),
        Triple("customizer", "কাস্টমাইজার", Icons.Default.Palette),
        Triple("resize", "কিবোর্ড রিসাইজ", Icons.Default.AspectRatio)
    )

    val filteredApps = remember(phoneApps, searchQuery) {
        if (searchQuery.isBlank()) phoneApps
        else phoneApps.filter { it.name.contains(searchQuery, ignoreCase = true) || it.packageName.contains(searchQuery, ignoreCase = true) }
    }

    val filteredTools = remember(availableTools, searchQuery) {
        if (searchQuery.isBlank()) availableTools
        else availableTools.filter { it.second.contains(searchQuery, ignoreCase = true) }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.PushPin,
                    contentDescription = null,
                    tint = pinGold,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "টুলবার শর্টকাট পিন করুন (${pinnedShortcuts.size}/৪টি)",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
            }
            IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = textColor, modifier = Modifier.size(16.dp))
            }
        }

        // Pinned shortcuts row overview
        if (pinnedShortcuts.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("পিন করা:", fontSize = 10.sp, color = subTextColor, fontWeight = FontWeight.SemiBold)
                pinnedShortcuts.forEach { id ->
                    val isApp = id.startsWith("app:")
                    val appPkg = if (isApp) id.removePrefix("app:") else null
                    val appInfo = phoneApps.find { it.packageName == appPkg }
                    val toolInfo = availableTools.find { it.first == id }
                    val label = appInfo?.name ?: toolInfo?.second ?: if (isApp) (appPkg ?: id) else id

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(pinGold.copy(alpha = 0.2f))
                            .border(1.dp, pinGold, RoundedCornerShape(12.dp))
                            .clickable { onTogglePin(id) }
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isApp && appInfo?.iconBitmap != null) {
                            Image(
                                bitmap = appInfo.iconBitmap,
                                contentDescription = label,
                                modifier = Modifier.size(14.dp).clip(RoundedCornerShape(3.dp))
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(
                            text = label,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Unpin",
                            tint = textColor.copy(alpha = 0.7f),
                            modifier = Modifier.size(11.dp)
                        )
                    }
                }
            }
        }

        // Search bar & Tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Search Box
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(cardBg)
                    .border(1.dp, textColor.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = subTextColor,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            fontSize = 11.sp,
                            color = textColor,
                            fontFamily = LocalKeyboardFont.current
                        ),
                        decorationBox = { innerTextField ->
                            if (searchQuery.isEmpty()) {
                                Text("অ্যাপ বা টুল খুঁজুন...", fontSize = 10.sp, color = subTextColor)
                            }
                            innerTextField()
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Tab 1: Phone Apps
            Box(
                modifier = Modifier
                    .height(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selectedTab == 0) accentColor.copy(alpha = 0.25f) else cardBg)
                    .border(1.dp, if (selectedTab == 0) accentColor else Color.Transparent, RoundedCornerShape(8.dp))
                    .clickable { selectedTab = 0 }
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "📱 ফোনের অ্যাপ",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (selectedTab == 0) accentColor else textColor
                )
            }

            // Tab 2: Keyboard Tools
            Box(
                modifier = Modifier
                    .height(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selectedTab == 1) accentColor.copy(alpha = 0.25f) else cardBg)
                    .border(1.dp, if (selectedTab == 1) accentColor else Color.Transparent, RoundedCornerShape(8.dp))
                    .clickable { selectedTab = 1 }
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "🛠️ টুলস",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (selectedTab == 1) accentColor else textColor
                )
            }
        }

        // Content Area
        if (selectedTab == 0) {
            // Phone Apps Grid
            if (isLoadingApps) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = accentColor)
                }
            } else if (filteredApps.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("কোনো অ্যাপ পাওয়া যায়নি", fontSize = 11.sp, color = subTextColor)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredApps, key = { it.packageName }) { app ->
                        val appId = "app:" + app.packageName
                        val isPinned = pinnedShortcuts.contains(appId)

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .border(
                                    width = if (isPinned) 1.5.dp else 0.5.dp,
                                    color = if (isPinned) pinGold else textColor.copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { onTogglePin(appId) },
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isPinned) pinGold.copy(alpha = 0.12f) else cardBg
                            )
                        ) {
                            Box(modifier = Modifier.fillMaxWidth()) {
                                if (isPinned) {
                                    Icon(
                                        imageVector = Icons.Default.PushPin,
                                        contentDescription = "Pinned",
                                        tint = pinGold,
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(3.dp)
                                            .size(11.dp)
                                    )
                                }
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp, horizontal = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    if (app.iconBitmap != null) {
                                        Image(
                                            bitmap = app.iconBitmap,
                                            contentDescription = app.name,
                                            modifier = Modifier
                                                .size(26.dp)
                                                .clip(RoundedCornerShape(5.dp))
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Apps,
                                            contentDescription = app.name,
                                            tint = accentColor,
                                            modifier = Modifier.size(26.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = app.name,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (isPinned) pinGold else textColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Keyboard Tools Grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredTools, key = { it.first }) { tool ->
                    val isPinned = pinnedShortcuts.contains(tool.first)

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .border(
                                width = if (isPinned) 1.5.dp else 0.5.dp,
                                color = if (isPinned) pinGold else textColor.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { onTogglePin(tool.first) },
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isPinned) pinGold.copy(alpha = 0.12f) else cardBg
                        )
                    ) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            if (isPinned) {
                                Icon(
                                    imageVector = Icons.Default.PushPin,
                                    contentDescription = "Pinned",
                                    tint = pinGold,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(3.dp)
                                        .size(11.dp)
                                )
                            }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = tool.third,
                                    contentDescription = tool.second,
                                    tint = if (isPinned) pinGold else accentColor,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = tool.second,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isPinned) pinGold else textColor,
                                    maxLines = 1,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

enum class EmojiCategory(val label: String, val icon: String) {
    RECENTS("সম্প্রতি", "🕒"),
    SMILEYS("হাসি ও মুখ", "😀"),
    HANDS("হাত ও ভঙ্গি", "👍"),
    HEARTS("ভালোবাসা", "❤️"),
    ANIMALS("প্রাণী ও প্রকৃতি", "🐶"),
    FOOD("খাবার ও পানীয়", "🍔"),
    SPORTS("খেলা ও ভ্রমণ", "⚽"),
    OBJECTS("প্রতীক ও পতাকা", "💡"),
    KAOMOJI("টেক্সট আর্ট", "ʕ•ᴥ•ʔ")
}

@Composable
fun KeyboardEmojiPanel(
    isDarkTheme: Boolean,
    onEmojiSelected: (String) -> Unit,
    onClose: () -> Unit,
    onBackspace: () -> Unit,
    height: Dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("moe_emoji_prefs", Context.MODE_PRIVATE) }

    var recentEmojisList by remember {
        val saved = prefs.getString("recent_emojis", "") ?: ""
        mutableStateOf(if (saved.isNotBlank()) saved.split(",") else emptyList())
    }

    var selectedCategory by remember { mutableStateOf(EmojiCategory.SMILEYS) }
    var searchQuery by remember { mutableStateOf("") }

    fun selectEmoji(emoji: String) {
        onEmojiSelected(emoji)
        val updated = (listOf(emoji) + recentEmojisList.filter { it != emoji }).take(24)
        recentEmojisList = updated
        prefs.edit().putString("recent_emojis", updated.joinToString(",")).apply()
    }

    val bgColor = if (isDarkTheme) Color(0xFF1E1F22) else Color(0xFFEFEFEF)
    val textColor = if (isDarkTheme) Color(0xFFF2F3F5) else Color(0xFF1F2937)
    val subTextColor = if (isDarkTheme) Color(0xFF9EA3B0) else Color(0xFF6B7280)
    val searchBg = if (isDarkTheme) Color(0xFF2B2D31) else Color(0xFFE0E0E0)
    val activePillBg = if (isDarkTheme) Color(0xFF35373C) else Color(0xFFD6D6D6)
    val itemBg = if (isDarkTheme) Color(0xFF2B2D31) else Color(0xFFFFFFFF)
    val bottomNavBg = if (isDarkTheme) Color(0xFF18191C) else Color(0xFFE5E7EB)
    val keyCapBg = if (isDarkTheme) Color(0xFF2B2D31) else Color(0xFFFFFFFF)

    val smileys = listOf(
        "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣", "🥲", "🥹", "😊", "😇",
        "🙂", "🙃", "😉", "😌", "😍", "🥰", "😘", "😗", "😙", "😚", "😋", "😛",
        "😜", "🤪", "😝", "🤑", "🤗", "🤭", "🤫", "🤔", "🤐", "🤨", "😐", "😑",
        "😶", "😏", "😒", "🙄", "😬", "😮‍💨", "🤥", "😌", "😔", "😪", "🤤", "😴",
        "😷", "🤒", "🤕", "🤢", "🤮", "🤧", "🥵", "🥶", "🥴", "😵", "🤯", "🤠",
        "🥳", "🥸", "😎", "🤓", "🧐", "😕", "😟", "🙁", "☹️", "😮", "😯", "😲",
        "😳", "🥺", "😦", "😧", "😨", "😰", "😥", "😢", "😭", "😱", "😖", "😣",
        "😞", "😓", "😩", "😫", "🥱", "😤", "😡", "😠", "🤬", "😈", "👿", "💀",
        "☠️", "💩", "🤡", "👹", "👺", "👻", "👽", "👾", "🤖"
    )

    val hands = listOf(
        "👍", "👎", "👌", "🤌", "🤏", "✌️", "🤞", "🫰", "🤟", "🤘", "🤙", "👈",
        "👉", "👆", "🖕", "👇", "☝️", "🫵", "👋", "🤚", "🖐️", "✋", "🖖", "👏",
        "🙌", "👐", "🤲", "🤝", "✍️", "💅", "🤳", "💪", "🦾", "🦵", "🦶", "👂",
        "🦻", "👃", "🧠", "🫀", "🫁", "🦷", "🦴", "👀", "👁️", "👅", "👄"
    )

    val hearts = listOf(
        "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "🤎", "💔", "❤️‍🔥", "❤️‍🩹",
        "💓", "💗", "💖", "💘", "💝", "💞", "💟", "❣️", "💌", "💋", "💍", "💎",
        "💯", "🔥", "✨", "🎉", "🎊", "🎂", "🎈", "🎁"
    )

    val animals = listOf(
        "🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼", "🐻‍❄️", "🐨", "🐯", "🦁",
        "🐮", "🐷", "🐸", "🐵", "🐔", "🐧", "🐦", "🐤", "🦆", "🦅", "🦉", "🦇",
        "🐺", "🐗", "🐴", "🦄", "🐝", "🐛", "🦋", "🐌", "🐞", "🐜", "🕷️", "🐢",
        "🐍", "🦎", "🐙", "🦑", "🦞", "🦀", "🐡", "🐠", "🐟", "🐬", "🐳", "🐋",
        "🦈", "🐊", "🐅", "🐆", "🦓", "🦍", "🦧", "🐘", "🦛", "🦏", "🐪", "🐫",
        "🦒", "🦘", "🐂", "🐄", "🐎", "🐖", "🐏", "🐑", "🐐", "🦌", "🐕", "🐈",
        "🐓", "🦃", "🦚", "🦜", "🕊️", "🐇", "🦝", "🌵", "🎄", "🌲", "🌳", "🌴",
        "🌱", "🌿", "☘️", "🍀", "🎍", "🪴", "🍃", "🍂", "🍁", "🍄", "🌾", "💐",
        "🌷", "🌹", "🌺", "🌸", "🌼", "🌻"
    )

    val food = listOf(
        "🍏", "🍎", "🍐", "🍊", "🍋", "🍌", "🍉", "🍇", "🍓", "🫐", "🍈", "🍒",
        "🍑", "🥭", "🍍", "🥥", "🥝", "🍅", "🥑", "🥦", "🥒", "🌶️", "🌽", "🥕",
        "🍞", "🥖", "🥨", "🧀", "🥚", "🍳", "🧈", "🥞", "🧇", "🥓", "🥩", "🍗",
        "🍖", "🌭", "🍔", "🍟", "🍕", "<ctrl42>", "🥙", "🧆", "🌮", "🌯", "🥗", "🥘",
        "🍝", "🍜", "🍲", "🍛", "🍣", "🍱", "🥟", "🍤", "🍙", "🍚", "🍩", "🍪",
        "🎂", "🍰", "🧁", "🥧", "🍫", "🍬", "🍭", "☕", "🫖", "🍵", "🧃", "🥤",
        "🧋", "🍺", "🍻", "🍷", "🍾"
    )

    val sports = listOf(
        "⚽", "🏀", "🏈", "⚾", "🥎", "🎾", "🏐", "🏉", "🎱", "🏓", "🏸", "🏒",
        "🏏", "⛳", "🏹", "<ctrl42>", "🥊", "🥋", "🛹", "🛼", "🏋️", "🚴", "🏆", "🥇",
        "🥈", "🥉", "🎯", "🎮", "🎲", "🚗", "🚕", "🚙", "🚌", "🏎️", "🚓", "🚑",
        "<ctrl42>", "🚚", "🚜", "🛵", "🏍️", "🚨", "✈️", "🚀", "🛸", "🚁", "⛵", "🚤",
        "🚢", "⚓", "🗿", "🗼", "🏰", "🎡", "🎢", "🏖️", "🌋", "⛰️", "🏠", "🏢",
        "🏥", "🏫", "🕌", "🕋"
    )

    val objects = listOf(
        "💡", "🔦", "🕯️", "📱", "📲", "💻", "⌨️", "🖥️", "🖨️", "🎙️", "📻", "🎷",
        "🎸", "🎹", "🥁", "⏰", "⏱️", "⌛", "🧭", "🎈", "🎁", "🏆", "📜", "✉️",
        "📦", "🏷️", "✏️", "🖊️", "📅", "📍", "📌", "🔍", "🔎", "🔒", "🔓", "🔑",
        "🔨", "🪛", "🔧", "🛡️", "💊", "🩸", "🩺", "🇧🇩", "🇮🇳", "🇵🇰", "🇺🇸", "🇬🇧",
        "🇨🇦", "🇦🇺", "🇯🇵", "🇰🇷", "🇸🇦", "🇦🇪", "🇹🇷", "🇵🇸", "🇩🇪", "🇫🇷", "🇮🇹", "🇪🇸",
        "🇧🇷", "🇦🇷", "🇨🇳"
    )

    val kaomojiList = listOf(
        "( ͡° ͜ʖ ͡°)", "(•‿•)", "(^_^)", "(⁠ಠ⁠_⁠ಠ⁠)", "¯\\_(ツ)_/¯", "(⁠~⁠￣⁠³⁠￣⁠)⁠~",
        "(⁠T⁠_⁠T⁠)", "(⁠ʘ⁠ᴗ⁠ʘ⁠✿⁠)", "(⁠人⁠ •͈⁠ᴗ⁠•͈⁠)", "(⁠;⁠;⁠;⁠;⁠;⁠;⁠;⁠;⁠)", "(⁠•⁠ө⁠•⁠)", "(⁠｡⁠•̀⁠ᴗ⁠-⁠)⁠✧",
        "(⁠◠⁠‿⁠◕⁠)", "(⁠≧⁠▽⁠≦⁠)", "(⁠ ⁠•⁠̀⁠⁠•⁠́⁠ ⁠)", "(⁠>⁠0⁠<⁠)", "(⁠;⁠_⁠;⁠)", "(⁠・⁠∀⁠・⁠)",
        "(⁠ʘ⁠A⁠ʘ⁠)", "(⁠╯⁠°⁠□⁠°⁠)⁠╯⁠︵⁠ ⁠┻⁠━⁠┻", "(⁠.⁠ ⁠❛⁠ ⁠ᴗ⁠ ⁠❛⁠.⁠)", "(⁠◕⁠ᴗ⁠◕⁠✿⁠)"
    )

    val allEmojisCombined = smileys + hands + hearts + animals + food + sports + objects

    val categoryEmojis = when (selectedCategory) {
        EmojiCategory.RECENTS -> if (recentEmojisList.isNotEmpty()) recentEmojisList else smileys
        EmojiCategory.SMILEYS -> smileys
        EmojiCategory.HANDS -> hands
        EmojiCategory.HEARTS -> hearts
        EmojiCategory.ANIMALS -> animals
        EmojiCategory.FOOD -> food
        EmojiCategory.SPORTS -> sports
        EmojiCategory.OBJECTS -> objects
        EmojiCategory.KAOMOJI -> emptyList()
    }

    val displayList = remember(searchQuery, selectedCategory, recentEmojisList) {
        if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim().lowercase()
            allEmojisCombined.filter { emoji ->
                when {
                    q.contains("love") || q.contains("heart") || q.contains("ভালবাসা") -> hearts.contains(emoji)
                    q.contains("smile") || q.contains("happy") || q.contains("হাসি") -> smileys.contains(emoji)
                    q.contains("cat") || q.contains("dog") || q.contains("animal") || q.contains("পাখি") -> animals.contains(emoji)
                    q.contains("food") || q.contains("pizza") || q.contains("tea") || q.contains("খাবার") -> food.contains(emoji)
                    q.contains("flag") || q.contains("bd") || q.contains("bangladesh") || q.contains("পতাকা") -> objects.contains(emoji)
                    else -> true
                }
            }.take(60)
        } else {
            categoryEmojis
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(if (isDarkTheme) Color(0xFF161617) else Color(0xFFF2F3F6))
    ) {
        // 1. Top Quick Access Bar - Shows EXACTLY the last 5 used emojis
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "সদ্য ব্যবহৃত (Last Used):",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = subTextColor
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Ensure there are always 5 emojis displayed by fallbacking to common ones
                val fiveRecents = (recentEmojisList + listOf("❤️", "😂", "😍", "🔥", "😘")).distinct().take(5)
                fiveRecents.forEach { emoji ->
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isDarkTheme) Color(0xFF2C2C2E) else Color(0xFFE5E5EA))
                            .clickable { selectEmoji(emoji) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = emoji, fontSize = 18.sp)
                    }
                }
            }
        }

        // 2. iOS-style Search Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 4.dp)
                .height(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (isDarkTheme) Color(0xFF2C2C2E) else Color(0xFFE3E3E6))
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search Emoji",
                tint = subTextColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            BasicTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(color = textColor, fontSize = 13.sp),
                decorationBox = { innerTextField ->
                    if (searchQuery.isEmpty()) {
                        Text("ইমোজি খুঁজুন... (Search Emoji)", color = subTextColor, fontSize = 13.sp)
                    }
                    innerTextField()
                }
            )
            if (searchQuery.isNotEmpty()) {
                IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Clear", tint = subTextColor, modifier = Modifier.size(16.dp))
                }
            }
        }

        // 3. Emoji Grid Canvas
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (displayList.isEmpty() && selectedCategory != EmojiCategory.KAOMOJI) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("কোনো ইমোজি পাওয়া যায়নি", color = subTextColor, fontSize = 13.sp)
                }
            } else if (selectedCategory == EmojiCategory.KAOMOJI && searchQuery.isEmpty()) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(kaomojiList) { kaomoji ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isDarkTheme) Color(0xFF2C2C2E) else Color(0xFFFFFFFF),
                            modifier = Modifier.clickable { selectEmoji(kaomoji) }
                        ) {
                            Text(
                                text = kaomoji,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = textColor,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 12.dp, horizontal = 6.dp)
                            )
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 42.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(displayList) { emoji ->
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { selectEmoji(emoji) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = emoji, fontSize = 26.sp)
                        }
                    }
                }
            }
        }

        // 4. iOS-style Bottom Navigation Bar
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            color = if (isDarkTheme) Color(0xFF1C1C1E) else Color(0xFFE5E5EA)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left side: ABC switch back to main keys
                Box(
                    modifier = Modifier
                        .width(48.dp)
                        .height(36.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isDarkTheme) Color(0xFF3A3A3C) else Color(0xFFFFFFFF))
                        .clickable { onClose() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "ABC",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = textColor
                    )
                }

                // Middle scrollable category row
                LazyRow(
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .padding(horizontal = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(EmojiCategory.values()) { category ->
                        val isSelected = (selectedCategory == category && searchQuery.isEmpty())
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(17.dp))
                                .background(if (isSelected) (if (isDarkTheme) Color(0xFF3A3A3C) else Color(0xFFD1D1D6)) else Color.Transparent)
                                .clickable {
                                    searchQuery = ""
                                    selectedCategory = category
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = category.icon,
                                fontSize = if (category == EmojiCategory.KAOMOJI) 11.sp else 16.sp
                            )
                        }
                    }
                }

                // Right side: iOS Backspace keycap
                Box(
                    modifier = Modifier
                        .width(48.dp)
                        .height(36.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isDarkTheme) Color(0xFF3A3A3C) else Color(0xFFFFFFFF))
                        .clickable { onBackspace() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Backspace,
                        contentDescription = "Backspace",
                        tint = textColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
