# AI Browser v2 Learning Engine Design

## Goal
Turn AI Browser from a simple HTTP/HTML search aggregator into an Android browser that can use modern JavaScript websites, remember how each site works, learn from a user's demonstration when needed, preserve that learning across app versions, and combine results into one answer.

## Product behavior

### 1. Prelearned site adapters
Ship a starter adapter library for major public sites discussed for AI Browser, including TikTok, Instagram, Reddit, Pinterest, X, Facebook, Discord, Snapchat, and other useful sources that can be supported safely and technically.

Each adapter stores:
- site/domain identity
- supported search/navigation flow
- semantic selectors for important controls
- result-card selectors and useful content regions
- expected page signatures
- fallback locators
- version and confidence
- whether login is commonly required

Adapters must never depend on fixed screen coordinates.

### 2. Real WebView execution
Site search runs in Android WebView so JavaScript, cookies, DOM updates, scrolling, login sessions, and dynamically rendered results work like a real browser.

The engine should:
- load the site in an isolated search session that can reuse the user's legitimate WebView cookies
- wait for meaningful DOM state rather than a fixed sleep when possible
- find and use the learned search control
- enter the user's query
- submit the search
- detect result readiness
- scroll/load more when the adapter says it is useful
- extract visible result cards and important text
- return structured results to the combined-search layer

### 3. Teach Mode
When a site has no reliable adapter or needs repair, the user can open Teach Mode and use the site normally.

A JavaScript bridge records semantic interaction snapshots such as:
- element role and tag
- accessible name / aria-label
- visible text
- associated label
- placeholder
- stable id/name/data attributes
- nearby DOM structure
- page URL and page signature before/after the action
- action type: tap, type, submit, scroll, result selection

Do not record passwords, payment fields, authentication secrets, or CAPTCHA answers.

The app turns the demonstration into a versioned site adapter and saves it locally.

### 4. CAPTCHA and human verification
AI Browser must not bypass CAPTCHA or human-verification systems.

When a challenge is detected:
- pause automated replay
- show a clear "Human action needed" banner
- let the user complete the challenge directly in WebView
- detect when the challenge page is gone
- resume the normal learned workflow

The app may learn navigation before and after the challenge, but it must not store the CAPTCHA answer or attempt to train a bypass model from the user's solution.

### 5. Self-repair
If a replay step fails, the engine tries semantic repair before asking the user to reteach it.

Repair order:
1. exact primary semantic locator
2. saved fallback locators
3. role/name/label similarity
4. nearby DOM-structure similarity
5. page-signature-compatible candidate search

Each candidate receives a confidence score. High-confidence repair can be saved automatically. Medium confidence may be used for the current session but marked for review. Low confidence changes site status to NEEDS ATTENTION and opens Teach Mode for the failed step only.

### 6. Persistent learned knowledge
Site adapters and learned navigation live in SQLite separately from APK code.

Database records include:
- adapter JSON
- adapter version
- last successful replay
- last repair
- confidence
- status
- failure reason
- source: bundled, auto-learned, or user-taught

Database schema upgrades must migrate existing learned data rather than deleting it.

### 7. Import / export
Add:
- Export Learned Sites
- Import Learned Sites

Export creates a portable JSON backup containing adapters and metadata but not cookies, passwords, CAPTCHA data, or other authentication secrets.

Bundled adapter updates should merge with locally learned adapters and must not blindly overwrite a newer successful local adapter.

### 8. Search All + Combine
Replace the current raw-HTTP regex search with the WebView adapter engine for supported sites.

For one user query:
1. search local uploaded knowledge
2. select relevant enabled site adapters
3. run site searches with bounded concurrency
4. collect structured results
5. deduplicate near-identical items
6. show one combined result grouped by useful findings and source

Initial v2 can use deterministic ranking/summarization on-device. The architecture should leave a clean `SynthesisEngine` interface for later optional GPT/Claude synthesis without making an external AI key mandatory for basic use.

### 9. Site Knowledge dashboard
Each site card shows:
- READY / NEEDS UPDATE / NEEDS ATTENTION / HUMAN ACTION
- adapter source
- confidence
- last successful use
- Update
- Test
- Teach / Repair

Global controls:
- Search All + Combine
- Update Everything
- Learn New Site
- Import Learned Sites
- Export Learned Sites

### 10. Safety and privacy
- keep normal learned navigation local by default
- never record passwords or secure input values
- never store CAPTCHA solutions
- do not attempt anti-bot or human-verification bypass
- do not silently upload browsing history or learned adapters
- preserve ordinary login cookies only through Android WebView's normal cookie store

## Architecture

### `SiteAdapter`
Serializable model for a site workflow, semantic locators, extraction rules, version, confidence, and metadata.

### `AdapterRepository`
SQLite-backed CRUD, migration, bundled-adapter merge, import/export.

### `BrowserAutomationEngine`
Owns WebView replay state and executes adapter actions.

### `SemanticDomBridge`
JavaScript injected into Teach/Replay WebViews. Captures DOM metadata, identifies elements, observes mutations, and extracts structured result regions.

### `TeachSession`
Collects demonstrated actions and converts them into an adapter.

### `RepairEngine`
Scores alternative DOM elements when a saved locator no longer resolves.

### `ChallengeDetector`
Detects likely CAPTCHA/security/human-verification states and pauses replay for the human.

### `SiteSearchCoordinator`
Chooses adapters, schedules searches, enforces timeouts/concurrency, and gathers results.

### `SynthesisEngine`
Consumes local knowledge plus structured web results and returns a single user-facing combined answer. v2 starts deterministic; external LLM synthesis can be added later behind the same interface.

## Data flow

Search:
User query -> SiteSearchCoordinator -> local knowledge + BrowserAutomationEngine per selected adapter -> structured results -> dedupe/rank -> SynthesisEngine -> combined answer.

Teach:
Open site -> SemanticDomBridge observes user actions -> TeachSession -> generated SiteAdapter -> AdapterRepository -> TEST replay -> READY or NEEDS ATTENTION.

Repair:
Replay failure -> RepairEngine -> high-confidence repaired locator -> retry -> persist repair if successful; otherwise -> NEEDS ATTENTION -> user reteaches failed step.

## Error handling
- per-site timeouts so one broken site does not block all results
- explicit statuses for login required, challenge required, changed layout, network failure, no results, and extraction failure
- store last failure reason for diagnosis
- preserve partial results from sites that did work
- never swallow errors silently as v1 does

## Testing

Unit tests:
- semantic locator scoring
- adapter serialization/migration
- merge rules for bundled vs local adapters
- challenge detection strings/signatures
- result deduplication
- teach-session action filtering, including password/CAPTCHA exclusion

Instrumentation tests where feasible:
- replay against deterministic local HTML fixtures in WebView
- JavaScript-rendered search fixture
- layout-change repair fixture
- login/challenge pause-and-resume fixture

GitHub Actions:
- unit tests
- Android build
- debug APK artifact upload

## v2 success criteria
- searching a supported JavaScript-heavy test site returns results through WebView, not raw HTTP parsing
- user can teach an unfamiliar search flow and replay it successfully
- learned adapter survives app restart and schema migration
- changing a selector in a test fixture triggers semantic repair rather than immediate failure
- human-verification screen pauses and resumes correctly without bypassing it
- Search All returns partial combined results even if one or more sites fail
- exported adapters can be imported into a clean install and replayed

## Scope boundary
v2 focuses on reliable site navigation, learning, persistence, repair, and combined search. It does not attempt autonomous account creation, CAPTCHA solving, password handling, payment automation, or unrestricted background scraping.
