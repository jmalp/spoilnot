#!/usr/bin/env bash
# Releases a new beta to the testers in Firebase App Distribution (docs/RUNBOOK.md, "Beta releases").
#
#   ./scripts/release-beta.sh "What changed"                 next beta (1.0.0-beta.3 becomes 1.0.0-beta.4)
#   ./scripts/release-beta.sh --name 1.0.0 "First release"   a chosen version name
#   ./scripts/release-beta.sh --dry-run "Notes"              build and check only; nothing is uploaded or committed
#
# It checks that main is clean and up to date, raises versionCode and versionName, builds the prod release signed
# with the upload key (keystore.properties, which stays on this Mac), refuses any other signature, uploads it to
# the testers listed in .beta-testers (one email per line, never committed), deploys the Firestore rules if they
# changed since the last release (after asking), then commits the version, tags it (v<versionName>) and pushes.
set -euo pipefail
cd "$(dirname "$0")/.."

APP_ID="1:977467444046:android:2e84de68984d0dd33cec1f"   # spoilnot-prod Android app
# SHA-256 of the upload certificate (public; docs/SECURITY_REVIEW.md). Testers' phones only accept updates with it.
UPLOAD_CERT_SHA256="a4e5c881875d0b89bcc71421eb7cc1a7483f1da2c7de2d5f96ab1511d9133f5d"
GRADLE_FILE="app/build.gradle.kts"
TESTERS_FILE=".beta-testers"
APK="app/build/outputs/apk/prod/release/app-prod-release.apk"
# Local-only edits that may stay uncommitted during a release.
ALLOWED_CHANGES="${RELEASE_ALLOW_CHANGES:-gradle.properties}"

export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@21}"
export PATH="/opt/homebrew/opt/node@24/bin:$PATH"

die() { echo "release-beta: $*" >&2; exit 1; }
step() { printf '\n==> %s\n' "$*"; }

# --- Arguments ---
DRY_RUN=false
NAME=""
NOTES=""
while [ $# -gt 0 ]; do
  case "$1" in
    --dry-run) DRY_RUN=true ;;
    --name) shift; NAME="${1:-}" ;;
    -h|--help) sed -n '2,12p' "$0"; exit 0 ;;
    *) NOTES="$1" ;;
  esac
  shift
done
[ -n "$NOTES" ] || die 'release notes are required, for example: ./scripts/release-beta.sh "Fixed the grocery list"'

# --- Checks ---
step "Checking the repository"
[ "$(git branch --show-current)" = "main" ] || die "switch to main first (git checkout main)"
changes=$(git status --porcelain | awk '{print $2}' | grep -vxF -f <(tr ' ' '\n' <<<"$ALLOWED_CHANGES") || true)
[ -z "$changes" ] || die "commit or discard these changes first: $(echo $changes)"
git fetch -q origin main
[ "$(git rev-parse HEAD)" = "$(git rev-parse origin/main)" ] || die "main is not the same as GitHub (git pull)"
[ -f keystore.properties ] || die "keystore.properties is missing: the release must be signed with the upload key"
if ! $DRY_RUN; then
  [ -f "$TESTERS_FILE" ] || die "create $TESTERS_FILE with one tester email per line"
  TESTERS=$(grep -vE '^[[:space:]]*(#|$)' "$TESTERS_FILE" | tr -d ' ' | paste -sd, -)
  [ -n "$TESTERS" ] || die "$TESTERS_FILE has no emails"
fi

# --- Version ---
old_code=$(grep -E '^[[:space:]]*versionCode = [0-9]+' "$GRADLE_FILE" | grep -oE '[0-9]+')
old_name=$(grep -E '^[[:space:]]*versionName = "' "$GRADLE_FILE" | sed -E 's/.*"(.*)".*/\1/')
new_code=$((old_code + 1))
if [ -z "$NAME" ]; then
  [[ "$old_name" =~ ^(.*-beta\.)([0-9]+)$ ]] || die "cannot work out the next name after $old_name; pass --name"
  NAME="${BASH_REMATCH[1]}$((BASH_REMATCH[2] + 1))"
fi
git rev-parse -q --verify "refs/tags/v$NAME" >/dev/null && die "tag v$NAME already exists"
step "Version $old_name ($old_code) -> $NAME ($new_code)"
restore_version() { git checkout -q -- "$GRADLE_FILE"; }
trap 'restore_version' ERR
sed -i '' -E "s/^([[:space:]]*versionCode = )[0-9]+/\1$new_code/; s/^([[:space:]]*versionName = )\".*\"/\1\"$NAME\"/" "$GRADLE_FILE"

# --- Build and verify ---
step "Building the prod release (a few minutes)"
./gradlew -q assembleProdRelease
BUILD_TOOLS=$(ls -d "${ANDROID_HOME:-$HOME/Library/Android/sdk}"/build-tools/* | sort -V | tail -1)
cert=$("$BUILD_TOOLS/apksigner" verify --print-certs "$APK" | grep -m1 'SHA-256 digest' | awk '{print $NF}')
[ "$cert" = "$UPLOAD_CERT_SHA256" ] || die "the APK is not signed with the upload key (got $cert); nothing was uploaded"
badging=$("$BUILD_TOOLS/aapt2" dump badging "$APK" | head -1)
[[ "$badging" == *"name='com.parcha.spoilnot'"*"versionCode='$new_code'"*"versionName='$NAME'"* ]] \
  || die "unexpected APK: $badging"
echo "Signed with the upload key: com.parcha.spoilnot $NAME ($new_code)"

if $DRY_RUN; then
  restore_version
  trap - ERR
  step "Dry run: built and checked $APK; version left at $old_name ($old_code). Nothing uploaded or committed."
  exit 0
fi

# --- Rules ---
last_tag=$(git describe --tags --abbrev=0 --match 'v*' 2>/dev/null || true)
if [ -z "$last_tag" ] || ! git diff --quiet "$last_tag" HEAD -- firebase/firestore.rules; then
  step "firebase/firestore.rules changed since ${last_tag:-the first release}"
  read -r -p "Deploy the rules to spoilnot-dev, then spoilnot-prod? [y/N] " answer
  if [[ "$answer" =~ ^[Yy]$ ]]; then
    (cd firebase && npx --no-install firebase deploy --only firestore:rules --project dev \
      && npx --no-install firebase deploy --only firestore:rules --project prod)
  else
    echo "Rules not deployed. Deploy them before testers use anything that needs them (docs/RUNBOOK.md)."
  fi
fi

# --- Upload ---
step "Uploading to App Distribution ($(tr ',' '\n' <<<"$TESTERS" | wc -l | tr -d ' ') tester(s))"
(cd firebase && npx --no-install firebase appdistribution:distribute "../$APK" --app "$APP_ID" --project prod \
  --testers "$TESTERS" --release-notes "$NOTES") | grep -E '✔|Error' | grep -v 'Download the release binary'

# --- Record ---
trap - ERR
step "Committing and tagging v$NAME"
git add "$GRADLE_FILE"
git commit -q -m "Release $NAME ($new_code)" -m "$NOTES"
git tag -a "v$NAME" -m "Spoil Not $NAME ($new_code): $NOTES"
git push -q origin main "v$NAME"
step "Done: $NAME ($new_code) is with the testers. To make older builds update, raise minSupportedBuild to $new_code in Firestore config/app (spoilnot-prod)."
