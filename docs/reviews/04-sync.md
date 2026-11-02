# 04 Bugs in sync, sessions and multi-phone behavior

Reviewed `0334b6a` on 2026-11-01 and 02 (the branch `review/2026-11` at `a4547dc`, which adds only review reports).
Platforms: Android first; the web where it shares the code (read, not run); the reminder job where it touches sync.

**Method.** `core/firebase` (`DeltaSync.kt`, `FirestoreFlows.kt`, `FirestoreProvider.kt`,
`FirestoreHouseholdWriter.kt`), `core/write`, every `feature/*/data` repository and change mapper, `SessionManager`,
`RestockRunner` and the services that build the plans were read against DESIGN 5.1, 7.2, 8, 9 and 10.2, then the web's
`data/delta-sync.ts`, mappers and `domain/session.ts` for parity. Each suspected case became a probe in one temporary
instrumented test class, `review/SyncProbeTest.kt`, built on the existing `TestPhone` harness: two (or more) simulated
phones in one app, each with its own FirebaseApp, Auth user, Firestore cache and sync log, wired to the production
repositories, services and restock runner. "Offline" is `disableNetwork()` on one phone's Firestore. The probes ran on
Pixel_8 (Android 17) against the Firebase emulators (`./scripts/emulators.sh`, `demo-spoilnot`); each asserts what
DESIGN says, so a failing probe is a finding. The class was deleted after the runs and is copied in Appendix A. No
real Firebase project was touched; nothing else in the source tree changed.

## Summary

Live sync between two online phones is fast and sound: a change reaches the other phone in about a tenth of a
second, deletes win over edits, removal and ownership transfer settle in a second, and large writes go through. The
problems are in what the changes-only design (9.8) and the write plans assume. **A phone that writes to a list while
it isn't showing it then skips the other members' earlier changes to that list for up to 20 hours** (SYNC-01, P1): the
restock engine does exactly that when someone uses a staple up on the Pantry tab, and the missing items, check-offs
and deletes come back only at the next daily full read; the meal week and Recent activity have the same gap. **Add to
pantry pressed on two phones, or on one offline phone and one online, adds every item twice and makes duplicate
products** (SYNC-02, P1). Several write plans are built from the phone's own copy and overwrite or undo another
member's change: the Edit window writes every field (SYNC-03), −1 on a stale copy throws away another member's
addition (SYNC-04), and finishing the same thing on two phones counts it twice and takes a recipe's ingredients twice
(SYNC-05). An approved member whose request was pruned is stuck at the gate (SYNC-06). Two items moved to the private
notes.

## Metrics

| Metric | Value | How measured | Target or budget |
| --- | --- | --- | --- |
| Scenario matrix | **27 scenarios: 12 converge as DESIGN says, 5 converge on wrong data (duplicates, double counts, a checked item), 4 diverge between phones, 4 lose a member's change, 1 gets a person stuck, 1 moved to the private notes** (table below) | 18 probes on Pixel_8 (Appendix A), existing two-phone tests, reading | All converge as DESIGN says |
| Time for a change to show on the other phone: stock +1 | **median 108 ms, p95 156 ms** (n = 30; min 56, max 196) | `p10`: phone A presses +1, phone B's `PantryRepository.observe` emits the new quantity; both test phones in one app on Pixel_8, Firestore emulator on the Mac | DESIGN 9.2: median under 1 s, p95 under 3 s |
| Time for a change to show on the other phone: check-off | **median 105 ms, p95 235 ms** (n = 30; min 79, max 241) | `p10`: A checks or unchecks, B's `observeItems` emits it | Same |
| Caveat for both times | Emulator network to the Mac, no real radio, both phones in one process: these are the client's own overhead, a floor. The owner's phone checks give real numbers | — | — |
| Time for a phone that was offline to see a member's change | Not a delay but a **gap**: up to **20 h** for changes skipped by SYNC-01 | `p01`, `p16` | 9.2: under 3 s |
| Removed member back at the gate | **1.2 s** after reconnecting; its unsent write rejected | `p12` | "within seconds" (5.1) |
| Ownership transfer seen on both phones | **160 ms** | `p14` | — |
| Large write: Add to pantry of 180 checked items | 720 changes in 2 batches; applied locally **0.68 s**, confirmed by the server **4.6 s**, all 180 on the other phone **5.8 s**; 180 products, 180 batches, nothing lost | `p11` | 500 operations per batch (DESIGN 8.5 chunks at 400) |
| Large write: Clear household data right after | about 550 operations in 2 batches, **5.6 s**; the other phone's pantry empty after **7.7 s** | `p11` | — |
| Retained Firestore references | **0.** Android: 12 repositories and the writer read `FirestoreProvider.current` per call or follow `instance` with `flatMapLatest`; no class keeps a `FirebaseFirestore` field (the four `db` parameters are per-call). Web: 0 (`sdk.current` per call, `instance$` for listeners) | `grep` for `FirebaseFirestore`, `firestore.current`, `firestore.instance`, `sdk.current` | 0 (CLAUDE.md) |
| Probes written and failed | **18 written, 11 failed** (each failure is a finding below or a private note). Two probe bugs were fixed and rerun: `p03` first waited for a server read while offline; `p16` first read the server through the phone under test, which refilled its cache and hid the bug | Appendix A, JUnit XML | — |
| Field-set parity, Android and web writers | **Identical**: products 13 fields, batches 10, items 13, recipe content 15 + favorite and rating, meals 10; same merge and update choices | Reading the 10 mappers side by side | Same fields (8.1) |

### Scenario matrix

"Converges" means both phones end up showing the same data as the server. Probes are in Appendix A; "existing" means
a test already in `app/src/androidTest`.

| # | Scenario | How checked | Result |
| --- | --- | --- | --- |
| 1 | A change while both phones listen | `p10` | Converges, about 0.1 s |
| 2 | A delete while the other phone listens | existing `DeltaSyncFlowTest` | Converges (tombstone) |
| 3 | A phone back within 20 h catches up on changes and deletes | existing `DeltaSyncFlowTest` | Converges |
| 4 | A phone back after more than 20 h, the delete's tombstone already deleted by the job | `p17` | Converges: the full read drops the deleted item ("bob after 21 h: [Bread]") |
| 5 | A phone writes to a list it isn't showing; the other phone changed the list more than 5 min earlier | `p01` | **Diverges** up to 20 h: added item missing, check-off missing, deleted item still shown (SYNC-01) |
| 6 | The same with the restock engine as the writer (this phone uses a staple up on the Pantry tab) | `p16` | **Diverges** up to 20 h: the other member's item missing (SYNC-01) |
| 7 | The same for the meal week (Plan this recipe, moving a meal to another week) | `p01` | **Diverges** up to 20 h (SYNC-01) |
| 8 | The same for Recent activity | `p01` | **Diverges** up to 20 h (SYNC-01) |
| 9 | A new phone's first sync in a household with 8 staples in stock (LOGIC-02) | `p18`, 3 new phones | Converges: no staple added in 3 tries on the emulator's fast local network (LOGIC-02 stays *Likely* for a slow one) |
| 10 | Add to pantry on both phones at once | `p02` | **Converges on duplicates**: 4 products, 4 batches for 2 items (SYNC-02) |
| 11 | Add to pantry on an offline phone and an online one | `p03` | **Converges on duplicates**, same (SYNC-02) |
| 12 | Both phones run the restock engine | existing `RestockFlowTest` | Converges: one item |
| 13 | A staple added again after Add to pantry | LOGIC-01 (section 03) | Comes back checked (known) |
| 14 | Both check off the same item | reading `SetChecked` (same fields, last write wins) | Converges |
| 15 | The Edit window open while the other phone uses the item | `p05` | **Loses the other member's change**: quantity back to 3 after a use of 2 (SYNC-03) |
| 16 | −1 on an offline phone while the other adds 2 | `p04` | **Loses the other member's change**: whole purchase deleted with 3 in it (SYNC-04) |
| 17 | Both use the same purchase up | `p06` | **Converges on a double count**: Insights "used" 2 for one purchase (SYNC-05) |
| 18 | Both mark the same planned meal cooked | `p07` | **Converges on a double take**: rice 5 → 1 instead of 3, two "cooked" events (SYNC-05) |
| 19 | A delete on one phone while the other edits offline | `p08` | Converges: delete wins. The editor isn't told (SYNC-03) |
| 20 | Undo on one phone after the other edited | reading `Restore*` (full overwrite) | Converges; the other edit is lost (section 03's note for 05) |
| 21 | A list deleted while the other phone adds to it | `p09` | **Loses the item** without a word (SYNC-07) |
| 22 | Removed while offline with unsent writes | `p12` | Converges: writes rejected, gate in 1.2 s |
| 23 | Ownership transfer | `p14` | Converges in 160 ms |
| 24 | Join approved while the joiner's app is closed, request pruned before they open it | `p13` | **Stuck**: a member who can't get in or ask again (SYNC-06) |
| 25 | The same account deleted on one phone, still open on another | `p15` | Moved to the private notes |
| 26 | Add to pantry of 180 items, then Clear household data | `p11` | Converges (2 batches each) |
| 27 | Android and the web writing the same documents | reading the mappers | Converges; Undo's full restore drops fields a newer client added (Ideas) |

## Findings

### SYNC-01 · P1 · Confirmed · Android (web by reading) — A phone that writes to a list while not showing it skips other members' changes for up to 20 hours

- **Where:** `core/firebase/DeltaSync.kt:122-125` (the server listener's cursor is the newest `updatedAt` **in the
  local cache**, minus 5 minutes); `feature/grocery/domain/RestockRunner.kt:55,89` (the engine runs in the application
  scope and writes to the default list from whatever screen is open); `liveNewest`
  (`DeltaSync.kt:152-155`) for Recent activity; the meal week's scope (`FirestoreMealPlanRepository.kt:29-31`). The web
  computes its cursor the same way: `web/src/data/delta-sync.ts:90-95`, `web/src/domain/grocery/restock.ts:147`.
- **What happens:** the cursor assumes everything in the cache came through the server listener, so anything older
  than the newest cached change has been seen. A phone's **own** write breaks that: once the server confirms it, the
  cache holds that document with a fresh server `updatedAt`, even when no listener on the collection is running. The
  next time the phone opens the list, the server listener asks only for changes after that write minus 5 minutes.
  Everything the other members did between the last time this phone listened and 5 minutes before its own write is
  never read: items they added don't appear, items they checked off stay unchecked, items they deleted stay. It
  corrects itself only at the next full read, up to 20 hours after the last one (9.8). Nothing on screen says the list
  is incomplete.

  The common trigger is the restock engine. It writes the Low stock item to the default list on the phone whose
  action drops a staple below Keep at least (other phones' engines may write it too; they race with the first phone's
  "disarmed" and weren't measured). Groceries and Home listen to the default list, and the recipe and week "add to
  list" sheets while they're open; Pantry, Recipes, Plan, Settings, Insights and the background don't. Using up the
  last milk on the Pantry tab is enough: this phone's engine writes "Milk", and when the person then opens Groceries,
  items a partner added on the way home are missing. The meal week has the same gap (Plan this recipe from a recipe, moving a
  meal to another week), and Recent activity too (any action logged while Home isn't showing).

  Two questions from the plan, answered while tracing this: a write committed with an earlier server time can't
  appear after a later one *while a listener runs* (Firestore snapshots are consistent at one read time), so the
  5-minute margin isn't what protects the cursor; and pending server timestamps are null in the cache
  (`getTimestamp` without an estimate) and so are left out of the cursor, which is safe. The gap is only about what
  else fills the cache.
- **Evidence:** `p01` (Appendix A), real services and repositories: Bob adds Butter and Bread; Alice opens the list,
  Recent activity and the week (full reads recorded), then leaves them. Bob adds Eggs, checks Bread, deletes Butter
  and plans Tacos. 5 min 20 s later Alice quick-adds Milk and plans Soup without the screens open; both reach the
  server. Alice opens them:

  ```
  P01 items  server=[Bread(checked), Eggs, Milk] alice=[Bread, Butter, Milk]
  P01 meals  server=[Soup, Tacos] alice=[Soup]
  P01 events server=[Bread, Bread, Butter, Eggs, Milk] alice=[Bread, Butter, Milk]
  P01 full reads unchanged: 2026-11-02T02:57:58.555Z
  ```

  `p16`, the real trigger: Milk ×3 with Keep at least 2 and both phones' restock engines running; Bob adds Bread;
  Alice opens the list once and leaves it; Bob adds Eggs; 5 min 20 s later Alice, on her Pantry tab, uses 2 milk, and
  the engines put Milk on the list; Alice opens Groceries:

  ```
  P16 server=[Bread, Eggs, Milk] alice=[Bread, Milk]
  ```

  For the fix's test: read the server's truth through *another* phone. A server read of the collection on the phone
  under test (`get(Source.SERVER)`) refills its cache and hides the gap; the first run of `p16` passed for that
  reason.
- **Who it affects:** every household with more than one phone; most often households that use Keep at least with
  Add to grocery list automatically, because using a staple up on the Pantry tab is the everyday trigger. The list is
  what people shop from, so the result is missed items and things bought twice. The web app has the same code.
- **Suggested fix:** keep the cursor from what the **server listener** delivered, not from the cache: `SyncLog` stores
  per collection (and per meal window) the newest `updatedAt` seen in a non-cache server snapshot, and the cursor is
  that value minus the margin (or the full read when there is none). Own writes and other queries then can't move it.
  Effort M. Also needs: web port (`delta-sync.ts`, `domain/sync.ts`), DESIGN 9.8 (say where the cursor comes from),
  unit tests for the cursor with a fake log, and an instrumented test like `p01` (make `CURSOR_MARGIN` injectable so
  it doesn't wait 5 minutes). No rules change; older builds keep the gap until they update.

### SYNC-02 · P1 · Confirmed · Android (web by reading) — Add to pantry on two phones adds everything twice and makes duplicate products

- **Where:** `feature/grocery/domain/GroceryService.kt:249-283` (`addToPantry` plans from the phone's copy: a new
  batch with a new ID per item, a new product when the phone's copy has none of that name);
  `feature/pantry/domain/PantryService.kt:40-46` (`planAdd` looks up the product by name in the phone's copy);
  `feature/grocery/data/GroceryChangeMapper.kt:105-107` (the item is removed with a merge that succeeds on an item
  that is already removed). Web: `web/src/domain/grocery/service.ts:287-320`.
- **What happens:** nothing makes Add to pantry happen once. If two people press it for the same checked list before
  each other's write arrives, or one of them is offline (in the store, in the car), both phones write their full
  plan: every item becomes two purchases, and every item without a product becomes **two products with the same
  name**, which breaks invariant 4 (8.5) for good: later adds, the restock engine and Use it up use whichever of the
  two comes first. The second phone's deletes of the grocery items succeed silently on the tombstones the first left.
- **Evidence:** `p02` (both at once) and `p03` (Alice offline presses it, Bob online presses it a second later, Alice
  reconnects). Two checked items, Milk ×2 and Rice ×1:

  ```
  P02 at once:              products=[Rice, Milk, Milk, Rice] stock=[Rice:1.0, Rice:1.0, Milk:2.0, Milk:2.0] items=0
  P03 offline then online:  products=[Rice, Milk, Rice, Milk] stock=[Rice:1.0, Milk:2.0, Milk:2.0, Rice:1.0] items=0
  P03 alice sees [Rice, Milk, Rice, Milk] / [Rice, Milk, Milk, Rice]
  ```

- **Who it affects:** couples who shop together or split the unpacking, and anyone who presses Add to pantry in a
  store with no signal while someone at home does the same. DESIGN's own example of a P1.
- **Suggested fix:** let the grocery item's removal decide who wins, inside the same batch: a rule that refuses an
  update setting `deletedAt` on an item whose stored `deletedAt` is already set. The second phone's whole batch is
  then rejected, including its purchases, and the app can say "Sam already added these to the pantry" from
  `HouseholdWriter.errors`. Products: also look up by name key on the server when the
  phone's copy may be stale, or merge duplicates when a snapshot shows two products with one name key. Effort M. Also
  needs: rules change and tests, rules deploy, web port, DESIGN 5.3 and 9.4, an instrumented test like `p02`/`p03`,
  and a check that no other flow tombstones an item twice on purpose (Clear checked, Delete).

### SYNC-03 · P2 · Confirmed · Android (web by reading) — The Edit window writes every field from the phone's copy and undoes another member's change

- **Where:** `feature/pantry/ui/PantryScreen.kt:111,163,226-236` (the sheet edits the `StockBatch` captured when the
  row was tapped and never updates); `feature/pantry/domain/PantryService.kt:342-356` (`planEdit` always writes
  `UpdateBatch` with quantity, date, date type, location and note) and `:276-281` (`move` does the same);
  `feature/pantry/data/PantryChangeMapper.kt:81-90`. Web: `web/src/domain/pantry/service.ts:427-440`.
- **What happens:** DESIGN 9.4 promises field-level last write wins, and 9.2 says a remote change to the item being
  edited keeps the draft and shows "Sam changed this item. Review". Neither is built. Saving the Edit window writes
  every field as the form holds it, including a quantity nobody touched, so a use, an increase or a move made by
  another member while the window was open is reverted. The same window on an item another member deleted stays open,
  and Save quietly writes to the tombstone (the delete wins, as `p08` shows, but 9.4's "the editor is told the item was
  removed" doesn't happen).
- **Evidence:** `p05`: Alice opens Milk ×3 for editing; Bob uses 2; Alice's phone shows 1, her form still 3; she
  changes only the date and saves:

  ```
  P05 alice's phone shows 1.0 while her form still has 3
  P05 server quantity=3.0 expiry=2026-11-10
  ```

- **Who it affects:** households where two people handle the same food in the same minutes (cooking together,
  unpacking); the pantry then shows food that's gone, and reminders and restocking follow it.
- **Suggested fix:** write only what the person changed (compare the form with the batch it was opened with), and a
  changed quantity as an increment of the difference; while the window is open, watch the batch: show 9.2's "Sam
  changed this item. Review" when its `updatedAt` moves, and close with "Sam removed this item" when it disappears.
  Same for the product fields written by `UpdateProduct` (a stale `restockArmed` can re-arm a staple whose item a
  member just deleted). Effort M. Also needs: web port, DESIGN 9.2 and 9.4 (or record the deviation in DECISIONS),
  a two-phone test, a Roborazzi image of the notice.

### SYNC-04 · P2 · Confirmed · Android (web by reading) — −1 on a stale copy deletes the whole purchase, including what another member added

- **Where:** `feature/pantry/domain/PantryService.kt:128-139` (`adjust` turns "−1 reaching 0 in this phone's copy"
  into `usedUp`, a tombstone of the whole batch). Web: `web/src/domain/pantry/service.ts:218-226`.
- **What happens:** +1 and −1 are increments so they commute (9.3, 9.4), except the last −1: when the phone's copy says
  1, it deletes the batch instead. If another member added to that purchase in the meantime (offline phone, or within
  the sync delay), their addition goes with it. Section 03 suspected this; it's reproduced here.
- **Evidence:** `p04`: Yogurt ×1 on both phones; Alice goes offline; Bob presses +2 (server 3); Alice, still seeing 1,
  presses −1; she reconnects:

  ```
  P04 server quantity=3.0 deletedAt=Timestamp(seconds=1793588640, nanoseconds=832000000)
  ```

  No yogurt is shown anywhere, though 2 should be left. It is also counted as one "used" in Insights.
- **Who it affects:** households that use +/− on shared staples, mostly with one phone offline (the plan's store
  scenario). Rarer than SYNC-02, same kind of loss.
- **Suggested fix:** let −1 always be an increment, and use up the batch only when a server snapshot shows it at 0 or
  below (invariant 1's "whichever client notices"), counting it for Insights then (which also fixes section 03's note
  that `deleteEmptyBatches` doesn't count). The Undo stays "+1". Effort M. Also needs: web port, DESIGN 9.4, a test
  like `p04`.

### SYNC-05 · P3 · Confirmed · Android (web by reading) — Finishing the same thing on two phones counts it twice, and Cooked takes the ingredients twice

- **Where:** `feature/pantry/domain/PantryService.kt:135-139` (`usedUp` adds 1 to Insights' "used" whether or not the
  batch is already gone); `feature/plan/domain/MealPlanService.kt:68-86` with `PantryService.kt:191-227` (Cooked takes
  the recipe's amounts from the phone's copy); `firebase/firestore.rules:234-237` (a meal may be marked cooked again by
  someone else). Web: `web/src/domain/pantry/service.ts:225`, `web/src/domain/plan/service.ts:147`.
- **What happens:** two people use up the same purchase at about the same time (or one offline): both tombstones land
  (harmless) but both increments land too, so Insights shows 2 used for one purchase. Two people mark the same planned
  dinner Cooked: each phone takes the recipe's amounts off the pantry, so it's taken twice, and Recent activity shows
  two "cooked" events.
- **Evidence:** `p06`: "used count for one batch used up on two phones: 2". `p07`: rice 5, a recipe that takes 2,
  Cooked on both phones at once: "rice left … 1.0 (of 5, recipe takes 2); cooked events=2".
- **Who it affects:** rare (needs simultaneous action or an offline phone), but the Cooked case leaves wrong stock.
- **Suggested fix:** for Cooked, a rules check that a meal can only go from not cooked to cooked
  (`resource.data.get('cookedAt', null) == null` when `cookedBy` is set): the second phone's whole batch, pantry takes
  included, is rejected. For the count, only count a use-up in the same batch as a tombstone that wasn't there (the
  same rule idea as SYNC-02), or accept and record it in DECISIONS. Effort S to M. Also needs: rules tests and deploy,
  web port.

### SYNC-06 · P2 · Confirmed · Android (web by reading) — An approved member whose request was pruned is stuck at the gate

- **Where:** `backend/reminder/src/job.ts:232` (the daily prune deletes join requests older than 7 days, **whatever
  their status**, approved ones included); `feature/household/domain/SessionManager.kt:209-222` (a missing request
  means `JoinState.Expired`); `firebase/firestore.rules:127-129` (a new request is refused when you're already a
  member); `res/values/strings.xml:85`. Web: `web/src/domain/session.ts:230`.
- **What happens:** the owner's approval makes the person a member at once; the joiner's own app finishes the join
  (points the profile at the household) the next time it runs. If the joiner doesn't open the app within 7 days of
  sending the request, the prune deletes the approved request first. Their app then says "Your request to join … expired
  before the owner answered. Ask for a new code and try again", which is false, and trying again fails with "You
  don't have permission to do that", because they are already a member. The owner sees them in the member list. Only
  the owner removing them (which nobody would guess) gets them out.
- **Evidence:** `p13`: Carol requests and closes the app; Alice approves; the request is deleted as the prune would;
  Carol opens the app: `NoHousehold(… join=Expired(…))`, members `[Alice, Bob, Carol]`, asking again: `PermissionDenied`.
  (The prune hasn't been running, per section 02's SECC-04; once it runs, this can happen.)
- **Who it affects:** a person who sends a request and doesn't open the app for a week; rare, but a dead end with a
  misleading message for someone who has never used the app.
- **Suggested fix:** when the request is missing, check the membership before saying Expired: if
  `members/{uid}` exists, complete the join (`completeJoin` without the request delete). Also prune only pending and
  declined requests, and approved ones only after they're completed. Effort S. Also needs: web port, the job's prune
  and its test, a session unit test.

### SYNC-07 · P3 · Confirmed · Android (web by reading) — An item added to a list as another member deletes it disappears without a word

- **Where:** `feature/grocery/domain/GroceryService.kt:235-243` (Delete list deletes the items it finds on the server
  and the list, for real); `firebase/firestore.rules:206-208` (items can be created under a list that doesn't exist).
- **What happens:** Bob adds "Chips" to Party (offline, or in the second before the delete arrives) while Alice deletes
  Party. Bob's write succeeds, under a list that no longer exists, so no screen shows it and no message says it was
  lost; Bob's list switches to Groceries. The rest is in the private notes.
- **Evidence:** `p09`: "list exists=false orphan items=[…:Chips] bob lists=[Groceries] (bob had Party)".
- **Who it affects:** rare; a lost item on a shared list.
- **Suggested fix:** a rule that creates items only under an existing list, so the late add is rejected and the app
  says "That list was deleted" (one extra read per item write; see the private notes for the trade-off). Effort S.
  Also needs: rules tests and deploy, a message string, web port.

### SYNC-08 · P3 · Likely · Android — A large write that fails half-way leaves half of it, and Clear household data can report a failure that later half-succeeds

- **Where:** `core/firebase/FirestoreHouseholdWriter.kt:46-62` (each 400-change chunk is its own batch; section 03
  noted it); `FirestoreHouseholdRepository.kt:341-345` (Clear household data commits its chunks one by one with
  `commitConfirmed`) and `core/firebase/FirestoreFlows.kt:80-86` (`commitConfirmed` throws Offline after 15 s while
  the batch stays queued).
- **What happens:** `p11` shows both large writes complete when online (Metrics). Two ways they can end half done:
  a chunk rejected by the rules while the others land (Undo then restores more than was written), and Clear household
  data timing out on a slow connection: it shows "You're offline" for chunk *n*, but that chunk is still queued and is
  sent later, while chunks after it never are. The household then ends up half cleared, with no "cleared" event.
- **Evidence:** reading; the online path measured in `p11`. *Likely*, not reproduced: needs a connection that stalls
  for more than 15 s in the middle of a clear, which the emulator setup here can't produce reliably.
- **Who it affects:** an owner clearing a large household on a weak connection; rare.
- **Suggested fix:** for Clear household data, refuse to start offline and, on a timeout, keep going (the next chunks
  queue too) instead of stopping, then say "Clearing will finish when you're online". Effort S.

### SYNC-09 · P3 · Likely · Android — Signing out with unsent changes drops them without a warning

- **Where:** `core/firebase/FirestoreProvider.kt:28-35` (waits up to 5 s for pending writes, then clears the cache,
  queue included); the Sign out confirmation says nothing about unsent changes.
- **What happens:** someone edits offline and then signs out (or leaves the household) before the phone reconnects:
  the edits are thrown away with the cache. SECC-03 covers another part of the same path.
- **Evidence:** reading. *Likely*: the code path is clear; the Settings sign-out on this commit has SECC-01, so the
  probe would have measured that bug instead.
- **Who it affects:** rare; someone signing out offline right after changes.
- **Suggested fix:** when `waitForPendingWrites` doesn't finish, say "Some changes haven't synced yet. Sign out
  anyway?" before clearing. Effort S. Also needs: a string, the web (same 3 s wait).

### SYNC-10 · P3 · Likely · Android — One listener error stops the restock engine on that phone until the app restarts

- **Where:** `feature/grocery/domain/RestockRunner.kt:69-70` (`combine(...).catch { }` completes the inner flow; the
  outer `flatMapLatest` restarts it only when the household or user changes).
- **What happens:** any error on the products, stock or lists listener (the quota running out, a permission refusal
  that outlasts `retryAfterOwnWrites`) ends the engine's flow for the rest of the process, without a message.
- **Evidence:** reading. *Likely*: the emulator can't run out of quota.
- **Who it affects:** the other phones still run the engine, so a household rarely notices; a one-phone household
  loses automatic restocking until the app restarts.
- **Suggested fix:** `retryWhen` with a backoff instead of `catch { }`, as `SessionManager` does. Effort S.

## Ideas

- **A "sync check" on the hidden debug screen.** A button that runs the full read for every collection now and shows
  how many documents changed compared with the cache. It would make SYNC-01-style gaps visible during beta testing
  and costs reads only when pressed.
- **Undo writes the whole document back.** Every `Restore*` change (Undo of delete, clear, throw away) is a `set`
  without merge on both platforms, so a field written by a newer client (8.1's "new fields optional") is dropped on
  Undo, and `checkedAt` always comes back null and `createdBy` becomes the person who pressed Undo for batches. A merge
  with `deletedAt: FieldValue.delete()` would keep unknown fields. Today both apps write the same fields, so nothing is lost yet.
- **Make the full read cheaper to trust.** The 20-hour full read is the only thing that repairs a cache that missed
  a change (SYNC-01's gaps, or a document removed without a tombstone). Starting it also when the app comes back after
  more than 30 minutes (which Firestore bills as a new query anyway, 9.7) would bound the damage of any future cursor
  bug to half an hour, at a quota cost section 09 can weigh.
- **One name for "the phone's copy".** Many plans (Add to pantry, −1, Edit, Cooked) are computed from the phone's copy
  and written blind. A short paragraph in DESIGN 9.4 listing which writes are increments, which are "set from my copy"
  and which need a rules guard would make the next feature safer.

## For other sections

- **05 Reliability:** `SessionManager.sessionFor` retries any error on the profile or membership listener by emitting
  `Session.Loading`, which replaces the whole app with a spinner (SECC-01 explains why); a quota error there would
  show a spinner instead of 7.5's banner. Undo's full overwrite (Ideas) matters for version skew (8.6). A
  `SetRestockArmed` update on a product deleted outright fails the whole engine batch and is retried every 30 s
  (`REPEAT_GUARD_MS`) until the next full read.
- **06 UX:** "Request expired … before the owner answered" is shown for approved requests (SYNC-06); Delete list has
  no Undo (LOGIC-10) and no warning that someone may be adding to it.
- **09 Efficiency:** every `pantry.observe` call starts its own pair of listeners with its own cursor (Home, Pantry,
  Groceries, Plan, recipe detail and the restock engine each call it), so up to six server queries on `products` and
  `stock` can run at once with different cursors; each costs at least one read when it starts. Worth counting in
  quota-sim.
- **10 Web:** every finding above was read in the web's code, not run; section 10's mixed-household run (Pixel_8 and
  WebKit) can repeat `p01`, `p02` and `p05` with the web as one phone.
- **11 Code:** `RestockFlowTest`'s `settledItems` comment already notes that a new listener's first snapshot can be a
  stale cache; SYNC-01 is the case where it stays stale.

## Not covered

- **The web app, run.** Every web statement here comes from reading `web/src/data` and `web/src/domain`, which follow
  Android line by line; nothing was run in WebKit. Section 10 owns the mixed-household run.
- **Real phones and real networks.** Latency is the emulator's floor (Metrics); the owner's phone checks below give
  real numbers. Slow or lossy networks (emulator network profiles) were not tried; section 05 owns them.
- **The Android UI end to end for SYNC-01.** The probes use the production repositories, services and restock runner
  through `TestPhone`, not the screens. Which screens listen to the default list was established by reading
  (`HomeViewModel.kt:105-108`, `GroceriesViewModel.kt:110`, `RecipeDetailViewModel.kt:76-92` while its sheet is
  open, `PlanViewModel.kt:191-204`).
- **Partial failures of large writes** (SYNC-08) and **sign-out with unsent changes** (SYNC-09): read, not reproduced.
- **Account deletion with unsent writes, switching accounts on one phone:** read only. The cache is cleared on both
  paths (when sign-out works, SECC-01), so the next account starts with a full read.
- **Quota exhaustion:** the emulator can't run out; SYNC-10 and the 05 note are from reading.

## Phone checks for the owner

Two real phones in one household: the Android phone with the beta and the iPhone with the web app, both online
unless a step says otherwise. Note the models and OS versions once in the Result column.

| # | Phone | Steps | Expected | Result |
| --- | --- | --- | --- | --- |
| 1 | Both | Open Groceries on both. On the Android phone, check off an item and time how long until it shows checked on the iPhone. Repeat five times, then the other way round. | Under 1 s each (DESIGN 9.2). Write the times. | |
| 1b | Both | Count "3, 2, 1" and check off the same item on both phones at the same moment. Look at the list and Home's Recent activity on both. | Checked on both, no flicker back. Note whether Recent activity shows one "checked" or two. | |
| 2 | Both | Put two test items on a list and check both off on both phones. Count "3, 2, 1" and press Add to pantry on both at the same moment, with the same dates. Then open Pantry. | Each item once (DESIGN). SYNC-02 predicts each twice, and two products of each name. Delete the extras afterwards. | |
| 3 | Both | Same as 2, but put the Android phone in airplane mode first, press Add to pantry there, then press it on the iPhone, then turn airplane mode off. | Each item once. SYNC-02 predicts duplicates. | |
| 4 | Both | Make a test product with 3 in stock. Android in airplane mode: Edit it (change only the date) and Save. iPhone: delete it. Turn airplane mode off. | The product is gone on both phones (delete wins). Note whether the Android phone said anything. | |
| 5 | Both | Make a test staple: 3 in stock, Keep at least 2, Add to grocery list automatically on. On the Android phone, open Groceries once, then go to the Pantry tab and wait a minute. Then on the iPhone add "Test eggs" to Groceries. Keep the Android phone on the Pantry tab (don't open Home or Groceries) for 6 more minutes, then use 2 of the staple with −. Then open Groceries on the Android phone. | Both the staple and "Test eggs" are on the list. SYNC-01 predicts "Test eggs" is missing on Android until tomorrow (it shows on the iPhone). | |
| 6 | Both | Plan a test dinner from a recipe that uses something in the pantry, for today. Open the day on both phones and press Cooked on both at the same moment. Check the pantry. | The recipe's amounts are taken once. SYNC-05 predicts twice. | |
| 7 | Android | Open a test item's Edit window and leave it open. On the iPhone, use 1 of the same item. On Android, change only the location and Save. | The iPhone's use stays (quantity down by 1). SYNC-03 predicts the old quantity comes back. | |

## Appendix A: the probe test (`app/src/androidTest/kotlin/com/parcha/spoilnot/review/SyncProbeTest.kt`, run and deleted)

Run with the emulators and Pixel_8 up, for example one probe:

```
./gradlew :app:connectedDevDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.tests_regex=.*SyncProbeTest.*p02_.*"
```

(A comma-separated `class=…#a,…#b` list ran only the first method here; `tests_regex` works.) Results are logged
under the tag `SyncProbe` (`adb logcat -s SyncProbe:I`). `p01` and `p16` each wait 5 min 20 s. Each probe asserts
what DESIGN says, so on this commit `p01`–`p07`, `p09`, `p13`, `p15` and `p16` fail; `p08`, `p10`–`p12`, `p14`,
`p17` and `p18` pass.

```kotlin
package com.parcha.spoilnot.review

import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.firestore.Source
import com.parcha.spoilnot.EmulatorSupport
import com.parcha.spoilnot.EmulatorSupport.PASSWORD
import com.parcha.spoilnot.EmulatorSupport.uniqueEmail
import com.parcha.spoilnot.TestPhone
import com.parcha.spoilnot.core.write.Actor
import com.parcha.spoilnot.feature.grocery.domain.GroceryChange
import com.parcha.spoilnot.feature.grocery.domain.GroceryItem
import com.parcha.spoilnot.feature.grocery.domain.ItemSource
import com.parcha.spoilnot.feature.grocery.domain.PantryEntry
import com.parcha.spoilnot.feature.household.domain.InviteCodes
import com.parcha.spoilnot.feature.household.domain.JoinState
import com.parcha.spoilnot.feature.household.domain.Role
import com.parcha.spoilnot.feature.household.domain.Session
import com.parcha.spoilnot.feature.pantry.domain.Category
import com.parcha.spoilnot.feature.pantry.domain.DateType
import com.parcha.spoilnot.feature.pantry.domain.ItemEdit
import com.parcha.spoilnot.feature.pantry.domain.Location
import com.parcha.spoilnot.feature.pantry.domain.NameKey
import com.parcha.spoilnot.feature.pantry.domain.PantryData
import com.parcha.spoilnot.feature.pantry.domain.QuantityUnit
import com.parcha.spoilnot.feature.pantry.domain.StockDraft
import com.parcha.spoilnot.feature.plan.domain.MealSlot
import com.parcha.spoilnot.feature.recipes.domain.Ingredient
import com.parcha.spoilnot.feature.recipes.domain.Recipe
import com.parcha.spoilnot.feature.recipes.domain.RecipeContent
import com.parcha.spoilnot.feature.recipes.domain.SaveResult
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Review 04 probes (temporary): two-phone sync cases on the emulators. Each asserts what DESIGN says. */
@RunWith(AndroidJUnit4::class)
class SyncProbeTest {
    private lateinit var alice: TestPhone
    private lateinit var bob: TestPhone
    private val extra = mutableListOf<TestPhone>()
    private lateinit var householdId: String
    private lateinit var listId: String
    private lateinit var code: String
    private val today: LocalDate = LocalDate.now()

    @Before
    fun setUp() = runBlocking {
        EmulatorSupport.requireEmulatorAndNetworkAccess()
        alice = TestPhone("alice")
        bob = TestPhone("bob")
        alice.sessionManager.signUp("Alice", uniqueEmail("alice"), PASSWORD)
        bob.sessionManager.signUp("Bob", uniqueEmail("bob"), PASSWORD)
        alice.awaitSession { it is Session.NoHousehold }
        bob.awaitSession { it is Session.NoHousehold }
        code = InviteCodes.generate()
        householdId = alice.households.createHousehold(alice.uid, "Alice", "Sync probe", code)
        alice.awaitSession { it is Session.InHousehold }
        join(bob, "Bob")
        listId = withTimeout(10_000) {
            bob.groceryRepository.observeLists(householdId).first { it.any { l -> l.isDefault } }
        }.single { it.isDefault }.id
    }

    @After
    fun tearDown() {
        extra.forEach { runCatching { it.close() } }
        if (::alice.isInitialized) alice.close()
        if (::bob.isInitialized) bob.close()
    }

    private suspend fun join(phone: TestPhone, name: String) {
        phone.households.requestToJoin(phone.uid, name, checkNotNull(phone.households.findInviteCode(code)))
        val request = withTimeout(10_000) {
            alice.households.observeJoinRequests(householdId).first { r -> r.any { it.uid == phone.uid } }
        }.single { it.uid == phone.uid }
        alice.households.approve(householdId, request)
        phone.awaitSession { it is Session.InHousehold }
    }

    private fun log(message: String) = Log.i(TAG, message)

    private val noPantry = PantryData(emptyList(), emptyList(), fromCache = false)

    private fun itemsPath() = "households/$householdId/groceryLists/$listId/items"

    private fun draft(name: String, quantity: Double, location: Location = Location.Fridge, days: Long = 5) =
        StockDraft(
            name, quantity, QuantityUnit.Pieces, today.plusDays(days), DateType.UseBy, location, Category.Dairy,
            note = null, minQuantity = null, autoAddToGrocery = false, buyQuantity = null,
        )

    private fun checkedItem(id: String, name: String, quantity: Double) = GroceryItem(
        id = id, name = name, nameKey = NameKey.of(name), quantity = quantity, unit = QuantityUnit.Pieces, note = null,
        category = Category.Dairy, checked = true, checkedBy = null, source = ItemSource.Manual, productId = null,
        recipeId = null, createdBy = null, createdAt = null,
    )

    /** The last value [this] emits within [ms]: a new listener's first snapshot may be a stale cache. */
    private suspend fun <T> Flow<T>.settled(ms: Long = 3_000): T? {
        var last: T? = null
        withTimeoutOrNull(ms) { collect { last = it } }
        return last
    }

    private suspend fun TestPhone.pantryData(predicate: (PantryData) -> Boolean = { true }): PantryData =
        withTimeout(20_000) { pantryRepository.observe(householdId).first { !it.fromCache && predicate(it) } }

    private suspend fun TestPhone.items(predicate: (List<GroceryItem>) -> Boolean): List<GroceryItem> =
        withTimeout(20_000) { groceryRepository.observeItems(householdId, listId).first(predicate) }

    private suspend fun TestPhone.flush() = withTimeout(30_000) { firestore.current.waitForPendingWrites().await() }

    /** What the server holds (tombstones left out), read past any phone's cache. */
    private suspend fun server(path: String, via: TestPhone = alice) =
        via.firestore.current.collection(path).get(Source.SERVER).await()
            .documents.filter { it.get("deletedAt") == null }

    // ---------------------------------------------------------------------------------------------------------------
    // P01: a phone's own write to a collection it is not listening to moves its cursor past changes it never saw.

    @Test
    fun p01_ownWriteWhileNotListening_skipsOtherPhonesChanges() = runBlocking {
        val itemsPath = itemsPath()
        val activityPath = "households/$householdId/activity"
        val from = today
        val to = today.plusDays(6)
        val mealsKey = "households/$householdId/meals?date=$from..$to"

        bob.grocery.quickAdd(householdId, bob.actor, listId, "Butter", emptyList(), noPantry)
        bob.grocery.quickAdd(householdId, bob.actor, listId, "Bread", emptyList(), noPantry)
        bob.flush()

        // Alice opens Groceries, Home's activity and the week once (full reads), then leaves those screens.
        val first = alice.items { it.size == 2 }.map { it.name }
        withTimeout(20_000) {
            alice.groceryRepository.observeItems(householdId, listId)
                .first { alice.syncLog.lastFullSync(itemsPath) != null }
        }
        withTimeout(20_000) {
            alice.activity.observeRecent(householdId, 30).first { alice.syncLog.lastFullSync(activityPath) != null }
        }
        withTimeout(20_000) {
            alice.mealPlanRepository.observe(householdId, from, to)
                .first { alice.syncLog.lastFullSync(mealsKey) != null }
        }
        log("P01 alice first read: $first")

        // While Alice listens to none of them, Bob adds Eggs, checks Bread, deletes Butter and plans Tacos.
        val bobItems = bob.items { it.size == 2 }
        bob.grocery.quickAdd(householdId, bob.actor, listId, "Eggs", bobItems, noPantry)
        bob.grocery.setChecked(householdId, bob.actor, listId, bobItems.single { it.name == "Bread" }, true)
        bob.grocery.delete(householdId, bob.actor, listId, bobItems.single { it.name == "Butter" })
        bob.mealPlan.addOther(householdId, bob.actor, today.plusDays(1), MealSlot.Dinner, "Tacos", emptyList(), emptyList())
        bob.flush()
        log("P01 bob changed the list at ${System.currentTimeMillis()}; waiting past the 5-minute margin")

        delay(WAIT_PAST_MARGIN_MS)

        // Alice writes to the list, the activity and the week without listening (as the restock engine, a recipe's
        // Add to list or Plan this recipe do), and her write reaches the server.
        alice.grocery.quickAdd(householdId, alice.actor, listId, "Milk", emptyList(), noPantry)
        alice.mealPlan.addOther(
            householdId, alice.actor, today.plusDays(2), MealSlot.Lunch, "Soup", emptyList(), emptyList(),
        )
        alice.flush()

        val aliceItems = checkNotNull(alice.groceryRepository.observeItems(householdId, listId).settled(5_000))
        val aliceEvents = checkNotNull(alice.activity.observeRecent(householdId, 30).settled(5_000))
        val aliceMeals = checkNotNull(alice.mealPlanRepository.observe(householdId, from, to).settled(5_000))

        val truthItems = server(itemsPath).map { "${it.getString("name")}${if (it.getBoolean("checked") == true) "(checked)" else ""}" }.sorted()
        val shownItems = aliceItems.map { "${it.name}${if (it.checked) "(checked)" else ""}" }.sorted()
        val truthMeals = server("households/$householdId/meals").mapNotNull { it.getString("title") }.sorted()
        val shownMeals = aliceMeals.meals.map { it.title }.sorted()
        val truthEvents = server(activityPath).mapNotNull { it.get("params.name") as? String }.sorted()
        val shownEvents = aliceEvents.mapNotNull { it.name }.sorted()
        log("P01 items  server=$truthItems alice=$shownItems")
        log("P01 meals  server=$truthMeals alice=$shownMeals")
        log("P01 events server=$truthEvents alice=$shownEvents")
        log("P01 full reads unchanged: ${alice.syncLog.lastFullSync(itemsPath)}")
        assertEquals("Alice's list after her own write while away", truthItems, shownItems)
        assertEquals("Alice's week after her own write while away", truthMeals, shownMeals)
        assertEquals("Alice's Recent activity after her own write while away", truthEvents, shownEvents)
    }

    // ---------------------------------------------------------------------------------------------------------------
    // P02, P03: Add to pantry on two phones for the same checked list.

    private suspend fun seedCheckedList() {
        bob.writer.apply(
            householdId,
            bob.actor,
            listOf(
                GroceryChange.CreateItem(listId, checkedItem(bob.writer.newId(), "Milk", 2.0)),
                GroceryChange.CreateItem(listId, checkedItem(bob.writer.newId(), "Rice", 1.0)),
            ),
        )
        bob.flush()
    }

    private suspend fun TestPhone.pressAddToPantry(items: List<GroceryItem>, data: PantryData? = null) =
        grocery.addToPantry(
            householdId,
            actor,
            listId,
            items.map { PantryEntry(it, it.quantity ?: 1.0, QuantityUnit.Pieces, today.plusDays(7), Location.Pantry) },
            emptyList(),
            data ?: pantryData(),
        )

    private suspend fun reportPantry(label: String): Pair<Int, Int> {
        val products = server("households/$householdId/products")
        val stock = server("households/$householdId/stock")
        val items = server(itemsPath())
        log(
            "$label products=${products.map { it.getString("name") }} " +
                "stock=${stock.map { "${it.getString("productName")}:${it.getDouble("quantity")}" }} items=${items.size}",
        )
        return products.size to stock.size
    }

    @Test
    fun p02_addToPantryOnBothPhonesAtOnce_addsOnce() = runBlocking {
        seedCheckedList()
        val a = alice.items { it.size == 2 && it.all { i -> i.checked } }
        val b = bob.items { it.size == 2 && it.all { i -> i.checked } }
        listOf(async { alice.pressAddToPantry(a) }, async { bob.pressAddToPantry(b) }).awaitAll()
        alice.flush()
        bob.flush()
        delay(2_000)
        val (products, stock) = reportPantry("P02 at once:")
        assertEquals("products after two Add to pantry", 2, products)
        assertEquals("batches after two Add to pantry", 2, stock)
    }

    @Test
    fun p03_addToPantryOfflineThenOnline_addsOnce() = runBlocking {
        seedCheckedList()
        val a = alice.items { it.size == 2 && it.all { i -> i.checked } }
        val b = bob.items { it.size == 2 && it.all { i -> i.checked } }
        val aliceData = alice.pantryData()
        alice.firestore.current.disableNetwork().await()
        alice.pressAddToPantry(a, aliceData)
        log("P03 alice (offline) pressed Add to pantry")
        delay(1_000)
        bob.pressAddToPantry(b)
        bob.flush()
        log("P03 bob (online) pressed Add to pantry")
        alice.firestore.current.enableNetwork().await()
        alice.flush()
        delay(2_000)
        val (products, stock) = reportPantry("P03 offline then online:")
        val aliceView = alice.pantryData()
        log("P03 alice sees ${aliceView.products.map { it.name }} / ${aliceView.batches.map { it.productName }}")
        assertEquals("products", 2, products)
        assertEquals("batches", 2, stock)
    }

    // ---------------------------------------------------------------------------------------------------------------
    // P04, P05: stale copies.

    @Test
    fun p04_minusOneOnAStaleCopy_keepsTheOtherPhonesAddition() = runBlocking {
        alice.pantry.add(householdId, alice.actor, draft("Yogurt", 1.0), alice.pantryData())
        val batch = alice.pantryData { it.batches.size == 1 }.batches.single()
        bob.pantryData { it.batches.size == 1 }
        alice.firestore.current.disableNetwork().await()
        // Bob adds 2 to the same purchase; Alice, offline in the store, still sees 1 and presses minus.
        bob.pantry.adjust(householdId, bob.actor, bob.pantryData().batches.single(), 2.0)
        bob.flush()
        alice.pantry.adjust(householdId, alice.actor, batch, -1.0)
        alice.firestore.current.enableNetwork().await()
        alice.flush()
        delay(2_000)
        val doc = alice.firestore.current.document("households/$householdId/stock/${batch.id}").get(Source.SERVER).await()
        log("P04 server quantity=${doc.getDouble("quantity")} deletedAt=${doc.get("deletedAt")}")
        val live = server("households/$householdId/stock")
        assertEquals("Yogurt left after +2 and -1 (expected 2)", 2.0, live.sumOf { it.getDouble("quantity") ?: 0.0 }, 0.0)
    }

    @Test
    fun p05_editWindowOpenWhileOtherPhoneUses_keepsTheUse() = runBlocking {
        alice.pantry.add(householdId, alice.actor, draft("Milk", 3.0), alice.pantryData())
        val opened = alice.pantryData { it.batches.size == 1 }
        val batch = opened.batches.single()
        // Alice opens the Edit window (form filled from her copy: 3). Bob uses 2 meanwhile.
        bob.pantry.adjust(householdId, bob.actor, bob.pantryData { it.batches.size == 1 }.batches.single(), -2.0)
        bob.flush()
        // Alice's phone has the new quantity by now; her open form still says 3.
        val seen = alice.pantryData { it.batches.singleOrNull()?.quantity == 1.0 }
        log("P05 alice's phone shows ${seen.batches.single().quantity} while her form still has 3")
        alice.pantry.editItem(
            householdId,
            alice.actor,
            batch,
            ItemEdit(
                name = "Milk", quantity = batch.quantity, unit = QuantityUnit.Pieces, category = Category.Dairy,
                expiryDate = today.plusDays(9), location = Location.Fridge, autoAddToGrocery = false,
                minQuantity = null, buyQuantity = null,
            ),
            seen,
        )
        alice.flush()
        val doc = alice.firestore.current.document("households/$householdId/stock/${batch.id}").get(Source.SERVER).await()
        log("P05 server quantity=${doc.getDouble("quantity")} expiry=${doc.getString("expiryDate")}")
        assertEquals("quantity after Bob used 2 and Alice changed only the date", 1.0, doc.getDouble("quantity")!!, 0.0)
    }

    // ---------------------------------------------------------------------------------------------------------------
    // P06, P07: both phones finish the same thing.

    @Test
    fun p06_usedUpOnBothPhones_countsOnce() = runBlocking {
        alice.pantry.add(householdId, alice.actor, draft("Cream", 1.0), alice.pantryData())
        val a = alice.pantryData { it.batches.size == 1 }.batches.single()
        val b = bob.pantryData { it.batches.size == 1 }.batches.single()
        listOf(
            async { alice.pantry.usedUp(householdId, alice.actor, a) },
            async { bob.pantry.usedUp(householdId, bob.actor, b) },
        ).awaitAll()
        alice.flush()
        bob.flush()
        val months = withTimeout(10_000) {
            alice.stats.observeMonths(householdId, YearMonth.from(today)).first { m -> m.any { it.used > 0 } }
        }
        delay(1_500)
        val used = alice.stats.observeMonths(householdId, YearMonth.from(today)).settled(2_000)?.sumOf { it.used }
        log("P06 used count for one batch used up on two phones: $used (first $months)")
        assertEquals("Insights 'used' for one batch", 1, used)
    }

    @Test
    fun p07_cookedOnBothPhones_takesOnce() = runBlocking {
        alice.pantry.add(householdId, alice.actor, draft("Rice", 5.0, Location.Pantry, days = 300), alice.pantryData())
        val pantry = alice.pantryData { it.products.size == 1 }
        val rice = pantry.products.single()
        val content = RecipeContent(title = "Risotto", servings = 2, ingredients = listOf(Ingredient("2 rice", 2.0, null, "rice")))
        val saved = alice.recipes.save(householdId, alice.actor, content, emptyList()) as SaveResult.Saved
        val recipe = Recipe(saved.recipeId, content)
        alice.mealPlan.addRecipe(householdId, alice.actor, today, MealSlot.Dinner, recipe, emptyList(), emptyList())
        alice.flush()
        val aMeal = withTimeout(20_000) {
            alice.mealPlanRepository.observe(householdId, today, today).first { !it.fromCache && it.meals.size == 1 }
        }.meals.single()
        val bMeal = withTimeout(20_000) {
            bob.mealPlanRepository.observe(householdId, today, today).first { !it.fromCache && it.meals.size == 1 }
        }.meals.single()
        val amounts = mapOf(rice.id to 2.0)
        listOf(
            async { alice.mealPlan.cooked(householdId, alice.actor, aMeal, recipe, amounts, alice.pantryData()) },
            async { bob.mealPlan.cooked(householdId, bob.actor, bMeal, recipe, amounts, bob.pantryData()) },
        ).awaitAll()
        alice.flush()
        bob.flush()
        delay(1_500)
        val left = server("households/$householdId/stock").sumOf { it.getDouble("quantity") ?: 0.0 }
        val cookedEvents = server("households/$householdId/activity").count { it.getString("type") == "recipe_cooked" }
        log("P07 rice left after one dinner cooked on two phones: $left (of 5, recipe takes 2); cooked events=$cookedEvents")
        assertEquals("rice left", 3.0, left, 0.0)
    }

    // ---------------------------------------------------------------------------------------------------------------
    // P08, P09: delete against edit or add.

    @Test
    fun p08_deleteWhileTheOtherEditsOffline_deleteWins() = runBlocking {
        alice.pantry.add(householdId, alice.actor, draft("Ham", 2.0), alice.pantryData())
        val a = alice.pantryData { it.batches.size == 1 }
        val batch = a.batches.single()
        bob.pantryData { it.batches.size == 1 }
        alice.firestore.current.disableNetwork().await()
        alice.pantry.editItem(
            householdId, alice.actor, batch,
            ItemEdit("Ham", 2.0, QuantityUnit.Pieces, Category.Dairy, today.plusDays(12), Location.Freezer, false, null, null),
            a,
        )
        bob.pantry.throwAway(
            householdId, bob.actor, bob.pantryData().batches.single(),
            com.parcha.spoilnot.feature.pantry.domain.DiscardReason.Spoiled,
        )
        bob.flush()
        alice.firestore.current.enableNetwork().await()
        alice.flush()
        val aliceView = alice.pantryData().batches.map { it.productName }
        val bobView = bob.pantryData().batches.map { it.productName }
        val live = server("households/$householdId/stock").size
        log("P08 alice=$aliceView bob=$bobView server live=$live")
        assertTrue("delete wins on both phones", aliceView.isEmpty() && bobView.isEmpty() && live == 0)
    }

    @Test
    fun p09_listDeletedWhileTheOtherAddsOffline_itemIsNotLostSilently() = runBlocking {
        val party = alice.grocery.createList(householdId, alice.actor, "Party")
        alice.flush()
        val bobList = withTimeout(10_000) {
            bob.groceryRepository.observeLists(householdId).first { l -> l.any { it.id == party } }
        }.single { it.id == party }
        bob.firestore.current.disableNetwork().await()
        bob.grocery.quickAdd(householdId, bob.actor, party, "Chips", emptyList(), noPantry)
        val aliceList = withTimeout(10_000) {
            alice.groceryRepository.observeLists(householdId).first { l -> l.any { it.id == party } }
        }.single { it.id == party }
        alice.grocery.deleteList(householdId, alice.actor, aliceList)
        alice.flush()
        bob.firestore.current.enableNetwork().await()
        bob.flush()
        delay(1_500)
        val orphans = alice.firestore.current.collection("households/$householdId/groceryLists/$party/items")
            .get(Source.SERVER).await().documents
        val listDoc = alice.firestore.current.document("households/$householdId/groceryLists/$party").get(Source.SERVER).await()
        val bobLists = bob.groceryRepository.observeLists(householdId).settled(2_000)?.map { it.name }
        log("P09 list exists=${listDoc.exists()} orphan items=${orphans.map { it.id + ":" + it.getString("name") }} bob lists=$bobLists (bob had ${bobList.name})")
        assertTrue("no item left under a deleted list", orphans.isEmpty())
    }

    // ---------------------------------------------------------------------------------------------------------------
    // P10: latency to the other phone.

    @Test
    fun p10_latencyToTheOtherPhone() = runBlocking {
        alice.pantry.add(householdId, alice.actor, draft("Apples", 1.0), alice.pantryData())
        val batch = alice.pantryData { it.batches.size == 1 }.batches.single()
        val seen = MutableStateFlow(0.0)
        val watcher = launch(Dispatchers.Default) {
            bob.pantryRepository.observe(householdId).collect { d ->
                d.batches.firstOrNull { it.id == batch.id }?.let { seen.value = it.quantity }
            }
        }
        withTimeout(10_000) { seen.first { it >= 1.0 } }
        val stock = mutableListOf<Long>()
        repeat(SAMPLES) { i ->
            val target = 2.0 + i
            val start = SystemClock.elapsedRealtimeNanos()
            alice.pantry.adjust(householdId, alice.actor, batch, 1.0)
            withTimeout(10_000) { seen.first { it >= target } }
            stock += (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000
            delay(300)
        }
        watcher.cancel()

        bob.grocery.quickAdd(householdId, bob.actor, listId, "Pears", emptyList(), noPantry)
        val item = alice.items { it.size == 1 }.single()
        val checked = MutableStateFlow<Boolean?>(null)
        val itemWatcher = launch(Dispatchers.Default) {
            bob.groceryRepository.observeItems(householdId, listId).collect { l ->
                l.firstOrNull { it.id == item.id }?.let { checked.value = it.checked }
            }
        }
        withTimeout(10_000) { checked.first { it == false } }
        val grocery = mutableListOf<Long>()
        repeat(SAMPLES) { i ->
            val target = i % 2 == 0
            val start = SystemClock.elapsedRealtimeNanos()
            alice.grocery.setChecked(householdId, alice.actor, listId, item, target)
            withTimeout(10_000) { checked.first { it == target } }
            grocery += (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000
            delay(300)
        }
        itemWatcher.cancel()
        fun summary(xs: List<Long>): String {
            val s = xs.sorted()
            return "n=${s.size} median=${s[s.size / 2]} p95=${s[(s.size * 95 / 100).coerceAtMost(s.size - 1)]} " +
                "min=${s.first()} max=${s.last()} all=$s"
        }
        log("P10 stock +1 to the other phone (ms): ${summary(stock)}")
        log("P10 check-off to the other phone (ms): ${summary(grocery)}")
        Unit
    }

    // ---------------------------------------------------------------------------------------------------------------
    // P11: large writes.

    @Test
    fun p11_largeAddToPantryAndClearHouseholdData() = runBlocking {
        val count = 180
        val items = (1..count).map { checkedItem(bob.writer.newId(), "Item %03d".format(it), 1.0) }
        bob.writer.apply(householdId, bob.actor, items.map { GroceryChange.CreateItem(listId, it) })
        bob.flush()
        val shown = alice.items { it.size == count }
        val start = SystemClock.elapsedRealtime()
        alice.pressAddToPantry(shown)
        val applied = SystemClock.elapsedRealtime() - start
        alice.flush()
        val confirmed = SystemClock.elapsedRealtime() - start
        val bobSees = withTimeout(60_000) { bob.pantryRepository.observe(householdId).first { it.batches.size == count } }
        val seen = SystemClock.elapsedRealtime() - start
        val (products, stock) = reportPantry("P11 after Add to pantry of $count:")
        log("P11 add: applied locally ${applied} ms, confirmed ${confirmed} ms, on Bob ${seen} ms (${bobSees.batches.size})")

        val resetStart = SystemClock.elapsedRealtime()
        alice.households.resetData(householdId, Actor(alice.uid, "Alice"))
        val resetDone = SystemClock.elapsedRealtime() - resetStart
        val bobEmpty = withTimeout(60_000) { bob.pantryRepository.observe(householdId).first { it.batches.isEmpty() && it.products.isEmpty() } }
        val resetSeen = SystemClock.elapsedRealtime() - resetStart
        log("P11 clear: done ${resetDone} ms, Bob empty after ${resetSeen} ms (${bobEmpty.batches.size})")
        assertEquals(count, products)
        assertEquals(count, stock)
    }

    // ---------------------------------------------------------------------------------------------------------------
    // P12 to P15: the session state machine.

    @Test
    fun p12_removedWhileOffline_withUnsentWrites() = runBlocking {
        bob.firestore.current.disableNetwork().await()
        bob.grocery.quickAdd(householdId, bob.actor, listId, "Offline add", emptyList(), noPantry)
        alice.households.removeMember(householdId, bob.uid)
        delay(1_000)
        val start = SystemClock.elapsedRealtime()
        bob.firestore.current.enableNetwork().await()
        bob.awaitSession(30_000) { it is Session.NoHousehold }
        log("P12 bob at the gate ${SystemClock.elapsedRealtime() - start} ms after reconnecting; history=${bob.history}")
        val items = server(itemsPath()).map { it.getString("name") }
        log("P12 server items=$items")
        Unit
    }

    @Test
    fun p13_joinApprovedWhileAppClosed_thenRequestPruned() = runBlocking {
        val carolEmail = uniqueEmail("carol")
        val carol1 = TestPhone("carol1").also { extra += it }
        carol1.sessionManager.signUp("Carol", carolEmail, PASSWORD)
        carol1.awaitSession { it is Session.NoHousehold }
        val carolUid = carol1.uid
        carol1.households.requestToJoin(carolUid, "Carol", checkNotNull(carol1.households.findInviteCode(code)))
        carol1.awaitSession { (it as? Session.NoHousehold)?.join is JoinState.Waiting }
        // Carol closes the app; Alice approves; the request is pruned (7 days) before Carol opens the app again.
        carol1.close()
        extra -= carol1
        val request = withTimeout(10_000) {
            alice.households.observeJoinRequests(householdId).first { r -> r.any { it.uid == carolUid } }
        }.single { it.uid == carolUid }
        alice.households.approve(householdId, request)
        alice.flush()
        alice.firestore.current.document("households/$householdId/joinRequests/$carolUid").delete().await()
        val carol2 = TestPhone("carol2").also { extra += it }
        carol2.sessionManager.signIn(carolEmail, PASSWORD)
        val s = carol2.awaitSession(20_000) { it is Session.InHousehold || (it as? Session.NoHousehold)?.join is JoinState.Expired }
        val members = withTimeout(10_000) { alice.households.observeMembers(householdId).first { m -> m.any { it.uid == carolUid } } }
        log("P13 carol after reopening: $s; members=${members.map { it.displayName }}")
        val retry = runCatching {
            carol2.households.requestToJoin(carolUid, "Carol", checkNotNull(carol2.households.findInviteCode(code)))
        }
        log("P13 carol asks again: ${retry.exceptionOrNull() ?: "ok"}")
        assertTrue("an approved member gets in", s is Session.InHousehold)
    }

    @Test
    fun p14_transferOwnership_rolesUpdateLive() = runBlocking {
        val start = SystemClock.elapsedRealtime()
        alice.households.transferOwnership(householdId, alice.uid, bob.uid)
        bob.awaitSession { (it as? Session.InHousehold)?.role == Role.Owner }
        alice.awaitSession { (it as? Session.InHousehold)?.role == Role.Member }
        log("P14 roles updated on both phones in ${SystemClock.elapsedRealtime() - start} ms")
        Unit
    }

    @Test
    fun p15_accountDeletedOnAnotherPhone_profileStaysDeleted() = runBlocking {
        val email = uniqueEmail("dana")
        val dana1 = TestPhone("dana1").also { extra += it }
        val dana2 = TestPhone("dana2").also { extra += it }
        dana1.sessionManager.signUp("Dana", email, PASSWORD)
        dana1.awaitSession { it is Session.NoHousehold }
        dana2.sessionManager.signIn(email, PASSWORD)
        dana2.awaitSession { it is Session.NoHousehold }
        val uid = dana1.uid
        dana1.sessionManager.deleteAccount(PASSWORD)
        log("P15 deleted on dana1; dana2 history so far=${dana2.history}")
        delay(8_000)
        val profile = runCatching { dana2.firestore.current.document("users/$uid").get(Source.SERVER).await() }
        log(
            "P15 users/$uid after deletion on the other phone: exists=${profile.getOrNull()?.exists()} " +
                "error=${profile.exceptionOrNull()} data=${profile.getOrNull()?.data?.keys} history=${dana2.history}",
        )
        assertTrue("profile stays deleted", profile.getOrNull()?.exists() != true)
    }

    // ---------------------------------------------------------------------------------------------------------------
    // P16: P01 with the real trigger: the restock engine writes to the default list while Groceries is closed.

    @Test
    fun p16_restockWriteWhileNotListening_skipsOtherPhonesItems() = runBlocking {
        alice.startRestock()
        bob.startRestock()
        alice.pantry.add(
            householdId, alice.actor,
            StockDraft(
                "Milk", 3.0, QuantityUnit.Pieces, today.plusDays(5), DateType.UseBy, Location.Fridge, Category.Dairy,
                note = null, minQuantity = 2.0, autoAddToGrocery = true, buyQuantity = null,
            ),
            alice.pantryData(),
        )
        bob.grocery.quickAdd(householdId, bob.actor, listId, "Bread", emptyList(), noPantry)
        bob.flush()
        alice.items { it.size == 1 }
        withTimeout(20_000) {
            alice.groceryRepository.observeItems(householdId, listId)
                .first { alice.syncLog.lastFullSync(itemsPath()) != null }
        }
        // Alice leaves Groceries (Pantry tab or app in the background); Bob adds Eggs.
        bob.grocery.quickAdd(householdId, bob.actor, listId, "Eggs", emptyList(), noPantry)
        bob.flush()
        delay(WAIT_PAST_MARGIN_MS)
        // Alice, on the Pantry tab, uses 2 milk: below Keep at least, so her engine puts Milk on the list.
        val milk = alice.pantryData { it.batches.size == 1 }.batches.single()
        alice.pantry.adjust(householdId, alice.actor, milk, -2.0)
        // Read the server through Bob's phone: a server read on Alice's would fill her cache.
        withTimeout(20_000) {
            while (server(itemsPath(), via = bob).none { it.id.startsWith("low_") }) delay(200)
        }
        delay(3_000)
        alice.flush()
        val shown = alice.groceryRepository.observeItems(householdId, listId).settled(5_000)?.map { it.name }?.sorted()
        val truth = server(itemsPath(), via = bob).mapNotNull { it.getString("name") }.sorted()
        val low = server(itemsPath(), via = bob).single { it.id.startsWith("low_") }
        log("P16 server=$truth alice=$shown low item createdBy=${low.getString("createdBy")} updatedBy=${low.getString("updatedBy")} alice=${alice.uid} bob=${bob.uid}")
        assertEquals("Alice's list after her engine added Milk while away", truth, shown)
    }

    // P17: a phone away longer than tombstones live (full read after 20 h) drops a delete it never saw.

    @Test
    fun p17_awayLongerThanTombstonesLive_fullReadDropsTheDeletedItem() = runBlocking {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("probe_sync_bob", android.content.Context.MODE_PRIVATE)
        try {
            alice.pantry.add(householdId, alice.actor, draft("Milk", 1.0), alice.pantryData())
            alice.pantry.add(householdId, alice.actor, draft("Bread", 1.0, days = 3), alice.pantryData { it.batches.size == 1 })
            val now = com.parcha.spoilnot.core.firebase.SyncLog(prefs, java.time.Clock.systemUTC())
            val first = withTimeout(20_000) {
                com.parcha.spoilnot.feature.pantry.data.FirestorePantryRepository(bob.firestore, now)
                    .observe(householdId).first { !it.fromCache && it.batches.size == 2 }
            }
            log("P17 bob before: ${first.batches.map { it.productName }}")
            // Bob is away. Alice throws Milk away; two days later the job deletes the tombstone for real.
            val milk = alice.pantryData { it.batches.size == 2 }.batches.single { it.productName == "Milk" }
            alice.pantry.throwAway(householdId, alice.actor, milk, com.parcha.spoilnot.feature.pantry.domain.DiscardReason.Spoiled)
            alice.flush()
            alice.firestore.current.document("households/$householdId/stock/${milk.id}").delete().await()
            // Bob comes back 21 hours after his last full read.
            val later = com.parcha.spoilnot.core.firebase.SyncLog(
                prefs,
                java.time.Clock.offset(java.time.Clock.systemUTC(), java.time.Duration.ofHours(21)),
            )
            val back = com.parcha.spoilnot.feature.pantry.data.FirestorePantryRepository(bob.firestore, later)
                .observe(householdId).settled(6_000)
            log("P17 bob after 21 h: ${back?.batches?.map { it.productName }}")
            assertEquals(listOf("Bread"), back?.batches?.map { it.productName })
        } finally {
            context.deleteSharedPreferences("probe_sync_bob")
        }
    }

    // P18: LOGIC-02, a new phone's first sync with staples in stock.

    @Test
    fun p18_firstSyncOnANewPhone_addsNoStaples() = runBlocking {
        val staples = listOf("Milk", "Eggs", "Bread", "Butter", "Rice", "Pasta", "Flour", "Sugar")
        var data = alice.pantryData()
        for (name in staples) {
            alice.pantry.add(
                householdId, alice.actor,
                StockDraft(
                    name, 5.0, QuantityUnit.Pieces, today.plusDays(30), DateType.BestBefore, Location.Pantry,
                    Category.Other, note = null, minQuantity = 1.0, autoAddToGrocery = true, buyQuantity = null,
                ),
                data,
            )
            data = alice.pantryData { d -> d.products.any { it.name == name } }
        }
        alice.flush()
        val results = mutableListOf<String>()
        repeat(3) { trial ->
            val phone = TestPhone("new$trial").also { extra += it }
            phone.sessionManager.signUp("New $trial", uniqueEmail("new$trial"), PASSWORD)
            phone.awaitSession { it is Session.NoHousehold }
            phone.startRestock()
            join(phone, "New $trial")
            delay(6_000)
            val low = server(itemsPath()).filter { it.id.startsWith("low_") }
            results += "trial $trial: ${low.size} staples added (${low.mapNotNull { it.getString("name") }})"
            // Put things back for the next trial: remove the items, re-arm the products.
            if (low.isNotEmpty()) {
                alice.writer.apply(
                    householdId, alice.actor,
                    low.map { GroceryChange.DeleteItem(listId, it.id) } +
                        alice.pantryData().products.map {
                            com.parcha.spoilnot.feature.pantry.domain.PantryChange.SetRestockArmed(it.id, true)
                        },
                )
                alice.flush()
            }
        }
        log("P18 ${results.joinToString("; ")}")
        assertTrue("no staple added by a first sync: $results", results.all { it.contains(": 0 staples") })
    }

    private companion object {
        const val TAG = "SyncProbe"
        const val SAMPLES = 30

        /** DeltaSync.CURSOR_MARGIN is 5 minutes. */
        const val WAIT_PAST_MARGIN_MS = 5L * 60_000 + 20_000
    }
}
```
