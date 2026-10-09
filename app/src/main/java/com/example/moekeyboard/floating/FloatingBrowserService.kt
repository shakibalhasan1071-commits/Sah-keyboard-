package com.example.moekeyboard.floating

import android.annotation.SuppressLint
import android.app.Service
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.net.Network
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
import kotlinx.coroutines.launch

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
    private var minimizedBubbleView: View? = null
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
    private var lastActionTimestamp = 0L

    private fun executeSingleAction(action: () -> Unit) {
        val now = System.currentTimeMillis()
        if (now - lastActionTimestamp < 1200L) {
            return
        }
        lastActionTimestamp = now
        action()
    }

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

        var activeInstance: FloatingBrowserService? = null
            private set

        /**
         * Invoked whenever the user starts typing in an external Android app (e.g. WhatsApp, Chrome, Notes)
         */
        fun onExternalAppInputStarted() {
            activeInstance?.releaseFocusToBackground()
        }
    }

    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun onCreate() {
        super.onCreate()
        activeInstance = this
        registerNetworkCallback()
        startKeepAliveJob()
    }

    private fun registerNetworkCallback() {
        try {
            connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    serviceScope.launch(Dispatchers.Main) {
                        webView?.let { wv ->
                            wv.setNetworkAvailable(true)
                            wv.evaluateJavascript("window.dispatchEvent(new Event('online'));", null)
                        }
                    }
                }
                override fun onLost(network: Network) {
                    serviceScope.launch(Dispatchers.Main) {
                        webView?.setNetworkAvailable(false)
                    }
                }
            }
            connectivityManager?.registerDefaultNetworkCallback(networkCallback!!)
        } catch (_: Exception) {}
    }

    private fun unregisterNetworkCallback() {
        try {
            networkCallback?.let { connectivityManager?.unregisterNetworkCallback(it) }
            networkCallback = null
        } catch (_: Exception) {}
    }

    private fun startKeepAliveJob() {
        serviceScope.launch {
            while (true) {
                kotlinx.coroutines.delay(8000)
                try {
                    webView?.let { wv ->
                        wv.resumeTimers()
                        wv.setNetworkAvailable(true)
                        wv.evaluateJavascript("""
                            (function() {
                                if (typeof window.__sahKeepAlive === 'function') {
                                    window.__sahKeepAlive();
                                }
                            })();
                        """.trimIndent(), null)
                    }
                } catch (_: Exception) {}
            }
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

        // 25% smaller default window size as requested by user
        val initialWidth = (screenWidth * 0.68f).toInt().coerceIn(dp(250), dp(400))
        val initialHeight = (screenHeight * 0.46f).toInt().coerceIn(dp(300), dp(520))

        savedWidth = initialWidth
        savedHeight = initialHeight

        // WindowManager flags: FLAG_NOT_FOCUSABLE prevents overlay from stealing focus on startup
        // This stops the keyboard from disappearing and prevents keyboard hanging!
        params = WindowManager.LayoutParams(
            initialWidth,
            initialHeight,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
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
            if (ev.action == MotionEvent.ACTION_OUTSIDE) {
                // User touched outside the floating window (e.g. background app)
                // Yield focus to background so typing and touching in background app works immediately!
                if (isWindowFocusedForInput) {
                    releaseFocusToBackground()
                }
                return false
            }
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

        // Left control container: Delete, Reload, 🔑 2FA, 📋 Paste icon buttons
        val leftControls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val lp = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ).apply {
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                marginStart = dp(5)
            }
            layoutParams = lp
        }

        // Delete button
        val clearDataBtn = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_delete)
            setColorFilter(0xFFEF4444.toInt()) // Red delete
            setPadding(dp(5), dp(5), dp(5), dp(5))
            layoutParams = LinearLayout.LayoutParams(dp(26), dp(26))
            setOnClickListener {
                try {
                    android.webkit.CookieManager.getInstance().removeAllCookies(null)
                    android.webkit.CookieManager.getInstance().flush()
                    webView?.clearCache(true)
                    webView?.clearHistory()
                    webView?.clearFormData()
                    android.webkit.WebStorage.getInstance().deleteAllData()
                    Toast.makeText(this@FloatingBrowserService, "সব ডাটা ও ক্যাশ মুছে ফেলা হয়েছে!", Toast.LENGTH_SHORT).show()
                    val urlToLoad = currentLoadedUrl.ifBlank { initialUrl }
                    webView?.loadUrl(urlToLoad)
                } catch (e: Exception) {
                    Toast.makeText(this@FloatingBrowserService, "ডাটা মুছতে সমস্যা হয়েছে", Toast.LENGTH_SHORT).show()
                }
            }
        }
        leftControls.addView(clearDataBtn)

        // Reload button
        val reloadBtn = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_popup_sync)
            setColorFilter(0xFF388AF6.toInt()) // Blue reload
            setPadding(dp(5), dp(5), dp(5), dp(5))
            layoutParams = LinearLayout.LayoutParams(dp(26), dp(26)).apply {
                marginStart = dp(3)
            }
            setOnClickListener {
                webView?.let { wv ->
                    val currentUrl = wv.url
                    if (!currentUrl.isNullOrBlank() && !currentUrl.startsWith("data:") && !currentUrl.contains("error")) {
                        currentLoadedUrl = currentUrl
                        wv.reload()
                    } else {
                        val targetUrl = currentLoadedUrl.ifBlank { TELEGRAM_A_URL }
                        wv.loadUrl(targetUrl)
                    }
                    Toast.makeText(this@FloatingBrowserService, "রিলোড হচ্ছে...", Toast.LENGTH_SHORT).show()
                }
            }
        }
        leftControls.addView(reloadBtn)

        // Button 1: 🔑 2FA Auto-Enter & Send (Polished Pill Button with Icon & Label)
        val twoFaBtn = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            val shape = GradientDrawable().apply {
                setColor(0x33FFC107.toInt())
                cornerRadius = dp(7).toFloat()
                setStroke(dp(1.2f), 0xFFFFB300.toInt())
            }
            background = shape
            setPadding(dp(7), dp(2), dp(8), dp(2))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(26)
            ).apply {
                marginStart = dp(4)
            }
            isClickable = true
            isFocusable = true

            val icon = TextView(this@FloatingBrowserService).apply {
                text = "🔑"
                textSize = 12f
                setPadding(0, 0, dp(3), 0)
            }
            addView(icon)

            val label = TextView(this@FloatingBrowserService).apply {
                text = "2FA"
                textSize = 10.5f
                setTextColor(0xFFFFD54F.toInt())
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            addView(label)

            setOnClickListener {
                executeSingleAction {
                    val code = "6MTN SUJ3 OB3J 3CFT UNBF TJWP MQRZ XPUS"
                    insertTextAndSendInWeb(code, autoSend = true)
                    Toast.makeText(this@FloatingBrowserService, "2FA কোড বসানো হচ্ছে...", Toast.LENGTH_SHORT).show()
                }
            }
        }
        leftControls.addView(twoFaBtn)

        // Button 2: 📋 Paste & Send (Polished Pill Button with Icon & Label)
        val pasteSendBtn = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            val shape = GradientDrawable().apply {
                setColor(0x3300E676.toInt())
                cornerRadius = dp(7).toFloat()
                setStroke(dp(1.2f), 0xFF00E676.toInt())
            }
            background = shape
            setPadding(dp(7), dp(2), dp(8), dp(2))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(26)
            ).apply {
                marginStart = dp(4)
            }
            isClickable = true
            isFocusable = true

            val icon = TextView(this@FloatingBrowserService).apply {
                text = "📋"
                textSize = 12f
                setPadding(0, 0, dp(3), 0)
            }
            addView(icon)

            val label = TextView(this@FloatingBrowserService).apply {
                text = "Paste"
                textSize = 10.5f
                setTextColor(0xFF69F0AE.toInt())
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            addView(label)

            setOnClickListener {
                executeSingleAction {
                    val clipMgr = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    val clipText = clipMgr?.primaryClip?.getItemAt(0)?.coerceToText(this@FloatingBrowserService)?.toString()?.trim()
                    if (!clipText.isNullOrBlank()) {
                        insertTextAndSendInWeb(clipText, autoSend = true)
                        Toast.makeText(this@FloatingBrowserService, "কপি করা টেক্সট পাঠানো হচ্ছে...", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@FloatingBrowserService, "ক্লিপবোর্ডে কোনো লেখা পাওয়া যায়নি", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        leftControls.addView(pasteSendBtn)

        header.addView(leftControls)

        // Drag Handle Bar in center of header (spacious and clear)
        val dragHandleBar = View(this).apply {
            val pill = GradientDrawable().apply {
                setColor(0x55FFFFFF.toInt())
                cornerRadius = dp(3).toFloat()
            }
            background = pill
            val lp = FrameLayout.LayoutParams(dp(44), dp(4)).apply {
                gravity = Gravity.CENTER
            }
            layoutParams = lp
        }
        header.addView(dragHandleBar)

        // Right control container: Move (✥), Keyboard (⌨️), Minimize (-), Close (✕)
        val rightControls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val lp = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ).apply {
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                marginEnd = dp(6)
            }
            layoutParams = lp
        }

        // Dedicated Move Button (✥): Hold and drag anywhere on screen to move window
        var dragStartX = 0
        var dragStartY = 0
        var touchStartX = 0f
        var touchStartY = 0f

        val moveBtn = TextView(this).apply {
            text = "✥"
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(0xFF00E5FF.toInt())
            val shape = GradientDrawable().apply {
                setColor(0x2200E5FF.toInt())
                cornerRadius = dp(6).toFloat()
                setStroke(dp(1f), 0x8800E5FF.toInt())
            }
            background = shape
            layoutParams = LinearLayout.LayoutParams(dp(26), dp(26)).apply {
                marginEnd = dp(4)
            }
            setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
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
        }
        rightControls.addView(moveBtn)

        // Minimize button (-)
        val minimizeBtn = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_manage)
            setColorFilter(0xFF388AF6.toInt()) // Blue minimize
            setPadding(dp(5), dp(5), dp(5), dp(5))
            layoutParams = LinearLayout.LayoutParams(dp(26), dp(26)).apply {
                marginEnd = dp(4)
            }
            setOnClickListener {
                toggleMinimizeToBubble()
            }
        }
        rightControls.addView(minimizeBtn)

        // Type button (Summon Soft Keyboard)
        val typeBtn = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_edit)
            setColorFilter(0xFF10B981.toInt()) // Green edit icon
            setPadding(dp(5), dp(5), dp(5), dp(5))
            layoutParams = LinearLayout.LayoutParams(dp(26), dp(26)).apply {
                marginEnd = dp(4)
            }
            setOnClickListener {
                showKeyboardForWebView()
                Toast.makeText(this@FloatingBrowserService, "মেসেজ লিখার জন্য কিবোর্ড চালু হয়েছে", Toast.LENGTH_SHORT).show()
            }
        }
        rightControls.addView(typeBtn)

        // Close (✕) button requested by user
        val closeBtn = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setColorFilter(0xFFFF4D4D.toInt()) // Red X
            setPadding(dp(5), dp(5), dp(5), dp(5))
            layoutParams = LinearLayout.LayoutParams(dp(26), dp(26))
            setOnClickListener {
                stopSelf()
            }
        }
        rightControls.addView(closeBtn)

        header.addView(rightControls)

        // Dragging handler on full header:
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

            // On tap inside WebView, make window focusable without dropping touch event
            setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        // Immediately acquire focus on touch down so the window is focusable during tap
                        if (!isWindowFocusedForInput) {
                            acquireFocusForFloatingWindow()
                        }
                        v.requestFocus()
                    }
                    MotionEvent.ACTION_UP -> {
                        val density = resources.displayMetrics.density
                        val cssX = (event.x / density).toInt()
                        val cssY = (event.y / density).toInt()

                        // Run JavaScript to focus the exact DOM element under the finger
                        val focusScript = """
                            (function() {
                                var el = document.elementFromPoint($cssX, $cssY);
                                if (el) {
                                    var inputEl = el.closest('input, textarea, [contenteditable="true"], [role="textbox"], .input-message-input, #editable-message-text') || el;
                                    if (inputEl && typeof inputEl.focus === 'function') {
                                        inputEl.focus();
                                        if (typeof inputEl.click === 'function') {
                                            inputEl.click();
                                        }
                                    }
                                }
                            })();
                        """.trimIndent()
                        (v as? WebView)?.evaluateJavascript(focusScript, null)

                        v.post {
                            v.requestFocus()
                            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                            imm?.showSoftInput(v, InputMethodManager.SHOW_IMPLICIT)
                        }
                    }
                }
                false
            }

            // WebSettings configuration optimized for Telegram Web & smooth non-hanging performance
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
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
                blockNetworkImage = false
                blockNetworkLoads = false
                userAgentString = "Mozilla/5.0 (Linux; Android 14; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
                textZoom = 80
            }
            setInitialScale(80)

            addJavascriptInterface(AutomationBridge(this@FloatingBrowserService), "AndroidAutomation")

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
                    val automationJs = """
                        (function() {
                            try { 
                                if (document.body) document.body.style.zoom = '82%'; 
                                var style = document.getElementById('sah-zoom-override') || document.createElement('style');
                                style.id = 'sah-zoom-override';
                                style.innerHTML = 'body { zoom: 82% !important; -webkit-text-size-adjust: 82% !important; }';
                                document.head.appendChild(style);
                            } catch(e) {}
                            if (window.__sah_automation_injected) return;
                            window.__sah_automation_injected = true;

                            setInterval(function() {
                                var text = document.body ? document.body.innerText : "";
                                
                                // 1. Check for Login credentials format (First name, Login, Password)
                                if (text.includes("Login:") || text.includes("Password:") || text.includes("First name:")) {
                                    var matchLogin = text.match(/Login:\s*([^\s\n]+)/i);
                                    var matchPass = text.match(/Password:\s*([^\s\n]+)/i);
                                    
                                    if (matchLogin) {
                                        var loginVal = matchLogin[1].trim();
                                        var passVal = matchPass ? matchPass[1].trim() : "";
                                        
                                        // Only copy the exact login value as requested by user (e.g. life_2tenia)
                                        if (window.AndroidAutomation && window.__sah_last_login !== loginVal) {
                                            window.__sah_last_login = loginVal;
                                            window.AndroidAutomation.copyToClipboard(loginVal, "Login: " + loginVal);
                                        }

                                        var userInputs = document.querySelectorAll('input[type="text"], input[type="email"], input[name*="user"], input[name*="login"], input[name*="username"], input[id*="user"], input[id*="login"]');
                                        var passInputs = document.querySelectorAll('input[type="password"], input[name*="pass"], input[id*="pass"]');
                                        
                                        if (userInputs.length > 0 && userInputs[0].value !== loginVal) {
                                            userInputs[0].value = loginVal;
                                            userInputs[0].dispatchEvent(new Event('input', { bubbles: true }));
                                            userInputs[0].dispatchEvent(new Event('change', { bubbles: true }));
                                        }
                                        if (passVal && passInputs.length > 0 && passInputs[0].value !== passVal) {
                                            passInputs[0].value = passVal;
                                            passInputs[0].dispatchEvent(new Event('input', { bubbles: true }));
                                            passInputs[0].dispatchEvent(new Event('change', { bubbles: true }));
                                        }

                                        // Auto-click login button / inline button / link matching login or action
                                        var clickables = document.querySelectorAll('button, a, .btn, .reply-markup-button, [role="button"], div[role="button"]');
                                        for (var c = 0; c < clickables.length; c++) {
                                            var el = clickables[c];
                                            var elText = el.innerText || el.textContent || "";
                                            if (elText.includes(loginVal) || elText.toLowerCase().includes("login") || elText.toLowerCase().includes("sign in") || elText.toLowerCase().includes("continue") || elText.toLowerCase().includes("submit") || elText.toLowerCase().includes("confirm")) {
                                                if (el.offsetParent !== null && !el.__sah_clicked) {
                                                    el.__sah_clicked = true;
                                                    el.click();
                                                    var ev = new MouseEvent('click', { bubbles: true, cancelable: true, view: window });
                                                    el.dispatchEvent(ev);
                                                    break;
                                                }
                                            }
                                        }
                                    }
                                }

                                // 2. Proactive Network Watchdog: Fix "Waiting for network" / "Connecting..."
                                var isConnecting = false;
                                var statusEl = document.querySelector('.ConnectionStatus, [class*="ConnectionStatus"], [class*="connection-status"], [class*="status-connecting"], .status-connecting, .connection-state');
                                if (statusEl && statusEl.offsetParent !== null) {
                                    isConnecting = true;
                                } else if (text.includes("waiting for network") || text.includes("Waiting for network") || text.includes("Connecting...") || text.includes("Reconnecting")) {
                                    isConnecting = true;
                                }

                                if (isConnecting) {
                                    window.__sah_stuck_count = (window.__sah_stuck_count || 0) + 1;
                                    window.dispatchEvent(new Event('online'));

                                    // Trigger status reconnect click
                                    if (statusEl) {
                                        try { statusEl.click(); } catch(e) {}
                                    }
                                    var reconnectBtn = document.querySelector('button[class*="reconnect"], [role="button"][class*="reconnect"], .btn-reconnect');
                                    if (reconnectBtn) {
                                        try { reconnectBtn.click(); } catch(e) {}
                                    }

                                    // Telegram Web API hook if available
                                    try {
                                        if (window.telegramMeApi && typeof window.telegramMeApi.reconnect === 'function') {
                                            window.telegramMeApi.reconnect();
                                        }
                                    } catch(e) {}

                                    // If stuck for 4 iterations (~4 seconds) and we haven't reloaded recently, auto-reconnect cleanly
                                    var now = Date.now();
                                    if (window.__sah_stuck_count >= 4 && (!window.__sah_last_reload || now - window.__sah_last_reload > 25000)) {
                                        window.__sah_stuck_count = 0;
                                        window.__sah_last_reload = now;
                                        try {
                                            window.location.reload();
                                        } catch(e) {}
                                    }
                                } else {
                                    window.__sah_stuck_count = 0;
                                }
                            }, 1000);
                        })();
                    """;
                    view?.evaluateJavascript(automationJs, null)
                }
            }

            loadUrl(initialUrl)
        }

        webContainer.addView(webView)
        card.addView(webContainer)

        // --- Bottom Drag Bar (Allows moving window from bottom handle too) ---
        var bottomDragStartX = 0
        var bottomDragStartY = 0
        var bottomTouchStartX = 0f
        var bottomTouchStartY = 0f

        val bottomBar = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(16)
            )
            setBackgroundColor(0xFF1E222D.toInt())
        }

        val bottomDragHandle = View(this).apply {
            val pill = GradientDrawable().apply {
                setColor(0x55FFFFFF.toInt())
                cornerRadius = dp(3).toFloat()
            }
            background = pill
            val lp = FrameLayout.LayoutParams(dp(50), dp(4)).apply {
                gravity = Gravity.CENTER
            }
            layoutParams = lp
        }
        bottomBar.addView(bottomDragHandle)

        bottomBar.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    releaseFocusToBackground()
                    bottomDragStartX = params?.x ?: 0
                    bottomDragStartY = params?.y ?: 0
                    bottomTouchStartX = event.rawX
                    bottomTouchStartY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params?.x = bottomDragStartX + (event.rawX - bottomTouchStartX).toInt()
                    params?.y = bottomDragStartY + (event.rawY - bottomTouchStartY).toInt()
                    try {
                        windowManager?.updateViewLayout(floatingView, params)
                    } catch (_: Exception) {}
                    true
                }
                else -> false
            }
        }
        card.addView(bottomBar)

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
        val edgeSize = dp(20)
        val cornerSize = dp(42)

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

        // Bottom-Right Corner (Resizing from right & bottom)
        val bottomRightCorner = FrameLayout(this).apply {
            val lp = FrameLayout.LayoutParams(cornerSize, cornerSize).apply {
                gravity = Gravity.BOTTOM or Gravity.END
            }
            layoutParams = lp
            isClickable = true

            val gripIndicator = ImageView(this@FloatingBrowserService).apply {
                val iconLp = FrameLayout.LayoutParams(dp(16), dp(16)).apply {
                    gravity = Gravity.BOTTOM or Gravity.END
                    setMargins(0, 0, dp(6), dp(6))
                }
                layoutParams = iconLp
                val gripShape = GradientDrawable().apply {
                    setStroke(dp(2.5f), 0xCC00E5FF.toInt())
                    cornerRadius = dp(3).toFloat()
                }
                background = gripShape
            }
            addView(gripIndicator)
            attachResizeListener(this, resizeRight = true, resizeBottom = true)
        }
        root.addView(bottomRightCorner)
        bottomRightCorner.bringToFront()

        // Bottom-Left Corner (Resizing from left & bottom)
        val bottomLeftCorner = FrameLayout(this).apply {
            val lp = FrameLayout.LayoutParams(cornerSize, cornerSize).apply {
                gravity = Gravity.BOTTOM or Gravity.START
            }
            layoutParams = lp
            isClickable = true

            val gripIndicator = ImageView(this@FloatingBrowserService).apply {
                val iconLp = FrameLayout.LayoutParams(dp(16), dp(16)).apply {
                    gravity = Gravity.BOTTOM or Gravity.START
                    setMargins(dp(6), 0, 0, dp(6))
                }
                layoutParams = iconLp
                val gripShape = GradientDrawable().apply {
                    setStroke(dp(2.5f), 0xCC00E5FF.toInt())
                    cornerRadius = dp(3).toFloat()
                }
                background = gripShape
            }
            addView(gripIndicator)
            attachResizeListener(this, resizeLeft = true, resizeBottom = true)
        }
        root.addView(bottomLeftCorner)
        bottomLeftCorner.bringToFront()

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
                    releaseFocusToBackground()
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
                    val minW = dp(200)
                    val maxW = (displayMetrics.widthPixels * 0.98f).toInt()
                    val minH = dp(200)
                    val maxH = (displayMetrics.heightPixels * 0.92f).toInt()

                    if (resizeRight) {
                        p.width = (startW + deltaX).coerceIn(minW, maxW)
                    } else if (resizeLeft) {
                        val proposedW = (startW - deltaX).coerceIn(minW, maxW)
                        val deltaApplied = proposedW - startW
                        val newX = startXPos - deltaApplied
                        if (newX >= 0) {
                            p.width = proposedW
                            p.x = newX
                        } else {
                            p.x = 0
                            p.width = (startXPos + startW).coerceIn(minW, maxW)
                        }
                    }

                    if (resizeBottom) {
                        p.height = (startH + deltaY).coerceIn(minH, maxH)
                    } else if (resizeTop) {
                        val proposedH = (startH - deltaY).coerceIn(minH, maxH)
                        val deltaApplied = proposedH - startH
                        val newY = startYPos - deltaApplied
                        if (newY >= 0) {
                            p.height = proposedH
                            p.y = newY
                        } else {
                            p.y = 0
                            p.height = (startYPos + startH).coerceIn(minH, maxH)
                        }
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
                // 1. Try clicking common web messenger send buttons (Telegram Web K, Web A, WhatsApp, etc.)
                var selectors = [
                    'button.btn-send',
                    'button.tgico-send',
                    'button[title*="Send"]',
                    'button[aria-label*="Send"]',
                    'button.send',
                    '.btn-circle.btn-primary',
                    '.chat-input-control.send',
                    '.btn-send-message',
                    'button[data-testid="send-button"]',
                    'button[type="submit"]',
                    '.icon-send'
                ];
                for (var i = 0; i < selectors.length; i++) {
                    var btn = document.querySelector(selectors[i]);
                    if (btn && btn.offsetParent !== null) {
                        btn.click();
                        var ev = new MouseEvent('click', { bubbles: true, cancelable: true, view: window });
                        btn.dispatchEvent(ev);
                        return 'clicked_' + selectors[i];
                    }
                }

                // 2. Dispatch simulated Enter events to active editable / input
                var target = document.activeElement;
                if (!target || target.tagName === 'BODY' || (target.tagName === 'DIV' && !target.getAttribute('contenteditable'))) {
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

    fun insertTextAndSendInWeb(textToInsert: String, autoSend: Boolean = true) {
        acquireFocusForFloatingWindow()
        webView?.requestFocus()
        val escapedText = org.json.JSONObject.quote(textToInsert)
        val js = """
            (function() {
                if (window.__sah_processing_insert) return 'already_processing';
                window.__sah_processing_insert = true;
                setTimeout(function() { window.__sah_processing_insert = false; }, 1200);

                var val = $escapedText;

                function findTargetInput() {
                    // 1. Check for password or 2FA code input (e.g. 2FA verification screen)
                    var passSelectors = [
                        'input[type="password"]',
                        'input[name*="pass"]',
                        'input[name*="code"]',
                        'input[name*="2fa"]',
                        'input[autocomplete*="password"]',
                        'input.form-control'
                    ];
                    for (var p = 0; p < passSelectors.length; p++) {
                        var el = document.querySelector(passSelectors[p]);
                        if (el && el.offsetParent !== null && el.type !== 'hidden') {
                            return el;
                        }
                    }

                    // 2. Telegram Web (Web A, Web K) chat message inputs
                    var chatSelectors = [
                        '.input-message-input',
                        '#editable-message-text',
                        '.chat-input-main div[contenteditable="true"]',
                        '.chat-input div[contenteditable="true"]',
                        '.input-message-container div[contenteditable="true"]',
                        'div[contenteditable="true"]',
                        'textarea',
                        '[role="textbox"]',
                        '.composer div[contenteditable="true"]',
                        '.input-field-input'
                    ];
                    for (var i = 0; i < chatSelectors.length; i++) {
                        var el = document.querySelector(chatSelectors[i]);
                        if (el && el.offsetParent !== null) {
                            return el;
                        }
                    }

                    // 3. Active element if already an editable or input
                    var act = document.activeElement;
                    if (act && act.tagName !== 'BODY' && (act.tagName === 'INPUT' || act.tagName === 'TEXTAREA' || act.getAttribute('contenteditable') === 'true')) {
                        return act;
                    }

                    // 4. Any visible input or textarea
                    var any = document.querySelector('div[contenteditable="true"], input:not([type="hidden"]), textarea');
                    if (any && any.offsetParent !== null) {
                        return any;
                    }
                    return null;
                }

                var target = findTargetInput();
                if (target) {
                    // Programmatically activate and focus target without requiring prior manual tap
                    try { target.focus(); } catch(e) {}
                    try { target.click(); } catch(e) {}
                    try { target.focus(); } catch(e) {}

                    // Insert text cleanly ONLY ONCE
                    var isContentEditable = target.getAttribute('contenteditable') === 'true' || 
                                           target.classList.contains('input-message-input') || 
                                           target.id === 'editable-message-text' || 
                                           target.tagName === 'DIV';

                    if (isContentEditable) {
                        target.innerText = val;
                    } else {
                        target.value = val;
                    }

                    target.dispatchEvent(new Event('input', { bubbles: true, cancelable: true }));
                    target.dispatchEvent(new Event('change', { bubbles: true, cancelable: true }));

                    ${if (autoSend) """
                    setTimeout(function() {
                        var sendSelectors = [
                            'button.btn-send',
                            'button.tgico-send',
                            'button[title*="Send"]',
                            'button[aria-label*="Send"]',
                            'button.send',
                            '.btn-circle.btn-primary',
                            '.chat-input-control.send',
                            '.btn-send-message',
                            'button[data-testid="send-button"]',
                            'button[type="submit"]',
                            '.icon-send'
                        ];
                        var sent = false;
                        for (var s = 0; s < sendSelectors.length; s++) {
                            var btn = document.querySelector(sendSelectors[s]);
                            if (btn && btn.offsetParent !== null) {
                                btn.click(); // Single click only - do NOT dispatch extra MouseEvent!
                                sent = true;
                                break;
                            }
                        }
                        if (!sent) {
                            // Single Enter key press if no button found
                            var ev = new KeyboardEvent('keydown', { key: 'Enter', code: 'Enter', keyCode: 13, which: 13, bubbles: true });
                            target.dispatchEvent(ev);
                            if (target.form) {
                                target.form.submit();
                            }
                        }
                    }, 350);
                    """ else ""}
                    return 'success';
                }
                return 'no_target';
            })();
        """.trimIndent()
        webView?.evaluateJavascript(js, null)
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

    inner class AutomationBridge(private val context: Context) {
        @android.webkit.JavascriptInterface
        fun copyToClipboard(text: String, label: String) {
            try {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText(label, text)
                clipboard.setPrimaryClip(clip)

                try {
                    val db = com.example.moekeyboard.data.db.MoeDatabase.getDatabase(context)
                    val now = System.currentTimeMillis()
                    serviceScope.launch(Dispatchers.IO) {
                        try {
                            val existing = db.clipboardDao().findByText(text)
                            if (existing == null) {
                                db.clipboardDao().insert(com.example.moekeyboard.data.db.ClipboardItem(text = text, timestamp = now))
                            } else {
                                db.clipboardDao().update(existing.copy(timestamp = now))
                            }
                        } catch (_: Exception) {}
                    }
                } catch (_: Exception) {}

                android.os.Handler(context.mainLooper).post {
                    Toast.makeText(context, "অটো-কপি ও কিবোর্ডে সেভ হয়েছে: $label", Toast.LENGTH_SHORT).show()
                }
            } catch (_: Exception) {}
        }
    }

    private fun toggleMinimizeToBubble() {
        if (!isMinimized) {
            // Minimize window to a circular Telegram-style floating logo bubble
            if (floatingView != null) {
                try {
                    windowManager?.removeView(floatingView)
                } catch (_: Exception) {}
                floatingView = null
            }
            isMinimized = true

            val bubbleSize = dp(56)
            val bubbleLayout = FrameLayout(this).apply {
                val bg = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(0xFF229ED9.toInt()) // Telegram blue
                    setStroke(dp(2f), 0xFFFFFFFF.toInt())
                }
                background = bg
                elevation = dp(12).toFloat()
                layoutParams = ViewGroup.LayoutParams(bubbleSize, bubbleSize)

                // Telegram / Chat Icon inside bubble
                val icon = ImageView(context).apply {
                    setImageResource(android.R.drawable.ic_menu_send)
                    setColorFilter(0xFFFFFFFF.toInt())
                    val pad = dp(14)
                    setPadding(pad, pad, pad, pad)
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                }
                addView(icon)

                setOnClickListener {
                    toggleMinimizeToBubble() // Restore window
                }
            }

            val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val bubbleParams = WindowManager.LayoutParams(
                bubbleSize,
                bubbleSize,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = params?.x ?: 100
                y = params?.y ?: 100
            }

            // Drag listener for bubble
            var dX = 0f
            var dY = 0f
            var startX = 0
            var startY = 0
            bubbleLayout.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        dX = bubbleParams.x - event.rawX
                        dY = bubbleParams.y - event.rawY
                        startX = bubbleParams.x
                        startY = bubbleParams.y
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        bubbleParams.x = (event.rawX + dX).toInt()
                        bubbleParams.y = (event.rawY + dY).toInt()
                        try {
                            windowManager?.updateViewLayout(bubbleLayout, bubbleParams)
                        } catch (_: Exception) {}
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        val moved = Math.abs(bubbleParams.x - startX) > 10 || Math.abs(bubbleParams.y - startY) > 10
                        if (!moved) {
                            bubbleLayout.performClick()
                        }
                        true
                    }
                    else -> false
                }
            }

            minimizedBubbleView = bubbleLayout
            try {
                windowManager?.addView(minimizedBubbleView, bubbleParams)
            } catch (_: Exception) {}

            Toast.makeText(this, "মিনিমাইজ করা হয়েছে। লোগোতে ক্লিক করলে উইন্ডো আবার খুলবে।", Toast.LENGTH_SHORT).show()
        } else {
            // Restore window from bubble
            if (minimizedBubbleView != null) {
                try {
                    windowManager?.removeView(minimizedBubbleView)
                } catch (_: Exception) {}
                minimizedBubbleView = null
            }
            isMinimized = false

            val urlToLoad = currentLoadedUrl.ifBlank { DEFAULT_TELEGRAM_URL }
            initFloatingWindow(urlToLoad)
            Toast.makeText(this, "উইন্ডো আবার ওপেন হয়েছে", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        unregisterNetworkCallback()
        if (activeInstance == this) {
            activeInstance = null
        }
        super.onDestroy()
        isRunning = false
        serviceScope.cancel()
        if (floatingView != null) {
            try {
                windowManager?.removeView(floatingView)
            } catch (_: Exception) {}
            floatingView = null
        }
        if (minimizedBubbleView != null) {
            try {
                windowManager?.removeView(minimizedBubbleView)
            } catch (_: Exception) {}
            minimizedBubbleView = null
        }
        webView?.destroy()
        webView = null
    }
}
