# Banaras Buzz — Android app

Native Android app (Kotlin + Jetpack Compose) for the Banaras Buzz news and events feed.

- Reads `news.json`, `events.json`, `manual-news.json`, `manual-events.json` and `hidden.json`
  from this repo's `main` branch, so anything the scraper or the web admin page publishes shows up in the app.
- Tabs: खबरें (news) and कार्यक्रम (events), with city / category filters, search, and an offline cache.
- Adding, deleting or hiding items is done on the web admin page (`admin.html`), not in the app.

## Get the APK

Every push that touches `android/` builds the app on GitHub Actions and publishes it to the
**apk-latest** release (Releases page of this repo). Open that page on your phone, download
`BanarasBuzz.apk`, and install it. You can also start a build by hand from Actions → "Build Android APK" → Run workflow.

## Build locally

Open the `android/` folder in Android Studio, or run `./gradlew assembleDebug`
(needs JDK 17 and the Android SDK, platform 35).
