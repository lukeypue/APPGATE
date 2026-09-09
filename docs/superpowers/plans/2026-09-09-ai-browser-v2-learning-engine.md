# AI Browser v2 Learning Engine Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace v1's raw-HTTP search with an Android WebView learning/replay engine that preserves learned site adapters, pauses for human verification, repairs changed selectors, and combines partial results from multiple sites.

**Architecture:** Pure-Java models and scoring logic remain Android-independent and unit-testable. Android WebView owns dynamic site execution and injects a semantic DOM bridge for teach/replay. SQLite stores adapters separately from the APK and migration code preserves learned knowledge. Search coordination returns per-site statuses and partial results rather than silently swallowing failures.

**Tech Stack:** Android Java, WebView/JavaScript bridge, SQLiteOpenHelper, org.json, JUnit 4, GitHub Actions/Gradle.

**Spec:** `docs/superpowers/specs/2026-09-09-ai-browser-v2-learning-engine-design.md`

## Global Constraints
- Android only; min SDK 26, target/compile SDK 35.
- Never solve or bypass CAPTCHA/human-verification challenges.
- Never store passwords, secure-field values, payment values, or CAPTCHA answers.
- Learned adapters persist across app upgrades and can be exported/imported without cookies or secrets.
- Fixed screen coordinates are forbidden for adapters; selectors are semantic with fallbacks.
- One broken site must not block combined results from working sites.
- APPGATE main branch remains untouched; work stays on `ai-browser-android-v1`.

---

### Task 1: Adapter model, locator scoring, bundled starter adapters

**Files:**
- Create: `app/src/main/java/com/aibrowser/knowledgehub/SiteAdapter.java`
- Create: `app/src/main/java/com/aibrowser/knowledgehub/SemanticLocator.java`
- Create: `app/src/main/java/com/aibrowser/knowledgehub/AdapterLibrary.java`
- Test: `app/src/test/java/com/aibrowser/knowledgehub/SemanticLocatorTest.java`
- Test: `app/src/test/java/com/aibrowser/knowledgehub/SiteAdapterTest.java`

**Interfaces:**
- Produces `SiteAdapter.toJson()`, `SiteAdapter.fromJson(String)`, `SemanticLocator.score(Candidate)`, and `AdapterLibrary.starters()`.
- Later tasks consume serialized adapters and locator scoring.

- [ ] Write tests asserting semantic attributes outrank CSS-only fallback and serialization round-trips action/result selectors.
- [ ] Run unit tests and confirm failure because the classes do not exist.
- [ ] Implement minimal immutable-ish data models, scoring, JSON serialization, and starter adapter metadata for Reddit, Pinterest, X, TikTok, Instagram, Facebook, Discord, and Snapchat.
- [ ] Run tests and confirm green.
- [ ] Commit Task 1.

### Task 2: Persistent adapter repository, migration, import/export

**Files:**
- Modify: `app/src/main/java/com/aibrowser/knowledgehub/KnowledgeDb.java`
- Create: `app/src/main/java/com/aibrowser/knowledgehub/AdapterRepository.java`
- Test: `app/src/test/java/com/aibrowser/knowledgehub/AdapterMergePolicyTest.java`

**Interfaces:**
- Produces `AdapterRepository.list()`, `getForDomain(String)`, `save(SiteAdapter)`, `mergeBundled(List<SiteAdapter>)`, `exportJson()`, and `importJson(String)`.
- Bundled merge keeps newer successful local/user-taught adapters over older bundled ones.

- [ ] Write failing tests for merge precedence and import/export purity (no cookies/secrets fields).
- [ ] Run tests to verify red.
- [ ] Upgrade SQLite schema with an `adapters` table and non-destructive `onUpgrade` migration.
- [ ] Implement repository merge/import/export behavior.
- [ ] Run tests and confirm green.
- [ ] Commit Task 2.

### Task 3: Teach session, challenge detection, and repair engine

**Files:**
- Create: `app/src/main/java/com/aibrowser/knowledgehub/TeachSession.java`
- Create: `app/src/main/java/com/aibrowser/knowledgehub/ChallengeDetector.java`
- Create: `app/src/main/java/com/aibrowser/knowledgehub/RepairEngine.java`
- Test: `app/src/test/java/com/aibrowser/knowledgehub/TeachSessionTest.java`
- Test: `app/src/test/java/com/aibrowser/knowledgehub/ChallengeDetectorTest.java`
- Test: `app/src/test/java/com/aibrowser/knowledgehub/RepairEngineTest.java`

**Interfaces:**
- `TeachSession.accept(ActionSnapshot)` rejects sensitive inputs and builds adapter steps.
- `ChallengeDetector.detect(String url,String title,String visibleText)` returns NONE/HUMAN_ACTION.
- `RepairEngine.best(SemanticLocator,List<Candidate>)` returns candidate + confidence band.

- [ ] Write failing tests for password/CAPTCHA exclusion, challenge detection, and changed-selector repair.
- [ ] Verify red.
- [ ] Implement minimal pure-Java logic.
- [ ] Verify green.
- [ ] Commit Task 3.

### Task 4: WebView automation engine and semantic DOM bridge

**Files:**
- Create: `app/src/main/java/com/aibrowser/knowledgehub/SemanticDomBridge.java`
- Create: `app/src/main/java/com/aibrowser/knowledgehub/BrowserAutomationEngine.java`
- Create: `app/src/main/java/com/aibrowser/knowledgehub/SearchResult.java`
- Create: `app/src/main/java/com/aibrowser/knowledgehub/SiteSearchCoordinator.java`
- Create: `app/src/main/java/com/aibrowser/knowledgehub/SynthesisEngine.java`
- Test: `app/src/test/java/com/aibrowser/knowledgehub/SynthesisEngineTest.java`

**Interfaces:**
- `BrowserAutomationEngine.search(WebView,SiteAdapter,String,Callback)` executes adapter steps in JavaScript and pauses on challenge.
- `SemanticDomBridge.javascript()` returns the injected observer/locator/extraction JS.
- `SiteSearchCoordinator` aggregates per-site `SearchResult` objects and preserves partial successes.
- `SynthesisEngine.combine(...)` deduplicates/group-ranks results without requiring an external LLM key.

- [ ] Write failing pure-Java synthesis/dedup tests first.
- [ ] Verify red.
- [ ] Implement result model and deterministic synthesis.
- [ ] Implement DOM bridge JavaScript and WebView replay state machine with explicit timeout/error statuses.
- [ ] Implement coordinator callback collection and partial-results behavior.
- [ ] Run unit tests and Gradle compile/build in CI.
- [ ] Commit Task 4.

### Task 5: v2 UI integration and human handoff

**Files:**
- Modify: `app/src/main/java/com/aibrowser/knowledgehub/MainActivity.java`
- Modify: `app/src/main/AndroidManifest.xml` only if required for document export/import behavior.

**Interfaces:**
- Main screen uses `AdapterRepository` and `SiteSearchCoordinator`.
- Teach mode injects `SemanticDomBridge`, records safe semantic actions, and tests the saved adapter.
- Human verification displays a persistent banner and resumes replay after the challenge disappears.

- [ ] Replace v1 site cards with READY/NEEDS UPDATE/NEEDS ATTENTION/HUMAN ACTION, source, confidence, last success, Update/Test/Teach-Repair.
- [ ] Replace raw `UnifiedSearch` usage with WebView-backed coordinator.
- [ ] Add `Import Learned Sites` and `Export Learned Sites` controls.
- [ ] Add starter adapter merge at startup.
- [ ] Ensure passwords/CAPTCHA answers are never passed into the teach bridge.
- [ ] Build via GitHub Actions and fix compile/test failures until green.
- [ ] Commit Task 5.

### Task 6: Verification and APK artifact

**Files:**
- Modify tests/workflow only if verification reveals a build-system issue.

- [ ] Run all unit tests in GitHub Actions.
- [ ] Build debug APK.
- [ ] Confirm artifact exists and is non-expired.
- [ ] Download artifact, extract APK, run ZIP/APK integrity check.
- [ ] Deliver the verified APK and source artifact to the user.
