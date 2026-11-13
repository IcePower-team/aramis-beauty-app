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
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import coil.load
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Beauty quiz: "چی به من میاد؟"
 *
 * Four questions (skin type, skin tone, occasion, style) score a curated
 * catalog of live products and build a 3-piece look — one product from
 * three different categories. Skin tone only shapes the result text;
 * it never changes which products are picked (their shades were not
 * verified). Before the result is shown, each recommended product is
 * re-checked live (price + availability); an out-of-stock pick is
 * swapped for the next-best product of the same category.
 */
class QuizActivity : AppCompatActivity() {

    companion object {
        private const val CHROME_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0 Safari/537.36"
    }

    private val handler = Handler(Looper.getMainLooper())
    private var currentIndex = 0
    private val answers = mutableMapOf<Int, Int>() // question index -> option index
    private var advancing = false
    private var quizFont: Typeface? = null
    private var quizFontBold: Typeface? = null

    private lateinit var questionView: View
    private lateinit var loadingView: View
    private lateinit var resultView: ScrollView
    private lateinit var progressText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var questionTitle: TextView
    private lateinit var optionsContainer: LinearLayout
    private lateinit var backButton: AppCompatButton
    private lateinit var resultTitle: TextView
    private lateinit var resultDesc: TextView
    private lateinit var resultProducts: LinearLayout

    private data class LiveInfo(val name: String?, val priceToman: Int?, val inStock: Boolean)
    private data class ResultItem(
        val product: QuizData.QuizProduct,
        val liveName: String?,
        val livePriceToman: Int?,
        val inStock: Boolean? // null = live check failed, assume available
    ) {
        val displayName: String get() = liveName ?: product.name
        val displayPrice: Int get() = livePriceToman ?: product.priceToman
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quiz)
        supportActionBar?.hide()
        window.statusBarColor = Color.parseColor("#081828")
        quizFont = ResourcesCompat.getFont(this, R.font.vazirmatn)
        quizFontBold = ResourcesCompat.getFont(this, R.font.vazirmatn_bold)

        questionView = findViewById(R.id.quiz_question_view)
        loadingView = findViewById(R.id.quiz_loading_view)
        resultView = findViewById(R.id.quiz_result_view)
        progressText = findViewById(R.id.quiz_progress_text)
        progressBar = findViewById(R.id.quiz_progress)
        questionTitle = findViewById(R.id.quiz_question_title)
        optionsContainer = findViewById(R.id.quiz_options)
        backButton = findViewById(R.id.quiz_back)
        resultTitle = findViewById(R.id.quiz_result_title)
        resultDesc = findViewById(R.id.quiz_result_desc)
        resultProducts = findViewById(R.id.quiz_result_products)

        findViewById<View>(R.id.quiz_close).setOnClickListener { finish() }
        backButton.setOnClickListener {
            if (currentIndex > 0) {
                currentIndex--
                showQuestion(currentIndex)
            }
        }
        findViewById<AppCompatButton>(R.id.quiz_retake).setOnClickListener {
            answers.clear()
            currentIndex = 0
            resultView.visibility = View.GONE
            questionView.visibility = View.VISIBLE
            showQuestion(0)
        }
        findViewById<AppCompatButton>(R.id.quiz_done).setOnClickListener { finish() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (resultView.visibility == View.VISIBLE ||
                    loadingView.visibility == View.VISIBLE
                ) {
                    finish()
                } else if (currentIndex > 0) {
                    currentIndex--
                    showQuestion(currentIndex)
                } else {
                    finish()
                }
            }
        })

        showQuestion(0)
    }

    private fun showQuestion(index: Int) {
        advancing = false
        val question = QuizData.questions[index]
        progressText.text = "سؤال ${faDigits(index + 1)} از ${faDigits(QuizData.questions.size)}"
        progressBar.progress = index
        questionTitle.text = question.title
        backButton.visibility = if (index > 0) View.VISIBLE else View.GONE
        optionsContainer.removeAllViews()
        question.options.forEachIndexed { optionIndex, option ->
            optionsContainer.addView(makeOptionRow(option.text, optionIndex, index))
        }
    }

    private fun makeOptionRow(text: String, optionIndex: Int, questionIndex: Int): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = ContextCompat.getDrawable(
                this@QuizActivity, R.drawable.quiz_option_default
            )
            setPadding(dp(16), dp(16), dp(16), dp(16))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) }
            isClickable = true
            isFocusable = true
        }
        val dot = View(this).apply {
            background = ContextCompat.getDrawable(
                this@QuizActivity, R.drawable.quiz_dot_default
            )
            layoutParams = LinearLayout.LayoutParams(dp(12), dp(12))
            tag = "dot"
        }
        val label = TextView(this).apply {
            this.text = text
            setTextColor(Color.WHITE)
            textSize = 16f
            typeface = quizFont
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = dp(12) }
        }
        row.addView(dot)
        row.addView(label)
        row.setOnClickListener { selectOption(questionIndex, optionIndex) }
        return row
    }

    private fun selectOption(questionIndex: Int, optionIndex: Int) {
        if (advancing) return
        advancing = true
        answers[questionIndex] = optionIndex
        // Highlight the chosen row, dim the rest.
        for (i in 0 until optionsContainer.childCount) {
            val row = optionsContainer.getChildAt(i) as LinearLayout
            val chosen = i == optionIndex
            row.background = ContextCompat.getDrawable(
                this,
                if (chosen) R.drawable.quiz_option_selected
                else R.drawable.quiz_option_default
            )
            val dot = row.findViewWithTag<View>("dot")
            dot?.background = ContextCompat.getDrawable(
                this,
                if (chosen) R.drawable.quiz_dot_selected
                else R.drawable.quiz_dot_default
            )
        }
        handler.postDelayed({
            advancing = false
            if (questionIndex + 1 < QuizData.questions.size) {
                currentIndex = questionIndex + 1
                showQuestion(currentIndex)
            } else {
                finishQuiz()
            }
        }, 350)
    }

    private fun finishQuiz() {
        questionView.visibility = View.GONE
        resultView.visibility = View.GONE
        loadingView.visibility = View.VISIBLE
        val weights = mutableMapOf<String, Int>()
        answers.forEach { (qi, oi) ->
            QuizData.questions[qi].options[oi].weights.forEach { (tag, w) ->
                weights[tag] = (weights[tag] ?: 0) + w
            }
        }
        val scored = QuizData.score(weights)
        val look = QuizData.pickLook(scored)
        val occasionKey = answers[2]?.let { QuizData.questions[2].options[it].key }
        val styleKey = answers[3]?.let { QuizData.questions[3].options[it].key }
        val skinKey = answers[0]?.let { QuizData.questions[0].options[it].key }
        val toneKey = answers[1]?.let { QuizData.questions[1].options[it].key }
        val title = QuizData.profileTitle(occasionKey, styleKey)
        val desc = QuizData.profileDescription(skinKey, toneKey)

        Thread {
            val items = loadResultsWithLiveCheck(look, scored)
            handler.post { showResult(title, desc, items) }
        }.start()
    }

    /** Re-check the look live; swap any out-of-stock pick for the
     * next-best product of the same category (top 2 candidates checked). */
    private fun loadResultsWithLiveCheck(
        look: List<QuizData.QuizProduct>,
        scored: List<Pair<QuizData.QuizProduct, Int>>
    ): List<ResultItem> {
        val pool = Executors.newFixedThreadPool(4)
        try {
            val out = mutableListOf<ResultItem>()
            for (category in look.map { it.category }.distinct()) {
                val candidates = scored
                    .filter { it.first.category == category }
                    .take(2)
                    .map { it.first }
                val checks = candidates.map { p ->
                    p to pool.submit<LiveInfo?> { fetchLive(p.id) }
                }
                var picked: ResultItem? = null
                for ((product, future) in checks) {
                    val live = try {
                        future.get(8, TimeUnit.SECONDS)
                    } catch (e: Exception) {
                        null
                    }
                    if (live == null) {
                        // Check failed: keep the product with bundled data.
                        if (picked == null) picked = ResultItem(product, null, null, null)
                    } else if (live.inStock) {
                        picked = ResultItem(product, live.name, live.priceToman, true)
                        break
                    } else if (picked == null) {
                        // Out of stock: remember it only if nothing better shows up.
                        picked = ResultItem(product, live.name, live.priceToman, false)
                    }
                }
                out.add(picked ?: ResultItem(candidates.first(), null, null, null))
            }
            return out
        } finally {
            pool.shutdown()
        }
    }

    private fun fetchLive(pid: Int): LiveInfo? {
        return try {
            val conn = URL("https://aramis-beauty.ir/product/$pid")
                .openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", CHROME_UA)
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            if (conn.responseCode != 200) return null
            val html = conn.inputStream.bufferedReader(Charsets.UTF_8).readText()
            conn.disconnect()
            parseLive(html)
        } catch (e: Exception) {
            null
        }
    }

    private fun parseLive(html: String): LiveInfo? {
        val regex = Regex(
            """<script type="application/ld\+json">(.*?)</script>""",
            RegexOption.DOT_MATCHES_ALL
        )
        for (m in regex.findAll(html)) {
            val raw = m.groupValues[1].trim()
            val objects: List<JSONObject> = try {
                if (raw.startsWith("[")) {
                    val arr = JSONArray(raw)
                    (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
                } else {
                    listOf(JSONObject(raw))
                }
            } catch (e: Exception) {
                continue
            }
            for (obj in objects) {
                if (obj.optString("@type") != "Product") continue
                val offers = obj.optJSONObject("offers") ?: continue
                val avail = offers.optString("availability").substringAfterLast("/")
                val price = offers.optDouble("price", Double.NaN)
                val priceToman = if (!price.isNaN()) (price / 10).toInt() else null
                return LiveInfo(obj.optString("name"), priceToman, avail == "InStock")
            }
        }
        return null
    }

    private fun showResult(
        title: String,
        desc: String,
        items: List<ResultItem>
    ) {
        loadingView.visibility = View.GONE
        resultTitle.text = title
        resultDesc.text = desc
        resultProducts.removeAllViews()
        items.forEach { item -> resultProducts.addView(makeProductCard(item)) }
        resultView.visibility = View.VISIBLE
    }

    private fun makeProductCard(item: ResultItem): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = ContextCompat.getDrawable(
                this@QuizActivity, R.drawable.quiz_card_bg
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
                this@QuizActivity, R.drawable.quiz_image_bg
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
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = quizFontBold
            maxLines = 2
        }
        val price = TextView(this).apply {
            text = formatPrice(item.displayPrice)
            setTextColor(ContextCompat.getColor(this@QuizActivity, R.color.gold))
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
                setTextColor(Color.parseColor("#FF8A80"))
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
                    this@QuizActivity, R.drawable.quiz_button_outline
                )
                backgroundTintList = null
                setTextColor(ContextCompat.getColor(this@QuizActivity, R.color.gold))
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
}
