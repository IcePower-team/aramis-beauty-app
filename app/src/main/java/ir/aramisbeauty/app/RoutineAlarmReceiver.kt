package ir.aramisbeauty.app

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import org.json.JSONObject
import java.util.Calendar

/**
 * Daily beauty-routine reminder, fully on-device (no server).
 *
 * The routine (steps + daily time) lives in SharedPreferences
 * ("aramis_routine"). [schedule] registers a daily inexact alarm;
 * [cancel] removes it. The alarm is inexact on purpose — a beauty
 * reminder does not need exact-alarm permission. After a reboot the
 * alarm is gone, so BOOT_COMPLETED re-registers it.
 *
 * The notification lists the routine's steps and opens the routine
 * screen, where every step links to its product page.
 */
class RoutineAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> schedule(context)
            ACTION_REMIND -> showReminder(context)
        }
    }

    private fun showReminder(context: Context) {
        val routine = loadRoutine(context) ?: return
        if (!routine.enabled || routine.steps.isEmpty()) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE)
            as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "یادآوری روتین زیبایی",
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }
        val openIntent = Intent(context, RoutineActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(
            context, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stepsText = routine.steps.take(3).mapIndexed { i, step ->
            "${faDigits(i + 1)}. ${step.title}"
        }.joinToString("، ")
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("وقت روتین زیبایی‌ته ✨")
            .setContentText(stepsText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(stepsText))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        nm.notify(NOTIFICATION_ID, notification)
    }

    private fun faDigits(value: Int): String =
        value.toString().map { c ->
            if (c in '0'..'9') "۰۱۲۳۴۵۶۷۸۹"[c - '0'] else c
        }.joinToString("")

    /** One routine step: a catalog product + the label shown for it. */
    data class Step(val productId: Int, val title: String)

    /** The saved routine: daily time + steps. */
    data class Routine(
        val hour: Int,
        val minute: Int,
        val enabled: Boolean,
        val steps: List<Step>
    )

    companion object {
        const val ACTION_REMIND = "ir.aramisbeauty.app.ACTION_ROUTINE_REMINDER"
        const val CHANNEL_ID = "aramis_routine"
        private const val NOTIFICATION_ID = 9001
        private const val PREFS = "aramis_routine"
        private const val KEY_JSON = "routine_json"
        private const val REQUEST_CODE = 9002

        /** Load the saved routine, or null when none was ever saved. */
        fun loadRoutine(context: Context): Routine? {
            val json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_JSON, null) ?: return null
            return try {
                val root = JSONObject(json)
                val arr = root.getJSONArray("steps")
                val steps = ArrayList<Step>(arr.length())
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    steps.add(Step(o.getInt("product_id"), o.getString("title")))
                }
                Routine(
                    hour = root.getInt("hour"),
                    minute = root.getInt("minute"),
                    enabled = root.optBoolean("enabled", true),
                    steps = steps
                )
            } catch (_: Exception) {
                null
            }
        }

        /** Persist the routine (null clears it) and re-schedule the alarm. */
        fun saveRoutine(context: Context, routine: Routine?) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (routine == null) {
                prefs.edit().remove(KEY_JSON).apply()
            } else {
                val root = JSONObject().apply {
                    put("hour", routine.hour)
                    put("minute", routine.minute)
                    put("enabled", routine.enabled)
                    val arr = org.json.JSONArray()
                    routine.steps.forEach { step ->
                        arr.put(JSONObject().apply {
                            put("product_id", step.productId)
                            put("title", step.title)
                        })
                    }
                    put("steps", arr)
                }
                prefs.edit().putString(KEY_JSON, root.toString()).apply()
            }
            schedule(context)
        }

        /** Register (or re-register) the daily alarm for the saved routine;
         * cancels it when there is no routine or it is disabled/empty. */
        fun schedule(context: Context) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pi = pendingIntent(context)
            val routine = loadRoutine(context)
            if (routine == null || !routine.enabled || routine.steps.isEmpty()) {
                am.cancel(pi)
                return
            }
            val first = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, routine.hour)
                set(Calendar.MINUTE, routine.minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= System.currentTimeMillis()) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }
            am.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                first.timeInMillis,
                AlarmManager.INTERVAL_DAY,
                pi
            )
        }

        fun cancel(context: Context) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.cancel(pendingIntent(context))
        }

        private fun pendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, RoutineAlarmReceiver::class.java).apply {
                action = ACTION_REMIND
            }
            return PendingIntent.getBroadcast(
                context, REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
