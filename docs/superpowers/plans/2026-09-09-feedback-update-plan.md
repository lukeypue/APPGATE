# Feedback + Update Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add privacy-filtered tester export logs and a manual update checker to the Android AI Browser.

**Architecture:** `AppEventLog` owns structured local event storage and JSON export. `UpdateChecker` parses/fetches the update manifest. `MainActivity` only records events and exposes the two user-facing buttons.

**Tech Stack:** Kotlin, Android SharedPreferences, Android document picker, HttpURLConnection, org.json, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-09-09-feedback-update-design.md`

## Global Constraints
- Android app, no external backend required for this test build.
- versionCode 5 / versionName 2.1.0-ai-browser-testlog.
- Never log cookies, passwords, form contents, page body text, or URL query/fragment data.
- Update install remains user-confirmed by Android.

---

### Task 1: Event log model and privacy behavior
**Files:**
- Create: `app/src/test/java/com/appgate/tv/AppEventLogTest.kt`
- Create: `app/src/main/java/com/appgate/tv/AppEventLog.kt`

**Interfaces:**
- Produces: `sanitizeUrl(String): String`, `buildReport(List<BrowserEvent>, notes: String, versionName: String, versionCode: Int): String`

- [ ] Write failing tests proving query/fragment stripping and JSON report shape.
- [ ] Run unit tests and verify failure because production code is missing.
- [ ] Implement minimal event model/report builder.
- [ ] Run tests and verify pass.

### Task 2: Update manifest parser
**Files:**
- Create: `app/src/test/java/com/appgate/tv/UpdateCheckerTest.kt`
- Create: `app/src/main/java/com/appgate/tv/UpdateChecker.kt`
- Create: `update/latest.json`

**Interfaces:**
- Produces: `UpdateInfo`, `parseManifest(json: String): UpdateInfo`, `check(url: String, callback: (Result<UpdateInfo>) -> Unit)`

- [ ] Write failing parser tests.
- [ ] Run tests and verify failure.
- [ ] Implement parser and background HTTP fetch.
- [ ] Run tests and verify pass.

### Task 3: UI wiring and version bump
**Files:**
- Modify: `app/src/main/java/com/appgate/tv/MainActivity.kt`
- Modify: `app/build.gradle`

**Interfaces:**
- Consumes `AppEventLog` and `UpdateChecker`.

- [ ] Add `Export Test Log` and `Check Update` buttons.
- [ ] Record search/navigation/save/teach/import/update events without page bodies or secrets.
- [ ] Export JSON with optional tester notes via Android CreateDocument.
- [ ] Check manifest on demand and open the provided download page when a newer version exists.
- [ ] Bump version to code 5/name 2.1.0-ai-browser-testlog.
- [ ] Run the full GitHub Actions unit-test + assembleDebug workflow.
