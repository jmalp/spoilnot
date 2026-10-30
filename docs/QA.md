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
9. Settings > Appearance > Colors: pick each preset in light and dark. The whole app changes at once, expiry chips keep
   their colors, text stays readable, and TalkBack reads each swatch as a selected or unselected radio button with its
   name. Restart the app: the choice is kept.
10. Changes-only sync: with both phones open, delete a grocery item and throw away a batch on Phone A; both
    disappear on Phone B. Close the app on Phone B, make three changes on Phone A (add, edit, delete), reopen Phone B:
    all three show. Offline on Phone B, delete something, reconnect: Phone A sees it go. Recent activity: with Home
    open on Phone B, throw something away on Phone A: it shows on Phone B; Undo: it goes away again.
11. Recipe to groceries: with "Onions" in the pantry (any amount, even out of stock), add a recipe with "1 extra large
    onion, diced" and "½ tbsp soy sauce" to the list. The sheet shows "Adds as Onions × <your usual amount>" and
    "Adds as Soy sauce". On the list: "Onions" with the usual amount and the note "<recipe>: 1 extra large onion,
    diced", and "Soy sauce" with no amount. Check them off and Add to pantry: the onions add to the existing product.
12. Change: with "Green onions" in the pantry, add a recipe with "3 scallions, sliced". Tap Change on that line, type
    "onion", pick Green onions, Save, and add. On the list it is "Green onions". Open the sheet again (or on the other
    phone): "3 scallions" now adds as Green onions by itself.
13. Use it up: give Spinach a date tomorrow and Yogurt one in 3 days; save a recipe using both and another using only
    yogurt. Home shows Use it up with the two-ingredient recipe first ("Uses Spinach (Tomorrow), Yogurt (3 days)");
    tapping it opens the recipe. With nothing expiring soon, the card is gone.
14. I cooked this: with 6 eggs and a bottle of soy sauce, open a recipe with "3 large eggs" and "2 tbsp soy sauce"
    and tap I cooked this. Eggs are checked with 3; Soy sauce is unchecked with 1. Take 1 item off: the pantry has 3
    eggs on both phones and Recent activity says you cooked it. Undo: 6 eggs again.
15. Mark opened: with 6 yogurts dated in 2 weeks, ⋮ > Mark opened, 1 of 6, 3 days. Two rows: 1 yogurt "Opened" expiring
    in 3 days, 5 sealed ones keep their date; Undo makes it one row again. Mark a milk with 2 days left opened for 1
    week: it keeps its date. Open another yogurt: 3 days is already chosen.
16. Store order: put items from Bakery, Produce and Dairy on the list (from pantry products, so they have aisles).
    In the store, check off Bakery first, then Produce, then Dairy. Next time those aisles show in that order on both
    phones; another list keeps its own order.
17. Quick wins: quick-add "bananas" and "frozen peas": they group under Produce and Frozen. Edit an item and rename it
    to the name of a pantry product: Add to pantry adds to that product. On a new household, the empty Home cards are one row each. The
    notification explanation appears on the third dated item, not the first. Pantry and grocery rows show "2", not
    "2 pcs". At font size 200%, Settings > Units shows radio buttons with full labels.
18. Add to pantry: check off five items and tap Add to pantry. Each is one line ("2 · Fridge · Oct 26") and the
    button says Add all 5 to pantry. Tap one line: its editor opens; turn on Skip: the line says Skipped and the
    button Add 4 to pantry. Clear a quantity and confirm: that line opens with the error.
19. Insights: the latest month is highlighted with its details below. Tap an earlier month: it is highlighted and the
    details change (counts, % used, reasons). With TalkBack, swipe through the months: each is read on its own with
    "selected" on the chosen one, and the new details are announced.
20. App tour: on a fresh install, create a household. The tour starts on Home. Follow it by tapping what is
    highlighted (Pantry, Add item, Recipes, Groceries); each tap moves it on. Taps on the dimmed part do nothing. At
    200% text the card stays readable and its buttons wrap. Restart the app: no tour. Settings > Take the app tour:
    it starts again; Skip tour ends it at any step.
21. Polish 1: each tab's title is one compact bar. Pantry rows show one status chip and "× 3" (nothing for one). With
    three or more expired items, the list starts with "N expired items": Show lists them. Add item: the quantity stepper sits beside the unit, the date chips wrap, the four locations are tiles
    on one line (two lines at 200% text). "Bag 2" sorts before "Bag 10". The tour highlight has no light corners.
22. Polish 2: recipes without photos show a colored initial and line up; an empty recipe box offers Paste a link,
    Browse the web and Create manually. On Groceries, tap the list's name to switch lists. A low-stock item that is
    back in stock shows "Back in stock · Remove" on one line. Settings cards are outlined. In Add to grocery list,
    each checkbox lines up with its ingredient. Settings > Colors: the check is visible on every swatch.
23. After beta 7: no Throw away all button on Home or in Pantry. Sign out in dark mode: "Spoil Not" is in the theme
    color, in the rounded Fredoka font. Add item: four equal date chips, no No expiry; tap +3 days twice and the date
    goes back to No expiry date. Pantry: no All chip; tap Fridge twice and everything shows again. Scroll Pantry and
    Recipes: Add item and Add recipe keep their labels. Try each preset color: backgrounds and chips show the theme.
24. Clear household data: as a member, the button is not in Settings. As the owner, Settings > Clear household data:
    Cancel changes nothing; Yes, clear everything asks for the password; a wrong password says so and clears
    nothing; the right one clears it. On both phones: empty pantry, recipes and grocery list (named Groceries, no
    extra lists), Insights empty, Recent activity shows only "<owner> cleared the household's data". Members, the
    invite code and the household name are unchanged.
25. Meal planner (part 1): the bottom bar shows Home, Pantry, Plan, Recipes, Groceries. Plan opens on this week at
    today ("Today" label and border). Tap + on Thursday dinner: pick yourself and the other person as cooks, then a
    recipe; it shows with its servings and both names on both phones. Add "Leftovers" as something else at lunch.
    Tap the dinner: change servings to 8 and add a note; the other phone shows it. Remove it, then Undo. Previous and
    next week work, and "Go to this week" returns. Delete the recipe: the meal says "Recipe deleted". At 200% text
    the days stay readable. Change the phone's region to the UK: the week starts on Monday.
26. Meal planner (part 2): with milk expiring tomorrow and a saved recipe using milk, + on a slot lists that recipe
    under Use it up ("Uses Milk"); tapping it plans it. Plan Tacos (4 servings) tonight and set it to 8: the cart
    button in Plan's top bar opens This week's ingredients with Tacos under "Dinner · …· 8 servings", counts doubled
    and pantry items unchecked. Add them: they show on the list with the recipe icon and "Meal plan" on both phones,
    and Recent activity says "… from the meal plan". On the Tacos sheet tap Cooked: What did you use? at 8 servings;
    take the amounts off: the meal shows a check and "Cooked", the pantry goes down, and the cart leaves Tacos out.
    Undo puts the pantry and the mark back. Cook "Leftovers": marked at once. Not cooked yet clears the mark only.
    Home shows a Today card with today's meals, cooks and Cooked; Open plan goes to the Plan tab. With nothing
    planned today there is no Today card.
27. Meal planner (part 3): on a meal's sheet tap Move, pick Friday and Lunch: it moves on both phones; Undo puts it
    back. Plan last week's dinners, go to this week and choose ⋮ > Copy last week: they appear on the same weekdays
    without Cooked; copying again says they are already here. ⋮ > Clear this week asks "Removes all N meals…", then
    empties the week; Undo brings them back. As a cook of tonight's meal with nothing expiring, run the reminder job:
    the notification says "You're cooking … for dinner" and opens Plan. With items expiring the meal is the last line.
    Settings > Notifications > Meals I'm cooking off: no meal line the next day.
28. Web app W1 (iPhone, Safari, with `npm run dev` on the Mac and the emulators): Create account, create a household;
    on a second iPhone (or a private tab) create an account and join with the code; the owner sees the badge on the
    avatar and approves in Settings > Household; the second phone opens Home. Copy and Share the code (iOS share
    sheet). Remove the member: they return to the gate. Sign out and back in; a wrong password says so. Larger Text
    at the largest size: text grows, buttons stay 48 px. Light and dark follow the phone.
29. Web app W2a (iPhone, with pantry data from the Android app in the same household): the Pantry tab lists the same
    purchases with the same chips; search, the filters (tap the chosen one again for all), Sort and group. Swipe a
    row right: used up, Undo brings it back; swipe left: the reasons, then thrown away. ⋮ > Mark opened on a pack of 6:
    1 moves to its own row with Opened. The Android phone shows each change within seconds, and the other way round.
30. Web app W2b (iPhone, same household as an Android phone): Add item from the empty Pantry: Greek yogurt, 6, +1 week:
    "Greek yogurt added". Add greek YOGURT with the same date: the row becomes × 7. Type the start of a product the
    household has used: it fills unit, place and category; pick a day in the date picker. Tap a row, set the quantity
    to 0, Save: it disappears; Undo brings it back. Tap an Out staple: Add item opens for it. Home: Needs attention and
    Low stock list the same items as the Android Home and their buttons open Pantry filtered; This month shows the
    same counts; throwing something away on Android appears in Recent activity within seconds. See insights: tap last
    month's bars for its details. Larger Text at the largest size: headlines grow less than body text, the ring's
    percentage stays inside it.
31. Web app W3 (iPhone, same household as an Android phone): on Groceries type "2 bananas", "Greek yogurt" and
    "batteries": they sort under Produce, Dairy and eggs, and Other; typing BANANAS asks Already on the list and
    Increase makes it × 3. Check off yogurt, then bananas; new milk and apples now list Dairy and eggs before Produce.
    Swipe a row: removed, Undo brings it back; Clear checked has Undo too. Add to pantry: one line each, tap a line to
    change it or Skip it; Add puts them in the Pantry tab. New list from the list name, rename it, delete it. Set Keep
    at least on an item on Android and use it up: it appears on the iPhone's default list as Low stock; restock it on
    Android: Back in stock with Remove. Items added on Android flash briefly on the iPhone and "Name added …" shows.
32. Web app W4 (iPhone, recipes saved on the Android phone): the Recipes tab shows the same cards and stars; search
    by an ingredient, the Favorites and tag chips, Sort A to Z. Open a recipe: the servings stepper scales the lines;
    Add to grocery list leaves what is in the pantry unchecked, Change renames a line; I cooked this takes the eggs off
    the pantry with Undo; Cook keeps the screen on while stepping through. Share opens the iOS share sheet; Open
    original opens Safari. Edit a step, then Delete: Undo brings it back on both phones. A recipe using food that
    expires soon shows under Use it up on Home.
33. Web app W5 (iPhone and an Android phone in the same household): the Plan tab's week starts on the same day as
    on Android. Plan a recipe for tonight with yourself as the cook, and Leftovers for lunch: both appear on the
    Android Plan and on Home's Today card. Change the servings and a note; Cooked takes the scaled amounts off the
    pantry, with Undo. Move a meal to another day, Undo puts it back. Next week: Copy last week, add the week's
    ingredients to a grocery list (Meal plan badge), Clear this week and Undo. A meal planned on Android shows on
    the iPhone within seconds.
34. Web app W6a (iPhone, same household as an Android phone): Settings > Appearance: Dark, then Fiery Palette change the whole app at once and stay after closing and
    opening it; System follows the iPhone again. Units: Metric shows on the Android phone's Settings too. Export:
    Safari asks to download `spoilnot-export-<date>.json`; it opens in Files and lists the pantry. About: the build,
    the privacy summary and the licenses; tap one for its text. As the owner, Clear household data: Yes, then a wrong
    password is refused, the right one clears the pantry, lists, recipes and plan on both phones, and Recent activity
    starts with "You cleared the household's data". Larger Text at the largest size: Theme and Units become lists of
    radio buttons. There is no app tour on the iPhone.
35. Web app W6b (an iPhone with iOS 16.4 or later and a deployed build, W7): in Safari, Settings > Notifications asks
    to add Spoil Not to the Home Screen. Add it and open it from there. Add three items with dates: the third explains
    reminders; Turn on shows the iPhone's question, Allow. Settings > Notifications has no banner; Send test
    notification shows one, and tapping it opens Pantry on Expiring soon. Set the time to the next hour: the digest
    arrives shortly after it, on the iPhone and the Android phone, with the same words; tapping it opens Pantry on
    Expiring soon (or Plan when only meals are listed). Turn notifications off for Spoil Not in the iPhone's Settings:
    the banner says where to turn them on. Sign out: the next digest does not reach this iPhone.
36. Web app W7 (an iPhone and `https://spoilnot-dev.web.app`, then prod): in Safari the welcome screen says how to add
    Spoil Not to the Home Screen and Home shows the card; Not now hides it. Share > Add to Home Screen: the icon is
    the Spoil Not jar, the app opens full screen with no Safari bars and neither hint shows. Sign in, then turn on
    Airplane Mode and reopen the app: it opens and shows the pantry; an item added offline appears on the Android
    phone once the iPhone is back online. Deploy a new build: within a day of opening the app, "A new version of Spoil
    Not is ready" offers Update, which reloads into it. Run flows 28 to 35 on this install.

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
