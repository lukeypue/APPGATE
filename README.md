# AppGate 2.0 — remote-first web apps for Fire TV

AppGate is a lightweight TV browser and bookmark launcher. It opens the real
websites in one Amazon WebView and adds controls that work with a Fire TV remote.
It is not a TikTok, Instagram, or other third-party client and does not copy,
repackage, or host their content.

## What changed in 2.0

- TikTok uses its official landscape desktop website instead of pretending the
  Fire TV is a phone.
- Channel Down moves to the next video/post; Channel Up moves to the previous one.
- TikTok gets its native web Arrow Down/Arrow Up action first, then AppGate uses a
  lightweight feed-scroll fallback if the website ignores it.
- The D-pad always moves a visible cursor and OK clicks the pointed-to control.
- Clicking a comment control opens its editor and asks Fire TV to show the system
  keyboard. Search boxes and other text fields use the same keyboard path.
- Back closes the keyboard or fullscreen video first; the next Back returns
  directly to the AppGate launcher instead of getting trapped in website history.
- Fast Forward/Rewind seek the visible video by 10 seconds, and Play/Pause keeps
  its normal media meaning.
- Camera and microphone web requests are denied. AppGate only asks Android for
  Internet access.
- Video/audio pauses when AppGate leaves the foreground. A killed WebView renderer
  is rebuilt after low-memory pressure instead of crashing the app.
- Added Threads and Bluesky bookmarks. Snapchat now opens its actual web client.

## Remote controls inside a website

| Remote button | Action |
|---|---|
| D-pad arrows | Move the on-screen pointer |
| OK / Select | Click the control under the pointer |
| Channel Down | Next video/post on feed sites; page down elsewhere |
| Channel Up | Previous video/post on feed sites; page up elsewhere |
| Play/Pause | Play or pause the most visible video |
| Fast Forward | Seek forward 10 seconds |
| Rewind | Seek backward 10 seconds |
| Menu | Show the control reminder |
| Back | Close keyboard/fullscreen, then return to AppGate |

## Included website bookmarks

TikTok, Instagram, Reddit, YouTube TV, Snapchat Web, Pinterest, X, Facebook,
Discord, Twitch, GeForce NOW, Lemon8, Threads, and Bluesky are preloaded. Users
can add another normal `https://` website from the launcher.

These are website bookmarks, not a promise that every mobile-app feature will
work. The website owner controls sign-in, regional availability, DRM, account
features, and page layout. Important limitations:

- TikTok viewing, search, profiles, likes, and comments are the priority path.
  QR sign-in is usually the most practical TV sign-in method.
- A website may block sign-in inside embedded browsers. AppGate does not bypass
  CAPTCHAs, bot checks, account restrictions, or provider security rules.
- Snapchat camera features cannot work because AppGate deliberately requests no
  camera or microphone permission.
- GeForce NOW gameplay can require browser/DRM/gamepad capabilities that a
  particular Fire TV WebView does not expose.
- Roblox is not preloaded because its website normally hands game launch to the
  Roblox app; presenting the bookmark as a working TV game client would mislead
  users. BeReal's public website also does not currently replace its full app.

## Why the implementation stays light

- Only one WebView exists while browsing; pages are not preloaded in the background.
- Feed navigation inspects visible videos and their nearby parents instead of
  repeatedly scanning the entire page.
- AppGate has no server, proxy, downloader, analytics SDK, ad SDK, or content cache.
- Off-screen pre-rasterization is disabled and the in-memory web cache is released
  during critical memory pressure.

## Build

The delivered package includes `APK/AppGate-v2-debug.apk` for Fire TV testing.
It is debug-signed and is not the final Amazon Appstore release package.

The repository includes a GitHub Actions workflow. Push the project to GitHub,
open **Actions**, run **Build AppGate APK**, and download the
`AppGate-debug-apk` artifact. In Android Studio, use **Build > Build APK(s)**.

## Required real-device checks

Test on the oldest/lowest-memory Fire TV Stick you plan to support:

1. Launch AppGate using only the remote and open TikTok.
2. Use Channel Down for at least 20 videos, then Channel Up for 5.
3. Move the cursor to Comments, press OK, type with the Fire TV keyboard, dismiss
   the keyboard with Back, and verify the next Back returns to AppGate.
4. Test Play/Pause, Fast Forward, Rewind, fullscreen, and exit while video plays.
5. Sign in with TikTok QR login, force-stop AppGate, reopen it, and confirm the
   session remains signed in.
6. Browse for 15 minutes while watching Fire TV System X-Ray memory. Confirm the
   app remains responsive and audio stops after exit.
7. Repeat the cursor, keyboard, Back, and feed checks on every bookmark shown in
   the final store screenshots.

## Amazon Appstore boundary

Submit and describe AppGate as a general remote-controlled TV browser/launcher.
Do not use third-party logos, names, screenshots, or claims in the app icon, app
name, store title, keywords, or promotional images without permission. Amazon can
request proof of rights for third-party intellectual property. Passing technical
tests does not guarantee approval of an unauthorized third-party wrapper.

Useful primary references:

- [Amazon Fire TV remote input](https://developer.amazon.com/docs/fire-tv/remote-input.html)
- [Amazon Appstore Fire TV test criteria](https://developer.amazon.com/docs/app-testing/test-criteria.html)
- [Amazon WebView wrapper tutorial](https://developer.amazon.com/apps-and-games/blogs/2021/07/tutorial-touch-enable-web-apps-for-fire-tv)
- [TikTok desktop web product update](https://newsroom.tiktok.com/new-features-bring-tiktok-magic-to-desktop?lang=en)
