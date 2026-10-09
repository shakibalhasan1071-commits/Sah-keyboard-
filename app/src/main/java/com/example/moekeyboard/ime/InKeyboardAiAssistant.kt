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
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.example.ui.theme.HindSiliguri
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun InKeyboardAiAssistant(
    isDarkTheme: Boolean,
    height: Dp,
    onInsertText: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("sah_ai_persona_prefs", Context.MODE_PRIVATE) }

    var activeTab by remember { mutableStateOf("autoreply") } // "autoreply", "translate", "rewrite"
    var inputText by remember { mutableStateOf("") }
    var outputText by remember { mutableStateOf("") }
    var replyOptions by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    // Persona Settings
    var savedPersonaPreset by remember {
        mutableStateOf(prefs.getString("persona_preset", "😄 ফ্রেন্ডলি ও দোস্ত স্টাইল") ?: "😄 ফ্রেন্ডলি ও দোস্ত স্টাইল")
    }
    var customPersonaText by remember {
        mutableStateOf(prefs.getString("custom_persona_text", "আমি অত্যন্ত অমায়িক ও দোস্ত স্টাইলে সংক্ষেপে উত্তর দিই।") ?: "")
    }
    var showEditPersona by remember { mutableStateOf(false) }

    var selectedLang by remember { mutableStateOf("English") }
    var selectedStyle by remember { mutableStateOf("পেশাদার (Professional)") }

    val personaPresets = listOf(
        "😄 ফ্রেন্ডলি ও দোস্ত স্টাইল",
        "💼 পেশাদার ও প্রাতিষ্ঠানিক",
        "❤️ মিষ্টি ও নম্র",
        "⚡ সংক্ষিপ্ত ও সোজা কথা",
        "✏️ আমার কাস্টম স্টাইল"
    )

    val languages = listOf("English", "বাংলা", "Arabic", "Hindi", "French", "German", "Spanish", "Japanese")
    val styles = listOf(
        "পেশাদার (Professional)",
        "অফিশিয়াল (Formal)",
        "মিষ্টি/বন্ধুভাবাপন্ন (Polite & Sweet)",
        "মজার/ফানি (Funny)",
        "ছোট ও সংক্ষিপ্ত (Short)",
        "গ্রামার ঠিক করুন (Fix Grammar)"
    )

    val bgColor = if (isDarkTheme) Color(0xFF090A0D) else Color(0xFFECEFF1)
    val cardBg = if (isDarkTheme) Color(0xFF181A20) else Color(0xFFFFFFFF)
    val textColor = if (isDarkTheme) Color(0xFFF2F3F5) else Color(0xFF1F2937)
    val subTextColor = if (isDarkTheme) Color(0xFF9CA3AF) else Color(0xFF6B7280)
    val neonGreen = Color(0xFF00FF88)
    val purpleAccent = Color(0xFFA142F4)
    val iosBlue = Color(0xFF007AFF)

    // Auto load last copied text when Auto Reply tab is opened
    LaunchedEffect(activeTab) {
        if (activeTab == "autoreply" && inputText.isBlank()) {
            val clipMgr = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clipText = clipMgr?.primaryClip?.getItemAt(0)?.text?.toString()
            if (!clipText.isNullOrBlank() && clipText.length < 300) {
                inputText = clipText
            }
        }
    }

    fun getActivePersonaPrompt(): String {
        return when (savedPersonaPreset) {
            "😄 ফ্রেন্ডলি ও দোস্ত স্টাইল" -> "Respond in a friendly, casual Bengali/English tone as a close friend, calling the sender 'ভাই' or 'দোস্ত'."
            "💼 পেশাদার ও প্রাতিষ্ঠানিক" -> "Respond in a formal, professional, polite business manner."
            "❤️ মিষ্টি ও নম্র" -> "Respond in an extremely sweet, warm, respectful and gentle manner."
            "⚡ সংক্ষিপ্ত ও সোজা কথা" -> "Respond in an ultra-short, direct, 1-line plain sentence."
            else -> if (customPersonaText.isNotBlank()) "User's exact personal speaking style persona: \"$customPersonaText\"" else "Respond naturally and politely."
        }
    }

    fun fallbackOffline(isMultiOptionReply: Boolean) {
        if (isMultiOptionReply) {
            replyOptions = generateSmartOfflineReplies(inputText, savedPersonaPreset)
        } else {
            outputText = generateSmartOfflineResult(activeTab, inputText, selectedLang, selectedStyle)
        }
    }

    fun callGemini(prompt: String, isMultiOptionReply: Boolean = false) {
        if (inputText.isBlank()) {
            Toast.makeText(context, "অনুগ্রহ করে আয়তকৃত বা আসা মেসেজটি লিখুন/পেস্ট করুন", Toast.LENGTH_SHORT).show()
            return
        }

        isLoading = true
        outputText = ""
        replyOptions = emptyList()

        scope.launch {
            try {
                val apiKey = try {
                    BuildConfig::class.java.getField("GEMINI_API_KEY").get(null) as? String ?: ""
                } catch (_: Exception) { "" }

                if (apiKey.isNotBlank()) {
                    val client = OkHttpClient.Builder()
                        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                        .build()

                    val rootJson = JSONObject().apply {
                        val contentsArr = JSONArray().apply {
                            val contentObj = JSONObject().apply {
                                val partsArr = JSONArray().apply {
                                    put(JSONObject().apply { put("text", prompt) })
                                }
                                put("parts", partsArr)
                            }
                            put(contentObj)
                        }
                        put("contents", contentsArr)
                    }

                    val body = rootJson.toString().toRequestBody("application/json".toMediaType())

                    val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"
                    val request = Request.Builder().url(url).post(body).build()

                    val res = withContext(Dispatchers.IO) { client.newCall(request).execute() }
                    val resBody = res.body?.string()

                    if (res.isSuccessful && !resBody.isNullOrBlank()) {
                        val parsed = JSONObject(resBody)
                        val candidates = parsed.optJSONArray("candidates")
                        val text = candidates?.optJSONObject(0)
                            ?.optJSONObject("content")
                            ?.optJSONArray("parts")
                            ?.optJSONObject(0)
                            ?.optString("text")

                        if (!text.isNullOrBlank()) {
                            val cleanText = text.trim()
                            if (isMultiOptionReply) {
                                val lines = cleanText.lines()
                                    .map { it.replace(Regex("^[0-9]+[.\\-)]\\s*"), "").trim() }
                                    .filter { it.isNotBlank() }
                                replyOptions = if (lines.isNotEmpty()) lines.take(3) else listOf(cleanText)
                            } else {
                                outputText = cleanText
                            }
                        } else {
                            fallbackOffline(isMultiOptionReply)
                        }
                    } else {
                        fallbackOffline(isMultiOptionReply)
                    }
                } else {
                    fallbackOffline(isMultiOptionReply)
                }
            } catch (e: Exception) {
                fallbackOffline(isMultiOptionReply)
            } finally {
                isLoading = false
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        // Top Header & Tab Navigation
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose, modifier = Modifier.size(26.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = textColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(2.dp))
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = purpleAccent,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "AI স্মার্ট রিপ্লাই",
                    fontSize = 12.sp,
                    fontFamily = HindSiliguri,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
            }

            // Navigation Tabs
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(cardBg)
                    .padding(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                listOf(
                    "autoreply" to "🤖 অটো রিপ্লাই",
                    "translate" to "অনুবাদ",
                    "rewrite" to "রিরাইট"
                ).forEach { (tabKey, tabName) ->
                    val isSel = (activeTab == tabKey)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (isSel) purpleAccent else Color.Transparent)
                            .clickable { activeTab = tabKey }
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = tabName,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSel) Color.White else textColor
                        )
                    }
                }
            }
        }

        // AUTO REPLY TAB SPECIFIC PERSONA STRIP
        if (activeTab == "autoreply") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("স্টাইল:", fontSize = 9.sp, color = subTextColor, fontWeight = FontWeight.Bold)
                    personaPresets.forEach { preset ->
                        val isSelected = (savedPersonaPreset == preset)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) iosBlue else cardBg)
                                .clickable {
                                    savedPersonaPreset = preset
                                    prefs.edit().putString("persona_preset", preset).apply()
                                    if (preset == "✏️ আমার কাস্টম স্টাইল") {
                                        showEditPersona = true
                                    }
                                }
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = preset,
                                fontSize = 9.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color.White else textColor
                            )
                        }
                    }
                }

                IconButton(
                    onClick = { showEditPersona = !showEditPersona },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Edit Persona",
                        tint = if (showEditPersona) iosBlue else subTextColor,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            // Custom Persona Prompt Input box
            if (showEditPersona) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = customPersonaText,
                        onValueChange = {
                            customPersonaText = it
                            prefs.edit().putString("custom_persona_text", it).apply()
                        },
                        placeholder = { Text("উদাহরণ: আমি রসিকতা করে 'দোস্ত' বলে সংক্ষেপে লিখি...", fontSize = 9.sp) },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp),
                        shape = RoundedCornerShape(17.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = cardBg,
                            unfocusedContainerColor = cardBg,
                            focusedBorderColor = iosBlue,
                            unfocusedBorderColor = subTextColor.copy(alpha = 0.3f)
                        )
                    )
                }
            }
        } else if (activeTab == "translate") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("ভাষা:", fontSize = 9.sp, color = subTextColor)
                languages.forEach { lang ->
                    FilterChip(
                        selected = (selectedLang == lang),
                        onClick = { selectedLang = lang },
                        label = { Text(lang, fontSize = 9.sp) },
                        modifier = Modifier.height(24.dp)
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("টোন:", fontSize = 9.sp, color = subTextColor)
                styles.forEach { st ->
                    FilterChip(
                        selected = (selectedStyle == st),
                        onClick = { selectedStyle = st },
                        label = { Text(st, fontSize = 9.sp) },
                        modifier = Modifier.height(24.dp)
                    )
                }
            }
        }

        // Input Field
        OutlinedTextField(
            value = inputText,
            onValueChange = { inputText = it },
            placeholder = {
                Text(
                    text = when (activeTab) {
                        "autoreply" -> "যে মেসেজের উত্তর দিতে চান তা এখানে লিখুন বা কপি পোস্ট করুন..."
                        "translate" -> "অনুবাদ করার টেক্সট লিখুন..."
                        else -> "সাজাতে চাওয়া মেসেজ লিখুন..."
                    },
                    fontSize = 11.sp,
                    color = subTextColor
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(55.dp),
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = purpleAccent,
                unfocusedBorderColor = subTextColor.copy(alpha = 0.2f),
                focusedContainerColor = cardBg,
                unfocusedContainerColor = cardBg,
                focusedTextColor = textColor,
                unfocusedTextColor = textColor
            )
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Action Trigger Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = {
                    when (activeTab) {
                        "autoreply" -> {
                            val prompt = "The user received this message: \"$inputText\". Generate 3 natural short reply options strictly matching this speaking style persona: \"${getActivePersonaPrompt()}\". Return ONLY 3 numbered reply options, each on a new line."
                            callGemini(prompt, isMultiOptionReply = true)
                        }
                        "translate" -> {
                            val prompt = "Translate the following text into $selectedLang accurately and naturally: \"$inputText\". Return ONLY the translated text."
                            callGemini(prompt)
                        }
                        else -> {
                            val prompt = "Rewrite the following text in a $selectedStyle tone: \"$inputText\". Return ONLY the rewritten text."
                            callGemini(prompt)
                        }
                    }
                },
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = purpleAccent, contentColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.height(30.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("AI জেনারেট করছে...", fontSize = 10.sp)
                } else {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = when (activeTab) {
                            "autoreply" -> "আমার স্টাইলে ৩টি রিপ্লাই জেনারেট করুন"
                            "translate" -> "অনুবাদ করুন"
                            else -> "মেসেজ সাজান"
                        },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (outputText.isNotBlank() && activeTab != "autoreply") {
                Button(
                    onClick = {
                        onInsertText(outputText)
                        Toast.makeText(context, "চ্যাটে পেস্ট করা হয়েছে!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = neonGreen, contentColor = Color.Black),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("চ্যাটে সেন্ড", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Display Options for Auto Reply
        if (activeTab == "autoreply" && replyOptions.isNotEmpty()) {
            Text("✨ পছন্দমতো ১-ক্লিকে সেন্ড করুন:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = purpleAccent)
            Spacer(modifier = Modifier.height(2.dp))
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(replyOptions) { replyText ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .border(0.5.dp, purpleAccent.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .clickable {
                                onInsertText(replyText)
                                Toast.makeText(context, "রিপ্লাই চ্যাটে পেস্ট করা হয়েছে!", Toast.LENGTH_SHORT).show()
                            },
                        color = cardBg
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = replyText,
                                fontSize = 11.sp,
                                fontFamily = HindSiliguri,
                                color = textColor,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = "Send",
                                tint = neonGreen,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        } else if (outputText.isNotBlank()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .border(1.dp, purpleAccent.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                ) {
                    Text(
                        text = "✨ AI আউটপুট:",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = purpleAccent
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = outputText,
                        fontSize = 12.sp,
                        color = textColor,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}

private fun generateSmartOfflineReplies(input: String, persona: String): List<String> {
    return when {
        persona.contains("ফ্রেন্ডলি") -> listOf(
            "হ্যাঁ দোস্ত! $input, একদম ঠিক আছে 👍",
            "আরে ভাই! একটু ব্যস্ত ছিলাম, রাতে কথা বলি?",
            "হা হা জোস! চল তাহলে যাওয়া যাক 🎉"
        )
        persona.contains("পেশাদার") -> listOf(
            "ধন্যবাদ যোগাযোগের জন্য। $input বিষয়ে আমি শীঘ্রই বিস্তারিত জানাচ্ছি।",
            "বিষয়টি সম্পর্কে অবহিত হয়েছি। ধন্যবাদ।",
            "জি, বিষয়টি সদয় বিবেচনার জন্য ধন্যবাদ।"
        )
        persona.contains("মিষ্টি") -> listOf(
            "আশা করি ভালো আছেন! ❤️ $input, অনেক ধন্যবাদ!",
            "অনেক ভালো লাগলো আপনার মেসেজ পেয়ে 😊",
            "জি অবশ্যই! শুভকামনা আপনার জন্য ✨"
        )
        persona.contains("সংক্ষিপ্ত") -> listOf(
            "জি, ঠিক আছে।",
            "ধন্যবাদ 👍",
            "একটু পর কল দিচ্ছি।"
        )
        else -> listOf(
            "জি, $input",
            "হ্যাঁ, ঠিক আছে!",
            "ধন্যবাদ!"
        )
    }
}

private fun generateSmartOfflineResult(type: String, input: String, lang: String, style: String): String {
    return if (type == "translate") {
        if (lang == "English") {
            "Translation ($lang): $input"
        } else {
            "অনুবাদ ($lang): $input"
        }
    } else {
        when {
            style.contains("পেশাদার") -> "শ্রদ্ধেয় মহোদয়, $input। বিষয়টি সদয় বিবেচনার জন্য অনুরোধ করা হলো।"
            style.contains("অফিশিয়াল") -> "সম্মানিত গ্রাহক/সহকর্মী, $input।"
            style.contains("মিষ্টি") -> "আশা করি ভালো আছেন! 😊 $input, অনেক ধন্যবাদ! ❤️"
            style.contains("মজার") -> "আরে ভাই শোনেন! 😜 $input, মারাত্মক ব্যপার না বলো?"
            style.contains("ছোট") -> input.take(30) + "..."
            else -> input
        }
    }
}
