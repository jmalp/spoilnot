# Runbook

Operational tasks for Spoil Not. The design is in `DESIGN.md`; deviations are in `DECISIONS.md`.

## Reminder job (DESIGN 10)

The job lives in `backend/reminder` and runs from `.github/workflows/reminders.yml` at minutes 7, 27 and 47 of
every hour (since 2026-11-08), once per Firebase project. GitHub starts many scheduled runs late or not at all; the
job catches up and sends at most one digest a day per user, however many runs start:

| Project | Secret (in the `production` environment, "How the jobs get their keys") |
| --- | --- |
| `spoilnot-prod` (testers) | `FIREBASE_SERVICE_ACCOUNT_JSON` |
| `spoilnot-dev` (development, optional) | `FIREBASE_SERVICE_ACCOUNT_JSON_DEV` |

A project whose secret is missing logs "nothing to do" and succeeds. A key stored under the wrong secret fails the
run with `expiry-reminders failed: wrong-project`, and every run logs the project it served.

### Create the service account (once per Firebase project)

1. Open https://console.cloud.google.com and pick the project (`spoilnot-dev` or `spoilnot-prod`) at the top.
2. **IAM & Admin → Service Accounts → + Create service account**. Name: `reminder-job`. **Create and continue**.
3. Grant two roles, then **Done**:
   - **Cloud Datastore User** (read and write Firestore),
   - **Firebase Cloud Messaging API Admin** (send notifications).
4. Open the account → **Keys → Add key → Create new key → JSON → Create**. A `.json` file downloads.
5. Store it as a secret of the `production` environment, then delete the downloaded file:
   ```bash
   # spoilnot-prod
   gh secret set FIREBASE_SERVICE_ACCOUNT_JSON --env production --repo jmalp/spoilnot < ~/Downloads/<file>.json
   # spoilnot-dev
   gh secret set FIREBASE_SERVICE_ACCOUNT_JSON_DEV --env production --repo jmalp/spoilnot < ~/Downloads/<file>.json
   ```
6. In the Firebase console → Project settings → Cloud Messaging, check that **Firebase Cloud Messaging API (V1)**
   is enabled.

Never commit the key or put it anywhere else. Do not link a billing account (DESIGN 3).

### Rotate the key (yearly, or at once if it shows in a log)

Create a new key (step 4), replace the secret (step 5), run the workflow once by hand (below), then delete the old
key under **Keys** in the console. The nightly export and the restore workflow use the same prod secret, so they
pick up the new key too.

### If the key ever shows in a log

Run logs are public for 90 days, and GitHub hides a multi-line secret only line by line. The jobs never print an
error itself, only its code, but if a log ever shows any part of a key (`private_key`, `BEGIN PRIVATE KEY`, lines of
base64, or the account's `@….iam.gserviceaccount.com` email), treat the key as leaked:

1. Rotate it at once (above): new key, new secret, then delete the old key under **Keys**. Deleting it is what makes
   the copy in the log useless.
2. Delete each run whose log shows it: the run's **⋯ → Delete workflow run**, or `gh run delete <run-id>`.
3. In Firestore → Usage, look for reads or writes you don't recognize since that run.

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
  never sends twice a day. To see how GitHub is starting them, count start times by UTC hour:
  ```bash
  gh run list --repo jmalp/spoilnot --workflow reminders.yml --event schedule --limit 500 --json startedAt --jq '.[].startedAt[11:13]' | sort | uniq -c
  ```
  Every hour should show runs. If whole hours are missing for a week, see DESIGN 10.4 and 10.6.
- Each run prints one summary line, `project <id>: {…}`, with counts only:

  | Field | Meaning |
  | --- | --- |
  | `due`, `sent`, `emptyDigests` | Users due this run, messages delivered, due users with nothing to report (done for the day) |
  | `skippedNoDevices` | Due users with no registered phone; tried again next run |
  | `skippedNotMember` | Due users whose household no longer lists them (below) |
  | `skippedDeleted` | Due users whose profile was deleted during the run (an account deletion) |
  | `gaveUp` | Users whose digest reached no device in 3 runs today; done for the day, tried again tomorrow |
  | `deadTokens` | Device documents deleted because FCM said the token is unregistered or invalid |
  | `failures` | Households or users whose step failed; the rest of the run went ahead, and the run fails at the end |
  | `pruned`, `lastPruneDate` | Whether this run pruned, and the UTC date of the last prune without failures |
  | `prunedActivity`, `prunedJoinRequests`, `prunedTombstones`, `prunedMeals`, `pruneFailures` | The prune's counts |

- `failures` counts a household or user whose step failed this run. Each one also prints a line such as
  `a digest failed: app/network-timeout` (the step and the code, never an ID). The run carries on, then fails so
  GitHub emails you; the next run tries them again. Look the code up in the table below.
- The log line `skippedNoDevices` counts users whose phones have not registered (they have not opened the app
  since installing, or notifications are blocked).
- `skippedNotMember` counts due users whose household no longer lists them (removed, or the household was
  deleted) and whose app has not run since. They get nothing, and the job clears their household pointer as their
  app would, so they are not counted again.
- The daily prune runs in the first run of each UTC day (it keeps `lastPruneDate` in the job's own document
  `jobs/state`, which clients can't read). Since 2026-11-10 it deletes join requests older than 7 days only when
  they are pending or declined, or approved and finished: an approved request whose sender's profile still waits on
  that household stays until their app opens and finishes the join (each costs one read of that profile a day). The
  apps people have (Android 1.0.0-beta.9, web 55edcc5) need the approved request to get in, so this takes effect
  safely with the merge, before any release. `pruneFailures` counts old documents it could not delete. The run then
  fails so GitHub emails you, the date is not recorded, and the next run prunes again. If `lastPruneDate` in the
  summary is more than a day old, the prune is failing or no run has started today.
- A failed run prints one line, `<job> failed: <code>.`, and never the error itself (logs are public):

  | Code | Meaning |
  | --- | --- |
  | `wrong-project` | The secret holds the other project's key |
  | `key-not-json`, `app/invalid-credential` | The secret is not a valid JSON key file; store it again (step 5) |
  | `PERMISSION_DENIED`, `UNAUTHENTICATED` | The key was deleted or lost a role; check the service account (steps 3 and 4) |
  | `RESOURCE_EXHAUSTED` | The day's free reads or writes ran out (Check quota usage) |
  | `UNAVAILABLE`, `DEADLINE_EXCEEDED` | Google was briefly unreachable; the next run catches up |
  | `usage`, `no-key`, `not-an-export`, `no-such-household` | A restore that could not start (Restore a household) |
  | `different-owner` | The restore's target household has another owner than the export's; nothing was written (Restore a household) |
  | Anything else, or `no code` | Run it again by hand. If it keeps failing, reproduce it on a computer with the dev key; never commit a change that prints the error |

## Firestore rules

Rules live in `firebase/firestore.rules` and are tested by `cd firebase && npm test`. Deploy after merging to
`main`:

```bash
cd firebase && npx firebase deploy --only firestore:rules --project dev
cd firebase && npx firebase deploy --only firestore:rules --project prod
```

Both projects run the same rules; deploy to dev first. `spoilnot-prod` was opened for the beta on 2026-10-18.

A rules change that refuses something an older build writes goes out **after** the builds that no longer write it:
Android first (then raise `minSupportedBuild`), the web app next (then raise `minWebBuild` if its build raised
`WEB_BUILD`, below; web builds before that gate keep their version until they restart), the rules last. If the rules refuse something they should not, Firebase console >
Firestore Database > Rules lists earlier versions; publish the previous one, then fix and test the rules here.

### The 2026-11-08, 2026-11-09 and 2026-11-10 rules (DESIGN 8.1, 8.5 and 5.1)

None is deployed yet; they go out together, in one deploy, with the order below. 2026-11-08's: allowed fields,
authors, deletes and counts. 2026-11-09's (one phone wins, 8.5 invariant 10): a grocery item is tombstoned once, a
meal is marked cooked once, and items are created only under a list that exists. 2026-11-10's: a join request may
carry `emailMasked`, the sender's sign-in email masked as the rules compute it (`ro***@example.com`), and a batch's
quantity may be below 0 (8.5 invariant 1), which refuses nothing any build writes; until it is deployed, builds from
2026-11-10 on see the old refusal only when two phones take the last unit of a purchase at once.

What the released builds (Android 1.0.0-beta.9, versionCode 10, and web build 55edcc5) do that these rules refuse:

- **Throwing several batches away at once**, and its Undo: the count moves by more than one in one write.
- **I cooked this when it uses up two or more whole batches**, and its Undo: two counts of one month in one batch.
- **Nothing of 2026-11-10's:** released builds send join requests without `emailMasked`, which stays allowed (the
  owner then sees the name and time only). The other way round, the new builds released in steps 2 and 4 send it,
  and the rules deployed until step 7 refuse it; they then send the request again without it, so joining works in
  between, without the email.
- **Only when two phones race (2026-11-09):** the second Add to pantry, Clear checked or delete of the same items, the
  second Cooked of the same meal, and an item added to a list another member just deleted. Old builds roll these back
  with "You don't have permission to do that." instead of the new message; before, they did the work twice.

Everything else they write passes (the instrumented tests and the web's browser tests ran against these rules). A
refused write shows "You don't have permission to do that." and the phone rolls it back; nothing is lost. The order:

1. Merge the pull request.
2. Release the Android beta: `./scripts/release-beta.sh "..."`. When it asks **"Deploy the rules to spoilnot-dev,
   then spoilnot-prod?"**, answer **N**.
3. Once the testers have it, raise `minSupportedBuild` in Firestore `config/app` (spoilnot-prod) to the new
   `versionCode` (11, unless another release came first).
   Its builds name the winner of a race ("Sam already took these off the list. Nothing was added to the pantry.").
4. Deploy the web app: `./scripts/deploy-web.sh dev`, check it, then `./scripts/deploy-web.sh prod`. When the script
   offers to deploy the rules, answer **no** for prod (yes for dev is fine).
5. Set `minWebBuild` in `config/app` (spoilnot-prod) to 3 (the web build with the 2026-11-09 messages; it covers
   build 2's sync cursor too), then wait until the iPhones run it: ask the testers to close the Home Screen app and
   open it again (or tap Update), or wait a day.
6. Deploy the rules to dev and try on spoilnot-dev with both apps: throw two batches away at once and Undo, I cooked
   this using up two batches, delete a list with items as a member, Clear household data, and on a spare household
   transfer ownership and delete the household. For 2026-11-09's rules (QA flow 43): Add to pantry for the same
   checked items on both phones (one in Airplane Mode), Cooked for the same meal on both, and an item added on one
   phone to a list the other deletes; each time the second phone names the first and nothing is doubled. For
   2026-11-10's (QA flow 47): a new phone asks to join and the owner sees its masked email:

   ```bash
   cd firebase && npx firebase deploy --only firestore:rules --project dev
   ```

7. Deploy the rules to prod:

   ```bash
   cd firebase && npx firebase deploy --only firestore:rules --project prod
   ```

8. Watch for refused writes for a day (testers seeing "You don't have permission to do that."). If one shows,
   publish the previous version in the console (above).

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

**The web app's counterpart is `minWebBuild`** in the same document (DESIGN 18.5). A change that older web builds
must not keep running raises `WEB_BUILD` in `web/src/core/version.ts` by one (its PR says so). After that build is
deployed to spoilnot-prod (`./scripts/deploy-web.sh prod`), set `minWebBuild` in `config/app` (spoilnot-prod) to that
number, as a number (Firestore console > `config` > `app` > Add field, type number; the field may not exist yet).
Do the same in spoilnot-dev after deploying there. Never set it before the deploy: phones would look for a version
that is not there. Then check on an iPhone that still has the old build (QA flow 36): open Spoil Not from the Home
Screen, signed in to a household, or bring it back to the front; within a few seconds "A new version of Spoil Not is
ready" shows and stays past 10 seconds until Update; after Update, Settings > About shows the new build number. If
the prompt never comes, check Hosting's release history for the deploy.

**The release with the sync cursor from the server (2026-11-09, DESIGN 9.8)** raised `WEB_BUILD` to 2: set
`minWebBuild` to 2 after its web deploy, and raise `minSupportedBuild` to its Android `versionCode` after the testers
have it. Older builds still take the cursor from the cache and can miss another member's changes to a list for up to
20 hours. Updated phones read each collection in full once at their next open (their sync log has no cursor yet),
about one day's full read more per phone, once.

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
- `npm run e2e` (with the emulators running) runs the WebKit tests with an iPhone profile, two test files at a time,
  two browser contexts as two phones (tests seed data through the emulators' REST APIs, `tests/e2e/support.ts`), and compares the
  screenshots in
  `tests/e2e/__screenshots__/`; after an intended change, `npm run e2e:update` and look at the new images in light,
  dark and 200% text next to the Android ones. Images within the 1% tolerance are not rewritten; for a small
  intended change (a line, a color) use `npx playwright test screens --update-snapshots=all` and keep only the
  images that should change. Month names on Insights are masked and the invite code is fixed, so the images do not change between runs. If the
  tests slow down after a long day of runs, stop the emulators, delete `firebase/.emulator-data` and start them again.
- After changing `strings.xml`, the Android theme colors or `icons.txt`: `npm run gen` (CI fails until it is run).
  After changing the launcher icon: `npm run gen && node scripts/gen-app-icons.ts` (the PNGs come out full-colour;
  ImgBot later offers lossless palette versions, about a third of the size). `npm run gen` also rewrites
  `public/licenses.json` (About's open source licenses) from the installed packages, so after adding or updating a
  dependency run `npm ci` and `npm run gen` and commit the file.
- Web push needs the project's Web Push certificate (a human step, once per project): Firebase console > Project
  settings > Cloud Messaging > Web Push certificates > Generate key pair. The public key is not a secret; the build
  reads it from `VITE_FIREBASE_VAPID_KEY` in `web/.env.<dev|prod>.local` (see Deploying the web app). Without it the web app writes the time
  zone but no device, so the digest does not reach that iPhone. The Firestore rules must be deployed with
  `platform: web` devices allowed (2026-10-30) before an iPhone can register.
- Push cannot be tried in development: the dev server has no service worker. Use a deployed build on an iPhone
  (QA flow 35).

### Deploying the web app (DESIGN 18.6)

Once per project (spoilnot-dev, then spoilnot-prod), with the owner's OK:

1. Register a Web app: Firebase console > Project settings > Your apps > Add app > Web (nickname "Spoil Not web",
   no Firebase Hosting setup needed). The deploy script reads its config from Firebase; nothing is copied by hand.
2. Create the Web Push certificate (Cloud Messaging > Web Push certificates > Generate key pair) and put its public
   key in `web/.env.dev.local` or `web/.env.prod.local` as `VITE_FIREBASE_VAPID_KEY=...` (git-ignored).
3. Deploy the Firestore rules (they allow `platform: web` devices since 2026-10-30); the deploy script offers it.

Then, from a clean checkout:

- `./scripts/deploy-web.sh dev` checks, tests (the emulators' ports must be free: stop `./scripts/emulators.sh`
  first), builds for spoilnot-dev and deploys to https://spoilnot-dev.web.app. `--skip-tests` skips the browser
  tests, `--dry-run` builds and checks without deploying.
- `./scripts/deploy-web.sh prod` does the same for https://spoilnot-prod.web.app, only from main matching GitHub and
  after a yes. Run QA flow 36 on an iPhone against dev first.
- The script retries a failed browser test once, as CI does, so one long flow running out of time on a busy Mac
  does not stop a deploy; a test that fails twice still does. If the Mac is so slow that tests fail on the retry too
  (on 2026-10-31 every WebKit action took 1.5–3 s for a while) and CI passed on the commit being deployed,
  `--skip-tests` is the way through (it still runs every other check and the unit tests).
- A bad release: Firebase console > Hosting > Release history > Roll back. Installed apps pick up the rolled-back
  version like any update.
- `npm run e2e:preview` builds and tests the app under the hosting headers locally (with the emulators running), as
  CI does. For a performance check: `npx vite preview --port 4173`, then
  `CHROME_PATH=<Playwright's Chromium> npx lighthouse http://localhost:4173/ --only-categories=performance,accessibility`
  (results in DESIGN 18.6).

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
are not phone timings, and a headless emulator with software rendering (`-gpu swiftshader_indirect`) draws scroll
frames about twice as slowly as one on the Mac's GPU, so compare scroll numbers only between runs on the same setup.

If a benchmark says the app "must not be running prior to cold start", something restarted the app right after the
benchmark closed it: look in `adb logcat` for `Start proc … com.parcha.spoilnot.dev … for bound-service`. Crash
reports and Firebase Sessions caused it until 2026-10-31 (their uploads to the emulators' demo project failed and were
retried); both are now off in the emulator builds.

## Nightly export and restore (DESIGN 13.4)

`nightly-export.yml` runs at 07:17 UTC (about 3 AM Eastern) and saves every spoilnot-prod household as
`<householdId>.json`, encrypted together as `household-exports.tar.gz.enc` in an artifact called `household-exports`,
kept 14 days. The format inside is the same as the app's Settings > Export file, without the two parts only the apps
add: the person's own profile and the activity log. The repository is public, and anyone
signed in to GitHub can download artifacts, so the job encrypts with the `EXPORT_ENCRYPTION_KEY` secret (AES-256,
PBKDF2) and fails rather than upload anything unencrypted. Its steps run with `shell: bash` (`pipefail`), so it also
fails rather than upload an archive that `tar` didn't finish.

**Set up the key once** (and again only to rotate it; older exports then need the old key):

```bash
openssl rand -base64 32 | tee /dev/tty | gh secret set EXPORT_ENCRYPTION_KEY --env production --repo jmalp/spoilnot
```

Save the printed key in your password manager: it is the only way to open an export outside GitHub. To open one on a
computer: `openssl enc -d -aes-256-cbc -pbkdf2 -iter 600000 -in household-exports.tar.gz.enc | tar -xzf -` (it asks
for the key).

To find an export: **Actions → nightly-export →** pick a run (its URL ends with the run ID) **→ Artifacts**.

### Restore a household

This is also the way back after an owner's **Clear household data** (Settings): pick the export from the night before
the clear. It brings back products, stock, lists and their items, recipes and the monthly counts; the cleared
activity does not come back.

Restoring writes back the export's products, stock, grocery lists and their items, recipes, monthly counts and
planned meals, as they were, each stamped with the time of the restore (`updatedAt`, and `updatedBy: restore`), so
every phone shows the restore within a minute. It never changes the household document or its members (they control
access), never writes a profile or the activity log, and it leaves documents created after the export alone.
Anything deleted since the export comes back; anything changed since goes back to the exported version. Only the
timestamp fields come back as timestamps (`createdAt`, `updatedAt`, `checkedAt` and the like); everything else, such
as a note that looks like a date and time, stays as it was typed.

The target household must have the same owner as the exported one. Otherwise the restore stops with `restore failed:
different-owner` before writing anything, also in the dry run. Tick **different owner** only when that is what you
mean: ownership moved to another member since the export, or you are restoring into a household someone else
owns.

1. **Actions → restore-from-export → Run workflow.** Enter the export run's ID and leave the household empty: the
   log lists the first four characters of each household ID in that export with its document counts (nothing is
   written). Logs are public, so full IDs are never printed; the full ID is in the Firebase console under
   `households`.
2. Run it again with the first characters of the household ID (four or more, enough to match one export) and
   "apply" unticked. The log shows how many documents would be written, by kind, and `Target: same owner as the
   export.` (it reads the target household, nothing else).
3. Run it again with **apply** ticked. The log ends with `restored N document(s), M failed`. If M is not 0, the
   run fails: those documents were not written (for example one too large for Firestore, which can take others in
   its batch with it). A restore sets each document as exported, so running it again is safe. `restore failed: no-such-household` means
   the target household does not exist (step 4).
4. To restore into a different household (for example after the original was deleted, recreate one in the app with
   the same owner's account, then find its ID in the Firebase console under `households`), fill in **target
   household**. A household with another owner also needs **different owner** ticked; the log then says `Target: a
   different owner, as asked`.

From a terminal: `gh workflow run restore.yml -f export_run_id=<id> -f household_id=<first characters> -f apply=false`
(add `-f different_owner=true` for step 4's other owner).

A file saved from Settings > Export can be restored the same way from a computer that has the service account key
in `FIREBASE_SERVICE_ACCOUNT_JSON` (`cd backend/reminder && npm ci --ignore-scripts && npm run build && node
dist/restore-main.js <file> <householdId> [--apply] [--different-owner]`), but the workflow is preferred because the
key never leaves GitHub. Its `profile` and `activity` are not restored. Settings > Export needs a connection: it reads
from the server and, offline, says "You're offline" and saves nothing.

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

When reads run out, both apps keep working from each phone's copy with the red quota banner above every screen and
"Offline" on the tabs, until midnight Pacific (DESIGN 6.7, 7.5); nobody is signed out. Listeners retry at most every
32 s, which costs nothing while refused. Nothing needs doing; if it happens often, see quota-sim below.

`cd firebase && npm run quota-sim` replays a day of 10 typical households against the emulator. Knobs:
`HOUSEHOLDS`, `RUNS` (reminder runs that start in a day, default 24; 72 are scheduled), `WEB` (how many of each
household's 4 phones are the web app) and `WARM` (of each phone's 7 later opens, how many find the app's process
still alive, or for web phones how many resume the suspended Home Screen app instead of reloading it; `HELD=1`
replays the apps before 2026-11-09 (Android) and 2026-11-10 (web), which kept some listeners while the process or page
lived). Result on 2026-11-09 (30 products, 60 batches, 25 grocery items, 15 recipes,
this month's counts, 30 tombstones and 42 planned meals (12 this week) per household; 4 Android phones opening the
app 8 times a day, the first open a full read, Home reading recipes and this week's meals on every open; the 40
daily writes spread between opens, each logging an activity event, 10 of them finishing a batch and 10 creating a
grocery item; a prune every day):

| Reads | Per day | Share of 50,000 |
| --- | --- | --- |
| App listeners | 12,720 | |
| Security-rule lookups | 3,540 | |
| Scheduled jobs: runs 984, digests 200, housekeeping 680, export 2,100 | 3,964 | |
| **Total for 10 households** | **20,224** | **40.4%** |
| Per household | about 2,020 | 4.0% |

| Writes and deletes | Per day | Share of 20,000 |
| --- | --- | --- |
| Writes (actions, month counts, activity events, the job's) | 941 | 4.7% |
| Deletes (the daily prune: tombstones, activity after 30 days, meals after 28) | 570 | 2.9% |

So the free plan holds **about 17 households** of this size under the 70% warning line (reads run out first).
Other runs: `RUNS=72` (every scheduled run starts) 22,192 reads, about 15.8 households, since each run reads every
user with reminders on; `RUNS=15` 19,855, about 17.6; `WEB=2` (two web phones per household) the same reads, since
the web app also reads `config/app` (DESIGN 18.5); `WARM=3` or `WARM=7` the same reads, since nothing holds a
household listener once its screens are gone (DESIGN 9.1).

| Later opens that find the process alive (of 7) | Before 2026-11-09 (`HELD=1`) | Now | Households under 70%, before → now |
| --- | --- | --- | --- |
| 0 | 20,224 | 20,224 | 17.3 → 17.3 |
| 3 | 37,180 (16,236 from held listeners resumed after 30 minutes, 720 more rule lookups) | 20,224 | 9.4 → 17.3 |
| 7 | 63,406 (41,502 held, 1,680 more rule lookups) | 20,224 | 5.5 → 17.3 |
| 3, one web phone of four | 37,582 (16,728 held) | 20,224 | 9.3 → 17.3 |
| 3, four web phones | 35,224 (14,640 held) | 20,224 | 9.9 → 17.3 |
| 7, four web phones | 55,224 (34,160 held) | 20,224 | 6.3 → 17.3 |

The web rows are from 2026-11-10, when `HELD=1` began to cover web phones: before, only Android phones held
listeners in the simulation, and one web phone of four gave 33,832. A web phone's "before" counts only the restock
engine's listeners (the screen on display kept its own too), so it is a lower bound. Repeated `HELD=1` runs differ by
about 1,000 reads (the Android-only row of 3 gave 38,368 on 2026-11-10), since the held listeners read what changed
since the day's first open, which depends on timing.

How often a phone keeps the process varies with its memory and maker: the console's usage page after a normal day,
divided by the households, says which row the beta is on (review 09's phone check 2).

History: before changes-only listeners it was 47,570 reads (95.1%; about 4,760 per household), and 19,320 before
Recent activity read only new events, 17,920 before the meal planner. Part 2 of the planner left it at 19,440; part 3
added the digest's query for today's meals: 19,520. Checking that each due user is still a member (2026-11-01) added
one read per user a day: 19,560 (about 18 households). On 2026-11-08 the simulation began counting what it had left
out: each activity event as a write (+400) with its rule lookup (+400 reads), the documents the prune reads before
deleting them (+420), the prune's deletes, and the job's state document (one read per run): 20,404 at 24 runs. On
2026-11-09 the rules began looking up a grocery item's list on create (+100 rule lookups); the simulation now makes 10
of the 40 daily writes item creates instead of batch edits, which the listeners deliver differently (−280): 20,224.
On 2026-11-10 the prune began reading the sender's profile for each old approved join request (rare; the simulation
has none, so 20,224 stays). The
largest remaining costs are each phone's daily full read and rule lookups. If usage nears 70%: check for a
regression with the simulation, then consider the Blaze plan (DESIGN 9.7; budget alerts notify but do not cap).

## How the jobs get their keys

The reminder job, the nightly export and the restore declare `environment: production`, and their secrets are
secrets of that environment: `FIREBASE_SERVICE_ACCOUNT_JSON`, `FIREBASE_SERVICE_ACCOUNT_JSON_DEV` and
`EXPORT_ENCRYPTION_KEY`. The environment allows only the `main` branch, so a workflow run from any other branch
cannot use it and gets none of them. It has no required reviewers and no wait timer, so scheduled runs start on their
own. CI, `secret-scan` and `keepalive` use no secrets besides GitHub's own token.

Inside each job, `npm ci --ignore-scripts && npm run build` runs in a step that has no secret, and no package's
install script runs at all. Only the step that
runs `node dist/…` gets the service account key; the export's Encrypt step and the restore's Decrypt step get
`EXPORT_ENCRYPTION_KEY`. CI installs with `--ignore-scripts` too, and the job's tests pass that way, so a dependency
that needs its install script fails CI before it reaches a job.

**Set up the environment** (once, and again if it is ever deleted):

1. GitHub → the repository → **Settings → Environments**. Open `production` (GitHub creates it, without rules, the
   first time a workflow names it) or **New environment** with that name.
2. **Deployment branches and tags → Selected branches and tags → Add deployment branch or tag rule**: branch `main`.
3. Leave **Required reviewers** and **Wait timer** off: either would hold every scheduled run until someone acts.
4. **Environment secrets → Add environment secret** for each of the three, or from a terminal with `--env
   production` (the commands in "Create the service account" and "Nightly export and restore").
5. Keep these secrets only in the environment: only there are they limited to `main`. When a repository secret
   (**Settings → Secrets and variables → Actions → Repository secrets**) has the same name, the job uses the
   environment's. After
   a manual run of `expiry-reminders` (dry run) and `nightly-export` succeeds, delete any repository secret with one
   of these names.

The environment's page lists each run of the three jobs as a deployment; that is expected.

## Scheduled workflows

`expiry-reminders` (three times an hour), `nightly-export` (daily) and `secret-scan` (weekly) run on schedules. GitHub disables
scheduled workflows in a public repository after 60 days without activity, and shows a banner on the Actions tab.
`keepalive.yml` re-enables them (and itself) through the API on the 3rd of every month, which resets that clock. If
one is disabled anyway: **Actions →** the workflow **→ Enable workflow** (or
`gh workflow enable reminders.yml --repo jmalp/spoilnot`). Then run it once by hand to check. If a run of the reminder
job, export or restore waits for approval or fails before its first step, check the `production` environment ("How
the jobs get their keys"): `main` allowed, no reviewers, no wait timer.

**Logs are public.** Workflow logs show counts, dates and the first characters of household IDs only: the reminder
job logs neither user IDs nor what is in a digest (a dry run shows how many items and meals, never names or titles), and
the export and restore print counts. Keep it that way when changing the jobs.

## Dependency updates

Dependabot (`.github/dependabot.yml`) opens grouped pull requests on Mondays for Gradle, the four npm projects
(`firebase`, `backend/reminder`, `web`, `tools/themes`) and GitHub Actions. Merge one when CI is green; for an app
library update, also install a build on a phone before the next beta. With Dependabot security updates on
(**Settings → Advanced Security**), security updates arrive as soon as they are published.

- **Actions** are pinned to a full commit SHA with the version in a comment (`uses: actions/checkout@<sha> #
  v7.0.1`), and Dependabot updates both. Pin a new action the same way: `gh api
  repos/<owner>/<action>/commits/<tag> --jq .sha`, and check that the tag is the release you meant.
- **npm** installs in CI and the jobs use `--ignore-scripts`. A dependency that needs its install script fails CI;
  don't add one to `backend/reminder` (its jobs hold the keys).
- **Guava** comes only through Firestore's gRPC; the catalog's `guava` is the lowest version allowed (a constraint in
  `app/build.gradle.kts`). Dependabot raises it. Remove both when Firebase brings that version or a newer one.
- Gradle dependency verification is not used (DECISIONS 2026-11-10); the Gradle wrapper is checked by its
  `distributionSha256Sum` and by `setup-gradle` in CI.

## Secret scanning

GitHub's secret scanning with push protection (**Settings → Advanced Security**) refuses a push that holds a known
kind of key, before it is public. `secret-scan.yml` also runs gitleaks on every push and pull request and over the
whole history weekly. If either finds something: treat the secret as leaked even after deleting it from the repository. Rotate it first (for the
service account, see "Rotate the key"), then remove it from the code. The Firebase configuration files are allowed
on purpose (`.gitleaks.toml`).
