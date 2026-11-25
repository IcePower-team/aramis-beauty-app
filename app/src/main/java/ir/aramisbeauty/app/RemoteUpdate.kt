package ir.aramisbeauty.app

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * In-app update notice as DATA, not as an app version.
 *
 * On every app open MainActivity fetches update.json from the repo —
 * the same raw.githubusercontent.com hosting as catalog.json, no
 * server of our own. When latest_version_code is higher than the
 * installed versionCode, a dialog offers the Cafe Bazaar page; the
 * old APK-download flow is gone (Bazaar rejects
 * REQUEST_INSTALL_PACKAGES), so the dialog only links out.
 * Every failure is silent: no popup, app works normally.
 */
object RemoteUpdate {

    private const val UPDATE_URL =
        "https://raw.githubusercontent.com/IcePower-team/aramis-beauty-app/main/update.json"
    const val DEFAULT_BAZAAR_URL = "https://cafebazaar.ir/app/ir.aramisbeauty.app"

    data class UpdateInfo(
        val latestVersionCode: Int,
        val versionName: String,
        val message: String,
        val bazaarUrl: String,
        val forced: Boolean
    )

    /** Download + parse; null on any failure (offline, non-200, bad JSON). */
    fun fetch(): UpdateInfo? {
        return try {
            val conn = URL(UPDATE_URL).openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", "AramisBeautyApp")
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            if (conn.responseCode != 200) return null
            val json = conn.inputStream.bufferedReader().use { it.readText() }
            parse(json)
        } catch (_: Exception) {
            null
        }
    }

    /** Parse + sanity-check: a broken file must never trigger a popup. */
    fun parse(json: String): UpdateInfo? {
        return try {
            val root = JSONObject(json)
            val code = root.optInt("latest_version_code", -1)
            if (code < 0) return null
            UpdateInfo(
                latestVersionCode = code,
                versionName = root.optString("version_name", ""),
                message = root.optString("message", "").trim()
                    .ifEmpty { "نسخه‌ی جدید آرامیس بیوتی منتشر شد! ✨" },
                bazaarUrl = root.optString("bazaar_url", "").trim()
                    .ifEmpty { DEFAULT_BAZAAR_URL },
                forced = root.optBoolean("forced", false)
            )
        } catch (_: Exception) {
            null
        }
    }
}
