package ir.aramisbeauty.app

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Live product check against the shop site: reads a product page's
 * Product ld+json and returns its name, price (toman) and stock state.
 * Returns null when the page cannot be fetched or parsed (network
 * failure, non-200, missing schema) — callers treat that as "unknown"
 * and fall back to the bundled catalog data.
 *
 * The site 403s without a browser User-Agent, hence CHROME_UA.
 */
object LiveCheck {

    private const val CHROME_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0 Safari/537.36"

    data class LiveInfo(val name: String?, val priceToman: Int?, val inStock: Boolean)

    fun fetch(productId: Int): LiveInfo? {
        return try {
            val conn = URL("https://aramis-beauty.ir/product/$productId")
                .openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", CHROME_UA)
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            if (conn.responseCode != 200) return null
            val html = conn.inputStream.bufferedReader(Charsets.UTF_8).readText()
            conn.disconnect()
            parse(html)
        } catch (e: Exception) {
            null
        }
    }

    fun parse(html: String): LiveInfo? {
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
}
