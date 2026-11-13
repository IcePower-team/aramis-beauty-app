package ir.aramisbeauty.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * The advisor's catalog as DATA, not as an app version.
 *
 * The app ships with a bundled snapshot (Catalog.products), but on every
 * advisor open it tries to download the latest catalog.json from the
 * repo — regenerated whenever a product is added, with NO APK rebuild.
 * The downloaded copy is cached, so the advisor also works offline with
 * the last good list. A download is only trusted when it parses and
 * holds a sane number of products; otherwise the bundled/cached list
 * stays in place.
 */
object RemoteCatalog {

    private const val CATALOG_URL =
        "https://raw.githubusercontent.com/IcePower-team/aramis-beauty-app/main/catalog.json"
    private const val PREFS = "aramis_catalog"
    private const val KEY_JSON = "catalog_json"

    /** Load the cached remote catalog, if any. */
    fun loadCached(context: Context): List<Catalog.ShopProduct>? {
        val json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_JSON, null) ?: return null
        return parse(json)
    }

    /** Download the latest catalog; saves it to cache on success. */
    fun fetch(context: Context): List<Catalog.ShopProduct>? {
        val json = download() ?: return null
        val products = parse(json) ?: return null
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_JSON, json).apply()
        return products
    }

    private fun download(): String? {
        return try {
            val conn = URL(CATALOG_URL).openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", "AramisBeautyApp")
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            if (conn.responseCode != 200) return null
            conn.inputStream.bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            null
        }
    }

    /** Parse + sanity-check: a broken or empty file must never wipe the catalog. */
    fun parse(json: String): List<Catalog.ShopProduct>? {
        return try {
            val root = JSONObject(json)
            val arr: JSONArray = root.getJSONArray("products")
            if (arr.length() < 100) return null
            val out = ArrayList<Catalog.ShopProduct>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val kws = LinkedHashSet<String>()
                val kwArr = o.optJSONArray("keywords")
                if (kwArr != null) {
                    for (j in 0 until kwArr.length()) kws.add(kwArr.getString(j))
                }
                out.add(
                    Catalog.ShopProduct(
                        id = o.getInt("id"),
                        name = o.getString("name"),
                        priceToman = o.getInt("price"),
                        imageUrl = o.getString("image"),
                        category = "site",
                        tags = emptySet(),
                        keywords = kws,
                        inStock = o.optBoolean("inStock", true)
                    )
                )
            }
            out
        } catch (_: Exception) {
            null
        }
    }
}
