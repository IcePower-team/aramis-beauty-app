package ir.aramisbeauty.app

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Flash deals ("پیشنهاد شگفت‌انگیز") as DATA, not as an app version.
 *
 * The deals screen reads deals.json from the repo — the same
 * raw.githubusercontent.com hosting as catalog.json, no server of
 * our own. Product ids and the end time can change without a new
 * build; prices and stock are NOT in this file, they are re-checked
 * live from the site at display time. The downloaded copy is cached,
 * so the screen also works offline with the last good list. A
 * download is only trusted when it parses and holds at least one
 * product id and a valid end time.
 */
object RemoteDeals {

    private const val DEALS_URL =
        "https://raw.githubusercontent.com/IcePower-team/aramis-beauty-app/main/deals.json"
    private const val PREFS = "aramis_deals"
    private const val KEY_JSON = "deals_json"

    data class DealsConfig(
        val title: String,
        val endsAtMillis: Long,
        val productIds: List<Int>
    )

    /** Load the cached remote deals, if any. */
    fun loadCached(context: Context): DealsConfig? {
        val json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_JSON, null) ?: return null
        return parse(json)
    }

    /** Download the latest deals; saves it to cache on success. */
    fun fetch(context: Context): DealsConfig? {
        val json = download() ?: return null
        val config = parse(json) ?: return null
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_JSON, json).apply()
        return config
    }

    private fun download(): String? {
        return try {
            val conn = URL(DEALS_URL).openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", "AramisBeautyApp")
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            if (conn.responseCode != 200) return null
            conn.inputStream.bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            null
        }
    }

    /** Parse + sanity-check: a broken or empty file must never wipe the deals. */
    fun parse(json: String): DealsConfig? {
        return try {
            val root = JSONObject(json)
            // ISO-8601 UTC ("2026-12-02T17:00:27Z"). Parsed with
            // SimpleDateFormat on purpose: java.time needs API 26+ and
            // minSdk is 24.
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                .apply { timeZone = TimeZone.getTimeZone("UTC") }
            val endsAt = format.parse(root.getString("ends_at"))?.time
                ?: return null
            val arr = root.getJSONArray("product_ids")
            if (arr.length() == 0) return null
            val ids = ArrayList<Int>(arr.length())
            for (i in 0 until arr.length()) ids.add(arr.getInt(i))
            DealsConfig(
                title = root.optString("title", "پیشنهاد شگفت‌انگیز"),
                endsAtMillis = endsAt,
                productIds = ids
            )
        } catch (_: Exception) {
            null
        }
    }
}
