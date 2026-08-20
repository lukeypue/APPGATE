# AppGate — TikTok web experience for Fire TV

AppGate is a lightweight, remote-friendly way to use the web version of TikTok on Fire TV. It opens directly into TikTok after a brief AppGate splash and adds TV controls around the normal website.

AppGate is independently branded. It is not TikTok, is not affiliated with or endorsed by TikTok, and does not copy, host, repackage, download, or proxy TikTok content.

## Version 1 scope

This first store candidate intentionally supports TikTok only. The app has no general URL bar, no custom-site launcher, and no other website tiles. The AppGate help screen says that more web services may be added later.

Verified on the test Fire TV:

- TikTok feed video playback works through the web experience.
- Direct hashtag pages such as `https://www.tiktok.com/tag/cats` work.
- Creator profiles and videos opened from profiles work.
- Comments can be opened/read.
- TikTok native-app/OneLink handoffs can strand Fire TV browsers on a blank page, so AppGate blocks those handoffs and keeps the viewer in the TV web experience.

Comment posting and every phone-app feature are **not** promised in v1.

## Remote controls

| Remote button | AppGate action |
|---|---|
| D-pad | Move the visible pointer |
| OK / Select | Click the item under the pointer |
| Channel Down | Next video/feed item |
| Channel Up | Previous video/feed item |
| Fast Forward | Next video/feed item |
| Rewind | Previous video/feed item |
| Play/Pause | Play or pause the most visible video |
| Back | Close keyboard/fullscreen, then previous web page, then exit |
| Menu | Open AppGate controls, Home, and Search |

Supporting both Channel Up/Down and FF/Rewind keeps navigation usable across Fire TV remote models.

## TV-friendly Search

TikTok's own web controls can sometimes try to open the phone app. AppGate therefore includes a small native TV search in the Menu overlay:

- `cats` or `#cats` -> `https://www.tiktok.com/tag/cats`
- `@username` -> `https://www.tiktok.com/@username`

This is deliberately simple and uses normal TikTok HTTPS pages that worked on the real Fire TV test device.

## Performance decisions

The Fire TV test showed that a sideloaded TikTok APK could interfere with browser playback. After removing it, Amazon Silk played TikTok much more smoothly. AppGate v1 therefore follows the lighter browser path:

- One WebView only.
- Amazon WebView's real/default user-agent instead of spoofing another Chrome build.
- No Compose launcher/runtime in the TikTok-only build.
- No background website preloading.
- No server, proxy, downloader, analytics SDK, or ad SDK.
- Camera and microphone web requests are denied.
- Videos pause when AppGate leaves the foreground.
- WebView renderer recovery is retained for low-memory situations.
- No global `100vh`/page scaling hacks; AppGate avoids changing the entire TikTok document geometry.

## AppGate identity

Launch flow:

1. Brief AppGate splash.
2. `TikTok web experience`.
3. `More web services coming later`.
4. TikTok opens automatically.

Menu always brings the viewer back to AppGate-branded controls/help, so the product is clearly an AppGate remote interface around a supported web service rather than an imitation native TikTok client.

## Build and tests

GitHub Actions runs JVM tests before creating the debug APK. The routing tests verify the known-good hashtag/profile paths and make sure native-app/OneLink handoffs are blocked.

To build manually:

```text
gradle testDebugUnitTest
gradle assembleDebug
```

The debug APK is for Fire TV testing only. A final Amazon Appstore submission still needs a release-signed build and final store metadata/artwork.

## First real-device test

1. Remove any sideloaded TikTok APK that can capture TikTok links.
2. Launch AppGate and confirm it goes straight to TikTok.
3. Watch at least 30 videos.
4. Test Channel Down/Up and FF/Rewind repeatedly.
5. Press Menu, search `cats`, open a video, then Back to the tag grid.
6. Search `@username` and open profile videos.
7. Click normal profile links from the feed.
8. Open/read comments.
9. Try a TikTok control that previously attempted a OneLink/native-app handoff; AppGate should stop the white-page escape and show a short message.
10. Confirm Back walks through TikTok page history and exits only when there is no previous page.
11. Leave AppGate, reopen it, and confirm the TikTok cookie/login session remains available.
12. Watch for the previously observed green-frame/audio-only failure during rapid video changes.

## Store positioning

The intended description is factual and independent: **AppGate provides a remote-friendly, app-like way to access the web version of TikTok on Fire TV.** AppGate branding and artwork should remain original, with no claim of official TikTok affiliation or endorsement.
