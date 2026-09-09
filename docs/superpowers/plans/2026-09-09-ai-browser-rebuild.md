# AI Browser Android Rebuild Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the old Fire TV/TikTok AppGate app with an installable Android AI browser that supports browsing, local knowledge search, site teaching, file import, and on-demand knowledge refresh.

**Architecture:** Keep one Android application module and package `com.appgate.tv`. `MainActivity` becomes the WebView/browser shell; a focused local persistence helper stores `KnowledgeEntry` and `SiteSkill` records; pure Kotlin search utilities keep ranking testable without Android runtime dependencies.

**Tech Stack:** Android SDK 34, Kotlin 1.9.24, Android Gradle Plugin 8.5.0, JDK/JVM 17, AndroidX AppCompat 1.7.0, SharedPreferences + org.json, WebView, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-09-09-ai-browser-design.md`

## Global Constraints
- Android phone/tablet first; remove Fire TV-specific launch behavior.
- Keep package/application ID `com.appgate.tv` for migration simplicity.
- Human-in-the-loop only for captcha/login challenges; no bypass automation.
- No paid AI API required for V1.
- Java/Kotlin compile target JVM 17.
- Knowledge remains local on device in V1.

---

### Task 1: Build configuration and manifest

**Files:**
- Modify: `app/build.gradle`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Produces: Android app configured for JVM 17 and phone/tablet launcher activity.

- [ ] **Step 1:** Change `compileOptions` to `JavaVersion.VERSION_17` and `kotlinOptions.jvmTarget` to `17`.
- [ ] **Step 2:** Remove leanback/landscape-only assumptions from the manifest, keep INTERNET permission, and label the app `AI Browser`.
- [ ] **Step 3:** Ensure only `.MainActivity` is required as the exported launcher activity.
- [ ] **Step 4:** Run `gradle testDebugUnitTest assembleDebug` in CI and verify configuration errors are absent.

### Task 2: Pure Kotlin knowledge models and search

**Files:**
- Create: `app/src/main/java/com/appgate/tv/KnowledgeModels.kt`
- Create: `app/src/main/java/com/appgate/tv/KnowledgeSearch.kt`
- Create: `app/src/test/java/com/appgate/tv/KnowledgeSearchTest.kt`

**Interfaces:**
- Produces: `data class KnowledgeEntry`, `data class SiteSkill`, `object KnowledgeSearch` with `search(query, entries, skills, limit)` and `normalizeHost(raw)`.

- [ ] **Step 1: Write failing tests** for title/content/source matching, site-skill matching, empty query, result limit, and host normalization.
- [ ] **Step 2: Run tests** and confirm failures before implementation.
- [ ] **Step 3: Implement minimal pure Kotlin ranking** using tokenized case-insensitive term scoring with title/host weighting.
- [ ] **Step 4: Run tests** and verify all pass.

### Task 3: Persistent knowledge store

**Files:**
- Create: `app/src/main/java/com/appgate/tv/KnowledgeStore.kt`

**Interfaces:**
- Consumes: `KnowledgeEntry`, `SiteSkill`.
- Produces: `getEntries()`, `upsertEntry(entry)`, `getSkills()`, `upsertSkill(skill)`, `skillForHost(host)`.

- [ ] **Step 1:** Implement JSON serialization/deserialization backed by `SharedPreferences`.
- [ ] **Step 2:** Make malformed stored JSON fail closed to empty lists rather than crash startup.
- [ ] **Step 3:** Cap stored entry content to 120,000 characters per item.

### Task 4: Browser shell and navigation

**Files:**
- Replace: `app/src/main/java/com/appgate/tv/MainActivity.kt`

**Interfaces:**
- Consumes: `KnowledgeStore`, `KnowledgeSearch`.
- Produces: visible URL/search bar, Back, Forward, Home, Refresh, Search, Teach, Upload, Update controls and WebView page display.

- [ ] **Step 1:** Build the browser layout programmatically to avoid new XML dependencies.
- [ ] **Step 2:** Configure WebView JavaScript, DOM storage, cookie support, mixed-content blocking, disabled file/content access, and external intent fallback.
- [ ] **Step 3:** Implement URL/domain detection and normal web-search fallback.
- [ ] **Step 4:** Surface saved site instructions when a learned hostname is visited.

### Task 5: Teach Site and unified local search

**Files:**
- Modify: `app/src/main/java/com/appgate/tv/MainActivity.kt`

**Interfaces:**
- Produces: `Teach Site` dialog and local result dialog.

- [ ] **Step 1:** Save/edit current-host navigation instructions using `KnowledgeStore.upsertSkill`.
- [ ] **Step 2:** Search stored entries and skills before web search.
- [ ] **Step 3:** Display local results with source/type labels and allow tapping source-backed items to navigate.

### Task 6: Upload Data and Update Knowledge

**Files:**
- Modify: `app/src/main/java/com/appgate/tv/MainActivity.kt`

**Interfaces:**
- Produces: Android document picker import and user-triggered web-source refresh.

- [ ] **Step 1:** Register `OpenDocument` and accept text-oriented MIME types.
- [ ] **Step 2:** Read imported text with a 500,000-character cap and store it as a `KnowledgeEntry(kind="upload")`.
- [ ] **Step 3:** Add `Save Page` action that extracts visible text from the current page into `KnowledgeEntry(kind="web")`.
- [ ] **Step 4:** Implement `Update Knowledge` over saved web-source URLs with bounded extraction and per-source timeout, reporting success/failure counts.

### Task 7: GitHub Actions and artifact verification

**Files:**
- Modify: `.github/workflows/build.yml`
- Modify: `README.md`

**Interfaces:**
- Produces: green CI and downloadable debug APK artifact named `AI-Browser-debug-apk`.

- [ ] **Step 1:** Keep setup on JDK 17/Gradle 8.7 and rename artifact/workflow to AI Browser.
- [ ] **Step 2:** Run `gradle testDebugUnitTest --stacktrace`.
- [ ] **Step 3:** Run `gradle assembleDebug --stacktrace`.
- [ ] **Step 4:** Upload `app-debug.apk` as `AI-Browser-debug-apk`.
- [ ] **Step 5:** Document V1 controls and known limits in README.
