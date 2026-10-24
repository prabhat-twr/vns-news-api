# Publishing Banaras Buzz on the Google Play Store

The signed Play Store bundle (`.aab`) is built by GitHub Actions. Your signing key never goes into the repo.

## 1. One-time: create your upload key (on your own computer)

You need Java installed (`keytool` comes with it).

```bash
keytool -genkeypair -v -keystore upload-keystore.jks -alias upload \
  -keyalg RSA -keysize 2048 -validity 10000
```

It asks for a password and some name details. Remember the **keystore password**, the **alias** (`upload`)
and the **key password**.

**Back up `upload-keystore.jks` and the passwords somewhere safe** (password manager plus a second copy).
Never commit the file to GitHub. If you enrol in Play App Signing (step 3) a lost upload key can be reset
through Play support, but it takes time.

## 2. One-time: give the key to GitHub as secrets

Encode the keystore as text:

```bash
base64 -w0 upload-keystore.jks          # Linux
base64 -i upload-keystore.jks | tr -d '\n'   # macOS
```

Then open the repo → **Settings → Secrets and variables → Actions → New repository secret** and add four secrets:

| Secret name | Value |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | the long base64 text from above |
| `ANDROID_KEYSTORE_PASSWORD` | your keystore password |
| `ANDROID_KEY_ALIAS` | `upload` |
| `ANDROID_KEY_PASSWORD` | your key password |

## 3. Build the signed bundle

Repo → **Actions → "Build signed Android App Bundle (Play Store)" → Run workflow**.
(Pushing a tag such as `v1.0.0` does the same.)
When it finishes, download the **BanarasBuzz-release-aab** artifact. Unzip it to get `app-release.aab`.

Each run uses the run number as the version code, so every upload is higher than the last one, which Play requires.
To change the visible version, edit `versionName` in `app/build.gradle.kts`.

## 4. Play Console setup

1. Create a Google Play developer account at <https://play.google.com/console> (one-time US$25 fee, identity verification required).
2. **Create app** → name `Banaras Buzz`, default language Hindi or English, type *App*, *Free*.
3. When you upload the first `.aab`, keep **Play App Signing** on (default). Your key becomes the *upload key*; Google holds the final signing key.
4. Store listing:
   - Short description, full description: see below.
   - App icon: `android/play-store/icon-512.png`
   - Feature graphic: `android/play-store/feature-graphic-1024x500.png`
   - Phone screenshots: at least 2, take them from the installed app (the Play Console shows the exact size limits).
5. **App content** section:
   - Privacy policy URL: `https://prabhat-twr.github.io/vns-news-api/privacy.html`
     (open it in a browser first to confirm it loads; it needs GitHub Pages on for this repo)
   - Ads: *No*. Target audience: 18+ or general, not designed for children.
   - Data safety: the app collects **no data** and shares none.
   - Content rating questionnaire: news app, no user-generated content.
   - News apps declaration: you will be asked whether it is a news app; answer yes.
6. **Production → Create new release** → upload `app-release.aab` → send for review.
   New personal developer accounts must first run a **closed test with at least 12 testers for 14 days** before production access is granted. Start this early.

## Suggested listing text

**Short description (80 characters max)**
- English: `Varanasi, Mirzapur and Jaunpur news and local events in one place.`
- Hindi: `वाराणसी, मिर्ज़ापुर और जौनपुर की खबरें और कार्यक्रम, एक ही जगह।`

**Full description**
```
Banaras Buzz brings the latest news and upcoming events from Varanasi, Mirzapur and Jaunpur into one simple app.

• Local news in Hindi, with city filters and a breaking-news view
• Upcoming events: religious, cultural and tourism, with dates and venues
• Search across news and events
• Last loaded stories stay available offline
• No sign-in, no ads, no tracking

Tap any story to read the full article on the publisher's website.
```

## Before you submit: content and copyright

The app shows headlines, short summaries and images that the feed collects from Dainik Jagran, Hindustan and Amar Ujala.
Play reviewers can reject apps that republish other publishers' material without permission, and publishers can file copyright complaints.
Safer options, from least to most work:

1. Show only the headline and a link to the original article (no summary text, no hotlinked images) for scraped stories.
2. Get written permission from the publishers.
3. Lead with content you write or license yourself (the admin page's manual news and events) and keep scraped items clearly attributed with links.

Also make sure the store listing does not imply the app is run by or affiliated with those publishers.
