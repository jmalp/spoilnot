# Spoil Not privacy summary

Spoil Not is a household app for tracking food, grocery lists and recipes. It keeps only what it needs to work.
This page lists exactly what is collected, where it is kept, who sees it and what is left after you delete your
account (DESIGN 12.2, control 11). It was last checked against both apps, the reminder job and the nightly backup on
2026-11-12.

## What is stored on servers

| Data | Where | Why | When it goes |
| --- | --- | --- | --- |
| Your account: email address, password (kept by Firebase Authentication as a hash, never by the app's database), the display name you signed up with, and Firebase's own account details (when it was created, your last sign-in, whether the email is verified) | Firebase Authentication | Signing in | When you delete your account. Renaming yourself later changes your profile and member name, not the sign-up name kept here |
| Your profile: display name, which household you are in, time zone, language setting (`locale`), preferred units, reminder settings (on or off, the hour, which stages, how expired food is mentioned, whether to include the meals you cook), and when you asked to join a household its ID, its name and the code you entered | Firestore (`users/{you}`), readable only by you | Showing who you are to your household; sending the daily reminder at your local time; the order of the unit menu; waiting for a join request | When you delete your account (a pending join, once it is answered) |
| What the reminder job notes on your profile: the date of your last reminder and the last date whose expired food it mentioned | Firestore (`users/{you}`) | One reminder a day, without repeating expired food | When you delete your account |
| One device record per phone or Home Screen web app: its notification token, platform (Android or web), app version and when it was last updated. The record is named by an install ID: on Android, Firebase's installation ID for the app on that phone; on the iPhone web app, a random ID the app makes | Firestore (`users/{you}/devices`), readable only by you | Delivering reminders to that device | When you sign out on that device, delete your account, or the token stops working |
| Your membership: your display name, your role (owner or member) and when you joined | Firestore, under the household (`members`) | Showing the household who is in it | When you leave, are removed or delete your account |
| When you ask to join a household: your display name, the code, the time you asked and your email address masked (the first two characters, three stars and the domain, such as `ro***@example.com`; your full address is never stored there) | Firestore, under that household (`joinRequests`), readable only by you and its owner | So the owner can tell who is asking before approving | When you join, cancel or are declined, or by the daily cleanup after 7 days |
| Household data: products, purchases with dates and places, grocery lists, recipes (with their link and source name), the meal plan (what is planned for which day, who is cooking and who marked it cooked), monthly counts of items used up and thrown away (Insights; numbers only, no names or items) | Firestore, under the household | The app's purpose; shared with the household's members | Past meal-plan weeks after 4 weeks; deleted things after 2 days (until then they wait as markers so every phone removes them); the rest when the owner deletes it, clears the household's data or deletes the household |
| Who did what on household records: your user ID (a random account ID, not your name or email) on what you add, change, check off, cook or are cooking, and on an invite code you created | Firestore, under the household | Showing "added by" and the household's history | With the record. It stays on records that outlive your account, with no name left to match it |
| The activity log: who did what (your user ID and display name), with item names, quantities and reasons | Firestore, under the household (`activity`) | Home's Recent activity | After 30 days |
| Your reminder's text: the names of products that are expiring or expired and of the meals you cook that day | Sent through Firebase Cloud Messaging (and, for the iPhone web app, Apple's web push service) to your devices; not stored by the app | The daily reminder | Shown as a notification on your devices; not kept by the app |
| Nightly backups: the household, its members' names, roles and user IDs, products, purchases, grocery lists, recipes, monthly counts and the meal plan | Encrypted files kept by GitHub Actions for the app's repository; only the app's developer holds the key | Restoring the household after a mistake | After 14 days |
| Crash reports, if on (see below) | Firebase Crashlytics (Google) | Fixing crashes | Kept by Google for 90 days |

A daily cleanup deletes what has passed its time: activity after 30 days, past meal-plan weeks after 4 weeks, join
requests after 7 days (an approved one once your app has finished joining), and deleted items after 2 days. It runs
once each day, in the reminder job's first run of the UTC day; if it fails, the next run tries again.

Firestore stores data in the `us-east1` region.

## Crash reports (Android)

Crash reports (Firebase Crashlytics) are on unless you turn them off in Settings > Send crash reports; debug builds
never send them. A report is sent only after a crash and contains:

- the crash itself: where in the app's code it happened and the error message, which can include an internal record
  path with a household or user ID;
- the app version, the device model, the Android version and the device's state at the crash (such as free memory
  and disk space);
- Firebase's installation ID for the app on that phone (the same ID that names its device record), so crashes from
  one phone can be told apart.

Reports carry no names, emails or household contents. Firebase Sessions, which would send an event each time the app
starts, is turned off, so nothing is sent while the app doesn't crash. The iPhone web app sends no crash reports.

## What stays on the phone

The phone keeps an offline copy of the household's data, which is cleared when you sign out, delete your account,
leave a household or are removed from it. Cleared with it: the record of when each list was last read in full and of
the newest change read from it (it names the household's lists), and on Android the built-in browser's cookies, site
storage, saved sign-ins and cache (so nobody stays signed in to a recipe site) and the cached recipe photos. While
nobody is signed in, the Android app also deletes its notification token, so the phone gets no reminders even if its
device record could not be removed when you signed out (for example without a connection).
If you sign out while some changes haven't reached the server yet, the app asks first, because signing out deletes
them with the phone's copy.
If you delete your account on one phone while another is still signed in, the other phone signs out and does not
recreate your profile.

What stays on an Android phone after sign-out: the theme, colors, crash-report choice, whether you finished the app
tour or saw the notification explanation, and its Firebase installation ID (Firebase's ID for the app on that
phone).

Spoil Not for iPhone (the web app, after v1) does the same in the browser's storage on the phone (IndexedDB and
localStorage): the sign-in, the household's offline copy and the record of full reads and newest changes read,
cleared the same way. Its theme, colors, text size, its random device ID, whether it explained notifications or how
to add it to the Home Screen, and, until its notification token is confirmed deleted, a "token to delete" mark stay in
the browser's storage on that phone. Its notification token (from Firebase Cloud Messaging, delivered through Apple's
web push service) is stored like the Android app's, and deleted when you sign out or delete your account; without a
connection it is deleted the next time the app opens online, before anyone signs in again.

## Who can see it

- Only members of your household can read or change household data. The security rules check membership on every
  request, so someone who leaves or is removed loses access at once.
- Your profile and device records are readable only by you (and the reminder job).
- An invite code only lets someone ask to join; the owner must approve each request. Copying the code marks it as
  sensitive, so Android 13 and later hide it in the copy preview and keyboards leave it out of their clipboard
  history.
- The app's developer can see the data in the Firebase console for operating the service. It is never sold or
  shared.

## What is not collected

- No analytics, no advertising, no tracking. Release builds of the Android app also leave the debug and
  informational messages of the app and its libraries out of the phone's system log.
- No location, contacts, photos or files. Recipe images stay links to the recipe's website; showing a recipe loads
  its photo from that site (the iPhone web app sends no referrer).

## Other services

- **Firebase** (Google): Authentication, Firestore and Cloud Messaging run the app; Crashlytics receives crash
  reports (above). Spoil Not for iPhone is served by Firebase Hosting, which sees ordinary page requests (as any
  website's host does); the app adds no analytics.
- **Apple's web push service** delivers the iPhone web app's reminders, as Google's delivers Android's.
- **GitHub** runs the reminder job and the nightly backup, and keeps the encrypted backups for 14 days.
- **Recipe websites**: when you import a recipe or browse in the built-in browser, the phone contacts that website
  directly, as any browser would. The site sees an ordinary page request. The app reads only the page's recipe data,
  and only up to a limit. Saved recipes keep the link and the source name.
- **Reminders** are sent by a scheduled job that reads the household's dates and your reminder settings and asks
  Firebase Cloud Messaging to deliver one notification.

## After you delete your account

Settings > Delete account removes your profile, devices and membership, then your sign-in (with its email and
sign-up name). What is left:

- household data you added stays with the household, and your user ID stays on records you added, changed or
  checked off, with no name left to match it;
- your name in the activity log, until those events are 30 days old;
- your name and user ID in the nightly backups, until they are 14 days old;
- crash reports from your phone, under Google's retention.

The owner can delete the whole household, or clear its data (Settings > Clear household data, after their password)
and keep the household and its members.

## Your controls

- **Export:** Settings > Data > Export household data saves, as a JSON file: the household's data (products,
  purchases, grocery lists, recipes, planned meals, monthly counts, the household and its members' names and roles),
  your own profile (everything listed for it above, including the reminder job's notes and a pending join) and the
  household's recent activity. It needs a connection, so the file always has what the household has; offline it
  says "You're offline". Your devices, your email and your sign-in details are not in it. The iPhone web app saves
  the file on the phone; nothing is uploaded.
- **Backups:** every night the household data is saved, encrypted, as a private backup kept for 14 days, so it can be
  restored after a mistake. Only the app's developer holds the key.
- **Delete account:** Settings > Delete account (above).
- **Notifications and crash reports** can be turned off in Settings.

Questions: ask the person who invited you to the beta.
