package com.example.moekeyboard.ime

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.moekeyboard.data.prefs.KeyboardPreferences

@Composable
fun InKeyboardCustomizer(
    isDarkTheme: Boolean,
    height: Dp,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember { KeyboardPreferences.getInstance(context) }

    val currentTheme by prefs.theme.collectAsState()
    val currentHeight by prefs.height.collectAsState()
    val vibrationEnabled by prefs.vibration.collectAsState()
    val soundEnabled by prefs.sound.collectAsState()
    val showNumberRow by prefs.showNumberRow.collectAsState()

    val bgColor = if (isDarkTheme) Color(0xFF1E1F22) else Color(0xFFECEFF1)
    val cardBg = if (isDarkTheme) Color(0xFF2B2D31) else Color(0xFFFFFFFF)
    val textColor = if (isDarkTheme) Color(0xFFF2F3F5) else Color(0xFF1F2937)
    val accentColor = if (isDarkTheme) Color(0xFF8AB4F8) else Color(0xFF1A73E8)

    val themes = listOf(
        Triple("light", "Light", Color(0xFFFFFFFF)),
        Triple("dark", "Dark Slate", Color(0xFF2C2E33)),
        Triple("amoled", "AMOLED Black", Color(0xFF000000)),
        Triple("ocean_blue", "Ocean Blue", Color(0xFF1E293B)),
        Triple("neon_cyber", "Neon Cyber", Color(0xFF161B22)),
        Triple("lavender", "Pastel Lavender", Color(0xFFF3E8FF)),
        Triple("forest", "Forest", Color(0xFF0B3B2D))
    )

    val heights = listOf(
        Pair("extra_small", "অতি ছোট"),
        Pair("small", "ছোট"),
        Pair("normal", "স্বাভাবিক"),
        Pair("medium", "মাঝারি"),
        Pair("tall", "বড়"),
        Pair("extra_tall", "অতি বড়")
    )

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
                .padding(bottom = 6.dp),
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
                Text(
                    text = "কিবোর্ড কাস্টমাইজেশন",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
            }

            Button(
                onClick = onClose,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F9D58)),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                modifier = Modifier.height(28.dp)
            ) {
                Text("সম্পন্ন", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 1. Themes Selector Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = "কিবোর্ড থিম ও কালার",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = textColor
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            themes.take(4).forEach { (id, name, color) ->
                                val isSelected = currentTheme == id
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(color)
                                        .border(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = if (isSelected) accentColor else Color.Gray.copy(alpha = 0.3f),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable { prefs.setTheme(id) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = name,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (id == "light" || id == "lavender") Color.Black else Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 2. Keyboard Height Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = "কিবোর্ডের উচ্চতা / সাইজ",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = textColor
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            heights.forEach { (id, label) ->
                                val isSelected = currentHeight == id
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(30.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { prefs.setHeight(id) },
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isSelected) accentColor else (if (isDarkTheme) Color(0xFF383A42) else Color(0xFFE0E0E0))
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = label,
                                            fontSize = 10.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) Color.White else textColor
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. Quick Toggles Card (Vibration, Sound, Number Row)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                        // Vibration Toggle
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { prefs.setVibration(!vibrationEnabled) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "হ্যাপটিক ভাইব্রেশন", fontSize = 11.sp, color = textColor)
                            Switch(
                                checked = vibrationEnabled,
                                onCheckedChange = { prefs.setVibration(it) },
                                modifier = Modifier.scale(0.75f)
                            )
                        }

                        HorizontalDivider(color = textColor.copy(alpha = 0.1f))

                        // Sound Toggle
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { prefs.setSound(!soundEnabled) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "কি-প্রেস সাউন্ড", fontSize = 11.sp, color = textColor)
                            Switch(
                                checked = soundEnabled,
                                onCheckedChange = { prefs.setSound(it) },
                                modifier = Modifier.scale(0.75f)
                            )
                        }

                        HorizontalDivider(color = textColor.copy(alpha = 0.1f))

                        // Number row Toggle
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { prefs.setShowNumberRow(!showNumberRow) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "নাম্বার রো সবসময় দেখান", fontSize = 11.sp, color = textColor)
                            Switch(
                                checked = showNumberRow,
                                onCheckedChange = { prefs.setShowNumberRow(it) },
                                modifier = Modifier.scale(0.75f)
                            )
                        }
                    }
                }
            }
        }
    }
}
