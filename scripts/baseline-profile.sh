#!/usr/bin/env bash
# Regenerates the app's baseline profile (DESIGN 13.1; docs/RUNBOOK.md, "Baseline profile").
#
#   ./scripts/baseline-profile.sh            seed, generate, copy into the app
#   ./scripts/baseline-profile.sh --measure  also run the startup and scroll benchmarks, without and with the profile
#   ./scripts/baseline-profile.sh --measure-only   only the benchmarks, with the profile already in the app
#
# Needs the Firebase emulators (./scripts/emulators.sh) and one Android emulator or phone (adb). It seeds the
# emulator-only 500-batch household (PantryScrollPerfTest), records which code the dev benchmark build runs while
# starting, scrolling Pantry and visiting every tab (benchmark module), and writes the result to
# app/src/main/baselineProfiles/baseline-prof.txt. Commit that file; release builds ship it.
set -euo pipefail
cd "$(dirname "$0")/.."

export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@21}"
ADB="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
OUT_DIR="benchmark/build/outputs/connected_android_test_additional_output"
PROFILE="app/src/main/baselineProfiles/baseline-prof.txt"
RUNNER="-Pandroid.testInstrumentationRunnerArguments"

die() { echo "baseline-profile: $*" >&2; exit 1; }
step() { printf '\n==> %s\n' "$*"; }

MEASURE=false
GENERATE=true
case "${1:-}" in
  --measure) MEASURE=true ;;
  --measure-only) MEASURE=true; GENERATE=false ;;
  "") ;;
  *) die "unknown option $1 (use --measure or --measure-only)" ;;
esac

curl -s -o /dev/null http://127.0.0.1:8080 || die "start the Firebase emulators first (./scripts/emulators.sh)"
[ "$("$ADB" devices | awk 'NR > 1 && $2 == "device"' | wc -l | tr -d ' ')" = "1" ] \
  || die "connect exactly one Android emulator or phone"

step "Seeding the 500-batch household (signs in if it already exists)"
./gradlew -q connectedDevDebugAndroidTest "$RUNNER.class=com.parcha.spoilnot.perf.PantryScrollPerfTest" "$RUNNER.perf=true"

if $GENERATE; then
  step "Generating the profile (several minutes)"
  rm -rf "$OUT_DIR"
  ./gradlew -q :benchmark:connectedBenchmarkAndroidTest \
    "$RUNNER.class=com.parcha.spoilnot.benchmark.BaselineProfileGenerator"
  generated=$(find "$OUT_DIR" -name '*BaselineProfileGenerator_generate-baseline-prof*.txt' | awk 'NR == 1')
  [ -n "$generated" ] || die "the generator wrote no profile under $OUT_DIR"
  mkdir -p "$(dirname "$PROFILE")"
  cp "$generated" "$PROFILE"
  echo "Wrote $PROFILE ($(wc -l <"$PROFILE" | tr -d ' ') rules)"
fi

if $MEASURE; then
  step "Measuring startup and the first Pantry scroll, without and with the profile"
  for benchmark in StartupBenchmark PantryScrollBenchmark; do
    rm -rf "$OUT_DIR"
    ./gradlew -q :benchmark:connectedBenchmarkAndroidTest "$RUNNER.class=com.parcha.spoilnot.benchmark.$benchmark"
    find "$OUT_DIR" -name '*benchmarkData.json' -exec python3 -I scripts/benchmark-summary.py {} +
  done
fi
step "Done. Review and commit $PROFILE."
