# Runbook

Operational tasks for Spoil Not. The design is in `DESIGN.md`; deviations are in `DECISIONS.md`.

## Reminder job (DESIGN 10)

The hourly job lives in `backend/reminder` and runs from `.github/workflows/reminders.yml` at minute 7 of every
hour, once per Firebase project:

| Project | GitHub secret |
| --- | --- |
| `spoilnot-prod` (testers) | `FIREBASE_SERVICE_ACCOUNT_JSON` |
| `spoilnot-dev` (development, optional) | `FIREBASE_SERVICE_ACCOUNT_JSON_DEV` |

A project whose secret is missing logs "nothing to do" and succeeds. A key stored under the wrong secret fails the
run with "This key is for project …", and every run logs the project it served.

### Create the service account (once per Firebase project)

1. Open https://console.cloud.google.com and pick the project (`spoilnot-dev` or `spoilnot-prod`) at the top.
2. **IAM & Admin → Service Accounts → + Create service account**. Name: `reminder-job`. **Create and continue**.
3. Grant two roles, then **Done**:
   - **Cloud Datastore User** (read and write Firestore),
   - **Firebase Cloud Messaging API Admin** (send notifications).
4. Open the account → **Keys → Add key → Create new key → JSON → Create**. A `.json` file downloads.
5. Store it as the repository secret, then delete the downloaded file:
   ```bash
   # spoilnot-prod
   gh secret set FIREBASE_SERVICE_ACCOUNT_JSON --repo jmalp/spoilnot < ~/Downloads/<file>.json
   # spoilnot-dev
   gh secret set FIREBASE_SERVICE_ACCOUNT_JSON_DEV --repo jmalp/spoilnot < ~/Downloads/<file>.json
   ```
6. In the Firebase console → Project settings → Cloud Messaging, check that **Firebase Cloud Messaging API (V1)**
   is enabled.

Never commit the key or put it anywhere else. Do not link a billing account (DESIGN 3).

### Rotate the key (yearly)

Create a new key (step 4), replace the secret (step 5), run the workflow once by hand (below), then delete the old
key under **Keys** in the console. The nightly export and the restore workflow use the same prod secret, so they
pick up the new key too.

### Run the job by hand

GitHub → **Actions → expiry-reminders → Run workflow**. "Dry run" is ticked by default: the log shows each digest
that would be sent and nothing is changed. Untick it for a live run. From a terminal:

```bash
gh workflow run reminders.yml -f dry_run=true
```

### If reminders stop

- **Actions → expiry-reminders**: look for failed runs. Make sure Actions failure emails are on
  (github.com/settings/notifications → Actions).
- Scheduled runs can be late or skipped at busy times; the job catches up until 22:00 in each user's time zone and
  never sends twice a day.
- The log line `skippedNoDevices` counts users whose phones have not registered (they have not opened the app
  since installing, or notifications are blocked).

## Firestore rules

Rules live in `firebase/firestore.rules` and are tested by `cd firebase && npm test`. Deploy after merging to
`main`:

```bash
cd firebase && npx firebase deploy --only firestore:rules --project dev
cd firebase && npx firebase deploy --only firestore:rules --project prod
```

Both projects run the same rules; deploy to dev first. `spoilnot-prod` was opened for the beta on 2026-10-18.

## Beta releases (Firebase App Distribution)

A merge does not update the app on phones: a release must be signed with the upload key, which stays on the
owner's Mac. One command does the rest:

```bash
./scripts/release-beta.sh "What changed, in a sentence or two"
```

It:

1. checks that you are on `main`, with no uncommitted changes (`gradle.properties` is allowed) and the same as
   GitHub;
2. raises `versionCode` by one and the beta number in `versionName` (`1.0.0-beta.3` becomes `1.0.0-beta.4`; use
   `--name 1.0.0` for a chosen name);
3. builds the prod release and **refuses to upload** unless it is signed with the upload key;
4. asks to deploy the Firestore rules (dev, then prod) if `firebase/firestore.rules` changed since the last release;
5. uploads it to everyone in `.beta-testers` (one email per line; the file is git-ignored because the emails are
   personal) with your notes;
6. commits the new version to `main`, tags it `v<versionName>` and pushes both.

`--dry-run` builds and checks only: nothing is uploaded, committed or tagged, and the version is left as it was.
If any step fails, the version change is undone and nothing is committed.

To make older builds stop working until updated, raise `minSupportedBuild` in Firestore `config/app`
(spoilnot-prod) to the new `versionCode` after the testers have it.

**Required for the release with changes-only listeners (DESIGN 9.8):** right after the release, raise
`minSupportedBuild` to that release's `versionCode`. Older builds show deleted items again (tombstones) and delete
without leaving one, so they must update.

**New tester:** add their email to `.beta-testers`. To invite them to the current release without a new build:

```bash
cd firebase && npx firebase appdistribution:distribute ../app/build/outputs/apk/prod/release/app-prod-release.apk \
  --app 1:977467444046:android:2e84de68984d0dd33cec1f --project prod --testers "<email>"
```

(This re-uses the existing release when the APK is the one last uploaded; otherwise run the script.)

## Web app for iPhone (DESIGN 18)

Everything is in `web/` and needs Node 24. Once: `cd web && npm install && npx playwright install webkit`.

- `npm run dev` serves the app at http://localhost:5173 against the Firebase emulators (`./scripts/emulators.sh`).
  An iPhone on the same Wi-Fi can open `http://<the Mac's IP>:5173`; the emulators listen on the network too.
- `npm run check` runs format, lint, type check, unit tests and the build, as CI does. `npm run format` fixes
  formatting.
- `npm run e2e` (with the emulators running) runs the WebKit tests with an iPhone profile, two browser contexts
  as two phones (tests seed data through the emulators' REST APIs, `tests/e2e/support.ts`), and compares the
  screenshots in
  `tests/e2e/__screenshots__/`; after an intended change, `npm run e2e:update` and look at the new images in light,
  dark and 200% text next to the Android ones.
- After changing `strings.xml`, the Android theme colors or `icons.txt`: `npm run gen` (CI fails until it is run).
  After changing the launcher icon: `npm run gen && node scripts/gen-app-icons.ts`.

Deploying (Firebase Hosting) comes with milestone W7.

## Baseline profile (DESIGN 13.1)

Release builds ship `app/src/main/baselineProfiles/baseline-prof.txt`, a list of the code the app runs while
starting and on its busiest screens, so Android can compile it ahead of time. Phones that install the APK from App
Distribution get it through `androidx.profileinstaller` and compile it during their next idle maintenance (often
overnight), so the first day may still feel like before.

Regenerate it after large UI changes (new screens, a new list), with the Firebase emulators running
(`./scripts/emulators.sh`) and one Android emulator or phone connected:

```bash
./scripts/baseline-profile.sh
```

It seeds the emulator-only 500-batch household (`PantryScrollPerfTest`), runs `BaselineProfileGenerator` in the
`benchmark` module against the dev benchmark build (start, sign in, every tab, Insights, Pantry scrolls, Add item, Plan's add sheet), and
copies the result into the app (the benchmarks tap Skip tour when the app tour shows). Review the diff and commit it. `--measure` also runs `StartupBenchmark` and
`PantryScrollBenchmark` without and with the profile and prints the medians; `--measure-only` runs just the
benchmarks with the profile already in the app. Emulator numbers compare builds; they
are not phone timings.

## Nightly export and restore (DESIGN 13.4)

`nightly-export.yml` runs at 07:17 UTC (about 3 AM Eastern) and saves every spoilnot-prod household as
`<householdId>.json`, encrypted together as `household-exports.tar.gz.enc` in an artifact called `household-exports`,
kept 14 days. The format inside is the same as the app's Settings > Export file. The repository is public, and anyone
signed in to GitHub can download artifacts, so the job encrypts with the `EXPORT_ENCRYPTION_KEY` secret (AES-256,
PBKDF2) and fails rather than upload anything unencrypted.

**Set up the key once** (and again only to rotate it; older exports then need the old key):

```bash
openssl rand -base64 32 | tee /dev/tty | gh secret set EXPORT_ENCRYPTION_KEY --repo jmalp/spoilnot
```

Save the printed key in your password manager: it is the only way to open an export outside GitHub. To open one on a
computer: `openssl enc -d -aes-256-cbc -pbkdf2 -iter 600000 -in household-exports.tar.gz.enc | tar -xzf -` (it asks
for the key).

To find an export: **Actions → nightly-export →** pick a run (its URL ends with the run ID) **→ Artifacts**.

### Restore a household

This is also the way back after an owner's **Clear household data** (Settings): pick the export from the night before
the clear. It brings back products, stock, lists and their items, recipes and the monthly counts; the cleared
activity does not come back.

Restoring writes back the export's products, stock, grocery lists and their items, and recipes, exactly as they
were. It never changes the household document or its members (they control access), and it leaves documents created
after the export alone. Anything deleted since the export comes back; anything changed since goes back to the
exported version.

1. **Actions → restore-from-export → Run workflow.** Enter the export run's ID and leave the household empty: the
   log lists the first four characters of each household ID in that export with its document counts (nothing is
   written). Logs are public, so full IDs are never printed; the full ID is in the Firebase console under
   `households`.
2. Run it again with the first characters of the household ID (four or more, enough to match one export) and
   "apply" unticked. The log shows how many documents would be written, by kind.
3. Run it again with **apply** ticked.
4. To restore into a different household (for example after the original was deleted, recreate one in the app,
   then find its ID in the Firebase console under `households`), fill in **target household**.

From a terminal: `gh workflow run restore.yml -f export_run_id=<id> -f household_id=<first characters> -f apply=false`.

A file saved from Settings > Export can be restored the same way from a computer that has the service account key
in `FIREBASE_SERVICE_ACCOUNT_JSON` (`cd backend/reminder && npm ci && npm run build && node dist/restore-main.js
<file> <householdId>`), but the workflow is preferred because the key never leaves GitHub.

## Regenerate an invite code by hand

Normally the owner rotates the code in the app (Settings > Household > invite card > Rotate), which also cancels
the old one. If that is not possible, in the Firebase console → Firestore:

1. Choose a new code: 10 characters from `ABCDEFGHJKLMNPQRSTUVWXYZ23456789` (no I, O, 0 or 1).
2. `inviteCodes` → **Add document** with that code as the document ID and fields `householdId` (string),
   `householdName` (string), `active` (boolean, true), `createdBy` (the owner's uid), `createdAt` (timestamp).
3. `households/<id>`: set `inviteCode` to the new code.
4. Delete the old `inviteCodes/<old code>` document.

## Check quota usage (DESIGN 9.7)

Firebase console → the project → **Usage and billing → Usage** (or Firestore → Usage) shows reads, writes and
deletes per day against the free limits (50,000 reads, 20,000 writes, 20,000 deletes). Look at it after adding a
household or a new feature.

`cd firebase && npm run quota-sim` replays a day of 10 typical households against the emulator. Result on
2026-10-28 with changes-only listeners, Use it up, changes-only Recent activity and the meal planner (30 products, 60 batches, 25 grocery items, 15 recipes, this
month's counts, 30 tombstones and 42 planned meals (12 this week) per household; 4 phones opening the app 8 times a
day, the first open a full read, Home reading recipes and this week's meals on every open, the 40 daily writes spread between opens, each logging an activity event, 10 of
them finishing a batch):

| Reads | Per day | Share of 50,000 |
| --- | --- | --- |
| App listeners | 13,000 | |
| Security-rule lookups | 3,040 | |
| Scheduled jobs (reminders, housekeeping, export) | 3,480 | |
| **Total for 10 households** | **19,520** | **39.0%** |
| Per household | about 1,950 | 3.9% |

Writes were 540 (2.7%). Before changes-only listeners it was 47,570 reads (95.1%; about 4,760 per household), and
19,320 before Recent activity read only new events, 17,920 before the meal planner. Part 2 of the planner (week's
grocery list, Cooked, Use it up while picking) left it at 19,440: those sheets listen to the pantry and lists only while
open, which the cache already holds from Home and Pantry. Part 3 adds the digest's query for today's meals (about 2
reads per user a day): 19,520. So the free plan holds about 18
households of this size under the 70% warning line. The largest remaining costs are each phone's daily full read and rule lookups. If usage nears 70%: check for a
regression with the simulation, then consider the Blaze plan (DESIGN 9.7; budget alerts notify but do not cap).

## Scheduled workflows

`expiry-reminders` (hourly), `nightly-export` (daily) and `secret-scan` (weekly) run on schedules. GitHub disables
scheduled workflows in a public repository after 60 days without activity, and shows a banner on the Actions tab.
`keepalive.yml` re-enables them (and itself) through the API on the 3rd of every month, which resets that clock. If
one is disabled anyway: **Actions →** the workflow **→ Enable workflow** (or
`gh workflow enable reminders.yml --repo jmalp/spoilnot`). Then run it once by hand to check.

**Logs are public.** Workflow logs show counts, dates and the first characters of household IDs only: the reminder
job logs neither user IDs nor what is in a digest (a dry run shows how many items and meals, never names or titles), and
the export and restore print counts. Keep it that way when changing the jobs.

## Dependency updates

Dependabot (`.github/dependabot.yml`) opens grouped pull requests on Mondays for Gradle, the two npm projects and
GitHub Actions. Merge one when CI is green; for an app library update, also install a build on a phone before the
next beta. Security updates arrive as soon as they are published.

## Secret scanning

`secret-scan.yml` runs gitleaks on every push and pull request and over the whole history weekly. If it finds
something: treat the secret as leaked even after deleting it from the repository. Rotate it first (for the
service account, see "Rotate the key"), then remove it from the code. The Firebase configuration files are allowed
on purpose (`.gitleaks.toml`).
