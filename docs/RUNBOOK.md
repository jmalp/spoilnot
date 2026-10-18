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
key under **Keys** in the console.

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

1. Raise `versionCode` (and `versionName`) in `app/build.gradle.kts`.
2. Build with the upload keystore (`keystore.properties` at the repository root; never committed):
   ```bash
   ./gradlew assembleProdRelease
   ```
   The build uploads the R8 mapping to Crashlytics so crash reports are readable.
3. Upload and invite testers (comma-separated emails; testers already invited get the new release):
   ```bash
   cd firebase && npx firebase appdistribution:distribute ../app/build/outputs/apk/prod/release/app-prod-release.apk \
     --app 1:977467444046:android:2e84de68984d0dd33cec1f --project prod --testers "<emails>" --release-notes "<notes>"
   ```
4. To force everyone onto the new build, raise `minSupportedBuild` in Firestore `config/app` (spoilnot-prod).
