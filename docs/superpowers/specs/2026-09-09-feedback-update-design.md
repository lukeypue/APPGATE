# AI Browser Feedback + Update Design

## Goal
Give testers two permanent controls before wider testing: **Export Test Log** and **Check Update**.

## Feedback/Test Log
The app keeps a local structured event log. Events include browser search, host navigation, save-page success/failure, taught-site changes, data import, knowledge refresh results, and user-entered improvement notes. Reports never include cookies, passwords, form contents, page bodies, or URL query/fragment data. Export produces a JSON document the tester can upload to the development team. Future server sync will reuse the same schema and be opt-in.

## Update Check
The app fetches a small public JSON manifest from the APPGATE GitHub repository. It compares `versionCode` against the installed build, displays release notes, and offers a button to open the current APK/build download page. Android remains responsible for the final install confirmation. The manifest URL is isolated so a future server can replace GitHub without redesigning the app.

## UI
Add `Export Test Log` and `Check Update` to the existing horizontal action bar. Export asks for optional notes, then opens Android's document save picker. Update runs on demand and reports whether the installed build is current.

## Version
This test build becomes versionCode 5 / versionName 2.1.0-ai-browser-testlog.
