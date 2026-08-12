# AppGate — remote-native web apps for Fire TV

A Fire TV launcher that opens popular web apps (TikTok, Instagram, Reddit, X, and
more) with TV-native remote controls. No mouse. No sideloading other apps.

## Controls (inside any site)
| Button | NAV mode (default) | CURSOR mode |
|---|---|---|
| Up / Down | Previous / next video or post (feed sites), page scroll elsewhere | Move pointer |
| Left / Right | Horizontal scroll | Move pointer |
| Center / Select | Play–pause the visible video | Click (keyboard pops up on text fields) |
| Menu (☰) | Toggle cursor mode | Toggle back to NAV mode |
| Play/Pause | Play–pause video | same |
| Rewind / FF | Seek −10s / +10s | same |
| Back | Exit fullscreen video → page back → home screen | same |

## Home screen
- 12 preloaded tiles + "Add Site" for any URL
- Recent row auto-tracks your last 5 opened apps
- Hold Select on a tile: set as startup app, or remove (custom tiles)
- Settings: cursor speed, clear startup app

## Engineering notes (what's under the hood)
- Per-site profiles in `SiteCatalog.kt`: user agent (mobile vs desktop layout),
  feed-mode flag, and cleanup CSS that hides "get the app" nags. Site fixes are
  one-line edits.
- Feed snap: D-pad down dispatches an ArrowDown key event (TikTok desktop
  supports it natively) plus a viewport-height smooth scroll fallback that
  works everywhere.
- `onRenderProcessGone` recovery: if Chromium's renderer is killed under memory
  pressure (common on 1GB sticks), the WebView is rebuilt and the page restored
  instead of crashing the app.
- Fullscreen video handoff via `onShowCustomView`/`onHideCustomView` so HTML5
  fullscreen is a real edge-to-edge native state, with Back routed correctly.
- Cookies flushed on pause → users stay signed in across launches.

## Build
Open in Android Studio → Build > Build APK(s) → `app/build/outputs/apk/debug/`.

## Test on your Fire Stick
1. Settings > My Fire TV > Developer Options: enable ADB Debugging + Apps from
   Unknown Sources. (Hidden? About > click device name 7×.)
2. `adb connect STICK_IP:5555` then `adb install app-debug.apk`

## Before submitting to the Amazon Appstore
- Keep third-party names/logos out of the app name, icon, store description,
  and keywords. Describe it as a TV browser/launcher with remote-native
  controls for popular sites.
- Screenshots should show YOUR home screen and cursor.
- Test on a real stick: Amazon reviews remote navigation strictly.
- Sign a release build (Build > Generate Signed App Bundle/APK) before upload.

## Sensible roadmap (not in v1)
QR "continue on phone" handoff, reader mode for text sites, IAP Pro unlock
(Amazon IAP SDK), theme packs, pre-warmed favorite site.
