#!/usr/bin/env bash
#
#   ./scripts/deploy-web.sh dev                 build, test and deploy the web app to https://spoilnot-dev.web.app
#   ./scripts/deploy-web.sh prod                the same for https://spoilnot-prod.web.app, from main, after asking
#   ./scripts/deploy-web.sh dev --skip-tests    skip the browser tests (they need the emulators' ports free)
#   ./scripts/deploy-web.sh dev --dry-run       build and check only; nothing is deployed
#
# The web app for iPhone (DESIGN 18.6). It reads the project's Web app config from Firebase (the Web app must be
# registered once in the console) and the public VAPID key for web push from web/.env.<dev|prod>.local
# (VITE_FIREBASE_VAPID_KEY, git-ignored; docs/RUNBOOK.md), runs the checks and the browser tests against the
# emulators, builds for the project, checks the build is for that project, offers to deploy the Firestore rules, and
# deploys Firebase Hosting. Production needs a clean main that matches GitHub and a yes.
set -euo pipefail
cd "$(dirname "$0")/.."

die() { echo "deploy-web: $*" >&2; exit 1; }
step() { printf '\n==> %s\n' "$*"; }
ask() { read -r -p "$1 [y/N] " answer; [[ "$answer" =~ ^[Yy]$ ]]; }
firebase() { (cd firebase && npx --no-install firebase "$@"); }

# --- Arguments ---
TARGET="${1:-}"
shift || true
SKIP_TESTS=false
DRY_RUN=false
for arg in "$@"; do
  case "$arg" in
    --skip-tests) SKIP_TESTS=true ;;
    --dry-run) DRY_RUN=true ;;
    *) die "unknown option $arg" ;;
  esac
done
case "$TARGET" in
  dev | prod) ;;
  -h | --help | '') sed -n '2,12p' "$0"; exit 0 ;;
  *) die "the target is dev or prod, not $TARGET" ;;
esac
PROJECT="spoilnot-$TARGET"
URL="https://$PROJECT.web.app"
ENV_FILE="web/.env.$TARGET.local"

# --- Checks ---
step "Checking the repository"
[ -z "$(git status --porcelain -- web firebase)" ] || die "commit or discard the changes in web/ and firebase/ first"
if [ "$TARGET" = prod ]; then
  [ "$(git branch --show-current)" = "main" ] || die "production deploys from main (git checkout main)"
  git fetch -q origin main
  [ "$(git rev-parse HEAD)" = "$(git rev-parse origin/main)" ] || die "main is not the same as GitHub (git pull)"
fi
[ -d web/node_modules ] || (cd web && npm ci)
[ -d firebase/node_modules ] || (cd firebase && npm ci)

# --- The project's Web app config (public: it ships in every page) ---
step "Reading the Web app config of $PROJECT"
APP_ID=$(firebase apps:list WEB --project "$TARGET" --json \
  | node -e 'const r = JSON.parse(require("fs").readFileSync(0, "utf8")).result ?? []; console.log(r[0]?.appId ?? "")')
[ -n "$APP_ID" ] || die "$PROJECT has no Web app: register one (Firebase console > Project settings > Your apps > Add app > Web)"
CONFIG=$(firebase apps:sdkconfig WEB "$APP_ID" --project "$TARGET" --json \
  | node -e 'const c = JSON.parse(require("fs").readFileSync(0, "utf8")).result?.sdkConfig; console.log(c ? JSON.stringify(c) : "")')
[[ "$CONFIG" == *"\"projectId\":\"$PROJECT\""* ]] || die "the Web app config is not for $PROJECT"
if ! grep -q '^VITE_FIREBASE_VAPID_KEY=.\+' "$ENV_FILE" 2>/dev/null; then
  echo "No VITE_FIREBASE_VAPID_KEY in $ENV_FILE: iPhones will get no reminders from this build (docs/RUNBOOK.md)."
  ask "Deploy without web push?" || die "add the key and run again"
fi

# --- Tests ---
step "Generated files, format, lint, types, unit tests and the build"
(cd web && npm run gen:check && npm run check)
if $SKIP_TESTS; then
  echo "Browser tests skipped (--skip-tests)."
else
  step "Browser tests against the emulators, then the build under the hosting headers (a few minutes)"
  (cd firebase && npx --no-install firebase emulators:exec --project demo-spoilnot --only auth,firestore \
    "cd ../web && npx playwright test && npx playwright test -c playwright.preview.config.ts")
fi

# --- Build for the project ---
step "Building for $PROJECT"
(cd web && VITE_FIREBASE_CONFIG="$CONFIG" npx vite build --mode "$TARGET" >/dev/null)
# The config is inlined at build time; without it the app would use the emulators' demo project.
grep -rqF "$APP_ID" web/dist/assets || die "the build does not carry the $PROJECT config"
echo "Built $(git rev-parse --short HEAD) for $PROJECT."

if $DRY_RUN; then
  step "Dry run: built and checked web/dist for $PROJECT. Nothing deployed."
  exit 0
fi

# --- Rules ---
if ask "Deploy firebase/firestore.rules to $PROJECT too (needed once for web push devices)?"; then
  firebase deploy --only firestore:rules --project "$TARGET"
fi

# --- Deploy ---
if [ "$TARGET" = prod ]; then
  ask "Deploy $(git rev-parse --short HEAD) to $URL for the testers?" || die "nothing was deployed"
fi
step "Deploying to $URL"
firebase deploy --only hosting --project "$TARGET" --message "web $(git rev-parse --short HEAD)"
step "Done: $URL (open it in Safari on the iPhone, then Share > Add to Home Screen)"
