package ir.aramisbeauty.app

import android.view.MotionEvent
import android.view.View

/**
 * Telegram-style liquid-glass press morph (v1.6.2): the tapped element
 * briefly shrinks to ~0.96 of its size and springs back on release —
 * a subtle "pressed water drop". No ripple rings, no color flash.
 *
 * The touch listener returns false, so the view's own click listener
 * still fires normally.
 */
fun View.applyPressMorph(scale: Float = 0.96f) {
    setOnTouchListener { v, event ->
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                v.animate().scaleX(scale).scaleY(scale).setDuration(90).start()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                v.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
            }
        }
        false
    }
}
