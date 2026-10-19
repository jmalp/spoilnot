# Spoil Not: Product and Technical Design Document

Version 1.2 · October 8, 2026 · Android only · Written for Claude Code to build from

## Summary for non-technical readers

**What Spoil Not is.** An Android app that helps a family or a group of friends stop wasting food. You record the food you have at home, and the app warns you before it expires so you can use it in time.

**What it does**

- **Pantry:** a list of everything you have, with how many, where it is stored (fridge, freezer, pantry) and when it expires.
- **Reminders:** a daily notification on your phone about food that expires in 3, 2 or 1 days, expires today, or has gone bad and should be thrown away.
- **Grocery list:** a shared shopping list. Add things yourself, and the app adds an item for you when you are running low (you choose the minimum for each product). After shopping, the checked items move into the pantry in one step.
- **Recipes:** save recipes from websites with one tap, cook from the app, and send a recipe's ingredients to the grocery list.
- **Households:** one person creates a household and gets a code. Others enter the code and the owner approves them. Everyone then shares the same pantry, grocery lists and recipes, and a change on one phone shows up on the others within about a second.

**How it is built, in plain terms.** It is an Android-only app, shared with your testers through Firebase App Distribution. The shared information is stored online with Google's Firebase service on its free plan, and the app keeps working with a weak connection and catches up when the connection returns. The daily reminders are sent by a small free scheduled program. There are no ads, and people outside your household cannot see your data.

**What it costs.** Nothing to run at this size.

**Not in the first version.** Barcode scanning, camera reading of expiry dates, a meal planner and an iPhone version.

**How to read the rest.** The rest of the document is technical and written for Claude Code, the AI that will build the app. Sections 1 to 5 describe the product in plain terms, and section 15 lists the decisions that have been made.

## 0. How to use this document

This is the single source of truth for building Spoil Not v1. It is written for Claude Code (the builder) and for the human who owns the project.

- **MUST** is a requirement, **SHOULD** is a strong default, **MAY** is optional.
- Read the whole document before writing code, then build milestone by milestone (section 14). Do not start a milestone until the previous one meets its acceptance criteria.
- Some steps only the human can do: creating accounts, pasting keys, paying fees. They are listed in section 3. When you reach one, stop and ask the human for it with exact step-by-step instructions. Never invent credentials and never commit secrets.
- Vendor facts (prices, free quotas, package status) were verified on October 7, 2026 and are listed with sources in Appendix C. Use the latest stable versions of the Android Gradle Plugin, Kotlin, Jetpack Compose and the Firebase BoM at build time, and re-verify anything that affects cost or security.
- Record every deviation from this document in `docs/DECISIONS.md` (date, decision, reason).
- **After v1.** Milestones M0 to M7 are complete (October 18, 2026). The document is now kept up to date with the app: every change updates the sections it affects and adds a row to section 17, and every decision still goes in `docs/DECISIONS.md`. Changes keep the same bar as the milestones: tests, checks and the other documents (runbook, QA script, privacy summary, security review) updated in the same pull request.

## 1. Product overview

**Pitch.** Spoil Not is a shared household app that tracks the food you own, warns you before it expires, stores your recipes, and builds your grocery list for you.

**The problem.** Food gets forgotten and thrown away, household members cannot see what the others bought, and shopping lists are disconnected from what is actually at home.

**Users.** Families and friends sharing a household (typically 2 to 6 people) on Android phones. Distribution is private, to people the owner knows. It is not a public store release.

**The five pillars**

1. **Pantry:** everything edible you own, with quantity, location and expiration date.
2. **Reminders:** notifications 3, 2 and 1 days before an item expires, on the day, and when it is no longer good.
3. **Grocery list:** shared and live, with items added by hand or automatically when stock runs low.
4. **Recipes:** save a recipe from any recipe website in one tap, cook from the app, send ingredients to the grocery list.
5. **Households:** create one, or join with a code and the owner's approval. Everyone sees changes within about a second.

**Goals for v1**

- All five pillars in one native Android app.
- Two people in one household see each other's changes almost instantly (target: under 2 seconds on a normal connection).
- Usable on a weak connection (grocery stores); changes sync when the connection returns.
- Backend running cost of $0 at family-and-friends scale.
- A sleek, modern Material 3 interface with light and dark themes.

**Non-goals for v1**

- Public store release, ads, in-app purchases, or an iPhone version (the app is Android only).
- Barcode scanning, camera reading of expiry dates, meal planner, receipt scanning (you chose core only; see the roadmap in section 16).
- Push notifications about household activity (live in-app updates only).
- More than one household per user, a web app, voice assistants, nutrition tracking.

**Success criteria**

- A new user can install, create a household, add 5 pantry items and receive the first expiry notification within 10 minutes of setup.
- An item added on phone A appears on phone B in under 2 seconds (median) with both online.
- Recipe import works on at least 9 of 10 popular recipe sites tested (section 11).
- Nobody outside a household can read or change its data, proven by rules tests (section 12).

## 2. Decisions and assumptions

Rows marked **You** are decisions you made. The rest are my defaults: change any of them before building starts.

| Topic | Decision | Basis |
| --- | --- | --- |
| Audience | Family and friends only | You |
| Platform | Android only (no iOS version) | You |
| Application ID | `com.parcha.spoilnot` (dev builds: `com.parcha.spoilnot.dev`) | You |
| v1 scope | Core only: pantry, reminders, grocery list, recipes, households | You |
| App framework | Native Android: Kotlin with Jetpack Compose and Material 3 | Android only, so no cross-platform layer is needed; best Material 3 fidelity; first-party Firebase and Android APIs |
| Backend | Firebase free Spark plan: Authentication, Firestore, Cloud Messaging, Crashlytics | Real-time sync and offline built in, no server to run, $0 |
| Server code | One small scheduled Node.js job that sends expiry reminders, nothing else | Cloud Functions need the paid Blaze plan |
| Sign-in | Email and password; no social login in v1 | No OAuth or SHA-1 fingerprint setup; Google sign-in is on the roadmap |
| Households | One per user. Joining needs the code and the owner's approval. The owner can pause or rotate the code and remove members | You |
| Reminders | One server-sent digest per person per day at a chosen hour (default 9 AM) covering 3, 2, 1, 0 days and expired | Same for every member and works with the app closed; you chose this over the on-device option in 10.6 |
| Offline | Offline-first; writes queue and sync later | Grocery stores have bad signal |
| Item model | Product (what it is) plus stock batches (each purchase with its own expiry date) | Real life: three yogurts, three dates |
| Date types | Each batch is Use by (safety) or Best before (quality) | Changes the warning wording |
| Language, units | English with strings externalized; no unit conversion in v1 | Keeps v1 small |
| Telemetry | Crash reports only, with an off switch; no analytics, no ads | Privacy |
| Distribution | Firebase App Distribution (free) | You |
| Source code | Private GitHub repository on the free plan | You |

## 3. What the human must provide, and what it costs

| # | Item | Needed by | Cost |
| --- | --- | --- | --- |
| 1 | A Google account to own two Firebase projects, `spoilnot-dev` and `spoilnot-prod`, both on the free Spark plan (no card needed) | M0 | $0 |
| 2 | A private GitHub repository (code, CI, reminder job) | M0 | $0 |
| 3 | Android Studio (free) with the Android SDK, plus an Android phone or the emulator | M0 | $0 |
| 4 | The permanent application ID `com.parcha.spoilnot` (decided; it cannot change after release) | M0 | $0 |
| 5 | The email addresses of your testers (Firebase App Distribution invites them by email) | M6 | $0 |
| 6 | Approval of the app name, icon and colors (Claude Code can propose a first icon) | M6 | $0 |

**Running cost.** $0. Hosting, reminders and tester distribution all run on free plans.

**How the free tier behaves.** The Spark plan has no payment method attached, so it cannot generate a bill. Firestore's free daily quota is 50,000 reads, 20,000 writes and 20,000 deletes, plus 1 GiB stored and 10 GiB per month of outbound data. If a quota runs out, that product stops working until the quota resets instead of charging you. The app MUST handle this gracefully (section 7.5), and section 9.7 shows that expected usage is well below the limits.

**Do not do any of these**, because each silently moves the project to the paid Blaze plan: linking a Cloud Billing account, using Google Cloud services such as Cloud Run or Pub/Sub in the same project, deploying Cloud Functions.

## 4. Concepts

| Term | Meaning |
| --- | --- |
| Household | A group of people sharing one pantry, grocery lists and recipe box |
| Owner, member | The owner created the household and controls the invite code and members; everyone else is a member |
| Product | A kind of food in the household catalog, such as Milk. Holds its unit, category and minimum quantity |
| Stock batch | A quantity of one product with one expiration date and one location, for example 2 cartons of milk expiring Oct 12 in the fridge. A product can have many batches |
| Date type | Use by (safety date) or Best before (quality date) |
| Status | Derived from the expiration date: expired, today, tomorrow, soon (2 to 3 days), week (4 to 7 days), fresh, no date |
| Keep at least | Per-product minimum. When total stock falls below it, the product goes on the grocery list |
| Digest | The daily notification that summarizes what needs attention |

## 5. Feature specifications

### 5.1 Accounts and households

**Sign-up and sign-in.** Email and password. Sign-up asks for display name, email and password (minimum 8 characters) and sends a verification email; verification is encouraged but not required. Password reset by email. Sign-out. **Delete account** in Settings (Google Play requires it for apps with accounts, and it is good practice even for a private app): removes the profile and device tokens, leaves the household and deletes the sign-in (recent re-authentication may be required). An owner must first transfer ownership or delete the household.

**Household gate.** After sign-in, a user with no household sees two large cards on the main screen: **Create a household** (name field) and **Join with a code** (10-character field, auto-uppercase, hyphen after 5 characters, paste supported). Nothing else is reachable until one is done.

**Create.** Generates a 10-character invite code from the alphabet `ABCDEFGHJKLMNPQRSTUVWXYZ23456789` (no I, O, 0, 1) with a cryptographically secure random source (`java.security.SecureRandom`). The household, owner membership, invite code, default grocery list and the user's `householdId` are written together in batched writes.

**Join.** The app looks up the code and asks “Send a request to join *The Smiths*?” showing the household name. On confirm it sends a join request and shows “Waiting for the owner to approve”, which updates by itself when the owner decides: approved means the user is in the household, declined shows “The owner declined your request.” The user can cancel a waiting request, has one waiting request at a time, and unanswered requests are removed after 7 days. A wrong or paused code shows: “That code isn't active. Ask the household owner for a new one.”

**Household settings** (Settings > Household)

- Member list with names, roles and join dates.
- Invite card: the code in large type with Copy and Share (system share sheet with a ready-made message). Every member can see and share the code. The owner also gets Regenerate and Pause or Resume joining.
- Owner only: approve or decline join requests (a badge on the Settings avatar and a Join requests card on Home show when someone is waiting; there is no push notification for this in v1), rename household, remove a member, transfer ownership, delete household. Members: leave household.

**Rules.** A user belongs to one household at a time; joining another requires leaving the first, with a confirmation that explains the local copy is cleared. The owner cannot leave without transferring ownership or deleting the household. A removed member is returned to the household gate within seconds while the app is open (membership watcher, section 9.1).

### 5.2 Pantry

**List**

- One row per product that has stock, plus every **staple** (a product with Keep at least set), which stays visible at 0 with an “Out” badge.
- A row shows name, total quantity and unit, a status chip for the soonest expiry (color plus icon plus text, such as “Tomorrow”), and the location icon. Tapping expands the row into its batches (quantity, date, location, date type).
- **Sort:** soonest expiry (default), name, recently added. **Group by:** none (default), location, category.
- **Filter chips:** All, Fridge, Freezer, Pantry, Other, Expiring soon (within 3 days), Expired, Low stock. A search bar filters by name as you type.
- Empty state: one sentence explaining the app and a button, “Add your first item”.

**Add item** (extended FAB; a bottom sheet that grows to full screen)

| Field | Behavior |
| --- | --- |
| Name | Required. Autocompletes from existing products (case-insensitive). Choosing one fills unit, location, category and typical shelf life |
| Quantity | Required. Stepper plus keyboard, decimals allowed, default 1 |
| Unit | pcs, g, kg, oz, lb, ml, l, fl oz. Default pcs. Fixed once a product has stock (no conversion in v1) |
| Expiration date | Date picker plus quick chips: +3 days, +1 week, +2 weeks, +1 month, No expiry. Pre-filled from the product's typical shelf life when known |
| Date type | Segmented control: Use by or Best before. Default Use by |
| Location | Fridge, Freezer, Pantry, Other |
| Category | Produce, Dairy and eggs, Meat and fish, Bakery, Frozen, Pantry staples, Drinks, Snacks, Other |
| Notes | Optional, up to 200 characters |
| Keep at least (advanced) | Optional number. Helper text: “Add to the grocery list when fewer than this remain” |
| Add to grocery list automatically (advanced) | Toggle, enabled only when Keep at least is set. Optional buy quantity |

Saving writes a batch. If a batch for the same product, date and location already exists, its quantity is incremented instead of creating a duplicate.

**Batch actions** (swipe or row menu; every swipe action MUST have a menu equivalent): −1 and +1, **Used up** (deletes the batch), **Throw away** (asks a reason: Expired, Spoiled, Didn't like it, Other; deletes the batch and logs the event), Edit, Move location. Every destructive action shows an Undo snackbar for 8 seconds.

**Product detail** (tap the product name): totals, all batches, product settings (unit, category, default location, Keep at least, automatic grocery add, buy quantity), rename, typical shelf life (editable) and the last 20 events.

**Learning shelf life.** When a batch is saved, set the product's `typicalShelfLifeDays` to the median of (expiry date minus date added) over its last 5 batches. It is only used to pre-fill the date.

**Opened (after v1).** A sealed batch's ⋮ menu has *Mark opened*: "Use within" 1 day, 3 days, 5 days, 1 week, 2 weeks or 1 month (and the product's remembered number if it is another), starting from what was chosen for that product last time (`openedShelfLifeDays`), else 1 week. The batch's date becomes today plus that, but never later than the date it has, and the sheet shows the result. For a batch kept in pieces with more than one, it asks how many were opened (default 1) and moves those to their own batch. Opened batches show "Opened" with an icon (and ", opened" to screen readers), no longer offer Mark opened, and new stock is not added to them. Undo puts the batch back.

**Status rules.** With `days = expiryDate - today` in the user's local calendar:

| days | Status | Use by wording | Best before wording |
| --- | --- | --- | --- |
| below 0 | Expired | Expired N days ago. Throw it away | Past its best before date. Check before using |
| 0 | Today | Expires today | Best before today |
| 1 | Tomorrow | Expires tomorrow | Best before tomorrow |
| 2 to 3 | Soon | Expires in N days | Best before in N days |
| 4 to 7 | Week | In N days | In N days |
| above 7 | Fresh | Date only | Date only |
| no date | No date | No expiry date | No expiry date |

### 5.3 Grocery lists and automatic restocking

**Lists.** Each household has a default list named “Groceries”. Users can add more lists (for example “Costco”), rename and delete them. Low-stock items always go to the default list.

**Items.** Name (required), optional quantity and unit, note, category (used to group by aisle), checked state, who added it and when, and a source badge (Manual, Low stock, Recipe). Adding a name that is already on the list unchecked shows “Already on the list” with Increase quantity or Add anyway.

**Interactions.** A quick-add field stays at the top; typing suggests pantry products and previously used names. Tap to check (strike-through; the item moves to a collapsed In cart section). Swipe to delete with Undo. Items are sorted by category, then time added. Clear checked removes everything in the cart.

**Done shopping, then Pantry.** When at least one item is checked, an **Add to pantry** button appears. It opens a sheet with one row per checked item: name, quantity, unit, expiry quick chips (pre-filled from typical shelf life) and location. Confirming creates the stock batches, creates products that do not exist yet, and removes those items from the list in a single batched write. Skip removes an item without adding stock.

**Keep at least and automatic add.** Each product has *Keep at least N* and *Add to grocery list automatically*. When total stock drops below N, the product is added once to the default list as a Low stock item with quantity equal to the buy quantity (default: N minus current stock, minimum 1). The state machine is in section 9.6. Guarantees:

- It is added exactly once per dip, even if two phones notice at the same moment.
- If someone deletes the auto-added item, it is not added again until stock has recovered to N or more and then dropped again.
- When stock recovers while the item is still unchecked, the item stays but shows “Back in stock” with a one-tap remove.

### 5.4 Expiry reminders (behavior)

The system design is in section 10. Behavior:

- Each member gets **one notification per day** at the hour they choose (default 9 AM local), and only if something needs attention.
- It groups items by stage: **Throw away** (expired and not yet reported), **Today**, **Tomorrow**, **In 2 to 3 days**. Each of the stages 3, 2, 1 and 0 days can be switched off. Best-before items use softer wording (“past best before”).
- Expired items are reported once by default. Setting: Expired reminder = once, every day until removed, or never.
- Tapping the notification opens the Pantry filtered to Expiring soon.
- Permission is requested after the first dated item is saved, after a short explanation screen. If denied, Settings shows a banner with a button that opens the system settings.
- Settings has **Send test notification**, which fires a local notification to verify permission and channel.

Example for three items. Title: “3 items need attention”. Body: “Throw away: Milk. Tomorrow: Yogurt, Eggs.”

### 5.5 Recipes

**Recipe box.** Cards with image, title, total time and a favorite star. Search by title, ingredient and tag. Chips: All, Favorites, and user tags. Sort: recent, A to Z.

**Add a recipe** (FAB with three choices)

1. **Import from the web:** paste a link, or open the built-in browser.
2. **From the share sheet:** in any browser or app, Share, then Spoil Not. The app opens on a preview of the extracted recipe with a Save button.
3. **Create manually:** title, optional image URL, servings, times, ingredients (one per line), steps (one per line), notes, tags.

**Built-in browser.** A web view with an address and search bar. After every page load the app checks for a recipe. When one is found, a bottom bar slides up: “Recipe found: *Title*. Save”. This is the Paprika-style experience you described; details are in section 11.

**Recipe detail.** Hero image, title, chips (time, servings, source site), a servings stepper that scales the quantities that could be parsed, an ingredient checklist, numbered steps, notes, an Open original link, and the actions Favorite, Edit, Share and Delete.

**Cook mode.** The screen stays awake, large type, one step at a time with swipe, ingredients in a bottom sheet.

**Add to grocery list.** Choose all or some ingredients. Ones that look like pantry items are pre-unchecked and tagged “In pantry” (simple name matching, section 11.5). Added items carry the source Recipe.

After v1, each ingredient is added as something you would buy, and the sheet shows it under the line ("Adds as Onions × 3 lb"):

- **Shopping name.** Size, preparation and freshness words are dropped ("1 extra-large onion, finely diced" becomes "Onion"), as is anything after a comma, in parentheses, after "or", and phrases such as "to taste" or "for garnish". Words that change what is bought stay (ground, whole, frozen, baby, hot).
- **Linked to the household's product.** If a product means the same thing, in stock or not, the item takes its name, unit and category and its product link, so Add to pantry adds to that product instead of creating a near-duplicate. A product matches when its words are all in the ingredient with at most one more ("Black pepper" for "ground black pepper"); the most shared words win. A product with more words than the ingredient never matches ("Green onions" is not "onion"). An unchecked item already on the list for the same product counts as already on the list.
- **Shopping amount.** Recipe measures (teaspoons, cups, grams) are never the item's amount. A linked product brings its usual amount: its Buy quantity, or how much was added last time (`lastAddedQuantity`). Otherwise only a count is kept ("3 eggs", "2 cans tomatoes").
- **Note.** The recipe and its line ("Pancakes: 1 extra large onion, diced"). Lines that become the same item are added once, with their notes joined and counts added up.
- **Change.** Each line has a Change button that opens "Add to the list as": a name field with the household's products that contain what is typed. Picking a product uses its name and usual amount.
- **I cooked this (after v1).** Next to Add to grocery list, *I cooked this* opens "What did you use?": every in-stock product the recipe uses (matched as above), with how much the household has and an amount at the detail screen's servings. Lines the pantry can count start checked with the recipe's amount (a count for a product kept in pieces, or the same unit: "500 g rice" from rice in grams); measures it cannot count ("2 tbsp soy sauce" from a bottle) start unchecked with the whole stock, so checking means "finished it". Amounts can be changed. The amounts come off the batches that expire first; a batch taken to 0 is used up and counted for Insights (5.7). Recent activity shows "Sam cooked Omelette". One Undo puts everything back.
- **Learning.** A change teaches the household's app the recipe's name for that product (`aliases`, name keys, at most 30 per product). A change to an existing product is remembered when the items are added; one to a new name rides on the grocery item (`aliasOf`) and is remembered when Add to pantry creates or finds the product. From then on that ingredient links to the product by itself, before any word matching. Changing a line back to the recipe's own name teaches nothing.

**Duplicates.** Saving a URL that is already in the box shows “Already saved” with Open or Save a copy.

### 5.6 Home, activity and settings

**Home** (first tab) has five cards: *Needs attention* (expired and expiring within 3 days; opens the filtered Pantry), after v1 *Use it up* (below), *Low stock* (products below Keep at least), *Grocery list* (item count; opens the list), *Your kitchen this month* (after v1, 5.7; opens Insights) and *Recent activity* (last 10 events such as “Sam added Eggs to Groceries”). The household owner also sees a *Join requests* card while someone is waiting for approval.

**Use it up (after v1).** Up to three saved recipes that use food expiring today or within 3 days (not expired), with what each uses and when ("Uses Spinach (Tomorrow), Yogurt (3 days)"); tapping one opens the recipe. Recipes using the most expiring products come first, then the one using the soonest, then favorites. Ingredients are matched to products the same way as Add to grocery list (5.5: shopping name, remembered names, word match). The card shows only when there is a match. Home therefore reads the recipes on every open (with the changes-only listener, 9.8), not only after the first visit to Recipes.

**Activity log.** Clients write events for: pantry item added, grocery item added or checked, recipe saved, member joined, items thrown away (with reason), and after v1 a recipe cooked. Events older than 30 days are pruned by the reminder job.

**Settings.** Account, Household, Notifications, Appearance (System, Light or Dark; Colors: Wallpaper on Android 12 and later, Spoil Not green or a preset theme (6.4); metric or imperial defaults for new products), Data (Export household data as JSON), About (version, licenses, privacy summary), and a Crash reports toggle.

### 5.7 Insights (after v1)

How the household is doing at using food before it spoils, shown so it feels good to look at rather than as a scolding.

- **What is counted.** A batch (one purchase) counts once when it is finished: **used** when it is used up (swipe right, Used up, Use 1 or an edit down to 0) and **thrown away** with its reason. Using part of a batch counts only when it is finished. Counts are of batches, not quantities, because units differ. Undo takes the count back off the same month. Counting starts with the version that added it; nothing earlier is filled in.
- **Home card** *Your kitchen this month*: a small ring with the share used (solid for used, dashed for thrown away), the counts ("11 used · 1 thrown away"), a friendly line by share (90% or more "Amazing! Almost nothing went to waste.", 75% "Nice work!", 50% "Good start.", below that an encouraging line pointing to Needs attention) and *See insights*. With nothing finished yet, it explains what it will show.
- **Insights screen:** this month's ring with a legend; the last 6 months as bars (solid used, striped thrown away, the number above each bar); the best month (highest share used, at least 5 batches, when two months qualify); the streak (days since anything was thrown away, or days of counting with nothing thrown away); why things were thrown away (striped bars by reason); a note on what is counted.
- **Accessibility.** Thrown away is never shown by color alone: dashed in the ring, striped in bars, always with a text legend and numbers. Each chart has a spoken summary. Charts grow in over 700 ms, or appear at once with reduce motion on. Colors come from the theme (primary for used, tertiary for thrown away).
- **Months** follow the phone's local date.

## 6. UX and visual design

**6.1 Principles.** Glanceable (what needs attention comes first), fast capture (adding an item takes under 10 seconds with thumb-reachable controls), calm (color is reserved for status), trustworthy (Undo on every destructive action, visible sync state).

**6.2 Navigation.** A bottom `NavigationBar` with four destinations: Home, Pantry, Recipes, Groceries. Settings opens from the avatar button in each tab's top app bar. Each tab keeps its own back stack (Navigation 3) so scroll position and sub-screens survive tab switches. Portrait-first; landscape and tablets MUST NOT break (use window size classes, center content, maximum width 640 dp).

**6.3 Screens**

| Screen | Key elements |
| --- | --- |
| Welcome, Sign in, Sign up | Logo, email, password, display name (sign-up), reset link |
| Household gate | Create card, Join card |
| Home | Four cards (5.6) |
| Pantry | Search bar, filter chips, grouped list, extended FAB |
| Add or edit batch | Bottom sheet form (5.2) |
| Product detail | Totals, batches, settings, history |
| Groceries | List switcher, quick-add field, grouped items, Add to pantry button |
| Add to pantry sheet | One row per checked item |
| Recipes, detail, cook mode, edit | See 5.5 |
| Import: paste, browser, preview | See section 11 |
| Settings and sub-pages | Account, Household (members, invite), Notifications, Appearance, Data, About |

**6.4 Design tokens**

**Color.** Build the light and dark `ColorScheme`s from the seed `#2E7D5B` (leafy green) with the Material Theme Builder. On Android 12 and later, dynamic color (Material You, through `dynamicLightColorScheme` and `dynamicDarkColorScheme`) is an Appearance option, on by default.

**Color themes (after v1).** Settings > Appearance > Colors offers *Wallpaper* (Material You, Android 12 and later, the default), *Spoil Not green* and five presets from the owner's Coolors palettes: *Fiery Palette*, *Summer Fun*, *Enchanted Forest*, *Watermelon Sorbet* and *Contrast Pop*. Each preset names four palette colors (main, second, accent, background tint) in `tools/themes/themes.json`; `tools/themes/generate-themes.mjs` builds the light and dark schemes with Material Color Utilities (fidelity variant) into `PresetColors.kt`. `ColorThemesContrastTest` checks every fixed theme: text 4.5:1 on its background, chart and icon colors 3:1, and the status colors on its surfaces. The choice is stored on the phone; the picker shows each theme as a swatch with its name and marks the choice with a ring and a check. Older phones keep their choice (the old switch off means Spoil Not green).

Status colors are fixed and are not taken from the dynamic scheme or any theme, so they stay recognizable:

| Status | Light | Dark | Icon |
| --- | --- | --- | --- |
| Fresh | #2E7D32 | #81C784 | check\_circle |
| Soon (2 to 3 days) | #B26A00 | #FFB74D | schedule |
| Today or tomorrow | #C2410C | #FF8A65 | warning\_amber |
| Expired | #BA1A1A | #FFB4AB | delete\_outline |
| No date | on-surface-variant | on-surface-variant | remove |

Status MUST never rely on color alone: always icon plus text. Text contrast at least 4.5:1.

**Type.** Material 3 type scale, one bundled variable font (Manrope or Inter; no runtime font downloads), large top app bars on the main tabs, body text at least 14 sp, honor the system font scale up to 200%.

**Shape and surface.** M3 defaults: 16 dp cards, fully rounded chips and buttons, 28 dp bottom sheets, tonal surface levels instead of heavy shadows.

**Motion.** M3 motion: shared-axis transition between tabs, container transform from a row to its detail, a spring on check-off, 200 to 300 ms standard easing elsewhere. Remote changes pulse the affected row with a tonal highlight for 1.5 s. A remote change MUST NOT move the list under the user's finger during a scroll or drag; queue it until the gesture ends. Respect the system reduce-motion setting.

**Icons and haptics.** Material Symbols (rounded). Light haptic on check-off, medium on delete.

**App icon.** "Fresh jar": a cream (#F5FBF5) jar with a leaf cut out of it on the brand green (#2E7D5B). The same shape is the monochrome (themed icon) layer, the notification icon and the welcome screen's mark.

**6.5 Component patterns.** `SearchBar`, horizontally scrolling `FilterChip` rows, filled tonal `Card`s, list rows with a leading status icon, `SwipeToDismissBox` rows with background actions, `ModalBottomSheet` with a drag handle, snackbars with Undo, `SingleChoiceSegmentedButtonRow`, skeleton shimmer on first load only, and an `ExtendedFloatingActionButton` that collapses on scroll. Use `Scaffold` with edge-to-edge insets, support predictive back, and provide an adaptive launcher icon with a monochrome layer (themed icons).

**Form sheets** (Add item, Edit, Add to pantry, Edit grocery item, Add to grocery list) close only when dragged past half their height; a quick flick never closes them, and Back or tapping outside still does. They stop below the status bar and pad only the bottom (navigation bar and keyboard), so their height never changes while they move.

**6.6 Wireframes.** In these sketches (SK) is the avatar button that opens Settings; the row icons are ! urgent, \~ soon, ok fresh, o out of stock.

Pantry:

```
+--------------------------------+
| Pantry                    (SK) |
| [ Search pantry              ] |
| (All)(Fridge)(Freezer)(Soon)>  |
| ------------------------------ |
| ! Milk                    x2   |
|   Tomorrow . Fridge            |
| ~ Yogurt                  x4   |
|   In 3 days . Fridge           |
| ok Rice                  1 kg  |
|   Mar 2027 . Pantry            |
| o  Olive oil  OUT (staple)     |
|                      [+ Add]   |
| Home  Pantry  Recipes  Lists   |
+--------------------------------+
```

Groceries:

```
+--------------------------------+
| Groceries  v              (SK) |
| [ Add an item...          (+)] |
| DAIRY                          |
| [ ] Milk  x2     LOW STOCK     |
| [ ] Eggs  x12    added by Sam  |
| PRODUCE                        |
| [ ] Spinach                    |
| IN CART (2)  v                 |
|     [ Add 2 to pantry ]        |
|                                |
| Home  Pantry  Recipes  Lists   |
+--------------------------------+
```

**6.7 States.** Loading: skeleton on first load only. Empty: a one-line explanation and a primary action. Error: inline message with Retry. Offline: a thin banner “Offline. Changes will sync later” and a cloud-off icon on rows with pending writes. Quota reached: banner “Spoil Not has reached its free daily limit and will work again tomorrow.” Update required: full screen with a link.

**6.8 Accessibility.** Screen reader labels read status in words (“Milk, 2, expires tomorrow, fridge”); 48 dp touch targets; every swipe action also in a menu; logical focus order; contrast checked in both themes; layouts tested at 200% font scale.

**6.9 Copy.** Short, friendly, plain, sentence case. Errors say what happened and what to do. All strings live in Android string resources (strings.xml, English now) so translation is possible later.

## 7. Architecture

**7.1 System overview**

```
Android phones (Kotlin, Jetpack Compose)  <---- push notifications (FCM) ----+
   |  sign in, read and write (live listeners, offline cache)                |
   v                                                                         |
Firebase free Spark plan: Auth | Firestore | Cloud Messaging | Crashlytics   |
   ^                                                                         |
   |  Admin SDK (reads Firestore, asks FCM to send)                          |
   +------------ Reminder job (Node.js, GitHub Actions, hourly) -------------+
```

No other servers exist. The only backend code is the reminder job.

**7.2 Client structure** (feature-first, layered)

```
app/src/main/
  AndroidManifest.xml
  kotlin/<package>/
    SpoilNotApp.kt        Application class: Firebase init, notification channel, Hilt entry point
    MainActivity.kt       single Activity: edge-to-edge, share and notification intents
    core/                 clock, ids, error types, Firestore helpers, shared composables, theme
    feature/<feature>/    auth, household, pantry, grocery, recipes, notifications, settings, home
      domain/             pure Kotlin: models, enums, business logic (no Android, no Firebase)
      data/               repositories and Firestore mapping
      ui/                 screens, composables, ViewModels (state exposed as StateFlow)
  res/                    strings.xml (English), themes, fonts, icons
app/src/test/             JVM unit tests
app/src/androidTest/      instrumented and Compose UI tests
```

**Layering rules (MUST).** Composables and ViewModels never use Firebase classes such as `FirebaseFirestore` or `FirebaseAuth`; they depend on repository interfaces declared in `domain/`. Only the implementations in `data/` touch Firestore. Business logic (status calculation, restock engine, shelf-life learning, recipe and ingredient parsing, invite-code generation) is pure Kotlin with unit tests and an injectable `java.time.Clock`. A future backend swap then stays inside `data/`.

**7.3 Libraries.** Use the latest stable versions through a Gradle version catalog and confirm each library is maintained at build time.

- Firebase: the Firebase Android BoM with `firebase-auth`, `firebase-firestore`, `firebase-messaging`, `firebase-crashlytics` and, later, `firebase-appcheck-playintegrity`. Use the main modules; the separate `-ktx` artifacts were removed from the BoM in version 34.
- App: Kotlin, Jetpack Compose through the Compose BOM with `androidx.compose.material3`, Navigation 3, Hilt, Kotlin coroutines and Flow, `androidx.datastore` (local settings such as theme), `androidx.core:core-splashscreen`, Coil (images), Timber (debug logging).
- Features: OkHttp and Jsoup (fetch and parse recipe pages), `kotlinx.serialization` (JSON-LD), `WebView` hosted in `AndroidView` (built-in browser), `androidx.browser` Custom Tabs (Open original), a manifest intent filter for the share sheet, and `WorkManager` only if the on-device reminder alternative in 10.6 is chosen.
- Testing: JUnit, MockK, Turbine, the Compose UI test library, a screenshot-testing library (Roborazzi or Paparazzi; confirm it is maintained), the Firebase Emulator Suite.

**Material 3 note.** Use the stable `androidx.compose.material3` through the Compose BOM (the 1.4 line was stable in August 2026; see Appendix C). Adopt Material 3 Expressive components and motion only where the APIs are stable, meaning not annotated as experimental, and use standard Material 3 otherwise.

**7.4 Environments.** Two Firebase projects: `spoilnot-dev` and `spoilnot-prod`. Gradle product flavors `dev` and `prod` with different application IDs (an `applicationIdSuffix` of `.dev`, so both install side by side: com.parcha.spoilnot for prod and com.parcha.spoilnot.dev for dev), each with its own `google-services.json` in `app/src/dev` and `app/src/prod`. Local development and all automated tests MUST use the Firebase Emulator Suite (Auth and Firestore; the Android emulator reaches the host at `10.0.2.2`) so they never touch real data.

**7.5 Errors and logging.** Repositories throw typed domain errors (`PermissionDenied`, `QuotaExceeded`, `Offline`, `NotFound`, `Conflict`); the UI maps them to messages. Map Firestore `resource-exhausted` to `QuotaExceeded` and show the banner from 6.7. Uncaught errors go to Crashlytics when enabled. Use `Timber`, planted only in debug builds.

**7.6 Platform floors.** `minSdk` 26 (Android 8.0), or higher if a library requires it (some Firebase libraries already need API level 23 or higher). `compileSdk` and `targetSdk` at the latest stable level.

## 8. Data model (Firestore)

**8.1 Conventions**

- Firestore **Standard edition**, the `(default)` database (the free quota applies to one database per project), one region close to the users. The region cannot be changed later.
- Document IDs are Firestore auto IDs unless a deterministic ID is specified.
- `createdAt` and `updatedAt` are server timestamps. Every mutable document also has `createdBy` and `updatedBy` (uid).
- **Dates** (expiry and similar) are calendar dates stored as strings `YYYY-MM-DD` with no time zone, and compared as strings.
- `nameKey` is the name lowercased, trimmed, with whitespace collapsed and diacritics removed. It is used for matching and de-duplication.
- Limits: names up to 80 characters, notes up to 500, arrays up to 100 items, documents far below 1 MiB.
- Write every field explicitly (use null, not a missing field) unless it is documented as optional. New fields MUST be optional on read with defaults, because phones will run different versions.
- **Tombstones (after v1, 9.8).** Products, stock batches, grocery items and recipes are deleted by merging `deletedAt` (the server time) and the update stamp into the document, not by deleting it, so other phones' change listeners see the deletion. Every reader skips documents with `deletedAt`; restoring (Undo) writes the document without it. The reminder job deletes tombstones after 2 days. Deleting a whole list or household still deletes for real.

**8.2 Collections**

```yaml
users/{uid}:                          # private to the user
  displayName: string (1-40)
  householdId: string | null
  timezone: string                    # IANA name, refreshed at app start
  locale: string                      # en
  units: metric | imperial            # default for new products
  notificationPrefs:
    enabled: bool
    digestMinutes: int                # minutes after local midnight; UI offers whole hours; default 540 (9 AM)
    stages: [int]                     # subset of [3, 2, 1, 0]; default all four
    expiredMode: once | daily | never # default once
  createdAt, updatedAt
  lastDigestDate: string | null       # SERVER-WRITTEN: local date of the last digest
  lastExpiredThrough: string | null   # SERVER-WRITTEN: expired items up to this date were reported
users/{uid}/devices/{installId}:
  token: string
  platform: android
  appVersion: string
  updatedAt

inviteCodes/{CODE}:                   # CODE: 10 characters from the safe alphabet
  householdId: string
  householdName: string
  active: bool
  createdBy: uid
  createdAt

households/{hid}:
  name: string (1-60)
  ownerUid: uid
  inviteCode: string                  # current code, for display
  schemaVersion: 1
  createdAt, updatedAt
households/{hid}/members/{uid}:
  role: owner | member
  displayName: string
  joinedAt: timestamp
households/{hid}/joinRequests/{uid}:  # one per person (the ID is their uid); readable by that person and the owner
  displayName: string
  joinCode: string                    # the code they entered (checked by rules at creation)
  status: pending | approved | declined   # only the owner changes it
  requestedAt: timestamp
households/{hid}/products/{productId}:
  name, nameKey: string
  category: string
  defaultLocation: fridge | freezer | pantry | other
  unit: pcs | g | kg | oz | lb | ml | l | fl_oz
  minQuantity: number | null          # keep at least
  autoAddToGrocery: bool
  buyQuantity: number | null
  restockArmed: bool                  # restock engine state (9.6); true when created
  typicalShelfLifeDays: int | null
  lastAddedQuantity: number | null      # after v1: the amount added last time, in unit (5.5 recipe grocery amounts)
  aliases: [string]                     # after v1: recipe names taught by Change (5.5), name keys, at most 30
  openedShelfLifeDays: int | null       # after v1: days it keeps once opened, last chosen in Mark opened (5.2)
  createdAt, createdBy, updatedAt, updatedBy
households/{hid}/stock/{stockId}:     # one batch
  productId: string
  productName, unit, category: string # denormalized copies from the product
  quantity: number                    # above 0 while the batch exists
  expiryDate: 'YYYY-MM-DD' | null
  dateType: use_by | best_before
  location: fridge | freezer | pantry | other
  note: string | null
  openedOn: 'YYYY-MM-DD' | null       # after v1: when it was opened (5.2)
  createdAt, createdBy, updatedAt, updatedBy
households/{hid}/groceryLists/{listId}:
  name: string
  isDefault: bool
  createdAt, updatedAt
households/{hid}/groceryLists/{listId}/items/{itemId}:   # low-stock items use itemId = low_ + productId
  name, nameKey: string
  quantity: number | null
  unit: string | null
  note: string | null
  category: string | null
  checked: bool                       # optional: absent means false
  checkedBy: uid | null
  checkedAt: timestamp | null
  source: manual | low_stock | recipe
  productId: string | null
  recipeId: string | null
  aliasOf: string | null              # after v1: the recipe name a Change renamed it from, taught to its product (5.5)
  createdAt, createdBy, updatedAt, updatedBy
households/{hid}/recipes/{recipeId}:
  title: string
  sourceUrl, sourceName, imageUrl, description: string | null
  servings: number | null
  servingsText: string | null
  prepMinutes, cookMinutes, totalMinutes: int | null
  ingredients: [ { raw: string, qty: number|null, unit: string|null, name: string|null, group: string|null } ]
  steps: [ { text: string, group: string|null } ]
  notes: string | null
  tags: [string]
  favorite: bool
  rating: int | null
  importMethod: manual | web
  createdAt, createdBy, updatedAt, updatedBy
households/{hid}/activity/{eventId}:
  type: stock_added | grocery_added | grocery_checked | recipe_saved | member_joined | discarded | recipe_cooked (after v1)
  actorUid, actorName: string
  params: { name, quantity, reason, listName }    # whatever applies to the type
  createdAt: timestamp
households/{hid}/stats/{yyyy-MM}:     # after v1: Insights counts for one month (5.7), written with increments
  used: int                           # batches used up
  expired, spoiled, didnt_like, other: int   # batches thrown away, by reason
  thrownByDay: { "1".."31": int }     # batches thrown away per day of the month, for the streak
  updatedAt, updatedBy

config/app:                           # read-only for clients; edited in the console
  minSupportedBuild: int
  message: string
```

**8.3 Example documents**

```yaml
# households/h1/stock/s1
productId: p1
productName: Milk
unit: pcs
category: dairy
quantity: 2
expiryDate: '2026-10-12'
dateType: use_by
location: fridge
note: null
createdAt: 2026-10-06T14:02:11Z
createdBy: uidSam
updatedAt: 2026-10-06T14:02:11Z
updatedBy: uidSam

# households/h1/groceryLists/g1/items/low_p1   (written by the restock engine)
name: Milk
nameKey: milk
quantity: 2
unit: pcs
category: dairy
source: low_stock
productId: p1
createdAt: 2026-10-07T08:30:00Z
createdBy: uidAnna
updatedAt: 2026-10-07T08:30:00Z
updatedBy: uidAnna
```

**8.4 Indexes.** No composite indexes are needed in v1. Clients listen to whole household subcollections (small). The reminder job queries `stock` with a range on `expiryDate` and `users` with `notificationPrefs.enabled == true`; both use automatic single-field indexes. If a composite index is ever needed, commit it in `firestore.indexes.json`.

**8.5 Invariants (MUST)**

1. A stock batch with `quantity <= 0` is hidden by the UI and deleted (a tombstone after v1) by whichever client notices it first (idempotent).
2. `productName`, `unit` and `category` on a batch equal the product's values. Renaming or editing a product updates its batches in the same batched write (chunk at 400 operations).
3. A product's `unit` cannot change while it has batches.
4. `nameKey` is unique among a household's products. The client looks it up before creating and reuses the match.
5. Exactly one default grocery list exists per household, created with the household.
6. Deleting a household deletes all its subcollections client-side first, because Firestore does not cascade deletes.

**8.6 Versioning.** `households.schemaVersion` and `config/app.minSupportedBuild` allow forced upgrades. Migrations are idempotent scripts in `tools/migrations/`. Never assume every phone runs the same version.

## 9. Real-time sync, offline and the restock engine

**9.1 Listeners.** After sign-in and household resolution the app attaches these listeners as Kotlin `callbackFlow`s collected by ViewModels (`stateIn` with `SharingStarted.WhileSubscribed`), all cancelled on sign-out:

| Listener | Scope | Lifetime |
| --- | --- | --- |
| `users/{uid}` | own profile | session |
| `members/{uid}` | own membership (membership watcher): if it disappears the user was removed, so clear local data and return to the gate | session |
| `joinRequests/{uid}` | own join request; the waiting screen watches it for approval or decline | while waiting |
| `joinRequests` | all requests (owner only); drives the badge, the Home card and the Join requests list | session, owner only |
| household doc and `members` | name and member list | session |
| `products`, `stock` | whole collections | session |
| `groceryLists`, items | list names always; items of the open list | while visible, plus 30 s |
| `recipes` | whole collection | from the first visit to the Recipes tab |
| `activity` | newest 30 by `createdAt` | session |

Register the stock and grocery listeners with `MetadataChanges.INCLUDE` to read `hasPendingWrites` (pending-sync icon) and `isFromCache`.

**9.2 Latency and behavior.** Target: median under 1 s and p95 under 3 s end to end with both phones online. No polling and no pull-to-refresh. Remote changes animate in with a row highlight (6.4). If a remote change arrives while the user is editing the same item, keep their draft and show “Sam changed this item. Review”. When someone else adds to the list being viewed, show a 3-second snackbar “Sam added Eggs”.

**9.3 Write patterns (MUST)**

- Use batched writes and `FieldValue.increment` for the plus and minus buttons. Typed quantities use a plain field set.
- Avoid `runTransaction` in common paths: transactions fail offline.
- Always write `updatedAt` as a server timestamp and `updatedBy`.
- The Firestore cache already provides optimistic UI. Do not keep a second copy of state.

**9.4 Conflicts.** Field-level last write wins, except quantity changes (increments commute) and the restock engine (idempotent). Edit versus delete: delete wins and the editor is told the item was removed. Rare duplicate grocery names are acceptable; the Already on the list check covers the normal case.

**9.5 Offline.** Firestore persistence is on by default on mobile; set the cache size to about 100 MB (PersistentCacheSettings). The app opens and works from cache with no connection. Pending writes show a cloud-off icon. A first launch while offline cannot sign in and says so. Detect connectivity from snapshot metadata and write errors, not from ConnectivityManager alone, because it cannot tell whether Firestore is reachable.

**9.6 Restock engine** (a client service that runs on every phone)

Per-product state: `restockArmed` (true when the product is created). The engine runs on every change of the combined `products` and `stock` snapshots, debounced 300 ms, including snapshots served from cache so it works offline.

```
for each product p with p.autoAddToGrocery and p.minQuantity != null:
  total = sum of quantity over p's batches where quantity > 0
  if total >= p.minQuantity:
      if not p.restockArmed: update p.restockArmed = true        # re-arm after recovery
  else:                                                          # total < minQuantity
      if p.restockArmed:
          one batched write:
            set groceryLists/{default}/items/low_{p.id} (merge) with
                name, nameKey, unit, category, source = low_stock, productId = p.id,
                quantity = p.buyQuantity ?? max(1, p.minQuantity - total),
                updatedAt, updatedBy, and createdAt/createdBy only if the doc is not in the local cache
                (never write checked: absent means false, and an existing checked item stays checked)
            update p.restockArmed = false
```

Properties: idempotent (two phones doing it at once write identical data), respects deletion (it stays disarmed until stock recovers to the minimum), never un-checks an item, no nagging. Editing `minQuantity` upward sets `restockArmed = true`. Unit tests MUST cover: crossing down, recovery then dropping again, the user deleting the item, two concurrent evaluations, a product without a minimum, a minimum raised while already low, and batches with quantity 0.

**9.7 Quota budget.** Spark allows 50,000 reads and 20,000 writes per day.

- A listener costs one read per document it returns. With persistence on, a listener disconnected for more than 30 minutes is billed like a brand-new query, so every return to the app after a long absence re-reads the collections. Security-rule lookups (`exists`, `get`) cost one extra read per dependent document.
- Typical household: about 130 documents across listeners (30 products, 60 batches, 20 grocery items, 15 recipes, members). A phone that opens the app 8 times a day after long gaps reads about 1,000 documents; four phones, about 4,500 reads a day including rule lookups. Writes: about 40 a day.
- After v1, Home also listens to the current month's Insights document (one read per open plus its rule lookup); the Insights screen reads up to six month documents when opened. Finishing a batch writes the month document in the same batch. The quota simulation (`npm run quota-sim`, docs/RUNBOOK.md) puts a typical household at about 4,760 reads a day.
- After v1, the changes-only listeners (9.8) cut a typical household to about 1,890 reads a day: one full read per phone a day, then only changes. Home's Use it up reads recipes on every open (5.6), about 40 more, so about 1,930.
- So the free quota supports roughly 10 active households' worth of reads (about 18 after 9.8). The reminder job adds a few hundred reads a day.
- If usage nears 70% of the daily quota: first, make sure recipes and non-active grocery lists are lazy-loaded (already specified); second, use a per-session read counter on a hidden debug screen to find regressions; third, move to the Blaze plan (extra reads cost a few cents per 100,000; check the current regional price; budget alerts notify but do not cap spending).

**9.8 Changes-only listeners (after v1).** Most reads were phones re-reading every document after more than 30 minutes away. Products, stock, grocery items and recipes are now each read with two listeners (`liveDocuments` in `core/firebase/DeltaSync.kt`):

- a **cache** listener on the whole collection (free; it shows the phone's copy, its own unsent writes and whatever the other listener brings in), and
- a **server** listener on `updatedAt` after the newest cached change minus 5 minutes, which costs one read per changed document (at least one per query).

The first open of a collection, an empty cache, and any open more than 20 hours after the last full read use a full server listener instead, recorded per phone (`SyncLog`). That daily full read also drops anything kept by mistake, so tombstones (8.1) only need to outlive it. `fromCache` and the offline banner come from the server listener; the pending-sync icon from the cache. The quota simulation puts a typical household at about 1,890 reads a day (from about 4,760), so about 18 households fit under the 70% line. Older app builds would show tombstones as items, so the release that adds this raises `config/app.minSupportedBuild` (docs/RUNBOOK.md).

## 10. Notification system

**10.1 Flow**

```
Hourly (GitHub Actions) -> reminder job (Node.js + firebase-admin)
  1. load users with notificationPrefs.enabled
  2. keep users who are due: local time is at or after their digest time, before 22:00, and not yet sent today
  3. per household: query stock with expiryDate between (today - 14 days) and (today + 3 days)
  4. build each due user's digest from their own stages and local date
  5. send through FCM to the user's device tokens; delete dead tokens
  6. update users/{uid}: lastDigestDate, lastExpiredThrough
  7. prune activity older than 30 days and join requests older than 7 days
```

**10.2 Client responsibilities**

- After the first dated item is saved (or from Settings) show a short explanation, then request the notification permission (`POST_NOTIFICATIONS`, a runtime permission on Android 13 and later).
- Get the FCM token and write `users/{uid}/devices/{installId}` (token, platform, appVersion). Refresh in `FirebaseMessagingService.onNewToken` and at app start, writing only when it changed. Delete the device document on sign-out.
- Write the IANA `timezone` (from `ZoneId.systemDefault()`) to `users/{uid}` at app start when it changes.
- Create the notification channel `expiry` (importance high, name Expiry reminders) at startup.
- Tapping the notification starts `MainActivity` with the message data as intent extras; read `route` (`/pantry?filter=expiring`) in `onCreate` and `onNewIntent`. In the foreground do not show a system notification; the Home card is already live.
- Notification settings: on or off, delivery hour (whole hours), stages, expired mode. Real delivery is shortly after the chosen hour because the job runs hourly.

**10.3 Reminder job** (`backend/reminder/`, TypeScript, Node.js LTS, `firebase-admin`)

```
now = current time
due = []
for user in users where notificationPrefs.enabled == true:
  local = toLocal(now, user.timezone ?? 'UTC')        # Intl.DateTimeFormat; handles DST
  if local.minutesOfDay < prefs.digestMinutes: continue
  if local.minutesOfDay >= 22 * 60: continue          # too late; skip today
  if user.lastDigestDate == local.date: continue
  due.add(user, local)
group due by householdId
for each household:
  lo = min(local.date of its due users) - 14 days
  hi = max(local.date of its due users) + 3 days
  batches = query stock where expiryDate >= lo and expiryDate <= hi   # tombstones skipped (after v1, 9.8)
  for each due user u:
    items = []
    for b in batches:
      d = calendarDaysBetween(u.local.date, b.expiryDate)       # negative when expired
      if d in u.stages: items.add(stage = d, batch = b)         # d is 0 to 3
      if d < 0 and u.expiredMode != 'never':
        if u.expiredMode == 'daily' or b.expiryDate > (u.lastExpiredThrough ?? ''):
          items.add(stage = expired, batch = b)
    tokens = users/{u}/devices
    if tokens is empty: continue                                # try again next run
    if items is empty: update u.lastDigestDate = local.date; continue
    result = fcm.sendEach(tokens, buildDigest(items, u.locale))
    delete tokens that returned unregistered or invalid-argument errors
    if any send succeeded:
      update u { lastDigestDate: local.date, lastExpiredThrough: local.date minus 1 day }
prune activity documents older than 30 days and join requests older than 7 days (after v1 also tombstones older than 2 days, 9.8)
```

Message shape per device token:

```yaml
notification: { title: <text>, body: <text> }
data: { type: expiry_digest, route: '/pantry?filter=expiring', date: <local date> }
android: { priority: high, notification: { channelId: expiry, tag: expiry_digest } }
```

Digest text: one item gives the title “Milk expires tomorrow”; several give “N items need attention”. The body has up to four lines in this order: Throw away, Today, Tomorrow, In 2 to 3 days. Each line names up to three products, then “+K more”. Best-before items say “past best before” instead of “throw away”.

**10.4 Scheduler** (`.github/workflows/reminders.yml`)

```yaml
name: expiry-reminders
on:
  schedule:
    - cron: '7 * * * *'          # hourly, off the busy top of the hour
  workflow_dispatch: {}
concurrency:
  group: expiry-reminders
  cancel-in-progress: false
jobs:
  send:
    runs-on: ubuntu-latest
    timeout-minutes: 5
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: lts/*
          cache: npm
          cache-dependency-path: backend/reminder/package-lock.json
      - run: npm ci && npm run build && node dist/main.js
        working-directory: backend/reminder
        env:
          FIREBASE_SERVICE_ACCOUNT_JSON: ${{ secrets.FIREBASE_SERVICE_ACCOUNT_JSON }}
```

Pin the action versions to the current majors at build time.

**Reliability (verified, Appendix C).** GitHub scheduled runs are best-effort: they can start 15 to 60 minutes late at busy times and are occasionally skipped; the minimum interval is 5 minutes; in public repositories schedules are disabled after 60 days without repository activity. The design tolerates this: reminders are stateless windows (an item inside the 3-day window appears in every daily digest), the job catches up at any time before 22:00, and `lastDigestDate` prevents duplicates. The hourly job uses about 720 billed minutes per month of the 2,000 free for private repositories, so keep CI frugal (path filters, caching). Make sure Actions failure notifications are enabled in your GitHub notification settings.

**Service account.** Create a dedicated service account for the job with least privilege (Firestore read and write, and permission to send FCM messages; confirm the current role names in the Google Cloud console). Store its JSON key only as the `FIREBASE_SERVICE_ACCOUNT_JSON` repository secret. Rotate it yearly. Never commit it.

**10.5 Tests.** Pure functions with a fixed clock: time zones on both sides of UTC, DST start and end days, month and year boundaries, a missed run followed by a catch-up, once versus daily expired mode, stage subsets, best-before wording, an empty digest, a user with no devices. `DRY_RUN=true` logs messages instead of sending. One integration test against the Firestore emulator with seeded data.

**10.6 Alternatives.** *Plan B, a more reliable scheduler.* If GitHub timing proves too flaky, deploy the same core module as a Cloud Function triggered by Cloud Scheduler. That requires the Blaze plan with a payment card; expected cost is cents per month, but budget alerts do not cap spending. Keep the core as a pure module with two entry points (`main.ts` for GitHub, `functions/index.ts` for Cloud Functions) so the switch takes under an hour. A free external cron service can also trigger the workflow through `workflow_dispatch`.

*Plan C, on-device reminders (possible because the app is Android only).* No server, no service account, no GitHub job and no FCM tokens. A WorkManager task chained daily (each run schedules the next one at the user's chosen hour) queries the server, not the cache, for batches expiring inside the window, builds the same digest and posts it as a local notification; it falls back to the cache when offline. Trade-offs: timing is approximate, and aggressive battery management on some phone brands can delay or block background work, whereas FCM delivery is generally less affected, so add a Settings screen that explains how to exempt the app from battery optimization. Do not request exact-alarm permissions. The human chose the server-sent digest, so Plan C is not part of v1; it is kept here in case the reminder job proves too burdensome; the digest rules stay the same, and `users.timezone`, the `devices` subcollection and the server-written fields are then unused.

## 11. Recipe import

**11.1 Entry points.** The Android share sheet (a manifest intent filter for `ACTION_SEND` with `text/plain`), the built-in browser, and Paste link. All of them call `RecipeImportService.fromUrl(url)` or `fromHtml(url, html)`.

**11.2 Flow**

```
url -> fetch HTML (15 s timeout, 3 MB cap, browser-like User-Agent, follow redirects, http or https only)
    -> extract the recipe: JSON-LD, then microdata, then heuristics
    -> found: preview screen -> Save
    -> not found or blocked (403, challenge page, empty): load the page in the built-in browser and extract from the live DOM
    -> still nothing: 'No recipe found' with Create manually prefilled (title, image, source URL)
```

In the built-in browser the app runs this script after each page load and reads the result with evaluateJavascript and feeds it to the same extractor:

```js
JSON.stringify({
  url: location.href,
  title: document.title,
  ld: [...document.scripts].filter(s => s.type === 'application/ld+json').map(s => s.textContent),
  ogImage: [...document.getElementsByTagName('meta')].find(m => m.getAttribute('property') === 'og:image')?.content ?? null
})
```

**11.3 JSON-LD extraction (primary).** Parse every `application/ld+json` script; tolerate bad JSON (BOM, trailing commas, HTML comments); handle a top-level object, an array and `@graph`; accept nodes whose `@type` is `Recipe` or a list containing it; if there are several, take the one with the most ingredients. Mapping:

| schema.org | Stored as | Notes |
| --- | --- | --- |
| name | title | Decode HTML entities, trim |
| image | imageUrl | String, ImageObject `url`, or array (first); resolve relative URLs |
| description | description | Strip tags |
| recipeYield | servings, servingsText | First number; keep the text |
| prepTime, cookTime, totalTime | minutes | ISO 8601 durations such as PT1H30M; total missing means prep plus cook |
| recipeIngredient | ingredients | List of strings |
| recipeInstructions | steps | String (split on line breaks), list of strings, `HowToStep.text`, or `HowToSection.itemListElement` (section name becomes `group`) |
| author, keywords, recipeCategory, recipeCuisine | sourceName, tags | Author name; up to 8 tags |
| page URL | sourceUrl | Canonicalize and drop tracking parameters (`utm_*`) |

**11.4 Fallbacks.** Microdata (`itemtype` containing `schema.org/Recipe`, with `itemprop` fields) through the same mapper. Last, heuristics: `og:title`, `og:image` and common recipe-plugin containers (class names starting `wprm-recipe-` or `tasty-recipes-`). Heuristic results are labeled “Check this recipe” in the preview.

**11.5 Ingredient parsing (best effort; `raw` is always kept).** Parse a leading quantity (integers, decimals, unicode fractions such as ½, mixed numbers like 1 1/2, and ranges like 2-3 using the lower value), then a known unit (cup, tbsp, tsp, g, kg, ml, l, oz, lb, pinch, clove, can, package, and plurals), then the name (drop text after the first comma and anything in parentheses). Lines that do not parse keep only `raw` and are not scaled. **In pantry** matching: a product matches an ingredient when every word of its `nameKey` (singularized) appears in the ingredient line. Show it only as “Probably in pantry” and let the user toggle it; never decide silently.

**11.6 Safety and quality**

- Sanitize imported text: strip tags; limits: title 200, ingredient 300, step 2,000, at most 100 ingredients and 100 steps.
- The web view MUST block non-http(s) schemes, disable file and content access, and not expose any addJavascriptInterface object (read the extraction result with evaluateJavascript instead).
- Recipes are saved for personal use with the source name and link shown; do not re-publish them.
- Images stay remote URLs cached on the device; a failed image shows a placeholder. Uploading photos needs file storage and is out of v1.
- Keep small synthetic HTML fixtures for every JSON-LD shape above, plus 10 trimmed structure snapshots of real pages (no copyrighted text) for regression tests. Keep the list of tested sites in `docs/import-test-sites.md`.

## 12. Security and privacy

**12.1 Assets and threats.** Assets: household data (low sensitivity but personal), emails and display names, device tokens, the reminder service account key. Threats: outsiders reading or changing household data; a guessed or leaked invite code; a removed member keeping access; a lost or shared phone; leaked secrets; abuse of the free quota; malicious recipe pages.

**12.2 Controls (MUST unless marked SHOULD)**

1. Firestore Security Rules enforce all authorization and deny by default (12.3). The client is never trusted.
2. Firebase Auth with email and password, minimum 8 characters; keep email enumeration protection enabled in the console.
3. Every household path checks membership in the rules; owner-only actions check the owner role.
4. Invite codes: 10 characters (50 bits) from a cryptographically secure generator, readable only by exact `get` (no listing). The owner can pause or rotate instantly. A leaked code only lets a stranger send a join request; nobody enters the household until the owner approves, and pause, rotate and remove exist as further controls.
5. Removed or departed members lose access at once (rules re-check membership on every request) and the app clears the local cache.
6. On sign-out or account switch call `terminate()` then `clearPersistence()` so another person on the same phone cannot see cached data.
7. TLS only. No cleartext traffic in the app (Android `usesCleartextTraffic=false` in the manifest).
8. No secrets in the app (Firebase configuration values are identifiers, not secrets). The service account key exists only as a GitHub Actions secret. `.gitignore` covers `*.jks`, `keystore.properties`, `local.properties`, `service-account*.json` and `.env*`. Enable GitHub secret scanning and push protection.
9. Supply chain: Dependabot or Renovate for Gradle, npm and GitHub Actions; pin Actions to major versions or commit hashes.
10. SHOULD (M7): Firebase App Check with the Play Integrity provider; start in monitor mode and enforce on Firestore only after all family devices pass. Distribution outside Google Play needs the matching Play Integrity settings (see the Firebase App Check Android docs). Use the debug provider for emulators and CI. Setting up the Play Integrity provider may require linking the project through a Google Play Console account, which has a one-time fee; check this at M7, and skip App Check if it would need a paid account, because it is optional.
11. Privacy: collect only email, display name, household data, device tokens and time zone. No analytics, no ads, no third-party SDKs besides Firebase. Provide in-app Delete account and Export data. Crashlytics is optional and carries no personal data. Add a one-page `docs/PRIVACY.md` listing exactly these data.
12. Web view hardening as in 11.6.

**12.3 Firestore Security Rules: reference draft.** This is a starting point; the tests in 12.4 define correctness. Limits to respect: at most 10 `exists`, `get` and `getAfter` calls per request (20 for batched writes), and each lookup is billed as a read.

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {

    function signedIn() { return request.auth != null; }
    function me() { return request.auth.uid; }
    function hhPath(hid) { return /databases/$(database)/documents/households/$(hid); }
    function memberPath(hid, u) { return /databases/$(database)/documents/households/$(hid)/members/$(u); }
    function requestPath(hid, u) { return /databases/$(database)/documents/households/$(hid)/joinRequests/$(u); }
    function isMember(hid) { return signedIn() && exists(memberPath(hid, me())); }
    function isOwner(hid) {
      return isMember(hid) && get(memberPath(hid, me())).data.role == 'owner';
    }
    function s(v, lo, hi) { return v is string && v.size() >= lo && v.size() <= hi; }
    function onlyChanges(keys) {
      return request.resource.data.diff(resource.data).affectedKeys().hasOnly(keys);
    }
    function stamped() {
      return request.resource.data.updatedBy == me()
          && request.resource.data.updatedAt == request.time;
    }
    function codeOk(c) { return c.matches('^[A-HJ-NP-Z2-9]{10}$'); }

    match /config/{doc} {
      allow read: if signedIn();
      allow write: if false;
    }

    match /users/{uid} {
      allow read, delete: if signedIn() && me() == uid;
      allow create: if signedIn() && me() == uid
        && request.resource.data.keys().hasOnly(['displayName', 'householdId', 'timezone',
             'locale', 'units', 'notificationPrefs', 'createdAt', 'updatedAt'])
        && s(request.resource.data.displayName, 1, 40)
        && request.resource.data.householdId == null;
      allow update: if signedIn() && me() == uid
        && onlyChanges(['displayName', 'householdId', 'timezone', 'locale', 'units',
             'notificationPrefs', 'updatedAt'])
        && s(request.resource.data.displayName, 1, 40)
        && (request.resource.data.householdId == null
            || existsAfter(memberPath(request.resource.data.householdId, me())));
      match /devices/{id} {
        allow read, delete: if signedIn() && me() == uid;
        allow create, update: if signedIn() && me() == uid
          && s(request.resource.data.token, 20, 4096)
          && request.resource.data.platform == 'android';
      }
    }

    match /inviteCodes/{code} {
      allow get: if signedIn() && codeOk(code);
      allow list: if false;
      allow create: if signedIn() && codeOk(code)
        && request.resource.data.keys().hasOnly(['householdId', 'householdName', 'active', 'createdBy', 'createdAt'])
        && request.resource.data.createdBy == me()
        && getAfter(hhPath(request.resource.data.householdId)).data.ownerUid == me();
      allow update: if isOwner(resource.data.householdId)
        && onlyChanges(['active', 'householdName']);
      allow delete: if isOwner(resource.data.householdId);
    }

    match /households/{hid} {
      allow read: if isMember(hid);
      allow create: if signedIn() && request.resource.data.ownerUid == me()
        && s(request.resource.data.name, 1, 60)
        && request.resource.data.keys().hasOnly(['name', 'ownerUid', 'inviteCode', 'schemaVersion', 'createdAt', 'updatedAt']);
      allow update: if isOwner(hid) && s(request.resource.data.name, 1, 60) && (
           onlyChanges(['name', 'inviteCode', 'updatedAt'])
        || (onlyChanges(['ownerUid', 'updatedAt'])                       // ownership transfer
            && getAfter(memberPath(hid, request.resource.data.ownerUid)).data.role == 'owner'));
      allow delete: if isOwner(hid);

      match /members/{uid} {
        allow read: if isMember(hid);
        allow create: if signedIn()
          && request.resource.data.keys().hasOnly(['role', 'displayName', 'joinedAt'])
          && s(request.resource.data.displayName, 1, 40)
          && (
            // the creator becomes the owner (same batch as the household)
            (me() == uid && request.resource.data.role == 'owner'
              && getAfter(hhPath(hid)).data.ownerUid == me())
            // the owner approves a join request (same batch that sets it to approved)
            || (isOwner(hid) && me() != uid && request.resource.data.role == 'member'
              && get(requestPath(hid, uid)).data.status == 'pending'
              && getAfter(requestPath(hid, uid)).data.status == 'approved'
              && get(requestPath(hid, uid)).data.displayName == request.resource.data.displayName));
        allow update: if signedIn() && (
             (me() == uid && onlyChanges(['displayName']) && s(request.resource.data.displayName, 1, 40))
          || (isOwner(hid) && onlyChanges(['role'])                        // ownership transfer only
              && (request.resource.data.role == 'owner') == (getAfter(hhPath(hid)).data.ownerUid == uid)));
        allow delete: if signedIn() && (
             (me() == uid && resource.data.role == 'member')
          || (isOwner(hid) && me() != uid)
          || (me() == uid && isOwner(hid) && !existsAfter(hhPath(hid))));   // owner deleting the household
      }

      match /joinRequests/{uid} {
        allow read: if signedIn() && (me() == uid || isOwner(hid));
        allow create: if signedIn() && me() == uid
          && !exists(memberPath(hid, me()))
          && request.resource.data.keys().hasOnly(['displayName', 'joinCode', 'status', 'requestedAt'])
          && s(request.resource.data.displayName, 1, 40)
          && request.resource.data.status == 'pending'
          && request.resource.data.requestedAt == request.time
          && get(/databases/$(database)/documents/inviteCodes/$(request.resource.data.joinCode)).data.householdId == hid
          && get(/databases/$(database)/documents/inviteCodes/$(request.resource.data.joinCode)).data.active == true;
        allow update: if isOwner(hid) && onlyChanges(['status'])
          && request.resource.data.status in ['approved', 'declined'];
        allow delete: if signedIn() && (me() == uid || isOwner(hid));
      }

      match /products/{id} {
        allow read, delete: if isMember(hid);
        allow create, update: if isMember(hid) && stamped()
          && s(request.resource.data.name, 1, 80)
          && (request.resource.data.minQuantity == null || request.resource.data.minQuantity is number);
      }
      match /stock/{id} {
        allow read, delete: if isMember(hid);
        allow create, update: if isMember(hid) && stamped()
          && request.resource.data.quantity is number && request.resource.data.quantity >= 0
          && (request.resource.data.expiryDate == null
              || request.resource.data.expiryDate.matches('^[0-9]{4}-[0-9]{2}-[0-9]{2}$'))
          && request.resource.data.location in ['fridge', 'freezer', 'pantry', 'other']
          && request.resource.data.dateType in ['use_by', 'best_before'];
      }
      match /groceryLists/{listId} {
        allow read, create, update, delete: if isMember(hid);
        match /items/{itemId} {
          allow read, delete: if isMember(hid);
          allow create, update: if isMember(hid) && stamped() && s(request.resource.data.name, 1, 80);
        }
      }
      match /recipes/{id} {
        allow read, delete: if isMember(hid);
        allow create, update: if isMember(hid) && stamped() && s(request.resource.data.title, 1, 200)
          && request.resource.data.ingredients.size() <= 100
          && request.resource.data.steps.size() <= 100;
      }
      match /activity/{id} {
        allow read: if isMember(hid);
        allow create: if isMember(hid) && request.resource.data.actorUid == me()
          && request.resource.data.createdAt == request.time;
        allow delete: if isOwner(hid);   // household deletion; routine pruning uses the Admin SDK
      }
      match /stats/{month} {             // after v1 (5.7); see firebase/firestore.rules for the full checks
        allow read: if isMember(hid);
        allow create, update: if isMember(hid) && stamped()      // yyyy-MM ids, known fields, whole counts >= 0
          && month.matches('^[0-9]{4}-(0[1-9]|1[0-2])$');
        allow delete: if isOwner(hid);   // household deletion only
      }
    }
  }
}
```

Notes on the draft: after v1 the deployed rules (`firebase/firestore.rules`) also check tombstones (`deletedAt`, 9.8) on products, stock, items and recipes, recipe names on products (`aliases`, at most 30) and on items (`aliasOf`), opened batches (`openedOn`, a date) and products (`openedShelfLifeDays`, 1 to 365), and the Insights `stats` documents (5.7). The reminder job uses the Admin SDK, which bypasses rules. The household creation batch (household, owner member, invite code, user profile update) relies on `getAfter` and `existsAfter`. Always write `expiryDate` explicitly (null when there is none), because the stock rule reads it. To delete a household, the owner deletes every subcollection first, then the owner's own member document and the household document in the same batch.

Joining takes two steps: the person creates a join request with a valid code, then the owner approves it in one batch that sets the request to approved and creates the member document (the rules accept a member document for someone else only in that case). Declining sets the request to declined; the person's app shows the result and deletes the request.

**12.4 Rules test matrix (MUST).** Use the emulator and `@firebase/rules-unit-testing`; run it in CI.

| Actor | Action | Expected |
| --- | --- | --- |
| Signed out | Any read or write | Denied |
| Non-member | Read any household document | Denied |
| Non-member with an active code | Create own join request with that code | Allowed |
| Same user | Wrong or paused code, someone else's uid, or a status other than pending | Denied |
| Non-member | Create own member document directly, skipping approval | Denied |
| Requester | Read own join request | Allowed |
| Another non-member | Read someone else's join request | Denied |
| Requester | Change own request to approved | Denied |
| Owner | Approve a request in one batch (request set to approved plus member created) | Allowed |
| Owner | Create a member without an approved request | Denied |
| Owner | Decline a request | Allowed |
| Member (not owner) | Approve or decline a request | Denied |
| Member | Read and write products, stock, lists, recipes | Allowed |
| Member | Invalid quantity, date format or location, or `updatedBy` not self | Denied |
| Member (not owner) | Delete own membership | Allowed |
| Member | Delete another member, or promote self | Denied |
| Member | Update the household or the invite code | Denied |
| Owner | Remove a member, rename, pause or rotate the code | Allowed |
| Owner | Change `ownerUid` in a plain update | Denied |
| Owner | Transfer ownership to a member in one batch | Allowed |
| Owner | Delete the household and all subcollections | Allowed |
| Anyone | List `inviteCodes`, or get a malformed code | Denied |
| User A | Read user B's profile or devices | Denied |
| Client | Write `lastDigestDate` or `lastExpiredThrough` | Denied |
| Removed member | Any household read | Denied immediately |
| New user | Create household batch (household, owner member, code, own profile update) | Allowed |

**12.5 If you ever publish on Google Play.** Add a privacy policy URL, the Data safety form, a web link for account deletion, App Check enforcement, and a fresh review of the free-quota math.

## 13. Quality, CI and release

**13.1 Testing (MUST)**

- **Unit (pure Kotlin, JVM):** status calculation, restock engine, shelf-life learning, ingredient parser, JSON-LD extractor against fixtures, ISO duration parser, invite-code generator and formatter (JUnit, MockK, Turbine for Flows). Reminder job: digest builder and time logic (Node tests).
- **Rules tests (emulator):** the matrix in 12.4.
- **Compose UI and screenshot tests:** pantry row, status chip, add-item sheet, grocery list, recipe card; light and dark; font scale 1.0 and 2.0.
- **Instrumented tests (`androidTest` with the Compose test rule and the Firebase emulators):** sign up, create a household, a second user requests to join with the code and the owner approves, add a pantry item, low-stock auto-add, propagation between two clients, an offline write followed by reconnect.
- **Manual QA script per milestone:** physical devices (two different brands if possible), offline mode, time zone change, a DST day, notification permission denied, battery saver, 200% font.
- **Performance budgets:** cold start under 2.5 s on a mid-range Android phone, 60 fps scrolling with 500 batches, initial household sync under 2 s on 4G.
- **Baseline profile:** release builds ship a baseline profile (`app/src/main/baselineProfiles/baseline-prof.txt`) so start-up and the first scroll run precompiled code; `androidx.profileinstaller` installs it on phones that get the APK outside Google Play, where Android compiles it during the next idle maintenance. The `benchmark` module generates it (`BaselineProfileGenerator`) and measures cold start and the first Pantry scroll with and without it (`StartupBenchmark`, `PantryScrollBenchmark`) against the dev benchmark build and the emulators. Regenerate it with `./scripts/baseline-profile.sh` after large UI changes (docs/RUNBOOK.md).

**13.2 CI** (GitHub Actions). On pull requests and main: `./gradlew spotlessCheck lintDebug testDebugUnitTest assembleDebug`, rules tests against the emulator, and reminder job tests. After v1, CI also compiles the `benchmark` module (`:benchmark:compileBenchmarkKotlin`); the benchmarks themselves need an Android device and run by hand. Use path filters, Gradle caching and a concurrency group to protect the 2,000 free private-repository minutes.

**13.3 Release and distribution**

- Version in `app/build.gradle.kts`: `versionName` (semantic version) and an incrementing `versionCode`; tag `v1.0.0`.
- **Signing:** create one upload keystore, keep it and `keystore.properties` out of the repository, and back the keystore up somewhere safe (if it is lost, testers must uninstall and reinstall).
- **Distribution:** Firebase App Distribution with tester emails (the human's choice; free). Collect each tester's email address for the invite; a Google account email is the safest choice.
- **Update gate:** on launch read `config/app.minSupportedBuild`; if the installed `versionCode` is lower, show a full-screen “Please update” with a link.
- **Runbook** (`docs/RUNBOOK.md`): rotate the service account key, regenerate an invite code by hand, check quota usage, re-enable the workflow, restore from an export.

**13.4 Backups.** Do not assume the free plan includes managed backups (verify in the Firebase console). v1 provides Settings > Export (JSON of products, stock, lists and recipes, without tombstones; after v1 also the Insights month documents, as an added `stats` array in format version 1, which the restore workflow also writes). In M7 the reminder job SHOULD also save a nightly JSON export per household as a private Actions artifact kept for 14 days.

## 14. Milestones for Claude Code

Each milestone ends with its acceptance checks passing and a short summary to the human before the next one starts.

**M0. Foundations**

- Android project (Kotlin, Jetpack Compose, Hilt) with `dev` and `prod` flavors, lint and formatting checks (Spotless with ktlint), theme tokens, a four-tab NavigationBar shell, string resources, Firebase projects and emulators wired, CI, a `docs/` folder with this document and `DECISIONS.md`, and `CLAUDE.md`.
- *Accept:* the app runs on an Android emulator in both flavors and shows four empty tabs in light and dark; CI is green; one command starts the emulators.

**M1. Accounts and households**

- Sign up, sign in, sign out, password reset, delete account; household gate; create, join request with the owner's approval (request, approve, decline, cancel), member list, invite card (copy, share, pause, rotate); leave, remove, transfer ownership, delete household; membership watcher; cache cleared on sign-out; Firestore rules and the full test matrix.
- *Accept:* two emulator users (A creates, B enters the code and waits, A approves, B is in); when A removes B, B is returned to the gate within 5 s; all rules tests pass.

**M2. Pantry**

- Products and stock batches, add and edit with merge, batch actions with Undo, status chips, filters, sort, search, grouping, product detail, shelf-life learning, offline behavior.
- *Accept:* 100 items added; statuses correct with a fixed clock; a second client sees edits in under 2 s; offline edits sync after reconnect; unit and Compose UI tests pass.

**M3. Grocery lists and restock**

- Lists and items, quick add, check-off, the In cart section, the Add to pantry flow, the restock engine, the Back in stock hint.
- *Accept:* all restock unit tests from 9.6 pass; integration test: dropping stock below the minimum on phone A creates exactly one item visible on phone B; deleting it does not recreate it; recovering and dropping again does.

**M4. Reminders**

- FCM setup, permission explanation, token registration, channel, deep link, notification settings, local test notification; the reminder job with tests and the GitHub workflow; written instructions for creating the service account.
- *Accept:* with a seeded household, the job (dry run, then live) delivers a correct digest to an Android phone with the app closed; time zone and DST tests pass; a second run on the same day sends nothing.

**M5. Recipes**

- Recipe box, manual create and edit, detail, servings scaling, cook mode, tags and favorites, the import service (JSON-LD, microdata, heuristics), paste link, the built-in browser with the detection bar, the Android share sheet, add ingredients to the grocery list with pantry hints.
- *Accept:* import works on 9 of the 10 sites listed in `docs/import-test-sites.md`; fixture tests pass; sharing a link from Chrome opens a preview in under 5 s.

**M6. Home, activity, polish and beta**

- Home dashboard, activity log, empty, error, offline and quota states, animations, an accessibility pass with TalkBack, dark-mode QA, a performance pass, icon and splash, complete settings, JSON export, `PRIVACY.md`, the Crashlytics toggle, a release build, and a beta to testers through Firebase App Distribution.
- *Accept:* the QA script passes on two physical Android devices and a small-screen emulator; the 13.1 budgets are met; testers can install and join the same household.

**M7. Hardening**

- App Check (monitor, then enforce), dependency automation, nightly export, the runbook, a security review against section 12, and a script that simulates 10 households against the emulator to check the quota math.
- *Accept:* enforcement is on without breaking family devices; rules tests are green; the section 12 checklist is signed off.

## 15. Risks and confirmed decisions

| Risk | Effect | Mitigation |
| --- | --- | --- |
| Free Firestore quota exceeded | Features stop for the day | Budget in 9.7, lazy listeners, usage checks, Blaze fallback with alerts |
| GitHub scheduled runs late or skipped | Late or missing digest | Stateless windows, catch-up until 22:00, failure notifications, Plan B or Plan C (10.6) |
| Recipe sites block fetches or lack structured data | Import fails | Web-view DOM extraction, manual fallback, regression list |
| Rules complexity | Security bugs | Emulator test matrix, review in M7 |
| Android battery management delays pushes | Late reminders | High-priority FCM, an in-app tip about battery settings; the digest repeats daily so a late one is still useful |
| Android toolchain and library churn (Gradle plugin, Kotlin, Compose, Firebase SDKs) | Build breaks | Version catalog with locked versions, Dependabot, deliberate upgrades |
| Wrong dates entered | False alarms | Easy edit, shelf-life prefill, Undo |
| No managed backups on the free plan | Data loss | JSON export, nightly artifact |
| Clock and time zone errors | Wrong stage shown | Date-only arithmetic, injected clock, DST tests |

**Decisions confirmed by the human**

1. Application ID: `com.parcha.spoilnot` (dev builds: `com.parcha.spoilnot.dev`).
2. Android distribution: Firebase App Distribution (free).
3. Joining a household needs the owner's approval; the code alone is not enough.
4. GitHub repository: private. The free plan includes private repositories and 2,000 Actions minutes per month.
5. Reminders: the server-sent daily digest from the small scheduled job (the default).

**Still to decide during setup (M0):** the Firestore region (choose the one closest to your household; it cannot be changed later) and whether new products default to metric or imperial units.

## 16. Roadmap after v1

- **In progress (owner's order, October 18, 2026):** a baseline profile for a faster first start and scroll, waste statistics with charts on Home and an Insights screen, preset color themes, then cheaper syncing that reads only what changed (fewer free-quota reads).
- **Next:** barcode scanning with the free Open Food Facts database, on-device camera reading of expiry dates, “use it up” recipe suggestions based on expiring items, opened-package tracking (a new expiry once opened).
- **Dropped:** a push notification for the owner when someone asks to join (the owner decided it is not useful enough, October 18, 2026).
- **Later:** meal planner, home-screen widgets (Jetpack Glance), receipt scanning, recipe photo upload (needs file storage), custom storage locations, import from Paprika export files.
- **Maybe:** Google sign-in (Credential Manager), multiple households, biometric app lock, push notifications for household activity (needs Cloud Functions on the Blaze plan), voice add.

## 17. Changes after v1

Every change after M7, newest last. Details and reasons are in `docs/DECISIONS.md`; the sections listed were updated.

| Date | Change | Sections |
| --- | --- | --- |
| 2026-10-18 | Beta feedback round 2 shipped in beta 2 (pantry rows per purchase, one Add and Edit form, Cooked category, half-drag form sheets) | 5.2, 6.5 (through DECISIONS) |
| 2026-10-18 | One-command beta releases (`scripts/release-beta.sh`) | 13.3, RUNBOOK |
| 2026-10-18 | New launcher icon "Fresh jar" (the owner's choice of three) | 6.4, DECISIONS |
| 2026-10-18 | Form sheets stop below the status bar (fixes endless shaking after a flick down) | 6.5, DECISIONS |
| 2026-10-18 | Beta 3 (1.0.0-beta.3, build 4) | 13.3 |
| 2026-10-18 | Insights: monthly counts of batches used up and thrown away, the Home card *Your kitchen this month* and the Insights screen with charts | 5.6, 5.7, 8.2, 9.7, 12.3, 13.4, RUNBOOK, QA, PRIVACY |
| 2026-10-18 | Preset color themes (Fiery Palette, Summer Fun, Enchanted Forest, Watermelon Sorbet, Contrast Pop) and the Colors picker, replacing the wallpaper switch | 5.6, 6.4, Appendix B, QA |
| 2026-10-18 | Changes-only listeners with tombstones: about 60% fewer reads (1,890 instead of 4,760 per household per day); the reminder job skips and purges tombstones; exports leave them out | 8.1, 8.5, 9.7, 9.8, 10.3, 12.3, 13.4, RUNBOOK, QA |
| 2026-10-19 | Beta 4 (1.0.0-beta.4, build 5) with Insights, color themes, the baseline profile and changes-only listeners; rules deployed to both projects | 13.3, RUNBOOK |
| 2026-10-19 | Lint waits for Hilt code generation (fixes an intermittent local lint crash) | 13.2, DECISIONS |
| 2026-10-19 | Recipe ingredients go on the grocery list with shopping names, linked to the household's products, with shopping amounts and the recipe line as the note; products remember the amount last added | 5.5, 8.2, QA |
| 2026-10-19 | Change in the Add to grocery list sheet, and the household's app remembers each change as a recipe name for that product; an ingredient no longer links to a product with more words | 5.5, 8.2, 12.3, QA |
| 2026-10-19 | Mark opened: a shorter date for opened food, the opened part of a batch in pieces on its own row, and each product remembers how long it keeps once opened | 5.2, 8.2, 12.3, QA |
| 2026-10-19 | I cooked this: a recipe takes what it used off the pantry, soonest-expiring first, with Undo and a "cooked" activity event | 5.5, 5.6, 8.2, QA |
| 2026-10-19 | Use it up on Home: saved recipes that use the most food expiring soon | 5.6, 9.7, QA, RUNBOOK |
| 2026-10-19 | README rewritten from the owner's point of view: the app's purpose and features, and how it was designed and built with Claude Code; the developer commands moved out (they are in CLAUDE.md and the RUNBOOK) | README |
| 2026-10-18 | Baseline profile, macrobenchmarks and `scripts/baseline-profile.sh`. On the emulator: cold start median 457 → 419 ms; first Pantry scroll frame CPU time P90 8.2 → 6.5 ms, P99 13.3 → 10.4 ms | 13.1, 13.2, Appendix B, RUNBOOK |

## Appendix A. Kickoff prompt and CLAUDE.md

**Prompt to give Claude Code**

```
You are building Spoil Not, a native Android app (Kotlin, Jetpack Compose, Material 3) with a Firebase Spark backend.
The full design is in docs/DESIGN.md. Read it completely before doing anything.
Start with milestone M0 only. Before coding, list every step you need from me (accounts, keys, IDs)
with exact instructions. Follow the layering rules in section 7.2: keep Firestore access inside data/
repositories and business logic in pure Kotlin. Write tests as you go, use the Firebase emulators for all
tests, and record any deviation in docs/DECISIONS.md. After each milestone run its acceptance checks and
summarize the results to me before starting the next one.
```

**Suggested `CLAUDE.md`**

```
# Spoil Not
- Source of truth: docs/DESIGN.md. Deviations go in docs/DECISIONS.md.
- Android only. Stack: Kotlin, Jetpack Compose with Material 3, Navigation 3, Hilt, Coroutines and Flow, Firebase (Auth, Firestore, FCM) on the free Spark plan. Latest stable versions through the Gradle version catalog.
- Never add Cloud Functions, Cloud Storage or any paid Google Cloud service. Never commit secrets.
- Composables and ViewModels never use Firebase classes. Only data/ repositories touch Firestore.
- Business logic is pure Kotlin with an injectable java.time.Clock and unit tests.
- Dates are 'YYYY-MM-DD' strings; timestamps are server timestamps.
- Every destructive action has Undo. Status is never conveyed by color alone.
- Before finishing a task run: ./gradlew spotlessCheck lintDebug testDebugUnitTest, and the rules tests.
- Use the Firebase emulators for development and tests (the host is 10.0.2.2 from the Android emulator).
```

## Appendix B. Repository layout

```
spoilnot/
  app/                    Android app module (src/main, src/test, src/androidTest)
  gradle/libs.versions.toml   version catalog
  backend/reminder/       Node.js reminder job (src/, test/, package.json)
  firebase/               firestore.rules, firestore.indexes.json, firebase.json, rules tests
  docs/                   DESIGN.md, DECISIONS.md, RUNBOOK.md, PRIVACY.md, import-test-sites.md
  benchmark/              macrobenchmarks and the baseline profile generator (never shipped)
  scripts/                emulators.sh, release-beta.sh, baseline-profile.sh
  tools/themes/           preset color theme definitions and their generator (not part of the app build)
  .github/workflows/      ci.yml, reminders.yml, nightly-export.yml, restore.yml, secret-scan.yml
  CLAUDE.md
```

## Appendix C. Facts verified on October 7, 2026

Re-check anything that affects cost or security before relying on it.

- **Firebase Spark plan:** no payment method needed; Authentication (most sign-in methods), Cloud Messaging, Crashlytics, App Check and App Distribution are no-cost products; Cloud Functions need the Blaze plan; exceeding a no-cost quota shuts that product off until it resets; linking a Cloud Billing account upgrades the project to Blaze; budget alerts do not cap spending. https://firebase.google.com/docs/projects/billing/firebase-pricing-plans
- **Firestore quotas:** free quota of 50,000 reads, 20,000 writes and 20,000 deletes per day and 10 GiB per month of outbound data on the `(default)` database, one database per project; rules limit of 10 access calls per request (20 for batched writes and transactions). https://cloud.google.com/firestore/quotas
- **Firestore billing:** a listener disconnected for more than 30 minutes is billed as a new query (with persistence on); rules lookups are billed as reads. https://firebase.google.com/docs/firestore/pricing
- **Jetpack Compose:** as of August 2026 the stable Compose libraries are at 1.12.0 and `androidx.compose.material3` is stable at 1.4.0 (1.5 is in alpha). https://developer.android.com/jetpack/androidx/releases/compose
- **Navigation 3:** stable since early 2026; version 1.2.0 was released on September 23, 2026. https://developer.android.com/jetpack/androidx/releases/navigation3
- **Firebase Android SDK:** the Kotlin extension (`-ktx`) modules were removed from the BoM in version 34.0.0, so use the main modules; some Firebase libraries require API level 23 or higher. https://firebase.google.com/docs/android/kotlin-migration
- **Google Play:** $25 one-time registration; internal testing allows up to 100 testers; new personal accounts need a closed test (12 testers, 14 days) only to apply for production access; apps that let users create an account must offer account deletion in the app and through a web link, and permanently private apps are exempt. https://support.google.com/googleplay/android-developer/answer/13327111
- **GitHub:** the Free plan includes 2,000 Actions minutes per month for private repositories and unlimited minutes on public ones (standard runners); scheduled workflows are best-effort and can be delayed, the minimum interval is 5 minutes, and in public repositories they are disabled after 60 days without activity. https://github.com/pricing and https://docs.github.com/actions/using-workflows/events-that-trigger-workflows#schedule
- **App Check (Android):** the Play Integrity provider supports apps distributed on Google Play, outside it, or both, with different settings per channel. https://firebase.google.com/docs/app-check
