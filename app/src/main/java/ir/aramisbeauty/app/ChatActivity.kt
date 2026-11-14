package ir.aramisbeauty.app

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import coil.load
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Beauty advisor chat: the user asks in Persian ("برای پوست چرب چی
 * خوبه؟", "یه رژ قرمز می‌خوام") and [AdvisorEngine] recommends up to
 * 3 products from the shop catalog. Recommendations show instantly
 * from the bundled catalog; availability + price are then re-checked
 * live in the background and any card that went out of stock gets a
 * "ناموجود" badge and loses its view button.
 */
class ChatActivity : AppCompatActivity() {

    private var quizFont: Typeface? = null
    private var quizFontBold: Typeface? = null

    private lateinit var messagesContainer: LinearLayout
    private lateinit var scrollView: ScrollView
    private lateinit var chipsContainer: LinearLayout
    private lateinit var input: EditText

    /** The catalog the advisor searches: bundled snapshot, replaced by
     * the downloaded catalog.json (cached) as soon as it is available,
     * so new products reach the advisor without an app update. */
    private var productList: List<Catalog.ShopProduct> = Catalog.products

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)
        supportActionBar?.hide()
        window.statusBarColor = Color.parseColor("#081828")
        quizFont = ResourcesCompat.getFont(this, R.font.vazirmatn)
        quizFontBold = ResourcesCompat.getFont(this, R.font.vazirmatn_bold)

        messagesContainer = findViewById(R.id.chat_messages)
        scrollView = findViewById(R.id.chat_scroll)
        chipsContainer = findViewById(R.id.chat_chips)
        input = findViewById(R.id.chat_input)

        findViewById<View>(R.id.chat_close).setOnClickListener { finish() }
        findViewById<View>(R.id.chat_send).setOnClickListener { sendCurrentInput() }
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendCurrentInput()
                true
            } else false
        }

        addTextMessage(
            "سلام! من مشاور زیبایی آرامیس‌م ✨ بگو چی دنبالشی — " +
                "مثلاً نوع پوستت، یه محصول خاص، یا موقعیتی که می‌خوای براش آماده شی.",
            isUser = false
        )
        setChips(AdvisorEngine.defaultChips)

        // Fresh catalog: the cached download first, then a background
        // refresh from the repo. New products appear in the advisor
        // without any app update; the bundled snapshot stays as fallback.
        RemoteCatalog.loadCached(this)?.let { productList = Catalog.merge(it) }
        Thread {
            RemoteCatalog.fetch(this)?.let { fresh ->
                productList = Catalog.merge(fresh)
            }
        }.start()
    }

    private fun sendCurrentInput() {
        val text = input.text.toString().trim()
        if (text.isEmpty()) return
        input.text.clear()
        sendMessage(text)
    }

    private fun sendMessage(text: String) {
        addTextMessage(text, isUser = true)
        val reply = AdvisorEngine.answer(text, productList)
        addTextMessage(reply.text, isUser = false)
        if (reply.products.isNotEmpty()) {
            reply.products.forEach { product ->
                messagesContainer.addView(makeProductCard(product))
            }
            verifyStockLive(reply.products)
            scrollToBottom()
        }
        if (reply.openQuiz) {
            startActivity(Intent(this, QuizActivity::class.java))
        }
    }

    /** Background live check: refresh price, badge out-of-stock cards.
     * Parallel (4 threads) so 20 cards don't take 20 sequential fetches. */
    private fun verifyStockLive(products: List<Catalog.ShopProduct>) {
        val pool = Executors.newFixedThreadPool(4)
        for (product in products) {
            pool.submit {
                val live = LiveCheck.fetch(product.id) ?: return@submit
                runOnUiThread {
                    val card = messagesContainer.findViewWithTag<View>(product.id) ?: return@runOnUiThread
                    card.findViewWithTag<TextView>("price")?.text = formatPrice(
                        live.priceToman ?: product.priceToman
                    )
                    if (!live.inStock) {
                        card.findViewWithTag<TextView>("badge")?.visibility = View.VISIBLE
                        card.findViewWithTag<AppCompatButton>("viewbtn")?.visibility = View.GONE
                    }
                }
            }
        }
        pool.shutdown()
    }

    private fun setChips(chips: List<String>) {
        chipsContainer.removeAllViews()
        chips.forEach { chipText ->
            val chip = TextView(this).apply {
                text = chipText
                setTextColor(ContextCompat.getColor(this@ChatActivity, R.color.gold))
                textSize = 13f
                typeface = quizFont
                background = ContextCompat.getDrawable(
                    this@ChatActivity, R.drawable.glass_chip
                )
                setPadding(dp(16), dp(10), dp(16), dp(10))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = dp(8) }
                setOnClickListener {
                    if (chipText == "کوئیز زیبایی") {
                        startActivity(Intent(this@ChatActivity, QuizActivity::class.java))
                    } else {
                        sendMessage(chipText)
                    }
                }
            }
            chipsContainer.addView(chip)
        }
    }

    private fun addTextMessage(text: String, isUser: Boolean) {
        val bubble = TextView(this).apply {
            this.text = text
            setTextColor(Color.WHITE)
            textSize = 15f
            typeface = quizFont
            background = ContextCompat.getDrawable(
                this@ChatActivity,
                if (isUser) R.drawable.chat_bubble_user
                else R.drawable.chat_bubble_advisor
            )
            setPadding(dp(14), dp(12), dp(14), dp(12))
            maxWidth = (resources.displayMetrics.widthPixels * 0.85).toInt()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = if (isUser) Gravity.END else Gravity.START
                bottomMargin = dp(8)
            }
        }
        messagesContainer.addView(bubble)
        scrollToBottom()
    }

    private fun makeProductCard(product: Catalog.ShopProduct): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = ContextCompat.getDrawable(
                this@ChatActivity, R.drawable.quiz_card_bg
            )
            setPadding(dp(12), dp(12), dp(12), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
            tag = product.id
        }
        val image = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(84), dp(84))
            background = ContextCompat.getDrawable(
                this@ChatActivity, R.drawable.quiz_image_bg
            )
            clipToOutline = true
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        image.load(product.imageUrl) {
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
            text = product.name
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = quizFontBold
            maxLines = 2
        }
        val price = TextView(this).apply {
            text = formatPrice(product.priceToman)
            setTextColor(ContextCompat.getColor(this@ChatActivity, R.color.gold))
            textSize = 14f
            typeface = quizFontBold
            tag = "price"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(4) }
        }
        val badge = TextView(this).apply {
            text = "ناموجود"
            setTextColor(Color.parseColor("#FF8A80"))
            textSize = 12f
            typeface = quizFont
            tag = "badge"
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(4) }
        }
        middle.addView(name)
        middle.addView(price)
        middle.addView(badge)
        card.addView(image)
        card.addView(middle)
        val viewButton = AppCompatButton(this).apply {
            text = "مشاهده"
            background = ContextCompat.getDrawable(
                this@ChatActivity, R.drawable.quiz_button_outline
            )
            backgroundTintList = null
            setTextColor(ContextCompat.getColor(this@ChatActivity, R.color.gold))
            textSize = 13f
            typeface = quizFontBold
            tag = "viewbtn"
            setPadding(dp(16), 0, dp(16), 0)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(40)
            ).apply { marginStart = dp(8) }
            setOnClickListener { openProduct(product.url) }
        }
        card.addView(viewButton)
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

    private fun scrollToBottom() {
        scrollView.post { scrollView.fullScroll(View.FOCUS_DOWN) }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun formatPrice(toman: Int): String {
        val grouped = String.format(Locale.US, "%,d", toman)
        val fa = grouped.map { c ->
            if (c in '0'..'9') "۰۱۲۳۴۵۶۷۸۹"[c - '0'] else c
        }.joinToString("")
        return "$fa تومان"
    }
}
