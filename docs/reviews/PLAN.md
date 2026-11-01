# In-depth review plan

Planned 2026-11-01 with the owner. Code under review: `main` at `0334b6a`, on the branch `review/2026-11`.

The review looks at the whole product: the Android app, the web app for iPhone, the Firestore rules, the reminder
job, the workflows and the docs. It runs as one session per section below. Each session **finds, verifies and
reports**; it changes nothing else. When every report is in, the owner and Claude triage them together and fix in
themed pull requests that follow CLAUDE.md (section 12).

**Android comes first.** Every new feature or change is built in the Android app and then ported to the web. So
each section reviews Android first and deepest, and the web only where its section says. When the two differ,
Android is the reference unless Android is the one that's wrong.

Two lenses apply to every section, not just UX:

- **User understanding.** Would a person who never read the docs understand what the app shows and why it did
  something?
- **Redundancy.** Anything done twice, shown twice, stored twice, read twice or documented twice, and whether it
  should stay.

## Sections

| # | Section | Report | Needs | Alongside another? |
| --- | --- | --- | --- | --- |
| 00 | Baseline and health check | `00-baseline.md` | Gradle, Firebase emulators, Pixel_8, WebKit | No |
| 01 | Security: data access, backend and supply chain | `private/01-security-data.md` | Firestore emulator | No |
| 02 | Security and privacy: Android and web clients | `private/02-security-clients.md` | Pixel_8, Firebase emulators, WebKit | No |
| 03 | Bugs in business logic | `03-logic.md` | Gradle (unit tests), network for live import | Yes, in its own worktree |
| 04 | Bugs in sync, sessions and multi-phone behavior | `04-sync.md` | Firebase emulators, Pixel_8 | No |
| 05 | Reliability, offline and failure handling | `05-reliability.md` | Firebase emulators, Pixel_8 | No |
| 06 | UX, user understanding and redundancy | `06-ux.md` | Firebase emulators, Pixel_8 | No |
| 07 | Accessibility | `07-accessibility.md` | Firebase emulators, Pixel_8, WebKit | No |
| 08 | Smoothness and runtime performance | `08-smoothness.md` | Firebase emulators, Pixel_8 | No |
| 09 | Efficiency: quota, battery, size, network and CI | `09-efficiency.md` | Firestore emulator, Pixel_8 | No |
| 10 | Web app: parity with Android and iPhone issues | `10-web.md` | Firebase emulators, WebKit, Pixel_8 | No |
| 11 | Code quality, redundancy, tests and docs | `11-code.md` | Gradle | Yes, in its own worktree |
| 12 | Triage and fix roadmap (with the owner) | `ROADMAP.md`, `private/SECURITY-ROADMAP.md` | Nothing | No |

Report paths are under `docs/reviews/`. Run 00 first and 12 last. The order in between is a recommendation (risk
first: security, then bugs, then experience, then cost and code).

**One emulator session at a time.** The Firebase emulators use fixed ports (Auth 9099, Firestore 8080, UI 4000) and
there is one Android emulator (Pixel_8). Sections 03 and 11 only need Gradle, so either can run next to one emulator
session, but only in its own worktree (two Gradle builds in one checkout get in each other's way). A worktree session
commits its report on its own branch, and Claude merges it into `review/2026-11` when the owner says so.

## Ground rules for every review session

1. **Read first:** CLAUDE.md, this plan's ground rules and your section, and the DESIGN sections your section lists.
   DESIGN is the intended behavior: a mismatch between the code and DESIGN is a finding (a bug or a doc fix, say
   which).
2. **Report only.** Don't change app code, tests, rules, workflows, scripts or docs. You write your report (and its
   screenshots under `docs/reviews/assets/<NN>/`) and nothing else. Experiments go in your scratchpad. If a test must
   live in the source tree to run, add it, run it, delete it, and copy it into the report so the fix PR can reuse
   it. At the end, `git status` shows only your report files.
3. **Review this commit.** Stay on `review/2026-11` in this checkout (or your worktree). Don't switch branches,
   pull or rebase, even if `main` moves on.
4. **Emulators only.** Use the `demo-spoilnot` emulators and local builds. Never sign in to, read from or write to
   `spoilnot-dev` or `spoilnot-prod`, and never run the reminder, export or restore workflows. Don't build prod
   release variants: they sign with the upload key and upload Crashlytics mapping files to `spoilnot-prod`. The dev
   benchmark build (`assembleDevBenchmark`) is release code, minified and debug-signed; use it instead. Fetching
   public pages is fine (the recipe sites for `LiveImportTest`, the hosted web app's response headers).
5. **No secrets or personal data.** Don't open `keystore.properties`, `local.properties`, `web/.env*`,
   `.beta-testers`, `*.jks`, `*.keystore` or `service-account*.json`. Test data is made up.
6. **Shared resources.** Before starting the Firebase emulators or Pixel_8, check nothing is already using them
   (`lsof -i :8080 -i :9099 -i :4000`, `adb devices`). Stop whatever you started when you finish.
7. **Search hygiene.** Skip `.claude/worktrees/`, `build/`, `node_modules/`, `firebase/firebase-export-*`,
   `firebase/.emulator-data/` and `firebase/hosting-dist/` when searching; they hold copies and generated files.
8. **Evidence over volume.** Every finding has a concrete scenario. P0 and P1 findings need a reproduction (a failing
   test, a script, emulator steps or a screenshot), or are marked *Likely* with what stopped you from reproducing
   them. A few verified findings beat many guesses. Style preferences go under Ideas, not Findings.
9. **Security stays private.** Anything security-relevant, found in any section, goes in
   `docs/reviews/private/` (ignored by git through `.git/info/exclude`; sections other than 01 and 02 append to
   `private/incidental.md`). Never put it in a public report, a commit message or a GitHub issue. The public report
   says only "one item moved to the private notes".
10. **Out of scope.** Something that belongs to another section gets one line under "For other sections". Don't
    chase it.
11. **Finish.** Fill in every part of the report template, including what you didn't cover. Commit only your report
    and its assets to `review/2026-11` (`Review <NN>: <section title>`). Don't push. Then give the owner a short
    summary in chat: the top findings, the key metrics and the phone checks waiting for them.

## Severity and confidence

| Severity | Meaning in Spoil Not | Examples |
| --- | --- | --- |
| **P0** Critical | Data reachable outside its household, account takeover, a leaked secret, or loss of a household's data | A stranger reads another household's pantry; a member's write wipes a list for everyone |
| **P1** High | Wrong data or broken core flow for normal use, a member doing an owner's job, a crash in a common flow, wrong or missing reminders, a group of people locked out of a feature | Add to pantry creates duplicates when two phones do it; TalkBack users can't delete an item; quota runs out at expected scale |
| **P2** Medium | Wrong in an edge case with a workaround, noticeable jank, confusing UX that leads to mistakes, waste that matters at scale | Status wrong on a DST day; a sheet stutters when the keyboard opens; a term nobody understands |
| **P3** Low | Polish, small inconsistency, small waste | Two names for the same thing; a redundant read on one screen |
| **Idea** | Not a defect: a suggestion, a simplification, a feature to remove | Merge two overlapping Home cards |

Confidence: **Confirmed** (reproduced, evidence attached), **Likely** (from reading the code, not reproduced, with the
reason), **Question** (needs the owner's product decision).

## Report template

```markdown
# <NN> <Section title>

Reviewed `<commit>` on <date>. Platforms: Android / web / backend.

## Summary
Three to five lines: the overall verdict and the top issues.

## Metrics
| Metric | Value | How measured | Target or budget |

## Findings
### <PREFIX>-01 · P1 · Confirmed · Android — <one-line title>
- **Where:** `path/File.kt:123`
- **What happens:** the behavior, in plain words.
- **Evidence:** steps, a test or script (in a code block), or `assets/<NN>/<name>.png`.
- **Who it affects:** which people, how often.
- **Suggested fix:** the approach. Effort S/M/L. Also needs: web port, rules deploy, `minSupportedBuild` raise,
  DESIGN section, new test.

## Ideas

## For other sections

## Not covered
What you skipped or couldn't check, and why.

## Phone checks for the owner
| # | Phone | Steps | Expected | Result |
```

Finding prefixes: BASE, SECD, SECC, LOGIC, SYNC, REL, UX, A11Y, PERF, EFF, WEB, CODE.

---

## 00 Baseline and health check

**Goal.** Know the starting state, so each finding and each later fix can be measured against it.

**Read.** CLAUDE.md "Before finishing a task"; DESIGN 13; RUNBOOK "Baseline profile" and "Check quota usage".

**Do.**

- Run the full suite from CLAUDE.md: `spotlessCheck lintDevDebug testDevDebugUnitTest assembleDebug
  :benchmark:compileBenchmarkKotlin`, `cd firebase && npm test`, `cd backend/reminder && npm test`, `cd web && npm
  run gen:check && npm run check && npm run e2e`, `recordRoborazziDevDebug`, `connectedDevDebugAndroidTest` with
  the emulators and Pixel_8, and `cd firebase && npm run quota-sim`. Record pass or fail, test counts and how long
  each took. Rerun a failure once and note anything flaky.
- Measure: lint warnings by type; APK size of the dev benchmark build; the web build's JS and CSS sizes, raw and
  gzipped; Lighthouse on
  the preview build (mobile); `StartupBenchmark` and `PantryScrollBenchmark` on Pixel_8, three runs each; quota-sim
  reads, writes and deletes per household per day against RUNBOOK.
- Dependencies: `npm outdated` and `npm audit` in `web/`, `firebase/` and `backend/reminder/`; Gradle libraries in
  `gradle/libs.versions.toml` against their latest stable releases.
- Check that the commands in CLAUDE.md and RUNBOOK still work as written.

**Metrics.** A single table that later sections and the re-run after fixes will compare against.

**Phone checks.** The installed Android beta's version and the web app's build on the iPhone; a cold start on each,
timed three times.

## 01 Security: data access, backend and supply chain (private)

**Goal.** Prove that nobody can read or change what they shouldn't, that the reminder job and workflows can't be
turned against the project, and that the dependency chain is sound. The repository is public, so assume attackers
can read every line.

**Read.** DESIGN 8, 10.3, 10.4, 12, 13.4, 18.4, 18.6; `docs/SECURITY_REVIEW.md`; `firebase/firestore.rules` and
`firebase/test/rules.test.js`; `backend/reminder/src/`; `.github/workflows/`, `.github/dependabot.yml`;
`scripts/*.sh`; `.gitleaks.toml`.

**Attackers to model.** A stranger with their own account (anyone can sign up: the API keys are public); someone
with a pending join request; a member trying to act as the owner or to harm other members; a removed member whose
phone still has a session; anyone reading the public repository, Actions logs and artifacts; a malicious pull
request or dependency.

**Check.**

- **Rules, path by path.** For every `match` and every operation (get, list, create, update, delete), write down
  who DESIGN says may do it, then confirm a test proves both the allow and the deny. Missing tests are findings.
- **Rules, attacks.** Join requests made for someone else's uid, approving yourself, writing yourself in as owner;
  a member changing roles, the owner, the member list or the invite code; a member editing another member's user
  document, devices, FCM tokens or time zone (redirecting someone's reminders); activity events forged in someone
  else's name; `updatedAt` or `deletedAt` set to anything but the request time (which would hide changes from the
  changes-only listeners, DESIGN 9.8); `stats` tampering; writes to `config/app`; Clear household data or household
  deletion by a member; data left behind when an account is deleted.
- **Quota abuse.** Size and shape limits on every write (long strings, big arrays, many documents). Can one signed-in
  account burn the project-wide daily quota for everyone? Weigh this against App Check not being adopted (control
  10).
- **Reminder job.** Data from Firestore is untrusted input: a member can write a huge or odd product name. Can one
  household's data break the run for every household (payload over FCM's limit, an exception that stops the loop)?
  The service account's roles; what is logged.
- **Workflows.** Triggers and `permissions:` per job; secrets reachable from forks; `workflow_dispatch` inputs used
  inside `run:` (script injection); actions pinned to a version tag rather than a commit SHA; artifact contents and
  retention; log redaction; the restore workflow's guardrails; the keep-alive.
- **Encrypted exports.** Cipher mode, key derivation, salt and IV handling, whether the ciphertext is authenticated,
  what a wrong key does. (Review the code; don't touch real exports or secrets.)
- **Supply chain.** `npm audit` in the three npm projects; known advisories for the Gradle dependencies (Dependabot
  alerts through `gh api` if allowed); lockfiles; Dependabot coverage; how broad the gitleaks allowlist is.
- **Since the sign-off.** SECURITY_REVIEW was signed on 2026-10-18, before the web app and the meal planner. Re-check
  each control against today's code.

**Method.** Proof-of-concept rules tests live in your scratchpad and run against the emulator, for example from
`firebase/`: `npx firebase emulators:exec --project demo-spoilnot --only firestore "node --test <scratch file>"`.

**Metrics.** Rules matrix coverage (path and operation pairs with both an allow and a deny test, out of all pairs);
open advisories by severity; jobs with least-privilege permissions; actions pinned by SHA.

**Owner checks (consoles, not phones).** Auth: password policy, email enumeration protection, authorized domains
(remove `localhost` from prod?); API key restrictions, including the **web** API keys added after the security
review (HTTP referrer restrictions); GitHub: two-factor, branch protection on `main`, approval for fork pull request
workflows, default workflow permissions set to read.

## 02 Security and privacy: Android and web clients (private)

**Goal.** Make sure the apps don't open doors the rules close, keep local data safe on shared or lost devices, and
store and send only what PRIVACY.md says.

**Read.** DESIGN 7, 10.2, 11.1, 11.6, 12, 18.3 to 18.6; `docs/PRIVACY.md`; the manifests in every source set and the
network security configs; `core/firebase`, `core/network`, `core/navigation/DeepLinks.kt`, `feature/recipes` (import
and web view), `feature/notifications`; `web/src/data`, `web/src/sw.ts`, `web/src/core/push.ts`,
`web/src/core/deepLinks.ts`; `firebase/firebase.json`.

**Check, Android.**

- Exported components: the share target (crafted text and URLs from other apps), notification deep links
  (`PendingIntent` immutable and explicit), anything that opens a screen without passing the session gate.
- The recipe web view and fetcher: re-verify control 12; parsing of `evaluateJavascript` results; response size
  limits, timeouts, redirects, non-http(s) schemes, private network addresses (a shared link to `192.168.x.x`), very
  large JSON-LD; imported text caps (11.6); recipe photos from any URL (a privacy leak of the phone's IP is worth a
  line).
- Local data: `allowBackup` and data extraction rules (could the Firestore cache or DataStore end up in a cloud
  backup or a device transfer?); logs in release builds; what Crashlytics receives (names, emails, food?); the
  invite code on the clipboard (marked sensitive?).
- Sign-out and account deletion: verify on the emulator that the local copy is gone; the device's FCM token removed
  so the phone stops getting the household's digests; what stays on the server versus what PRIVACY.md promises.
- Release builds: the dev benchmark APK (release code) is minified and has no debug-only classes (`apkanalyzer`);
  the merged prod release manifest (`./gradlew :app:processProdReleaseMainManifest`, which doesn't sign or upload)
  is not debuggable, has no local-network permission and no cleartext exceptions. Confirm in `app/build.gradle.kts`
  that only dev debug and dev benchmark get the emulator hosts.

**Check, web.** XSS sinks and URLs from data; how strict the CSP really is; the service worker's scope, what it
caches, push payload handling, `safeRoute`; message handlers checking the origin; auth and Firestore persistence on a
shared computer after sign-out; what sits in localStorage; framing (`frame-ancestors`); third-party requests; the
response headers of the hosted app (a plain public fetch).

**Check, privacy.** A data inventory: every field written (DESIGN 8 against the actual writes), sent (FCM payloads,
lock-screen text), logged (job logs), exported and kept after deletion, with retention (activity pruning, tombstones,
stats, 14-day backups). Compare it line by line with PRIVACY.md. Check committed screenshots and fixtures hold only
made-up data.

**Metrics.** Exported components; data points stored per person against the PRIVACY.md list; mismatches.

**Owner checks.** Android: sign out, then confirm no digest arrives on that phone the next day; what the lock screen
shows for a digest. iPhone: the same after signing out of the web app.

## 03 Bugs in business logic

**Goal.** Find wrong results in the pure logic both platforms rely on, especially at boundaries. Android's
`feature/*/domain` first, then the reminder job's digest and time logic.

**Read.** DESIGN 4, 5, 8.5, 9.3 to 9.6, 10.3 to 10.5, 11.3 to 11.6; every `feature/*/domain` package next to its
tests; `backend/reminder/src/digest.ts` and `time.ts`.

**Check.**

- **Dates and time.** Status boundaries (expired, today, tomorrow, soon, week) at midnight, in time zones from
  UTC-12 to UTC+14, on DST start and end days, on Feb 29 and Dec 31; `YYYY-MM-DD` formatting under other locales
  (Arabic or Persian digits would corrupt stored dates); every `now()` that bypasses the injected `Clock`; week start
  by region in the planner.
- **Quantities.** Decimals and rounding, units, zero and negative, opened parts split into pieces, increments that
  must commute (9.4), recipe scaling with fractions.
- **Restock engine.** Thresholds, items added and removed at the right moments, manual items, products deleted while
  on a list, two phones running it at once.
- **Learning.** Shelf-life and opened shelf-life learning, aisle guesses, store order learned from check-offs (odd
  sequences), recipe names taught to products (the cap of 30), ingredient-to-product linking (plurals, accents,
  case, extra words).
- **Recipe import.** The ingredient parser (unicode fractions, ranges, "a pinch", other languages), the JSON-LD
  extractor (`@graph`, `HowToSection`, ISO durations such as `PT1H30M` or `P0DT…`), the microdata fallback, the
  caps.
- **Undo.** Each destructive action's Undo restores exactly what was there (dates, opened state, list membership),
  including after a partial failure and after another member changed the same thing.
- **Insights, planner, Use it up.** Double counting after Undo, month edges by time zone, the streak; week windows,
  copy last week, moving meals across weeks, servings to the grocery list, Cooked taking from the pantry; Use it up
  ranking.
- **The digest.** Who gets what, the chosen hour per time zone across DST, several devices per person, Meals I'm
  cooking, plurals in the text.

**Method.** Read each function against its tests and list untested branches. Write boundary and property-style tests
to probe them (temporarily in `app/src/test`, removed before finishing; Node tests from the scratchpad). Run
`LiveImportTest` (docs/import-test-sites.md) for the import success rate.

**Metrics.** Domain functions with no direct tests; probe tests written and how many failed; live import success
rate (DESIGN 1 target: 9 of 10 sites); `Clock` bypasses.

## 04 Bugs in sync, sessions and multi-phone behavior

**Goal.** Find ways the phones in one household can disagree, lose data or get stuck, and ways the session state
machine can end up wrong.

**Read.** DESIGN 5.1, 7.2, 8.1, 8.5, 8.6, 9 (all), 10.2; CLAUDE.md "Patterns"; `core/firebase/` (`DeltaSync.kt`,
`FirestoreFlows.kt`, `FirestoreProvider.kt`, `FirestoreHouseholdWriter.kt`), `core/write/`, `feature/*/data/`,
`feature/household/domain/SessionManager.kt`; the two-phone tests in `app/src/androidTest` (`TestPhone`).

**Check.**

- **Changes-only listeners (9.8).** The 5-minute overlap: can a write committed with an earlier server time become
  visible after a later one, and be skipped? Pending server timestamps (null `updatedAt` locally). The 20-hour full
  read: what happens to a phone that was away longer than tombstones live (zombie items, deletes never seen)? The
  `SyncLog` after clearing local data or switching accounts.
- **Listener errors.** `retryAfterOwnWrites` after permission-denied (removed member), retry backoff, retry loops that
  could drain the quota.
- **Session state machine.** Sign-out with unsent writes; removal or household deletion while the member is offline;
  the owner leaving or transferring; a join approved while the joiner's app is closed; account deletion with unsent
  writes; switching accounts on one phone. Any repository or flow still holding the old `FirebaseFirestore` after
  `FirestoreProvider` replaced it (a CLAUDE.md rule).
- **Two members at once.** Both edit one batch (field-level last write wins); both use the same batch up; both press
  Add to pantry on the same checked list (duplicates?); both run the restock engine; both mark a meal cooked; Undo
  on one phone after the other edited; a delete on one phone while the other edits (does a merge write bring a
  tombstoned item back?); a list deleted while the other phone adds to it.
- **Large writes.** Batches near the 500-operation limit (Clear household data, Add all, copy last week) and partial
  failures.
- **Mixed clients.** An Android phone and the web app in one household: does either drop fields it doesn't know when
  it writes?

**Method.** Reproduce on the emulators: the `TestPhone` harness for two-phone cases, or a Node script with two
clients against the Firestore emulator, plus the emulator UI. Go offline with `adb shell cmd connectivity
airplane-mode enable`. Temporary tests are removed before you finish.

**Metrics.** A scenario matrix (scenario: converges, diverges or loses data); time for a change to show on the other
phone, median and p95 (target in 9.2: median under 1 s); retained Firestore references.

**Owner checks.** Two real phones (Android and iPhone web) in one household: check off the same grocery item at the
same moment; both press Add to pantry on one list; one phone in airplane mode edits, the other deletes, then
reconnect.

## 05 Reliability, offline and failure handling

**Goal.** Find what breaks, hangs or confuses when the network, the quota, the data or the platform misbehave, and
check the operations around the app (reminders, backups, updates).

**Read.** DESIGN 6.7, 7.5, 8.6, 9.5, 9.7, 10 (all), 13.3, 13.4, 18.5; RUNBOOK; `core/error`, `core/crash`,
`feature/update`, `feature/notifications`; `backend/reminder/src/job.ts`, `main.ts`, `backup.ts`; the reminders,
nightly-export and restore workflows.

**Check.**

- **Offline.** Every flow in airplane mode: does it work and sync later, or say clearly that it needs a connection?
  (Create household, join, delete account, Clear household data and export need the server.) Cold start offline;
  offline for days, then reconnect; a slow or lossy connection (emulator network profiles); the offline banner and
  the pending-sync icon (6.7).
- **Quota reached (7.5).** The banner, no retry storms, what still works. The emulator can't run out, so use a fake in
  a temporary test or trace the code path.
- **Errors.** Every repository error becomes a typed `AppError` and a message that says what to do
  (`ErrorMessages.kt`); errors swallowed silently; crash-prone code (`!!`, `first()` on lists that can be empty,
  unchecked casts in Firestore mappers).
- **Bad data.** Documents with missing fields, wrong types, null `updatedAt`, or fields from a newer app or the web:
  write them into the emulator by hand and confirm lists still load and nothing crashes.
- **Version skew (8.6).** An older Android build, the current web app and the job in one household; the update gate
  when offline; what `schemaVersion` actually does.
- **Process death and configuration changes.** "Don't keep activities", rotation, dark mode, font size changes on
  every screen with a half-filled form or sheet (Add item, Add to pantry, cook mode, the plan sheet); a share
  arriving while the app is in the background.
- **Reminders.** FCM token refresh; reinstall leaving old tokens (does the job remove unregistered tokens?); Doze,
  battery saver and app standby; the channel turned off; permission revoked; GitHub's hourly cron running late or
  skipping an hour (missed or doubled digests?); one household's failure stopping the run for others; the keep-alive.
- **Backup and restore drill.** Export and restore against the emulator only: a round trip comes back identical
  (stock, lists, recipes, stats, meals, no tombstones); the dry run; encryption round trip; members untouched.
- **Web.** The service worker update path after a deploy; opening offline; storage eviction on iOS.

**Metrics.** Offline matrix (flow: works, clear message, broken); crash-prone patterns; bad-data cases that crash;
screens that lose input on process death; export and restore round trip.

**Owner checks.** Use the app in airplane mode in a real store; leave the Android phone untouched for three days,
then open it; note when the daily digest arrives for a week against the chosen hour; reinstall the beta and confirm
the old install stops getting digests.

## 06 UX, user understanding and redundancy

**Goal.** Judge whether people understand the app and get things done quickly, and find what's confusing,
inconsistent or redundant. Android only (the web copies it; section 10 covers web-only issues).

**Read.** DESIGN 1 (success criteria), 4 (concepts), 5, 6 (especially 6.1, 6.7, 6.9); QA.md;
`app/src/main/res/values/strings.xml`; the Roborazzi screenshots.

**People to walk through as.** The owner setting up the household; a family member with an invite code who has
never read anything; someone in the grocery store with one free hand.

**Check.**

- **First run to value.** DESIGN 1's criterion: install, create a household, add 5 items and get the first
  notification in under 10 minutes. Time it, count taps and screens, and note every spot where the person has to
  guess.
- **User understanding.** Where each concept from DESIGN 4 meets the user, is it explained right there? Use by
  versus Best before, Keep at least, the statuses, owner approval, pausing and rotating the code, opened items,
  store order learning, automatic restock, Use it up, Insights' used versus thrown away. Does the app say why
  something happened (an item appeared on the list by itself, a row moved, a digest count)? Can you tell what the
  app did from what a person did?
- **Copy.** Read every string: plain words, one name per thing everywhere ("batch" or "purchase", "list" or
  "groceries"), sentence case, errors that say what to do (6.9), plurals, truncation at 200%.
- **Redundancy and clutter.** Two ways to do the same thing, two places showing the same information, settings
  nobody needs, confirmations where Undo already exists, steps that could go, overlapping features (Home cards and
  the tabs, Use it up on Home and in the recipe picker, the Today card and the Plan tab). For each: keep, merge or
  remove, and why.
- **Consistency.** Add, edit and delete across Pantry, Groceries, Recipes and Plan; swipe directions and how anyone
  discovers them; Undo snackbar timing; FABs; Back in each tab; sheets versus screens.
- **States (6.7).** Loading, empty, error, offline and partial data on every screen; 0, 1 and 500 items; very long
  names.
- **Speed for frequent tasks.** Taps and seconds for: add an item with a date (6.1 says under 10 s), check off
  groceries, Add to pantry after shopping, find what expires soon, save a recipe from a link.
- **Look.** Hierarchy, spacing, alignment, the six themes, dark mode, motion that helps versus distracts.
- **Prompts.** The notification permission prompt (after the third dated item): timing, wording, what happens if
  denied.

**Method.** The dev debug app on Pixel_8 against the emulators with two made-up households: a brand-new one and a
lived-in one (about 150 batches, several lists, recipes, a planned week). Walk the QA.md flows and Nielsen's ten
heuristics. Screenshots for findings go in `docs/reviews/assets/06/`.

**Metrics.** Taps and seconds per frequent task; time to first value; concepts left unexplained; strings flagged;
redundant elements; screens missing a state.

**Owner checks.** A hallway test: hand the Android phone to someone who has never seen the app, give them five tasks
without help, and write down where they hesitate or ask. The same with the iPhone web app if possible.

## 07 Accessibility

**Goal.** Everyone can use every feature: screen reader users, large text, low vision, motor limits. Android first,
then the web.

**Read.** DESIGN 6.4, 6.8, 18.2; `app/src/androidTest/.../a11y/`; the Roborazzi tests; `core/designsystem/theme`;
`web/src/theme`.

**Check.**

- **Contrast.** Compute every text-on-background and icon-on-background pair the UI uses, for the default theme, a
  sample Material You palette and the five presets, light and dark (script it from `Color.kt`, `PresetColors.kt`
  and `StatusColors.kt` or from the web's generated tokens). WCAG AA: 4.5:1 for text, 3:1 for large text and
  controls. Status chips especially.
- **Not color alone.** Every status shows text or an icon too.
- **TalkBack.** Every screen: labels, roles and states (checked, expanded, selected), rows read as one sentence
  ("Milk, 2, expires tomorrow, fridge", 6.8), focus order, swipe actions also available as accessibility actions or
  in a menu (6.8 requires it), snackbars and Undo announced, focus kept inside sheets and dialogs, Insights charts
  described.
- **Size and reach.** Touch targets of 48 dp; 200% font and the largest display size (clipping, overlap, important
  text cut off); landscape; keyboard and switch access (visible focus, every action reachable); animations off
  (respects the system setting).
- **Automated coverage.** Which screens `AccessibilityChecksTest` covers and which it doesn't; run the same checks on
  the uncovered ones.
- **Web.** axe-core on every screen in Playwright (run from the scratchpad without adding dependencies), the text
  scale table (`textScale.ts`) at the iPhone's accessibility sizes, labels and roles in the kit components.

**Metrics.** Contrast failures by theme and mode; unlabeled controls; swipe-only actions; targets under 48 dp;
screens that clip at 200%; screens covered by automated checks out of all screens.

**Owner checks.** TalkBack on the Android phone: add an item, delete one and undo it, eyes closed. VoiceOver on the
iPhone web app: check off a grocery item. The largest text size on both.

## 08 Smoothness and runtime performance

**Goal.** The Android app feels instant and never stutters. Measure against DESIGN 13.1's budgets and find the
causes of any jank.

**Read.** DESIGN 13.1; RUNBOOK "Baseline profile"; `benchmark/`; `app/src/androidTest/.../perf/`;
`core/designsystem/motion`; `core/firebase/DeltaSync.kt`.

**Check.**

- **Budgets (13.1).** Cold start under 2.5 s, 60 fps scrolling with 500 batches, first household sync under 2 s.
  `StartupBenchmark` and `PantryScrollBenchmark` (cold, warm and hot start).
- **Jank beyond Pantry.** Frame timing on Groceries with many items, the Recipes grid, the Plan week, Insights
  charts, Home with every card, tab switches, opening and dragging sheets, the keyboard opening in Add item.
- **Compose.** Stability reports (enable the compiler's reports and metrics through a Gradle init script in the
  scratchpad, without editing the build); unstable parameters on list rows; missing `key` or `contentType` in lazy
  lists; sorting and filtering inside composables instead of the ViewModel; state read higher than needed;
  recomposition counts on the Pantry row while scrolling.
- **Main thread.** A Perfetto trace of startup and of a scroll: disk or network work on the main thread (DataStore,
  Firestore setup, fonts), long binder calls, garbage collection churn. App startup work in `Application.onCreate`
  and eager Hilt singletons.
- **Flows.** `collectAsStateWithLifecycle`, `stateIn` with `WhileSubscribed`, heavy mapping on the main dispatcher,
  the cost of merging snapshots in `DeltaSync` on large collections.
- **Images and memory.** Coil decoding recipe photos at the size shown; memory after visiting every tab ten times
  (`dumpsys meminfo`), rising memory as a sign of a leak.
- **Baseline profile.** Do the newest screens appear in the profile journey, and is the committed profile still
  current?
- **Feel.** Consistent durations and easing (motion tokens), item animations in lists, sheet drags, haptics.

**Method.** Pixel_8 with the benchmark build and its seeded data (500 batches). Run each measurement 3 to 5 times and
report the median and spread. Say clearly that emulator numbers are relative, not real-phone numbers.

**Metrics.** Cold and warm start medians; frame time P50, P90 and P99 and jank percentage per scenario; unstable
composables; recompositions per scroll; memory after the loop; main-thread blocking during startup.

**Owner checks.** On the Android phone: Developer options > Profile HWUI rendering, then scroll Pantry and Groceries
and note the bars crossing the line; three timed cold starts; any stutter in sheets or when the keyboard opens.

## 09 Efficiency: quota, battery, size, network and CI

**Goal.** Spend as little of the free quota, the battery, the data plan and the build time as possible, and know how
many households fit on the Spark plan.

**Read.** DESIGN 9.1, 9.7, 9.8, 10.3, 10.4, 13.2, 18.5, 18.6; RUNBOOK "Check quota usage"; `firebase/tools/
quota-sim.mjs`; `core/firebase/DeltaSync.kt`, `FirestoreFlows.kt`; `web/src/data/delta-sync.ts`,
`firestore-rx.ts`.

**Check.**

- **Reads.** Every listener and one-off read on Android and the web: what, when it attaches, when it detaches (app in
  the background, web tab hidden), two screens listening to the same thing, full re-reads after a cold start, reads
  per cold start. The reminder job's reads per hourly run and per day, and the nightly export's. Does quota-sim model
  web members and the meal planner? Where the limit breaks first (reads, writes or deletes) and how many households
  fit (DESIGN 9.8 says about 18).
- **Writes.** Writes that change nothing, the restock engine on every phone writing the same item, store-order
  learning on every check-off, the web writing the time zone at every start.
- **Growth.** Activity events, tombstones, stats and meals over a year; estimated size per household.
- **Battery and background.** Work done while the app is in the background, wake-ups, WorkManager jobs,
  `dumpsys batterystats` after an idle hour.
- **Network.** Payload sizes, recipe photo sizes and caching, recipe import download limits.
- **Size.** APK breakdown (`apkanalyzer`): R8, resource shrinking, fonts, the licenses asset, the baseline profile.
  The web bundle: JS and CSS gzipped, code split by tab or not, unused icons, Firebase modules, the service worker's
  precache size, first-load bytes.
- **CI and the dev loop.** Workflow durations, path filters, caching, overlapping jobs; how long the finishing checks
  take (from section 00) and what could run in parallel; the reminder job's run time, 24 times a day.

**Method.** quota-sim plus extra scenarios (a web member, cold starts per day, the app in the background); request
counts from the Firestore emulator for scripted sessions; `apkanalyzer`; the Vite build output.

**Metrics.** Reads, writes and deletes per household per day (Android only, and mixed with web); households that fit
under 70% of the quota; reads per cold start; the job's reads per day; APK size; web first-load and precache size
(gzipped); background wake-ups per hour; CI minutes per pull request.

## 10 Web app: parity with Android and iPhone issues

**Goal.** The web app behaves like the Android app (except the exceptions in 18.1), shares data with it safely, works
well on an iPhone, and stays easy to port to. Android is the reference.

**Read.** DESIGN 18 (all) and 6; CLAUDE.md's web rules; `web/` (`scripts/gen-*.ts`, `src/domain`, `src/data`,
`src/ui`, `src/sw.ts`, `tests/`), next to their Android counterparts.

**Check.**

- **Feature parity.** A table of every Android feature, screen, setting and message with its web status (same,
  differs, missing, documented exception). Android changes since the port (`git log` since 2026-10-29) that never
  reached the web.
- **Logic parity.** Differential tests: feed the same inputs to the Kotlin and TypeScript versions of each ported
  domain module (status and time, restock, aisles, items, insights, plan, recipe scaling and matching, the session
  state machine, delta sync) and compare outputs. Reuse the ported test cases and add generated ones (dates, time
  zones, quantities). A temporary Kotlin test writes outputs to JSON; Vitest compares (both removed afterward).
- **Data compatibility.** Documents written by one platform read correctly by the other (names, types, optional
  fields, server timestamps, tombstones, web devices); unknown fields kept. A mixed household on the emulators
  (Pixel_8 and WebKit).
- **Generated assets.** `npm run gen:check`; Android strings missing on the web, web strings Android doesn't have.
- **iPhone.** Home Screen app mode: safe areas, the notch and home indicator, the status bar; the on-screen keyboard
  covering fields and sheets; rubber-band scrolling; `100vh`; zoom on focus for small inputs; the date picker; swipe
  actions versus the iOS back-swipe edge; long press; push permission from a tap only (iOS 16.4+, Home Screen only);
  the Update snackbar; storage eviction; landscape; Dynamic Type.
- **Web performance and accessibility.** Lighthouse mobile on the preview build; scrolling 500 batches in WebKit and
  interaction delay; React re-render hot spots; axe (if section 07 didn't already).
- **Porting cost.** Where the web's structure has drifted from Android's in ways that make the next port error-prone.
  Suggest a parity checklist for Android pull requests.

**Metrics.** Parity table counts; differential mismatches; Android changes not ported; Lighthouse scores; first-load
size; QA flows 28 to 36 covered by e2e tests.

**Owner checks.** On the iPhone: install from Safari; add an item with the keyboard open (does the sheet stay
visible?); check off groceries with one hand; a push at the chosen hour and opening it; rotate; the largest text
size.

## 11 Code quality, redundancy, tests and docs

**Goal.** The code stays easy and safe to change: the architecture rules hold, nothing is duplicated or dead, the
tests protect what matters, and the docs say what the code does.

**Read.** CLAUDE.md; DESIGN 7, 13 and Appendix B; every doc in `docs/` and the README.

**Check.**

- **Architecture rules (CLAUDE.md), by search.** Firebase classes outside `feature/*/data` and `core/firebase`;
  composables or ViewModels touching Firebase; business logic outside `domain`; `Clock` not injected; dates not
  `YYYY-MM-DD`; user-facing text outside strings.xml; repositories keeping a `FirebaseFirestore`; destructive actions
  without Undo; status by color alone.
- **Redundancy.** Duplicated code (copied composables, forms, mappers); dead code; unused strings, drawables and
  libraries in `libs.versions.toml`; interfaces with one implementation that add nothing (keep the ones tests need);
  tests that check the same thing several times; facts kept in several docs (DESIGN, DECISIONS, RUNBOOK, README,
  SECURITY_REVIEW) that can drift. Propose one home per fact.
- **Complexity.** The largest files and functions (recipes is about 5,100 lines, pantry about 3,700), deep nesting,
  ViewModels doing too much.
- **Tests.** Map DESIGN's acceptance criteria and the QA.md flows to automated tests and list the gaps; test smells
  (sleeps, reliance on retries, over-mocking, asserting implementation); the slowest tests; screens without
  screenshot tests.
- **Dependencies and build.** Versions against latest stable (AGP is held back on purpose); unused or overlapping
  libraries; licenses in the open-source list; configuration and build caches; a lint baseline hiding issues.
- **Docs.** Spot-check every MUST in DESIGN 5 to 13 against the code and list mismatches; RUNBOOK commands; QA.md
  against today's screens; the README's claims (such as "about 360 automated tests"); CLAUDE.md.

**Metrics.** Rule violations; unused resources, strings and libraries; duplicated blocks; largest files; lint
warnings; test gaps against DESIGN and QA.md; doc mismatches.

## 12 Triage and fix roadmap (with the owner)

**Goal.** Turn the reports into an ordered set of fix pull requests the owner has agreed to.

**Do.**

1. Read every report, public and private, and the owner's phone check results. Merge duplicates across sections and
   rate severity the same way throughout.
2. Settle the Questions and Ideas with the owner: what to fix, what to accept, which redundant features to remove.
3. Group the fixes into batches, one pull request each. Each batch lists its findings, the DESIGN sections, DECISIONS
   entries and tests it brings, and whether it needs a rules deploy, a `minSupportedBuild` raise or a beta release.
   Android first; each batch says whether the web port goes in the same pull request or the next one.
4. Order: P0 security right away, outside the queue; then P1 bugs and data loss; then reliability; then UX and
   accessibility; then smoothness and efficiency; then code quality.
5. Write `docs/reviews/ROADMAP.md` and `docs/reviews/private/SECURITY-ROADMAP.md`.

**After the fixes.** Rerun section 00 and compare. Once the security fixes have shipped (rules deployed and the
release out), publish a sanitized security report. When `review/2026-11` becomes a pull request, it adds a row to
DESIGN 17 and a DECISIONS entry (security findings were kept private until fixed), and it's pushed only with the
owner's OK.
