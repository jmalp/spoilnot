# 11 Code quality, redundancy, tests and docs

Reviewed `0334b6a` on 2026-11-01 (the branch `review/2026-11` at `b11a8b4`, which adds only review reports).
Platforms: Android first and deepest; the web, the reminder job and the docs where this section asks.

**Method.** Searches and small scripts over the sources (Appendix A has the scripts): the CLAUDE.md rules by
`grep`; a copy-paste detector over normalized lines (exact, and again with identifiers renamed); declarations,
strings, resources and libraries counted against their uses on Android and the web; function length and nesting;
interfaces against their implementations; a map of the screens to the screenshot tests and of DESIGN's acceptance
criteria and QA.md's flows to the automated tests. Gradle ran once: the unit tests fresh (`--rerun`) and lint with
every check turned on through an init script in the scratchpad (the build files were not touched). The emulators and
Pixel_8 were in use by another session, so no instrumented test ran here; their numbers are section 00's. Nothing in
the source tree was changed.

## Summary

The code is in good shape for its size. The architecture rules hold almost everywhere (two small exceptions, CODE-10
and a Service the SDK requires), copy-paste in the app is low (3 exact repeats of 8 or more lines), no string,
drawable or private function is unused, and the Android tests use no mocks, no ignored cases and one short poll. The redundancy is
mostly elsewhere: **two v1 pantry code paths are dead but still carry the tests**, so 19 unit tests and the two-phone
rename test check code the app no longer runs (CODE-01); **DESIGN still describes v1 in 20 places** where later
changes went only to DECISIONS (CODE-02); the README and QA.md describe an older app (CODE-03); and 13 facts live in
two to ten places each, 12 of them already different (CODE-04, with one proposed home per fact). Smaller items: four
libraries that nothing uses (CODE-05), the two bundled fonts missing from Android's licence list (CODE-06), Android's
screenshot tests never comparing images while the web's do (CODE-07), Android test gaps the web port doesn't have
(CODE-08), and Groceries not holding remote changes during a scroll as DESIGN 6.4 requires (CODE-09). No item had a
security angle; nothing moved to the private notes.

## Metrics

### Architecture rules (CLAUDE.md, DESIGN 7.2)

| Metric | Value | How measured | Target or budget |
| --- | --- | --- | --- |
| Firebase classes outside `feature/*/data` and `core/firebase` | **1**, harmless: `feature/notifications/ui/SpoilNotMessagingService.kt` extends `FirebaseMessagingService` (the SDK requires a Service; it only forwards `onNewToken` to `DeviceSync`) | `grep` for `com.google.firebase` and `com.google.android.gms` imports in `app/src/main` | 0 |
| Composables or ViewModels using Firebase | **0** | Same search over every `ui/` package | 0 |
| Business logic outside `domain` | **1**: the "explain notifications after the third dated item" rule sits in `PantryViewModel` (CODE-10). Otherwise the `ui/` packages hold almost no logic: at most 5 sorting, grouping or date calls per file | Reading the ViewModels; a count of collection and date calls per `ui/` file | 0 |
| `Clock` not injected | 3 call sites in 2 places (section 03's metric, not counted again here) | 03-logic.md | 0 |
| Dates not stored as `YYYY-MM-DD` | **0** (section 03 also probed Arabic and Persian locales) | `grep` for date writes; 03 | 0 |
| User-facing text outside `strings.xml` | **0** found. The English messages in `core/error/AppError.kt` are exception messages, mapped to strings by `messageRes()`; notifications and the channel use `getString` | `grep` for literals in `Text(`, `contentDescription`, notification builders | 0 |
| Repositories keeping a `FirebaseFirestore` | **0**; 15 files go through `FirestoreProvider` | `grep` for `FirebaseFirestore` properties | 0 |
| Household-scoped listeners without `retryAfterOwnWrites` | **0**. The 3 listeners without it watch the user's own profile, notification settings and `config/app` | `grep` for `snapshotFlow(` and `retryAfterOwnWrites` | 0 |
| Destructive actions without Undo | 3 (LOGIC-10: Add to pantry, Skip, Delete list) | 03-logic.md | 0 or a DECISIONS entry |
| Status by color alone | **0**: `StatusChip` draws an icon and text (`PantryLabels.kt:131-143`), and `PantryComponentsTest` checks the wording | Reading the status composables | 0 |

### Redundancy

| Metric | Value | How measured | Target or budget |
| --- | --- | --- | --- |
| Unused Android strings | **0** of 596 (572 strings, 24 plurals). 488 are used by both apps, 108 by Android only | `strings_usage.py` (Appendix A): `R.string.*`, `@string/*` on Android; `str('…')`, `plural('…')` and the 8 template-key families on the web | 0 |
| Unused web-only strings | **1** of 21: `web_not_configured` (`web/strings-web.xml:9`, unused since W0) | Same script | 0 |
| Strings with the same English text under several names | **37 texts, 79 names** (for example "Today" ×4, "Low stock" ×3, `action_share` and `recipe_share`) | Same script; lint `DuplicateStrings` agrees (37) | Ideas |
| Unused drawables, fonts, colors, XML and raw resources | **0** of 15 files (`raw/keep.xml` is the shrinker's keep list) | References in Kotlin and XML | 0 |
| Unused libraries | **4** of 56 catalog libraries, plus 1 configuration: MockK and Turbine (`testImplementation`), `ui-tooling-preview` and `ui-tooling` (no `@Preview` anywhere), and `kotlinx-coroutines-test` in `androidTestImplementation` (CODE-05). Every catalog entry is referenced by a build file | Imports per library and source set | 0 |
| Dead or test-only Kotlin declarations | `PantryList.build` with its filter, sort and grouping (90 lines), `PantryService.updateProduct` with `ProductEdit` (about 50 lines), `PantryLimits.BATCH_LIMIT` (unused), `TabNavigator.visibleKeys` (tests only). Of 2,316 declarations scanned, the only others never referenced are 37 Hilt modules and providers (used by generated code). Unused private declarations: **0** | `unused.py` (Appendix A), then each candidate read by hand | 0 |
| Duplicated blocks, Android app code | **3** exact runs of 8 or more normalized lines (27 lines in all); **3** of 10 or more with identifiers renamed (the largest: two halves of `PlanSheets.kt`, 13 lines; the busy-and-error runner in `AuthViewModel` and `SettingsViewModel`, 12; the two "add ingredients to a list" sheets, 12) | `dupes.py`, windows of 8 and 10 lines (Appendix A) | — |
| Duplicated blocks, Android tests | **34** runs, 324 lines: the two-phone household setup in 6 flow tests, the Robolectric screenshot harness in 4 test files, `RecordingWriter` in 4 (CODE-11) | Same | — |
| Duplicated blocks, web | `src/`: **20** runs, 180 lines (17 in `.tsx` screens and sheets, 3 in unit tests); `tests/e2e`: **2** runs, 23 lines | Same, `.ts` and `.tsx` | — |
| Duplicated blocks, reminder job and benchmarks | **0** | Same | — |
| Interfaces with one implementation that add nothing | **0**. Of 19 plain interfaces, 17 are repository and service ports the layering rule requires (ViewModels may not see Firestore classes), several with test fakes (`RecordingWriter`, `FakeAuth`, `FakeHouseholds`, `FakeUsers`, `FakeLocalData`, `FakeRecipeImportModule`); the other 2 have several implementations (`FirestoreChangeMapper` 5, the `HouseholdChange` marker) | Implementations in `main`, `test` and `androidTest` | — |
| The same limit defined more than once | `BATCH_LIMIT = 400` three times on Android (one unused) and twice on the web; name and note limits in 7 Kotlin files and 4 web files, besides the rules and DESIGN (CODE-04 row 7) | `grep` for `const val MAX_` and `LIMIT` | One per fact |

### Complexity

| Metric | Value | How measured | Target or budget |
| --- | --- | --- | --- |
| Android sources | 172 files, **22,703 lines**. By feature (data / domain / ui): recipes 300 / 1,746 / 3,092 = **5,138**; pantry 315 / 1,131 / 2,241 = **3,687**; plan 161 / 383 / 1,468; grocery 283 / 771 / 1,309; household 457 / 385 / 935; settings 68 / 83 / 950; core 3,191 | `wc -l` | — |
| Largest files | `PantryForms.kt` 712, `GroceriesScreen.kt` 659, `HomeScreen.kt` 521, `PlanScreen.kt` 510, `PantryScreen.kt` 503, `HouseholdSettingsScreen.kt` 459, `RecipeDetailScreen.kt` 450, `FirestoreHouseholdRepository.kt` 436, `PlanSheets.kt` 434, `PresetColors.kt` 414 (generated) | Same | — |
| Largest functions | 875 functions; **41** over 60 lines, **17** over 100. Longest: `AppShell` 234 (`core/navigation/AppShell.kt:91`), `SettingsScreen` 216, `PlanScreen` 177, `GroceriesScreen` 151, `GroceryRow` 144, `PantryScreen` 143, `MealSheet` 143, `NotificationSettingsScreen` 137; outside Compose: `GroceryChangeMapper.write` 127 and `PantryChangeMapper.write` 106 (one `when` branch per change type), `PantryService.planAdd` 86 | `kfuncs.py` (brace matching, Appendix A) | — |
| Deepest nesting | Brace depth **10** in `GroceriesScreen` and `WeekGrocerySheet`, 8 in `GroceryRow`, `PantryScreen`, `NotificationSettingsScreen`, `RecipesScreen` and `PurchaseRow` (Compose lambdas count) | Same | — |
| Largest ViewModels | `PlanViewModel` 360 lines, 17 public functions, 9 state flows; `GroceriesViewModel` 286 and 14; `PantryViewModel` 233 and 13; `SettingsViewModel` 193 and 14 (account, household, appearance, export, Clear household data and crash reports in one) | Counting | Ideas |
| Web sources | 17,206 lines without tests (3,614 of unit tests); largest `PlanScreen.tsx` 630, `ItemForm.tsx` 579, `GroceriesScreen.tsx` 567, `domain/pantry/service.ts` 520 | `wc -l` | — |

### Lint and build

| Metric | Value | How measured | Target or budget |
| --- | --- | --- | --- |
| Lint as configured | 0 errors, 9 warnings (section 00) | BASE metrics | `abortOnError` |
| Lint with every check on, main and test sources | **5 errors, 123 warnings**: DuplicateStrings 37, SyntheticAccessor 28, TypographyQuotes 27, LogNotTimber 6 and LogConditional 6 (all in `androidTest`), ComposableLambdaParameterPosition 5, PluralsCandidate 4, ComposableLambdaParameterNaming 3, AndroidGradlePluginVersion 2, GradleDependency, UnusedAttribute, UseKtx, StaticFieldLeak and SdCardPath 1 each (the last two in tests). Errors: InvalidPackage 3 (inside the `grpc-core` and `jsoup` jars, code Android never loads) and **NewApi 2** (CODE-12). No UnusedResources | `-I lint-all.init.gradle` (`checkAllWarnings`, `checkTestSources`), Appendix A | — |
| Lint baseline file | **None**, so no issue is hidden | `app/lint*.xml`, the `lint {}` block | — |
| Kotlin compiler warnings | 24 (section 00) | BASE metrics | — |
| Gradle caches | Build cache and configuration cache on (`gradle.properties`); a second run of the full check is 2 s (section 00) | Reading; BASE | On |
| Versions against latest stable | See section 00 (35 of 38 current; AGP held on purpose) | BASE | Latest stable |
| Open-source licence list | Android: generated by AboutLibraries from the runtime dependencies, so it stays current, but it leaves out the two bundled fonts (CODE-06). Web: generated from `package-lock.json`, fonts included | Reading both generators and `web/public/licenses.json` | Complete |

### Tests

| Metric | Value | How measured | Target or budget |
| --- | --- | --- | --- |
| Android unit tests | **370** in 62 classes, all pass, **25.1 s** of test time | `testDevDebugUnitTest --rerun`, JUnit XML | All pass |
| Slowest unit tests | The first Robolectric test in a class pays the start-up: `SegmentedTest.largeTextStacksTheOptionsAndStillPicks` 5.7 s, `ColorThemeScreenshotTest.homeInEveryTheme` 4.5 s, `WelcomeScreenshotTest.welcome` 3.7 s; every other test under 0.7 s | Same | — |
| Slowest instrumented and browser tests | Section 00: `RestockFlowTest.dropAddsExactlyOneItem…` 14.4 s; `plan.spec.ts` 37 s | BASE metrics | — |
| Test smells | Sleeps **1** (`TestPhoneTest.kt:45`, a 200 ms poll); fixed waits **6** (`web/tests/e2e/screens.spec.ts`, 400 to 800 ms for animations); retries: Playwright `retries: 1` in CI and the deploy script; mocks **0** (hand-written fakes, no MockK); `@Ignore` **0**; skipped inside the default run 2 (BASE-02) | `grep` | — |
| Tests that check the same thing twice | 14 `PantryListTest` cases repeat what `PantryItemsTest` checks, against dead code; 5 `updateProduct` cases repeat `planEdit`'s rules (CODE-01); `StatusColorsContrastTest` repeats `ColorThemesContrastTest`'s check of the green theme (its only own case is the No date tone, CODE-11) | Reading the tests side by side | — |
| Android screens and sheets with a screenshot test | **14 of 33**, 5 of them only in part (CODE-07). None for: Sign in, Sign up, Reset password, Household gate, Household settings, Notifications, About, Update required, Mark opened, Throw away, Edit grocery item, Recipe edit, Browser, Cook mode, I cooked this, Add meal, Meal sheet, Move, This week's ingredients | Composables named in each `captureRoboImage` test | DESIGN 13.1's five: all covered |
| Android screenshots compared against references | **0** (rendered and reviewed by eye; the web compares 84 committed images on the Mac) | `git ls-files '*.png'`, DECISIONS 2026-10-16 | CODE-07 |
| DESIGN acceptance criteria with an automated test | Every automatable item in DESIGN 1, 9.6, 10.5, 13.1 and 14 has one, except **3**: the share preview under 5 s (M5; the test allows 60 s since 2026-10-31), the first household sync under 2 s (13.1; never measured by a test, QA.md measured 4.3 s), and the performance budgets, which run by hand and have no baseline (BASE-08) | Table in CODE-08 | All |
| QA.md flows 1 and 2.1–2.27 (Android) with automated coverage | **17 covered, 11 in part, 0 none**; the 10 conditions: 4 covered, 2 in part, 4 manual only | Table in CODE-08 | — |
| QA.md flows 28–36 (web) | Section 10's metric | — | — |

### Docs

| Metric | Value | How measured | Target or budget |
| --- | --- | --- | --- |
| Doc lines that no longer match the code, new in this report | **33**: DESIGN 20 (CODE-02), README 6 and QA.md 3 (CODE-03), CLAUDE.md 2 (CODE-10), RUNBOOK 1 and DECISIONS 1 (CODE-04) | Reading each doc against the code | 0 |
| Already reported elsewhere | CLAUDE.md BASE-03, BASE-04; RUNBOOK BASE-07; DESIGN 5.2's Add item table (LOGIC-05), 9.6's pseudo-code (LOGIC-01), DECISIONS on linking (section 03's note); SECURITY_REVIEW and PRIVACY (SECD-17, SECC-12, private) | Those reports | — |
| DESIGN MUSTs in 5 to 13 checked | **10** checked here (5.2's menu equivalents, 6.2's width, 6.4's color rule and remote changes, 7.2, 7.4, 8.5's invariants, 9.3, 9.6's required tests, 13.1's test lists): 9 hold, **1 doesn't** (6.4, remote changes during a scroll, CODE-09). 13.1's screenshot tests exist but aren't compared (CODE-07). 9.3's "avoid `runTransaction`" holds: its one use creates the profile at sign-up (`FirestoreUserRepository.kt:42`). 8.1, 11.6 and 12 belong to sections 05, 01 and 02 | Reading the code at each MUST | All hold |
| RUNBOOK and CLAUDE.md commands | Every npm script, Gradle task and script option they name exists (`gen:check`, `e2e:update`, `--measure-only`, `--skip-tests`, `--dry-run`, `--name`, `restore-main.ts`); section 00 ran the ones that touch no real project | `package.json` scripts, `case` statements in `scripts/` | 0 broken |
| Facts kept in several docs | **13** facts in 2 to 10 places each; **12** already differ between places (CODE-04) | Searching every doc for each fact | One home per fact |

## Findings

### CODE-01 · P3 · Confirmed · Android — Dead v1 pantry code still carries the tests, including the two-phone rename test

- **Where:**
  - `feature/pantry/domain/PantryList.kt:56-145`: `PantryList.build`, `applyFilter`, `sort`, `newest` and
    `PantryGroup` (90 lines). The app calls only `PantryList.rows`, from `HomeModels.kt:85` (Home keeps per-product
    rows, DECISIONS 2026-10-18). The Pantry tab builds its list with `PantryItems.build` (`PantryViewModel.kt:115`)
    since beta 2's one row per purchase (`24d7b6c`, 2026-10-18).
  - `feature/pantry/domain/PantryService.kt:283-323` (`updateProduct`) and `PantryModels.kt:164` (`ProductEdit`): the
    v1 product detail's save. Nothing in `app/src/main` calls it since the Edit window (`editItem` and `planEdit`,
    `PantryService.kt:330`) replaced product detail. Section 03 noted it too (LOGIC-05).
  - Smaller: `PantryLimits.BATCH_LIMIT` (`PantryModels.kt:210`) is never read (the writer and the household
    repository each keep their own 400); `TabNavigator.visibleKeys` (`core/navigation/TabNavigator.kt:27`) is read only
    by `TabNavigatorTest`; `web_not_configured` in `web/strings-web.xml:9` is never shown.
- **What happens:** the tests guard code the app doesn't run, and the code it does run has fewer of them.
  `PantryListTest` has 14 cases for filters, sort, search, grouping, a missing product, pending writes and 500 batches
  (152 lines), all on `PantryList.build`; `PantryItemsTest` has 4 for the live list (67 lines) and nothing for a batch
  whose product isn't loaded yet, pending writes or 500 batches. `PantryServiceTest` has 5 cases on `updateProduct`
  and 4 on `planEdit`. And M2's acceptance check that a rename reaches the other phone,
  `PantryFlowTest.renamingAProductUpdatesItsBatchesEverywhere` (`app/src/androidTest/.../PantryFlowTest.kt:196-213`),
  renames through `updateProduct`, so the Edit window's rename has no two-phone test. The two paths also disagree: a
  rename into a taken name throws `AppError.Conflict` in `updateProduct` and `AppError.NameTaken` in `planEdit`; the
  "a raised Keep at least re-arms restocking" rule is written three times in `PantryService` (`updateProduct`,
  `planEdit`, `withKeepAtLeast`).
- **Evidence:** `grep -rn 'PantryList\.' app/src/main` → only `PantryList.rows` in `HomeModels.kt`;
  `grep -rn 'updateProduct\|ProductEdit' app/src/main` → only the declarations; `unused.py` (Appendix A) lists
  `updateProduct` and `visibleKeys` as referenced from tests only.
- **Who it affects:** whoever changes the pantry list or the Edit window. A fix made in `PantryList.build` passes its
  tests and changes nothing on screen; a rename bug in `planEdit` passes the two-phone suite.
- **Suggested fix:** delete `PantryList.build` and its helpers (keep `rows` for Home) and move the `PantryListTest`
  cases that still apply (missing product, pending writes, 500 batches, grouping by location) to `PantryItemsTest`;
  delete `updateProduct` and `ProductEdit` with their 5 cases after checking each rule has a `planEdit` case; point
  `renamingAProductUpdatesItsBatchesEverywhere` at `editItem`; delete `PantryLimits.BATCH_LIMIT`,
  `web_not_configured` and either use or drop `visibleKeys`. Effort S. Also needs: DESIGN 5.2 (CODE-02), a DECISIONS
  entry; no web port (the web has neither).

### CODE-02 · P3 · Confirmed · Docs — DESIGN still describes v1 in 20 places that later changes replaced

- **Where and what** (the code is right in each case; DESIGN, the source of truth, is not):
  1. Header, Summary, 1 (Non-goals), 2 (Platform), 10.6 (Plan C "possible because the app is Android only"): "Android
     only". Sections 16 and 18 describe the web app released on 2026-10-31.
  2. 2 (Source code), 3 (item 2), 15 (confirmed decision 4): "private GitHub repository". It is public since
     2026-10-20 (DECISIONS; DESIGN 10.4 and 13.4 say so).
  3. 5.2 List: "one row per product … Tapping expands the row into its batches". One row per purchase, tap opens the
     Edit window, since beta 2 (DECISIONS 2026-10-18; DESIGN 17's first row says "through DECISIONS").
  4. 5.2 Product detail, 6.3's "Product detail" row and M2's "product detail": no such screen exists.
  5. 6.3: "Home | Four cards". 5.6 lists seven, and the Join requests card.
  6. 6.5: "list rows with a leading status icon". 5.2 after v1 removed it ("a second status icon … only repeated it").
  7. 6.6 wireframes: an (All) chip, four tabs, "Lists". There is no All chip and there are five tabs.
  8. 6.4: Soon's light color `#B26A00`. The app uses `#A05F00` for 4.5:1 (`StatusColors.kt:27`, DECISIONS).
  9. 7.2: the feature list (auth, household, pantry, grocery, recipes, notifications, settings, home) misses
     insights, plan, tutorial and update.
  10. 7.3 and 13.1: "JUnit, MockK, Turbine". The tests use neither (CODE-05).
  11. 8.1: "notes up to 500". 5.2 says 200, as do the app and the rules (`firestore.rules:232`).
  12. 8.2: no `users.pendingJoin`, `products.recentShelfLifeDays` or the activity events' top-level `productId` and
      `recipeId` (section 02's note; DECISIONS records them).
  13. 8.6: "Migrations are idempotent scripts in `tools/migrations/`". The folder doesn't exist, and
      `schemaVersion` is written (`FirestoreHouseholdRepository.kt:117`, `web/src/data/households.ts:189`) but read
      by nothing.
  14. 9.1's listener table: recipes "from the first visit to the Recipes tab" (Home reads them on every open, 5.6);
      activity "newest 30" (10, 9.8); no rows for the meal plan or the Insights month.
  15. 9.7: "Spark allows 50,000 reads and 20,000 writes per day", without the 20,000 deletes that 3 and Appendix C
      list (BASE-06 is the simulation side).
  16. 10.2: "After the first dated item is saved … request the notification permission". 5.4: the third.
  17. 10.4's workflow: one project, no dry-run input, `checkout@v4`. `reminders.yml` runs a matrix of two projects
      with a `dry_run` input on `checkout@v7` (RUNBOOK describes it).
  18. 13.2: "`./gradlew spotlessCheck lintDebug testDebugUnitTest assembleDebug`". Those aggregate tasks don't exist
      with flavors (CLAUDE.md, DECISIONS 2026-10-15); CI also runs the web job and the reminder job's integration
      test, which 13.2 doesn't name.
  19. 17: "newest last", but the 2026-10-18 baseline profile row is the table's last line, after 2026-10-31.
  20. Appendix B: `scripts/` lacks `deploy-web.sh`, `benchmark-summary.py` and `recipe-snapshot.js`; the workflows lack
      `keepalive.yml`; `docs/` lacks `QA.md`, `SECURITY_REVIEW.md` and `reviews/`; `firebase/` lacks `tools/`.
- **What happens:** CLAUDE.md makes DESIGN the source of truth, and every review section reads it first. Items 3,
  4, 14 and 16 describe behavior the app no longer has; a session that builds or ports from them builds the old
  behavior. Most come from the time when changes went "through DECISIONS" without updating DESIGN; the rule since v1
  is to update both.
- **Evidence:** each line above names the file and line that disagree.
- **Who it affects:** the owner and every Claude session reading DESIGN; the web port in particular, which follows
  DESIGN for anything Android doesn't show.
- **Suggested fix:** one docs pull request: rewrite 5.2's list and Product detail as today's Edit window, update the
  other lines, sort section 17, and add a DESIGN 17 row. Effort S to M. Also needs: CLAUDE.md's checklist could ask
  "did DECISIONS get an entry that changes a DESIGN sentence?" (Ideas).

### CODE-03 · P3 · Confirmed · Docs — The README and QA.md describe an older app

- **Where and what:**
  - `README.md:56`: "It is Android only." The web app for iPhone is released (DESIGN 16, 18).
  - `README.md:30-48` (What the app does): no meal planner, app tour or web app; `README.md:50-56` (How it's built)
    doesn't mention the web app or Firebase Hosting.
  - `README.md:96`: "about 360 automated tests … all running in CI". 819 ran in section 00; the instrumented
    two-phone tests don't run in CI, Android screenshots are rendered but never compared (CODE-07) and the web
    screenshot comparisons are skipped on Linux (`screens.spec.ts:22`).
  - `README.md:90`: "Four betas went to testers". The tags go to `v1.0.0-beta.9`.
  - `docs/QA.md:3-6`: "run this on two physical Android phones", and the Results table has rows only for sections 1
    to 5, while flows 28 to 36 are for the iPhone (their result is one sentence under the table).
  - `docs/QA.md` flow 20: "tapping what is highlighted (Pantry, Add item, Recipes, Groceries)". The tour also points
    at Plan and Add recipe (DESIGN 5.8).
  - `docs/QA.md` section 4: measurements "on the Pixel 8 emulator (2026-10-18)"; DESIGN 17 has four newer sets.
- **What happens:** the README is the public face of the repository and states numbers and limits that are no longer
  true; QA.md's header and results table don't fit half of its flows.
- **Evidence:** the lines above; `git tag` lists `v1.0.0-beta.2` to `v1.0.0-beta.9`.
- **Who it affects:** visitors to the repository; the owner when running QA.
- **Suggested fix:** README: add the planner, the tour and the iPhone web app, say "hundreds of automated tests" and
  which run in CI (or link to the CI workflow), drop the beta count or say "nine". QA.md: a header for both apps, a
  results row per web flow, flow 20's steps, and section 4 pointing to DESIGN 17 for numbers. Effort S.

### CODE-04 · P3 · Confirmed · Docs — 13 facts live in two to ten places, and 12 have already drifted

- **Where:** DESIGN, DECISIONS, RUNBOOK, README, SECURITY_REVIEW, PRIVACY, QA.md, CLAUDE.md and code comments.
- **What happens:** each fact below is written in several places; when it changes, one place is updated and the
  others keep the old value. The proposed home is where the fact is decided or measured; the other places link to it
  or say it in words without the number.

  | # | Fact | Where it is now | Drift today | Proposed single home |
  | --- | --- | --- | --- | --- |
  | 1 | Reads per household per day, households that fit | DESIGN 9.7 (6 figures), 9.8 (3), 17 (2 rows); RUNBOOK "Check quota usage"; DECISIONS 2026-10-18; SECURITY_REVIEW; README | SECURITY_REVIEW still says 4,700 (SECD-17); DESIGN 9.7 keeps the v1 estimate (4,500) beside five later figures | RUNBOOK's table (measured, dated). DESIGN 9.7 keeps the budget and method and links there; DESIGN 17 keeps before and after as history |
  | 2 | Test counts | README (360), SECURITY_REVIEW (61 and 79 rules tests), DECISIONS | All stale (819 run; 80 rules tests) | None in prose: the review baseline (00) records counts; README says "hundreds" |
  | 3 | The finishing checks and CI commands | CLAUDE.md, DESIGN 13.2, DESIGN Appendix A, DECISIONS 2026-10-15, `ci.yml` | DESIGN 13.2 names tasks that don't exist; CLAUDE.md lacks three CI checks (BASE-03) | One script (section 00's `check-all.sh` idea) that CLAUDE.md and `ci.yml` call; DESIGN 13.2 says what is checked, not how |
  | 4 | Platforms | DESIGN header, Summary, 1, 2, 10.6, Appendix A; README | All say Android only (CODE-02, CODE-03) | DESIGN 1, with 18 for the web |
  | 5 | Repository visibility | DESIGN 2, 3, 15, Appendix C; DESIGN 10.4, 13.4; RUNBOOK; DECISIONS 2026-10-20 | DESIGN 2, 3 and 15 say private | DECISIONS 2026-10-20 and DESIGN 2 |
  | 6 | Retention: activity 30 days, join requests 7, tombstones 2, meals 28 after their day, exports 14 | DESIGN 5.1, 5.6, 5.9, 8.1, 10.1, 10.3, 13.4; PRIVACY; RUNBOOK; `backend/reminder/src/job.ts:63-70` | None found (10 places agree) | DESIGN 10.3 and `job.ts`; PRIVACY keeps a plain-words copy for users |
  | 7 | Field limits (names 80, notes 200, titles, array sizes) | DESIGN 5.2, 8.1, 8.2, 11.6, 12.3; `firestore.rules`; 7 Kotlin files (`PantryLimits`, `GroceryRules`, `HouseholdModels`, `AccountValidation`, `MealPlanModels`, `RecipeModels`, `RecipeService`); 4 web files | DESIGN 8.1 says notes 500 | DESIGN 8.2 per field; one constants file per platform, with a unit test that reads `firestore.rules` and compares |
  | 8 | Benchmark results | DESIGN 17 (5 rows), QA.md section 4, `StartupBenchmark` KDoc ("under 2 s") | QA.md is from 2026-10-18; the KDoc's budget is 2 s where DESIGN 13.1 says 2.5 s (section 00's note) | DESIGN 13.1 for budgets, DESIGN 17 rows for results; QA.md links |
  | 9 | What a restore writes back | RUNBOOK "Restore a household" (twice, `RUNBOOK.md:220-227`), DESIGN 13.4 | RUNBOOK's two paragraphs disagree (one has the monthly counts, the other not) and both miss the meals (BASE-07) | DESIGN 13.4; RUNBOOK says it once |
  | 10 | Repository layout | DESIGN Appendix B, README's documents table, RUNBOOK | Appendix B misses 4 scripts and workflows (CODE-02 item 20) | DESIGN Appendix B |
  | 11 | Held library versions and why | DECISIONS 2026-10-18 and 2026-10-31, `dependabot.yml`, `libs.versions.toml`, DESIGN 17 | DECISIONS says `fragment` 1.5.4; the catalog has 1.9.1 | A comment next to the version in `libs.versions.toml`; DECISIONS keeps the reason, not the number |
  | 12 | Which listeners exist and how long they live | DESIGN 9.1, 5.6, 5.9, 9.8; CLAUDE.md "Patterns" | 9.1 is stale (CODE-02 item 14); CLAUDE.md lacks the meal plan and activity's `liveNewest` (CODE-10) | DESIGN 9.1's table |
  | 13 | Status colors | DESIGN 6.4, `StatusColors.kt`, the web's generated theme | DESIGN says `#B26A00` for Soon; the code `#A05F00` | `StatusColors.kt` (the web generates from it); DESIGN 6.4 gives the rule (4.5:1, icon plus text) and the code's values |

- **Evidence:** the table; each drift is cited in CODE-02, CODE-03 or the reports named.
- **Who it affects:** anyone reading one doc and trusting a number another doc has changed.
- **Suggested fix:** fix the drifts with CODE-02 and CODE-03, then cut each fact down to its home and links. Effort
  S to M. The field-limits test (row 7) is the one that also guards the code. Also needs: a DESIGN 17 row.

### CODE-05 · P3 · Confirmed · Android — Four libraries are declared but unused

- **Where:** `app/build.gradle.kts`: `testImplementation(libs.mockk)` and `(libs.turbine)` (no `io.mockk` or
  `app.cash.turbine` import anywhere); `implementation(libs.androidx.compose.ui.tooling.preview)` and
  `debugImplementation(libs.androidx.compose.ui.tooling)` (no `@Preview` in the project);
  `androidTestImplementation(libs.kotlinx.coroutines.test)` (no `kotlinx.coroutines.test` import in `androidTest`;
  the unit tests do use it). DESIGN 7.3 and 13.1 name MockK and Turbine.
- **What happens:** each one adds download, resolution and dependency-update work (Dependabot proposes their
  updates) for nothing, and DESIGN tells a new session to write tests with tools the project doesn't use (it uses
  hand-written fakes, which is better). The Compose tooling also feeds Android Studio's Layout Inspector with source
  information in debug builds; if the owner uses that, keep `ui-tooling` and say so in a comment.
- **Evidence:** import counts per library and source set (Metrics; command in Appendix A).
- **Who it affects:** builds and dependency updates; sessions following DESIGN 13.1.
- **Suggested fix:** remove the four declarations, their catalog entries and versions (`mockk`, `turbine`), and the
  extra `androidTestImplementation`; change DESIGN 7.3 and 13.1 to "hand-written fakes". Effort S.

### CODE-06 · P3 · Confirmed · Android — The licence list leaves out the two bundled fonts

- **Where:** `feature/settings/ui/AboutScreen.kt:43-54` (AboutLibraries' generated list); the font files
  `res/font/manrope.ttf` and `fredoka.ttf`; their licences in `app/src/main/assets/licenses/manrope-OFL.txt` and
  `fredoka-OFL.txt`, which no code reads (only a comment in `Type.kt:28` mentions one).
- **What happens:** AboutLibraries lists Maven dependencies; the fonts aren't one, so Settings > About > Licenses
  shows no Manrope or Fredoka. The SIL Open Font License asks each copy to carry the copyright notice and licence;
  the text files are inside the APK, but nobody can see them. The web app lists both fonts with their licence texts
  (`web/public/licenses.json`, from the `@fontsource-variable` packages).
- **Evidence:** `grep -rn "licenses/" app/src/main/kotlin` finds only the comment; the web file has both entries.
- **Who it affects:** the owner's licence compliance; parity with the web's About screen.
- **Suggested fix:** add the two fonts to the About list from the asset files (AboutLibraries accepts custom
  library definitions in its config folder, or show two extra rows above the generated list). Effort S. Also needs:
  a phone check (below).

### CODE-07 · P3 · Confirmed · Android · Question — Android screenshots are never compared, while the web port's are

- **Where:** `app/build.gradle.kts:212-214` (Roborazzi only writes to `build/outputs/roborazzi`); no reference image
  is committed for Android (`git ls-files '*.png'` lists 84 web references and none for the app); DECISIONS
  2026-10-16 ("image comparison across macOS and Linux CI is fragile"); for the web, DECISIONS 2026-10-29 and
  `screens.spec.ts:22` compare on the Mac and skip in CI.
- **What happens:** the 90 Android images catch crashes and are looked at by eye after UI changes, as CLAUDE.md asks;
  nothing notices a change nobody looked for (a padding, a color, a cut-off label at 200%). The web solved the same
  cross-platform problem by comparing locally and skipping in CI, and that comparison is what caught BASE-01.
  Android, the reference, has the weaker guard. Besides, 19 of 33 screens and sheets have no screenshot test at all
  (Metrics), among them every form sheet of the planner, Mark opened, Throw away, Cook mode and all of Settings
  except Appearance.
- **Evidence:** the files above; the screen list in Metrics.
- **Who it affects:** every Android UI change; 200% text and dark mode regressions in particular.
- **Suggested fix:** commit Roborazzi references recorded on the Mac and run `verifyRoborazziDevDebug` in CLAUDE.md's
  checklist (not in CI), as the web does; add screenshots for the planner sheets, the Edit window, Mark opened and the
  Settings screens. **Question:** does the owner want the extra step and the images in the repository (about 90 PNGs,
  a few MB)? Effort M. Also needs: DECISIONS (replacing 2026-10-16), CLAUDE.md, RUNBOOK.

### CODE-08 · P3 · Confirmed · Android — Test gaps against DESIGN and QA.md, some of which only the web port covers

- **Where:** `app/src/androidTest`, `app/src/test`; compared with `web/tests/e2e`.
- **What happens:** the gaps below have no automated test on Android:
  - **Groceries end to end.** No flow drives the Groceries screen or a second phone: quick add, check off, store-order
    learning across phones, lists, swipe and Clear checked with Undo. The parts have JVM tests (`GroceryServiceTest`,
    `GroceryRowTest`, `AddToPantryTest`, `StoreOrderTest`); the web has two end-to-end tests for this
    (`groceries.spec.ts`).
  - **Settings.** Export, Clear household data from the screen (hidden for members, the password dialog, a wrong
    password), About and Delete account have no UI test; `HouseholdFlowTest` covers the service calls, and
    `SignUpToHouseholdUiTest` taps Sign out but checks only that the welcome screen shows. The web covers each
    (`settings.spec.ts`, `household.spec.ts`). Section 02 noted the sign-out and Delete account part.
  - **The notification explanation after the third dated item** (DESIGN 5.4): no test (CODE-10); the web has one.
  - **DESIGN acceptance items:** the share preview under 5 s (M5; `RecipeUiTest` allows 60 s since 2026-10-31, so
    nothing measures the 5 s), the first household sync under 2 s (13.1), the performance budgets (by hand, BASE-08).
  - **QA conditions** with no automated check: notification permission denied, the small screen, rotation (and
    battery saver, which needs a phone).

  QA.md flows against tests (Android):

  | Flow | Tests | Coverage |
  | --- | --- | --- |
  | 1 Install and join | `SignUpToHouseholdUiTest`; `HouseholdFlowTest.ownerApprovesThenRemovesMember…` (service level). The Join requests card and the "joined" event: none | In part |
  | 2.1 Add, the other phone in 2 s | `PantryFlowTest.editsReachTheOtherPhoneInUnder2sMedian`, `RemoteChangeTrackerTest` | Yes |
  | 2.2 Used up, Throw away, Undo, the Edit window | `PantryComponentsTest` (swipes and menu, tap to edit), `PantryFlowTest.throwAwayLogsTheReasonAndUndoRestores`. The half-drag sheet: none | In part |
  | 2.3 Automatic add on both phones | `RestockFlowTest` | Yes |
  | 2.4 Quick add, check off, Add to pantry | `GroceryServiceTest`, `GroceryRowTest`, `AddToPantryTest`; not through the screen | In part |
  | 2.5 Share, preview, servings, cook, to the list | `RecipeUiTest` (both), `RecipeFlowTest.ingredientsGoToTheGroceryList…`. The 5 s and the screen kept on: none | In part |
  | 2.6 Home cards and their buttons | `HomeDashboardTest`, `HomeScreenshotTest`, `DeepLinkTest`. Tapping a card's button: none | In part |
  | 2.7 Settings closes on a tab switch | `AppShellTest.settingsClosesWhenItsTabIsLeft`, `TabNavigatorTest` | Yes |
  | 2.8 Insights on both phones, Undo | `InsightsFlowTest`, `InsightsTest`, `InsightsScreenshotTest` | Yes |
  | 2.9 Color themes | `ColorThemeScreenshotTest`, `ColorThemesContrastTest`. Kept after a restart: none | In part |
  | 2.10 Changes-only sync | `DeltaSyncFlowTest`, `DeltaSyncTest`, `PantryFlowTest` (offline, activity) | Yes |
  | 2.11 to 2.13 Recipe to groceries, Change, Use it up | `RecipeShoppingTest`, `RecipeServiceTest`, `GroceryServiceTest`, `RecipeComponentsTest`, `UseItUpTest` | Yes (JVM) |
  | 2.14 I cooked this | `CookingTest`, `RecipeFlowTest.cookingTakes…` | Yes |
  | 2.15 Mark opened | `PantryServiceTest`, `PantryComponentsTest.anOpenedRow…` | Yes (logic and row) |
  | 2.16 Store order | `StoreOrderTest`. Across two phones: none | In part |
  | 2.17 Quick wins | `AisleGuessTest`, `GroceryServiceTest`, `HomeScreenshotTest` (calm), `SegmentedTest`. The third dated item: none | In part |
  | 2.18 Add to pantry sheet | `AddToPantryTest` | Yes |
  | 2.19 Insights months | `InsightsMonthsTest` | Yes |
  | 2.20 App tour | `TourUiTest`, `TourTest`, `TourScreenshotTest` | Yes |
  | 2.21 Polish 1 | `PantryScreenshotTest`, `NaturalOrderTest` | Yes (images by eye) |
  | 2.22 Polish 2 | `RecipeScreenshotTest`, `GroceryRowTest.backInStockRemoveDeletes`, `ColorThemeScreenshotTest`. The list switcher, Settings cards: none | In part |
  | 2.23 After beta 7 | `WelcomeScreenshotTest`, `PantryComponentsTest.dateChipsToggle…`. Filter toggling, FAB labels: none | In part |
  | 2.24 Clear household data | `HouseholdFlowTest.ownerClearsHouseholdData…` (service). The screen: none | In part |
  | 2.25 to 2.27 Meal planner | `PlanFlowTest` (5), `MealWeekTest`, `MealPlanGroceryTest`, `PlanScreenshotTest`, the job's integration test | Yes |

- **Evidence:** test names from the sources (Appendix A has the listing commands).
- **Who it affects:** changes to Groceries and Settings on Android, which today rely on the manual QA script; ports,
  where the web may be right and Android wrong without a test saying so.
- **Suggested fix:** in order of value: a two-phone `GroceryFlowTest` (quick add, check off, Add to pantry, Undo,
  another phone sees each); a Settings UI test for Clear household data and sign-out; a domain test for the
  notification prompt (CODE-10); assert the share preview at 5 s in a separate, opt-in timing test. Effort M.

### CODE-09 · P3 · Likely · Android — Groceries doesn't hold remote changes while the list scrolls (DESIGN 6.4 MUST)

- **Where:** `feature/grocery/ui/GroceriesScreen.kt:167-205` (the `LazyColumn`); compare
  `feature/pantry/ui/PantryScreen.kt:251-252`.
- **What happens:** DESIGN 6.4: "A remote change MUST NOT move the list under the user's finger during a scroll or
  drag; queue it until the gesture ends." Pantry does this (`if (!listState.isScrollInProgress) shown = groups`).
  Groceries shows each snapshot at once. Keys keep the first visible row in place, so most remote changes don't jump,
  but a row that moves under the finger (another shopper checks it off, it moves to In cart; a new item lands in its
  aisle above) does. Groceries is the list two people change at the same moment, in a store.
- **Evidence:** reading both screens; no other list in the app has the hold (`grep -rn isScrollInProgress` finds only
  Pantry). *Likely*: not reproduced, because the emulators and Pixel_8 were in use by another session.
- **Who it affects:** two people shopping from one list.
- **Suggested fix:** the same hold as Pantry, ideally as one shared helper used by both (and Plan and Recipes if they
  get remote edits under a finger). Effort S. Also needs: the web port, if it lacks it too (section 10); a phone check
  (below).

### CODE-10 · P3 · Confirmed · Android — One rule sits in a ViewModel, and CLAUDE.md's patterns drifted

- **Where:** `feature/pantry/ui/PantryViewModel.kt:160-165` and `:229` (`NOTIFICATION_PROMPT_DATED = 3`); `CLAUDE.md`
  lines 9 ("core/firebase only provides the SDK instances") and 28-29 (`liveDocuments` users).
- **What happens:**
  - The rule "explain notifications once the third dated item is saved" (DESIGN 5.4) is decided inside
    `PantryViewModel.add`, against the rule that business logic is pure Kotlin in `domain` with tests. It has no test.
    The web put the same rule in `web/src/domain/notifications.ts:26-27` with a test, so the port is cleaner than the
    reference.
  - CLAUDE.md says `core/firebase` "only provides the SDK instances". It also holds the writer that applies every
    feature's change plans (`FirestoreHouseholdWriter.kt`), the changes-only listeners (`DeltaSync.kt`) and the
    listener helpers (`FirestoreFlows.kt`), all of which read or write Firestore. The patterns list `liveDocuments`
    for products, stock, grocery items and recipes, but the meal plan uses it too (with a window), and Recent
    activity uses `liveNewest`. (BASE-03 and BASE-04 cover the other CLAUDE.md lines.)
- **Evidence:** the lines above.
- **Who it affects:** sessions that follow CLAUDE.md to place new code; the next change to the prompt rule.
- **Suggested fix:** move the prompt decision to `feature/notifications/domain` (a pure function of the batches and
  whether the explainer was shown) with a unit test, like the web; reword CLAUDE.md: "`core/firebase` holds the SDK
  instances, the household writer and the listener helpers; feature Firestore code stays in `feature/*/data`", and
  add the meal plan and `liveNewest`. Effort S.

### CODE-11 · P3 · Confirmed · Android — Test scaffolding copied between files, and one test repeated

- **Where:**
  - The two-phone household setup (sign up two users, create, request, approve, wait) in the `@Before` of 6 flow
    tests: `PantryFlowTest.kt:47-66`, `DeltaSyncFlowTest.kt`, `InsightsFlowTest.kt`, `PlanFlowTest.kt`,
    `RecipeFlowTest.kt`, `RestockFlowTest.kt` (12 to 20 identical lines each).
  - `RecordingWriter` copied as a private class in `PantryServiceTest`, `MealPlanServiceTest`, `GroceryServiceTest` and
    `RecipeServiceTest` (three identical, one recording a list).
  - The Robolectric screenshot harness (rule, configuration, theme and font loops) in `PantryScreenshotTest`,
    `RecipeScreenshotTest`, `ColorThemeScreenshotTest` and `HomeScreenshotTest` (8 to 16 lines each).
  - `StatusColorsContrastTest` checks the green theme's status colors on two surfaces; `ColorThemesContrastTest` checks
    the same pairs for every fixed theme, the green one included, and skips only No date "checked above". Both carry
    their own `contrast()`.
- **What happens:** a change to how two phones join (for example after SECC-01's sign-out fix) has to be made six
  times; a change to `HouseholdWriter` four times. The repeated contrast test costs little but reads as extra
  coverage.
- **Evidence:** `dupes.py` over `app/src/test` and `app/src/androidTest`: 34 runs, 324 lines (Metrics).
- **Who it affects:** whoever changes the test setup.
- **Suggested fix:** a `TwoPhoneHousehold` helper (or JUnit rule) next to `TestPhone`; one `RecordingWriter` in a
  shared test file; a small screenshot helper; fold the No date case into `ColorThemesContrastTest` and delete
  `StatusColorsContrastTest`. Effort S.

### CODE-12 · P3 · Likely · Android — The accessibility checks would fail before they start below Android 14

- **Where:** `app/src/androidTest/kotlin/com/parcha/spoilnot/a11y/AccessibilityChecksTest.kt:152` and
  `AccessibilityChecksCanaryTest.kt:31`.
- **What happens:** both call `enableAccessibilityChecks()`, which needs API 34, while the app's minimum is 26 and
  there is no `@SdkSuppress(minSdkVersion = 34)` or `assumeTrue`. On an older emulator or phone the two tests fail
  with a missing-method error instead of being skipped. Lint flags it (NewApi, an error) only when test sources are
  checked, which the build doesn't do.
- **Evidence:** the all-checks lint run (Metrics). *Likely*: not run on an older device; Pixel_8 is Android 17 and
  was busy.
- **Who it affects:** anyone running the instrumented suite on a phone older than Android 14 (DESIGN 13.1 suggests two
  brands of physical phones).
- **Suggested fix:** `@SdkSuppress(minSdkVersion = 34)` on both classes. Effort S. Optionally turn on
  `checkTestSources` for lint.

## Ideas

- **Merge strings that mean the same thing in the same role.** Lint's 37 duplicate texts include real duplicates
  (`action_share` and `recipe_share`, `action_save` and `plan_save`, `action_remove` and `grocery_remove`,
  `action_edit` and `grocery_entry_edit`, `action_move` and `plan_move`, `open_settings` and `notif_open_settings`,
  `recipe_fewer_servings` and `plan_fewer`, `recipe_more_servings` and `plan_more`, `home_use_it_up` and
  `plan_use_it_up`, `activity_you` and `plan_you`) and words that only look alike ("Pantry" the tab and the place,
  "Today" the status and the planner's label) and should stay apart for translation. Merging the first ten removes
  them from both apps through `npm run gen`.
- **A check for DESIGN when DECISIONS changes.** Most of CODE-02 came from deviations recorded only in DECISIONS.
  CLAUDE.md's checklist could ask: "Does any new DECISIONS entry contradict a DESIGN sentence? Then update it."
- **Shared helpers in app code.** The busy-and-error runner (`AuthViewModel.launch`, `SettingsViewModel.run`); a
  `WhileUiSubscribed` constant for the 23 `SharingStarted.WhileSubscribed(5_000)`; one batch limit for the writer and
  the household repository; a shared header for the two "add ingredients to a list" sheets (`PlanGrocerySheet`,
  `AddToGrocerySheet`).
- **Split the biggest ViewModels.** `SettingsViewModel` serves six cards from 9 dependencies, `PlanViewModel` has 17
  public functions; splitting Settings by card (account, household data, appearance) and the planner's sheets into
  their own state holders would make each testable on its own.
- **Turn on a few more lint checks.** After section 00's "let new lint warnings show": `checkTestSources` (it found
  CODE-12) and `ComposableLambdaParameterPosition` are cheap; `SyntheticAccessor` and `TypographyQuotes` aren't worth
  it.
- **Localized date patterns.** `PantryLabels.kt:121` and `PlanScreen.kt:507-510` use fixed "MMM d" and "EEE d"
  patterns; `DateFormat.getBestDateTimePattern` keeps the order right for other locales when translation comes.

## For other sections

- **05 Reliability:** `households.schemaVersion` is written by both apps and read by nothing; only
  `minSupportedBuild` can force an update (DESIGN 8.6).
- **06 UX:** the same words under different names (Ideas) are a hint for the "one name per thing" check; QA flow 20
  doesn't mention the Plan and Add recipe steps.
- **07 Accessibility:** `AccessibilityChecksTest` needs API 34 (CODE-12); 19 screens and sheets have no screenshot at
  200% text (CODE-07).
- **08 Smoothness:** Groceries doesn't hold remote changes during a scroll (CODE-09).
- **09 Efficiency:** `androidx.fragment` and `concurrent-futures` ship in the app only to align test dependencies
  (DECISIONS 2026-10-18); `kotlinx-serialization-core` is declared next to `-json`, which already brings it; the four
  unused libraries (CODE-05).
- **10 Web:** reverse parity, where the web is ahead: Groceries and Settings end-to-end tests, the notification prompt
  rule in `domain` with a test, the fonts in the licence list. Check whether the web's Groceries holds remote changes
  during a scroll (CODE-09).

## Not covered

- **Instrumented tests:** not run here (the emulators and Pixel_8 were in use by another session); their counts and
  timings are section 00's. CODE-09 and CODE-12 are therefore *Likely*.
- **Dead code on the web:** measured for duplication only; finding unused exports needs a tool such as `ts-prune`,
  which would have to be downloaded. Android comes first.
- **A compiler-level unused-code check:** the scan in Appendix A matches names, so a dead function that shares its
  name with a live one (`planAdd`, `apply`) can hide. Detekt or the IDE's inspections would be exact.
- **Licences of each transitive dependency:** AboutLibraries and `gen-licenses.ts` generate both lists; only the
  fonts were checked by hand.
- **The reminder job and `tools/themes`:** scanned for duplication only (none in the job); their code quality wasn't
  reviewed further.
- **PRIVACY.md and SECURITY_REVIEW.md:** checked by sections 01 and 02 (SECC-12, SECD-17).
- **DESIGN 8.1's "new fields optional on read":** not checked field by field; section 05 tests bad data.

## Phone checks for the owner

| # | Phone | Steps | Expected | Result |
| --- | --- | --- | --- | --- |
| 1 | Android | Settings > About > Open source licenses: scroll the list for Manrope and Fredoka. | Today neither is listed (CODE-06). Note whether any font appears. | |
| 2 | iPhone | Settings > About > licenses in the web app: look for Manrope and Fredoka. | Both are listed with the Open Font License, for comparison with check 1. | |
| 3 | Android and a second phone | Put about 25 items on one grocery list. On the Android phone, scroll the list slowly up and down while the other phone checks off three items near the middle and adds one. | Ideally rows don't move under your finger until you let go (DESIGN 6.4). Note whether a row jumped or moved to In cart while you were scrolling (CODE-09). | |

## Appendix A: scripts and commands

All scripts ran from the scratchpad with Python 3.9 against the repository root; none is in the source tree.

**Lint with every check** (`lint-all.init.gradle`), run with
`./gradlew :app:testDevDebugUnitTest --rerun :app:lintDevDebug -I lint-all.init.gradle --continue`. It leaves the
build files alone; the reports land in `app/build/reports/lint-results-devDebug.*` (the next normal lint run
overwrites them).

```groovy
// Review 11: run lint with every warning enabled, without changing the build.
allprojects {
    pluginManager.withPlugin('com.android.application') {
        android.lint.checkAllWarnings = true
        android.lint.abortOnError = false
        android.lint.checkTestSources = true
    }
}
```

**Copy-paste detector** (`dupes.py`). Usage: `BASE=<prefix> TOP=40 python3 dupes.py 8 .kt app/src/main/kotlin`.
The structural variant replaces string literals with `S` and lower-case identifiers with `v` before hashing.

```python
"""Windows of N normalized lines that occur in two places; prints maximal runs, longest first."""
import os, re, sys, hashlib
from collections import defaultdict

W = int(sys.argv[1]); ext = sys.argv[2]; roots = sys.argv[3:]
skip_dirs = {'build', 'node_modules', '.claude', 'dist', 'generated'}
trivial = re.compile(r'^[\s{}()\[\],;]*$')
files = {}
for root in roots:
    for dp, dn, fn in os.walk(root):
        dn[:] = [d for d in dn if d not in skip_dirs]
        for f in fn:
            if f.endswith(ext):
                p = os.path.join(dp, f); norm = []
                for no, l in enumerate(open(p, encoding='utf-8').read().split('\n'), 1):
                    s = re.sub(r'//.*$', '', l).strip()
                    if not s or trivial.match(s) or s.startswith(('import ', 'package ', '*', '/*', '@file')):
                        continue
                    norm.append((no, re.sub(r'\s+', ' ', s)))
                files[p] = norm
index = defaultdict(list)
for p, norm in files.items():
    for i in range(len(norm) - W + 1):
        index[hashlib.md5('\n'.join(t for _, t in norm[i:i+W]).encode()).hexdigest()].append((p, i))
pairs = defaultdict(set)
for occ in index.values():
    if 2 <= len(occ) <= 6:
        for a in range(len(occ)):
            for b in range(a + 1, len(occ)):
                (pa, ia), (pb, ib) = occ[a], occ[b]
                if not (pa == pb and abs(ia - ib) < W):
                    pairs[(pa, pb)].add((ia, ib))
runs = []
for (pa, pb), ms in pairs.items():
    seen = set()
    for ia, ib in sorted(ms):
        if (ia, ib) in seen:
            continue
        k = 0
        while (ia + k, ib + k) in ms:
            seen.add((ia + k, ib + k)); k += 1
        n = k + W - 1
        runs.append((n, pa, files[pa][ia][0], files[pa][ia+n-1][0], pb, files[pb][ib][0], files[pb][ib+n-1][0]))
runs.sort(reverse=True)
print(f"duplicated runs: {len(runs)} (window {W}), normalized lines in runs: {sum(r[0] for r in runs)}")
rel = lambda p: os.path.relpath(p, os.environ.get('BASE', '/'))
for r in runs[:int(os.environ.get('TOP', '40'))]:
    print(f"{r[0]:4d}  {rel(r[1])}:{r[2]}-{r[3]}  ==  {rel(r[4])}:{r[5]}-{r[6]}")
```

**String usage** (`strings_usage.py`): reads `strings.xml` and `strings-web.xml`, then counts `R.string.<name>`,
`R.plurals.<name>` and `@string/<name>` in `app/src` (without `strings.xml` itself) and `'<name>'` in `web/src`
(without tests and generated files). It prints the dynamic web key families it can't follow (`theme_`, `units_`,
`colors_`, `location_`, `category_`, `unit_`, `reason_`, `plan_slot_`), so a string in one of them counts as used only
if it is named somewhere else too; none of the 596 ended up unused either way.

**Unused declarations** (`unused.py`): every `fun`, `class`, `object`, `interface`, top-level or member `val`/`var`
and `typealias` in `app/src/main/kotlin` (overrides left out), with its name counted as a whole word in every `.kt`,
`.kts` and `.xml` file of `app/src` and `benchmark/src`, comments stripped. Names seen no more often than they are
declared are "never referenced"; names seen in `main` only at their declaration but also in tests are "tests only".
Private declarations are checked within their own file.

**Function length** (`kfuncs.py`): finds each `fun` declaration, then matches braces from the body's `{` (string
literals and comments removed) to the closing one; an expression body counts until the next line indented no deeper
than the declaration. Depth is the deepest brace level inside the function.

**Library use**, one line per library and source set:

```bash
grep -rlE 'io\.mockk' app/src/test | wc -l          # 0
grep -rlE 'app\.cash\.turbine' app/src/test | wc -l # 0
grep -rl '@Preview' app/src | wc -l                 # 0
grep -rl 'kotlinx.coroutines.test' app/src/androidTest | wc -l  # 0
```

**Test listings**: `grep -rn -A1 '@Test' app/src/androidTest | grep 'fun '` for the instrumented names;
`grep -n "^\s*test(" web/tests/e2e/*.spec.ts` for the browser tests; the unit test times from
`app/build/test-results/testDevDebugUnitTest/*.xml`.
