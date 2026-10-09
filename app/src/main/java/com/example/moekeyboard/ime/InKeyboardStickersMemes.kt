package com.example.moekeyboard.ime

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class StickerItem(
    val emojiText: String,
    val title: String,
    val sendText: String
)

@Composable
fun InKeyboardStickersMemes(
    isDarkTheme: Boolean,
    height: Dp,
    onInsertText: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf("stickers") } // "stickers", "memes", "gifs"

    val bgColor = if (isDarkTheme) Color(0xFF0D0E11) else Color(0xFFECEFF1)
    val cardBg = if (isDarkTheme) Color(0xFF1E2024) else Color(0xFFFFFFFF)
    val textColor = if (isDarkTheme) Color(0xFFF2F3F5) else Color(0xFF1F2937)
    val neonGreen = Color(0xFF00FF88)

    val banglaStickers = listOf(
        StickerItem("🤣👌", "মারাত্মক", "মারাত্মক ভাই! 🤣👌"),
        StickerItem("মাথা নষ্ট! 🤯", "মাথা নষ্ট", "একদম মাথা নষ্ট ভাই! 🤯"),
        StickerItem("কী বলিস! 😱", "অবাক", "কী বলিস ভাই! 😱"),
        StickerItem("চা খাবি? ☕", "চা প্রমি", "এক কাপ গরম চা খাবি? ☕"),
        StickerItem("খেলা হবে! ⚽", "খেলা হবে", "আজকে খেলা হবে! ⚽🔥"),
        StickerItem("কিরে মামা! 😎", "মামা", "কিরে মামা খবর কী? 😎"),
        StickerItem("ধন্যবাদ ভাই! 🙏", "ধন্যবাদ", "অনেক ধন্যবাদ ভাই! 🙏❤️"),
        StickerItem("টাকা দেন! 💸", "টাকা", "ভাই টাকা দেন দ্রুত! 💸💸"),
        StickerItem("শুভ সকাল! ☀️", "সকাল", "শুভ সকাল! দিনটি ভালো কাটুক ☀️🌱"),
        StickerItem("শুভরাত্রি! 🌙", "রাত", "শুভরাত্রি, ভালো থেকো! 🌙✨"),
        StickerItem("বসের সালাম! 🫡", "সালাম", "বসের সালাম নিন! 🫡🔥"),
        StickerItem("জটিল হইছে! 🔥", "জটিল", "জটিল হইছে ভাই! 🔥👏")
    )

    val banglaMemes = listOf(
        "ভাইরে ভাই! এ কি দেখলাম! 🤦‍♂️",
        "হাহা! কথা সত্য, একদম ১০০% সত্য! 😂",
        "আমাকে বিশ্বাস করো ভাই, আমি নির্দোষ! 😇",
        "ধৈর্য্য ধরো ভাই, ভালো দিন আসবে! ⏳",
        "এসব আমার হজম হচ্ছে না ভাই! 🤢",
        "চলুন ঘুরে আসি চা খেয়ে আসি! ☕🛵"
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
                    imageVector = Icons.Default.InsertEmoticon,
                    contentDescription = null,
                    tint = neonGreen,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "বাংলা স্টিকার ও ফানি মিম",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                FilterChip(
                    selected = (selectedTab == "stickers"),
                    onClick = { selectedTab = "stickers" },
                    label = { Text("স্টিকার", fontSize = 10.sp) },
                    modifier = Modifier.height(26.dp)
                )
                FilterChip(
                    selected = (selectedTab == "memes"),
                    onClick = { selectedTab = "memes" },
                    label = { Text("ফানি ডায়ালগ", fontSize = 10.sp) },
                    modifier = Modifier.height(26.dp)
                )
            }
        }

        if (selectedTab == "stickers") {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(banglaStickers) { st ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                onInsertText(st.sendText)
                                Toast.makeText(context, "স্টিকার পাঠানো হয়েছে!", Toast.LENGTH_SHORT).show()
                            },
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBg)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(text = st.emojiText, fontSize = 22.sp)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = st.title,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = textColor,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(banglaMemes) { meme ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                onInsertText(meme)
                                Toast.makeText(context, "ডায়ালগ পাঠানো হয়েছে!", Toast.LENGTH_SHORT).show()
                            },
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBg)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = meme,
                                fontSize = 12.sp,
                                color = textColor,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}
