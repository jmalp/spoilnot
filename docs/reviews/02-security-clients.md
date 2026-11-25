# 02 Security and privacy: Android and web clients

Reviewed `0334b6a` on 2026-11-01 (the branch `review/2026-11` at `8004603`). Platforms: Android and the web. The
backend was looked at only for the privacy inventory; section 01 covers it.

**Published 2026-11-24.** During the review this report was kept out of the repository until its fixes had shipped
(PLAN.md, ground rule 9). This is the cleaned-up version. Every finding keeps its ID and severity, and says what the
weakness was, why it mattered and how it was fixed. Reproduction steps, probe scripts, crafted inputs, the evidence
screenshots and the owner's phone checklist are left out. Where the private report held a reproduction, this one says
"Reproduced; details withheld."

**Method.** The Firebase emulators, a Pixel_8 emulator (Android 17) with the dev debug build, and for the release
checks the dev benchmark build. The web app was built against the emulators and served with the hosting headers in
WebKit with the iPhone 15 profile. Only made-up accounts and names were used. The hosted app was fetched only for its
response headers. No prod build was made: the prod manifest came from the build's merge step, and an earlier prod
beta APK was read with `apkanalyzer`.

## Summary

The web app held up well. Hostile text rendered as text, the CSP was strict and live on all three hosts, the service
worker cached only the app shell, and signing out wiped the browser's copy even with two tabs open. The Android app
had one serious hole:

- **SECC-01 (P0):** signing out from Settings did neither of its two jobs. The household's data stayed in the
  phone's Firestore database, and the phone stayed registered for that account's daily digest. The Settings screen
  ran sign-out in its own coroutine scope, the first step of sign-out removed that screen, and the cancellation was
  swallowed, so only the final step ran. A phone handed to someone else kept showing the household's expiring food
  and meals on its lock screen every day.
- **SECC-02 (P1):** the same cause made Delete account in Settings delete only the membership.
- **SECC-03 (P2):** signing out offline left the phone registered, on the web and, once SECC-01 was fixed, on
  Android too.
- **SECC-04 (P2):** the digest prune hadn't run since at least 21 October, so PRIVACY.md's retention was about to
  stop being true.
- **SECC-12 (P3):** PRIVACY.md also missed several stored and exported data points.

The rest were P3. Android trusted recipe links and photo addresses from Firestore where the web checked them, and it
had a few smaller gaps. 13 findings: 1 P0, 1 P1, 2 P2, 9 P3. Three items from other sections are added at the end.
**All are fixed and live.** Crash reports staying on by default is the owner's choice (SECC-11).

## Fixes at a glance

| Finding | Severity | Fixed in | Live |
| --- | --- | --- | --- |
| SECC-01 Settings sign-out leaves data and the digest | P0 | jmalp/spoilnot#74 | Android 1.0.0-beta.10, 2026-11-14 |
| SECC-02 Delete account removes only the membership | P1 | jmalp/spoilnot#74; S1, jmalp/spoilnot#77 (both gates) | Android 1.0.0-beta.10, 2026-11-14; web, 2026-11-21 |
| SECC-03 Offline sign-out leaves the phone registered | P2 | jmalp/spoilnot#74 (Android); S1, jmalp/spoilnot#77 (web) | Android 1.0.0-beta.10, 2026-11-14; web, 2026-11-21 |
| SECC-04 Retention depends on a prune that didn't run | P2 | B01, jmalp/spoilnot#75 | Job, 2026-11-08 |
| SECC-05 Android trusted stored recipe links and photos | P3 | S2, jmalp/spoilnot#78 | Android 1.0.0-beta.10, 2026-11-14; rules, 2026-11-21 |
| SECC-06 Very long names froze Home and Pantry | P3 | S2, jmalp/spoilnot#78 | Android 1.0.0-beta.10, 2026-11-14; rules and web, 2026-11-21 |
| SECC-07 A notification tap could leave the site | P3 | S1, jmalp/spoilnot#77 | Web, 2026-11-21 |
| SECC-08 Other things stayed on the phone after sign-out | P3 | S1, jmalp/spoilnot#77 | Android 1.0.0-beta.10, 2026-11-14; web, 2026-11-21 |
| SECC-09 Invite code copied without the sensitive flag | P3 | S4, jmalp/spoilnot#106 | Android 1.0.0-beta.10, 2026-11-14 |
| SECC-10 Release builds logged the email and user ID | P3 | S4, jmalp/spoilnot#106 | Android 1.0.0-beta.10, 2026-11-14 |
| SECC-11 Crash reports and session events | P3 | S4, jmalp/spoilnot#106 | Android 1.0.0-beta.10, 2026-11-14 |
| SECC-12 PRIVACY.md and Export | P3 | B13, jmalp/spoilnot#101; S4, jmalp/spoilnot#106 | PRIVACY.md 2026-11-12; Export on Android 2026-11-14, web 2026-11-21 |
| SECC-13 Unbounded JSON-LD on the main thread | P3 | S4, jmalp/spoilnot#106 | Android 1.0.0-beta.10, 2026-11-14 |
| 04-S1 Deleted account's profile recreated | P3 | B12, jmalp/spoilnot#99 | Android 1.0.0-beta.10, 2026-11-14; web, 2026-11-21 |
| 05-S1 One wrong-typed field blanks every phone's tabs | P2 | B10, jmalp/spoilnot#103; S2, jmalp/spoilnot#78 | Android 1.0.0-beta.10, 2026-11-14; rules and web, 2026-11-21 |
| 06-S1 A join request showed only a typed name | P3 | B12, jmalp/spoilnot#99 | Apps and rules, 2026-11-21 |

The Android fixes first reached testers in 1.0.0-beta.10 (2026-11-14). Android 1.0.0 (build 13, `744d251`) and the
web app from the same commit went to prod on 2026-11-21, with `minSupportedBuild` 13 and `minWebBuild` 3, so older
builds must update. The rules parts and the masked email (06-S1) take effect from the rules deploy on 2026-11-21. The rules followed the same day (DECISIONS
2026-11-21).

## Metrics

### Android

| Metric | At review | How measured | Now |
| --- | --- | --- | --- |
| Exported components, prod release manifest | 6 of 20: `MainActivity` (the launcher and text shares) and five from Firebase and AndroidX libraries | The merged manifest, parsed | — |
| What `MainActivity` accepts from other apps | A shared text gives only its first http(s) link; a `route` extra gives only the two digest routes (exact match). Both are used only inside the signed-in shell, so the session gate holds | Crafted intents | — |
| `PendingIntent`s in app code | 1 (the test notification): explicit and immutable | Code | — |
| Prod release manifest | Not debuggable; cleartext off with no domain exceptions; no local-network permission; backup off; data extraction rules exclude everything | Merged manifest; an earlier prod beta APK | As DESIGN 12.2 control 7 |
| Debug code in release builds | 0 debug-only classes; no emulator host in prod builds | `apkanalyzer` | — |
| Backup and device transfer | Nothing leaves the phone | `bmgr` with the local transport | — |
| Logs, release code | The app's own code: 0 lines with IDs, names or emails. The Firebase Auth SDK: the email and user ID at each sign-in (SECC-10) | `logcat` of the benchmark build | Release builds drop verbose, debug and info logging |
| Built-in browser | JavaScript and DOM storage on; file and content access off; no JavaScript interface; no mixed content; Safe Browsing on; non-https navigations blocked | `RecipeWebView` | DESIGN 11.6, control 12: holds |
| Recipe fetch | 15 s timeout, 3 MB cap, non-http(s) schemes rejected | Code and a JVM probe | — |
| Hostile JSON-LD and markup | Deep nesting is refused by the JSON parser before the mappers see it; no stack overflow; very deep markup is fine | Throwaway JVM test | No crash; size limits added (SECC-13) |
| Imported text caps | Match DESIGN 11.6 | `RecipeLimits` | — |
| Sign-out from Settings | Local copy cleared: **no**. Device document deleted: **no** | Emulator, the app's database, the emulator's request log | Both yes (`SettingsAccountUiTest`) |
| Sign-out from the household gate | Both yes | Same | — |
| Delete account from Settings | Membership removed; **profile, device and sign-in kept**, with no error | Same | Everything removed (`SettingsAccountUiTest`) |

### Web

| Metric | At review | Now |
| --- | --- | --- |
| Raw HTML sinks | 1 (`Icon.tsx`, icon SVG generated at build time); no `eval`, `new Function`, `innerHTML` or `srcDoc` | Same |
| Hostile names | Rendered as text: no element created, no dialog, no CSP violation | — |
| Links and photos from data | Used only when http(s), with `noopener noreferrer` and `referrerPolicy="no-referrer"` | Same |
| CSP and headers, hosted | All three hosts send `firebase.json`'s policy (`script-src 'self'`, no inline or eval, `frame-ancestors 'none'`), `nosniff`, `Referrer-Policy`, `Permissions-Policy`, COOP and HSTS with `preload` | Same (DESIGN 18.6) |
| Service worker | Precaches the shell only, no runtime caching, so no household data in Cache Storage; a tap's route goes through `safeRoute` (SECC-07) | `safeRoute` compares origins |
| Browser storage after sign-out | Firestore and Auth storage wiped, also with a second tab open. **But** the sync log's keys (household and list IDs) stayed (SECC-08) | Sync log cleared too |
| Device document after sign-out | Online: deleted. **Offline: kept** (SECC-03) | Push token deleted, again at the next start if needed |
| Third-party requests | Firebase (`*.googleapis.com`) and recipe photo hosts only | Same |

### Privacy

| Metric | At review | Now |
| --- | --- | --- |
| Kinds of personal data stored on servers or sent to third parties | **17**, from every write in both apps, the job and the exports | PRIVACY.md is rewritten from this inventory (SECC-12) |
| … that PRIVACY.md described fully | **7**; 6 partly; 4 not at all | All |
| Kinds kept only on the phone | 3, none described fully | Described; cleared at sign-out (SECC-01, SECC-08) |
| Controls that didn't work as PRIVACY.md said | **3**: Export didn't save "everything above", Delete account on Android (SECC-02), retention (SECC-04) | 0 |
| Committed screenshots and fixtures with real data | **0** | — |

The full inventory is now [PRIVACY.md](../PRIVACY.md) itself: where each kind of data is kept, why, and when it goes.

## Findings

### SECC-01 · P0 · Confirmed · Android — Signing out from Settings left the household on the phone and kept the phone getting that account's digests

- **Area:** Settings' sign-out, `SessionManager`, the app shell's navigation, the local data clear and the device
  registry.
- **What it was:** Settings ran sign-out inside its own screen's scope. Sign-out's first step turned the session into
  Loading, and the app replaced its whole shell with a spinner. That cleared the Settings screen and cancelled the
  coroutine doing the sign-out. Each later step's cancellation was swallowed, so only the final step (signing out of
  Firebase Auth, which doesn't suspend) ran. The person saw Welcome, but:
  1. the phone's Firestore database kept the household: its name, invite code, products, stock, recipes and the
     profile;
  2. the device delete stayed queued in that database, to be sent only at the same account's next sign-in on that
     phone. Until then the reminder job kept sending the account's digest to the phone every day.

  The household gate's Sign out worked, because its ViewModel belongs to the activity, which is why the bug looked
  intermittent. Unit tests called sign-out directly, and the one UI test only checked that Welcome appeared. Every
  beta since M1 had it.
- **Why it mattered:** it hit every Android user who signed out from Settings, which is the only sign-out once
  they're in a household. The leak needed the phone to keep the app: a shared family tablet, a phone given away or
  sold without a reset, or a second account signing in on the same install (which then got both people's digests).
  Whoever held the phone saw the household's expiring food and that person's meals on the lock screen daily, with no
  end. It broke DESIGN 12.2 control 6 and 10.2. It was P0 by the plan's definition (household data reaching someone
  outside it), the same class as SECD-01.
- **Evidence:** reproduced on the Android emulator against the Firebase emulators; details withheld.
- **Fix:** jmalp/spoilnot#74, merged 2026-11-02. `SessionManager` runs sign-out and account deletion in its
  application scope and the caller awaits the result, so closing the screen no longer stops them. Cleanup steps
  rethrow cancellation instead of swallowing it. While nobody is signed in, the phone keeps no FCM token: it deletes
  it after a sign-out or account deletion, and at a start with no user. That also cleans up phones that signed out
  with the bug, once they update. Tests: `SessionManagerTest` cancels the caller as Settings does, and
  `SettingsAccountUiTest` signs out through Settings and checks the phone's cache and the server. Live: Android
  1.0.0-beta.10, 2026-11-14.

### SECC-02 · P1 · Confirmed · Android — Delete account in Settings removed only the membership

- **Area:** Settings' Delete account, `SessionManager`, the household gate's menu.
- **What it was:** the same cause as SECC-01. After the password check, the first step of deletion removed the
  Settings screen and cancelled the work. Leaving the household had already been queued and reached the server, but
  deleting the profile, clearing the phone and deleting the sign-in never ran. No error was shown, and the person
  ended at the household gate, still signed in. Separately, someone not in a household had no Delete account at all:
  the gate's menu had only Sign out, while PRIVACY.md pointed everyone to Settings.
- **Why it mattered:** every Android user who deleted their account (testers leaving the beta) kept their profile,
  devices and sign-in, and believed they were gone. PRIVACY.md says Delete account "removes your profile, devices
  and membership, then your sign-in".
- **Evidence:** reproduced on the Android emulator with a made-up member; details withheld.
- **Fix:** jmalp/spoilnot#74 (the cause, 2026-11-02) and S1, jmalp/spoilnot#77 (2026-11-08). Both apps' household
  gates offer Delete account with Settings' password dialog, and a deletion that fails shows its error. Test:
  `SettingsAccountUiTest` deletes an account through Settings and checks that the profile, devices, membership and
  sign-in are gone. Live: Android 1.0.0-beta.10 (2026-11-14), web 2026-11-21.

### SECC-03 · P2 · Confirmed (web) · Likely (Android) — Signing out offline left the phone registered, and neither app deleted its push token

- **Area:** both apps' sign-out: the device delete and the local data clear.
- **What it was:** offline, the device delete was queued, both apps stopped waiting after a few seconds, and then
  wiped the local database, queued delete included. The device document stayed on the server and the push token
  stayed valid, so the phone kept getting the digest. The same happened online when the delete took longer than the
  wait.
- **Why it mattered:** anyone who signed out without a connection and kept the app then had SECC-01's consequences,
  on both platforms.
- **Evidence:** reproduced on the web in WebKit; details withheld. **Why Likely on Android:** the code had the same
  shape, but SECC-01 hid it on the emulator.
- **Fix:** Android: jmalp/spoilnot#74 deletes the FCM token while nobody is signed in, so a device document left
  behind gets a token FCM rejects, and the job removes it. Web: S1, jmalp/spoilnot#77 (2026-11-08). The web app
  deletes its push token when a user leaves the phone. Until FCM confirms the delete it keeps a "token to delete"
  flag, and every start tries again before registering anyone. Tests: `DeviceSyncTest`, the web's `session.test.ts`
  with a fake push identity, `notifications.test.ts`. DECISIONS 2026-11-08. Live: Android 1.0.0-beta.10
  (2026-11-14), web 2026-11-21.

### SECC-04 · P2 · Confirmed · Backend · Privacy — The retention PRIVACY.md promised depended on a prune that hadn't run since at least 21 October

- **Area:** the reminder job's daily prune; PRIVACY.md's retention lines.
- **What it was:** the prune ran only in a run that started in the 07:00 UTC hour, and GitHub started none in that
  hour between 21 October and 1 November. So nothing was pruned: tombstones, join requests, the activity log and past
  meals. From mid-November PRIVACY.md's "kept 30 days" would have been wrong, and a deleted account's name would
  have stayed in activity indefinitely. Section 05 reported the reliability side (REL-05).
- **Why it mattered:** the retention promise for every household, and for anyone who left or deleted their account.
- **Evidence:** the workflow's run history, by UTC hour.
- **Fix:** B01, jmalp/spoilnot#75, merged 2026-11-08. The prune runs in the first run of each UTC day, keyed on a
  job-only record of the last prune date, and the job's summary prints that date. DECISIONS 2026-11-08. Live from
  the next job run.

### SECC-05 · P3 · Confirmed · Android — Recipe links and photo addresses from Firestore were used unchecked (the web checked them)

- **Area:** Android's recipe repository and recipe screens.
- **What it was:** the app checked links when it saved a recipe, but another member's modified client could store
  anything (SECD-04). Android then offered Open original for any link scheme and launched it, so a non-web link could
  open another app, such as the dialer with a number filled in. Photos loaded from any address the image loader
  understands. An https photo address on a server the member controlled would tell them each other member's IP
  address and when they opened Recipes. The web app ignored non-web values entirely.
- **Why it mattered:** members of a household with a member who would do this; low.
- **Evidence:** reproduced on the Android emulator; details withheld.
- **Fix:** S2, jmalp/spoilnot#78, merged 2026-11-08. The rules accept recipe links and photos only as http or https,
  and Android drops any other stored value when it reads a recipe. Test: `RecipeReadTest`. Live: Android
  1.0.0-beta.10 (2026-11-14), rules 2026-11-21.

### SECC-06 · P3 · Confirmed · Android — Very long names from another client froze Home and Pantry

- **Area:** the product and stock mappers; Home and Pantry. The root cause was SECD-04.
- **What it was:** a product with an extremely long name (the rules then allowed it) froze Home for about 2 s when it
  arrived and made Pantry's frames take up to 3 s while scrolling. Nothing crashed, and the name was ellipsized.
- **Why it mattered:** a household with a modified client; low.
- **Evidence:** reproduced on the Android emulator; details withheld.
- **Fix:** S2, jmalp/spoilnot#78. The rules limit names to DESIGN 8.1's lengths, and both apps cut names to those
  lengths when they read them. Tests: `PantryReadTest`, `GroceryReadTest`, `HouseholdReadTest`, `ActivityReadTest`,
  the web's `reads.test.ts`. Live: Android 1.0.0-beta.10 (2026-11-14); rules and web 2026-11-21.

### SECC-07 · P3 · Confirmed · Web — `safeRoute` let certain characters send a notification tap to another site

- **Area:** the web's `safeRoute` and its service worker's notification click.
- **What it was:** `safeRoute` accepted a route that looked like a path by its first characters. The URL parser
  drops some characters, so a crafted route could still resolve to another site when the service worker opened it.
  Only someone who can send pushes to the app (the FCM credentials) could have used it, so this was defense in depth.
  An open window already accepted only the two digest routes.
- **Why it mattered:** a notification tap that leaves the app's site.
- **Evidence:** reproduced in Node; details withheld.
- **Fix:** S1, jmalp/spoilnot#77, merged 2026-11-08. `safeRoute` reads the route as a URL on the app's origin and
  keeps only its path and query when the origin is unchanged; anything else opens `/`. Unit tested. DECISIONS
  2026-11-08. Live: web, 2026-11-21.

### SECC-08 · P3 · Confirmed (web, Android sync log) · Likely (Android browser and photos) — Other things stayed on the phone after sign-out

- **Area:** the sync log on both apps; on Android, the built-in browser's data and the photo cache.
- **What it was:** besides the Firestore copy, sign-out left the sync log, which is keyed by household and list IDs,
  on both platforms. On Android it also left the built-in browser's cookies and site storage, so someone signed in to
  a recipe site stayed signed in for the next person, and the cached recipe photos. The sensitivity was low, but
  PRIVACY.md listed only the theme, colors, the device ID and the explainer flag as staying.
- **Why it mattered:** the next person on a shared phone, and PRIVACY.md's accuracy.
- **Evidence:** the web's storage after sign-out; on Android, the sync log's file and a search of the code.
- **Fix:** S1, jmalp/spoilnot#77, merged 2026-11-08. Clearing local data (sign-out, account deletion, leaving or
  losing a household) also clears the sync log on both apps. On Android it also clears the built-in browser's
  cookies, site storage, saved sign-ins and cache, and the photo caches. Test: `SettingsAccountUiTest`. PRIVACY.md
  updated. Live: Android 1.0.0-beta.10 (2026-11-14), web 2026-11-21.

### SECC-09 · P3 · Confirmed · Android — The invite code was copied without the sensitive flag

- **Area:** Household settings' Copy code.
- **What it was:** the clip wasn't marked sensitive, so Android 13 and later showed the code in the copy preview and
  keyboards kept it in clipboard history. The clip's label was the code itself. A code only lets someone ask to join,
  so this was low.
- **Fix:** S4, jmalp/spoilnot#106, merged 2026-11-12. The code is copied marked sensitive, with the label "Invite
  code". Test: `SensitiveClipTest`. DECISIONS 2026-11-12. Live: Android 1.0.0-beta.10, 2026-11-14.

### SECC-10 · P3 · Confirmed · Android — Release builds wrote the email and user ID to the system log at sign-in

- **Area:** the Firebase Auth SDK (not the app's code); `app/proguard-rules.pro`.
- **What it was:** at every sign-in the Auth SDK logged the email at info level and the user ID at debug level, also
  in release builds. Only `adb`, the system and bug reports can read the log, so the exposure was small. The app's
  own logging was already debug-only.
- **Fix:** S4, jmalp/spoilnot#106. Release builds drop `Log.v`, `Log.d` and `Log.i` calls, the libraries' included.
  `Log.w` and `Log.e` stay. Checked in R8's merged configuration and the benchmark APK's code, and crash reports
  still work. DECISIONS 2026-11-12. Live: Android 1.0.0-beta.10, 2026-11-14.

### SECC-11 · P3 · Likely · Android · Question — Crash reports were on by default and brought Firebase Sessions events

- **Area:** the crash reports preference; the prod manifest; PRIVACY.md.
- **What it was:** with collection on (the default), Crashlytics' Firebase Sessions library sent an event at each
  app start, with the installation ID and app and device details. That is usage telemetry beside PRIVACY.md's "no
  analytics, no tracking". Crash reports also carry the Firebase installation ID and the exception message, which
  PRIVACY.md didn't name. **Why Likely:** read from the manifest and the libraries' documented behavior; no
  real-Firebase build was run to watch the traffic.
- **Fix (owner's decision, 2026-11-08):** S4, jmalp/spoilnot#106. Firebase Sessions is off in prod builds, which
  loses only the crash-free figures. Crash reports stay on by default, with the opt-out in Settings, and PRIVACY.md
  names what they contain, the installation ID included. DECISIONS 2026-11-12. Live: Android 1.0.0-beta.10, 2026-11-14.

### SECC-12 · P3 · Confirmed · Docs — PRIVACY.md missed data points and overstated Export

- **Area:** `docs/PRIVACY.md`; Settings > Export on both apps.
- **What it was:** 10 of the inventory's 17 kinds of data, and the two kinds kept in phone storage, differed from
  PRIVACY.md. For example:
  - the name also sits in Firebase Auth, in join requests and in activity;
  - the profile also holds the locale, the job's reminder state and a pending join;
  - devices store the platform, app version and an install ID;
  - user IDs stay on household records after an account is deleted;
  - backups hold names and user IDs for 14 days after deletion;
  - crash reports (SECC-11).

  Export didn't include the activity log, one's own profile, devices or email, though PRIVACY.md said it saved
  "everything above".
- **Why it mattered:** PRIVACY.md is the promise to users (DESIGN 12.2 control 11).
- **Fix (owner's decision, 2026-11-08):** B13, jmalp/spoilnot#101 (2026-11-12). Export also holds the person's own
  profile and the household's activity log; devices and the email stay out. Then S4, jmalp/spoilnot#106
  (2026-11-12) rewrote PRIVACY.md from the inventory. DECISIONS 2026-11-11 and 2026-11-12. Live: PRIVACY.md on
  merge; Export on Android 2026-11-14 (beta.10), on the web 2026-11-21.

### SECC-13 · P3 · Likely · Android — A page opened in the built-in browser could hand the app JSON-LD of any size, parsed on the main thread

- **Area:** the built-in browser's extraction script and its ViewModel.
- **What it was:** the page's HTML was capped at 3 MB, but its JSON-LD wasn't. The result was decoded twice on the
  main thread after every page load, so a page with tens of MB of JSON-LD could freeze the app or crash it out of
  memory. Ordinary pages without a recipe already sent up to 3 MB of page text twice per load. **Why Likely:** no
  hostile page could be served to the emulator's web view without publishing one.
- **Fix:** S4, jmalp/spoilnot#106. JSON-LD is read only up to 32 blocks, 1 Mi characters each and 3 Mi in all, in
  the browser's script and again in the app. A result over 16 Mi characters is left unread, and parsing runs off the
  main thread. Fetched pages get the same limits. Test: `JsonLdLimitsTest`, plus `LiveImportTest` against the 10 test
  sites. DECISIONS 2026-11-12. Live: Android 1.0.0-beta.10, 2026-11-14.

## From other sections

These items were found by other sections of the review. During the review they were kept with this report's notes;
the public reports pointed here.

### 04-S1 · P3 · Confirmed · Android and web (by reading) — A phone still signed in recreated a deleted account's profile

- **Area:** both apps' session handling, which recreated a missing profile.
- **What it was:** the same account is signed in on two phones (or a phone and the iPhone web app). Delete account on
  one deletes the profile and the sign-in. The other phone saw the profile gone and treated it as a sign-up
  interrupted before its profile was written. So it wrote a fresh profile with the name, time zone, locale and
  notification settings. Its sign-in token stays valid for up to an hour, so the rules allowed it. The deleted
  account's profile was then back for good: nobody can delete it from a client once the account is gone, and the job
  only reads profiles.
- **Why it mattered:** data kept after an account deletion, against PRIVACY.md. Small (a name and settings) and rare
  (the same account on two devices), but a broken promise.
- **Evidence:** reproduced on two test phones against the emulators (section 04's probe `p15`); details withheld.
- **Fix:** B12, jmalp/spoilnot#99, merged 2026-11-11. A missing profile is created only right after a sign-up or
  sign-in on this phone. At any other time a missing profile means the account was deleted, and the phone signs out
  and clears its data. DECISIONS 2026-11-10. Live: Android 1.0.0-beta.10 (2026-11-14), web 2026-11-21.

### 05-S1 · P2 · Confirmed · Android (and the rules) — One wrong-typed field, written by any member, blanked tabs on every Android phone in the household

- **Area:** Android's mappers, which used typed getters that throw on another type, and the ViewModels' flows, which
  ended silently on an error (REL-04 in `05-reliability.md`). The rules checked few field types (SECD-04).
- **What it was:** a member with a modified client could write one field of the wrong type on one document. Every
  Android phone in the household then showed Pantry and Groceries blank, Home on skeletons and Plan spinning, with no
  message. That lasted until the document was fixed in the console and each app was restarted.
- **Why it mattered:** a deliberate denial of service by one member against the others, a variant of SECD-04.
- **Evidence:** section 05's bad-data round, with the emulator's admin access standing in for a member; details
  withheld.
- **Fix:** B10, jmalp/spoilnot#103 (2026-11-12): both apps read every field on its own and skip a document that
  doesn't map, never the whole list, and a load error offers Retry. S2, jmalp/spoilnot#78: the rules check the field
  types the apps write. DECISIONS 2026-11-11. Live: Android 1.0.0-beta.10 (2026-11-14); rules and web 2026-11-21.

### 06-S1 · P3 · Confirmed · Android (web by reading) — A join request showed only the name the requester typed

- **Area:** join requests in Household settings and on Home.
- **What it was:** the owner saw only a name, with Approve and Decline: no email and no time. The name is free text
  chosen at sign-up. Anyone who learned the code could sign up under a name the owner expected and wait for the owner
  to approve. Codes are shared in messages, and every member can share them. Approval gives full read and write
  access to the household.
- **Why it mattered:** a social-engineering path into a household, past the very step approval is meant to guard.
- **Evidence:** a seeded request viewed as the owner on the Android emulator.
- **Fix:** B12, jmalp/spoilnot#99, merged 2026-11-11. A join request carries the sender's masked email
  ("ro***@example.com"). The rules compute the mask from the sender's sign-in and accept only that. The owner sees it
  with when the request was sent, under "Only approve people you invited." PRIVACY.md says the owner sees the masked
  email. DECISIONS 2026-11-10. Live: apps and rules, 2026-11-21.

## Ideas

Not findings. The owner added the first three to the roadmap on 2026-11-25, as batch S5.

- **Private lock-screen digests:** a public version of the notification without food or meal names.
- **A narrower web CSP:** `connect-src` limited to the Firebase hosts the app uses.
- **Removing the unused Firebase Auth activities** from the manifest.
- **One sign-out path:** `SessionManager` owns sign-out for every caller. Done in jmalp/spoilnot#74.

## Not covered

- **Real FCM, real phones and the lock screen:** emulator builds can't get FCM tokens, so digests after sign-out
  weren't seen arriving. The owner went through a phone checklist for these, every item checked; the checklist isn't
  reproduced here.
- **Prod builds:** not built. The merged prod manifest and an earlier prod beta APK were read instead, and Crashlytics
  traffic wasn't observed (SECC-11 is Likely).
- **The built-in browser against a hostile page:** no way to serve https to the emulator without publishing a page
  (SECC-13 is Likely).
- **iPhone Safari itself:** WebKit in Playwright only. Storage eviction and Home Screen behavior weren't tested.
- **Fuzzing the import regexes:** read only; inputs are capped before most of them run.

## Appendices

The private report's appendices held the web probe, the Android emulator steps, the helper scripts and the extractor
probe, plus six screenshots of the findings. All are withheld. The checks they made are now covered by the tests named
under each fix.
