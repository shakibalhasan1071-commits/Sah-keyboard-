package com.example.moekeyboard.ui

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.moekeyboard.ime.TEMP_MAIL_WEBSITES
import com.example.moekeyboard.ime.TempMailWebSite
import com.example.moekeyboard.tempmail.TempMailManager
import com.example.moekeyboard.tempmail.SavedTempMailAccount

class MainTempMailJsBridge(
    private val onEmailReceived: (String) -> Unit,
    private val onOtpReceived: (String, String) -> Unit = { _, _ -> }
) {
    @JavascriptInterface
    fun onEmailDetected(email: String?) {
        if (!email.isNullOrBlank() && email.contains("@")) {
            Handler(Looper.getMainLooper()).post {
                onEmailReceived(email.trim())
            }
        }
    }

    @JavascriptInterface
    fun onOtpDetected(otp: String?, subject: String?, from: String?) {
        if (!otp.isNullOrBlank() && otp.trim().length in 4..8) {
            Handler(Looper.getMainLooper()).post {
                onOtpReceived(otp.trim(), subject?.trim() ?: "")
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TempMailScreen(
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var activeSite by remember { mutableStateOf(TEMP_MAIL_WEBSITES.first()) }
    var detectedEmail by remember { mutableStateOf("") }
    var detectedOtp by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }

    val bgColor = Color(0xFF0D0E12)
    val headerBg = Color(0xFF161820)
    val cardBg = Color(0xFF222530)
    val accentGreen = Color(0xFF00E676)
    val otpBannerBg = Color(0xFF0D532C)
    val textColor = Color(0xFFFFFFFF)
    val subTextColor = Color(0xFFA0A5B5)

    fun injectCompactScript(view: WebView?) {
        val script = """
            (function() {
                try {
                    var styleId = 'moe-tempmail-full-style';
                    if (!document.getElementById(styleId)) {
                        var style = document.createElement('style');
                        style.id = styleId;
                        style.innerHTML = `
                            header, nav.header, footer, .ad-banner, .advertisement, [id*="google_ads"], [class*="adsbygoogle"], iframe[src*="doubleclick"], .fc-consent-root {
                                display: none !important;
                            }
                            body {
                                zoom: 0.95 !important;
                                -webkit-text-size-adjust: 95% !important;
                                padding-top: 4px !important;
                            }
                        `;
                        document.head.appendChild(style);
                    }
                } catch(e) {}

                function findAndReportEmail() {
                    try {
                        var val = '';
                        var url = window.location.href;

                        // 1. temp-mail.io
                        if (url.indexOf('temp-mail.io') !== -1) {
                            var elIo = document.querySelector('[data-qa="current-email"]') || document.getElementById('email') || document.querySelector('#mail');
                            if (elIo) val = (elIo.value || elIo.getAttribute('value') || elIo.innerText || elIo.textContent || '').trim();
                        }

                        // 2. tempmail.lol
                        if (!val || val.indexOf('@') === -1) {
                            if (url.indexOf('tempmail.lol') !== -1) {
                                try {
                                    if (window.localStorage && window.localStorage.getItem('address')) {
                                        val = window.localStorage.getItem('address');
                                    }
                                } catch(e) {}
                                if (!val || val.indexOf('@') === -1) {
                                    var elLol = document.querySelector('.email-address') || 
                                                document.querySelector('p.email-address') || 
                                                document.querySelector('#email-address');
                                    if (elLol) {
                                        var txt = (elLol.innerText || elLol.textContent || '').trim();
                                        if (txt && txt.indexOf('@') !== -1 && txt.indexOf('Loading') === -1) {
                                            val = txt;
                                        }
                                    }
                                }
                            }
                        }

                        // 3. Generic input scan
                        if (!val || val.indexOf('@') === -1) {
                            var inputs = document.querySelectorAll('input, [data-clipboard-text]');
                            for (var i = 0; i < inputs.length; i++) {
                                var v = (inputs[i].value || inputs[i].getAttribute('data-clipboard-text') || inputs[i].getAttribute('value') || '').trim();
                                if (v && v.indexOf('@') !== -1 && !v.startsWith('http') && v.indexOf(' ') === -1) {
                                    val = v;
                                    break;
                                }
                            }
                        }

                        // 4. Text regex scan
                        if (!val || val.indexOf('@') === -1) {
                            var emailRegex = /[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}/g;
                            var bodyText = document.body ? document.body.innerText : '';
                            var match = bodyText.match(emailRegex);
                            if (match && match.length > 0) {
                                val = match[0];
                            }
                        }

                        if (val && val.indexOf('@') !== -1 && window.AndroidTempMailScreen) {
                            window.AndroidTempMailScreen.onEmailDetected(val);
                        }
                    } catch(e) {}
                }

                function findAndExtractOtp(fullText) {
                    if (!fullText) return '';
                    var strictPatterns = [
                        /(?:confirmation\s+code|verification\s+code|security\s+code|confirm\s+code|login\s+code|passcode)\D{0,35}?([0-9]{6})\b/i,
                        /(?:FB|Meta|Facebook|Google|WhatsApp|IG|Instagram)\s*[-:_]?\s*([0-9]{6})\b/i,
                        /(?:code|otp|pin)\s*[:=-]?\s*([0-9]{6})\b/i,
                        /\b([0-9]{3}[-\s][0-9]{3})\b/,
                        /(?:is|is:)\s*([0-9]{6})\b/i,
                        /(?:confirmation\s+code|verification\s+code|security\s+code|confirm\s+code|login\s+code|passcode)\D{0,35}?([0-9]{4,8})\b/i,
                        /(?:FB|Meta|Facebook|Google|WhatsApp|IG|Instagram)\s*[-:_]?\s*([0-9]{4,8})\b/i,
                        /(?:code|otp|pin)\s*[:=-]?\s*([0-9]{4,8})\b/i
                    ];
                    for (var p = 0; p < strictPatterns.length; p++) {
                        var m = fullText.match(strictPatterns[p]);
                        if (m && m.length > 0) {
                            var candidate = m[m.length - 1].replace(/[- ]/g, '').trim();
                            if (candidate.length >= 4 && candidate.length <= 8) return candidate;
                        }
                    }
                    if (/confirmation|verification|security|facebook|meta|google|whatsapp|instagram|login/i.test(fullText)) {
                        var sixDigits = fullText.match(/\b([0-9]{6})\b/g);
                        if (sixDigits && sixDigits.length > 0) {
                            for (var s = 0; s < sixDigits.length; s++) {
                                var c = sixDigits[s];
                                if (c !== '202400' && c !== '202500' && c !== '202600' && c !== '202700') return c;
                            }
                        }
                    }
                    return '';
                }

                function scanAndReportOtp() {
                    try {
                        // 1. Check tempmail.lol localStorage emails
                        if (window.location.href.indexOf('tempmail.lol') !== -1) {
                            try {
                                var rawEmails = window.localStorage.getItem('emails');
                                if (rawEmails) {
                                    var emailList = JSON.parse(rawEmails);
                                    if (Array.isArray(emailList) && emailList.length > 0) {
                                        for (var i = emailList.length - 1; i >= 0; i--) {
                                            var em = emailList[i];
                                            var combined = (em.subject || '') + ' ' + (em.body || '') + ' ' + (em.html || '');
                                            var found = findAndExtractOtp(combined);
                                            if (found && window.AndroidTempMailScreen) {
                                                window.AndroidTempMailScreen.onOtpDetected(found, em.subject || '', '');
                                                return;
                                            }
                                        }
                                    }
                                }
                            } catch(e) {}
                        }

                        var roots = [];
                        var iframes = document.querySelectorAll('iframe');
                        for (var f = 0; f < iframes.length; f++) {
                            try {
                                var idoc = iframes[f].contentDocument || iframes[f].contentWindow.document;
                                if (idoc && idoc.body) roots.push(idoc.body);
                            } catch(e) {}
                        }
                        var selectors = [
                            '.modal-content', '.modal', '.email-view', '[data-qa="message-body"]', '.message-body', '#mail-content', '.mail-content',
                            '#icerik', '#mesaj-alani', '.email-content', '.message', '.mail', '.inbox-data',
                            '#inbox_content', '.mail-message', '#email-content', '#msg_body', '.email_body',
                            '.pm-mail-body', '#display_email', '.mail_body', '#gm-message'
                        ];
                        for (var s = 0; s < selectors.length; s++) {
                            var el = document.querySelector(selectors[s]);
                            if (el) roots.push(el);
                        }
                        if (document.body) roots.push(document.body);

                        for (var r = 0; r < roots.length; r++) {
                            var txt = roots[r].innerText || roots[r].textContent || '';
                            var foundOtp = findAndExtractOtp(txt);
                            if (foundOtp && window.AndroidTempMailScreen) {
                                var subject = '';
                                var subEl = document.querySelector('.subject, #subject, [data-qa="message-subject"], h1, h2');
                                if (subEl) subject = (subEl.innerText || '').trim();
                                window.AndroidTempMailScreen.onOtpDetected(foundOtp, subject, '');
                                break;
                            }
                        }
                    } catch(e) {}
                }

                function autoOpenLatestMessage() {
                    try {
                        if (window.location.href.indexOf('tempmail.lol') !== -1) {
                            var cards = document.querySelectorAll('.email, div.email');
                            if (cards && cards.length > 0) {
                                var lastCard = cards[cards.length - 1];
                                if (!lastCard.__moeAutoClicked) {
                                    lastCard.__moeAutoClicked = true;
                                    lastCard.scrollIntoView({ behavior: 'smooth', block: 'center' });
                                    lastCard.click();
                                }
                            }
                        }
                    } catch(e) {}
                }

                function attachMessageClickListeners() {
                    try {
                        var msgElements = document.querySelectorAll('.email, div.email, tr, li, [data-qa="message-item"], .mail, .message, .email-item, .inbox-data tr, #messagesTable tr, .mail-row, a[href*="message"], a[href*="msg"]');
                        for (var i = 0; i < msgElements.length; i++) {
                            var elem = msgElements[i];
                            if (!elem.__moeClickListenerAttached) {
                                elem.__moeClickListenerAttached = true;
                                elem.addEventListener('click', function() {
                                    setTimeout(scanAndReportOtp, 300);
                                    setTimeout(scanAndReportOtp, 800);
                                    setTimeout(scanAndReportOtp, 1500);
                                });
                            }
                        }
                    } catch(e) {}
                }

                findAndReportEmail();
                autoOpenLatestMessage();
                scanAndReportOtp();
                attachMessageClickListeners();
                if (!window.__moeTempMailScreenInterval) {
                    window.__moeTempMailScreenInterval = setInterval(function() {
                        findAndReportEmail();
                        autoOpenLatestMessage();
                        scanAndReportOtp();
                        attachMessageClickListeners();
                    }, 1200);
                }
            })();
        """.trimIndent()
        view?.evaluateJavascript(script, null)
    }

    fun handleCopyEmail() {
        if (detectedEmail.isNotBlank()) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Temp Mail", detectedEmail))
            Toast.makeText(context, "ইমেল কপি হয়েছে: $detectedEmail", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "ইমেল লোড হচ্ছে...", Toast.LENGTH_SHORT).show()
        }
    }

    fun handleCopyOtp(otp: String) {
        if (otp.isNotBlank()) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("OTP Code", otp))
            Toast.makeText(context, "ওটিপি কোড কপি হয়েছে: $otp", Toast.LENGTH_SHORT).show()
        }
    }

    fun handleClearData() {
        detectedEmail = ""
        detectedOtp = ""
        try {
            webViewInstance?.clearCache(true)
            webViewInstance?.clearFormData()
            webViewInstance?.clearHistory()
            android.webkit.CookieManager.getInstance().removeAllCookies(null)
            android.webkit.CookieManager.getInstance().flush()
            android.webkit.WebStorage.getInstance().deleteAllData()
        } catch (_: Exception) {}
        webViewInstance?.evaluateJavascript("""
            (function() {
                try {
                    localStorage.clear();
                    sessionStorage.clear();
                } catch(e) {}
            })();
        """.trimIndent(), null)
        webViewInstance?.loadUrl(activeSite.url)
        Toast.makeText(context, "ডাটা ও ক্যাশ ক্লিয়ার করা হয়েছে", Toast.LENGTH_SHORT).show()
    }

    fun handleSaveToSheet() {
        if (detectedEmail.isNotBlank() && detectedEmail.contains("@")) {
            val nowStr = java.text.SimpleDateFormat("dd MMM, hh:mm a", java.util.Locale.getDefault()).format(java.util.Date())
            val acc = SavedTempMailAccount(
                email = detectedEmail,
                password = "",
                domain = detectedEmail.substringAfter("@"),
                username = detectedEmail.substringBefore("@"),
                createdAt = nowStr,
                token = "",
                provider = activeSite.id,
                lastReceivedOtp = detectedOtp,
                lastSubject = ""
            )
            TempMailManager.getInstance(context).saveAccountToSheet(acc)
            Toast.makeText(context, "ইমেলটি শীটে সেভ করা হয়েছে", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "সেভ করার মতো কোনো ইমেল পাওয়া যায়নি", Toast.LENGTH_SHORT).show()
        }
    }

    fun handleChangeEmail() {
        detectedOtp = ""
        if (activeSite.id == "tempmail_lol") {
            handleClearData()
        } else {
            webViewInstance?.evaluateJavascript("""
                (function() {
                    var delBtn = document.querySelector('[data-qa="delete-button"]') || 
                                 document.querySelector('button[title*="Delete" i]') ||
                                 document.querySelector('#click-to-delete') ||
                                 document.querySelector('.btn-delete');
                    if (delBtn) {
                        delBtn.click();
                        return;
                    }
                    var btn = document.querySelector('[data-qa="random-button"]') || 
                              document.querySelector('button[title*="Random" i]') ||
                              document.querySelector('[data-qa="change-button"]') ||
                              document.querySelector('#new') ||
                              document.querySelector('.new');
                    if (btn) {
                        btn.click();
                    } else {
                        location.reload();
                    }
                })();
            """.trimIndent(), null)
            Toast.makeText(context, "ডিলিট করে নতুন ইমেল আনা হচ্ছে...", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.MarkEmailRead,
                                contentDescription = "Temp Mail",
                                tint = accentGreen,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Temp Mail",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textColor
                                )
                                Text(
                                    text = if (detectedEmail.isNotBlank()) detectedEmail else activeSite.name,
                                    fontSize = 11.sp,
                                    color = if (detectedEmail.isNotBlank()) accentGreen else subTextColor,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        if (onBack != null) {
                            IconButton(onClick = onBack) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = textColor
                                )
                            }
                        }
                    },
                    actions = {
                        // Copy button
                        Button(
                            onClick = { handleCopyEmail() },
                            colors = ButtonDefaults.buttonColors(containerColor = cardBg),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "কপি",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "কপি",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        // Save to Sheet
                        IconButton(onClick = { handleSaveToSheet() }) {
                            Icon(
                                imageVector = Icons.Default.Save,
                                contentDescription = "শীটে সেভ",
                                tint = Color(0xFF64B5F6)
                            )
                        }

                        // Change email button
                        IconButton(onClick = { handleChangeEmail() }) {
                            Icon(
                                imageVector = Icons.Default.Autorenew,
                                contentDescription = "নতুন ইমেল",
                                tint = textColor
                            )
                        }

                        // Reload button
                        IconButton(onClick = { webViewInstance?.reload() }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "রিলোড",
                                tint = textColor
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = headerBg)
                )

                // Website switcher bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .background(Color(0xFF11131A))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "সার্ভিস:",
                        color = subTextColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )

                    TEMP_MAIL_WEBSITES.forEach { site ->
                        val isSelected = activeSite.id == site.id
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) Color(0xFF1A73E8) else Color(0xFF222530))
                                .border(
                                    width = if (isSelected) 1.dp else 0.5.dp,
                                    color = if (isSelected) Color(0xFF64B5F6) else Color(0xFF333745),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    if (activeSite.id != site.id) {
                                        activeSite = site
                                        detectedEmail = ""
                                        detectedOtp = ""
                                        webViewInstance?.loadUrl(site.url)
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(accentGreen)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                Text(
                                    text = site.shortLabel,
                                    fontSize = 11.sp,
                                    color = if (isSelected) Color.White else Color(0xFFBDC1C6),
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Clear Data Button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF3E2723))
                            .border(width = 0.5.dp, color = Color(0xFFFF7043), shape = RoundedCornerShape(12.dp))
                            .clickable { handleClearData() }
                            .padding(horizontal = 9.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CleaningServices,
                                contentDescription = "ডাটা ক্লিয়ার",
                                tint = Color(0xFFFF8A65),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "ডাটা ক্লিয়ার",
                                fontSize = 11.sp,
                                color = Color(0xFFFFCCBC),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        },
        containerColor = bgColor
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(bgColor)
        ) {
            // Live OTP banner
            AnimatedVisibility(
                visible = detectedOtp.isNotBlank(),
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    color = otpBannerBg,
                    shadowElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = "OTP Detected",
                                tint = Color(0xFF69F0AE),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "ওটিপি কোড: $detectedOtp",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Button(
                            onClick = { handleCopyOtp(detectedOtp) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C853)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("কপি কোড", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }

            if (isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = accentGreen,
                    trackColor = Color.Transparent
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bgColor)
            ) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            webViewInstance = this
                            setBackgroundColor(android.graphics.Color.parseColor("#0D0E12"))

                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.databaseEnabled = true
                            settings.setSupportZoom(true)
                            settings.builtInZoomControls = true
                            settings.displayZoomControls = false
                            settings.useWideViewPort = true
                            settings.loadWithOverviewMode = true
                            settings.textZoom = 90
                            setInitialScale(90)

                            addJavascriptInterface(
                                MainTempMailJsBridge(
                                    onEmailReceived = { email ->
                                        detectedEmail = email
                                        TempMailManager.getInstance(ctx).updateCurrentAccountFromWeb(email)
                                    },
                                    onOtpReceived = { otp, subject ->
                                        detectedOtp = otp
                                        if (detectedEmail.isNotBlank()) {
                                            TempMailManager.getInstance(ctx).updateSavedAccountOtp(detectedEmail, otp, subject)
                                        }
                                    }
                                ),
                                "AndroidTempMailScreen"
                            )

                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    isLoading = newProgress < 90
                                    if (newProgress > 60) {
                                        injectCompactScript(view)
                                    }
                                }
                            }

                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    isLoading = true
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    isLoading = false
                                    injectCompactScript(view)
                                }

                                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                    return false
                                }
                            }

                            loadUrl(activeSite.url)
                        }
                    },
                    update = { view ->
                        webViewInstance = view
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
