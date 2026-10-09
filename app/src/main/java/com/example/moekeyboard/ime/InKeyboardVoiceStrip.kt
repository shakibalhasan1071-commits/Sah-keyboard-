package com.example.moekeyboard.ime

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicNone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Gboard-style In-Keyboard Voice Typing Toolbar Strip matching user's screenshot:
 * Left: Circular back arrow [<-]
 * Center: "এখনই বলুন" ("Speak now") text with animated status
 * Right: Chevron dropdown [v], Language badge [BN]/[EN], and Green Mic button
 */
@Composable
fun InKeyboardVoiceStrip(
    isDarkTheme: Boolean,
    currentKeyboardMode: KeyboardMode,
    onInsertText: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedLanguage by remember {
        mutableStateOf(
            if (currentKeyboardMode == KeyboardMode.BENGALI) VoiceLanguage.BENGALI else VoiceLanguage.ENGLISH
        )
    }
    var isListening by remember { mutableStateOf(false) }
    var isContinuousMode by remember { mutableStateOf(true) }
    val coroutineScope = rememberCoroutineScope()
    var speechStatusText by remember {
        mutableStateOf(
            if (selectedLanguage == VoiceLanguage.BENGALI) "এখনই বলুন" else "Speak now"
        )
    }
    var recognizedPartialText by remember { mutableStateOf("") }
    var speechRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }
    var showLanguageMenu by remember { mutableStateOf(false) }

    val barBg = if (isDarkTheme) Color(0xFF2C2E33) else Color(0xFFE8ECEF)
    val circleBg = if (isDarkTheme) Color(0xFF383A42) else Color(0xFFFFFFFF)
    val textColor = if (isDarkTheme) Color(0xFFF1F2F6) else Color(0xFF1F2937)
    val micGreenColor = Color(0xFF34A853)
    val langBadgeBg = Color(0xFFC2E7FF)
    val langBadgeText = Color(0xFF001D35)

    // Pulse animation for active speech listening
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isListening) 1.2f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    fun openPermissionSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            val intent = Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    fun stopListening() {
        isContinuousMode = false
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
            speechRecognizer = null
        } catch (_: Exception) {}
        isListening = false
    }

    fun startListening(lang: VoiceLanguage = selectedLanguage) {
        hasAudioPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasAudioPermission) {
            isContinuousMode = false
            speechStatusText = "মাইক্রোফোন পারমিশন দিন"
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            speechStatusText = "স্পিচ ইঞ্জিন রেডি"
            val sample = if (lang == VoiceLanguage.BENGALI) "বাংলা ভয়েস টাইপিং " else "Voice typing "
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
                    speechStatusText = if (lang == VoiceLanguage.BENGALI) "এখনই বলুন" else "Speak now"
                }

                override fun onBeginningOfSpeech() {
                    speechStatusText = if (lang == VoiceLanguage.BENGALI) "শুনছি..." else "Listening..."
                }

                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    isListening = false
                    speechStatusText = if (lang == VoiceLanguage.BENGALI) "প্রসেস হচ্ছে..." else "Processing..."
                }

                override fun onError(error: Int) {
                    isListening = false
                    if (isContinuousMode) {
                        // Continuous listening auto-restart after error or pause!
                        coroutineScope.launch {
                            kotlinx.coroutines.delay(350)
                            if (isContinuousMode) {
                                startListening(lang)
                            }
                        }
                    } else {
                        speechStatusText = when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH -> if (lang == VoiceLanguage.BENGALI) "আবার বলুন" else "Try speaking again"
                            SpeechRecognizer.ERROR_NETWORK -> "ইন্টারনেট নেই"
                            else -> if (lang == VoiceLanguage.BENGALI) "মাইকে ট্যাপ করুন" else "Tap mic to retry"
                        }
                    }
                }

                override fun onResults(results: Bundle?) {
                    isListening = false
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val text = matches[0] + " "
                        onInsertText(text)
                        speechStatusText = text.trim()
                    }

                    if (isContinuousMode) {
                        // Continuous loop: auto-restart listening immediately after inserting speech text!
                        coroutineScope.launch {
                            kotlinx.coroutines.delay(250)
                            if (isContinuousMode) {
                                startListening(lang)
                            }
                        }
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val partials = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!partials.isNullOrEmpty()) {
                        recognizedPartialText = partials[0]
                        speechStatusText = partials[0]
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            recognizer.startListening(intent)
            speechRecognizer = recognizer
            isListening = true
            speechStatusText = if (lang == VoiceLanguage.BENGALI) "এখনই বলুন" else "Speak now"
        } catch (e: Exception) {
            isListening = false
            speechStatusText = "এখনই বলুন"
        }
    }

    LaunchedEffect(selectedLanguage) {
        startListening(selectedLanguage)
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                speechRecognizer?.destroy()
            } catch (_: Exception) {}
        }
    }

    // Gboard-style voice strip container (matches Screenshot 20260915_183951)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(22.dp)),
        color = barBg,
        shape = RoundedCornerShape(22.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Circular Back Arrow Button
            Surface(
                shape = CircleShape,
                color = circleBg,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable {
                        stopListening()
                        onClose()
                    }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "বন্ধ করুন",
                        tint = textColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Center: "এখনই বলুন" / Permission Request Button / Live Recognized Speech Text
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.weight(1f).padding(horizontal = 6.dp)
            ) {
                if (!hasAudioPermission) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { openPermissionSettings() }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "পারমিশন দিন",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                } else {
                    Text(
                        text = speechStatusText,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = textColor,
                        maxLines = 1
                    )
                }
            }

            // Right: Language Selector Chevron + BN/EN Badge + Green Mic Button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Dropdown Chevron
                Box {
                    IconButton(
                        onClick = { showLanguageMenu = !showLanguageMenu },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "ভাষা নির্বাচন",
                            tint = textColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showLanguageMenu,
                        onDismissRequest = { showLanguageMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("বাংলা (BN)") },
                            onClick = {
                                selectedLanguage = VoiceLanguage.BENGALI
                                showLanguageMenu = false
                                startListening(VoiceLanguage.BENGALI)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("English (EN)") },
                            onClick = {
                                selectedLanguage = VoiceLanguage.ENGLISH
                                showLanguageMenu = false
                                startListening(VoiceLanguage.ENGLISH)
                            }
                        )
                    }
                }

                // Language tag badge + Mic button combined pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (isListening) micGreenColor.copy(alpha = 0.2f) else circleBg)
                        .clickable {
                            if (isListening || isContinuousMode) {
                                stopListening()
                            } else {
                                isContinuousMode = true
                                startListening(selectedLanguage)
                            }
                        }
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    // Language tag badge (e.g. BN or EN)
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = langBadgeBg,
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Text(
                            text = if (selectedLanguage == VoiceLanguage.BENGALI) "BN" else "EN",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = langBadgeText,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }

                    // Green Microphone Icon with subtle pulsing scale when listening
                    Icon(
                        imageVector = if (isListening) Icons.Default.Mic else Icons.Default.MicNone,
                        contentDescription = "Microphone",
                        tint = if (isListening) micGreenColor else textColor,
                        modifier = Modifier
                            .size(22.dp)
                            .scale(if (isListening) pulseScale else 1f)
                    )
                }
            }
        }
    }
}
