# Spoil Not

A shared household app for Android that tracks your food, warns you before it expires and builds your grocery list.
See [docs/DESIGN.md](docs/DESIGN.md) for the full design and [docs/DECISIONS.md](docs/DECISIONS.md) for deviations.

## Develop

Needs Android Studio (SDK 37), JDK 21 and Node 24.

```bash
./scripts/emulators.sh                  # Firebase Auth + Firestore emulators, UI at http://127.0.0.1:4000
./gradlew installDevDebug               # dev build, talks to the emulators
./gradlew spotlessCheck lintDevDebug testDevDebugUnitTest assembleDebug
(cd firebase && npm test)               # Firestore rules tests
./gradlew connectedDevDebugAndroidTest  # needs both emulators running
```
