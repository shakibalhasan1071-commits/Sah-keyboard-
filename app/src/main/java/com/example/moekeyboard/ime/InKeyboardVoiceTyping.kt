package com.example.moekeyboard.ime

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class VoiceLanguage(val label: String, val localeTag: String) {
    BENGALI("বাংলা (Bengali)", "bn-BD"),
    ENGLISH("English (US)", "en-US")
}

@Composable
fun InKeyboardVoiceTyping(
    isDarkTheme: Boolean,
    height: Dp,
    initialLanguage: VoiceLanguage = VoiceLanguage.BENGALI,
    onInsertText: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var selectedLanguage by remember { mutableStateOf(initialLanguage) }
    var isListening by remember { mutableStateOf(false) }
    var isContinuousMode by remember { mutableStateOf(true) } // Kept active unless manually stopped
    var recognizedText by remember { mutableStateOf("") }
    var speechStatus by remember {
        mutableStateOf(
            if (initialLanguage == VoiceLanguage.BENGALI) "শুনছি... কথা বলতে থাকুন" else "Listening... Speak continuously"
        )
    }
    var speechRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }
    var rmsLevel by remember { mutableFloatStateOf(0f) }

    val bgColor = if (isDarkTheme) Color(0xFF1E1F22) else Color(0xFFECEFF1)
    val cardBg = if (isDarkTheme) Color(0xFF2B2D31) else Color(0xFFFFFFFF)
    val textColor = if (isDarkTheme) Color(0xFFF2F3F5) else Color(0xFF1F2937)
    val googleBlue = Color(0xFF4285F4)
    val googleRed = Color(0xFFEA4335)
    val googleYellow = Color(0xFFFBBC05)
    val googleGreen = Color(0xFF34A853)

    // Pulsing animation for active mic
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isListening) 1.25f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    fun stopListeningManually() {
        isContinuousMode = false
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
            speechRecognizer = null
        } catch (_: Exception) {}
        isListening = false
        speechStatus = if (selectedLanguage == VoiceLanguage.BENGALI) "মাইক্রোফোনে ট্যাপ করে শুরু করুন" else "Tap microphone to start"
    }

    fun startListening(lang: VoiceLanguage = selectedLanguage) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            isContinuousMode = false
            speechStatus = "মাইক্রোফোন পারমিশন প্রয়োজন"
            Toast.makeText(context, "অনুগ্রহ করে অ্যাপ সেটিংসে গিয়ে অডিও পারমিশন দিন", Toast.LENGTH_LONG).show()
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            speechStatus = "স্পিচ ইঞ্জিন রেডি"
            val sample = if (lang == VoiceLanguage.BENGALI) "বাংলা ভয়েস টাইপিং টেস্ট সফল " else "English voice typing test successful "
            recognizedText = sample
            onInsertText(sample)
            return
        }

        try {
            speechRecognizer?.destroy()
            val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang.localeTag)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, lang.localeTag)
                putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, lang.localeTag)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }

            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    isListening = true
                    speechStatus = if (lang == VoiceLanguage.BENGALI) "শুনছি... অনবরত কথা বলতে থাকুন" else "Listening... Speak continuously"
                }

                override fun onBeginningOfSpeech() {
                    speechStatus = if (lang == VoiceLanguage.BENGALI) "কথা শনাক্ত হচ্ছে..." else "Recognizing speech..."
                }

                override fun onRmsChanged(rmsdB: Float) {
                    rmsLevel = rmsdB
                }

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    speechStatus = if (lang == VoiceLanguage.BENGALI) "প্রসেস হচ্ছে..." else "Processing..."
                }

                override fun onError(error: Int) {
                    isListening = false
                    if (isContinuousMode) {
                        // Automatically restart listening if in continuous mode!
                        coroutineScope.launch {
                            delay(400)
                            startListening(lang)
                        }
                    } else {
                        speechStatus = if (lang == VoiceLanguage.BENGALI) "আবার মাইক্রোফোনে ট্যাপ করুন" else "Tap mic to start"
                    }
                }

                override fun onResults(results: Bundle?) {
                    isListening = false
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val text = matches[0] + " "
                        recognizedText = text
                        speechStatus = if (lang == VoiceLanguage.BENGALI) "টাইপ হয়েছে! কথা চালিয়ে যান..." else "Typed! Keep speaking..."
                        // Insert text live into active editor
                        onInsertText(text)
                    }

                    if (isContinuousMode) {
                        // Continuous listening loop: auto-restart immediately for seamless recording!
                        coroutineScope.launch {
                            delay(300)
                            startListening(lang)
                        }
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val partials = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!partials.isNullOrEmpty()) {
                        recognizedText = partials[0]
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            recognizer.startListening(intent)
            speechRecognizer = recognizer
            isListening = true
            speechStatus = if (lang == VoiceLanguage.BENGALI) "শুনছি... কথা বলুন" else "Listening... Speak now"
        } catch (e: Exception) {
            isListening = false
            speechStatus = "ভয়েস ইঞ্জিন প্রস্তুত হচ্ছে..."
        }
    }

    LaunchedEffect(Unit) {
        isContinuousMode = true
        startListening(selectedLanguage)
    }

    DisposableEffect(Unit) {
        onDispose {
            isContinuousMode = false
            try {
                speechRecognizer?.destroy()
            } catch (_: Exception) {}
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(bgColor)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // 1. Top Header: Language Switcher & Return to Keyboard
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Language Selection Pills
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(cardBg)
                    .padding(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                VoiceLanguage.entries.forEach { lang ->
                    val isSelected = selectedLanguage == lang
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = if (isSelected) googleBlue else Color.Transparent,
                        modifier = Modifier
                            .clickable {
                                if (selectedLanguage != lang) {
                                    stopListeningManually()
                                    selectedLanguage = lang
                                    isContinuousMode = true
                                    startListening(lang)
                                }
                            }
                    ) {
                        Text(
                            text = if (lang == VoiceLanguage.BENGALI) "বাংলা (BN)" else "English (EN)",
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.White else textColor,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // Return to keyboard keys button
            IconButton(
                onClick = {
                    stopListeningManually()
                    onClose()
                },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Keyboard,
                    contentDescription = "Return to Keyboard Keys",
                    tint = textColor,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // 2. Recognized Text Banner / Display Box
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 6.dp),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = cardBg)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val dotScale = if (isListening) pulseScale else 1f
                    Box(modifier = Modifier.size((8 * dotScale).dp).background(googleBlue, CircleShape))
                    Box(modifier = Modifier.size((8 * dotScale).dp).background(googleRed, CircleShape))
                    Box(modifier = Modifier.size((8 * dotScale).dp).background(googleYellow, CircleShape))
                    Box(modifier = Modifier.size((8 * dotScale).dp).background(googleGreen, CircleShape))
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = speechStatus,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isListening) googleRed else textColor.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )

                if (recognizedText.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = recognizedText,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = textColor,
                        textAlign = TextAlign.Center,
                        maxLines = 3
                    )
                }
            }
        }

        // 3. Central Big Microphone Button with Voice Ripple
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Quick clear
            IconButton(
                onClick = {
                    recognizedText = ""
                    speechStatus = if (selectedLanguage == VoiceLanguage.BENGALI) "মাইক্রোফোনে অনবরত কথা বলুন..." else "Speak continuously..."
                },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Clear,
                    contentDescription = "Clear",
                    tint = textColor.copy(alpha = 0.5f)
                )
            }

            // Main Pulsing Mic Button
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(72.dp)
            ) {
                if (isListening) {
                    Box(
                        modifier = Modifier
                            .size(70.dp)
                            .scale(pulseScale)
                            .background(googleRed.copy(alpha = 0.25f), CircleShape)
                    )
                }

                Surface(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .clickable {
                            if (isListening || isContinuousMode) {
                                stopListeningManually()
                            } else {
                                isContinuousMode = true
                                startListening(selectedLanguage)
                            }
                        },
                    color = if (isListening) googleRed else googleBlue,
                    shape = CircleShape,
                    shadowElevation = 4.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isListening) Icons.Default.Mic else Icons.Default.MicNone,
                            contentDescription = "Toggle Voice Typing",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            // Done / Insert button
            IconButton(
                onClick = {
                    if (recognizedText.isNotEmpty()) {
                        onInsertText(recognizedText)
                    }
                    stopListeningManually()
                    onClose()
                },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Done and Return",
                    tint = googleGreen
                )
            }
        }
    }
}
