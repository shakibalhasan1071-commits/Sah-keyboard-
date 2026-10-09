package com.example.moekeyboard.floating

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import com.example.moekeyboard.ui.MiniBrowserActivity
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Vivo OriginOS / FuntouchOS style Floating Small Window (Overlay)
 *
 * Fully Optimized for Telegram Web & Smooth Typing:
 * - Proper WindowManager Layout flags (`FLAG_NOT_FOCUSABLE` combined with `FLAG_NOT_TOUCH_MODAL` during background or `FLAG_ALT_FOCUSABLE_IM`)
 *   so that clicking the floating WebView allows direct typing without dismissing the system keyboard or stealing full focus.
 * - Compact default size (smaller and more refined by default so it doesn't cover the whole screen).
 * - Hardware acceleration, cache optimization, and DOM storage enabled for smooth scrolling & no hanging/freezing in Telegram Web A/K.
 * - Top bar Quick Controls:
 *   * Drag handle pill
 *   * Web A / Web K version switcher badge
 *   * Keyboard summon button
 *   * Reload button
 *   * Minimize and Close (X) buttons
 * - Smooth 4-edge & 4-corner resizing.
 */
class FloatingBrowserService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var webView: WebView? = null
    private var params: WindowManager.LayoutParams? = null

    private var isMinimized = false
    private var savedWidth = 0
    private var savedHeight = 0
    private var savedX = 0
    private var savedY = 0

    private var currentLoadedUrl: String = TELEGRAM_A_URL
    private var isUsingVersionK = false
    private var versionBadgeText: TextView? = null
    private var isWindowFocusedForInput = true
    private var focusBadgeText: TextView? = null
    private var addressBarInput: EditText? = null

    private var isMaximized = false
    private var preMaximizeWidth = 0
    private var preMaximizeHeight = 0
    private var preMaximizeX = 0
    private var preMaximizeY = 0
    private var maxBtnView: ImageView? = null

    companion object {
        const val ACTION_START = "ACTION_START_FLOATING_BROWSER"
        const val ACTION_STOP = "ACTION_STOP_FLOATING_BROWSER"
        const val ACTION_TOGGLE = "ACTION_TOGGLE_FLOATING_BROWSER"
        const val ACTION_OPEN_OR_SHOW = "ACTION_OPEN_OR_SHOW"
        const val EXTRA_URL = "EXTRA_URL"

        const val DEFAULT_TELEGRAM_URL = "https://web.telegram.org/a/"
        const val TELEGRAM_K_URL = "https://web.telegram.org/k/"
        const val TELEGRAM_A_URL = "https://web.telegram.org/a/"

        var isRunning = false
            private set

        fun startOrToggle(context: Context, url: String? = null) {
            if (!Settings.canDrawOverlays(context)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    android.net.Uri.parse("package:${context.packageName}")
                ).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                Toast.makeText(context, "Sah Keyboard-এর ফ্লোটিং উইন্ডোর জন্য Overlay অনুমতি দিন", Toast.LENGTH_LONG).show()
                return
            }

            val serviceIntent = Intent(context, FloatingBrowserService::class.java).apply {
                action = ACTION_OPEN_OR_SHOW
                if (url != null) {
                    putExtra(EXTRA_URL, url)
                }
            }
            context.startService(serviceIntent)
        }

        fun stop(context: Context) {
            val serviceIntent = Intent(context, FloatingBrowserService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(serviceIntent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_OPEN_OR_SHOW
        when (action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_TOGGLE -> {
                if (floatingView != null) {
                    stopSelf()
                    return START_NOT_STICKY
                }
            }
            ACTION_OPEN_OR_SHOW, ACTION_START -> {
                if (floatingView != null) {
                    if (isMinimized) {
                        toggleMinimize()
                    } else {
                        webView?.onResume()
                        webView?.resumeTimers()
                    }
                    val newUrl = intent?.getStringExtra(EXTRA_URL)
                    if (!newUrl.isNullOrBlank() && newUrl != currentLoadedUrl) {
                        currentLoadedUrl = newUrl
                        webView?.loadUrl(newUrl)
                    }
                    return START_STICKY
                }
            }
        }

        val urlToLoad = intent?.getStringExtra(EXTRA_URL) ?: DEFAULT_TELEGRAM_URL
        currentLoadedUrl = urlToLoad
        if (floatingView == null) {
            initFloatingWindow(urlToLoad)
        } else if (urlToLoad.isNotBlank()) {
            webView?.loadUrl(urlToLoad)
        }

        return START_STICKY
    }

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    private fun initFloatingWindow(initialUrl: String) {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        // Generous, comfortable window size so chats and messages are fully visible
        val initialWidth = (screenWidth * 0.90f).toInt().coerceIn(dp(320), dp(500))
        val initialHeight = (screenHeight * 0.62f).toInt().coerceIn(dp(400), dp(720))

        savedWidth = initialWidth
        savedHeight = initialHeight

        // WindowManager flags optimized for floating chat window:
        // By default focusable for seamless typing, FLAG_NOT_TOUCH_MODAL allows outside touches to pass through
        params = WindowManager.LayoutParams(
            initialWidth,
            initialHeight,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (screenWidth - initialWidth) / 2
            y = dp(45)
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }

        val rootLayout = createVivoFloatingWindow(initialUrl)
        floatingView = rootLayout

        try {
            windowManager?.addView(floatingView, params)
            isRunning = true
        } catch (e: Exception) {
            stopSelf()
        }
    }

    /**
     * Yields touch and input focus back to the background app (e.g. Chrome, WhatsApp, Notes)
     */
    private fun releaseFocusToBackground() {
        if (!isWindowFocusedForInput && params?.let { (it.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) != 0 } == true) {
            return
        }
        isWindowFocusedForInput = false
        updateFocusBadge()
        params?.let { p ->
            p.flags = p.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            try {
                windowManager?.updateViewLayout(floatingView, p)
            } catch (_: Exception) {}
        }
    }

    /**
     * Makes the floating window focusable so keyboard input works inside the WebView
     */
    private fun acquireFocusForFloatingWindow() {
        isWindowFocusedForInput = true
        updateFocusBadge()
        params?.let { p ->
            p.flags = p.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            try {
                windowManager?.updateViewLayout(floatingView, p)
            } catch (_: Exception) {}
        }
    }

    private fun updateFocusBadge() {
        focusBadgeText?.let { badge ->
            if (isWindowFocusedForInput) {
                badge.text = "⌨️ উইন্ডো"
                badge.setTextColor(0xFF4CAF50.toInt())
                val bg = GradientDrawable().apply {
                    setColor(0x224CAF50.toInt())
                    cornerRadius = dp(5).toFloat()
                    setStroke(dp(1f), 0x554CAF50.toInt())
                }
                badge.background = bg
            } else {
                badge.text = "📱 ব্যাকগ্রাউন্ড"
                badge.setTextColor(0xFF388AF6.toInt())
                val bg = GradientDrawable().apply {
                    setColor(0x22388AF6.toInt())
                    cornerRadius = dp(5).toFloat()
                    setStroke(dp(1f), 0x55388AF6.toInt())
                }
                badge.background = bg
            }
        }
    }

    private fun toggleFocusMode() {
        if (isWindowFocusedForInput) {
            releaseFocusToBackground()
            Toast.makeText(this, "ব্যাকগ্রাউন্ড মোড: এখন যেকোনো অ্যাপে টাইপ করতে পারবেন", Toast.LENGTH_SHORT).show()
        } else {
            showKeyboardForWebView()
            Toast.makeText(this, "উইন্ডো মোড: ফ্লোটিং উইন্ডোতে টাইপ করুন", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Shows the soft input keyboard explicitly for this window's WebView
     */
    private fun showKeyboardForWebView() {
        acquireFocusForFloatingWindow()
        webView?.let { wv ->
            wv.isFocusable = true
            wv.isFocusableInTouchMode = true
            wv.requestFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(wv, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    /**
     * Root layout for floating window
     */
    private inner class FloatingRootLayout(context: Context) : FrameLayout(context) {
        override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
            // Note: Do NOT revoke focus on ACTION_OUTSIDE.
            // Touching the soft keyboard keys fires ACTION_OUTSIDE because the IME is outside this window.
            // Revoking focus drops the keyboard connection and breaks message typing and sending.
            return super.dispatchTouchEvent(ev)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            return super.onTouchEvent(event)
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    private fun createVivoFloatingWindow(initialUrl: String): View {
        val root = FloatingRootLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            isFocusable = true
            isFocusableInTouchMode = true
        }

        // Padding around root so border touch targets have generous touch areas
        val touchMargin = dp(6)

        // Main Card Container with Vivo smooth rounded corners & subtle modern outline
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val shape = GradientDrawable().apply {
                setColor(0xFF161920.toInt())
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1.5f), 0x4DFFFFFF.toInt())
            }
            background = shape
            clipToOutline = true
            elevation = dp(10).toFloat()
            val lp = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ).apply {
                setMargins(touchMargin, touchMargin, touchMargin, touchMargin)
            }
            layoutParams = lp
        }

        // --- 1. Vivo Top Bar: Minimal Handle & Controls ---
        val header = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(34)
            )
            setBackgroundColor(0xFF1E222D.toInt())
        }

        // Left control container: Minimize & Telegram Version Switcher
        val leftControls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val lp = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ).apply {
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                marginStart = dp(4)
            }
            layoutParams = lp
        }

        // Left control: Minimize / Mini bubble (-)
        val minBtn = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_crop)
            setColorFilter(0xFFB0B7C3.toInt())
            setPadding(dp(7), dp(7), dp(7), dp(7))
            layoutParams = LinearLayout.LayoutParams(dp(28), dp(28))
            setOnClickListener {
                toggleMinimize()
            }
        }
        leftControls.addView(minBtn)

        // Left control: Maximize / Fullscreen toggle button ([ ])
        val maxBtn = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_always_landscape_portrait)
            setColorFilter(0xFFB0B7C3.toInt())
            setPadding(dp(6), dp(6), dp(6), dp(6))
            layoutParams = LinearLayout.LayoutParams(dp(28), dp(28))
            setOnClickListener {
                toggleMaximize()
            }
        }
        maxBtnView = maxBtn
        leftControls.addView(maxBtn)

        // Telegram Web Version switch badge (Click to toggle Web A vs Web K)
        val versionBadge = TextView(this).apply {
            text = "Web K"
            textSize = 9.5f
            setTextColor(0xFF388AF6.toInt())
            val badgeBg = GradientDrawable().apply {
                setColor(0x22388AF6.toInt())
                cornerRadius = dp(5).toFloat()
                setStroke(dp(1f), 0x55388AF6.toInt())
            }
            background = badgeBg
            setPadding(dp(5), dp(2), dp(5), dp(2))
            val badgeLp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = dp(4)
            }
            layoutParams = badgeLp
            setOnClickListener {
                switchTelegramVersion()
            }
        }
        versionBadgeText = versionBadge
        leftControls.addView(versionBadge)

        // Focus Mode Badge: toggles between typing in floating window vs typing in background apps
        val focusBadge = TextView(this).apply {
            text = "📱 ব্যাকগ্রাউন্ড"
            textSize = 9.5f
            setTextColor(0xFF388AF6.toInt())
            val badgeBg = GradientDrawable().apply {
                setColor(0x22388AF6.toInt())
                cornerRadius = dp(5).toFloat()
                setStroke(dp(1f), 0x55388AF6.toInt())
            }
            background = badgeBg
            setPadding(dp(5), dp(2), dp(5), dp(2))
            val badgeLp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = dp(4)
            }
            layoutParams = badgeLp
            setOnClickListener {
                toggleFocusMode()
            }
        }
        focusBadgeText = focusBadge
        leftControls.addView(focusBadge)

        header.addView(leftControls)

        // Center pill / horizontal drag bar (Classic Vivo small window handle)
        val centerPill = View(this).apply {
            val pillDrawable = GradientDrawable().apply {
                setColor(0xFF9EA6B8.toInt())
                cornerRadius = dp(3).toFloat()
            }
            background = pillDrawable
            val lp = FrameLayout.LayoutParams(dp(40), dp(3.5f).toInt()).apply {
                gravity = Gravity.CENTER
            }
            layoutParams = lp
        }
        header.addView(centerPill)

        // Right controls: Keyboard button, Reload & Close (x)
        val rightControls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val lp = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ).apply {
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                marginEnd = dp(4)
            }
            layoutParams = lp
        }

        // Keyboard Button (Allows user to pop open keyboard instantly)
        val keyboardBtn = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_edit)
            setColorFilter(0xFF388AF6.toInt())
            setPadding(dp(5), dp(5), dp(5), dp(5))
            layoutParams = LinearLayout.LayoutParams(dp(28), dp(28))
            setOnClickListener {
                showKeyboardForWebView()
            }
        }

        // Clear Data / Delete (Trash) button
        val clearDataBtn = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_delete)
            setColorFilter(0xFFEF4444.toInt()) // Red delete
            setPadding(dp(5), dp(5), dp(5), dp(5))
            layoutParams = LinearLayout.LayoutParams(dp(28), dp(28))
            setOnClickListener {
                try {
                    android.webkit.CookieManager.getInstance().removeAllCookies(null)
                    android.webkit.CookieManager.getInstance().flush()
                    webView?.clearCache(true)
                    webView?.clearHistory()
                    webView?.clearFormData()
                    android.webkit.WebStorage.getInstance().deleteAllData()
                    
                    Toast.makeText(this@FloatingBrowserService, "সব ডাটা, আইডি ও একাউন্ট মুছে ফেলা হয়েছে!", Toast.LENGTH_SHORT).show()
                    val urlToLoad = currentLoadedUrl.ifBlank { initialUrl }
                    webView?.loadUrl(urlToLoad)
                } catch (e: Exception) {
                    Toast.makeText(this@FloatingBrowserService, "ডাটা মুছতে সমস্যা হয়েছে", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Telegram App Launch button
        val tgAppBtn = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_send)
            setColorFilter(0xFF229ED9.toInt()) // Telegram blue
            setPadding(dp(5), dp(5), dp(5), dp(5))
            layoutParams = LinearLayout.LayoutParams(dp(28), dp(28))
            setOnClickListener {
                try {
                    val intent = packageManager.getLaunchIntentForPackage("org.telegram.messenger") 
                        ?: packageManager.getLaunchIntentForPackage("org.telegram.plus")
                        ?: Intent(Intent.ACTION_VIEW, android.net.Uri.parse("tg://resolve"))
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this@FloatingBrowserService, "টেলিগ্রাম অ্যাপ ইন্সটল করা নেই", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Reload / Sync button
        val reloadBtn = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_popup_sync)
            setColorFilter(0xFFB0B7C3.toInt())
            setPadding(dp(5), dp(5), dp(5), dp(5))
            layoutParams = LinearLayout.LayoutParams(dp(28), dp(28))
            setOnClickListener {
                webView?.let { wv ->
                    wv.clearCache(true)
                    val targetUrl = wv.url?.takeIf { !it.isBlank() && !it.startsWith("data:") && !it.contains("error") } 
                        ?: currentLoadedUrl.ifBlank { initialUrl }
                    wv.loadUrl(targetUrl)
                    Toast.makeText(this@FloatingBrowserService, "রিলোড হচ্ছে...", Toast.LENGTH_SHORT).show()
                }
            }
            setOnLongClickListener {
                try {
                    android.webkit.CookieManager.getInstance().removeAllCookies(null)
                    android.webkit.CookieManager.getInstance().flush()
                    webView?.clearCache(true)
                    webView?.clearHistory()
                    webView?.clearFormData()
                    android.webkit.WebStorage.getInstance().deleteAllData()
                    Toast.makeText(this@FloatingBrowserService, "ডাটা ও আইডি মুছে রিলোড করা হচ্ছে...", Toast.LENGTH_SHORT).show()
                    val targetUrl = currentLoadedUrl.ifBlank { initialUrl }
                    webView?.loadUrl(targetUrl)
                } catch (_: Exception) {}
                true
            }
        }

        // Close (x) button
        val closeBtn = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setColorFilter(0xFFB0B7C3.toInt())
            setPadding(dp(5), dp(5), dp(5), dp(5))
            layoutParams = LinearLayout.LayoutParams(dp(28), dp(28))
            setOnClickListener {
                stopSelf()
            }
        }

        // Quick Send Button (Directly triggers send in Telegram / Web Chat)
        val sendBtn = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_send)
            setColorFilter(0xFF00E676.toInt()) // Vibrant Green
            setPadding(dp(5), dp(5), dp(5), dp(5))
            layoutParams = LinearLayout.LayoutParams(dp(28), dp(28))
            setOnClickListener {
                triggerSendMessageInWeb()
            }
        }

        // Scroll to latest messages button (Down arrow)
        val scrollDownBtn = ImageView(this).apply {
            setImageResource(android.R.drawable.arrow_down_float)
            setColorFilter(0xFF388AF6.toInt()) // Telegram blue
            setPadding(dp(6), dp(6), dp(6), dp(6))
            layoutParams = LinearLayout.LayoutParams(dp(28), dp(28))
            setOnClickListener {
                scrollToLatestMessages()
                Toast.makeText(this@FloatingBrowserService, "সর্বশেষ মেসেজে স্ক্রল করা হচ্ছে...", Toast.LENGTH_SHORT).show()
            }
        }

        rightControls.addView(sendBtn)
        rightControls.addView(scrollDownBtn)
        rightControls.addView(keyboardBtn)
        rightControls.addView(clearDataBtn)
        rightControls.addView(reloadBtn)
        rightControls.addView(closeBtn)
        header.addView(rightControls)

        // Dragging handler on header:
        var dragStartX = 0
        var dragStartY = 0
        var touchStartX = 0f
        var touchStartY = 0f

        header.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // Release keyboard focus to background app immediately when dragging/touching header
                    releaseFocusToBackground()
                    dragStartX = params?.x ?: 0
                    dragStartY = params?.y ?: 0
                    touchStartX = event.rawX
                    touchStartY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params?.x = dragStartX + (event.rawX - touchStartX).toInt()
                    params?.y = dragStartY + (event.rawY - touchStartY).toInt()
                    try {
                        windowManager?.updateViewLayout(floatingView, params)
                    } catch (_: Exception) {}
                    true
                }
                else -> false
            }
        }

        card.addView(header)

        // --- 1.5 Normal Browser Address Bar & Navigation Strip ---
        val addressBarStrip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(0xFF222634.toInt())
            setPadding(dp(4), dp(3), dp(4), dp(3))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(36)
            )
        }

        // Back button (◀)
        val navBackBtn = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_media_previous)
            setColorFilter(0xFFB0B7C3.toInt())
            setPadding(dp(4), dp(4), dp(4), dp(4))
            layoutParams = LinearLayout.LayoutParams(dp(26), dp(26))
            setOnClickListener {
                if (webView?.canGoBack() == true) webView?.goBack()
            }
        }
        addressBarStrip.addView(navBackBtn)

        // Forward button (▶)
        val navForwardBtn = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_media_next)
            setColorFilter(0xFFB0B7C3.toInt())
            setPadding(dp(4), dp(4), dp(4), dp(4))
            layoutParams = LinearLayout.LayoutParams(dp(26), dp(26))
            setOnClickListener {
                if (webView?.canGoForward() == true) webView?.goForward()
            }
        }
        addressBarStrip.addView(navForwardBtn)

        // Address Bar EditText
        val urlEditText = EditText(this).apply {
            setText(initialUrl)
            textSize = 10.5f
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFF8E95A5.toInt())
            hint = "ওয়েবসাইট লিখুন বা সার্চ করুন..."
            isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_GO
            val bg = GradientDrawable().apply {
                setColor(0xFF141720.toInt())
                cornerRadius = dp(14).toFloat()
                setStroke(dp(0.8f), 0x33FFFFFF.toInt())
            }
            background = bg
            setPadding(dp(8), dp(2), dp(8), dp(2))
            layoutParams = LinearLayout.LayoutParams(0, dp(28), 1f).apply {
                setMargins(dp(3), 0, dp(3), 0)
            }
            setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    acquireFocusForFloatingWindow()
                    selectAll()
                }
            }
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE) {
                    navigateToUrl(text.toString())
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                    imm?.hideSoftInputFromWindow(windowToken, 0)
                    true
                } else false
            }
        }
        addressBarInput = urlEditText
        addressBarStrip.addView(urlEditText)

        // Open in Chrome / Phone Browser Button (🌐)
        val openInChromeBtn = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_compass)
            setColorFilter(0xFF388AF6.toInt())
            setPadding(dp(4), dp(4), dp(4), dp(4))
            layoutParams = LinearLayout.LayoutParams(dp(26), dp(26))
            setOnClickListener {
                try {
                    val targetUrl = currentLoadedUrl.ifBlank { webView?.url ?: DEFAULT_TELEGRAM_URL }
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(intent)
                    Toast.makeText(this@FloatingBrowserService, "Chrome / ব্রাউজারে ওপেন করা হচ্ছে...", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this@FloatingBrowserService, "ব্রাউজার ওপেন করা যায়নি", Toast.LENGTH_SHORT).show()
                }
            }
        }
        addressBarStrip.addView(openInChromeBtn)

        // Open in Full App Browser Activity Button (📑)
        val openFullAppBtn = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_agenda)
            setColorFilter(0xFF00E676.toInt())
            setPadding(dp(4), dp(4), dp(4), dp(4))
            layoutParams = LinearLayout.LayoutParams(dp(26), dp(26))
            setOnClickListener {
                try {
                    val intent = Intent(this@FloatingBrowserService, MiniBrowserActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(intent)
                    Toast.makeText(this@FloatingBrowserService, "ফুল ব্রাউজার অ্যাক্টিভিটি ওপেন হচ্ছে...", Toast.LENGTH_SHORT).show()
                } catch (_: Exception) {}
            }
        }
        addressBarStrip.addView(openFullAppBtn)

        card.addView(addressBarStrip)

        // --- 2. Progress Bar ---
        val progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(2)
            )
            visibility = View.GONE
        }
        card.addView(progressBar)

        // --- 3. WebView Content Container ---
        val webContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        webView = object : WebView(this) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP) {
                    triggerSendMessageInWeb()
                }
                return super.dispatchKeyEvent(event)
            }
        }.apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )

            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            isVerticalScrollBarEnabled = true
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS

            isFocusable = true
            isFocusableInTouchMode = true

            // On touch down inside WebView, make window focusable so inputs work seamlessly without closing keyboard
            setOnTouchListener { v, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    acquireFocusForFloatingWindow()
                    if (!v.hasFocus()) {
                        v.requestFocus()
                    }
                }
                false
            }

            // WebSettings configuration optimized for Telegram Web & smooth non-hanging performance
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                cacheMode = WebSettings.LOAD_DEFAULT
                useWideViewPort = true
                loadWithOverviewMode = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                allowFileAccess = true
                allowContentAccess = true
                javaScriptCanOpenWindowsAutomatically = true
                mediaPlaybackRequiresUserGesture = false
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                setSupportMultipleWindows(false)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                try {
                    android.webkit.ServiceWorkerController.getInstance().serviceWorkerWebSettings.apply {
                        allowContentAccess = true
                        allowFileAccess = true
                        cacheMode = WebSettings.LOAD_DEFAULT
                    }
                } catch (_: Exception) {}
            }

            val currentWv = this
            CookieManager.getInstance().apply {
                setAcceptCookie(true)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    setAcceptThirdPartyCookies(currentWv, true)
                }
                flush()
            }

            // Immediately start active JavaScript runtime & timers so WebSockets, Telegram Web and timers run at full speed without throttling or freezing
            onResume()
            resumeTimers()

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    if (newProgress < 100) {
                        progressBar.visibility = View.VISIBLE
                        progressBar.progress = newProgress
                    } else {
                        progressBar.visibility = View.GONE
                    }
                }

                override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                    android.util.Log.d("FloatingBrowser", "JS Console: ${consoleMessage?.message()} (Line: ${consoleMessage?.lineNumber()})")
                    return super.onConsoleMessage(consoleMessage)
                }
            }

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val url = request?.url?.toString() ?: ""
                    if (url.startsWith("http://") || url.startsWith("https://")) {
                        // Allow SPA (like Telegram Web) internal routing without reloading page and breaking WebSockets!
                        return false
                    }
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        startActivity(intent)
                        return true
                    } catch (_: Exception) {
                        return false
                    }
                }

                override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                    handler?.proceed()
                }

                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                    if (request?.isForMainFrame == true) {
                        val failingUrl = request.url?.toString() ?: ""
                        if (failingUrl.contains("web.telegram.org/a") && !failingUrl.contains("/k/")) {
                            isUsingVersionK = true
                            versionBadgeText?.text = "Web K"
                            currentLoadedUrl = TELEGRAM_K_URL
                            view?.loadUrl(TELEGRAM_K_URL)
                        }
                    }
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    progressBar.visibility = View.GONE
                    url?.let {
                        currentLoadedUrl = it
                        addressBarInput?.setText(it)
                        if (it.contains("web.telegram.org/k")) {
                            isUsingVersionK = true
                            versionBadgeText?.text = "Web K"
                        } else if (it.contains("web.telegram.org/a")) {
                            isUsingVersionK = false
                            versionBadgeText?.text = "Web A"
                        }
                    }
                }
            }

            loadUrl(initialUrl)
        }

        webContainer.addView(webView)
        card.addView(webContainer)

        root.addView(card)

        // --- 4. Vivo-Style Resizing: 4 Sides & 4 Corners ---
        setupEdgeAndCornerResizers(root)

        return root
    }

    /**
     * Toggles between Telegram Web A and Web K
     */
    private fun switchTelegramVersion() {
        if (isUsingVersionK) {
            isUsingVersionK = false
            versionBadgeText?.text = "Web A"
            currentLoadedUrl = TELEGRAM_A_URL
            webView?.loadUrl(TELEGRAM_A_URL)
            Toast.makeText(this, "Telegram Web A লোড হচ্ছে...", Toast.LENGTH_SHORT).show()
        } else {
            isUsingVersionK = true
            versionBadgeText?.text = "Web K"
            currentLoadedUrl = TELEGRAM_K_URL
            webView?.loadUrl(TELEGRAM_K_URL)
            Toast.makeText(this, "Telegram Web K লোড হচ্ছে...", Toast.LENGTH_SHORT).show()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupEdgeAndCornerResizers(root: FrameLayout) {
        val edgeSize = dp(12)
        val cornerSize = dp(22)

        // Right Edge
        val rightEdge = View(this).apply {
            val lp = FrameLayout.LayoutParams(edgeSize, FrameLayout.LayoutParams.MATCH_PARENT).apply {
                gravity = Gravity.END
                setMargins(0, cornerSize, 0, cornerSize)
            }
            layoutParams = lp
            attachResizeListener(this, resizeRight = true)
        }
        root.addView(rightEdge)

        // Left Edge
        val leftEdge = View(this).apply {
            val lp = FrameLayout.LayoutParams(edgeSize, FrameLayout.LayoutParams.MATCH_PARENT).apply {
                gravity = Gravity.START
                setMargins(0, cornerSize, 0, cornerSize)
            }
            layoutParams = lp
            attachResizeListener(this, resizeLeft = true)
        }
        root.addView(leftEdge)

        // Bottom Edge
        val bottomEdge = View(this).apply {
            val lp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, edgeSize).apply {
                gravity = Gravity.BOTTOM
                setMargins(cornerSize, 0, cornerSize, 0)
            }
            layoutParams = lp
            attachResizeListener(this, resizeBottom = true)
        }
        root.addView(bottomEdge)

        // Top Edge
        val topEdge = View(this).apply {
            val lp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, edgeSize).apply {
                gravity = Gravity.TOP
                setMargins(cornerSize, 0, cornerSize, 0)
            }
            layoutParams = lp
            attachResizeListener(this, resizeTop = true)
        }
        root.addView(topEdge)

        // Bottom-Right Corner
        val bottomRightCorner = FrameLayout(this).apply {
            val lp = FrameLayout.LayoutParams(cornerSize, cornerSize).apply {
                gravity = Gravity.BOTTOM or Gravity.END
            }
            layoutParams = lp

            val gripIndicator = ImageView(this@FloatingBrowserService).apply {
                val iconLp = FrameLayout.LayoutParams(dp(12), dp(12)).apply {
                    gravity = Gravity.BOTTOM or Gravity.END
                    setMargins(0, 0, dp(8), dp(8))
                }
                layoutParams = iconLp
                val gripShape = GradientDrawable().apply {
                    setStroke(dp(2f), 0x88FFFFFF.toInt())
                    cornerRadius = dp(2).toFloat()
                }
                background = gripShape
                alpha = 0.6f
            }
            addView(gripIndicator)
            attachResizeListener(this, resizeRight = true, resizeBottom = true)
        }
        root.addView(bottomRightCorner)

        // Bottom-Left Corner
        val bottomLeftCorner = View(this).apply {
            val lp = FrameLayout.LayoutParams(cornerSize, cornerSize).apply {
                gravity = Gravity.BOTTOM or Gravity.START
            }
            layoutParams = lp
            attachResizeListener(this, resizeLeft = true, resizeBottom = true)
        }
        root.addView(bottomLeftCorner)

        // Top-Right Corner
        val topRightCorner = View(this).apply {
            val lp = FrameLayout.LayoutParams(cornerSize, cornerSize).apply {
                gravity = Gravity.TOP or Gravity.END
            }
            layoutParams = lp
            attachResizeListener(this, resizeRight = true, resizeTop = true)
        }
        root.addView(topRightCorner)

        // Top-Left Corner
        val topLeftCorner = View(this).apply {
            val lp = FrameLayout.LayoutParams(cornerSize, cornerSize).apply {
                gravity = Gravity.TOP or Gravity.START
            }
            layoutParams = lp
            attachResizeListener(this, resizeLeft = true, resizeTop = true)
        }
        root.addView(topLeftCorner)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachResizeListener(
        view: View,
        resizeLeft: Boolean = false,
        resizeRight: Boolean = false,
        resizeTop: Boolean = false,
        resizeBottom: Boolean = false
    ) {
        var startX = 0f
        var startY = 0f
        var startW = 0
        var startH = 0
        var startXPos = 0
        var startYPos = 0

        view.setOnTouchListener { _, event ->
            val p = params ?: return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    startY = event.rawY
                    startW = p.width
                    startH = p.height
                    startXPos = p.x
                    startYPos = p.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = (event.rawX - startX).toInt()
                    val deltaY = (event.rawY - startY).toInt()

                    val displayMetrics = resources.displayMetrics
                    val minW = dp(220)
                    val maxW = (displayMetrics.widthPixels * 0.95f).toInt()
                    val minH = dp(240)
                    val maxH = (displayMetrics.heightPixels * 0.90f).toInt()

                    if (resizeRight) {
                        p.width = (startW + deltaX).coerceIn(minW, maxW)
                    } else if (resizeLeft) {
                        val proposedW = (startW - deltaX).coerceIn(minW, maxW)
                        val actualDelta = startW - proposedW
                        p.width = proposedW
                        p.x = startXPos + actualDelta
                    }

                    if (resizeBottom) {
                        p.height = (startH + deltaY).coerceIn(minH, maxH)
                    } else if (resizeTop) {
                        val proposedH = (startH - deltaY).coerceIn(minH, maxH)
                        val actualDelta = startH - proposedH
                        p.height = proposedH
                        p.y = startYPos + actualDelta
                    }

                    savedWidth = p.width
                    savedHeight = p.height

                    try {
                        windowManager?.updateViewLayout(floatingView, p)
                    } catch (_: Exception) {}
                    true
                }
                else -> false
            }
        }
    }

    private fun toggleMinimize() {
        val p = params ?: return
        if (isMinimized) {
            p.width = savedWidth
            p.height = savedHeight
            isMinimized = false
            webView?.visibility = View.VISIBLE
            webView?.onResume()
            webView?.resumeTimers()
        } else {
            savedWidth = p.width
            savedHeight = p.height
            p.width = dp(170)
            p.height = dp(34)
            isMinimized = true
            releaseFocusToBackground()
            webView?.visibility = View.GONE
        }
        try {
            windowManager?.updateViewLayout(floatingView, p)
        } catch (_: Exception) {}
    }

    private fun toggleMaximize() {
        val wm = windowManager ?: return
        val root = floatingView ?: return
        val p = params ?: return
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        if (!isMaximized) {
            preMaximizeWidth = p.width
            preMaximizeHeight = p.height
            preMaximizeX = p.x
            preMaximizeY = p.y

            val maxWidth = (screenWidth * 0.96f).toInt()
            val maxHeight = (screenHeight * 0.86f).toInt()

            p.width = maxWidth
            p.height = maxHeight
            p.x = (screenWidth - maxWidth) / 2
            p.y = dp(24)

            isMaximized = true
            maxBtnView?.setImageResource(android.R.drawable.ic_menu_revert)
            Toast.makeText(this, "উইন্ডো বড় করা হয়েছে - সব মেসেজ স্পষ্ট দেখতে পাবেন", Toast.LENGTH_SHORT).show()
        } else {
            p.width = if (preMaximizeWidth > 0) preMaximizeWidth else savedWidth
            p.height = if (preMaximizeHeight > 0) preMaximizeHeight else savedHeight
            p.x = preMaximizeX
            p.y = preMaximizeY

            isMaximized = false
            maxBtnView?.setImageResource(android.R.drawable.ic_menu_always_landscape_portrait)
            Toast.makeText(this, "উইন্ডো স্বাভাবিক আকারে ফিরে এসেছে", Toast.LENGTH_SHORT).show()
        }

        try {
            wm.updateViewLayout(root, p)
            scrollToLatestMessages()
        } catch (_: Exception) {}
    }

    fun triggerSendMessageInWeb() {
        acquireFocusForFloatingWindow()
        val js = """
            (function() {
                // 1. Try clicking common web messenger send buttons (Telegram Web K, Web A, etc.)
                var selectors = [
                    'button.btn-send',
                    'button.tgico-send',
                    'button[title*="Send"]',
                    'button[aria-label*="Send"]',
                    'button.send',
                    '.btn-circle.btn-primary',
                    '.chat-input-control.send',
                    '.btn-send-message',
                    'button[data-testid="send-button"]'
                ];
                for (var i = 0; i < selectors.length; i++) {
                    var btn = document.querySelector(selectors[i]);
                    if (btn && btn.offsetParent !== null) {
                        btn.click();
                        return 'clicked_' + selectors[i];
                    }
                }

                // 2. Dispatch simulated Enter events to active editable / input
                var target = document.activeElement;
                if (!target || target.tagName === 'BODY') {
                    target = document.querySelector('div[contenteditable="true"], .input-message-input, #editable-message-text, textarea, input[type="text"]');
                }
                if (target) {
                    target.focus();
                    var ev1 = new KeyboardEvent('keydown', { key: 'Enter', code: 'Enter', keyCode: 13, which: 13, bubbles: true });
                    target.dispatchEvent(ev1);
                    var ev2 = new KeyboardEvent('keypress', { key: 'Enter', code: 'Enter', keyCode: 13, which: 13, bubbles: true });
                    target.dispatchEvent(ev2);
                    var ev3 = new KeyboardEvent('keyup', { key: 'Enter', code: 'Enter', keyCode: 13, which: 13, bubbles: true });
                    target.dispatchEvent(ev3);
                    if (target.form) target.form.submit();
                    return 'dispatched_enter';
                }
                return 'no_target';
            })();
        """.trimIndent()
        webView?.evaluateJavascript(js) { res ->
            android.util.Log.d("FloatingBrowser", "triggerSendMessage result: $res")
        }
    }

    fun scrollToLatestMessages() {
        val js = """
            (function() {
                var containers = document.querySelectorAll('.bubbles, .messages-container, .bubbles-inner, .chat-history, .Transition, .MessageList, .messages-layout, .chat-container');
                for (var i = 0; i < containers.length; i++) {
                    var c = containers[i];
                    c.scrollTop = c.scrollHeight + 10000;
                }
                var downBtn = document.querySelector('.btn-circle.btn-to-bottom, .scroll-to-bottom, .btn-scroll-down, .bottom-button');
                if (downBtn && downBtn.offsetParent !== null) {
                    downBtn.click();
                }
                window.scrollTo(0, document.body.scrollHeight);
            })();
        """.trimIndent()
        webView?.evaluateJavascript(js, null)
    }

    private fun navigateToUrl(raw: String) {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return
        val url = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else if (trimmed.contains(".") && !trimmed.contains(" ")) {
            "https://$trimmed"
        } else {
            "https://www.google.com/search?q=" + Uri.encode(trimmed)
        }
        currentLoadedUrl = url
        addressBarInput?.setText(url)
        webView?.loadUrl(url)
    }

    private fun dp(v: Float): Int {
        return (v * resources.displayMetrics.density).toInt()
    }

    private fun dp(v: Int): Int {
        return (v * resources.displayMetrics.density).toInt()
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        serviceScope.cancel()
        if (floatingView != null) {
            try {
                windowManager?.removeView(floatingView)
            } catch (_: Exception) {}
            floatingView = null
        }
        webView?.destroy()
        webView = null
    }
}
