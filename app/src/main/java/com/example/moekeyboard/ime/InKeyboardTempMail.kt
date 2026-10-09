package com.example.moekeyboard.ime

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.moekeyboard.tempmail.TempMailManager
import com.example.moekeyboard.tempmail.SavedTempMailAccount
import com.example.moekeyboard.tempmail.AuthCredential

data class TempMailWebSite(
    val id: String,
    val name: String,
    val url: String,
    val shortLabel: String
)

val TEMP_MAIL_WEBSITES = listOf(
    TempMailWebSite("temp_mail_io", "Temp-Mail.io", "https://temp-mail.io/en", "Temp-Mail.io"),
    TempMailWebSite("tempmail_lol", "Tempmail.lol", "https://tempmail.lol", "Tempmail.lol")
)

private const val PREFS_NAME = "moe_keyboard_tempmail"
private const val KEY_SELECTED_SITE = "selected_tempmail_site"

object TempMailWebBridge {
    private var persistentWebView: WebView? = null
    var activeWebView: WebView? = null
        get() = field ?: persistentWebView

    var latestWebEmail: String = ""
    var latestWebOtp: String = ""
    var latestWebOtpSubject: String = ""
    var lastInsertedEmail: String = ""
    private var cachedSiteIndex: Int = -1
    @Volatile
    var isDeletingOperationRunning = false

    private val listeners = mutableListOf<TempMailEventListener>()

    interface TempMailEventListener {
        fun onEmailReceived(email: String)
        fun onOtpReceived(otp: String, subject: String)
    }

    fun addListener(listener: TempMailEventListener) {
        synchronized(listeners) {
            if (!listeners.contains(listener)) {
                listeners.add(listener)
            }
        }
    }

    fun removeListener(listener: TempMailEventListener) {
        synchronized(listeners) {
            listeners.remove(listener)
        }
    }

    fun notifyEmailReceived(email: String) {
        val clean = email.trim()
        if (clean.isNotBlank() && clean.contains("@")) {
            latestWebEmail = clean
            synchronized(listeners) {
                listeners.forEach { it.onEmailReceived(clean) }
            }
        }
    }

    fun notifyOtpReceived(otp: String, subject: String = "") {
        val clean = otp.trim()
        if (clean.isNotBlank() && clean.length in 4..8) {
            latestWebOtp = clean
            latestWebOtpSubject = subject
            synchronized(listeners) {
                listeners.forEach { it.onOtpReceived(clean, subject) }
            }
        }
    }

    fun getCurrentSite(context: Context): TempMailWebSite {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedId = prefs.getString(KEY_SELECTED_SITE, "temp_mail_io") ?: "temp_mail_io"
        var idx = TEMP_MAIL_WEBSITES.indexOfFirst { it.id == savedId }
        if (idx == -1) idx = 0
        cachedSiteIndex = idx
        return TEMP_MAIL_WEBSITES[cachedSiteIndex]
    }

    fun switchSite(context: Context, site: TempMailWebSite) {
        val idx = TEMP_MAIL_WEBSITES.indexOfFirst { it.id == site.id }.coerceAtLeast(0)
        cachedSiteIndex = idx
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_SELECTED_SITE, site.id).apply()

        latestWebEmail = ""
        latestWebOtp = ""
        latestWebOtpSubject = ""
        val wv = getOrCreatePersistentWebView(context)
        wv.post {
            wv.stopLoading()
            wv.loadUrl(site.url)
        }
    }

    fun switchToNextSite(context: Context): TempMailWebSite {
        val currentIdx = if (cachedSiteIndex == -1) 0 else cachedSiteIndex
        val nextIdx = (currentIdx + 1) % TEMP_MAIL_WEBSITES.size
        val next = TEMP_MAIL_WEBSITES[nextIdx]
        switchSite(context, next)
        return next
    }

    fun clearSiteData(context: Context, onCleared: (() -> Unit)? = null) {
        latestWebEmail = ""
        latestWebOtp = ""
        latestWebOtpSubject = ""
        Handler(Looper.getMainLooper()).post {
            try {
                val wv = activeWebView
                wv?.clearCache(true)
                wv?.clearFormData()
                wv?.clearHistory()
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()
                WebStorage.getInstance().deleteAllData()
                wv?.evaluateJavascript("try { localStorage.clear(); sessionStorage.clear(); } catch(e){};", null)
                val current = getCurrentSite(context)
                wv?.loadUrl(current.url)
                onCleared?.invoke()
            } catch (_: Exception) {
                onCleared?.invoke()
            }
        }
    }

    fun injectCompactScript(view: WebView?) {
        val script = """
            (function() {
                try {
                    var styleId = 'moe-tempmail-compact-style';
                    if (!document.getElementById(styleId)) {
                        var style = document.createElement('style');
                        style.id = styleId;
                        style.innerHTML = `
                            header, nav, nav.header, footer, .ad-banner, .advertisement, 
                            [id*="google_ads"], [class*="adsbygoogle"], iframe[src*="doubleclick"], 
                            .reklam, #reklam, .adsbygoogle, .banner, #banner, .cookie-banner, 
                            .consent-banner, #cookie-law-info-bar, .modal-backdrop, .modal, 
                            .fc-consent-root, .adthrive, .ads-holder {
                                display: none !important;
                            }
                            body {
                                zoom: 0.90 !important;
                                -webkit-text-size-adjust: 90% !important;
                                padding-top: 2px !important;
                                margin-top: 0 !important;
                            }
                            main, #main, .container, .main-content, #app {
                                padding-top: 2px !important;
                                margin-top: 0 !important;
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
                            var elIo = document.querySelector('[data-qa="current-email"]') || 
                                       document.getElementById('email') || 
                                       document.querySelector('#mail');
                            if (elIo) {
                                val = (elIo.value || elIo.getAttribute('value') || elIo.innerText || elIo.textContent || '').trim();
                            }
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
                            var inputs = document.querySelectorAll('input, [data-clipboard-text], [data-email]');
                            for (var i = 0; i < inputs.length; i++) {
                                var v = (inputs[i].value || inputs[i].getAttribute('data-clipboard-text') || inputs[i].getAttribute('data-email') || inputs[i].getAttribute('value') || '').trim();
                                if (v && v.indexOf('@') !== -1 && !v.startsWith('http') && v.indexOf(' ') === -1) {
                                    val = v;
                                    break;
                                }
                            }
                        }

                        // 10. Text regex pattern scan
                        if (!val || val.indexOf('@') === -1) {
                            var emailRegex = /[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}/g;
                            var bodyText = document.body ? document.body.innerText : '';
                            var match = bodyText.match(emailRegex);
                            if (match && match.length > 0) {
                                for (var m = 0; m < match.length; m++) {
                                    var candidate = match[m];
                                    if (candidate.indexOf('@') > 0 && 
                                        !candidate.startsWith('info@') && 
                                        !candidate.startsWith('support@') &&
                                        !candidate.startsWith('contact@') &&
                                        !candidate.startsWith('noreply@')) {
                                        val = candidate;
                                        break;
                                    }
                                }
                            }
                        }

                        if (val && val.indexOf('@') !== -1 && window.AndroidTempMail) {
                            window.AndroidTempMail.onEmailDetected(val);
                        }
                    } catch(e) {}
                }

                function findAndExtractOtpFromText(fullText) {
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
                            if (candidate.length >= 4 && candidate.length <= 8) {
                                return candidate;
                            }
                        }
                    }
                    if (/confirmation|verification|security|facebook|meta|google|whatsapp|instagram|login|auth|passcode/i.test(fullText)) {
                        var sixDigits = fullText.match(/\b([0-9]{6})\b/g);
                        if (sixDigits && sixDigits.length > 0) {
                            for (var s = 0; s < sixDigits.length; s++) {
                                var c = sixDigits[s];
                                if (c !== '202400' && c !== '202500' && c !== '202600' && c !== '202700') {
                                    return c;
                                }
                            }
                        }
                    }
                    return '';
                }

                function scanAndReportOtp() {
                    try {
                        var roots = [];
                        // Check iframes
                        var iframes = document.querySelectorAll('iframe');
                        for (var f = 0; f < iframes.length; f++) {
                            try {
                                var idoc = iframes[f].contentDocument || iframes[f].contentWindow.document;
                                if (idoc && idoc.body) roots.push(idoc.body);
                            } catch(e) {}
                        }
                        // Specific email message containers across various services
                        var selectors = [
                            '[data-qa="message-body"]', '.message-body', '#mail-content', '.mail-content',
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
                            var foundOtp = findAndExtractOtpFromText(txt);
                            if (foundOtp && window.AndroidTempMail) {
                                var subject = '';
                                var subEl = document.querySelector('.subject, #subject, [data-qa="message-subject"], h1, h2');
                                if (subEl) subject = (subEl.innerText || '').trim();
                                window.AndroidTempMail.onOtpDetected(foundOtp, subject, '');
                                break;
                            }
                        }
                    } catch(e) {}
                }

                // Attach click listeners to all message list rows/items so opening any message instantly extracts OTP!
                function attachMessageClickListeners() {
                    try {
                        var msgElements = document.querySelectorAll('tr, li, [data-qa="message-item"], .mail, .message, .email-item, .inbox-data tr, #messagesTable tr, .mail-row, a[href*="message"], a[href*="msg"]');
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
                scanAndReportOtp();
                attachMessageClickListeners();

                if (!window.__moeTempMailCheckInterval) {
                    window.__moeTempMailCheckInterval = setInterval(function() {
                        findAndReportEmail();
                        scanAndReportOtp();
                        attachMessageClickListeners();
                    }, 1000);
                }
            })();
        """.trimIndent()
        view?.evaluateJavascript(script, null)
    }

    fun init(context: Context) {
        Handler(Looper.getMainLooper()).post {
            getOrCreatePersistentWebView(context)
        }
    }

    fun getOrCreatePersistentWebView(context: Context): WebView {
        val existing = persistentWebView
        if (existing != null) {
            return existing
        }
        val wv = WebView(context.applicationContext).apply {
            setBackgroundColor(android.graphics.Color.parseColor("#0D0E12"))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.textZoom = 85
            setInitialScale(85)
            settings.userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

            addJavascriptInterface(
                TempMailJsBridge(
                    onEmailReceived = { email ->
                        latestWebEmail = email
                        notifyEmailReceived(email)
                        TempMailManager.getInstance(context).updateCurrentAccountFromWeb(email)
                    },
                    onOtpReceived = { otp, subject, _ ->
                        latestWebOtp = otp
                        latestWebOtpSubject = subject
                        notifyOtpReceived(otp, subject)
                        if (latestWebEmail.isNotBlank()) {
                            TempMailManager.getInstance(context).updateSavedAccountOtp(latestWebEmail, otp, subject)
                        }
                    }
                ),
                "AndroidTempMail"
            )

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    injectCompactScript(view)
                }
            }

            val currentSite = getCurrentSite(context)
            loadUrl(currentSite.url)
        }
        persistentWebView = wv
        return wv
    }

    fun extractCurrentEmail(callback: (String) -> Unit) {
        val wv = activeWebView
        if (wv != null) {
            wv.post {
                wv.evaluateJavascript("""
                    (function() {
                        var val = '';
                        var url = window.location.href;

                        // 1. temp-mail.io
                        if (url.indexOf('temp-mail.io') !== -1) {
                            var elIo = document.querySelector('[data-qa="current-email"]') || 
                                       document.getElementById('email') || 
                                       document.querySelector('#mail');
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

                        return (val && val.indexOf('@') !== -1) ? val.trim() : '';
                    })();
                """.trimIndent()) { res ->
                    val clean = res?.trim('"', '\'', ' ', '\\') ?: ""
                    if (clean.isNotBlank() && clean.contains("@")) {
                        latestWebEmail = clean
                        callback(clean)
                    } else if (latestWebEmail.isNotBlank()) {
                        callback(latestWebEmail)
                    } else {
                        callback("")
                    }
                }
            }
        } else {
            callback(latestWebEmail)
        }
    }

    fun extractRawEmailFromDom(callback: (String) -> Unit) {
        val wv = activeWebView
        if (wv != null) {
            wv.post {
                wv.evaluateJavascript("""
                    (function() {
                        var val = '';
                        var url = window.location.href;

                        // 1. temp-mail.io
                        if (url.indexOf('temp-mail.io') !== -1) {
                            var elIo = document.querySelector('[data-qa="current-email"]') || 
                                       document.getElementById('email') || 
                                       document.querySelector('#mail');
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

                        return (val && val.indexOf('@') !== -1) ? val.trim() : '';
                    })();
                """.trimIndent()) { res ->
                    val clean = res?.trim('"', '\'', ' ', '\\') ?: ""
                    callback(if (clean.contains("@")) clean else "")
                }
            }
        } else {
            callback("")
        }
    }

    fun deleteAndGetNewEmail(callback: (String) -> Unit) {
        val wv = activeWebView
        if (wv == null) {
            callback("")
            return
        }
        if (isDeletingOperationRunning) {
            callback(latestWebEmail)
            return
        }
        isDeletingOperationRunning = true

        wv.post {
            val oldEmail = latestWebEmail
            val currentSite = getCurrentSite(wv.context)
            val currentUrl = wv.url ?: currentSite.url

            // Case 1: https://tempmail.lol
            if (currentSite.id == "tempmail_lol" || currentUrl.contains("tempmail.lol")) {
                latestWebEmail = ""
                latestWebOtp = ""
                
                // Use Coroutine to call API fast
                CoroutineScope(Dispatchers.Main).launch {
                    val manager = TempMailManager.getInstance(wv.context)
                    val newEmail = withContext(Dispatchers.IO) {
                        manager.generateNewTempMailLolAccount()
                    }
                    val newToken = manager.currentToken

                    if (newEmail.isNotBlank()) {
                        wv.evaluateJavascript("""
                            (function() {
                                try {
                                    localStorage.setItem('address', '$newEmail');
                                    localStorage.setItem('address_token', '$newToken');
                                    localStorage.removeItem('emails');
                                    localStorage.setItem('expires_at', (Date.now() + 3600000).toString());
                                } catch(e) {}
                                return 'injected';
                            })();
                        """.trimIndent()) {
                            wv.stopLoading()
                            wv.loadUrl("https://tempmail.lol")
                            
                            latestWebEmail = newEmail
                            isDeletingOperationRunning = false
                            callback(newEmail)
                        }
                    } else {
                        // Fallback if API fails
                        wv.loadUrl("https://tempmail.lol")
                        var isDone = false
                        val handler = Handler(Looper.getMainLooper())
                        fun finishWithEmail(email: String) {
                            if (isDone) return
                            isDone = true
                            latestWebEmail = email
                            isDeletingOperationRunning = false
                            callback(email)
                        }
                        var attempts = 0
                        val poller = object : Runnable {
                            override fun run() {
                                if (isDone) return
                                attempts++
                                extractRawEmailFromDom { mail ->
                                    if (mail.isNotBlank() && mail.contains("@") && mail != oldEmail) {
                                        finishWithEmail(mail)
                                    } else if (attempts < 20) {
                                        handler.postDelayed(this, 400)
                                    } else {
                                        finishWithEmail(oldEmail)
                                    }
                                }
                            }
                        }
                        handler.postDelayed(poller, 800)
                    }
                }
                return@post
            }

            // Case 2: temp-mail.io
            // "temp-mail.io etar ketre shortcut a click korle delete a click kore noton emaul dey"
            val script = """
                (function() {
                    if (window.__isTempMailActionInProgress) {
                        return 'busy';
                    }
                    window.__isTempMailActionInProgress = true;
                    setTimeout(function() { window.__isTempMailActionInProgress = false; }, 4000);

                    var clicked = false;
                    function singleClick(el) {
                        if (!el || clicked) return false;
                        clicked = true;
                        try {
                            el.removeAttribute('disabled');
                            el.disabled = false;
                        } catch(e) {}
                        try {
                            if (typeof el.click === 'function') {
                                el.click();
                            } else {
                                el.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true, view: window }));
                            }
                            return true;
                        } catch(e) {
                            return false;
                        }
                    }

                    // 1. Delete button first for temp-mail.io
                    var delBtn = document.querySelector('[data-qa="delete-button"]') || 
                                 document.querySelector('button[title*="Delete" i]') ||
                                 document.querySelector('#click-to-delete') ||
                                 document.querySelector('.btn-delete');
                    if (delBtn) singleClick(delBtn);

                    // 2. Random button fallback
                    if (!clicked) {
                        var randBtn = document.querySelector('[data-qa="random-button"]') || 
                                      document.querySelector('button[title*="Random" i]') ||
                                      document.querySelector('.random');
                        if (randBtn) singleClick(randBtn);
                    }

                    // 3. Change button fallback
                    if (!clicked) {
                        var changeBtn = document.querySelector('[data-qa="change-button"]') ||
                                        document.querySelector('button[title*="Change" i]');
                        if (changeBtn) singleClick(changeBtn);
                    }

                    return clicked ? 'clicked' : 'none';
                })();
            """.trimIndent()

            wv.evaluateJavascript(script) { _ ->
                var attempts = 0
                val handler = Handler(Looper.getMainLooper())
                val poller = object : Runnable {
                    override fun run() {
                        attempts++
                        extractRawEmailFromDom { newEmail ->
                            if (newEmail.isNotBlank() && newEmail != oldEmail) {
                                latestWebEmail = newEmail
                                isDeletingOperationRunning = false
                                callback(newEmail)
                            } else if (attempts < 25) {
                                handler.postDelayed(this, 250)
                            } else {
                                extractRawEmailFromDom { current ->
                                    isDeletingOperationRunning = false
                                    callback(current.ifBlank { oldEmail })
                                }
                            }
                        }
                    }
                }
                handler.postDelayed(poller, 400)
            }
        }
    }

    fun openMessageAndExtractOtp() {
        val wv = activeWebView
        if (wv != null) {
            wv.post {
                wv.evaluateJavascript("""
                    (function() {
                        try {
                            // 1. tempmail.lol:
                            // "noton code asle seta nise thake ashe oivabe e click korbe"
                            if (window.location.href.indexOf('tempmail.lol') !== -1) {
                                var emailCards = document.querySelectorAll('.email, div.email');
                                if (emailCards && emailCards.length > 0) {
                                    // Target newest incoming message at the bottom of the list
                                    var lastCard = emailCards[emailCards.length - 1];
                                    try {
                                        lastCard.scrollIntoView({ behavior: 'smooth', block: 'center' });
                                        lastCard.click();
                                        return;
                                    } catch(e) {}
                                }
                            }

                            // 2. temp-mail.io:
                            var msgList = document.querySelectorAll('[data-qa="message-item"], [data-qa="messages-list"] > *, ul.messages > li, .message, .mail');
                            if (msgList && msgList.length > 0) {
                                var latestMsg = msgList[msgList.length - 1];
                                try {
                                    latestMsg.click();
                                    return;
                                } catch(e) {}
                            }

                            // 3. Generic selectors fallback
                            var msgSelectors = [
                                '.email', 'div.email', '[data-qa="message-item"]', '[data-qa="messages-list"] > *',
                                '#gelen-kutusu li a', '.gelenler tr', '#inbox-data tr', '.mail-list .mail',
                                'table.table tr', '#messagesTable tr', '.email-item', '#inbox tr', '.mail-row', 'a[href*="message"]'
                            ];
                            for (var s = 0; s < msgSelectors.length; s++) {
                                var all = document.querySelectorAll(msgSelectors[s]);
                                if (all && all.length > 0) {
                                    all[all.length - 1].click();
                                    return;
                                }
                            }
                        } catch(e) {}
                    })();
                """.trimIndent(), null)
            }
        }
    }

    fun extractOtpFromWebPage(excludeOtp: String? = null, callback: (String) -> Unit) {
        val wv = activeWebView
        if (wv != null) {
            wv.post {
                val safeExclude = excludeOtp?.replace("'", "\\'") ?: ""
                wv.evaluateJavascript("""
                    (function() {
                        try {
                            var excluded = '$safeExclude';

                            function extractCodeFromStr(str) {
                                if (!str) return '';
                                var patterns = [
                                    /(?:confirmation\s+code|verification\s+code|security\s+code|confirm\s+code|login\s+code|passcode)\D{0,35}?([0-9]{6})\b/i,
                                    /(?:FB|Meta|Facebook|Google|WhatsApp|IG|Instagram)\s*[-:_]?\s*([0-9]{6})\b/i,
                                    /(?:code|otp|pin)\s*[:=-]?\s*([0-9]{6})\b/i,
                                    /\b([0-9]{3}[-\s][0-9]{3})\b/,
                                    /(?:is|is:)\s*([0-9]{6})\b/i,
                                    /(?:confirmation\s+code|verification\s+code|security\s+code|confirm\s+code|login\s+code|passcode)\D{0,35}?([0-9]{4,8})\b/i,
                                    /(?:FB|Meta|Facebook|Google|WhatsApp|IG|Instagram)\s*[-:_]?\s*([0-9]{4,8})\b/i,
                                    /(?:code|otp|pin)\s*[:=-]?\s*([0-9]{4,8})\b/i
                                ];
                                for (var p = 0; p < patterns.length; p++) {
                                    var m = str.match(patterns[p]);
                                    if (m && m.length > 0) {
                                        var candidate = m[m.length - 1].replace(/[- ]/g, '').trim();
                                        if (candidate.length >= 4 && candidate.length <= 8) {
                                            if (!excluded || candidate !== excluded) {
                                                return candidate;
                                            }
                                        }
                                    }
                                }
                                var six = str.match(/\b([0-9]{6})\b/g);
                                if (six) {
                                    for (var i = 0; i < six.length; i++) {
                                        var c = six[i];
                                        if (c !== '202400' && c !== '202500' && c !== '202600' && c !== '202700') {
                                            if (!excluded || c !== excluded) return c;
                                        }
                                    }
                                }
                                return '';
                            }

                            // 1. Direct inspection of tempmail.lol localStorage.getItem('emails')
                            if (window.location.href.indexOf('tempmail.lol') !== -1) {
                                try {
                                    var rawEmails = window.localStorage.getItem('emails');
                                    if (rawEmails) {
                                        var arr = JSON.parse(rawEmails);
                                        if (Array.isArray(arr) && arr.length > 0) {
                                            // Inspect from newest message (at the bottom / end of array)
                                            for (var idx = arr.length - 1; idx >= 0; idx--) {
                                                var em = arr[idx];
                                                var full = (em.subject || '') + ' ' + (em.body || '') + ' ' + (em.html || '');
                                                var found = extractCodeFromStr(full);
                                                if (found) return found;
                                            }
                                        }
                                    }
                                } catch(e) {}
                            }

                            // 2. Scan DOM roots (modal-content, message-body, body, iframes)
                            var roots = [];
                            var iframes = document.querySelectorAll('iframe');
                            for (var f = 0; f < iframes.length; f++) {
                                try {
                                    var idoc = iframes[f].contentDocument || iframes[f].contentWindow.document;
                                    if (idoc && idoc.body) roots.push(idoc.body);
                                } catch(e) {}
                            }
                            var selectors = [
                                '.modal-content', '.modal', '.email-view', '[data-qa="message-body"]',
                                '.message-body', '#mail-content', '.mail-content', '#icerik', '#mesaj-alani',
                                '.email-content', '.message', '.mail', '.email'
                            ];
                            for (var s = 0; s < selectors.length; s++) {
                                var el = document.querySelector(selectors[s]);
                                if (el) roots.push(el);
                            }
                            if (document.body) roots.push(document.body);

                            for (var r = 0; r < roots.length; r++) {
                                var fullText = roots[r].innerText || roots[r].textContent || '';
                                var cand = extractCodeFromStr(fullText);
                                if (cand) return cand;
                            }
                        } catch(e) {}
                        return '';
                    })();
                """.trimIndent()) { res ->
                    val clean = res?.trim('"', '\'', ' ', '\\') ?: ""
                    callback(clean)
                }
            }
        } else {
            callback("")
        }
    }
}

class TempMailJsBridge(
    private val onEmailReceived: (String) -> Unit,
    private val onOtpReceived: (String, String, String) -> Unit = { _, _, _ -> }
) {
    @JavascriptInterface
    fun onEmailDetected(email: String?) {
        if (!email.isNullOrBlank() && email.contains("@")) {
            Handler(Looper.getMainLooper()).post {
                if (!TempMailWebBridge.isDeletingOperationRunning) {
                    TempMailWebBridge.latestWebEmail = email.trim()
                    onEmailReceived(email.trim())
                }
            }
        }
    }

    @JavascriptInterface
    fun onOtpDetected(otp: String?, subject: String?, from: String?) {
        if (!otp.isNullOrBlank() && otp.trim().length in 4..8) {
            Handler(Looper.getMainLooper()).post {
                val cleanOtp = otp.trim()
                val cleanSub = subject?.trim() ?: ""
                val cleanFrom = from?.trim() ?: ""
                TempMailWebBridge.latestWebOtp = cleanOtp
                TempMailWebBridge.latestWebOtpSubject = cleanSub
                onOtpReceived(cleanOtp, cleanSub, cleanFrom)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun InKeyboardTempMail(
    isDarkTheme: Boolean,
    height: Dp,
    onInsertText: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val tempMailManager = remember { TempMailManager.getInstance(context) }
    var activeSite by remember { mutableStateOf(TempMailWebBridge.getCurrentSite(context)) }
    var detectedEmail by remember { mutableStateOf(TempMailWebBridge.latestWebEmail) }
    var detectedOtp by remember { mutableStateOf(TempMailWebBridge.latestWebOtp) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var isHistoryView by remember { mutableStateOf(false) }
    var isSheetView by remember { mutableStateOf(false) }
    var isClearingData by remember { mutableStateOf(false) }
    var savedAccountsList by remember { mutableStateOf(TempMailManager.getInstance(context).getSavedAccounts()) }
    var sheetAccountsList by remember { mutableStateOf(TempMailManager.getInstance(context).getSheetAccounts()) }

    fun handleSaveToSheet() {
        val email = if (detectedEmail.isNotBlank()) detectedEmail else TempMailWebBridge.latestWebEmail
        if (email.isNotBlank() && email.contains("@")) {
            val nowStr = java.text.SimpleDateFormat("dd MMM, hh:mm a", java.util.Locale.getDefault()).format(java.util.Date())
            val acc = com.example.moekeyboard.tempmail.SavedTempMailAccount(
                email = email,
                password = "",
                domain = email.substringAfter("@"),
                username = email.substringBefore("@"),
                createdAt = nowStr,
                token = "",
                provider = activeSite.id,
                lastReceivedOtp = detectedOtp,
                lastSubject = ""
            )
            TempMailManager.getInstance(context).saveAccountToSheet(acc)
            sheetAccountsList = TempMailManager.getInstance(context).getSheetAccounts()
            Toast.makeText(context, "ইমেলটি শীটে সেভ করা হয়েছে", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "সেভ করার মতো কোনো ইমেল পাওয়া যায়নি", Toast.LENGTH_SHORT).show()
        }
    }

    fun handleCopyAllSheet() {
        val list = TempMailManager.getInstance(context).getSheetAccounts()
        if (list.isNotEmpty()) {
            val all = list.joinToString("\n") { it.email }
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Sheet Emails", all))
            Toast.makeText(context, "সব ইমেল কপি হয়েছে (${list.size}টি)", Toast.LENGTH_SHORT).show()
        }
    }

    // Register live event listener for instant dynamic updates
    DisposableEffect(Unit) {
        val listener = object : TempMailWebBridge.TempMailEventListener {
            override fun onEmailReceived(email: String) {
                detectedEmail = email
            }

            override fun onOtpReceived(otp: String, subject: String) {
                detectedOtp = otp
            }
        }
        TempMailWebBridge.addListener(listener)
        onDispose {
            TempMailWebBridge.removeListener(listener)
        }
    }

    val bgColor = Color(0xFF0D0E12)
    val headerBg = Color(0xFF161820)
    val cardBg = Color(0xFF222530)
    val accentGreen = Color(0xFF00E676)
    val writeButtonBg = Color(0xFF00C853)
    val otpBannerBg = Color(0xFF0D532C)
    val textColor = Color(0xFFFFFFFF)
    val subTextColor = Color(0xFFA0A5B5)

    fun handleWriteEmail() {
        if (detectedEmail.isNotBlank()) {
            onInsertText(detectedEmail)
            Toast.makeText(context, "ইমেল লেখা হয়েছে: $detectedEmail", Toast.LENGTH_SHORT).show()
        } else {
            TempMailWebBridge.extractCurrentEmail { clean ->
                if (clean.isNotBlank() && clean.contains("@")) {
                    detectedEmail = clean
                    onInsertText(clean)
                    Toast.makeText(context, "ইমেল লেখা হয়েছে: $clean", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "ইমেল লোড হচ্ছে, এক মুহূর্ত অপেক্ষা করুন", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun handleCopyEmail() {
        val target = if (detectedEmail.isNotBlank()) detectedEmail else TempMailWebBridge.latestWebEmail
        if (target.isNotBlank()) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Temp Mail", target))
            Toast.makeText(context, "ইমেল কপি হয়েছে: $target", Toast.LENGTH_SHORT).show()
        } else {
            TempMailWebBridge.extractCurrentEmail { clean ->
                if (clean.isNotBlank() && clean.contains("@")) {
                    detectedEmail = clean
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Temp Mail", clean))
                    Toast.makeText(context, "ইমেল কপি হয়েছে: $clean", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "ইমেল লোড হচ্ছে, এক মুহূর্ত অপেক্ষা করুন", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun handleChangeEmail() {
        Toast.makeText(context, "নতুন ইমেল তৈরি করা হচ্ছে...", Toast.LENGTH_SHORT).show()
        detectedOtp = ""
        TempMailWebBridge.latestWebOtp = ""
        TempMailManager.getInstance(context).clearActiveOtpState()
        TempMailWebBridge.deleteAndGetNewEmail { newEmail ->
            if (newEmail.isNotBlank()) {
                detectedEmail = newEmail
                Toast.makeText(context, "নতুন ইমেল: $newEmail", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun handleClearData() {
        isClearingData = true
        detectedOtp = ""
        TempMailWebBridge.latestWebOtp = ""
        Toast.makeText(context, "টেম্প মেইল ডাটা ও ক্যাশ ক্লিয়ার করা হচ্ছে...", Toast.LENGTH_SHORT).show()
        TempMailWebBridge.clearSiteData(context) {
            detectedEmail = ""
            isClearingData = false
            Toast.makeText(context, "ডাটা ক্লিয়ার সফল! নতুন ফ্রেশ সেশন চালু হয়েছে", Toast.LENGTH_SHORT).show()
        }
    }

    fun handleInsertOtp(code: String) {
        if (code.isNotBlank()) {
            onInsertText(code)
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("OTP Code", code))
            Toast.makeText(context, "ওটিপি কোড পেস্ট ও কপি হয়েছে: $code", Toast.LENGTH_SHORT).show()
        }
    }

    fun handleCopyOtp(code: String) {
        if (code.isNotBlank()) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("OTP Code", code))
            Toast.makeText(context, "ওটিপি কোড কপি হয়েছে: $code", Toast.LENGTH_SHORT).show()
        }
    }

    fun handleInsertCredential() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val text = clip.getItemAt(0).text?.toString() ?: ""
            // Regex to match: First name: <name>, Login: <login>, Password: <pass>
            // Need to support multiline too if possible
            val regex = Regex("""First name:\s*(.*)\s*Login:\s*(.*)\s*Password:\s*(.*)""", RegexOption.IGNORE_CASE)
            val match = regex.find(text)
            
            if (match != null) {
                val (firstName, login, password) = match.destructured
                tempMailManager.saveCredential(AuthCredential(firstName.trim(), login.trim(), password.trim()))
                Toast.makeText(context, "ক্রেডেনশিয়াল সেভ হয়েছে!", Toast.LENGTH_SHORT).show()
            } else {
                val creds = tempMailManager.getSavedCredentials()
                if (creds.isNotEmpty()) {
                    val last = creds.first() // Latest added is at index 0
                    onInsertText(last.login)
                    onInsertText(" ") // Space
                    onInsertText(last.password)
                    Toast.makeText(context, "লগইন ও পাসওয়ার্ড বসানো হয়েছে", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "কোনো ক্রেডেনশিয়াল সেভ করা নেই।", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(bgColor)
    ) {
        // 1. Top Action Toolbar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = headerBg,
            shadowElevation = 2.dp
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left: Icon + Detected Email badge
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .clip(RoundedCornerShape(8.dp))
                            .background(cardBg)
                            .clickable {
                                if (isHistoryView || isSheetView) {
                                    isHistoryView = false
                                    isSheetView = false
                                } else {
                                    handleWriteEmail()
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = when {
                                isSheetView -> Icons.Default.Description
                                isHistoryView -> Icons.Default.History
                                else -> Icons.Default.MarkEmailRead
                            },
                            contentDescription = "Temp Mail",
                            tint = accentGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = when {
                                isSheetView -> "শীট (সেভ করা ইমেল)"
                                isHistoryView -> "ইমেল ও কোড হিস্ট্রি"
                                detectedEmail.isNotBlank() -> detectedEmail
                                else -> "ইমেল লোড হচ্ছে..."
                            },
                            color = if (detectedEmail.isNotBlank() || isHistoryView || isSheetView) textColor else subTextColor,
                            fontSize = 11.sp,
                            fontWeight = if (detectedEmail.isNotBlank() || isHistoryView || isSheetView) FontWeight.Bold else FontWeight.Normal,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Right: 1-Click Action Buttons
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        // Sheet Button (Normal Click: Save, Long Click: View)
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(if (isSheetView) Color(0xFF0F9D58) else Color.Transparent)
                                .combinedClickable(
                                    onClick = { handleSaveToSheet() },
                                    onLongClick = {
                                        isSheetView = !isSheetView
                                        if (isSheetView) {
                                            isHistoryView = false
                                            sheetAccountsList = TempMailManager.getInstance(context).getSheetAccounts()
                                        }
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = "শীট",
                                tint = if (isSheetView) Color.White else Color(0xFF0F9D58),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        // "লিখুন" Button
                        Button(
                            onClick = { handleWriteEmail() },
                            colors = ButtonDefaults.buttonColors(containerColor = writeButtonBg),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "লিখুন",
                                tint = Color.White,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "লিখুন",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        // "কপি" Button
                        Button(
                            onClick = { handleCopyEmail() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333745)),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "কপি",
                                tint = Color.White,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "কপি",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }

                        // "নতুন" (New / Change / Delete) Button
                        IconButton(
                            onClick = { handleChangeEmail() },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Autorenew,
                                contentDescription = "নতুন ইমেল",
                                tint = accentGreen,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        // History Toggle Button
                        IconButton(
                            onClick = {
                                isHistoryView = !isHistoryView
                                if (isHistoryView) {
                                    isSheetView = false
                                    savedAccountsList = TempMailManager.getInstance(context).getSavedAccounts()
                                }
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = if (isHistoryView) Icons.Default.Language else Icons.Default.History,
                                contentDescription = if (isHistoryView) "ওয়েব" else "হিস্ট্রি",
                                tint = if (isHistoryView) Color(0xFF1A73E8) else subTextColor,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        // Close Panel Button
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "বন্ধ করুন",
                                tint = subTextColor,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // Sub-Bar: Website Provider Selector Chips & Clear Data Button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .background(Color(0xFF11131A))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "সাইট:",
                        color = subTextColor,
                        fontSize = 10.sp,
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
                                        isSheetView = false
                                        isHistoryView = false
                                        TempMailWebBridge.switchSite(context, site)
                                        Toast.makeText(context, "${site.name} ওপেন হচ্ছে...", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                .padding(horizontal = 9.dp, vertical = 3.dp),
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

                    // "ডাটা ক্লিয়ার" Button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF3E2723))
                            .border(width = 0.5.dp, color = Color(0xFFFF7043), shape = RoundedCornerShape(12.dp))
                            .clickable { handleClearData() }
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CleaningServices,
                                contentDescription = "ডাটা ক্লিয়ার",
                                tint = Color(0xFFFF8A65),
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "ডাটা ক্লিয়ার",
                                fontSize = 10.sp,
                                color = Color(0xFFFFCCBC),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // Reload Button
                    IconButton(
                        onClick = { webViewInstance?.reload() },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "রিলোড",
                            tint = subTextColor,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }

        // Live OTP Detection Banner (Appears automatically when an OTP arrives or message is opened in ANY website)
        AnimatedVisibility(
            visible = detectedOtp.isNotBlank(),
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 3.dp)
                    .clip(RoundedCornerShape(8.dp)),
                color = otpBannerBg,
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
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
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "ওটিপি কোড: ${detectedOtp}",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Button(
                            onClick = { handleInsertOtp(detectedOtp) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C853)),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(26.dp)
                        ) {
                            Text("কোড বসান", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }

                        Button(
                            onClick = { handleCopyOtp(detectedOtp) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(26.dp)
                        ) {
                            Text("কপি", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color.White)
                        }

                        IconButton(
                            onClick = { detectedOtp = "" },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss",
                                tint = Color(0xFFA5D6A7),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }

        if (isSheetView) {
            // Render Sheet (Pinned Emails)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(bgColor)
            ) {
                // Sheet Sub-Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "সেভ করা ইমেল তালিকা (${sheetAccountsList.size})",
                        color = textColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(
                            onClick = { handleCopyAllSheet() },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier = Modifier.height(24.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, null, tint = Color(0xFF64B5F6), modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("সব কপি", fontSize = 10.sp, color = Color(0xFF64B5F6))
                        }
                        TextButton(
                            onClick = {
                                TempMailManager.getInstance(context).clearSheet()
                                sheetAccountsList = emptyList()
                                Toast.makeText(context, "সব শীট ডাটা মোছা হয়েছে", Toast.LENGTH_SHORT).show()
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier = Modifier.height(24.dp)
                        ) {
                            Icon(Icons.Default.DeleteSweep, null, tint = Color(0xFFEA4335), modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("সব মুছুন", fontSize = 10.sp, color = Color(0xFFEA4335))
                        }
                    }
                }

                if (sheetAccountsList.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("শীটে কোনো ইমেল সেভ করা নেই।\nবড় মেইল বাটনে ক্লিক করে সেভ করুন।", color = subTextColor, fontSize = 11.sp, textAlign = TextAlign.Center)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(sheetAccountsList) { acc ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = cardBg),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(acc.email, color = textColor, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                        Text(acc.createdAt, color = subTextColor, fontSize = 9.sp)
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        IconButton(onClick = {
                                            onInsertText(acc.email)
                                            Toast.makeText(context, "বসানো হয়েছে", Toast.LENGTH_SHORT).show()
                                        }, modifier = Modifier.size(30.dp)) {
                                            Icon(Icons.Default.Edit, null, tint = accentGreen, modifier = Modifier.size(16.dp))
                                        }
                                        IconButton(onClick = {
                                            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            cb.setPrimaryClip(ClipData.newPlainText("Email", acc.email))
                                            Toast.makeText(context, "কপি হয়েছে", Toast.LENGTH_SHORT).show()
                                        }, modifier = Modifier.size(30.dp)) {
                                            Icon(Icons.Default.ContentCopy, null, tint = Color(0xFF64B5F6), modifier = Modifier.size(16.dp))
                                        }
                                        IconButton(onClick = {
                                            TempMailManager.getInstance(context).deleteSheetAccount(acc.email)
                                            sheetAccountsList = TempMailManager.getInstance(context).getSheetAccounts()
                                        }, modifier = Modifier.size(30.dp)) {
                                            Icon(Icons.Default.Delete, null, tint = Color(0xFFEA4335), modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else if (isHistoryView) {
            // Render Saved History & OTP list
            val currentList = savedAccountsList
            if (currentList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(bgColor)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "কোনো সেভ করা ইমেল বা কোড হিস্ট্রি নেই।\nকিবোর্ড টুলবারের 'মেইল' বাটনে চাপলে অটোমেটিক সেভ হবে।",
                        color = subTextColor,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(bgColor)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(currentList) { acc ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = cardBg),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = acc.email,
                                        color = textColor,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    IconButton(
                                        onClick = {
                                            TempMailManager.getInstance(context).deleteSavedAccount(acc.email)
                                            savedAccountsList = TempMailManager.getInstance(context).getSavedAccounts()
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "মুছুন",
                                            tint = Color(0xFFEA4335),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                Text(
                                    text = acc.createdAt,
                                    color = subTextColor,
                                    fontSize = 10.sp
                                )

                                if (acc.lastReceivedOtp.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color(0xFF0F9D58).copy(alpha = 0.2f))
                                            .padding(horizontal = 6.dp, vertical = 3.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Key,
                                            contentDescription = "OTP",
                                            tint = Color(0xFF69F0AE),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "কোড: ${acc.lastReceivedOtp}",
                                            color = Color(0xFF69F0AE),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.weight(1f))
                                        Button(
                                            onClick = {
                                                onInsertText(acc.lastReceivedOtp)
                                                val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                cb.setPrimaryClip(ClipData.newPlainText("OTP Code", acc.lastReceivedOtp))
                                                Toast.makeText(context, "কোড ইনসার্ট ও কপি হয়েছে: ${acc.lastReceivedOtp}", Toast.LENGTH_SHORT).show()
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F9D58)),
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.height(24.dp)
                                        ) {
                                            Text("কোড বসান", fontSize = 10.sp, color = Color.White)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            onInsertText(acc.email)
                                            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            cb.setPrimaryClip(ClipData.newPlainText("Temp Mail", acc.email))
                                            Toast.makeText(context, "ইমেল ইনসার্ট ও কপি হয়েছে: ${acc.email}", Toast.LENGTH_SHORT).show()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = writeButtonBg),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.height(26.dp)
                                    ) {
                                        Text("মেইল বসান", fontSize = 11.sp, color = Color.White)
                                    }
                                    Button(
                                        onClick = {
                                            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            cb.setPrimaryClip(ClipData.newPlainText("Temp Mail", acc.email))
                                            Toast.makeText(context, "ইমেল কপি হয়েছে", Toast.LENGTH_SHORT).show()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333745)),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.height(26.dp)
                                    ) {
                                        Text("কপি", fontSize = 11.sp, color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // 2. Embedded Scaled Compact WebView with multi-provider support
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(bgColor)
            ) {
                AndroidView(
                    factory = { ctx ->
                        val persistent = TempMailWebBridge.getOrCreatePersistentWebView(ctx)
                        (persistent.parent as? android.view.ViewGroup)?.removeView(persistent)
                        webViewInstance = persistent
                        TempMailWebBridge.activeWebView = persistent
                        persistent
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
