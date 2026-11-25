package ir.aramisbeauty.app

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.WindowCompat
import coil.load
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Flash deals ("پیشنهاد شگفت‌انگیز"): the products and the end time
 * come from deals.json in the repo (RemoteDeals), so both can change
 * without a new build. Prices and stock are NOT in that file — they
 * are re-checked live from the site at display time, exactly like the
 * quiz result cards. Tapping a product opens its site page in the
 * main WebView. When the countdown ends the list is hidden.
 */
class DealsActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private var quizFont: Typeface? = null
    private var quizFontBold: Typeface? = null

    private lateinit var countdownView: TextView
    private lateinit var productsContainer: LinearLayout

    private var config: RemoteDeals.DealsConfig? = null
    private var expired = false
    private var countdownRunning = false

    private val countdownTick = object : Runnable {
        override fun run() {
            val cfg = config ?: return
            val remaining = cfg.endsAtMillis - System.currentTimeMillis()
            if (remaining <= 0) {
                expired = true
                countdownView.text = "⏰ پیشنهاد تمام شد"
                productsContainer.removeAllViews()
                countdownRunning = false
                return
            }
            countdownView.text = formatRemaining(remaining)
            handler.postDelayed(this, 1000)
        }
    }

    private data class DealItem(
        val product: Catalog.ShopProduct,
        val liveName: String?,
        val livePriceToman: Int?,
        val inStock: Boolean? // null = live check failed, assume available
    ) {
        val displayName: String get() = liveName ?: product.name
        val displayPrice: Int get() = livePriceToman ?: product.priceToman
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_deals)
        supportActionBar?.hide()
        // Light Telegram-style glass screen, like the quiz/advisor.
        window.statusBarColor = Color.parseColor("#FDF6F0")
        WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightStatusBars = true
        quizFont = ResourcesCompat.getFont(this, R.font.vazirmatn)
        quizFontBold = ResourcesCompat.getFont(this, R.font.vazirmatn_bold)

        countdownView = findViewById(R.id.deals_countdown)
        productsContainer = findViewById(R.id.deals_products)
        findViewById<View>(R.id.deals_close).setOnClickListener { finish() }

        // Cached deals first, then a background refresh from the repo.
        val cached = RemoteDeals.loadCached(this)
        if (cached != null) showConfig(cached)
        Thread {
            val fresh = RemoteDeals.fetch(this)
            if (fresh != null) {
                handler.post {
                    if (fresh != config) showConfig(fresh)
                }
            } else if (cached == null) {
                handler.post {
                    countdownView.text = "به‌زودی پیشنهادهای تازه می‌رسند ✨"
                }
            }
        }.start()
    }

    private fun showConfig(cfg: RemoteDeals.DealsConfig) {
        config = cfg
        findViewById<TextView>(R.id.deals_title).text = "✦ ${cfg.title}"
        val products = resolveProducts(cfg)
        if (products.isEmpty()) {
            countdownView.text = "به‌زودی پیشنهادهای تازه می‌رسند ✨"
            return
        }
        // Show bundled data instantly; the live check refreshes
        // price/stock in the background right after.
        renderProducts(products.map { DealItem(it, null, null, null) })
        startCountdown()
        Thread { liveCheck(products) }.start()
    }

    /** Resolve the configured ids against the full catalog (bundled
     * snapshot merged with the downloaded catalog.json, like the
     * advisor); unknown ids are skipped, never shown as empty cards. */
    private fun resolveProducts(cfg: RemoteDeals.DealsConfig): List<Catalog.ShopProduct> {
        var list: List<Catalog.ShopProduct> = Catalog.products
        RemoteCatalog.loadCached(this)?.let { list = Catalog.merge(it) }
        val byId = list.associateBy { it.id }
        return cfg.productIds.mapNotNull { byId[it] }
    }

    private fun renderProducts(items: List<DealItem>) {
        productsContainer.removeAllViews()
        items.forEach { productsContainer.addView(makeProductCard(it)) }
    }

    private fun startCountdown() {
        if (countdownRunning) return
        countdownRunning = true
        handler.post(countdownTick)
    }

    private fun liveCheck(products: List<Catalog.ShopProduct>) {
        val pool = Executors.newFixedThreadPool(4)
        try {
            val futures = products.map { p ->
                p to pool.submit<LiveCheck.LiveInfo?> { LiveCheck.fetch(p.id) }
            }
            val items = futures.map { (product, future) ->
                val live = try {
                    future.get(8, TimeUnit.SECONDS)
                } catch (e: Exception) {
                    null
                }
                if (live == null) DealItem(product, null, null, null)
                else DealItem(product, live.name, live.priceToman, live.inStock)
            }
            handler.post { if (!expired) renderProducts(items) }
        } finally {
            pool.shutdown()
        }
    }

    private fun makeProductCard(item: DealItem): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = ContextCompat.getDrawable(
                this@DealsActivity, R.drawable.quiz_card_bg
            )
            setPadding(dp(12), dp(12), dp(12), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(12) }
        }
        val image = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(84), dp(84))
            background = ContextCompat.getDrawable(
                this@DealsActivity, R.drawable.quiz_image_bg
            )
            clipToOutline = true
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        image.load(item.product.imageUrl) {
            crossfade(true)
            placeholder(R.drawable.quiz_image_bg)
            error(R.drawable.quiz_image_bg)
        }
        val middle = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            ).apply { marginStart = dp(12) }
        }
        val name = TextView(this).apply {
            text = item.displayName
            setTextColor(ContextCompat.getColor(this@DealsActivity, R.color.burgundy_dark))
            textSize = 14f
            typeface = quizFontBold
            maxLines = 2
        }
        val price = TextView(this).apply {
            text = formatPrice(item.displayPrice)
            setTextColor(ContextCompat.getColor(this@DealsActivity, R.color.gold))
            textSize = 14f
            typeface = quizFontBold
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(4) }
        }
        middle.addView(name)
        middle.addView(price)
        if (item.inStock == false) {
            val badge = TextView(this).apply {
                text = "ناموجود"
                setTextColor(Color.parseColor("#C62828"))
                textSize = 12f
                typeface = quizFont
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(4) }
            }
            middle.addView(badge)
        }
        card.addView(image)
        card.addView(middle)
        if (item.inStock != false) {
            val viewButton = AppCompatButton(this).apply {
                text = "مشاهده"
                background = ContextCompat.getDrawable(
                    this@DealsActivity, R.drawable.quiz_button_outline
                )
                backgroundTintList = null
                setTextColor(ContextCompat.getColor(this@DealsActivity, R.color.gold))
                textSize = 13f
                typeface = quizFontBold
                setPadding(dp(16), 0, dp(16), 0)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, dp(40)
                ).apply { marginStart = dp(8) }
                setOnClickListener { openProduct(item.product.url) }
            }
            card.addView(viewButton)
        }
        return card
    }

    private fun openProduct(url: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse(url)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
    }

    private fun formatRemaining(millis: Long): String {
        val totalSeconds = millis / 1000
        val days = totalSeconds / 86400
        val hours = (totalSeconds % 86400) / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        val clock = "%02d:%02d:%02d".format(hours, minutes, seconds)
        return if (days > 0) {
            "⏳ ${faDigits(days.toInt())} روز و $clock مانده"
        } else {
            "⏳ $clock مانده"
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun faDigits(value: Int): String =
        value.toString().map { c ->
            if (c in '0'..'9') "۰۱۲۳۴۵۶۷۸۹"[c - '0'] else c
        }.joinToString("")

    private fun formatPrice(toman: Int): String {
        val grouped = String.format(Locale.US, "%,d", toman)
        val fa = grouped.map { c ->
            if (c in '0'..'9') "۰۱۲۳۴۵۶۷۸۹"[c - '0'] else c
        }.joinToString("")
        return "$fa تومان"
    }

    override fun onDestroy() {
        handler.removeCallbacks(countdownTick)
        super.onDestroy()
    }
}
