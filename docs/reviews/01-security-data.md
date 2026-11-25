# 01 Security: data access, backend and supply chain

Reviewed `0334b6a` on 2026-11-01 (the branch `review/2026-11` at `8004603`). Platforms: the backend (Firestore rules,
the reminder job, the nightly export and the restore, the workflows), the supply chain and the GitHub settings. The
Android and web code was read only where it decided a finding's severity; section 02 covers the clients.

**Published 2026-11-24.** During the review this report was kept out of the repository until its fixes had shipped
(PLAN.md, ground rule 9). This is the cleaned-up version. Every finding keeps its ID and severity, and says what the
weakness was, why it mattered and how it was fixed. Reproduction steps, test code, payloads and the owner's console
checklist are left out, and so is any real ID, address or key. Where the private report held a reproduction, this one
says "Reproduced; details withheld."

**Method.** The rules were read against DESIGN 4, 8, 12.3 and 12.4 and the deviations in DECISIONS. They were then
tested in the Firestore emulator with throwaway checks: 31 rules checks and 7 checks of the real job code. The
workflows and repository settings were read through `gh api`. Dependencies were checked with `npm audit` and OSV.dev.
Only made-up accounts and data were used, and no real project was touched.

## Summary

The rules held against every direct attack tried. Strangers, people waiting to join, members acting as the owner and
removed members were denied on every path, collection-group queries included. The weak points were around the rules:

- **SECD-01 (P0):** the hourly reminder job trusted each profile's household pointer. A removed member kept getting
  that household's expiring food and their planned meals every day until they opened the app. Fixed the same day
  (#72).
- **SECD-02 (P1):** a failed write in the job's housekeeping or in a restore could have printed the production
  service account key into a public Actions log. It never happened: all 72 workflow logs were clean (#72).
- **SECD-03 to SECD-05 (P1):** any signed-up stranger, with no invite, could stop everyone's reminders, stop the
  nightly backups and fill the free storage (the rules didn't limit sizes), or drain the shared daily reads.
- **SECD-08 (P2):** on GitHub, secret scanning, push protection and Dependabot alerts were off on this public
  repository, and `main` was unprotected.

Rules matrix coverage was 47 of the 68 allowed path and operation pairs. 17 findings: 1 P0, 4 P1, 3 P2, 9 P3. Four
items from other sections are added at the end. **All are fixed and live.** The owner chose to leave three parts as
they are: SECD-05's App Check, SECD-08's environment and SECD-16's Gradle verification. Three small parts weren't
done, and each finding says so: SECD-04's export guard, SECD-15's integrity check, and cleaning up 04-S2's older
items.

## Fixes at a glance

| Finding | Severity | Fixed in | Live |
| --- | --- | --- | --- |
| SECD-01 Removed member still gets digests | P0 | jmalp/spoilnot#72 | Job, 2026-11-01 |
| SECD-02 Key could reach a public log | P1 | jmalp/spoilnot#72 | Job, 2026-11-01 |
| SECD-03 One account stops everyone's reminders | P1 | B01, jmalp/spoilnot#75 | Job, 2026-11-08 |
| SECD-04 No size limits in the rules | P1 | S2, jmalp/spoilnot#78 | Rules deployed to prod 2026-11-21 |
| SECD-05 One account drains the day's reads | P1 | B01, jmalp/spoilnot#75 (job); App Check not adopted (owner's choice) | Job, 2026-11-08 |
| SECD-06 Long names unregister other members' phones | P2 | B01, jmalp/spoilnot#75; S2, jmalp/spoilnot#78 | Job 2026-11-08; rules 2026-11-21 |
| SECD-07 Key present while npm installs | P2 | S3, jmalp/spoilnot#94 | Workflows, 2026-11-10 |
| SECD-08 GitHub protections off | P2 | S3, owner's settings, jmalp/spoilnot#94, jmalp/spoilnot#150 | Settings on; keys stay repository secrets (owner's choice) |
| SECD-09 Forged names and authors | P3 | S2, jmalp/spoilnot#78 | Android 1.0.0-beta.10, 2026-11-14; rules and web, 2026-11-21 |
| SECD-10 Members delete outright, rewrite counts | P3 | S2, jmalp/spoilnot#78 | Rules, 2026-11-21 |
| SECD-11 Two owners after a transfer | P3 | S2, jmalp/spoilnot#78 | Rules, 2026-11-21 |
| SECD-12 IDs in error logs | P3 | jmalp/spoilnot#72, B01 jmalp/spoilnot#75 | Job, 2026-11-08 |
| SECD-13 Rules matrix gaps | P3 | S2, jmalp/spoilnot#78 | CI, 2026-11-08 |
| SECD-14 Actions pinned to tags | P3 | S3, jmalp/spoilnot#94, jmalp/spoilnot#150 | Workflows 2026-11-10; settings 2026-11-22 |
| SECD-15 Backups and restore | P3 | B13, jmalp/spoilnot#101 | Workflows, 2026-11-12 |
| SECD-16 Supply chain | P3 | S3, jmalp/spoilnot#94; S4, jmalp/spoilnot#106 | Android 1.0.0-beta.10, 2026-11-14 |
| SECD-17 Security docs out of date | P3 | Each fix PR | Docs on `main` |
| 04-S2 Items left under a deleted list | P3 | B08, jmalp/spoilnot#90 | Rules, 2026-11-21 |
| 00-S1, 00-S2, 05-S2, 05-S3 | Notes | See below | — |

"Job" means the reminder, export and restore jobs. They run from `main`, so a job fix takes effect at the next run
after merging. The Android fixes first reached testers in 1.0.0-beta.10 (2026-11-14). Rules changes were held back
until Android 1.0.0 (build 13, `744d251`) and the web app from the same commit had gone out on 2026-11-21, and were
deployed to prod the same day (DECISIONS 2026-11-21).

## Metrics

### Rules matrix

| Metric | At review | How measured | Now |
| --- | --- | --- | --- |
| Path and operation pairs | **75** (15 `match` paths × get, list, create, update, delete) | `firebase/firestore.rules` | — |
| Pairs DESIGN allows to someone, with both an allow and a deny test | **47 of 68 (69%)** | Each call in `firebase/test/rules.test.js` mapped to its pair | 116 rules tests after #78, 122 after #90; the missing pairs are covered ("more of the matrix") |
| Pairs nobody may do, with a deny test | **2 of 7** | Same | Covered |
| Throwaway rules checks | **31 pass**: 19 showed the rules hold, 12 showed a gap | Firestore emulator | The 12 gaps are SECD-01, 03, 04, 09, 10 and 11, all closed |
| Throwaway job checks | **7 pass**, using the real job and backup code | Firestore emulator | — |

### Attackers modeled

| Attacker | Result at review | Now |
| --- | --- | --- |
| A stranger with their own account | Denied on every household path, code listing and collection-group query. **But** a household of their own let them store unlimited sizes (SECD-04), stop everyone's reminders (SECD-03) and drain the shared quota (SECD-05) | Sizes and fields limited by the rules; the job caps devices, reads and retries |
| Someone with a pending join request | Denied | — |
| A member acting as the owner | Denied | — |
| A member harming other members | Denied on others' profiles and devices. **But** they could forge names and authors (SECD-09), get others' phones unregistered through the job (SECD-06), and delete outright and rewrite counts (SECD-10) | Closed by S2 and B01 |
| The owner | Could leave two owners (SECD-11) | One owner, enforced |
| A removed member whose phone still has a session | Denied. **But** the daily digest kept coming (SECD-01) | Fixed in #72 |
| Anyone reading the repository, logs and artifacts | Clean: only the Firebase configuration's public API keys, counts in the logs, encrypted exports. **But** error paths could print IDs (SECD-12) and the key (SECD-02) | Error paths print a fixed line with a code |
| A malicious pull request | No `pull_request_target`, no script injection, no secrets for forks, approval for first-time contributors | Approval for all outside contributors |
| A malicious dependency | Install scripts ran next to the key (SECD-07) | Installs run without secrets and without scripts |

### Supply chain and workflows

| Metric | At review | Now |
| --- | --- | --- |
| npm advisories | `web/` 24 (4 high, in the Firebase SDK's Node build only, not the browser bundle); `firebase/` 23 (dev tools); `backend/reminder/` 6 moderate; `tools/themes/` 0 (details: 00-S1) | Clean in all four (the last, a low advisory in the web's service-worker build, closed 2026-11-25; DECISIONS) |
| Android advisories, prod release runtime classpath (277 artifacts) | 1 high, in Guava (not reachable, SECD-16) | Guava held at 33.8.0-android or later |
| Dependabot alerts and security updates | **Off** | On |
| Dependabot version updates | 3 of 4 npm projects (`tools/themes` missing) | All four |
| Packages with install scripts in the job's production tree, run next to the key | 2 | Installs run with `--ignore-scripts`, in a step without secrets |
| Jobs with least-privilege `permissions:` | 9 of 9 | 9 of 9 |
| Action references pinned by commit SHA | **0 of 27** | All (28 references to 8 actions); SHA pinning required by the repository |
| Workflows holding secrets | 3 (reminders, nightly export, restore), GitHub's own actions only | Same; the restore waits for the owner's approval (`restore` environment, 2026-11-22) |
| Script injection points, `pull_request_target` triggers | 0, 0 | 0, 0 |
| Public log retention | 90 days | 90 days |

### Quota abuse and job robustness

| Metric | At review | Now |
| --- | --- | --- |
| Largest document a member or a stranger could write | About 1 MB, on most household collections | DESIGN 8.1's sizes (names 80, notes 500, links 2,000, lists bounded) |
| Devices per user | Unlimited | The rules can't count documents; the job sends to at most the 10 newest devices per user |
| Collections with every field shaped by the rules | 6 of 15 | All household collections, profiles and devices |
| Documents read per hourly run, one due user with no devices and 300 batches | **303 every run**, never marked done | Users without a device are skipped before any stock query; the stock query is capped |
| Reminder runs per day | **4 to 7** of 24, and none in the 07:00 UTC prune hour | Three schedule entries an hour (#75), then an on-time trigger (#151); the prune is keyed on the UTC date |

### Controls since the sign-off (SECURITY_REVIEW, 2026-10-18)

| Control | At review | Finding | Now |
| --- | --- | --- | --- |
| 1. Rules authorize everything, deny by default | ✅ The jobs bypass the rules and didn't check membership | SECD-01, SECD-13 | ✅ |
| 3. Membership on every path; owner-only actions | ✅, but a transfer could leave two owners | SECD-11 | ✅ |
| 5. Removed members lose access at once | ❌ The reminder job kept sending them the household's digest | SECD-01 | ✅ |
| 8. No secrets; secret scanning and push protection | ⚠ GitHub's scanning and push protection were free on the public repository but off; a crash could print the key | SECD-02, SECD-08 | ✅ |
| 9. Dependency automation; Actions pinned | ⚠ Alerts and security updates off; one project not covered; major tags | SECD-08, SECD-14, SECD-16 | ✅ |
| 10. App Check (SHOULD) | ⏸ Not adopted, and the quota risk it was weighed against was larger than assumed | SECD-04, SECD-05 | ⏸ Not adopted (owner's choice) |
| Threat "abuse of the free quota": rules limit sizes and shapes | ❌ Mostly not | SECD-04 | ✅ |
| Backups encrypted | ✅, with gaps | SECD-15 | ✅ |
| Logs hold counts only | ⚠ Normal output yes; error paths no | SECD-02, SECD-12 | ✅ |

### GitHub settings

| Setting | At review | Now (checked 2026-11-24) |
| --- | --- | --- |
| Secret scanning, push protection | **Off** | On |
| Dependabot alerts, security updates | **Off** | On |
| Rulesets on `main` | **None** | An active `main` ruleset |
| Allowed actions | All; SHA pinning not required | GitHub's own plus `gradle/actions/setup-gradle` and `gitleaks/gitleaks-action`; full-length SHA pins required |
| Fork pull request workflows | Approval for first-time contributors | Approval for all outside contributors |
| Default workflow permissions | Read; Actions can't approve pull requests | Same |
| Secrets | 3 repository secrets, no environments | 3 repository secrets (owner's choice, SECD-08); a `restore` environment with the owner as required reviewer |

## Findings

### SECD-01 · P0 · Confirmed · Backend — A removed member kept getting the household's expiring food and their meals every day

- **Area:** the hourly reminder job, which groups due users by the household their profile points to.
- **What it was:** when the owner removed a member, that member's profile still pointed at the household until
  their own app ran and noticed. Only the user can write their profile, so the owner couldn't clear it. The job
  trusted the pointer. It read that household's stock and the day's meals and sent the removed person a digest every
  day, for as long as they didn't open the app. Reading the notification on the lock screen doesn't open the app.
  After a household was deleted the digests were empty, so that case leaked nothing.
- **Why it mattered:** this is household data reaching someone outside the household, every day with no end. It
  broke DESIGN 12.2 control 5 ("lose access at once"). It hit every removal of someone who didn't open the app soon
  after: an ex-partner, a former roommate, or a lost phone the owner cut off by removing its member. What leaked was
  limited to the names of food about to expire and the meals they were listed to cook.
- **Evidence:** reproduced against the emulator with the real job code; details withheld.
- **Fix:** jmalp/spoilnot#72, merged 2026-11-01. Before reading a household, the job checks each due user's member
  document and sends only to current members. A stale pointer is cleared as the app would clear it. Tests: emulator
  tests for a removed member (no digest, pointer cleared). Live from the next job run; no rules deploy or app release
  was needed.

### SECD-02 · P1 · Likely · Backend — A failed write in the job's housekeeping or a restore could have printed the production key in a public log

- **Area:** the reminder job's prune, the restore, and the three jobs' entry points.
- **What it was:** a failed bulk write produces an error that holds a reference to the Firestore client, and through
  it the service account credentials. The prune and the restore left those failures unhandled. Node prints an
  unhandled error in full, so the first failed write would have printed a full household path and the private key,
  split over many lines. GitHub masks a multi-line secret line by line, and the key's lines would not have matched.
  Run logs are public for 90 days.
- **Why it mattered:** the key reads and writes every household and sends notifications. One such log would have
  exposed everyone until the key was rotated.
- **Evidence:** the printing was reproduced with a throwaway key made for the test and never registered anywhere;
  details withheld. **Why Likely:** the trigger. The writer retries the common transient errors, and no way was found
  for an outsider to force a failure that isn't retried. GitHub's masking was reasoned from its observed behavior.
  The owner checked all 72 workflow logs: none held key material, so no key was rotated.
- **Fix:** jmalp/spoilnot#72, merged 2026-11-01. Bulk writes go through one helper that handles every write and
  counts failures, and the entry points print one fixed line with the error's code, also for uncaught errors. Tests:
  a failing prune and a failing restore whose output holds no key material and no full household ID. RUNBOOK: rotate
  the key at once if it ever shows in a log. Live from the next job run.

### SECD-03 · P1 · Confirmed · Backend — Any signed-up account could stop the reminders for every household after theirs, every hour

- **Area:** the reminder job's send step; the rules for device documents.
- **What it was:** the job sent all of a user's devices in one call. That call refuses more messages than its
  per-call limit, and nothing in the job's loop caught the error, so one such user ended the whole run. Every
  household whose turn came later got no digest, and the day's prune was skipped. The account's own digest was never
  marked done, so this repeated every run. It needed only one account with a household of its own (no invite) and
  enough device documents, which the rules allowed (rules can't count documents).
- **Why it mattered:** missing reminders for every household in both projects, for as long as it lasted.
- **Evidence:** reproduced against the emulator with the real job and messaging code; details withheld.
- **Fix:** B01, jmalp/spoilnot#75, merged 2026-11-08. Sends go in chunks within the limit, to at most the 10 most
  recently updated devices per user. Each household and each user is wrapped on its own: a failure is counted with no
  IDs, the run carries on, and it exits non-zero at the end so GitHub still emails. Tests: job unit tests with a
  throwing sender. Live from the next job run.

### SECD-04 · P1 · Likely · Rules — Without size limits, one stranger's household could stop the nightly backups for everyone and fill the free storage

- **Area:** the rules for products, stock, grocery items, recipes, meals, lists and activity; the nightly export.
- **What it was:** the rules checked a few fields (names, dates, enums) and accepted any other field of any size. A
  member, or anyone in a household they created, could store documents of about 1 MB each. DESIGN 8.1 promised names
  of 80 characters, notes of 500 and lists of 100. The consequences:
  1. **Nightly export:** it writes each household as one JSON string, so a big enough household would have made it
     fail for every household, every night, until someone found the data.
  2. **Storage:** about a thousand such writes would have filled Spark's 1 GiB.
  3. **Network:** reading one's own large documents repeatedly would have spent the month's outbound allowance,
     leaving every household without Firestore until the month ended.
- **Why it mattered:** every household's backups, and all of Firestore on the free plan.
- **Evidence:** the large writes and the export's limit were reproduced separately; details withheld. **Why Likely:**
  not run end to end (a household that size needs several GB of memory), and the emulator has no quotas.
- **Fix:** S2, jmalp/spoilnot#78, merged 2026-11-08. Every household collection, profiles and devices accept only
  the fields the apps write, with DESIGN 8.1's and 11.6's sizes. Recipe links must be http or https, lists are
  bounded and numbers finite. A new account's own household gets the same limits. Before deploying, every released
  build's writes were checked against the new rules. Tests: "allowed fields and sizes" in `rules.test.js`. Live in
  prod: 2026-11-21.
- **Not done:** the export itself still has no per-household guard or size cap. With the size limits, a household
  would need far more documents than a day's free writes to reach the export's limit.

### SECD-05 · P1 · Likely · Backend — One account could use up the day's reads for everyone, and the reminder job made it daily

- **Area:** the reminder job's reads; the free plan's shared daily quota.
- **What it was:** Spark gives the whole project 50,000 reads a day. The job read a household's whole stock window
  before checking whether the due user had a device, and retried a user without devices every run. So a one-time
  setup (many batches and a due member with no device) made every run read all of them, every day, until the owner
  noticed. Devices whose sends failed with an error that wasn't "dead" did the same.
- **Why it mattered:** every household. When reads run out, nothing loads or saves until the quota resets, and on
  Android at the time the app turned into a spinner (05-S2). Low likelihood, wide impact.
- **Evidence:** the repeated reads were counted against the emulator; details withheld. **Why Likely:** the emulator
  has no quota.
- **Fix:** B01, jmalp/spoilnot#75, merged 2026-11-08. The job reads devices first and skips users without one before
  any stock query. The stock and meal queries are capped per household. A user is marked done for the day after 3
  runs without a delivered digest. S2's size limits cap the write side. Live from the next job run.
- **Accepted:** rules can't limit read rates. The platform answer, App Check, needs an Android attestation provider,
  and Play Integrity needs a Play Console account, which Spoil Not doesn't have (it ships through App Distribution).
  App Check on the web alone wouldn't help. So it stays not adopted (SECURITY_REVIEW control 10, the owner's choice),
  and the prod usage page is the monitor.

### SECD-06 · P2 · Likely · Backend — A member's very long product name could get the other members' phones unregistered from reminders

- **Area:** the reminder job's handling of send errors, and the digest text.
- **What it was:** FCM refuses a message over its size limit with the same error code the job treated as a dead
  token. A member with a modified client (the rules then allowed what the app doesn't) could add a batch with a name
  long enough to push the digest over the limit. Every member who got it in a digest had all their device documents
  deleted. Their apps registered again at the next start and lost it again at the next run, as long as the batch
  existed. Firebase's own guidance warns that this error can come from the payload, not the token.
- **Why it mattered:** the other members' reminders stopped, with no sign.
- **Evidence:** reproduced with a sender that answers as FCM does; details withheld. **Why Likely:** FCM's real
  answer wasn't reproduced (no real project).
- **Fix:** B01, jmalp/spoilnot#75, merged 2026-11-08. A token is deleted only when FCM says it is gone
  (not-registered or invalid token). Names in a digest are cut to 80 characters and the body to 1,000 bytes. S2's
  rules limit names to 80 characters (live 2026-11-21). DECISIONS 2026-11-08. Live from the next job run.

### SECD-07 · P2 · Confirmed · Workflows — The production key was in the environment while npm installed packages and the build ran

- **Area:** the reminder, nightly export and restore workflows.
- **What it was:** the key was set for the same step that ran `npm ci`, the build and the job. npm runs packages'
  install scripts with that environment. Two packages in the job's production tree had install scripts, and a
  compromised future version of any transitive package could add one: the 2025 npm worms stole secrets exactly this
  way. Such a version would have reached the job within an hour of merging a Dependabot pull request, since CI on the
  pull request runs without secrets and shows nothing.
- **Why it mattered:** everyone, if it happened: read and write access to prod Firestore.
- **Evidence:** the workflow steps and the lockfile.
- **Fix:** S3, jmalp/spoilnot#94, merged 2026-11-10. CI and the jobs install with `--ignore-scripts`. In the jobs,
  install and build are a step of their own without secrets, and only the step that runs the job gets the key.
  DECISIONS 2026-11-10. Live from the next run.

### SECD-08 · P2 · Confirmed · GitHub — Public repository with secret scanning, push protection and Dependabot alerts off, `main` unprotected, and the production secrets open to any branch

- **Area:** repository settings; `.github/dependabot.yml`; DESIGN 12.2 controls 8 and 9; DECISIONS 2026-10-18.
- **What it was:**
  - Secret scanning and push protection were off. They're free on public repositories, so the reason for using
    gitleaks instead no longer held. gitleaks finds a secret after the push, when it's already public; push
    protection stops it before.
  - Dependabot alerts and security updates were off. So the docs' "security updates are not delayed" was untrue, and
    nothing flagged SECD-16's advisory.
  - `main` had no protection, so it could be force-pushed or deleted, and the scheduled workflows run whatever is on
    it with the prod key.
  - The keys were repository secrets, so a workflow on any branch pushed to this repository could read them. Forks
    can't push here, and fork pull requests get no secrets.
- **Why it mattered:** leak prevention and advisories for the owner, and everyone indirectly.
- **Evidence:** the repository's settings, read with `gh api`.
- **Fix:** S3. The owner turned on secret scanning, push protection, Dependabot alerts and security updates, added an
  active `main` ruleset and required approval for all outside contributors (all on, checked 2026-11-24).
  jmalp/spoilnot#94 (2026-11-10) records these settings in the docs and supersedes the 2026-10-18 entry. The jobs
  name a `production` environment.
- **Owner's choice:** the keys stay repository secrets; the `production` environment holds none
  (jmalp/spoilnot#150, DECISIONS 2026-11-22). Moving them meant creating new service account keys for both projects.
  The repository has one collaborator, who alone can push branches or start workflows, and fork pull requests get no
  secrets. The restore now waits for the owner's approval in a `restore` environment (DECISIONS 2026-11-22).

### SECD-09 · P3 · Confirmed · Rules — A member could put words in another member's mouth

- **Area:** the rules for activity events, `createdBy` and `checkedBy`; Home's Recent activity.
- **What it was:** an activity event checked only its author's user ID. A member could log an event under another
  member's name, of any type, mark a batch as created by someone else, or an item as checked by someone else. Home
  showed the stored name.
- **Why it mattered:** a household with a member who would do it; low.
- **Evidence:** reproduced against the emulator; details withheld.
- **Fix:** S2, jmalp/spoilnot#78, merged 2026-11-08. `createdBy` is the writer on create and doesn't change after.
  `checkedBy` is the writer, nobody, or unchanged. Events have a known type, the writer as author and a name of 1 to
  40 characters. Both apps show an event's author from the member list. Tests: "who wrote it" in `rules.test.js`,
  `ActivityFeedTest`, the web's `models.test.ts`. Live: Android 1.0.0-beta.10 (2026-11-14); rules and web 2026-11-21.

### SECD-10 · P3 · Confirmed · Rules — Any member could delete shared data outright and rewrite the Insights counts

- **Area:** the delete rules for products, batches, items, recipes and meals; the rules for monthly counts.
- **What it was:** the apps delete by writing a tombstone (DESIGN 8.1, 9.8), but the rules also let any member
  delete outright. Other phones' changes-only listeners never see an outright delete, so the item stayed on their
  screens until their next full read (section 04). A member could also set a month's counts to any value, not just
  add or take one. Members are trusted (DESIGN 4), so the risk was mostly a buggy or old client.
- **Why it mattered:** shared data could disappear for good, or stay on some phones but not others; Insights could be
  rewritten.
- **Evidence:** reproduced against the emulator; details withheld.
- **Fix (owner's decision, 2026-11-08):** S2, jmalp/spoilnot#78. Members delete only by tombstone. Outright deletes
  are the owner's (deleting the household, Clear household data), or a list's items together with the list. Counts
  move by at most one per field per write. Every client delete path was walked before the deploy. DECISIONS
  2026-11-08. Live: rules 2026-11-21.

### SECD-11 · P3 · Confirmed · Rules — An ownership transfer could leave two owners, and the old one could then remove the new one

- **Area:** the household and member rules for an ownership transfer.
- **What it was:** a transfer had to make the new owner's role owner, but not the old owner's role member. A batch
  that skipped the demotion left two owners. The old owner kept every owner power and could remove the member named
  as owner, leaving a household whose owner wasn't a member. Both apps demote in the same batch, so this took a
  modified or buggy client.
- **Why it mattered:** DESIGN 8.5's one-owner invariant.
- **Evidence:** reproduced against the emulator; details withheld.
- **Fix:** S2, jmalp/spoilnot#78. A transfer must demote the old owner in the same batch. Test: "an ownership
  transfer must demote the old owner in the same batch". DECISIONS 2026-11-08. Live: rules 2026-11-21.

### SECD-12 · P3 · Confirmed · Backend — Error paths printed user IDs and full household IDs into public logs

- **Area:** the jobs' entry points.
- **What it was:** DESIGN 13.4 and SECURITY_REVIEW say logs never hold user IDs or full household IDs. Normal output
  kept to counts, but the entry points printed raw errors. A profile deleted while the job ran made an update fail
  with an error naming the user's document, and a bulk-write failure printed the full household path (SECD-02).
- **Why it mattered:** IDs in public logs for 90 days, against the docs' promise.
- **Evidence:** reproduced against the emulator; details withheld.
- **Fix:** jmalp/spoilnot#72 (fixed lines with the error's code) and B01, jmalp/spoilnot#75 (a profile gone during
  the run counts as a deleted account and is skipped). Live from 2026-11-08.

### SECD-13 · P3 · Confirmed · Tests — The rules matrix missed 21 allowed pairs and 5 deny-only pairs

- **Area:** `firebase/test/rules.test.js`.
- **What it was:** of the 68 pairs DESIGN allows to someone, 47 had both an allow and a deny test, 11 only the allow,
  3 only the deny and 7 neither. Of the 7 pairs nobody may do, 5 had no deny test. A regression there would have
  passed CI, for example a rules edit letting any member delete the household.
- **Why it mattered:** the rules are the only authorization; untested pairs could regress silently.
- **Evidence:** the matrix above; the review's throwaway checks covered every pair and passed.
- **Fix:** S2, jmalp/spoilnot#78 added 35 rules tests: the missing pairs ("more of the matrix", "paths and people")
  and each gap above, inverted with its fix. 116 tests after #78, 122 after #90. Running in CI since 2026-11-08.

### SECD-14 · P3 · Confirmed · Workflows — Actions were pinned to tags, and any action could run

- **Area:** every workflow (27 references to 8 actions); the repository's Actions settings.
- **What it was:** a tag can be moved to other code, as in the 2025 `tj-actions/changed-files` compromise. DESIGN
  12.2 control 9 allowed major tags, so this was hardening. The workflows with secrets used only GitHub's own actions.
- **Why it mattered:** a moved tag runs someone else's code with the repository's token.
- **Fix:** S3, jmalp/spoilnot#94 (2026-11-10): every action is pinned to a full commit SHA with its version in a
  comment, and Dependabot keeps both current. On 2026-11-22 the owner allowed only GitHub's own actions plus
  `gradle/actions/setup-gradle` and `gitleaks/gitleaks-action`, and required full-length SHA pins
  (jmalp/spoilnot#150, DECISIONS 2026-11-22).

### SECD-15 · P3 · Confirmed · Backups — A broken archive could still upload, and a restore could write one household's data into another

- **Area:** the nightly export and restore workflows.
- **What it was:**
  - The steps ran without `pipefail`, so if the archive step failed, the encryption step still encrypted what it got
    and succeeded. A truncated backup would have uploaded without a warning.
  - The restore accepted any existing household as its target. One wrong ID would have written household A's
    products, stock, recipes, lists and meals into household B, another family's, for its members to see. Nothing
    compared the export's owner with the target's.
  - The encryption itself is sound for secrecy: AES-256-CBC with PBKDF2-SHA256 (600,000 iterations), a random salt
    per file and a random 32-byte passphrase.
- **Why it mattered:** silent bad backups, and one family's data shown to another after a mistyped restore.
- **Evidence:** the workflow files.
- **Fix:** B13, jmalp/spoilnot#101, merged 2026-11-12. Every step runs with `shell: bash` (adds `pipefail`). The
  restore refuses a target whose owner differs from the export's unless a "different owner" input is ticked, also in
  the dry run. Since 2026-11-22 every restore also waits for the owner's approval (`restore` environment). DECISIONS
  2026-11-11 and 2026-11-22. Live from the next run.
- **Not done:** the archives have no integrity check (MAC), which the review suggested only as an option. Only the
  owner can write artifacts, and a wrong key or a damaged file still fails to decrypt or unpack.

### SECD-16 · P3 · Confirmed · Supply chain — One high advisory in the app (not reachable), one npm project without Dependabot, no Gradle dependency verification

- **Area:** the Android runtime classpath, `tools/themes/`, the Gradle build.
- **What it was:**
  - GHSA-xxph-c9ww-hj94 (high): Guava's compact collections allocate unbounded memory during Java deserialization,
    fixed in 33.7.2. Guava came through Firestore at 33.6.0-android. The app doesn't deserialize untrusted Java
    objects, so it wasn't reachable. Nothing flagged it, since alerts were off (SECD-08).
  - `tools/themes` had a lockfile but wasn't in Dependabot.
  - The Gradle wrapper is checked, but dependencies aren't verified, so a tampered Maven artifact would be accepted.
  - The npm advisories (00-S1) didn't reach a user's phone or browser.
- **Why it mattered:** advisories that nothing reported, and one project nothing updated.
- **Fix:** S3, jmalp/spoilnot#94 (2026-11-10): a Gradle constraint holds Guava at 33.8.0-android or later, and
  `tools/themes` is in Dependabot. S4, jmalp/spoilnot#106 (2026-11-12): `npm audit fix` in `firebase/` and
  `backend/reminder/`, then npm overrides in jmalp/spoilnot#119 (2026-11-14). Live on phones with Android
  1.0.0-beta.10 (2026-11-14).
- **Owner's choice:** Gradle dependency verification is not used. Dependabot doesn't update the verification file,
  so every weekly Gradle pull request would fail CI until someone regenerated it (DECISIONS 2026-11-10).

### SECD-17 · P3 · Confirmed · Docs — The security docs claimed things that no longer held

- **Area:** `docs/SECURITY_REVIEW.md`, `.github/dependabot.yml`, RUNBOOK, DESIGN 8.1, 10.3 and 13.4, DECISIONS
  2026-10-18.
- **What it was:** control 5's "lose access at once" (SECD-01). Control 8's "private repository" reasoning. Control
  9's "both npm projects" (there are four). The threat table's "rules limit sizes and shapes of writes" (SECD-04) and
  an old read estimate. The Dependabot comment and RUNBOOK's promise of immediate security updates (SECD-08). DESIGN
  8.1's limits, which the rules didn't enforce. DESIGN 10.3's token deletion (SECD-06). DESIGN 13.4's log promise
  (SECD-02, SECD-12).
- **Why it mattered:** the docs are what the owner and readers rely on.
- **Fix:** each fix PR updated the claims it changed (CLAUDE.md), and SECURITY_REVIEW on `main` lists each change
  with its date.

## From other sections

These items were found by other sections of the review. During the review they were kept with this report's notes;
the public reports pointed here.

### 00-S1 · Note — npm audit details

Section 00 published only the counts. At review time:

- **`web/`:** 4 high advisories in the production tree, all in `@grpc/grpc-js` 1.9, which the Firebase SDK pins. It
  ships only in the SDK's Node build: the browser bundle contains no gRPC code, so the deployed web app wasn't
  exposed. The rest (18 moderate, 2 low) were in build and lint tools.
- **`firebase/`:** the advisories were in the rules-test SDK and `firebase-tools`, which are dev tools for the
  emulators and deploys.
- **`backend/reminder/`:** 6 moderate advisories, under `google-gax`. Nothing feeds them user input, and
  `npm audit fix` without `--force` resolved them.

**Fixed:** `npm audit fix` in S4 (jmalp/spoilnot#106, 2026-11-12). npm overrides for the advisories no parent
release fixes, including the gRPC pin, followed in jmalp/spoilnot#119 (2026-11-14). `npm audit` is clean in all four
projects since 2026-11-25, when the last low advisory, in the web app's service-worker build, closed (DECISIONS).

### 00-S2 · Note — Source maps are built but not deployed

The web build writes source maps, and hosting ignores `**/*.map`, so they aren't served. The repository is public
anyway. No action.

### 04-S2 · P3 · Confirmed · Rules — Items added to a list as it was deleted stayed in the database for good

- **Area:** the rule for creating grocery items; Delete list, Delete household and the job's prune, which find items
  only through existing lists.
- **What it was:** a member adds an item to a list (offline, or in the second before the deletion arrives) while
  another member deletes that list. The item was written under the deleted list. No screen showed it (SYNC-07 in
  `04-sync.md`), and no code found it again: not the prune, not the export, and not Delete household, which deletes
  items through their lists. The item (name, quantity, note, creator) outlived the household.
- **Why it mattered:** household data left behind after Delete household, which PRIVACY.md and DESIGN 8.5 say
  deletes everything. Tiny and rare.
- **Evidence:** reproduced on two test phones against the emulators; details withheld.
- **Fix:** B08, jmalp/spoilnot#90, merged 2026-11-09. An item can be created only where its list exists, so the late
  add is refused and the app says "That list was deleted". It costs one read per batch that creates items, under 1%
  of the free reads (DECISIONS 2026-11-09). Live: rules 2026-11-21.
- **Not done:** items orphaned this way before 2026-11-21 weren't looked for or removed.

### 05-S2 · Note — SECD-05's quota drain was worse than "the quota banner"

When Firestore refused listeners for quota, the Android app replaced everything with a spinner instead of working
from the phone's copy (REL-02 in `05-reliability.md`). So an account that drained the day's reads would have taken
every Android user's app away, cached data included, until the quota reset. **Fixed** by B05 (jmalp/spoilnot#91,
2026-11-09): both apps keep the last known session through any error that isn't a permission refusal, and the quota
banner shows above the app (live 2026-11-21).

### 05-S3 · Note — Starting the reminder job from outside GitHub

REL-03's cheapest fix, an outside service calling `workflow_dispatch`, needs a token that can start workflows on this
public repository. The note asked for a fine-grained token limited to this repository and Actions, stored only in
that service. It also asked that the workflow's inputs can't change anything but `dry_run`. **Done that way:** the
on-time trigger (jmalp/spoilnot#151, DECISIONS 2026-11-22) runs in the owner's Google account with such a token, kept
as a script property. The workflow's only input is `dry_run`, and the restore waits for the owner's approval however
it was started.

## Ideas

Not findings. The owner added them to the roadmap on 2026-11-25, as batch S6.

- **No key at all:** Workload Identity Federation instead of a JSON key, limited to this repository and `main`.
- **One service account per job,** so the export's is read-only.
- **The key stored on one line,** so GitHub masks it as one value.
- **A narrower gitleaks allowlist:** the API key rule in the Firebase configuration files, not the whole files.
- **`ignore-scripts` on the Mac,** where the deploy and release scripts run.
- **Validating restored documents** against the rules' shapes (batch S5, *done* 2026-11-25: a document of another shape is skipped and counted).
- **Rotating the invite code when the owner removes a member** (batch S5, *done* 2026-11-25: in the same batch, on both apps).
- **A lower sign-up quota per IP** in Firebase Auth.

## Not covered

- **Consoles:** Firebase Auth settings, API key restrictions, IAM and service account keys, and the GitHub account
  weren't visible to the review. The owner went through a checklist for them, every item checked; the checklist isn't
  reproduced here. The GitHub settings in it were also confirmed through the API on 2026-11-24 (table above).
- **Quotas:** the emulator has none, so the quota effects in SECD-04 and SECD-05 were reasoned, not reproduced.
- **Real FCM** (SECD-06) and **GitHub's log masking** (SECD-02): no real project and no workflow run.
- **The large export end to end:** too much memory for this Mac; the string limit was shown directly instead.
- **Artifacts:** listed by name and size only. No export was downloaded or decrypted, and no restore was run.
- **Gradle:** only the prod release runtime classpath was checked, through OSV.dev.
- **Fork pull requests:** read from the settings and workflow triggers; not tried with a real fork.

## Appendices

The private report's two appendices held the throwaway rules checks and job checks used as evidence. They are
withheld. The checks that show the rules hold became part of `firebase/test/rules.test.js` in jmalp/spoilnot#78, and
each gap check went in inverted, with its fix.
