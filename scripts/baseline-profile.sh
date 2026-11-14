#!/usr/bin/env bash
# Regenerates the app's baseline profile (DESIGN 13.1; docs/RUNBOOK.md, "Baseline profile").
#
#   ./scripts/baseline-profile.sh            seed, generate, copy into the app
#   ./scripts/baseline-profile.sh --measure  also run the startup and scroll benchmarks, without and with the profile
#   ./scripts/baseline-profile.sh --measure-only   only the benchmarks, with the profile already in the app
#   ./scripts/baseline-profile.sh --check    only the device checks below
#
# Before anything it checks the device (RUNBOOK, "Benchmark setup"): it refuses an emulator that draws with
# SwiftShader (software rendering), and warns when macOS throttles the emulator (App Nap), when the guest is still
# busy, or when the Mac is short of memory, since all of these make the numbers meaningless.
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
warn() { echo "baseline-profile: warning: $*" >&2; }
step() { printf '\n==> %s\n' "$*"; }

MEASURE=false
GENERATE=true
CHECK_ONLY=false
case "${1:-}" in
  --measure) MEASURE=true ;;
  --measure-only) MEASURE=true; GENERATE=false ;;
  --check) CHECK_ONLY=true ;;
  "") ;;
  *) die "unknown option $1 (use --measure, --measure-only or --check)" ;;
esac

# The guest's one-minute load under this means it has settled after boot or an install (review 08: under 2.5 on 4 cores).
SETTLED_LOAD=2.5
SETTLE_WAIT_S=300

preflight() {
  step "Checking the device"
  [ "$("$ADB" devices | awk 'NR > 1 && $2 == "device"' | wc -l | tr -d ' ')" = "1" ] \
    || die "connect exactly one Android emulator or phone"

  # Software rendering draws frames several times slower than any phone and drifts from run to run.
  local gles
  gles=$("$ADB" shell dumpsys SurfaceFlinger | grep -m 1 '^GLES:' || true)
  echo "Renderer: ${gles:-unknown}"
  if printf '%s' "$gles" | grep -qi 'swiftshader'; then
    die "the emulator draws with SwiftShader (software). Start it on the Mac's GPU, headless:
  ~/Library/Android/sdk/emulator/emulator -avd Pixel_8 -gpu host -feature -Uwb -no-window -no-snapshot -no-boot-anim -no-audio
(docs/RUNBOOK.md, \"Benchmark setup\")"
  fi

  if [ "$("$ADB" shell getprop ro.kernel.qemu | tr -d '\r')" = "1" ] || "$ADB" devices | grep -q '^emulator-'; then
    # A windowed emulator whose window isn't in front runs at macOS's background priority (4, not 31): App Nap.
    local pid pri
    for pid in $(pgrep -f 'qemu-system' || true); do
      pri=$(ps -o pri= -p "$pid" | tr -d ' ')
      if [ -n "$pri" ] && [ "$pri" -lt 31 ]; then
        warn "the emulator (pid $pid) runs at macOS priority $pri, under 31: macOS is throttling it (App Nap), so
frames take several times longer. Start it headless with -no-window (docs/RUNBOOK.md, \"Benchmark setup\")."
      fi
    done
  fi

  # Wait for the guest to settle (boot, installs, dex2oat), up to SETTLE_WAIT_S.
  local load waited=0
  while :; do
    load=$("$ADB" shell cat /proc/loadavg | awk '{print $1}')
    awk -v l="$load" -v m="$SETTLED_LOAD" 'BEGIN { exit !(l < m) }' && break
    if [ "$waited" -ge "$SETTLE_WAIT_S" ]; then
      warn "the device's load is still $load after ${SETTLE_WAIT_S} s (settled is under $SETTLED_LOAD); numbers may be slow"
      break
    fi
    echo "Waiting for the device to settle (load $load) ..."
    sleep 15
    waited=$((waited + 15))
  done
  echo "Device load: $load"

  if command -v memory_pressure >/dev/null; then
    local free
    free=$(memory_pressure | awk -F': ' '/free percentage/ { gsub(/%/, "", $2); print $2 }')
    if [ -n "$free" ] && [ "$free" -lt 20 ]; then
      warn "the Mac has only $free% memory free; close large apps (review 00 saw the emulator swap and slow down 10 times)"
    fi
  fi
  if pmset -g 2>/dev/null | grep -Eq 'lowpowermode +1'; then
    warn "Low Power Mode is on; turn it off for stable numbers"
  fi
}

preflight
$CHECK_ONLY && exit 0

curl -s -o /dev/null http://127.0.0.1:8080 || die "start the Firebase emulators first (./scripts/emulators.sh)"

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
