package ir.aramisbeauty.app

import android.content.Context
import android.widget.Toast
import com.google.firebase.messaging.FirebaseMessaging

/**
 * "خبرم کن" (notify me): when the user is on a product page, they can
 * ask to be told when that product comes back in stock or its price
 * drops. Implemented with plain FCM topics — no server of our own
 * stores anything: subscribing joins product_<id>_stock and
 * product_<id>_price, and the checker script
 * (~/workspace/aramis-app/notify_checker.py) sends to those topics
 * when it sees a transition. Subscriptions are remembered locally so
 * the toolbar button can show the state and toggle it off.
 */
object NotifyMe {

    private const val PREFS = "aramis_notify"
    private const val KEY_SUBS = "subscriptions"

    fun stockTopic(id: Int) = "product_${id}_stock"
    fun priceTopic(id: Int) = "product_${id}_price"

    fun isSubscribed(context: Context, id: Int): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY_SUBS, emptySet())?.contains(id.toString()) == true
    }

    /** Toggle the subscription for a product; returns the new state. */
    fun toggle(context: Context, id: Int): Boolean {
        val subscribed = isSubscribed(context, id)
        if (subscribed) {
            FirebaseMessaging.getInstance().unsubscribeFromTopic(stockTopic(id))
            FirebaseMessaging.getInstance().unsubscribeFromTopic(priceTopic(id))
        } else {
            FirebaseMessaging.getInstance().subscribeToTopic(stockTopic(id))
            FirebaseMessaging.getInstance().subscribeToTopic(priceTopic(id))
        }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val set = prefs.getStringSet(KEY_SUBS, emptySet())?.toMutableSet()
            ?: mutableSetOf()
        if (subscribed) set.remove(id.toString()) else set.add(id.toString())
        prefs.edit().putStringSet(KEY_SUBS, set).apply()
        Toast.makeText(
            context,
            if (subscribed) "خبرم کن لغو شد"
            else "ثبت شد 🔔 وقتی موجود شد یا قیمتش اومد پایین خبرت می‌کنم",
            Toast.LENGTH_SHORT
        ).show()
        return !subscribed
    }
}
