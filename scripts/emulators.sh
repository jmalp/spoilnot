#!/usr/bin/env bash
# Starts the Firebase Emulator Suite (Auth on 9099, Firestore on 8080, UI on http://127.0.0.1:4000) with the
# emulator-only project demo-spoilnot. Data persists in firebase/.emulator-data between runs.
set -euo pipefail
cd "$(dirname "$0")/../firebase"
if [ ! -d node_modules ]; then npm ci; fi
exec npm run emulators
