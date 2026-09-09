# SearchAI Android App Design

## Goal
Build SearchAI as an installable Android phone application and produce a debug APK through GitHub Actions.

## Product experience
SearchAI opens to a phone-first screen with a large “What are you looking for?” field and a “Search Everywhere” action. Searches can be routed to useful web sources, results appear as mobile cards, and the app shows clear source states such as Searching, Learning this site, Needs your help, and Unavailable today.

## Architecture
Use Kotlin and Jetpack Compose for the native Android UI. Use Android WebView only when a website needs a browser/session or human interaction. Keep source definitions, search routing, URL construction, structural learning records, and update preferences in small Kotlin components so the system can evolve without tying the UI directly to individual sites.

The first APK is local-first and does not require a paid backend. It stores settings and non-sensitive learning metadata on-device. Site sessions stay inside Android WebView storage. Search queries, cookies, passwords, authorization data, session IDs, and CAPTCHA answers are never included in learned structural records.

## Search flow
1. User enters a natural-language search.
2. SearchPlanner selects General Web plus relevant sources.
3. SourceRegistry builds safe search URLs.
4. SearchActivity opens sources through the Android browser layer and presents navigable source/result cards.
5. If verification is detected, SearchAI opens the site in a visible WebView and asks the user to complete it manually.
6. After the user taps Continue, SearchAI resumes the source flow.
7. Structural failures can create sanitized local learning records for later adapter updates.

## Initial sources
General Web, KSL, Facebook Marketplace, Autotrader, CarGurus, Zillow, Redfin, OfferUp, Craigslist, eBay, Mercari, Depop, LinkedIn, Nextdoor, StubHub, and SeatGeek.

## Safety boundaries
SearchAI does not solve or bypass CAPTCHA or identity verification. It does not rotate fingerprints/proxies to evade anti-bot systems. It does not automatically buy, bid, pay, message, delete, or perform other consequential actions. Web content cannot change app permissions or safety rules.

## Updates
The app exposes Every launch, Daily, Weekly, and Ask me first. The first build supports local catalog/update metadata. A future signed remote catalog can use the same preference model without changing the UI contract.

## Build and testing
The project uses Gradle and GitHub Actions on ubuntu-latest. Unit tests cover source selection, URL generation, sanitization, and update preferences. The workflow runs tests, builds a debug APK, and uploads the APK as a GitHub artifact. The APK is intended for direct sideload testing on Android phones; Play Store signing/release packaging is a later step.