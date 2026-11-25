# 10 Web app: parity with Android and iPhone issues

Reviewed `0334b6a` on 2026-11-07 and 2026-11-08 (the branch `review/2026-11` at `860a4bb`, which adds only review
reports). Platforms: web (Android as the reference) / backend where they share data.

**Setup.** Firebase emulators from `firebase/` without import or export (`demo-spoilnot`, Auth 9099, Firestore 8080,
UI 4000). Pixel_8 headless with review 08's launch line, the dev debug app (`assembleDevDebug`) against the emulators.
The web app from `npm run dev` on port 5173 (the e2e configuration, emulator config, no `.env` file opened), driven
by Playwright 1.64.1 WebKit with the iPhone 15 profile (Safari 27.2 user agent). Each WebKit "phone" is a persistent
profile in the scratchpad, so its sign-in and IndexedDB cache survive between step scripts like a Home Screen app
between opens. Made-up accounts on the emulator only (password `review-pass-10`): household **Mixed home** with Alex
(owner, the iPhone in WebKit) and Blake (member, Pixel_8), and **Big pantry** (Perry, 500 purchases) for the scroll
measurement. Xcode's iOS Simulator is not installed on this Mac (`xcrun simctl` is missing), so nothing ran in real
Safari or in Home Screen mode.

## Summary

The port is faithful where it can be checked mechanically: 4,743 generated cases through the Kotlin and TypeScript
domain modules gave 10 mismatches, all about unusual space characters and week-start data; the session state machine
differs only where DESIGN 18.3 says it does; both apps write the same fields and read each other's documents; and of
69 features, screens, settings and messages, 49 are the same, 10 are documented exceptions, 8 differ and 2 are
missing on the web. No Android change since the port is missing, apart from #74, which is tracked privately. The flip
side of a faithful port is that **the web has Android's sync bugs**, all reproduced here in a household with Pixel_8
and an iPhone in WebKit: Add to pantry on both phones makes every product twice (WEB-01), an iPhone whose restock
engine writes to the list then misses the other members' changes for up to 20 hours (WEB-16), offline the web keeps
removed items on screen so Add to pantry can be pressed twice (WEB-02), and a listener error turns the app into a
spinner (WEB-04). The web-only problems are smaller: one wrong-typed list in a document leaves Pantry and Home spinning
on that iPhone even after a reload (WEB-10), remote changes move both lists under the finger (WEB-11), every remote
change re-renders every row (WEB-13), the keyboard likely covers the bottom of sheets (WEB-14), and there is no quota
banner (WEB-09). The web is ahead in a few places: the offline banner on Groceries, ASCII digits, a clock that follows
the time zone, browser tests for Groceries and Settings, and the fonts in its licence list. 16 findings: 4 P1, 3 P2,
9 P3; 10 of them confirm or extend an earlier finding on the web. Nothing went to the private notes.

## Metrics

| Metric | Value | How measured | Target or budget |
| --- | --- | --- | --- |
| Findings | **16: 4 P1, 3 P2, 9 P3.** 10 confirm or extend an earlier finding on the web (SYNC-01, SYNC-02, SYNC-03, SYNC-06, REL-01, REL-02, REL-04, REL-12, CODE-09, PERF-03/04); 6 are new, one of them (WEB-07) section 04's Undo idea confirmed | This report | — |
| Feature parity | **69 features, screens, settings and messages: 49 same, 8 differ, 2 missing, 10 documented exceptions** (18.1, 18.4, 18.5) | Table below: Android's screens, sheets and dialogs (51 composables) against the web's (48 components), DESIGN 5 feature by feature, each checked in the code and, where marked, on the emulators | Same, or a documented exception |
| Android changes since the port not on the web | **0 unported** at `0334b6a`: #69 (tour) and #70 (crash reports) touch documented exceptions, #71 and the release and dependency commits are Android-only tooling. On `main` since: #72 (backend, serves both apps) and #74 (Android sign-out: known, tracked privately) | `git log --since=2026-10-29 -- app/`, `git log main` | 0 |
| Differential logic tests | **4,743 cases in 22 sections, 10 mismatches**: name keys 4 of 34, week start 4 of 47, quick add 1 of 31, ingredient parse 1 of 58; 0 in the other 18 sections (table below) | A temporary Kotlin unit test generated inputs (seeded) and wrote Android's outputs to JSON; a temporary Vitest replayed them through `web/src/domain` (Appendix A). Both removed | 0 |
| Session state machine | **11 scripts, 76 steps; 4 scripts differ**: 3 by the documented 1 s pointer wait (DESIGN 18.3), 1 by trimming the display name | The same event scripts through both `SessionManager`s with fakes and virtual time (Appendix B) | Only documented differences |
| Data compatibility | **Same field set in every collection** (products, stock, items, meals, recipes, activity, stats, users); number encoding differs (Android doubles, web integers for whole numbers), and each app reads the other's | Mixed household on the emulators; every document dumped with the emulator's admin access and grouped by writer (Appendix C) | Same names and types |
| Unknown fields kept | **Every update keeps them; every Undo after a delete drops them, on both platforms** (WEB-07) | `futureField` added to 14 documents; 8 kinds of edits on each phone | Kept (DESIGN 8.6) |
| Mixed-household scenarios from section 04 | `p01` **diverges** (WEB-16), `p02`/`p03` **duplicates** (WEB-01), `p05` **loses Android's change** (WEB-03) | Pixel_8 and WebKit on the emulators | Converge |
| QA flows 28 to 36 covered by browser tests | **9 of 9 in part, 0 of 9 in full** (table below) | `web/tests/e2e` against QA.md | — |
| Wrong-typed documents (REL-04) | **Scalars absorbed (5 of 5 cases); a wrong-typed list blocks Pantry and Home**, with no recovery on that phone (WEB-10) | Household "Bad data" in WebKit | Lists still load (6.7) |
| Lighthouse mobile on the preview build | **Not run**: running Lighthouse through `npx` was refused by this session's permission check (it downloads and runs a package). Stand-in, Playwright's Chromium with Lighthouse's mobile throttling applied (4× CPU, 150 ms, 1.6 Mbps), 5 cold loads of the welcome screen: **FCP 2.31 s, LCP 2.37 s, long tasks after FCP 0 ms, 363 KiB transferred** (medians; runs within 10 ms of each other). Applied throttling is slower than Lighthouse's simulated one, so these don't compare with 00's 0.75 s / 3.2 s | `loadperf.cjs` (Appendix C) on `vite preview` | 00: 92 / 100 / 100; the owner can rerun RUNBOOK's command |
| First-load size | **382 KB gzipped** (main JS 365.1, second JS 7.8, CSS 8.7, HTML 0.4) plus the fonts the page draws; precache 21 entries, 1,488.5 KiB. Identical to 00 and 09 (no web change since `55edcc5`) | `npm run build` (emulator config) | `chunkSizeWarningLimit` 1,200 kB raw: exceeded (BASE-05, EFF-06) |
| Scrolling 500 purchases (462 rows; the list isn't virtualized) | **Frame interval P50 17, P90 17–18, P99 19 ms, 0–0.3% over 25 ms**; tap to row menu **33–44 ms** (two runs) | WebKit iPhone 15 profile, headless on the M1 Mac, dev build; the list scrolled 40 px a frame from the page (Playwright's mobile WebKit has no touch moves) | Relative only: an iPhone, not the Mac, decides (phone check 3) |
| React re-renders for one remote change | **Pantry: 924 row renders (462 rows × 2 commits) for one +1; Groceries: 80 (40 × 2) for one check-off**; arrival 60–63 ms (Groceries) and 286 ms (Pantry, one valid run) from the server write to the screen | A stand-in React DevTools hook counting components that rendered in each commit (Appendix C, `perf.cjs`) | Only the changed row (PERF-04's fix) |
| Remote change during a scroll (DESIGN 6.4) | **Moves the rows under the finger on both lists**: Pantry +73 px (twice), Groceries −56 px | `perf.cjs` probe: a slow scroll in progress, then a purchase that sorts above, or a check-off | No movement until the gesture ends |
| iPhone layout checks | **0 fields under 16 px** (no zoom on focus) on 6 fields × 3 profiles; no horizontal overflow on iPhone 15, iPhone 15 landscape and iPhone SE; safe areas through `env(safe-area-inset-*)` with `viewport-fit=cover`; `100dvh` (no `100vh`); `overscroll-behavior: contain` on the lists; sheets fit the screen (Add item 335 of 343 px in landscape, the date picker scrolls) | `iphone.cjs` and reading | — |
| Generated assets | `npm run gen:check` **passes** (0.8 s, no diff) | — | No diff |
| Strings | Android 596, web-only 21 (1 unused: `web_not_configured`); **68 Android strings the web never uses**: 59 belong to documented exceptions (recipe import and browser, tour, crash reports, battery tip, update gate, notification channel, open system settings), 3 have web versions on purpose, 6 are gaps (list below) | A script over `strings.xml`, `strings-web.xml`, `R.string` uses and the web's `str()` ids (dynamic prefixes counted) | — |

### Differential results by module

| Module (Kotlin → TypeScript) | Cases | Mismatches |
| --- | --- | --- |
| `ExpiryStatus` → `Expiry` (dates from 2025 to 2028 plus Feb 29, Dec 31, DST days) | 400 | 0 |
| Today and month in 20 time zones (UTC-12 to UTC+14, half and quarter hours, DST edges ±1 h) | 600 | 0 |
| `NameKey.of`/`clean` | 34 | **4** (WEB-06) |
| `NaturalOrder` | 500 | 0 |
| `ShelfLife` record, median, suggestion | 200 | 0 |
| `LocalDate.plusMonths` → `addMonths` | 150 | 0 |
| `WeekFields.of(locale)` → `firstDayOfWeek` (47 locale tags) | 47 | **4** (WEB-08: language-only tags on the JVM, `ar-AE` by CLDR version) |
| `PantryItems.build`, `ExpiredFold`, `PantryList.rows` (random products, batches, filters, sorts, groupings, searches) | 250 | 0 |
| `RestockEngine.plan`, `isBackInStock` | 250 | 0 |
| `AisleGuess` (197 names, with and without products) | 197 | 0 |
| `StoreOrder.effective`/`learn` | 300 | 0 |
| `QuickAddParser` | 31 | **1** (WEB-06: `3 kg rice`) |
| `GroceryLayout.of`, `suggestions` | 120 | 0 |
| `Insights.summary` (including 9,000,000-batch months for float rounding) | 250 | 0 |
| `MealWeek` start, build, next position, copy | 200 | 0 |
| `IngredientParser.parse`/`quantity`, `ShoppingName`, `PantryMatch.words` | 58 | **1** (WEB-06: `1 cup milk`) |
| `Scaling.line` (58 lines × 11 factors), `formatQuantity`, `factor` | 724 | 0 |
| `PantryMatch.singular` | 32 | 0 |
| `PantryMatch.matches`/`productFor`, `ProductLink.find`, `Cooking.lines`, `UseItUp.suggestions` | 200 | 0 |
| `DeltaSync.cursor` (including the 20-hour edge) | 200 | 0 |

The cases include section 03's probe inputs, so the zero rows also mean the web has the same LOGIC bugs (next
table).

### Hand-offs from earlier sections

| Hand-off | On the web | How checked |
| --- | --- | --- |
| BASE-01 (Insights screenshot test on the real clock) | **Same**: `web/tests/e2e/screens.spec.ts` uses the real clock; Android's test fixes 2026-10-18 | Reading |
| LOGIC-01 (restocked staple comes back checked) | **Same code**: `web/src/data/grocery.ts:218-240` writes the same merge without `checked` | Reading; section 03 reproduced it with the rules |
| LOGIC-02 (first sync adds every staple) | **Same**: `web/src/domain/grocery/restock.ts:97-150` evaluates cache snapshots | Reading |
| LOGIC-03, 04, 05, 10 | **Same**: Add to pantry looks up by name (`grocery/service.ts` `addToPantry`), offers any unit (`GroceryForms.tsx:186,276`), the Edit window clears Keep at least when automatic add is off (`ItemForm.tsx:138,213`), no Undo for Add to pantry or Delete list | Reading |
| LOGIC-08, 13, 14, 15, 16, 17 | **Same**: identical outputs for section 03's probe inputs ("3 lbs apples", "2l milk", "0 eggs", "0 tsp salt", copy last week, "1,5 kg Mehl", "cookies", "chicken broth"); `MAX_SERVINGS` is 100 on both | Differential test |
| LOGIC-11 (clock keeps its time zone) | **Not on the web**: `today(clock)` reads the zone on every call | Differential test (TZ switched per case) |
| LOGIC-12 (Arabic or Persian digits) | **Not on the web**: `formatQuantity` always writes ASCII digits (`ui/pantry/labels.ts:117`) | Reading |
| Week start (03) | Same on the same region tag; differences in WEB-08 | Differential test; WebKit's `Intl.Locale.getWeekInfo` |
| SYNC-01 (`p01`) | **Same**, reproduced with the iPhone as the writing phone | WEB-16 |
| SYNC-02 (`p02`/`p03`) | **Same**, reproduced in a mixed household | WEB-01 |
| SYNC-03 (`p05`) | **Same**, reproduced with the web as the editor | WEB-03 |
| SYNC-06 (approved request pruned) | **Same**: the web session says Expired | Session script `approvedPruned` (WEB-05) |
| REL-01 (offline deletes stay) | **Same** (`web/src/data/delta-sync.ts:34`) | WEB-02 |
| REL-02 (quota error → spinner) | **Same**: any listener error gives Loading, retried after 1, 2, 4 s | Session script `profileErrors` (WEB-04) |
| REL-10 (no offline banner on Groceries) | **Not on the web**: Groceries shows "Offline. Changes will sync later" (`assets/10/p02-web-offline-after.png`) | WebKit offline |
| REL-04 (wrong-typed document blanks a screen) | **Partly**: scalar types are absorbed; a wrong-typed list spins Pantry and Home, and the phone doesn't recover after a reload | WEB-10 |
| REL-12 (Export offline) | **Same** | WEB-12 |
| UX-01 (See all opens a list without the expired items) | **Same**: `HomeScreen.tsx:161` picks Expiring soon unless every item is expired; the shared `HomeDashboard` model sorts oldest first | Reading; differential test (`PantryList.rows`) |
| UX-02 (members never asked for notifications) | **Same**: the explainer only after Add item, when the household's dated purchases + 1 ≥ 3 (`NotificationRules.shouldExplain`, `PantryViewModel.kt:160`); on the web also only once installed. Pixel_8 asked Blake on his first dated item because the household already had dated food | Seen on Pixel_8; reading |
| UX-05 (Use it up suggests what's planned) | **Same** (`UseItUp.suggestions` takes no plan; 200 identical cases) | Differential test |
| UX-06 (Low stock and Grocery list cards repeat) | **Same**: both cards on the web's Home | Seen |
| UX-11 (confirm and Undo mixed) | **Same**: Delete recipe asks, then offers Undo (`recipe_delete_confirm`, then Undo in `recipes.spec.ts`); Clear checked and grocery deletes don't ask | Seen; reading |
| UX-16 (every check-off in Recent activity) | **Same**: the web wrote a `grocery_checked` event per check-off (8 web events in the mixed household's activity) | Seen (Appendix C dump) |
| A11Y-04 (Home names break at 200%) | **Same** (section 07 saw "Tortil / la…" on the web) | Section 07 |
| A11Y-07 (rows not one sentence) | **Same**: Home's attention row is a name span and a separate chip (`HomeScreen.tsx:395-401`); the grocery row is a `label` with the checkbox, the name and "× 6" (`GroceryRow.tsx:60-70`), so VoiceOver reads "Bananas, × 6, checkbox" without a unit or why it was added | Reading |
| A11Y-10 (tour reachable behind its card) | **Not on the web**: the web has no tour (DESIGN 18.1, removed in W6b), which is why section 07's axe passes never saw it | Reading |
| PERF-03 (Insights charts redrawn every frame) | **Same pattern**: `useGrow` sets React state on every animation frame in each chart (`ui/insights/Charts.tsx:36-52`); opening Insights made 47 to 51 commits in 1.5 s, re-rendering the ring, the bars and 395 to 422 stripe components (`Striped`) | WEB-13 |
| PERF-04 (every row redrawn on any change) | **Same, larger on Pantry**: no row is memoized (`React.memo` appears nowhere in `ui/`), so one remote check-off re-rendered all 40 grocery rows twice, and one remote +1 all 462 Pantry rows twice | WEB-13 |
| CODE-09 (Groceries moves rows under a scroll) | **Same**, and the web's Pantry lacks Android's hold too | WEB-11 |
| EFF-01 (listeners that never let go) | **Same for the restock engine**: started in `main.tsx:18` for the page's life; nothing listens to `visibilitychange` (only cook mode and Settings > Notifications do). Tabs are better than Android's: the shell renders only the open tab, so a screen's listeners detach when it is left (section 09 measured the re-attach). An iPhone that keeps the suspended page and resumes it after 30 minutes resumes the engine's products and stock listeners, billed as new queries | Reading; section 09's counts |
| REL-15 (update only at a cold start) | **Same**: `core/updates.ts:18-28` registers once and never calls `update()` | Reading |
| Reverse parity (11) | Confirmed: the web has Groceries and Settings e2e tests, the notification prompt rule in `domain` with a test, and the fonts in its licence list | Reading |

### Feature parity (Android is the reference)

Status: **Same**, **Differs**, **Missing** (on the web), **Exception** (documented in DESIGN 18). "Seen" means
checked on the emulators in this review; the rest by reading both implementations side by side.

| # | Area | Feature, screen, setting or message | Web | Note |
| --- | --- | --- | --- | --- |
| 1 | Account | Welcome, Create account, Sign in, Reset password | Same | Seen (sign-in on both) |
| 2 | Account | Email verification from Settings > Account | Same | |
| 3 | Household | Gate: create, join with a code, waiting, declined, expired | Same | SYNC-06 on both (WEB-05) |
| 4 | Household | Join requests on Home and in household settings, approve, decline | Same | |
| 5 | Household | Members, invite code (copy, share), new code, pause joining, rename, remove, transfer, leave, delete | Same | |
| 6 | Household | Clear household data (owner, password) | Same | |
| 7 | Account | Sign out | Same at this commit | #74 changed Android later; known, tracked privately |
| 8 | Account | Delete account | Same | |
| 9 | Shell | Settings button named with the join-request count | Differs | "Open settings (2)" against the plural sentence |
| 10 | Pantry | One row per purchase, Out rows, status chips with icon and text | Same | Seen |
| 11 | Pantry | Search, toggling filters, sort and group, folded expired header | Same | Differential test (250 cases) |
| 12 | Pantry | Swipes and the ⋮ menu: Use 1, Add 1, Mark opened, Used up, Throw away with a reason, Move | Same | Seen on both |
| 13 | Pantry | Undo snackbar (8 s) | Same | A11Y-02 on both |
| 14 | Pantry | Add item: known products, stepper, unit, the four date chips, date picker, location tiles, category, automatic add with Keep at least and buy quantity, merging, shelf-life learning | Same | Seen on both |
| 15 | Pantry | Edit window, unit locked for a product with stock | Same | SYNC-03 on both (WEB-03) |
| 16 | Pantry | Mark opened: the opened part on its own row, opened shelf life | Same | Seen (web wrote, Android read) |
| 17 | Pantry | Rows another phone changed are highlighted | Same | |
| 18 | Pantry | A remote change waits until the scroll ends (DESIGN 6.4 MUST) | **Missing** | WEB-10 |
| 19 | Pantry | Pending-sync icon | Same | |
| 20 | Shell | Offline banner on Home, Pantry, Plan and Recipes | Same | |
| 21 | Pantry, Groceries | Haptics on swipes and check-offs | Differs | Safari has no vibration API; nothing to port |
| 22 | Pantry | Notification explainer after the third dated item | Same | Waits until installed (18.4); UX-02 on both |
| 23 | Pantry | Out row's screen-reader action "Add stock" | Differs | No action name on the web |
| 24 | Groceries | Lists: switch, new, rename, delete with a confirmation | Same | |
| 25 | Groceries | Quick add with suggestions, Already on the list (Increase, Add anyway) | Same | WEB-06 for odd spaces |
| 26 | Groceries | Guessed aisles, learned store order | Same | Differential test |
| 27 | Groceries | Check off, In cart, Clear checked with Undo | Same | Seen on both |
| 28 | Groceries | Edit and rename an item | Same | Seen on both |
| 29 | Groceries | Delete with Undo (swipe and menu) | Same | Seen on both |
| 30 | Groceries | Add to pantry sheet: one line per item, Add all, skip | Same | Seen on both; SYNC-02 on both (WEB-01) |
| 31 | Groceries | Restock engine, Low stock, Back in stock with Remove | Same | Differential test; LOGIC-01, LOGIC-02 on both |
| 32 | Groceries | "added by" under another member's items | Same | Seen |
| 33 | Groceries | Offline banner | **Differs (web ahead)** | REL-10 is Android only |
| 34 | Groceries | A remote change waits until the scroll ends | Same (missing on both) | CODE-09; WEB-10 |
| 35 | Groceries | List switcher's action name "Change list" | Differs | |
| 36 | Reminders | Daily digest delivery | Exception | Web push from the Home Screen app, iOS 16.4+ (18.4) |
| 37 | Reminders | Settings > Notifications: on/off, hour, stages, expired mode, Meals I'm cooking, test | Same | |
| 38 | Reminders | Banner when reminders can't appear | Exception | Three web states (not allowed, turned off, not installed) for Android's blocked banner (18.4) |
| 39 | Reminders | Battery tip | Exception | 18.1 |
| 40 | Reminders | A tap opens Pantry on Expiring soon, or Plan | Same | |
| 41 | Reminders | Time zone written when it changes | Same | |
| 42 | Recipes | Box: search, Favorites and tag chips, sort, stars | Same | |
| 43 | Recipes | Detail: servings stepper, checklist, source link only when http(s) | Same | |
| 44 | Recipes | Add to grocery list: shopping names, product links, Change, learned names | Same | Differential test |
| 45 | Recipes | I cooked this | Same | Differential test (`Cooking.lines`) |
| 46 | Recipes | Cook mode with the screen kept on | Same | Screen Wake Lock on the web |
| 47 | Recipes | Edit; Delete with Undo | Same | Seen on both |
| 48 | Recipes | Share | Same | System share sheet on each |
| 49 | Recipes | Add recipe (paste, browse, write), import, built-in browser, share target | Exception | 18.1 |
| 50 | Recipes, Plan | Empty box and empty picker point to the Android app | Exception | 18.1 |
| 51 | Home | Needs attention, Use it up, Low stock, Grocery list, This month, Today, Recent activity, Join requests | Same | Seen on both |
| 52 | Home, Welcome | Add to Home Screen help | Exception | Web only (18.5) |
| 53 | Insights | Ring, six months, streak, reasons, tap a month | Same | Differential test |
| 54 | Tour | App tour and its Settings row | Exception | 18.1 |
| 55 | Plan | Week from the phone's first day of the week | Differs | WEB-08 |
| 56 | Plan | Add a recipe or something else with cooks, servings, note; move; copy last week; clear the week; the week to a grocery list; Cooked; Use it up in the picker; Today card | Same | Seen; differential test |
| 57 | Settings | Appearance: System, Light, Dark; Spoil Not green and five presets; units | Same | |
| 58 | Settings | Wallpaper colors (Material You) | Exception | 18.1 |
| 59 | Settings | Export | Differs | A download kept in Files; offline below |
| 60 | Settings | About and open source licences | Differs (web ahead) | The web lists Manrope and Fredoka (CODE-06) |
| 61 | Settings | Crash reports switch, test crash | Exception | 18.1 |
| 62 | Shell | Quota banner (DESIGN 7.5) | **Missing** | WEB-09 |
| 63 | Shell | Update required gate (`minSupportedBuild`) | Exception | 18.5; the web has the Update snackbar instead (REL-15) |
| 64 | Shell | Skeletons and empty states | Same | |
| 65 | Shell | Back within each tab's own stack | Same | Browser history per tab on the web |
| 66 | Data | Changes-only listeners, daily full read, tombstones | Same | Differential test (`cursor`); REL-01 on both |
| 67 | Data | Session state machine | Same | 1 s pointer wait documented (18.3) |
| 68 | Data | Fields written per collection | Same | Seen (Appendix C) |
| 69 | Data | Undo of a delete restores the whole document | Same | Drops unknown fields on both (WEB-07) |

### QA flows 28 to 36 against the browser tests

The browser tests play both phones as WebKit contexts and seed "Android" data through the emulator's admin access;
no test has an Android phone in it, and none goes past 200% text.

| Flow | Browser tests | Covered | Left to the owner's iPhone run |
| --- | --- | --- | --- |
| 28 W1 sign-in and household | `household.spec.ts` (4 tests: join and approve, remove, sign out, wrong password, reset, delete account); `screens.spec.ts` (light, dark, 200%) | Most | Copy and Share the code; Larger Text at the largest size |
| 29 W2a pantry list | `pantry.spec.ts` (3: list, filters, search; swipe, Undo, throw away, Mark opened, a second phone follows; expired header) | Most | The Android phone's side |
| 30 W2b add, edit, Home, Insights | `pantry-forms.spec.ts`, `home.spec.ts` | Most | Tapping an Out staple; the Android Home; Larger Text |
| 31 W3 groceries | `groceries.spec.ts` (2: aisles, Already on the list, store order, Add to pantry; lists, edit, swipe, Clear checked, Low stock, Back in stock, another phone's addition) | Most | Keep at least set on Android |
| 32 W4 recipes | `recipes.spec.ts` (2) | Most | The iOS share sheet; Open original in Safari; the screen kept on |
| 33 W5 plan | `plan.spec.ts` | Most | Week start against the Android phone; a meal planned on Android |
| 34 W6a settings | `settings.spec.ts` (3: appearance kept, export and Clear household data, About) | Most | Files app; radio buttons at the largest text |
| 35 W6b notifications | `notifications.spec.ts` (3, with the push APIs played by the test) | Part | Every real push: permission prompt, delivery, tap, turning it off in iOS Settings, sign-out |
| 36 W7 install and offline | `install.spec.ts` (2); `tests/preview` (the built app under the hosting headers, the pantry offline) | Part | Add to Home Screen, full screen, the Update path after a deploy |

**Result: 9 of 9 flows have browser tests for their web-only steps, 0 of 9 are fully automated**; what stays manual
is the Android phone's half, real push and install, and the largest text sizes.

### Strings Android has that the web doesn't use, outside the documented exceptions

| String | Android use | Web |
| --- | --- | --- |
| `open_settings_with_requests` ("Open settings, %d join requests", plural) | The settings button's name with the request count (`TabScaffold.kt:65`) | "Open settings (2)" (`Scaffold.tsx:65`) |
| `grocery_lists` ("Change list") | TalkBack action on the list switcher (`GroceriesScreen.kt:555`) | No action name; the switcher is named "List: Groceries" |
| `action_add_stock` ("Add stock") | TalkBack action on an Out row (`PantryComponents.kt:200`) | No action name |
| `filter_all` | Pantry's All filter label (`PantryScreen.kt:414`) | The web has no All chip (the chips toggle, as on Android since beta 7); the label is used only in Android's state description |
| `date_type_use_by`, `date_type_best_before` | `DateType.label()` (`PantryLabels.kt:189`) | Not shown |
| `plan_no_recipes`, `empty_recipes`, `about_privacy_body` | — | Replaced by web versions on purpose (`web_plan_no_recipes`, `web_recipes_empty`, `web_about_privacy_body`) |

## Findings

### WEB-01 · P1 · Confirmed · Android + web (SYNC-02) — Add to pantry on an offline iPhone and an Android phone adds every item twice and makes duplicate products

- **Where:** `web/src/domain/grocery/service.ts` (`addToPantry`, plans from the phone's copy with `planAdd` by name);
  Android `GroceryService.kt:249-283`. Same as SYNC-02.
- **What happens:** section 04 reproduced SYNC-02 with two Android phones. With the web as one phone: Alex's iPhone,
  offline in the store, presses Add to pantry on a cart of two (Droid milk, web rice 2 kg) and gets "Added to the
  pantry"; Blake presses Add all on Pixel_8 a moment later; the iPhone reconnects. Both plans land: two purchases of
  each and **two products of each name**, one created by each app.
- **Evidence:** `w08-p02.cjs` (Appendix C) and Pixel_8 by hand; the server afterwards:

  ```
  1KBD8kpi… web rice    created by Blake  03:58:03.577
  DFSFL9mZ… Droid milk  created by Blake  03:58:03.577
  IZCcCZmQ… web rice    created by Alex   03:58:06.679
  Ztz2uvE4… Droid milk  created by Alex   03:58:06.679
  ```

  The iPhone's Pantry then lists each twice (`assets/10/p02-web-pantry-after.png`).
- **Who it affects:** a household with an iPhone and an Android phone that shop together or unpack in two places;
  the iPhone is the one most likely to be offline in a store.
- **Suggested fix:** SYNC-02's (a rule that refuses a second tombstone on an item, so the later batch fails whole).
  Effort M. Also needs: the web port in the same pull request (the rule change rejects the web's write too, so its
  message must exist), a WebKit + Android check like this one.

### WEB-16 · P1 · Confirmed · Android + web (SYNC-01) — An iPhone whose restock engine writes to the list then misses the other members' changes to it

- **Where:** `web/src/data/delta-sync.ts:90-95` (the server listener's cursor is the newest `updatedAt` in the
  phone's cache minus 5 minutes), `web/src/domain/grocery/restock.ts` (the engine writes the default list from any
  tab); same as SYNC-01.
- **What happens:** section 04's `p01` with the iPhone as the phone that writes. Alex's iPhone opens Groceries once
  (a full read) and leaves. On Pixel_8 Blake adds P01 eggs and P01 bread, checks the bread off and deletes Alex's
  "early item". 5 min 20 s later Alex's iPhone, opened straight on Pantry from a digest link, uses up the last Milk;
  its restock engine writes Milk to the list, which puts a fresh `updatedAt` in the iPhone's cache. Alex opens
  Groceries: the listener asks only for changes after that write minus 5 minutes, so Blake's three changes are never
  read. The iPhone shows Milk and the deleted "early item", and neither of Blake's items, until the next daily full
  read (up to 20 hours).
- **Evidence:** `w12-p01a.cjs`, Pixel_8 by hand, `w12-p01b.cjs` (Appendix C):

  ```
  iPhone: DAIRY AND EGGS / Milk × 1 Low stock / OTHER / early item
  server: Milk, P01 bread (checked), P01 eggs
  ```

  `assets/10/p01-web-groceries.png`.
- **Who it affects:** every household with an iPhone and staples on automatic restock; using something up on Pantry is
  the everyday trigger, and the list is what people shop from.
- **Suggested fix:** SYNC-01's (keep the cursor from what the server listener delivered, per collection, in the
  `SyncLog`; on the web in `localStorage` next to the full-read records). Effort M. Also needs: both platforms in one
  batch, a browser test with an injectable margin.

### WEB-02 · P1 · Confirmed · Android + web (REL-01) — Offline, the web keeps what was removed on screen, so Add to pantry can be pressed again

- **Where:** `web/src/data/delta-sync.ts:34` (`snapshot.get('deletedAt') != null` reads a pending server timestamp as
  null); same as REL-01.
- **What happens:** with the network off, Add to pantry says "Added to the pantry", but the cart still shows "In
  cart (2)" with both items and the Add to pantry button, because the items' tombstones aren't recognized until the
  server confirms them. A second press adds everything again (REL-01 showed it on Android: one banana became two).
- **Evidence:** WebKit offline (`context.setOffline(true)`), `w08-p02.cjs`: statuses `["Offline. Changes will sync
  later", "Added to the pantry"]`, the list text after the press still ends with `IN CART (2) … Droid milk … web rice
  × 2 kg`, the button still visible. `assets/10/p02-web-offline-after.png`.
- **Who it affects:** iPhone users in a store without signal, which is the web's main offline case.
- **Suggested fix:** REL-01's: `snapshot.get('deletedAt', { serverTimestamps: 'estimate' }) != null` (or the field's
  presence). Effort S. Also needs: a WebKit test with `setOffline(true)`: delete, assert gone; Add to pantry, assert
  the cart is empty.

### WEB-04 · P1 · Confirmed (fake) · Likely (real Firestore) · Android + web (REL-02) — Any listener error turns the whole web app into a spinner

- **Where:** `web/src/domain/session.ts:262-275` (`withRetry`: any error → `LOADING`, then 1, 2, 4, 8 … 32 s);
  `web/src/App.tsx` replaces the app with the loading screen for `loading`.
- **What happens:** the same as REL-02 on Android: a profile or membership listener error (the quota running out,
  section 05's case 1) replaces the app, cached data and any half-filled sheet, with a spinner while it retries.
- **Evidence:** session script `profileErrors` (Appendix B), identical on both platforms:

  ```
  profileError:3 => loading | wait:500 => loading | wait:1000 => loading | wait:2000 => loading
  wait:4000 => inHousehold:h1:owner
  ```

  *Likely* for real Firestore, for section 05's reason (the emulator has no quota).
- **Who it affects:** every web user of the project at once when the quota runs out.
- **Suggested fix:** REL-02's (keep the last session while retrying a non-permission error; the quota banner above the
  root). Effort M. Also needs: a web session test with a fake that throws `resource-exhausted`.

### WEB-03 · P2 · Confirmed · Android + web (SYNC-03) — The web's Edit window writes back a quantity another member just changed

- **Where:** `web/src/domain/pantry/service.ts:427-440` (`planEdit` writes every field), `web/src/ui/pantry/ItemForm.tsx`
  (the form keeps the values it opened with). Same as SYNC-03.
- **What happens:** Alex opens Eggs (8) in the Edit window on the iPhone. Blake uses 2 on Pixel_8. The list behind the
  sheet updates to 6, the form keeps 8 and says nothing. Alex changes only the date and saves: the server has 8 again,
  and Blake's phone shows 8.
- **Evidence:** `w07-p05.cjs` (Appendix C):

  ```
  {"before": "Eggs Nov 19 Fridge × 8", "formBefore": "8", "formAfter": "8",
   "listBehindSheet": "Eggs Nov 19 Fridge × 6", "after": "Eggs Nov 21 Fridge × 8"}
  server quantity 8, expiry 2026-11-21; Pixel_8: "Eggs, 8 pieces, Nov 21, Fridge"
  ```

  `assets/10/p05-web-form.png`.
- **Who it affects:** two people handling the same food in the same minutes.
- **Suggested fix:** SYNC-03's (write only changed fields; watch the batch while the window is open and show 9.2's
  notice). Effort M. Also needs: the web port in the same change.

### WEB-05 · P2 · Confirmed (fake) · Android + web (SYNC-06) — An approved member whose request was pruned sees "expired" on the web too

- **Where:** `web/src/domain/session.ts:230` (`request === null ? 'expired' : …`); same as SYNC-06.
- **What happens:** with Blake already a member and the request deleted (the job prunes requests older than 7 days),
  the web session shows the gate with "expired", like Android.
- **Evidence:** session script `approvedPruned`: `member:member`, `profile:pending`, `request:none` →
  `noHousehold:expired` on both platforms.
- **Who it affects:** as SYNC-06; an iPhone joiner is as likely to wait a week as an Android one.
- **Suggested fix:** SYNC-06's (check the membership before saying Expired). Effort S. Also needs: the web session test.

### WEB-10 · P2 · Confirmed · Web (REL-04) — One document with a wrong-typed list keeps Pantry and Home spinning on that phone, even after a reload

- **Where:** `web/src/data/pantry.ts:56-61` (`(s.get('recentShelfLifeDays') as unknown[] | null) ?? []).filter(…)`
  and the same cast for `aliases`; other list fields: `data/recipes.ts` (ingredients, steps, tags), `data/plan.ts`
  (cooks); `core/hooks.ts` (a failed load listens again after 3 s); `data/delta-sync.ts` (the cache listener emits
  first).
- **What happens:** the web's mappers check scalar types one by one (`num`, `str`, `date` return null for another
  type), so most wrong types are absorbed better than on Android: a product name `42` shows nothing, a quantity
  `"two"` counts as 0, an expiry date `20261109` reads as no date, a grocery item `checked: "yes"` reads unchecked, a
  meal date `20261107` is left out. But a **list** field holding a string throws (`"7".filter is not a function`), and
  since one pantry flow maps every product, **Pantry and Home show a spinner** with no message. The 3-second retry
  listens again, the cache listener hands back the same cached copy first, and it throws again. After the document
  was fixed on the server (a valid list and a new `updatedAt`, as section 05 advises), that phone still spun after
  20 s and after a reload: its cached copy is read before the server's, every time. A fresh phone signed in to the
  same household loaded at once. Only signing out (which clears the cache) would get the first phone back. Android
  recovers on a restart (REL-04), the web doesn't.
- **Evidence:** household "Bad data" (`seed-bad.cjs`, Appendix C: one wrong field per document), WebKit
  (`w09-bad.cjs`, `w10-bad2.cjs`, `w11-reload.cjs`): Home `""`, Pantry `""` (spinner, `assets/10/bad-data-pantry-spinner.png`),
  Plan, Recipes and Groceries loaded; after the fix, the same phone: Pantry `""` after 20 s and after reopening; a new
  profile: Pantry lists Good milk twice (`assets/10/bad-data-fresh-phone-home.png`).
- **Who it affects:** an iPhone in a household where one document gets a wrong-typed list (another client's bug, a
  console edit, REL-14's restore); rare, but the phone is stuck until its owner thinks of signing out.
- **Suggested fix:** a safe `list(value)` helper (`Array.isArray(value) ? value : []`) for every array field, map each
  document in a `try` that skips (and counts) one that still fails, and an inline "Couldn't load. Retry" state
  (DESIGN 6.7) instead of the endless spinner. Effort S. Also needs: mapper unit tests with wrong types on the web,
  REL-04's fix on Android.

### WEB-06 · P3 · Confirmed · Android + web — Non-breaking and full-width spaces give a name a different key on each platform

- **Where:** Android `feature/pantry/domain/NameKey.kt:8` (`Regex("\\s+")`: Java's `\s` is ASCII whitespace only),
  `QuickAddParser` (`GroceryService.kt:27`), `IngredientParser`; web `domain/pantry/rules.ts:6-14`,
  `grocery/service.ts:16`, `recipes/parser.ts` (JavaScript's `\s` includes U+00A0, U+2028, U+3000 and other Unicode
  spaces).
- **What happens:** the same typed or pasted name gets two keys: "Milk  2%" is `milk  2%` on Android and
  `milk 2%` on the web; "牛乳　2本" (a Japanese keyboard's full-width space) keeps U+3000 on Android and becomes a
  plain space on the web. Since products, grocery items and "Already on the list" match by name key, an iPhone and an
  Android phone each create their own product for such a name, and Add to pantry from the other phone can't find it.
  The parsers differ the same way: "3 kg rice" is 3 kg of rice on the web and a name on Android; "1 cup
  milk" has the unit "cup" on the web and the name "cup milk" on Android. Imported recipes are safe: `RecipeText`
  collapses U+00A0 on both platforms before parsing.
- **Evidence:** differential test, sections `nameKey` (4 of 34), `quickAdd` (1), `ingredient` (1):

  ```
  "Milk  2%"   kotlin key "milk  2%"   web key "milk 2%"
  "x　y"        kotlin key "x　y"        web key "x y"
  "3 kg rice"  kotlin {name "3 kg rice", qty null}   web {name "rice", qty 3, unit "kg"}
  ```

- **Who it affects:** names pasted from web pages or messages (U+00A0 is common there) and people typing with a
  Japanese or Chinese keyboard; rare in English typing.
- **Suggested fix:** one definition of whitespace for both: on Android `Regex("\\s+", RegexOption.…)` won't do, so use
  `"[\\s\\u00A0\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000]+"` (or `\\p{Z}|\\s`) in `NameKey`, `QuickAddParser`
  and `IngredientParser`, and the same explicit class on the web so it doesn't depend on the engine. Existing keys with
  those characters need a one-time re-key (rare; a DECISIONS line). Effort S. Also needs: a shared test table both
  test suites read.

### WEB-07 · P3 · Confirmed · Android + web — Every Undo after a delete drops fields a newer app added, and changes who created a purchase

- **Where:** the `restore*` changes on both platforms (`set` without merge): web `data/pantry.ts`, `data/grocery.ts`,
  `data/recipes.ts`; Android `PantryChangeMapper`, `GroceryChangeMapper`, `RecipeChangeMapper` (section 04's Ideas).
- **What happens:** DESIGN 8.6 lets a newer app add optional fields that older ones keep. Updates keep them on both
  platforms; Undo of a delete writes the whole document from the phone's copy, so the field is gone. Undo of Used up
  also makes the person who pressed Undo the purchase's `createdBy` (web, on Blake's purchase), and Android's Undo of
  a checked grocery item clears `checkedAt`.
- **Evidence:** `futureField` added to 14 documents with the emulator's admin access (`future.sh`, Appendix C), then:

  | Action | On the web | On Android |
  | --- | --- | --- |
  | Edit window save, rename item, meal servings, recipe edit, check off | Kept | Kept |
  | Used up, then Undo | **Gone** (Droid butter; `createdBy` → Alex) | **Gone** (Web cheese) |
  | Delete grocery item, then Undo | **Gone** | **Gone** (`checkedAt` → null) |
  | Delete recipe, then Undo | **Gone** | **Gone** |

- **Who it affects:** nobody today (both apps write the same fields); the first release that adds a field to these
  documents, while older Android builds or a cached web app are in use.
- **Suggested fix:** restore with `set(…, merge)` of the old fields plus `deletedAt: deleteField()`, so unknown fields
  stay, and keep `createdBy`/`createdAt` from the copy. Effort S. Also needs: DESIGN 8.6 (say what Undo keeps), a
  mapper test per collection on both platforms.

### WEB-08 · P3 · Confirmed (code) · Likely (iPhone) · Web — The week starts from the browser's language, not the iPhone's First day of week setting

- **Where:** `web/src/ui/plan/components.tsx:10` (`firstDayOfWeek(navigator.language)`), `web/src/domain/plan/week.ts:21-35`;
  Android `PlanViewModel.kt:112` and `HomeViewModel.kt:92` (`WeekFields.of(Locale.getDefault())`).
- **What happens:** on the same region tag both apps agree (WebKit has `Intl.Locale.getWeekInfo`: `en-US` Sunday,
  `de-DE` Monday, `ar-EG` and `fa-IR` Saturday). Android 14+ also honors the phone's *First day of week* regional
  setting, which reaches `Locale.getDefault()` as the `fw` extension (`en-US-u-fw-mon` → Monday on both in the test).
  A web app can't read the iPhone's own setting and `navigator.language` doesn't carry it, so an iPhone set to start
  weeks on Monday in the US still sees Sunday weeks. Older Safari versions without week info use the fallback list,
  which has the Sunday regions but none of the Saturday ones (Egypt, Iran, Afghanistan, …), so those get Monday.
- **Evidence:** differential test (47 tags; the 4 mismatches are JVM-only cases: `es`, `de`, `ar` without a region and
  `ar-AE` by CLDR version); WebKit evaluation of `getWeekInfo()` for 14 tags (Appendix A).
- **Who it affects:** iPhone users whose week start differs from their language's region default.
- **Suggested fix:** offer "Week starts on" (Automatic, Sunday, Monday, Saturday) in Settings > Appearance on the web
  (kept in `AppPreferences`), and add Saturday regions to the fallback list. Effort S. Also needs: DESIGN 5.9 and 18.1
  (a documented difference).

### WEB-09 · P3 · Confirmed (code) · Web — The web has no quota banner, so a day without quota says nothing about why

- **Where:** Android `core/navigation/AppShell.kt:306-310,353-366` (`QuotaBanner` when `QuotaSignal.isExhausted()`);
  web: `core/errors.ts:44` maps `quotaExceeded` to `error_quota` for a failed action's snackbar, and nothing records
  the quota state or shows a banner (no `QuotaSignal` port, no use of `error_quota` in `ui/`).
- **What happens:** DESIGN 7.5 and 6.7 ask for "Spoil Not has reached its free daily limit and will work again
  tomorrow" as a banner. When reads run out the web shows either the spinner (WEB-04) or "Offline" with the cached
  data (section 05's case 2); a write that fails shows the message once as a snackbar. Android has the banner (though
  REL-02 shows it can be hidden behind the spinner).
- **Evidence:** `grep -rn "quota\|error_quota" web/src` finds only the error mapping; Android's banner is in the shell.
- **Who it affects:** every web user on a day the shared quota runs out.
- **Suggested fix:** port `QuotaSignal` into `data/firestore-rx.ts` (record the day when a `resource-exhausted` error
  arrives) and show the banner above the shell, as REL-02's fix does for Android. Effort S. Also needs: a unit test
  with a fake error, DESIGN 18.1 if the banner should differ.

### WEB-11 · P3 · Confirmed · Web (CODE-09) — A remote change moves Pantry and Groceries under the finger while scrolling

- **Where:** `web/src/ui/pantry/PantryScreen.tsx` and `ui/grocery/GroceriesScreen.tsx` render each snapshot at once
  (`useOnInnerScroll` only lifts the top bar); Android's Pantry holds the list while scrolling
  (`PantryScreen.kt:252`), its Groceries doesn't (CODE-09).
- **What happens:** DESIGN 6.4: a remote change MUST NOT move the list under the finger during a scroll; queue it
  until the gesture ends. On the web neither list waits, and WebKit doesn't keep the rows in place either: a purchase
  another phone adds that sorts above the visible rows pushes everything down by one row in the middle of a scroll,
  and another shopper's check-off pulls the Groceries rows up as the item moves to In cart.
- **Evidence:** `perf.cjs` (Appendix C), Big pantry in WebKit: the row under the middle of the list moved **73 px**
  beyond the scroll on Pantry (two runs) and **56 px** on Groceries; the remote change reached the screen 2.2 s into
  a 2.5 s scroll.
- **Who it affects:** two people shopping from one list, or one adding while the other scrolls the pantry; a tap meant
  for one row lands on the next.
- **Suggested fix:** the hold Android's Pantry has, as one hook for both lists: keep showing the last rows while a
  scroll is in progress (`scroll` events, then `scrollend` or 150 ms idle), then apply the latest snapshot. Effort S.
  Also needs: Android's Groceries (CODE-09) in the same batch, a browser test that changes a row during a scroll.

### WEB-12 · P3 · Confirmed · Android + web (REL-12) — Export offline says "Export saved" for the phone's copy

- **Where:** `web/src/data/export.ts:48-50` (`getDocs`/`getDoc` fall back to the cache offline); same as REL-12.
- **What happens:** with the network off, Settings > Export downloads `spoilnot-export-<date>.json` and says "Export
  saved". It holds whatever this phone has cached, with nothing in the file or the message saying so; a phone that
  hasn't opened some lists or the recipe box recently exports less than the household has.
- **Evidence:** WebKit offline (`w10-bad2.cjs`): `{"file": "spoilnot-export-2026-11-08.json", "sizes": {"products": 3,
  "stock": 3, "recipes": 2, "meals": 2, …}, "statuses": ["Export saved"]}`.
- **Who it affects:** owners exporting before Clear household data without a connection; rare.
- **Suggested fix:** REL-12's: `getDocsFromServer`/`getDocFromServer` and "You're offline" (`error_offline`), on both
  platforms. Effort S.

### WEB-13 · P3 · Confirmed (WebKit on the Mac) · Likely (iPhone) · Web (PERF-03, PERF-04) — Every remote change re-renders every row, and Insights re-renders its charts on every frame

- **Where:** `ui/pantry/PantryScreen.tsx` and `PantryRow.tsx`, `ui/grocery/GroceriesScreen.tsx:170-180` (`rowProps`
  builds new props and callbacks for every row on every render) and `GroceryRow.tsx`; no `React.memo` in `ui/`;
  `ui/insights/Charts.tsx:36-52` (`useGrow`: `setState` per animation frame).
- **What happens:** the pantry flow emits a new object for any change, the screen recomputes its groups and renders
  every row with fresh props. One +1 from another phone re-rendered all 462 Pantry rows twice (924 row renders,
  2,778 icons) and took 286 ms from the server write to the new number on screen;
  one check-off re-rendered all 40 grocery rows twice. Opening Insights re-renders the ring, the six bars and the
  reason bars about 47 times in its 700 ms growth, with 400 stripe components each time. WebKit on the Mac absorbs it
  (scrolling stayed at 17 ms frames); an older iPhone with a large pantry is where it would show.
- **Evidence:** `perf.cjs` counts (Metrics). The measurements are from the dev build, which renders slower than the
  release build.
- **Who it affects:** iPhones with large pantries, every time another member changes something.
- **Suggested fix:** `React.memo` on `PantryItemRow` and `GroceryRow` with props that keep their identity (the item
  object from the snapshot, `useCallback` handlers keyed by id); keep unchanged item objects from the previous
  snapshot in the mapper so `memo` can skip them; grow the charts with a CSS transform transition (`scaleY` and a
  stroke-dash animation) instead of React state per frame. Effort S–M. Also needs: PERF-03 and PERF-04's Android fixes
  in the same batch.

### WEB-14 · P3 · Likely · Web — The on-screen keyboard can cover the bottom of a sheet, and nothing scrolls the field into view

- **Where:** `ui/kit/BottomSheet.tsx` and `kit.css:560-575` (a bottom sheet up to `100dvh` minus the top inset); no
  `visualViewport` listener or `interactive-widget` setting anywhere in `web/src`.
- **What happens:** on an iPhone the keyboard doesn't shrink the layout viewport (`100dvh` stays the same), it covers
  the bottom of the page. Add item, the Edit window and Add to pantry are tall sheets with Save at the bottom; with the
  keyboard open on Name or Quantity, Save and the fields near the bottom sit under the keyboard until it is closed. In
  landscape the Add item sheet already takes 335 of 343 px (`assets/10/iphone-l15-add-item.png`), so the keyboard
  leaves room for about two fields. Android resizes the window for the keyboard (`imePadding`).
- **Evidence:** reading, and the landscape measurement. *Likely*: Playwright's WebKit shows no keyboard; phone check 2.
- **Who it affects:** everyone adding an item on an iPhone, many times a week.
- **Suggested fix:** follow `window.visualViewport` (`resize`, `scroll`) and set a `--sn-keyboard` CSS variable that
  lifts the sheet and its Save button above the keyboard; `scrollIntoView({ block: 'nearest' })` on focus. Effort S.
  Also needs: the owner's check on a real iPhone.

### WEB-15 · P3 · Likely · Web — In Safari, a row swipe that starts at the left edge goes back instead

- **Where:** `web/src/ui/swipe.ts:17-45` (a swipe starts anywhere in the row); `core/NavProvider.tsx` (each screen is
  a history entry).
- **What happens:** before the app is on the Home Screen, it runs in a Safari tab, where a swipe from the left edge is
  Safari's Back. A Pantry row swiped to the right (Used up) or a grocery row swiped from its left edge then leaves the
  screen instead of acting on the row. In the installed app iOS has no edge gesture, so this is only the first days
  before people install it (and for those who never do).
- **Evidence:** reading; the browser tests start their swipes at 80% of the row width. *Likely*: needs a real
  Safari tab.
- **Who it affects:** people trying the app in Safari.
- **Suggested fix:** ignore swipes that start within 24 px of the left edge (`e.clientX < 24`) when not in standalone
  mode. Effort S.

## Porting cost

The port is easy to follow where it mirrors Android file for file: 36 domain files, 48 of the web's files say what
they were "ported from", the generated strings, colors and icons can't drift (gen:check in CI), and the differential
test found no logic drift. Where the next port is likely to go wrong:

1. **Files don't map one to one.** Several Kotlin files became one TypeScript file and back: `NameKey`,
   `NaturalOrder`, `ExpiryStatus` and `ShelfLife` are `pantry/rules.ts`; `AisleGuess`, `StoreOrder` and
   `ShoppingTrip` are `grocery/aisles.ts`; `Cooking`, `UseItUp` and `RecipeIngredients` are `recipes/cooking.ts`;
   `PantryList.rows` is `pantry/rows.ts` without `PantryList.build`. A porter has to search for the counterpart, and
   a change to one Kotlin file can miss its web half.
2. **No ViewModel layer.** What Android keeps in ViewModels sits in React components on the web: store-order learning
   on check-off (`GroceriesScreen.tsx` `toggle`), `deleteEmptyBatches` and the writer's late errors
   (`PantryScreen.tsx:81-89`), the Pantry query in `AppShell.tsx`. A ViewModel change has no obvious place to go, and
   the components are the largest web files (PlanScreen 630 lines, ItemForm 579, GroceriesScreen 567).
3. **Language semantics.** Regular expressions (`\s`, `\d`, `\b` differ between Java and JavaScript: WEB-06),
   lowercasing, number formatting (`DecimalFormat` half-even against `Math.round`), `Float` against `number` in
   Insights, and integer against double encoding of stored numbers (both read either today). Each is invisible in a
   line-by-line port.
4. **Different failure paths.** Android ends a failed listener with `.catch {}` (REL-04); the web listens again after
   3 s (`core/hooks.ts`) and the session retries with a backoff. Same intent, different code, so a fix to one (REL-04,
   REL-02, WEB-10) doesn't carry over by porting.
5. **UI behavior that isn't in `domain`.** The scroll hold (6.4), row memoization, keyboard handling and
   accessibility action names are per-platform UI code with no shared test: the hold exists on Android's Pantry only
   (WEB-11), "Add stock" and "Change list" only on Android.
6. **Tests ported, not shared.** Unit tests were ported case by case, but there is no common table both suites read,
   and the browser tests never include an Android phone, so cross-platform behavior (WEB-01, WEB-03, WEB-16) is only in
   the manual QA flows. The test setups also drift (BASE-01: Android's screenshot test fixes its date, the web's
   doesn't).

### Parity checklist for Android pull requests

Proposed for the pull request template (one line each, ticked or "n/a because …"):

- [ ] **Domain:** every changed `feature/*/domain` function has its web counterpart changed in the same pull request
  or a linked one; the Kotlin test cases are added to the web test (and to a shared case table, see Ideas).
- [ ] **Strings, colors, icons:** `cd web && npm run gen` run and the generated files committed (CI checks).
- [ ] **Data:** a new or changed field is written by both mappers with the same name and type; old documents without it
  still read; Undo keeps fields it doesn't know (WEB-07); rules accept both apps' writes.
- [ ] **Text and numbers:** no `\s`, `\d` or `\b` without an explicit character class; no locale-dependent formatting
  of stored values.
- [ ] **ViewModel logic:** where it lives on the web (component or hook) is named in the description.
- [ ] **UI rules from DESIGN 6:** scroll hold, remote-change highlight, offline banner, Undo, empty and error states,
  accessibility labels and actions, the same on the web.
- [ ] **Tests:** a browser test for the web side; for anything two phones do at once, a mixed-household step in QA
  flows 28 to 36.
- [ ] **Docs:** DESIGN 17's row says "Android and web" or names the web pull request; if the web won't get it, DESIGN
  18.1 lists the exception.

## Ideas

- **Ask for persistent storage on the web.** `navigator.storage.persist()` (Safari 17+) after sign-in makes iOS less
  likely to evict the IndexedDB copy of a household (DESIGN 18.3 accepts eviction; section 05 suggested the same).
  Nothing in `web/src` asks today.
- **Quick add before the list loads.** Both apps ignore Enter while the list is still loading (web
  `GroceriesScreen.tsx:145`, Android `GroceriesViewModel.kt:154`): the text stays and nothing says why. Seen on the web
  right after opening Groceries from Home; a short "Loading your list…" or queuing the add would help on a slow first
  start.
- **One shared test table for both ports.** The differential test here is throwaway; a committed JSON of cases
  (inputs and expected outputs, generated from Kotlin) that the web's Vitest reads would catch port drift on every
  pull request.

## For other sections

- **12 Triage:** WEB-01, WEB-16, WEB-02, WEB-04, WEB-03, WEB-05 and WEB-12 are SYNC-02, SYNC-01, REL-01, REL-02,
  SYNC-03, SYNC-06 and REL-12 on the web: fix each on both platforms in one batch, so a mixed household never runs one
  fixed app and one unfixed. WEB-07 is section 04's Undo idea, now confirmed on both. WEB-11 and CODE-09 are one hook
  on the web and one helper on Android. WEB-13 goes with PERF-03 and PERF-04.
- **11 Code:** the web's `formatQuantity` rounds half up (`Math.round`) where Android's `DecimalFormat("0.##")` rounds
  half even (0.125 shows 0.13 against 0.12); a display detail. The porting-cost list above is input for the
  "one home per fact" work.
- **07 Accessibility:** the web lacks Android's "Add stock" and "Change list" action names, and its settings button
  says "Open settings (2)" instead of the plural sentence (strings table).
- **00 Baseline:** Lighthouse could not run from this session (permission); the throttled stand-in is in Metrics, and
  RUNBOOK's command still needs a run for the comparison with 92/100/100.

## Not covered

- **A real iPhone.** Xcode's iOS Simulator isn't installed here (`xcrun simctl` is missing), so nothing ran in
  Safari on iOS or in Home Screen mode. Left to the owner's phone checks: installing and standalone mode, the status
  bar in light and dark, the on-screen keyboard over sheets (WEB-14), Safari's edge swipe (WEB-15), long press
  callouts, real web push (the permission prompt, delivery at the chosen hour, opening a digest), the Update snackbar
  after a deploy (REL-15), storage eviction after weeks unused, Dynamic Type at the iPhone's own sizes (the app's
  scale table was checked by section 07; here only the profile's default size), rotation, and timing on iPhone
  hardware (WebKit on the M1 Mac is not an iPhone).
- **Lighthouse itself.** Refused by this session's permission check (`npx` runs a downloaded package); the stand-in
  measures load with applied throttling, which isn't comparable with Lighthouse's simulated scores.
- **The iPhone's First day of week setting** (WEB-08): whether iOS exposes it to Safari at all was not checked.
- **An iPhone resuming a suspended app after hours** (EFF-01): Playwright can't suspend a WebKit page; judged from the
  code and section 09's counts.
- **LOGIC-01 and LOGIC-02 on the web** were read, not run: section 03 reproduced LOGIC-01 with the rules and the
  same field writes, and LOGIC-02 needs a slow first sync.
- **axe** was not rerun (section 07, A11Y-12).
- **The services' change plans** (`PantryService`, `GroceryService`, `MealPlanService`, `RecipeService`) were not in
  the differential test: they need the writers' fakes on both sides. Their pure inputs (statuses, matching, restock,
  layout, week, scaling) were; the plans were compared by reading and through the mixed-household runs.
- **Pixel_8's own screenshots of this run** were used only to read what Android showed; Android-side visuals are
  section 06's and 07's.

## Phone checks for the owner

| # | Phone | Steps | Expected | Result |
| --- | --- | --- | --- | --- |
| 1 | iPhone | Install from Safari: Share > Add to Home Screen, open it | Opens full screen with the Spoil Not icon; no install card on Home | |
| 2 | iPhone | Pantry > Add item: tap Name with the keyboard open, then Quantity | The sheet and the Save button stay visible above the keyboard | |
| 3 | iPhone | Groceries: check off five items with the thumb of one hand, then Add to pantry | Every tap hits; nothing moves under the thumb | |
| 4 | iPhone | Allow notifications from Settings > Notifications, set the hour to the next hour; wait | A digest arrives after that hour (REL-03 makes it hours late); tapping it opens Pantry on Expiring soon | |
| 5 | iPhone | Rotate on Home, Pantry and an open sheet | Usable in landscape, nothing cut off | |
| 6 | iPhone | Settings > Accessibility > Display & Text Size > Larger Text, the largest size; open each tab | Text grows; names readable; nothing runs off cards (A11Y-04, A11Y-12) | |
| 7 | iPhone + Android | Airplane mode on the iPhone; Add to pantry on the same cart on both; airplane mode off (WEB-01) | Today: duplicates | |
| 8 | iPhone | In Safari (not installed), swipe a Pantry row to the right starting at the very left edge (WEB-15) | Today: Safari goes back instead of Used up | |
| 9 | iPhone | Settings > Appearance: Dark; look at the status bar on Home and in a sheet; then Light | Readable status bar in both | |
| 10 | iPhone | Settings > General > Language & Region: change the first day of the week (if the iPhone offers it); open Plan (WEB-08) | Today: the web ignores it | |
| 11 | iPhone | Long-press a recipe photo and a recipe's source link | No callout or text selection gets in the way | |
| 12 | iPhone | Leave the installed app unused for a week, then open it offline | The household is still there (storage not evicted) | |
| 13 | iPhone | With the app open, ask the owner's other phone to use up a staple; on the iPhone open Groceries a few minutes later (WEB-16) | Today: the list can miss the other phone's earlier changes | |

## Appendix A: differential logic tests (temporary, removed)

Put the Kotlin file at `app/src/test/kotlin/com/parcha/spoilnot/review/` and run
`./gradlew :app:testDevDebugUnitTest --tests com.parcha.spoilnot.review.ParityExportTest`; it writes `kotlin.json`
to the path in `OUT`. Put the Vitest file in `web/src/` and run `npx vitest run src/review-parity.test.ts`; it writes
`mismatches.json` next to it. Change the two paths for another machine.

`ParityExportTest.kt`:

```kotlin
package com.parcha.spoilnot.review

import com.parcha.spoilnot.core.firebase.DeltaSync
import com.parcha.spoilnot.feature.grocery.domain.AisleGuess
import com.parcha.spoilnot.feature.grocery.domain.GroceryChange
import com.parcha.spoilnot.feature.grocery.domain.GroceryItem
import com.parcha.spoilnot.feature.grocery.domain.GroceryLayout
import com.parcha.spoilnot.feature.grocery.domain.ItemSource
import com.parcha.spoilnot.feature.grocery.domain.QuickAddParser
import com.parcha.spoilnot.feature.grocery.domain.RestockEngine
import com.parcha.spoilnot.feature.grocery.domain.StoreOrder
import com.parcha.spoilnot.feature.insights.domain.Insights
import com.parcha.spoilnot.feature.insights.domain.MonthStats
import com.parcha.spoilnot.feature.pantry.domain.Category
import com.parcha.spoilnot.feature.pantry.domain.DateType
import com.parcha.spoilnot.feature.pantry.domain.DiscardReason
import com.parcha.spoilnot.feature.pantry.domain.ExpiredFold
import com.parcha.spoilnot.feature.pantry.domain.ExpiryStatus
import com.parcha.spoilnot.feature.pantry.domain.Location
import com.parcha.spoilnot.feature.pantry.domain.NameKey
import com.parcha.spoilnot.feature.pantry.domain.NaturalOrder
import com.parcha.spoilnot.feature.pantry.domain.PantryChange
import com.parcha.spoilnot.feature.pantry.domain.PantryData
import com.parcha.spoilnot.feature.pantry.domain.PantryFilter
import com.parcha.spoilnot.feature.pantry.domain.PantryGrouping
import com.parcha.spoilnot.feature.pantry.domain.PantryItem
import com.parcha.spoilnot.feature.pantry.domain.PantryItems
import com.parcha.spoilnot.feature.pantry.domain.PantryList
import com.parcha.spoilnot.feature.pantry.domain.PantryQuery
import com.parcha.spoilnot.feature.pantry.domain.PantrySort
import com.parcha.spoilnot.feature.pantry.domain.Product
import com.parcha.spoilnot.feature.pantry.domain.QuantityUnit
import com.parcha.spoilnot.feature.pantry.domain.ShelfLife
import com.parcha.spoilnot.feature.pantry.domain.StockBatch
import com.parcha.spoilnot.feature.plan.domain.MealSlot
import com.parcha.spoilnot.feature.plan.domain.MealWeek
import com.parcha.spoilnot.feature.plan.domain.PlannedMeal
import com.parcha.spoilnot.feature.recipes.domain.Cooking
import com.parcha.spoilnot.feature.recipes.domain.IngredientParser
import com.parcha.spoilnot.feature.recipes.domain.PantryMatch
import com.parcha.spoilnot.feature.recipes.domain.ProductLink
import com.parcha.spoilnot.feature.recipes.domain.Recipe
import com.parcha.spoilnot.feature.recipes.domain.RecipeContent
import com.parcha.spoilnot.feature.recipes.domain.Scaling
import com.parcha.spoilnot.feature.recipes.domain.ShoppingName
import com.parcha.spoilnot.feature.recipes.domain.UseItUp
import java.io.File
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.random.Random
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Test

/**
 * Review 10 (temporary, removed after the run): feeds generated inputs to the Kotlin domain and writes inputs and
 * outputs to JSON; web/src/review-parity.test.ts replays them through the TypeScript port and lists mismatches.
 */
class ParityExportTest {
    private val rnd = Random(20261107)
    private val out = mutableMapOf<String, MutableList<JsonElement>>()

    private fun case(section: String, input: JsonElement, output: JsonElement) {
        out.getOrPut(section) { mutableListOf() } += obj("in" to input, "out" to output)
    }

    @Test
    fun export() {
        timeAndStatus()
        pantryItems()
        restock()
        aisles()
        insights()
        plan()
        recipes()
        sync()
        val json = JsonObject(out.mapValues { JsonArray(it.value) })
        val file = File(OUT)
        file.parentFile.mkdirs()
        file.writeText(json.toString())
        println("wrote ${out.mapValues { it.value.size }} to $OUT")
    }

    // ---------------------------------------------------------------- time, status, names

    private fun timeAndStatus() {
        val edges = listOf(
            "2024-02-28", "2024-02-29", "2024-03-01", "2026-12-31", "2027-01-01", "2026-03-08", "2026-03-29",
            "2026-10-25", "2026-11-01", "2028-02-29", "2026-11-07",
        ).map(LocalDate::parse)
        repeat(400) {
            val today = if (it < edges.size * 10) edges[it % edges.size] else randomDate()
            val expiry = if (rnd.nextInt(12) == 0) null else today.plusDays((rnd.nextInt(24) - 10).toLong())
            case(
                "expiry",
                obj("expiry" to str(expiry?.toString()), "today" to str(today.toString())),
                obj(
                    "status" to str(statusWire(ExpiryStatus.of(expiry, today))),
                    "needsAttention" to JsonPrimitive(ExpiryStatus.of(expiry, today).needsAttention),
                    "days" to (expiry?.let { e -> JsonPrimitive(ExpiryStatus.daysUntil(e, today)) } ?: JsonNull),
                ),
            )
        }
        val zones = listOf(
            "Etc/GMT+12", "Pacific/Kiritimati", "America/New_York", "Europe/Madrid", "Australia/Lord_Howe",
            "Asia/Kolkata", "Asia/Kathmandu", "America/St_Johns", "Pacific/Chatham", "America/Santiago",
            "Africa/Casablanca", "Asia/Tehran", "Europe/London", "America/Los_Angeles", "Pacific/Apia", "UTC",
            "Pacific/Honolulu", "Asia/Tokyo", "Australia/Sydney", "America/Sao_Paulo",
        )
        val anchors = listOf(
            "2026-03-08T06:59:59Z", "2026-03-08T07:00:00Z", "2026-03-29T00:59:59Z", "2026-03-29T01:00:00Z",
            "2026-10-25T00:59:59Z", "2026-11-01T05:59:59Z", "2026-11-01T06:00:00Z", "2026-12-31T23:59:59Z",
            "2027-01-01T00:00:00Z", "2028-02-29T12:00:00Z", "2026-04-05T15:30:00Z", "2026-09-27T13:59:00Z",
        ).map(Instant::parse)
        repeat(600) {
            val zone = zones[it % zones.size]
            val instant = if (it < 240) {
                anchors[it % anchors.size].plusSeconds((rnd.nextInt(7200) - 3600).toLong())
            } else {
                Instant.ofEpochSecond(1_767_225_600L + rnd.nextInt(2 * 366 * 86_400))
            }
            val date = instant.atZone(ZoneId.of(zone)).toLocalDate()
            case(
                "today",
                obj("ms" to JsonPrimitive(instant.toEpochMilli()), "zone" to str(zone)),
                obj(
                    "today" to str(date.toString()),
                    "month" to str(YearMonth.from(date).toString()),
                    "firstMonth" to str(Insights.firstMonth(date).toString()),
                ),
            )
        }
        val names = listOf(
            "Crème Brûlée", "CRÈME  brûlée ", "Milk  2%", "Milk \t2%", "  Eggs  ", "İstanbul simit",
            "STRASSE", "Straße", "ﬁsh", "Ｍｉｌｋ", "ΣΊΣΥΦΟΣ", "éclair", "éclair", "Jalapeño", "Item ٣",
            "Item 3", "Bag 007", "Bag 7", "Bag 10", "🍎 apples", "Apple​", "naïve", "Zoë", "1 cup milk",
            "Ñoquis", "nOQUIS", "Øl", "Łódź", "Café́", "Ǆ", "Ångström", "a b", "x　y", "tab\u000bbed",
        )
        for (name in names) {
            case("nameKey", obj("name" to str(name)), obj("key" to str(NameKey.of(name)), "clean" to str(NameKey.clean(name))))
        }
        val orderPool = names + listOf("Item 20", "Item 100", "item 2", "Bag 02", "bag 2", "B", "a", "A", "10", "9", "", "Item 0010")
        repeat(500) {
            val a = orderPool[rnd.nextInt(orderPool.size)]
            val b = orderPool[rnd.nextInt(orderPool.size)]
            case("naturalOrder", obj("a" to str(a), "b" to str(b)), obj("sign" to JsonPrimitive(Integer.signum(NaturalOrder.compare(a, b)))))
        }
        repeat(200) {
            val recent = List(rnd.nextInt(7)) { rnd.nextInt(40) }
            val added = randomDate()
            val expiry = if (rnd.nextInt(6) == 0) null else added.plusDays((rnd.nextInt(60) - 5).toLong())
            val median = ShelfLife.median(recent)
            case(
                "shelfLife",
                obj("recent" to ints(recent), "added" to str(added.toString()), "expiry" to str(expiry?.toString())),
                obj(
                    "record" to ints(ShelfLife.record(recent, added, expiry)),
                    "median" to (median?.let { m -> JsonPrimitive(m) } ?: JsonNull),
                    "suggest" to str(ShelfLife.suggestExpiry(median, added)?.toString()),
                ),
            )
        }
        repeat(150) {
            val date = if (it % 3 == 0) LocalDate.of(2024 + rnd.nextInt(5), 1 + rnd.nextInt(12), 28 + rnd.nextInt(4).coerceAtMost(0)).withDayOfMonth(1).plusMonths(1).minusDays(1) else randomDate()
            val months = rnd.nextInt(30) - 15
            case("addMonths", obj("date" to str(date.toString()), "months" to JsonPrimitive(months)), obj("date" to str(date.plusMonths(months.toLong()).toString())))
        }
        val tags = listOf(
            "en-US", "en-GB", "de-DE", "fr-FR", "es-ES", "es-MX", "pt-BR", "pt-PT", "ar-EG", "ar-SA", "ar-AE", "fa-IR",
            "he-IL", "ja-JP", "zh-CN", "zh-TW", "ko-KR", "hi-IN", "en-IN", "en-AU", "en-CA", "fr-CA", "en-NZ", "ru-RU",
            "tr-TR", "nl-NL", "sv-SE", "pl-PL", "th-TH", "id-ID", "en", "es", "ar", "de", "en-US-u-fw-mon", "en-GB-u-fw-sun",
            "en-DE", "de-US", "es-US", "en-001", "en-150", "ar-MA", "ar-DZ", "ur-PK", "bn-BD", "am-ET", "mn-MN",
        )
        for (tag in tags) {
            val first = WeekFields.of(Locale.forLanguageTag(tag)).firstDayOfWeek.value
            case("weekStart", obj("locale" to str(tag)), obj("firstDay" to JsonPrimitive(first)))
        }
    }

    // ---------------------------------------------------------------- pantry list

    private val productNames = listOf(
        "Milk", "Whole milk", "Eggs", "Egg noodles", "Rice", "Basmati rice", "Spinach", "Baby spinach", "Yogurt",
        "Greek yogurt", "Chicken thighs", "Chicken broth", "Tomatoes", "Canned tomatoes", "Tomato paste", "Olive oil",
        "Flour", "Tortillas", "Butter", "Peanut butter", "Bread", "Cheddar cheese", "Crème fraîche", "Jalapeños",
        "Bay leaf", "Cookie", "Chili", "Brownie", "Onions", "Garlic", "Coffee", "Bananas", "Apples", "Item 2",
        "Item 10", "Bag 02", "Café", "Ice cream", "Frozen peas", "Soy sauce", "Lemon", "Potatoes", "Strawberries",
        "Cherry tomatoes", "Mozzarella", "Pie crust", "Oats", "Salt", "Sugar", "Cayenne", "Dill", "Water",
    )

    private fun randomProducts(n: Int): List<Product> = productNames.shuffled(rnd).take(n).mapIndexed { i, name ->
        val min = if (rnd.nextInt(3) == 0) listOf(1.0, 2.0, 0.5, 3.0, 500.0)[rnd.nextInt(5)] else null
        val recent = List(rnd.nextInt(4)) { rnd.nextInt(30) }
        Product(
            id = "p$i",
            name = name,
            nameKey = NameKey.of(name),
            category = Category.entries[rnd.nextInt(Category.entries.size)],
            defaultLocation = Location.entries[rnd.nextInt(Location.entries.size)],
            unit = QuantityUnit.entries[rnd.nextInt(QuantityUnit.entries.size)].takeIf { rnd.nextBoolean() }
                ?: QuantityUnit.Pieces,
            minQuantity = min,
            autoAddToGrocery = min != null && rnd.nextInt(5) != 0,
            buyQuantity = if (rnd.nextInt(4) == 0) 2.0 else null,
            restockArmed = rnd.nextBoolean(),
            typicalShelfLifeDays = ShelfLife.median(recent),
            recentShelfLifeDays = recent,
            createdAt = if (rnd.nextInt(5) == 0) null else Instant.ofEpochMilli(1_790_000_000_000L + rnd.nextInt(1_000_000_000)),
            lastAddedQuantity = null,
            aliases = emptyList(),
            openedShelfLifeDays = null,
        )
    }

    private fun randomBatches(products: List<Product>, n: Int, today: LocalDate): List<StockBatch> = List(n) { i ->
        val p = products[rnd.nextInt(products.size)]
        StockBatch(
            id = "b$i",
            productId = if (rnd.nextInt(15) == 0) "gone$i" else p.id,
            productName = if (rnd.nextInt(8) == 0) p.name.uppercase() else p.name,
            unit = p.unit,
            category = if (rnd.nextInt(6) == 0) Category.Other else p.category,
            quantity = listOf(1.0, 2.0, 0.5, 0.0, -1.0, 3.0, 250.0, 0.25, 1.5)[rnd.nextInt(9)],
            expiryDate = if (rnd.nextInt(5) == 0) null else today.plusDays((rnd.nextInt(20) - 6).toLong()),
            dateType = if (rnd.nextInt(4) == 0) DateType.BestBefore else DateType.UseBy,
            location = Location.entries[rnd.nextInt(Location.entries.size)],
            note = null,
            createdAt = if (rnd.nextInt(6) == 0) null else Instant.ofEpochMilli(1_790_000_000_000L + rnd.nextInt(1_000_000) * 1000L),
            pendingSync = rnd.nextInt(5) == 0,
            openedOn = null,
        )
    }

    private fun pantryItems() {
        repeat(250) {
            val today = randomDate()
            val products = randomProducts(2 + rnd.nextInt(8))
            val batches = randomBatches(products, rnd.nextInt(16), today)
            val search = when (rnd.nextInt(5)) {
                0 -> products[rnd.nextInt(products.size)].name.take(3).lowercase()
                1 -> " " + products[rnd.nextInt(products.size)].name.uppercase().take(4) + " "
                else -> ""
            }
            val query = PantryQuery(
                filter = PantryFilter.entries[rnd.nextInt(PantryFilter.entries.size)],
                sort = PantrySort.entries[rnd.nextInt(PantrySort.entries.size)],
                grouping = PantryGrouping.entries[rnd.nextInt(PantryGrouping.entries.size)],
                search = search,
            )
            val groups = PantryItems.build(products, batches, query, today)
            val fold = ExpiredFold.of(groups, query)
            val rows = PantryList.rows(products, batches, today)
            case(
                "pantryItems",
                obj(
                    "today" to str(today.toString()),
                    "products" to JsonArray(products.map(::productJson)),
                    "batches" to JsonArray(batches.map(::batchJson)),
                    "query" to obj(
                        "filter" to str(query.filter.name.replaceFirstChar { c -> c.lowercase() }),
                        "sort" to str(query.sort.name.replaceFirstChar { c -> c.lowercase() }),
                        "grouping" to str(query.grouping.name.replaceFirstChar { c -> c.lowercase() }),
                        "search" to str(query.search),
                    ),
                ),
                obj(
                    "groups" to JsonArray(
                        groups.map { g ->
                            obj(
                                "location" to str(g.location?.wire),
                                "category" to str(g.category?.wire),
                                "items" to JsonArray(g.items.map(::itemJson)),
                            )
                        },
                    ),
                    "fold" to (
                        fold?.let { f ->
                            obj(
                                "expired" to JsonArray(f.expired.map { e -> str(e.key) }),
                                "rest" to JsonArray(f.rest.map { g -> JsonArray(g.items.map { i -> str(i.key) }) }),
                            )
                        } ?: JsonNull
                        ),
                    "rows" to JsonArray(
                        rows.map { r ->
                            obj(
                                "productId" to str(r.productId),
                                "name" to str(r.name),
                                "unit" to str(r.unit.wire),
                                "category" to str(r.category.wire),
                                "batches" to JsonArray(r.batches.map { b -> str(b.id) }),
                                "total" to JsonPrimitive(r.total),
                                "status" to str(statusWire(r.status)),
                                "soonest" to str(r.soonest?.id),
                                "location" to str(r.location.wire),
                                "isOut" to JsonPrimitive(r.isOut),
                                "isLow" to JsonPrimitive(r.isLow),
                                "pendingSync" to JsonPrimitive(r.pendingSync),
                            )
                        },
                    ),
                ),
            )
        }
    }

    private fun itemJson(item: PantryItem): JsonElement = when (item) {
        is PantryItem.Purchase -> obj(
            "kind" to str("purchase"), "key" to str(item.key), "name" to str(item.name), "category" to str(item.category.wire),
            "location" to str(item.location.wire), "isLow" to JsonPrimitive(item.isLow), "status" to str(statusWire(item.status)),
        )
        is PantryItem.Out -> obj(
            "kind" to str("out"), "key" to str(item.key), "name" to str(item.name), "category" to str(item.category.wire),
            "location" to str(item.location.wire), "isLow" to JsonPrimitive(item.isLow), "status" to JsonNull,
        )
    }

    // ---------------------------------------------------------------- restock and groceries

    private fun restock() {
        repeat(250) {
            val today = randomDate()
            val products = randomProducts(1 + rnd.nextInt(8))
            val batches = randomBatches(products, rnd.nextInt(12), today)
            val data = PantryData(products, batches, fromCache = rnd.nextBoolean())
            val listId = if (rnd.nextInt(6) == 0) null else "list1"
            val cached = products.filter { rnd.nextBoolean() }.map { "low_${it.id}" }.toSet()
            val plan = RestockEngine.plan(data, listId, cached)
            val items = products.take(4).map { p -> groceryItem(p) }
            case(
                "restock",
                obj(
                    "products" to JsonArray(products.map(::productJson)),
                    "batches" to JsonArray(batches.map(::batchJson)),
                    "fromCache" to JsonPrimitive(data.fromCache),
                    "listId" to str(listId),
                    "cached" to JsonArray(cached.map(::str)),
                    "items" to JsonArray(items.map(::groceryJson)),
                ),
                obj(
                    "plan" to JsonArray(
                        plan.map { c ->
                            when (c) {
                                is PantryChange.SetRestockArmed -> obj("type" to str("pantry.setRestockArmed"), "productId" to str(c.productId), "armed" to JsonPrimitive(c.armed))
                                is GroceryChange.UpsertLowStock -> obj(
                                    "type" to str("grocery.upsertLowStock"), "listId" to str(c.listId), "productId" to str(c.productId),
                                    "name" to str(c.name), "nameKey" to str(c.nameKey), "unit" to str(c.unit.wire), "category" to str(c.category.wire),
                                    "quantity" to JsonPrimitive(c.quantity), "writeCreated" to JsonPrimitive(c.writeCreated),
                                )
                                else -> obj("type" to str(c::class.simpleName))
                            }
                        },
                    ),
                    "backInStock" to JsonArray(items.map { i -> JsonPrimitive(RestockEngine.isBackInStock(i, data)) }),
                ),
            )
        }
    }

    private fun groceryItem(p: Product, checked: Boolean = rnd.nextInt(4) == 0) = GroceryItem(
        id = "low_${p.id}", name = p.name, nameKey = p.nameKey, quantity = 1.0, unit = p.unit, note = null,
        category = p.category, checked = checked, checkedBy = null,
        source = if (rnd.nextInt(4) == 0) ItemSource.Manual else ItemSource.LowStock, productId = p.id, recipeId = null,
        createdBy = null, createdAt = null,
    )

    private val groceryNames = listOf(
        "chicken broth", "frozen spinach", "frozen chicken", "canned tomatoes", "tomato paste", "salad dressing",
        "ice cream", "sparkling water", "dish soap", "toilet paper", "paper towels", "cat food", "sourdough bread",
        "almond milk", "oat milk", "coconut milk", "peanut butter", "jalapeños", "crème fraîche", "café", "ÉPINARDS",
        "tofu", "hummus", "salsa", "tortilla chips", "milk", "eggs", "bananas", "apples", "chicken thighs", "ground beef",
        "salmon", "shrimp", "cheddar", "yogurt", "butter", "bread", "bagels", "tortillas", "rice", "pasta", "flour",
        "sugar", "olive oil", "soy sauce", "ketchup", "coffee", "tea", "orange juice", "beer", "wine", "chips",
        "cookies", "chocolate", "frozen peas", "frozen pizza", "lettuce", "spinach", "onions", "garlic", "lemons",
        "basil", "fresh dill", "baby carrots", "cherry tomatoes", "potato chips", "sweet potatoes", "dog treats",
        "batteries", "shampoo", "Milk 2%", "2% milk", "chicken", "beef stock", "vegetable stock", "apple juice",
        "pickles", "mustard", "mayonnaise", "frozen berries", "berries", "granola", "cereal", "oats", "honey",
    )

    private fun aisles() {
        for (name in groceryNames + productNames) {
            case("aisle", obj("name" to str(name), "products" to JsonArray(emptyList())), obj("category" to str(AisleGuess.of(name)?.wire)))
        }
        repeat(60) {
            val products = randomProducts(6)
            val name = if (rnd.nextBoolean()) products[rnd.nextInt(products.size)].name.lowercase() + listOf("", "s", " 2%", " organic").random(rnd) else groceryNames.random(rnd)
            case("aisle", obj("name" to str(name), "products" to JsonArray(products.map(::productJson))), obj("category" to str(AisleGuess.of(name, products)?.wire)))
        }
        repeat(300) {
            val saved = Category.entries.shuffled(rnd).take(rnd.nextInt(Category.entries.size + 1)).let { s -> if (rnd.nextInt(5) == 0) s + s.take(2) else s }
            val visited = List(rnd.nextInt(6)) { Category.entries.random(rnd) }
            val checked = Category.entries.random(rnd)
            case(
                "storeOrder",
                obj("saved" to wires(saved), "visited" to wires(visited), "checked" to str(checked.wire)),
                obj("effective" to wires(StoreOrder.effective(saved)), "learn" to (StoreOrder.learn(saved, visited, checked)?.let(::wires) ?: JsonNull)),
            )
        }
        val quick = listOf(
            "2 kg rice", "1,5 l milk", "3 lbs apples", "2l milk", "0 eggs", "10 fl oz juice", "12 eggs", "2  kg  rice",
            "  milk  ", "1.5 kg", "2 pcs bananas", "500 g", "3 oz cheese", "4 LB potatoes", "1 l", "6 bananas",
            "٢ kg rice", "1 fl_oz x", "2 Kg Flour", "2 kgrice", "1.5.2 kg rice", "007 eggs", "2 g", "1 gallon milk",
            "3 kg rice", "1 L water", "250 ML cream", "2 lb", "5 oz", "1 pcs", "10 pcsx",
        )
        for (q in quick) {
            val p = QuickAddParser.parse(q)
            case("quickAdd", obj("text" to str(q)), obj("name" to str(p.name), "quantity" to (p.quantity?.let { v -> JsonPrimitive(v) } ?: JsonNull), "unit" to str(p.unit?.wire)))
        }
        repeat(120) {
            val products = randomProducts(5)
            val items = List(rnd.nextInt(10)) { i ->
                val name = (groceryNames + productNames).random(rnd)
                GroceryItem(
                    id = "i$i", name = name, nameKey = NameKey.of(name), quantity = null, unit = null, note = null,
                    category = if (rnd.nextInt(4) == 0) null else Category.entries.random(rnd), checked = rnd.nextInt(3) == 0,
                    checkedBy = null, source = ItemSource.Manual, productId = null, recipeId = null, createdBy = null,
                    createdAt = if (rnd.nextInt(4) == 0) null else Instant.ofEpochMilli(1_790_000_000_000L + rnd.nextInt(10) * 1000L),
                )
            }
            val query = listOf("mi", "M", "chick", "c", "", " ", "frozen", "é", "café", "BREAD", "to").random(rnd)
            val view = GroceryLayout.of(items)
            case(
                "groceryLayout",
                obj("items" to JsonArray(items.map(::groceryJson)), "products" to JsonArray(products.map(::productJson)), "query" to str(query)),
                obj(
                    "aisles" to JsonArray(view.aisles.map { (c, list) -> obj("category" to str(c.wire), "items" to JsonArray(list.map { i -> str(i.id) })) }),
                    "inCart" to JsonArray(view.inCart.map { i -> str(i.id) }),
                    "suggestions" to JsonArray(GroceryLayout.suggestions(query, items, PantryData(products, emptyList(), false)).map(::str)),
                ),
            )
        }
    }

    // ---------------------------------------------------------------- insights

    private fun insights() {
        repeat(250) {
            val today = randomDate()
            val stored = (0 until 8).mapNotNull { back ->
                if (rnd.nextInt(3) == 0) return@mapNotNull null
                val month = YearMonth.from(today).minusMonths(back.toLong() - rnd.nextInt(2).toLong())
                val thrown = DiscardReason.entries.filter { rnd.nextInt(3) == 0 }.associateWith { 1 + rnd.nextInt(4) }
                val days = (1..month.lengthOfMonth()).filter { rnd.nextInt(12) == 0 }.associateWith { if (rnd.nextInt(5) == 0) 0 else 1 + rnd.nextInt(3) }
                val big = rnd.nextInt(20) == 0
                MonthStats(month, if (big) 8_999_999 else rnd.nextInt(13), if (big) mapOf(DiscardReason.Expired to 1_000_001) else thrown, days)
            }.distinctBy { s -> s.month }
            val s = Insights.summary(stored, today)
            case(
                "insights",
                obj("today" to str(today.toString()), "stored" to JsonArray(stored.map(::statsJson))),
                obj(
                    "current" to str(s.current.month.toString()),
                    "mood" to str(s.mood.name.replaceFirstChar { c -> c.lowercase() }),
                    "months" to JsonArray(s.months.map { m -> obj("month" to str(m.month.toString()), "used" to JsonPrimitive(m.used), "thrown" to JsonPrimitive(m.thrownTotal)) }),
                    "reasons" to JsonArray(s.reasons.map { (r, n) -> obj("reason" to str(r.wire), "count" to JsonPrimitive(n)) }),
                    "daysWithoutWaste" to (s.daysWithoutWaste?.let { d -> JsonPrimitive(d) } ?: JsonNull),
                    "neverThrown" to JsonPrimitive(s.neverThrown),
                    "bestMonth" to str(s.bestMonth?.month?.toString()),
                ),
            )
        }
    }

    // ---------------------------------------------------------------- plan

    private val mealTitles = listOf("Leftovers", "leftovers ", "Tacos", "Green curry", "Pasta", "Pasta", "Soup", "Oats", "Café au lait", "Pizza night")

    private fun plan() {
        repeat(200) {
            val date = randomDate()
            val first = 1 + rnd.nextInt(7)
            val start = MealWeek.start(date, DayOfWeek.of(first))
            val meals = List(rnd.nextInt(14)) { i ->
                PlannedMeal(
                    id = "m$i", date = start.plusDays((rnd.nextInt(9) - 1).toLong()), slot = MealSlot.entries.random(rnd),
                    position = rnd.nextInt(4).toLong(), recipeId = if (rnd.nextBoolean()) "r$i" else null,
                    title = mealTitles.random(rnd), servings = if (rnd.nextBoolean()) 1 + rnd.nextInt(6) else null,
                    cooks = emptyList(), note = null,
                    createdAt = if (rnd.nextInt(4) == 0) null else Instant.ofEpochMilli(1_790_000_000_000L + rnd.nextInt(5) * 1000L),
                )
            }
            val today = start.plusDays((rnd.nextInt(11) - 2).toLong())
            val week = MealWeek.build(start, meals, today)
            val previous = meals.filter { m -> !m.date.isBefore(start) && m.date.isBefore(start.plusDays(7)) }
            val to = start.plusDays(7)
            val existing = List(rnd.nextInt(5)) { i ->
                PlannedMeal(
                    id = "e$i", date = to.plusDays(rnd.nextInt(7).toLong()), slot = MealSlot.entries.random(rnd),
                    position = rnd.nextInt(3).toLong(), recipeId = null, title = mealTitles.random(rnd), servings = null,
                    cooks = emptyList(), note = null,
                )
            }
            val copies = MealWeek.copy(previous, start, to, existing)
            case(
                "plan",
                obj(
                    "date" to str(date.toString()), "firstDay" to JsonPrimitive(first), "today" to str(today.toString()),
                    "meals" to JsonArray(meals.map(::mealJson)), "existing" to JsonArray(existing.map(::mealJson)),
                ),
                obj(
                    "start" to str(start.toString()),
                    "end" to str(MealWeek.end(start).toString()),
                    "days" to JsonArray(
                        week.map { d ->
                            obj(
                                "date" to str(d.date.toString()), "isToday" to JsonPrimitive(d.isToday), "isPast" to JsonPrimitive(d.isPast),
                                "isEmpty" to JsonPrimitive(d.isEmpty),
                                "slots" to JsonArray(MealSlot.entries.map { s -> JsonArray(d.slots[s].orEmpty().map { m -> str(m.id) }) }),
                            )
                        },
                    ),
                    "next" to JsonArray(MealSlot.entries.map { s -> JsonPrimitive(MealWeek.nextPosition(meals.filter { m -> m.slot == s && m.date == start })) }),
                    "copy" to JsonArray(copies.map { m -> obj("date" to str(m.date.toString()), "slot" to str(m.slot.wire), "position" to JsonPrimitive(m.position), "title" to str(m.title)) }),
                ),
            )
        }
    }

    // ---------------------------------------------------------------- recipes

    private val lines = listOf(
        "2 cups fresh spinach", "1 cup Greek yogurt", "½ cup milk", "1 1/2 cups flour", "1½ tsp salt",
        "2-3 cloves garlic, minced", "3 eggs", "1 egg, for brushing", "500 g basmati rice", "1 kg potatoes",
        "2 tbsp extra-virgin olive oil", "1 (14 oz) can diced tomatoes", "a pinch of salt", "Salt and pepper to taste",
        "8 small flour tortillas", "200 ml chicken broth", "1,5 kg Mehl", "1,000 g flour", "2 bay leaves", "12 cookies",
        "1/8 tsp cayenne", "¼ cup peanut butter", "3 to 4 tomatoes", "1 lb chicken thighs", "2 lbs apples",
        "16 oz cheddar cheese, grated", "1 l whole milk", "250g butter", "2 jalapeños, seeded", "0.5 cup oats",
        "⅓ cup sugar", "1 ½ cups water", "2 x 400g cans tomatoes", "1 tablespoon soy sauce", "juice of 1 lemon",
        "3 large onions, chopped", "5 cherry tomatoes", "Fresh dill", "2 handfuls baby spinach", "1 cup milk",
        "2 brownies", "3 chilies", "1 pie crust", "2 cans (15 oz) chickpeas", "1 package tofu", "4 strawberries",
        "1 cup of coffee", "100 g mozzarella", "2 tsp sugar", "1 kg rice", "3 Eggs", "2 CUPS SPINACH", "1 ⅔ cups milk",
        "⅛ tsp salt", "1.25 lb potatoes", "10-12 bananas", "2–3 apples", "1 1/4 cups water",
    )

    private fun recipes() {
        for (raw in lines) {
            val ing = IngredientParser.parse(raw)
            val q = IngredientParser.quantity(raw)
            case(
                "ingredient",
                obj("raw" to str(raw)),
                obj(
                    "qty" to (ing.qty?.let { v -> JsonPrimitive(v) } ?: JsonNull), "unit" to str(ing.unit), "name" to str(ing.name),
                    "quantity" to (q?.let { x -> obj("value" to JsonPrimitive(x.value), "max" to (x.max?.let { m -> JsonPrimitive(m) } ?: JsonNull), "end" to JsonPrimitive(x.end)) } ?: JsonNull),
                    "shopping" to str(ShoppingName.of(ing.name ?: raw)),
                    "words" to JsonArray(PantryMatch.words(raw).sorted().map(::str)),
                ),
            )
            for (factor in listOf(0.1, 0.25, 0.5, 1.0, 1.5, 2.0, 3.0, 1.0 / 3, 2.0 / 3, 0.8333, 10.0)) {
                case("scaleLine", obj("raw" to str(raw), "factor" to JsonPrimitive(factor)), obj("line" to str(Scaling.line(raw, factor))))
            }
        }
        for (v in listOf(0.0, 0.01, 0.0625, 0.1, 0.125, 0.2, 0.25, 0.3, 1.0 / 3, 0.5, 0.6, 2.0 / 3, 0.75, 0.8, 0.9, 1.0, 1.05, 1.125, 1.5, 1.75, 2.0, 2.5, 3.333, 4.5, 10.0, 12.75, 100.0, 1234.5, 0.999, 1.999)) {
            case("formatQuantity", obj("value" to JsonPrimitive(v)), obj("text" to str(Scaling.formatQuantity(v))))
        }
        for (o in listOf(null, 0, 1, 2, 4, 6, 12, 1000)) for (t in listOf(null, 0, 1, 2, 3, 8, 100)) {
            case("factor", obj("original" to (o?.let { JsonPrimitive(it) } ?: JsonNull), "target" to (t?.let { JsonPrimitive(it) } ?: JsonNull)), obj("factor" to JsonPrimitive(Scaling.factor(o, t))))
        }
        val words = listOf("tomatoes", "potatoes", "cookies", "brownies", "chilies", "leaves", "berries", "eggs", "glass", "bus", "boxes", "dishes", "knives", "loaves", "cheese", "rice", "jalapeños", "onions", "s", "is", "peas", "hummus", "couscous", "asparagus", "chips", "sandwiches", "anchovies", "radishes", "mangoes", "avocados", "cherries", "pies")
        for (w in words) case("singular", obj("word" to str(w)), obj("singular" to str(PantryMatch.singular(w))))
        repeat(200) {
            val today = randomDate()
            val products = randomProducts(4 + rnd.nextInt(10))
            val batches = randomBatches(products, rnd.nextInt(14), today)
            val pantry = PantryData(products, batches, false)
            val line = lines.random(rnd)
            val nameKey = products.random(rnd).nameKey
            val linked = ProductLink.find(line, products)
            val recipes = List(1 + rnd.nextInt(5)) { i ->
                Recipe(
                    id = "r$i",
                    content = RecipeContent(
                        title = listOf("Green curry", "omelette", "Tacos", "apple pie", "Banana bread", "café salad").random(rnd),
                        servings = listOf(null, 2, 4).random(rnd),
                        ingredients = lines.shuffled(rnd).take(2 + rnd.nextInt(6)).map { r -> IngredientParser.parse(r) },
                    ),
                    favorite = rnd.nextBoolean(),
                )
            }
            val factor = listOf(0.5, 1.0, 1.5, 2.0, 0.1, 3.0).random(rnd)
            val cook = Cooking.lines(recipes.first(), pantry, factor)
            val use = UseItUp.suggestions(recipes, pantry, today)
            case(
                "matching",
                obj(
                    "today" to str(today.toString()), "products" to JsonArray(products.map(::productJson)), "batches" to JsonArray(batches.map(::batchJson)),
                    "line" to str(line), "nameKey" to str(nameKey), "recipes" to JsonArray(recipes.map(::recipeJson)), "factor" to JsonPrimitive(factor),
                ),
                obj(
                    "matches" to JsonPrimitive(PantryMatch.matches(nameKey, line)),
                    "productFor" to str(PantryMatch.productFor(line, pantry)?.id),
                    "link" to str(linked?.id),
                    "cook" to JsonArray(cook.map { c -> obj("productId" to str(c.product.id), "lines" to JsonArray(c.lines.map(::str)), "inStock" to JsonPrimitive(c.inStock), "suggested" to (c.suggested?.let { s -> JsonPrimitive(s) } ?: JsonNull)) }),
                    "useItUp" to JsonArray(use.map { u -> obj("recipeId" to str(u.recipe.id), "uses" to JsonArray(u.uses.map { e -> str("${e.product.id}/${e.batch.id}") })) }),
                ),
            )
        }
    }

    // ---------------------------------------------------------------- sync

    private fun sync() {
        val now = Instant.parse("2026-11-07T12:00:00Z")
        repeat(200) {
            val newest = if (rnd.nextInt(6) == 0) null else now.minusSeconds(rnd.nextInt(200_000).toLong() - 1000)
            val full = if (rnd.nextInt(6) == 0) null else now.minusSeconds(rnd.nextInt(30 * 3600).toLong() - 3600)
            val edge = if (it < 6) now.minusSeconds(20 * 3600L + (it - 3)) else full
            val c = DeltaSync.cursor(newest, edge, now)
            case(
                "cursor",
                obj("newest" to ms(newest), "full" to ms(edge), "now" to ms(now)),
                obj("cursor" to ms(c)),
            )
        }
    }

    // ---------------------------------------------------------------- JSON helpers

    private fun randomDate(): LocalDate = LocalDate.of(2025, 1, 1).plusDays(rnd.nextInt(4 * 365).toLong())

    private fun statusWire(s: ExpiryStatus) = when (s) {
        ExpiryStatus.NoDate -> "noDate"
        else -> s.name.lowercase()
    }

    private fun ms(i: Instant?): JsonElement = i?.let { JsonPrimitive(it.toEpochMilli()) } ?: JsonNull

    private fun str(s: String?): JsonElement = s?.let(::JsonPrimitive) ?: JsonNull

    private fun ints(l: List<Int>) = JsonArray(l.map(::JsonPrimitive))

    private fun wires(l: List<Category>) = JsonArray(l.map { str(it.wire) })

    private fun obj(vararg pairs: Pair<String, JsonElement>) = JsonObject(mapOf(*pairs))

    private fun num(d: Double?): JsonElement = d?.let(::JsonPrimitive) ?: JsonNull

    private fun productJson(p: Product) = obj(
        "id" to str(p.id), "name" to str(p.name), "nameKey" to str(p.nameKey), "category" to str(p.category.wire),
        "defaultLocation" to str(p.defaultLocation.wire), "unit" to str(p.unit.wire), "minQuantity" to num(p.minQuantity),
        "autoAddToGrocery" to JsonPrimitive(p.autoAddToGrocery), "buyQuantity" to num(p.buyQuantity),
        "restockArmed" to JsonPrimitive(p.restockArmed),
        "typicalShelfLifeDays" to (p.typicalShelfLifeDays?.let(::JsonPrimitive) ?: JsonNull),
        "recentShelfLifeDays" to ints(p.recentShelfLifeDays), "createdAt" to ms(p.createdAt),
        "lastAddedQuantity" to num(p.lastAddedQuantity), "aliases" to JsonArray(p.aliases.map(::str)),
        "openedShelfLifeDays" to (p.openedShelfLifeDays?.let(::JsonPrimitive) ?: JsonNull),
    )

    private fun batchJson(b: StockBatch) = obj(
        "id" to str(b.id), "productId" to str(b.productId), "productName" to str(b.productName), "unit" to str(b.unit.wire),
        "category" to str(b.category.wire), "quantity" to JsonPrimitive(b.quantity), "expiryDate" to str(b.expiryDate?.toString()),
        "dateType" to str(b.dateType.wire), "location" to str(b.location.wire), "note" to str(b.note), "createdAt" to ms(b.createdAt),
        "pendingSync" to JsonPrimitive(b.pendingSync), "openedOn" to str(b.openedOn?.toString()),
    )

    private fun groceryJson(i: GroceryItem) = obj(
        "id" to str(i.id), "name" to str(i.name), "nameKey" to str(i.nameKey), "quantity" to num(i.quantity),
        "unit" to str(i.unit?.wire), "note" to str(i.note), "category" to str(i.category?.wire), "checked" to JsonPrimitive(i.checked),
        "checkedBy" to str(i.checkedBy), "source" to str(i.source.wire), "productId" to str(i.productId), "recipeId" to str(i.recipeId),
        "createdBy" to str(i.createdBy), "createdAt" to ms(i.createdAt), "pendingSync" to JsonPrimitive(i.pendingSync), "aliasOf" to str(i.aliasOf),
    )

    private fun mealJson(m: PlannedMeal) = obj(
        "id" to str(m.id), "date" to str(m.date.toString()), "slot" to str(m.slot.wire), "position" to JsonPrimitive(m.position),
        "recipeId" to str(m.recipeId), "title" to str(m.title), "servings" to (m.servings?.let(::JsonPrimitive) ?: JsonNull),
        "cooks" to JsonArray(m.cooks.map(::str)), "note" to str(m.note), "cookedAt" to ms(m.cookedAt), "cookedBy" to str(m.cookedBy),
        "createdAt" to ms(m.createdAt), "createdBy" to str(m.createdBy), "pendingSync" to JsonPrimitive(m.pendingSync),
    )

    private fun statsJson(s: MonthStats) = obj(
        "month" to str(s.month.toString()), "used" to JsonPrimitive(s.used),
        "thrown" to JsonObject(s.thrown.entries.associate { (r, n) -> r.wire to JsonPrimitive(n) }),
        "thrownByDay" to JsonObject(s.thrownByDay.entries.associate { (d, n) -> d.toString() to JsonPrimitive(n) }),
    )

    private fun recipeJson(r: Recipe) = obj(
        "id" to str(r.id),
        "content" to obj(
            "title" to str(r.content.title), "sourceUrl" to JsonNull, "sourceName" to JsonNull, "imageUrl" to JsonNull,
            "description" to JsonNull, "servings" to (r.content.servings?.let(::JsonPrimitive) ?: JsonNull), "servingsText" to JsonNull,
            "prepMinutes" to JsonNull, "cookMinutes" to JsonNull, "totalMinutes" to JsonNull,
            "ingredients" to JsonArray(r.content.ingredients.map { i -> obj("raw" to str(i.raw), "qty" to num(i.qty), "unit" to str(i.unit), "name" to str(i.name), "group" to str(i.group)) }),
            "steps" to JsonArray(emptyList()), "notes" to JsonNull, "tags" to JsonArray(emptyList()), "importMethod" to str("manual"),
        ),
        "favorite" to JsonPrimitive(r.favorite), "rating" to JsonNull, "createdAt" to JsonNull, "createdBy" to JsonNull,
    )

    private companion object {
        const val OUT = "/private/tmp/claude-501/-Users-jmalp-Code-personal-spoilnot/23c17d6c-43ec-46c2-bb13-75dfc78faed3/scratchpad/parity/kotlin.json"
    }
}
```

`review-parity.test.ts`:

```ts
// Review 10 (temporary, removed after the run): replays the cases the Kotlin ParityExportTest wrote through the
// TypeScript port and writes every mismatch to the scratchpad.
import { readFileSync, writeFileSync } from 'node:fs';
import { expect, test } from 'vitest';
import { guessAisle, StoreOrder } from './domain/grocery/aisles';
import { RestockEngine } from './domain/grocery/restock';
import { GroceryLayout, parseQuickAdd } from './domain/grocery/service';
import { Insights, type MonthStats } from './domain/insights/insights';
import { ExpiredFold, PantryItems, type PantryItem } from './domain/pantry/items';
import { PantryRows } from './domain/pantry/rows';
import { Expiry, NameKey, naturalOrder, ShelfLife } from './domain/pantry/rules';
import { MEAL_SLOTS } from './domain/plan/models';
import { firstDayOfWeek, MealWeek, PlanDays, type DayOfWeek } from './domain/plan/week';
import { PantryMatch } from './domain/recipes/box';
import { Cooking, UseItUp } from './domain/recipes/cooking';
import { IngredientParser, Scaling } from './domain/recipes/parser';
import { ProductLink, ShoppingName } from './domain/recipes/shopping';
import { cursor } from './domain/sync';
import { addDays, addMonths, today as todayOf, yearMonthOf } from './domain/time';

const DIR = '/private/tmp/claude-501/-Users-jmalp-Code-personal-spoilnot/23c17d6c-43ec-46c2-bb13-75dfc78faed3/scratchpad/parity';
type Case = { in: any; out: any };
const cases: Record<string, Case[]> = JSON.parse(readFileSync(`${DIR}/kotlin.json`, 'utf8'));

/** Kotlin writes 2.0 for doubles; compare numbers by value, everything else exactly. */
function same(a: unknown, b: unknown): boolean {
  if (typeof a === 'number' && typeof b === 'number') return Math.abs(a - b) < 1e-9;
  if (Array.isArray(a) && Array.isArray(b)) return a.length === b.length && a.every((x, i) => same(x, b[i]));
  if (a && b && typeof a === 'object' && typeof b === 'object') {
    const ka = Object.keys(a).sort();
    const kb = Object.keys(b).sort();
    return same(ka, kb) && ka.every((k) => same((a as any)[k], (b as any)[k]));
  }
  return a === b;
}

const itemJson = (i: PantryItem) => ({
  kind: i.kind,
  key: i.key,
  name: i.name,
  category: i.category,
  location: i.location,
  isLow: i.isLow,
  status: i.kind === 'purchase' ? i.status : null,
});

const run: Record<string, (input: any) => unknown> = {
  expiry: ({ expiry, today }) => {
    const status = Expiry.of(expiry, today);
    return {
      status,
      needsAttention: Expiry.needsAttention(status),
      days: expiry === null ? null : Expiry.daysUntil(expiry, today),
    };
  },
  today: ({ ms, zone }) => {
    process.env.TZ = zone;
    const date = todayOf({ now: () => ms });
    return { today: date, month: yearMonthOf(date), firstMonth: Insights.firstMonth(date) };
  },
  nameKey: ({ name }) => ({ key: NameKey.of(name), clean: NameKey.clean(name) }),
  naturalOrder: ({ a, b }) => ({ sign: Math.sign(naturalOrder(a, b)) }),
  shelfLife: ({ recent, added, expiry }) => {
    const median = ShelfLife.median(recent);
    return {
      record: ShelfLife.record(recent, added, expiry),
      median,
      suggest: ShelfLife.suggestExpiry(median, added),
    };
  },
  addMonths: ({ date, months }) => ({ date: addMonths(date, months) }),
  weekStart: ({ locale }) => ({ firstDay: firstDayOfWeek(locale) }),
  pantryItems: ({ today, products, batches, query }) => {
    const groups = PantryItems.build(products, batches, query, today);
    const fold = ExpiredFold.of(groups, query);
    return {
      groups: groups.map((g) => ({ location: g.location, category: g.category, items: g.items.map(itemJson) })),
      fold: fold && { expired: fold.expired.map((e) => e.key), rest: fold.rest.map((g) => g.items.map((i) => i.key)) },
      rows: PantryRows.rows(products, batches, today).map((r) => ({
        productId: r.productId,
        name: r.name,
        unit: r.unit,
        category: r.category,
        batches: r.batches.map((b) => b.id),
        total: r.total,
        status: r.status,
        soonest: r.soonest?.id ?? null,
        location: r.location,
        isOut: r.isOut,
        isLow: r.isLow,
        pendingSync: r.pendingSync,
      })),
    };
  },
  restock: ({ products, batches, fromCache, listId, cached, items }) => {
    const data = { products, batches, fromCache };
    const plan = RestockEngine.plan(data, listId, new Set(cached));
    return {
      plan: plan.map((c: any) =>
        c.type === 'pantry.setRestockArmed'
          ? { type: c.type, productId: c.productId, armed: c.armed }
          : {
              type: c.type,
              listId: c.listId,
              productId: c.productId,
              name: c.name,
              nameKey: c.nameKey,
              unit: c.unit,
              category: c.category,
              quantity: c.quantity,
              writeCreated: c.writeCreated,
            },
      ),
      backInStock: items.map((i: any) => RestockEngine.isBackInStock(i, data)),
    };
  },
  aisle: ({ name, products }) => ({ category: guessAisle(name, products) }),
  storeOrder: ({ saved, visited, checked }) => ({
    effective: StoreOrder.effective(saved),
    learn: StoreOrder.learn(saved, visited, checked),
  }),
  quickAdd: ({ text }) => parseQuickAdd(text),
  groceryLayout: ({ items, products, query }) => {
    const view = GroceryLayout.of(items);
    return {
      aisles: view.aisles.map(([category, list]) => ({ category, items: list.map((i) => i.id) })),
      inCart: view.inCart.map((i) => i.id),
      suggestions: GroceryLayout.suggestions(query, items, { products, batches: [], fromCache: false }),
    };
  },
  insights: ({ today, stored }) => {
    const months: MonthStats[] = stored;
    const s = Insights.summary(months, today);
    const thrown = (m: MonthStats) => Object.values(m.thrown).reduce((a, n) => a + (n ?? 0), 0);
    return {
      current: s.current.month,
      mood: s.mood,
      months: s.months.map((m) => ({ month: m.month, used: m.used, thrown: thrown(m) })),
      reasons: s.reasons.map(([reason, count]) => ({ reason, count })),
      daysWithoutWaste: s.daysWithoutWaste,
      neverThrown: s.neverThrown,
      bestMonth: s.bestMonth?.month ?? null,
    };
  },
  plan: ({ date, firstDay, today, meals, existing }) => {
    const start = MealWeek.start(date, firstDay as DayOfWeek);
    const week = MealWeek.build(start, meals, today);
    const to = addDays(start, 7);
    const previous = meals.filter((m: any) => m.date >= start && m.date < to);
    return {
      start,
      end: MealWeek.end(start),
      days: week.map((d) => ({
        date: d.date,
        isToday: d.isToday,
        isPast: d.isPast,
        isEmpty: PlanDays.isEmpty(d),
        slots: MEAL_SLOTS.map((s) => d.slots[s].map((m) => m.id)),
      })),
      next: MEAL_SLOTS.map((s) => MealWeek.nextPosition(meals.filter((m: any) => m.slot === s && m.date === start))),
      copy: MealWeek.copy(previous, start, to, existing).map((m) => ({
        date: m.date,
        slot: m.slot,
        position: m.position,
        title: m.title,
      })),
    };
  },
  ingredient: ({ raw }) => {
    const ing = IngredientParser.parse(raw);
    const q = IngredientParser.quantity(raw);
    return {
      qty: ing.qty,
      unit: ing.unit,
      name: ing.name,
      quantity: q && { value: q.value, max: q.max, end: q.end },
      shopping: ShoppingName.of(ing.name ?? raw),
      words: [...PantryMatch.words(raw)].sort(),
    };
  },
  scaleLine: ({ raw, factor }) => ({ line: Scaling.line(raw, factor) }),
  formatQuantity: ({ value }) => ({ text: Scaling.formatQuantity(value) }),
  factor: ({ original, target }) => ({ factor: Scaling.factor(original, target) }),
  singular: ({ word }) => ({ singular: PantryMatch.singular(word) }),
  matching: ({ today, products, batches, line, nameKey, recipes, factor }) => {
    const pantry = { products, batches, fromCache: false };
    return {
      matches: PantryMatch.matches(nameKey, line),
      productFor: PantryMatch.productFor(line, pantry)?.id ?? null,
      link: ProductLink.find(line, products)?.id ?? null,
      cook: Cooking.lines(recipes[0], pantry, factor).map((c) => ({
        productId: c.product.id,
        lines: c.lines,
        inStock: c.inStock,
        suggested: c.suggested,
      })),
      useItUp: UseItUp.suggestions(recipes, pantry, today).map((u) => ({
        recipeId: u.recipe.id,
        uses: u.uses.map((e) => `${e.product.id}/${e.batch.id}`),
      })),
    };
  },
  cursor: ({ newest, full, now }) => ({ cursor: cursor(newest, full, now) }),
};

test('Kotlin and TypeScript domain outputs match', () => {
  const report: Record<string, { total: number; mismatches: { in: unknown; kotlin: unknown; web: unknown }[] }> = {};
  for (const [section, list] of Object.entries(cases)) {
    const fn = run[section];
    const entry = (report[section] = { total: list.length, mismatches: [] as any[] });
    if (!fn) {
      entry.mismatches.push({ in: 'no replay for section', kotlin: null, web: null });
      continue;
    }
    for (const c of list) {
      let web: unknown;
      try {
        web = fn(structuredClone(c.in));
      } catch (error) {
        web = { threw: String(error) };
      }
      if (!same(c.out, web)) entry.mismatches.push({ in: c.in, kotlin: c.out, web });
    }
  }
  delete process.env.TZ;
  writeFileSync(`${DIR}/mismatches.json`, JSON.stringify(report, null, 1));
  const summary = Object.fromEntries(Object.entries(report).map(([k, v]) => [k, `${v.mismatches.length}/${v.total}`]));
  console.log(summary);
  expect(Object.keys(report).length).toBeGreaterThan(0);
});
```

Week start in WebKit (`node weekinfo.cjs` from the scratchpad, Playwright from `web/node_modules`):

```js
const { createRequire } = require('module');
const req = createRequire('/Users/jmalp/Code/personal/spoilnot/web/package.json');
const { webkit, devices } = req('@playwright/test');
(async () => {
  const browser = await webkit.launch();
  const ctx = await browser.newContext({ ...devices['iPhone 15'] });
  const page = await ctx.newPage();
  await page.setContent('<p>x</p>');
  const r = await page.evaluate(() => {
    const tags = ['en-US','en-GB','de-DE','ar-EG','ar-AE','fa-IR','he-IL','pt-BR','es','de','ar','en-US-u-fw-mon','ja-JP','hi-IN'];
    return { ua: navigator.userAgent, lang: navigator.language, langs: navigator.languages, res: tags.map(t => { const l = new Intl.Locale(t); return [t, typeof l.getWeekInfo, l.getWeekInfo ? l.getWeekInfo().firstDay : null, l.weekInfo ? l.weekInfo.firstDay : null, l.maximize().region]; }) };
  });
  console.log(JSON.stringify(r, null, 1));
  await browser.close();
})();
```

## Appendix B: session state machine scripts (temporary, removed)

Same placement as Appendix A: `ParitySessionTest.kt` writes `session-kotlin.json`, `review-parity-session.test.ts`
replays the steps and writes `session-mismatches.json`.

`ParitySessionTest.kt`:

```kotlin
package com.parcha.spoilnot.review

import com.parcha.spoilnot.core.error.AppError
import com.parcha.spoilnot.feature.auth.domain.AuthRepository
import com.parcha.spoilnot.feature.auth.domain.AuthUser
import com.parcha.spoilnot.feature.auth.domain.PendingJoin
import com.parcha.spoilnot.feature.auth.domain.Units
import com.parcha.spoilnot.feature.auth.domain.UserProfile
import com.parcha.spoilnot.feature.auth.domain.UserRepository
import com.parcha.spoilnot.feature.household.domain.Household
import com.parcha.spoilnot.feature.household.domain.HouseholdRepository
import com.parcha.spoilnot.feature.household.domain.InviteCode
import com.parcha.spoilnot.feature.household.domain.JoinRequest
import com.parcha.spoilnot.feature.household.domain.JoinRequestStatus
import com.parcha.spoilnot.feature.household.domain.JoinState
import com.parcha.spoilnot.feature.household.domain.LocalData
import com.parcha.spoilnot.feature.household.domain.Member
import com.parcha.spoilnot.feature.household.domain.Membership
import com.parcha.spoilnot.feature.household.domain.Role
import com.parcha.spoilnot.feature.household.domain.Session
import com.parcha.spoilnot.feature.household.domain.SessionManager
import com.parcha.spoilnot.feature.notifications.domain.DeviceRegistry
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Test

/**
 * Review 10 (temporary): the same event scripts through Android's SessionManager; the session after each step goes
 * to JSON for web/src/review-parity-session.test.ts.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ParitySessionTest {
    private val pending = PendingJoin("h1", "The Smiths", "ABCDE23456")

    private val scripts: Map<String, List<String>> = mapOf(
        "missingProfile" to listOf("signIn", "profile:none"),
        "memberThenRemoved" to listOf("signIn", "member:owner", "profile:h1", "member:gone", "wait:100"),
        "joinStates" to listOf("signIn", "profile:pending", "request:pending", "request:declined", "request:none"),
        "approvedPruned" to listOf("signIn", "member:member", "profile:pending", "request:none"),
        "approvedCompletes" to listOf("signIn", "member:member", "profile:pending", "request:approved", "wait:10"),
        "profileErrors" to listOf(
            "signIn", "member:owner", "profile:h1", "profileError:3", "wait:500", "wait:1000", "wait:2000", "wait:4000",
            "wait:8000",
        ),
        "memberError" to listOf("signIn", "member:owner", "profile:h1", "memberError:1", "wait:500", "wait:1000"),
        "pointerFlicker" to listOf("signIn", "member:owner", "profile:h1", "profile:none-household", "wait:200", "profile:h1", "wait:2000"),
        "pointerLost" to listOf("signIn", "member:owner", "profile:h1", "profile:none-household", "wait:200", "wait:2000"),
        "blankName" to listOf("signInBlank", "profile:none"),
        "signOut" to listOf("signIn", "member:owner", "profile:h1", "signOut"),
    )

    @Test
    fun export() {
        val result = scripts.mapValues { (_, steps) -> JsonArray(run(steps).map(::JsonPrimitive)) }
        File(OUT).writeText(JsonObject(result).toString())
    }

    private fun run(steps: List<String>): List<String> {
        val trace = mutableListOf<String>()
        runTest(UnconfinedTestDispatcher()) {
            val log = mutableListOf<String>()
            val auth = FakeAuth(log)
            val users = FakeUsers()
            val households = FakeHouseholds(users)
            val local = FakeLocalData(log)
            val manager = SessionManager(auth, users, households, local, NoDevices, backgroundScope)
            runCurrent()
            for (step in steps) {
                val (kind, arg) = step.split(":").let { it[0] to it.getOrNull(1) }
                when (kind) {
                    "signIn" -> auth.user.value = AuthUser("sam", "sam@example.com", "Sam", false)
                    "signInBlank" -> auth.user.value = AuthUser("sam", "sam.smith@example.com", "  Sam  ", false)
                    "profile" -> users.doc("sam").value = when (arg) {
                        "none" -> null
                        "h1" -> UserProfile("sam", "Sam", "h1", null, Units.Imperial)
                        "pending" -> UserProfile("sam", "Sam", null, pending, Units.Imperial)
                        else -> UserProfile("sam", "Sam", null, null, Units.Imperial)
                    }
                    "member" -> households.membership("h1", "sam").value =
                        if (arg == "gone") Membership.Gone else Membership.Active(if (arg == "owner") Role.Owner else Role.Member)
                    "request" -> households.ownRequest.value = when (arg) {
                        "none" -> null
                        else -> JoinRequest("sam", "Sam", pending.code, JoinRequestStatus.valueOf(arg!!.replaceFirstChar { it.uppercase() }), null)
                    }
                    "profileError" -> users.fail(arg!!.toInt())
                    "memberError" -> households.fail(arg!!.toInt())
                    "wait" -> advanceTimeBy(arg!!.toLong())
                    "signOut" -> launch { manager.signOut() }
                }
                runCurrent()
                trace += "$step => ${describe(manager.session.value)} | clears=${local.clears} name=${users.doc("sam").value?.displayName} log=${log.joinToString(",")}"
            }
        }
        return trace
    }

    private fun describe(s: Session): String = when (s) {
        Session.Loading -> "loading"
        Session.SignedOut -> "signedOut"
        is Session.NoHousehold -> "noHousehold:" + when (s.join) {
            null -> "none"
            is JoinState.Waiting -> "waiting"
            is JoinState.Declined -> "declined"
            is JoinState.Expired -> "expired"
        }
        is Session.InHousehold -> "inHousehold:${s.householdId}:${s.role.name.lowercase()}"
    }

    private class FakeAuth(private val log: MutableList<String>) : AuthRepository {
        val user = MutableStateFlow<AuthUser?>(null)
        override val authUser: Flow<AuthUser?> = user
        override val currentUser: AuthUser? get() = user.value
        override suspend fun signUp(displayName: String, email: String, password: String) = Unit
        override suspend fun signIn(email: String, password: String) = Unit
        override suspend fun sendPasswordReset(email: String) = Unit
        override suspend fun sendEmailVerification() = Unit
        override suspend fun reload(): AuthUser? = user.value
        override suspend fun verifyPassword(password: String) {
            if (password != "password1") throw AppError.InvalidCredentials()
        }
        override suspend fun reauthenticate(password: String) = verifyPassword(password)
        override fun signOut() {
            log += "signOut"
            user.value = null
        }
        override suspend fun deleteAccount() {
            log += "delete"
            user.value = null
        }
    }

    private class FakeUsers : UserRepository {
        private val docs = mutableMapOf<String, MutableStateFlow<UserProfile?>>()
        private val errors = MutableStateFlow(0)
        private var throwsLeft = 0

        fun doc(uid: String) = docs.getOrPut(uid) { MutableStateFlow(null) }

        fun fail(times: Int) {
            throwsLeft = times
            errors.value += 1
        }

        override fun observeProfile(uid: String): Flow<UserProfile?> = errors.flatMapLatest {
            flow {
                if (throwsLeft > 0) {
                    throwsLeft--
                    throw IllegalStateException("quota")
                }
                emitAll(doc(uid))
            }
        }

        override suspend fun createProfileIfMissing(uid: String, displayName: String) {
            if (doc(uid).value == null) doc(uid).value = UserProfile(uid, displayName, null, null)
        }

        override suspend fun clearHousehold(uid: String) {
            doc(uid).value = doc(uid).value?.copy(householdId = null)
        }

        override suspend fun deleteProfile(uid: String) {
            doc(uid).value = null
        }

        override suspend fun setUnits(uid: String, units: Units) = Unit
    }

    private class FakeHouseholds(private val users: FakeUsers) : HouseholdRepository {
        private val memberships = mutableMapOf<Pair<String, String>, MutableStateFlow<Membership?>>()
        val ownRequest = MutableStateFlow<JoinRequest?>(null)
        private val errors = MutableStateFlow(0)
        private var throwsLeft = 0

        fun membership(hid: String, uid: String) = memberships.getOrPut(hid to uid) { MutableStateFlow(null) }

        fun fail(times: Int) {
            throwsLeft = times
            errors.value += 1
        }

        override fun observeMembership(householdId: String, uid: String): Flow<Membership> = errors.flatMapLatest {
            flow {
                if (throwsLeft > 0) {
                    throwsLeft--
                    throw IllegalStateException("quota")
                }
                emitAll(membership(householdId, uid).filterNotNull())
            }
        }

        override fun observeOwnJoinRequest(householdId: String, uid: String): Flow<JoinRequest?> = ownRequest.map { it }

        override suspend fun completeJoin(uid: String, displayName: String, pending: PendingJoin) {
            users.doc(uid).value = UserProfile(uid, users.doc(uid).value!!.displayName, pending.householdId, null)
            ownRequest.value = null
        }

        override suspend fun abandonJoin(uid: String, pending: PendingJoin) = Unit
        override suspend fun leave(householdId: String, uid: String) = Unit
        override fun observeHousehold(householdId: String): Flow<Household?> = error("unused")
        override fun observeMembers(householdId: String): Flow<List<Member>> = error("unused")
        override fun observeJoinRequests(householdId: String): Flow<List<JoinRequest>> = error("unused")
        override fun observeInviteCode(code: String): Flow<InviteCode?> = error("unused")
        override suspend fun createHousehold(uid: String, displayName: String, name: String, inviteCode: String) = error("unused")
        override suspend fun findInviteCode(code: String): InviteCode? = error("unused")
        override suspend fun requestToJoin(uid: String, displayName: String, invite: InviteCode) = error("unused")
        override suspend fun approve(householdId: String, request: JoinRequest) = error("unused")
        override suspend fun decline(householdId: String, requesterUid: String) = error("unused")
        override suspend fun rename(household: Household, newName: String) = error("unused")
        override suspend fun regenerateInviteCode(household: Household, uid: String, newCode: String, active: Boolean) = error("unused")
        override suspend fun setJoiningPaused(code: String, paused: Boolean) = error("unused")
        override suspend fun removeMember(householdId: String, memberUid: String) = error("unused")
        override suspend fun transferOwnership(householdId: String, fromUid: String, toUid: String) = error("unused")
        override suspend fun deleteHousehold(household: Household, uid: String) = error("unused")
        override suspend fun resetData(householdId: String, actor: com.parcha.spoilnot.core.write.Actor) = error("unused")
    }

    private object NoDevices : DeviceRegistry {
        override suspend fun sync(uid: String) = Unit
        override suspend fun unregister(uid: String) = Unit
    }

    private class FakeLocalData(private val log: MutableList<String>) : LocalData {
        var clears = 0
        override suspend fun clear() {
            clears++
            log += "clear"
        }
    }

    private companion object {
        const val OUT = "/private/tmp/claude-501/-Users-jmalp-Code-personal-spoilnot/23c17d6c-43ec-46c2-bb13-75dfc78faed3/scratchpad/parity/session-kotlin.json"
    }
}
```

`review-parity-session.test.ts`:

```ts
// Review 10 (temporary, removed after the run): the session scripts of the Kotlin ParitySessionTest through the web's
// SessionManager, compared step by step.
import { readFileSync, writeFileSync } from 'node:fs';
import { BehaviorSubject, defer, filter, map, switchMap, throwError, type Observable } from 'rxjs';
import { expect, test, vi } from 'vitest';
import type { AuthRepository, AuthUser, PendingJoin, UserProfile, UserRepository } from './domain/account';
import type { HouseholdRepository, JoinRequest, LocalData, Membership } from './domain/household';
import { SessionManager, type Session } from './domain/session';

const DIR = '/private/tmp/claude-501/-Users-jmalp-Code-personal-spoilnot/23c17d6c-43ec-46c2-bb13-75dfc78faed3/scratchpad/parity';
const kotlin: Record<string, string[]> = JSON.parse(readFileSync(`${DIR}/session-kotlin.json`, 'utf8'));
const pending: PendingJoin = { householdId: 'h1', householdName: 'The Smiths', code: 'ABCDE23456' };

class Users implements UserRepository {
  readonly docs = new Map<string, BehaviorSubject<UserProfile | null>>();
  private readonly errors = new BehaviorSubject(0);
  private throwsLeft = 0;
  doc(uid: string) {
    if (!this.docs.has(uid)) this.docs.set(uid, new BehaviorSubject<UserProfile | null>(null));
    return this.docs.get(uid)!;
  }
  fail(times: number) {
    this.throwsLeft = times;
    this.errors.next(this.errors.value + 1);
  }
  observeProfile = (uid: string): Observable<UserProfile | null> =>
    this.errors.pipe(
      switchMap(() =>
        defer(() => {
          if (this.throwsLeft > 0) {
            this.throwsLeft--;
            return throwError(() => new Error('quota'));
          }
          return this.doc(uid);
        }),
      ),
    );
  createProfileIfMissing = async (uid: string, displayName: string) => {
    if (this.doc(uid).value === null)
      this.doc(uid).next({ uid, displayName, householdId: null, pendingJoin: null, units: 'imperial' });
  };
  clearHousehold = async (uid: string) => {
    const p = this.doc(uid).value;
    this.doc(uid).next(p && { ...p, householdId: null });
  };
  deleteProfile = async (uid: string) => this.doc(uid).next(null);
  setUnits = async () => undefined;
}

class Households {
  readonly memberships = new Map<string, BehaviorSubject<Membership | null>>();
  readonly ownRequest = new BehaviorSubject<JoinRequest | null>(null);
  private readonly errors = new BehaviorSubject(0);
  private throwsLeft = 0;
  constructor(private readonly users: Users) {}
  membership(hid: string, uid: string) {
    const key = `${hid}/${uid}`;
    if (!this.memberships.has(key)) this.memberships.set(key, new BehaviorSubject<Membership | null>(null));
    return this.memberships.get(key)!;
  }
  fail(times: number) {
    this.throwsLeft = times;
    this.errors.next(this.errors.value + 1);
  }
  observeMembership = (hid: string, uid: string): Observable<Membership> =>
    this.errors.pipe(
      switchMap(() =>
        defer(() => {
          if (this.throwsLeft > 0) {
            this.throwsLeft--;
            return throwError(() => new Error('quota'));
          }
          return this.membership(hid, uid).pipe(filter((m): m is Membership => m !== null));
        }),
      ),
    );
  observeOwnJoinRequest = () => this.ownRequest.pipe(map((r) => r));
  completeJoin = async (uid: string, _name: string, p: PendingJoin) => {
    const name = this.users.doc(uid).value!.displayName;
    this.users.doc(uid).next({ uid, displayName: name, householdId: p.householdId, pendingJoin: null, units: 'imperial' });
    this.ownRequest.next(null);
  };
  abandonJoin = async () => undefined;
  leave = async () => undefined;
}

function describe(s: Session): string {
  switch (s.kind) {
    case 'loading':
    case 'signedOut':
      return s.kind;
    case 'noHousehold':
      return `noHousehold:${s.join?.kind ?? 'none'}`;
    case 'inHousehold':
      return `inHousehold:${s.householdId}:${s.role}`;
  }
}

async function run(steps: string[]): Promise<string[]> {
  const trace: string[] = [];
  const log: string[] = [];
  const user = new BehaviorSubject<AuthUser | null>(null);
  const auth = {
    authUser$: user.asObservable(),
    currentUser: () => user.value,
    signOut: async () => {
      log.push('signOut');
      user.next(null);
    },
  } as unknown as AuthRepository;
  const users = new Users();
  const households = new Households(users);
  let clears = 0;
  const local: LocalData = {
    clear: async () => {
      clears++;
      log.push('clear');
    },
  };
  const devices = { sync: async () => undefined, unregister: async () => undefined };
  const manager = new SessionManager(
    auth,
    users,
    households as unknown as HouseholdRepository,
    local,
    devices as never,
  );
  const flush = async () => {
    for (let i = 0; i < 5; i++) await vi.advanceTimersByTimeAsync(0);
  };
  await flush();
  for (const step of steps) {
    const [kind, arg] = step.split(':') as [string, string | undefined];
    switch (kind) {
      case 'signIn':
        user.next({ uid: 'sam', email: 'sam@example.com', displayName: 'Sam', emailVerified: false });
        break;
      case 'signInBlank':
        user.next({ uid: 'sam', email: 'sam.smith@example.com', displayName: '  Sam  ', emailVerified: false });
        break;
      case 'profile':
        users
          .doc('sam')
          .next(
            arg === 'none'
              ? null
              : arg === 'h1'
                ? { uid: 'sam', displayName: 'Sam', householdId: 'h1', pendingJoin: null, units: 'imperial' }
                : arg === 'pending'
                  ? { uid: 'sam', displayName: 'Sam', householdId: null, pendingJoin: pending, units: 'imperial' }
                  : { uid: 'sam', displayName: 'Sam', householdId: null, pendingJoin: null, units: 'imperial' },
          );
        break;
      case 'member':
        households
          .membership('h1', 'sam')
          .next(arg === 'gone' ? { kind: 'gone' } : ({ kind: 'active', role: arg } as Membership));
        break;
      case 'request':
        households.ownRequest.next(
          arg === 'none'
            ? null
            : ({ uid: 'sam', displayName: 'Sam', code: pending.code, status: arg, requestedAt: null } as JoinRequest),
        );
        break;
      case 'profileError':
        users.fail(Number(arg));
        break;
      case 'memberError':
        households.fail(Number(arg));
        break;
      case 'wait':
        await vi.advanceTimersByTimeAsync(Number(arg));
        break;
      case 'signOut':
        void manager.signOut();
        break;
    }
    await flush();
    trace.push(
      `${step} => ${describe(manager.session)} | clears=${clears} name=${users.doc('sam').value?.displayName} log=${log.join(',')}`,
    );
  }
  manager.dispose();
  return trace;
}

test('session scripts match', async () => {
  vi.useFakeTimers();
  const report: Record<string, { step: number; kotlin: string; web: string }[]> = {};
  const traces: Record<string, string[]> = {};
  for (const [name, kotlinTrace] of Object.entries(kotlin)) {
    const steps = kotlinTrace.map((line) => line.split(' => ')[0]!);
    const web = await run(steps);
    traces[name] = web;
    report[name] = web.flatMap((line, i) => (line === kotlinTrace[i] ? [] : [{ step: i, kotlin: kotlinTrace[i]!, web: line }]));
  }
  vi.useRealTimers();
  writeFileSync(`${DIR}/session-mismatches.json`, JSON.stringify({ report, traces }, null, 1));
  expect(Object.keys(report).length).toBe(Object.keys(kotlin).length);
});
```

## Appendix C: mixed-household scripts (scratchpad)

Run with the emulators and `npm run dev -- --port 5173` up. `seed.cjs` makes the two households; `wlib.cjs` holds the
WebKit "phones" (persistent profiles); `droid.py` drives Pixel_8 by label (`dump`, `tap`, `type`, `shot`); the step
scripts wait for a flag file when Pixel_8 has to act in between; `inspect.cjs` dumps every document with its field
types by writer; `future.sh set|check` adds and checks the unknown field.

`seed.cjs`:

```js
// node seed.cjs   Review 10: two households on the emulators (made-up data, password review-pass-10).
// - "mixed": Alex (owner, plays the iPhone in WebKit) and Blake (member, Pixel_8); a default list, a few
//   products, three recipes. Everything else is written by the two apps during the review.
// - "big": Perry alone, 500 purchases over 120 products, for the WebKit scroll measurement.
const { createRequire } = require('node:module');
const req = createRequire('/Users/jmalp/Code/personal/spoilnot/firebase/package.json');
const { initializeTestEnvironment } = req('@firebase/rules-unit-testing');
const { doc, writeBatch, Timestamp } = req('firebase/firestore');
const DAY = 86400000;
const now = Date.now();
const local = (offset) => {
  const t = new Date(now + offset * DAY);
  return `${t.getFullYear()}-${String(t.getMonth() + 1).padStart(2, '0')}-${String(t.getDate()).padStart(2, '0')}`;
};
const ts = (hoursAgo) => Timestamp.fromMillis(now - hoursAgo * 3600000);
const nameKey = (s) => s.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().trim().replace(/\s+/g, ' ');
async function signUp(email, password, displayName) {
  const res = await fetch('http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/accounts:signUp?key=demo', {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ email, password, displayName, returnSecureToken: true }),
  });
  const json = await res.json();
  if (!json.localId) throw new Error(JSON.stringify(json));
  return json.localId;
}
(async () => {
  const alex = await signUp('alex@mixed.test', 'review-pass-10', 'Alex');
  const blake = await signUp('blake@mixed.test', 'review-pass-10', 'Blake');
  const perry = await signUp('perry@big.test', 'review-pass-10', 'Perry');
  const env = await initializeTestEnvironment({ projectId: 'demo-spoilnot', firestore: { host: '127.0.0.1', port: 8080 } });
  const prefs = { enabled: true, digestMinutes: 540, stages: [3, 2, 1, 0], expiredMode: 'once', cooking: true };
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    let batch = writeBatch(db);
    let n = 0;
    const put = async (path, data) => {
      batch.set(doc(db, path), data);
      if (++n % 400 === 0) {
        await batch.commit();
        batch = writeBatch(db);
      }
    };
    await put('config/app', { minSupportedBuild: 1, message: '' });
    const stamp = (uid, h = 48) => ({ createdAt: ts(h), createdBy: uid, updatedAt: ts(h), updatedBy: uid });
    const household = async (hid, name, code, people) => {
      const H = `households/${hid}`;
      for (const [uid, display, role] of people) {
        await put(`users/${uid}`, { displayName: display, householdId: hid, pendingJoin: null, timezone: 'America/New_York', locale: 'en', units: 'imperial', notificationPrefs: prefs, createdAt: ts(500), updatedAt: ts(500), lastDigestDate: null, lastExpiredThrough: null });
        await put(`${H}/members/${uid}`, { role, displayName: display, joinedAt: ts(500) });
      }
      await put(H, { name, ownerUid: people[0][0], inviteCode: code, schemaVersion: 1, createdAt: ts(500), updatedAt: ts(500) });
      await put(`inviteCodes/${code}`, { householdId: hid, householdName: name, active: true, createdBy: people[0][0], createdAt: ts(500) });
      await put(`${H}/groceryLists/default`, { name: 'Groceries', isDefault: true, aisleOrder: [], createdAt: ts(500), updatedAt: ts(500) });
      return H;
    };
    // ---- mixed
    const M = await household('mixed', 'Mixed home', 'MXDKR7M4PQ', [[alex, 'Alex', 'owner'], [blake, 'Blake', 'member']]);
    const P = [
      ['Milk', 'dairy', 'fridge', 'pcs', 1, true, [[2, 5]]],
      ['Yogurt', 'dairy', 'fridge', 'pcs', null, false, [[3, 3]]],
      ['Rice', 'pantry_staples', 'pantry', 'g', null, false, [[500, 200]]],
      ['Spinach', 'produce', 'fridge', 'g', null, false, [[200, 1]]],
      ['Eggs', 'dairy', 'fridge', 'pcs', 6, true, [[8, 12]]],
    ];
    for (let i = 0; i < P.length; i++) {
      const [name, category, loc, unit, min, auto, batches] = P[i];
      await put(`${M}/products/mp${i}`, { name, nameKey: nameKey(name), category, defaultLocation: loc, unit, minQuantity: min, autoAddToGrocery: auto, buyQuantity: null, restockArmed: true, typicalShelfLifeDays: null, recentShelfLifeDays: [], lastAddedQuantity: null, aliases: [], openedShelfLifeDays: null, ...stamp(alex, 100) });
      batches.forEach(async ([qty, off], j) => {
        await put(`${M}/stock/ms${i}_${j}`, { productId: `mp${i}`, productName: name, unit, category, quantity: qty, expiryDate: local(off), dateType: 'use_by', location: loc, note: null, openedOn: null, ...stamp(blake, 90) });
      });
    }
    const R = [
      ['Spinach omelette', 2, ['3 eggs', '1 cup fresh spinach', '2 tbsp milk', 'salt to taste']],
      ['Rice pudding', 4, ['200 g rice', '1 l milk', '80 g sugar']],
      ['Yogurt bowl', 1, ['1 cup yogurt', '2 tbsp honey']],
    ];
    for (let i = 0; i < R.length; i++) {
      const [title, servings, ing] = R[i];
      await put(`${M}/recipes/mr${i}`, { title, sourceUrl: null, sourceName: null, imageUrl: null, description: null, servings, servingsText: null, prepMinutes: null, cookMinutes: null, totalMinutes: 15, ingredients: ing.map((raw) => ({ raw, qty: null, unit: null, name: null, group: null })), steps: [{ text: 'Cook.', group: null }], notes: null, tags: ['quick'], favorite: false, rating: null, importMethod: 'manual', ...stamp(blake, 80) });
    }
    // ---- big
    const B = await household('big', 'Big pantry', 'BGPNT7M4PQ', [[perry, 'Perry', 'owner']]);
    const cats = ['produce', 'dairy', 'meat_fish', 'bakery', 'frozen', 'pantry_staples', 'drinks', 'snacks', 'cooked', 'other'];
    const locs = ['fridge', 'freezer', 'pantry', 'other'];
    for (let i = 0; i < 120; i++) {
      const name = `Item ${i} ${['apples', 'beans', 'cheese', 'dates', 'eggs', 'flour'][i % 6]}`;
      await put(`${B}/products/bp${i}`, { name, nameKey: nameKey(name), category: cats[i % cats.length], defaultLocation: locs[i % 4], unit: 'pcs', minQuantity: i % 15 === 0 ? 2 : null, autoAddToGrocery: i % 15 === 0, buyQuantity: null, restockArmed: true, typicalShelfLifeDays: null, recentShelfLifeDays: [], lastAddedQuantity: null, aliases: [], openedShelfLifeDays: null, ...stamp(perry, 300) });
    }
    for (let s = 0; s < 500; s++) {
      const i = (s * 7) % 120;
      const name = `Item ${i} ${['apples', 'beans', 'cheese', 'dates', 'eggs', 'flour'][i % 6]}`;
      await put(`${B}/stock/bs${s}`, { productId: `bp${i}`, productName: name, unit: 'pcs', category: cats[i % cats.length], quantity: 1 + (s % 4), expiryDate: s % 11 === 0 ? null : local((s % 60) - 5), dateType: 'use_by', location: locs[s % 4], note: null, openedOn: null, ...stamp(perry, 1 + (s % 200)) });
    }
    await batch.commit();
  });
  await env.cleanup();
  console.log(JSON.stringify({ alex, blake, perry }));
})();
```

`wlib.cjs`:

```js
// Review 10: helpers for the WebKit "phones". Each phone is a persistent WebKit profile in the scratchpad, so its
// sign-in, IndexedDB cache and localStorage survive between the step scripts (like a Home Screen app between opens).
const { createRequire } = require('node:module');
const req = createRequire('/Users/jmalp/Code/personal/spoilnot/web/package.json');
const { webkit, devices, expect } = req('@playwright/test');
const ROOT = '/private/tmp/claude-501/-Users-jmalp-Code-personal-spoilnot/23c17d6c-43ec-46c2-bb13-75dfc78faed3/scratchpad';
const SHOTS = `${ROOT}/shots`;
require('node:fs').mkdirSync(SHOTS, { recursive: true });

async function phone(name, { device = 'iPhone 15', dark = false, textScale = 1, url = 'http://localhost:5173/', offline = false, viewport } = {}) {
  const d = devices[device];
  const ctx = await webkit.launchPersistentContext(`${ROOT}/profiles/${name}`, {
    viewport: viewport ?? d.viewport,
    userAgent: d.userAgent,
    deviceScaleFactor: d.deviceScaleFactor,
    isMobile: d.isMobile,
    hasTouch: d.hasTouch,
    colorScheme: dark ? 'dark' : 'light',
  });
  const page = ctx.pages()[0] ?? (await ctx.newPage());
  const log = [];
  page.on('console', (m) => log.push(`${m.type()}: ${m.text()}`.slice(0, 400)));
  page.on('pageerror', (e) => log.push(`pageerror: ${e.message}`.slice(0, 400)));
  await page.addInitScript(
    ([scale]) => {
      localStorage.setItem('spoilnot.textScale', scale);
      if (localStorage.getItem('spoilnot.installHelpDismissed') === null) localStorage.setItem('spoilnot.installHelpDismissed', 'true');
    },
    [String(textScale)],
  );
  if (offline) await ctx.setOffline(true);
  if (url) await page.goto(url);
  return { ctx, page, log };
}

async function signIn(page, email, password = 'review-pass-10') {
  await page.getByRole('button', { name: 'Sign in' }).click();
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page.getByRole('heading', { name: 'Home', exact: true })).toBeVisible({ timeout: 20000 });
}

const tab = (page, name) => page.getByRole('navigation').getByRole('button', { name }).click();
const shot = (page, file, opts = {}) => page.screenshot({ path: `${SHOTS}/${file}.png`, ...opts });
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
/** Waits until the file exists (a step done on Pixel_8 by hand), at most [ms]. */
async function waitFor(file, ms = 600000) {
  const fs = require('node:fs');
  const end = Date.now() + ms;
  while (Date.now() < end) {
    if (fs.existsSync(`${ROOT}/live/${file}`)) return true;
    await sleep(250);
  }
  return false;
}
module.exports = { phone, signIn, tab, shot, sleep, waitFor, expect, devices, webkit, ROOT, SHOTS };
```

`droid.py`:

```python
#!/usr/bin/env python3 -I
"""Review 10: drive the dev app on Pixel_8 by label.

droid.py dump                     texts and descriptions on screen with their centers
droid.py tap "<label>" [n]        taps the n-th node whose text or content-desc equals the label (or contains it with ~)
droid.py type "<text>"            types into the focused field, one character at a time
droid.py key <code>               input keyevent
droid.py shot <name>              screenshot to the scratchpad shots folder
droid.py swipe x1 y1 x2 y2 [ms]
"""
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

ADB = '/Users/jmalp/Library/Android/sdk/platform-tools/adb'
SHOTS = '/private/tmp/claude-501/-Users-jmalp-Code-personal-spoilnot/23c17d6c-43ec-46c2-bb13-75dfc78faed3/scratchpad/shots'


def sh(*args, check=True):
    return subprocess.run([ADB, *args], capture_output=True, text=True, check=check).stdout


def nodes():
    for _ in range(3):
        raw = subprocess.run([ADB, 'exec-out', 'uiautomator', 'dump', '/dev/tty'], capture_output=True, text=True).stdout
        start = raw.find('<?xml')
        end = raw.rfind('</hierarchy>')
        if start >= 0 and end > 0:
            root = ET.fromstring(raw[start:end + len('</hierarchy>')])
            out = []
            for n in root.iter('node'):
                b = re.findall(r'\d+', n.get('bounds', ''))
                if len(b) != 4:
                    continue
                x1, y1, x2, y2 = map(int, b)
                out.append({
                    'text': n.get('text', ''), 'desc': n.get('content-desc', ''), 'id': n.get('resource-id', ''),
                    'cls': n.get('class', '').split('.')[-1], 'click': n.get('clickable') == 'true',
                    'checked': n.get('checked') == 'true', 'focused': n.get('focused') == 'true',
                    'x': (x1 + x2) // 2, 'y': (y1 + y2) // 2, 'bounds': (x1, y1, x2, y2),
                })
            return out
        time.sleep(1)
    raise SystemExit('dump failed')


def label(n):
    return n['text'] or n['desc']


def find(lbl, idx=0):
    contains = lbl.startswith('~')
    key = lbl[1:] if contains else lbl
    hits = [n for n in nodes() if (key in label(n) if contains else (n['text'] == key or n['desc'] == key or n['id'].endswith('/' + key) or n['id'] == key))]
    if len(hits) <= idx:
        raise SystemExit(f'not found: {lbl}')
    return hits[idx]


def main():
    cmd, *rest = sys.argv[1:]
    if cmd == 'dump':
        for n in nodes():
            if label(n) or n['click']:
                flags = ''.join(f for f, on in (('C', n['click']), ('X', n['checked']), ('F', n['focused'])) if on)
                print(f"{n['x']:4},{n['y']:4} {n['cls'][:12]:12} {flags:3} {label(n)!r}{(' id=' + n['id']) if n['id'] else ''}")
    elif cmd == 'tap':
        n = find(rest[0], int(rest[1]) if len(rest) > 1 else 0)
        sh('shell', 'input', 'tap', str(n['x']), str(n['y']))
        print(f"tapped {label(n)!r} at {n['x']},{n['y']}")
    elif cmd == 'type':
        text = rest[0]
        script = []
        for c in text:
            if c == ' ':
                script.append('input keyevent 62')
            elif c == '@':
                script.append('input keyevent 77')
            elif c == '.':
                script.append('input keyevent 56')
            elif c == '-':
                script.append('input keyevent 69')
            else:
                script.append(f"input text '{c}'")
        sh('shell', '; '.join(script))
        print(f'typed {text!r}')
    elif cmd == 'key':
        sh('shell', 'input', 'keyevent', *rest)
    elif cmd == 'shot':
        data = subprocess.run([ADB, 'exec-out', 'screencap', '-p'], capture_output=True).stdout
        open(f'{SHOTS}/{rest[0]}.png', 'wb').write(data)
        print(f'{SHOTS}/{rest[0]}.png')
    elif cmd == 'swipe':
        sh('shell', 'input', 'swipe', *rest)
    else:
        raise SystemExit(__doc__)


main()
```

`w07-p05.cjs`:

```js
// node w07-p05.cjs: SYNC-03 (p05) with the web as Alice. Opens the Edit window of Eggs, signals p05-open, waits for
// p05-go (Blake used some on Pixel_8), then changes only the date and saves. Prints what the form and the list showed.
const fs = require('node:fs');
const { phone, tab, shot, sleep, waitFor, expect, ROOT } = require('./wlib.cjs');
const local = (offset) => {
  const t = new Date(Date.now() + offset * 86400000);
  return `${t.getFullYear()}-${String(t.getMonth() + 1).padStart(2, '0')}-${String(t.getDate()).padStart(2, '0')}`;
};
(async () => {
  for (const f of ['p05-open', 'p05-go']) fs.rmSync(`${ROOT}/live/${f}`, { force: true });
  const { ctx, page } = await phone('alex');
  await expect(page.getByRole('heading', { name: 'Home', exact: true })).toBeVisible({ timeout: 20000 });
  await tab(page, 'Pantry');
  const eggs = page.getByTestId('pantry_list').locator('[data-testid^="pantry_item_"]').filter({ hasText: 'Eggs' });
  await expect(eggs).toBeVisible({ timeout: 20000 });
  const before = await eggs.innerText();
  await eggs.getByRole('button', { name: /Eggs/ }).first().click();
  const edit = page.getByTestId('sheet_edit_item');
  const formBefore = await edit.getByTestId('field_quantity').inputValue();
  fs.writeFileSync(`${ROOT}/live/p05-open`, '1');
  await waitFor('p05-go');
  await sleep(1500);
  const formAfter = await edit.getByTestId('field_quantity').inputValue();
  const listBehindSheet = await eggs.innerText().catch(() => '?');
  await edit.getByTestId(`date_chip_${local(14)}`).click().catch(async () => {
    await edit.locator('[data-testid^="date_chip_"]').last().click();
  });
  await shot(page, 'p05-web-form');
  await edit.getByTestId('button_save_item').click();
  await sleep(2500);
  const after = await eggs.innerText();
  console.log(JSON.stringify({ before, formBefore, formAfter, listBehindSheet, after }, null, 1));
  await ctx.close();
})();
```

`w08-p02.cjs`:

```js
// node w08-p02.cjs: SYNC-02 (p02/p03) and REL-01 with the web as Alice. Offline, presses Add to pantry on the cart,
// records what the list shows (REL-01), signals p02-web-done, waits for p02-go (Blake pressed it on Pixel_8), then
// reconnects and prints the pantry.
const fs = require('node:fs');
const { phone, tab, shot, sleep, waitFor, expect, ROOT } = require('./wlib.cjs');
(async () => {
  for (const f of ['p02-web-done', 'p02-go']) fs.rmSync(`${ROOT}/live/${f}`, { force: true });
  const { ctx, page } = await phone('alex');
  await expect(page.getByRole('heading', { name: 'Home', exact: true })).toBeVisible({ timeout: 20000 });
  await tab(page, 'Groceries');
  await expect(page.getByTestId('cart_header')).toBeVisible({ timeout: 20000 });
  const cartBefore = await page.getByTestId('grocery_list').innerText();
  await ctx.setOffline(true);
  await sleep(1500);
  const banner = await page.getByText(/Offline/).allInnerTexts();
  await shot(page, 'p02-web-offline-before');
  await page.getByTestId('fab_add_to_pantry').click();
  const sheet = page.getByTestId('sheet_add_to_pantry');
  const sheetText = await sheet.innerText();
  await sheet.getByTestId('button_confirm_add_to_pantry').click();
  await sleep(3000);
  const statuses = await page.getByRole('status').allInnerTexts();
  const cartAfter = await page.getByTestId('grocery_list').innerText().catch(() => '');
  const fabStill = await page.getByTestId('fab_add_to_pantry').isVisible().catch(() => false);
  await shot(page, 'p02-web-offline-after');
  fs.writeFileSync(`${ROOT}/live/p02-web-done`, '1');
  await waitFor('p02-go');
  await ctx.setOffline(false);
  await sleep(8000);
  await tab(page, 'Pantry');
  await sleep(2000);
  const pantry = await page.getByTestId('pantry_list').innerText();
  await shot(page, 'p02-web-pantry-after');
  console.log(JSON.stringify({ cartBefore, banner, sheetText, statuses, cartAfter, fabStill, pantry }, null, 1));
  await ctx.close();
})();
```

`inspect.cjs`:

```js
// node inspect.cjs [household]: every document of the household and its people as the server has it (emulator admin
// read), grouped by collection and by which app wrote it last (updatedBy/createdBy/actorUid), with each field's type.
const FS = 'http://127.0.0.1:8080/v1/projects/demo-spoilnot/databases/(default)/documents';
const H = { Authorization: 'Bearer owner' };
const uids = require('./uids.json');
const who = { [uids.alex]: 'web', [uids.blake]: 'android', [uids.perry]: 'seed' };
const type = (v) => Object.keys(v)[0].replace('Value', '');
async function list(path) {
  const out = [];
  let token = '';
  do {
    const r = await (await fetch(`${FS}/${path}?pageSize=300${token ? `&pageToken=${token}` : ''}`, { headers: H })).json();
    out.push(...(r.documents ?? []));
    token = r.nextPageToken ?? '';
  } while (token);
  return out;
}
async function collections(path) {
  const r = await (await fetch(`${FS}/${path}:listCollectionIds`, { method: 'POST', headers: { ...H, 'content-type': 'application/json' }, body: '{}' })).json();
  return r.collectionIds ?? [];
}
(async () => {
  const hid = process.argv[2] ?? 'mixed';
  const docs = [];
  const walk = async (path) => {
    for (const c of await collections(path)) {
      for (const d of await list(`${path}/${c}`)) {
        const rel = d.name.split('/documents/')[1];
        docs.push({ path: rel, coll: rel.split('/').filter((_, i) => i % 2 === 0).slice(-1)[0], fields: d.fields ?? {} });
        await walk(rel);
      }
    }
  };
  const top = await (await fetch(`${FS}/households/${hid}`, { headers: H })).json();
  docs.push({ path: `households/${hid}`, coll: 'households', fields: top.fields ?? {} });
  await walk(`households/${hid}`);
  for (const uid of [uids.alex, uids.blake]) {
    const u = await (await fetch(`${FS}/users/${uid}`, { headers: H })).json();
    docs.push({ path: `users/${uid}`, coll: 'users', fields: u.fields ?? {}, author: who[uid] });
    await walk(`users/${uid}`);
  }
  const groups = {};
  for (const d of docs) {
    const f = d.fields;
    const by = d.author ?? who[f.updatedBy?.stringValue] ?? who[f.createdBy?.stringValue] ?? who[f.actorUid?.stringValue] ?? (d.path.includes(`/devices/`) ? (d.fields.platform?.stringValue ?? '?') : 'seed');
    const key = `${d.coll} by ${by}`;
    const g = (groups[key] ??= { count: 0, fields: {} });
    g.count++;
    for (const [k, v] of Object.entries(f)) (g.fields[k] ??= new Set()).add(type(v));
  }
  const out = Object.fromEntries(
    Object.entries(groups)
      .sort()
      .map(([k, g]) => [k, { count: g.count, fields: Object.fromEntries(Object.entries(g.fields).sort().map(([f, t]) => [f, [...t].sort().join('|')])) }]),
  );
  if (process.argv[3] === 'raw') console.log(JSON.stringify(docs.map((d) => ({ path: d.path, f: Object.fromEntries(Object.entries(d.fields).map(([k, v]) => [k, Object.values(v)[0]])) })), null, 1));
  else console.log(JSON.stringify(out, null, 1));
})();
```

`future.sh`:

```bash
#!/bin/bash
# future.sh set|check : adds futureField to the review's test documents (emulator admin), or shows which still have it.
FS='http://127.0.0.1:8080/v1/projects/demo-spoilnot/databases/(default)/documents'
H=households/mixed
DOCS="$H/stock/OG0vIYn2htGIHnbQLZoI $H/stock/35aIbIiftNvpjzSbfnZu $H/groceryLists/default/items/v8DHRLPcdoZaRnUr5tLp $H/groceryLists/default/items/EqZrSVutdqPk0B1FA7MP $H/meals/ItARlrzzFyZNyO8RioKe $H/meals/qBTO901Hjcs4fDia5t5L $H/recipes/mr1 $H/recipes/mr2 $H/products/kSeJ6g88FeVD69ynEew7 $H/products/AgtKP7OvpFt71VdLLn0S $H users/RBzYdKNc4Dh6S1gIU06Cpq57QP6V users/a6MHdsjBYQtvFV2hcjbnbqLlUMQ9 $H/groceryLists/default"
for d in $DOCS; do
  if [ "$1" = set ]; then
    curl -s -X PATCH -H 'Authorization: Bearer owner' -H 'content-type: application/json' "$FS/$d?updateMask.fieldPaths=futureField" -d '{"fields":{"futureField":{"stringValue":"from a newer app"}}}' >/dev/null && echo "set $d"
  else
    r=$(curl -s -H 'Authorization: Bearer owner' "$FS/$d")
    echo "$d future=$(echo "$r" | python3 -c 'import json,sys; d=json.load(sys.stdin); f=d.get("fields",{}); print("KEPT" if "futureField" in f else ("GONE" if f else "NO-DOC"), "deleted" if "deletedAt" in f else "", f.get("name",f.get("title",f.get("productName",{}))).get("stringValue",""))')"
  fi
done
```

Added on 2026-11-08: `seed-bad.cjs` (household "Bad data"), `w09-bad.cjs`/`w10-bad2.cjs`/`w11-reload.cjs` (WEB-10, WEB-12),
`seed-big-items.cjs` and `perf.cjs` (WEB-11, WEB-13), `loadperf.cjs` (the Lighthouse stand-in), `iphone.cjs` (layout and input
sizes) and `w12-p01a.cjs`/`w12-p01b.cjs` (WEB-16; Pixel_8 by hand in between).

`seed-bad.cjs`:

```js
// node seed-bad.cjs: household "bad" (Bea) with good documents and one wrong-typed field per bad document (REL-04 on
// the web). Made-up data on the emulator only.
const { createRequire } = require('node:module');
const req = createRequire('/Users/jmalp/Code/personal/spoilnot/firebase/package.json');
const { initializeTestEnvironment } = req('@firebase/rules-unit-testing');
const { doc, writeBatch, Timestamp } = req('firebase/firestore');
const now = Date.now();
const local = (o) => {
  const t = new Date(now + o * 86400000);
  return `${t.getFullYear()}-${String(t.getMonth() + 1).padStart(2, '0')}-${String(t.getDate()).padStart(2, '0')}`;
};
const ts = (h) => Timestamp.fromMillis(now - h * 3600000);
(async () => {
  const res = await fetch('http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/accounts:signUp?key=demo', {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ email: 'bea2@bad.test', password: 'review-pass-10', displayName: 'Bea', returnSecureToken: true }),
  });
  const bea = (await res.json()).localId;
  const env = await initializeTestEnvironment({ projectId: 'demo-spoilnot', firestore: { host: '127.0.0.1', port: 8080 } });
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    const b = writeBatch(db);
    const H = 'households/bad';
    const st = { createdAt: ts(5), createdBy: bea, updatedAt: ts(5), updatedBy: bea };
    const prefs = { enabled: true, digestMinutes: 540, stages: [3, 2, 1, 0], expiredMode: 'once', cooking: true };
    b.set(doc(db, `users/${bea}`), { displayName: 'Bea', householdId: 'bad', pendingJoin: null, timezone: 'America/New_York', locale: 'en', units: 'imperial', notificationPrefs: prefs, createdAt: ts(9), updatedAt: ts(9), lastDigestDate: null, lastExpiredThrough: null });
    b.set(doc(db, `${H}/members/${bea}`), { role: 'owner', displayName: 'Bea', joinedAt: ts(9) });
    b.set(doc(db, H), { name: 'Bad data', ownerUid: bea, inviteCode: 'BDDTK7M4PQ', schemaVersion: 1, createdAt: ts(9), updatedAt: ts(9) });
    b.set(doc(db, `${H}/groceryLists/default`), { name: 'Groceries', isDefault: true, aisleOrder: [], createdAt: ts(9), updatedAt: ts(9) });
    const product = (id, name, extra = {}) => b.set(doc(db, `${H}/products/${id}`), { name, nameKey: String(name).toLowerCase(), category: 'dairy', defaultLocation: 'fridge', unit: 'pcs', minQuantity: null, autoAddToGrocery: false, buyQuantity: null, restockArmed: true, typicalShelfLifeDays: null, recentShelfLifeDays: [], lastAddedQuantity: null, aliases: [], openedShelfLifeDays: null, ...st, ...extra });
    const batch = (id, pid, name, extra = {}) => b.set(doc(db, `${H}/stock/${id}`), { productId: pid, productName: name, unit: 'pcs', category: 'dairy', quantity: 2, expiryDate: local(2), dateType: 'use_by', location: 'fridge', note: null, openedOn: null, ...st, ...extra });
    product('good', 'Good milk');
    batch('good1', 'good', 'Good milk');
    product('bad_name', 42);
    batch('bad_qty', 'good', 'Good milk', { quantity: 'two' });
    product('bad_array', 'Array cheese', { recentShelfLifeDays: '7' });
    batch('bad_date', 'good', 'Good milk', { expiryDate: 20261109 });
    const item = (id, name, extra = {}) => b.set(doc(db, `${H}/groceryLists/default/items/${id}`), { name, nameKey: String(name).toLowerCase(), quantity: null, unit: null, note: null, category: 'produce', checked: false, checkedBy: null, checkedAt: null, source: 'manual', productId: null, recipeId: null, aliasOf: null, ...st, ...extra });
    item('ok', 'Apples');
    item('bad_checked', 'Pears', { checked: 'yes' });
    b.set(doc(db, `${H}/meals/ok`), { date: local(0), slot: 'dinner', position: 0, recipeId: null, title: 'Soup', servings: null, cooks: [], note: null, cookedAt: null, cookedBy: null, ...st });
    b.set(doc(db, `${H}/meals/bad_date`), { date: 20261107, slot: 'lunch', position: 0, recipeId: null, title: 'Salad', servings: null, cooks: [], note: null, cookedAt: null, cookedBy: null, ...st });
    b.set(doc(db, `${H}/recipes/bad_ing`), { title: 'Odd recipe', sourceUrl: null, sourceName: null, imageUrl: null, description: null, servings: 2, servingsText: null, prepMinutes: null, cookMinutes: null, totalMinutes: null, ingredients: 'two eggs', steps: [], notes: null, tags: [], favorite: false, rating: null, importMethod: 'manual', ...st });
    b.set(doc(db, `${H}/recipes/ok`), { title: 'Good recipe', sourceUrl: null, sourceName: null, imageUrl: null, description: null, servings: 2, servingsText: null, prepMinutes: null, cookMinutes: null, totalMinutes: 10, ingredients: [{ raw: '2 eggs', qty: null, unit: null, name: null, group: null }], steps: [{ text: 'Cook.', group: null }], notes: null, tags: [], favorite: false, rating: null, importMethod: 'manual', ...st });
    await b.commit();
  });
  await env.cleanup();
  console.log(bea);
})();
```

`w10-bad2.cjs`:

```js
// node w10-bad2.cjs: fixes the one wrong-typed array (with a new updatedAt) while Pantry spins, to see whether the web
// recovers by itself; then Export with the network off (REL-12).
const fs = require('node:fs');
const { phone, tab, shot, sleep, expect, ROOT } = require('./wlib.cjs');
const FS = 'http://127.0.0.1:8080/v1/projects/demo-spoilnot/databases/(default)/documents';
(async () => {
  const { ctx, page } = await phone('bea');
  await expect(page.getByRole('heading', { name: 'Home', exact: true })).toBeVisible({ timeout: 20000 });
  await tab(page, 'Pantry');
  await sleep(4000);
  const before = (await page.locator('main').innerText()).slice(0, 200);
  const mask = ['recentShelfLifeDays', 'updatedAt'].map((f) => `updateMask.fieldPaths=${f}`).join('&');
  const r = await fetch(`${FS}/households/bad/products/bad_array?${mask}`, {
    method: 'PATCH',
    headers: { Authorization: 'Bearer owner', 'content-type': 'application/json' },
    body: JSON.stringify({ fields: { recentShelfLifeDays: { arrayValue: { values: [] } }, updatedAt: { timestampValue: new Date().toISOString() } } }),
  });
  const t0 = Date.now();
  let after = '';
  while (Date.now() - t0 < 20000) {
    after = (await page.locator('main').innerText()).slice(0, 400);
    if (after.trim()) break;
    await sleep(500);
  }
  const recoveredAfterMs = after.trim() ? Date.now() - t0 : null;
  await shot(page, 'bad-pantry-recovered');
  await ctx.setOffline(true);
  await page.getByRole('button', { name: /settings/i }).first().click();
  await sleep(800);
  const dl = page.waitForEvent('download', { timeout: 20000 }).catch((e) => `no download: ${String(e.message).split('\n')[0]}`);
  await page.getByRole('button', { name: 'Export', exact: true }).click();
  const d = await dl;
  let exp;
  if (typeof d === 'string') exp = d;
  else {
    const p = `${ROOT}/live/export-offline.json`;
    await d.saveAs(p);
    const json = JSON.parse(fs.readFileSync(p, 'utf8'));
    exp = { file: d.suggestedFilename(), keys: Object.keys(json), sizes: Object.fromEntries(Object.entries(json).filter(([, v]) => Array.isArray(v) || (v && typeof v === 'object')).map(([k, v]) => [k, Array.isArray(v) ? v.length : Object.keys(v).length])) };
  }
  await sleep(1500);
  const statuses = await page.getByRole('status').allInnerTexts();
  await shot(page, 'bad-export-offline2');
  console.log(JSON.stringify({ patched: r.status, before, recoveredAfterMs, after, export: exp, statuses }, null, 1));
  await ctx.close();
})();
```

`seed-big-items.cjs`:

```js
// node seed-big-items.cjs: 40 grocery items and six months of Insights for Big pantry (made-up data).
const { createRequire } = require('node:module');
const req = createRequire('/Users/jmalp/Code/personal/spoilnot/firebase/package.json');
const { initializeTestEnvironment } = req('@firebase/rules-unit-testing');
const { doc, writeBatch, Timestamp } = req('firebase/firestore');
const { perry } = require('./uids.json');
(async () => {
  const env = await initializeTestEnvironment({ projectId: 'demo-spoilnot', firestore: { host: '127.0.0.1', port: 8080 } });
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    const b = writeBatch(db);
    const cats = ['produce', 'dairy', 'meat_fish', 'bakery', 'frozen', 'pantry_staples', 'drinks', 'snacks', 'other'];
    for (let i = 0; i < 40; i++) {
      const t = Timestamp.fromMillis(Date.now() - (40 - i) * 60000);
      b.set(doc(db, `households/big/groceryLists/default/items/g${i}`), { name: `Thing ${i}`, nameKey: `thing ${i}`, quantity: i % 3 ? 2 : null, unit: i % 3 ? 'pcs' : null, note: null, category: cats[i % cats.length], checked: i % 7 === 0, checkedBy: i % 7 === 0 ? perry : null, checkedAt: null, source: 'manual', productId: null, recipeId: null, aliasOf: null, createdAt: t, createdBy: perry, updatedAt: t, updatedBy: perry });
    }
    const now = new Date();
    for (let m = 0; m < 6; m++) {
      const d = new Date(now.getFullYear(), now.getMonth() - m, 1);
      const key = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
      b.set(doc(db, `households/big/stats/${key}`), { used: 10 + m * 3, expired: 2 + (m % 3), spoiled: m % 2, didnt_like: 1, other: 0, thrownByDay: { 3: 2 }, updatedAt: Timestamp.now(), updatedBy: perry });
    }
    await b.commit();
  });
  await env.cleanup();
  console.log('ok');
})();
```

`perf.cjs`:

```js
// node perf.cjs: Big pantry (500 purchases) on the web in WebKit (dev build, so React keeps component names).
// - Re-renders per component for one remote change on Groceries and Pantry, and while Insights' charts grow, counted
//   with a stand-in React DevTools hook (fibers that rendered since the previous commit).
// - Frame intervals while scrolling Pantry; delay from a tap to the row menu.
// - Whether a remote change moves the rows under a scroll in progress (DESIGN 6.4), on Pantry and Groceries.
const { phone, signIn, tab, shot, sleep, expect } = require('./wlib.cjs');
const FS = 'http://127.0.0.1:8080/v1/projects/demo-spoilnot/databases/(default)/documents';
const H = { Authorization: 'Bearer owner', 'content-type': 'application/json' };
const local = (o) => {
  const t = new Date(Date.now() + o * 86400000);
  return `${t.getFullYear()}-${String(t.getMonth() + 1).padStart(2, '0')}-${String(t.getDate()).padStart(2, '0')}`;
};
const v = (x) =>
  x === null ? { nullValue: null } : typeof x === 'number' ? (Number.isInteger(x) ? { integerValue: String(x) } : { doubleValue: x })
  : typeof x === 'boolean' ? { booleanValue: x } : x instanceof Date ? { timestampValue: x.toISOString() } : { stringValue: x };
async function patch(path, fields) {
  const mask = Object.keys(fields).map((f) => `updateMask.fieldPaths=${f}`).join('&');
  const r = await fetch(`${FS}/${path}?${mask}`, { method: 'PATCH', headers: H, body: JSON.stringify({ fields: Object.fromEntries(Object.entries(fields).map(([k, x]) => [k, v(x)])) }) });
  if (!r.ok) throw new Error(`${path} ${r.status}`);
}
const HOOK = () => {
  const counts = {};
  let commits = 0;
  let last = performance.now();
  const name = (f) => (typeof f.type === 'function' ? f.type.displayName || f.type.name : f.type && (f.type.displayName || (f.type.render && f.type.render.name) || (f.type.type && f.type.type.name))) || null;
  window.__RENDERS__ = { counts, get commits() { return commits; }, reset() { for (const k of Object.keys(counts)) delete counts[k]; commits = 0; } };
  window.__REACT_DEVTOOLS_GLOBAL_HOOK__ = {
    isDisabled: false, supportsFiber: true, renderers: new Map(),
    inject(r) { const id = this.renderers.size + 1; this.renderers.set(id, r); return id; },
    checkDCE() {}, onScheduleFiberRoot() {}, onCommitFiberUnmount() {}, onPostCommitFiberRoot() {},
    onCommitFiberRoot(_id, root) {
      commits++;
      const since = last;
      const stack = [root.current];
      while (stack.length) {
        const f = stack.pop();
        if ([0, 1, 11, 14, 15].includes(f.tag) && (f.flags & 1) === 1 && f.actualStartTime >= since) {
          const n = name(f);
          if (n) counts[n] = (counts[n] || 0) + 1;
        }
        if (f.sibling) stack.push(f.sibling);
        if (f.child) stack.push(f.child);
      }
      last = performance.now();
    },
  };
};
(async () => {
  const { ctx, page } = await phone('perry');
  await page.addInitScript(HOOK);
  await page.reload();
  const signedIn = await page.getByRole('heading', { name: 'Home', exact: true }).waitFor({ timeout: 15000 }).then(() => true, () => false);
  if (!signedIn) await signIn(page, 'perry@big.test');
  await sleep(3000);
  const out = {};
  const renders = async () => page.evaluate(() => ({ commits: window.__RENDERS__.commits, counts: { ...window.__RENDERS__.counts } }));
  const reset = () => page.evaluate(() => window.__RENDERS__.reset());
  const top = (r, n = 8) => ({ commits: r.commits, top: Object.entries(r.counts).sort((a, b) => b[1] - a[1]).slice(0, n) });

  // Groceries: one remote check-off.
  await tab(page, 'Groceries');
  await expect(page.getByTestId('grocery_list')).toBeVisible({ timeout: 20000 });
  await sleep(2000);
  out.groceryRows = await page.locator('[data-testid^="grocery_item_"]').count();
  await reset();
  const tg = Date.now();
  await patch('households/big/groceryLists/default/items/g10', { checked: true, checkedBy: 'someone-else', updatedAt: new Date() });
  await page.getByTestId('grocery_list').locator('[data-testid="grocery_item_g10"] input:checked, [data-testid="grocery_item_g10"] [aria-checked="true"]').first().waitFor({ timeout: 20000 }).catch(() => undefined);
  out.groceriesRemoteMs = Date.now() - tg;
  await sleep(1000);
  out.groceriesRemoteCheck = top(await renders());

  // Pantry: one remote +1.
  await tab(page, 'Pantry');
  await expect(page.getByTestId('pantry_list')).toBeVisible({ timeout: 30000 });
  await sleep(3000);
  out.pantryRows = await page.locator('[data-testid^="pantry_item_"]').count();
  await reset();
  const tp = Date.now();
  await patch('households/big/stock/bs100', { quantity: 12, updatedAt: new Date() });
  await page.getByTestId('pantry_item_bs100').getByText('× 12').waitFor({ timeout: 20000 }).catch(() => undefined);
  out.pantryRemoteMs = Date.now() - tp;
  await sleep(1000);
  out.pantryRemotePlusOne = top(await renders());

  // Scroll: frame intervals while the list scrolls (wheel steps), WebKit headless on the Mac: relative only.
  // Mobile WebKit has no wheel or touch moves in Playwright: the list is scrolled from the page, 40 px a frame.
  await page.getByTestId('pantry_list').evaluate((list) => new Promise((resolve) => {
    window.__frames = [];
    let prev = performance.now();
    let n = 0;
    const loop = (t) => {
      window.__frames.push(t - prev);
      prev = t;
      list.scrollTop += n < 150 ? 40 : -40;
      if (++n < 300) requestAnimationFrame(loop); else resolve();
    };
    requestAnimationFrame(loop);
  }));
  const frames = await page.evaluate(() => window.__frames.slice(1));
  const sorted = [...frames].sort((a, b) => a - b);
  const pct = (p) => +sorted[Math.min(sorted.length - 1, Math.floor((p / 100) * sorted.length))].toFixed(1);
  out.pantryScroll = { frames: frames.length, p50: pct(50), p90: pct(90), p99: pct(99), max: +sorted[sorted.length - 1].toFixed(1), over25ms: +((100 * frames.filter((f) => f > 25).length) / frames.length).toFixed(1) };

  // Tap to menu: pointerdown on a row's ⋮ until the menu is in the DOM.
  const delays = [];
  for (let i = 0; i < 5; i++) {
    const btn = page.getByTestId('pantry_list').locator('[data-testid^="pantry_item_"]').nth(3 + i).getByRole('button', { name: /Options/ });
    await page.evaluate(() => {
      window.__tap = new Promise((resolve) => {
        let t0 = 0;
        document.addEventListener('pointerdown', () => { t0 = performance.now(); }, { once: true, capture: true });
        const mo = new MutationObserver(() => { if (document.querySelector('[role="menu"]')) { mo.disconnect(); requestAnimationFrame(() => resolve(performance.now() - t0)); } });
        mo.observe(document.body, { childList: true, subtree: true, attributes: true });
      });
    });
    await btn.click();
    delays.push(+(await page.evaluate(() => window.__tap)).toFixed(1));
    await page.keyboard.press('Escape');
    await sleep(400);
  }
  out.tapToMenuMs = delays;

  // A remote change during a scroll (DESIGN 6.4): mid-list, a slow wheel scroll in progress, then a purchase that
  // sorts above the visible rows arrives.
  const probe = async (testId, rowSel, change) => {
    const el = page.getByTestId(testId);
    const b = await el.boundingBox();
    await el.evaluate((n) => { n.scrollTop = 1200; });
    await sleep(800);
    const row = await page.evaluate(([x, y, sel]) => document.elementFromPoint(x, y)?.closest(sel)?.getAttribute('data-testid') ?? null, [b.x + b.width / 2, b.y + b.height / 2, rowSel]);
    const pos = () => page.evaluate(([id, list]) => ({ top: document.querySelector(`[data-testid="${id}"]`)?.getBoundingClientRect().top ?? null, scroll: document.querySelector(`[data-testid="${list}"]`).scrollTop }), [row, testId]);
    const start = await pos();
    const scrolling = el.evaluate((n) => new Promise((resolve) => { let k = 0; const step = () => { n.scrollTop += 1; if (++k < 150) requestAnimationFrame(step); else resolve(); }; requestAnimationFrame(step); }));
    await sleep(300);
    const t = Date.now();
    await change();
    let end = await pos();
    while (Date.now() - t < 6000) {
      end = await pos();
      const shift = end.top === null ? null : Math.round(end.top - start.top + (end.scroll - start.scroll));
      if (shift === null || Math.abs(shift) > 20) break;
      await sleep(100);
    }
    await scrolling;
    const shift = end.top === null ? null : Math.round(end.top - start.top + (end.scroll - start.scroll));
    return { row, scrolledPx: end.scroll - start.scroll, rowMovedOnScreenBeyondScrollPx: shift, afterMs: Date.now() - t };
  };
  out.pantryHold = await probe('pantry_list', '[data-testid^="pantry_item_"]', () =>
    patch(`households/big/stock/zz_${Date.now()}`, { productId: 'bp1', productName: 'Item 1 beans', unit: 'pcs', category: 'dairy', quantity: 1, expiryDate: local(0), dateType: 'use_by', location: 'fridge', note: null, openedOn: null, createdAt: new Date(), createdBy: 'someone-else', updatedAt: new Date(), updatedBy: 'someone-else' }),
  );
  await shot(page, 'perf-pantry-after-insert');
  await tab(page, 'Groceries');
  await expect(page.getByTestId('grocery_list')).toBeVisible({ timeout: 20000 });
  await sleep(1500);
  out.groceriesHold = await probe('grocery_list', '[data-testid^="grocery_item_"]', () =>
    patch('households/big/groceryLists/default/items/g1', { checked: true, checkedBy: 'someone-else', updatedAt: new Date() }),
  );

  // Insights: renders while the charts grow.
  await tab(page, 'Home');
  await sleep(1500);
  await reset();
  await page.getByRole('button', { name: 'See insights' }).click();
  await sleep(1500);
  out.insightsOpen = top(await renders(), 10);
  console.log(JSON.stringify(out, null, 1));
  await ctx.close();
})();
```

`loadperf.cjs`:

```js
// node loadperf.cjs: the welcome screen of `vite preview` in Playwright's Chromium with Lighthouse's mobile throttling
// (4x CPU, 150 ms RTT, 1.6 Mbps down), five cold loads: FCP, LCP, long-task time after FCP (a TBT stand-in) and bytes.
const { createRequire } = require('node:module');
const req = createRequire('/Users/jmalp/Code/personal/spoilnot/web/package.json');
const { chromium } = req('@playwright/test');
(async () => {
  const browser = await chromium.launch();
  const runs = [];
  for (let i = 0; i < 5; i++) {
    const ctx = await browser.newContext({ viewport: { width: 412, height: 823 }, deviceScaleFactor: 1.75, isMobile: true, hasTouch: true });
    const page = await ctx.newPage();
    const cdp = await ctx.newCDPSession(page);
    await cdp.send('Network.enable');
    await cdp.send('Network.emulateNetworkConditions', { offline: false, latency: 150, downloadThroughput: (1.6 * 1024 * 1024) / 8, uploadThroughput: (750 * 1024) / 8 });
    await cdp.send('Emulation.setCPUThrottlingRate', { rate: 4 });
    await page.addInitScript(() => {
      window.__lcp = 0;
      window.__long = [];
      new PerformanceObserver((l) => { for (const e of l.getEntries()) window.__lcp = e.startTime; }).observe({ type: 'largest-contentful-paint', buffered: true });
      new PerformanceObserver((l) => { for (const e of l.getEntries()) window.__long.push([e.startTime, e.duration]); }).observe({ type: 'longtask', buffered: true });
    });
    await page.goto('http://localhost:4173/', { waitUntil: 'load' });
    await page.waitForTimeout(6000);
    const r = await page.evaluate(() => {
      const fcp = performance.getEntriesByName('first-contentful-paint')[0]?.startTime ?? null;
      const nav = performance.getEntriesByType('navigation')[0];
      const bytes = performance.getEntriesByType('resource').reduce((s, e) => s + (e.transferSize || 0), nav?.transferSize || 0);
      const tbt = window.__long.filter(([s]) => fcp !== null && s >= fcp).reduce((s, [, d]) => s + Math.max(0, d - 50), 0);
      return { fcp: Math.round(fcp), lcp: Math.round(window.__lcp), tbt: Math.round(tbt), kib: Math.round(bytes / 1024), title: document.title };
    });
    runs.push(r);
    await ctx.close();
  }
  await browser.close();
  const med = (k) => [...runs.map((r) => r[k])].sort((a, b) => a - b)[2];
  console.log(JSON.stringify({ runs, median: { fcp: med('fcp'), lcp: med('lcp'), tbt: med('tbt'), kib: med('kib') } }, null, 1));
})();
```

`iphone.cjs`:

```js
// node iphone.cjs: iPhone checks Playwright WebKit can do (Mixed home, Alex): input font sizes (iOS zooms into fields
// under 16 px), portrait and landscape layouts of Home, Pantry, Add item and the date picker on iPhone 15 and SE.
const { phone, signIn, tab, shot, sleep, expect } = require('./wlib.cjs');
async function fonts(page) {
  return page.evaluate(() =>
    [...document.querySelectorAll('input, textarea, select')]
      .filter((e) => e.offsetParent !== null && e.type !== 'checkbox' && e.type !== 'radio')
      .map((e) => ({ id: e.getAttribute('data-testid') || e.getAttribute('aria-label') || e.id, px: parseFloat(getComputedStyle(e).fontSize) })),
  );
}
const fits = (page) =>
  page.evaluate(() => {
    const vw = innerWidth;
    const vh = innerHeight;
    const out = [...document.querySelectorAll('dialog[open], [role="dialog"]')].map((d) => {
      const r = d.getBoundingClientRect();
      return { top: Math.round(r.top), bottom: Math.round(r.bottom), height: Math.round(r.height), vh, scrollable: d.scrollHeight > d.clientHeight + 1 };
    });
    return { vw, vh, horizontalOverflow: document.documentElement.scrollWidth > vw, dialogs: out };
  });
(async () => {
  const out = {};
  for (const [device, label] of [['iPhone 15', 'p15'], ['iPhone 15 landscape', 'l15'], ['iPhone SE', 'se']]) {
    const { ctx, page } = await phone(`alex-${label}`, { device });
    const ok = await page.getByRole('heading', { name: 'Home', exact: true }).waitFor({ timeout: 15000 }).then(() => true, () => false);
    if (!ok) await signIn(page, 'alex@mixed.test');
    await sleep(2500);
    const r = { fields: [] };
    r.home = await fits(page);
    await shot(page, `iphone-${label}-home`);
    await tab(page, 'Pantry');
    await sleep(1500);
    r.fields.push(...(await fonts(page)));
    await page.getByTestId('fab_add_item').click();
    await sleep(800);
    r.fields.push(...(await fonts(page)));
    r.addItem = await fits(page);
    await shot(page, `iphone-${label}-add-item`);
    await page.getByTestId('sheet_add_item').getByTestId('field_expiry').click();
    await sleep(800);
    r.datePicker = await fits(page);
    await shot(page, `iphone-${label}-date-picker`);
    await page.keyboard.press('Escape');
    await sleep(300);
    await page.keyboard.press('Escape');
    await tab(page, 'Groceries');
    await sleep(1200);
    r.fields.push(...(await fonts(page)));
    await tab(page, 'Recipes');
    await sleep(1000);
    r.fields.push(...(await fonts(page)));
    r.small = [...new Map(r.fields.filter((f) => f.px < 16).map((f) => [f.id, f])).values()];
    r.fieldCount = r.fields.length;
    delete r.fields;
    out[device] = r;
    await ctx.close();
  }
  console.log(JSON.stringify(out, null, 1));
})();
```

`w12-p01a.cjs`:

```js
// node w12-p01a.cjs: SYNC-01 (p01) step A. Alex's iPhone opens Groceries (a full read of the items), adds one item and
// leaves the tab, so nothing on this phone listens to the list afterwards.
const { phone, signIn, tab, sleep, expect } = require('./wlib.cjs');
(async () => {
  const { ctx, page } = await phone('alex');
  const ok = await page.getByRole('heading', { name: 'Home', exact: true }).waitFor({ timeout: 15000 }).then(() => true, () => false);
  if (!ok) await signIn(page, 'alex@mixed.test');
  await tab(page, 'Groceries');
  await expect(page.getByTestId('grocery_quick_add')).toBeVisible({ timeout: 20000 });
  await sleep(2500);
  await page.getByTestId('grocery_quick_add').fill('early item');
  await page.getByTestId('grocery_quick_add').press('Enter');
  await expect(page.getByTestId('grocery_quick_add')).toHaveValue('', { timeout: 15000 });
  await sleep(2000);
  await tab(page, 'Pantry');
  await sleep(1500);
  console.log(JSON.stringify({ at: new Date().toISOString(), fullReads: await page.evaluate(() => Object.entries(localStorage).filter(([k]) => /sync|full/i.test(k)).map(([k, v]) => [k.slice(-60), v])) }, null, 1));
  await ctx.close();
})();
```

`w12-p01b.cjs`:

```js
// node w12-p01b.cjs: SYNC-01 (p01) step D, 5 min 20 s after Blake's changes. Alex's iPhone opens straight on Pantry
// (a digest link, so Home's list listener never starts), uses up the staple Milk (the restock engine writes Milk to the
// default list from this phone), then opens Groceries. Prints what the iPhone shows and what the server has.
const { phone, tab, shot, sleep, expect } = require('./wlib.cjs');
const FS = 'http://127.0.0.1:8080/v1/projects/demo-spoilnot/databases/(default)/documents';
(async () => {
  const { ctx, page } = await phone('alex', { url: 'http://localhost:5173/pantry?filter=expiring' });
  await expect(page.getByTestId('pantry_list')).toBeVisible({ timeout: 20000 });
  await page.getByRole('button', { name: /Expiring soon/ }).first().click();
  await sleep(1500);
  const milk = page.getByTestId('pantry_list').locator('[data-testid^="pantry_item_"]').filter({ hasText: 'Milk' }).first();
  await milk.getByRole('button', { name: /Options/ }).click();
  await page.getByRole('menuitem', { name: 'Used up' }).click();
  await sleep(5000);
  await tab(page, 'Groceries');
  await sleep(6000);
  const iphone = await page.getByTestId('grocery_list').innerText();
  await shot(page, 'p01-web-groceries');
  const r = await (await fetch(`${FS}/households/mixed/groceryLists/default/items?pageSize=100`, { headers: { Authorization: 'Bearer owner' } })).json();
  const server = (r.documents ?? [])
    .map((d) => d.fields)
    .filter((f) => !f.deletedAt)
    .map((f) => `${f.name.stringValue}${f.checked?.booleanValue ? ' (checked)' : ''}`)
    .sort();
  console.log(JSON.stringify({ at: new Date().toISOString(), iphone, server }, null, 1));
  await ctx.close();
})();
```

