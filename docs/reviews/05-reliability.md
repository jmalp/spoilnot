# 05 Reliability, offline and failure handling

Reviewed `0334b6a` on 2026-11-02 (the branch `review/2026-11` at `ed940e4`, which adds only review reports).
Platforms: Android first; the reminder job, the nightly export and the restore; the web by reading.

**Method.** DESIGN 6.7, 7.5, 8.6, 9.5, 9.7, 10, 13.3, 13.4 and 18.5 and the RUNBOOK were read against `core/error`,
`core/firebase`, `core/crash`, `feature/update`, `feature/notifications`, every `feature/*/data` mapper, the reminder
job (`main.ts`, `job.ts`, `backup.ts`, `export-main.ts`, `restore-main.ts`) and the reminders, nightly-export,
restore and keep-alive workflows. Then:

- **Pixel_8** (Android 17, `-gpu host`) ran the dev debug build against the Firebase emulators, driven through `adb`
  (a small script that taps by on-screen label). Offline is real airplane mode (`cmd connectivity airplane-mode`).
- **A scratch proxy** (Appendix A) sat between the app and the Firestore emulator: the emulator ran on 8181 and the
  proxy on 8080, where the app looks for Firestore. It passes traffic through, or fakes what the emulator can't: a
  quota that ran out (two ways a server can report it), a stalled connection and a lossy one.
- **Bad data** was written straight into the emulator with its admin access (Appendix B).
- **The backup and restore drill** used the job's own `exportHousehold`, `planRestore` and `applyRestore` against the
  emulator, the workflow's `openssl` line with a made-up passphrase, and the phone watching (Appendix C).
- **Reminder timing**: GitHub's real start times of the 61 scheduled `expiry-reminders` runs (21 Oct to 2 Nov, from
  `gh run list`) were replayed through the job's own `runJob` against the emulator (Appendix D), and one run was
  given a sender that fails once (Appendix E).

All test data was made up (test accounts on `example.test`, a made-up passphrase). No real project was touched, no
workflow was run or dispatched, and nothing in the source tree changed; the only temporary files were in the
scratchpad. The Android emulator rebooted once under load in the middle of the offline checks, which became a test
of its own (pending writes survived).

## Summary

The basics hold: the app opens from the phone's copy with no connection, even after a reboot; edits made offline
survive and reach the server within seconds of reconnecting, also over a connection that drops every few seconds;
rotation, dark mode, font size and process death keep the Add item sheet as it was; and the encrypted export comes
back byte-identical and restores 44 of 44 documents with members untouched. The problems cluster in four places.
**Offline, nothing deleted leaves the screen** (REL-01, P1): removed grocery items, a cleared cart, Used up and Add to
pantry all say they worked but stay, so people repeat them; one bought banana became two in the pantry. **When the
quota runs out the app can become a full-screen spinner**, cached data and the quota banner included (REL-02, P1).
**The reminder job runs about five times a day, not 24**: digests arrive about three hours late, the 20:00 and 21:00
choices miss most days (REL-03, P1), the daily prune has never run (REL-05, the cause of SECC-04), and one failed send
ends a run for everyone (REL-06). And **one listener error, or one wrong-typed document, silently blanks a screen
until the app restarts** (REL-04), which the restore can trigger (REL-14). One item moved to the private notes.

## Metrics

### Offline matrix (Android, airplane mode)

"Works" means it works offline and syncs on reconnect; "message" means it refuses with a message that says to
connect. Emulator checks unless the result says "reading".

| Flow | Result | Notes |
| --- | --- | --- |
| Cold start offline, signed in | **Works** | Opens from the phone's copy; also after the emulator rebooted mid-test with unsent edits, which were kept |
| Home, Pantry, Plan, Recipes | **Works**, banner shown | "Offline. Changes will sync later" |
| Groceries | **Works, no banner** | REL-10 |
| Add a grocery item, check one off | **Works** | "Waiting to sync" cloud icon on each row |
| Delete a grocery item, Clear checked | **Broken** | Snackbar says it worked; the item stays until online (REL-01) |
| Add to pantry | **Broken** | Items stay in the cart; pressing again adds them twice (REL-01) |
| Pantry: Add item | **Works** | Pending icon; on the server 20 s after reconnecting |
| Pantry: Used up, Throw away, −1 to zero | **Broken** | The batch stays on the list (REL-01) |
| Pantry: +1, −1 above zero, Edit | Works (reading) | Same writer path as Add item |
| Recipes: add or edit by hand | Works (reading) | |
| Recipe import from a link | Message (reading) | `RecipeImportScreen` shows "You're offline" |
| Plan: add or move a meal | Works (reading); delete **broken** (reading) | Delete leaves a tombstone, as REL-01 |
| Insights | Works (reading) | Reads the cached month documents |
| Notification settings, units | Works (reading) | Queued; a later refusal is ignored silently (REL-13) |
| Export | **Saves the phone's copy without saying so** | 0 products in the file, 4 on the server (REL-12) |
| Sign in / Create account (signed out) | Message, but "Something went wrong" | The Auth emulator reports a dropped connection as an internal error; on a real phone it should map to "You're offline" (Not covered) |
| Join with a code | **Message** | "You're offline. Check your connection and try again." |
| Create household | **Works** | Home opens with the banner; the household reached the server on reconnect. (The plan expected it to need the server; it doesn't, and that's fine) |
| Clear household data | **Stops at the password** | Nothing is cleared; same generic message as sign-in on the emulator |
| Delete account | Stops at the password (reading) | Owners get "transfer ownership first" before that |
| Leave household, transfer ownership | Message after 15 s (reading) | `commitConfirmed` → Offline |
| Rotate invite code, rename, approve or decline a request | Works, queued (reading) | `commitQueued` |
| Sign out | Not tried | SECC-01 on this commit; SYNC-09 and SECC-03 cover offline sign-out |
| Reconnect after offline edits | **Works** | Every offline edit was on the server within 20 s |
| Stalled connection (accepted, never answered) | **Works** | Banner after 12–18 s (Firestore's own 10 s check) |
| Lossy connection (each connection killed after 3–12 s) | **Works** | 4 of 4 items on the server, 7 reconnects in a minute, no duplicates |

Totals: 26 flows. 14 work (9 checked on the emulator), 6 refuse with a message (3 checked), 4 are broken or
misleading (all checked), Plan works except deleting (reading), and 1 wasn't tried.

### Quota reached (DESIGN 7.5), faked by the proxy

| Metric | Value | How measured | Target or budget |
| --- | --- | --- | --- |
| Listeners refused one by one with RESOURCE_EXHAUSTED (how a refused listen is reported) | Home with "Offline" at 5 s, **full-screen spinner from 15 s for the whole 2 minutes watched**; no quota banner | Proxy mode `quota-reads` (writes still pass), screen sampled every 9 s | 6.7: the quota banner, app usable from cache |
| Retries in that mode | 33 listener starts refused in 110 s (about 18 a minute); backoff caps at 32 s | Proxy counters | No retry storm |
| Whole call refused with status 8 | "Offline" banner, cached data usable, **no quota banner** | Proxy mode `quota-stream`, cold start | 6.7: quota banner |
| Retries in that mode | 2 connection attempts in 60 s | Proxy counters | No retry storm |
| After the quota "came back" | Home returns; the quota banner stays until midnight Pacific | Proxy back to pass-through | Resets at midnight Pacific (as designed) |

### Crash-prone patterns (Android main code)

| Pattern | Count | Reachable from bad data? |
| --- | --- | --- |
| Typed getters in mappers (`getString`, `getDouble`, `getLong`, `getBoolean`, `getTimestamp`), which throw on another type | **100** | **Yes**: no crash, but the list's flow ends (REL-04) |
| `.catch` on listener flows; all but one end the flow without a message or retry | **29** (in 11 ViewModel and domain files) | Yes, they turn the errors above into blank screens |
| `!!` | 12 | No: each is guarded by a filter or a check just before it |
| `.first()` / `.last()` on collections | 9 | No: all on non-empty groups or fixed lists |
| Unchecked casts in mappers | 0 (38 safe `as?` casts) | — |
| `runCatching` (swallows failures) | 18 | Some hide failures (REL-13; SECC-01 notes the cancellation side) |
| `AppError` types without a message | 0 of 15 (`core/ui/ErrorMessages.kt`); anything unmapped becomes "Something went wrong. Try again." | — (the Auth emulator's offline errors land there, see Not covered) |
| Crashes seen in the whole session | **0** | `logcat -b crash` showed only the emulator's UWB service |

### Bad data written into the emulator

| Case | Documents | Result |
| --- | --- | --- |
| Round 1: one wrong-typed field per collection (`stock.quantity: "2"`, `items.checked: "yes"`, `recipes.servings: "4"`, `meals.position: "1"`, `activity.actorName: 42`) | 5 | **No crash; 5 of 5 blank or freeze a screen**: Pantry and Groceries blank, Home on skeletons, Plan spinning, Recipes stuck on an old list with a false "Offline" banner. Recovered only after an app restart (REL-04) |
| Round 2: what a newer app or the web might write: unknown category, place, unit and date type; extra fields; a batch with 2 fields; `updatedAt: null`; quantity Infinity, NaN and −3; dates 2026-02-30 and 1900-01-01; a grocery item with an unknown source and a minimal one; recipe `ingredients` as a string and `servings: 2.5`; a meal slot `snack`; an unknown activity type | 14 | **No crash, lists load.** Unknown values show as Other; Infinity shows as "∞ g"; NaN and negative batches are hidden; the bad dates show as "No expiry date" and "Expired 46326 days ago"; the unknown slot and activity type are skipped; `updatedAt: null` appears only after the next full read. **One problem**: the −3 batch starts a write loop the rules refuse, 3.5 writes a second (REL-11) |
| Restore of a note that looks like a date-time | 1 | Becomes a timestamp; Pantry, Groceries and Home blank (REL-14) |

Totals: 20 cases, 0 crashes, 6 that blank a screen, 1 write loop.

### Screens that lose input (process death and configuration changes)

| Screen or sheet, half filled | Rotation, dark mode, font 2.0 | Process death (`am kill` in the background) | Another member's change |
| --- | --- | --- | --- |
| Pantry: Add item (name, date, place) | Kept | **Kept** (all fields) | — |
| Groceries: Add to pantry (row amounts and dates) | **Lost** | **Lost** (sheet back, edits gone) | **Lost** when anyone checks off another item (REL-08) |
| Plan: add a meal (typed title) | **Lost** (sheet closes) | **Lost** | — |
| Plan: a meal's sheet (cooks picked) | Cooks lost (reading) | — | — |
| Cook mode (ingredients ticked) | Ticks lost (reading) | — | — |
| Recipe: Add to grocery list (choices, renames) | Lost (reading) | — | — |
| Recipe: I cooked this (amounts) | Lost (reading) | — | — |
| Recipe edit form | Kept (reading: `rememberSaveable` with a saver) | Kept (reading) | — |

Totals: 6 sheets lose input on a configuration change (2 checked, 4 by reading `remember` without `Saveable`), 2
lose it on process death (checked), 1 on a remote change (checked). The share-while-in-background case wasn't run.

### Export and restore round trip

| Metric | Value | How measured | Target or budget |
| --- | --- | --- | --- |
| Export of the test household | 16 products, 16 batches, 1 list with 4 items, 3 recipes, 1 month of Insights, 3 meals, 1 member; tombstones left out | `exportHousehold` against the emulator | 13.4 |
| Encryption round trip | **Byte-identical** after the workflow's `tar` and `openssl enc -aes-256-cbc -pbkdf2 -iter 600000` and back (10,272 bytes encrypted); a wrong passphrase fails with "bad decrypt" | The workflow's command with a made-up passphrase (LibreSSL 3.3 here; the runner has OpenSSL 3, same defaults) | Identical |
| Dry run | 44 writes: products 16, stock 16, lists 1, items 4, recipes 3, stats 1, meals 3; nothing written | `planRestore` | Matches the export |
| Restore after everything was tombstoned (like Clear household data) | **44 of 44 back: 0 missing, 0 extra, 1 changed** (a note "2026-12-24T18:00:00Z" became a timestamp, REL-14); household document and members identical | Every field compared before and after | Identical |
| Timestamps | Kept to the millisecond; Firestore stores microseconds, so client-written times lose their last three digits (harmless) | Reading `toJson` | — |
| Seen on the phone after the restore | Yes when the phone's cached tombstones were within 5 minutes of its newest change; **no, for up to 20 h, when someone changed anything later than that** (REL-07) | Pixel_8 watching, three variants | 9.2: seconds |

### Reminder job

| Metric | Value | How measured | Target or budget |
| --- | --- | --- | --- |
| Scheduled runs that started | **61 in 12 days, 4 to 7 a day**; gaps median 5.0 h, max 8.5 h; start times spread over the whole hour | `gh run list --workflow reminders.yml` | 24 a day (DESIGN 10.4) |
| Runs in the 07:00 UTC prune hour | **0** (also 0 in the 03:00 and 17:00 hours) | Same | 1 a day |
| Nightly export | Every day, but 5 to 7 hours late (12:00–14:21 UTC for 07:17) | `gh run list --workflow nightly-export.yml` | — |
| Digest at 08:00 New York | 11 of 11 days, **median 175 min late**, max 265 | Replay through `runJob` (Appendix D) | "shortly after" (Settings) |
| Digest at 12:00 / 18:00 / 19:00 | 11/11, 11/11, 10/11 days; median 184, 104, 67 min late | Same | Same |
| Digest at **20:00 / 21:00** | **6 of 11 / 3 of 11 days** | Same | Every day |
| Doubled digests | 0 | Same (`lastDigestDate`) | 0 |
| Prunes in the replay | **0** | Same, with `main.ts`'s rule | 1 a day |
| One failed FCM call | **Ends the run**: 3 of 3 households missed it | Appendix E | Others unaffected |
| Keep-alive | Never ran yet (monthly on the 3rd; first run 2026-11-03) | `gh run list --workflow keepalive.yml` | Monthly |

## Findings

### REL-01 · P1 · Confirmed · Android (web by reading) — Offline, deleted items stay on screen, so people do it twice and Add to pantry adds everything again

- **Where:** `core/firebase/DeltaSync.kt:81` (`isTombstone() = contains(FIELD) && get(FIELD) != null`) with
  `Tombstones.fields` writing `deletedAt` as `FieldValue.serverTimestamp()` (`DeltaSync.kt:79`); the web's
  `web/src/data/delta-sync.ts:34` (`snapshot.get('deletedAt') != null`).
- **What happens:** until the server confirms a write, a pending server timestamp reads as `null` (the SDKs' default
  for unconfirmed server timestamps). So a deletion made offline isn't recognized as one: the item stays on the list,
  marked "Waiting to sync", while the snackbar says it worked. Every deletion goes this way: removing a grocery item,
  Clear checked, Add to pantry (which removes the bought items), Used up, Throw away, −1 to zero, deleting a recipe or
  a meal. In a shop without a signal, the person taps again. Add to pantry is the worst case: the cart still shows the
  items and the button, and a second press adds them again (one bought banana became two, also on the server after
  reconnecting). Used up twice would also count twice in Insights, and the restock engine still sees the batch.
- **Evidence:** Pixel_8 in airplane mode: delete "Onions" → "Onions removed" with Undo, Onions still listed after
  the Undo window; Clear checked → "Cart cleared", Coffee still in the cart; "Offline cheese" Used up → still listed;
  Add to pantry twice → Bananas and Coffee "2 pieces" each. After reconnecting, the server had them deleted and the
  pantry at 2: `stock … {"name":"Coffee","qty":2}`, `{"name":"Bananas","qty":2}`.
  `assets/05/offline-deleted-items-stay.png`, `assets/05/offline-used-up-stays.png`.
- **Who it affects:** everyone who shops or cooks without a signal, which is what the offline design (9.5) is for.
  Online, the gap is a fraction of a second and invisible, which is why no test caught it.
- **Suggested fix:** treat the field's presence as the mark: `contains(FIELD)` (Undo restores with a full `set`, so it
  never leaves `deletedAt: null`), or `get(FIELD, ServerTimestampBehavior.ESTIMATE) != null`; on the web
  `snapshot.get('deletedAt', { serverTimestamps: 'estimate' }) != null`. Effort S. Also needs: web port, an
  instrumented test (network off, delete, assert gone; Add to pantry, assert the cart empties), DESIGN 9.8 one line.

### REL-02 · P1 · Confirmed (fake server) · Likely (real Firestore) · Android — When the quota runs out, the whole app can become a spinner

- **Where:** `feature/household/domain/SessionManager.kt:180-185` (any error on the profile or membership listener
  emits `Session.Loading`, then retries with a backoff up to 32 s); `core/navigation/SpoilNotRoot.kt:40` (Loading
  replaces the whole app); `core/navigation/AppShell.kt:306-310` (the quota banner lives inside the app it replaced);
  `FirestoreFlows.kt:93` (`QuotaSignal.report()`).
- **What happens:** Firestore can report an exhausted quota two ways, and the app handles neither as DESIGN 6.7 and
  7.5 ask:
  1. **Each listener refused with RESOURCE_EXHAUSTED** (how a refused listen is reported, and how reports of "Quota
     exceeded" reaching snapshot listeners describe it): the profile listener fails, the session drops to Loading,
     and the app becomes a full-screen spinner, even though the phone has everything cached. Any half-filled form is
     gone (the shell is torn down). The quota banner can't show, because it is inside the shell. It stays that way
     until midnight Pacific, retrying about 18 listeners a minute.
  2. **The whole call refused:** the SDK treats it as a connection problem, retries about once a minute, and the
     app shows "Offline" and works from the cache. Better, but the banner never says why.
- **Evidence:** the proxy (Appendix A) in `quota-reads` mode answered every listen with a target removal carrying
  status 8 and passed writes through. The screen: "Offline" at 5 s, then a spinner from 15 s for the whole 2 minutes;
  33 refusals in 110 s. In `quota-stream` mode (status 8 on every call), a cold start showed the cached Home with
  "Offline" and made 2 connection attempts in 60 s. `assets/05/quota-spinner.png`. **Why Likely for real Firestore:**
  the emulator has no quota, so which of the two a real project sends couldn't be observed; case 1 matches public
  reports of "Quota exceeded" errors in listeners.
- **Who it affects:** every Android user of the project at once, for the rest of the Pacific day. Reaching the quota
  is plausible: 39% of reads at 10 households (section 00) and SECD-05.
- **Suggested fix:** in `SessionManager`, keep emitting the last known session while retrying a non-permission error
  (only a permission refusal or a missing membership should change the screen); show the quota banner above the
  root, not inside the shell; and in the changes-only listeners, let the cache listener keep the list on screen when
  the server listener fails with QuotaExceeded or Offline (today the error ends both, REL-04). Effort M. Also needs:
  a `SessionManager` unit test with a fake that throws QuotaExceeded; the web (`domain/session.ts`) by section 10.

### REL-03 · P1 · Confirmed · Backend — GitHub starts the hourly reminder job about five times a day: digests come hours late, and evening choices miss most days

- **Where:** `.github/workflows/reminders.yml:7` (`cron: '7 * * * *'`); `backend/reminder/src/digest.ts:46`
  (`LATEST_MINUTES = 22 * 60`); `feature/notifications/domain/NotificationModels.kt:33` (hours 5 to 21 offered);
  strings `notif_time_help` ("It arrives shortly after this time") and `settings_notifications_on`.
- **What happens:** DESIGN 10.4 expected runs "15 to 60 minutes late at busy times and occasionally skipped". In fact
  GitHub started 61 runs from 21 Oct to 2 Nov where about 290 were scheduled, at any minute of the hour, with gaps
  of 5 hours on average and up to 8.5. The job catches up until 22:00, so morning digests arrive, but about three hours after
  the chosen hour. After 19:00 there often is no run before 22:00, and the digest is skipped for the day: someone
  who picked 21:00 got 3 of 11.
- **Evidence:** Appendix D replays the 61 real start times through `runJob` for six made-up users in New York:
  08:00 → 11/11 days, median 175 min late (max 265); 12:00 → 11/11, 184; 18:00 → 11/11, 104; 19:00 → 10/11, 67;
  **20:00 → 6/11; 21:00 → 3/11**. No doubles. Other time zones look the same: in UTC, Madrid and Los Angeles the last three
  offered hours got a run before 22:00 on 2 to 10 days of 11 or 12 (`gh run list` times against each zone).
- **Who it affects:** every user who gets reminders; most of all those who picked 19:00 to 21:00.
- **Suggested fix:** (1) The repository is public, so Actions minutes are free: add schedule entries (for example
  `7,27,47 * * * *`) so dropped runs matter less, and check the start times after a week (section 09 weighs the
  extra job reads; the job reads every enabled user per run). (2) If that's not enough, an external free cron
  service calling `workflow_dispatch` (DESIGN 10.6 mentions it; one item for it is in the private notes).
  (3) Meanwhile, make the copy honest ("usually within a few hours") or stop offering 20:00 and 21:00. Effort S. Also
  needs: DESIGN 10.4's reliability paragraph, RUNBOOK, the web's hour list.

### REL-04 · P2 · Confirmed · Android — One listener error or one wrong-typed document silently blanks a screen until the app restarts

- **Where:** 29 `.catch { }` on listener flows, for example `PantryViewModel.kt:106`, `GroceriesViewModel.kt:97,111,112`,
  `PlanViewModel.kt:125`, `RecipeStore.kt:33`, `HomeViewModel.kt:109-133`; the mappers' typed getters (100 of them,
  for example `FirestorePantryRepository.kt:111-145`, `FirestoreGroceryRepository.kt:74-90`) and
  `DeltaSync.kt:123` (`getTimestamp(UPDATED_AT)`), which throw when a field has another type.
- **What happens:** a typed getter on a field of another type throws, the list's flow fails, and the ViewModel's
  `.catch { }` ends it: no message, no retry, no recovery until the process restarts. DESIGN 6.7 asks for "an inline
  message with Retry". Because Groceries and Home combine the pantry flow, one bad stock document blanks them too.
  Recipes kept an old list and a false "Offline" banner. The same path stops on any other listener error (quota,
  REL-02; the restock engine, SYNC-10). Types written by the newer clients of DESIGN 8.6 are fine: 14 such shapes
  loaded (Metrics); the problem is a *different type*, which can come from a bug in another client, the restore
  (REL-14) or a console edit.
- **Evidence:** round 1 of the bad data (Appendix B): five documents, each with one field of another type. Pantry and
  Groceries showed nothing, Home stayed on skeletons, Plan on a spinner, Recipes on a stale list with "Offline".
  After the documents were fixed, the screens stayed blank until a force-stop and restart.
  `assets/05/bad-data-pantry-blank.png`, `bad-data-home-skeleton.png`, `bad-data-plan-spinner.png`,
  `bad-data-recipes-stale-offline.png`. Logcat had no error: the failure isn't even logged.
- **Who it affects:** every Android phone in a household that has one such document, until someone fixes it in the
  console. Rare today (both apps write the same types, section 04), but the effect is wide and invisible. One item
  moved to the private notes.
- **Suggested fix:** map field by field with safe casts (`get(field) as? String`, `(get(field) as? Number)?.toDouble()`),
  as the recipe and Insights mappers already do; map each document in its own `runCatching` and skip (and log) one
  that still fails; replace `.catch { }` with a retry and an error state that shows "Couldn't load. Retry" (6.7).
  Effort M. Also needs: unit tests per mapper with wrong types; the web's mappers by section 10.

### REL-05 · P2 · Confirmed · Backend — The daily prune runs only if a run starts in the 07:00 UTC hour, which hasn't happened since 21 October (SECC-04's cause)

- **Where:** `backend/reminder/src/main.ts:8,34` (`const prune = now.getUTCHours() === PRUNE_HOUR_UTC`); unchanged on
  `main` (210aec4 added `pruneFailures` but kept the rule).
- **What happens:** the prune (activity after 30 days, join requests after 7, tombstones after 2, meals 28 days past)
  happens only in a run that *starts* between 07:00 and 07:59 UTC. With about five runs a day at random times
  (REL-03), that hour gets a run on roughly one day in five, and in this window on none: 0 of 61 runs (the hours 06
  and 08 had one each). GitHub's load around 07:00 UTC is high (the 07:17 export
  starts 5 to 7 hours late every day). Consequences: SECC-04's retention promise; tombstones pile up, so every
  phone's daily full read grows; join requests over 7 days stay.
- **Evidence:** `gh run list --workflow reminders.yml` by UTC hour: 00:3, 01:2, 02:2, 03:0, 04:4, 05:5, 06:1, **07:0**,
  08:1, … 23:6. Appendix D's replay applies `main.ts`'s rule to the real times: 0 prunes.
- **Who it affects:** every household (privacy promise, quota), slowly.
- **Suggested fix:** prune in the first run of each UTC day: keep `lastPruneDate` in a document only the job reads
  and writes (an unmatched path such as `jobs/state`, which the rules already deny to clients), prune when it is
  before today, and write it after a prune without failures. Log the last prune date in the summary. Effort S. Also
  needs: a unit test with two runs in one day, quota-sim (it assumes a prune a day), DESIGN 10.1, RUNBOOK.

### REL-06 · P2 · Confirmed · Backend — One failed send ends the whole run, so every household after it misses that run

- **Where:** `backend/reminder/src/job.ts:132-199` (no try/catch around a household's queries, the device read, the
  `sendEach` call or the `update`); `main.ts:39-42`. Unchanged on `main`.
- **What happens:** any exception inside the loop, such as an FCM timeout or outage for one batch of messages, a
  profile deleted between the query and the update (SECD-12) or a Firestore error, rejects `runJob`: the households
  after it get nothing this run, and the prune is skipped. With runs about five hours apart (REL-03), one failure
  costs everyone after it hours, or the day for evening hours. GitHub emails a failure, and the next run catches up.
  SECD-03 (private) is the deliberate version.
- **Evidence:** Appendix E: three households due, a sender that throws once ("fcm timeout") → `run stopped: fcm
  timeout`; all three users have no `lastDigestDate`.
- **Who it affects:** every household whose turn comes after the failing one.
- **Suggested fix:** try/catch per household and per user; count failures in the summary (no IDs, as the logs are
  public); carry on; exit non-zero at the end so GitHub still emails. Effort S. Also needs: a unit test with a
  throwing sender (Appendix E is one), DESIGN 10.3.

### REL-07 · P2 · Confirmed · Backend + Android — After a restore, a phone can keep showing the household empty for up to 20 hours

- **Where:** `backend/reminder/src/backup.ts:128-135` (`applyRestore` writes each document exactly as exported,
  `updatedAt` included); `core/firebase/DeltaSync.kt:120-135` (the server listener reads only documents with
  `updatedAt` after the phone's newest cached change minus 5 minutes).
- **What happens:** the restore brings documents back with their old `updatedAt`, older than the phone's cursor, so
  the changes-only listener never asks for them. Firestore still fixes the phone's copy when its cached tombstones
  fall inside the listener's query (it looks up documents that disappeared from the results), so a phone that saw
  only the clear shows the restore at once. But when anyone changed anything later than five minutes after the
  clear, the tombstones fall outside the window, and the restored pantry stays invisible until that phone's next
  full read, up to 20 hours. The owner sees an empty household after "restored 44 document(s)" and may restore
  again or give up.
- **Evidence:** three runs with Pixel_8 watching (Appendix C): (a) clear, then restore while the app watched: visible;
  (b) clear, a new item seconds later, app closed, restore, app opened: visible (inside the 5-minute margin); (c) clear,
  an item stamped an hour after the clear, app closed, restore, app opened: **only that item shows**, while the server
  has 16 live batches.
- **Who it affects:** an owner restoring after Clear household data or a mistake (RUNBOOK), whenever the household
  kept using the app in between; every phone and the web (same listeners).
- **Suggested fix:** stamp every restored document with `updatedAt: FieldValue.serverTimestamp()` (and
  `updatedBy: 'restore'`) in `applyRestore`, so every phone's changes-only listener sees it within seconds; say so in
  RUNBOOK ("phones show the restore within a minute"). Effort S. Also needs: the integration test checking
  `updatedAt` is new, DESIGN 13.4.

### REL-08 · P2 · Confirmed · Android — Add to pantry loses typed amounts and dates when someone else checks something off, the phone rotates or Android closes the app

- **Where:** `feature/grocery/ui/GroceryForms.kt:123` (`val drafts = remember(items) { mutableStateListOf(...) }`).
- **What happens:** the per-row amounts, dates and places live in `remember(items)`, which is not saved and starts
  over whenever the list of checked items changes. Two people shopping together is the common case: one sets dates
  for ten items, the other checks off one more, and every row goes back to "1 · Fridge · No expiry date" without a
  word. Rotation and process death (a phone call, the camera) do the same.
- **Evidence:** Pixel_8: Butter set to 3 and 2 weeks; a second member's check-off of Sugar written to the emulator →
  Butter back to "1 · Fridge · No expiry date" in 5 s (`assets/05/add-to-pantry-edits-reset.png`); the same reset
  after `am kill` and after rotation.
- **Who it affects:** anyone adding a shop's worth of items while another member is still checking things off, or
  whose phone closes the app in the background.
- **Suggested fix:** keep the drafts by item id in `rememberSaveable` (a saver for the draft fields) and merge them
  with the current items: keep edits for items still there, add new rows, drop removed ones. Effort S. Also needs:
  a Compose test that changes `items` and checks an edited row stays.

### REL-09 · P3 · Confirmed · Android — The Plan add-meal sheet closes on rotation, theme or font changes and process death; several sheets forget picks

- **Where:** `feature/plan/ui/PlanScreen.kt:85` (`var adding by remember { … }`); `PlanSheets.kt:89,214` (cooks);
  `recipes/ui/CookModeScreen.kt:95` (ticked ingredients); `AddToGrocerySheet.kt:73,75` (choices, renames);
  `CookedSheet.kt:54-55` (amounts).
- **What happens:** these use `remember` where the rest of the app uses `rememberSaveable`. The add-meal sheet
  closes and its typed title is gone after a rotation or when the app comes back from the background; the others
  keep the sheet but reset what was picked.
- **Evidence:** Pixel_8: "Pizza night" typed in the add sheet; rotation → the Plan tab without the sheet; the same after
  `am kill` (the Add item sheet, by contrast, came back complete). The other four by reading.
- **Who it affects:** people who rotate their phone or switch apps mid-plan; small amounts of input.
- **Suggested fix:** `rememberSaveable` (the slot pair as two saveable values, lists with `listSaver`). Effort S.

### REL-10 · P3 · Confirmed · Android — Groceries, the screen used in the shop, has no offline banner

- **Where:** `feature/grocery/ui/GroceriesScreen.kt` (no `OfflineBanner`; the other four tabs have one, for example
  `PantryScreen.kt:144`); `FirestoreGroceryRepository.observeItems` doesn't expose `fromCache`.
- **What happens:** in airplane mode Home, Pantry, Plan and Recipes say "Offline. Changes will sync later"; Groceries
  says nothing. Only the per-row "Waiting to sync" icon hints at it.
- **Evidence:** `assets/05/offline-groceries-no-banner.png` (against `offline-pantry-banner.png`).
- **Who it affects:** shoppers without a signal, who most need to know their check-offs aren't shared yet.
- **Suggested fix:** pass the items listener's `fromCache` up and show `OfflineBanner` as Pantry does. Effort S. Also
  needs: the web's Groceries checked by section 10.

### REL-11 · P3 · Confirmed · Android — A pantry batch the rules won't let the app delete makes Pantry retry 3.5 times a second

- **Where:** `feature/pantry/ui/PantryViewModel.kt:128-137` (`deleteEmptyBatches` on every data change);
  `PantryService.kt:381-384`; the stock rule's `quantity >= 0` (`firebase/firestore.rules:158`).
- **What happens:** a batch at zero or below is tombstoned by whichever phone sees it (invariant 1). If the rules
  refuse that write (here a batch at −3, which a console edit or a script with admin access could leave), the local write is
  rolled back, the data changes, and the cleanup tries again: about 70 refused writes in 20 s, each showing "You
  don't have permission to do that.", for as long as Pantry is open.
- **Evidence:** logcat `Write failed at households/<id>/stock/neg: PERMISSION_DENIED`, 140 lines in 20 s (two per
  write); the snackbar repeated on every sample; it stopped when the batch was fixed.
- **Who it affects:** rare; battery and an endless error message when it happens.
- **Suggested fix:** remember batch IDs whose cleanup was refused in this process and skip them; don't show messages
  for background cleanup. Effort S.

### REL-12 · P3 · Confirmed · Android — Export offline quietly saves only what is on the phone

- **Where:** `feature/settings/data/FirestoreHouseholdExporter.kt:33-45` (`get()` falls back to the cache offline);
  the review plan expected Export to need the server.
- **What happens:** offline, Export saves a file from the phone's copy without saying so. An owner following 5.1's
  advice to export before Clear household data may keep an incomplete backup.
- **Evidence:** a household with 4 products on the server, none yet on the phone; Export in airplane mode saved
  `spoilnot-export-2026-11-02.json` with `products: 0`, `stock: 0`.
- **Who it affects:** owners exporting without a connection; rare.
- **Suggested fix:** read with `Source.SERVER` and show "You're offline" (the nightly export is the safety net), or
  name the file and the message "from this phone's copy". Effort S. Also needs: the web's Export, 13.4.

### REL-13 · P3 · Likely · Android — A change the server refuses later is reported only if Pantry, Plan or Groceries is open

- **Where:** `core/firebase/FirestoreHouseholdWriter.kt:41-60` (`errors` is a `SharedFlow` without replay);
  collectors only in `PantryViewModel.kt:138`, `PlanViewModel.kt:223`, `GroceriesViewModel.kt:146`;
  `FirestoreFlows.kt:75-77` (`commitQueued` drops a refusal that arrives after 3 s);
  `NotificationSettingsViewModel.kt:42-45` (`runCatching` around an update that isn't awaited).
- **What happens:** offline changes are refused when they reach the server if, meanwhile, the person was removed, the
  data changed shape or a rule says no. The app then rolls the change back. A message appears only if one of three
  screens is open at that moment; from Recipes, Home, Settings, cook mode or the household screens the change just
  disappears. Household actions (rename, invite code, approve) and notification settings never report a late
  refusal.
- **Evidence:** reading. *Likely*: SYNC-04's p12 showed refused writes after removal; the message path wasn't run here.
- **Who it affects:** rare (a removed member, a rules mismatch after a release); confusing when it happens.
- **Suggested fix:** collect `writer.errors` once in the shell with its own snackbar (and a replay of the last error),
  and remove the per-screen collectors. Effort S.

### REL-14 · P3 · Confirmed · Backend + Android — The restore turns text that looks like a date-time into a timestamp, which then blanks Pantry and Groceries

- **Where:** `backend/reminder/src/backup.ts:28,46-48` (any string matching an ISO instant becomes a `Timestamp`);
  REL-04's typed getters.
- **What happens:** section 01 noted the conversion. The consequence: a free-text field such as a batch note
  "2026-12-24T18:00:00Z" comes back as a timestamp, and `getString("note")` throws on every Android phone, so Pantry,
  Groceries and Home go blank after the restore meant to fix things.
- **Evidence:** Appendix C: the drill's only changed field was `stock/iso: note "2026-12-24T18:00:00Z" -> timestamp`;
  Pixel_8 then showed Pantry and Groceries blank until the note was fixed and the app restarted.
- **Who it affects:** rare (a note or name in exactly that form), but it lands during a restore.
- **Suggested fix:** convert back only the known timestamp fields (`createdAt`, `updatedAt`, `deletedAt`, `checkedAt`,
  `cookedAt`, `joinedAt`, `requestedAt`, `exportedAt`), or mark timestamps in a format version 2
  (`{"$timestamp": "…"}`) while still reading version 1; and REL-04. Effort S. Also needs: a backup unit test.

### REL-15 · P3 · Likely · Web — The iPhone app looks for a new version only when it starts cold, and nothing can make an old one update

- **Where:** `web/src/core/updates.ts:18-29` (registers once; no `workbox.update()` later); DESIGN 18.5
  (`minSupportedBuild` doesn't apply to the web).
- **What happens:** a Home Screen web app stays in memory for days on an iPhone, and the browser looks for a new
  service worker only on a navigation, so a deploy reaches it at the next cold start. That is fine for small changes,
  but for a change in what the data means (as 9.8's tombstones were for Android, which raised `minSupportedBuild`),
  an old copy would keep running with no way to stop it.
- **Evidence:** reading. *Likely*: the service worker's update timing on iOS wasn't observed.
- **Who it affects:** iPhone users after a deploy that changes the data's meaning.
- **Suggested fix:** call `workbox.update()` when the app becomes visible (at most hourly); have the web read
  `config/app.minWebBuild` (or reuse the gate) and show the Update prompt without the 10-second limit when the build is
  older. Effort S. Also needs: DESIGN 18.5, section 10's check.

## Ideas

- **One "sync health" line on the hidden debug screen**: last full read per collection, the cursor, pending writes,
  the last listener error. It would have made REL-01, REL-04 and REL-07 visible in the beta.
- **Request persistent storage on the web** (`navigator.storage.persist()`, Safari 17+), so iOS is less likely to evict
  the IndexedDB copy, and unsent edits with it, of an app left unopened for weeks.
- **Time-box the nightly export's lateness in the RUNBOOK**: it runs every day but 5 to 7 hours after 07:17 UTC, so
  "the export from the night before" is often from the middle of the previous day.
- **Count meals in the export's log line** (`export-main.ts:23-24` counts everything but meals and members).
- **Use `Clock` in `QuotaSignal.isExhausted()` from the shell**: today the banner is checked only when the shell
  recomposes, so it can outlive midnight Pacific until something redraws.

## For other sections

- **06 UX:** the rules refuse a −1 that would go below zero, so two people using the last one at the same time get
  "You don't have permission to do that." (seen for REL-11's batch); Auth failures that aren't mapped show "Something
  went wrong. Try again." (the emulator shows it for offline sign-in and for the password
  check of Clear household data).
- **09 Efficiency:** GitHub runs about 5 of 24 reminder runs a day and no prune (REL-03, REL-05), so quota-sim's job
  numbers are high and its tombstone numbers low; REL-03's fix adds runs; REL-02's retries cost nothing while the
  quota is out.
- **10 Web:** REL-01 is in `delta-sync.ts:34` too; check the web's Groceries banner (REL-10), its mappers against
  wrong types (REL-04), its offline Export (REL-12) and its session on a quota error (REL-02).
- **11 Code:** no test deletes anything offline (REL-01); the mappers mix typed getters and safe casts (REL-04).
- **12 Triage:** REL-01 and REL-02 are small fixes with wide effect; REL-03, REL-05 and REL-06 are one job change.

## Not covered

- **Real Firestore quota**: the emulator has none; REL-02 used a fake, and which reply a real project sends stays
  Likely.
- **Real FCM, Doze, battery saver and app standby**: emulator builds get no FCM token (section 02). The client code
  was read: the token is synced at every start and on `onNewToken`, high-priority notification messages are shown by
  the system, and Settings shows a banner when the permission or channel is off. Owner checks 3 and 4.
- **Reinstall leaving an old token**: read only. A reinstall gets a new installation ID and device document; the old
  token's sends fail as "not registered", and the job then deletes it.
- **Offline for days**: not reproduced (time). Read: unsent writes are kept (they survived a reboot here), the full
  read runs after 20 hours, and p17 (section 04) showed that pruned tombstones don't leave ghosts. Owner check 2.
- **Slow connection (high latency)**: the proxy's `slow` mode was written but not run; stalled and lossy were.
- **A share arriving while the app is in the background**, and process death in cook mode: not run (time); cook
  mode's `remember` is in REL-09.
- **Sign-out offline, account deletion offline as a member, SYNC-08 and SYNC-09**: SECC-01 makes sign-out on this
  commit unrepresentative; the rest by reading.
- **The real wording of an offline sign-in on Android**: the Auth emulator reports a dropped connection as an
  internal error, so the emulator shows "Something went wrong" where a phone should get FirebaseNetworkException and
  "You're offline". Checking it needs a signed-out phone, and signing out is unsafe on this beta (SECC-01): check it
  after that fix.
- **The web app, run**: everything web here is from reading `web/src`; section 10 runs it.
- **The version-skew case end to end**: read only. `households.schemaVersion` is written and read by nothing (section
  11); the update gate reads the cached `config/app`, so it works offline once seen; Undo's full overwrite drops
  fields from newer clients (section 04, Ideas); the job reads fields defensively.

## Phone checks for the owner

Note the phone model and OS version once in the Result column. Use test items you can delete afterwards.

| # | Phone | Steps | Expected | Result |
| --- | --- | --- | --- | --- |
| 1 | Android | In a shop (or at home) put the phone in airplane mode. On Groceries: check off two test items, remove a third with its menu, then press Add to pantry. Look at the list for a minute, then turn airplane mode off. | Removed and added items leave the list at once. REL-01 predicts they stay until you're back online, and that Groceries has no "Offline" banner (REL-10). Don't press Add to pantry twice. | |
| 2 | Android | Leave the phone untouched for three days with the app closed, then open Spoil Not on Wi-Fi. | Home within a few seconds, with what the household changed meanwhile. Note anything missing that shows on the iPhone. | |
| 3 | Android | For a week, note when the daily digest arrives against your chosen hour (Settings > Notifications). | REL-03 predicts about three hours late for a morning hour; for 20:00 or 21:00, about half the days with none. | |
| 4 | Android | Uninstall the beta, install it again from App Distribution, sign in. Over the next two days, count digests. | One digest a day, on this install only (the old token is dropped by the job after its first failed send). | |
| 5 | iPhone | Open the Home Screen app, add a test item to Groceries, then put the iPhone in airplane mode, remove the item with its menu and check off another. | Same as check 1 on the web: REL-01 predicts the removed item stays until online. | |

## Appendix A: the Firestore proxy (`fsproxy.mjs`, scratchpad)

Run the Firestore emulator on 8181 (a scratch `firebase.json` with `"firestore": { "host": "0.0.0.0", "port": 8181 }`
and the rules and indexes by absolute path, started from `firebase/` with `npx firebase emulators:start --config
<scratch>/firebase.json --project demo-spoilnot`), then `node fsproxy.mjs`. The dev debug build reaches it at
`10.0.2.2:8080` as usual. Switch modes with `curl "127.0.0.1:8097/mode?m=quota-reads"` and read counters with
`curl 127.0.0.1:8097/stats`. Seed scripts write to 8181 directly.

```js
// Review 05 scratch tool: a TCP/HTTP2 proxy in front of the Firestore emulator.
// Listens on 0.0.0.0:8080 (where the app expects Firestore), forwards to 127.0.0.1:8181 (the real emulator).
// Control on 127.0.0.1:8097:  GET /mode?m=<mode>[&delay=ms]   GET /stats   GET /reset
// Modes:
//   pass         plain TCP pipe
//   slow         TCP pipe, every chunk delayed by `delay` ms each way
//   lossy        TCP pipe, each connection killed after a random 3-12 s
//   blackhole    accepts connections, never answers (a stalled network)
//   quota-reads  gRPC: Listen targets rejected with RESOURCE_EXHAUSTED (target removal with a cause, as the
//                backend reports a refused listen), RunQuery/BatchGet fail with status 8; Write/Commit pass through
//   quota-all    as quota-reads, but Write/Commit fail with status 8 too
//   quota-stream every gRPC call fails at once with status 8 (the other way a backend could report it)
import net from 'node:net';
import http from 'node:http';
import http2 from 'node:http2';
import fs from 'node:fs';

const UPSTREAM = { host: '127.0.0.1', port: 8181 };
let mode = 'pass';
let delay = 0;
const sockets = new Set();
const stats = new Map();
const logFile = process.env.PROXY_LOG ?? 'proxy.log';
const log = (line) => fs.appendFileSync(logFile, `${new Date().toISOString()} ${line}\n`);
const count = (key) => stats.set(key, (stats.get(key) ?? 0) + 1);

// ---------- protobuf bits (hand-rolled; only what ListenRequest/ListenResponse need) ----------
function varint(n) {
  const out = [];
  let v = n >>> 0;
  while (v > 0x7f) { out.push((v & 0x7f) | 0x80); v >>>= 7; }
  out.push(v);
  return Buffer.from(out);
}
function readVarint(buf, pos) {
  let result = 0, shift = 0, b;
  do { b = buf[pos++]; result += (b & 0x7f) * 2 ** shift; shift += 7; } while (b & 0x80);
  return [result, pos];
}
function fields(buf) {
  const out = [];
  let pos = 0;
  while (pos < buf.length) {
    let key; [key, pos] = readVarint(buf, pos);
    const no = Math.floor(key / 8), wt = key & 7;
    if (wt === 0) { let v; [v, pos] = readVarint(buf, pos); out.push({ no, v }); }
    else if (wt === 2) { let len; [len, pos] = readVarint(buf, pos); out.push({ no, b: buf.subarray(pos, pos + len) }); pos += len; }
    else if (wt === 1) pos += 8;
    else if (wt === 5) pos += 4;
    else throw new Error(`wire type ${wt}`);
  }
  return out;
}
const ld = (no, b) => Buffer.concat([varint(no * 8 + 2), varint(b.length), b]);
const vi = (no, v) => Buffer.concat([varint(no * 8), varint(v)]);
const frame = (msg) => { const h = Buffer.alloc(5); h.writeUInt32BE(msg.length, 1); return Buffer.concat([h, msg]); };
function targetRemoved(targetId) {
  const status = Buffer.concat([vi(1, 8), ld(2, Buffer.from('Quota exceeded.'))]);
  const change = Buffer.concat([vi(1, 2), vi(2, targetId), ld(3, status)]);
  return frame(ld(2, change)); // ListenResponse.target_change
}

// ---------- gRPC handling for the quota modes ----------
const h2 = http2.createServer();
let upstream = null;
function upstreamSession() {
  if (!upstream || upstream.closed || upstream.destroyed) {
    upstream = http2.connect(`http://${UPSTREAM.host}:${UPSTREAM.port}`);
    upstream.on('error', () => {});
  }
  return upstream;
}
function fail(stream) {
  stream.respond({ ':status': 200, 'content-type': 'application/grpc', 'grpc-status': '8', 'grpc-message': 'Quota exceeded.' }, { endStream: true });
}
function forward(stream, headers) {
  const out = {};
  for (const [k, v] of Object.entries(headers)) if (![':scheme', ':authority'].includes(k)) out[k] = v;
  const up = upstreamSession().request(out);
  let trailers = null;
  stream.pipe(up);
  up.on('response', (h) => {
    if (h['grpc-status'] !== undefined) { stream.respond(h, { endStream: true }); return; }
    stream.respond(h, { waitForTrailers: true });
    up.pipe(stream);
  });
  up.on('trailers', (t) => { trailers = t; });
  stream.on('wantTrailers', () => stream.sendTrailers(trailers ?? { 'grpc-status': '0' }));
  up.on('error', () => stream.destroy());
  stream.on('error', () => up.destroy());
  stream.on('close', () => up.destroy());
}
function listen(stream) {
  stream.respond({ ':status': 200, 'content-type': 'application/grpc' });
  let buf = Buffer.alloc(0);
  stream.on('data', (chunk) => {
    buf = Buffer.concat([buf, chunk]);
    while (buf.length >= 5) {
      const len = buf.readUInt32BE(1);
      if (buf.length < 5 + len) break;
      const msg = buf.subarray(5, 5 + len);
      buf = buf.subarray(5 + len);
      for (const f of fields(msg)) {
        if (f.no === 2 && f.b) {
          const id = fields(f.b).find((x) => x.no === 5)?.v ?? 0;
          count('listen.target.rejected');
          log(`listen target ${id} rejected`);
          stream.write(targetRemoved(id));
        } else if (f.no === 3) {
          count('listen.target.removedByClient');
        }
      }
    }
  });
  stream.on('error', () => {});
}
h2.on('stream', (stream, headers) => {
  const path = headers[':path'] ?? '';
  const method = path.split('/').pop();
  count(`grpc.${method}`);
  log(`grpc ${method} (${mode})`);
  stream.on('error', () => {});
  if (mode === 'quota-stream') return fail(stream);
  if (method === 'Listen') return listen(stream);
  if (mode === 'quota-reads' && (method === 'Write' || method === 'Commit')) return forward(stream, headers);
  return fail(stream);
});
h2.on('sessionError', () => {});

// ---------- TCP front ----------
const PREFACE = Buffer.from('PRI * HTTP/2.0');
const server = net.createServer((client) => {
  sockets.add(client);
  client.on('close', () => sockets.delete(client));
  client.on('error', () => {});
  count(`tcp.${mode}`);
  if (mode === 'blackhole') return; // hold it open, never answer
  // Quota modes speak gRPC only (the app); seed scripts use the emulator's own port.
  if (mode.startsWith('quota')) { h2.emit('connection', client); return; }
  client.once('data', (first) => {
    const up = net.connect(UPSTREAM);
    sockets.add(up);
    up.on('close', () => { sockets.delete(up); client.destroy(); });
    client.on('close', () => up.destroy());
    up.on('error', () => client.destroy());
    const send = (to, chunk) => (delay > 0 && mode === 'slow' ? setTimeout(() => to.write(chunk), delay) : to.write(chunk));
    send(up, first);
    client.on('data', (c) => send(up, c));
    up.on('data', (c) => send(client, c));
    if (mode === 'lossy') {
      const ms = 3000 + Math.random() * 9000;
      setTimeout(() => { count('lossy.kill'); client.destroy(); up.destroy(); }, ms);
    }
  });
});
server.listen(8080, '0.0.0.0', () => log('proxy on 8080 -> 8181'));

http.createServer((req, res) => {
  const url = new URL(req.url, 'http://x');
  if (url.pathname === '/mode') {
    mode = url.searchParams.get('m') ?? mode;
    delay = Number(url.searchParams.get('delay') ?? 0);
    for (const s of sockets) s.destroy();
    sockets.clear();
    if (upstream) { upstream.destroy(); upstream = null; }
    log(`mode ${mode} delay ${delay}`);
  } else if (url.pathname === '/reset') {
    stats.clear();
  }
  res.setHeader('content-type', 'application/json');
  res.end(JSON.stringify({ mode, delay, stats: Object.fromEntries(stats) }));
}).listen(8097, '127.0.0.1');
```

## Appendix B: bad-data documents (`seed/lib.mjs`, `bad1.mjs`, `bad1b.mjs`, `bad2.mjs`)

Written with the emulator's admin access (`Authorization: Bearer owner`) into a household created in the app.
`bad1b.mjs` replaced the activity case with a wrong-typed `actorName` (a string `createdAt` would sort first and
test the same thing). Fixes were written as tombstones with a new `updatedAt`: an outright delete is invisible to the
changes-only listeners until the next full read.

```js
// lib.mjs
// Review 05 scratch: seed made-up data straight into the Firestore emulator (admin, bypasses rules).
export const FS = 'http://127.0.0.1:8181/v1/projects/demo-spoilnot/databases/(default)/documents';
export const ADMIN = { Authorization: 'Bearer owner', 'Content-Type': 'application/json' };
const enc = (v) => v === null ? { nullValue: null }
  : v instanceof Date ? { timestampValue: v.toISOString() }
  : v && v.__raw ? v.__raw
  : Array.isArray(v) ? { arrayValue: { values: v.map(enc) } }
  : typeof v === 'object' ? { mapValue: { fields: Object.fromEntries(Object.entries(v).map(([k, x]) => [k, enc(x)])) } }
  : typeof v === 'boolean' ? { booleanValue: v }
  : typeof v === 'number' ? (Number.isInteger(v) ? { integerValue: String(v) } : { doubleValue: v })
  : { stringValue: v };
export const raw = (r) => ({ __raw: r });
export async function put(path, fields) {
  const r = await fetch(`${FS}/${path}`, { method: 'PATCH', headers: ADMIN, body: JSON.stringify({ fields: Object.fromEntries(Object.entries(fields).map(([k, v]) => [k, enc(v)])) }) });
  if (!r.ok) throw new Error(`${path}: ${r.status} ${await r.text()}`);
}
export async function del(path) { await fetch(`${FS}/${path}`, { method: 'DELETE', headers: ADMIN }); }
export async function query(parent, structuredQuery) {
  const r = await fetch(`${FS}${parent ? '/' + parent : ''}:runQuery`, { method: 'POST', headers: ADMIN, body: JSON.stringify({ structuredQuery }) });
  return (await r.json()).filter((x) => x.document).map((x) => x.document);
}
export async function household(name) {
  const docs = await query('', { from: [{ collectionId: 'households' }], where: { fieldFilter: { field: { fieldPath: 'name' }, op: 'EQUAL', value: { stringValue: name } } } });
  return docs[0].name.split('/').pop();
}
export async function uidOf(email) {
  const r = await fetch('http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/projects/demo-spoilnot/accounts:lookup', { method: 'POST', headers: ADMIN, body: JSON.stringify({ email: [email] }) });
  return (await r.json()).users[0].localId;
}
export async function defaultList(hid) {
  const docs = await query(`households/${hid}`, { from: [{ collectionId: 'groceryLists' }], where: { fieldFilter: { field: { fieldPath: 'isDefault' }, op: 'EQUAL', value: { booleanValue: true } } } });
  return docs[0].name.split('/').pop();
}
export function inDays(d) { const t = new Date(); t.setDate(t.getDate() + d); return t.toISOString().slice(0, 10); }
export async function product(hid, uid, id, name, { qty = 1, unit = 'pcs', expiry = inDays(5), category = 'dairy', location = 'fridge', min = null, extra = {}, batchExtra = {} } = {}) {
  const now = new Date();
  const stamp = { createdAt: now, createdBy: uid, updatedAt: now, updatedBy: uid };
  await put(`households/${hid}/products/p-${id}`, { name, nameKey: name.toLowerCase(), category, defaultLocation: location, unit, minQuantity: min, autoAddToGrocery: min != null, buyQuantity: null, restockArmed: true, typicalShelfLifeDays: null, recentShelfLifeDays: [], lastAddedQuantity: null, openedShelfLifeDays: null, aliases: [], ...stamp, ...extra });
  if (qty > 0) await put(`households/${hid}/stock/${id}`, { productId: `p-${id}`, productName: name, unit, category, quantity: qty, expiryDate: expiry, dateType: 'use_by', location, note: null, openedOn: null, ...stamp, ...batchExtra });
}
export async function item(hid, uid, list, id, name, extra = {}) {
  const now = new Date();
  await put(`households/${hid}/groceryLists/${list}/items/${id}`, { name, nameKey: name.toLowerCase(), quantity: null, unit: null, note: null, category: null, checked: false, checkedBy: null, checkedAt: null, source: 'manual', productId: null, recipeId: null, aliasOf: null, createdAt: now, createdBy: uid, updatedAt: now, updatedBy: uid, ...extra });
}
export async function recipe(hid, uid, id, title, extra = {}) {
  const now = new Date();
  await put(`households/${hid}/recipes/${id}`, { title, sourceUrl: null, sourceName: null, imageUrl: null, description: null, servings: 4, servingsText: null, prepMinutes: null, cookMinutes: null, totalMinutes: 30, ingredients: ['2 eggs', '200 g rice', '1 onion'].map((r) => ({ raw: r, qty: null, unit: null, name: null, group: null })), steps: [{ text: 'Cook it.', group: null }], notes: null, tags: [], importMethod: 'manual', favorite: false, rating: null, createdAt: now, createdBy: uid, updatedAt: now, updatedBy: uid, ...extra });
}
export async function meal(hid, uid, id, date, slot, title, extra = {}) {
  const now = new Date();
  await put(`households/${hid}/meals/${id}`, { date, slot, position: 0, recipeId: null, title, servings: null, cooks: [uid], note: null, cookedAt: null, cookedBy: null, createdAt: now, createdBy: uid, updatedAt: now, updatedBy: uid, ...extra });
}

// bad1.mjs (round 1: one wrong-typed field per collection)
const now = new Date();
const s = { createdAt: now, createdBy: uid, updatedAt: now, updatedBy: uid };
// One wrong-typed field per collection, otherwise as the app writes them.
await put(`households/${hid}/stock/bad-qty`, { productId: 'p-b0', productName: 'Milk', unit: 'pcs', category: 'dairy', quantity: '2', expiryDate: inDays(3), dateType: 'use_by', location: 'fridge', note: null, openedOn: null, ...s });
await put(`households/${hid}/groceryLists/${list}/items/bad-checked`, { name: 'Bad checked', nameKey: 'bad checked', quantity: null, unit: null, note: null, category: null, checked: 'yes', checkedBy: null, checkedAt: null, source: 'manual', productId: null, recipeId: null, aliasOf: null, ...s });
await put(`households/${hid}/recipes/bad-servings`, { title: 'Bad servings', servings: '4', ingredients: [], steps: [], tags: [], importMethod: 'manual', favorite: false, rating: null, ...s });
await put(`households/${hid}/meals/bad-position`, { date: inDays(0), slot: 'lunch', position: '1', recipeId: null, title: 'Bad position', servings: null, cooks: [], note: null, cookedAt: null, cookedBy: null, ...s });
await put(`households/${hid}/activity/bad-created`, { type: 'stock_added', actorUid: uid, actorName: 'Alice', params: { name: 'Bad time' }, createdAt: now, productId: null });
console.log('seeded bad round 1');

// bad1b.mjs
await put(`households/${hid}/activity/bad-created`, { type: 'stock_added', actorUid: uid, actorName: 42, params: { name: 'Bad name type' }, createdAt: new Date(), productId: null });

// bad2.mjs (round 2: what a newer client might write)
const now = new Date();
const s = { createdAt: now, createdBy: uid, updatedAt: now, updatedBy: uid };
// Fields and values a newer app or the web might write.
await put(`households/${hid}/products/p-new`, { name: 'Future product', nameKey: 'future product', category: 'spaceship', defaultLocation: 'garage', unit: 'barrels', futureField: { a: [1, 2] }, minQuantity: null, ...s });
await put(`households/${hid}/stock/new-enum`, { productId: 'p-new', productName: 'Future product', unit: 'barrels', category: 'spaceship', quantity: 2, expiryDate: inDays(2), dateType: 'eat_by', location: 'garage', futureField: true, ...s });
await put(`households/${hid}/stock/minimal`, { productId: 'p-b1', quantity: 1, updatedAt: now });
await put(`households/${hid}/stock/null-updated`, { productId: 'p-b2', productName: 'Null updatedAt', unit: 'pcs', category: 'dairy', quantity: 1, expiryDate: inDays(4), dateType: 'use_by', location: 'fridge', createdAt: now, updatedAt: null });
await put(`households/${hid}/stock/inf`, { productId: 'p-b3', productName: 'Infinite cheddar', unit: 'g', category: 'dairy', quantity: raw({ doubleValue: 'Infinity' }), expiryDate: inDays(6), dateType: 'use_by', location: 'fridge', ...s });
await put(`households/${hid}/stock/nan`, { productId: 'p-b4', productName: 'NaN spinach', unit: 'pcs', category: 'produce', quantity: raw({ doubleValue: 'NaN' }), expiryDate: inDays(6), dateType: 'use_by', location: 'fridge', ...s });
await put(`households/${hid}/stock/neg`, { productId: 'p-b5', productName: 'Negative chicken', unit: 'pcs', category: 'meat', quantity: -3, expiryDate: inDays(6), dateType: 'use_by', location: 'fridge', ...s });
await put(`households/${hid}/stock/baddate`, { productId: 'p-b6', productName: 'Feb 30 rice', unit: 'pcs', category: 'pantry', quantity: 1, expiryDate: '2026-02-30', dateType: 'use_by', location: 'pantry', ...s });
await put(`households/${hid}/stock/olddate`, { productId: 'p-b7', productName: 'Ancient pasta', unit: 'pcs', category: 'pantry', quantity: 1, expiryDate: '1900-01-01', dateType: 'use_by', location: 'pantry', ...s });
await put(`households/${hid}/groceryLists/${list}/items/new-source`, { name: 'Voice item', nameKey: 'voice item', quantity: 2.5, unit: 'barrels', category: 'spaceship', checked: false, source: 'voice', futureField: 'x', ...s });
await put(`households/${hid}/groceryLists/${list}/items/minimal`, { name: 'Minimal item', updatedAt: now });
await put(`households/${hid}/recipes/new-recipe`, { title: 'Future recipe', ingredients: 'one string, not a list', steps: [{ text: 'Step', group: null, timerSeconds: 60 }], servings: 2.5, tags: ['x', 3], importMethod: 'ai', ...s });
await put(`households/${hid}/meals/new-meal`, { date: inDays(0), slot: 'snack', title: 'Snack (newer slot)', cooks: [uid], ...s });
await put(`households/${hid}/activity/new-type`, { type: 'future_event', actorUid: uid, actorName: 'Alice', params: {}, createdAt: now });
console.log('seeded round 2');
```

## Appendix C: the backup and restore drill (`drill.mjs`)

Emulator only (`FIRESTORE_EMULATOR_HOST=127.0.0.1:8181`). The sequence used:

```sh
node drill.mjs snapshot $H before.json            # 72 documents, tombstones included
node drill.mjs export $H export.json              # what the nightly export writes for one household
mkdir exp && cp export.json exp/$H.json
export EXPORT_ENCRYPTION_KEY=made-up-drill-passphrase-…
tar -C exp -czf - . | openssl enc -aes-256-cbc -pbkdf2 -iter 600000 -salt -pass env:EXPORT_ENCRYPTION_KEY -out exports.tar.gz.enc
openssl enc -d -aes-256-cbc -pbkdf2 -iter 600000 -pass env:EXPORT_ENCRYPTION_KEY -in exports.tar.gz.enc | tar -C dec -xzf -
cmp dec/$H.json export.json                       # identical
node drill.mjs wipe $H clear                      # tombstone everything, like Clear household data
node drill.mjs restore $H dec/$H.json             # dry run: 44 writes
node drill.mjs restore $H dec/$H.json --apply
node drill.mjs snapshot $H after.json && node drill.mjs compare before.json after.json
```

REL-07's three phone variants: (a) wipe, restore with the app open; (b) wipe, add an item, force-stop the app,
restore, start the app; (c) as (b) with the item's `updatedAt` an hour after the wipe. The export used for (b) and
(c) had the date-like note replaced, so REL-14 didn't mask the result.

```js
// Review 05: backup and restore drill against the Firestore EMULATOR only (FIRESTORE_EMULATOR_HOST must be set).
// Uses the job's own export and restore code from backend/reminder/src/backup.ts.
//   node drill.mjs snapshot <hid> <file>        every document of the household (tombstones included) to a file
//   node drill.mjs export <hid> <file>          exportHousehold, as the nightly export writes it
//   node drill.mjs wipe <hid> clear|delete      clear = tombstone everything like Clear household data; delete = outright
//   node drill.mjs restore <hid> <file> [--apply]
//   node drill.mjs compare <before> <after>     product/stock/list/item/recipe/stats/meal documents, tombstones left out
import { createRequire } from 'node:module';
import { readFile, writeFile } from 'node:fs/promises';
const REPO = '/Users/jmalp/Code/personal/spoilnot';
if (!process.env.FIRESTORE_EMULATOR_HOST) throw new Error('emulator only');
const require = createRequire(`${REPO}/backend/reminder/package.json`);
const { initializeApp } = require('firebase-admin/app');
const { getFirestore, Timestamp, FieldValue } = require('firebase-admin/firestore');
const backup = await import(`${REPO}/backend/reminder/src/backup.ts`);
const db = getFirestore(initializeApp({ projectId: 'demo-spoilnot' }));

const plain = (v) => v instanceof Timestamp ? { $ts: `${v.seconds}.${String(v.nanoseconds).padStart(9, '0')}` }
  : Array.isArray(v) ? v.map(plain)
  : v && typeof v === 'object' ? Object.fromEntries(Object.keys(v).sort().map((k) => [k, plain(v[k])]))
  : v;

async function snapshot(hid) {
  const out = {};
  const walk = async (ref) => {
    for (const col of await ref.listCollections()) {
      for (const d of (await col.get()).docs) {
        out[d.ref.path.replace(`households/${hid}/`, '')] = plain(d.data());
        await walk(d.ref);
      }
    }
  };
  await walk(db.doc(`households/${hid}`));
  out['(household)'] = plain((await db.doc(`households/${hid}`).get()).data());
  return out;
}

const [cmd, hid, arg, flag] = process.argv.slice(2);
if (cmd === 'snapshot') {
  await writeFile(arg, JSON.stringify(await snapshot(hid), null, 1));
  console.log('documents', Object.keys(JSON.parse(await readFile(arg, 'utf8'))).length);
} else if (cmd === 'export') {
  const data = await backup.exportHousehold(db, hid, new Date(), 'drill');
  await writeFile(arg, JSON.stringify(data, null, 2) + '\n');
  console.log('exported', { products: data.products.length, stock: data.stock.length, lists: data.groceryLists.length,
    items: data.groceryLists.reduce((n, l) => n + l.items.length, 0), recipes: data.recipes.length, stats: data.stats.length, meals: data.meals.length, members: data.members.length });
} else if (cmd === 'wipe') {
  const snap = await snapshot(hid);
  const writer = db.bulkWriter();
  let n = 0;
  for (const path of Object.keys(snap)) {
    if (path === '(household)' || path.startsWith('members/') || path.startsWith('joinRequests/')) continue;
    const ref = db.doc(`households/${hid}/${path}`);
    const tombstoned = /^(products|stock|recipes|meals)\/|\/items\//.test(path);
    if (arg === 'clear' && tombstoned) writer.set(ref, { deletedAt: FieldValue.serverTimestamp(), updatedAt: FieldValue.serverTimestamp(), updatedBy: 'drill' }, { merge: true });
    else if (arg === 'clear' && /^groceryLists\/[^/]+$/.test(path)) continue;
    else writer.delete(ref);
    n++;
  }
  await writer.close();
  console.log(arg, n, 'documents');
} else if (cmd === 'restore') {
  const plan = backup.planRestore(JSON.parse(await readFile(arg, 'utf8')), hid);
  const kinds = {};
  for (const w of plan.writes) kinds[w.path.split('/').at(-2)] = (kinds[w.path.split('/').at(-2)] ?? 0) + 1;
  console.log('plan', plan.writes.length, kinds);
  if (flag === '--apply') console.log('written', await backup.applyRestore(db, plan));
} else if (cmd === 'compare') {
  const a = JSON.parse(await readFile(hid, 'utf8'));
  const b = JSON.parse(await readFile(arg, 'utf8'));
  const keep = (s) => Object.fromEntries(Object.entries(s).filter(([p, d]) => /^(products|stock|recipes|meals|stats|groceryLists)\//.test(p) && d.deletedAt == null));
  const ka = keep(a), kb = keep(b);
  const diffs = { missing: [], extra: [], changed: [] };
  const fieldDiff = (x, y) => [...new Set([...Object.keys(x), ...Object.keys(y)])].filter((k) => JSON.stringify(x[k]) !== JSON.stringify(y[k]));
  for (const p of Object.keys(ka)) {
    if (!(p in kb)) diffs.missing.push(p);
    else { const f = fieldDiff(ka[p], kb[p]); if (f.length) diffs.changed.push(`${p}: ${f.map((k) => `${k} ${JSON.stringify(ka[k] ?? ka[p][k])} -> ${JSON.stringify(kb[p][k])}`).join('; ')}`); }
  }
  for (const p of Object.keys(kb)) if (!(p in ka)) diffs.extra.push(p);
  const members = (s) => JSON.stringify(Object.entries(s).filter(([p]) => p.startsWith('members/') || p === '(household)'));
  console.log(JSON.stringify({ compared: Object.keys(ka).length, missing: diffs.missing.length, extra: diffs.extra.length, changed: diffs.changed.length, membersAndHouseholdIdentical: members(a) === members(b) }, null, 1));
  for (const k of ['missing', 'extra', 'changed']) for (const line of diffs[k].slice(0, 12)) console.log(k, line);
}
process.exit(0);
```

## Appendix D: replay of the real run times (`replay.mjs`)

Input: `gh run list --repo jmalp/spoilnot --workflow reminders.yml --limit 500 --json databaseId,createdAt,startedAt,event,conclusion`.

```js
// Review 05: replay GitHub's real start times of the hourly reminder workflow (21 Oct - 2 Nov) through the job's
// own runJob, against the Firestore EMULATOR, with made-up users at different digest hours. Counts digests per
// local day and how late each came. Also applies main.ts's prune rule (only in a run starting at 07:xx UTC).
import { createRequire } from 'node:module';
import { readFile } from 'node:fs/promises';
const REPO = '/Users/jmalp/Code/personal/spoilnot';
if (!process.env.FIRESTORE_EMULATOR_HOST) throw new Error('emulator only');
const require = createRequire(`${REPO}/backend/reminder/package.json`);
const { initializeApp } = require('firebase-admin/app');
const { getFirestore, Timestamp } = require('firebase-admin/firestore');
const { runJob } = await import(`${REPO}/backend/reminder/src/job.ts`);
const db = getFirestore(initializeApp({ projectId: 'demo-spoilnot' }), );

const runs = JSON.parse(await readFile(process.argv[2], 'utf8'))
  .filter((r) => r.event === 'schedule').map((r) => new Date(r.startedAt)).sort((a, b) => a - b);
const zone = 'America/New_York';
const hours = [8, 12, 18, 19, 20, 21];
const hid = 'replay-household';
await db.doc(`households/${hid}`).set({ name: 'Replay', ownerUid: 'u8' });
for (const h of hours) {
  await db.doc(`users/u${h}`).set({ displayName: `User ${h}`, householdId: hid, timezone: zone, locale: 'en',
    notificationPrefs: { enabled: true, digestMinutes: h * 60, stages: [3, 2, 1, 0], expiredMode: 'once', cooking: false } });
  await db.doc(`users/u${h}/devices/d1`).set({ token: `tok-${h}`, platform: 'android' });
}
// A batch that is always expiring tomorrow, whatever the replayed date (rewritten before each run).
const local = (d) => new Intl.DateTimeFormat('en-CA', { timeZone: zone, year: 'numeric', month: '2-digit', day: '2-digit' }).format(d);
const sent = new Map(); // `${h} ${date}` -> local time sent
let prunes = 0;
const sender = { async sendEach(messages) { return messages.map(() => ({ success: true, deadToken: false })); } };
for (const now of runs) {
  const tomorrow = local(new Date(now.getTime() + 86_400_000));
  await db.doc(`households/${hid}/stock/b1`).set({ productName: 'Milk', expiryDate: tomorrow, dateType: 'use_by', quantity: 1 });
  const before = Object.fromEntries(await Promise.all(hours.map(async (h) => [h, (await db.doc(`users/u${h}`).get()).get('lastDigestDate')])));
  const prune = now.getUTCHours() === 7;
  if (prune) prunes++;
  await runJob(db, sender, { now, prune });
  for (const h of hours) {
    const after = (await db.doc(`users/u${h}`).get()).get('lastDigestDate');
    if (after !== before[h]) {
      const key = `${h} ${after}`;
      if (sent.has(key)) console.log('DOUBLE', key);
      sent.set(key, new Intl.DateTimeFormat('en-GB', { timeZone: zone, hour: '2-digit', minute: '2-digit' }).format(now));
    }
  }
}
const first = local(runs[0]); const last = local(runs.at(-1));
const days = [];
for (let d = new Date(`${first}T12:00:00Z`); local(d) <= last; d = new Date(d.getTime() + 86_400_000)) days.push(local(d));
const full = days.slice(1, -1); // whole days only
console.log(`${runs.length} runs, ${full.length} whole days (${full[0]} to ${full.at(-1)}), prune runs: ${prunes}`);
for (const h of hours) {
  const got = full.filter((d) => sent.has(`${h} ${d}`));
  const late = got.map((d) => { const [hh, mm] = sent.get(`${h} ${d}`).split(':').map(Number); return hh * 60 + mm - h * 60; });
  late.sort((a, b) => a - b);
  const med = late.length ? late[Math.floor(late.length / 2)] : null;
  console.log(`digest ${String(h).padStart(2, '0')}:00 ${zone}: ${got.length}/${full.length} days; minutes late median ${med}, max ${late.at(-1) ?? '-'}; missed: ${full.filter((d) => !sent.has(`${h} ${d}`)).join(' ') || 'none'}`);
}
process.exit(0);
```

## Appendix E: one failed send (`onefail.mjs`)

A unit-test version for the fix: the same three users with a sender that throws on its first call, then assert
the second and third users got their digest and the summary counts one failure.

```js
// Review 05: does one failed FCM call stop the run for the households after it? (emulator only)
import { createRequire } from 'node:module';
const REPO = '/Users/jmalp/Code/personal/spoilnot';
if (!process.env.FIRESTORE_EMULATOR_HOST) throw new Error('emulator only');
const require = createRequire(`${REPO}/backend/reminder/package.json`);
const { initializeApp } = require('firebase-admin/app');
const { getFirestore } = require('firebase-admin/firestore');
const { runJob } = await import(`${REPO}/backend/reminder/src/job.ts`);
const db = getFirestore(initializeApp({ projectId: 'demo-spoilnot' }));
const now = new Date('2026-11-02T15:30:00Z'); // 10:30 in New York
for (const [uid, hid] of [['a-first', 'hh-a'], ['b-second', 'hh-b'], ['c-third', 'hh-c']]) {
  await db.doc(`users/${uid}`).set({ householdId: hid, timezone: 'America/New_York', notificationPrefs: { enabled: true, digestMinutes: 540, stages: [0, 1, 2, 3], expiredMode: 'once' } });
  await db.doc(`users/${uid}/devices/d1`).set({ token: `tok-${uid}`, platform: 'android' });
  await db.doc(`households/${hid}/stock/s1`).set({ productName: 'Milk', expiryDate: '2026-11-03', dateType: 'use_by', quantity: 1 });
}
let calls = 0;
const sender = { async sendEach(messages) { calls++; if (calls === 1) throw Object.assign(new Error('fcm timeout'), { code: 'app/network-timeout' }); return messages.map(() => ({ success: true, deadToken: false })); } };
try { await runJob(db, sender, { now, prune: false }); console.log('run finished'); } catch (e) { console.log('run stopped:', e.message); }
for (const uid of ['a-first', 'b-second', 'c-third']) console.log(uid, 'lastDigestDate =', (await db.doc(`users/${uid}`).get()).get('lastDigestDate') ?? '(none: no digest this run)');
process.exit(0);
```
