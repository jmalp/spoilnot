# Runbook

Operational tasks for Spoil Not. The design is in `DESIGN.md`; deviations are in `DECISIONS.md`.

## Reminder job (DESIGN 10)

The job lives in `backend/reminder` and runs from `.github/workflows/reminders.yml`, once per Firebase project.
An Apps Script trigger in the owner's Google account starts it every quarter hour, with the workflow's own schedule
as the backup; DESIGN 10.4 says why, and how on time the runs are. Setting the trigger up is "On-time reminders"
below. The job catches up and sends at most one digest a day per user, however many runs start. Each project's run
signs in as that project's `reminder-job` service account, with workload identity when its repository variables are
set, otherwise with a key ("How the jobs get their keys"):

| Project | Workload identity (repository variable) | Key until then (repository secret) |
| --- | --- | --- |
| `spoilnot-prod` (testers) | `GCP_SA_REMINDERS_PROD` | `FIREBASE_SERVICE_ACCOUNT_JSON` |
| `spoilnot-dev` (development, optional) | `GCP_SA_REMINDERS_DEV` | `FIREBASE_SERVICE_ACCOUNT_JSON_DEV` |

A project with neither logs "nothing to do" and succeeds. A key or service account for the wrong project fails the
run with `expiry-reminders failed: wrong-project`, and every run logs the project it served and how it signed in
("Signed in with workload identity as reminder-job.").

### Create the service account (once per Firebase project)

The nightly export and the restore have service accounts of their own in `spoilnot-prod` ("How the jobs get their
keys"). The reminder job's, in each project:

1. Open https://console.cloud.google.com and pick the project (`spoilnot-dev` or `spoilnot-prod`) at the top.
2. **IAM & Admin → Service Accounts → + Create service account**. Name: `reminder-job`. **Create and continue**.
3. Grant two roles, then **Done**:
   - **Cloud Datastore User** (read and write Firestore),
   - **Firebase Cloud Messaging API Admin** (send notifications).
4. With workload identity, the account needs no key: allow the workflow to act as it instead ("Set up workload
   identity", step 4) and skip steps 5 and 6. Otherwise: open the account → **Keys → Add key → Create new key → JSON
   → Create**. A `.json` file downloads.
5. Store it minified, on one line, as a repository secret (GitHub masks a multi-line secret line by line, a one-line
   one as a whole), then delete the downloaded file. `jq` comes with macOS 15 and later (`brew install jq` before):
   ```bash
   # spoilnot-prod
   jq -c . ~/Downloads/<file>.json | gh secret set FIREBASE_SERVICE_ACCOUNT_JSON --repo jmalp/spoilnot
   # spoilnot-dev
   jq -c . ~/Downloads/<file>.json | gh secret set FIREBASE_SERVICE_ACCOUNT_JSON_DEV --repo jmalp/spoilnot
   rm ~/Downloads/<file>.json
   ```
6. In the Firebase console → Project settings → Cloud Messaging, check that **Firebase Cloud Messaging API (V1)**
   is enabled.

Never commit the key or put it anywhere else. Do not link a billing account (DESIGN 3).

### Rotate the key (yearly, or at once if it shows in a log)

With workload identity there is no key to rotate: each run gets a token that lasts an hour at most.

For each key still in use: create a new key (step 4), replace its secret (step 5), run the workflow once by hand
(below), then delete the old key under **Keys** in the console. The nightly export and the restore use
`FIREBASE_SERVICE_ACCOUNT_JSON_EXPORT` and `…_RESTORE` when those are set, and the reminder job's prod key otherwise
("How the jobs get their keys"), so rotating the prod key also changes theirs.

### If the key ever shows in a log

Run logs are public for 90 days, and GitHub hides a multi-line secret only line by line. The jobs never print an
error itself, only its code, but if a log ever shows any part of a key (`private_key`, `BEGIN PRIVATE KEY`, lines of
base64, or the account's `@….iam.gserviceaccount.com` email), treat the key as leaked:

1. Rotate it at once (above): new key, new secret, then delete the old key under **Keys**. Deleting it is what makes
   the copy in the log useless.
2. Delete each run whose log shows it: the run's **⋯ → Delete workflow run**, or `gh run delete <run-id>`.
3. In Firestore → Usage, look for reads or writes you don't recognize since that run.

### On-time reminders (Apps Script)

GitHub runs a workflow started through its API (`workflow_dispatch`) at once, but starts scheduled runs hours late
(DESIGN 10.4). So a Google Apps Script in the owner's Google account starts the job every quarter hour:
`backend/reminder/apps-script/trigger.gs`, tested in `backend/reminder/test/trigger.test.ts`. It ticks every minute,
starts the job once per quarter hour (:00, :15, :30 and :45 UTC) on `main` with **dry_run** off, and tries again
every minute when GitHub refuses. The job and its keys stay on GitHub.

**Set it up** (once):

1. **Token.** github.com → your picture → **Settings → Developer settings → Personal access tokens → Fine-grained
   tokens → Generate new token**. Name: `spoilnot-reminder-trigger`. Expiration: the longest offered (note the date).
   **Repository access → Only select repositories →** `jmalp/spoilnot`. **Permissions → Repository permissions →
   Actions: Read and write** (GitHub adds Metadata: read-only by itself), nothing else. **Generate token**, copy it.
2. **Script.** https://script.google.com → **New project**, named `Spoil Not reminder trigger`. Replace everything in
   `Code.gs` with `backend/reminder/apps-script/trigger.gs` and save.
3. **Token in the script.** **Project Settings** (the gear) **→ Script properties → Add script property**: name
   `GITHUB_TOKEN`, value the token. Save. The token is never in the code, so the file stays the repository's.
4. **Start it.** In the editor, pick `install` in the function menu and **Run**. Google asks you to authorize the
   script: it is your own, so it says Google hasn't verified it (**Advanced → Go to Spoil Not reminder trigger**),
   and it asks to connect to an external service, manage its triggers and send email as you. `install` checks the
   token with a read that starts nothing, then creates the every-minute trigger; the log says "Installed".
5. **Check.** Within 15 minutes, a run started on the quarter hour shows up (`gh run list --repo jmalp/spoilnot
   --workflow reminders.yml --event workflow_dispatch --limit 4`), and its log is a live run, not a dry run.

**What the token can do.** In this repository only, and only Actions: start, re-run and cancel workflows, switch them
on or off, and delete runs, logs and artifacts, the nightly exports among them. It cannot read secrets, change
settings or environments, or approve a deployment. So the restore, which waits for the owner's approval in the
`restore` environment ("How the jobs get their keys"), never writes on its own, and an export started with it only
makes one more encrypted export. If it ever leaks: **Fine-grained tokens →** the token **→ Delete**, then make a new
one (step 1) and replace the script property.

**Renew the token** before its expiration date: **Regenerate token** (or make a new one as in step 1), replace the
script property and run `install` again to check it. An expired token makes the script email you once a day
("GitHub refused the token (401)"); the reminders then go out on GitHub's schedule, late, until it is replaced.

**Its emails** (at most one of each kind a day; since 2026-11-25):

| Subject | Says | Do |
| --- | --- | --- |
| "reminders are not starting on time" | "refused the token (401)" | Renew the token (above) |
| "reminders are not starting on time" | "answered 403" or "404 … no longer has access" | Check the token's repository access and **Actions: Read and write** (step 1) |
| "reminders are not starting on time" | "answered 422 …" (another 4xx) | Check that `reminders.yml` on `main` still starts by hand with its `dry_run` input |
| "reminders are not starting on time" | "GITHUB_TOKEN is missing" | Add the script property (step 3) |
| "GitHub is slow to start the reminders" | "has answered 500" (or 502, 503, 429), "could not be reached" | Nothing: a quarter hour's run hadn't started 10 minutes in because of GitHub. The script keeps trying; see https://www.githubstatus.com. Shorter hiccups send no email |

**Update the script** after a change to `trigger.gs`: open the project on script.google.com, replace everything in
`Code.gs` with the new file and save. The trigger runs the saved code from its next minute; no `install` needed.

**Turn it off.** Run `uninstall` in the script (GitHub's schedule carries on), then delete the token. To start the
job hourly instead of every quarter hour, set `SLOT_MINUTES = 60` in the script: time zones with a half-hour offset
then get their digest up to half an hour late. Since 2026-11-23 (B25) a run with nobody due costs three reads, so this
saves only about 200 reads a day ("Check quota usage").

### Who each run reads (B25, since 2026-11-23)

Each run reads only the users who may be due: those whose `nextDigestAt` (on their profile, written only by the job)
has come, and those whose profile changed since the previous run (`updatedAt` after `usersCursor` in `jobs/state`).
The first run of each UTC day reads everyone with reminders on instead (`fullRead: true` in the summary, recorded as
`lastUsersReadDate`) and fills in anyone without a time. DESIGN 10.3 has the details; DECISIONS 2026-11-23 the reasons.

**Deploy and release order: none.** The change is the job only. It goes live with the merge, since every run builds
`main`; it needs no rules deploy (the apps never write `nextDigestAt`, and the rules already refuse it), no index
deploy (two automatic single-field indexes: `nextDigestAt` and `updatedAt` on `users`) and no app release (both apps,
the released Android 1.0.0 and web 744d251 included, stamp `updatedAt` on every profile write). The first run after
the merge finds no cursor, reads everyone (as every run did before) and writes each user's `nextDigestAt` once, one
write a user; from the next run on, `usersRead` is the due and the changed, usually 0 to a handful.

- To check it: the summary's `usersRead` should be small except when `fullRead` is true, and digests should arrive
  as before. If someone says a reminder came late after changing a setting, the next full read (the first run after
  00:00 UTC) fixes it; look for what wrote that profile without `updatedAt` (both apps always set it, DESIGN 10.3).
- To make the next run read everyone: in the Firebase console delete the `usersCursor` field of `jobs/state`.
- To undo it: revert the PR. The job before it ignores `nextDigestAt`, and the field stays on profiles, harmless. If
  the change comes back later, its first run reads everyone again only if the cursor is gone; delete `usersCursor`
  first so the times are recomputed (a time the old job left behind is only ever too early, which costs a read).

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
- Since 2026-11-22 most runs are started by the Apps Script trigger ("On-time reminders"). If digests come late
  again, see whether it still starts them (`gh run list --repo jmalp/spoilnot --workflow reminders.yml --event
  workflow_dispatch --limit 8`), then the script's **Executions** page on script.google.com and its email: usually
  the token expired (the table under "On-time reminders" says what each email means).
- Each run prints one summary line, `project <id>: {…}`, with counts only:

  | Field | Meaning |
  | --- | --- |
  | `fullRead` | Whether this run read every user with reminders on (the first run of each UTC day, or no cursor yet) |
  | `usersRead` | Profiles read: those whose `nextDigestAt` has come (everyone on a full read) and those changed since the last run |
  | `due`, `sent`, `emptyDigests` | Users due this run, messages delivered, due users with nothing to report (done for the day) |
  | `skippedNoDevices` | Due users with no registered phone; tried again next run |
  | `skippedNotMember` | Due users whose household no longer lists them (below) |
  | `skippedDeleted` | Due users whose profile was deleted during the run (an account deletion) |
  | `gaveUp` | Users whose digest reached no device in 3 runs today; done for the day, tried again tomorrow |
  | `deadTokens` | Device documents deleted because FCM said the token is unregistered or invalid |
  | `rescheduled` | Users read but not sent to whose `nextDigestAt` the run set or removed (most on the first run after a change) |
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
  | `wrong-project` | The secret holds the other project's key, or the variable names the other project's account |
  | `key-not-json`, `app/invalid-credential` | The secret is not a valid JSON key file; store it again (step 5) |
  | `bad-workload-identity` | A workload identity variable is mistyped: the provider must be `projects/<NUMBER>/…/providers/spoilnot`, with the project number, not its ID ("Set up workload identity", step 5) |
  | `no-id-token` | The workflow lost `permissions: id-token: write` |
  | `PERMISSION_DENIED`, `UNAUTHENTICATED`, or an HTTP status such as `400` or `403` | The key was deleted or lost a role; check the service account (steps 3 and 4). With workload identity, Google refused the run's token: check "Set up workload identity" steps 1 to 4 (the APIs, the pool's condition and mapping, the Workload Identity User grant with the workflow's name, the account's roles). To keep the job running meanwhile, delete its variable: it goes back to its key |
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

### The 2026-11-08, 2026-11-09, 2026-11-10 and 2026-11-13 rules (DESIGN 8.1, 8.5 and 5.1): deployed 2026-11-21

They went out together, in one deploy to each project, after the builds that no longer write what they refuse.
2026-11-08's: allowed fields, authors, deletes and counts. 2026-11-09's (one phone wins, 8.5 invariant 10): a grocery
item is tombstoned once, a meal is marked cooked once, and items are created only under a list that exists.
2026-11-10's: a join request may carry `emailMasked`, the sender's sign-in email masked as the rules compute it
(`ro***@example.com`), and a batch's quantity may be below 0 (8.5 invariant 1). 2026-11-13's: a join request no longer
needs the code's `active` to be true (Pause joining was removed, DESIGN 5.1), and a planned meal may have up to 1,000
servings (it was 100; B21, DESIGN 5.9).

They refuse what builds before Android 1.0.0-beta.10 and web build 3 did when throwing several batches away at once,
when I cooked this used up two or more whole batches (and both Undos), and the second phone's write when two phones
race; the update gates below keep those builds out. How it went out on 2026-11-21, in the order set above (Android,
then the web app, then the rules):

1. Android 1.0.0 (versionCode 13, commit `744d251`) released with `release-beta.sh --name 1.0.0`. The rules had not
   changed since beta.11, so the script did not ask about them.
2. `minSupportedBuild` 13 in `config/app` (spoilnot-prod).
3. The web app from `744d251` to spoilnot-dev, then spoilnot-prod (`deploy-web.sh`, both with the full browser tests,
   answering no to the rules).
4. `minWebBuild` 3 in `config/app` (prod and dev). The web build from 2026-10-31 predates that gate, so its iPhones
   took the new build when the Home Screen app restarted.
5. The rules to spoilnot-dev, checked there by the owner with both apps (throw two batches away and Undo, I cooked
   this using up two batches, a member deleting a list with items, Clear household data, transfer and delete on a
   spare household, QA flows 43 and 47), then to spoilnot-prod:

   ```bash
   cd firebase && npx firebase deploy --only firestore:rules --project dev
   cd firebase && npx firebase deploy --only firestore:rules --project prod
   ```

6. Then a day's watch for refused writes (testers seeing "You don't have permission to do that."). If one shows,
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
   `--name 1.0.0` for a chosen name). Since 1.0.0 (2026-11-21) the name has no beta number, so every release passes
   `--name` (`--name 1.0.1` for fixes, `--name 1.1.0` for new features); without it the script stops before building;
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

**The first release after 2026-11-12 (privacy details):** release builds now drop the system log's verbose, debug and
info lines (R8, DECISIONS 2026-11-12) and turn Firebase Sessions off. On that beta, run QA section 5 (Crash reports)
and QA flow 53: a crash report still arrives with Send crash reports on. The app's own logs were always debug-only;
to read them, run a dev debug build.

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
  CI does; it also checks what the service worker precaches and caches on first use (`runtime-cache.spec.ts`). For a performance check: `npx vite preview --port 4173`, then
  `CHROME_PATH=<Playwright's Chromium> npx lighthouse http://localhost:4173/ --only-categories=performance,accessibility`
  (results in DESIGN 18.6).

## Benchmark setup (DESIGN 13.1)

Benchmarks and the baseline profile need an emulator that runs at full speed on the Mac's GPU, or the numbers mean
nothing (review 08, PERF-01). `adb` in the commands of this section and the next is
`~/Library/Android/sdk/platform-tools/adb`, which isn't on the PATH (the scripts call it by that path). Start Pixel_8
headless:

```bash
~/Library/Android/sdk/emulator/emulator -avd Pixel_8 -gpu host -feature -Uwb -no-window -no-snapshot -no-boot-anim -no-audio
```

- `-gpu host` draws with the Mac's GPU. The default `-gpu auto` can choose SwiftShader (software rendering), which
  draws frames several times slower than a phone.
- `-no-window`: macOS throttles an emulator whose window isn't in front (App Nap), so a windowed one runs at background
  priority (4 instead of 31) and its frames take 50 to 70 ms even in the system Settings list. Headless it runs at
  full priority and gives the same numbers run after run.
- `-feature -Uwb` stops the UWB service's crash loop on this image (a crash dump every few seconds takes a whole guest
  core). The emulator gives this image 4 GB whatever `-memory` says.

`./scripts/baseline-profile.sh` checks the device before it does anything (`--check` runs only the checks): it refuses
a SwiftShader renderer (`adb shell dumpsys SurfaceFlinger | grep '^GLES:'` must name "Apple M1 Pro"), warns when the
emulator's process runs under priority 31 (`ps -o pri -p <qemu pid>`), waits up to five minutes for the guest's
one-minute load to fall under 2.5, and warns when the Mac has under 20% memory free or Low Power Mode is on. Also:

- Keep the lid open and the Mac awake for the whole run: a sleeping Mac freezes the emulator mid-run, and the run is
  worthless (`pmset -g log | grep Sleep` shows when).
- Run one benchmark method per Gradle call with `-Pandroid.testInstrumentationRunnerArguments.class=<class>#<method>`;
  a `tests_regex` with `|` or `(` runs no tests at all (AGP passes it through the device's shell unquoted).
- Emulator numbers compare builds and screens on this Mac; they aren't phone timings. Phone timings come from the
  owner's phone checks (QA.md 4).
- The emulator drifts between sessions (on 2026-11-17 the keyboard made frames three to four times as janky as on
  2026-11-02 for the same build). When a number moves, run the previous build the same day: a second worktree at
  the older commit with `JankBenchmark.kt` and `AppDriver.kt` copied in, the same test, alternating with this one
  (DESIGN 13.1 has the 2026-11-17 A/B).

## Baseline profile (DESIGN 13.1)

Release builds ship the rules in `app/src/main/baselineProfiles/`, a list of the code the app runs while starting and
on its busiest screens, so Android can compile it ahead of time. AGP merges every file there:

- `baseline-prof.txt`, generated (below).
- `crash-reports-prof.txt`, kept by hand: crash reports and Firebase Sessions, which run at every start of a build on
  real Firebase but are off in the emulator builds the generator drives, so a fresh profile leaves them out. It holds
  those packages' rules from the last profile generated with them on (`f737440`, 2026-10-28). After a Firebase update,
  rules for classes that no longer exist are ignored; refresh the file only if crash reporting changes a lot.

Phones that install the APK from App Distribution get the profile through `androidx.profileinstaller` and compile it
during their next idle maintenance (often overnight), so the first day may still feel like before.

Regenerate it after large UI changes (new screens, a new list) and after library updates (Compose, Firebase,
DataStore: their code is in the profile too), on the benchmark setup above, with the Firebase emulators running
(`./scripts/emulators.sh`):

```bash
./scripts/baseline-profile.sh
```

It checks the device, seeds the emulator-only perf household (`PantryScrollPerfTest`, with `-e perf true`: 500 batches,
150 grocery items with 30 in the cart, 40 recipes without photos, three meals a day from a week ago to the end of next
week, and six months of counts for Your kitchen; each part is added only where it is missing, so a run on an older
household fills it in), runs `BaselineProfileGenerator` in the `benchmark` module against the dev benchmark build and
copies the result into the app. The journey signs in, waits for Home's cards, opens Your kitchen from Home's card,
scrolls Pantry, opens Add item, opens Plan's add sheet and visits every tab (it taps Skip tour when the app tour
shows). Every step checks that it opened
what it should and fails the run otherwise: the benchmark build shows the screens' test tags to UI Automator as
resource ids (`screen_pantry`, `card_home_attention`), and the sheets are found by their text. When a run fails with
"… didn't show", a button or screen changed: fix the selector in `AppDriver.kt` rather than dropping the step. Review
the diff and commit it.

`--measure` also runs `StartupBenchmark` and `PantryScrollBenchmark` without and with the profile and prints the
medians; `--measure-only` runs just the benchmarks with the profile already in the app. `--jank` runs `JankBenchmark`
(about 25 minutes), one test per Gradle call, with the profile already in the app: the first and steady scrolls of
Pantry, Groceries, Recipes, Plan and Home, Your kitchen opened from Home while its charts grow, every tab twice, Add
item and Plan's add sheet opened, dragged and closed, and typing "item 12" in Pantry's search and Groceries' quick add
(which capitalizes words, so it shows "Item 12"). For each it prints frame CPU time (P50, P90, P99) and the share of
janky frames, those whose `frameOverrunMs` is above 0, over all five iterations and per iteration. A test that fails
doesn't stop the others; the script names the failed ones at the end, exits 1, and keeps each one's test report in
`benchmark/build/jank-failures/<test>/` (the next test's run replaces the one in `build/outputs`). Only a run that
generated the profile ends with the reminder to commit it. Compare them with the last run in DESIGN 13.1; run it after changes to
a screen's lists, sheets or animations, and after Compose updates. `StartupBenchmark` reports
two numbers per start: `timeToInitialDisplayMs`, the first frame (Home's loading skeleton), and
`timeToFullDisplayMs`, when Home reports itself drawn with its cards (`ReportDrawnWhen`), which is what DESIGN 13.1's
cold-start budget is measured on.

If a benchmark says the app "must not be running prior to cold start", something restarted the app right after the
benchmark closed it: look in `adb logcat` for `Start proc … com.parcha.spoilnot.dev … for bound-service`. Crash
reports and Firebase Sessions caused it until 2026-10-31 (their uploads to the emulators' demo project failed and were
retried); both are now off in the emulator builds.

### What a late frame did

Every iteration leaves a Perfetto trace of its measured part next to the results
(`benchmark/build/outputs/connected_android_test_additional_output/…/*.perfetto-trace`). Macrobenchmark clears the
app's shader cache before every iteration, so a "first" scenario sees what a phone sees after an install or update.
The trace processor that reads them comes with the benchmark library and runs on the emulator:

```bash
adb push "$(find ~/.gradle/caches -name trace_processor_shell_aarch64 | head -1)" /data/local/tmp/tp
adb shell chmod 755 /data/local/tmp/tp
adb push <iteration>.perfetto-trace /data/local/tmp/t.trace
adb shell "/data/local/tmp/tp -q /data/local/tmp/q.sql /data/local/tmp/t.trace"
```

with a query in `q.sql` (pushed the same way), for example the GPU programs compiled on the RenderThread and the Skia
operation each one was compiled for:

```sql
select round(c.dur / 1e6, 1) as ms, p.name as op,
  (select a.name from ancestor_slice(c.id) a where a.name in ('flush layers', 'flush commands')) as pass
from slice c join slice p on p.id = c.parent_id where c.name = 'shader_compile';
```

`flush layers` is drawing into an offscreen layer and `flush commands` drawing onto the screen; the frame's
`drawLayer [<name>] <size>` slices name the layers (`AndroidEdgeEffectOverscrollEffect` is a list stretched past its
end). Frames that wait in `dequeueBuffer` / `waitForBufferRelease` are waiting for the emulator's compositor, not the
app (DESIGN 13.1).

## Opt-in instrumented tests

Three instrumented tests run only when asked, and the default `connectedDevDebugAndroidTest` run leaves them out
entirely (`HiltTestRunner` adds them to `notAnnotation`), so its report counts only tests that ran:

- `LiveImportTest` (`@LiveTest`) reads the 10 real recipe sites; `-Pandroid.testInstrumentationRunnerArguments.live=true`
  (the command is in docs/import-test-sites.md).
- `PantryScrollPerfTest` (`@PerfTest`) seeds the perf household for the benchmarks (500 batches, a long grocery list,
  recipes, a plan and six months of counts; "Baseline profile" above);
  `-Pandroid.testInstrumentationRunnerArguments.perf=true` (`baseline-profile.sh` passes it).
- `SharePreviewTimingTest` (`@TimingTest`) holds M5's share to preview, activity start included, to 5 s (the median of
  three shares). Run it alone on a quiet emulator, with nothing else running on the Mac:
  `./gradlew connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.parcha.spoilnot.feature.recipes.SharePreviewTimingTest -Pandroid.testInstrumentationRunnerArguments.timing=true`.
  Logcat's `SharePreviewTimingTest` line has the three times.

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
openssl rand -base64 32 | tee /dev/tty | gh secret set EXPORT_ENCRYPTION_KEY --repo jmalp/spoilnot
```

Save the printed key in your password manager: it is the only way to open an export outside GitHub. To open one on a
computer: `openssl enc -d -aes-256-cbc -pbkdf2 -iter 600000 -in household-exports.tar.gz.enc | tar -xzf -` (it asks
for the key).

To find an export: **Actions → nightly-export →** pick a run (its URL ends with the run ID) **→ Artifacts**.

### Restore a household

This is also the way back after an owner's **Clear household data** (Settings): pick the export from the night before
the clear. It brings back everything below; the cleared activity does not come back.

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
3. Run it again with **apply** ticked. Since 2026-11-22 every restore run waits for your approval, the listing and
   dry runs too: on the run's page, **Review deployments →** tick `restore` **→ Approve and deploy** (GitHub also
   emails you). The log ends with `restored N document(s), M failed`. If M is not 0, the
   run fails: those documents were not written (for example one too large for Firestore, which can take others in
   its batch with it). A restore sets each document as exported, so running it again is safe. `restore failed: no-such-household` means
   the target household does not exist (step 4).
4. To restore into a different household (for example after the original was deleted, recreate one in the app with
   the same owner's account, then find its ID in the Firebase console under `households`), fill in **target
   household**. A household with another owner also needs **different owner** ticked; the log then says `Target: a
   different owner, as asked`.

From a terminal: `gh workflow run restore.yml -f export_run_id=<id> -f household_id=<first characters> -f apply=false`
(add `-f different_owner=true` for step 4's other owner).

A file saved from Settings > Export can be restored the same way from a computer that has a service account key
in `FIREBASE_SERVICE_ACCOUNT_JSON`; with workload identity there is none, so create one for `restore-job`, use it
and delete it under **Keys** right after (`cd backend/reminder && npm ci --ignore-scripts && npm run build && node
dist/restore-main.js <file> <householdId> [--apply] [--different-owner]`), but the workflow is preferred because the
key never leaves GitHub. Its `profile` and `activity` are not restored. Settings > Export needs a connection: it reads
from the server and, offline, says "You're offline" and saves nothing.

## Regenerate an invite code by hand

Normally the owner rotates the code in the app (Settings > Household > invite card > Rotate), which also cancels
the old one. If that is not possible, in the Firebase console → Firestore:

1. Choose a new code: 10 characters from `ABCDEFGHJKLMNPQRSTUVWXYZ23456789` (no I, O, 0 or 1).
2. `inviteCodes` → **Add document** with that code as the document ID and fields `householdId` (string),
   `householdName` (string), `active` (boolean, true; builds before 2026-11-13 refuse a code without it),
   `createdBy` (the owner's uid), `createdAt` (timestamp).
3. `households/<id>`: set `inviteCode` to the new code.
4. Delete the old `inviteCodes/<old code>` document.

## A screen says "Couldn't load" or something is missing from a list (DESIGN 6.7, 8.6)

"Couldn't load." with Retry means one of the screen's listeners failed; the app listens again by itself after up to
32 s, so a short outage needs nothing. If it stays, the usual cause is the phone's access (it was removed from the
household, or the rules changed): the session moves it to the gate once the server confirms. A document with a field
of another type (a console edit, a restore, a buggy build) doesn't cause it any more: both apps read that field as
missing (a text quantity counts as 0) and skip a document only if it still can't be read. To find one, run a dev
debug build and filter logcat for `Skipped a document`, or in the web app on the emulators look for the same warning
in the browser console; it names the document ID. Fix the field in the console with its right type and a new
`updatedAt` (a server timestamp), so the changes-only listeners pick it up.

## Check quota usage (DESIGN 9.7)

**Today's figure** (the one place for it; other docs link here): about **1,970 reads a household a day**, so about
**17.8 households** fit under 70% of the free reads, with the default 120 reminder runs a day (quota-sim, 2026-11-23,
the B25 table below). Reads run out first. DESIGN 9.7 gives the budget and the method, and DESIGN 17 how each change
moved the figure.

Firebase console → the project → **Usage and billing → Usage** (or Firestore → Usage) shows reads, writes and
deletes per day against the free limits (50,000 reads, 20,000 writes, 20,000 deletes). Look at it after adding a
household or a new feature.

When reads run out, both apps keep working from each phone's copy with the red quota banner above every screen and
"Offline" on the tabs, until midnight Pacific (DESIGN 6.7, 7.5); nobody is signed out. Listeners retry at most every
32 s, which costs nothing while refused. Nothing needs doing; if it happens often, see quota-sim below.

`cd firebase && npm run quota-sim` replays a day of 10 typical households against the emulator. Knobs:
`HOUSEHOLDS`, `RUNS` (reminder runs that start in a day, default 120 since 2026-11-22: the on-time trigger's 96 and
about 24 of the 72 scheduled), `WEB` (how many of each
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

So the free plan held **about 17 households** of this size under the 70% warning line (reads run out first).
Since 2026-11-22 the on-time trigger starts the job 96 more times a day ("On-time reminders"), and each run read every
user with reminders on: `RUNS=120`, the new default, gave 24,160 reads (48.3%), about 14.5 households; `RUNS=168`
(every scheduled run starts as well) 26,128 reads, about 13.4.

Since 2026-11-23 (B25, "Who each run reads") a run reads only the users who may be due, so the number of runs barely
matters:

| `RUNS` | Scheduled jobs' reads (runs, digests) | Total reads | Writes | Households under 70%, before → now |
| --- | --- | --- | --- | --- |
| 24 | 3,152 (132, 240) | 19,412 (38.8%) | 974 | 17.3 → 18.0 |
| 120 (the default) | 3,440 (420, 240) | 19,700 (39.4%) | 1,070 | 14.5 → **17.8** |
| 168 | 3,584 (564, 240) | 19,844 (39.7%) | 1,118 | 13.4 → 17.6 |

A run costs three reads (the due query and the changed query, each at least one read even when empty, and the job's
state), plus each due user's profile once a day, the daily full read (one read a user) and each profile change read
by one or two runs (the simulation assumes one change a household a day). It writes the state once a run, so writes
grow by one a run; reads still run out first. The tables above were measured with `RUNS=24` before B25; only the
scheduled jobs' line and the writes change. Starting the job hourly (`SLOT_MINUTES = 60`, `RUNS=48`) now gives
19,484 reads, 18.0 households: it no longer saves much.
Other runs: `RUNS=72` (every scheduled run starts) 19,556 reads, about 17.9 households (22,192 and 15.8 before B25);
`RUNS=15` 19,385, about 18.1; `WEB=2` (two web phones per household) the same reads, since
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
has none, so 20,224 stays). On 2026-11-23 (B25) the job stopped reading every user each run: 19,412 at 24 runs
(19,700 at the default 120). The
largest remaining costs are each phone's daily full read and rule lookups. If usage nears 70%: check for a
regression with the simulation, then consider the Blaze plan (DESIGN 9.7; budget alerts notify but do not cap).

## How the jobs get their keys

The reminder job, the nightly export and the restore sign in to Firebase in one of two ways, chosen per job by the
code (`backend/reminder/src/credentials.ts`) and logged at the start of each run ("Signed in with workload identity
as export-job."):

- **Workload identity** (no key; DESIGN 12.2 control 8): when the repository variable `GCP_WORKLOAD_IDENTITY_PROVIDER`
  and the job's own service account variable are both set. The run asks GitHub for its OIDC token, Google's Security
  Token Service checks it against the pool's provider (this repository, `main`), and the job gets a token of its own
  service account that lasts an hour at most. Nothing is stored that could leak.
- **A key**, while workload identity isn't set up for that job: the JSON key in a repository secret. A job never gets
  the key when its workload identity variables are set.

| Job (workflow name) | Service account | Roles | Variable | Key until then |
| --- | --- | --- | --- | --- |
| `expiry-reminders`, prod | `reminder-job@spoilnot-prod` | Cloud Datastore User, Firebase Cloud Messaging API Admin | `GCP_SA_REMINDERS_PROD` | `FIREBASE_SERVICE_ACCOUNT_JSON` |
| `expiry-reminders`, dev | `reminder-job@spoilnot-dev` | the same | `GCP_SA_REMINDERS_DEV` | `FIREBASE_SERVICE_ACCOUNT_JSON_DEV` |
| `nightly-export` | `export-job@spoilnot-prod` | Cloud Datastore Viewer (reads only) | `GCP_SA_EXPORT` | `FIREBASE_SERVICE_ACCOUNT_JSON_EXPORT`, else the reminder job's prod key |
| `restore-from-export` | `restore-job@spoilnot-prod` | Cloud Datastore User | `GCP_SA_RESTORE` | `FIREBASE_SERVICE_ACCOUNT_JSON_RESTORE`, else the reminder job's prod key |

(Each account's full address ends in `.iam.gserviceaccount.com`.) `EXPORT_ENCRYPTION_KEY` is a repository secret in
every case: it is the export's passphrase, not a Google key. Variables and secrets are under **Settings → Secrets and
variables → Actions**, on the **Variables** and **Secrets** tabs. Only the owner can push branches or start
workflows, and workflows run for pull requests from forks get no secrets. CI, `secret-scan` and `keepalive` use no
secrets besides GitHub's own token and never ask for an OIDC token.

The reminder job and the export declare `environment: production`, and the restore `environment: restore`. Neither
holds secrets or has a branch rule (owner's choice, DECISIONS 2026-11-22); GitHub lists each run there as a
deployment, which is expected. Keep `production`'s **Required reviewers** and **Wait timer** off: either would hold
every scheduled run. `restore` has the owner as its required reviewer (since 2026-11-22), so a restore waits for the
owner's approval however it was started, also by the on-time trigger's token ("On-time reminders"); a job that waits
gets no OIDC token until it is approved. A secret with one of the names above added to an environment would win over
the repository's, so don't add one there.

Inside each job, `npm ci --ignore-scripts && npm run build` runs in a step that has no secret and can't ask for an
OIDC token (it unsets GitHub's two `ACTIONS_ID_TOKEN_REQUEST_*` variables first), and no package's install script
runs at all. Only the step that runs `node dist/…` can sign in; the export's Encrypt step and the restore's Decrypt
step get `EXPORT_ENCRYPTION_KEY`. CI installs with `--ignore-scripts` too, and the job's tests pass that way, so a
dependency that needs its install script fails CI before it reaches a job.

### Set up workload identity (once)

Google's guide is "Configure Workload Identity Federation with deployment pipelines" (cloud.google.com, IAM docs,
GitHub Actions). IAM, workload identity included, costs nothing, but Google doesn't say whether a project without a
billing account may use it (its guide lists "Verify that billing is enabled" as a step). So if any page below asks
you to enable billing or link a billing account, **stop there** (link nothing) and use one key per job instead (next
section). The workflows keep using the
keys until step 5, so the jobs run normally at every step.

1. **APIs.** https://console.cloud.google.com → pick **spoilnot-prod** at the top → **APIs & Services → Library**.
   Search for each and **Enable** it: **Identity and Access Management (IAM) API**, **IAM Service Account
   Credentials API**, **Security Token Service API**. Then pick **spoilnot-dev** and enable **IAM Service Account
   Credentials API** there too.
2. **Pool and provider** (in spoilnot-prod only; dev's account trusts the same pool). **IAM & Admin → Workload
   Identity Federation → Create pool** (or **Get started**):
   - Name `GitHub`, Pool ID `github`. **Continue**.
   - Provider: **OpenID Connect (OIDC)**. Name `spoilnot`, Provider ID `spoilnot`. Issuer (URL):
     `https://token.actions.githubusercontent.com/`. Audiences: **Default audience**. **Continue**.
   - Attribute mapping, one row each (**Add mapping**):

     | Google | OIDC |
     | --- | --- |
     | `google.subject` | `assertion.sub` |
     | `attribute.repository_id` | `assertion.repository_id` |
     | `attribute.repository_owner_id` | `assertion.repository_owner_id` |
     | `attribute.ref` | `assertion.ref` |
     | `attribute.workflow` | `assertion.workflow` |

   - **Attribute conditions → Add condition**, exactly:
     `assertion.repository_id == '1416247646' && assertion.repository_owner_id == '75514361' && assertion.ref ==
     'refs/heads/main'` (on one line). These are `jmalp/spoilnot`'s and its owner's numeric IDs (`gh api
     repos/jmalp/spoilnot --jq '.id, .owner.id'`), which stay the same if a name changes, as Google advises: GitHub
     signs every repository's tokens with one issuer, so without the condition any repository could use the pool.
     `ref` rather than `sub`, because a job with an environment has `sub` =
     `repo:jmalp/spoilnot:environment:production`. **Save**.
   - Note the project number: **IAM & Admin → Settings → Project number** (or the Dashboard). Below it is `<NUMBER>`.
3. **Two new accounts** (spoilnot-prod, **IAM & Admin → Service Accounts → + Create service account**; no keys):
   - `export-job`, role **Cloud Datastore Viewer**;
   - `restore-job`, role **Cloud Datastore User**.
   `reminder-job` already exists in both projects.
4. **Let each workflow act as its account.** For each row below: the project at the top → **IAM & Admin → Service
   Accounts →** the account **→ Principals with access → Grant access**. New principal (one line, with `<NUMBER>`
   from step 2):
   `principalSet://iam.googleapis.com/projects/<NUMBER>/locations/global/workloadIdentityPools/github/attribute.workflow/<workflow>`.
   Role: **Workload Identity User**. **Save**.

   | Project | Account | `<workflow>` |
   | --- | --- | --- |
   | spoilnot-prod | `reminder-job` | `expiry-reminders` |
   | spoilnot-prod | `export-job` | `nightly-export` |
   | spoilnot-prod | `restore-job` | `restore-from-export` |
   | spoilnot-dev | `reminder-job` | `expiry-reminders` |

   The workflow name is the `name:` at the top of each file. With the pool's condition, only that workflow's runs on
   `main` of this repository can act as the account.
5. **Turn it on** (the repository variables; values are not secret):
   ```bash
   gh variable set GCP_WORKLOAD_IDENTITY_PROVIDER --repo jmalp/spoilnot --body "projects/<NUMBER>/locations/global/workloadIdentityPools/github/providers/spoilnot"
   gh variable set GCP_SA_REMINDERS_PROD --repo jmalp/spoilnot --body "reminder-job@spoilnot-prod.iam.gserviceaccount.com"
   gh variable set GCP_SA_REMINDERS_DEV --repo jmalp/spoilnot --body "reminder-job@spoilnot-dev.iam.gserviceaccount.com"
   gh variable set GCP_SA_EXPORT --repo jmalp/spoilnot --body "export-job@spoilnot-prod.iam.gserviceaccount.com"
   gh variable set GCP_SA_RESTORE --repo jmalp/spoilnot --body "restore-job@spoilnot-prod.iam.gserviceaccount.com"
   ```
   Check each address against the account's page. A job whose variable isn't set yet keeps using its key, so they can
   be set one at a time. To go back to the key for a job, delete its variable (`gh variable delete GCP_SA_EXPORT
   --repo jmalp/spoilnot`).
6. **A dry run of each job** ("Dry runs" below). Each log says "Signed in with workload identity as …".
7. **Remove the keys** once all four runs signed in with workload identity, and a scheduled reminder run after them
   did too:
   - Google Cloud console, each project → **IAM & Admin → Service Accounts → reminder-job → Keys** → delete every key
     (the trash icon). Deleting it is what makes any copy useless.
   - The secrets:
     ```bash
     gh secret delete FIREBASE_SERVICE_ACCOUNT_JSON --repo jmalp/spoilnot
     gh secret delete FIREBASE_SERVICE_ACCOUNT_JSON_DEV --repo jmalp/spoilnot
     ```
   - Keep `EXPORT_ENCRYPTION_KEY`.

**If a run fails after step 5**, its log says `<job> failed: <code>`; the codes are in the table under "Reminder
job".

### If Google asks for billing: one key per job

The same split without workload identity: a leaked export key can then only read.

1. In spoilnot-prod create `export-job` (**Cloud Datastore Viewer**) and `restore-job` (**Cloud Datastore User**),
   as in "Set up workload identity" step 3.
2. A key for each (the account → **Keys → Add key → Create new key → JSON**), stored minified on one line, then
   delete the files:
   ```bash
   jq -c . ~/Downloads/<export-job file>.json | gh secret set FIREBASE_SERVICE_ACCOUNT_JSON_EXPORT --repo jmalp/spoilnot
   jq -c . ~/Downloads/<restore-job file>.json | gh secret set FIREBASE_SERVICE_ACCOUNT_JSON_RESTORE --repo jmalp/spoilnot
   rm ~/Downloads/<export-job file>.json ~/Downloads/<restore-job file>.json
   ```
3. Store the reminder job's keys on one line too: rotate both ("Rotate the key"; step 5 there stores them minified).
4. A dry run of each job ("Dry runs"): the export's log says "Signed in with key as export-job", the restore's "as
   restore-job".

### Dry runs

Start each by hand after a change to how the jobs sign in, then read the log (`gh run list --repo jmalp/spoilnot
--workflow <file> --limit 1`, then `gh run view <run-id> --repo jmalp/spoilnot --log | grep -E "Signed in|project
|failed"`):

```bash
gh workflow run reminders.yml --repo jmalp/spoilnot -f dry_run=true
```
Both projects' runs log "[dry run] project …" and change nothing.

```bash
gh workflow run nightly-export.yml --repo jmalp/spoilnot
```
The export has no dry run: it only reads, so a run by hand is the nightly run once more (one more encrypted artifact,
kept 14 days).

```bash
gh workflow run restore.yml --repo jmalp/spoilnot -f export_run_id=<run ID of a nightly-export run> -f household_id=<first 4 or more characters of a household ID> -f apply=false
```
It waits for your approval: the run's page → **Review deployments → restore → Approve and deploy**. Without "apply"
it writes nothing; it signs in and checks the target household ("Target: same owner as the export."). Leaving
`household_id` empty only lists the exports and doesn't sign in.

## Scheduled workflows

`expiry-reminders` (three times an hour), `nightly-export` (daily) and `secret-scan` (weekly) run on schedules. GitHub disables
scheduled workflows in a public repository after 60 days without activity, and shows a banner on the Actions tab.
`keepalive.yml` re-enables them (and itself) through the API on the 3rd of every month, which resets that clock. If
one is disabled anyway: **Actions →** the workflow **→ Enable workflow** (or
`gh workflow enable reminders.yml --repo jmalp/spoilnot`). Then run it once by hand to check. If a run of the reminder
job or the export waits for approval, check the `production` environment ("How the jobs get their keys"): no
reviewers, no wait timer. A restore always waits for the owner's approval ("Restore a household"). If any job fails in "Set up job" naming an action, check the Actions allow-list ("Dependency
updates").

**Logs are public.** Workflow logs show counts, dates and the first characters of household IDs only: the reminder
job logs neither user IDs nor what is in a digest (a dry run shows how many items and meals, never names or titles), and
the export and restore print counts. Keep it that way when changing the jobs.

## Dependency updates

Dependabot (`.github/dependabot.yml`) opens grouped pull requests on Mondays for Gradle, the four npm projects
(`firebase`, `backend/reminder`, `web`, `tools/themes`) and GitHub Actions. Merge one when CI is green; for an app
library update, also install a build on a phone before the next beta, and regenerate the baseline profile after a
Compose, Firebase or DataStore update (their code is in it; "Baseline profile"). With Dependabot security updates on
(**Settings → Advanced Security**), security updates arrive as soon as they are published.

- **Actions** are pinned to a full commit SHA with the version in a comment (`uses: actions/checkout@<sha> #
  v7.0.1`), and Dependabot updates both. Pin a new action the same way: `gh api
  repos/<owner>/<action>/commits/<tag> --jq .sha`, and check that the tag is the release you meant.
- **Allowed actions** (since 2026-11-22, **Settings → Actions → General → Actions permissions**): GitHub's own actions
  plus `gradle/actions/setup-gradle@*` and `gitleaks/gitleaks-action@*`; Marketplace "verified creators" are not
  allowed, and **Require actions to be pinned to a full-length commit SHA** is on. A workflow that uses any other
  action fails in "Set up job" with a message naming it. To add an outside action, pin it as above and add
  `<owner>/<repo>[/<path>]@*` to that list in the same settings page (or `gh api -X PUT
  repos/jmalp/spoilnot/actions/permissions/selected-actions` with the whole list). Check the settings with `gh api
  repos/jmalp/spoilnot/actions/permissions` and `…/permissions/selected-actions`.
- **npm** installs in CI and the jobs use `--ignore-scripts`. A dependency that needs its install script fails CI;
  don't add one to `backend/reminder` (its jobs hold the keys).
- **npm audit:** run `npm audit fix --ignore-scripts` (never `--force`) in a project and review the lockfile diff;
  it may pick an older patch release that sits outside an advisory's range. When no parent release has the fix, add
  an `overrides` entry in that project's `package.json` (scoped to the old line, like `"glob@^10"`, so other users
  are untouched), check that the callers' APIs still exist in the new version, run the project's tests, and add a
  DECISIONS entry saying when to remove it. All four projects pass `npm audit`: since 2026-11-14, and `web/` since
  2026-11-25, when the audit stopped listing its build-time deepmerge (DECISIONS 2026-11-25). The overrides (DECISIONS 2026-11-14): `@grpc/grpc-js` in `web/` and
  `firebase/` (the Firebase web SDK pins it to 1.9 for its Node build; drop it when a release lifts that pin, and
  update `firebase` in both projects together), glob, minimatch, uuid and OpenTelemetry core in the Firebase CLI and
  the web build tools, and `@babel/plugin-transform-runtime` in `web/`, which only lets `@vitejs/plugin-react`
  install. Before removing one, `npm ls <package>` shows whether the parent brings the fixed version yet.
- **Guava** comes only through Firestore's gRPC; the catalog's `guava` is the lowest version allowed (a constraint in
  `app/build.gradle.kts`). Dependabot raises it. Remove both when Firebase brings that version or a newer one.
  `errorprone` works the same way: Firestore 27.2 dropped the annotations from gRPC, and without the constraint
  the instrumented tests' classpath fails to resolve. Remove it when Firebase asks for 2.30.0 or later.
- **Compose** 1.13 (BOM 2026.11.01) and later need `compileSdk` 37.1 or later: the app compiles against 37.2 with
  `compileSdk { version = release(37) { minorApiLevel = 2 } }` in `app` and `benchmark` (`targetSdk` stays 37), and
  Gradle downloads the SDK platform the first time. A Compose update can also remove APIs the app uses: read the
  compile errors first, then rerun `verifyRoborazziDevDebug` and the instrumented tests, which CI does not run.
- **AGP** 9.5.2 since 2026-11-24. 9.5.0 and 9.5.1 could not configure the release variants ("Compose Preview requires
  unit tests"), so the app stayed on 9.4.1 until then. Before taking a new AGP, check that `./gradlew :app:tasks`
  lists the `ProdRelease` tasks: CI builds only debug variants.
- Gradle dependency verification is not used (DECISIONS 2026-11-10); the Gradle wrapper is checked by its
  `distributionSha256Sum` and by `setup-gradle` in CI.
- **Playwright's image** (CI's web job, DESIGN 13.2): Dependabot updates `@playwright/test` in `web/` but not the
  job's `container.image`. When it does, the web job warns ("update the web job's image") and installs WebKit itself,
  so the update still passes. Then raise the image in `.github/workflows/ci.yml` to the same version, the `resolute`
  tag (Ubuntu 26.04, like the runner), with its digest from
  `curl -sI -H "Accept: application/vnd.oci.image.index.v1+json" https://mcr.microsoft.com/v2/playwright/manifests/v<version>-resolute`
  (`docker-content-digest`), and the `image=` version in the browser step.
- **Fonts** (DESIGN 6.4): the app bundles subsets of the full fonts in `tools/fonts`. For a new font version, replace
  the file there and run `./scripts/subset-fonts.sh` (`brew install harfbuzz` once), then
  `./gradlew testDevDebugUnitTest --tests '*FontSubsetTest'` and `verifyRoborazziDevDebug`. A new string with a
  character outside the subset fails `FontSubsetTest`: add its range to the script.

## Secret scanning

GitHub's secret scanning with push protection (**Settings → Advanced Security**) refuses a push that holds a known
kind of key, before it is public. `secret-scan.yml` also runs gitleaks on every push and pull request and over the
whole history weekly. If either finds something: treat the secret as leaked even after deleting it from the repository. Rotate it first (for the
service account, see "Rotate the key"), then remove it from the code. Only the Google API key in the Firebase configuration files
(`google-services.json`) is allowed, on purpose (`.gitleaks.toml`): any other secret in those files is caught like
anywhere else. `secret-scan.yml` pins the gitleaks version that `.gitleaks.toml` was checked with; when raising it,
run `gitleaks git --config .gitleaks.toml .` locally with the new version first (`brew install gitleaks`), which
must find nothing.

## npm on the owner's Mac (DESIGN 12.2 control 9)

The deploy and release scripts run on the owner's Mac, with the Firebase CLI's sign-in and the upload keystore
there. So npm on that Mac runs no package's install script (once):

```bash
npm config set ignore-scripts true
```
```bash
npm config get ignore-scripts
```

The second prints `true`. It applies to every project and to `npm install -g`. No package in this repository needs
its script (checked 2026-11-25): `@firebase/util` and `protobufjs` only print or write optional files, `re2` is an
optional speed-up of the hosting emulator's, and `fsevents` ships built. CI and the jobs install the same way. The
repository's own `npm run …` scripts still run; npm skips `pre…` and `post…` scripts around them, and none of the
four projects has one. The scripts in `scripts/` install with `--ignore-scripts` either way. If a package someday
needs its script, run it for that package alone, in that project: `npm rebuild <package> --ignore-scripts=false`.
Undo with `npm config delete ignore-scripts`.

## Sign-up limit per IP (Firebase Auth)

Firebase Auth allows 100 new accounts an hour from one IP address by default. Spoil Not has a few testers, so a
lower limit slows anyone creating accounts in bulk (DESIGN 12.2 control 2). In each project (spoilnot-prod and
spoilnot-dev): Firebase console → **Authentication → Settings → Sign-up quota** → set the number of sign-ups per IP
address per hour to **10** and save. Google describes this setting as a temporary change with a start time and a
duration. If the page only lets you raise the limit, or only for a few days, leave it at the default and say so;
DECISIONS then records that it wasn't possible. The tests use the emulator, which has no such limit.
