# 00 Baseline and health check

Reviewed `0334b6a` on 2026-11-01 (the branch `review/2026-11` at `6f664b5`, which adds only the review plan).
Platforms: Android / web / backend.

**Setup.** Mac with an Apple M1 Pro (10 cores) and 16 GB, macOS 27.2, JDK 21.0.13, Node 24.22.1, Gradle 9.8.2 (warm
daemon). Pixel_8 emulator: Android 17 (API 37), arm64, 4 cores, 4 GB in use. It first ran windowed with the default
GPU mode (`emulator -avd Pixel_8 -no-snapshot-save -no-boot-anim -no-audio`, as its last launch), which turned out
to be software rendering; it was restarted with `-gpu host` for two of the three benchmark runs (BASE-08). Firebase
emulators from `./scripts/emulators.sh`, starting from empty data (emptied again at the end). The steps ran one
after another, so their durations don't overlap, but Pixel_8 was running next to everything from the browser tests
on. Emulator timings compare builds and runs on this Mac; they are not phone timings.

## Summary

The project is healthy. Every Android, rules, reminder job and web check passes, with one exception: the web
browser test of the Insights months card fails every time since 1 November, because the month names it was recorded
with (May to Oct) changed (BASE-01). That breaks `npm run e2e` and the deploy script's test gate for the whole month,
not CI. No flaky tests showed up. The quota simulation gives exactly the RUNBOOK's numbers (19,520 reads, 39.0%),
Lighthouse matches DESIGN 18.6 (performance 92, accessibility 100), and dependencies are current apart from two
holds that DECISIONS explains. The other findings are small: the instrumented report shows two "failures" on every
passing run (BASE-02), CLAUDE.md's checklist misses checks CI runs and names a method removed in M3 (BASE-03,
BASE-04), the web bundle passed its own size warning (BASE-05), deletes aren't simulated (BASE-06), and some RUNBOOK
lines drifted (BASE-07). The one real gap: the startup and scroll benchmarks gave numbers that swing tenfold
between identical runs on this Mac (software rendering by default, then too little memory), so there is no
performance baseline yet and section 08 has to settle whether Pantry's first scroll got slower (BASE-08). One item
moved to the private notes.

## Metrics

Durations are wall-clock times on the Mac above. "Pass" means the command exited 0.

### Suite

| Metric | Value | How measured | Target or budget |
| --- | --- | --- | --- |
| Android checks: `spotlessCheck lintDevDebug testDevDebugUnitTest assembleDebug :benchmark:compileBenchmarkKotlin` | Pass, **96 s**, 127 tasks | `--rerun-tasks --profile --continue` (everything re-executed, warm daemon) | CI job timeout 30 min |
| Slowest Android tasks | `lintAnalyzeDevDebug` 58 s, `testDevDebugUnitTest` 28 s, `lintAnalyzeDevDebugUnitTest` 22 s, `mergeExtDexDevDebug` and `…ProdDebug` 21 s each, `lintAnalyzeDevDebugAndroidTest` 15 s, `compileDevDebugKotlin` and `compileProdDebugKotlin` 13 s each, `kspDevDebugKotlin` 10 s, `spotlessKotlin` 8 s | Gradle profile report (273 s of task time in 96 s, in parallel) | — |
| Android unit tests | **370** pass, 0 fail, 0 skipped, 62 classes, 26 s of test time | JUnit XML of `testDevDebugUnitTest` | All pass |
| Android lint (dev debug) | **0 errors, 9 warnings**: PluralsCandidate 4 (Insights count strings), AndroidGradlePluginVersion 2 (AGP 9.5.0, held on purpose), GradleDependency 1 (concurrent-futures 1.3.0, pinned on purpose), UnusedAttribute 1 (`profileable android:shell` in the dev debug manifest), UseKtx 1 (`DeltaSync.kt:88`) | `lint-results-devDebug.sarif` | `abortOnError = true` (errors only) |
| Kotlin compiler warnings | **24**: main code 5 (2 deprecated FCM token APIs, 3 unnecessary `!!`), unit tests 19 (15 deprecated `createComposeRule`, 4 unnecessary `!!`), instrumented tests 0 | `w:` lines in the build logs, deduplicated | — |
| Gradle build warnings | 2 kinds, both from plugins: `Configuration.setVisible` deprecated, to be removed in Gradle 11 (AGP 9.4.1's `BasePlugin` and `SourceSetManager`); 4 configurations (`dev`/`prodDebugCompileClasspath`, `…RuntimeClasspath`) resolved at configuration time (AboutLibraries 15.3.0, `BaseAboutLibrariesTask.kt:299`) | `--warning-mode all`, `-Dorg.gradle.deprecation.trace=true --info` | — |
| Android checks again with nothing changed | Pass, **2 s** (125 of 127 tasks up to date) | Same command, second time | — |
| Screenshots (`recordRoborazziDevDebug`) | Pass, **29 s**, 90 images of 26 scenes (light and dark, font 1.0 and 2.0, six themes) | Image count in `app/build/outputs/roborazzi`; two opened and checked by eye | — |
| Instrumented tests (`connectedDevDebugAndroidTest`) | Pass, **190 s** (153 s of test time): 44 defined, **42 pass**, 2 skipped by design (`LiveImportTest`, `PantryScrollPerfTest` run only with `-e live true` / `-e perf true`); the report counts the 2 as failures (BASE-02) | Pixel_8 and the Firebase emulators | All pass |
| Slowest instrumented tests | `RestockFlowTest.dropAddsExactlyOneItem…` 14.4 s, `PantryFlowTest.hundredItemsAddedAndVisibleOnBothPhones` 14.3 s, `AccessibilityChecksTest.mainScreensPassAccessibilityChecks` 13.2 s | JUnit XML | — |
| Firestore rules tests (`cd firebase && npm test`) | Pass, **80** tests in 20 suites, **10 s** (6.1 s of tests) | Firestore emulator through `emulators:exec` | All pass |
| Reminder job (`cd backend/reminder && npm test`) | Pass, **31** tests in 9 suites, 0.6 s | Node test runner | All pass |
| Reminder job, as CI runs it | `typecheck` and `build` pass (1 s); integration tests **9** pass, 11 s | `emulators:exec … "npm --prefix ../backend/reminder run test:integration"`, as in `ci.yml` | All pass |
| Web generated files (`npm run gen:check`) | Pass, 1 s, no drift from the Android sources | — | No diff |
| Web checks (`npm run check`) | Pass, **15 s**; Vitest **258** tests in 37 files (3.6 s) | Format, lint, types, unit tests, build | All pass |
| Web browser tests (`npm run e2e`) | **Fail**: 26 pass, **2 fail** (`screens light_font2`, `screens dark_font2`), **198 s**; the rerun of the 2 failed again with the same pixel counts (13 s) | WebKit, iPhone 15 profile, two workers, against the emulators | All pass |
| Slowest browser tests | `plan.spec.ts` 37 s, `recipes.spec.ts` "only web links open" 33 s, `notifications.spec.ts` "installed on the Home Screen" 27 s | Playwright list reporter | 60 s per test, 90 s for long flows (DESIGN 18.7) |
| Web build under hosting headers (`npm run e2e:preview`) | Pass, 1 test, **6 s** including the build | `vite preview` with `firebase.json`'s headers | Pass |
| Flaky tests | **None seen.** The only failures (BASE-01) are deterministic | Each suite once; failures rerun once | None |
| Total time of the finishing checks | **569 s (about 9.5 min)**, of which browser tests 198 s and instrumented tests 190 s | Sum of the rows above, without the benchmarks | — |

### Size and web performance

| Metric | Value | How measured | Target or budget |
| --- | --- | --- | --- |
| APK, dev benchmark build (release code, R8, debug-signed) | **4.97 MB** file (4,973,656 bytes), **4.29 MB** download (4,290,611 bytes). Compressed: dex 3.63 MB (7.92 MB unpacked), `resources.arsc` 0.64 MB, `google/` 0.22 MB, `res/` 0.19 MB, native libraries 67 kB, assets 58 kB (baseline profile included) | `assembleDevBenchmark` output, `apkanalyzer apk file-size` and `download-size` | — |
| APK, dev debug | 29.8 MB | `assembleDebug` output | — |
| Web main JS chunk (`index-*.js`) | **1,216.8 kB raw, 365.1 kB gzip** | `vite build` report | `chunkSizeWarningLimit` 1,200 kB: **exceeded** (BASE-05) |
| Web second JS chunk (`index.esm-*.js`) | 24.9 kB raw, 7.8 kB gzip | `vite build` report | — |
| Web CSS | 47.9 kB raw, 8.7 kB gzip | `vite build` report | — |
| Web fonts | 9 woff2 files, 118.1 kB (Manrope and Fredoka subsets) | `vite build` report | — |
| Service worker | 17.3 kB raw, 5.9 kB gzip | `vite build` report | — |
| Service worker precache | 21 entries, 1,488.5 KiB | vite-plugin-pwa report | — |
| Lighthouse 13.6.0, mobile preset, welcome screen of `vite preview` | Performance **92** median (87, 92, 93, 93, 92), accessibility **100**, best practices **100**; FCP 0.75 s, LCP 3.2 s median, TBT 0 ms median (530 ms on the first, cold run), CLS 0, speed index 1.36 s, 422 KiB transferred | RUNBOOK's command with Playwright's Chromium, five runs | DESIGN 18.6: 92–93, 100, 100; LCP 3.2 s |
| Lighthouse audits below 0.9 | Unused JavaScript (about 274 KiB could be saved), render-blocking requests (about 160 ms), network dependency tree | Run 1 | — |

### Runtime (Pixel_8 emulator, dev benchmark build, 500 batches)

**Not usable as a baseline** (BASE-08). Three runs of `./scripts/baseline-profile.sh --measure-only` gave three
different pictures: run A with the emulator's default `-gpu auto`, which chose SwiftShader (software rendering);
runs B and C after a restart with `-gpu host` (Apple M1 Pro through Metal), while the Mac was short of memory. A
fourth run was stopped: it couldn't have made a median meaningful. The numbers are kept so the rerun can see the
spread.

| Metric | Value | How measured | Target or budget |
| --- | --- | --- | --- |
| Cold start to Home, without the profile (median of 10) | A 1,089 ms · B 460 ms · C 4,937 ms | `StartupBenchmark.startupWithoutProfile`, `timeToInitialDisplayMs` | DESIGN 13.1: under 2.5 s on a mid-range phone. Last recorded (2026-10-31, headless software rendering): 691 ms |
| Cold start to Home, with the baseline profile | A 953 ms · B 4,251 ms · C 4,856 ms | `StartupBenchmark.startupWithBaselineProfile` | Last recorded: 623 ms |
| First Pantry scroll without the profile, frame CPU time P50 / P90 / P99 | A 48.7 / 65.4 / 78.9 ms · B 69.9 / 176.3 / 393.0 ms · C 98.4 / 237.4 / 536.3 ms | `PantryScrollBenchmark.firstScrollWithoutProfile`, 5 iterations | 16.7 ms per frame for 60 fps. Last recorded (2026-10-29): 5.3 / 8.0 / 19.0 ms |
| First Pantry scroll with the profile, frame CPU time P50 / P90 / P99 | A 48.4 / 65.2 / 73.7 ms · B 93.9 / 239.3 / 480.9 ms · C 82.5 / 270.7 / 605.8 ms | `…firstScrollWithBaselineProfile` | Last recorded: 4.9 / 7.1 / 28.3 ms |
| Frame overrun P50 with the profile | A 43.8 ms · B 128.2 ms · C 101.2 ms | `frameOverrunMs` | Below 0 (frame on time) |
| One benchmark run (seed, both benchmarks, both modes) | A 495 s · B 695 s · C 1,173 s | Wall time of the script | — |
| Emulator state during B and C | Guest load average 16 to 42 on 4 cores, 575 MB of guest swap; on the Mac the emulator's footprint was 9.4 GB with 1.3 GB resident, 1.5 GB of swap in use, about 70 MB free | `adb shell cat /proc/loadavg`, `top`, `memory_pressure`, `vm_stat` | — |

### Quota (`cd firebase && npm run quota-sim`)

| Metric | Value | How measured | Target or budget |
| --- | --- | --- | --- |
| Reads per day, 10 households | **19,520 = 39.0%** of 50,000 (app listeners 13,000, rule lookups 3,040, scheduled jobs 3,480) | quota-sim, 11 s | RUNBOOK 2026-10-28: 19,520 (39.0%). **Same** |
| Reads per household per day | **1,952** | quota-sim | RUNBOOK: about 1,950 |
| Writes per day, 10 households | **540 = 2.7%** of 20,000 (54 per household) | quota-sim | RUNBOOK: 540 (2.7%). **Same** |
| Deletes per day | **Not simulated** (BASE-06). Estimate from the sim's own assumptions: about 57 per household (40 activity events pruned after 30 days, 15 tombstones after 2 days, about 2 meals after 28 days), about 570 = 2.9% of 20,000 for 10 households | Reading `quota-sim.mjs` and `backend/reminder/src/job.ts` | 20,000 per day |
| Households under the 70% warning line | About 18 (reads are the limit) | 35,000 / 1,952 | RUNBOOK: about 18 |

### Dependencies

| Metric | Value | How measured | Target or budget |
| --- | --- | --- | --- |
| Gradle libraries and plugins | 38 versions checked, **35 current**. Behind: KSP 2.3.12 → 2.3.13; AGP 9.4.1 → 9.5.0 (held on purpose, DECISIONS 2026-10-31 and `dependabot.yml`); concurrent-futures 1.2.0 → 1.3.0 (pinned on purpose, DECISIONS 2026-10-18). Gradle 9.8.2 is current | `maven-metadata.xml` from Google Maven, Maven Central and the Gradle plugin portal; latest stable = highest version without alpha, beta, rc or dev | Latest stable (CLAUDE.md) |
| `npm outdated`, `web/` | 4: eslint 10.13.0 → 10.14.0, globals 17.13.0 → 17.14.0; @types/node 24 → 26 and typescript 6 → 7 are majors Dependabot skips on purpose | `npm outdated --json` | — |
| `npm outdated`, `firebase/` | 1: firebase-tools 15.34.1 → 15.34.2 | Same | — |
| `npm outdated`, `backend/reminder/` | 1: @types/node 24 → 26 (skipped on purpose) | Same | — |
| `npm audit`, `web/` | 24: 0 critical, **4 high**, 18 moderate, 2 low; production tree: 4 high | `npm audit --json`, and `--omit=dev` | — |
| `npm audit`, `firebase/` | 23: 0 critical, **6 high**, 17 moderate; production tree: 0 (all dev tools) | Same | — |
| `npm audit`, `backend/reminder/` | 6: 0 critical, 0 high, 6 moderate; production tree: 6 moderate | Same | — |
| Audit details | One item moved to the private notes | — | — |

### Docs and commands

| Metric | Value | How measured | Target or budget |
| --- | --- | --- | --- |
| CLAUDE.md commands that failed as written | **1**: `cd web && npm run e2e` (BASE-01, this month only) | Ran every command in "Before finishing a task" except `spotlessApply` (it rewrites files; `spotlessCheck` passed, so it would change nothing) and `LiveImportTest` (section 03) | 0 |
| CLAUDE.md lines that no longer match the code | **1**: `PantryRepository.apply` (BASE-04); the finishing checklist lacks 3 checks CI runs (BASE-03) | Reading against the code and `ci.yml` | 0 |
| RUNBOOK commands that failed as written | **0** of those run (`emulators.sh`, `npm run quota-sim`, `npm run e2e:preview`, the Lighthouse command, `baseline-profile.sh --measure-only`); bare `adb` isn't on PATH (BASE-07) | Running them; the rest only read (they touch real projects or GitHub) | 0 |
| RUNBOOK lines that no longer match | **2**: "the two npm projects" (three), the restore section without planned meals (BASE-07) | Reading against `dependabot.yml` and `backend/reminder/src/backup.ts` | 0 |
| Releases | Android beta `1.0.0-beta.9` (build 10, 2026-10-29): `main` has one user-facing change since (#69, app tour). Web app build `55edcc5` (2026-10-31): no web changes since | `git log v1.0.0-beta.9..0334b6a -- app/`, `git log 55edcc5..0334b6a -- web/` | — |
| Automated tests in total | **819 run** (370 Android unit, 42 instrumented, 80 rules, 40 reminder job, 258 web unit, 28 browser, 1 preview) | Sum of the rows above | README says "about 360" |

## Findings

### BASE-01 · P2 · Confirmed · Web — The Insights months screenshot test fails in every month but October

- **Where:** `web/tests/e2e/screens.spec.ts:109` and its references
  `web/tests/e2e/__screenshots__/screens.spec.ts/insights-months-{light,dark}-font2.png` (recorded 2026-10-29).
- **What happens:** the card shows the last six months. The test masks the month labels, but at 200% text the
  labels' widths decide where the columns sit, so a different set of months moves the bars. The references show
  May to Oct; since 1 November the card shows Jun to Nov, and both 200% images differ by 2% of their pixels (3,265
  and 3,302), over the 1% tolerance. The app is right; the test depends on the calendar. It isn't flaky: the rerun
  failed with the same pixel counts. The Android twin (`InsightsScreenshotTest`) fixes its date at 2026-10-18; the
  web test uses the real clock.
- **Evidence:** `cd web && npm run e2e` → "2 failed: screens light_font2, screens dark_font2"; `npx playwright test
  --last-failed` → the same two, same counts. The failure's page snapshot lists the tabs "June: 0 used…" to
  "November: 3 used, 1 thrown away". Images: `assets/00/insights-months-light-font2-expected.png`, `-actual.png`,
  `-diff.png`.
- **Who it affects:** whoever runs CLAUDE.md's finishing checklist, every month except October. Worse,
  `./scripts/deploy-web.sh` runs these tests with one retry and stops on a test that fails twice, so a web release
  this month needs `--skip-tests`, which skips every browser test. CI skips screenshot tests on Linux
  (`screens.spec.ts:22`), so CI stays green and won't flag it.
- **Suggested fix:** fix the browser's clock in the screens test (Playwright's `page.clock.setFixedTime`, with the
  seeded months computed from that date), as the Android test does; or give each month column a fixed width so its
  label can't move it. Effort S. Also needs: re-record the four `insights-months-*` images once.

### BASE-02 · P3 · Confirmed · Android — The instrumented test report shows two failures on every passing run

- **Where:** `app/src/androidTest/kotlin/com/parcha/spoilnot/feature/recipes/LiveImportTest.kt:57` and
  `app/src/androidTest/kotlin/com/parcha/spoilnot/perf/PantryScrollPerfTest.kt:80` (`assumeTrue`).
- **What happens:** both tests skip themselves unless the run passes `-e live true` or `-e perf true`. AGP writes an
  assumption failure into the JUnit XML and the HTML report as a failure, so a green
  `connectedDevDebugAndroidTest` (BUILD SUCCESSFUL) reports "44 tests, 2 failures, 95% successful". A real failure
  is easy to miss next to two expected ones, and test counts disagree (42 run; #71's title says 43).
- **Evidence:** `app/build/outputs/androidTest-results/connected/debug/flavors/dev/TEST-Pixel_8(AVD) - 17.xml` has
  `failures="2"`, both `AssumptionViolatedException: live import runs only with -e live true` and `perf runs only
  with -e perf true`; `app/build/reports/androidTests/connected/debug/flavors/dev/index.html` shows 95%.
- **Who it affects:** the owner and review sessions reading the report; CI doesn't run instrumented tests.
- **Suggested fix:** keep the two out of the default run rather than skipping inside it: mark them with an
  annotation and exclude it by default (`testInstrumentationRunnerArguments["notAnnotation"]`), with
  `baseline-profile.sh` and the `LiveImportTest` command in `docs/import-test-sites.md` asking for them explicitly.
  Effort S. Also needs: those two commands and the RUNBOOK updated.

### BASE-03 · P3 · Confirmed · Docs — CLAUDE.md's finishing checklist misses three checks CI runs

- **Where:** `CLAUDE.md`, "Before finishing a task"; `.github/workflows/ci.yml` (jobs `reminder-job` and `web`).
- **What happens:** CI runs the reminder job's type check, unit tests, build and integration test against the
  Firestore emulator, and the web build under the hosting headers (`playwright.preview.config.ts`). CLAUDE.md's list
  has none of these, and says `npm run gen` where CI runs `npm run gen:check` (which also fails on drift). A change
  to `backend/reminder` can pass every listed local check and still fail CI.
- **Evidence:** the two lists side by side. All these checks pass today (Metrics), so nothing is broken yet.
- **Who it affects:** every session that changes the reminder job or the web hosting setup.
- **Suggested fix:** add `cd backend/reminder && npm run typecheck && npm test && npm run build`, the integration
  command from `ci.yml`, and `npm run e2e:preview` to the list; or one script that runs the whole list (Ideas).
  Effort S.

### BASE-04 · P3 · Confirmed · Docs — CLAUDE.md names `PantryRepository.apply`, which left in M3

- **Where:** `CLAUDE.md:32` ("Patterns").
- **What happens:** the rule says `PantryRepository.apply` writes the `PantryChange` plans. `PantryRepository`
  (`feature/pantry/domain/PantryRepository.kt:66`) only observes now. Plans from every feature (`PantryChange`,
  `GroceryChange`, `RecipeChange`, `MealPlanChange`, `StatsChange`) go through `HouseholdWriter.apply`
  (`core/write/HouseholdWriter.kt:27`, implemented by `core/firebase/FirestoreHouseholdWriter.kt`), and `Undo` is
  defined there too (`HouseholdWriter.kt:10`). `git log -S "fun apply"` dates the move to M3 (`1ae3188`,
  2026-10-16); DESIGN 18.3 already says "applied by a `HouseholdWriter` port".
- **Evidence:** the files above.
- **Who it affects:** any session that follows CLAUDE.md to add a write: it looks for a method that isn't there and
  isn't told the rule covers every feature.
- **Suggested fix:** "Actions go through the feature's service (`PantryService`, `GroceryService`, …), which returns
  change plans; `HouseholdWriter.apply` writes them. Destructive actions return an `Undo`." Effort S.

### BASE-05 · P3 · Confirmed · Web — The main JS chunk is over the project's own size warning

- **Where:** `web/vite.config.ts:47-51`.
- **What happens:** the build sets `chunkSizeWarningLimit: 1200` with the comment "the Firebase SDK … most of the
  ~900 kB". The chunk is now 1,216.8 kB (365.1 kB gzip), so every `npm run check` and deploy prints "(!) Some chunks
  are larger than 1200 kB". A warning that always shows stops being read, so the next jump would go unnoticed.
  DECISIONS 2026-10-30 chose not to split the bundle; Lighthouse estimates 274 KiB of unused JavaScript on the
  welcome screen.
- **Evidence:** the `vite build` output in Metrics.
- **Who it affects:** the owner's builds (no user impact today: performance is still 92).
- **Suggested fix:** decide, and record it in DECISIONS: split the signed-in screens out of the welcome screen's
  download (section 09 can size it), or raise the limit just above today's size with a correct comment. Effort S to
  raise, M to split.

### BASE-06 · P3 · Confirmed · Backend — The quota simulation leaves out deletes

- **Where:** `firebase/tools/quota-sim.mjs:18` (`QUOTA = { reads, writes }`); RUNBOOK "Check quota usage".
- **What happens:** the Spark plan has three daily limits: 50,000 reads, 20,000 writes and 20,000 deletes. The sim
  reports reads and writes only, but the hourly job deletes every day (activity after 30 days, tombstones after 2,
  join requests after 7, meals 28 days after their day). From the sim's own assumptions that's about 57 deletes per
  household a day, 2.9% for 10 households: no risk today, but the baseline can't measure it and a change that
  deletes more wouldn't show.
- **Evidence:** the sim's output (Metrics) has no delete line; `backend/reminder/src/job.ts:63-70` has the
  retention periods.
- **Who it affects:** the owner's quota checks.
- **Suggested fix:** count the day's pruning as deletes in the sim and add a row to RUNBOOK's table. Effort S.

### BASE-07 · P3 · Confirmed · Docs — Three RUNBOOK lines drifted

- **Where and what:**
  - "Dependency updates" says Dependabot covers "the two npm projects"; `.github/dependabot.yml` covers three
    (`firebase/`, `backend/reminder/`, `web/`).
  - "Restore a household" lists products, stock, lists, recipes and the monthly counts; the restore also writes the
    planned meals back (`backend/reminder/src/backup.ts:83,123`; DESIGN 13.4).
  - "Baseline profile" says to look in `adb logcat`, and the review plan says `adb devices`, but `adb` isn't on PATH
    in fish or bash on this Mac; the scripts use `~/Library/Android/sdk/platform-tools/adb`.
- **Evidence:** the files named above; `fish -c 'command -v adb'` prints nothing.
- **Who it affects:** the owner when restoring or debugging a benchmark.
- **Suggested fix:** three line edits, or add `platform-tools` to CLAUDE.md's toolchain line. Effort S.

### BASE-08 · P2 · Confirmed · Android — The benchmarks don't give stable numbers on this Mac, so there's no performance baseline

- **Where:** RUNBOOK "Baseline profile"; `scripts/baseline-profile.sh`; the Pixel_8 emulator on the owner's Mac.
- **What happens:** three runs of the same build gave cold-start medians of 1,089, 460 and 4,937 ms (without the
  profile), and the profile looked 9 times *slower* in one run (4,251 against 460 ms). First-scroll frame CPU time
  was 49 to 98 ms at P50 against 4.9 ms recorded on 2026-10-29, and frames overran their deadline by 44 to 128 ms.
  Two causes were seen:
  1. The emulator's default `-gpu auto` chose SwiftShader, software rendering, even in a window (the emulator log
     says `gles_mode_selected:swangle`, "SwiftShader Device"). RUNBOOK warns about software rendering but doesn't
     say how to start the emulator, and the plan's launch line didn't ask for the GPU.
  2. With `-gpu host`, the Mac ran out of memory: the emulator's 4 GB guest (its `config.ini` says 2,048 MB, the
     running `hardware-qemu.ini` 4,096 MB) took a 9.4 GB footprint with only 1.3 GB resident, macOS had 1.5 GB in
     swap and about 70 MB free, and the guest's load average climbed from 16 to 42 on 4 cores. Results got worse
     run by run.
- **Evidence:** the Runtime table; `emulator -avd Pixel_8 … -gpu host` log: "Graphics Adapter … (Apple M1 Pro)";
  `adb shell dumpsys SurfaceFlinger | grep GLES` shows the renderer from the device side.
- **Who it affects:** section 08, the rerun of this section after the fixes, and every baseline profile update
  (DESIGN 17 records before-and-after numbers). It also hides the answer to a real question: whether the app's
  first scroll got slower since 2026-10-29. That stays open (**Question** for section 08: A/B against `f737440` on
  the same quiet setup, or on a phone).
- **Suggested fix:** a "benchmark setup" paragraph in RUNBOOK: start the emulator with `-gpu host`, close other
  large apps, check `memory_pressure` and the `GLES` line before measuring; have `baseline-profile.sh` refuse (or
  warn) when the renderer is SwiftShader; consider a 2 GB guest for benchmarks; or measure on the owner's Android
  phone, which Macrobenchmark supports (the dev benchmark build reaches the Mac's emulators with
  `-Pspoilnot.emulatorHost=<LAN IP>`). Keep the `*benchmarkData.json` files (Ideas). Effort S for the docs and
  the check, M for phone runs.

## Ideas

- **One command for the whole checklist.** A `scripts/check-all.sh` that runs CLAUDE.md's list and everything CI
  runs, in order, timing each step and printing a table like the Suite metrics above. It would fix BASE-03 for
  good (CLAUDE.md would name the script), and rerunning this section after the fixes would be one command.
- **Let new lint warnings show.** With 9 standing warnings and `abortOnError` failing only on errors, a new warning
  joins the pile unnoticed. Fix the small ones (`UseKtx`, `UnusedAttribute`), turn off the two version checks
  (`GradleDependency`, `AndroidGradlePluginVersion`: Dependabot already proposes updates, and the held versions have
  DECISIONS entries), decide the plurals, then set `warningsAsErrors = true`.
- **Keep benchmark results.** `baseline-profile.sh --measure` prints medians and deletes the JSON before each
  benchmark. Copying `*benchmarkData.json` to a dated folder would let a rerun be compared with this baseline without
  copying by hand.

## For other sections

- **05 Reliability:** the reminders rely on two Firebase Messaging APIs that compile with deprecation warnings under
  BoM 35.1.0: `FirebaseMessaging.getInstance().token`
  (`feature/notifications/data/FirebaseDeviceRegistry.kt:30`) and the `onNewToken` override
  (`feature/notifications/ui/SpoilNotMessagingService.kt:17`). Worth finding the replacement before a BoM removes them.
- **08 Smoothness:** there is no usable benchmark baseline (BASE-08). Before measuring, start Pixel_8 with
  `-gpu host`, check the renderer (`adb shell dumpsys SurfaceFlinger | grep GLES`) and the Mac's memory, and settle
  the open question: was Pantry's first scroll really about 5 ms per frame on 2026-10-29 and 50 to 100 ms now, or is
  that all environment? An A/B against `f737440` on the same quiet setup answers it.
- **09 Efficiency:** lint is the biggest part of the Android check (58 + 22 + 15 s of the 273 s of task time; it also
  lints the unit and instrumented test sources); `assembleDebug` builds prod debug too (`mergeExtDexProdDebug` 21 s);
  Gradle resolves four dependency configurations at configuration time; the web precache is 1,488.5 KiB and
  Lighthouse sees 274 KiB of unused JavaScript on the welcome screen.
- **10 Web:** Android's Insights screenshot test fixes its date and the web's doesn't (BASE-01), a test-setup parity
  gap worth a line in a parity checklist.
- **11 Code:** README says "It is Android only" and "about 360 automated tests" (819 ran today);
  `StartupBenchmark`'s KDoc says "under 2 s" where DESIGN 13.1 says 2.5 s; 15 unit test files use the deprecated
  `createComposeRule`; 7 unnecessary `!!` (main and tests); `UseKtx` in `DeltaSync.kt:88`;
  `baseline-profile.sh --measure-only` ends with "Review and commit … baseline-prof.txt" though it generated nothing.
- **06 UX:** lint flags four Insights strings as plural candidates (`insights_counts`, `insights_ring_description`,
  `insights_month_details`, `insights_month_entry`). "1 used · 1 thrown away" reads fine in English; worth a glance
  during the copy review.
- **01 Security:** the `npm audit` details are in `private/incidental.md` (00-S1).

## Not covered

- **Prod release builds**, by the ground rules (they sign with the upload key and upload mapping files). The APK size
  is the dev benchmark build's. For reference only, the beta.9 prod release APK the release script left in
  `app/build/outputs/apk/prod/release/` on 2026-10-29 is 4.91 MB (4,909,646 bytes).
- **`LiveImportTest`**: section 03 runs it against the real recipe sites. Its command in
  `docs/import-test-sites.md` was read, not run.
- **Commands that touch real projects or GitHub**, read only: rules deploys, `deploy-web.sh` (even `--dry-run` reads
  the project's web config from Firebase), `release-beta.sh`, App Distribution, the `gh workflow` and `gh secret`
  commands, the restore and the `openssl` decryption.
- **`./gradlew spotlessApply`**: it rewrites files; `spotlessCheck` passed, so it would change nothing.
- **CI's own durations and the real quota in the Firebase console**: section 09 and the owner.
- **Gradle dependency advisories** (Dependabot alerts): section 01.
- **A performance baseline**: see BASE-08; the cold start on a real phone is in the checks below.
- **Real phones**: the checks below.

## Phone checks for the owner

Note the phone model and OS version once in the Result column.

| # | Phone | Steps | Expected | Result |
| --- | --- | --- | --- | --- |
| 1 | Android | Open Spoil Not > Settings > About. | "Spoil Not 1.0.0-beta.9 (build 10)", the latest beta (2026-10-29). `main` has one user-facing change since (#69, the app tour opens each tab at its root). | |
| 2 | iPhone | Open the Home Screen web app > Settings > About. | "Spoil Not for iPhone, build 55edcc5", the build deployed to prod on 2026-10-31. No web changes since. | |
| 3 | Android | Cold start, three times: swipe Spoil Not away in Recents, wait 10 s, tap its icon, and time from the tap until Home shows its cards (not the splash). | Each under 2.5 s (DESIGN 13.1, QA.md section 4). Write all three times. | |
| 4 | iPhone | Cold start, three times: swipe the Home Screen app away in the app switcher, wait 10 s, tap its icon, and time from the tap until Home shows its cards. | No budget is set for the web app; write all three times, they become its baseline. | |
| 5 | Android | Right after a cold start, open Pantry and fling quickly to the bottom and back to the top, twice. (The emulator couldn't measure this today, BASE-08.) | Smooth, no visible stutter after the first pass (QA.md section 4). Note any stutter and whether it's only the first pass. | |
