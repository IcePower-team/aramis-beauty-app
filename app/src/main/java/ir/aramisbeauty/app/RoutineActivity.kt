package ir.aramisbeauty.app

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.WindowCompat

/**
 * Beauty routine builder: the user picks products from the catalog
 * as routine steps (each step keeps its product link), chooses a
 * daily time, and the app reminds them every day at that time with
 * a local notification — no server involved. One routine, stored in
 * SharedPreferences; the alarm is (re-)scheduled by
 * [RoutineAlarmReceiver] on every save and on boot.
 */
class RoutineActivity : AppCompatActivity() {

    private var quizFont: Typeface? = null
    private var quizFontBold: Typeface? = null

    private lateinit var timeButton: AppCompatButton
    private lateinit var searchInput: EditText
    private lateinit var searchResults: LinearLayout
    private lateinit var stepsContainer: LinearLayout
    private lateinit var emptyView: TextView
    private lateinit var deleteButton: AppCompatButton
    private lateinit var statusView: TextView

    private var productList: List<Catalog.ShopProduct> = Catalog.products
    private var hour = 21
    private var minute = 0

    private data class StepRow(
        val productId: Int,
        val titleInput: EditText,
        val row: View
    )

    private val stepRows = ArrayList<StepRow>()

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_routine)
        supportActionBar?.hide()
        // Light Telegram-style glass screen, like the quiz/advisor.
        window.statusBarColor = Color.parseColor("#FDF6F0")
        WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightStatusBars = true
        quizFont = ResourcesCompat.getFont(this, R.font.vazirmatn)
        quizFontBold = ResourcesCompat.getFont(this, R.font.vazirmatn_bold)

        timeButton = findViewById(R.id.routine_time_button)
        searchInput = findViewById(R.id.routine_search)
        searchResults = findViewById(R.id.routine_search_results)
        stepsContainer = findViewById(R.id.routine_steps)
        emptyView = findViewById(R.id.routine_empty)
        deleteButton = findViewById(R.id.routine_delete)
        statusView = findViewById(R.id.routine_status)

        findViewById<View>(R.id.routine_close).setOnClickListener { finish() }
        timeButton.setOnClickListener { pickTime() }
        findViewById<AppCompatButton>(R.id.routine_save).setOnClickListener { saveRoutine() }
        deleteButton.setOnClickListener { deleteRoutine() }
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                showSearchResults(s?.toString().orEmpty())
            }
        })

        // Fresh catalog: the cached download first, then a background
        // refresh from the repo (same pattern as the advisor).
        RemoteCatalog.loadCached(this)?.let { productList = Catalog.merge(it) }
        Thread {
            RemoteCatalog.fetch(this)?.let { fresh ->
                productList = Catalog.merge(fresh)
            }
        }.start()

        RoutineAlarmReceiver.loadRoutine(this)?.let { routine ->
            hour = routine.hour
            minute = routine.minute
            routine.steps.forEach { step ->
                val product = productList.firstOrNull { it.id == step.productId }
                addStepRow(step.productId, step.title, product?.name)
            }
            updateTimeButton()
            updateStatus()
        } ?: updateTimeButton()
    }

    private fun pickTime() {
        TimePickerDialog(this, { _, h, m ->
            hour = h
            minute = m
            updateTimeButton()
        }, hour, minute, true).show()
    }

    private fun updateTimeButton() {
        timeButton.text = "ساعت ${faDigits(hour)}:${faDigits(minute).padStart(2, '۰')}"
    }

    private fun updateStatus() {
        val routine = RoutineAlarmReceiver.loadRoutine(this)
        if (routine != null && routine.enabled && routine.steps.isNotEmpty()) {
            statusView.text =
                "روتین فعال است — هر روز ساعت ${faDigits(routine.hour)}:" +
                    "${faDigits(routine.minute).padStart(2, '۰')} یادآوری می‌شود 🌸"
            deleteButton.visibility = View.VISIBLE
        } else {
            statusView.text = "روتینی ذخیره نشده است."
            deleteButton.visibility = View.GONE
        }
        emptyView.visibility = if (stepRows.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showSearchResults(query: String) {
        searchResults.removeAllViews()
        val q = normalize(query)
        if (q.length < 2) return
        val matches = productList
            .filter { normalize(it.name).contains(q) }
            .filter { p -> stepRows.none { it.productId == p.id } }
            .take(6)
        matches.forEach { product ->
            val row = TextView(this).apply {
                text = product.name
                setTextColor(ContextCompat.getColor(this@RoutineActivity, R.color.burgundy_dark))
                textSize = 14f
                typeface = quizFont
                setPadding(dp(12), dp(12), dp(12), dp(12))
                background = ContextCompat.getDrawable(
                    this@RoutineActivity, R.drawable.quiz_option_default
                )
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(6) }
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    addStepRow(product.id, product.name, product.name)
                    searchInput.text.clear()
                    searchResults.removeAllViews()
                }
            }
            row.applyPressMorph()
            searchResults.addView(row)
        }
    }

    private fun addStepRow(productId: Int, title: String, productName: String?) {
        if (stepRows.any { it.productId == productId }) return
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = ContextCompat.getDrawable(
                this@RoutineActivity, R.drawable.quiz_card_bg
            )
            setPadding(dp(12), dp(12), dp(12), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) }
        }
        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val number = TextView(this).apply {
            text = faDigits(stepRows.size + 1)
            setTextColor(ContextCompat.getColor(this@RoutineActivity, R.color.gold))
            textSize = 16f
            typeface = quizFontBold
        }
        val titleInput = EditText(this).apply {
            setText(title)
            setTextColor(ContextCompat.getColor(this@RoutineActivity, R.color.burgundy_dark))
            textSize = 14f
            typeface = quizFontBold
            background = null
            setSingleLine()
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            ).apply { marginStart = dp(8) }
        }
        val remove = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            background = null
            setColorFilter(ContextCompat.getColor(this@RoutineActivity, R.color.gray_text))
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
            contentDescription = "حذف قدم"
            setOnClickListener {
                stepRows.removeAll { it.row == row }
                stepsContainer.removeView(row)
                renumberSteps()
                updateStatus()
            }
        }
        topRow.addView(number)
        topRow.addView(titleInput)
        topRow.addView(remove)
        row.addView(topRow)
        // The product link under the step title.
        val link = TextView(this).apply {
            text = productName ?: "مشاهده محصول"
            setTextColor(ContextCompat.getColor(this@RoutineActivity, R.color.gold))
            textSize = 12f
            typeface = quizFont
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(4) }
            isClickable = true
            setOnClickListener { openProduct("https://aramis-beauty.ir/product/$productId") }
        }
        row.addView(link)
        stepsContainer.addView(row)
        stepRows.add(StepRow(productId, titleInput, row))
        updateStatus()
    }

    private fun renumberSteps() {
        // Step numbers are the first TextView child of each row's top row.
        stepsContainer.let { container ->
            for (i in 0 until container.childCount) {
                val row = container.getChildAt(i) as? LinearLayout ?: continue
                val top = row.getChildAt(0) as? LinearLayout ?: continue
                (top.getChildAt(0) as? TextView)?.text = faDigits(i + 1)
            }
        }
    }

    private fun saveRoutine() {
        if (stepRows.isEmpty()) {
            Toast.makeText(this, "اول یک قدم به روتین اضافه کن", Toast.LENGTH_SHORT).show()
            return
        }
        val steps = stepRows.map { row ->
            val title = row.titleInput.text.toString().trim()
                .ifEmpty { productList.firstOrNull { it.id == row.productId }?.name ?: "قدم" }
            RoutineAlarmReceiver.Step(row.productId, title)
        }
        RoutineAlarmReceiver.saveRoutine(
            this,
            RoutineAlarmReceiver.Routine(hour, minute, true, steps)
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        updateStatus()
        Toast.makeText(
            this,
            "روتین ذخیره شد 🌸 هر روز یادآوری می‌شه",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun deleteRoutine() {
        RoutineAlarmReceiver.saveRoutine(this, null)
        stepRows.clear()
        stepsContainer.removeAllViews()
        updateStatus()
        Toast.makeText(this, "روتین حذف شد", Toast.LENGTH_SHORT).show()
    }

    private fun openProduct(url: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse(url)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
    }

    private fun normalize(s: String): String =
        s.replace("\u200c", "").replace(" ", "")

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun faDigits(value: Int): String =
        value.toString().map { c ->
            if (c in '0'..'9') "۰۱۲۳۴۵۶۷۸۹"[c - '0'] else c
        }.joinToString("")
}
