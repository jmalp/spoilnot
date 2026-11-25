# 03 Bugs in business logic

Reviewed `0334b6a` on 2026-11-01 (the branch `review/2026-11` at `8004603`, which adds only review reports).
Platforms: Android / backend (and the web where it shares a bug).

**Method.** Every `feature/*/domain` package was read next to its tests, then `backend/reminder/src/digest.ts`,
`time.ts` and `job.ts`. Boundary and property-style probes were written as tests: 26 Kotlin unit tests (in
`app/src/test` while they ran, then deleted; Appendix A), 10 Node tests against the reminder job's pure functions
(scratchpad; Appendix B) and one script against a private Firestore emulator with the real rules (ports 8181/4410,
no UI, so it couldn't clash with another session; Appendix C). Each probe asserts what DESIGN says should happen, so
a failing probe is a candidate finding; each finding below says which probe shows it. The probe file was removed
after the run; nothing else in the source tree was touched.

## Summary

The pure logic is in good shape where it matters most: expiry statuses are right at every midnight from UTC-12 to
UTC+14, on DST days, Feb 29 and Dec 31; stored dates stay ASCII under Arabic and Persian locales; Insights counts
and their Undo land in the right month; the digest's timing is right across DST and odd time zones. One bug matters
in normal use: **a staple that the restock engine adds to the list again comes back already checked, in the cart**,
because the tombstone left by Add to pantry keeps `checked: true` (LOGIC-01, P1, reproduced on the emulator; the web
has the same code). Three P2s are around Add to pantry and restocking: a first sync on a new phone can put every
staple on the list (LOGIC-02, likely), Add to pantry ignores the item's product link, so a renamed product gets a
duplicate (LOGIC-03), and it stores "2 kg" as "2 g" when the product is kept in grams (LOGIC-04). The rest are P3
edge cases in parsing, matching, scaling and wording, such as the digest title "1 items need attention" (LOGIC-06).

## Metrics

| Metric | Value | How measured | Target or budget |
| --- | --- | --- | --- |
| Domain functions with no direct tests | **37 of 200** non-private functions in `feature/*/domain` are never named in a unit test; **24** without the 10 enum `fromWire` lookups and 3 local `walk` helpers. Most are reached through other tests (`planAdd` through `add`, `JsonLd.repair` through the fixtures). Never named: `PantryService.planAdd`, `editItem`; `GroceryService.createList`, `renameList`, `setAisleOrder`; `RestockEngine.totalOf`; `MealPlanService.validTitle`, `validServings`, `validCooks`, `validNote`; `RecipeService.setFavorite`, `cookedEvent`; `JsonLd.repair`, `recipeNodes`, `isRecipe`, `types`; `RecipeExtractor.ogTitle`; `RecipeText.blockText`; `RecipeLines.stepsToText`; `ProductLink.Index.linkWords`; `SourceUrl.isWebUrl`, `siteName`; `QuantityUnit.menu`; `DeviceSync.onNewToken` | A script over the declarations and `app/src/test` (name search; private and overriding functions and interface members left out) | — |
| Reminder job functions with no direct tests | **0** of the exported functions in `digest.ts` and `time.ts` | Reading `test/digest.test.ts`, `test/time.test.ts` | — |
| Probe tests written | **37**: 26 Kotlin unit, 10 Node, 1 emulator script | Appendices A to C | — |
| Probe tests that failed | **23**: Kotlin 18, Node 4, emulator script 1 (reproduced LOGIC-01). 22 lead to the 17 findings below; 1 (an offset time zone ID, Node T5) is under Ideas | Gradle JUnit XML, `node --test` | — |
| Probe checks that held | Kotlin 8 (statuses in 12 cases at local midnight in UTC-12 and UTC+14, on DST days, Feb 29 and Dec 31; shelf life across DST and Feb 29; ASCII dates and month keys under `ar-EG` and `fa-IR`; week start by region and the `fw` extension on the JVM; Insights' local month at a month edge; fractional cooking takes; restock thresholds with decimals; 2,000 random store-order updates keep every aisle once). Node 6 (2 AM digest on the spring-forward night, the repeated fall-back hour sends once, UTC+14 and UTC-12 New Year dates, +5:30 and +5:45 zones, meal lines and plurals, "once" mode after a missed day) | Appendices A and B | — |
| Live import success rate | **Not run**: Pixel_8 was busy with another session's instrumented tests (see Not covered). Last recorded: 10 of 10 (2026-10-17, `docs/import-test-sites.md`) | — | DESIGN 1: 9 of 10 |
| `Clock` bypasses | **Android: 2 places, 3 call sites.** `QuotaSignal.report()` and `isExhausted()` default to `Clock.systemUTC()` and both callers use the default (`core/firebase/FirestoreFlows.kt:93`, `core/navigation/AppShell.kt:310`); Recent activity's "5 minutes ago" uses `System.currentTimeMillis()` (`feature/home/ui/HomeScreen.kt:447`). Besides these, the injected `Clock` itself fixes the time zone when it is created (LOGIC-11), and the Home and Plan ViewModels read `Locale.getDefault()` once for the week start. `MainActivity`'s splash timer (`SystemClock.uptimeMillis`) is a timer, not a date, and is left out. **Web domain: 0** (`web/src/domain/time.ts` defines `systemClock`; nothing else in `web/src/domain` calls `Date.now()`). **Reminder job: 0** (`main.ts` and `export-main.ts` create `now` and pass it in) | `grep` for `now(`, `currentTimeMillis`, `systemDefaultZone`, `systemUTC`, `new Date()`, `Date.now()` | 0 (CLAUDE.md: business logic takes an injectable Clock) |

## Findings

### LOGIC-01 · P1 · Confirmed · Android + web — A staple the restock engine adds again comes back already checked, in the cart

- **Where:** `feature/grocery/data/GroceryChangeMapper.kt:123-146` (`UpsertLowStock` merges and removes `deletedAt`,
  but never writes `checked`); `feature/grocery/data/FirestoreGroceryRepository.kt:55-61` (`isCached` treats a
  tombstone as absent); `feature/grocery/domain/GroceryService.kt:278` (Add to pantry deletes the item as a tombstone,
  which keeps every other field). The web writes the same fields: `web/src/data/grocery.ts:218-240`.
- **What happens:** the restock engine adds Milk as `low_<productId>`. In the store it's checked off, then Add to
  pantry removes it from the list by merging `deletedAt` into it, so the document keeps `checked: true`, `checkedBy`
  and `checkedAt`. The next time Milk runs low, the engine merges the same document again: it removes `deletedAt`,
  writes `createdAt` (the cached copy is a tombstone, so it counts as new) and leaves `checked` alone, as DESIGN 9.6
  says ("never write checked"). The item reappears **in the In cart section, struck through**, not in the list of
  things to buy, and Home's grocery count leaves it out. On the next trip it's easy to miss, and the Add to pantry
  sheet, which lists every checked item, would offer to add milk nobody bought. Clear checked leaves the same
  tombstone. DESIGN 9.6 was written before tombstones (9.8); "an existing checked item stays checked" was meant for
  a live item.
- **Evidence:** Appendix C, the real rules on a Firestore emulator, writing exactly the mapper's fields:

  ```
  1 restock adds the item: checked=undefined checkedBy=null deletedAt=absent source=low_stock
  2 checked off in the store: checked=true checkedBy=alice deletedAt=absent source=low_stock
  3 Add to pantry (tombstone): checked=true checkedBy=alice deletedAt=set source=low_stock
  4 restock adds it again: checked=true checkedBy=alice deletedAt=absent source=low_stock
  ```

  The reader maps `checked == true` to the cart (`FirestoreGroceryRepository.kt:82`).
- **Who it affects:** every household that uses Add to grocery list automatically, on every staple that dips again
  while its tombstone is still there: within two days of the last Add to pantry (milk, eggs, bread), and for as long
  as the reminder job's daily prune doesn't run (section 05 has a note on how often it runs).
- **Suggested fix:** when the engine creates the item or brings back a tombstone (today's `writeCreated`), also write
  `checked: false`, `checkedBy: null`, `checkedAt: null` (and `aliasOf: null`); keep leaving `checked` alone for a
  live item. Same in the web mapper. Effort S. Also needs: web port, DESIGN 9.6 (the pseudo-code's
  "createdAt/createdBy only if the doc is not in the local cache" and "never write checked"), a mapper unit test,
  and a step in `RestockFlowTest` (drop, check, Add to pantry, drop again: unchecked). No rules change. Older builds
  keep the bug until they update.

### LOGIC-02 · P2 · Likely · Android + web — A phone's first sync can put every staple on the grocery list

- **Where:** `feature/grocery/domain/RestockRunner.kt:58-95` (evaluates every snapshot, cache or not);
  `feature/pantry/data/FirestorePantryRepository.kt:36-46` (products and stock are two separate listeners combined);
  `web/src/domain/grocery/restock.ts:97-150` (the same).
- **What happens:** on a phone with an empty cache (a member's first sign-in, a reinstall, a new browser or Home
  Screen install on the iPhone, signing in again after sign-out cleared the local data), the products and the stock
  each arrive with their own first server read. If the products arrive more than 300 ms (the debounce) before the
  stock, the engine sees every staple at 0: it adds each one with Add to grocery list automatically to the default
  list as Low stock and disarms it. When the stock arrives it re-arms them, and the items stay on everyone's list with
  "Back in stock". A weak connection, which DESIGN expects in stores, makes the gap longer.
- **Evidence:** probe P16: the engine plans `UpsertLowStock` and `SetRestockArmed(false)` for a snapshot with a
  staple and no stock, marked `fromCache = true`. The runner has no check that the stock has been read once.
  *Likely*, not reproduced end to end: it depends on two listeners' timing on a device, the Pixel_8 emulator was in
  use by another session, and this section adds no instrumented tests.
- **Who it affects:** each new phone or browser joining a household with staples, once; the whole household sees the
  extra items.
- **Suggested fix:** let the runner evaluate only after both products and stock have had one server snapshot this
  session, or after the `SyncLog` has a full read of both on this phone (so offline evaluation still works from a
  warm cache). Effort S to M. Also needs: web port, DESIGN 9.6 (which says it runs on cache snapshots too), a runner
  test with a fake repository. Section 04 can time the gap on Pixel_8 with a throttled network.

### LOGIC-03 · P2 · Confirmed · Android — Add to pantry ignores the item's product link, so a renamed product gets a duplicate

- **Where:** `feature/grocery/domain/GroceryService.kt:259-275` (`planAdd` with `name = entry.item.name`; the item's
  `productId` isn't used); `feature/pantry/domain/PantryService.kt:342-376` (the Edit window renames the product and
  its batches, not grocery items that link to it). The sheet itself finds the product by `productId`
  (`feature/grocery/ui/GroceryForms.kt:126`).
- **What happens:** a grocery item linked to a product (every Low stock item, and recipe items that matched a
  product) keeps the name the product had when the item was added. After someone renames "Milk" to "Whole milk",
  Add to pantry looks up "Milk" by name, finds nothing and creates a second product "Milk" with the new stock. The
  renamed staple stays at 0 (an Out row, Low stock on Home), and since its Low stock item was just removed, the
  engine won't add it again until it recovers, which it can't, because new milk goes to the duplicate.
- **Evidence:** probe P14: a `low_p1` item named "Milk" for product `p1` now named "Whole milk" →
  `CreateProduct(Milk)` and the batch goes to the new product.
- **Who it affects:** anyone who renames a product while it's on a list; it shows up as two products and a staple
  that never fills.
- **Suggested fix:** give `planAdd` the item's `productId`, and use that product when it still exists, then fall
  back to the name. Optionally rename unchecked linked items with the product. Effort S. Also needs: web port, a
  `GroceryServiceTest` case, DESIGN 5.3 ("adds to the product the item is linked to").

### LOGIC-04 · P2 · Confirmed · Android — Add to pantry offers any unit, then stores the number in the product's unit

- **Where:** `feature/grocery/ui/GroceryForms.kt:131` (the row starts from the item's unit) and `:274` (any unit can
  be picked); `feature/pantry/domain/PantryService.kt:70` (`planAdd` keeps the product's unit when it has stock).
- **What happens:** quick-add "2 kg rice" links the household's Rice, kept in grams, and keeps "kg" on the item. The
  Add to pantry sheet shows "2 kg" and lets the user pick any unit. Saving adds **2 g**: the number is kept and the
  unit silently becomes the product's (there is no conversion, DESIGN 2). Add item and the Edit window lock the unit
  for a product with stock and say so (`PantryForms.kt:206`, `:352-353`); Add to pantry doesn't.
- **Evidence:** probe P13: entry 2 kg, product in grams with 500 g in stock → `CreateBatch(quantity = 2.0, unit =
  Grams)`.
- **Who it affects:** anyone whose quick-add or recipe item uses another unit than the product; the pantry then shows
  a wrong amount and the restock engine works from it.
- **Suggested fix:** in the sheet, lock the unit to the product's when it has stock, with the same "unit locked"
  helper, and start the amount from the item only when the units match (else empty, or the product's usual amount).
  Effort S. Also needs: web port, a `GroceryServiceTest` case, the Roborazzi image of the sheet.

### LOGIC-05 · P3 · Confirmed · Android — Editing an older staple without automatic add removes its Keep at least

- **Where:** `feature/pantry/ui/PantryForms.kt:301` (the Edit window sends `minQuantity = form.min.takeIf {
  form.autoAdd }`) and `feature/pantry/domain/PantryService.kt:350`.
- **What happens:** since the beta, Keep at least exists only with Add to grocery list automatically on. Products
  made in v1 could have Keep at least without it (DESIGN 5.2's Add item table still says so); they show as staples
  with an Out row. Saving any change in the Edit window for such a product (a new date, a location) clears its Keep
  at least without a word, so it stops being a staple.
- **Evidence:** probe P26: a product with Keep at least 2 and automatic add off, edited with the switch off →
  `UpdateProduct(minQuantity = null)`.
- **Who it affects:** the beta testers' products from before the change; once each.
- **Suggested fix:** keep the product's `minQuantity` when the form never showed it (switch off and the product had
  auto-add off), or migrate such products once (turn auto-add on, or clear the minimum) and say so in DECISIONS.
  Effort S. Also needs: DESIGN 5.2 (the Add item table), a `PantryServiceTest` case. For section 11:
  `PantryService.updateProduct` and `ProductEdit` have no caller since the Edit window replaced product detail.

### LOGIC-06 · P3 · Confirmed · Backend — "1 items need attention" when one product has two batches in different stages

- **Where:** `backend/reminder/src/digest.ts:143-144`.
- **What happens:** the title uses one sentence only when there is exactly one entry after de-duplicating by stage,
  name and date type; otherwise it says "N items" with N = distinct names. One product with a batch expiring today
  and one tomorrow, or an expired use-by and an expired best-before batch, gives two entries and one name: "1 items
  need attention". The existing test covers two batches in the same stage only.
- **Evidence:** probes D1 and D2: `actual: '1 items need attention'`.
- **Who it affects:** anyone with two purchases of one product close together (common for milk or yogurt).
- **Suggested fix:** decide the title by distinct names: one name gives the single-item sentence for its most urgent
  stage, several give "N items". Effort S. Also needs: a digest test; DESIGN 10.3's wording if it changes.

### LOGIC-07 · P3 · Confirmed · Backend · Question — "Every day until it is removed" stops after 14 days

- **Where:** `backend/reminder/src/digest.ts:75-78` (`stockWindow`: 14 days back) with
  `app/src/main/res/values/strings.xml:373` (`expired_daily`).
- **What happens:** with Expired reminder set to every day, an item that expired 15 or more days ago is no longer
  loaded, so it drops out of the digest while it's still in the pantry. DESIGN 10.1 sets the 14-day window, so the
  job follows DESIGN; the setting's wording promises more.
- **Evidence:** probe E1: an item expired 20 days ago, daily mode → not in the stock query, 0 items.
- **Who it affects:** people who chose the daily mode and leave something expired in the pantry for over two weeks.
- **Suggested fix:** the owner's choice: reword ("Every day for two weeks") or, for daily-mode users only, query
  expired stock without a lower bound (a few more reads per run). Effort S. Also needs: DESIGN 5.4, the web string
  (generated).

### LOGIC-08 · P3 · Confirmed · Android — Quick add: Increase quantity adds across units, and some amounts are misread

- **Where:** `feature/grocery/domain/GroceryService.kt:140-155` (`increase`) and `:26-39` (`QuickAddParser`).
- **What happens:** with "Rice 500 g" on the list, typing "2 kg rice" asks "Already on the list" and Increase
  quantity makes it **502 g**. The parser also reads "3 lbs apples" as an item named "lbs apples" (only "lb " is a
  unit), "2l milk" as a name (a unit glued to the number isn't read), and "0 eggs" as an amount of 0.
- **Evidence:** probes P11 (`AddToItem(delta = 2.0)` on the gram item) and P12.
- **Who it affects:** people who type amounts in quick add; the result is visible and fixable in Edit.
- **Suggested fix:** in `increase`, add only when the units match or one side has none; otherwise use Add anyway (or
  ask). Accept the `IngredientParser` unit words (lbs, glued units) in the quick-add parser, and ignore a zero
  amount. Effort S. Also needs: web port, tests.

### LOGIC-09 · P3 · Confirmed · Android · Question — Undo of Used up leaves the staple on the grocery list

- **Where:** `feature/pantry/domain/PantryService.kt:135-139` and the restock engine (`RestockEngine.kt:18-42`).
- **What happens:** Used up on the last Milk; 300 ms later the engine adds Milk to the list. Undo puts the batch
  back, and the engine only re-arms, so Milk stays on the list with "Back in stock". DESIGN 5.3 describes Back in
  stock for stock that recovers, so this is by design, but from the person's side Undo didn't put things back as they
  were (the plan's Undo check: list membership).
- **Evidence:** probe P15: after Undo, the plan is `[SetRestockArmed(milk, true)]`, with no removal.
- **Who it affects:** anyone who undoes a Used up or Throw away of a staple's last batch; one tap removes the item.
- **Suggested fix:** the owner's choice. If Undo should be exact: when the engine re-arms a product whose `low_` item
  is still unchecked and was created after the batch was finished (or carries a "from this dip" marker), remove it.
  Or accept and record it in DECISIONS. Effort S to M.

### LOGIC-10 · P3 · Confirmed · Android · Question — Add to pantry, Skip and Delete list have no Undo and no recorded exception

- **Where:** `feature/grocery/ui/GroceriesViewModel.kt:232-242`; `feature/grocery/domain/GroceryService.kt:235-283`
  (`deleteList` and `addToPantry` return no `Undo`).
- **What happens:** Add to pantry removes the shopped items (and the skipped ones) from the list; Delete list removes
  a list and its items outright after a confirmation. Neither offers Undo. CLAUDE.md says every destructive action
  has Undo; DECISIONS records exceptions for household actions and Clear household data, not these.
- **Evidence:** the code above; `grep -n -i undo docs/DECISIONS.md`.
- **Who it affects:** someone who taps Add all by mistake or skips the wrong items; someone who deletes the wrong list
  (the confirmation names it).
- **Suggested fix:** Add to pantry returns an Undo that deletes the new batches (and products it created) and
  restores the items; Delete list keeps its confirmation and gets a DECISIONS entry (items are deleted outright, so
  an Undo would have to rewrite them). Effort M for Add to pantry, S for the entry.

### LOGIC-11 · P3 · Confirmed · Android — The injected Clock keeps the time zone it was created with

- **Where:** `core/ClockModule.kt:13-14` (`Clock.systemDefaultZone()`, unscoped) and `todayFlow`
  (`feature/pantry/ui/PantryViewModel.kt:60-65`).
- **What happens:** `Clock.systemDefaultZone()` copies the zone when it is created. Each ViewModel gets its own copy,
  and a time zone change isn't a configuration change, so the Pantry, Home, Plan, Groceries and Insights screens keep
  working out "today" in the old zone until their ViewModel is recreated. After a flight across many zones, statuses
  ("Tomorrow", "Today"), the Plan's today and the month for Insights counts can be a day off until the app restarts.
- **Evidence:** probe P03: a clock made in `Pacific/Kiritimati`, then the default zone set to `Etc/GMT+12` →
  `LocalDate.now(clock)` = 2026-11-02, `LocalDate.now()` = 2026-11-01.
- **Who it affects:** people who travel across time zones with the app in memory.
- **Suggested fix:** provide a clock that reads the zone on every call (a small `Clock` subclass delegating to
  `Clock.system(ZoneId.systemDefault())`), still injectable for tests. Effort S. Also needs: a unit test.

### LOGIC-12 · P3 · Confirmed (JVM) · Likely (phone) · Android — With Arabic or Persian digits, a shown quantity can't be edited

- **Where:** `feature/pantry/ui/PantryLabels.kt:230` (`DecimalFormat("0.##")` in the default locale) and
  `feature/pantry/ui/PantryForms.kt:99` (`parseQuantity` accepts ASCII digits only); the forms pre-fill with
  `formatQuantity` (`PantryForms.kt:267`, `:491`, `:503`; `GroceryForms.kt:130`).
- **What happens:** on a phone whose language uses other digits, 1.5 is shown and pre-filled as "١٫٥" (`ar-EG`) or
  "۱٫۵" (`fa-IR`). Reading it back gives nothing, so the Edit window's amount is invalid until retyped with Latin
  digits. The stepper breaks after one press in any form: + turns "1" into "٢", the next + can't read it and starts
  again from 1, and − does nothing. Stored values are safe: dates, month keys and day keys stay ASCII (probe P04).
- **Evidence:** probe P05 on the JVM: `ar-EG shows '١٫٥'` → `null`. Android formats with ICU, which also uses
  native digits for these locales; confirm on a phone (phone check 4).
- **Who it affects:** people with the phone in Arabic, Persian or another language with its own digits (the app's
  text stays English).
- **Suggested fix:** format amounts for fields with `Locale.ROOT` (or parse with the same `DecimalFormat`), and keep
  locale digits for display only if wanted. Effort S. Also needs: a unit test under a non-Latin locale.

### LOGIC-13 · P3 · Confirmed · Android — Servings scaling: large recipes shrink, cooking takes half eggs, small amounts read 0

- **Where:** `feature/plan/domain/MealPlanModels.kt:76` (`MAX_SERVINGS = 100`) with
  `feature/recipes/domain/RecipeService.kt:293` (recipes keep 1 to 1,000); `feature/recipes/domain/Cooking.kt:54`;
  `feature/recipes/domain/IngredientParser.kt:120-134` (`Scaling.formatQuantity`).
- **What happens:**
  - A recipe with more than 100 servings ("Makes 120 meatballs") is planned at 100, so the week's grocery list uses
    0.83 of the recipe.
  - I cooked this at 1.5 times the servings suggests taking 4.5 eggs from the pantry; the grocery list rounds counts
    up ("4½ eggs buys 5", DESIGN 5.9), so the pantry is left with half an egg.
  - A small scaled amount reads "0": "1/8 tsp salt" at a tenth becomes "0 tsp salt".
- **Evidence:** probes P25 (factor 0.8333), P09 (suggested 4.5), P10 ("0 tsp salt").
- **Who it affects:** large-batch recipes in the planner; scaled cooking with pieces; heavily scaled-down recipes.
- **Suggested fix:** let a meal's servings go to the recipe's maximum (or plan at the recipe's servings when it's
  above 100); round suggested amounts up for products kept in pieces; show amounts under 1/16 as a fraction or "a
  pinch" instead of 0. Effort S each. Also needs: web port, tests.

### LOGIC-14 · P3 · Confirmed · Android — Copy last week drops a second meal with the same name in one slot

- **Where:** `feature/plan/domain/MealWeek.kt:66-74`.
- **What happens:** the duplicate check also runs within last week's meals, so two "Leftovers" in Monday's lunch
  become one. DESIGN 5.9 skips meals "already in that slot" of the target week; it doesn't ask to merge last week's.
- **Evidence:** probe P24: two copies expected, one made.
- **Who it affects:** households that plan the same meal twice in one slot (for two people); rare.
- **Suggested fix:** check against the target week's meals only, and count same-named meals (copy as many as last
  week had minus those already there). Effort S. Also needs: web port, a `MealWeekTest` case.

### LOGIC-15 · P3 · Confirmed · Android — Recipe import misreads comma decimals, "1h20m", "1 1/2 hours" and one-string ingredient lists

- **Where:** `feature/recipes/domain/IngredientParser.kt:22-24`; `feature/recipes/domain/RecipeText.kt:70-86`
  (`Durations`); `feature/recipes/domain/JsonLd.kt:125`.
- **What happens:**
  - "1,5 kg Mehl" (a comma decimal, common outside English) and "1,000 g flour" parse as 1 with no unit or name.
  - "1h20m" is 20 minutes (the `\b` after `h` fails before a digit), and "1 1/2 hours" is 120 minutes (it finds
    "2 hours").
  - A `recipeIngredient` sent as one string with line breaks (some sites do) becomes one ingredient.
  Raw text is always kept, so nothing is lost, but scaling, the shopping name and the times are wrong.
- **Evidence:** probes P21 (`1h20m -> 20`, `1 1/2 hours -> 120`), P22 (`1,5 kg Mehl -> 1.0 null null`, `1,000 g flour
  -> 1.0 null null`), P23 (one ingredient instead of two). The ISO forms (`PT1H30M`, `P0DT1H30M`, `PT0.5H`, `P1DT2H`),
  `@graph`, type lists, entities, trailing commas and `HowToSection` groups all passed.
- **Who it affects:** recipes from non-English sites and the few sites with these shapes; `LiveImportTest`'s ten
  sites import (last run).
- **Suggested fix:** accept a comma decimal when one or two digits follow, and a thousands separator when three do;
  allow `h` followed by a digit; parse mixed numbers in durations; split a string `recipeIngredient` on line breaks.
  Effort S. Also needs: web port, parser tests.

### LOGIC-16 · P3 · Confirmed · Android — Ingredient linking misses "-ie" plurals, "leaves" and "extra-virgin"

- **Where:** `feature/recipes/domain/RecipeBox.kt:75-90` (`PantryMatch.singular`) and
  `feature/recipes/domain/RecipeShopping.kt:104` (at most one extra word).
- **What happens:** "-ies" always becomes "-y", so "cookies" is "cooky" and never meets "cookie"; the same for
  "brownies" and "chilies". "Leaves" isn't "leaf". "Extra-virgin olive oil" splits into four words, two more than
  "Olive oil", so it doesn't link. These lines then get a new grocery item instead of the household's product, and
  Use it up and I cooked this miss them.
- **Evidence:** probe P18: no link for cookie/Cookies, chilies/Chili, brownies/Brownie, extra-virgin olive
  oil/Olive oil, bay leaves/Bay leaf ("pie crust"/"Pie crusts" links).
- **Who it affects:** households with those products; a Change on the line fixes it for good (aliases).
- **Suggested fix:** compare both singular forms ("-ies" → "-y" and "-ie"), add "-ves" → "-f", and drop
  "extra-virgin"/"extra virgin" with the other prep words in `ShoppingName`. Effort S. Also needs: web port (section
  10 can diff it), tests.

### LOGIC-17 · P3 · Confirmed · Android — Aisle guesses pick the longer word, not the more specific one

- **Where:** `feature/grocery/domain/AisleGuess.kt:61` (single words ranked by length) and `:71`.
- **What happens:** between two one-word matches, the longer word wins: "chicken broth" goes to Meat and fish
  ("chicken" is longer than "broth"), "frozen spinach" and "frozen chicken" are not Frozen, and "canned tomatoes",
  "tomato paste" and "salad dressing" go to Produce. Only listed phrases ("frozen peas") are right.
- **Evidence:** probe P19: six of seven common names in the wrong aisle.
- **Who it affects:** typed items without a matching product; the store order then places them in the wrong aisle
  until someone edits the category.
- **Suggested fix:** let modifier words decide: "frozen", "canned", "broth", "stock", "sauce", "paste", "dressing",
  "juice" win over the food word they come with; or rank by a priority per word rather than its length. Effort S.
  Also needs: web port, `AisleGuessTest` cases.

## Ideas

- **Normalize the time zone on write.** Intl rejects offset-style IDs such as `GMT+05:30`, and the job then uses UTC
  (Node probe T5: 13:07 instead of 18:37). Android normally reports IANA names, so this is a guard, not a bug: the app
  could write `ZoneId.systemDefault().normalized()` or an `Etc/GMT` name, and the job could log a count of unknown
  zones.
- **Keep the probes.** Appendix A's tests are written to fail on today's code and pass once fixed; most can move into
  the matching `*Test` files of the fix PRs. P01, P04, P07, P20 and the Node T1 to T4 pass today and are cheap
  boundary guards worth keeping.
- **A sequence test for restocking.** A property-style test that runs random sequences (use, add, check, Add to
  pantry, delete, Undo) through the services, the engine and an in-memory model of the mapper's merge semantics
  would have caught LOGIC-01, and would guard DESIGN 9.6's guarantees as they change.
- **Insights streak window.** The streak only looks at the six months on screen, and counting starts on the 1st of
  the first month with counts. A household whose last waste was seven months ago sees "Nothing thrown away in N days
  of counting" with N counted from inside the window. Harmless; a stored "last thrown away" date would be exact.

## For other sections

- **04 Sync:** time LOGIC-02's gap between the products' and the stock's first server snapshot on Pixel_8 with a
  slow network. `HouseholdWriter.apply` chunks at 400 *changes* and commits each chunk separately (DECISIONS
  2026-10-17 accepts it for Add to pantry), so a large plan can half-apply and its Undo then restores more than was
  written. `PantryService.adjust` decides "−1 uses it up" from the phone's copy: if another phone just raised the
  batch, −1 deletes the whole batch. `deleteEmptyBatches` deletes batches that reached 0 through two phones' −1
  without counting them for Insights. `RestockRunner`'s 30-second repeat guard skips a plan equal to the last one, so
  drop, delete, recover and drop again within 30 s waits for the next snapshot.
- **05 Reliability:** Undo restores the batch as it was before the action, so an edit another member made in the 8
  seconds in between is lost (accepted by the design, worth a line in DECISIONS).
- **06 UX:** the daily expired mode's wording (LOGIC-07); Back in stock after Undo (LOGIC-09); what the Already on
  the list prompt says when units differ (LOGIC-08).
- **10 Web:** LOGIC-01 and LOGIC-02 are in the web's code too (cited above); diff the ports of the other findings,
  and compare the week start (`WeekFields` on Android against the web's choice) on the same region settings.
- **11 Code:** `PantryService.updateProduct` and `ProductEdit` have no caller; Home uses the older per-product
  `PantryList` rows while Pantry uses `PantryItems`, two list models for one pantry; DECISIONS 2026-10-19 (linking)
  mentions a uniqueness rule the code doesn't have (it takes the most shared words, then A to Z, as DESIGN 5.5 says);
  37 domain functions are never named in a test (Metrics).

## Not covered

- **`LiveImportTest` and the live import rate.** Pixel_8 and the Firebase emulator ports were free at this
  section's first check, but by the time the unit probes were done the session fixing sign-out had started Pixel_8
  and the Firebase emulators (8080, 9099) and was running instrumented tests on it (`com.parcha.spoilnot.dev.test` in
  `adb shell ps -A` at 20:51 and 20:54, again from 20:57; the emulator still up at 21:05). Running
  `connectedDevDebugAndroidTest` from this checkout would have reinstalled the app under its tests, so the rate isn't
  measured. Run it when Pixel_8 is free with the command in `docs/import-test-sites.md`; the last recorded result is
  10 of 10 (2026-10-17).
- **End-to-end reproduction of LOGIC-02** on a device, for the same reason and because this section adds no
  instrumented tests.
- **Android's own number and week formatting.** P05 and P06 ran on the JVM; Android uses ICU, which may differ for
  native digits and for the "first day of week" regional preference (Android 14 and later). Phone checks 3 and 4.
- **The web domain.** Only read where a finding needed it; section 10 runs the differential tests.
- **Backup and restore logic** (`backup.ts`): section 05's drill.
- **Rules and security.** Nothing in this section had a security angle; nothing was added to the private notes.

## Phone checks for the owner

Note the phone model and OS version once in the Result column.

| # | Phone | Steps | Expected | Result |
| --- | --- | --- | --- | --- |
| 1 | Android | Make a test product with Keep at least 1 and Add to grocery list automatically on, with 1 in stock. Use it up (it appears on Groceries). Check it off, then Add to pantry. Use it up again the same day. | It reappears in the list of things to buy, unchecked. (LOGIC-01 says it will show in the cart, checked.) | |
| 2 | iPhone | The same steps in the web app. | The same; LOGIC-01 predicts the same bug. | |
| 3 | Android | On a phone set to English (US): Settings > System > Languages > Regional preferences > First day of week > Monday. Open Spoil Not > Plan (swipe it away from Recents first). | The week starts on Monday. If it still starts on Sunday, the app ignores the preference. | |
| 4 | Android | Optional: set the phone's language to Arabic (or Persian), open Spoil Not, Edit an item with 1.5 or 2 in stock, tap + and then Save. Set the language back. | + changes the amount and Save works. LOGIC-12 predicts the amount shows in Arabic digits and isn't accepted. | |
| 5 | Android | With Spoil Not open on Pantry, go to Settings > System > Date & time, turn off the automatic time zone and pick one about 12 hours away, then return to Spoil Not without closing it. Note an item's chip, then swipe the app away, reopen and compare. Set the time zone back. | The chips follow the new date at once. LOGIC-11 predicts they change only after reopening. | |

---

## Appendix A: Kotlin probes (`app/src/test/kotlin/com/parcha/spoilnot/review/LogicProbeTest.kt`, run once and deleted)

Run with `./gradlew testDevDebugUnitTest --tests 'com.parcha.spoilnot.review.LogicProbeTest'`. 26 tests, 18 failed
(P03, P05, P09 to P16, P18, P19, P21 to P26); the failure messages are quoted in the findings.

```kotlin
package com.parcha.spoilnot.review

import com.parcha.spoilnot.core.ClockModule
import com.parcha.spoilnot.core.error.AppError
import com.parcha.spoilnot.core.write.Actor
import com.parcha.spoilnot.core.write.HouseholdChange
import com.parcha.spoilnot.core.write.HouseholdWriter
import com.parcha.spoilnot.feature.grocery.domain.AisleGuess
import com.parcha.spoilnot.feature.grocery.domain.GroceryChange
import com.parcha.spoilnot.feature.grocery.domain.GroceryItem
import com.parcha.spoilnot.feature.grocery.domain.GroceryList
import com.parcha.spoilnot.feature.grocery.domain.GroceryRepository
import com.parcha.spoilnot.feature.grocery.domain.GroceryService
import com.parcha.spoilnot.feature.grocery.domain.ItemSource
import com.parcha.spoilnot.feature.grocery.domain.PantryEntry
import com.parcha.spoilnot.feature.grocery.domain.QuickAdd
import com.parcha.spoilnot.feature.grocery.domain.QuickAddParser
import com.parcha.spoilnot.feature.grocery.domain.RestockEngine
import com.parcha.spoilnot.feature.grocery.domain.StoreOrder
import com.parcha.spoilnot.feature.insights.domain.StatsChange
import com.parcha.spoilnot.feature.pantry.domain.Category
import com.parcha.spoilnot.feature.pantry.domain.DateType
import com.parcha.spoilnot.feature.pantry.domain.ExpiryStatus
import com.parcha.spoilnot.feature.pantry.domain.Location
import com.parcha.spoilnot.feature.pantry.domain.NameKey
import com.parcha.spoilnot.feature.pantry.domain.PantryChange
import com.parcha.spoilnot.feature.pantry.domain.PantryData
import com.parcha.spoilnot.feature.pantry.domain.PantryService
import com.parcha.spoilnot.feature.pantry.domain.Product
import com.parcha.spoilnot.feature.pantry.domain.QuantityUnit
import com.parcha.spoilnot.feature.pantry.domain.ShelfLife
import com.parcha.spoilnot.feature.pantry.domain.StockBatch
import com.parcha.spoilnot.feature.plan.domain.MealPlanGrocery
import com.parcha.spoilnot.feature.plan.domain.MealPlanService
import com.parcha.spoilnot.feature.plan.domain.MealSlot
import com.parcha.spoilnot.feature.plan.domain.MealWeek
import com.parcha.spoilnot.feature.plan.domain.PlannedMeal
import com.parcha.spoilnot.feature.recipes.domain.Cooking
import com.parcha.spoilnot.feature.recipes.domain.Durations
import com.parcha.spoilnot.feature.recipes.domain.Ingredient
import com.parcha.spoilnot.feature.recipes.domain.IngredientParser
import com.parcha.spoilnot.feature.recipes.domain.JsonLd
import com.parcha.spoilnot.feature.recipes.domain.ProductLink
import com.parcha.spoilnot.feature.recipes.domain.Recipe
import com.parcha.spoilnot.feature.recipes.domain.RecipeContent
import com.parcha.spoilnot.feature.recipes.domain.Scaling
import java.text.DecimalFormat
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.WeekFields
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Section 03 review probes (temporary; copied into docs/reviews/03-logic.md and then deleted). */
class LogicProbeTest {
    private val actor = Actor("u1", "Sam")
    private val today: LocalDate = LocalDate.of(2026, 10, 12)
    private val utc = Clock.fixed(Instant.parse("2026-10-12T12:00:00Z"), ZoneId.of("UTC"))

    private fun product(
        id: String,
        name: String = id.replaceFirstChar { it.uppercase() },
        unit: QuantityUnit = QuantityUnit.Pieces,
        min: Double? = null,
        armed: Boolean = true,
        category: Category = Category.Dairy,
    ) = Product(
        id, name, NameKey.of(name), category, Location.Fridge, unit, min, min != null, null, armed, null,
        emptyList(), null,
    )

    private fun batch(id: String, p: Product, quantity: Double, expiry: LocalDate? = today.plusDays(5)) = StockBatch(
        id, p.id, p.name, p.unit, p.category, quantity, expiry, DateType.UseBy, Location.Fridge, null,
        Instant.parse("2026-10-01T00:00:00Z"),
    )

    private fun item(id: String, name: String, quantity: Double?, unit: QuantityUnit?, productId: String? = null) =
        GroceryItem(
            id, name, NameKey.of(name), quantity, unit, null, null, checked = true, checkedBy = null,
            source = if (id.startsWith("low_")) ItemSource.LowStock else ItemSource.Manual, productId = productId,
            recipeId = null, createdBy = "u1", createdAt = null,
        )

    // ---- Dates and time ----

    @Test
    fun `P01 status boundaries follow the local midnight from UTC-12 to UTC+14, DST days, Feb 29 and Dec 31`() {
        data class Case(val zone: String, val instant: String, val expiry: String, val expected: ExpiryStatus)
        val cases = listOf(
            // 23:59 and 00:00 local in the two extreme zones.
            Case("Etc/GMT+12", "2026-10-13T11:59:00Z", "2026-10-12", ExpiryStatus.Today),
            Case("Etc/GMT+12", "2026-10-13T12:00:00Z", "2026-10-12", ExpiryStatus.Expired),
            Case("Pacific/Kiritimati", "2026-10-12T09:59:00Z", "2026-10-12", ExpiryStatus.Today),
            Case("Pacific/Kiritimati", "2026-10-12T10:00:00Z", "2026-10-12", ExpiryStatus.Expired),
            // US DST start (2026-03-08) and end (2026-11-01): 23:30 local the evening before and after.
            Case("America/New_York", "2026-03-08T04:30:00Z", "2026-03-08", ExpiryStatus.Tomorrow),
            Case("America/New_York", "2026-03-09T03:30:00Z", "2026-03-09", ExpiryStatus.Tomorrow),
            Case("America/New_York", "2026-11-01T03:30:00Z", "2026-11-01", ExpiryStatus.Tomorrow),
            Case("America/New_York", "2026-11-02T04:30:00Z", "2026-11-02", ExpiryStatus.Tomorrow),
            // Leap day and year end.
            Case("Europe/London", "2028-02-28T12:00:00Z", "2028-03-01", ExpiryStatus.Soon),
            Case("Europe/London", "2028-02-29T23:59:00Z", "2028-03-07", ExpiryStatus.Week),
            Case("Asia/Kolkata", "2026-12-31T18:29:00Z", "2027-01-03", ExpiryStatus.Soon),
            Case("Asia/Kolkata", "2026-12-31T18:30:00Z", "2027-01-01", ExpiryStatus.Today),
        )
        for (c in cases) {
            val clock = Clock.fixed(Instant.parse(c.instant), ZoneId.of(c.zone))
            assertEquals(c.toString(), c.expected, ExpiryStatus.of(LocalDate.parse(c.expiry), LocalDate.now(clock)))
        }
    }

    @Test
    fun `P02 shelf life counts calendar days across DST and Feb 29`() {
        assertEquals(listOf(2), ShelfLife.record(emptyList(), LocalDate.of(2028, 2, 28), LocalDate.of(2028, 3, 1)))
        assertEquals(listOf(1), ShelfLife.record(emptyList(), LocalDate.of(2026, 3, 7), LocalDate.of(2026, 3, 8)))
    }

    @Test
    fun `P03 the injected clock follows a time zone change, like LocalDate now`() {
        val before = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati"))
            val clock = ClockModule.clock() // What Hilt hands a ViewModel created before the trip.
            TimeZone.setDefault(TimeZone.getTimeZone("Etc/GMT+12")) // Landed 26 hours west.
            assertEquals(LocalDate.now(), LocalDate.now(clock))
        } finally {
            TimeZone.setDefault(before)
        }
    }

    @Test
    fun `P04 stored dates and keys stay ASCII under Arabic and Persian locales`() {
        val before = Locale.getDefault()
        try {
            for (tag in listOf("ar-EG", "fa-IR")) {
                Locale.setDefault(Locale.forLanguageTag(tag))
                assertEquals("2026-10-12", LocalDate.of(2026, 10, 12).toString())
                assertEquals("2026-10", YearMonth.of(2026, 10).toString())
                assertEquals("31", 31.toString())
            }
        } finally {
            Locale.setDefault(before)
        }
    }

    @Test
    fun `P05 an edited quantity survives the form round trip under Arabic and Persian locales`() {
        val before = Locale.getDefault()
        try {
            for (tag in listOf("ar-EG", "fa-IR")) {
                Locale.setDefault(Locale.forLanguageTag(tag))
                // PantryLabels.formatQuantity uses DecimalFormat("0.##"); PantryForms.parseQuantity reads it back.
                val shown = DecimalFormat("0.##").format(1.5)
                val parsed = shown.trim().replace(',', '.').toDoubleOrNull()
                assertEquals("$tag shows '$shown'", 1.5, parsed)
            }
        } finally {
            Locale.setDefault(before)
        }
    }

    @Test
    fun `P06 week start honours the region and the first-day-of-week preference`() {
        assertEquals(DayOfWeek.SUNDAY, WeekFields.of(Locale.US).firstDayOfWeek)
        assertEquals(DayOfWeek.MONDAY, WeekFields.of(Locale.UK).firstDayOfWeek)
        assertEquals(DayOfWeek.MONDAY, WeekFields.of(Locale.forLanguageTag("en-US-u-fw-mon")).firstDayOfWeek)
        // Week math across the year end.
        assertEquals(LocalDate.of(2025, 12, 28), MealWeek.start(LocalDate.of(2026, 1, 1), DayOfWeek.SUNDAY))
        assertEquals(LocalDate.of(2025, 12, 29), MealWeek.start(LocalDate.of(2026, 1, 1), DayOfWeek.MONDAY))
    }

    @Test
    fun `P07 Insights counts land in the local month at a month edge`() {
        // 23:30 on Oct 31 in New York is Nov 1 in UTC.
        val clock = Clock.fixed(Instant.parse("2026-11-01T03:30:00Z"), ZoneId.of("America/New_York"))
        assertEquals(YearMonth.of(2026, 10), StatsChange.usedUp(LocalDate.now(clock)).month)
    }

    // ---- Quantities ----

    @Test
    fun `P08 cooking fractional takes leaves nothing behind`() = runTest {
        val rice = product("rice", unit = QuantityUnit.Kilograms)
        val writer = RecordingWriter()
        val service = PantryService(writer, utc)
        var b = batch("r1", rice, 0.3)
        repeat(3) { i ->
            service.use("h", actor, mapOf("rice" to 0.1), PantryData(listOf(rice), listOf(b), false))
            val change = writer.last.first()
            if (i < 2) {
                b = b.copy(quantity = b.quantity + (change as PantryChange.AddToBatch).delta)
            } else {
                assertEquals(PantryChange.DeleteBatch("r1"), change)
            }
        }
    }

    @Test
    fun `P09 I cooked this at 1·5 times the servings suggests whole eggs`() {
        val eggs = product("eggs")
        val recipe = Recipe("r", RecipeContent("Omelette", servings = 2, ingredients = listOf(Ingredient("3 eggs"))))
        val line = Cooking.lines(recipe, PantryData(listOf(eggs), listOf(batch("e", eggs, 12.0)), false), 1.5).single()
        assertEquals(5.0, line.suggested!!, 0.0)
    }

    @Test
    fun `P10 a scaled line never reads zero`() {
        val scaled = Scaling.line("1/8 tsp salt", 0.1)
        assertFalse(scaled, scaled.startsWith("0 "))
    }

    @Test
    fun `P11 Increase quantity on the Already on the list prompt respects units`() = runTest {
        val writer = RecordingWriter()
        val service = GroceryService(writer, NoRepository, PantryService(writer, utc))
        val existing = item("i1", "Rice", 500.0, QuantityUnit.Grams).copy(checked = false)
        service.increase("h", actor, "list", existing, QuickAdd("rice", 2.0, QuantityUnit.Kilograms))
        val change = writer.last.single()
        assertFalse("2 kg added to 500 g as $change", change == GroceryChange.AddToItem("list", "i1", 2.0))
    }

    @Test
    fun `P12 quick add reads lbs, glued units and refuses a zero amount`() {
        assertEquals(QuickAdd("apples", 3.0, QuantityUnit.Pounds), QuickAddParser.parse("3 lbs apples"))
        assertEquals(QuickAdd("milk", 2.0, QuantityUnit.Liters), QuickAddParser.parse("2l milk"))
        assertTrue(QuickAddParser.parse("0 eggs").quantity != 0.0)
    }

    @Test
    fun `P13 Add to pantry keeps the amount the sheet showed when the unit differs from the product's`() = runTest {
        val rice = product("rice", unit = QuantityUnit.Grams, category = Category.PantryStaples)
        val writer = RecordingWriter()
        val service = GroceryService(writer, NoRepository, PantryService(writer, utc))
        val shopped = item("i1", "Rice", 2.0, QuantityUnit.Kilograms, productId = "rice")
        val data = PantryData(listOf(rice), listOf(batch("r1", rice, 500.0)), false)
        service.addToPantry("h", actor, "list", listOf(PantryEntry(shopped, 2.0, QuantityUnit.Kilograms, null, Location.Pantry)), emptyList(), data)
        val created = writer.last.filterIsInstance<PantryChange.CreateBatch>().single().batch
        // No unit conversion (DESIGN 2), so the sheet must not offer kg, or the batch must not become 2 g.
        assertFalse("2 kg became ${created.quantity} ${created.unit}", created.quantity == 2.0 && created.unit == QuantityUnit.Grams)
    }

    // ---- Restock engine ----

    @Test
    fun `P14 Add to pantry adds a low-stock item to its linked product, even after a rename`() = runTest {
        val renamed = product("p1", name = "Whole milk", min = 2.0, armed = false)
        val writer = RecordingWriter()
        val service = GroceryService(writer, NoRepository, PantryService(writer, utc))
        val low = item("low_p1", "Milk", 2.0, QuantityUnit.Pieces, productId = "p1")
        service.addToPantry("h", actor, "list", listOf(PantryEntry(low, 2.0, QuantityUnit.Pieces, null, Location.Fridge)), emptyList(), PantryData(listOf(renamed), emptyList(), false))
        val creates = writer.last.filterIsInstance<PantryChange.CreateProduct>().map { it.product.name }
        assertEquals("a second product was created", emptyList<String>(), creates)
        assertEquals("p1", writer.last.filterIsInstance<PantryChange.CreateBatch>().single().batch.productId)
    }

    @Test
    fun `P15 Undo of Used up takes back the low-stock item the restock engine added`() = runTest {
        val milk = product("milk", min = 1.0)
        val m1 = batch("m1", milk, 1.0)
        val writer = RecordingWriter()
        val undo = PantryService(writer, utc).usedUp("h", actor, m1)
        // 300 ms later the engine sees no milk and adds low_milk.
        val added = RestockEngine.plan(PantryData(listOf(milk), emptyList(), false), "list", emptySet())
        assertTrue(added.any { it is GroceryChange.UpsertLowStock })
        // Undo puts the batch back; the engine then only re-arms.
        val afterUndo = RestockEngine.plan(PantryData(listOf(milk.copy(restockArmed = false)), listOf(m1), false), "list", setOf("low_milk"))
        val removesItem = (undo.changes + afterUndo).any { it is GroceryChange.DeleteItem }
        assertTrue("Undo left Milk on the list as $afterUndo", removesItem)
    }

    @Test
    fun `P16 a snapshot with products but no stock yet (first sync) adds nothing`() {
        val milk = product("milk", min = 2.0)
        val plan = RestockEngine.plan(PantryData(listOf(milk), emptyList(), fromCache = true), "list", emptySet())
        assertEquals(emptyList<HouseholdChange>(), plan)
    }

    @Test
    fun `P17 restock thresholds with decimals`() {
        val flour = product("flour", unit = QuantityUnit.Kilograms, min = 2.0)
        val atMin = RestockEngine.plan(PantryData(listOf(flour), listOf(batch("f", flour, 2.0)), false), "l", emptySet())
        assertEquals(emptyList<HouseholdChange>(), atMin)
        val low = RestockEngine.plan(PantryData(listOf(flour), listOf(batch("f", flour, 1.5)), false), "l", emptySet())
        assertEquals(1.0, (low.first() as GroceryChange.UpsertLowStock).quantity, 0.0)
    }

    // ---- Learning ----

    @Test
    fun `P18 ingredient linking handles plurals and extra virgin`() {
        val cases = mapOf(
            "cookie" to "Cookies",
            "chilies" to "Chili",
            "pie crust" to "Pie crusts",
            "brownies" to "Brownie",
            "extra-virgin olive oil" to "Olive oil",
            "bay leaves" to "Bay leaf",
        )
        val misses = cases.filter { (line, name) -> ProductLink.find(line, listOf(product("x", name = name)))?.name != name }
        assertEquals("no link for", emptyMap<String, String>(), misses)
    }

    @Test
    fun `P19 aisle guesses for common names`() {
        val cases = mapOf(
            "chicken broth" to Category.PantryStaples,
            "beef broth" to Category.PantryStaples,
            "frozen spinach" to Category.Frozen,
            "frozen chicken" to Category.Frozen,
            "canned tomatoes" to Category.PantryStaples,
            "salad dressing" to Category.PantryStaples,
            "tomato paste" to Category.PantryStaples,
        )
        val wrong = cases.mapNotNull { (name, aisle) -> AisleGuess.of(name).takeIf { it != aisle }?.let { "$name -> $it" } }
        assertEquals(emptyList<String>(), wrong)
    }

    @Test
    fun `P20 store order learning always keeps every aisle exactly once`() {
        val aisles = Category.entries
        var saved = emptyList<Category>()
        val random = java.util.Random(3)
        repeat(2_000) {
            val visited = aisles.shuffled(random).take(random.nextInt(4))
            val checked = aisles[random.nextInt(aisles.size)]
            saved = StoreOrder.learn(saved, visited, checked) ?: saved
            assertEquals(aisles.toSet(), StoreOrder.effective(saved).toSet())
            assertEquals(aisles.size, StoreOrder.effective(saved).size)
        }
    }

    // ---- Recipe import ----

    @Test
    fun `P21 durations in ISO and in words`() {
        val cases = mapOf(
            "PT1H30M" to 90, "P0DT1H30M" to 90, "PT90M" to 90, "PT0.5H" to 30, "P1DT2H" to 1560, "pt20m" to 20,
            "1 hour 20 minutes" to 80, "1h20m" to 80, "1 1/2 hours" to 90, "45" to 45,
        )
        val wrong = cases.mapNotNull { (text, minutes) ->
            Durations.minutes(text).takeIf { it != minutes }?.let { "$text -> $it" }
        }
        assertEquals(emptyList<String>(), wrong)
    }

    @Test
    fun `P22 ingredient quantities`() {
        data class Case(val raw: String, val qty: Double?, val unit: String?, val name: String?)
        val cases = listOf(
            Case("½ cup sugar", 0.5, "cup", "sugar"),
            Case("1 ½ cups flour", 1.5, "cup", "flour"),
            Case("1⁄2 tsp salt", 0.5, "tsp", "salt"),
            Case("2–3 tomatoes", 2.0, null, "tomatoes"),
            Case("3 to 4 cloves garlic, minced", 3.0, "clove", "garlic"),
            Case("1 (14 oz) can tomatoes", 1.0, "can", "tomatoes"),
            Case("a pinch of salt", null, null, null),
            Case("1,5 kg Mehl", 1.5, "kg", "Mehl"),
            Case("1,000 g flour", 1000.0, "g", "flour"),
        )
        val wrong = cases.mapNotNull { c ->
            val p = IngredientParser.parse(c.raw)
            if (p.qty == c.qty && p.unit == c.unit && p.name == c.name) null else "${c.raw} -> ${p.qty} ${p.unit} ${p.name}"
        }
        assertEquals(emptyList<String>(), wrong)
    }

    @Test
    fun `P23 JSON-LD shapes`() {
        val graph = """{"@context":"https://schema.org","@graph":[{"@type":"WebPage"},{"@type":["Recipe","NewsArticle"],
            "name":"Soup &amp; bread","recipeIngredient":["1 onion","2 carrots",],"totalTime":"P0DT0H45M",
            "recipeInstructions":[{"@type":"HowToSection","name":"Soup","itemListElement":[{"@type":"HowToStep","text":"Chop."},
            {"@type":"HowToStep","text":"Simmer."}]}]}]}"""
        val node = JsonLd.best(listOf(graph))!!
        val c = JsonLd.map(node, "https://example.com/soup")
        assertEquals("Soup & bread", c.title)
        assertEquals(2, c.ingredients.size)
        assertEquals(45, c.totalMinutes)
        assertEquals(listOf("Soup", "Soup"), c.steps.map { it.group })
        // One string with line breaks, which some sites send instead of a list.
        val oneString = """{"@type":"Recipe","name":"Tea","recipeIngredient":"1 tea bag\n1 cup water","recipeInstructions":"Steep."}"""
        assertEquals(2, JsonLd.map(JsonLd.best(listOf(oneString))!!, null).ingredients.size)
    }

    // ---- Planner ----

    @Test
    fun `P24 copy last week keeps two meals of the same name in one slot`() {
        fun meal(id: String, date: LocalDate) =
            PlannedMeal(id, date, MealSlot.Lunch, id.last().digitToInt().toLong(), null, "Leftovers", null, emptyList(), null)
        val from = LocalDate.of(2026, 10, 18)
        val previous = listOf(meal("m0", from), meal("m1", from))
        assertEquals(2, MealWeek.copy(previous, from, from.plusDays(7), emptyList()).size)
    }

    @Test
    fun `P25 a recipe for 120 is planned at full size`() {
        val recipe = Recipe("r", RecipeContent("Meatballs", servings = 120))
        val servings = MealPlanService.validServings(recipe.content.servings)
        val meal = PlannedMeal("m", today, MealSlot.Dinner, 0, "r", "Meatballs", servings, emptyList(), null)
        assertEquals(1.0, MealPlanGrocery.factor(meal, recipe), 1e-9)
    }

    @Test
    fun `P26 the Edit window keeps Keep at least on a staple without automatic add`() {
        // A v1 product: Keep at least 2, Add to grocery list automatically off (DESIGN 5.2's Out row).
        val rice = product("rice", min = 2.0).copy(autoAddToGrocery = false)
        val b = batch("r1", rice, 1.0)
        // PantryForms sends minQuantity = form.min.takeIf { form.autoAdd }, so null when the switch is off.
        val edit = com.parcha.spoilnot.feature.pantry.domain.ItemEdit(
            "Rice", 1.0, QuantityUnit.Pieces, Category.Dairy, today.plusDays(9), Location.Pantry, false, null, null,
        )
        val changes = PantryService(RecordingWriter(), utc).planEdit(b, edit, PantryData(listOf(rice), listOf(b), false))
        val updated = changes.filterIsInstance<PantryChange.UpdateProduct>().single().product
        assertEquals(2.0, updated.minQuantity)
    }

    private class RecordingWriter : HouseholdWriter {
        var last: List<HouseholdChange> = emptyList()
        private var next = 0

        override fun newId() = "id${next++}"

        override suspend fun apply(householdId: String, actor: Actor, changes: List<HouseholdChange>) {
            last = changes
        }

        override val errors: Flow<AppError> = emptyFlow()
    }

    private object NoRepository : GroceryRepository {
        override fun observeLists(householdId: String): Flow<List<GroceryList>> = emptyFlow()

        override fun observeItems(householdId: String, listId: String): Flow<List<GroceryItem>> = emptyFlow()

        override suspend fun isCached(householdId: String, listId: String, itemId: String) = false

        override suspend fun itemIds(householdId: String, listId: String) = emptyList<String>()
    }
}
```

## Appendix B: Node probes for the reminder job (scratchpad, `node --test`)

Run from `backend/reminder` with `node --test <file>`. 10 tests, 4 failed (D1, D2, T5, E1).

```ts
// Section 03 probes for the reminder job's digest and time logic (scratchpad only).
import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import {
  buildDigest, buildDigestWithMeals, cookingFor, DEFAULT_PREFS, dueAt, selectItems, stockWindow, type Batch, type Meal,
  type UserDoc,
} from '/Users/jmalp/Code/personal/spoilnot/backend/reminder/src/digest.ts';
import { addDays, daysBetween, toLocal } from '/Users/jmalp/Code/personal/spoilnot/backend/reminder/src/time.ts';

const user = (over: Partial<UserDoc> = {}, prefs: Partial<UserDoc['prefs']> = {}): UserDoc => ({
  uid: 'u1', householdId: 'h1', timezone: 'America/New_York', locale: 'en',
  prefs: { ...DEFAULT_PREFS, ...prefs }, lastDigestDate: null, lastExpiredThrough: null, ...over,
});
const batch = (productName: string, expiryDate: string, dateType: Batch['dateType'] = 'use_by'): Batch =>
  ({ productName, expiryDate, dateType, quantity: 1 });

describe('D: digest text', () => {
  it('D1 one product in two stages is not "1 items"', () => {
    const d = buildDigest(selectItems(user(), '2026-10-12', [batch('Milk', '2026-10-12'), batch('Milk', '2026-10-13')]));
    assert.doesNotMatch(d!.title, /^1 items/, d!.title);
  });
  it('D2 one product expired as use-by and best-before is not "1 items"', () => {
    const d = buildDigest(selectItems(user(), '2026-10-12', [batch('Yogurt', '2026-10-10'), batch('Yogurt', '2026-10-11', 'best_before')]));
    assert.doesNotMatch(d!.title, /^1 items/, d!.title);
  });
  it('D3 two meals today read as plural, one as a sentence', () => {
    const meals: Meal[] = [
      { date: '2026-10-12', slot: 'dinner', position: 0, title: 'Tacos', cooks: ['u1'], cooked: false },
      { date: '2026-10-12', slot: 'breakfast', position: 0, title: 'Pancakes', cooks: ['u1'], cooked: false },
    ];
    const two = buildDigestWithMeals([], cookingFor(user(), '2026-10-12', meals))!;
    assert.equal(two.title, "You're cooking 2 meals today");
    assert.equal(two.body, "You're cooking: Pancakes (breakfast), Tacos (dinner).");
  });
});

describe('T: who is due, by time zone and DST', () => {
  it('T1 a 2 AM digest on the US spring-forward night arrives at 3:07', () => {
    const u = user({}, { digestMinutes: 120 });
    assert.equal(dueAt(u, new Date('2026-03-08T06:07:00Z')), null); // 1:07 EST
    assert.deepEqual(dueAt(u, new Date('2026-03-08T07:07:00Z')), { date: '2026-03-08', minutesOfDay: 187 }); // 3:07 EDT
  });
  it('T2 the repeated hour on fall-back night sends once', () => {
    const u = user({}, { digestMinutes: 60 });
    const first = dueAt(u, new Date('2026-11-01T05:07:00Z')); // 1:07 EDT
    assert.ok(first);
    assert.equal(dueAt({ ...u, lastDigestDate: first!.date }, new Date('2026-11-01T06:07:00Z')), null); // 1:07 EST
  });
  it('T3 UTC+14 and UTC-12 get their own New Year dates', () => {
    assert.equal(toLocal(new Date('2026-12-31T19:07:00Z'), 'Pacific/Kiritimati').date, '2027-01-01');
    assert.equal(toLocal(new Date('2027-01-01T21:07:00Z'), 'Etc/GMT+12').date, '2027-01-01');
    assert.equal(daysBetween('2028-02-28', '2028-03-01'), 2);
    assert.equal(addDays('2026-12-31', 1), '2027-01-01');
  });
  it('T4 half-hour and 45-minute zones are due at their hour', () => {
    assert.ok(dueAt(user({ timezone: 'Asia/Kathmandu' }), new Date('2026-10-12T03:22:00Z'))); // 9:07 NPT
    assert.ok(dueAt(user({ timezone: 'Asia/Kolkata' }), new Date('2026-10-12T03:37:00Z'))); // 9:07 IST
  });
  it('T5 an offset-style zone id from a phone is not read as UTC', () => {
    // 13:07 UTC is 18:37 at +05:30; read as UTC it would be 13:07.
    assert.equal(toLocal(new Date('2026-10-12T13:07:00Z'), 'GMT+05:30').minutesOfDay, 18 * 60 + 37);
  });
});

describe('E: expired modes', () => {
  it('E1 "every day until removed" still reports an item that expired 20 days ago', () => {
    const { lo } = stockWindow(['2026-10-12']);
    const loaded = [batch('Jam', '2026-09-22')].filter((b) => b.expiryDate >= lo); // the job's stock query
    const items = selectItems(user({}, { expiredMode: 'daily' }), '2026-10-12', loaded);
    assert.equal(items.length, 1);
  });
  it('E2 once: an item expired yesterday is reported once, also after a missed day', () => {
    const u = user({ lastExpiredThrough: '2026-10-09' }); // last digest on Oct 10, none on Oct 11
    const items = selectItems(u, '2026-10-12', [batch('Milk', '2026-10-10'), batch('Eggs', '2026-10-11')]);
    assert.deepEqual(items.map((i) => i.name), ['Milk', 'Eggs']);
    const next = selectItems({ ...u, lastExpiredThrough: '2026-10-11' }, '2026-10-13', [batch('Milk', '2026-10-10'), batch('Eggs', '2026-10-11')]);
    assert.equal(next.length, 0);
  });
});
```

## Appendix C: low-stock reproduction against the Firestore emulator (LOGIC-01)

A `firebase.json` in the scratchpad with Firestore on 8181 (websocket 9160), the hub on 4410, logging on 4510 and no
UI, then from `firebase/`: `npx firebase emulators:exec --config <scratch>/firebase.json --project demo-spoilnot
--only firestore "node lowstock-probe.cjs"`. Output in LOGIC-01.

```js
// LOGIC probe: a low-stock item removed by Add to pantry comes back checked the next time stock runs low.
// Writes mirror GroceryChangeMapper (UpsertLowStock, SetChecked, DeleteItem tombstone) field for field.
const { createRequire } = require('node:module');
const req = createRequire('/Users/jmalp/Code/personal/spoilnot/firebase/package.json');
const fs = require('node:fs');
const { initializeTestEnvironment, assertSucceeds } = req('@firebase/rules-unit-testing');
const { doc, getDoc, setDoc, writeBatch, serverTimestamp, deleteField } = req('firebase/firestore');

const HID = 'h1', LIST = 'g1', P = 'p1', ITEM = `households/${HID}/groceryLists/${LIST}/items/low_${P}`;
(async () => {
  const env = await initializeTestEnvironment({
    projectId: 'demo-spoilnot',
    firestore: { rules: fs.readFileSync('/Users/jmalp/Code/personal/spoilnot/firebase/firestore.rules', 'utf8') },
  });
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore(); const t = new Date('2026-10-01T00:00:00Z');
    await setDoc(doc(db, 'users/alice'), { displayName: 'Alice', householdId: HID });
    await setDoc(doc(db, `households/${HID}`), { name: 'Test home', ownerUid: 'alice', inviteCode: 'ABCDE23456', schemaVersion: 1, createdAt: t, updatedAt: t });
    await setDoc(doc(db, `households/${HID}/members/alice`), { role: 'owner', displayName: 'Alice', joinedAt: t });
    await setDoc(doc(db, `households/${HID}/groceryLists/${LIST}`), { name: 'Groceries', isDefault: true, createdAt: t, updatedAt: t });
  });
  const db = env.authenticatedContext('alice').firestore();
  const stamp = () => ({ updatedAt: serverTimestamp(), updatedBy: 'alice' });
  const upsertLowStock = (writeCreated) => {
    const fields = { name: 'Milk', nameKey: 'milk', unit: 'pcs', category: 'dairy', source: 'low_stock', productId: P, quantity: 1, deletedAt: deleteField(), ...stamp() };
    const all = writeCreated ? { ...fields, createdAt: serverTimestamp(), createdBy: 'alice', note: null, recipeId: null } : fields;
    const b = writeBatch(db); b.set(doc(db, ITEM), all, { merge: true }); return b.commit();
  };
  const show = async (label) => {
    const d = (await getDoc(doc(db, ITEM))).data();
    console.log(`${label}: checked=${d.checked} checkedBy=${d.checkedBy ?? null} deletedAt=${d.deletedAt ? 'set' : 'absent'} source=${d.source}`);
  };
  // 1. Milk drops below Keep at least: the engine adds low_p1 (not in the cache yet, so writeCreated = true).
  await assertSucceeds(upsertLowStock(true)); await show('1 restock adds the item');
  // 2. In the store: checked off (GroceryChange.SetChecked).
  let b = writeBatch(db); b.update(doc(db, ITEM), { checked: true, checkedBy: 'alice', checkedAt: serverTimestamp(), ...stamp() });
  await assertSucceeds(b.commit()); await show('2 checked off in the store');
  // 3. Add to pantry removes it from the list (GroceryChange.DeleteItem, tombstone merge).
  b = writeBatch(db); b.set(doc(db, ITEM), { deletedAt: serverTimestamp(), ...stamp() }, { merge: true });
  await assertSucceeds(b.commit()); await show('3 Add to pantry (tombstone)');
  // 4. Days later milk runs low again. isCached() treats the tombstone as absent, so writeCreated = true again.
  await assertSucceeds(upsertLowStock(true)); await show('4 restock adds it again');
  await env.cleanup();
})().catch((e) => { console.error(e); process.exit(1); });
```
