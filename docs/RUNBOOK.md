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

**New tester:** add their email to `.beta-testers`. To invite them to the current release without a new build:

```bash
cd firebase && npx firebase appdistribution:distribute ../app/build/outputs/apk/prod/release/app-prod-release.apk \
  --app 1:977467444046:android:2e84de68984d0dd33cec1f --project prod --testers "<email>"
```

(This re-uses the existing release when the APK is the one last uploaded; otherwise run the script.)

## Nightly export and restore (DESIGN 13.4)

`nightly-export.yml` runs at 07:17 UTC (about 3 AM Eastern) and saves every spoilnot-prod household as
`<householdId>.json` in an artifact called `household-exports`, kept 14 days. The format is the same as the app's
Settings > Export file. Artifacts of this private repository are visible only to people with access to it.

To find an export: **Actions → nightly-export →** pick a run (its URL ends with the run ID) **→ Artifacts**.

### Restore a household

Restoring writes back the export's products, stock, grocery lists and their items, and recipes, exactly as they
were. It never changes the household document or its members (they control access), and it leaves documents created
after the export alone. Anything deleted since the export comes back; anything changed since goes back to the
exported version.

1. **Actions → restore-from-export → Run workflow.** Enter the export run's ID and leave the household empty: the
   log lists each household ID in that export with its document counts (nothing is written).
2. Run it again with the household ID and "apply" unticked. The log shows how many documents would be written,
   by kind.
3. Run it again with **apply** ticked.
4. To restore into a different household (for example after the original was deleted, recreate one in the app,
   then find its ID in the Firebase console under `households`), fill in **target household**.

From a terminal: `gh workflow run restore.yml -f export_run_id=<id> -f household_id=<hid> -f apply=false`.

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
2026-10-18 (30 products, 60 batches, 25 grocery items and 15 recipes per household; 4 phones opening the app 8
times a day):

| Reads | Per day | Share of 50,000 |
| --- | --- | --- |
| App listeners | 42,280 | |
| Security-rule lookups | 2,200 | |
| Scheduled jobs (reminders, housekeeping, export) | 2,440 | |
| **Total for 10 households** | **46,920** | **93.8%** |
| Per household | about 4,700 | 9.4% |

Writes were 440 (2.2%). So the free plan holds about 7 households of this size under the 70% warning line. Most
reads are a phone re-reading the pantry when it opens after more than 30 minutes. If usage nears 70%: check for a
regression with the simulation, then consider the Blaze plan (DESIGN 9.7; budget alerts notify but do not cap).

## Scheduled workflows

`expiry-reminders` (hourly), `nightly-export` (daily) and `secret-scan` (weekly) run on schedules. GitHub can
disable scheduled workflows in a repository with no activity for 60 days, and shows a banner on the Actions tab. To
turn one back on: **Actions →** the workflow **→ Enable workflow** (or
`gh workflow enable reminders.yml --repo jmalp/spoilnot`). Then run it once by hand to check.

## Dependency updates

Dependabot (`.github/dependabot.yml`) opens grouped pull requests on Mondays for Gradle, the two npm projects and
GitHub Actions. Merge one when CI is green; for an app library update, also install a build on a phone before the
next beta. Security updates arrive as soon as they are published.

## Secret scanning

`secret-scan.yml` runs gitleaks on every push and pull request and over the whole history weekly. If it finds
something: treat the secret as leaked even after deleting it from the repository. Rotate it first (for the
service account, see "Rotate the key"), then remove it from the code. The Firebase configuration files are allowed
on purpose (`.gitleaks.toml`).
