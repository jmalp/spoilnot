# Spoil Not
- Source of truth: docs/DESIGN.md. Deviations go in docs/DECISIONS.md (date, decision, reason).
- Android only. Stack: Kotlin, Jetpack Compose with Material 3, Navigation 3, Hilt, Coroutines and Flow, Firebase (Auth, Firestore, FCM) on the free Spark plan. Latest stable versions through gradle/libs.versions.toml.
- Never add Cloud Functions, Cloud Storage or any paid Google Cloud service. Never commit secrets.
- Composables and ViewModels never use Firebase classes. Only feature/*/data/ repositories touch Firestore (core/firebase only provides the SDK instances).
- Business logic is pure Kotlin in feature/*/domain/ with an injectable java.time.Clock and unit tests.
- Dates are 'YYYY-MM-DD' strings; timestamps are server timestamps.
- Every destructive action has Undo. Status is never conveyed by color alone.
- All user-facing text lives in app/src/main/res/values/strings.xml.

## Toolchain (macOS, fish shell)
- JDK 21 (Homebrew openjdk@21) and Node 24 LTS (Homebrew node@24) are on the fish PATH. In non-fish shells:
  `export JAVA_HOME=/opt/homebrew/opt/openjdk@21 PATH=/opt/homebrew/opt/node@24/bin:$PATH`
- Flavors: `dev` (com.parcha.spoilnot.dev, Firebase project spoilnot-dev) and `prod` (com.parcha.spoilnot, spoilnot-prod).
- Aggregate `lintDebug`/`testDebugUnitTest` tasks don't exist with flavors; use the dev variants below.

## Emulators
- `./scripts/emulators.sh` starts Auth (9099), Firestore (8080) and the UI (http://127.0.0.1:4000) with the emulator-only project `demo-spoilnot`.
- Dev debug builds connect to them at 10.0.2.2 (`-Pspoilnot.emulatorHost=<LAN IP>` for a physical phone). All other builds use real Firebase.
- On Android 17+ dev debug builds need ACCESS_LOCAL_NETWORK to reach 10.0.2.2; the app asks once and tests grant it.

## Before finishing a task
- `./gradlew spotlessApply` then `./gradlew spotlessCheck lintDevDebug testDevDebugUnitTest assembleDebug`
- `cd firebase && npm test` (rules tests in the Firestore emulator)
- With emulators and an Android emulator running: `./gradlew connectedDevDebugAndroidTest`
