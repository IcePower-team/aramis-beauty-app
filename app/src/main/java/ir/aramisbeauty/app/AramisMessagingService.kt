package ir.aramisbeauty.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Push notifications via Firebase Cloud Messaging.
 *
 * Every install joins the [TOPIC_ALL] topic, so a message sent to that
 * topic from the Firebase console reaches all app users. Tapping a
 * notification opens the app; a "url" data field deep-links to a shop page
 * (MainActivity handles it in onNewIntent).
 *
 * Note: notification messages sent from the console while the app is in
 * the background are shown by the system itself (onMessageReceived is not
 * called for those) — the manifest's default_notification_icon and
 * default_notification_channel_id make those render with our icon/channel.
 */
class AramisMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        FirebaseMessaging.getInstance().subscribeToTopic(TOPIC_ALL)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.data["title"]
            ?: message.notification?.title
            ?: "آرامیس بیوتی"
        val body = message.data["body"] ?: message.notification?.body ?: return
        showNotification(title, body, message.data["url"])
    }

    private fun showNotification(title: String, body: String, url: String?) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            url?.takeIf { it.startsWith("https://aramis-beauty.ir/") }?.let {
                data = Uri.parse(it)
            }
        }
        val pending = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "اطلاع‌رسانی آرامیس بیوتی",
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        nm.notify(System.currentTimeMillis().toInt(), notification)
    }

    companion object {
        const val CHANNEL_ID = "aramis_updates"
        const val TOPIC_ALL = "all"
    }
}
