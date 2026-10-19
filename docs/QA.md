# Manual QA script

DESIGN 13.1 and the M6 acceptance check: run this on **two physical Android phones (two brands if possible)** and
on a **small-screen emulator** (for example a 5-inch, 360 dp wide device). Use the beta build from Firebase App
Distribution (prod). Write the result of each step (pass, fail and what happened) in the table at the end.

Before starting: Phone A and Phone B have the App Tester app and the beta installed, and two accounts are ready.

## 1. Install and join (M6 acceptance)

1. Phone A: create an account, create a household. Note the invite code (Settings > Household).
2. Phone B: create an account, enter the code, request to join.
3. Phone A: the Home tab shows **Join requests**; approve.
4. Phone B: lands in the household within a few seconds. Home > Recent activity on Phone A shows "… joined the
   household".

## 2. Everyday flows

1. Pantry: add Milk (expires tomorrow) on Phone A. Phone B shows it in under 2 seconds with a short highlight.
2. Mark it used up with a swipe, then Undo from the snackbar. Throw away something with a reason; Undo. Tap a row:
   the Edit window opens; drag it down a little (it stays) and then past halfway (it closes).
3. Turn on Add to grocery list automatically with Keep at least on a product and use it below that: it appears on
   the grocery list by itself on both phones.
4. Groceries: quick-add "2 kg rice", check it off (light vibration), Add to pantry.
5. Recipes: share a recipe page from Chrome (Share > Spoil Not). The preview opens in under 5 seconds; Save. Open it,
   change servings, start Cook mode (screen stays on), add ingredients to the grocery list ("Probably in pantry"
   items start unchecked).
6. Home: Needs attention, Low stock, Grocery list and Recent activity match what you just did; each card's button
   switches to the right tab with the right filter.
7. Open Settings on one tab, switch to another tab and back: Settings is closed.
8. Insights: use up one item and throw away another (Spoiled). On both phones, Home > Your kitchen this month shows
   "1 used · 1 thrown away" and a 50% ring with a dashed part. Undo the throw-away: it shows "1 used · 0 thrown
   away". See insights: the bars, streak and reasons match, and TalkBack reads each chart's summary.

## 3. Conditions

| Check | How | Expected |
| --- | --- | --- |
| Offline | Airplane mode, add and edit items, then reconnect | "Offline" banner and cloud icons; everything syncs after reconnect, nothing lost |
| Time zone change | Change the phone's time zone | Today's statuses follow the new local date; the next reminder arrives at the chosen local hour |
| A DST day | On or just after a daylight saving change (or simulate with the time zone of a place changing that week) | Reminder arrives once, at the chosen hour |
| Notification permission denied | Deny the permission when asked; open Settings > Notifications | A banner explains how to turn it on; no crash |
| Battery saver | Turn on battery saver overnight | The morning reminder still arrives (it may be a little late) |
| 200% font | Settings > Display > Font size at maximum | Every screen readable, nothing cut off or overlapping |
| Dark mode | Settings > Appearance > Dark, then System with the phone in dark mode | All screens readable; status colors still distinct |
| TalkBack | Turn on TalkBack; go through Home, Pantry, a recipe and Groceries | Every control is announced with its name and state ("Milk, 2, expires tomorrow, fridge"); every swipe action is also in a menu |
| Small screen | The small-screen emulator | No horizontal cut-offs; sheets scroll; buttons reachable |
| Rotation | Turn the phone sideways on each tab | Nothing breaks; content stays centered |

## 4. Budgets (DESIGN 13.1)

| Budget | How | Target |
| --- | --- | --- |
| Cold start | Swipe the app away, open it, count to the first screen | Under 2.5 s on a mid-range phone |
| Scrolling | Scroll a long pantry quickly | Smooth (60 fps); no stutter after the first pass |
| Initial sync | Sign out, sign in on mobile data (4G), time until the pantry shows | Under 2 s for a typical household |

Measured on the Pixel 8 emulator (2026-10-18): cold start 0.2 to 0.4 s to first frame (release build); 500
batches scroll at 17 to 19 ms per frame with 1.7% janky frames once warm (23% on the very first pass); a fresh
sign-in with about 750 documents under LTE speed and 3G latency took 4.3 s including sign-in.

## 5. Crash reports

Settings > About: tap the version 7 times, choose Crash now. After reopening the app, the report appears in the
Firebase console > Crashlytics for spoilnot-prod within a few minutes. Turn off Send crash reports and repeat: no
new report.

## Results

| Step | Phone A (model, Android) | Phone B (model, Android) | Small emulator |
| --- | --- | --- | --- |
| 1 Install and join | | | |
| 2 Everyday flows | | | |
| 3 Conditions | | | |
| 4 Budgets | | | |
| 5 Crash reports | | | |
