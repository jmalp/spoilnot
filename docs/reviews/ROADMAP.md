# 12 Triage and fix roadmap

Triage of the in-depth review on branch `review/2026-11`, done with the owner on 2026-11-08. The inputs are reports
00 and 03 to 11, plus the private security reports and notes in `docs/reviews/private/`, which are uncommitted until
fixed. The owner hasn't run the phone checks yet; the batches they affect say so.

## Summary

- **Findings:** the public reports hold 127 findings: 12 P1, 26 P2 and 89 P3, with no P0.
  - Ten of section 10's web findings confirm an earlier Android finding. Each pair goes in the same batch.
  - The private reports add 30 findings and 8 notes. Their two P0s are already fixed and merged (#72, #74).
- **Batches:** everything is grouped into **32 batches, one pull request each**: 28 public (B01 to B28) and 4 private
  (S1 to S4, described only in `private/SECURITY-ROADMAP.md`). Every finding is in exactly one batch.
- **Order:** P1 bugs and data loss first, then reliability, UX and accessibility, the remaining logic bugs,
  smoothness and efficiency, and code and docs last.
- **Biggest risks to users:**
  - reminders arrive late or not at all (B01);
  - offline deletes that come back (B02);
  - the restock engine putting staples straight into the cart (B04);
  - the app turning into a spinner when the quota runs out (B05);
  - two phones finishing the same thing twice (B08);
  - one sync shortcut that hides other members' changes for hours (B07).
- **Biggest risk to the free plan:** listeners that never let go (B09), which could cut the households that fit from
  about 17 to 5–9.

## Already done

| PR | What | Still to do |
| --- | --- | --- |
| jmalp/spoilnot#72 | Reminder job: digests go only to current members; job errors print codes only | Nothing: the job runs from `main` |
| jmalp/spoilnot#74 | Sign-out and account deletion finish even when Settings closes | **Release an Android beta** so testers get it; the web side is batch S1 |

## Owner's decisions (2026-11-08)

| # | Topic | Decision | Batch |
| --- | --- | --- | --- |
| 1 | Best before can't be chosen (UX-04) | **Leave it as is.** Record it in DECISIONS, and make DESIGN 5.2 describe what the app does | B17 |
| 2 | First notification within 10 minutes (UX-03) | **A local "You're set" notification** right after Turn on, saying when reminders come and what's due tomorrow. DESIGN 1's criterion stays | B16 |
| 3 | Reminder schedule (REL-03) | **Three schedule entries an hour** plus a daily prune that doesn't depend on the 07:00 hour, then measure for a week. Plan B (Cloud Functions) stays out | B01 |
| 4 | Redundancy (06's table) | **Merge** Low stock into the Grocery list card (R1, R2). **Undo with no dialog** for Clear this week and Delete recipe (R13). **Remove** Pause joining (R12). **Stop logging** check-offs in Recent activity (R15) | B15, B17 |
| 5 | When the web port lands | **Shared data together:** fixes that change what's written or synced (rules, sync, mappers, domain rules both apps apply) go in one PR for both apps. UI and copy fixes land on Android first, with the web port as the next PR | all |
| 6 | Add to pantry has no Undo (LOGIC-10) | **Add an Undo** that removes the new batches and puts the items back. Delete list keeps its confirmation, recorded in DECISIONS | B14 |
| 7 | Android screenshots never compared (CODE-07) | **Commit Roborazzi references** and run `verifyRoborazziDevDebug` in CLAUDE.md's finishing checks (not in CI) | B19 |
| 8 | Daily mode stops after 14 days (LOGIC-07) | **Reword it** to "Every day for two weeks" | B17 |
| 9 | Three security and privacy questions | Settled; see `private/SECURITY-ROADMAP.md` | S2, S4, B13 |

**Defaults I chose (say if you disagree):**

- **Kept:** review 06's other "keep" verdicts (R3 to R7, R11, R14).
- **One expiring set (R8):** Needs attention, the Expiring soon filter and the digest use one set under one name (B15).
- **Recipes:**
  - remove the servings chip and the duplicate "By" line (R9, R10, B17);
  - remove Recipes' All chip, as Pantry did (R16, B17).
- **Undo of Used up (LOGIC-09):** leaves the staple on the list; record that in DECISIONS, with no code change (B04).
- **Groceries swipes (UX-10):** swipe right to check off and left to delete, with both shown under the row (B17).
- **First-sync budget (PERF-05):** no change to DESIGN 13.1 until the owner's phone check measures it on 4G (B22).
- **Week start on the web (WEB-08):** a "Week starts on" setting in Settings (B20).
- **Ideas:** the reports' Ideas sections aren't scheduled, so pick from them when planning new work.

## How every batch is done

- **One PR per batch,** from `main`, each in its own worktree session. Batches marked "any time" can run alongside
  another batch, provided they touch different files.
- **Docs in the same PR (CLAUDE.md):**
  - the DESIGN sections listed;
  - a row in DESIGN 17;
  - DECISIONS entries;
  - tests;
  - RUNBOOK, QA, PRIVACY and SECURITY_REVIEW where affected.
- **Finishing checks:** CLAUDE.md's list, plus `cd web && npm run gen` whenever Android strings or colors change.
  Generated web files always land in the same PR as the Android change, even when the web's UI port comes next.
- **Merging is not releasing.**
  - Job changes take effect at the next scheduled run after merging to `main`.
  - Each rules deploy, Android beta, web deploy and `minSupportedBuild` or `minWebBuild` raise needs the owner's
    go-ahead.
  - The "Ships via" column says which a batch needs.
- **Phone checks:** after a batch ships, the owner runs the reports' phone checks for its findings.

## Order at a glance

**Phase 1: security and P1 (data loss, broken core)**

| # | Batch | Findings | Worst | Web port | Ships via | Effort |
| --- | --- | --- | --- | --- | --- | --- |
| B01 | Reminder job: three runs an hour, daily prune, one failure doesn't stop the run | REL-03, REL-05, REL-06, EFF-02, BASE-06, LOGIC-06 + private | P1 | — (backend) | Job on merge | M |
| S1 | Private | — | — | — | Android beta, web | S |
| S2 | Private | — | — | — | Rules deploy | M |
| B02 | Deleted things disappear offline | REL-01, WEB-02 | P1 | Same PR | Android beta, web | S |
| B03 | Name the floating buttons | A11Y-01, A11Y-05, CODE-12 | P1 | — (Android only) | Android beta | S |
| B04 | Restock engine: no staples in the cart, none on first sync | LOGIC-01, LOGIC-02, SYNC-10, EFF-09, LOGIC-05, LOGIC-09 | P1 | Same PR | Android beta, web | M |
| B05 | Stay usable when the quota runs out | REL-02, WEB-04, WEB-09, REL-10 | P1 | Same PR | Android beta, web | M |
| B06 | Web update check and `minWebBuild` | REL-15 | P3 | — (web only) | Web | S |
| B07 | Sync cursor from what the server sent | SYNC-01, WEB-16 | P1 | Same PR | Android beta, web | M |
| B08 | One phone wins when two finish the same thing | SYNC-02, WEB-01, SYNC-05, SYNC-07 + private | P1 | Same PR | Android beta, web, then rules deploy and minimum-build raise | M |
| B09 | Listeners that let go | EFF-01, PERF-02, EFF-03, EFF-05 | P1 | Next PR | Android beta, then web | L |

**Phase 2: reliability**

| # | Batch | Findings | Worst | Web port | Ships via | Effort |
| --- | --- | --- | --- | --- | --- | --- |
| B10 | Bad data skips one document, not the screen | REL-04, WEB-10, REL-14, REL-13, REL-11 + private | P2 | Same PR | Android beta, web, restore code | M |
| B11 | Edits don't undo someone else's change | SYNC-03, WEB-03, SYNC-04, WEB-07 | P2 | Same PR | Android beta, web | M |
| B12 | Joining and sign-out edges | SYNC-06, WEB-05, SYNC-09, SYNC-08, UX-13 + private | P2 | Same PR | Android beta, web, job | M |
| B13 | Restore and Export tell the truth | REL-07, REL-12, WEB-12 + private | P2 | Same PR | Android beta, web, job | S–M |
| B14 | Forms keep what you typed; Undo for Add to pantry | REL-08, REL-09, LOGIC-03, LOGIC-04, LOGIC-08, LOGIC-10 | P2 | Same PR (writes), Next PR (form state) | Android beta, web | M |
| S3 | Private | — | — | — | CI and owner settings | S |

**Phase 3: UX and accessibility**

| # | Batch | Findings | Worst | Web port | Ships via | Effort |
| --- | --- | --- | --- | --- | --- | --- |
| B15 | Home tells the truth | UX-01, UX-06, UX-05, UX-07, UX-15, UX-16 | P2 | Next PR | Android beta, job (digest link), then web | M |
| B16 | Reminders people can count on | UX-02, UX-03, CODE-10 | P2 | Next PR | Android beta, then web | S–M |
| B17 | Clear words and one rule for confirmations | UX-18, UX-08, UX-09, UX-10, UX-11, UX-12, UX-04, LOGIC-07 | P2 | Strings same PR, UI next PR | Android beta, then web | M |
| B18 | Contrast and time to act | A11Y-02, A11Y-03, UX-17, CODE-11 | P2 | Same PR (generated colors), Next PR (web timer) | Android beta, web | S |
| B19 | Large text, screen readers and keyboard | A11Y-04, UX-14, A11Y-06, A11Y-07, A11Y-08, A11Y-09, A11Y-10, A11Y-11, A11Y-12, CODE-07 | P2 | Next PR | Android beta, then web | M |
| B20 | iPhone polish | WEB-14, WEB-15, WEB-08 | P3 | — (web only) | Web | S |
| S4 | Private | — | — | — | Android beta, web | S |

**Phase 4: the remaining logic bugs**

| # | Batch | Findings | Worst | Web port | Ships via | Effort |
| --- | --- | --- | --- | --- | --- | --- |
| B21 | Recipes, plan and names | LOGIC-13, LOGIC-14, LOGIC-15, LOGIC-16, LOGIC-17, WEB-06, LOGIC-11, LOGIC-12 | P3 | Same PR | Android beta, web | M |

**Phase 5: smoothness and efficiency**

| # | Batch | Findings | Worst | Web port | Ships via | Effort |
| --- | --- | --- | --- | --- | --- | --- |
| B22 | Benchmark setup and a current profile | PERF-01, BASE-08, PERF-05, PERF-06, BASE-02 | P2 | — | Android beta | S |
| B23 | Lists that redraw less | PERF-07, PERF-08, PERF-04, CODE-09, WEB-11, WEB-13 | P3 | Next PR | Android beta, then web | M |
| B24 | Insights, tab switches and motion | PERF-03, PERF-09, PERF-10 | P3 | Next PR | Android beta, then web | M |
| B25 | The job reads only the users who are due | EFF-04 | P3 | Same PR | Job, Android beta, web | M |
| B26 | Smaller downloads and faster CI | EFF-06, BASE-05, EFF-07, EFF-08, CODE-05, BASE-03 | P3 | — | Android beta, web, CI | S–M |

**Phase 6: code and docs**

| # | Batch | Findings | Worst | Web port | Ships via | Effort |
| --- | --- | --- | --- | --- | --- | --- |
| B27 | Docs catch-up (any time) | CODE-02, CODE-03, CODE-04, BASE-04, BASE-07, CODE-06 | P3 | — | Docs, Android beta (licence list) | M |
| B28 | Tests and dead code | CODE-01, CODE-08, BASE-01 | P2 | — | Tests only | M |

## Batches

### B01 · Reminder job: three runs an hour, a daily prune, and one failure doesn't stop the run

- **Findings:** REL-03 (P1), REL-05, REL-06, EFF-02, BASE-06, LOGIC-06, plus five private items (SECURITY-ROADMAP).
- **Change:**
  - `reminders.yml` gets three schedule entries an hour, such as minutes 7, 27 and 47. A test keeps the job at one
    digest a day per user.
  - The prune runs in the first run of each UTC day, keyed on a job-only document (`jobs/state.lastPruneDate`), and
    the summary prints the last prune date.
  - Each household and user is wrapped in try/catch. The summary counts failures with no IDs, the run carries on, and
    it exits non-zero at the end so GitHub still emails.
  - The digest title is decided by distinct names.
  - quota-sim counts deletes, activity events and housekeeping reads, and gets `RUNS` and `WEB` knobs.
- **DESIGN:** 9.7, 9.8 (re-state how many households fit, about 17), 10.1, 10.3, 10.4, 10.6 (Plan B closed: no Cloud
  Functions), 13.4; RUNBOOK; PRIVACY's retention line.
- **DECISIONS:** three schedule entries an hour; a prune keyed on the UTC date; a failed send no longer ends the run.
- **Tests:**
  - Job unit tests: a throwing sender (05's Appendix E), the prune date, the digest title.
  - The integration test.
  - A quota-sim rerun compared with RUNBOOK.
- **Ships via:** the next scheduled run after merging. No app release, no rules deploy.
- **After a week:** compare start times by hour (`gh run list --workflow reminders.yml`). If whole hours are still
  missing, revisit the scheduler (a free external cron, or DESIGN 10.6's Plan C for Android).

### B02 · Deleted things disappear offline

- **Findings:** REL-01 (P1) and WEB-02.
- **Change:** a document counts as deleted when `deletedAt` is present. This includes an estimated server timestamp:
  `ServerTimestampBehavior.ESTIMATE` in `liveDocuments`, and `serverTimestamps: 'estimate'` in `web/src/data/delta-sync.ts`.
- **DESIGN:** 9.8.
- **DECISIONS:** "deleted" means the field is present, so Undo always restores with a full write.
- **Tests:**
  - Android: an instrumented test with the network off (delete, then check the item is gone; Add to pantry, then
    check the cart is empty).
  - Web: the same in WebKit with `setOffline(true)`.

### B03 · Name the floating buttons

- **Findings:** A11Y-01 (P1), A11Y-05 and CODE-12.
- **Change:**
  - A `SpoilNotFab(text, icon, onClick)` wrapper gives Add item, Add N to pantry, Add recipe and Cook their names.
  - `AccessibilityChecksTest` covers the 13 screens and their sheets, plus its own rule: every clickable or toggleable
    node has a name.
  - `@SdkSuppress(minSdkVersion = 34)` on both classes.
- **DESIGN:** 6.8; QA.md.
- **Tests:** the extended checks, which fail on the old FAB.
- **Unblocks:** B22, since the profile journey can then find Add item.

### B04 · Restock engine: no staples in the cart, none on first sync

- **Findings:** LOGIC-01 (P1), LOGIC-02, SYNC-10, EFF-09, LOGIC-05 and LOGIC-09.
- **Change:**
  - When the engine creates or restores an item, it writes `checked: false` with no `checkedBy` or `checkedAt`.
  - It evaluates only after both products and stock have had one server snapshot this session, or after a full read
    in `SyncLog`.
  - Its listener retries with a backoff.
  - The phone whose own write caused the drop acts at once; other phones act only after a short delay, if the item
    still isn't there.
  - Editing an older staple keeps Keep at least.
  - LOGIC-09 gets a DECISIONS entry only.
- **DESIGN:** 5.2, 5.3, 9.6.
- **DECISIONS:** LOGIC-09 accepted; how older staples are handled (LOGIC-05).
- **Tests:** restock unit tests (a tombstone brought back, first sync, two phones evaluating at once), `RestockFlowTest`
  and the web's domain tests.

### B05 · Stay usable when the quota runs out

- **Findings:** REL-02 (P1), WEB-04, WEB-09 and REL-10.
- **Change:**
  - `SessionManager`, and its web port, keep the last known session while retrying any error that isn't a permission
    refusal.
  - The quota banner sits above the app's root on both apps; the web gets `QuotaSignal`.
  - Changes-only listeners keep the cached list on screen.
  - Android's Groceries gets the offline banner.
- **DESIGN:** 6.7, 7.5, 18.1.
- **Tests:**
  - Session tests with a fake that throws `resource-exhausted`, on both apps.
  - Banner screenshots.
- **Phone check:** 05's quota check settles the real-Firestore behavior (REL-02 is Likely there).

### B06 · Web update check and `minWebBuild`

- **Findings:** REL-15.
- **Change:**
  - Call `workbox.update()` when the app becomes visible, at most hourly.
  - Read `config/app.minWebBuild`, and show the Update prompt without the 10-second limit when the build is older.
- **DESIGN:** 18.5.
- **DECISIONS:** `minWebBuild`, set by the owner like `minSupportedBuild`.
- **Tests:** a unit test of the gate.
- **Why this early:** B07, B08 and B11 change what both apps write, so old iPhone builds must be made to update.

### B07 · Sync cursor from what the server sent

- **Findings:** SYNC-01 and WEB-16 (both P1).
- **Change:**
  - `SyncLog` keeps, per collection and meal window, the newest `updatedAt` seen in a server (not cache) snapshot.
  - The cursor is that value minus the margin. Own writes and other queries can't move it.
  - On the web this is stored next to the full-read records.
- **DESIGN:** 9.8.
- **DECISIONS:** where the cursor comes from.
- **Tests:**
  - Android: an instrumented test like 04's `p01`.
  - Web: a browser test with an injectable margin.
  - A mixed-household run like 10's.

### B08 · One phone wins when two finish the same thing

- **Findings:** SYNC-02 and WEB-01 (both P1), SYNC-05 and SYNC-07, plus one private item.
- **Change:**
  - **Rules:**
    - refuse a second tombstone on a grocery item, so the later Add to pantry batch fails whole;
    - a meal goes from not cooked to cooked only once;
    - items are created only under an existing list.
  - **Both apps say why:** "Sam already added these to the pantry" or "That list was deleted".
- **DESIGN:** 5.3, 8.5, 9.4.
- **DECISIONS:** the rules decide the winner; the cost of one extra read per item write.
- **Tests:** rules tests, two-phone instrumented tests (04's `p02` and `p03`), and an Android plus WebKit check.
- **Ships via:**
  1. Release the apps.
  2. Deploy the rules.
  3. Raise `minSupportedBuild` and `minWebBuild` once testers have the release, so old builds never show a raw error.

### B09 · Listeners that let go

- **Findings:** EFF-01 (P1; the billing is Likely), PERF-02, EFF-03 and EFF-05.
- **Change:**
  - One shared pantry flow per household (`shareIn(appScope, WhileSubscribed(5_000))`) for every screen and the
    restock engine.
  - Mapping and state building run off the main thread (`flowOn(Dispatchers.Default)`, `conflate()`).
  - The engine reads the cache only.
  - The `init` collectors in `PantryViewModel` and `GroceriesViewModel` go.
  - Household listeners start when an activity starts, not in `Application.onCreate`.
  - Split it into two PRs if it grows: the shared flow and off-main work first, then the cache-only engine.
- **DESIGN:** 6.4, 9.1, 9.6, 9.7, 9.8; RUNBOOK's quota table.
- **DECISIONS:** one shared flow; the engine reads the cache only.
- **Tests:**
  - Unit tests of the shared flow and the engine.
  - quota-sim with 09's scenarios.
  - The scroll benchmark before and after (with B22's setup).
- **Before starting:** the owner's phone check 2 in 09 (the console's usage page after a normal day) sizes the
  billing effect. The fix is worth doing either way, for PERF-02.

### B10 · Bad data skips one document, not the screen

- **Findings:** REL-04, WEB-10, REL-14, REL-13 and REL-11, plus one private item.
- **Change:**
  - Mappers read field by field with safe casts. Each document maps on its own, and one that fails is skipped and
    logged.
  - `.catch { }` becomes a retry with DESIGN 6.7's "Couldn't load. Retry".
  - The restore converts only the known timestamp fields.
  - Write errors are collected once, in the shell.
  - A cleanup the rules refused isn't retried.
- **DESIGN:** 6.7, 8.6, 13.4.
- **Tests:**
  - Mapper tests with wrong types, on both apps.
  - A backup unit test.
  - 05's bad-data documents rerun.

### B11 · Edits don't undo someone else's change

- **Findings:** SYNC-03, WEB-03, SYNC-04 and WEB-07.
- **Change:**
  - The Edit window writes only the fields the person changed, with a changed quantity written as an increment.
  - While open, it watches the batch: "Sam changed this item. Review", or it closes with "Sam removed this item".
  - −1 is always an increment.
  - Undo restores with a merge plus `deleteField()`, so unknown fields and `createdBy` stay.
- **DESIGN:** 8.6, 9.2, 9.4.
- **DECISIONS:** what Undo keeps.
- **Tests:** unit tests, two-phone instrumented tests, and mapper round trips on both apps.

### B12 · Joining and sign-out edges

- **Findings:** SYNC-06, WEB-05, SYNC-09, SYNC-08 and UX-13, plus two private items.
- **Change:**
  - **Joining:**
    - Check the membership before saying Expired.
    - The job prunes only pending and declined requests, and approved ones once they're completed.
    - The waiting screen says the owner gets no notification.
  - **Sign-out:** "Some changes haven't synced yet. Sign out anyway?"
  - **Clear household data:** refuses to start offline, and keeps going after a timeout.
- **DESIGN:** 4, 10.1, 16.
- **Tests:** session tests on both apps, the job's prune test, and an instrumented test of the waiting screen.

### B13 · Restore and Export tell the truth

- **Findings:** REL-07, REL-12 and WEB-12, plus two private items.
- **Change:**
  - **Restore:** stamps every document with a server `updatedAt` (and `updatedBy: 'restore'`), so phones show it
    within a minute.
  - **Export:**
    - reads from the server, or says "You're offline";
    - also includes the person's own profile and the household's activity log (the owner's decision).
- **DESIGN:** 13.4; RUNBOOK; PRIVACY.
- **Tests:** the restore integration test (checks `updatedAt`), and export tests on both apps.

### B14 · Forms keep what you typed, and Add to pantry gets Undo

- **Findings:** REL-08, REL-09, LOGIC-03, LOGIC-04, LOGIC-08 and LOGIC-10.
- **Change:**
  - **Add to pantry:**
    - keeps its drafts by item ID across rotation, process death and others' check-offs;
    - uses the item's linked product;
    - locks the unit to the product's.
  - **Plan sheets:** keep their picks.
  - **Quick add:** Increase quantity only adds matching units, and the parser accepts IngredientParser's unit words.
  - **Undo for Add to pantry:** removes the new batches, and any products it created, and puts the items back.
- **DESIGN:** 5.2, 5.3, 6.1.
- **DECISIONS:** Delete list keeps a confirmation instead of an Undo.
- **Tests:** `GroceryServiceTest` cases, a Compose test that changes `items` under an edited row, and an Undo test.
- **Web port:**
  - The write changes (LOGIC-03, 04, 08, 10) go in the same PR.
  - The form-state fixes for the web's own forms go in the next PR.

### B15 · Home tells the truth

- **Findings:** UX-01, UX-06, UX-05, UX-07, UX-15 and UX-16.
- **Change:**
  - **Needs attention:**
    - Lists what can still be used first, with expired items folded into one line.
    - One set and one name for Home, the Pantry filter and the digest's link (R8).
  - **Shopping card:** one card instead of Low stock and Grocery list (R1, R2).
  - **Use it up:** leaves out recipes already planned this week.
  - **Keep some in stock:** the switch is reworded, and a new household gets a sentence on how to start.
  - **Plan's empty week:** a sentence and a Copy last week button.
  - **Check-offs:** no longer logged in Recent activity (R15).
- **DESIGN:** 5.2, 5.4, 5.6, 5.9, 6.7, 8.2.
- **DECISIONS:** R1/R2, R8, R15.
- **Tests:** Home tests and screenshots, and Use it up tests.

### B16 · Reminders people can count on

- **Findings:** UX-02, UX-03 and CODE-10.
- **Change:**
  - **Asking for notifications:** a member who joins, or signs in on a new phone, is asked once the household opens
    with dated food in it.
  - **When blocked:** Home shows "Reminders are off on this phone · Turn on", and Settings says "Off on this phone".
  - **Right after Turn on:** a local notification gives the reminder time and tomorrow's items.
  - **The prompt rule:** moves to `feature/notifications/domain`, as on the web.
- **DESIGN:** 1, 5.4, 10.3, 18.4.
- **DECISIONS:** the local "You're set" notification.
- **Tests:** a domain unit test, and a QA flow for a member.

### B17 · Clear words and one rule for confirmations

- **Findings:** UX-18, UX-08, UX-09, UX-10, UX-11, UX-12, UX-04 (accepted) and LOGIC-07 (reworded).
- **Change:**
  - **Copy:** review 06's 29 flagged strings.
  - **What the app did by itself:** "Added automatically" with the reason, one "Back in stock · Remove" line, and a
    one-time hint about aisle order.
  - **Add item:** Name is focused, and the category comes from the aisle guess.
  - **Groceries swipes:** right checks off, left deletes; the tour says so.
  - **Confirmations:** Undo with no dialog wherever Undo exists (Delete recipe, Clear this week).
  - **Recipes:**
    - "Start cooking" no longer covers I cooked this;
    - one servings control and one source line (R9, R10);
    - Recipes' All chip goes (R16).
  - **Removed:** Pause joining (R12). The rules keep accepting its field until old builds are gone.
  - **Daily mode:** "Every day for two weeks".
- **DESIGN:** 2, 5.2, 5.3, 5.4, 5.5, 5.8, 6.1, 6.9, 10.1, 16.
- **DECISIONS:** UX-04 accepted; R12 removed; the confirmation rule.
- **Tests:** screenshots, and UI tests for the swipes and Undo.

### B18 · Contrast and time to act

- **Findings:** A11Y-02, A11Y-03, UX-17 and CODE-11.
- **Change:**
  - **Undo snackbar:** on Android, the timeout comes from `calculateRecommendedTimeoutMillis` (8 s stays the
    default); the web pauses the timer while the snackbar has focus or hover.
  - **Contrast:** the test checks chip text on the blended chip background, and the three light status tones get
    darker.
  - **Presets:** their primary colors stay away from the status hues.
  - **Tests consolidated:** `StatusColorsContrastTest` folds into `ColorThemesContrastTest`.
- **DESIGN:** 5.2, 6.1, 6.4.
- **Tests:** `ColorThemesContrastTest` on every preset, light and dark.

### B19 · Large text, screen readers and keyboard

- **Findings:** A11Y-04, UX-14, A11Y-06 to A11Y-11, A11Y-12 and CODE-07.
- **Change:**
  - **Large text:**
    - From 1.5× text, chips go under names.
    - Tab labels stay on one line.
    - Lists get padding the height of the floating button.
  - **Touch:** 48 dp drag handles.
  - **Screen readers:**
    - Rows and cards read as one sentence, and roles and states are fixed.
    - Group headers become headings.
    - The tour hides the screen behind it.
  - **Keyboard:** content comes before the tab bar, with a visible focus ring.
  - **Web port (A11Y-12):** the empty heading, the progress bar's name, and tests at 312%.
  - **Screenshots:**
    - Roborazzi references are committed and verified locally.
    - 2.0× images are added for the 19 screens that lack them.
- **DESIGN:** 6.2, 6.4, 6.8; QA.md; CLAUDE.md's finishing checks.
- **Tests:** Roborazzi at 2.0 and `AccessibilityChecksTest`.
- **Phone check:** 07's TalkBack checks.

### B20 · iPhone polish

- **Findings:** WEB-14, WEB-15 and WEB-08.
- **Change:**
  - **Keyboard:** sheets follow `visualViewport` so Save stays above the keyboard, and the focused field scrolls into
    view.
  - **Swipes:** a swipe that starts at Safari's left edge is ignored.
  - **Week start:** a "Week starts on" setting.
- **DESIGN:** 5.9, 18.1.
- **Tests:** Playwright WebKit.
- **Phone check:** 10's iPhone checks settle WEB-14.

### B21 · Recipes, plan and names

- **Findings:** LOGIC-13 to LOGIC-17, WEB-06, LOGIC-11 and LOGIC-12.
- **Change:**
  - **Servings:** scaling goes up to the recipe's own servings, rounds pieces up, and shows tiny amounts as a pinch.
  - **Copy last week:** counts same-named meals.
  - **Recipe import parsing:** comma decimals, "1h20m", mixed numbers, and one-string ingredient lists.
  - **Ingredient linking:** both singular forms, "-ves", and "extra-virgin".
  - **Aisle guesses:** modifier words win.
  - **Names:** one whitespace definition for name keys on both apps.
  - **Time:** a clock that reads the current time zone.
  - **Amounts:** editable amounts use ASCII digits.
- **DESIGN:** 5.5, 5.9, 11.
- **DECISIONS:** the whitespace definition, and what happens to existing keys.
- **Tests:**
  - Parser, `MealWeekTest`, `AisleGuessTest` and `NameKey` tests on both apps.
  - Section 10's differential cases.
  - `LiveImportTest` (import changes).

### B22 · Benchmark setup and a current profile

- **Findings:** PERF-01, BASE-08, PERF-05, PERF-06 and BASE-02.
- **Change:**
  - **RUNBOOK:** a "Benchmark setup" section with review 08's launch line (`-gpu host -feature -Uwb -no-window`).
  - **Benchmark preflight:** `baseline-profile.sh` refuses SwiftShader and warns about a throttled emulator.
  - **Home:** reports drawn when its cards load.
  - **Profile journey:**
    - fails when a step doesn't open what it should;
    - finds Add item (needs B03);
    - keeps the crash-report path.
  - **Profile:** regenerated and committed.
  - **Always-failing tests:** the two move out of the default instrumented run.
- **DESIGN:** 13.1, 17.
- **Tests:** startup and scroll benchmarks, recorded in DESIGN 17.
- **Phone check:** 08's cold starts, and the first sync on 4G.

### B23 · Lists that redraw less

- **Findings:** PERF-07, PERF-08, PERF-04, CODE-09, WEB-11 and WEB-13.
- **Change:**
  - **Compose:**
    - a stability configuration for `java.time`;
    - `contentType` everywhere, and scroll state read outside composition.
  - **Groceries:**
    - rows get precomputed values;
    - suggestions are computed in the ViewModel.
  - **Scroll hold:** one helper used by Pantry and Groceries.
  - **Web port:**
    - memoized rows that keep their identity across snapshots;
    - the same scroll hold.
- **DESIGN:** 6.4.
- **DECISIONS:** the stability configuration.
- **Tests:** a remote check-off during a drag doesn't move the list (both apps), and the Groceries benchmark.

### B24 · Insights, tab switches and motion

- **Findings:** PERF-03, PERF-09 and PERF-10, plus WEB-13's chart part in the web port.
- **Change:**
  - **Insights:** chart growth is drawn in the draw phase, and stripes are cached.
  - **Tabs:** screens stay composed across switches, with a simpler transition between tabs.
  - **Motion:**
    - a `Motion` object for durations and easings;
    - reduce motion observed live.
- **DESIGN:** 6.4.
- **DECISIONS:** the tab transition.
- **Tests:** Insights and tab-switch benchmarks.

### B25 · The job reads only the users who are due

- **Findings:** EFF-04.
- **Change:**
  - Each user gets a `nextDigestAt` timestamp, and the job queries `nextDigestAt <= now`.
  - Both apps clear it when the hour, the time zone or reminders change. Do this after B01's week of measurements.
- **DESIGN:** 9.7, 10.3.
- **Tests:** job tests, rules tests if the field needs a rule, and quota-sim.

### B26 · Smaller downloads and faster CI

- **Findings:** EFF-06, BASE-05, EFF-07, EFF-08, CODE-05 and BASE-03.
- **Change:**
  - **Web bundle:**
    - vendor chunks split from the app code;
    - rarely used files left out of the precache;
    - the size limit settled in DECISIONS.
  - **APK:** proto sources and Kotlin metadata excluded, fonts subset, and ABI filters for release only.
  - **CI:**
    - per-job path filters;
    - the Playwright container for the web job.
  - **Dependencies:** the four unused libraries removed.
  - **Finishing checks:** CLAUDE.md's checklist gains the three checks CI runs.
- **DESIGN:** 7.3, 13.1, 13.2, 18.6.
- **Tests:** CI. APK and bundle sizes recorded in DESIGN 17.

### B27 · Docs catch-up (any time)

- **Findings:** CODE-02, CODE-03, CODE-04, BASE-04, BASE-07 and CODE-06.
- **Change:**
  - **DESIGN:** the 20 lines that still describe v1 are rewritten.
  - **README and QA.md:** brought up to the planner, the tour and the web app.
  - **Repeated facts:** each fact lives in one place, with links elsewhere.
  - **CLAUDE.md and RUNBOOK:** their drifted lines are fixed.
  - **Licence list:** the two bundled fonts are added to Android's.
- **DESIGN:** 1, 2, 5.x, 6.4, 8.x, 9.x, 10.x, 13.x, 16, 17, 18.3.
- **Tests:** the field-limits test (CODE-04's row 7).
- **When:** docs only, so it can run alongside any batch that doesn't touch the same DESIGN sections.

### B28 · Tests and dead code

- **Findings:** CODE-01, CODE-08 and BASE-01.
- **Change:**
  - **Dead code:** v1's pantry code is deleted, and its useful tests move to `PantryItemsTest`.
  - **New Android tests:**
    - a two-phone `GroceryFlowTest`;
    - a Settings UI test for Clear household data;
    - an opt-in share-preview timing test.
  - **Web:** the Insights screenshot test gets a fixed clock.
- **Tests:** the above.

## After the fixes

- **Remeasure:** rerun section 00 and compare it with `00-baseline.md`.
- **Security report:** once the security fixes have shipped (rules deployed, the release out), publish a sanitized
  security report.
- **The review branch's PR:** when `review/2026-11` becomes a PR, it adds a DESIGN 17 row and a DECISIONS entry
  (security findings were kept private until fixed). It's pushed only with the owner's OK.

## For the owner

- **Releases:** an Android beta with #74, and later each batch's release (rules deploys, betas, web deploys).
- **Phone checks:** in every report. The ones that change a batch:
  - 09's usage page (B09);
  - 05's quota check (B05);
  - 07's TalkBack (B19);
  - 10's iPhone checks (B20);
  - 08's cold starts and first sync (B22).
- **Hallway test:** in `06-ux.md`.
- **Private items:** the console and GitHub checks in the private reports (S3).
- **B01's follow-up:** a week after it ships, look at the job's start times with me.
