# 13 Remeasure: section 00 again, after the fixes

Measured `main` at `714c5b6` (2026-11-24, "Docs catch-up (review B27)", #163) against
[00-baseline.md](00-baseline.md) (`0334b6a`, 2026-11-01). Platforms: Android / web / backend. Measurement only: no
code or docs on `main` were changed.

**Setup.** The same Mac as 00 (Apple M1 Pro, 10 cores, 16 GB, macOS 27.2), JDK 21.0.13, Node 24.22.1, Gradle 9.9.2
(the wrapper; 9.8.2 in 00), warm daemon. A second worktree at `origin/main`, with `ANDROID_HOME` set and
`npm ci --ignore-scripts` in each npm project. Pixel_8 (Android 17, API 37, arm64) ran headless on the Mac's GPU, as
RUNBOOK "Benchmark setup" says (`-gpu host -feature -Uwb -no-window -no-snapshot -no-boot-anim -no-audio`; renderer
"Apple M1 Pro", checked by the script). It was already running and stayed running for every step, idle at about one
core, including the Android checks (in 00 it started only at the browser tests). Firebase emulators from
`./scripts/emulators.sh`, from empty data (emptied again at the end). Steps ran one after another; the Mac was on
mains power, with 33 to 43% of memory free, but 8.4 of 9.2 GB of swap in use after 24 days' uptime (Runtime has the
state at each run). Emulator timings compare builds and runs on this Mac; they are not phone timings.

## Summary

The review closes with every number better or explained. The biggest change is that there is now a performance
baseline at all: on the headless, GPU-backed emulator (B22, #117) three benchmark runs agree within a few percent,
where 00's swung tenfold (BASE-08). A cold start reaches Home's cards in **708 ms** with the profile (first frame 328
ms; 00 recorded 953 to 4,856 ms for the first frame), and the first Pantry scroll draws at **4.7 / 6.2 / 7.8 ms** (P50
/ P90 / P99) with 0.2–0.6% janky frames, against 48 to 98 ms at P50 in 00: the emulator, not the app, was slow, which
answers 00's open question. Plan's first scroll keeps the warm-up's gain (#158: P99 8.4 ms, 6.5% janky), typing in
Pantry's search is nearly jank-free since #142 (0.7%, from 23–31%), and Your kitchen and tab switches are less
janky than DESIGN 13.1's last table; only Add item looks worse, in one of five iterations.

Every test suite passes, and there are twice as many tests: **1,688** run (819 in 00), with none flaky and none
reported as failed (the benchmarks are another matter, REM-03). Every finding from 00 is fixed: the web's Insights screenshot no longer depends on the month
(BASE-01, #118 and B28 #145), the instrumented report shows 85 of 85 passed instead of two false failures (BASE-02,
B22 #117), CLAUDE.md's list matches CI and the code (BASE-03, BASE-04, B26 #144, B27 #163), the web bundle is split
and under its warning (BASE-05, B26), quota-sim counts deletes (BASE-06, B01 #75), the RUNBOOK lines are right
(BASE-07, B27), and the benchmarks give usable numbers (BASE-08, B22 #117). The dev benchmark APK is **2.7% smaller**
(4.97 → 4.84 MB; download 4.29 → 4.14 MB: no `.proto` files, subset fonts, B26) though its code grew about 5%. The web
downloads about the same code in five chunks instead of two (373 → 396 kB gzipped), so an app-only release now
costs about 113 kB; Lighthouse went from 92 to **90** (LCP 3.2 → 3.7 s), which B26 measured and accepted. Quota-sim
gives exactly RUNBOOK's figures: **17.8 households** under 70% at today's 120 reminder runs a day, 18.0 at 24 (about
18 in 00). `npm audit` finds nothing in any of the four npm projects (00: 4 high in `web/`'s production tree), and
37 of 39 Gradle versions are current. The finishing checks take **733 s** (569 s): they now include the instrumented
tests' compile, screenshot verification, the reminder job's checks and `e2e:preview`, and run twice as many
tests. Three new findings, all P3: compiler and lint warnings grew from 24 to 127 and 9 to 18, mostly Compose 1.13
deprecations nobody is told about (REM-01); a few doc lines and one script message drifted (REM-02); and the jank
benchmark's Groceries quick add scenario fails every run since #142 capitalized name fields, so `--jank` always
exits 1 and that number has no current value (REM-03).

## Metrics

Durations are wall-clock times on the Mac above. "Pass" means the command exited 0. Each row is measured as 00
measured it unless it says otherwise.

### Suite

| Metric | 2026-11-01 | Now | What changed it |
| --- | --- | --- | --- |
| Android checks (`--rerun-tasks --profile --continue`) | Pass, **96 s**, 127 tasks (5 task names) | Pass, **130 s and 150 s** (two runs), 138 tasks, with `compileDevDebugAndroidTestKotlin` added; 00's five tasks alone: **120 s**, 128 tasks | More code and tests (795 unit tests against 370, the new look's screens, 77 commits); the instrumented tests' compile joined the list in B26 (#144). Pixel_8 ran beside it this time (about one core) |
| Task time in the profile | 273 s in 96 s | 364 s (00's tasks), 431 and 438 s (all) | As above; every task is 1.2–1.7 times longer |
| Slowest Android tasks | `lintAnalyzeDevDebug` 58 s, `testDevDebugUnitTest` 28 s, `lintAnalyzeDevDebugUnitTest` 22 s, `mergeExtDex…` 21 s each, `lintAnalyzeDevDebugAndroidTest` 15 s, `compile…Kotlin` 13 s each | `lintAnalyzeDevDebug` 71 s, `testDevDebugUnitTest` 49 s, `mergeExtDexDevDebug` and `…ProdDebug` 38 s each, `lintAnalyzeDevDebugUnitTest` 32 s, `compileDevDebugKotlin` and `…ProdDebug…` 25 s each, `lintAnalyzeDevDebugAndroidTest` 23 s, `compileDevDebugAndroidTestKotlin` 19 s | Same tasks on top; lint still is the largest part (126 s of 431 s) |
| Android unit tests | **370** pass, 62 classes, 26 s of test time | **795** pass, 0 fail, 0 skipped, 135 classes, 43 s | Tests added with the fixes (B01–B28) and the new look's screenshot tests (CODE-07, B28 #145) |
| Android lint (dev debug) | 0 errors, **9 warnings** | 0 errors, **18 warnings**: GradleDependency 4 (Navigation 3 1.2.2, Lifecycle 2.11.1, out since), PluralsCandidate 3, UseKtx 3 (`DeltaSync.kt:144,162,170`), VisibleForTests 2 (`AddToPantryDrafts.kt:27`), ModifierParameter 2 (`PantryScreen.kt:235`, `Stepper.kt:48`), ComposableNaming 2 (two test helpers), UnusedAttribute 1, UnusedResources 1 (`notif_explainer_body`) | AGP 9.5.2 (#159) ended the AGP warning; the rest are new code and new releases (REM-01) |
| Kotlin compiler warnings | **24**: main 5, unit tests 19, instrumented 0 | **127**: main 23, unit tests 89, instrumented 15 | Compose 1.13 (#161) deprecates the text fields with a `VisualTransformation`, `BasicTextField`'s old form and the sheet state API (17 in main code); the deprecated test rules (`createComposeRule`, 15 uses in 00) are now in 47 unit test and 14 instrumented places, with the tests added since and the other rule factories; 34 `runCurrent` calls without `@OptIn` in tests from B04, B07 and B09; FCM's `token`, `deleteToken` and `onNewToken` 3; `!!` 3 + 4 (REM-01) |
| Gradle build warnings | 2 kinds: `Configuration.setVisible` (AGP 9.4.1); 4 configurations resolved at configuration time (AboutLibraries 15.3.0) | **1 kind**: the same 4 configurations, still from AboutLibraries 15.3.0 (`BaseAboutLibrariesTask.kt:299`) | AGP 9.5.2 (#159) |
| Android checks again with nothing changed | Pass, 2 s | Pass, **1 s** (137 of 138 up to date) | — |
| Screenshots | `recordRoborazziDevDebug` pass, 29 s, 90 images of 26 scenes, in `build/` | `verifyRoborazziDevDebug` (the new finishing check) pass, **38 s**, no differences; `recordRoborazziDevDebug` pass, 44 s, **240** committed references (126 at font 1.0, 113 at 2.0, 1 focus ring), none changed | Every screen and sheet has one, committed in `app/src/test/screenshots` and compared (CODE-07, B28 #145) |
| Instrumented tests (`connectedDevDebugAndroidTest`) | Pass, **190 s**: 44 defined, 42 pass, 2 skipped and reported as failures | Pass, **293 s** (282 s of test time): **85** run, 85 pass, report 100% | The opt-in tests left out of the run (`notAnnotation`, BASE-02, B22 #117); new flow tests from the fixes (`GroceryFlowTest`, `SyncCursorFlowTest`, `OnePhoneWinsFlowTest`, …) |
| Slowest instrumented tests | `RestockFlowTest.dropAddsExactlyOneItem…` 14.4 s, `PantryFlowTest.hundredItems…` 14.3 s, `AccessibilityChecksTest.mainScreens…` 13.2 s | `AccessibilityChecksTest.mainScreensAndTheirSheetsPassAccessibilityChecks` 28.7 s, `SyncCursorFlowTest.ownWritesWhileNotListening…` 23.9 s, `JoinAndSignOutUiTest.signOutWithUnsyncedChangesAsksFirst` 22.8 s, `RestockFlowTest.dropAddsExactlyOneItem…` 13.9 s | The accessibility test's name says it covers the sheets too now (review 07) |
| Firestore rules tests | Pass, **80** in 20 suites, 10 s | Pass, **135** in 31 suites, **15 s** (11.1 s of tests) | The rules fixes of 2026-11-08 to 11-13 and their tests |
| Reminder job (`npm test`) | Pass, **31** in 9 suites, 0.6 s | Pass, **83** in 19 suites, 3 s | B01 (#75), B16 (#111), B25 (#157): the selection is replayed over four days around each 2026 DST change |
| Reminder job, as CI runs it | `typecheck` and `build` pass (1 s); integration **9**, 11 s | `typecheck` and `build` pass (1 s); integration **32** pass, 16 s through `emulators:exec` as in `ci.yml`, 13 s as CLAUDE.md writes it (`FIRESTORE_EMULATOR_HOST=… npm run test:integration` with the emulators running) | B01, B16, B25 |
| Web generated files (`npm run gen:check`) | Pass, 1 s | Pass, 1 s, no drift | — |
| Web checks (`npm run check`) | Pass, 15 s; Vitest **258** in 37 files | Pass, **18 s**; Vitest **493** in 64 files (5.1 s); no chunk size warning | Web ports of the fixes; B26 split the bundle (BASE-05) |
| Web browser tests (`npm run e2e`) | **Fail**: 26 pass, 2 fail (BASE-01), 198 s | Pass, **62** of 62, **201 s**, no retries, no screenshot changed | BASE-01 fixed (#118, B28 #145); new flows (`sync`, `race`, `listeners`, `offline`, `quota`, …) |
| Slowest browser tests | `plan.spec.ts` 37 s, `recipes.spec.ts` 33 s, `notifications.spec.ts` 27 s | `plan.spec.ts` 26.7 s, `offline.spec.ts` "a cooked meal removed and brought back…" 21.7 s, `listeners.spec.ts` "the restock engine reads the cache only" 21.3 s, `groceries.spec.ts` 20.4 s | Shorter longest flows; well under the 60 s and 90 s limits |
| Web build under hosting headers (`npm run e2e:preview`) | Pass, 1 test, 6 s | Pass, **3** tests, **20 s** including the build | `runtime-cache.spec.ts` checks the precache and first-use caching (B26) |
| Flaky tests | None seen | **None seen** (no rerun needed, no Playwright retry) | — |
| Total time of the finishing checks | **569 s** (9.5 min) | **733 s** (12.2 min): Android checks 130, screenshots 38, instrumented 293, rules 15, reminder job 4 + 13, web 1 + 18 + 201 + 20 | The list now matches CI (BASE-03, B26) and runs twice as many tests; the instrumented tests are the largest part now |

### Size and web performance

| Metric | 2026-11-01 | Now | What changed it |
| --- | --- | --- | --- |
| APK, dev benchmark build | **4.97 MB** file (4,973,656 bytes), **4.29 MB** download (4,290,611). Compressed: dex 3.63 MB (7.92 MB unpacked), `resources.arsc` 0.64 MB, `google/` 0.22 MB, `res/` 0.19 MB, native 67 kB, assets 58 kB | **4.84 MB** file (4,841,777 bytes, −2.7%), **4.14 MB** download (4,140,734, −3.5%). Compressed: dex **3.84 MB** (8.34 MB unpacked, +5%), `resources.arsc` 0.67 MB, `google/` gone, `res/` 0.08 MB, native 66 kB (every ABI, as before), assets 53 kB | B26 (#144): the libraries' `.proto` files left out (0.22 MB) and subset fonts (`res/` −0.11 MB); dex grew with the new look and the fixes. B26 measured the prod release APK at 4,621,214 bytes, download 3,967,285 |
| APK, dev debug | 29.8 MB | 31.3 MB (31,258,105 bytes) | More code |
| Web JS | Main chunk **1,216.8 kB raw, 365.1 kB gzip**, over the 1,200 kB warning; `index.esm` 24.9 / 7.8 kB | Five chunks, **1,315.8 kB raw, 396.2 kB gzip** in all: Firebase 660.5 / 196.9, the app (`index`) **376.7 / 112.5**, React 218.8 / 68.3, other libraries 33.5 / 10.4, `index.esm` (Messaging, loaded only with reminders on) 26.3 / 8.1; largest under the 750 kB warning | B26 (#144) split the libraries out (BASE-05), so an app-only release downloads about 113 kB; the total grew 6% (the fixes, the new look) |
| Web CSS | 47.9 kB raw, 8.7 kB gzip | 76.1 kB raw, 13.0 kB gzip | The new look (DESIGN 19) |
| Web fonts | 9 woff2 files, 118.1 kB | 9 woff2 files, 118.1 kB | Unchanged; the non-Latin subsets are now cached on first use, not precached (B26) |
| Service worker | 17.3 kB raw, 5.9 kB gzip | 18.2 kB raw, 6.2 kB gzip | B26's first-use caching |
| Service worker precache | 21 entries, 1,488.5 KiB | **18 entries, 1,445.7 KiB** | B26 (`licenses.json` and non-Latin fonts out of the precache); DESIGN 18.6 says 19 entries (REM-02) |
| Lighthouse 13.6.0, mobile, welcome screen of `vite preview` | Performance **92** median (87, 92, 93, 93, 92), accessibility 100, best practices 100; FCP 0.75 s, LCP 3.2 s, TBT 0 ms (530 ms cold), CLS 0, SI 1.36 s, 422 KiB | Performance **90** in all five runs, accessibility 100, best practices 100; FCP 0.75 s, **LCP 3.66 s**, TBT 4 ms median (15 ms on the first), CLS 0, SI 1.43 s, 452 KiB | B26 split the code in four requests, which Lighthouse's simulated slow 4G charges; B26 measured 91 → 90 and 3.5 → 3.7 s and kept the split (DESIGN 18.6). The new look's CSS adds 4 kB gzip |
| Lighthouse audits below 0.9 | Unused JavaScript (about 274 KiB), render-blocking (about 160 ms), network dependency tree | Unused JavaScript (about **284 KiB**), unused CSS (12 KiB, new), render-blocking (about 150 ms), network dependency tree, LCP (0.59) | The welcome screen still loads every chunk, since Firebase Auth decides what it shows (DESIGN 18.6) |

### Runtime (Pixel_8 emulator, dev benchmark build, 500 batches)

**Usable now** (BASE-08 fixed by B22, #117). Three runs of `./scripts/baseline-profile.sh --measure-only` on the
headless emulator agree within a few percent, where 00's three swung tenfold. The script's device check passed
every time (renderer "Apple M1 Pro", guest load 0.8 to 2.5 at the start, no warnings). 00 measured the first frame
only; since PERF-05 (B22) DESIGN 13.1's budget is measured to Home's cards, so both are given. Medians below are of
the three runs' medians.

| Metric | 2026-11-01 | Now | What changed it |
| --- | --- | --- | --- |
| Cold start, first frame, without the profile (median of 10) | A 1,089 · B 460 · C 4,937 ms | 365 · 383 · 379 ms: **379 ms** | The benchmark setup (B22 #117): GPU, headless, enough memory; not the app |
| Cold start, first frame, with the profile | A 953 · B 4,251 · C 4,856 ms | 334 · 328 · 328 ms: **328 ms** | As above. DESIGN 13.1, 2026-11-23: 426–430 ms |
| Cold start to Home's cards (`timeToFullDisplayMs`), without the profile | Not measured (no `ReportDrawnWhen` yet) | 773 · 838 · 834 ms: **834 ms** | PERF-05 (B22) made it the budget's measure; DESIGN 13.1: 881 ms (2026-11-17) |
| Cold start to Home's cards, with the profile | Not measured | 706 · 718 · 708 ms: **708 ms** | DESIGN 13.1: 757 ms (2026-11-17), 890 ms (2026-11-23, with the warm-up, #158); the profile regenerated for Compose 1.13 (#161, 17,789 rules). Budget: 2.5 s on a mid-range phone |
| First Pantry scroll without the profile, frame CPU time P50 / P90 / P99 | A 48.7 / 65.4 / 78.9 · B 69.9 / 176.3 / 393.0 · C 98.4 / 237.4 / 536.3 ms | 4.7 / 6.2 / 8.3 · 4.8 / 6.2 / 8.4 · 4.7 / 6.2 / 8.9 ms; janky frames 1.0%, 0.8%, 0.0% | The setup (B22); 00's open question for section 08 answered: the 50–100 ms were the emulator |
| First Pantry scroll with the profile, P50 / P90 / P99 | A 48.4 / 65.2 / 73.7 · B 93.9 / 239.3 / 480.9 · C 82.5 / 270.7 / 605.8 ms | 4.8 / 6.3 / 7.7 · 4.7 / 6.2 / 7.8 · 4.6 / 6.2 / 8.0 ms: **4.7 / 6.2 / 7.8 ms**; janky 0.6%, 0.6%, 0.2%; 96–99 frames an iteration | Same as DESIGN 13.1's 4.8 / 6.2 / 8.0 ms (2026-11-17). 16.7 ms is the 60 fps budget |
| Frame overrun P50 with the profile | A 43.8 · B 128.2 · C 101.2 ms | −9.4 · −9.4 · −9.6 ms (frames on time) | The setup |
| One benchmark run (seed, both benchmarks, both modes) | A 495 · B 695 · C 1,173 s | 276 · 287 · 302 s | Frames on time, so each iteration is shorter |
| Emulator and Mac state | Guest load 16 to 42, 575 MB guest swap; Mac about 70 MB free, 1.5 GB swap | Guest 4 cores, 4 GB, load 0.8–2.5 at each start; Mac 36–43% memory free, 6.9–8.4 GB of swap in use (24 days' uptime), mains power, nothing else heavy running | The script's checks (B22) |

`--jank` once (about 14 minutes, 13 scenarios), against DESIGN 13.1's latest table (the new look, 2026-11-17; Plan
with the warm-up, 2026-11-23). Frame CPU time P50 / P90 / P99 in ms, then the share of janky frames over five
iterations. The emulator drifts between sessions (DESIGN 13.1), so a moved number is a hint for an A/B, not a
result.

| Scenario | DESIGN 13.1 latest | Now | What changed it |
| --- | --- | --- | --- |
| Pantry, steady scroll | 4.8 / 6.5 / 8.1, 0.0% | 4.5 / 6.3 / 9.7, 0.2% | — |
| Groceries, first scroll | 4.8 / 6.9 / 9.9, 0.0% | 4.5 / 6.5 / 9.5, 0.0% | — |
| Groceries, steady scroll | 4.8 / 6.6 / 9.1, 0.0% | 4.5 / 6.7 / 10.2, 0.0% | — |
| Recipes, first scroll | 4.8 / 7.1 / 12.3, 0.8% | Failed once in the run (cause not kept); the rerun: 4.6 / 6.1 / 10.2, 0.2% | Flaky once (REM-03) |
| Recipes, steady scroll | 4.5 / 6.7 / 8.7, 0.0% | 4.5 / 6.4 / 10.5, 0.0% | — |
| Plan, first scroll | 4.1 / 7.0 / 11.6, 7.1% (with the warm-up, 2026-11-23) | 4.5 / 6.2 / 8.4, 6.5% (5–11% per iteration) | The stretch warm-up (#158) holds: no 20 ms P99 |
| Home, first scroll | 4.9 / 8.0 / 25.9, 2.6% | 4.8 / 7.1 / 29.5, 2.6% | — |
| Your kitchen: open, charts grow, fling, back | 8.1 / 19.8 / 24.5, 46.8% | **5.8** / 19.4 / 25.0, **35.2%** | Better; not attributed (Compose 1.13 #161 is the likely change since) |
| Every tab twice | 4.5 / 17.8 / 22.2, 18.1% of 1,047 (12.0–12.7 P90, 18.9–22.4% on 2026-11-23) | 4.2 / **10.8** / 23.2, **12.3%** of 1,042 | Better; the warm-up (#158) and Compose 1.13 (#161) are the changes since |
| Add item: open, keyboard, drag, close | 5.1 / 15.2 / 23.1, 14.5% | 4.9 / 18.4 / 27.5, **21.0%** (6–67% per iteration: one bad iteration) | Worse in one iteration; an A/B would tell whether it's the emulator |
| Plan's add sheet: open, drag, close | 4.0 / 8.9 / 19.5, 6.1% | 4.3 / 7.2 / 20.2, 6.5% | — |
| Typing in Pantry's search | 8.6–9.6 / 18.6–18.7 / 23–26, 23–31%, 54–56 frames an iteration | 4.4 / 9.3 / 13.0, **0.7%**, 138 frames (about 28 an iteration) | #142 (2026-11-21): the field shows what was typed at once instead of waiting for the list's state |
| Typing in Groceries' quick add | 5.1 / 12.4 / 24.6, 13.0% | **Fails every time** (2 of 2 with the same check) | REM-03 |

### Quota (`cd firebase && npm run quota-sim`)

| Metric | 2026-11-01 | Now | What changed it |
| --- | --- | --- | --- |
| Reminder runs simulated | 24 a day (the hourly job) | **120** a day by default (the Apps Script trigger's 96 and about 24 of the 72 scheduled, #151); 24 also run | On-time reminders (#151); `RUNS` became a knob |
| Reads per day, 10 households | **19,520 = 39.0%** (listeners 13,000, rule lookups 3,040, scheduled jobs 3,480) | `RUNS=120`: **19,700 = 39.4%** (listeners 12,720, rule lookups 3,540, scheduled jobs 3,440: runs 420, digests 240, housekeeping 680, export 2,100). `RUNS=24`: **19,412 = 38.8%** (scheduled jobs 3,152, runs 132) | Mostly the sim counting what it left out (2026-11-08: each activity event's rule lookup +400, the prune's reads +420, the job's state; 2026-11-09: a list lookup on item create +100, item creates instead of batch edits −280 for the listeners), which took it to 20,404 at 24 runs; then B25 (#157): a run reads only the users who may be due, so 120 runs cost about what 24 did (24,160 reads at 120 before it). Same as RUNBOOK's B25 table to the read |
| Reads per household per day | **1,952** | **1,970** (120 runs), 1,941 (24 runs) | As above |
| Writes per day, 10 households | **540 = 2.7%** | **1,070 = 5.3%** (120 runs), 974 = 4.9% (24 runs) | The sim counts each action's activity event as a write since 2026-11-08 (+400), and the job writes its state once a run (so writes grow by one a run). Same as RUNBOOK |
| Deletes per day | Not simulated (estimate about 570) | **570 = 2.9%** of 20,000, simulated | BASE-06, B01 (#75); matches 00's estimate |
| Households under the 70% warning line | About 18 | **17.8** (120 runs), **18.0** (24 runs), for every limit | Reads still run out first |

### Dependencies

| Metric | 2026-11-01 | Now | What changed it |
| --- | --- | --- | --- |
| Gradle libraries and plugins | 38 checked, 35 current (KSP behind; AGP and concurrent-futures held) | **39** checked (errorprone added), **37** current. Behind: Navigation 3 1.2.1 → 1.2.2, Lifecycle 2.11.0 → 2.11.1. Nothing held: AGP 9.5.2 and concurrent-futures 1.3.0 are current. Gradle 9.9.2 is current | AGP 9.5.2 (#159), Compose BOM 2026.11.01 (#161), the 2026-11-14 updates; the two behind came out since (lint flags them too) |
| `npm outdated`, `web/` | 4 (eslint, globals; @types/node and typescript majors skipped) | 4: @types/react 19.3.0 → 19.3.1, typescript-eslint 8.73.0 → 8.74.0; @types/node 24 → 26 and typescript 6 → 7, the majors Dependabot skips | Dependabot (#147) |
| `npm outdated`, `firebase/` | 1 | **0** | #146 |
| `npm outdated`, `backend/reminder/` | 1 (@types/node major) | 1 (@types/node 24 → 26, skipped on purpose) | — |
| `npm outdated`, `tools/themes/` | Not measured | 1: @material/material-color-utilities 0.3.0 → 0.4.0 | A fourth npm project (Dependabot covers it since 2026-11-10) |
| `npm audit`, `web/` | 24 (0 critical, 4 high, 18 moderate, 2 low); production tree 4 high | **0**; production tree **0** | The 2026-11-14 updates and `overrides` (DECISIONS 2026-11-14) |
| `npm audit`, `firebase/` | 23 (6 high, 17 moderate); production tree 0 | **0**; production tree **0** | Same |
| `npm audit`, `backend/reminder/` | 6 moderate; production tree 6 moderate | **0**; production tree **0** | Same |
| `npm audit`, `tools/themes/` | Not measured | **0**; production tree **0** | — |

### Docs and commands

| Metric | 2026-11-01 | Now | What changed it |
| --- | --- | --- | --- |
| CLAUDE.md commands that failed as written | 1 (`npm run e2e`, BASE-01) | **0** of those run: the Android checks, `verifyRoborazziDevDebug`, `recordRoborazziDevDebug`, the rules tests, the reminder job's four commands, `gen:check`, `check`, `e2e`, `e2e:preview`, `connectedDevDebugAndroidTest`, `quota-sim`, `spotlessApply` (no change), and `baseline-profile.sh --measure-only`; `baseline-profile.sh --jank` exits 1 (REM-03) | BASE-01 fixed. Not run, as in 00: `LiveImportTest` (real sites), `subset-fonts.sh` (rewrites the fonts) and a full profile generation (rewrites the profile) |
| CLAUDE.md lines that no longer match the code | 1 (BASE-04), plus 3 checks CI runs missing (BASE-03) | **0**: every class, file and helper it names exists where it says (`HouseholdWriter.apply`, `FirestoreHouseholdWriter`, `DeltaSync.kt`, `liveDocuments`, `liveNewest`, the four services, …), and its list matches `ci.yml` | B26 (#144), B27 (#163) |
| RUNBOOK commands that failed as written | 0 (bare `adb` not on PATH) | **1** of those run: `baseline-profile.sh --jank` exits 1 (REM-03); `emulators.sh`, `quota-sim` with `RUNS`, `e2e:preview`, the Lighthouse command and `baseline-profile.sh --measure-only` pass. RUNBOOK and CLAUDE.md now give `adb`'s full path | BASE-07, B22, B27; REM-03 from #142 |
| RUNBOOK and other doc lines that no longer match | 2 (BASE-07) | **3** small ones: four docs say `web/`'s `npm audit` still lists deepmerge, which it no longer does; DESIGN 18.6 gives the precache as 19 entries (18 now); `--measure-only` ends with "Review and commit … baseline-prof.txt" though it wrote nothing (REM-02) | BASE-07's lines are fixed (four npm projects, planned meals in the restore, `adb`) |
| Releases | Android `1.0.0-beta.9`; web `55edcc5` | Android **1.0.0** (build 13, `744d251`, 2026-11-21) and the web from the same commit; no release since. `main` has 16 commits since `v1.0.0`: user-facing on Android #148 (Cooked offline shows at once), #149 and #162 (reminder times), #158 (Plan's first scroll), #161 (Compose 1.13, focus ring), #144 (smaller APK), #145 (tests and dead code); on the web #144 (chunks), #147, #148, #149, #162. Live without a release: the Apps Script trigger (#151) and B25 (#157) | 1.1.0 comes after this wave |
| Automated tests in total | **819** run | **1,688** run: 795 Android unit, 85 instrumented, 135 rules, 115 reminder job (83 + 32), 493 web unit, 62 browser, 3 preview | Tests with every batch; README says "hundreds" |

## Findings

### REM-01 · P3 · Confirmed · Android — Compiler and lint warnings grew fivefold and twofold, unseen

- **Where:** `app/build.gradle.kts` (lint `abortOnError` fails on errors only; no `warningsAsErrors`); the Kotlin
  warnings in `core/designsystem/component/FormFields.kt`, `TextFields.kt`, `SearchAndFilters.kt`, `FormSheet.kt`,
  `feature/household/ui/InviteCodeTransformation.kt`, `feature/pantry/ui/PantryForms.kt:643`, 47 places in unit tests and 14
  in instrumented tests; lint in `DeltaSync.kt:144,162,170`, `AddToPantryDrafts.kt:27`, `PantryScreen.kt:235`,
  `Stepper.kt:48`, `strings.xml:417`.
- **What happens:** the build passes with 127 Kotlin warnings (24 on 2026-11-01) and 18 lint warnings (9). In main
  code, Compose 1.13 (#161) deprecates the text fields that take a `VisualTransformation` (the password and invite
  code fields), `BasicTextField`'s old form and the bottom sheet's state API (17 warnings); DECISIONS 2026-11-24
  knows and leaves the move for its own change, but no row tracks it. In tests, the deprecated `createComposeRule` and its kin are
  used in 61 places (15 in 00), and 34 `runCurrent` calls in tests added by B04, B07 and B09 lack `@OptIn`. Lint adds
  new kinds: `VisibleForTests` (main code calling a test-only method), `ModifierParameter`, `ComposableNaming`,
  `UnusedResources`. 00's "Ideas" suggested making new warnings fail the build; with no gate, the count kept growing
  and a new warning that matters (a deprecation with a removal date, a wrong `@VisibleForTesting` use) sits among
  the rest.
- **Evidence:** `w:` lines of the `--rerun-tasks` build, deduplicated by file, line and message: main 23, unit tests
  89, instrumented 15; `lint-results-devDebug.sarif` (Metrics). The unused string is `notif_explainer_body`, left
  when B16 (#111) moved to `notif_explainer_body_time`; neither app reads it (the web's generated strings skip it).
- **Who it affects:** the owner and every session reading build output; nobody using the app. The deprecated text
  field APIs still work in Compose 1.13.
- **Suggested fix:** a row for the Compose 1.13 migration (`TextFieldState`, `SecureTextField`, the new sheet
  state); a mechanical pass over the test rules and `@OptIn`; delete `notif_explainer_body`; then 00's idea: turn off
  the version checks lint duplicates from Dependabot and set `warningsAsErrors` (or a lint baseline) so the count
  can only go down. Effort S for tests and lint, M for the text fields.

### REM-02 · P3 · Confirmed · Docs — Three small lines drifted since the fixes

- **Where and what:**
  - RUNBOOK "Dependency updates" (line 735: "all four projects pass `npm audit` except `web/`'s deepmerge"), DESIGN
    12.2 item 9, DESIGN 17's 2026-11-14 row, DECISIONS 2026-11-14 and the checklist row that repeats DESIGN 12.2
    item 9 say `web/` keeps four low-severity entries for deepmerge. Today `npm audit` lists nothing in `web/`
    (deepmerge 4.3.1 is still installed), so the DECISIONS entry's "revisit when…" has nothing left to wait for.
  - DESIGN 18.6 (line 1808) gives the precache as 19 entries, 1,445 KiB; the build reports 18 entries, 1,445.7 KiB.
  - `./scripts/baseline-profile.sh --measure-only` and `--measure` end with "Done. Review and commit
    app/src/main/baselineProfiles/baseline-prof.txt" although `--measure-only` wrote nothing (noted in 00 for
    section 11; still there). `--jank` prints its own end line.
- **Evidence:** `npm audit --json` in `web/` (0 of every severity); the `vite build` output in `npm run check`;
  `scripts/baseline-profile.sh:149`.
- **Who it affects:** the owner reading the dependency docs or a benchmark run's last line.
- **Suggested fix:** update the five lines, or close the DECISIONS entry with a dated note; say 18 entries; print
  the commit hint only when the script generated a profile. Effort S.

### REM-03 · P3 · Confirmed · Android — `--jank` can't measure Groceries' quick add since 2026-11-21

- **Where:** `benchmark/src/main/kotlin/com/parcha/spoilnot/benchmark/JankBenchmark.kt:113` (the check) and `:158`
  (`TYPED = "item 12"`); `feature/grocery/ui/GroceriesScreen.kt:783` (`KeyboardCapitalization.Words`).
- **What happens:** #142 (2026-11-21) made the name fields capitalize words on the keyboard, and in the same change
  the typing benchmark began checking that the field shows exactly what was typed (the Pantry search bug it fixed).
  Pantry's search doesn't capitalize, so it passes; Groceries' quick add does, so the keyboard turns "item 12" into
  "Item 12" and every iteration fails the check. The app does what DESIGN 8.1 says; the benchmark expects the old
  field. `./scripts/baseline-profile.sh --jank` runs every other scenario and then exits 1 ("failed:
  groceriesQuickAddTyping"), so the number B23 fixed (quick add suggestions off the main thread, 13.0% janky) has
  no current value, and a run always ends red. `recipesScrollFirst` also failed once in the same run and passed on
  its rerun; its reason was overwritten by the next test's report, since the script keeps only the last one.
- **Evidence:** the run's end line; `TEST-Pixel_8(AVD) - 17.xml`: `IllegalStateException: grocery_quick_add shows
  "Item 12" after typing "item 12"`, the same on a rerun (a second rerun failed earlier, at sign-in). DESIGN 13.1's
  tables have no quick add row after 2026-11-17.
- **Who it affects:** the owner and sessions running `--jank` after Compose or list changes (CLAUDE.md's finishing
  checks); nobody using the app.
- **Suggested fix:** compare case-insensitively for the quick add, or type "Item 12"; keep each failed test's report
  (copy the XML next to the results before the next test). Effort S.

## Not covered

- **Prod release builds**, as in 00. The APK size is the dev benchmark build's; B26 measured the prod release APK.
- **`LiveImportTest`** (real recipe sites), **`subset-fonts.sh`** and a full profile generation: they touch real
  sites or rewrite committed files. `spotlessApply` ran in the throwaway worktree and changed nothing.
- **An A/B for the jank numbers that moved** (Your kitchen and tab switches better, Add item worse in one
  iteration): only one `--jank` run was asked for.
- **Commands that touch real projects or GitHub**: rules deploys, `deploy-web.sh`, `release-beta.sh`, the
  reminder, export and restore workflows, the Apps Script trigger.
- **CI's own durations** (DESIGN 13.2 has B26's), **the real quota in the Firebase console**, and **Gradle
  dependency advisories**.
- **Real phones:** 00's phone checks for the owner still apply, with 1.0.0 (build 13) and web build `744d251` as
  the installed versions.
