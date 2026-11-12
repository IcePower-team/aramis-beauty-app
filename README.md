# Aramis Beauty — Android app

Android app for the Aramis Beauty shop (Tabriz) — a WebView wrapper around
https://aramis-beauty.ir with a native navigation drawer.

## Features (v1.0.0)

- WebView of the shop site, with pull-to-refresh and in-app back navigation
- Splash screen with the Aramis logo
- Navigation drawer (hamburger menu):
  - **ورود** and **عضویت** buttons (open the site's login / register pages)
  - Home, cart, and the main shop categories
  - Social links at the bottom of the menu: Telegram channel
    (@AramisBeauty_Ch), Rubika group, Instagram page (@aramisbeauty.shop)
- External links (Telegram, Rubika, Instagram, phone, mail) open in their own apps

## Build

Pushes to `main` build a signed release APK via GitHub Actions
(`.github/workflows/build.yml`). Signing uses the personal keystore
(secrets `ANDROID_SIGNING_KEY`, `ANDROID_SIGNING_STORE_PASSWORD`,
`ANDROID_SIGNING_KEY_PASSWORD`, `ANDROID_SIGNING_KEY_ALIAS`) — the same
personal key as the Hiddify fork, so updates install over the old version.
