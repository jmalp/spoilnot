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
3. Turn on Keep some in stock with Keep at least on a product and use it below that: it appears on
   the grocery list by itself on both phones.
4. Groceries: quick-add "2 kg rice", check it off (light vibration), Add to pantry.
5. Recipes: share a recipe page from Chrome (Share > Spoil Not). The preview opens in under 5 seconds; Save. Open it,
   change servings, start Cook mode (screen stays on), add ingredients to the grocery list ("Probably in pantry"
   items start unchecked).
6. Home: Needs attention, Grocery list (with what is running low) and Recent activity match what you just did; each
   card's button switches to the right tab with the right filter.
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
    the Grocery list card's Running low list the same items as the Android Home (after v1, flow 56); See all opens
    Pantry's Needs attention filter and Open list opens Groceries; This month shows the same counts; throwing something away on Android appears in Recent activity within seconds. See insights: tap last
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
    notification shows one, and tapping it opens Pantry on Needs attention. Set the time to the next hour: the digest
    arrives shortly after it, on the iPhone and the Android phone, with the same words; tapping it opens Pantry on
    Needs attention (or Plan when only meals are listed). Turn notifications off for Spoil Not in the iPhone's Settings:
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
    opens the Pantry on Needs attention (notification links stay on this site).
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
54. iPhone polish and the web's own form state (iPhone web app, after v1, 2026-11-12, DESIGN 5.9, 6.1, 18.1, 18.2,
    18.3): with Butter, Sugar and Eggs on the list, check off Butter and Eggs on the iPhone and open Add to pantry. Set
    Butter to 3 in the Freezer. On another phone check off Sugar: the iPhone gets a Sugar row and Butter still says
    "3 · Freezer". Switch to other apps for a while (open the camera and a few heavy apps) and come back, or pull down
    to reload in Safari: the sheet is still open with Butter's 3 and Freezer. Close it and reload: it stays closed.
    In Plan, go to next week, tap + on a dinner, pick yourself and type "Pizza night"; reload: the sheet is back on
    next week with the pick and the text. **Keyboard** (review 10's phone checks 2 and 5): in Pantry > Add item tap
    Name, then Quantity, then Notes: the sheet and its Save button stay above the keyboard and the focused field
    scrolls into view; the same in Add to pantry with every row open, and in landscape. **Edge swipe** (review 10's
    check 8): in Safari (not installed), swipe a Pantry row to the right starting at the very left edge: Safari goes
    back and the row is untouched; starting a finger's width in, the row is used up (Undo). **Week starts on**:
    Settings > Appearance says "Automatic (Sunday)" on a US iPhone; choose Monday: Plan's weeks start on Monday, also
    after closing and opening the app.
55. Home tells the truth (Android, and the shared parts on the iPhone web app; after v1, 2026-11-12, DESIGN 5.2, 5.4,
    5.6, 5.9): in a household with 8 expired purchases and Milk (today) and Yogurt (in 2 days), Home > Needs attention
    lists Milk and Yogurt with their chips, then "8 expired items to throw away or check"; See all 10 in Pantry opens
    the Needs attention filter with the same 10 purchases, the expired folded at the top. A digest naming expired items
    opens the same filter on Android and on the iPhone (`/pantry?filter=expiring`). With Olive oil out and on the list,
    there is no Low stock card: the Grocery list card shows "N items to buy", then Running low with Olive oil and an
    Out badge, and Open list opens Groceries. Plan Tacos for tonight with a recipe that uses Milk: Use it up on Home and
    in Plan's Add meal sheet no longer suggests Tacos. Check off an item: Recent activity doesn't say so (a check-off
    from a phone on beta 9 still shows). Create a new household: Needs attention says "Add food with its date…" and the
    Grocery list card "Turn on Keep some in stock…", with no green checks; Add item's switch reads Keep some in stock
    with "Puts it on the grocery list when it runs low". Go to next week on Plan: "Nothing planned this week…" with
    Copy last week, which copies this week's meals (or says last week has no meals to copy).
56. Home tells the truth on the iPhone (iPhone web app, after v1, 2026-11-12, DESIGN 5.6, 5.9, 18.1): flow 55's
    household, on the iPhone next to the Android phone. Home > Needs attention lists Milk and Yogurt, then "8 expired
    items to throw away or check" with a trash icon in the Expired color; See all 10 in Pantry opens Needs attention
    with the same 10. With Olive oil out there is no Low stock card: the Grocery list card shows Running low with
    Olive oil and an Out badge (with five or more low, "and 1 more"), as on Android. In Settings > Appearance choose
    Week starts on: Monday, plan Tacos (which uses Milk) for Sunday dinner from the iPhone's Plan: Use it up on Home no
    longer suggests Tacos, and Plan's Add meal sheet doesn't either for this week but does for next week. Create a
    new household on the iPhone: Needs attention says "Add food with its date…" with a fridge icon, the Grocery list
    card says "Turn on Keep some in stock…", and no green checks; Add item's switch reads Keep some in stock with
    "Puts it on the grocery list when it runs low" under it, gone once it's on (VoiceOver reads it as the switch's
    hint). Plan opens this empty week at "Nothing planned this week…" with Copy last week ("Last week has no meals to
    copy"); add a meal and the sentence goes; next week's Copy last week copies it, with Undo. Larger Text at the
    largest size: the expired line and Running low wrap without cutting words.
57. Reminders people can count on (Android, after v1, 2026-11-13, DESIGN 1, 5.4, 5.6, 10.2): on a phone that never
    ran the app (or after clearing its storage), sign in as a **member** of a household that has dated food, and
    finish or skip the tour. Without opening Pantry, Home asks "Get a heads-up before food expires" and names the time
    ("around 9:00 AM"). Tap Not now: Needs attention ends with "Reminders are off on this phone · Turn on", and Settings
    says Notifications "Off on this phone". Tap Turn on and Allow: within seconds a "You're set" notification says
    "Spoil Not reminds you once a day, around 9:00 AM…" and names tomorrow's items (or "Nothing is due tomorrow"); the
    Home line goes away and Settings says "Daily, shortly after 9:00 AM". Tapping it opens Pantry on Needs attention.
    Turn notifications off for Spoil Not in the system settings and come back: the line is back; Turn on now opens the
    system settings, and turning them on there and coming back shows "You're set". Turn the daily reminder off in
    Settings > Notifications: Home has no line and Settings says Off. On a new household as **owner**: no question
    for the first two dated items (an undated one doesn't count), then it asks after the third, on any tab.
58. Clear words and one rule for confirmations (Android, and the copy and joining on the iPhone web app; after v1,
    2026-11-13, DESIGN 5.1, 5.2, 5.3, 5.5, 5.9, 6.1): on Groceries, swipe Eggs to the right: "Check off" shows under
    the row and Eggs goes into the cart; swipe it right again in the cart: "Back to list". Swipe Milk to the left:
    "Delete" shows, then "Milk removed · Undo". The aisle headers read "Dairy and eggs", not capitals, and the cart
    button says Clear cart. With a product on Keep some in stock that runs low, its item says Added automatically,
    and Edit says "Added automatically: you have 0 and keep at least 2."; add stock back: the row shows one line,
    "Back in stock · Remove". After a trip that changed the aisle order, "Aisles follow the order you shop in…"
    shows under the first aisle until Got it, and not again on that phone. Type "bananas" with "Bananas × 6" on the
    list: "Add 1 more to the 6 on the list, or add it as a separate item?". Pantry > Add item: the keyboard is up
    on Name; type "Greek yogurt" and save: it is under Dairy and eggs (Group by category). Open a recipe imported from
    a site: no servings chip next to the stepper, no "By site" line under the site chip, Start cooking in a bar at
    the bottom with I cooked this fully visible above it. ⋮ > Delete: no dialog, the recipe box says "Deleted …" with
    Undo, which brings it back. Plan > ⋮ > Clear this week: no dialog, Undo brings the meals back. Recipes has no All
    chip; tapping Favorites twice shows everything. Settings > Notifications > Expired reminder offers "Every day for
    two weeks". Settings > Household as the owner: New code, and no Pause joining, on both apps; on a household an
    older build paused, after the 2026-11-13 rules, a new phone can still ask to join and the owner approves it.
59. Reminders, clear words and confirmations on the iPhone (iPhone web app, after v1, 2026-11-13, DESIGN 5.2, 5.3,
    5.4, 5.5, 5.9, 18.4): on an iPhone with Spoil Not on the Home Screen and its website data cleared (or a new
    install), sign in as a **member** of a household that has dated food. Without opening Pantry, Home asks "Get a
    heads-up before food expires", naming the time. Tap Not now: Needs attention ends with "Reminders are off on this
    phone · Turn on", and Settings says Notifications "Off on this phone". Tap Turn on and Allow: a "You're set"
    notification says "Spoil Not reminds you once a day, around 9:00 AM…" and names tomorrow's items (or "Nothing is
    due tomorrow"); tapping it opens Pantry on Needs attention. Turn notifications off for Spoil Not in the iPhone's
    Settings and come back: the line is back, and Turn on opens Settings > Notifications, which says where to turn
    them on. In Safari (not the Home Screen app) nothing asks, and Settings says Off on this phone. On Groceries,
    swipe Eggs right: "Check off" shows under the row and Eggs goes into the cart; right again in the cart: "Back to
    list"; swipe Milk left: "Delete", then Undo. Aisle headers read "Dairy and eggs"; after a trip that changed the
    order, "Aisles follow the order you shop in…" shows once until Got it. A restocked item says Added automatically,
    Edit gives the reason, and stock coming back leaves one line "Back in stock · Remove". Pantry > Add item: the
    keyboard opens on Name; "Greek yogurt" lands under Dairy and eggs. A recipe: Start cooking in a bar at the bottom,
    I cooked this fully visible; ⋮ > Delete goes at once with Undo; Recipes has no All chip. Plan > ⋮ > Clear this
    week goes at once with Undo. With VoiceOver on, swipe to an Undo snackbar: it stays while focused, and goes about
    8 seconds after focus leaves.
60. Large text, screen readers and keyboard (Android, after v1, 2026-11-13, DESIGN 6.2, 6.4, 6.8): **TalkBack on**
    (Settings > Accessibility > TalkBack), swipe through Home from the top: each Needs attention row is one stop with
    the name and its status ("Milk, Expires tomorrow"), each Today meal one stop ("Dinner: Tacos, cooking: You,
    Alex"), and the avatar says "Open settings, 1 join request waiting" once. Groceries: each row is one stop with the
    amount in words ("Bananas, 2 pieces, from a recipe, checkbox, not checked"); TalkBack's Headings navigation
    (swipe up then down to choose Headings) jumps from aisle to aisle, and in Pantry from Fridge to Freezer. Add item:
    the date tiles say "radio button, 1 of 4". A recipe card's star says "Favorite, checkbox, checked" or "not
    checked", the same on the recipe. Cook mode's Back says "Back to the previous step". Settings > Take the app tour:
    while the card shows, swiping right never leaves it, and double-tap Next moves on; finish or skip, then Home reads
    normally again. **TalkBack off, a Bluetooth keyboard** (or the emulator's): press Tab on Home: focus goes to
    Settings, then the cards' buttons, and only then the five tabs, with a green ring around each; tapping the screen
    hides the ring. **Font size and Display size at the largest** (Settings > Display): Home's names are whole with
    the chip under them, the join requests card has Review under its sentence, the tab labels are on one line, a
    recipe's "Ingredients" is whole with the servings stepper under it, cook mode's Ingredients button is on its own
    line, and in Pantry, Groceries and Recipes the last row scrolls fully above the floating button. Any sheet's drag
    handle can be dragged from a little beside the bar.
61. Large text, VoiceOver and the keyboard on the iPhone (web, after v1, 2026-11-13, DESIGN 6.8, 18.2): in the Home
    Screen app, **VoiceOver on** (Settings > Accessibility > VoiceOver), swipe through Home from the top: each Needs
    attention row is one stop ("Milk, Expires tomorrow"), each Today meal one stop ("Dinner: Tacos, cooking: You"),
    and the avatar says "Open settings, 1 join request waiting" once. Groceries: each row's checkbox reads the whole
    row ("Bananas, 2 pieces, from a recipe, checkbox, unchecked"); with the rotor on Headings, swiping down jumps from
    aisle to aisle and to In cart, which says expanded or collapsed. Add item: the date tiles say "radio button, 1 of
    4". A recipe card reads its title and time, its star "Favorite, toggle button" with on or off, the same on the
    recipe. Cook mode's progress says "Step 1 of 3" and the second Back "Back to the previous step". Household:
    Approve and Decline name the person. With a sheet open, swiping never leaves it. **VoiceOver off, a Bluetooth
    keyboard** with Settings > Accessibility > Keyboards > Full Keyboard Access on: Tab on Pantry goes through the
    screen (Settings, search, chips, rows) and only then the five tabs, with a green ring around each. **Larger Text at
    the largest size** (Settings > Accessibility > Display & Text Size > Larger Text, Larger Accessibility Sizes, slider
    at the end), then reopen the app: Home's names are whole with the chip under them, the join requests card has
    Review under its sentence, the five tab labels are whole on one line, a recipe's "Ingredients" is whole with the
    servings stepper under it, cook mode's Ingredients button is on its own line, I cooked this shows each amount under
    its ingredient, and in Pantry and Groceries the last row scrolls fully above the floating button.
62. Recipes, plan and names (Android and the iPhone web app, after v1, 2026-11-13, DESIGN 5.3, 5.5, 5.9, 8.1, 11): on
    Android, import a recipe with "Makes 120" servings (or create one with 120 servings and "1/8 tsp salt", "3 eggs"
    and "2 tbsp extra-virgin olive oil"): the detail's stepper goes past 100 to 120; at 12 servings the salt reads "a
    pinch of salt", not "0 tsp salt". Plan it: the meal's sheet shows 120 servings and + stays usable up to 120. With
    Olive oil and Eggs in the pantry, I cooked this at 6 servings of a recipe for 4 with "3 eggs" suggests 5 eggs, and
    the olive oil line is linked. Groceries: type "chicken broth", "frozen spinach" and "tomato paste": Pantry
    staples, Frozen, Pantry staples. Plan: put two "Leftovers" at Monday lunch, go to next week, Copy last week: two
    Leftovers there. On both apps, paste "Milk 2%" with a no-break space from a web page (or type a full-width space
    on a Japanese keyboard) as a product on Android, then add "milk 2%" on the iPhone's Groceries and Add to pantry:
    it adds to the same product. Android, optional: phone language Arabic, Edit an item with 1.5: the field shows
    "1.5", + makes it 2.5 and Save works. Android, optional: with Pantry open, set the time zone about 12 hours away
    (automatic off), return: within a minute the chips show the new day.
63. The new look, step 2: the shell and Home (Android, after v1, 2026-11-15, DESIGN 19.7 to 19.12): every tab sits on
    the tinted page under a header with a round Settings button, as tall as the old bar; Home's header says today's
    date above "Home" in the phone's language. With food expiring tomorrow and two expired items, Needs attention is
    the green card with "3" in a circle, a Tomorrow row with its tile and the words "Tomorrow", and "2 expired items to
    throw away or check" as its last row; TalkBack reads the first row as "Milk, Expires tomorrow" and never reads the
    "3" alone. With nothing expiring it is a small white card that says so. Plan two meals today: Home shows them as
    two tiles under "Today" with Open plan beside it; at 2× text they stack. Your kitchen is the dark green card with
    the large percentage; tapping anywhere on it opens Your kitchen, and TalkBack reads the title and one sentence
    with the counts. Pantry's Add item is the dark green button; use something up: the Undo snackbar has rounded
    corners above it. Turn on airplane mode: the Offline banner is a rounded strip under the header. Join from another
    account as the owner's household: the Settings button shows the badge, which stays readable at 2× text. Dark mode
    and a preset color theme (Fiery Palette): the same, in their colors.
64. The new look, step 2 on the iPhone (web, after v1, 2026-11-16, DESIGN 18.2, 19.7 to 19.12): flow 63 in the Home
    Screen app on an iPhone, with VoiceOver instead of TalkBack: Home's line above the title is today's date in the
    iPhone's language; VoiceOver reads the first Needs attention row as one sentence and never the count alone, and
    Your kitchen as one button ("Your kitchen this month, This month: … used") that opens Your kitchen. In Safari (not
    installed), the Add Spoil Not to your Home Screen card is a plain white card above Needs attention. Settings >
    Accessibility > Larger Text at the largest size: the header grows with the text, Today's tiles stack, and the
    Settings badge's number stays inside its dot. Scroll Pantry: the header keeps the page's color.
65. The new look, step 3: Pantry (Android, after v1, 2026-11-16, DESIGN 5.2, 19.6, 19.9 to 19.12): with milk
    expiring tomorrow, yogurt in 2 days, eggs in 6 days and rice with no date, Pantry's header says "4 items" above
    the title, and the list has two groups: "Use first" (milk, yogurt) and "Everything else" (eggs, rice). Each row
    starts with a tile and says its status in words, then the location ("Tomorrow · Fridge"); rice says "No expiry
    date · Pantry". TalkBack reads a row as one sentence ("Milk, 2 pieces, Expires tomorrow, Fridge"), "double tap to
    Edit", and the ⋮ separately; headings navigation jumps from Use first to Everything else. Add three expired items:
    "3 expired items" with Show is Use first's first row; Show puts them under it. Throw everything in Use first
    away: no headings. Sort by name, group by location, type in the search or choose a filter: the two groups go
    (location groups keep their headings, pinned while scrolling). A staple at 0 says "Out · Pantry" in red. Swipe a
    row right and left: the tint fills the row's rounded shape. Add item: the labels sit above the fields, − and +
    join the quantity, the quick dates are joined buttons (tap the chosen one again to clear it), the chosen location
    is a filled green tile; leave Name empty and save: its box turns red with an icon and "Enter a name.". Throw away:
    the dialog's reasons are rows. Mark opened: − and + join the count, the choices are chips. At 2× text: the quick
    dates are radio buttons, "× 2" never breaks, the location tiles are a 2 by 2 grid. Sign in, the gate and Edit on
    Groceries show the same fields. Dark mode and Fiery Palette: the same in their colors; a dialog's rows stand off
    the dialog.
66. The new look, step 3 on the iPhone (web, after v1, 2026-11-16, DESIGN 18.2, 19.6, 19.9 to 19.12): flow 65 in the
    Home Screen app on an iPhone, with VoiceOver instead of TalkBack: Pantry's header counts the items, the list has
    Use first and Everything else (the rotor's headings jump between them), each row reads as one sentence, and the
    quick dates and locations are radio buttons. With a second phone, scroll Pantry slowly while the other adds an
    item that sorts near the top: the rows under your finger don't move until the scroll stops, then the new row
    appears. Settings > Accessibility > Larger Text at the largest size: "× 2" joins the second line, the quick dates
    are a list of radio buttons and the locations a 2 by 2 grid. Sign in, the gate and Groceries' Edit show the new
    fields; Groceries' quick add and Recipes' search keep the outlined ones.
67. The new look, step 4: Groceries (Android, after v1, 2026-11-16, DESIGN 5.3, 19.5, 19.10): with four items to buy
    and one in the cart, the header says "4 to buy" over the list's name, and tapping the name still switches lists.
    The quick-add field is round with a filled +, grey until something is typed; typing "mi" suggests Milk. Each aisle
    is a group of rows under its heading (pinned while scrolling; TalkBack's headings jump between aisles), every row
    with its checkbox, "× 12" beside the name and its source as a chip ("Added automatically", "Meal plan",
    "Recipe"). In cart (1) is grey with Clear cart beside it; its row is darker and struck through; tapping In cart
    folds it. Swipe right and left: the background fills the row's rounded shape. With a second phone, scroll the list
    slowly while the other checks something off: the rows under your finger don't move until the scroll stops. Add to
    pantry: its lines are rounded rows that open in place with the joined − and + stepper, the unit, the date's label
    and the quick dates; Skip has room before it at 2× text. Edit: the sheet title and labels above the fields. Dark
    mode and Fiery Palette: the same in their colors.
68. The new look, step 4 on the iPhone (web, after v1, 2026-11-16, DESIGN 18.2, 19.5, 19.10): flow 67 in the Home
    Screen app on an iPhone, with VoiceOver instead of TalkBack: each row reads as one sentence on its checkbox, the
    rotor's headings jump between aisles and to In cart (expanded or collapsed), and the + says "Add to list". Scroll
    down so Dairy and eggs' heading is pinned, then open the list switcher: its menu shows over the heading and its
    lists can be chosen. With a second phone checking items off while you scroll slowly, the rows under your finger
    don't move until the scroll stops. Settings > Accessibility > Larger Text at the largest size: names wrap, chips
    and Remove stay whole, and Add to pantry's stepper and unit stack.
69. The new look, step 5: Plan (Android, after v1, 2026-11-16, DESIGN 5.9, 19.12): this week shows "This week" over
    Plan, the week on a round bar, and under it seven day buttons with today's filled. Tap Thursday: the list scrolls
    to Thursday's heading; tap Sunday: it scrolls to the end, where Sunday shows. Today is the green card with a Today
    badge; other days are a heading over their rows, past days' headings grey. Plan two dinners today: each has its
    own row with "Dinner" over its name, and only the first has the +. TalkBack: a day button reads "Saturday, Nov 14,
    today, double tap to show this day"; a meal reads as one sentence ("Dinner: Tacos, 4 servings, cooking: Sam") and
    its + is the next stop; the headings jump between days. Next week: the line above the title is gone and Go to this
    week sits between the bar and the days. Add meal: the title, Who's cooking chips, Something else with its filled +
    (grey until something is typed), Use it up and the recipes as rounded rows under the round search field. A meal:
    the servings stepper, the chips, Note, Save and the tonal Cooked, Move and Open recipe; Move's chips have a check
    when chosen. Larger text at 2×: the day row scrolls sideways and the Today badge goes under the title. Dark mode and
    Fiery Palette: the same in their colors.
70. The new look, step 5 on the iPhone (web, after v1, 2026-11-16, DESIGN 18.2, 19.12): flow 69 in the Home Screen app
    on an iPhone, with VoiceOver instead of TalkBack: a day button reads "Monday, Nov 16, today, button, Show this
    day", and double-tapping Thursday scrolls Thursday's heading to the top; a meal reads as one sentence and its + is
    the next item; the rotor's headings jump between days. With Reduce Motion on, a day button jumps instead of
    scrolling. Settings > Appearance > Week starts on Sunday, then Monday: the buttons follow, today's still filled.
    Settings > Accessibility > Larger Text at the largest size: the day row scrolls sideways under the week bar, both
    stay put while the days scroll, and the Today badge goes under the title.
71. The new look, step 6: Recipes (Android, after v1, 2026-11-16, DESIGN 5.5, 19.12): the box says "4 recipes" over
    Recipes; search and the chips (Favorites with a star) are on the page; cards show the photo or the first letter
    in a leaf on a colored block, and the star is a round button that turns it on and off. Open a recipe without a
    photo: its letter in a leaf on top, the page overlapping it with rounded corners, round Back, star, Edit and ⋮
    staying over it as you scroll; the time and site are pills (the site opens the original); − and + beside
    Ingredients change "4 servings" and the amounts, and TalkBack says the new number; tick lines off; steps have their
    numbers in leaves and read "1, Whisk the eggs"; Add to grocery list and I cooked this are side by side; Start
    cooking is at the bottom. Cook mode: Back and the name at the top with "Step 1 of 2", a bar per step, the step's
    number in a leaf; Back is grey on the first step, Next on the last; Ingredients opens the checklist. The + menu:
    three rounded rows; Paste a link: the field's label above it. Add to grocery list and I cooked this: rounded rows,
    Probably in pantry as a chip, the amounts in the new fields. Edit and an import's preview: the round Back over the
    title; the editor's ingredients and steps are tall fields. The in-app browser: round Close and the round address
    field; Recipe found has rounded top corners. Larger text at 2×: the stepper goes under Ingredients, the two buttons
    stack, and cook mode's Ingredients has its own line. Dark mode and Fiery Palette: the same in their colors.
72. The new look, step 6 on the iPhone (web, after v1, 2026-11-17, DESIGN 18.2, 19.12): flow 71 in the Home Screen app
    on an iPhone, without the parts the web doesn't have (adding, importing, the browser), with VoiceOver instead of
    TalkBack: a card reads "Pancakes, Total 20 min" and its star "Favorite, toggle button"; on a recipe the round
    Back, star, Edit and More stay over the page as you scroll, and the servings are read out as + and − change them;
    in cook mode the top reads the name, then "Step 1 of 2", the progress bar says "Step 1 of 2", and "Back to the
    previous step" is dimmed on the first step. Add to grocery list: rounded rows on the sheet, Probably in pantry as a
    chip; Change: the pantry's products as chips. Edit: the round Back over Edit recipe, and the ingredients and steps
    in tall fields with their labels above. Settings > Accessibility > Larger Text at the largest size: the servings
    stepper goes under Ingredients, the two buttons stack, and cook mode's Ingredients has its own line.
73. The new look, step 7: Your kitchen and Settings (Android, after v1, 2026-11-17, DESIGN 5.6, 5.7, 6.4, 19.12):
    Home > Your kitchen: round Back, this month's name over Your kitchen, then the green card with the percentage
    large, Used and Thrown away beside their swatches, the ring at the end and the friendly sentence under them; the
    charts grow once without stutter, and with Remove animations on they are drawn at once. Last 6 months: tap another
    month, its background turns tinted, and its details in the rounded box below change (TalkBack reads them); the best
    month has a trophy. The streak is a blue card with the fire in a leaf and "15 days" large; the reasons are striped
    bars. Settings: the account card with the initial in a leaf (and Email not verified with Resend email when it
    isn't); Household and Notifications as rounded rows with tiles and chevrons ("Off on this phone" when blocked);
    Appearance: Theme and Units as joined buttons, the colors in a grid of four with a ring and a check on the choice;
    Data: tap Export household data to save the file, and the crash reports row turns its switch; the tour and About;
    Sign out is a wide tonal button, Delete account and (owner) Clear household data red text buttons. Household: the
    name as the title with a round Rename, join requests on the green card with Approve and Decline, the code large
    with Copy and Share, the members as rows with their initials. Notifications: the reminder and Meals I'm cooking as
    switch rows, Time, then rounded check and radio rows; Send test notification is tonal. About: the version row,
    Privacy on a card, the licenses below. Switching tabs: the new tab fades in, without the sideways slide that opening
    a screen still has; back from a tab to Home fades the tab out; turn on Remove animations with the app open and
    switch tabs: no fade at once, without restarting. Larger text at 2×: the ring goes under the numbers, Theme and
    Units become radio buttons, the colors two to a row. Dark mode and Fiery Palette: the same in their colors.
74. The new look, step 7 on the iPhone (web, after v1, 2026-11-17, DESIGN 18.2, 19.12): flow 73 in the Home Screen app
    on an iPhone, without what the web doesn't have (crash reports, the tour, the app's tab fade), with VoiceOver
    instead of TalkBack: Your kitchen's ring reads "This month: 3 used, 1 thrown away, 75% used", "Your kitchen this
    month" and "Streak" are headings, and the charts sweep and grow in once (at once with Reduce Motion on). Settings:
    the account card with the initial, Household read as one button with its summary, Appearance with Theme and Units
    as joined buttons, the colors four to a row, Week starts on under them, Data with Export household data as a row,
    About, and a wide tonal Sign out. Household, Notifications and About: the round Back over the title; Notifications
    in Safari shows the rounded red banner about adding the app to the Home Screen. Settings > Accessibility > Larger
    Text at the largest size: the ring under the numbers, Theme and Units as radio buttons, the colors two to a row,
    Resend email under its words.
75. The new look, step 8: signing in and the gate (Android, after v1, 2026-11-17, DESIGN 5.1, 19.12): signed out, Welcome
    shows the jar in a large leaf, Spoil Not in the rounded face and the tagline, with Create account and Sign in as
    tall buttons at the bottom. Sign in with a wrong password: the password field is outlined in red with an icon and
    "Email or password is incorrect." under it, and the cursor is in it (TalkBack reads the field with its error);
    offline, the message shows under the fields with its icon. Create account with an email that already has an
    account: Email is outlined with the message. Forgot password?: the round Back over the title. Signed in without a
    household: "Welcome, Sam" as the title with a round ⋮ (Sign out, Delete account), Create and Join on white cards
    with visible fields; send a join request: the waiting card is green with Cancel request. Take the app tour: the
    card has rounded corners and a round Next. Update required (raise minSupportedBuild on the emulator): the update
    icon in a leaf and Get the update. Larger text at 2×: Welcome scrolls with its buttons at the end, nothing is cut
    off. Dark mode and Fiery Palette: the same in their colors.
76. The new look, step 8 on the iPhone (web, after v1, 2026-11-17, DESIGN 18.2, 19.12): flow 75 in Safari and the Home
    Screen app on an iPhone, without the tour and Update required (the web has neither as a screen), with VoiceOver
    instead of TalkBack: in Safari, Welcome's line about adding the app to the Home Screen sits under the two buttons;
    a wrong password puts the cursor in Password and VoiceOver reads "Password, Email or password is incorrect.";
    the gate's ⋮ is a round button. Settings > Appearance: Week starts on's box shows on the card. Buttons such as Copy
    and Share on Household and Resend email on Settings have bold labels.
77. Pantry's search (Android, after v1, 2026-11-17, DESIGN 6.4): type a few letters in Pantry's search; the rows
    change at once with each letter, without fading or sliding, and nothing keeps moving once you stop. Clear the
    search: every row is back at once and none is highlighted. Pick a filter chip, then a sort from the menu: the same.
    Mark an item used up: its row still fades out. With a second phone, change an item while the search is empty and
    again while searching: the row is highlighted both times.
78. Pantry's search on the iPhone web app and switching grocery lists (both apps, after v1, 2026-11-18, DESIGN 6.4):
    on the iPhone, Pantry: search, clear the search, pick a filter and a sort; no row is highlighted. With a second
    phone, change an item while the search is empty and again while searching: the row is highlighted both times. On
    each app, Groceries with two lists that both have items: switch lists back and forth; no row is highlighted and no
    "… added …" message shows. Add an item to the open list on the second phone: it is highlighted.

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
| Cold start | Swipe the app away, wait 10 s, open it, time from the tap until Home shows its cards (not the splash, not the grey placeholders) | Under 2.5 s on a mid-range phone |
| Scrolling | Scroll a long pantry quickly | Smooth (60 fps); no stutter after the first pass |
| Initial sync | Wi-Fi off. Sign out, sign in on mobile data (4G), time from the Sign in tap until Home shows its cards | Under 2 s for a typical household (DESIGN 13.1; this check decides whether that budget stays) |

Measured on the Pixel 8 emulator (2026-10-18): cold start 0.2 to 0.4 s to first frame (release build); 500
batches scroll at 17 to 19 ms per frame with 1.7% janky frames once warm (23% on the very first pass); a fresh
sign-in with about 750 documents under LTE speed and 3G latency took 4.3 s including sign-in.
Again on 2026-11-13, headless on the Mac's GPU (RUNBOOK "Benchmark setup"), dev benchmark build with the profile: cold
start 0.36 s to the first frame and 0.71 s until Home shows its cards (medians of 10); the first Pantry scroll's
frame CPU time P50 4.7 ms, P99 21 ms. These are emulator numbers; the phone check is what counts.
Again on 2026-11-17, after the new look: cold start 0.36 s to the first frame and 0.76 s until Home shows its cards;
the first Pantry scroll P50 4.8 ms, P99 8.0 ms, no janky frames; every other screen in DESIGN 13.1. On a phone, check
especially the first scroll of Plan after installing (open in 13.1), and flow 77.

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
