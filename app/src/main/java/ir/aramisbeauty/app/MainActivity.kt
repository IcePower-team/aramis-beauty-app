package ir.aramisbeauty.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import androidx.appcompat.widget.SearchView
import java.net.URLEncoder
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
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
        const val TELEGRAM_URL = "https://t.me/AramisBeauty_Ch"
        const val RUBIKA_URL = "https://rubika.ir/joing/HCIBADFA0JCEETLQHXTIJKEXVSMCNMYU"
        const val INSTAGRAM_URL = "https://www.instagram.com/aramisbeauty.shop"
        // Payment gateways open in the system browser: Zarinpal's page checks the
        // real browser environment (referer / user agent) and rejects embedded
        // WebViews with a domain error, so the gateway must not run inside the app.
        val PAYMENT_HOSTS = setOf("zarinpal.com", "www.zarinpal.com", "snapppay.ir", "www.snapppay.ir")
    }

    private lateinit var drawer: DrawerLayout
    private lateinit var webView: WebView
    private lateinit var swipe: SwipeRefreshLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        findViewById<ImageView>(R.id.toolbar_logo).setOnClickListener { loadUrl(HOME_URL) }

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
                return when {
                    host in PAYMENT_HOSTS || PAYMENT_HOSTS.any { host.endsWith(".$it") } -> {
                        openExternal(url)
                        true
                    }
                    host == "aramis-beauty.ir" || host.endsWith(".aramis-beauty.ir") -> {
                        false // stay inside the app
                    }
                    else -> {
                        openExternal(url)
                        true
                    }
                }
            }

            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                swipe.isRefreshing = true
            }

            override fun onPageFinished(view: WebView, url: String) {
                swipe.isRefreshing = false
            }
        }
        webView.webChromeClient = WebChromeClient()

        val navView = findViewById<NavigationView>(R.id.nav_view)
        navView.setNavigationItemSelectedListener { item: MenuItem ->
            when (item.itemId) {
                R.id.nav_home -> loadUrl(HOME_URL)
                R.id.nav_cart -> loadUrl(CART_URL)
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

        // Login / register buttons in the drawer header
        val header = navView.getHeaderView(0)
        header.findViewById<Button>(R.id.btn_login).setOnClickListener {
            loadUrl(LOGIN_URL)
            drawer.closeDrawers()
        }
        header.findViewById<Button>(R.id.btn_register).setOnClickListener {
            loadUrl(REGISTER_URL)
            drawer.closeDrawers()
        }

        // Social links at the bottom of the drawer
        navView.findViewById<ImageButton>(R.id.social_telegram).setOnClickListener {
            openExternal(TELEGRAM_URL)
        }
        navView.findViewById<ImageButton>(R.id.social_rubika).setOnClickListener {
            openExternal(RUBIKA_URL)
        }
        navView.findViewById<ImageButton>(R.id.social_instagram).setOnClickListener {
            openExternal(INSTAGRAM_URL)
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    drawer.isDrawerOpen(findViewById<View>(R.id.nav_view)) ->
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

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_cart -> {
                loadUrl(CART_URL)
                true
            }
            else -> super.onOptionsItemSelected(item)
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
