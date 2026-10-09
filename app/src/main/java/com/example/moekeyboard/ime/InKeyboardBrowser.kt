package com.example.moekeyboard.ime

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun InKeyboardBrowser(
    isDarkTheme: Boolean,
    height: Dp,
    initialUrl: String = "https://www.google.com",
    onUrlChange: (String) -> Unit = {},
    onAction: ((BrowserAction) -> Unit) -> Unit = {},
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val bgColor = if (isDarkTheme) Color(0xFF1E1F22) else Color(0xFFF0F2F5)
    val cardBg = if (isDarkTheme) Color(0xFF2B2D31) else Color(0xFFFFFFFF)
    val textColor = if (isDarkTheme) Color.White else Color.Black
    val accentColor = Color(0xFF1A73E8)

    var url by remember { mutableStateOf(initialUrl) }
    var inputUrl by remember { mutableStateOf(initialUrl) }
    var isLoading by remember { mutableStateOf(false) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var webView: WebView? by remember { mutableStateOf(null) }
    var isAddressBarFocused by remember { mutableStateOf(false) }

    // Method to inject text into the webpage or address bar
    fun injectText(text: String) {
        if (isAddressBarFocused) {
            inputUrl += text
            onUrlChange(inputUrl)
        } else {
            // Inject into the active element of the webpage
            val escapedText = text.replace("'", "\\'")
            val script = "(function() { " +
                    "   var el = document.activeElement; " +
                    "   if (!el || (el.tagName !== 'INPUT' && el.tagName !== 'TEXTAREA' && el.contentEditable !== 'true')) { " +
                    "       el = document.querySelector('input:not([type=\"hidden\"]), textarea, [contenteditable=\"true\"]'); " +
                    "       if (el) el.focus(); " +
                    "   } " +
                    "   if (el) { " +
                    "       if (el.contentEditable === 'true') { " +
                    "           document.execCommand('insertText', false, '$escapedText'); " +
                    "       } else { " +
                    "           var start = el.selectionStart; " +
                    "           var end = el.selectionEnd; " +
                    "           var val = el.value; " +
                    "           el.value = val.substring(0, start) + '$escapedText' + val.substring(end); " +
                    "           el.selectionStart = el.selectionEnd = start + ${text.length}; " +
                    "           el.dispatchEvent(new Event('input', { bubbles: true })); " +
                    "           el.dispatchEvent(new Event('change', { bubbles: true })); " +
                    "       } " +
                    "   } " +
                    "})();"
            webView?.evaluateJavascript(script, null)
        }
    }

    // Handle backspace
    fun injectBackspace() {
        if (isAddressBarFocused) {
            if (inputUrl.isNotEmpty()) {
                inputUrl = inputUrl.dropLast(1)
                onUrlChange(inputUrl)
            }
        } else {
            val script = "if (document.activeElement) { " +
                    "   var el = document.activeElement; " +
                    "   if (el.tagName === 'INPUT' || el.tagName === 'TEXTAREA' || el.contentEditable === 'true') { " +
                    "       if (el.contentEditable === 'true') { " +
                    "           document.execCommand('delete', false, null); " +
                    "       } else { " +
                    "           var start = el.selectionStart; " +
                    "           var end = el.selectionEnd; " +
                    "           if (start === end && start > 0) { " +
                    "               el.value = el.value.substring(0, start - 1) + el.value.substring(end); " +
                    "               el.selectionStart = el.selectionEnd = start - 1; " +
                    "           } else if (start !== end) { " +
                    "               el.value = el.value.substring(0, start) + el.value.substring(end); " +
                    "               el.selectionStart = el.selectionEnd = start; " +
                    "           } " +
                    "           el.dispatchEvent(new Event('input', { bubbles: true })); " +
                    "           el.dispatchEvent(new Event('change', { bubbles: true })); " +
                    "       } " +
                    "   } " +
                    "}"
            webView?.evaluateJavascript(script, null)
        }
    }

    fun navigate() {
        isAddressBarFocused = false
        var formattedUrl = inputUrl.trim()
        if (formattedUrl.isEmpty()) return
        
        if (!formattedUrl.startsWith("http://") && !formattedUrl.startsWith("https://")) {
            if (formattedUrl.contains(".") && !formattedUrl.contains(" ")) {
                formattedUrl = "https://$formattedUrl"
            } else {
                formattedUrl = "https://www.google.com/search?q=$formattedUrl"
            }
        }
        url = formattedUrl
        inputUrl = formattedUrl
        onUrlChange(formattedUrl)
    }

    // Handle enter
    fun injectEnter() {
        if (isAddressBarFocused) {
            navigate()
        } else {
            val script = "if (document.activeElement) { " +
                    "   var el = document.activeElement; " +
                    "   var event = new KeyboardEvent('keydown', { key: 'Enter', code: 'Enter', keyCode: 13, which: 13, bubbles: true }); " +
                    "   el.dispatchEvent(event); " +
                    "   if (el.form) el.form.submit(); " +
                    "}"
            webView?.evaluateJavascript(script, null)
        }
    }

    // Register external action handler
    onAction { action ->
        when (action) {
            is BrowserAction.TypeChar -> injectText(action.char)
            is BrowserAction.Backspace -> injectBackspace()
            is BrowserAction.Enter -> injectEnter()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        // Browser Toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Navigation Controls
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { 
                        webView?.goBack()
                        webView?.url?.let { 
                            inputUrl = it
                            onUrlChange(it)
                        }
                    },
                    enabled = canGoBack,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = if (canGoBack) textColor else textColor.copy(alpha = 0.3f),
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = { 
                        webView?.goForward()
                        webView?.url?.let { 
                            inputUrl = it
                            onUrlChange(it)
                        }
                    },
                    enabled = canGoForward,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = "Forward",
                        tint = if (canGoForward) textColor else textColor.copy(alpha = 0.3f),
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = { webView?.reload() },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = if (isLoading) Icons.Default.Close else Icons.Default.Refresh,
                        contentDescription = "Reload",
                        tint = textColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Address Bar
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(32.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(cardBg)
                    .clickable { isAddressBarFocused = true }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                BasicTextField(
                    value = inputUrl,
                    onValueChange = { 
                        inputUrl = it
                        onUrlChange(it)
                    },
                    textStyle = TextStyle(color = textColor, fontSize = 12.sp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { navigate() }),
                    modifier = Modifier.fillMaxWidth().onFocusChanged { if(it.isFocused) isAddressBarFocused = true },
                    cursorBrush = SolidColor(accentColor)
                )
                
                if (inputUrl.isEmpty()) {
                    Text("Search or enter URL", fontSize = 11.sp, color = textColor.copy(alpha = 0.4f))
                }
            }
            
            IconButton(
                onClick = { navigate() },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(Icons.Default.Search, contentDescription = "Go", tint = accentColor, modifier = Modifier.size(18.dp))
            }

            IconButton(
                onClick = onClose,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = textColor.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Progress Bar
        if (isLoading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = accentColor,
                trackColor = Color.Transparent
            )
        }

        // WebView Area with Loading Overlay
        Box(modifier = Modifier.fillMaxSize().background(bgColor)) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.setSupportZoom(true)
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                isLoading = true
                                url?.let { 
                                    inputUrl = it
                                    onUrlChange(it)
                                }
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                isLoading = false
                                canGoBack = view?.canGoBack() ?: false
                                canGoForward = view?.canGoForward() ?: false
                            }

                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                return false
                            }
                        }
                        loadUrl(url)
                        webView = this
                    }
                },
                update = { view ->
                    if (view.url != url) {
                        view.loadUrl(url)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        shape = RoundedCornerShape(8.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = accentColor, strokeWidth = 2.dp)
                            Text("লোড হচ্ছে...", fontSize = 12.sp, color = textColor)
                        }
                    }
                }
            }
        }
    }
}
