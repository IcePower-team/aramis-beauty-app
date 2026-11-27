package ir.aramisbeauty.app

import android.Manifest
import android.animation.Animator
import android.animation.Keyframe
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.app.Dialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.ColorDrawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import java.net.URLEncoder
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.google.firebase.messaging.FirebaseMessaging
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.drawerlayout.widget.DrawerLayout
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

class MainActivity : AppCompatActivity() {

    companion object {
        const val HOME_URL = "https://aramis-beauty.ir/"
        const val LOGIN_URL = "https://aramis-beauty.ir/login"
        const val REGISTER_URL = "https://aramis-beauty.ir/register"
        const val CART_URL = "https://aramis-beauty.ir/cart"
        const val PANEL_URL = "https://aramis-beauty.ir/panel"
        const val TELEGRAM_URL = "https://t.me/AramisBeauty_Ch"
        const val RUBIKA_URL = "https://rubika.ir/joing/HCIBADFA0JCEETLQHXTIJKEXVSMCNMYU"
        const val INSTAGRAM_URL = "https://www.instagram.com/aramisbeauty.shop"
    }

    private lateinit var drawer: DrawerLayout
    private lateinit var webView: WebView
    private lateinit var swipe: SwipeRefreshLayout
    private var headerView: View? = null
    private var updateDialog: Dialog? = null
    // Product id of the page currently shown in the WebView, if any —
    // drives the "خبرم کن" toolbar button.
    private var currentProductId: Int? = null
    // True once a live check has confirmed the current product is out
    // of stock — the bell only shows then, never on in-stock pages.
    private var currentProductOutOfStock: Boolean = false
    // Generation counter so a slow live check for a page the user has
    // already left cannot flip the bell for the new page.
    private var notifyCheckGen: Int = 0
    // Animated launch splash (shown while the site loads, then fades out)
    private var splashView: View? = null
    private var splashHidden = false
    private var splashOffline = false
    private var splashStartTime = 0L
    private val splashHandler = Handler(Looper.getMainLooper())
    private var dealsChipHandler: Handler? = null
    // Pink heartbeat pulse on the deals countdown chip (see
    // setDealsChipPulse); cancelled in onDestroy.
    private var dealsChipPulse: Animator? = null
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(true)

        drawer = findViewById(R.id.drawer_layout)
        val toggle = ActionBarDrawerToggle(
            this, drawer, toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )
        drawer.addDrawerListener(toggle)
        toggle.syncState()

        swipe = findViewById(R.id.swipe_refresh)
        swipe.setColorSchemeResources(R.color.burgundy)
        swipe.setOnRefreshListener { webView.reload() }

        webView = findViewById(R.id.webview)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.databaseEnabled = true
        CookieManager.getInstance().setAcceptCookie(true)
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val url = request.url.toString()
                val host = request.url.host ?: ""
                // Payment gateways stay INSIDE the app's WebView: opening the
                // payment URL in an external browser strips the navigation
                // context (no referer from the shop), and the gateway rejects
                // it with "access from this domain is not allowed".
                // The chain is: shop -> zarinpal.com -> sep.shaparak.ir
                // (Shaparak switch) -> the card's bank PSP (*.shaparak.ir)
                // -> back to the shop, so every *.shaparak.ir host must stay
                // in-app too, or the payment page pops out to the browser.
                return if (host == "aramis-beauty.ir" || host.endsWith(".aramis-beauty.ir")
                    || host.endsWith("zarinpal.com") || host.endsWith("snapppay.ir")
                    || host == "shaparak.ir" || host.endsWith(".shaparak.ir")) {
                    false // stay inside the app
                } else {
                    openExternal(url)
                    true
                }
            }

            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                swipe.isRefreshing = true
            }

            override fun onPageFinished(view: WebView, url: String) {
                swipe.isRefreshing = false
                checkLoginState()
                updateNotifyMenu()
                hideSplash()
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                // Main-frame load failures only. Show the offline state when
                // there is no network, or the error itself is a connectivity
                // failure (unknown / host lookup / connect / I/O / timeout).
                if (request.isForMainFrame &&
                    (!isOnline() || error.errorCode in listOf(-1, -2, -6, -7, -8))
                ) {
                    showSplashOffline()
                }
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                findViewById<ProgressBar>(R.id.splash_progress)?.progress = newProgress
            }
        }

        // Categories live on the site homepage, so the drawer has no
        // categories section (v1.6.9 fix: it was pushed below the fold
        // with no way to scroll to it).
        findViewById<View>(R.id.row_home).setOnClickListener {
            loadUrl(HOME_URL)
            drawer.closeDrawers()
        }
        findViewById<View>(R.id.row_cart).setOnClickListener {
            loadUrl(CART_URL)
            drawer.closeDrawers()
        }
        findViewById<View>(R.id.row_quiz).setOnClickListener {
            startActivity(Intent(this, QuizActivity::class.java))
            drawer.closeDrawers()
        }
        findViewById<View>(R.id.row_advisor).setOnClickListener {
            startActivity(Intent(this, ChatActivity::class.java))
            drawer.closeDrawers()
        }
        findViewById<View>(R.id.row_deals).setOnClickListener {
            startActivity(Intent(this, DealsActivity::class.java))
            drawer.closeDrawers()
        }
        findViewById<View>(R.id.row_routine).setOnClickListener {
            startActivity(Intent(this, RoutineActivity::class.java))
            drawer.closeDrawers()
        }

        val prefs = getSharedPreferences("aramis_prefs", MODE_PRIVATE)

        // App features group — collapsible header with a rotating
        // chevron, choice remembered in prefs. Default expanded on
        // first install.
        val featuresGroup = findViewById<View>(R.id.features_group)
        val featuresChevron = findViewById<ImageView>(R.id.features_chevron)
        fun setFeaturesExpanded(expanded: Boolean) {
            featuresGroup.visibility = if (expanded) View.VISIBLE else View.GONE
            featuresChevron.rotation = if (expanded) 180f else 0f
        }
        setFeaturesExpanded(prefs.getBoolean("drawer_features_expanded", true))
        findViewById<View>(R.id.features_header).setOnClickListener {
            val expanded = featuresGroup.visibility != View.VISIBLE
            featuresChevron.animate()
                .rotation(if (expanded) 180f else 0f)
                .setDuration(250).start()
            setFeaturesExpanded(expanded)
            prefs.edit().putBoolean("drawer_features_expanded", expanded).apply()
        }

        // Live countdown chip on the deals row (cached deals only; hidden
        // until the deals have been fetched once, and after they end).
        // Ticks once a second, same as the DealsActivity countdown.
        val dealsChip = findViewById<TextView>(R.id.deals_countdown_chip)
        val chipHandler = Handler(Looper.getMainLooper())
        dealsChipHandler = chipHandler
        val dealsChipTick = object : Runnable {
            override fun run() {
                try {
                    val cfg = RemoteDeals.loadCached(this@MainActivity)
                    if (cfg == null) {
                        dealsChip.visibility = View.GONE
                    } else {
                        val remaining = cfg.endsAtMillis - System.currentTimeMillis()
                        if (remaining <= 0) {
                            dealsChip.visibility = View.GONE
                        } else {
                            dealsChip.visibility = View.VISIBLE
                            dealsChip.text = formatDealsChip(remaining)
                        }
                    }
                } catch (_: Exception) {
                    dealsChip.visibility = View.GONE
                }
                setDealsChipPulse(dealsChip, dealsChip.visibility == View.VISIBLE)
                chipHandler.postDelayed(this, 1000)
            }
        }
        chipHandler.post(dealsChipTick)

        // Login / register buttons in the drawer header
        val header = findViewById<View>(R.id.drawer_header)
        headerView = header
        header.findViewById<Button>(R.id.btn_login).setOnClickListener {
            loadUrl(LOGIN_URL)
            drawer.closeDrawers()
        }
        header.findViewById<Button>(R.id.btn_register).setOnClickListener {
            loadUrl(REGISTER_URL)
            drawer.closeDrawers()
        }
        header.findViewById<Button>(R.id.btn_profile).setOnClickListener {
            loadUrl(PANEL_URL)
            drawer.closeDrawers()
        }

        // Re-check the login state every time the drawer opens, so the
        // header buttons are right even if the state changed without a
        // fresh page load in between. While the drawer is open the
        // content behind its frosted panel is really blurred (API 31+).
        drawer.addDrawerListener(object : DrawerLayout.DrawerListener {
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {}
            override fun onDrawerOpened(drawerView: View) {
                checkLoginState()
                setContentBlur(true)
            }
            override fun onDrawerClosed(drawerView: View) { setContentBlur(false) }
            override fun onDrawerStateChanged(newState: Int) {}
        })

        // Social links at the bottom of the drawer
        findViewById<View>(R.id.social_telegram).setOnClickListener {
            openExternal(TELEGRAM_URL)
        }
        findViewById<View>(R.id.social_rubika).setOnClickListener {
            openExternal(RUBIKA_URL)
        }
        findViewById<View>(R.id.social_instagram).setOnClickListener {
            openExternal(INSTAGRAM_URL)
        }
        // v1.6.2: Telegram-style press morph on the glass social circles.
        findViewById<View>(R.id.social_telegram).applyPressMorph()
        findViewById<View>(R.id.social_rubika).applyPressMorph()
        findViewById<View>(R.id.social_instagram).applyPressMorph()

        // Privacy policy (v1.6.3): Cafe Bazaar requires the full text
        // readable inside the app — opens the policy screen.
        findViewById<View>(R.id.row_privacy).setOnClickListener {
            startActivity(Intent(this, PrivacyPolicyActivity::class.java))
            drawer.closeDrawers()
        }

        // Installed version, shown at the bottom of the drawer
        findViewById<TextView>(R.id.drawer_version)?.text =
            "نسخه ${toFaDigits(currentVersionName())}"

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    drawer.isDrawerOpen(findViewById<View>(R.id.drawer_container)) ->
                        drawer.closeDrawers()
                    webView.canGoBack() -> webView.goBack()
                    else -> {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        })

        val startUrl = intent?.data?.toString()?.takeIf {
            it.startsWith("https://aramis-beauty.ir/")
        } ?: HOME_URL
        if (savedInstanceState == null) {
            webView.loadUrl(startUrl)
        }

        // Animated launch splash: the logo fades/scales in over a gold
        // progress bar while the site loads; it fades out once the page
        // is ready (never later than 8s, so it can never trap the user).
        startSplash()

        // Push notifications: channel, topic subscription, permission.
        setupNotifications()

        // Check update.json for a newer version; shows the update dialog
        // only when one exists (silent when offline or already up to date).
        checkForUpdate()
    }

    // Push notifications (Firebase Cloud Messaging): create the channel,
    // join the "all" topic so console sends reach this device, and ask
    // for the runtime notification permission on Android 13+.
    private fun setupNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(
                    AramisMessagingService.CHANNEL_ID,
                    "اطلاع‌رسانی آرامیس بیوتی",
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }
        FirebaseMessaging.getInstance().subscribeToTopic(AramisMessagingService.TOPIC_ALL)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // Notification taps (and deep links) carry the shop URL in the intent;
    // load it when the activity is already running.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.data?.toString()?.takeIf {
            it.startsWith("https://aramis-beauty.ir/")
        }?.let { loadUrl(it) }
    }

    private fun startSplash() {
        val splash = findViewById<View>(R.id.splash_overlay) ?: return
        splashView = splash
        splashStartTime = SystemClock.uptimeMillis()
        val logo = findViewById<ImageView>(R.id.splash_logo)
        logo?.let {
            it.alpha = 0f
            it.scaleX = 0.92f
            it.scaleY = 0.92f
            it.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(700)
                .setInterpolator(DecelerateInterpolator()).start()
        }
        findViewById<TextView>(R.id.splash_text)?.let { text ->
            text.alpha = 0f
            text.animate().alpha(1f).setStartDelay(300).setDuration(500).start()
        }
        findViewById<Button>(R.id.splash_retry)?.setOnClickListener { retrySplashLoad() }
        if (!isOnline()) {
            showSplashOffline()
            return
        }
        splashHandler.postDelayed({ hideSplash() }, 8000)
    }

    private fun isOnline(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    // No-internet state on the launch screen: hide the loading UI, show the
    // warning + retry at the bottom, and keep the splash up until the user
    // retries (cancels any pending auto-hide).
    private fun showSplashOffline() {
        val splash = splashView ?: return
        splashOffline = true
        splashHandler.removeCallbacksAndMessages(null)
        splash.animate().cancel()
        splash.alpha = 1f
        splash.visibility = View.VISIBLE
        findViewById<View>(R.id.splash_progress)?.visibility = View.GONE
        findViewById<View>(R.id.splash_text)?.visibility = View.GONE
        findViewById<View>(R.id.splash_offline_box)?.visibility = View.VISIBLE
    }

    // Retry button: back to the loading state and reload the site.
    private fun retrySplashLoad() {
        val splash = splashView ?: return
        splashOffline = false
        splashHidden = false
        splashStartTime = SystemClock.uptimeMillis()
        findViewById<View>(R.id.splash_offline_box)?.visibility = View.GONE
        findViewById<ProgressBar>(R.id.splash_progress)?.let {
            it.visibility = View.VISIBLE
            it.progress = 0
        }
        findViewById<View>(R.id.splash_text)?.let {
            it.visibility = View.VISIBLE
            it.alpha = 1f
        }
        splashHandler.postDelayed({ hideSplash() }, 8000)
        webView.reload()
    }

    private fun hideSplash() {
        val splash = splashView ?: return
        if (splashHidden || splashOffline) return
        splashHidden = true
        // Let the intro animation be seen for at least 1.5s, then fade out.
        val elapsed = SystemClock.uptimeMillis() - splashStartTime
        val delay = (1500L - elapsed).coerceAtLeast(0L)
        splashHandler.postDelayed({
            splash.animate().alpha(0f).setDuration(400).withEndAction {
                splash.visibility = View.GONE
            }.start()
        }, delay)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.toolbar_menu, menu)
        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem.actionView as SearchView
        searchView.queryHint = getString(R.string.search_hint)
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                val q = query?.trim()
                if (!q.isNullOrEmpty()) {
                    loadUrl(HOME_URL + "?search=" + URLEncoder.encode(q, "UTF-8"))
                }
                searchView.clearFocus()
                searchItem.collapseActionView()
                return true
            }
            override fun onQueryTextChange(newText: String?): Boolean = false
        })
        return true
    }

    // Detect whether the user is logged in to the shop and swap the drawer
    // header buttons accordingly. Two signals, either is enough: the
    // logged-in header carries a "خروج" (logout) link, and the header's
    // login link ("/login?backurl=") disappears once logged in. The footer
    // keeps a plain "/login" link either way, so only the backurl form is
    // used as the signal.
    private fun checkLoginState() {
        val currentUrl = try {
            webView.url
        } catch (_: Exception) {
            null
        } ?: return
        val host = try {
            android.net.Uri.parse(currentUrl).host ?: ""
        } catch (_: Exception) {
            ""
        }
        if (host != "aramis-beauty.ir" && !host.endsWith(".aramis-beauty.ir")) return
        webView.evaluateJavascript(
            "(function(){var h=document.body?document.body.innerHTML:'';return h.indexOf('خروج')!==-1||h.indexOf('login?backurl=')===-1;})()"
        ) { result -> updateAuthButtons(result == "true") }
    }

    // Swap the drawer header buttons by login state: logged in -> a single
    // "مشاهده پروفایل" button; logged out -> ورود / عضویت.
    private fun updateAuthButtons(loggedIn: Boolean) {
        val header = headerView ?: return
        header.findViewById<View>(R.id.auth_buttons_row)?.visibility =
            if (loggedIn) View.GONE else View.VISIBLE
        header.findViewById<View>(R.id.btn_profile)?.visibility =
            if (loggedIn) View.VISIBLE else View.GONE
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val notifyItem = menu.findItem(R.id.action_notify)
        val id = currentProductId
        notifyItem?.isVisible = id != null && currentProductOutOfStock
        if (id != null) {
            notifyItem?.title =
                if (NotifyMe.isSubscribed(this, id)) "🔔 خبرت می‌کنم" else "🔔 خبرم کن"
        }
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_cart -> {
                loadUrl(CART_URL)
                true
            }
            R.id.action_home -> {
                loadUrl(HOME_URL)
                true
            }
            R.id.action_notify -> {
                currentProductId?.let { NotifyMe.toggle(this, it) }
                invalidateOptionsMenu()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // "خبرم کن": the toolbar bell is only visible on a product page
    // (https://aramis-beauty.ir/product/<id>) whose live stock check
    // says OUT of stock. In-stock pages never show it, and neither do
    // pages whose stock could not be confirmed (fetch failed) — the
    // bell only appears on a confirmed out-of-stock result.
    private fun updateNotifyMenu() {
        val url = try {
            webView.url
        } catch (_: Exception) {
            null
        } ?: return
        val match = Regex("^https://aramis-beauty\\.ir/product/(\\d+)")
            .find(url.trim())
        val id = match?.groupValues?.getOrNull(1)?.toIntOrNull()
        if (id != currentProductId) {
            currentProductId = id
            currentProductOutOfStock = false
            invalidateOptionsMenu()
            if (id != null) {
                val gen = ++notifyCheckGen
                Thread {
                    val live = LiveCheck.fetch(id)
                    runOnUiThread {
                        if (gen == notifyCheckGen && currentProductId == id) {
                            currentProductOutOfStock = live != null && !live.inStock
                            invalidateOptionsMenu()
                        }
                    }
                }.start()
            }
        }
    }

    // ---- In-app update (remote config -> Cafe Bazaar) ----
    //
    // v1.6.9: on every open, update.json is fetched from the repo (same
    // hosting as catalog.json). When its latest_version_code is higher
    // than the installed versionCode, a dialog links out to Cafe
    // Bazaar. No APK is downloaded here — Bazaar rejects
    // REQUEST_INSTALL_PACKAGES, so updates come only from Bazaar.
    // A fetch failure is silent: no popup, the app works normally.
    // "Later" remembers the dismissed version, so it does not nag on
    // every open; a forced update has no "Later" button at all.

    private fun checkForUpdate() {
        Thread {
            val info = RemoteUpdate.fetch() ?: return@Thread
            if (info.latestVersionCode <= currentVersionCode()) return@Thread
            if (!info.forced) {
                val dismissed = getSharedPreferences("aramis_prefs", MODE_PRIVATE)
                    .getInt("update_dismissed_version", -1)
                if (dismissed == info.latestVersionCode) return@Thread
            }
            runOnUiThread { showBazaarUpdateDialog(info) }
        }.start()
    }

    private fun currentVersionCode(): Int {
        return try {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0).versionCode
        } catch (_: Exception) {
            0
        }
    }

    private fun currentVersionName(): String {
        return try {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0).versionName ?: "0.0.0"
        } catch (_: Exception) {
            "0.0.0"
        }
    }

    private fun toFaDigits(s: String): String {
        val fa = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')
        return s.map { if (it in '0'..'9') fa[it - '0'] else it }.joinToString("")
    }

    private fun showBazaarUpdateDialog(info: RemoteUpdate.UpdateInfo) {
        if (isFinishing || isDestroyed) return
        val dialog = Dialog(this)
        updateDialog = dialog
        dialog.setContentView(R.layout.dialog_update_bazaar)
        dialog.setCancelable(!info.forced)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        val versionLabel = info.versionName.ifEmpty {
            info.latestVersionCode.toString()
        }
        dialog.findViewById<TextView>(R.id.bazaar_update_version).text =
            "نسخه ${toFaDigits(versionLabel)}"
        dialog.findViewById<TextView>(R.id.bazaar_update_message).text =
            info.message.trim().take(400)
        val downloadBtn = dialog.findViewById<Button>(R.id.btn_bazaar_download)
        val laterBtn = dialog.findViewById<Button>(R.id.btn_bazaar_later)
        if (info.forced) {
            laterBtn.visibility = View.GONE
        } else {
            laterBtn.setOnClickListener {
                // Remember the dismissed version so the popup does not
                // nag on every open; a newer version still shows.
                getSharedPreferences("aramis_prefs", MODE_PRIVATE)
                    .edit()
                    .putInt("update_dismissed_version", info.latestVersionCode)
                    .apply()
                dialog.dismiss()
            }
        }
        downloadBtn.setOnClickListener {
            openExternal(info.bazaarUrl)
            dialog.dismiss()
        }
        dialog.setOnDismissListener { updateDialog = null }
        dialog.show()
    }

    // Real backdrop blur while the drawer is open (API 31+): the content
    // behind the frosted drawer panel is blurred; cleared on close so
    // the WebView is never left blurred.
    private fun setContentBlur(blur: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val content = findViewById<View>(R.id.main_content) ?: return
        try {
            content.setRenderEffect(
                if (blur) RenderEffect.createBlurEffect(18f, 18f, Shader.TileMode.CLAMP)
                else null
            )
        } catch (_: Exception) {
            // RenderEffect unsupported on this device; the drawer scrim
            // and its frosted fill still apply.
        }
    }

    private fun loadUrl(url: String) {
        webView.loadUrl(url)
    }

    // Pink heartbeat pulse on the deals countdown chip: a slow lub-dub
    // (scale + alpha) repeating about once every 1.6s — one double beat,
    // then a rest, so it reads as a heartbeat rather than a blink.
    // Runs only while the chip is visible; the once-a-second countdown
    // tick only sets the text, so it never fights the animation.
    // Transform-only animation on one small view: cheap on battery.
    private fun setDealsChipPulse(chip: TextView, running: Boolean) {
        if (running) {
            if (dealsChipPulse?.isRunning == true) return
            // Heartbeat keyframes: beat (up-down), smaller beat, rest.
            val scaleX = PropertyValuesHolder.ofKeyframe(
                "scaleX",
                Keyframe.ofFloat(0f, 1f),
                Keyframe.ofFloat(0.08f, 1.12f),
                Keyframe.ofFloat(0.16f, 1f),
                Keyframe.ofFloat(0.24f, 1.07f),
                Keyframe.ofFloat(0.32f, 1f),
                Keyframe.ofFloat(1f, 1f)
            )
            val scaleY = PropertyValuesHolder.ofKeyframe(
                "scaleY",
                Keyframe.ofFloat(0f, 1f),
                Keyframe.ofFloat(0.08f, 1.12f),
                Keyframe.ofFloat(0.16f, 1f),
                Keyframe.ofFloat(0.24f, 1.07f),
                Keyframe.ofFloat(0.32f, 1f),
                Keyframe.ofFloat(1f, 1f)
            )
            val alpha = PropertyValuesHolder.ofKeyframe(
                "alpha",
                Keyframe.ofFloat(0f, 1f),
                Keyframe.ofFloat(0.08f, 0.88f),
                Keyframe.ofFloat(0.16f, 1f),
                Keyframe.ofFloat(0.24f, 0.94f),
                Keyframe.ofFloat(0.32f, 1f),
                Keyframe.ofFloat(1f, 1f)
            )
            dealsChipPulse = ObjectAnimator.ofPropertyValuesHolder(chip, scaleX, scaleY, alpha).apply {
                duration = 1600
                repeatCount = ObjectAnimator.INFINITE
                start()
            }
        } else {
            dealsChipPulse?.cancel()
            dealsChipPulse = null
            chip.scaleX = 1f
            chip.scaleY = 1f
            chip.alpha = 1f
        }
    }

    /** Compact countdown for the deals drawer chip: «⏳ ۲ روز» or «⏳ ۱۲:۳۴:۵۶». */
    private fun formatDealsChip(millis: Long): String {
        val totalSeconds = millis / 1000
        val days = totalSeconds / 86400
        if (days > 0) return "⏳ ${toFaDigits(days.toString())} روز"
        val hours = (totalSeconds % 86400) / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return "⏳ ${toFaDigits("%02d:%02d:%02d".format(hours, minutes, seconds))}"
    }

    private fun openExternal(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
            // No app can handle it; ignore.
        }
    }

    override fun onDestroy() {
        dealsChipHandler?.removeCallbacksAndMessages(null)
        dealsChipPulse?.cancel()
        dealsChipPulse = null
        webView.destroy()
        super.onDestroy()
    }
}
