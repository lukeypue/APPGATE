# AI Browser v3 Super-Agent Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Upgrade AI Browser v2 into a trustworthy v3 browser-agent foundation that can understand unfamiliar pages, execute verified workflows, learn from successful browsing, retain a local user-web knowledge graph, enforce action-risk policy, export redacted developer reports, and keep appropriate web navigation inside the WebView.

**Architecture:** Keep the Android WebView/session foundation, but add a driver-neutral semantic page model and workflow runtime above it. Known workflows run deterministically; unfamiliar pages use a constrained generic exploration policy over semantic affordances, with every action checked by postconditions. Learning, graph memory, policy, and telemetry stay local-first and survive upgrades.

**Tech Stack:** Android Java, WebView/JavaScript bridge, SQLite, org.json, JUnit 4, GitHub Actions, Gradle 8.9, Java 17.

**Spec:** `docs/superpowers/specs/2026-09-09-ai-browser-v3-super-agent-design.md`

## Global Constraints

- Android only; package remains `com.aibrowser.knowledgehub`.
- Preserve legitimate WebView login/cookie sessions across app upgrades.
- CAPTCHA, 2FA, login, and security challenges are human handoffs only; no bypass/evasion logic.
- Never persist or export passwords, OTPs, cookies, auth/session tokens, payment-card data, or CAPTCHA answers.
- APPGATE main branch remains untouched; implementation happens on `ai-browser-android-v3`.
- v3 must remain usable without a mandatory cloud-LLM API key; the initial generic agent may be deterministic/heuristic behind interfaces that allow a later model implementation.
- Every executable browser step has an expected outcome/postcondition and failure state.
- High-risk actions are blocked or confirmed by policy outside the planner.
- Learned data is stored separately from the APK and migrated non-destructively.

---

### Task 1: Semantic Page Model and page archetypes

**Files:**
- Create: `app/src/main/java/com/aibrowser/knowledgehub/SemanticElement.java`
- Create: `app/src/main/java/com/aibrowser/knowledgehub/SemanticPageModel.java`
- Create: `app/src/main/java/com/aibrowser/knowledgehub/PageArchetypeClassifier.java`
- Modify: `app/src/main/java/com/aibrowser/knowledgehub/SemanticDomBridge.java`
- Test: `app/src/test/java/com/aibrowser/knowledgehub/PageArchetypeClassifierTest.java`

**Interfaces:**
- Produces `SemanticPageModel.fromJson(String)`, `List<SemanticElement> interactiveElements`, `PageArchetypeClassifier.classify(SemanticPageModel)`.
- `SemanticDomBridge.pageModelJavascript()` returns JSON containing URL, title, visible elements, roles/names/text/state, page text summary, and structured-data hints.

- [ ] Write failing tests for search/feed/listing/profile archetype detection from hand-built semantic models.
- [ ] Run the archetype tests and verify failure because the new classes do not exist.
- [ ] Implement the semantic element/page-model data classes and deterministic archetype scoring.
- [ ] Extend the DOM bridge with a compact page-model extractor that ignores password/payment/hidden secret fields and captures visible semantic affordances.
- [ ] Run unit tests and verify all Task 1 tests pass.
- [ ] Commit Task 1.

### Task 2: Verified workflow/skill runtime

**Files:**
- Create: `app/src/main/java/com/aibrowser/knowledgehub/SkillStep.java`
- Create: `app/src/main/java/com/aibrowser/knowledgehub/SkillWorkflow.java`
- Create: `app/src/main/java/com/aibrowser/knowledgehub/PostconditionVerifier.java`
- Create: `app/src/main/java/com/aibrowser/knowledgehub/SkillRuntime.java`
- Test: `app/src/test/java/com/aibrowser/knowledgehub/PostconditionVerifierTest.java`
- Test: `app/src/test/java/com/aibrowser/knowledgehub/SkillWorkflowTest.java`

**Interfaces:**
- `SkillStep` stores semantic intent, action type, locator, parameters, precondition, postcondition, timeout, risk tier.
- `SkillWorkflow` is versioned JSON with parameters, steps, confidence, provenance, run/success counts.
- `PostconditionVerifier.verify(String condition, SemanticPageModel before, SemanticPageModel after)` returns a typed result.

- [ ] Write failing tests for URL-changed, element-appeared, text-present, state-changed, and no-change postconditions.
- [ ] Implement workflow serialization and postcondition verification.
- [ ] Implement a runtime state machine that refuses to advance when postconditions fail and reports the failed step.
- [ ] Run Task 2 tests and the existing v2 suite.
- [ ] Commit Task 2.

### Task 3: Policy engine and safe generic exploration

**Files:**
- Create: `app/src/main/java/com/aibrowser/knowledgehub/RiskPolicyEngine.java`
- Create: `app/src/main/java/com/aibrowser/knowledgehub/GenericWebAgent.java`
- Create: `app/src/main/java/com/aibrowser/knowledgehub/GoalSpec.java`
- Create: `app/src/main/java/com/aibrowser/knowledgehub/LocalGoalPlanner.java`
- Test: `app/src/test/java/com/aibrowser/knowledgehub/RiskPolicyEngineTest.java`
- Test: `app/src/test/java/com/aibrowser/knowledgehub/LocalGoalPlannerTest.java`

**Interfaces:**
- Risk levels: READ, INTERACT, SOCIAL_WRITE, HIGH_IMPACT.
- `RiskPolicyEngine.decision(action, siteGrant, confidence)` returns ALLOW, CONFIRM, or BLOCK.
- `LocalGoalPlanner.parse(String)` recognizes initial task families SEARCH/COMPARE/FIND_PERSON/WHATS_NEW and extracts explicit constraints where possible.
- `GenericWebAgent` chooses only from approved semantic actions and never emits raw JavaScript/selectors.

- [ ] Write failing tests for autonomous read actions, confirm-required messages/comments, blocked payment/destructive actions, and confidence escalation.
- [ ] Write failing tests for common shopping/person/what-is-new goal parsing.
- [ ] Implement the policy engine and local goal parser.
- [ ] Implement a constrained first-visit exploration policy: detect page archetype, choose search-like controls for search goals, inspect filter-like controls, extract read-only results, and stop before write/high-impact operations unless policy permits.
- [ ] Run Task 3 tests and existing tests.
- [ ] Commit Task 3.

### Task 4: User Web Knowledge Graph and temporal memory

**Files:**
- Modify: `app/src/main/java/com/aibrowser/knowledgehub/KnowledgeDb.java`
- Create: `app/src/main/java/com/aibrowser/knowledgehub/WebEntity.java`
- Create: `app/src/main/java/com/aibrowser/knowledgehub/WebEdge.java`
- Create: `app/src/main/java/com/aibrowser/knowledgehub/WebKnowledgeGraph.java`
- Create: `app/src/main/java/com/aibrowser/knowledgehub/EntityMatcher.java`
- Test: `app/src/test/java/com/aibrowser/knowledgehub/EntityMatcherTest.java`

**Interfaces:**
- DB migration adds `web_entities`, `web_edges`, `goal_threads`, and `observations` without dropping v1/v2 tables.
- Entities contain type, canonical key, site, source ID/URL, normalized fields JSON, observed timestamp, content hash, confidence, provenance.
- `WebKnowledgeGraph.diffSince(...)` returns NEW/UPDATED/UNCHANGED records.
- `EntityMatcher` is conservative: exact IDs/URLs dominate; fuzzy matches below high threshold remain POSSIBLE_MATCH rather than SAME.

- [ ] Write failing tests for exact dedupe, conservative probable match, and snapshot diff behavior.
- [ ] Implement DB v3 migration and graph repository.
- [ ] Implement temporal observation/diff logic and conservative entity matching.
- [ ] Run Task 4 tests plus migration-related tests.
- [ ] Commit Task 4.

### Task 5: Passive learning, repair, and skill health

**Files:**
- Modify: `app/src/main/java/com/aibrowser/knowledgehub/TeachSession.java`
- Modify: `app/src/main/java/com/aibrowser/knowledgehub/RepairEngine.java`
- Create: `app/src/main/java/com/aibrowser/knowledgehub/PassiveLearningEngine.java`
- Create: `app/src/main/java/com/aibrowser/knowledgehub/SkillHealth.java`
- Test: `app/src/test/java/com/aibrowser/knowledgehub/PassiveLearningEngineTest.java`
- Test: `app/src/test/java/com/aibrowser/knowledgehub/SkillHealthTest.java`

**Interfaces:**
- Passive learning accepts only verified successful read/low-risk episodes as candidate workflow traces.
- Single runs remain candidates; repeated equivalent successes promote confidence. Failed runs are retained only as anti-pattern/failure statistics.
- Repair keeps the v2 semantic-locator fallback and adds page-archetype/signature context plus provisional-repair state.

- [ ] Write failing tests proving sensitive/high-impact steps are never auto-promoted and repeated verified flows gain confidence.
- [ ] Implement candidate episode normalization and workflow promotion rules.
- [ ] Extend repair/health scoring so drifting page signatures downgrade autonomy before hard failure.
- [ ] Run Task 5 tests and existing teaching/repair tests.
- [ ] Commit Task 5.

### Task 6: Navigation policy, challenge resume, and developer report

**Files:**
- Create: `app/src/main/java/com/aibrowser/knowledgehub/BrowserNavigationPolicy.java`
- Create: `app/src/main/java/com/aibrowser/knowledgehub/DeveloperReport.java`
- Create: `app/src/main/java/com/aibrowser/knowledgehub/TelemetryRedactor.java`
- Modify: `app/src/main/java/com/aibrowser/knowledgehub/BrowserAutomationEngine.java`
- Test: `app/src/test/java/com/aibrowser/knowledgehub/BrowserNavigationPolicyTest.java`
- Test: `app/src/test/java/com/aibrowser/knowledgehub/TelemetryRedactorTest.java`

**Interfaces:**
- HTTP/HTTPS remains in WebView when allowed; `intent://`, custom app schemes, and Play-store/app launches are blocked during agent runs and recorded as external-navigation attempts.
- Existing CAPTCHA/login detector pauses the job and resumes after the page no longer matches a challenge state.
- DeveloperReport contains app/runtime version, goal, plan, sites, steps, statuses, timings, page signatures, redirects, repairs, confidence, human interventions, and final summary.
- TelemetryRedactor structurally omits secrets and pattern-redacts likely tokens/payment/auth values before export.

- [ ] Write failing tests for TikTok-style intent/deep-link interception while preserving normal HTTPS navigation.
- [ ] Write failing redaction tests for passwords, OTPs, cookies, bearer/JWT-like tokens, and card-like values.
- [ ] Implement navigation policy and wire it into the WebView client.
- [ ] Implement append-only report events and safe export JSON/Markdown.
- [ ] Run Task 6 tests and all existing challenge tests.
- [ ] Commit Task 6.

### Task 7: v3 UI integration and end-to-end Android build

**Files:**
- Modify: `app/src/main/java/com/aibrowser/knowledgehub/MainActivity.java`
- Modify: `app/src/main/java/com/aibrowser/knowledgehub/SiteSearchCoordinator.java`
- Modify: `app/src/main/java/com/aibrowser/knowledgehub/SynthesisEngine.java`
- Modify: `app/build.gradle.kts`
- Modify: `README.md`
- Modify: `.github/workflows/build-ai-browser-apk.yml`

**Interfaces:**
- Home UI exposes `Ask AI Browser`, `What’s New`, `Learn Site`, `Site Knowledge`, and `Export Developer Report` while retaining import/export/upload controls.
- Search/goal run view shows current goal, site, step, status, confidence, human-action banner, skip/stop.
- `What’s New` reads graph diffs rather than pretending a fresh search is temporal memory.
- Version becomes 3.0 and GitHub artifact becomes `AI-Browser-Android-v3-APK`.

- [ ] Integrate the planner/runtime/graph into the existing coordinator without removing v2 learned adapters or login sessions.
- [ ] Add developer-report export and clear human-handoff UI.
- [ ] Add a truthful v3 `What’s New` experience that reports only entities actually observed by the app.
- [ ] Update version/readme and GitHub Actions source reconstruction for v3.
- [ ] Run the complete unit-test suite in GitHub Actions.
- [ ] Build `assembleDebug` in GitHub Actions.
- [ ] Download the resulting artifact, verify ZIP/APK integrity and SHA-256, and provide the direct APK.
- [ ] Commit Task 7.

## Self-review

- Spec coverage: v3 foundation includes verified execution, semantic perception, generic unknown-site fallback, workflow skills, passive learning, graph memory, risk policy, challenge handoff, TikTok/deep-link policy, telemetry, persistence, and build verification. Full on-device vision models, native-app drivers, background standing monitors, and community skill registry remain later roadmap items by design.
- Placeholder scan: no implementation placeholders are required to complete v3.
- Type consistency: planner emits `GoalSpec`; browser runtime consumes semantic page models/workflows; graph stores normalized observations; telemetry records all layers without containing credentials.
