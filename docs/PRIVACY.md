# Spoil Not privacy summary

Spoil Not is a household app for tracking food, grocery lists and recipes. It keeps only what it needs to work.
This page lists exactly what is collected (DESIGN 12.2, control 11).

## What is stored

| Data | Where | Why |
| --- | --- | --- |
| Email address and password (the password is handled by Firebase Authentication, never by the app's database) | Firebase Authentication | Signing in |
| Display name | Firestore (`users`, household `members`) | Showing who added or changed something |
| Household data: products, stock with dates and places, grocery lists, recipes, the activity log (who did what, kept 30 days) | Firestore, under the household | The app's purpose; shared with the household's members |
| Device notification token, time zone and reminder settings | Firestore (`users/{you}`) | Sending the daily expiry reminder at your local time |
| Preferred units | Firestore (`users/{you}`) | The order of the unit menu |
| Theme, colors and crash-report choice | On the phone only | Settings |

Firestore stores data in the `us-east1` region. The phone keeps an offline copy of the household's data, which is
cleared when you sign out, leave a household or are removed from it.

## Who can see it

- Only members of your household can read or change household data. The security rules check membership on every
  request, so someone who leaves or is removed loses access at once.
- An invite code only lets someone ask to join; the owner must approve each request.
- The app's developer can see the data in the Firebase console for operating the service. It is never sold or
  shared.

## What is not collected

- No analytics, no advertising, no tracking.
- No location, contacts, photos or files. Recipe images stay links to the recipe's website.

## Other services

- **Firebase** (Google): Authentication, Firestore and Cloud Messaging run the app.
- **Crash reports** (Firebase Crashlytics) are optional. They contain the crash's technical details, the device model
  and the Android version, and no names, emails or household data. Turn them off in Settings.
- **Recipe websites**: when you import a recipe or browse in the built-in browser, the phone contacts that website
  directly, as any browser would. The site sees an ordinary page request. Saved recipes keep the link and the source
  name.
- **Reminders** are sent by a scheduled job that reads the household's dates and your reminder settings and asks
  Firebase Cloud Messaging to deliver one notification.

## Your controls

- **Export:** Settings > Data > Export household data saves everything above as a JSON file.
- **Delete account:** Settings > Delete account removes your profile, devices and membership, then your sign-in.
  Household data you added stays with the household; the owner can delete the whole household.
- **Notifications and crash reports** can be turned off in Settings.

Questions: ask the person who invited you to the beta.
