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
    it starts again; Skip tour ends it at any step. Open Settings from Home and take the tour: Start the tour closes
    Settings and highlights Home's first card.
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
    phone once the iPhone is back online. Deploy a new build: the next time the app comes to the front (at most an
    hour after it last looked), "A new version of Spoil Not is ready" offers Update for 10 seconds, which reloads
    into it. For a build that raised `WEB_BUILD` (after v1), set `minWebBuild` (RUNBOOK) with the app open on the
    old build: the prompt shows within seconds and stays until Update, and About then shows the new build number.
    Run flows 28 to 35 on this install.
37. Sign out and Delete account in Settings (Android, after v1): on Phone B, with reminders on and an item expiring
    tomorrow, sign out from Settings (not from the gate): Welcome shows within a few seconds. Leave Phone B signed
    out past the digest hour: no digest reaches it, while Phone A gets its own. Sign in again, then sign out in
    Airplane Mode: Welcome still shows. Turn Airplane Mode off, swipe Spoil Not away and open it again: no digest
    reaches Phone B from then on. With a spare member account on Phone B: Settings > Delete account, the password,
    Delete: Welcome shows; signing in with that account says the email or password is wrong, and Phone A's member
    list no longer has it.
38. What sign-out leaves behind (Android and the iPhone web app, after v1): on Phone B open a recipe's source in the
    built-in browser and sign in to that site, or accept its cookies; open a recipe with a photo. Sign out from
    Settings and sign in with another account: the built-in browser no longer remembers the site's sign-in, and the
    photo loads again from the network (it shows a moment later than before). On the iPhone (Home Screen app with
    notifications on, item expiring tomorrow): sign out in Airplane Mode, turn Airplane Mode off, swipe Spoil Not away
    and open it again (leave it on Welcome): no digest reaches the iPhone from then on, while Phone A gets its own. Signed in
    without a household on either phone: the gate's menu has Delete account; a wrong password says so in the
    dialog; the right one shows Welcome, and signing in with that account then fails. A test notification still
    opens the Pantry on Expiring soon (notification links stay on this site).
39. Rules of 2026-11-08 (both apps on spoilnot-dev after the rules are deployed there, RUNBOOK): with two expired
    batches, throw both away at once: no "You don't have permission to do that.", Insights' month counts both, and
    Undo puts both batches and the count back. I cooked this on a recipe that uses up two whole batches: both go,
    Insights counts two used, Undo puts them back. As a member (not the owner), delete a list with items: it goes on
    both phones, and Recent activity names each person as the member list does. On a spare household, transfer
    ownership, then delete the household as the new owner. Repeat on the iPhone.
40. Deleting offline (Android and the iPhone web app, after v1): with a few grocery items and a pantry item, turn on
    Airplane Mode. Delete a grocery item: it disappears at once; Undo brings it back. Check off two items and Add to
    pantry: the In cart section and the Add to pantry button disappear at once, and the items show in the Pantry with
    "Waiting to sync". Used up on a pantry item: it disappears. Turn Airplane Mode off: within a minute the icons go,
    Phone A shows the same pantry, and each bought item is there once, not twice.
41. Sync cursor (Android and the iPhone web app, after v1, DESIGN 9.8): with Milk in the pantry and Keep at least 1
    with Add to grocery list on, open Groceries on the iPhone, then go to its Pantry tab. On Phone A add two items to
    the list, check one off and delete another. Wait 6 minutes. On the iPhone use up the Milk from Pantry (Low stock
    Milk goes on the list), then open Groceries: Phone A's changes are all there, not only Milk. Repeat with the
    phones swapped (Phone A uses up the Milk on its Pantry tab).
42. Restock engine (Android and the iPhone web app, after v1, 2026-11-09): give Milk Keep at least 2 with Add to
    grocery list automatically, 3 in stock, both phones open on Groceries. On Android use 2: within a second Milk is on
    the list as Low stock on both phones, once. Check it off on the iPhone, then Add to pantry with 2. Use up that new
    purchase on Android: Milk comes back **unchecked**, in the list to buy, not in the In cart section, and Home counts
    it. Repeat with the roles swapped. Sign out on the iPhone and sign in again (an empty cache): no staple that has
    stock is added. Undo of Used up on the last Milk puts it back and leaves the item with Back in stock. On Android,
    edit a product that has Keep at least but no automatic add (from before the beta, if a tester still has one): change
    its location and save: it still shows as a staple.
43. One phone wins (Android and the iPhone web app, after v1, 2026-11-09, DESIGN 8.5): put two test items on the
    list and check both off on both phones. Turn on Airplane Mode on the iPhone and press Add to pantry there ("Added
    to the pantry"); press Add all on Android; turn Airplane Mode off. Within a minute the iPhone says "<Android
    user's name> already took these off the list. Nothing was added to the pantry." and both Pantries show each item
    once, with one product of each name. Repeat with the phones swapped, and once with both online pressing at the same
    moment (count "3, 2, 1"). Plan a test dinner from a recipe that uses something in the pantry for today; press
    Cooked on both phones at the same moment: one says "<name> already marked this meal cooked. Nothing was taken
    from the pantry." and the amounts come off once. Create a list "Test party"; with Android in Airplane Mode add
    "Chips" to it, delete the list on the iPhone, turn Airplane Mode off: Android says "That list was deleted, so the
    change wasn't saved." Delete the test purchases afterwards.
44. Quota reached (Android and the iPhone web app, after v1, 2026-11-09, DESIGN 6.7): on the emulators, run the web
    e2e `quota.spec.ts` (it refuses every listener with `resource-exhausted`). On a real phone this happens only when
    the project's free reads run out, so check it whenever it does (RUNBOOK, Check quota usage): both apps keep showing
    the household from the phone with the red banner "Spoil Not has reached its free daily limit and will work again
    tomorrow." above the screen, never a spinner or the sign-in; tabs say Offline, Groceries included; adding and
    checking off still work. After midnight Pacific the banner goes by itself and Offline goes within a minute.
45. Listeners that let go (Android, after v1, 2026-11-09, DESIGN 9.1, 9.6): on the emulators, open Home, Pantry and
    Groceries, then press Home on the phone and wait 15 s. The Emulator UI's Firestore Requests show no new listen
    requests while the app is in the background. Bring it back: Home's lists show again at once, and another phone's
    +1 on a purchase shows within a second on Home and on Pantry, read once (one request per collection, not one per
    tab). With Pantry open on phone B and phone A on Settings, use up a staple on phone A: one Low stock item appears
    on both. Kill the app, then send it a digest (or start it from `adb shell am broadcast` as review 09 did): the
    Requests page shows the session's reads (profile, membership, device) but no products, stock or grocery list. On
    a real phone, the console's
    usage page after a normal day (RUNBOOK, Check quota usage) should be near quota-sim's per-household figure.
46. Listeners that let go on the iPhone web app (after v1, 2026-11-10, DESIGN 9.1, 9.6, 18.3): on the emulators with
    `npm run dev`, open Home, then Pantry and Groceries on the iPhone (Home Screen app). The Emulator UI's Firestore
    Requests show one products and one stock listen for the three tabs, not one per tab. Swipe the app away to the
    Home Screen (don't close it) and wait 15 s, then bring it back: the screen shows what it showed at once, and the
    Requests page shows changes-only listens (`updatedAt >`) for the screen on display, never a full read of products
    or stock again. With the iPhone on Settings and the Android phone on Pantry, use up a staple on Android: one Low
    stock item appears on both (written by the Android phone). Then open Pantry on the iPhone and use up another
    staple there: the iPhone writes the Low stock item at once. Sign out: Welcome shows and no household listen stays
    open.
47. Joining and signing out (Android and the iPhone web app, after v1, 2026-11-10, DESIGN 5.1, 10.1): on a spare
    account, ask to join the test household. The waiting screen says the owner doesn't get a notification. On the
    owner's phone, Settings > Household > Join requests shows the name, the masked email (`ro***@example.com`, the
    account's first two letters and domain), "Sent: Just now" and "Only approve people you invited." With the rules
    not yet deployed (RUNBOOK) the request still goes through and shows no email. Approve it while the joiner's app is
    closed, delete the request in the console (as the job's prune once did), then open the joiner's app: it goes
    straight to Home, never "Request expired". On the joiner's phone in Airplane Mode, change units in Settings and tap
    Sign out: after a few seconds "Sign out anyway?" says changes haven't synced; Cancel keeps you signed in, Sign out
    anyway signs out. Clear household data offline on a spare household says "You're offline" and changes nothing.
    Sign the spare account in on a second phone, delete the account on the first: the second goes to Welcome, and
    the console shows no `users` document for it a minute later.
48. Edits don't undo someone else's change (Android and the iPhone web app, after v1, 2026-11-10, DESIGN 9.2, 9.4):
    with Milk ×3 on both phones, open its Edit window on phone A. On phone B press −1 twice: phone A shows "Bob changed
    this item." with Review and keeps the form at 3. Change only the date on phone A and save: both phones show Milk
    ×1 with the new date. Open it again on A, and on B throw it away: A's window closes with "Bob removed this item".
    Put Yogurt ×1 on both, turn on Airplane Mode on A, press +1 twice on B, press −1 on A ("Yogurt used up" with
    Undo), turn Airplane Mode off: both show Yogurt ×2. Press −1 on Yogurt until it is used up on A and Undo within a
    few seconds: Yogurt ×1 is back on both. Throw a purchase away on B and Undo: it comes back, and in the Emulator UI
    (or the console on spoilnot-dev) its `createdBy` is still A's.
49. Restore and Export tell the truth (Android and the iPhone web app, after v1, 2026-11-11, DESIGN 13.4): in Airplane
    Mode, Settings > Export on Android says "You're offline" and opens no Save dialog; on the iPhone it says the same
    and downloads nothing. Back online, Export saves the file: it has `profile` (your name and settings) and
    `activity` (recent events) after `meals`. Owner's restore check on spoilnot-prod: run restore-from-export with the
    household's first characters and **apply** unticked; the log shows the counts and `Target: same owner as the
    export.` (never the full ID). A target household of another account (a spare household) stops with `restore
    failed: different-owner`. After a real restore, a phone that stayed closed shows the restored pantry within a
    minute of opening, without a restart.
50. Forms keep what you typed, and Add to pantry gets Undo (Android and the iPhone web app, after v1, 2026-11-11,
    DESIGN 5.3, 6.1): with Butter, Sugar and Eggs on the list, check off Butter and Eggs on phone A and open Add to
    pantry. Set Butter to 3 and +2 weeks. On phone B check off Sugar: A gets a Sugar row and Butter still says 3 and
    the date. Rotate A, then switch to another app, run `adb shell am kill com.parcha.spoilnot.dev` and come back:
    the sheet and Butter's edits are still there (Android). Press Add all: "Added to the pantry" with Undo. Press Undo:
    Butter, Sugar and Eggs are back in the cart on both phones and the pantry has no new purchases; a product the
    addition created (say Sugar) is gone from the pantry and from Add item's suggestions. With Rice kept in grams and
    in stock, quick-add "2 kg rice" and open Add to pantry: its unit is grams and can't be changed ("Set while in
    stock") and the amount is empty or the last amount. With "Rice 500 g" on the list, quick-add "2 kg rice": the
    prompt says it's already on the list as 500 g and offers only Add anyway. Quick-add "3 lbs apples": an item
    "apples" with 3 lb. Rename Milk to "Whole milk" while "Milk" is on the list from Low stock, then add it to the
    pantry: it goes to Whole milk, with no second Milk. On Android, type a title in Plan's Add meal sheet and rotate:
    the sheet stays with the title.
51. Bad data skips one document (Android and the iPhone web app, after v1, 2026-11-11, DESIGN 6.7, 8.6): add Cheese to
    the pantry, then in the Emulator UI (or the console on spoilnot-dev) change one purchase's `quantity` to the text
    "2", its `updatedAt` to now, and one grocery item's `checked` to the text "yes" (or run `bad-data.spec.ts`).
    Pantry, Groceries and Home keep showing everything, the edited purchase as used up and the item unchecked; before,
    the tabs went blank. In the web app on the emulators in a desktop browser, run
    `spoilnotTest.refuseListens('internal')` in the console on Pantry: it says "Couldn't load." with Retry above the
    list it showed; run `spoilnotTest.refuseListens(null)` and tap Retry: the banner goes within a few seconds. On a
    phone, turn on Airplane Mode, press +1 on Cheese, go to Recipes, delete the purchase in the Emulator UI or the
    console and turn Airplane Mode off: Recipes shows "You don't have permission to do that." (the rules see no
    purchase to update).
52. Contrast and time to act (Android, colors also on the iPhone web app, after v1, 2026-11-12, DESIGN 5.2, 6.1, 6.4):
    on the phone set Settings > Accessibility > Time to take action to 1 minute, mark a pantry item Used up and wait:
    the Undo snackbar stays about a minute (8 s with the default setting), and Undo still works after 30 s. Put the
    setting back. In light mode on Spoil Not green and on Summer Fun, the Fresh, Soon and Today chips on Pantry and
    Home read clearly, and so do Used up and Throw away while swiping a row. In dark mode on Fiery Palette and on
    Watermelon Sorbet, Home's buttons and Pantry's Add item are teal and mint, and the Expired chips stand out next to
    them; the backgrounds and the selected tab keep the orange and the pink. On the web app the same chips use the
    new colors.
53. Privacy details (Android, after v1, 2026-11-12, DESIGN 11.6, 12.2): on Android 13 or later, Household > Invite
    people > Copy. The copy preview at the bottom of the screen shows dots instead of the code, and the keyboard's
    clipboard (Gboard: the clipboard icon) doesn't list it; pasting into a notes app gives the code. Import a recipe
    the app opens in the built-in browser (Allrecipes, from docs/import-test-sites.md): the recipe is found as before.
    On the next beta (a prod build), run section 5 (Crash reports): the report arrives with Send crash reports on and
    none with it off. Optional, with the phone on `adb`: `adb logcat -s FirebaseAuth` while signing in shows no `D` or
    `I` lines.

## 3. Conditions

| Check | How | Expected |
| --- | --- | --- |
| Offline | Airplane mode, add and edit items, then reconnect | "Offline" banner and cloud icons; everything syncs after reconnect, nothing lost |
| Time zone change | Change the phone's time zone | Today's statuses follow the new local date; the next reminder arrives at the chosen local hour |
| A DST day | On or just after a daylight saving change (or simulate with the time zone of a place changing that week) | Reminder arrives once, at the chosen hour |
| Notification permission denied | Deny the permission when asked; open Settings > Notifications | A banner explains how to turn it on; no crash |
| Battery saver | Turn on battery saver overnight | The morning reminder still arrives (it may be a little late) |
| A removed member's phone left closed | Close the app on the second phone (reminders on), then remove that member on the owner's phone; wait for the member's reminder hour | No reminder reaches the second phone; opening its app shows the gate |
| 200% font | Settings > Display > Font size at maximum | Every screen readable, nothing cut off or overlapping |
| Dark mode | Settings > Appearance > Dark, then System with the phone in dark mode | All screens readable; status colors still distinct |
| TalkBack | Turn on TalkBack; go through Home, Pantry, a recipe and Groceries; on each, find the floating button (Add item, Add N to pantry after checking an item off, Add recipe, Cook) | Every control is announced with its name and state ("Milk, 2, expires tomorrow, fridge"); the floating buttons are read by their text ("Add item, button"), never just "Button"; every swipe action is also in a menu |
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

Web app for iPhone (flows 28–36): passed on the owner's iPhone against https://spoilnot-dev.web.app on 2026-10-31,
before the first deploy to https://spoilnot-prod.web.app.
