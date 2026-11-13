package ir.aramisbeauty.app

import android.Manifest
import android.app.Dialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
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
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.widget.SearchView
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONObject
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessaging
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.drawerlayout.widget.DrawerLayout
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.navigation.NavigationView

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
        // In-app update: latest GitHub release of this app (public repo).
        const val UPDATE_CHECK_URL =
            "https://api.github.com/repos/IcePower-team/aramis-beauty-app/releases/latest"
        const val INSTALL_PERMISSION_REQUEST = 1001
    }

    private lateinit var drawer: DrawerLayout
    private lateinit var webView: WebView
    private lateinit var swipe: SwipeRefreshLayout
    private var headerView: View? = null
    private var updateDialog: Dialog? = null
    private var pendingApkFile: File? = null
    // Animated launch splash (shown while the site loads, then fades out)
    private var splashView: View? = null
    private var splashHidden = false
    private var splashOffline = false
    private var splashStartTime = 0L
    private val splashHandler = Handler(Looper.getMainLooper())
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

        val navView = findViewById<NavigationView>(R.id.nav_view)
        navView.setNavigationItemSelectedListener { item: MenuItem ->
            when (item.itemId) {
                R.id.nav_makeup_face -> loadUrl("https://aramis-beauty.ir/category/3")
                R.id.nav_makeup_eyes -> loadUrl("https://aramis-beauty.ir/category/4")
                R.id.nav_makeup_brows -> loadUrl("https://aramis-beauty.ir/category/5")
                R.id.nav_makeup_lips -> loadUrl("https://aramis-beauty.ir/category/6")
                R.id.nav_makeup_hair -> loadUrl("https://aramis-beauty.ir/category/7")
                R.id.nav_skincare -> loadUrl("https://aramis-beauty.ir/category/11")
                R.id.nav_lens -> loadUrl("https://aramis-beauty.ir/category/22")
                R.id.nav_bracelet -> loadUrl("https://aramis-beauty.ir/category/35")
                R.id.nav_necklace -> loadUrl("https://aramis-beauty.ir/category/36")
                R.id.nav_ring -> loadUrl("https://aramis-beauty.ir/category/38")
                R.id.nav_watch -> loadUrl("https://aramis-beauty.ir/category/49")
            }
            drawer.closeDrawers()
            true
        }

        // Fixed home / cart rows above the scrolling categories list
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
        // fresh page load in between.
        drawer.addDrawerListener(object : DrawerLayout.DrawerListener {
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {}
            override fun onDrawerOpened(drawerView: View) { checkLoginState() }
            override fun onDrawerClosed(drawerView: View) {}
            override fun onDrawerStateChanged(newState: Int) {}
        })

        // Social links at the bottom of the drawer
        findViewById<ImageButton>(R.id.social_telegram).setOnClickListener {
            openExternal(TELEGRAM_URL)
        }
        findViewById<ImageButton>(R.id.social_rubika).setOnClickListener {
            openExternal(RUBIKA_URL)
        }
        findViewById<ImageButton>(R.id.social_instagram).setOnClickListener {
            openExternal(INSTAGRAM_URL)
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

        // Check GitHub for a newer release; shows the update dialog only
        // when one exists (silent when offline or already up to date).
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
            else -> super.onOptionsItemSelected(item)
        }
    }

    // ---- In-app update (GitHub releases) ----

    private fun checkForUpdate() {
        Thread {
            try {
                val conn = (URL(UPDATE_CHECK_URL).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "AramisBeauty-App")
                    connectTimeout = 8000
                    readTimeout = 8000
                }
                if (conn.responseCode != HttpURLConnection.HTTP_OK) return@Thread
                val json = JSONObject(conn.inputStream.bufferedReader().readText())
                val tag = json.optString("tag_name", "").removePrefix("v")
                val notes = json.optString("body", "")
                var apkUrl: String? = null
                val assets = json.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        if (asset.optString("name") == "app-release.apk") {
                            apkUrl = asset.optString("browser_download_url")
                            break
                        }
                    }
                }
                if (tag.isEmpty() || apkUrl.isNullOrEmpty()) return@Thread
                if (compareVersions(tag, currentVersionName()) > 0) {
                    runOnUiThread { showUpdateDialog(tag, notes, apkUrl) }
                }
            } catch (_: Exception) {
                // Silent: no network / API down — just don't show the dialog.
            }
        }.start()
    }

    private fun currentVersionName(): String {
        return try {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0).versionName ?: "0.0.0"
        } catch (_: Exception) {
            "0.0.0"
        }
    }

    /** Returns > 0 when a is newer than b ("1.0.10" > "1.0.4"). */
    private fun compareVersions(a: String, b: String): Int {
        val pa = a.split(".").map { it.toIntOrNull() ?: 0 }
        val pb = b.split(".").map { it.toIntOrNull() ?: 0 }
        val n = maxOf(pa.size, pb.size)
        for (i in 0 until n) {
            val x = pa.getOrElse(i) { 0 }
            val y = pb.getOrElse(i) { 0 }
            if (x != y) return x.compareTo(y)
        }
        return 0
    }

    private fun toFaDigits(s: String): String {
        val fa = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')
        return s.map { if (it in '0'..'9') fa[it - '0'] else it }.joinToString("")
    }

    private fun showUpdateDialog(version: String, notes: String, apkUrl: String) {
        if (isFinishing || isDestroyed) return
        val dialog = Dialog(this)
        updateDialog = dialog
        dialog.setContentView(R.layout.dialog_update)
        dialog.setCancelable(true)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.findViewById<TextView>(R.id.update_version).text =
            "نسخه ${toFaDigits(version)}"
        val cleanNotes = notes.trim().take(400)
        dialog.findViewById<TextView>(R.id.update_notes).text =
            if (cleanNotes.isEmpty()) "بهبودها و رفع اشکال‌ها ✨" else cleanNotes
        val progressBar = dialog.findViewById<ProgressBar>(R.id.update_progress)
        val percentText = dialog.findViewById<TextView>(R.id.update_percent)
        val statusText = dialog.findViewById<TextView>(R.id.update_status)
        val downloadBtn = dialog.findViewById<Button>(R.id.btn_update_download)
        val laterBtn = dialog.findViewById<Button>(R.id.btn_update_later)
        // "Later" just closes the dialog; nothing is remembered, so the
        // next app launch shows the update again.
        laterBtn.setOnClickListener { dialog.dismiss() }
        downloadBtn.setOnClickListener {
            downloadBtn.isEnabled = false
            laterBtn.visibility = View.GONE
            progressBar.visibility = View.VISIBLE
            percentText.visibility = View.VISIBLE
            statusText.visibility = View.VISIBLE
            statusText.text = "⏳ در حال دانلود..."
            downloadUpdate(apkUrl, progressBar, percentText, statusText, downloadBtn)
        }
        dialog.setOnDismissListener { updateDialog = null }
        dialog.show()
    }

    private fun downloadUpdate(
        apkUrl: String,
        progressBar: ProgressBar,
        percentText: TextView,
        statusText: TextView,
        downloadBtn: Button
    ) {
        Thread {
            try {
                val conn = (URL(apkUrl).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "AramisBeauty-App")
                    connectTimeout = 10000
                    readTimeout = 15000
                }
                if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                    throw Exception("HTTP ${conn.responseCode}")
                }
                val total = conn.contentLengthLong
                val dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                val file = File(dir, "aramis-beauty-update.apk")
                conn.inputStream.use { input ->
                    FileOutputStream(file).use { output ->
                        val buf = ByteArray(8192)
                        var read: Int
                        var downloaded = 0L
                        while (input.read(buf).also { read = it } != -1) {
                            output.write(buf, 0, read)
                            downloaded += read
                            if (total > 0) {
                                val pct = (downloaded * 100 / total).toInt()
                                runOnUiThread {
                                    progressBar.progress = pct
                                    percentText.text = "٪${toFaDigits(pct.toString())}"
                                }
                            }
                        }
                        output.flush()
                    }
                }
                runOnUiThread {
                    statusText.text = "✅ دانلود کامل شد!"
                    percentText.text = "٪۱۰۰"
                    progressBar.progress = 100
                    installApk(file, statusText, downloadBtn)
                }
            } catch (_: Exception) {
                runOnUiThread {
                    statusText.text = "❌ دانلود ناموفق بود؛ دوباره امتحان کن"
                    downloadBtn.isEnabled = true
                    downloadBtn.text = "🔄 تلاش دوباره"
                }
            }
        }.start()
    }

    private fun installApk(file: File, statusText: TextView, downloadBtn: Button) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !packageManager.canRequestPackageInstalls()
        ) {
            // Android 8+: installing from the app needs the
            // "install unknown apps" permission first.
            pendingApkFile = file
            statusText.text = "برای نصب، اجازه‌ی نصب از منابع ناشناس رو بده ✅"
            downloadBtn.isEnabled = true
            downloadBtn.text = "⚙️ دادن اجازه و نصب"
            downloadBtn.setOnClickListener {
                startActivityForResult(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:$packageName")
                    ),
                    INSTALL_PERMISSION_REQUEST
                )
            }
            return
        }
        launchInstaller(file)
    }

    private fun launchInstaller(file: File) {
        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
            updateDialog?.dismiss()
        } catch (_: Exception) {
            // Installer couldn't open; the dialog stays so the user can retry.
        }
    }

    @Deprecated("Used for the install-unknown-apps permission result")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == INSTALL_PERMISSION_REQUEST) {
            val file = pendingApkFile
            if (file != null && file.exists() &&
                (Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                    packageManager.canRequestPackageInstalls())
            ) {
                launchInstaller(file)
            }
        }
    }

    private fun loadUrl(url: String) {
        webView.loadUrl(url)
    }

    private fun openExternal(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
            // No app can handle it; ignore.
        }
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
