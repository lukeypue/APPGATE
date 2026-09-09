# AI Browser Android Rebuild Design

## Goal
Repurpose the disposable AppGate Android repository into a phone/tablet AI-assisted browser that can browse normal websites, retain user-provided knowledge, remember site-specific navigation instructions, and refresh learned sources on demand.

## Product Scope for V1
- Android phone/tablet app; no Fire TV-specific UX.
- Built-in WebView with URL/search bar, back, forward, refresh, and home controls.
- Unified search that checks locally learned knowledge/site skills and can open a normal web search.
- Persistent local knowledge store using SharedPreferences/JSON so learning survives app restarts.
- Teach Site action: user can save instructions for how to use the current site and later view them when returning to that host.
- Upload Data action: import text-oriented files through Android's document picker and add their contents to local knowledge.
- Update Knowledge action: revisit saved source URLs, extract readable page text, and update stored snapshots.
- Captcha handling is human-in-the-loop only. The app may remember that a site needs human verification and resume afterward, but it does not automate captcha solving or bypass anti-bot controls.
- No paid AI API is required for V1. Multi-model orchestration is deferred until the browser/memory layer is stable.

## Architecture
The app remains a single Android application module and keeps the existing package name `com.appgate.tv` to reduce migration risk. `MainActivity` becomes the browser shell and owns WebView/navigation UI. A small pure-Kotlin search layer ranks locally stored knowledge. A persistence helper serializes knowledge entries and site skills to JSON in SharedPreferences.

### Main components
1. `MainActivity.kt` — browser UI, WebView lifecycle, document picker, dialogs, source refresh.
2. `KnowledgeStore.kt` — persistent CRUD for knowledge entries and site skills.
3. `KnowledgeSearch.kt` — deterministic local search/ranking with no Android dependencies.
4. `KnowledgeModels.kt` — data models shared by storage/search/UI.
5. `KnowledgeSearchTest.kt` — JVM unit tests for ranking and host matching behavior.

## Data model
`KnowledgeEntry`: id, title, source, content, updatedAt, kind.
`SiteSkill`: host, instructions, updatedAt.

Knowledge is stored locally on device. No content is uploaded to a server in V1.

## Search flow
When the user submits text:
1. If it is a URL/domain, navigate directly.
2. Otherwise search local knowledge and site skills.
3. Show matching local results in a compact dialog.
4. Offer/open a normal web search for broader results.

## Teach flow
When a page is open, Teach Site opens a text dialog. The user writes practical navigation notes such as where search lives, which menu reveals results, or that a captcha requires manual completion. Notes are stored by hostname and surfaced on later visits.

## Update flow
Update Knowledge iterates source-backed knowledge entries, loads each URL in a hidden/temporary WebView sequence, extracts `document.body.innerText`, truncates to a bounded snapshot, updates the entry, and reports how many succeeded/failed. V1 is intentionally user-triggered rather than background crawling.

## Upload flow
The Android Storage Access Framework document picker accepts `text/*`, JSON, CSV, Markdown, and HTML-like text files. Imported text is capped to a safe size before persistence. Binary formats such as PDF are not parsed in V1.

## Safety and reliability
- Java and Kotlin targets are JVM 17.
- WebView JavaScript is enabled because many modern sites require it.
- File/content access from WebView is disabled unless required by the document picker path.
- Mixed content remains blocked by default.
- External schemes such as `mailto:` are handed to Android intents when possible.
- Captchas and login challenges always remain under user control.
- Knowledge refresh has bounded text sizes and per-source timeout behavior to avoid runaway memory use.

## Build and test
GitHub Actions remains the build path. It runs JVM unit tests and `assembleDebug`, then uploads the APK artifact. The first acceptance gate is a green workflow with an installable debug APK and passing local search tests.
