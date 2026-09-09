# AI Browser for Android

This repository has been repurposed from the old AppGate Fire TV experiment into an Android phone/tablet AI-assisted browser.

## V1 features

- Normal WebView browser with address/search bar, back, forward, home and refresh.
- Local knowledge search before opening a normal web search.
- **Save Page** stores readable text from the current web page on-device.
- **Teach Site** stores navigation instructions for the current website and shows them again when that site is revisited.
- **Upload Data** imports text-oriented files such as TXT, Markdown, CSV, JSON and HTML into local browser knowledge.
- **Update Knowledge** revisits web pages you previously saved and refreshes their text snapshots.
- **Knowledge** shows how many items and learned sites are stored.
- All V1 knowledge is stored locally on the device with SharedPreferences/JSON.

## Captchas and login verification

Captcha and login challenges are intentionally human-in-the-loop. Complete them yourself in the browser. The app can remember site navigation notes around those steps, but it does not automate captcha solving or bypass anti-bot controls.

## Current limits

- V1 does not require or call a paid AI API yet.
- Binary documents such as PDF are not parsed by Upload Data yet.
- Some websites block text extraction or behave differently inside Android WebView; those pages may browse normally but may not save/update correctly.
- Update Knowledge is user-triggered and refreshes only pages previously saved with Save Page.

## Build

GitHub Actions uses JDK 17 and Gradle 8.7. Every push runs unit tests and builds a debug APK. When the workflow is green, download the `AI-Browser-debug-apk` artifact from the workflow run.

Core configuration:

- Android compile/target SDK: 34
- Minimum SDK: 22
- Java/Kotlin JVM target: 17
- Package/application ID: `com.appgate.tv`

## Branch used for rebuild

Development is taking place on `ai-browser-rebuild` so the previous main branch stays intact until the rebuild is verified.
