# AI Browser v3 Super-Agent Design

## Goal

Turn AI Browser from a search-oriented WebView prototype into a trustworthy, learning browser agent that can understand user goals, operate unfamiliar and learned websites inside the user's legitimate logged-in sessions, verify each action, remember what it learns, combine information across sites, and export safe diagnostics for iterative improvement.

AI Browser is not a normal search engine. The product goal is one AI layer above the user's web: the user describes the outcome they want, while the system selects sites, navigates them, reads and normalizes information, and performs user-authorized actions.

## Product principles

1. **General browser intelligence first.** AI Browser must be able to attempt an unknown website without a hand-built adapter.
2. **Skills are compiled knowledge.** Successful general-agent runs and user demonstrations can become reusable, versioned workflows.
3. **Every action is verified.** Clicking is not success; observable postconditions determine success.
4. **The LLM is not the hot loop.** Deterministic logic and small/local decision layers handle routine perception, skill replay, ranking, and policy. Cloud reasoning is reserved for planning, novel tasks, complex repairs, and explanation.
5. **Perception is multimodal by design.** DOM/accessibility semantics are primary in v3, with screenshot/visual perception represented as a first-class interface so later visual grounding does not require architectural replacement.
6. **User data is local-first.** Login sessions stay in normal WebView storage. Passwords, cookies, auth tokens, payment data, CAPTCHA answers, and OTPs are never written to skill packs or diagnostic exports.
7. **Security challenges are human handoffs.** CAPTCHA, 2FA, login walls, and account-security challenges pause the agent, let the user act, and then resume. AI Browser does not defeat or learn challenge solutions.
8. **Autonomy is earned.** Read-only tasks are highly autonomous. Write actions are gated by risk, site/skill confidence, and user permission.
9. **Learning survives upgrades.** User-learned skills, knowledge graph data, preferences, and diagnostics live outside bundled APK assets and migrate forward.
10. **Failures are observable.** No silent catch-and-ignore behavior in agent execution.

## Scope for v3

v3 is the foundation build, not the entire final super-app. It must produce a real phone-testable agent loop with safe diagnostics and learning.

### v3 must include

- Goal intake and lightweight intent classification.
- Task plans represented as typed steps.
- A Semantic Page Model (SPM) produced from WebView DOM/accessibility-friendly semantics.
- Stable element descriptors and page signatures.
- A constrained action vocabulary: NAVIGATE, TAP, TYPE, SCROLL, BACK, WAIT, EXTRACT.
- Preconditions and postconditions for every executable step.
- General-site exploration for simple unfamiliar-site search/read workflows.
- Website Skills as versioned workflow graphs, not single search templates.
- Demonstration recording that captures semantic actions instead of coordinates.
- Passive-learning hooks so ordinary browsing can later generate candidate skills.
- Candidate-skill validation so a single accidental run is not automatically trusted.
- Self-repair ladder for broken locators.
- Risk/policy engine outside the planner.
- Human handoff state machine for login/CAPTCHA/2FA/security challenges.
- Deep-link/navigation policy that keeps legitimate HTTP/HTTPS navigation in WebView when appropriate and prevents automatic external-app launches during agent runs.
- User Web Knowledge Graph (UWKG) foundation.
- Canonical entity records for at least Person, Post, Listing, Product, Vehicle, Site, Skill, and Goal.
- Provenance/confidence on learned facts and entity links.
- Snapshot/diff support for future "what changed since last time?" workflows.
- Redacted diagnostic report export designed for upload back to ChatGPT/other development AIs.
- Persistent migrations from current v2 data.
- Site/skill health and confidence tracking.
- A live agent-run UI showing current site, current step, status, human-handoff state, and final combined results.

### v3 should demonstrate these phone scenarios

1. **Unknown-site search:** user enters a simple goal for a site without a specialized search template; AI Browser identifies a search-like control, performs the query, verifies a results-like state, and extracts visible results.
2. **Learned replay:** user demonstrates a search/filter/open-result workflow once; AI Browser stores semantic steps and replays them later without using old screen coordinates.
3. **Self-repair:** a stored primary locator is unavailable but a semantically similar alternate exists; the runtime repairs the step and verifies the postcondition.
4. **Cross-site shopping foundation:** a goal such as "find a baby crib" can fan out to multiple learned/known marketplaces, normalize Listing/Product results, preserve provenance, and present partial results when one site fails.
5. **Human handoff:** a login/security/CAPTCHA page pauses the run, surfaces the live WebView, and resumes when the challenge disappears.
6. **TikTok navigation protection:** intent/custom-scheme/app-store redirects do not automatically destroy the WebView automation session; the user can explicitly choose external-app handoff when desired.
7. **Developer feedback loop:** one tap exports a sanitized report containing enough context to diagnose site failures without exposing credentials/session secrets.

## Architecture

### 1. Goal and planner layer

A user goal is compiled into a `GoalSpec` containing:

- intent/archetype such as SEARCH, FIND_PERSON, READ_FEED, FIND_LISTING, COMPARE, SOCIAL_ACTION;
- entities and constraints;
- expected output schema;
- risk ceiling;
- missing critical parameters;
- selected sites;
- completion criteria.

The planner emits a `TaskPlan` containing site-level nodes and dependencies. It does not emit arbitrary JavaScript, raw CSS selectors, or unrestricted clicks.

For v3, planning should be hybrid and intentionally modest: deterministic archetype planning for known goal families plus a clean `ReasoningPlanner` interface for later local/cloud models.

### 2. Semantic Page Model

`SemanticPageModel` is the contract between WebView perception and the rest of the agent.

It includes:

- current URL/origin;
- page title;
- page archetype hint;
- page signature;
- visible semantic elements;
- interactive affordances;
- visible text blocks;
- result-card candidates;
- challenge/login/interstitial indicators.

Each `SemanticElement` includes:

- stable runtime element id;
- role/tag;
- accessible/ARIA name;
- visible text;
- label;
- placeholder;
- selected stable attributes;
- enabled/checked/expanded state;
- ancestor/nearby-landmark summary;
- bounding rectangle as supporting metadata, never the primary identity;
- semantic fingerprint;
- confidence.

Raw DOM is not passed upward as the normal planner contract.

### 3. Perception drivers

Define a driver-neutral `PerceptionDriver` interface.

v3 implementation: WebView DOM/ARIA/visibility extraction via tightly scoped JavaScript.

Future driver: screenshot/vision perception producing the same semantic model. The architecture must allow both DOM and visual evidence to contribute to a single element candidate without changing planner/skill APIs.

### 4. Action runtime

The executor accepts only a fixed `AgentAction` vocabulary:

- `NAVIGATE(url)`
- `TAP(elementRef)`
- `TYPE(elementRef, value)`
- `SCROLL(direction/target)`
- `BACK`
- `WAIT(condition)`
- `EXTRACT(schema)`

Each step includes:

- semantic intent label;
- precondition;
- locator/candidate stack;
- action;
- postcondition oracle;
- timeout;
- risk tier;
- retry/repair policy.

An action is successful only when its postcondition is observed.

### 5. Website Skills

A `WebsiteSkill` is a parameterized, versioned workflow graph.

Header fields:

- skill id/name;
- site/origin pattern or generic archetype scope;
- skill archetype;
- parameters and types;
- output schema;
- risk tier;
- version/schema version;
- provenance: bundled, demonstrated, discovered, repaired, imported;
- confidence;
- success/failure counts;
- last verified timestamp;
- health state.

Workflow nodes contain semantic intent, locator candidates, action, precondition, postcondition, timeout, and failure edges.

Skills may include branches and bounded loops such as SCROLL_UNTIL, NEXT_PAGE_UNTIL, or RETRY_WITH_ALTERNATE.

### 6. General unknown-site agent

When no trusted skill exists, AI Browser invokes `GeneralWebSkill`, a constrained exploration workflow rather than unrestricted LLM clicking.

The flow is:

1. classify page archetype;
2. enumerate safe affordances from SPM;
3. rank actions against current goal;
4. execute one low-risk action;
5. verify page-state progress;
6. update the local world state;
7. repeat under a strict action/time budget;
8. stop on completion, human-handoff condition, low confidence, or risk escalation.

Initial heuristics support search/read workflows. A future reasoning model can provide candidate ranking through the same interface.

### 7. Demonstration and passive learning

Teach Mode becomes a semantic episode recorder.

For each meaningful user action, record:

- sanitized SPM before;
- target semantic descriptor and candidate locators;
- action type;
- parameterized/sanitized value class;
- SPM after;
- inferred state change/postcondition;
- URL/page signature;
- timing;
- whether the action produced meaningful progress.

Password, payment, OTP, auth-token, CAPTCHA, and other sensitive fields are excluded at capture time.

Ordinary browsing can optionally use the same recorder in passive mode. Passive sessions create **candidate skills**, not immediately trusted skills.

Promotion policy:

- postconditions must have been achieved;
- no security-sensitive/high-risk action can be auto-promoted;
- accidental/no-op actions are pruned;
- repeated similar successful episodes increase confidence;
- high-impact skills require explicit user acceptance;
- failed runs become negative evidence rather than training targets.

### 8. Verification and self-repair

Self-repair ladder:

1. primary semantic locator;
2. alternate locator stack;
3. semantic similarity against current SPM;
4. structural-neighborhood/page-signature similarity;
5. optional reasoning/visual candidate provider;
6. minimal human repair: highlight likely candidate or ask user to tap correct element.

A proposed repair is provisional until the postcondition succeeds. Persistent promotion requires repeated verified success. The previous known-good skill remains available for rollback.

### 9. Policy, permissions, and autonomy

Policy decisions are enforced outside the planner.

Risk tiers:

- **Tier 0 READ:** navigate, search, read, extract, filter, sort, scroll. Autonomous within budgets.
- **Tier 1 REVERSIBLE INTERACTION:** like, save, follow known account, wishlist, add to cart. User can grant per-site/per-skill autonomy; new/low-confidence skills may be escalated for confirmation.
- **Tier 2 SOCIAL/VISIBLE WRITE:** comment, message, post, review, follow unknown account. Exact target/content preview and explicit confirmation.
- **Tier 3 MONEY/IDENTITY/DESTRUCTIVE:** purchase, bid, payment submission, delete, account/security setting, identity forms. Explicit per-action confirmation; future builds may add biometric confirmation.

Runtime may raise a tier due to low confidence or unexpected state. Runtime may never lower a tier chosen by policy.

### 10. Human handoff

`HumanHandoffManager` unifies:

- login required;
- CAPTCHA/security challenge;
- 2FA/OTP;
- account re-verification;
- app-only feature unavailable on web;
- ambiguous low-confidence repair.

The job state is checkpointed before handoff. The user acts in the same WebView/session. The runtime detects the blocker has cleared and resumes from the checkpoint when safe.

AI Browser never records or learns CAPTCHA/OTP/password solutions.

### 11. Deep-link and navigation policy

During agent runs:

- same-task HTTP/HTTPS navigation remains in WebView unless blocked by policy;
- `intent://`, custom schemes, Play Store links, and automatic app launches are intercepted and logged;
- no external app launch happens silently;
- manual browsing can show `Open in app` as an explicit user action;
- if a required feature is genuinely unavailable on mobile web, the agent reports that constraint rather than attempting security/evasion tricks.

This policy specifically addresses the observed TikTok behavior.

### 12. User Web Knowledge Graph

Introduce a local `UserWebKnowledgeGraph` as the long-lived memory substrate.

Core node types for v3:

- Person
- Account
- Post
- Listing
- Product
- Vehicle
- Site
- Skill
- Goal
- Snapshot

Core edge types:

- SAME_AS / POSSIBLY_SAME_AS
- POSTED_BY
- LISTED_ON
- SEEN_AT
- MATCHES_GOAL
- PRODUCED_BY_SKILL
- VIEWED_BY_USER
- SAVED_BY_USER
- RELATED_TO

Every node/edge stores provenance, confidence, timestamps, and source URL/site identifiers where available.

Entity resolution is probabilistic. Ambiguous people/listings/products are presented as possible matches rather than silently merged.

### 13. Temporal snapshots and diffing

For supported read workflows, store compact observations:

- stable source/entity id when available;
- timestamp;
- content/listing signature;
- source URL;
- goal/site association.

A later run can classify items as new, changed, unchanged, or missing. This becomes the foundation for future "what changed since last time?", saved-search monitoring, and daily briefings.

### 14. Cross-site results

Site skills return canonical records rather than raw HTML.

v3 canonical schemas include at least:

- PersonResult
- PostResult
- ListingResult
- ProductResult
- VehicleResult

All include provenance and confidence. The coordinator can fan out read-only tasks, preserve partial successes, normalize units/currency where safe, deduplicate conservatively, and rank against user constraints.

### 15. Telemetry and developer report

Every agent run creates an append-only local episode.

Events include:

- goal received;
- plan generated;
- site/node started;
- SPM/page signature snapshot reference;
- action attempted;
- precondition/postcondition result;
- locator/candidate chosen;
- timing;
- confidence;
- redirect/deep-link;
- repair attempt;
- human handoff;
- extracted canonical records;
- final outcome.

Redaction is structural and layered:

1. never capture cookies, Authorization headers, WebView storage secrets, password values, OTPs, payment credentials, CAPTCHA answers;
2. field-aware sensitive-input redaction;
3. pattern/entropy scan for tokens/secrets;
4. screenshot masking for sensitive input regions before saving/export;
5. user-visible export preview.

Export levels:

- Minimal: plan/actions/outcomes/timings/failure summary.
- Standard: plus sanitized SPM excerpts around failures.
- Verbose: plus masked screenshots around failures.

The exported artifact should be directly useful to ChatGPT/Claude/Grok/Copilot for debugging without exposing session credentials.

### 16. Persistence and upgrades

Extend the existing SQLite store using non-destructive schema migrations. Bundled skills remain seed assets; learned skills and graph records remain mutable user data. Upgrades never blindly replace newer successful learned skills.

User-controlled export/import includes skills, safe preferences, and optionally graph metadata, but excludes sessions/cookies/passwords/tokens/CAPTCHA/OTP/payment secrets.

### 17. Health and quarantine

Track per-skill and per-site:

- success rate;
- failure rate;
- average completion time;
- last successful page signature;
- locator/repair drift;
- human handoff frequency;
- last verified timestamp.

Repeated drift/failure lowers autonomy and can quarantine a skill. v3 exposes health status; background proactive revalidation can be expanded in later versions.

### 18. Android runtime constraints

- Use a small WebView pool; v3 may serialize most site work to stay stable on phones with constrained memory.
- Long-running interactive jobs must be resumable and compatible with a foreground-service architecture; v3 should structure state accordingly even if not every monitor is implemented yet.
- Observe SPAs using DOM mutation/debounced rescans rather than relying only on `onPageFinished`.
- Log Android WebView version in diagnostics.
- Login/session state remains in normal WebView cookie/storage mechanisms.
- Do not exfiltrate cookies to external services.

## UI changes for v3

Home screen:

- Goal input: `What do you want me to do?`
- `Run Goal`
- `What’s New?` placeholder/early snapshot-based view
- `Learn / Teach`
- `Site & Skill Knowledge`
- `Developer Report`
- existing upload/import/export capabilities retained where useful

Agent-run screen:

- current goal;
- current site;
- current semantic step;
- progress/status;
- confidence/health indicator;
- live WebView;
- `Take Over` for manual intervention;
- `Skip Site`;
- `Stop`;
- human-handoff banner when needed.

Skill knowledge screen:

- skill name/site;
- provenance;
- health/confidence;
- last successful run;
- runs/success/failure;
- autonomy tier;
- Teach/Repair/Test controls.

## Deferred beyond v3

The following are explicitly architecture-compatible but not required to call v3 complete:

- full on-device vision model and screenshot element detection;
- native-app accessibility driver;
- fully autonomous social-feed digest across every major network;
- standing/background monitors and push notifications;
- community skill marketplace/registry;
- encrypted multi-device sync;
- advanced local vector index/personal web search;
- fully local small language model for micro-actions;
- autonomous purchases or destructive/account actions;
- broad native-app automation;
- CAPTCHA/authentication/anti-bot bypass of any kind.

## v4+ direction

### v4

- visual perception provider and DOM+vision fusion;
- richer passive-learning skill compiler;
- cross-site saved goals and temporal diffs;
- stronger shopping/vehicle comparison;
- local preference learning;
- deterministic compiled skill execution with minimal cloud reasoning;
- daily/goal-thread updates.

### v5

- Daily Web Briefing;
- persistent Goal Threads;
- proactive monitors with Android scheduling/foreground constraints;
- personal web index;
- replay viewer;
- signed sanitized skill packs;
- optional reasoning-model integration.

### v6+

- community skill health ecosystem;
- optional on-device compact models for grounding/perception;
- driver-neutral support for additional legitimate surfaces where platform policy permits;
- encrypted multi-device continuity.

## Testing strategy

Unit tests:

- semantic fingerprint/locator scoring;
- postcondition evaluation;
- risk-tier enforcement;
- sensitive-action filtering;
- skill serialization/migration;
- skill promotion/quarantine rules;
- challenge classification;
- deep-link navigation policy;
- entity match confidence and non-merge behavior;
- snapshot diffing;
- telemetry redaction.

Integration/WebView fixture tests:

- dynamic JS search page;
- SPA mutation after action;
- changed locator with successful semantic repair;
- ambiguous repair requiring human;
- login/challenge pause and resume;
- custom-scheme/deep-link interception;
- multiple result-card extraction;
- partial failure across multiple sites.

Scenario tests:

- unknown-site search/read;
- demonstrate and replay;
- search multiple marketplaces and merge results;
- TikTok-like app-redirect attempt;
- export a redacted diagnostic bundle.

Adversarial tests:

- page content attempting to instruct the agent;
- misleading destructive control text;
- token-like strings in DOM/telemetry;
- sensitive input fields;
- slow/partial page loads;
- duplicate entities with ambiguous identity.

## v3 completion criteria

v3 is complete only when:

1. GitHub Actions unit tests pass.
2. Android debug APK builds successfully.
3. An unknown/simple site can be searched through the semantic action loop without a prebuilt URL template.
4. A demonstrated workflow can be persisted and replayed semantically.
5. Every replayed action is postcondition-verified.
6. A broken primary locator can repair through an alternate/semantic candidate and verify success.
7. Human challenge/login handoff pauses and resumes a job.
8. TikTok-style external-app redirects are intercepted during agent runs.
9. At least two sites can return normalized records into one combined result set while preserving a failure from another site.
10. Learned skill/graph data survives app upgrade migration from v2.
11. Developer Report export passes redaction tests and contains useful failure context.
12. The final APK is downloaded from the successful GitHub Actions artifact and integrity-checked before being handed to the user.
