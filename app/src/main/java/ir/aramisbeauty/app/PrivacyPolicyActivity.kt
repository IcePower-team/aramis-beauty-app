package ir.aramisbeauty.app

import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat

/**
 * Privacy policy — Cafe Bazaar requires the full policy text to be
 * readable inside the app (added in v1.6.3 for the Bazaar submission).
 */
class PrivacyPolicyActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_privacy)
        supportActionBar?.hide()
        // Light Telegram-style glass screen, like the quiz / advisor.
        window.statusBarColor = Color.parseColor("#FDF6F0")
        WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightStatusBars = true
        findViewById<View>(R.id.privacy_close).setOnClickListener { finish() }
    }
}
