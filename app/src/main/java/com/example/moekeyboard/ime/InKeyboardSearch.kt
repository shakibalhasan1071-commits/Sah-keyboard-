package com.example.moekeyboard.ime

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.HindSiliguri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.net.URLEncoder

data class SearchShareOption(
    val title: String,
    val subtitle: String,
    val url: String,
    val icon: ImageVector,
    val color: Color
)

@Composable
fun InKeyboardSearch(
    isDarkTheme: Boolean,
    height: Dp,
    query: String,
    onQueryChange: (String) -> Unit,
    onInsertText: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val bgColor = if (isDarkTheme) Color(0xFF1E1F22) else Color(0xFFF0F2F5)
    val cardBg = if (isDarkTheme) Color(0xFF2B2D31) else Color(0xFFFFFFFF)
    val textColor = if (isDarkTheme) Color.White else Color.Black
    val textMuted = if (isDarkTheme) Color.LightGray else Color.Gray
    val accentColor = Color(0xFF1A73E8) // Google Blue

    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    val scope = rememberCoroutineScope()
    val client = remember { OkHttpClient() }

    // Fetch real-time suggestions using Google Suggestion API
    LaunchedEffect(query) {
        if (query.isBlank()) {
            suggestions = emptyList()
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) {
            try {
                val encodedQuery = URLEncoder.encode(query, "UTF-8")
                val request = Request.Builder()
                    .url("https://suggestqueries.google.com/complete/search?client=chrome&q=$encodedQuery")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        if (body.startsWith("[")) {
                            val jsonArray = JSONArray(body)
                            val suggestionsArray = jsonArray.getJSONArray(1)
                            val list = mutableListOf<String>()
                            for (i in 0 until minOf(suggestionsArray.length(), 5)) {
                                list.add(suggestionsArray.getString(i))
                            }
                            withContext(Dispatchers.Main) {
                                suggestions = list
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    val encodedQuery = remember(query) {
        try {
            URLEncoder.encode(query.trim(), "UTF-8")
        } catch (_: Exception) {
            ""
        }
    }

    val shareOptions = remember(query, encodedQuery) {
        if (query.isBlank()) emptyList() else listOf(
            SearchShareOption(
                title = "গুগল সার্চ লিংক",
                subtitle = "Google Search Link",
                url = "https://www.google.com/search?q=$encodedQuery",
                icon = Icons.Default.Search,
                color = Color(0xFF1A73E8)
            ),
            SearchShareOption(
                title = "ইউটিউব ভিডিও লিংক",
                subtitle = "YouTube Video Search",
                url = "https://www.youtube.com/results?search_query=$encodedQuery",
                icon = Icons.Default.PlayCircle,
                color = Color(0xFFEA4335)
            ),
            SearchShareOption(
                title = "উইকিপিডিয়া আর্টিকেল",
                subtitle = "Wikipedia Knowledge Search",
                url = "https://bn.wikipedia.org/wiki/Special:Search?search=$encodedQuery",
                icon = Icons.AutoMirrored.Filled.MenuBook,
                color = Color(0xFF757575)
            ),
            SearchShareOption(
                title = "গুগল ম্যাপস লোকেশন",
                subtitle = "Google Maps Location Search",
                url = "https://www.google.com/maps/search/$encodedQuery",
                icon = Icons.Default.Place,
                color = Color(0xFF34A853)
            )
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(bgColor)
            .padding(8.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search Icon",
                    tint = accentColor,
                    modifier = Modifier.size(20.dp).padding(end = 4.dp)
                )
                Text(
                    text = "ইনস্ট্যান্ট ওয়েব সার্চ",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    fontFamily = HindSiliguri
                )
            }

            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(24.dp)
                    .background(textMuted.copy(alpha = 0.15f), androidx.compose.foundation.shape.CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close Panel",
                    tint = textMuted,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Search Input field
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(cardBg)
                .border(1.dp, if (isDarkTheme) Color(0xFF3F424A) else Color(0xFFCFD4DC), RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = textMuted,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = "এখানে যা খুঁজতে চান তা লিখুন...",
                        color = textMuted.copy(alpha = 0.6f),
                        fontSize = 14.sp,
                        fontFamily = HindSiliguri
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = query,
                            color = textColor,
                            fontSize = 14.sp,
                            fontFamily = HindSiliguri
                        )
                        // Blinking cursor representation
                        Box(
                            modifier = Modifier
                                .padding(start = 2.dp)
                                .width(1.5.dp)
                                .height(16.dp)
                                .background(accentColor)
                        )
                    }
                }
            }
            if (query.isNotEmpty()) {
                // Google Logo-like search button
                Surface(
                    onClick = {
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                            data = android.net.Uri.parse("https://www.google.com/search?q=${java.net.URLEncoder.encode(query, "UTF-8")}")
                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    },
                    modifier = Modifier.size(28.dp),
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = accentColor.copy(alpha = 0.1f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Google Search",
                            tint = accentColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Default.Clear,
                    contentDescription = "Clear",
                    tint = textMuted.copy(alpha = 0.6f),
                    modifier = Modifier
                        .size(18.dp)
                        .clickable { onQueryChange("") }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (query.isBlank()) {
            // Empty view
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Link,
                        contentDescription = "Link Search",
                        tint = textMuted.copy(alpha = 0.4f),
                        modifier = Modifier.size(42.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "যেকোনো লিংক বা তথ্য চ্যাটের ভেতর থেকেই খুঁজুন।",
                        color = textMuted,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        fontFamily = HindSiliguri
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Left Column: Google Autocomplete Suggestions
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "গুগল সাজেশনস:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = textMuted,
                        modifier = Modifier.padding(bottom = 4.dp),
                        fontFamily = HindSiliguri
                    )
                    if (suggestions.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(cardBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("টাইপ করুন...", fontSize = 11.sp, color = textMuted, fontFamily = HindSiliguri)
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxHeight()
                        ) {
                            items(suggestions) { s ->
                                Text(
                                    text = s,
                                    fontSize = 12.sp,
                                    color = textColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(cardBg)
                                        .clickable { onQueryChange(s) }
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    fontFamily = HindSiliguri
                                )
                            }
                        }
                    }
                }

                // Right Column: Direct Link Sharing Cards
                Column(
                    modifier = Modifier.weight(1.2f)
                ) {
                    Text(
                        text = "এক ক্লিকে লিংক পাঠান:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = textMuted,
                        modifier = Modifier.padding(bottom = 4.dp),
                        fontFamily = HindSiliguri
                    )
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        items(shareOptions) { option ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(cardBg)
                                    .clickable {
                                        onInsertText(option.url)
                                    }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = option.icon,
                                    contentDescription = option.title,
                                    tint = option.color,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = option.title,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = textColor,
                                        fontFamily = HindSiliguri
                                    )
                                    Text(
                                        text = "লিংক পাঠান 🔗",
                                        fontSize = 10.sp,
                                        color = option.color,
                                        fontFamily = HindSiliguri
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
