# Aramis Beauty — Android app

Android app for the Aramis Beauty shop (Tabriz) — a WebView wrapper around
https://aramis-beauty.ir with a native navigation drawer and native
in-app features (quiz, beauty advisor, flash deals, routine reminders).

Current version: **1.6.9** (`versionCode` 33), published on Cafe Bazaar.
Package: `ir.aramisbeauty.app` — https://cafebazaar.ir/app/ir.aramisbeauty.app

## Features (v1.6.9)

- WebView of the shop site, with pull-to-refresh and in-app back navigation
- Splash screen with the Aramis logo (fades out once the page is ready,
  never later than 8 seconds); an offline state with retry when the site
  cannot be loaded
- Search in the toolbar (searches the site); cart and home shortcuts
- Navigation drawer (hamburger menu):
  - **ورود** / **عضویت** buttons (open the site's login / register pages);
    when logged in, a single "مشاهده پروفایل" button instead
  - Home, cart, quiz, advisor, flash deals, and routine rows
  - A collapsible "features" group (choice remembered)
  - A live countdown chip on the deals row while an offer is running
  - Social links at the bottom of the menu: Telegram channel
    (@AramisBeauty_Ch), Rubika group, Instagram page (@aramisbeauty.shop)
  - Privacy policy screen (full text readable in-app, as Cafe Bazaar
    requires)
  - Installed version shown at the bottom of the menu
- Payment pages stay inside the WebView (ZarinPal, SnappPay, Shaparak);
  other external links (Telegram, Rubika, Instagram, phone, mail) open
  in their own apps
- Deep links: `https://aramis-beauty.ir/...` links open in the app

### In-app update notice

On every app open, the app fetches `update.json` from this repo. When
`latest_version_code` is higher than the installed `versionCode`, a
dialog shows the new version and message, with a button that opens the
Cafe Bazaar page. No APK is downloaded by the app — updates come only
from Cafe Bazaar. A failed fetch is silent: no popup, the app works
normally. Tapping "Later" remembers the dismissed version, so the popup
does not nag on every open; a newer version still shows. A forced
update (`forced: true`) has no "Later" button at all.

### Flash deals — «پیشنهاد شگفت‌انگیز»

A native deals screen with a live per-second countdown. The product
list and the end time come from `deals.json` in this repo (see below),
so both can change without a new build. Prices and stock are **not**
in that file — they are re-checked live from the site when the screen
is shown, and tapping a product opens its page in the main WebView.
When the countdown ends, the list is hidden. The current offer
(at this commit) is five Dahab lenses (product ids 9–13), ending
11 Azar 1405, 20:30 Tehran time.

### «خبرم کن» (Notify Me)

On a product page, a bell button in the toolbar subscribes to that
product: when it comes back in stock or its price drops, the user is
notified. It uses plain Firebase Cloud Messaging topics
(`product_<id>_stock` and `product_<id>_price`) — no server of ours
stores anything — and the subscription is remembered on the device, so
the button shows the state and can toggle it off.

### Beauty routine reminders

The user builds one routine from catalog products (each step keeps its
product link) and picks a daily time (default 21:00). The app reminds
them every day at that time with a local notification listing the
routine's steps; tapping it opens the routine screen. Fully on-device:
the routine lives in SharedPreferences and the reminder is a daily
inexact alarm, re-registered after a reboot. No server is involved.

### Push notifications (Firebase Cloud Messaging)

Every install subscribes to the `all` topic, so a message sent to that
topic from the Firebase console reaches all app users. Tapping a
notification opens the app; a `url` data field deep-links to a shop
page (only `https://aramis-beauty.ir/` URLs are accepted). The
notification permission is requested on Android 13+.

### Beauty quiz — «چی به من میاد؟»

A native 4-question quiz (skin type, skin tone, occasion, style) that
builds a 3-product look from a curated pool of 17 products, one per
category. Before showing the result, each pick is re-checked live
(price + availability); an out-of-stock pick is swapped for the
next-best product of the same category. Skin tone only changes the
result text, never which products are picked.

### Beauty advisor chat

A native chat that answers in Persian and recommends products from the
shop catalog. It is rule-based and runs entirely on the phone — no
network, no AI. After showing a reply, it re-checks the recommended
products live (price + stock) and marks out-of-stock ones. It can hand
off to the quiz, and it understands budget requests (e.g. «زیر ۳۰۰
هزار تومان») and "cheapest" requests. The catalog it searches is the
bundled snapshot, refreshed from `catalog.json` in this repo whenever
the chat is opened, so new products reach the advisor without an app
update.

## Remote files (data, not app versions)

`update.json`, `deals.json`, and `catalog.json` live at the repo root
and are fetched from
`https://raw.githubusercontent.com/IcePower-team/aramis-beauty-app/main/<file>`.
Changing them changes the app's behaviour without a new build.
A download is only trusted when it parses and passes a sanity check;
otherwise the app keeps working with the bundled/cached data.

- `update.json` — `latest_version_code`, `version_name`, `message`,
  `bazaar_url`, `forced`. Set `latest_version_code` to the new
  versionCode when a release goes live on Bazaar; the message and the
  Bazaar link can be changed here at any time. `forced: true` means
  the user cannot dismiss the popup (only for very important updates).
- `deals.json` — `title`, `ends_at` (ISO-8601 UTC), `product_ids`
  (product ids on the site). The downloaded copy is cached, so the
  deals screen also works offline with the last good list.
- `catalog.json` — the advisor/quiz product catalog. The app ships a
  bundled snapshot and refreshes it from here; the cached copy is used
  offline.

## Build

- Android Gradle Plugin 8.5.2, Kotlin 2.0.20, Java 17, `compileSdk` /
  `targetSdk` 34, `minSdk` 24 (`app/build.gradle.kts`).
- Pushes to `main` build a signed release APK via GitHub Actions
  (`.github/workflows/build.yml`, JDK 17): the signing keystore is
  decoded from the `ANDROID_SIGNING_KEY` secret and the Firebase
  config (`app/google-services.json`, not in the repo) from the
  `FIREBASE_CONFIG` secret; then `./gradlew :app:assembleRelease`.
  The APK is uploaded as the `aramis-beauty-apk` artifact.
- Signing uses the personal keystore (secrets
  `ANDROID_SIGNING_KEY`, `ANDROID_SIGNING_STORE_PASSWORD`,
  `ANDROID_SIGNING_KEY_PASSWORD`, `ANDROID_SIGNING_KEY_ALIAS`) — the
  same personal key as the Hiddify fork, so updates install over the
  old version.
- Local build: `./gradlew :app:assembleRelease` (a release build needs
  `app/google-services.json` and the keystore environment variables
  `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`).
