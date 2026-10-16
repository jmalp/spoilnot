# Decisions and deviations

Every deviation from DESIGN.md, and every choice DESIGN.md left open, with the date and reason.

| Date | Decision | Reason |
| --- | --- | --- |
| 2026-10-15 | Firestore region **us-east1** for both `spoilnot-dev` and `spoilnot-prod`. | Chosen by the owner (DESIGN 15 left it open). Cannot be changed later. |
| 2026-10-15 | New products default to **imperial** units. | Chosen by the owner (DESIGN 15 left it open). |
| 2026-10-15 | Font: **Manrope** variable font, bundled in `res/font`, licence in `assets/licenses/manrope-OFL.txt`. | Owner approved; DESIGN 6.4 allowed Manrope or Inter. |
| 2026-10-15 | All emulator work uses the project ID **`demo-spoilnot`**, not `spoilnot-dev`. | `demo-` projects are emulator-only, so tests cannot reach real data even by mistake, and no `firebase login` is needed. `firebase/.firebaserc` keeps `dev` and `prod` aliases for deploying rules. |
| 2026-10-15 | Both `google-services.json` files are committed (`app/src/dev`, `app/src/prod`). | They hold identifiers, not secrets (DESIGN 12.2), and CI needs them to build. Follow-up for M7: restrict the Android API keys in the Google Cloud console to the package names. |
| 2026-10-15 | Light **Soon** status color is **#A05F00** instead of #B26A00. | #B26A00 is only 4.04:1 on the light surface, below the 4.5:1 that DESIGN 6.4 requires. #A05F00 is the same hue at 4.84:1. Enforced by `StatusColorsContrastTest`. |
| 2026-10-15 | Theme colors generated with Material Color Utilities 0.3.0 (tonal spot scheme, seed #2E7D5B). | This is the library behind Material Theme Builder; output is identical to its default scheme. The newest npm release (0.4.x) has a broken ESM import. |
| 2026-10-15 | Dev debug builds declare `ACCESS_LOCAL_NETWORK` and request it once at startup; instrumented tests grant it. A dev-debug-only network security config allows cleartext to 10.0.2.2, localhost and 127.0.0.1. | Android 17 blocks apps targeting API 37 from private-network addresses such as the emulator host, and the emulators speak plain HTTP. Prod and release builds never declare the permission and stay TLS only (DESIGN 12.2). |
| 2026-10-15 | Backups disabled: `allowBackup=false` plus data-extraction and full-backup rules that exclude everything. | Keeps the Firestore cache from moving to other devices (DESIGN 12.2, controls 5 and 6). Required by lint on API 31+. |
| 2026-10-15 | CI runs `spotlessCheck lintDevDebug testDevDebugUnitTest assembleDebug` instead of `lintDebug testDebugUnitTest`. | With product flavors AGP has no aggregate `lintDebug` or `testDebugUnitTest` task. Unit tests and lint are flavor-independent today; `assembleDebug` still builds both flavors. |
| 2026-10-15 | ktlint uses the `android_studio` style with trailing commas allowed, configured in the root `build.gradle.kts`. | Spotless did not pick up `.editorconfig` sections; trailing commas keep diffs small. |
| 2026-10-15 | Icons come from `material-icons-extended` (Rounded and Outlined Material Icons). | Material Symbols are not published as a Compose artifact; the extended Material Icons set is the closest maintained equivalent. R8 strips unused icons in release builds. |
| 2026-10-15 | Crashlytics, splash screen, DataStore, Coil and screenshot tests (Roborazzi) are deferred to the milestones that need them (M2 and M6). | M0 has no screens with content to snapshot and no settings to store; adding them now would be untested scaffolding. |
| 2026-10-15 | Settings route carries its tab (`SettingsRoute(tab)`). | Each tab can open Settings on its own back stack; a shared key would collide in saved state. |
| 2026-10-15 | `compileSdk` and `targetSdk` 37, AGP 9.4.1 (built-in Kotlin), Kotlin 2.4.21, Gradle 9.8.1, Compose BOM 2026.09.00, Material 3 1.4.0, Navigation 3 1.2.0, Firebase BoM 35.0.2, Hilt 2.60.1. | Latest stable versions on 2026-10-15 (DESIGN 0 and 7.3). |
