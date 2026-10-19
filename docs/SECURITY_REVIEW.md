# Security review (DESIGN 12, M7)

Reviewed 2026-10-18 against DESIGN 12.1 to 12.4 on the code merged through M6 plus the M7 branch. Each control lists
what enforces it and how it was checked. Items marked **Owner** need a console setting only the owner can make;
they are listed at the end with exact steps.

## 12.1 Threats

| Threat | Mitigation | Checked |
| --- | --- | --- |
| Outsiders read or change household data | Rules require membership on every household path; deny by default | 61 rules tests at sign-off, 66 after v1 (`firebase/test/rules.test.js`), "non-members" and "signed out" groups |
| A guessed or leaked invite code | 50-bit codes, readable only by exact `get`; a code only lets someone ask to join; the owner approves, can pause or rotate | Rules tests "join requests"; `InviteCodes` uses `SecureRandom` |
| A removed member keeps access | Rules re-check membership on every request; the app's membership watcher returns them to the gate and clears the cache | `HouseholdFlowTest` (removed member back at the gate within 5 s), rules tests |
| A lost or shared phone | Sign-out and account deletion clear the local copy (`terminate()` then `clearPersistence()`); the owner can remove a lost phone's member | `FirestoreProvider.clearLocalData`, `SessionManager.signOut` |
| Leaked secrets | No secrets in the app or repository; the service account key exists only as GitHub secrets; gitleaks scans every push and the full history weekly | History sweep 2026-10-18: only the Firebase configuration's public API keys; `.gitleaks.toml` |
| Abuse of the free quota | Rules limit sizes and shapes of writes; API keys restricted to the app (**Owner**); App Check not adopted (see control 10) | Quota simulation: about 4,700 reads per household per day (docs/RUNBOOK.md) |
| Malicious recipe pages | Hardened web view; imported text sanitized and capped (DESIGN 11.6) | Control 12 |

## 12.2 Controls

| # | Control | Status | Evidence |
| --- | --- | --- | --- |
| 1 | Rules enforce all authorization, deny by default | ✅ | No rule allows unconditionally; every `match` lists explicit conditions; 61 rules tests (66 after v1) |
| 2 | Email and password, minimum 8 characters; email enumeration protection on | ✅ | `AccountValidation` requires 8; the server password policy (8, enforced) and email enumeration protection are on in both projects (owner, 2026-10-18) |
| 3 | Membership on every household path; owner-only actions check the role | ✅ | `isMember` / `isOwner` in `firestore.rules`; rules tests for owner-only delete, transfer, approve |
| 4 | Invite codes: 10 characters (50 bits), secure generator, `get` only, pause and rotate | ✅ | `InviteCodes.kt` (`SecureRandom`); rules `allow list: if false`; Household settings pause and rotate |
| 5 | Removed members lose access at once; cache cleared | ✅ | Rules; `SessionManager` membership watcher; `HouseholdFlowTest` |
| 6 | `terminate()` then `clearPersistence()` on sign-out or account switch | ✅ | `FirestoreProvider.clearLocalData`, called by `SessionManager.signOut` and on leaving a household |
| 7 | TLS only | ✅ | `usesCleartextTraffic="false"` and a cleartext-free network config; only dev debug and benchmark builds allow the local emulator hosts. The prod APK was checked: no emulator host, no local-network permission, not debuggable |
| 8 | No secrets; `.gitignore`; secret scanning and push protection | ✅ with a substitute | `.gitignore` covers `*.jks`, `keystore.properties`, `local.properties`, `service-account*.json`, `.env*`. GitHub secret scanning is not available for this private repository on the free plan, so `secret-scan.yml` runs gitleaks on every push, pull request and weekly over the whole history |
| 9 | Dependency automation; Actions pinned | ✅ | `.github/dependabot.yml` (Gradle, both npm projects, Actions; weekly, grouped). Every action is pinned to a major version |
| 10 | SHOULD: App Check with Play Integrity | ⏸ Not adopted | Play Integrity needs the app added in a Google Play Console account (one-time fee); DESIGN 12.2 says to skip it then. The owner chose to skip (docs/DECISIONS.md). Revisit if the app goes on Google Play |
| 11 | Privacy: minimal data, no analytics or ads, delete account, export, optional crash reports, `PRIVACY.md` | ✅ | `docs/PRIVACY.md`; Settings > Data > Export; Settings > Delete account; crash reports switch (off in debug builds). Other libraries (OkHttp, Jsoup, Coil, AboutLibraries) send nothing on their own |
| 12 | Web view: http(s) only, no file or content access, no JavaScript interface | ✅ | `RecipeWebView`: other schemes blocked in `shouldOverrideUrlLoading`, `allowFileAccess` and `allowContentAccess` false, no `addJavascriptInterface` (searched), results read with `evaluateJavascript` |

## 12.3 and 12.4 Rules and test matrix

`firebase/firestore.rules` follows the 12.3 draft with the deviations in docs/DECISIONS.md (pending joins, the
default list, own activity events). The 61 tests in `firebase/test/rules.test.js` cover the 12.4 matrix: signed out,
non-members, join requests, members, owner-only actions, grocery lists, recipes and activity. They run in CI on
every change.

**After v1:** `stats/{month}` (Insights, 2026-10-18) allows members to read and to write stamped, whole, non-negative
counts with known fields and `yyyy-MM` ids; only the owner deletes (with the household). 5 tests ("insights stats").
Re-reviewed against 12.1: the documents hold numbers only, readable only by members.

Rules are deployed to both projects (spoilnot-dev since M3, spoilnot-prod on 2026-10-18). Rule changes after v1 are
deployed with the release that needs them (`scripts/release-beta.sh` asks when `firestore.rules` changed).

## Operations

| Area | Status |
| --- | --- |
| Service account | One per project, roles Cloud Datastore User and Firebase Cloud Messaging API Admin; stored only as `FIREBASE_SERVICE_ACCOUNT_JSON` (prod) and `FIREBASE_SERVICE_ACCOUNT_JSON_DEV`; a key for the wrong project fails the run. The old dev key was deleted 2026-10-18 |
| Backups | Nightly export kept 14 days as a private artifact; restore by workflow, dry run first; restores never touch members (docs/RUNBOOK.md) |
| Release signing | Upload keystore outside the repository; the owner keeps a backup |
| Logs | Job logs carry counts and user ids, never household contents (the export prints counts only) |

## Owner actions

Status on 2026-10-18: all done. 1 ✅ (sign-in checked on the beta), 2 ✅, 3 ✅ (both projects), 4 ✅.

1. **Restrict the Android API keys** (Google Cloud console → the project → APIs & Services → Credentials → the
   "Android key (auto created by Firebase)" → Application restrictions: **Android apps**):
   - `spoilnot-prod`: package `com.parcha.spoilnot`, SHA-1 `92:6C:BD:51:0C:4F:95:79:F5:5F:76:AA:1F:FB:D0:F1:F3:9C:48:68`
   - `spoilnot-dev`: package `com.parcha.spoilnot.dev`, SHA-1 `92:6C:BD:51:0C:4F:95:79:F5:5F:76:AA:1F:FB:D0:F1:F3:9C:48:68`
     and `3F:DE:E9:DF:E3:C5:6B:38:3A:05:58:18:5E:14:3A:E0:93:3A:ED:3C` (debug key)
   Then open the app once to confirm sign-in still works.
2. **Password policy**: Firebase console → Authentication → Settings → Password policy → require at least 8
   characters, **Enforce**. (Both projects.)
3. **Email enumeration protection**: Firebase console → Authentication → Settings → User actions → check that
   "Email enumeration protection" is on. (Both projects.)
4. **GitHub account**: two-factor authentication on (github.com/settings/security).

## Sign-off

| | Name | Date |
| --- | --- | --- |
| Reviewed by | Claude Code | 2026-10-18 |
| Owner actions done and accepted by | Jomar Malpica (owner) | 2026-10-18 |
