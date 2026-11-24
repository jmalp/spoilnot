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

- A new user can install, create a household, add 5 pantry items and receive the first expiry notification within 10 minutes of setup. *After v1 (2026-11-13, owner's decision of 2026-11-08):* the criterion stays. The first digest comes only with the reminder job's next run after the chosen hour (10.3), hours later at best, so right after Turn on the app shows a local **You're set** notification with the reminder time and tomorrow's items (5.4); that is the first notification within the 10 minutes.
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
| Households | One per user. Joining needs the code and the owner's approval. The owner can rotate the code and remove members (after v1, 2026-11-13, Pause joining was removed: approval already guards joining, R12) | You |
| Reminders | One server-sent digest per person per day at a chosen hour (default 9 AM) covering 3, 2, 1, 0 days and expired | Same for every member and works with the app closed; you chose this over the on-device option in 10.6 |
| Offline | Offline-first; writes queue and sync later | Grocery stores have bad signal |
| Item model | Product (what it is) plus stock batches (each purchase with its own expiry date) | Real life: three yogurts, three dates |
| Date types | Each batch is Use by (safety) or Best before (quality). Since beta 2 (DECISIONS 2026-10-18) the form has no date type, so new purchases are Use by; Best before stays on purchases saved before, with its softer wording. Kept as is after review UX-04 (owner's decision, 2026-11-13) | Changes the warning wording |
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
| Owner, member | The owner created the household and controls the invite code and members; everyone else is a member. A household has exactly one owner. Members delete shared things by marking them deleted (tombstones, 8.1); only the owner deletes them for real, when deleting the household |
| Product | A kind of food in the household catalog, such as Milk. Holds its unit, category and minimum quantity |
| Stock batch | A quantity of one product with one expiration date and one location, for example 2 cartons of milk expiring Oct 12 in the fridge. A product can have many batches |
| Date type | Use by (safety date) or Best before (quality date) |
| Status | Derived from the expiration date: expired, today, tomorrow, soon (2 to 3 days), week (4 to 7 days), fresh, no date |
| Keep at least | Per-product minimum. When total stock falls below it, the product goes on the grocery list |
| Digest | The daily notification that summarizes what needs attention |
| Join request | Someone's request to join with the code; the owner approves or declines it. After v1 it shows the requester's masked sign-in email ("ro***@example.com") and when it was sent. The requester's own app finishes the join after approval, even if the request itself was removed by then |

## 5. Feature specifications

### 5.1 Accounts and households

**Sign-up and sign-in.** Email and password. Sign-up asks for display name, email and password (minimum 8 characters) and sends a verification email; verification is encouraged but not required. Password reset by email. After v1 (2026-11-17, the new look, step 8, 19.12) an error from the server that belongs to a field (a wrong email or password, an email already in use, a weak password) shows on that field, which takes the focus; other errors show under the form. Sign-out; after v1 (2026-11-10), when this phone has changes that haven't reached the server within a few seconds, it asks "Sign out anyway? Some changes haven't synced yet. They're only on this phone, and signing out deletes them." first (from Settings and the gate, both apps). **Delete account** in Settings (Google Play requires it for apps with accounts, and it is good practice even for a private app): removes the profile and device tokens, leaves the household and deletes the sign-in (recent re-authentication may be required). An owner must first transfer ownership or delete the household. After v1 (2026-11-10) a missing profile is created only right after sign-up or sign-in on this phone (an interrupted sign-up, or a failed Delete account whose sign-in remains); a profile that disappears at any other time was deleted with its account, maybe on another phone, so that phone signs out instead and the profile isn't brought back. After an interrupted sign-up the app therefore starts at sign-in, and signing in creates the profile.

**Household gate.** After sign-in, a user with no household sees two large cards on the main screen: **Create a household** (name field) and **Join with a code** (10-character field, auto-uppercase, hyphen after 5 characters, paste supported). Nothing else is reachable until one is done. The menu offers Sign out and, after v1 (2026-11-08), Delete account with the same password dialog as Settings, so someone who never joined a household can also delete their account; if the deletion fails after the gate gave way to Loading, the gate comes back with the dialog and the error.

**Create.** Generates a 10-character invite code from the alphabet `ABCDEFGHJKLMNPQRSTUVWXYZ23456789` (no I, O, 0, 1) with a cryptographically secure random source (`java.security.SecureRandom`). The household, owner membership, invite code, default grocery list and the user's `householdId` are written together in batched writes.

**Join.** The app looks up the code and asks “Send a request to join *The Smiths*?” showing the household name. On confirm it sends a join request and shows “Waiting for the owner to approve”, which updates by itself when the owner decides: approved means the user is in the household, declined shows “The owner declined your request.” The user can cancel a waiting request, has one waiting request at a time, and unanswered requests are removed after 7 days. After v1 (2026-11-10): the waiting screen says the owner gets no notification ("The owner doesn't get a notification, so let them know you asked"); the gate asks for the code from "someone in the household" (every member can share it); the confirm dialog asks once ("Join *The Smiths*?" / "The owner needs to approve your request…"); an expired request says a new request with the same code works. A request that disappeared is Expired only when no membership exists: when the owner approved it and it was removed before the joiner's app opened, the app finishes the join instead. The job removes approved requests only once the join is finished (10.1). A wrong or replaced code shows: “That code doesn't work. It may have been replaced: ask someone in the household for the current one.” (after v1, 2026-11-13; it said “isn't active” when codes could be paused). A code's `active` field is no longer read (R12): a code an older build paused takes requests again once the 2026-11-13 rules are deployed (DECISIONS).

**Household settings** (Settings > Household)

- Member list with names, roles and join dates.
- Invite card: the code in large type with Copy and Share (system share sheet with a ready-made message). Every member can see and share the code. The owner also gets Regenerate (*New code*), which ends the old code at once. After v1 (2026-11-13, review R12) there is no Pause or Resume joining: the owner approves every request, and New code covers a leaked one; codes still carry `active: true` for older builds, and the screen no longer listens to the code document.
- Owner only: approve or decline join requests; after v1 (2026-11-10) each shows the name, the requester's masked sign-in email (first two characters, `***`, the domain; computed by the rules from the sign-in, so it can't be someone else's) and "Sent: 3 hours ago", under "Only approve people you invited." Requests from builds before this have no email and show the rest (a badge on the Settings avatar and a Join requests card on Home show when someone is waiting; there is no push notification for this in v1), rename household, remove a member, transfer ownership, delete household. Members: leave household.
- **Clear household data (owner, after v1).** The last button in Settings, after Delete account. It asks "Clear all household data?" (what goes, what stays, that it cannot be undone, and to export first), then the owner's password. It leaves the household as it was when created: the name, members, roles, invite code and join requests stay; every product, pantry purchase, recipe, grocery item and planned meal is removed (as tombstones, so other phones' changes-only listeners drop them, 9.8), extra grocery lists, recent activity and Insights counts are deleted, and the default list is named Groceries again and forgets its store order. Last it logs "Sam cleared the household's data"; Recent activity never shows events from before the newest clear, so stale events cached on a phone stay hidden. There is no Undo; the nightly export and restore (13.4, RUNBOOK) bring the data back if needed. After v1 (2026-11-10) it refuses to start offline (it reads from the server first, so the owner sees "You're offline") and, when a chunk times out on a connection that stalls half-way, it queues the remaining chunks behind it instead of stopping, so the clear is never left half done; it then says "Clearing will finish when you're online".

**Rules.** A user belongs to one household at a time; joining another requires leaving the first, with a confirmation that explains the local copy is cleared. The owner cannot leave without transferring ownership or deleting the household. A removed member is returned to the household gate within seconds while the app is open (membership watcher, section 9.1).

### 5.2 Pantry

**List**

- One row per product that has stock, plus every **staple** (a product with Keep at least set), which stays visible at 0 with an “Out” badge. Since beta 2 (2026-10-18, DECISIONS) each purchase is its own row instead (`PantryItems`), and a staple with nothing left keeps its Out row; v1's per-product list code was deleted after the 2026-11 review (CODE-01). Only Home's Running low lines still total a product's stock (`PantryList.rows`).
- A row shows name, total quantity and unit, a status chip for the soonest expiry (color plus icon plus text, such as “Tomorrow”), and the location icon. Tapping expands the row into its batches (quantity, date, location, date type). Since beta 2 a row shows its purchase's amount, date and place, and tapping it opens the Edit window (9.4), which also renames the product; v1's product detail and its save (`updateProduct`) are gone.
- **Sort:** soonest expiry (default), name, recently added. **Group by:** none (default), location, category.
- **Filter chips:** Fridge, Freezer, Pantry, Other, Needs attention (expired or expiring within 3 days), Expired, Low stock. After v1 (2026-11-12, review R8) *Needs attention* replaced *Expiring soon*, which left the expired out: it is the one set Home's card counts and the daily reminder opens (5.4, 5.6), with its expired folded at the top as below. A search bar filters by name as you type. After v1 there is no *All* chip: no filter is the default, and tapping the chosen filter again clears it. In the 2026-11 look (19.10) the location chips show their location's icon.
- Empty state: one sentence explaining the app and a button, “Add your first item”.
- **Search and filters (owner, 2026-11-21).** The search field shows what was typed at once, from its own copy in the view model, never the list's state, which comes back later (it had lost and doubled letters while typing). A new search, filter, sort or grouping starts the list at its top: with nothing in Use first, the default view and a filtered list share row keys, so taking a filter off left the list on the filtered list's first row with the rows above it out of sight. Both apps.
- **After v1, row and list polish.** The status chip alone shows the status (a second status icon at the start of the row only repeated it). The amount at the end reads "× 3" for pieces, nothing for a single piece, and "500 g" for other units; an Out row shows only its badge. Names sort the way people expect: case and accents ignored and numbers by value ("Bag 2" before "Bag 10"), here and on the grocery list and in Recipes A to Z. With **3 or more expired** purchases, and no search or Expired filter, they fold into one header at the top ("65 expired items" with *Show*), so a backlog does not push everything else off the screen. Each is thrown away from its row as usual (the bulk *Throw away all* button was removed after beta 7, owner feedback).
- **The 2026-11 look (19.6, 19.12; step 3, 2026-11-16).** Rows are grouped (19.5) and each starts with a status tile; the second line says the status in words in its color, then the location and Opened ("Tomorrow · Fridge · Opened"), and an Out staple's says "Out" in the Expired color. In the default view (soonest expiry, no grouping, search or filter) the list splits into *Use first* (the Needs attention set) and *Everything else*, with the folded expired line as Use first's first row. The line above the title counts every row of the unfiltered list ("8 items"). The empty pantry shows a kitchen in a leaf above its sentence.

**Add item** (extended FAB; a bottom sheet that grows to full screen)

| Field | Behavior |
| --- | --- |
| Name | Required. Autocompletes from existing products (case-insensitive). Choosing one fills unit, location, category and typical shelf life. After v1 (2026-11-13, UX-09) the keyboard opens on it when the sheet opens from Add item (not from an Out row, whose product is chosen already, and not in Edit) |
| Quantity | Required. Stepper plus keyboard, decimals allowed, default 1 |
| Unit | pcs, g, kg, oz, lb, ml, l, fl oz. Default pcs. Fixed once a product has stock (no conversion in v1); after v1 Add to pantry fixes it the same way (5.3) |
| Expiration date | Date picker plus quick chips: +3 days, +1 week, +2 weeks, +1 month (after v1: no *No expiry* chip, since no date is the default; tapping the chosen chip again clears the date). Pre-filled from the product's typical shelf life when known |
| Location | Fridge, Freezer, Pantry, Other |
| Category | Produce, Dairy and eggs, Meat and fish, Bakery, Frozen, Pantry staples, Drinks, Snacks, Other. After v1 (2026-11-13, UX-09) a name with no product takes the aisle Groceries guesses for it (5.3: "Greek yogurt" is Dairy and eggs), until a category is picked by hand; Other when nothing matches. Before, every new product was Other |
| Keep at least (advanced) | Optional number. Helper text: “Adds it to the grocery list when you have fewer than this” (after v1, 2026-11-12; “Add to the grocery list when fewer than this remain” before). Since the beta it shows only with the switch below on (required, above 0) |
| Keep some in stock (advanced) | Toggle, called *Add to grocery list automatically* until 2026-11-12 (after v1, review UX-07: it named the mechanism, not the idea, and the idea only appeared once it was on). While off it says “Puts it on the grocery list when it runs low”. Since the beta, turning it on shows Keep at least and an optional buy quantity, and turning it off clears both. After v1 (2026-11-09) a product from before the beta with Keep at least but the switch off (an *older staple*) keeps its Keep at least when edited with the switch left off, since the form never showed it |

**Date type and notes (after v1).** The form has neither since beta 2 (DECISIONS 2026-10-18): every new purchase is Use by and has no note. Purchases saved before keep their date type, so an old Best before item still reads "Past its best before date. Check before using" and is counted with the expired; a new one past its date reads "Expired … Throw it away", rice and pasta included. The owner kept it so (2026-11-13, review UX-04, DECISIONS): the status table below describes both wordings because both exist.

After v1 the form is laid out by how often each field changes: Name; a compact quantity stepper beside the Unit (stacked at large text sizes); the date button with its four quick chips as equal tiles in one row (two rows at large text sizes); Location as four equal tiles with an icon over the label (a 2 by 2 grid at large text sizes; the chosen one has a border, a fill and a bold label); then Category, which the product or the name usually fills in. Add to pantry uses the same date chips and location tiles. In the 2026-11 look (19.9, 19.10; step 3) every label sits above its field, the stepper joins − and + to the amount, the quick dates are a connected group (radio buttons from 1.5× text), and the chosen location tile is filled `primaryContainer`, rounder and heavier.

Saving writes a batch. If a batch for the same product, date and location already exists, its quantity is incremented instead of creating a duplicate.

**Batch actions** (swipe or row menu; every swipe action MUST have a menu equivalent): −1 and +1 (*Use 1* and *Add 1* in the menu; the form's stepper says *Remove 1* and *Add 1*, after v1, 2026-11-13), **Used up** (deletes the batch), **Throw away** (asks a reason: Expired, Spoiled, Didn't like it, Other; deletes the batch and logs the event), Edit, Move location. Every destructive action shows an Undo snackbar for 8 seconds, or longer when the phone asks for more: on Android (after v1, 2026-11-12) the time comes from the accessibility manager's recommended timeout, which follows Accessibility > "Time to take action" and what a screen reader requests, and is never under 8 seconds (review A11Y-02). The web app (2026-11-13) keeps 8 seconds, not counting the time its snackbar has focus or the pointer over it: the timer waits and then goes on with the time that was left, since a web page can't read the iPhone's accessibility settings.

**Product detail** (tap the product name): totals, all batches, product settings (unit, category, default location, Keep at least, automatic grocery add, buy quantity), rename, typical shelf life (editable) and the last 20 events.

**Learning shelf life.** When a batch is saved, set the product's `typicalShelfLifeDays` to the median of (expiry date minus date added) over its last 5 batches. It is only used to pre-fill the date.

**Opened (after v1).** A sealed batch's ⋮ menu has *Mark opened*: "Use within" 1 day, 3 days, 5 days, 1 week, 2 weeks or 1 month (and the product's remembered number if it is another), starting from what was chosen for that product last time (`openedShelfLifeDays`), else 1 week. The batch's date becomes today plus that, but never later than the date it has, and the sheet shows the result. For a batch kept in pieces with more than one, it asks how many were opened (default 1) and moves those to their own batch. Opened batches say "Opened" (in their second line since the 2026-11 look, with an icon before it; ", opened" to screen readers), no longer offer Mark opened, and new stock is not added to them. Undo puts the batch back.

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

**Lists.** Each household has a default list named “Groceries”. Users can add more lists (for example “Costco”), rename and delete them. Low-stock items always go to the default list. After v1 the list's name in the top bar is the switcher ("Groceries ▾"), with the lists and New, Rename and Delete list; a bare list icon did not say which list was open.

**Items.** Name (required), optional quantity and unit, note, category (used to group by aisle), checked state, who added it and when, and a source badge (Manual, Low stock, Recipe). Adding a name that is already on the list unchecked shows “Already on the list” with Increase quantity or Add anyway; after v1 (2026-11-13, UX-18) it says how much: “Add 2 more to the 6 on the list, or add it as a separate item?” with *Add more* and *Add separately* (an item without an amount counts as 1). After v1 (2026-11-11) Increase quantity is offered only when the units match or one side has none; for "2 kg rice" with "Rice 500 g" on the list it says "Rice is already on the list as 500 g. Amounts in different units aren't added together." with Add anyway only (there is no conversion). After v1, tapping an item's Edit also changes its name; the item then links to the household product of that name, if there is one, or to none. After v1 each source badge has its own icon (Low stock, Recipe) and Back in stock sits on the same line as the badges with its Remove.

**What the app did by itself (after v1, 2026-11-13, review UX-08).** An item the restock engine added says **Added automatically** (it said "Low stock", which read as a status), and its Edit sheet says why: "Added automatically: you have 0 and keep at least 2." (from the product as it is now; "Added automatically when it ran low." when the product is gone or has no Keep at least). When its stock is back, the row shows one line, **Back in stock · Remove**, instead of a Low stock badge beside a Back in stock one. Once a list has learned a store order and has two aisles or more, a hint under the first aisle says "Aisles follow the order you shop in. They move as you check things off in the store." until *Got it* on that phone (a local preference, `aisle_hint_done`). Aisle and In cart headers are in sentence case ("Dairy and eggs", not capitals; 6.9).

**Aisle guess (after v1).** An item typed without a matching product gets a guessed aisle so it groups (and follows the store order) instead of landing in Other: first from the household's own products (the product with the most words that are all in the name, so "oat milk, barista" takes the household's Oat milk aisle), then from a built-in English word list where longer phrases win ("peanut butter" is a pantry staple, "frozen peas" frozen). After v1 (2026-11-13, review LOGIC-17) modifier words decide before the food word they come with: how it is kept ("frozen", "canned", "tinned") wins over everything but a phrase that contains it ("frozen ground beef" is frozen), and what kind of product it is ("broth", "stock", "sauce", "paste", "dressing", "juice") wins over single food words ("chicken broth" and "tomato paste" are pantry staples, "apple juice" a drink). Before, the longer single word won, so "chicken broth" went to Meat and fish. Unknown names stay in Other. The same guess applies to recipe ingredients that link to no product.

**Interactions.** A quick-add field stays at the top; typing suggests pantry products and previously used names. It reads a leading amount and unit ("2 kg rice"); after v1 (2026-11-11) it also takes the recipe parser's words for the pantry's units ("3 lbs apples", "2 litres milk", "1 kilo flour"), a unit right after the number ("2l milk", "500g rice"; a name never, so "7up" stays a name), and treats an amount of 0 as none ("0 eggs" is eggs). Tap to check (strike-through; the item moves to a collapsed In cart section). After v1 (2026-11-13, review UX-10) a **swipe right checks the item off** (or, in the cart, puts it back) and a **swipe left deletes it** with Undo; while the row moves, the background shows a check and "Check off" ("Back to list" in the cart) or a trash can and "Delete". Before, either swipe deleted, the opposite of what Pantry's swipe right (Used up) teaches. The tap and the ⋮ menu (Edit, Delete) do the same (6.8). Items are sorted by category, then time added. *Clear cart* (called Clear checked before 2026-11-13) removes everything in the cart, with Undo. In the 2026-11 look (19.5, 19.10; step 4, 2026-11-16) the line above the list's name says how many are left to buy ("4 to buy"); the quick-add field is the page's round field with a filled + at its end; each aisle is a group of rows under its pinned heading, every row with its checkbox, the amount beside the name and the source as a label chip ("Added automatically", "Meal plan", "Recipe"; Back in stock in the person chip's colors); In cart is a heading in `onSurfaceVariant` with Clear cart beside it, its rows a step darker and struck through. Add to pantry's lines are a group of rows that each open in place, with Add item's stepper, unit, quick dates and location tiles.

**Store order (after v1).** Aisles (item categories) on each list follow the order the household walks that store, learned from check-offs and saved on the list (`aisleOrder`), since lists are often for different stores. During a trip (check-offs on one list with no gap over 2 hours), when the first item of an aisle goes into the cart and the saved order puts that aisle before one already visited this trip, it moves to just after the last visited one; the first aisle of a trip changes nothing. A few trips settle the order; aisles never seen keep the default order after the learned ones. Items without a category stay in Other.

**Done shopping, then Pantry.** When at least one item is checked, an **Add to pantry** button appears. It opens a sheet with one row per checked item: name, quantity, unit, expiry quick chips (pre-filled from typical shelf life) and location. After v1 each row is one line summing up what will be added ("2 · Fridge · Oct 26"); tapping it opens that row's editor, and the button reads **Add all N to pantry** when nothing is skipped (Remove from the list when everything is). An invalid amount opens its row. Confirming creates the stock batches, creates products that do not exist yet, and removes those items from the list in a single batched write. Skip removes an item without adding stock.

After v1 (2026-11-11):

- **It adds to the product the item is linked to** (a Low stock item, a recipe item that matched a product, a quick-add of a known name), even after the product was renamed; only an item with no link, or whose product is gone, goes by its name. Before, a renamed staple got a second product and stayed Out.
- **The unit follows the product.** For a product with stock the row's unit is the product's and can't be changed ("Set while in stock", as in Add item), and the item's amount is used only in that unit or when the item has none. "2 kg rice" for Rice kept in grams starts from the amount last added to Rice, or empty to type, instead of adding 2 g.
- **What was typed stays** while the sheet is open: through rotation, Android closing the app in the background, and other members checking items off or unchecking them. Each row's edits are kept by item, so a new check-off adds a row and an unchecked item's row leaves without touching the others; rows nobody touched follow their item. Closing the sheet clears them.
- **Undo** (8 seconds, "Added to the pantry" or "Removed from the list" with Undo) takes the addition back in one write: the new purchases and any products it created are deleted (tombstones), an amount it added to an existing purchase comes off again, the product's learned shelf life and last amount go back, its activity entries go, and the items come back to the cart as they were. A recipe name it taught an existing product stays.

**Two phones, one cart (after v1, 2026-11-09).** When two people press Add to pantry for the same items, at the same moment or one of them offline in the store, the items go into the pantry once: the first write to reach the server wins, and the rules refuse the other phone's whole write, its purchases and new products included (8.5). That phone rolls it back and says why: "Sam already took these off the list. Nothing was added to the pantry." The same holds for Clear checked or a delete of items another member already took off the list ("Sam already took that off the list."), and for an item added to a list as another member deletes it: "That list was deleted, so the change wasn't saved." The name comes from the member list; when it is the same account on another phone, or someone who left, the message says it without a name.

**Keep at least and automatic add.** Each product has *Keep at least N* and *Add to grocery list automatically*. When total stock drops below N, the product is added once to the default list as a Low stock item with quantity equal to the buy quantity (default: N minus current stock, minimum 1). The state machine is in section 9.6. Guarantees:

- It is added exactly once per dip, even if two phones notice at the same moment. After v1 (2026-11-09) usually only one phone writes it: the phone whose own change caused the dip at once, the others only after a few seconds and only if it still needs writing.
- If someone deletes the auto-added item, it is not added again until stock has recovered to N or more and then dropped again.
- When stock recovers while the item is still unchecked, the item stays but shows “Back in stock” with a one-tap remove. This includes Undo of Used up or Throw away on the last batch: the stock comes back and the item the dip added stays, with Back in stock (DECISIONS 2026-11-09).
- After v1 (2026-11-09): an item that comes back after Add to pantry, Clear checked or a delete comes back **unchecked**, to buy, never in the In cart section; an item still on the list keeps its checked state.
- After v1 (2026-11-09): a phone's first sync never adds staples. The engine waits until the phone has read both the products and the stock from the server (or read them in full before), so a staple whose stock has not arrived yet is not taken for empty.

### 5.4 Expiry reminders (behavior)

The system design is in section 10. Behavior:

- Each member gets **one notification per day** at the hour they choose (default 9 AM local), and only if something needs attention.
- It groups items by stage: **Throw away** (expired and not yet reported), **Today**, **Tomorrow**, **In 2 to 3 days**. Each of the stages 3, 2, 1 and 0 days can be switched off. Best-before items use softer wording (“past best before”).
- Expired items are reported once by default. Setting: Expired reminder = once, every day for two weeks, or never. The daily mode lasts as long as the digest's stock read reaches back, 14 days (10.1); after v1 (2026-11-13, review LOGIC-07) the setting says so ("Every day for two weeks"; it said "Every day until it is removed").
- Tapping the notification opens the Pantry filtered to Needs attention (5.2). After v1 (2026-11-12, review R8) the digest, Home's Needs attention card and that filter use **one set**: every purchase in stock that has expired or expires within 3 days (`NeedsAttention` in both apps, `needsAttention` in the job). The digest lists that set narrowed by the member's own choices (stages, expired once, daily or never) and by its stock read, which reaches 14 days back (10.3). Before, the tap opened *Expiring soon*, which left out the expired items the digest had just named. The route keeps its old name, `/pantry?filter=expiring`, so released builds still understand it (they open Expiring soon).
- After v1 (5.9), a member listed as a cook for today's meals gets a last line "You're cooking: Pancakes (breakfast), Tacos (dinner)." (meals not yet cooked, breakfast first). With nothing expiring, the meals make their own notification ("You're cooking Tacos for dinner", or "You're cooking 2 meals today"), and tapping it opens the Plan tab. Settings > Notifications has **Meals I'm cooking** (on by default) under the daily reminder.
- Permission is requested after a short explanation, once per phone (after v1, 2026-11-13, review UX-02; the rule is `NotificationPrompt` in `feature/notifications/domain`, with tests). A household that **opens with dated food** on this phone asks at once, on whichever tab is open: a member who joins, or anyone signing in on a new phone, is asked without adding anything. A household that opens with none (a new one) asks once it has **three dated items**, whoever added them, so the owner's first items are not interrupted (2026-10-19; it was the first item). Dated food is a purchase in stock that expires today or later; "opens with" is the first pantry read from the server, so a new phone's empty cache doesn't count as a new household. It asks only while the phone blocks notifications and the member's daily reminder is on, and after the app tour (5.8). A phone that already allows notifications is marked explained without asking. Before, only Pantry's Add item asked, after this phone saved the third dated item: members who never added food by hand were never asked. The explanation names the time and that the reminder comes after it, often a few hours later ("It comes after 9:00 AM, often a few hours later."; until 2026-11-22 it said "around 9:00 AM", 10.4). If denied, Settings > Notifications shows a banner with a button that opens the system settings.
- **When this phone blocks reminders** the member wants (after v1, review UX-02): once the explanation was answered, Home's Needs attention card ends with "Reminders are off on this phone · Turn on" (with a crossed-out bell), and Settings' Notifications row says **Off on this phone** instead of the time. Turn on asks for the permission while Android still asks, and otherwise opens the app's notification settings; both are checked again when the app comes back. Before, Settings said "Daily, shortly after 9:00 AM" while Android dropped every digest.
- **You're set** (after v1, owner's decision of 2026-11-08, review UX-03): right after a Turn on that works (the explanation, Home's line or Settings' banner), a local notification on the expiry channel says "Spoil Not reminds you once a day, some time after 9:00 AM, when food is about to expire. Tomorrow's reminder: Chicken, Spinach and 2 more." (or "Nothing is due tomorrow."). Tomorrow's items are what the member's own stages pick for tomorrow, soonest first, one name per product, up to three; expired food is left out, since whether it is named again depends on what was already reported. It opens Pantry on Needs attention, like a digest. Nothing is shown when the daily reminder is off. `YoureSet` (domain) builds it, with tests.
- Settings has **Send test notification**, which fires a local notification to verify permission and channel.

Example for three items. Title: “3 items need attention”. Body: “Throw away: Milk. Tomorrow: Yogurt, Eggs.”

### 5.5 Recipes

**Recipe box.** Cards with image, title, total time and a favorite star. Search by title, ingredient and tag. Chips: Favorites and user tags; after v1 (2026-11-13, R16) there is no *All* chip, as on Pantry: no filter is the default, and tapping the chosen chip again clears it. Sort: recent, A to Z. After v1 a recipe without a photo shows its first letter on one of the theme's container colors (chosen by the title, so it stays the same), titles always take two lines and the time row keeps its place, so cards side by side match. An empty box offers the three ways to add one right there: Paste a link, Browse the web, Create manually.

**Add a recipe** (FAB with three choices)

1. **Import from the web:** paste a link, or open the built-in browser.
2. **From the share sheet:** in any browser or app, Share, then Spoil Not. The app opens on a preview of the extracted recipe with a Save button.
3. **Create manually:** title, optional image URL, servings, times, ingredients (one per line), steps (one per line), notes, tags.

**Built-in browser.** A web view with an address and search bar. After every page load the app checks for a recipe. When one is found, a bottom bar slides up: “Recipe found: *Title*. Save”. This is the Paprika-style experience you described; details are in section 11.

**Recipe detail.** Hero image, title, chips (time, servings, source site), a servings stepper that scales the quantities that could be parsed, an ingredient checklist, numbered steps, notes, an Open original link, and the actions Favorite, Edit, Share and Delete. In the 2026-11 look (19.12, step 6) a recipe without a photo has its letter in a leaf as the hero, the facts are pills, the checklist and the steps are groups of rows, and Back, Favorite, Edit and ⋮ are round buttons over the hero.

After v1 (2026-11-13, review LOGIC-13) the stepper goes up to 100 or the recipe's own servings when it makes more ("Makes 120 meatballs"; recipes keep 1 to 1,000), and a scaled amount under 1/16 reads "a pinch of" and the ingredient, without its unit ("1/8 tsp salt" at a tenth is "a pinch of salt"; it read "0 tsp salt"). The words come from strings.xml (`recipe_scaled_pinch`).

After v1 (2026-11-13, review UX-12, R9, R10, R13):

- **One servings control.** The servings chip shows only when there is no stepper (no ingredients, or servings only as text such as "Makes 12"); it repeated the stepper's number a few lines down.
- **One source line.** "By …" shows only when it names someone other than the site chip ("By Kenji López-Alt"); "By recipes.example.test" or "By BBC Good Food" under the bbcgoodfood.com chip is left out (`SourceUrl.namesSite`: letters and digits, with or without the site's ending).
- **Start cooking** (it was *Cook*) is a full-width button in a bar under the content, not a floating button, so it never covers *I cooked this*; the content scrolls clear of it. Shown when the recipe has steps.
- **Delete** deletes at once and goes back to the recipe box, which offers Undo (6.1); there is no "Delete recipe?" dialog.
- **Add to grocery list** with nothing selected says "Select items to add" and is disabled (it read "Add 0 items").

**Cook mode** (*Start cooking*). The screen stays awake, large type, one step at a time with swipe, ingredients in a bottom sheet. In the 2026-11 look (19.12, step 6) Back and the recipe's name are at the top with "Step 1 of 2" as a chip, a bar per step shows where the cook is, the step sits on a card with its number in a leaf, and Back, Ingredients and Next are joined at the bottom.

**Add to grocery list.** Choose all or some ingredients. Ones that look like pantry items are pre-unchecked and tagged “In pantry” (simple name matching, section 11.5). Added items carry the source Recipe.

After v1, each ingredient is added as something you would buy, and the sheet shows it under the line ("Adds as Onions × 3 lb"):

- **Shopping name.** Size, preparation and freshness words are dropped ("1 extra-large onion, finely diced" becomes "Onion"), as is anything after a comma, in parentheses, after "or", and phrases such as "to taste" or "for garnish". Words that change what is bought stay (ground, whole, frozen, baby, hot).
- **Linked to the household's product.** If a product means the same thing, in stock or not, the item takes its name, unit and category and its product link, so Add to pantry adds to that product instead of creating a near-duplicate. A product matches when its words are all in the ingredient with at most one more ("Black pepper" for "ground black pepper"); the most shared words win. A product with more words than the ingredient never matches ("Green onions" is not "onion"). After v1 (2026-11-13, review LOGIC-16) words are compared in a form where both singulars of a plural meet ("cookies" links to Cookie, "chilies" to Chili, "brownies" to Brownie), "-ves" meets "-f" and "-fe" ("bay leaves" to Bay leaf, while "olives" still meets Olive), and "extra-virgin" or "extra virgin" is a grade, not a word ("extra-virgin olive oil" links to Olive oil); the shopping name drops it too. An unchecked item already on the list for the same product counts as already on the list.
- **Shopping amount.** Recipe measures (teaspoons, cups, grams) are never the item's amount. A linked product brings its usual amount: its Buy quantity, or how much was added last time (`lastAddedQuantity`). Otherwise only a count is kept ("3 eggs", "2 cans tomatoes").
- **Note.** The recipe and its line ("Pancakes: 1 extra large onion, diced"). Lines that become the same item are added once, with their notes joined and counts added up.
- **Change.** Each line has a Change button that opens "Add to the list as": a name field with the household's products that contain what is typed. Picking a product uses its name and usual amount.
- **I cooked this (after v1).** Next to Add to grocery list, *I cooked this* opens "What did you use?": every in-stock product the recipe uses (matched as above), with how much the household has and an amount at the detail screen's servings. Lines the pantry can count start checked with the recipe's amount (a count for a product kept in pieces, or the same unit: "500 g rice" from rice in grams; after v1, 2026-11-13, LOGIC-13, a scaled count is rounded up to whole pieces like the grocery list, so 1.5 times "3 eggs" takes 5, not 4.5, while the recipe's own "½ onion" stays as written); measures it cannot count ("2 tbsp soy sauce" from a bottle) start unchecked with the whole stock, so checking means "finished it". Amounts can be changed. The amounts come off the batches that expire first; a batch taken to 0 is used up and counted for Insights (5.7). Recent activity shows "Sam cooked Omelette". One Undo puts everything back.
- **Learning.** A change teaches the household's app the recipe's name for that product (`aliases`, name keys, at most 30 per product). A change to an existing product is remembered when the items are added; one to a new name rides on the grocery item (`aliasOf`) and is remembered when Add to pantry creates or finds the product. From then on that ingredient links to the product by itself, before any word matching. Changing a line back to the recipe's own name teaches nothing.

**Duplicates.** Saving a URL that is already in the box shows “Already saved” with Open or Save a copy.

### 5.6 Home, activity and settings

**Home** (first tab) has these cards: *Needs attention* (expired and expiring within 3 days; opens the filtered Pantry), after v1 *Today* (today's planned meals with their cooks and Cooked, breakfast first, and *Open plan*; shown only when something is planned, 5.9), after v1 *Use it up* (below), *Grocery list* (what is left to buy and what is running low; opens the list), *Your kitchen this month* (after v1, 5.7; opens Insights, called *Your kitchen* on screen) and *Recent activity* (last 10 events such as “Sam added Eggs to Groceries”). The household owner also sees a *Join requests* card while someone is waiting for approval. After v1 (2026-11-15, the new look, 19.12) *Your kitchen this month* comes right after *Today*, before *Use it up*, and the whole card opens Insights.

After v1 (2026-11-12, review UX-01, UX-06, UX-07, R1, R2, R8):

- **Needs attention** is the one set of 5.4: every purchase that has expired or expires within 3 days, one row per purchase as in Pantry. It lists what can still be used first (today, tomorrow, in 2 to 3 days, soonest first, up to four) and folds the expired into one line ("8 expired items to throw away or check"), so a backlog of old food no longer hides what to use today. *See all N in Pantry* counts the whole set and opens Pantry's Needs attention filter, which lists exactly those purchases; when everything is already on the card (no expired, four or fewer to use) the button says *Open in Pantry* instead (after v1, 2026-11-13, UX-18: "See 1 in Pantry" sat next to the one item shown). Before, the card listed the oldest expired products first and its button opened Expiring soon, a list without them.
- **One Grocery list card** instead of *Low stock* and *Grocery list*: the number of items to buy, then up to four products running low with their Out or Low badge ("and 2 more" after that), and *Open list*. Keep at least comes only with Keep some in stock, so every low product is already on the list; the Low stock card said it twice and sent people to Pantry, where nothing can be done about an empty bottle. Pantry's Low stock filter stays.
- **A new household is told how to start** instead of seeing "all done" checks: with nothing in the pantry Needs attention says "Add food with its date, and what to use first shows here." (with a kitchen icon, not a check), and while no product has Keep some in stock on the Grocery list card says "Turn on Keep some in stock for food you always want at home, and it goes on the list when it runs low." User text no longer says "staples".

After v1 (2026-11-13, review UX-02), while this phone blocks reminders the member wants, Needs attention ends with one line "Reminders are off on this phone · Turn on" (5.4), in its full and its compact form. It sits with the food the reminders are about, under the offline banner and the quota banner (6.7) rather than as a third banner.

After v1, a card with nothing to show (nothing needs attention, no activity yet) is one compact row with a check and its title, so the cards with something to do stand out.

**Use it up (after v1).** Up to three saved recipes that use food expiring today or within 3 days (not expired: the usable part of Needs attention), with what each uses and when ("Uses Spinach (Tomorrow), Yogurt (3 days)"); tapping one opens the recipe. Recipes using the most expiring products come first, then the one using the soonest, then favorites. After v1 (2026-11-12, review UX-05) recipes already planned from today to the end of the week, cooked or not, are left out, on Home and in Plan's picker: two of three suggestions used to be tonight's and this morning's planned meals. The food they will use is not counted as taken. Ingredients are matched to products the same way as Add to grocery list (5.5: shopping name, remembered names, word match). The card shows only when there is a match. Home therefore reads the recipes on every open (with the changes-only listener, 9.8), not only after the first visit to Recipes. To stay quick with a large recipe box, product words are worked out once per pass, and only lines sharing a word with an expiring product (or one of its remembered names) are matched; every link needs such a word, so the result is the same.

**Activity log.** Clients write events for: pantry item added, grocery item added, recipe saved, member joined, items thrown away (with reason), and after v1 a recipe cooked and the household's data cleared. Events older than 30 days are pruned by the reminder job. After v1 (2026-11-12, review UX-16, R15) checking off a grocery item is no longer logged: the list shows who checked what, and one shopping trip pushed every other event out of the ten lines. Check-offs written by older builds still show until newer events push them out.

**Settings.** Account, Household, Notifications, Appearance (System, Light or Dark; Colors: Wallpaper on Android 12 and later, Spoil Not green or a preset theme (6.4; the chosen swatch's check sits on its own dot so it shows on any color); metric or imperial units shown first in the unit menus, *Units shown first* after v1, 2026-11-13; it was *Units for new items*, though new items stay in pieces), Data (Export household data as JSON; after v1 its line reads "Save a copy of all your household's data as a file."), About (version, licenses, privacy summary), and a Crash reports toggle. After v1, *Take the app tour* starts the tour (5.8) again. From 2026-11-17 (the new look, step 7, 19.12) the page reads, top to bottom: the account (a highlight card), Household and Notifications, Appearance, Data (Export household data, which starts the export with a tap on its row, and Send crash reports), the tour and About, then Sign out, Delete account and, for the owner, Clear household data.

### 5.7 Insights (after v1)

How the household is doing at using food before it spoils, shown so it feels good to look at rather than as a scolding.

- **What is counted.** A batch (one purchase) counts once when it is finished: **used** when it is used up (swipe right, Used up, Use 1 or an edit down to 0) and **thrown away** with its reason. Using part of a batch counts only when it is finished. Counts are of batches, not quantities, because units differ. Undo takes the count back off the same month. Counting starts with the version that added it (October 2026, which the note on the screen now names); nothing earlier is filled in.
- **Home card** *Your kitchen this month*: a small ring with the share used (solid for used, dashed for thrown away), the counts ("11 used · 1 thrown away"), a friendly line by share (90% or more "Amazing! Almost nothing went to waste.", 75% "Nice work!", 50% "Good start.", below that an encouraging line pointing to Needs attention) and *See your kitchen* (*See insights* before 2026-11-13: one screen had three names; it is *Your kitchen* everywhere, Settings' clear and delete texts included). With nothing finished yet, it explains what it will show.
- **Insights screen:** this month's ring with a legend; the last 6 months as bars (solid used, striped thrown away, the number above each bar; after v1 each month can be tapped, the latest selected first, and its details show below: the counts, the share used and the reasons, e.g. "Thrown away: Expired 4, Spoiled 1"); the best month (highest share used, at least 5 batches, when two months qualify); the streak (days since anything was thrown away, or days of counting with nothing thrown away); why things were thrown away (striped bars by reason); a note on what is counted. From 2026-11-17 (the new look, step 7, 19.8) this month's name sits above the title, this month is the `primary` card Home has (the percentage large, the counts, the ring and the friendly line), the months and the reasons are plain cards with the chosen month's details in a box of the page's color, and the streak is an accent card with the days large ("15 days" over "with nothing thrown away").
- **Accessibility.** Thrown away is never shown by color alone: dashed in the ring, striped in bars, always with a text legend and numbers. Each chart has a spoken summary; the months chart is read one month at a time, each a tab that says whether it is selected, and the selected month is marked by a tinted background and a bold label, not color alone. Charts grow in over 700 ms, or appear at once with reduce motion on; from 2026-11-17 they grow in the draw phase (6.4, review PERF-03). Colors come from the theme (primary for used, tertiary for thrown away).
- **Months** follow the phone's local date.

### 5.8 App tour (after v1)

A short follow-along tour teaches new users the app and its main actions. The owner chose it instead of two other ideas from the post-v1 list.

- **When.** Once per phone, the first time the household app opens (a local preference, `tour_done`; nothing is stored in Firestore). Finishing it or tapping *Skip tour* at any step ends it for good; Settings > *Take the app tour* starts it again.
- **How it looks.** The screen is dimmed except for a rounded hole around one element, outlined in the theme's primary color, with a card next to it ("3 of 9", the text, *Skip tour* and *Next*; *Done* on the last step). The card goes below a target in the top half and above one in the bottom half, or covers a target too tall to leave room. Taps on the dimmed part do nothing; taps in the hole reach the app.
- **Follow along.** Steps that point at a tab move on when the user opens that tab, and steps that point at a button (Add item, Add recipe) move on when it is tapped, so the user learns by doing. *Next* always works too, and opens the tab the next step needs, at its root: taken from Settings, the first step closes Settings even on the Home tab (after v1; it used to stay open with nothing highlighted).
- **Steps.** Welcome; Home's Needs attention card (after v1 its text also names Today, Use it up and the month's card); tap Pantry; Add item (dates and reminders); pantry rows (swipe right used up, left throw away, Undo, tap to edit, ⋮ for Mark opened); tap Plan (after v1, 5.9); tap Recipes; Add recipe (share sheet, built-in browser, missing ingredients to the list); tap Groceries; the quick-add field (check off with a tap or a swipe right, swipe left deletes, Add to pantry; after v1, 2026-11-13); the Settings avatar (invite the household, replay the tour). A target that is not on screen (an empty pantry has no rows) shows its card in the middle.
- **Accessibility.** The card is a pane announced politely when it changes, every step has a text and a button, so it works without seeing the highlight, and text follows the font scale (buttons wrap at 200%).

### 5.9 Meal planner (after v1)

A fifth tab, **Plan** (Home, Pantry, Plan, Recipes, Groceries), shows one week of meals for the household, shared by every member.

- **Week.** The header shows the week ("Oct 26 – Nov 1") with Previous and Next week buttons, and *Go to this week* when another week is shown. In the 2026-11 look (19.12, step 5) the week is a round bar under the title with the row of the week's days under it, and both stay in place while the days scroll; "This week" sits above the title while this week shows. The week starts on the first day of the phone's region (Sunday in the US, Monday in most places). This week opens scrolled to today. In the web app (after v1, 2026-11-12) Settings > Appearance > *Week starts on* chooses it (Automatic, Sunday, Monday or Saturday), because a web app cannot read the iPhone's own First Day of Week setting (18.1).
- **Days.** One card per day with three slots, **Breakfast, Lunch and Dinner**. Today has a thicker border and a *Today* label (never color alone); past days have a quieter title. In the 2026-11 look (step 5) today is the highlight card with a *Today* badge and every other day a heading over a group of rows: an empty slot is a row with its name and **+**, a slot with meals has a row per meal (the slot's name over the meal's), and the slot's first meal carries its **+**, so each slot has one.
- **Adding.** **+** on a slot opens "Dinner · Thursday, Oct 29": *Who's cooking?* (any members, as chips with their names), *Something else* (a typed meal without a recipe such as "Leftovers" or "Eat out"), and the recipe box with a search. Tapping a recipe adds it at once with the recipe's servings. A slot can hold several meals; new ones go after the others.
- **A planned meal** shows its name, servings and cooks (chips with a person icon and the name, "You" for the user). Tapping it opens its sheet: servings (− and +, or not set; up to 100, or the recipe's own servings when it makes more, after v1, 2026-11-13, LOGIC-13: a recipe for 120 was planned at 100, so the week's list used 0.83 of it; meals keep 1 to 1,000, also in the rules), cooks, a note, *Open recipe* (when the recipe still exists) and *Remove from plan* with Undo. A meal whose recipe was deleted keeps its name and says *Recipe deleted*; a member who left disappears from the cooks.
- **Who may change it.** Every member, like the rest of the household's data. Planning is not logged in Recent activity, to keep it useful; cooking still is (5.5).
- **Data and cost.** One document per meal (8.2), so two phones editing the same day never overwrite each other. The tab listens changes-only to the dates on screen (9.8): a week seen for the first time on a phone that day is read in full, then only changes. The daily job deletes meals more than 28 days past and their tombstones after 2 days (10.3).
- **Use it up while picking.** The recipe list in the add sheet starts with up to three *Use it up* recipes (5.6) with what they use ("Uses Spinach, Yogurt"); tapping one adds it like any recipe. After v1 (2026-11-12) recipes the shown week already plans from today on are left out. The pantry is listened to only while the sheet is open.
- **The week's grocery list.** A cart button in the top bar (shown when the week has recipes still to cook) opens *This week's ingredients*: for each recipe meal from today on that is not cooked, under its name and "Dinner · Thursday, Oct 29 · 4 servings", the ingredient lines chosen the same way as Add to grocery list (5.5): shopping names, *Probably in pantry* and *On the list* unchecked, *Change*, a list to add to and select all or none. A meal's servings scale the recipe: counts round up to whole items ("4½ eggs" buys 5) and the note shows the scaled line. The same item from two meals becomes one item with both notes. Items carry the source **Meal plan**: the recipe icon with the words *Meal plan* on the list. Recent activity says "Sam added 6 ingredients from the meal plan to Groceries".
- **Cooked.** *Cooked* in the meal's sheet: for a meal with a saved recipe it opens "What did you use?" (5.5) at the meal's servings, and the button also works with nothing checked (*Mark as cooked*); a meal without a recipe is marked at once. The meal shows a check and *Cooked* (never color alone), its sheet says "Cooked by Sam" with *Not cooked yet* for mistakes (the pantry is not touched then), and a recipe meal logs "Sam cooked Tacos". One Undo puts back the pantry, the mark and the event. Cooked meals are left out of the week's grocery list. After v1 (2026-11-09) a meal is marked cooked once: when two people press Cooked for the same meal, the second phone's whole write, its pantry takes and event included, is refused (8.5) and it says "Sam already marked this meal cooked. Nothing was taken from the pantry." *Not cooked yet* or Undo clears the mark, and it can be cooked again. After v1 (2026-11-22) a Cooked made offline shows at once: `cookedAt` is read with the phone's estimate of the server time (`instant("cookedAt")` on Android, `millis(s, 'cookedAt')` on the web, 8.6), as tombstones are (9.8), so the meal shows *Cooked* and its sheet *Not cooked yet* before the write reaches the server, and Cooked can't be pressed a second time. Undo of *Remove* brings a cooked meal back still cooked, also when Cooked, Remove and Undo were all offline: Undo removes only the tombstone (2026-11-10) and never writes `cookedAt` back, which the rules accept only as the stored value.
- **Elsewhere.** Clear household data (5.1) removes planned meals; Settings > Export and the nightly export include them (13.4); the tour has a step for the tab (5.8); Home's Today card listens to the same week window as the tab (9.8), on the web with its *Week starts on* (18.1).
- **Move.** *Move* in the meal's sheet: a day of its week and a slot, as chips with a check; the meal goes after the meals there. Undo puts it back.
- **An empty week** (after v1, 2026-11-12, review UX-15) starts with "Nothing planned this week. Tap + on a meal, or copy last week's meals." and a *Copy last week* button, and opens there instead of on today; before, it was 21 empty slots with no words. The button shows on every empty week (knowing whether last week has meals would cost a read each time); with nothing to copy it says "Last week has no meals to copy".
- **Copy last week and Clear this week** in the Plan tab's ⋮ menu. Copy puts last week's meals on the same weekdays and slots of the week shown (recipe, servings, cooks and note; not Cooked), after what is there, skipping a meal already in that slot with the same name, so copying twice adds nothing; after v1 (2026-11-13, review LOGIC-14) meals of the same name are counted, so two "Leftovers" in last Monday's lunch become two (as many as last week had, minus those already there); last week is read from the server when it answers within 5 seconds. Clear removes the week's meals at once, with Undo (after v1, 2026-11-13, R13: it asked first, "Removes all 6 meals planned for Oct 25 – Oct 31", and then offered Undo too). Both have one Undo; Copy says "Copied 5 meals from last week".
- **The daily reminder** names the meals the user cooks today (5.4, 10.3).

## 6. UX and visual design

After v1 (2026-11-15) the app is getting a new look, described in section 19. It replaces 6.4's *Shape and surface* and 6.5's component patterns one screen at a time; a screen that has not been rebuilt yet keeps what this section says, and the rest of this section holds for both.

**6.1 Principles.** Glanceable (what needs attention comes first), fast capture (adding an item takes under 10 seconds with thumb-reachable controls), calm (color is reserved for status), trustworthy (Undo on every destructive action, visible sync state). After v1 (2026-11-12) "calm" holds on every theme: the presets' filled buttons and floating buttons keep their hue at least 45° (OKLCH) from the Soon, Today/Tomorrow and Expired colors, so what needs attention stands out next to them (review UX-17), and Undo stays as long as the phone's accessibility timeout asks (5.2). After v1 (2026-11-11), Add to pantry has Undo too; the household actions, Clear household data and Delete list ask first instead (DECISIONS). **One rule for confirmations** (after v1, 2026-11-13, review UX-11, R13): an action with Undo never asks first (Delete recipe and Clear this week asked, then offered Undo too), and only an action without Undo asks (Delete list, Leave, Remove member, Transfer ownership, New code, Delete household, Clear household data, Delete account, Sign out with unsynced changes). Forms and sheets keep what was typed or picked through rotation, theme and font changes and Android closing the app: the Add to pantry rows, Plan's Add meal sheet (open, with its title and cooks) and a meal's cooks, cook mode's ticked ingredients, a recipe's Add to grocery list choices and renames, and I cooked this amounts. In the web app (after v1, 2026-11-12) the Add to pantry rows and Plan's Add meal sheet (cooks and typed meal) and meal sheet (servings, cooks and note) also come back, still open, when iOS reloads a page it discarded in the background (18.3).

**6.2 Navigation.** A bottom `NavigationBar` with four destinations: Home, Pantry, Recipes, Groceries (after v1, five: Plan between Pantry and Recipes, 5.9). Settings opens from the avatar button in each tab's top app bar. Each tab keeps its own back stack (Navigation 3) so scroll position and sub-screens survive tab switches. Portrait-first; landscape and tablets MUST NOT break (use window size classes, center content, maximum width 640 dp). After v1 (2026-11-13, review UX-14, A11Y-11) the bar sits under the screen in the layout, so Tab from a keyboard (and switch access) reaches the screen before the five tabs; tab labels stay on one line, growing with the phone's text size up to 1.2× (`TAB_LABEL_MAX_FONT_SCALE`) next to their icons, where at 2× they broke mid-word ("Pantr / y"). The iPhone web app (18.2) has the same order and labels (2026-11-13): the screen comes before the tab bar in the page, and labels stop at 1.2×, or at 3.3% of the screen's width where that is less, since an iPhone's fifth is narrower.

**6.3 Screens**

| Screen | Key elements |
| --- | --- |
| Welcome, Sign in, Sign up | Logo, email, password, display name (sign-up), reset link |
| Household gate | Create card, Join card; menu with Sign out and Delete account (after v1) |
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

**Color themes (after v1).** Settings > Appearance > Colors offers *Wallpaper* (Material You, Android 12 and later, the default), *Spoil Not green* and five presets from the owner's Coolors palettes: *Fiery Palette*, *Summer Fun*, *Enchanted Forest*, *Watermelon Sorbet* and *Contrast Pop*. Each preset names four palette colors (main, second, accent, background tint) in `tools/themes/themes.json`; `tools/themes/generate-themes.mjs` builds the light and dark schemes with Material Color Utilities (fidelity variant) into `PresetColors.kt`. After beta 7 the presets are bolder (owner feedback "uninspiring"): containers (chips, the FAB, the selected tab, segmented buttons) use deeper tones (80 in light, 35 in dark, instead of about 90 and 30), light backgrounds sit two tones deeper (surface 96), and backgrounds take a stronger tint of the theme (neutral chroma 26 and 32 instead of 10 and 14). Spoil Not green and Wallpaper are unchanged. After v1 (2026-11-12) Fiery Palette's buttons take its teal (main `0f4c5c`, the orange moves to the accent) and Watermelon Sorbet's its mint (main `06d6a0`, the pink moves to the second color), because their orange and pink buttons had the hue of the Expired and Today colors (review UX-17); backgrounds and the swatch keep each palette's look. `ColorThemesContrastTest` checks every fixed theme, light and dark: text 4.5:1 on its background, chart and icon colors 3:1, the status colors 4.5:1 on its surfaces and on their own tint (the chip and swipe backgrounds), and `primary` and `primaryContainer` at least 45° of hue from Soon, Today/Tomorrow and Expired. The choice is stored on the phone; the picker shows each theme as a swatch with its name and marks the choice with a ring and a check. Older phones keep their choice (the old switch off means Spoil Not green).

Status colors are fixed and are not taken from the dynamic scheme or any theme, so they stay recognizable:

| Status | Light | Dark | Icon |
| --- | --- | --- | --- |
| Fresh | #28702C | #81C784 | check\_circle |
| Soon (2 to 3 days) | #8F5500 | #FFB74D | schedule |
| Today or tomorrow | #B03A0A | #FF8A65 | warning\_amber |
| Expired | #BA1A1A | #FFB4AB | delete\_outline |
| No date | on-surface-variant | on-surface-variant | remove |

Status MUST never rely on color alone: always icon plus text. Text contrast at least 4.5:1, measured on what the text really sits on: the status chip and Pantry's swipe backgrounds tint the surface with the status color at 12% (`STATUS_TINT_ALPHA`), and the text is checked on that blend. After v1 (2026-11-12) the light Fresh, Soon and Today/Tomorrow colors are about 10% darker than first designed (#2E7D32, #A05F00 and #C2410C), which were 3.9 to 4.2:1 on their chip in light mode; the swipe backgrounds went from 16% to 12% for the same reason (review A11Y-03, DECISIONS).

**Type.** Material 3 type scale, one bundled variable font (Manrope or Inter; no runtime font downloads; after v1 also Fredoka, a rounded display face, for the app's name on the welcome screen only; after v1, 2026-11-22, review EFF-07, both bundled as subsets that `scripts/subset-fonts.sh` writes from the full fonts in `tools/fonts`: Manrope with the Latin and Latin Extended characters the web's font files cover, every weight and OpenType feature, Fredoka as a static SemiBold with ASCII and Latin-1, since it draws only the app's name; any other character, such as a name typed in Greek or Cyrillic, is drawn in the phone's own font, as ⋮ and amounts like ⅓ always were; `FontSubsetTest` fails if a string, a character the code puts into text or an amount needs a character the subset dropped), compact top app bars on the main tabs (after v1; the large ones took a fifth of the screen before any content), body text at least 14 sp, honor the system font scale up to 200%. **Large text** (after v1, 2026-11-13, review A11Y-04): from font scale 1.5 (`LARGE_TEXT_FONT_SCALE`, `isLargeText()`) what sits beside a name goes under it, so names never break mid-word: status chips and Low or Out badges on Home and Pantry rows (Pantry's amount joins the chip's line), the join requests card's Review button, the recipe's servings stepper under "Ingredients", I cooked this amounts, cook mode's Ingredients button and Insights' legend; segmented buttons become radio buttons from the same scale. Tab labels are the one exception to the font scale (6.2).

**Shape and surface.** M3 defaults: 16 dp cards, fully rounded chips and buttons, 28 dp bottom sheets, tonal surface levels instead of heavy shadows.

**Motion.** Material motion, its durations and easing in one place (`Motion` in `core/designsystem/motion`; after v1, 2026-11-17, review PERF-10): a pushed or popped screen moves with Material's shared axis X, a short slide with a fade (250 ms, standard easing; the old screen fades out in 150 ms). A **tab switch** is one 150 ms fade of the screen drawn on top, with no slide (2026-11-17, review PERF-09, DECISIONS): the new tab fades in over the old one, and going back to Home fades the tab out; each tab keeps its place and its lists' scroll positions as before. Your kitchen's charts grow over 700 ms when first shown, read in the draw phase so nothing is laid out again while they grow, with the stripes and the ring's dashes made once per size (review PERF-03). The first loading skeleton pulses every 800 ms. Remote changes pulse the affected row with a tonal highlight for 1.5 s. A new search, filter, sort or grouping replaces Pantry's rows at once, without a fade or a slide (the list is composed afresh for it); its rows animate only when the food changes, and another phone's changes are told from every batch, so a row that a wider search or filter brings back isn't highlighted (after v1, 2026-11-17, DECISIONS; the web the same from 2026-11-18, where rows never faded or slid, 18.2). Groceries tells them one list at a time (after v1, 2026-11-18, both apps): the state says which list its items belong to, since right after a switch the old list's items still show under the new list's name, and the first items of another list are a first load, so switching lists highlights nothing and "Sam added Eggs" isn't said for what was already on it. There is no container transform from a row to its detail and no spring on check-off, which the first design listed: a Pantry row opens its Edit sheet, a recipe card a pushed screen, and a checked grocery row moves to In cart with the list's own placement animation and a light haptic tick (DECISIONS 2026-11-17). A remote change MUST NOT move the list under the user's finger during a scroll or drag; queue it until the gesture ends. Respect the system reduce-motion setting (Remove animations), followed while the app runs: the theme watches it once for the whole app (`LocalReduceMotion`, after v1, 2026-11-17; it used to be read once per screen). With it on, pushed screens only cross-fade (120 ms), tab switches and the charts' growth are instant, the skeleton doesn't pulse and the highlight shows without fading. After v1 (2026-11-09) holding the update is not enough on its own: the work a remote change causes (mapping the snapshot, building the screen's state) runs off the main thread, once per change for the whole phone, so it doesn't delay the frames of a scroll (9.1). From 2026-11-16 (review PERF-07, PERF-08, the Pantry half of B23) Pantry holds the update with `rememberHeldWhileScrolling`, which reads the scroll state outside composition so a fling starting and stopping doesn't redraw the list; its rows have content types, and `java.time` values count as stable (`app/compose-stability.conf`), so rows whose data didn't change skip when a snapshot arrives. Groceries took the same helper in its step (2026-11-16, review CODE-09, PERF-04), with content types, rows that get only their own facts (`backInStock`, `addedBy`, worked out in the view model) instead of the whole screen state, and the quick-add suggestions worked out in the view model, off the main thread.

**Icons and haptics.** Material Symbols (rounded). Light haptic on check-off, medium on delete.

**App icon.** "Fresh jar": a cream (#F5FBF5) jar with a leaf cut out of it on the brand green (#2E7D5B). The same shape is the monochrome (themed icon) layer, the notification icon and the welcome screen's mark.

**6.5 Component patterns.** After v1: content cards are outlined everywhere (Home, Insights, Settings, Household), filled cards are kept for recipe tiles, highlights such as join requests and errors; search and quick-add fields are pills; an empty screen offers its ways to start under its sentence. `SearchBar`, horizontally scrolling `FilterChip` rows, filled tonal `Card`s, list rows with a leading status icon, `SwipeToDismissBox` rows with background actions, `ModalBottomSheet` with a drag handle (after v1 every sheet is `SpoilNotSheet`, whose handle has a 48 dp touch target instead of Material's 32 dp, review A11Y-06), snackbars with Undo, `SingleChoiceSegmentedButtonRow` (after v1: a list of radio buttons at font scale 1.5 and above, so labels are never cut off; from 2026-11-17 the theme and units choices, its last users, are a connected button group, 19.9, which does the same), skeleton shimmer on first load only, and an `ExtendedFloatingActionButton` that stays extended (after v1: it used to collapse while scrolling, owner feedback). Amounts in pieces show as just the number ("× 2", "2"), and TalkBack reads "2 pieces"; other units keep their symbol ("500 g") (after v1). Use `Scaffold` with edge-to-edge insets, support predictive back, and provide an adaptive launcher icon with a monochrome layer (themed icons).

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
| Dairy and eggs                 |
| [ ] Milk  x2   Added autom.    |
| [ ] Eggs  x12    added by Sam  |
| Produce                        |
| [ ] Spinach                    |
| In cart (2)  v    Clear cart   |
|     [ Add 2 to pantry ]        |
|                                |
| Home  Pantry  Recipes  Lists   |
+--------------------------------+
```

**6.7 States.** Loading: skeleton on first load only. Empty: a one-line explanation and a primary action; after v1 (2026-11-12) also Plan's empty week (5.9) and a new household's Home (5.6), whose empty cards say how to start instead of showing an "all done" check. Error: inline message with Retry; after v1 (2026-11-11) a thin banner "Couldn't load." with a Retry button and an error icon, where the Offline banner goes (in place of the first load's spinner or skeleton), on Home, Pantry, Plan, Recipes and Groceries, shown while one of the screen's listeners has failed for a reason other than the quota or the network (the quota and Offline banners already say those). The screen keeps what it already showed, listens again by itself after 1, 2, 4 … 32 s (Retry listens at once), and the banner goes once a listen has stayed up for 2 s. Other screens and sheets retry the same way without the banner. Before, a failed listener ended for good and left its screen blank, spinning or stale until the app restarted (review REL-04, WEB-10). A change the server refuses after the action returned is reported once by the shell, as a snackbar above the navigation bar, whichever screen is open (after v1, 2026-11-11; before, only Pantry, Plan and Groceries reported it, while open, review REL-13). Offline: a thin banner “Offline. Changes will sync later” and a cloud-off icon on rows with pending writes; every tab shows it, Groceries included (after v1, 2026-11-09). Quota reached: banner “Spoil Not has reached its free daily limit and will work again tomorrow.” It sits above whichever top-level screen shows (sign-in, the gate, the loading screen or the app), takes the status bar's space, and holds until midnight Pacific time, when it turns itself off; meanwhile the app keeps working from the phone's copy (after v1, 2026-11-09, 7.5). Update required: full screen with a link.

**6.8 Accessibility.** Screen reader labels read status in words (“Milk, 2, expires tomorrow, fridge”); 48 dp touch targets; every swipe action also in a menu; logical focus order; contrast checked in both themes; layouts tested at 200% font scale. Every control has a name a screen reader can say: buttons carry their text or a content description, and icons inside a named control are decorative. The floating buttons (Add item, Add N to pantry, Add recipe; Cook until 2026-11-13, when Start cooking moved into a bar, 5.5) are `SpoilNotFab`, which gives the button its text, because Material's extended floating button names itself from its icon. A row that opens something carries its sentence on the clickable node itself (Pantry: "Milk, 2, expires tomorrow, fridge", then Edit), with its ⋮ menu a separate stop. `AccessibilityChecksTest` runs Google's Accessibility Test Framework and the app's own rule (every clickable or toggleable node has a name) on the 13 main screens (Home, Pantry, Plan, Recipes, recipe detail, Cook mode, recipe edit, Groceries, Settings, Household, Notifications, About, Insights) and on the sheets, menus and dialogs they open, with no result excused (until B19 the drag handle was); it also checks Pantry's and Groceries' group headings, Settings' section headings and the date tiles' radio role. It needs Android 14 and is skipped on older phones. The web app follows the same rules for VoiceOver (2026-11-13, review A11Y-12 and B19's port): rows read as one sentence (a visually hidden sentence with the visible parts hidden, or the sentence on the row's own control: Home's Needs attention, Running low and Today rows, grocery rows on their checkbox, Add to pantry rows, recipe cards with their time), date tiles and list chips are radio buttons, the star is one "Favorite" toggle (`aria-pressed`), cook mode's progress bar is named "Step 1 of 3" and its second Back says "Back to the previous step", Approve and Decline name the person, the In cart header is a heading with expanded or collapsed, and no heading is empty. Sheets and dialogs are modal `<dialog>`s, so the page behind is inert while one is open. From a keyboard every control shows a 2 px primary ring (`:focus-visible`), drawn inside rows that fill a clipped list. `a11y.spec.ts` checks these in WebKit, at 312% text too.

After v1 (2026-11-13, review A11Y-07 to A11Y-11), for screen readers and keyboards:
- **One sentence per row or card:** Home's Needs attention rows ("Milk, Expires tomorrow"), Running low rows and Today's meals ("Breakfast: Porridge, cooking: You, Cooked"); grocery rows ("Bananas, 6 pieces, from a recipe, added by Sam", with the checkbox state; Remove and ⋮ stay their own stops); Add to pantry rows ("Apples, 6 pieces, Fridge, expires Nov 23", expanded or collapsed). A recipe without a photo hides its placeholder letter, and Insights' legend isn't read after the ring that already says both counts. I cooked this names each amount ("Amount of Flour to use, in kg") and reads it after its ingredient.
- **Roles and states:** date tiles (Add item, Edit, Add to pantry) and the list chips (This week's ingredients, Add to grocery list) are radio buttons in a group; Take the app tour and About are plain buttons; the favorite star is one toggle named "Favorite" with its state, on cards and on recipe detail; cook mode's Back says "Back to the previous step" and its progress bar "Step 1 of 3"; the settings avatar's badge number isn't read twice; Approve and Decline name the person; the cart header says Expanded or Collapsed.
- **Headings:** Pantry's group headers, Groceries' aisle and In cart headers, the section titles in Settings, Household and Notifications.
- **The tour** (5.8) hides the screen and the tab bar behind it from TalkBack while a step shows, so swiping stays on the card; Next does what tapping the highlighted element does. Only while a screen reader that explores by touch is on: a finger still taps the highlighted element through the dimming.
- **Keyboard:** besides the order (6.2), the control with keyboard focus has a 2 dp ring in the theme's primary color (`FocusRing`, around the whole screen and tab bar), shown only in keyboard mode, never for touch; sheets, dialogs and menus keep Material's state layer. After v1 (2026-11-24, Compose 1.13) the ring asks where focus is when it draws (`getFocusedRect` of a never-focusable focus target around the shell): after each key event and each time focus enters or leaves the shell it redraws every frame for about 0.75 s (a list scrolling the focused control into view), then checks every 0.1 s for 2.5 s more whether focus moved without a key (a form focusing the field a server error belongs to). Focus that code moves later than that is ringed at the next key. `FocusRingTest` checks the ring beside the focused button, after a move by code and after Tab, and none for touch.
- **Lists clear the floating button:** a tab's list leaves room under its last row for the floating button as measured (`listBottomPadding()`, at least 96 dp), so at 2× text the last row's ⋮ can still scroll above Add item.

**6.9 Copy.** Short, friendly, plain, sentence case (headers too: aisles and In cart are not in capitals, after v1). Errors say what happened and what to do. After v1 (2026-11-13, review UX-18) one thing has one name (*Your kitchen* for Insights, *Clear cart*, *Use 1* and *Add 1*), amounts are digits in action names, a count goes in the sentence ("Copied 5 meals from last week", not "(5)"), a button never offers nothing ("Select items to add", not "Add 0 items"), and what the app did by itself says so ("Added automatically"). All strings live in Android string resources (strings.xml, English now) so translation is possible later.

## 7. Architecture

**7.1 System overview**

```
Android phones (Kotlin, Jetpack Compose)  <---- push notifications (FCM) ----+
   |  sign in, read and write (live listeners, offline cache)                |
   v                                                                         |
Firebase free Spark plan: Auth | Firestore | Cloud Messaging | Crashlytics   |
   ^                                                                         |
   |  Admin SDK (reads Firestore, asks FCM to send)                          |
   +------- Reminder job (Node.js, GitHub Actions, 3 times an hour) ---------+
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
- Testing: JUnit, MockK, Turbine, the Compose UI test library, a screenshot-testing library (Roborazzi or Paparazzi; confirm it is maintained), the Firebase Emulator Suite. After v1 (2026-11-22, review CODE-05): Turbine was never used and is removed; tests use hand-written fakes and collect Flows with `kotlinx-coroutines-test`, and MockK only stands in for Firestore's snapshot classes, which can't be built otherwise. The Compose tooling libraries (`ui-tooling`, `ui-tooling-preview`) are removed too: the app has no `@Preview`.

**Material 3 note.** Use the stable `androidx.compose.material3` through the Compose BOM (the 1.4 line was stable in August 2026; see Appendix C). Adopt Material 3 Expressive components and motion only where the APIs are stable, meaning not annotated as experimental, and use standard Material 3 otherwise.

**7.4 Environments.** Two Firebase projects: `spoilnot-dev` and `spoilnot-prod`. Gradle product flavors `dev` and `prod` with different application IDs (an `applicationIdSuffix` of `.dev`, so both install side by side: com.parcha.spoilnot for prod and com.parcha.spoilnot.dev for dev), each with its own `google-services.json` in `app/src/dev` and `app/src/prod`. Local development and all automated tests MUST use the Firebase Emulator Suite (Auth and Firestore; the Android emulator reaches the host at `10.0.2.2`) so they never touch real data.

**7.5 Errors and logging.** Repositories throw typed domain errors (`PermissionDenied`, `QuotaExceeded`, `Offline`, `NotFound`, `Conflict`); the UI maps them to messages. Map Firestore `resource-exhausted` to `QuotaExceeded` and show the banner from 6.7. A document that doesn't map never fails a listener (after v1, 2026-11-11, 8.6): each field is read on its own, another type reading as missing, and each document maps on its own. A listener error changes the screen only when it is a **permission refusal** (`permission-denied` or `unauthenticated`, `PermissionDenied`): after v1 (2026-11-09) `SessionManager` keeps the last session on screen while it listens again after 1, 2, 4 … 32 s for any other error (the quota, the network, a bad document), so an exhausted quota never signs anyone out, sends them to the gate or replaces the app with the loading screen; a permission refusal shows Loading while it retries, as before. Screens' own listeners retry with the same backoff and say "Couldn't load. Retry" meanwhile (6.7). The changes-only listeners (9.8) likewise keep the cached list on screen, marked offline, when their server listener is refused for the quota or can't reach the server, and listen again with the same backoff (a refused listen reads nothing). Uncaught errors go to Crashlytics when enabled, and only from builds on real Firebase: debug and dev benchmark builds, which use the emulators, never send (after v1; their failed uploads restarted the app between benchmark runs). Use `Timber`, planted only in debug builds. After v1 (2026-11-12) release builds also drop every `Log.v`, `Log.d` and `Log.i` call, the libraries' included (12.2, control 11).

**7.6 Platform floors.** `minSdk` 26 (Android 8.0), or higher if a library requires it (some Firebase libraries already need API level 23 or higher). `compileSdk` and `targetSdk` at the latest stable level. After v1 (2026-11-24) `compileSdk` is 37.2 (`compileSdk { version = release(37) { minorApiLevel = 2 } }`), which Compose 1.13 requires, while `targetSdk` stays 37: a minor SDK adds APIs to compile against and changes nothing on phones.

## 8. Data model (Firestore)

**8.1 Conventions**

- Firestore **Standard edition**, the `(default)` database (the free quota applies to one database per project), one region close to the users. The region cannot be changed later.
- Document IDs are Firestore auto IDs unless a deterministic ID is specified.
- `createdAt` and `updatedAt` are server timestamps. Every mutable document also has `createdBy` and `updatedBy` (uid).
- **Dates** (expiry and similar) are calendar dates stored as strings `YYYY-MM-DD` with no time zone, and compared as strings.
- **A food's name is saved with every word capitalized (owner, 2026-11-21):** "black beans" and "Black beans" are both saved as "Black Beans", and "super Pickled pickles of Doom" as "Super Pickled Pickles Of Doom". Each word's first letter becomes a capital and the rest stays as typed ("BBQ Sauce"); punctuation before a word's first letter keeps it open ("(Organic)"), a digit doesn't ("7up"). It applies to pantry items and grocery items however they are added (Add item, Edit, quick add, a recipe's or the plan's ingredients, renames), on both apps (`NameCase`); not to recipe titles, meals, lists, households or people. Names saved before keep their case until the item is edited. The name fields start each word with a capital on the keyboard.
- `nameKey` is the name lowercased, trimmed, with whitespace collapsed and diacritics removed. It is used for matching and de-duplication. After v1 (2026-11-13, review WEB-06) whitespace has one definition on both apps, written out instead of each language's `\s`: Unicode's White_Space characters (tab to carriage return, space, U+0085, the no-break space U+00A0, U+1680, U+2000 to U+200A, U+2028, U+2029, U+202F, U+205F, the full-width space U+3000) and U+FEFF (`NameKey.WHITESPACE`). The quick-add field and the ingredient parser read text with these as plain spaces. Keys stored before are normalised when read, not migrated (DECISIONS 2026-11-13).
- Limits: names up to 80 characters (people 40, households and lists 60, recipe titles 200, meal titles 120), notes up to 500 (recipe text has 11.6's limits), other strings about 100, recipe links and photos http or https up to 2,000 characters, arrays up to 100 items, documents far below 1 MiB. **After v1 (2026-11-08) the rules enforce them:** each collection accepts only the fields the apps write (8.2), every string and list within its limit (lists of names, tags, cooks and aisles also in total characters), and finite numbers; recipe ingredients and steps are checked only by number, because rules cannot walk a list of maps. Both apps also cut names to these lengths when they read them, and Android drops recipe links and photos that are not http or https (the web app skips them when it shows a recipe), so data another client wrote cannot slow a list or open another app.
- **Who wrote it (after v1, 2026-11-08).** `createdBy` is the writer when a document is created or brought back from a tombstone (Undo), and never changes after (a missing one may be filled in by the writer). A grocery item's `checkedBy` is the writer, null, or left as it was. An activity event's `actorUid` is the writer, its `type` one of 8.2's, `actorName` 1 to 40 characters and `params` only 8.2's fields. The apps name an event's author from the member list by `actorUid`; the stored `actorName` is shown only for someone who has left.
- Write every field explicitly (use null, not a missing field) unless it is documented as optional. New fields MUST be optional on read with defaults, because phones will run different versions.
- **Tombstones (after v1, 9.8).** Products, stock batches, grocery items and recipes are deleted by merging `deletedAt` (the server time) and the update stamp into the document, not by deleting it, so other phones' change listeners see the deletion. Every reader skips documents with `deletedAt`, also while its time is still waiting for the server (an offline delete, 9.8); restoring (Undo) removes it with a field delete, never `deletedAt: null`. After v1 (2026-11-10) Undo merges only `deletedAt: deleteField()` and the update stamp into the tombstone, which kept every field, so the document comes back as the server has it (8.6). The reminder job deletes tombstones after 2 days. Deleting a whole list or household still deletes for real. After v1 (2026-11-08) members can only tombstone these and meals: an outright delete is the owner's (deleting the household), or a list's items going with their list (8.5).

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
    cooking: bool                     # after v1 (5.9): today's meals in the digest; default true
  createdAt, updatedAt
  lastDigestDate: string | null       # SERVER-WRITTEN: local date of the last digest
  lastExpiredThrough: string | null   # SERVER-WRITTEN: expired items up to this date were reported
  digestAttempts: { date, count }     # SERVER-WRITTEN (after v1): runs today whose digest reached no device
  nextDigestAt: timestamp             # SERVER-WRITTEN (after v1, B25): when the job next reads this user (10.3)
users/{uid}/devices/{installId}:
  token: string
  platform: android | web             # web: the web app for iPhone (after v1, 18.4)
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
  emailMasked: string                 # after v1, optional: "ro***@example.com", checked against the sign-in's email
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
  aisleOrder: [category]              # after v1: the store's aisle order, learned from check-offs (5.3)
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
  source: manual | low_stock | recipe | meal_plan (after v1, 5.9; older apps show it as manual)
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
  type: stock_added | grocery_added | grocery_checked | recipe_saved | member_joined | discarded | recipe_cooked, household_reset (after v1)
        # grocery_checked: no longer written after v1 (2026-11-12, R15); still read and allowed for older builds
  actorUid, actorName: string
  params: { name, quantity, reason, listName }    # whatever applies to the type
  createdAt: timestamp
households/{hid}/meals/{mealId}:      # after v1: one planned meal (5.9)
  date: string (YYYY-MM-DD)
  slot: breakfast | lunch | dinner
  position: int                       # order within the slot
  recipeId: string | null             # null for "Leftovers" and other meals without a recipe
  title: string (1-120)               # the recipe's title when planned
  servings: int (1-100) | null
  cooks: [uid] (at most 20)
  note: string (1-200) | null
  cookedAt: timestamp | null, cookedBy: uid | null   # Cooked (5.9): set together by the cook at server time
  createdAt, createdBy, updatedAt, updatedBy, deletedAt (tombstone)
households/{hid}/stats/{yyyy-MM}:     # after v1: Insights counts for one month (5.7), written with increments
  used: int                           # batches used up
  expired, spoiled, didnt_like, other: int   # batches thrown away, by reason
  thrownByDay: { "1".."31": int }     # batches thrown away per day of the month, for the streak
  updatedAt, updatedBy

config/app:                           # read-only for clients; edited in the console
  minSupportedBuild: int
  minWebBuild: int                    # after v1: the oldest web build (WEB_BUILD) the data supports (18.5)
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

1. A stock batch with `quantity <= 0` is hidden by the UI and deleted (a tombstone after v1) by whichever client notices it first (idempotent). After v1 (2026-11-09) a phone notices while its Pantry tab is open, as the web app always did (9.1). After v1 (2026-11-10) −1 and a quantity typed in the Edit window are increments (9.4), so two phones taking the last of a batch can leave it below 0; the rules accept quantities down to −10⁹ and a batch below 0 is treated as one at 0. After v1 (2026-11-11) the clean-up is background work, so a refusal isn't shown, and a batch whose clean-up the server refused isn't tried again until the app restarts (or the page reloads); one waiting for the server's answer isn't written twice. Before, a batch the rules wouldn't let go was tried about 3.5 times a second, each with "You don't have permission to do that." (review REL-11).
2. `productName`, `unit` and `category` on a batch equal the product's values. Renaming or editing a product updates its batches in the same batched write (chunk at 400 operations).
3. A product's `unit` cannot change while it has batches.
4. `nameKey` is unique among a household's products. The client looks it up before creating and reuses the match.
5. Exactly one default grocery list exists per household, created with the household.
6. Deleting a household deletes all its subcollections client-side first, because Firestore does not cascade deletes.
7. (After v1, 2026-11-08) A household has exactly one owner: an ownership transfer sets the new owner's role to owner and the old owner's to member in the same batch, and the rules refuse a transfer that does not.
8. (After v1, 2026-11-08) Members delete products, batches, grocery items, recipes and meals only with a tombstone. Deleting a list (not the default one) deletes the list first and then its items for real: the rules accept a member's outright delete of an item only once its list is gone, which also holds for the later batches of a list with more than 400 items. Clear household data and deleting the household are the owner's.
9. (After v1, 2026-11-08) An Insights count (5.7) moves by at most one per field per write, and only one day of `thrownByDay` changes per write. The rules see a batch's writes to one document together, so both apps' writers send a larger count (throwing several batches away at once, I cooked this using up several, and their Undo) as one step in the batch it belongs to and further batches of one step each, at most one step per month document in a batch (`WriteBatches` in the app, `planBatches` on the web).
10. (After v1, 2026-11-09) When two phones finish the same thing, the rules pick the winner and refuse the other phone's whole batch (9.4): a grocery item is tombstoned once (a later write may keep or remove `deletedAt`, never write it again), so a second Add to pantry, Clear checked or delete of the same items fails with everything in its batch; a meal goes from not cooked to cooked once (`cookedAt` is set only where it is null), so a second Cooked takes nothing from the pantry; and an item is created only under a list that exists after the batch (`existsAfter`), so an add or Undo to a deleted list is refused rather than left where no screen shows it. The refused phone reads, from the server, why (the item's tombstone and its `updatedBy`, the meal's `cookedBy`, or the missing list) and names the winner.

**8.6 Versioning.** `households.schemaVersion` and `config/app.minSupportedBuild` allow forced upgrades. Migrations are idempotent scripts in `tools/migrations/`. Never assume every phone runs the same version.

**What both apps accept on read (after v1, 2026-11-11).** A document another client wrote can hold a field of another type: a bug in another build, a console edit or a restore. Both apps read every field on its own with a safe cast (Android `core/firebase/Fields.kt`, web `data/fields.ts`): a value of another type reads as missing, so the field's default applies (a text quantity counts as 0, `checked: "yes"` as unchecked, a list field holding text as an empty list, a time of another type as none), and list entries of another type are left out. Each document is mapped on its own, and one that still fails is skipped and logged (debug builds only) while the rest of the list shows. Before, Android's typed getters threw on another type and the web's list fields threw on text, so one such document blanked Pantry, Groceries, Home, Plan or Recipes on every phone of the household until the app restarted, and kept the web spinning even after a reload (review REL-04, WEB-10). Values of the right type but outside what the app writes (an unknown category, a negative or infinite quantity, an impossible date) are handled as before.

**What an older app keeps (after v1, 2026-11-10).** Only creating a document writes it whole. Every later write of both apps is a merge or an update of the fields it sets, and since 2026-11-10 that includes Undo of a deletion (purchases, grocery items, recipes and meals): it removes `deletedAt` and stamps the write, nothing else. So a field a newer app added survives every write of an older app, and `createdBy` and `createdAt` stay the creator's. Before, Undo rewrote the document from the phone's copy: a newer field was lost, Undo of Used up made the person who pressed Undo the purchase's creator, and Android's Undo of a checked grocery item cleared `checkedAt` (review WEB-07). A document deleted for real (by the owner, or with its list) can't be brought back this way, and the rules refuse the Undo.

## 9. Real-time sync, offline and the restock engine

**9.1 Listeners.** After sign-in and household resolution the app attaches these listeners as Kotlin `callbackFlow`s collected by ViewModels (`stateIn` with `SharingStarted.WhileSubscribed`), all cancelled on sign-out:

| Listener | Scope | Lifetime |
| --- | --- | --- |
| `users/{uid}` | own profile | session |
| `members/{uid}` | own membership (membership watcher): if it disappears the user was removed, so clear local data and return to the gate | session |
| `joinRequests/{uid}` | own join request; the waiting screen watches it for approval or decline | while waiting |
| `joinRequests` | all requests (owner only); drives the badge, the Home card and the Join requests list | session, owner only |
| household doc and `members` | name and member list | session |
| `products`, `stock` | whole collections | session (after v1: while a screen or sheet shows them, plus 5 s) |
| `groceryLists`, items | list names always; items of the open list | while visible, plus 30 s |
| `recipes` | whole collection | from the first visit to the Recipes tab |
| `activity` | newest 30 by `createdAt` | session |

Register the stock and grocery listeners with `MetadataChanges.INCLUDE` to read `hasPendingWrites` (pending-sync icon) and `isFromCache`.

After v1 (2026-11-09, review findings EFF-01, EFF-03, EFF-05 and PERF-02) nothing holds a household listener once its screens are gone. Each held one before: the restock engine from the moment the process started, and Pantry's and Groceries' ViewModels from their first visit (removing empty batches and "Sam added Eggs" were collectors started in `init`). In the background that read nothing, but Android kept the process, the SDK resumed every listener on the way back, and after more than 30 minutes away each was billed like a new query, usually the day's full read of products and stock (9.7). Now:

- **One shared pantry flow per household.** `PantryRepository.observe` gives every screen, sheet and the Plan tab the same flow (`SharedByKey` in `core/coroutines`: `shareIn` in the application scope, `WhileSubscribed(5_000)`, the latest value replayed to a screen that joins and dropped when the listeners stop). So a phone has one pair of `products` and `stock` listeners and one cursor per collection (9.8), not one per screen; before, a change reached up to three listeners on the same phone and each was billed. The open list's items are shared the same way, per list. A failure reaches every screen as before, and the next listen starts fresh.
- **Lifetimes.** The shared listeners stop 5 s after the last screen leaves. Screens collect with `collectAsStateWithLifecycle`, so in the background the screens stop after 5 s, and the listeners 5 s later. A return starts fresh changes-only listeners from the cursor (9.8). Removing batches that reached 0 (8.5 invariant 1) and "Sam added Eggs" (9.2) run inside their screen's state while it is collected: Pantry removes empty batches while it is open, and "Sam added Eggs" shows only for additions made while the list is on screen.
- **Off the main thread.** The shared flow maps each snapshot once, in the application scope (`Dispatchers.Default`). Pantry, Groceries, Home and Plan build their state with `flowOn(Dispatchers.Default)`, from the newest snapshot (`conflate()`, or a `StateFlow`). Pantry builds its items once for the groups and for "is the pantry empty" (`PantryItems.build(all, query)`). Before, every observer mapped every document again on the main thread, about 70 to 80 ms of main-thread work per remote change with 500 batches on the emulator (review 08).
- What is still held for the whole session: the profile and membership listeners (`SessionManager`) and, while the activity lives, `config/app`: three single documents, which every open reads anyway.
- **The web app (after v1, 2026-11-10)** does the same. `pantry.observe` and each list's `observeItems` are shared per key (`SharedByKey` in `web/src/domain/shared.ts`: RxJS `share` with a new `ReplaySubject(1)` per run, reset 5 s after the last subscriber, on error and on completion), as the recipe box already was (`RecipeStore`). The page's visibility stands in for the activity's lifecycle (`pageVisible$` in `web/src/core/visibility.ts`): `useObservable`, the web's `collectAsStateWithLifecycle`, lets go of its source while the page is hidden, keeping what the screen shows, and listens again when the page comes back. One difference: while the page is hidden the shared listeners (pantry, list items, recipes) stop at once instead of after 5 s, because iOS can suspend a Home Screen app before the timer fires and then resume its listeners before the late timer stops them (DECISIONS). Before, the web's restock engine kept its own products, stock and lists listeners from page load for as long as the page lived, the screen on display kept its listeners while iOS suspended the app, and each screen and sheet that showed the pantry made its own pair. Pantry's empty-batch clean-up already ran only while Pantry was open; "Sam added Eggs" on the web still announces, on return, items added in the last 2 minutes (as before), since its list of known items is the screen's own.

**9.2 Latency and behavior.** Target: median under 1 s and p95 under 3 s end to end with both phones online. No polling and no pull-to-refresh. Remote changes animate in with a row highlight (6.4). If a remote change arrives while the user is editing the same item, keep their draft and show “Sam changed this item. Review”. When someone else adds to the list being viewed, show a 3-second snackbar “Sam added Eggs”.

After v1 (2026-11-10, review findings SYNC-03 and WEB-03) the pantry Edit window does this on both apps. It keeps the purchase and product it opened with and compares them with the live pantry (`ItemEditWatch` in the app, `domain/pantry/edit-watch.ts` on the web). When what the window shows (quantity, date, location, name, unit, category, Keep at least) changed and the last writer isn't this person, a notice above the fields says “Sam changed this item.” with *Review*; the draft stays. Review shows the item as it is now and keeps what the person changed: untouched fields take the new values, and a changed quantity keeps its difference (3 changed to 2 while Sam used 2 shows 0). When the purchase is gone (deleted, or used up to 0 by someone else), the window closes with “Sam removed this item”; the remover comes from the tombstone in the local cache, so no read is billed. Names come from the member list, which the screen listens to only while the window is open; without one it says “Someone else changed this item.” or “This item was removed”. Saving without Review is safe too: only what the person changed is written (9.4).

**9.3 Write patterns (MUST)**

- Use batched writes and `FieldValue.increment` for the plus and minus buttons. Typed quantities use a plain field set. After v1 (2026-11-10) a quantity typed in the Edit window is also an increment, of the difference from what the window opened with (9.4); the grocery Edit sheet still sets its amount, only when it changed.
- Avoid `runTransaction` in common paths: transactions fail offline.
- Always write `updatedAt` as a server timestamp and `updatedBy`.
- The Firestore cache already provides optimistic UI. Do not keep a second copy of state.

**9.4 Conflicts.** Field-level last write wins, except quantity changes (increments commute) and the restock engine (idempotent). Edit versus delete: delete wins and the editor is told the item was removed. Rare duplicate grocery names are acceptable; the Already on the list check covers the normal case.

After v1 (2026-11-10, review findings SYNC-03, SYNC-04, WEB-03 and WEB-07) the apps keep that promise; before, edits wrote every field from the phone's copy and undid another member's change. Both apps now:

- **Write only what changed.** The pantry Edit window compares the form with the purchase and product it opened with: a changed quantity is an increment of the difference, the date and location are written only when changed, and of the product only the changed name (with its name key), unit, category, and Keep at least with Buy quantity and the switch (written together). Move writes the location only; the product detail and Add item write only the product fields whose values differ from the phone's copy; the grocery Edit sheet writes only the changed amount, unit, note or aisle. A stale `restockArmed` or Keep at least is never written back. Saving with nothing changed writes nothing.
- **−1 is always an increment.** Before, a −1 that took this phone's copy to 0 deleted the whole purchase, including what another member had added meanwhile (an offline phone, review 04's p04). Now it takes what the copy has left as an increment and counts the purchase as used for Insights with the press; the batch is removed by whichever phone sees it at 0 on the server (8.5 invariant 1). Undo adds it back and removes a tombstone in the same write, in case a phone already removed it. Typing 0 in the Edit window does the same with what the window opened with. Used up and Throw away still delete the purchase.
- **Undo of a deletion keeps the document as it is** (8.1, 8.6), so another member's change made between the delete and Undo stays.
- **The Edit window watches its item** (9.2).

Not covered: the grocery Edit sheet doesn't watch its item, and Undo of Mark opened and of Move puts back the values this phone saw.

After v1 (2026-11-09) finishing something is not last write wins: both phones planned their write from their own copy, so both would land and the work would happen twice (Add to pantry made two purchases and two products of each name; Cooked took the recipe's amounts twice). The rules decide instead (8.5 invariant 10): the first write to reach the server wins and the other phone's whole batch is refused. The refusal arrives after the write was applied locally, as any rejection does (`HouseholdWriter.errors`); the writer then asks the feature that wrote the batch why (`whyRefused` on its change mapper, which reads the item, the meal or the list from the server, so it costs reads only on a refusal) and the open screen shows the reason with the winner's name (`AppError.LostRace` on Android, `lostRace` on the web). A refusal that no feature explains keeps the plain "You don't have permission" message. Not covered: Used up on two phones still counts the purchase twice in Insights (accepted, DECISIONS 2026-11-09), and the extra count steps of a refused Cooked that used up two or more purchases, which go in later batches of their own (8.5 invariant 9), still land.

**9.5 Offline.** Firestore persistence is on by default on mobile; set the cache size to about 100 MB (PersistentCacheSettings). The app opens and works from cache with no connection. Pending writes show a cloud-off icon. A first launch while offline cannot sign in and says so. Detect connectivity from snapshot metadata and write errors, not from ConnectivityManager alone, because it cannot tell whether Firestore is reachable. After v1 (2026-11-21) the Offline banner shows only once a screen's data has come from the cache for 2 seconds without a break (`OFFLINE_BANNER_DELAY_MS`, the same on the web), and goes as soon as the server answers: a screen reads the cache first and the server a moment later when it opens or starts a new query (Plan's next or previous week), and the banner showed for that moment and moved everything under it twice.

**9.6 Restock engine** (a client service that runs on every phone while the app is open)

Per-product state: `restockArmed` (true when the product is created). The engine runs on every change of the combined `products` and `stock` snapshots, debounced 300 ms, including snapshots served from cache so it works offline. After v1 (2026-11-09):

- **Only after a server read.** It evaluates only once both products and stock have had a server snapshot in this listener, or a full read of each is on record on this phone (`SyncLog`, 9.8), so a warm cache still works offline (`PantryData.serverRead`). On a first sync the products can arrive before the stock, which made every staple look empty. After v1 (2026-11-09) the engine listens to the cache only, so this means a full read of each on record (`SyncLog.fullSyncs`, which tells the engine when one is recorded or the log is cleared).
- **Cache only (after v1, 2026-11-09).** The engine reads the local cache only (`PantryRepository.observeCached`, `GroceryRepository.observeCachedLists`; cache listeners are free) and holds no server listener: it reacts to what the screens' listeners (Home, Pantry, Groceries, Plan and the recipe sheets, 9.1) and this phone's own writes put in the cache. A phone that shows none of them doesn't hear another phone's change until it does; the phone that made the change, or one that shows the pantry, writes the item. Before, the engine kept its own pair of server listeners for as long as the process lived, which was billed again in full on most returns (9.1, 9.7). It starts when an activity starts (`SpoilNotApp`'s activity callbacks), not when the process does, so a process started in the background for a digest (10.2) starts no listener for it. The web app (after v1, 2026-11-10) does the same (`observeCached`, `observeCachedLists`, `SyncLog.fullSyncs$`); the signed-in shell (`AppShell`) starts the engine and stops it when the household leaves the screen, instead of `main.tsx` at page load.
- **Who writes.** The writer reports every plan this phone applies (`HouseholdWriter.localPlans`, `localPlans$` on the web). If this phone changed stock or a product (creating, editing, +1 or −1, Used up, Throw away, Undo, Add to pantry; not the engine's own writes) in the last 5 seconds, it writes at once. Otherwise it waits 5 seconds (`OTHER_PHONE_DELAY_MS`); a newer snapshot during the wait starts the evaluation over, so when the other phone's write arrives first the product is disarmed and nothing is left to write. Writing twice is still harmless, only wasteful.
- **Retries.** A failed listener (quota, a refused read) listens again after 1, 2, 4 … 32 seconds instead of stopping the engine until the app restarts.

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
                updatedAt, updatedBy, and removes deletedAt;
                if the item is not live in the local cache (new, or a tombstone coming back):
                    also createdAt, checked = false, checkedBy = null, checkedAt = null,
                    note = null, recipeId = null, aliasOf = null
                (a live item's checked is left alone: an item already in the cart stays checked;
                 never createdBy, see below)
            update p.restockArmed = false
```

Before v1's tombstones (9.8) "never write checked" was enough; a tombstone left by Add to pantry keeps `checked: true`, so bringing it back must clear it. The writes pass the rules of 2026-11-08 (8.5) from any number of phones at once: the engine never writes `createdBy` (a create may leave it out and a merge keeps the stored one, while a second phone naming itself would be refused and show an error), and `checkedBy` is null. A Low stock item therefore has no creator (one brought back from a tombstone keeps its old one); its source badge says where it came from.

Properties: idempotent (two phones doing it at once write identical data), respects deletion (it stays disarmed until stock recovers to the minimum), never un-checks an item that is on the list, no nagging. Editing `minQuantity` upward sets `restockArmed = true`. Unit tests MUST cover: crossing down, recovery then dropping again, the user deleting the item, two concurrent evaluations, a product without a minimum, a minimum raised while already low, batches with quantity 0, and after v1 a tombstone brought back, a first sync, which phone writes and a failed listener (`RestockEngineTest`, `RestockRunnerTest`, `GroceryChangeMapperTest`; `restock.test.ts` and `data/grocery.test.ts` on the web). `RestockFlowTest` runs two phones against the rules.

**9.7 Quota budget.** Spark allows 50,000 reads, 20,000 writes and 20,000 deletes per day.

- A listener costs one read per document it returns. With persistence on, a listener disconnected for more than 30 minutes is billed like a brand-new query, so every return to the app after a long absence re-reads the collections. Security-rule lookups (`exists`, `get`) cost one extra read per dependent document.
- Typical household: about 130 documents across listeners (30 products, 60 batches, 20 grocery items, 15 recipes, members). A phone that opens the app 8 times a day after long gaps reads about 1,000 documents; four phones, about 4,500 reads a day including rule lookups. Writes: about 40 a day.
- After v1, Home also listens to the current month's Insights document (one read per open plus its rule lookup); the Insights screen reads up to six month documents when opened. Finishing a batch writes the month document in the same batch. The quota simulation (`npm run quota-sim`, docs/RUNBOOK.md) puts a typical household at about 4,760 reads a day.
- After v1, the changes-only listeners (9.8) cut a typical household to about 1,890 reads a day: one full read per phone a day, then only changes. Home's Use it up reads recipes on every open (5.6), about 40 more, so about 1,930; reading only new activity events (9.8) brings it to about 1,790. The meal planner (5.9) adds about 150 (this week's meals, their changes, the nightly export and two housekeeping queries): about 1,940; the digest's meals query adds about 8: about 1,950. Checking that each due user is still a member (10.3) adds about 4, one read per member a day: about 1,960.
- So the free quota supports roughly 10 active households' worth of reads (about 18 after 9.8). The reminder job adds a few hundred reads a day. After v1 (2026-11-22) the on-time trigger starts the job about 96 more times a day (10.4), and each run reads every user with reminders on: the simulation puts the free plan at about 14 households under the 70% line, from about 17 (RUNBOOK, Check quota usage).
- After v1 (2026-11-08) the simulation also counts what it left out: each activity event as a write with its rule lookup, the documents the daily prune reads before deleting them, the prune's deletes (about 57 per household a day: a day's tombstones, activity events from 30 days ago and meals passing 28 days), and the job's state document (one read per run). It takes the number of job runs that start in a day (`RUNS`) and how many phones are the web app (`WEB`). With 24 runs a day a typical household costs about 2,040 reads, 94 writes and 57 deletes a day, so **about 17 households** fit under 70% of every limit (reads run out first). The job is scheduled 72 times a day (10.4); if every run started it would be about 2,240 reads, about 15.6 households, since each run reads every user with reminders on. GitHub has started far fewer (10.4).
- After v1 (2026-11-09) the rules look up the list for every batch that creates grocery items (8.5 invariant 10): one read per list per batch (Firestore bills a document a request looks up once, however many writes use it). With 10 item creates a household a day it adds about 10 reads; the simulation now makes 10 of its 40 daily writes item creates, and a typical household costs about 2,020 reads a day: still about 17 households. Explaining a refusal reads up to 11 documents, only when a phone loses a race.
- After v1 (2026-11-09) Android lets go of every household listener once its screens are gone (9.1). Before, the restock engine and Pantry's and Groceries' screens kept theirs for as long as the process lived; Android often keeps it between opens, and a return after more than 30 minutes paid for them again, usually the day's full read of products and stock (review 09, EFF-01). The simulation's `WARM` and `HELD=1` knobs replay that: with 3 of each phone's 7 later opens finding the process alive it was about 3,720 reads a household a day (about 9 households), with all 7 about 6,340 (about 5). Now an open costs the same whether the process was kept or not: about 2,020, about 17 households. The phone's own listeners on one change also went from up to three per collection to one (EFF-03), which the simulation doesn't model (it lists one query per collection).
- After v1 (2026-11-10) the web app lets go the same way (9.1, 18.3). Before, its restock engine kept the day's full read of products and stock, and the lists, from page load, and iOS resumed them whenever it brought a suspended Home Screen app back instead of reloading it. `HELD=1` now replays that for web phones too (only the engine's listeners; the screen on display also kept its own, so the web's "before" is a lower bound): with four web phones and 3 of 7 later opens resumed it was about 3,520 reads a household a day (about 10 households), with all 7 about 5,520 (about 6). Now about 2,020, about 17 households, as on Android.
- After v1 (2026-11-23, B25) the reminder job reads only the users who may be due (10.3): those whose `nextDigestAt` has come, those whose profile changed since its last run, and once a day everyone with reminders on. A run with nobody due costs three reads (two empty queries and the job's state), so the number of runs barely matters: about 1,940 reads a household a day at 24 runs, 1,970 at 120 and 1,980 at 168, so **about 18 households** at 120 runs (from about 14.5) and 17.6 at 168 (from 13.4). It writes the job's state once a run, about 120 writes a day across the project.
- If usage nears 70% of the daily quota: first, make sure recipes and non-active grocery lists are lazy-loaded (already specified); second, use a per-session read counter on a hidden debug screen to find regressions; third, move to the Blaze plan (extra reads cost a few cents per 100,000; check the current regional price; budget alerts notify but do not cap spending).

**9.8 Changes-only listeners (after v1).** Most reads were phones re-reading every document after more than 30 minutes away. Products, stock, grocery items and recipes are now each read with two listeners (`liveDocuments` in `core/firebase/DeltaSync.kt`):

- a **cache** listener on the whole collection (free; it shows the phone's copy, its own unsent writes and whatever the other listener brings in), and
- a **server** listener on `updatedAt` after the **cursor**, which costs one read per changed document (at least one per query). When the server refuses it for the quota or can't be reached (after v1, 2026-11-09), the list stays on screen from the cache listener with the Offline banner, and the server listener starts again after 1, 2, 4 … 32 s (7.5); a permission refusal still ends both, and the screen then says "Couldn't load. Retry" and listens again (6.7, after v1 2026-11-11; a document of another type no longer ends anything, 8.6). Both SDKs hand a server listener's refusal to every listener of the same query, the cache-only one included, and a first full read uses the same query for both: right after creating or joining a household, before the membership is on the server, the cache listener listens again after 250 ms × attempt, at most 10 times, instead of failing the list (web 2026-11-10, Android 2026-11-11).

The cursor (after v1, 2026-11-09) is the newest `updatedAt` that this phone's server listener received for that collection, less 5 minutes. `SyncLog` keeps it per collection and per meal window, next to the time of the last full read, from server snapshots only (`metadata.isFromCache` false), leaving out documents with unsent writes; it only moves forward. It never comes from the cache: the phone's own confirmed writes, and other queries, put fresh server times there while no listener on the collection is reading what the other members did before them, so a cursor taken from the cache skipped those changes until the next full read (review 04 SYNC-01, 10 WEB-16; the common case was the restock engine writing to the list from the Pantry tab). The 5 minutes cover a snapshot that shows the phone's own confirmed write before the server has sent another member's slightly older change. The first open of a collection, an empty cache, a collection whose server listener has brought no change yet, and any open more than 20 hours after the last full read use a full server listener instead, recorded per phone (`SyncLog`). After the update each collection is read in full once, since the log has no cursor yet. That daily full read also drops anything kept by mistake, so tombstones (8.1) only need to outlive it. A document is deleted when `deletedAt` holds a time, the phone's own estimate included (after v1, 2026-11-08: `ServerTimestampBehavior.ESTIMATE` in `Tombstones.isTombstone`, `serverTimestamps: 'estimate'` on the web), so a delete made offline leaves the screen at once instead of when the server confirms it; every reader goes through that one check (the cache listener, the restock engine's `isCached`, Clear household data, Export). `fromCache` and the offline banner come from the server listener; the pending-sync icon from the cache. The quota simulation puts a typical household at about 1,890 reads a day (from about 4,760), so about 18 households fit under the 70% line (about 17 once the simulation counted events, the prune and its deletes, 9.7). Older app builds would show tombstones as items, so the release that adds this raises `config/app.minSupportedBuild` (docs/RUNBOOK.md).

After v1 (2026-11-09; the web app 2026-11-10) each phone has one such pair for products, stock and each list's items, whichever screens show them (9.1, `SharedByKey`): before, every screen that attached computed its own cursor, and since the cursor moves with each change received, a screen opened after any change got a query of its own, which the SDK could not share. While the pair lives its cursor stays the one it started with; the next start takes the newest from `SyncLog`.

Recent activity (after v1) works the same way without tombstones, since events are only added (`liveNewest`): a cache listener on the newest 10 by `createdAt`, and a server listener on `createdAt` after the newest event it received before minus 5 minutes (its cursor in `SyncLog`, as above), or the newest 10 on the daily full read. A new event costs one read per phone instead of the newest ten on every open. Removing an event (Undo of a throw-away, the 30-day pruning) shows at once when the phone saw the event arrive and by the next daily full read otherwise. The quota simulation puts a typical household at about 1,790 reads a day.

The meal plan (after v1, 5.9) uses the same two listeners limited to a window of dates (`liveDocuments` with a scope): the cache listener and the full read cover only the dates on screen, while the changes listener reads changes anywhere in the collection (they are few, and land in the cache for other weeks). Each window has its own full-read record and cursor, so a week seen for the first time that day is read in full.

## 10. Notification system

**10.1 Flow**

```
Every quarter hour (an Apps Script trigger, after v1; GitHub's schedule three times an hour as the backup; hourly
in v1) -> reminder job (Node.js + firebase-admin)
  1. load users with notificationPrefs.enabled (after v1, B25: only those whose nextDigestAt has come and those
     whose profile changed since the last run; everyone with reminders on only in the first run of each UTC day)
  2. keep users who are due: local time is at or after their digest time, before 22:00, and not yet sent today
  3. per household: keep due users it still lists as members (after v1; clear a removed member's pointer), keep
     those with a device (after v1, read first), then query stock with expiryDate between (today - 14 days) and
     (today + 3 days); so the Expired reminder's daily mode names an item for two weeks, which the setting says
     after v1 ("Every day for two weeks", LOGIC-07)
  4. build each due user's digest from their own stages and local date: the Needs attention set (5.4, after v1 one
     set with the apps) narrowed by their stages and expired mode
  5. send through FCM to the user's device tokens; delete tokens FCM says are unregistered or invalid
  6. update users/{uid}: lastDigestDate, lastExpiredThrough (after v1 also nextDigestAt, tomorrow's time; every
     other user read gets a new nextDigestAt too)
  7. prune activity older than 30 days and join requests older than 7 days (after v1: in the first run of each
     UTC day, keyed on jobs/state.lastPruneDate; after v1, 2026-11-10: pending and declined ones, and approved ones
     only once the join is finished, that is when the sender's profile no longer waits on this household or is
     gone, one read of that profile per old approved request)
  after v1 a household or user whose step fails is counted (failures) and the run carries on; it exits non-zero at
  the end
```

**10.2 Client responsibilities**

- When a household opens with dated food on the phone, or reaches its third dated item (after v1, 2026-11-13; 5.4), or from Settings, show a short explanation, then request the notification permission (`POST_NOTIFICATIONS`, a runtime permission on Android 13 and later). After v1 Home and Settings say when the phone blocks them, and a Turn on that works shows the local "You're set" notification (5.4).
- Get the FCM token and write `users/{uid}/devices/{installId}` (token, platform, appVersion). Refresh in `FirebaseMessagingService.onNewToken` and at app start, writing only when it changed. Delete the device document on sign-out. After v1, the phone also deletes its FCM token whenever nobody is signed in: after a sign-out or an account deletion, and at every app start without a signed-in user. A device document that a sign-out could not delete (for example offline) then holds a token FCM rejects, and the job deletes it (10.3). The web app (18.4, 2026-11-08) deletes its token when a user leaves the phone and keeps a "token to delete" flag in `localStorage` until FCM confirms it, so a phone signed out offline deletes it at its next start, before anyone registers.
- Write the IANA `timezone` (from `ZoneId.systemDefault()`) to `users/{uid}` at app start when it changes.
- Create the notification channel `expiry` (importance high, name Expiry reminders) at startup.
- Tapping the notification starts `MainActivity` with the message data as intent extras; read `route` (`/pantry?filter=expiring`) in `onCreate` and `onNewIntent`. In the foreground do not show a system notification; the Home card is already live.
- Notification settings: on or off, delivery hour (whole hours, 5:00 to 21:00), stages, expired mode. Real delivery is after the chosen hour, usually a few hours after it: the job is scheduled three times an hour (after v1), but GitHub starts only a few of those runs (10.4). The job sends nothing from 22:00 (`LATEST_MINUTES`), so an evening hour can be skipped on some days. Since 2026-11-22 the apps say so instead of promising a time: Settings' row says "Daily, after 9:00 AM" (it said "shortly after"), the time's helper says "It comes after this time, often a few hours later. Nothing is sent after 10:00 PM, so an evening time can be skipped on some days." (the hour from `NotificationPrefs.LATEST_HOUR`, `NotificationRules.LATEST_HOUR` on the web), and the explanation and You're set say "after" the hour (5.4). The battery hint says "If reminders don't arrive", since a late reminder is GitHub's delay, not the phone's.

**10.3 Reminder job** (`backend/reminder/`, TypeScript, Node.js LTS, `firebase-admin`)

```
now = current time
state = jobs/state                                    # after v1, B25: usersCursor, lastUsersReadDate, lastPruneDate
if no usersCursor or lastUsersReadDate before today (UTC):     # the first run of each UTC day, and the first ever
  read = users where notificationPrefs.enabled == true
else:
  read = users where nextDigestAt <= now               # automatic single-field indexes; no composite index
read += users where updatedAt > usersCursor            # profiles the apps changed since the last run
                                                       # (both apps stamp updatedAt with the server time)
due = []
for user in read:                                      # until B25: every user with reminders on, every run
  local = toLocal(now, user.timezone ?? 'UTC')        # Intl.DateTimeFormat; handles DST
  if local.minutesOfDay < prefs.digestMinutes: continue
  if local.minutesOfDay >= 22 * 60: continue          # too late; skip today
  if user.lastDigestDate == local.date: continue
  due.add(user, local)
group due by householdId
for each household:
  # after v1: only current members (12.2 control 5); one read per due user
  for each due user u whose households/{id}/members/{u} does not exist:     # removed, or the household was deleted
    skippedNotMember += 1
    unless dry run: update users/{u} { householdId: null, updatedAt: server time }    # as their app would
                    only if the profile is unchanged since it was read (else they may have joined again; skip)
  keep the others
  # after v1: devices first, so a user with none costs no stock read
  for each kept user u:
    tokens = users/{u}/devices, the 10 most recently updated              # at most 10 a user
    if tokens is empty: skippedNoDevices += 1; drop u                     # try again next run
  if none remain: continue
  lo = min(local.date of its due users) - 14 days
  hi = max(local.date of its due users) + 3 days
  batches = query stock where expiryDate >= lo and expiryDate <= hi,
            ordered by expiryDate, at most 500                              # tombstones skipped (after v1, 9.8)
  for each due user u:
    items = []
    for b in batches:
      d = calendarDaysBetween(u.local.date, b.expiryDate)       # negative when expired
      if d in u.stages: items.add(stage = d, batch = b)         # d is 0 to 3
      if d < 0 and u.expiredMode != 'never':
        if u.expiredMode == 'daily' or b.expiryDate > (u.lastExpiredThrough ?? ''):
          items.add(stage = expired, batch = b)
    cooking = meals today where u is a cook, not cooked     # after v1 (5.9), when u.cooking; one meals query per household
    if items and cooking are empty: update u.lastDigestDate = local.date; continue    # with nextDigestAt, below
    result = fcm.sendEach(tokens, buildDigest(items, cooking, u.locale))    # after v1 in chunks of 500
    delete tokens that returned registration-token-not-registered or invalid-registration-token
      # after v1 not invalid-argument: FCM also answers it for a message it refuses, and the token is fine
    if any send succeeded:
      update u { lastDigestDate: local.date, lastExpiredThrough: local.date minus 1 day }
    else:                                                       # after v1
      count the attempt (u.digestAttempts = { date: local.date, count }); at 3 in one local day
      update u.lastDigestDate = local.date                      # done for the day; expired items wait for tomorrow
  # after v1 (B25): each of these updates that ends the day also sets u.nextDigestAt = nextDigestAt(u), tomorrow
  # after v1: each household and each user in try/catch. A failure is counted (failures, no IDs) and logged as
  # "<step> failed: <code>"; the run carries on and exits non-zero at the end so GitHub emails. A profile deleted
  # during the run (NOT_FOUND) is an account deletion: skippedDeleted, not a failure.
# after v1 (B25): every other user read (not due, no devices, a failed send, a removed member) gets
for user u in read whose digest step didn't set it:
  next = nextDigestAt(u)   # the first instant at or after now when u is due, if the profile stays as it is;
                           # now while due; none when reminders are off or there is no household
  if next differs from u.nextDigestAt (two times at or before now count as the same): update u.nextDigestAt = next
  # a profile deleted since it was read (NOT_FOUND) is left alone; any other failure is counted and keeps the cursor
if every one of those updates succeeded: usersCursor = the earliest read's time minus 5 minutes
                                         (and lastUsersReadDate = today on a full read)
if the first run of this UTC day (jobs/state.lastPruneDate before today; after v1, 10.4):
  prune activity documents older than 30 days and join requests older than 7 days (after v1 also tombstones older than 2 days, 9.8, and planned meals more than 28 days past, 5.9)
  # after v1: a deletion that fails after BulkWriter's retries is counted (pruneFailures), never thrown or printed,
  # and so is a household whose queries fail; the run then exits non-zero so GitHub emails
  if nothing failed: set jobs/state.lastPruneDate = today (UTC)   # else the next run prunes again
  # after v1 (B25) one write of jobs/state carries the cursor and the prune's date; without the state (its read
  # failed) the run reads everyone, sends, and writes no state and prunes nothing
  # jobs/state is the job's own document; the rules match no such path, so clients can neither read nor write it
```

After v1 every entry point (reminders, nightly export, restore) prints a failure as one fixed line with the error's code (a gRPC status name such as `PERMISSION_DENIED`, a Firebase or Node code, or one of the jobs' own such as `wrong-project`), never the error object or its message, including errors nothing handled: a Firestore error can hold the client and, through it, the service account key, and its message can name a full document path (13.4).

*After v1 (2026-11-23, B25, review EFF-04): reading only the users who may be due.* Each run used to read every user with reminders on, to find the few who were due; with the on-time trigger that was 120 or more reads a user a day. Now the job keeps `nextDigestAt` on each profile itself: `nextDigestAt(user, now)` in `digest.ts` is the first instant at or after `now` at which `dueAt` would say due if the profile stays as it is (today at the chosen time if today's window is still ahead, otherwise the next day not already done; `zonedInstant` in `time.ts` finds it on daylight saving days, the first of a repeated time and the end of a skipped one), `now` while due, none with reminders off or no household. A digest, an empty digest and giving up store tomorrow's time in the same update as `lastDigestDate`; a due user left for the next run (no devices, a failed send, a household whose step failed) keeps a time at or before now; any other user read gets a fresh time. What can make a user due sooner is a change the apps make: the time, the time zone, reminders on, a household. Both apps write those with `updatedAt` set to the server's time (the released builds too: Android 1.0.0, web 744d251), so the next run reads them through the cursor, as it would have read everyone before, and the digest goes out at the same run as before. The cursor only moves on once every new time is stored, and it is taken from the server's read time less 5 minutes, so a profile changed while the run went on is read again. The job only ever stores a time earlier than the truth or equal to it, so a time computed from a profile that changed later costs a read, never a digest. The daily full read gives users without the field theirs (everyone at the first run after the merge, and any profile a write without `updatedAt` changed, such as an edit in the console). Only `notificationPrefs.enabled == true` counts, as the old query had it. The selection needs no app change, no rules change and no composite index, so it went live with the merge (RUNBOOK). `backend/reminder/test/job.test.ts` replays today's selection and this one side by side over every quarter-hour and scheduled start for four days around each 2026 daylight saving change, every offered hour in five time zones plus odd stored minutes and half-hour zones, with profiles changed between runs and phones that miss runs, and checks they send the same digests at the same runs.

*After v1 (2026-11-13, review UX-03).* A new member's first digest therefore comes with the first run after their hour (and before 22:00) that finds something in their stages: hours after setting up, the next morning after 22:00, or not at all while nothing expires within 3 days. The job is unchanged; the app covers the first minutes with its local "You're set" notification (5.4).

Message shape per device token:

```yaml
notification: { title: <text>, body: <text> }
data: { type: expiry_digest, route: '/pantry?filter=expiring' (after v1 '/plan' when only meals), date: <local date> }
android: { priority: high, notification: { channelId: expiry, tag: expiry_digest } }
```

Digest text: one item gives the title “Milk expires tomorrow”; several give “N items need attention”. After v1 the title is decided by distinct names: one product with batches in several stages is one item, and its title is the sentence for its most urgent batch (the body's order), so it never reads “1 items”. Product names and meal titles are cut to 80 characters and the body to 1,000 bytes, ending with “…”, well inside FCM's 4,096-byte limit. The body has up to four lines in this order: Throw away, Today, Tomorrow, In 2 to 3 days. Each line names up to three products, then “+K more”. Best-before items say “past best before” instead of “throw away”. After v1 the cook's meals are a last line, or the whole notification when nothing expires (5.4).

**10.4 Scheduler** (`.github/workflows/reminders.yml`)

```yaml
name: expiry-reminders
on:
  schedule:
    - cron: '7 * * * *'          # after v1 three entries an hour, off the busy top of the hour
    - cron: '27 * * * *'
    - cron: '47 * * * *'
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

Pin the action versions to the current majors at build time. *After v1 (2026-11-10):* actions are pinned to commit SHAs (since 2026-11-22 a repository setting requires it, and only allowed actions run), the job declares `environment: production` (its keys are repository secrets since 2026-11-22), and `npm ci --ignore-scripts && npm run build` is a step of its own without secrets before the step that runs `node dist/main.js` (12.2 controls 8 and 9).

**Reliability (verified, Appendix C).** GitHub scheduled runs are best-effort: they can start 15 to 60 minutes late at busy times and are occasionally skipped; the minimum interval is 5 minutes; in public repositories schedules are disabled after 60 days without repository activity. (After v1 the repository is public; `keepalive.yml` re-enables the scheduled workflows monthly.) The design tolerates this: reminders are stateless windows (an item inside the 3-day window appears in every daily digest), the job catches up at any time before 22:00, and `lastDigestDate` prevents duplicates. The hourly job uses about 720 billed minutes per month of the 2,000 free for private repositories, so keep CI frugal (path filters, caching). Make sure Actions failure notifications are enabled in your GitHub notification settings.

*After v1 (2026-11-08), measured.* GitHub started 61 of about 290 hourly runs from 21 October to 2 November 2026, about 5 a day at any minute, with gaps of 5 hours on average and up to 8.5. Morning digests came about three hours late, and a digest chosen for 20:00 or 21:00 often had no run before 22:00 and was skipped for the day. So the workflow has three schedule entries an hour (minutes 7, 27 and 47): a dropped run then costs 20 minutes, not an hour. The job still sends at most one digest a day per user, however many runs start (`lastDigestDate`; `backend/reminder/test/job.test.ts` replays every scheduled start over six days and every offered hour in five time zones). The repository is public, so Actions minutes are free. After a week, compare the start times by hour (`gh run list --workflow reminders.yml`); if whole hours are still missing, the next options are a free external cron service calling `workflow_dispatch` (10.6) or Plan C. The daily prune no longer depends on a run starting in one UTC hour: it runs in the first run of each UTC day (10.3).

*After v1 (2026-11-22), measured again.* With three entries an hour, GitHub started 71 of about 500 scheduled runs from 15 to 22 November 2026. Runs fell in 6 to 11 hours of each day, with a median gap of 1.3 hours, 6.8 hours at the 90th percentile and up to 8.5. A 9:00 digest in New York came 0.4 to 7 hours late, about 3 hours on a typical day; none was missed, since the job catches up. More schedule entries were not enough, so the owner chose honest copy first: the apps no longer say "around" or "shortly after" the hour, and Settings names 22:00 (10.2). A free external cron service calling `workflow_dispatch` (10.6) or Plan C stays open, decided later (DECISIONS 2026-11-22).

*After v1 (2026-11-22), started on time.* The owner then chose an outside trigger (10.6): a Google Apps Script in the owner's own Google account (`backend/reminder/apps-script/trigger.gs`, tested in `backend/reminder/test/trigger.test.ts`) ticks every minute and starts the workflow through `workflow_dispatch` once per quarter hour (:00, :15, :30 and :45 UTC), on `main` with `dry_run` false. GitHub runs a dispatched workflow at once and the job takes about 20 seconds, so a digest arrives about a minute after its time; every quarter hour rather than hourly, so that time zones with half-hour offsets get theirs on time too. The script's GitHub token is fine-grained, for this repository only, with Actions: read and write, kept as a script property. It can start, re-run, cancel, enable and disable workflows and delete runs and artifacts, but not read secrets, change settings or environments, or approve deployments, so the restore waits for the owner's approval in its own `restore` environment (12.2 control 8). The three schedule entries stay as the backup, and the job still sends at most one digest a day per user. When GitHub refuses (an expired token), the script tries again every minute and emails the owner at most once a day. About 96 more runs a day, each reading every user with reminders on: the quota simulation's planning figure is now 120 runs a day, about 14 households under 70% of the free reads (9.7; RUNBOOK, Check quota usage). Since 2026-11-23 (B25, 10.3) a run reads only the users who may be due, so 120 runs a day cost about what 24 did: about 18 households. The apps keep the 2026-11-22 wording (10.2) until a day of runs shows the starts on time.

**Service account.** Create a dedicated service account for the job with least privilege (Firestore read and write, and permission to send FCM messages; confirm the current role names in the Google Cloud console). Store its JSON key only as the `FIREBASE_SERVICE_ACCOUNT_JSON` repository secret. Rotate it yearly. Never commit it.

**10.5 Tests.** Pure functions with a fixed clock: time zones on both sides of UTC, DST start and end days, month and year boundaries, a missed run followed by a catch-up, once versus daily expired mode, stage subsets, best-before wording, an empty digest, a user with no devices. `DRY_RUN=true` logs messages instead of sending. One integration test against the Firestore emulator with seeded data. After v1 (2026-11-08) also: the schedule replay (one digest a day per user), the prune key, which FCM codes delete a token, chunks, the title by distinct names and the length limits as unit tests; and against the emulator a day of three runs an hour, a sender that throws, three failed attempts, the device cap, devices read before stock, the stock cap, a profile deleted during the run, and a prune with failures that the next run repeats. After v1 (2026-11-23, B25) also: `zonedInstant` and `nextDigestAt` on daylight saving days, skipped and repeated times, odd stored minutes and unknown zones; the side-by-side replay of the old and new selection (10.3); and against the emulator the stored times after a run, later runs reading only the due and the changed, a user whose time, time zone or reminders change between runs, a profile deleted before its time is stored, a dry run that stores nothing, and a run whose state can't be read.

**10.6 Alternatives.** *Plan B, a more reliable scheduler (closed after v1, 2026-11-08).* It was to deploy the same core module as a Cloud Function triggered by Cloud Scheduler, which needs the Blaze plan with a payment card. The project uses no Cloud Functions and no paid Google Cloud service, so Plan B is not an option; GitHub's timing is handled with more schedule entries (10.4). If that is not enough, a free external cron service can trigger the workflow through `workflow_dispatch`, with a fine-grained token limited to this repository and Actions: write, kept only in that service; the workflow's only input stays `dry_run`. Chosen on 2026-11-22 (10.4): the trigger is a Google Apps Script in the owner's own Google account, so the token stays in that account rather than with a cron service.

*Plan C, on-device reminders (possible because the app was Android only; since 2026-10-31 the web app for iPhone cannot schedule its own notifications, so Plan C would cover Android only).* No server, no service account, no GitHub job and no FCM tokens. A WorkManager task chained daily (each run schedules the next one at the user's chosen hour) queries the server, not the cache, for batches expiring inside the window, builds the same digest and posts it as a local notification; it falls back to the cache when offline. Trade-offs: timing is approximate, and aggressive battery management on some phone brands can delay or block background work, whereas FCM delivery is generally less affected, so add a Settings screen that explains how to exempt the app from battery optimization. Do not request exact-alarm permissions. The human chose the server-sent digest, so Plan C is not part of v1; it is kept here in case the reminder job proves too burdensome; the digest rules stay the same, and `users.timezone`, the `devices` subcollection and the server-written fields are then unused.

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

In the built-in browser the app runs this script after each page load and reads the result with evaluateJavascript and feeds it to the same extractor (after v1 the script also caps the JSON-LD it returns, 11.6, and sends the page's markup when no JSON-LD mentions a Recipe, DECISIONS):

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
| prepTime, cookTime, totalTime | minutes | ISO 8601 durations such as PT1H30M; total missing means prep plus cook. After v1 (2026-11-13, LOGIC-15) the plain-text forms also read "1h20m" (80; it was 20), "1 1/2 hours", "1½ hours" and "1,5 hours" (90) |
| recipeIngredient | ingredients | List of strings; after v1 (2026-11-13, LOGIC-15) one string with line breaks, which some sites send, is one ingredient per line |
| recipeInstructions | steps | String (split on line breaks), list of strings, `HowToStep.text`, or `HowToSection.itemListElement` (section name becomes `group`) |
| author, keywords, recipeCategory, recipeCuisine | sourceName, tags | Author name; up to 8 tags |
| page URL | sourceUrl | Canonicalize and drop tracking parameters (`utm_*`) |

**11.4 Fallbacks.** Microdata (`itemtype` containing `schema.org/Recipe`, with `itemprop` fields) through the same mapper. Last, heuristics: `og:title`, `og:image` and common recipe-plugin containers (class names starting `wprm-recipe-` or `tasty-recipes-`). Heuristic results are labeled “Check this recipe” in the preview.

**11.5 Ingredient parsing (best effort; `raw` is always kept).** Parse a leading quantity (integers, decimals, unicode fractions such as ½, mixed numbers like 1 1/2, and ranges like 2-3 using the lower value; after v1, 2026-11-13, LOGIC-15, a comma before one or two digits is a decimal, "1,5 kg Mehl", and before exactly three a thousands separator, "1,000 g flour"; any whitespace counts as a space, 8.1), then a known unit (cup, tbsp, tsp, g, kg, ml, l, oz, lb, pinch, clove, can, package, and plurals), then the name (drop text after the first comma and anything in parentheses). Lines that do not parse keep only `raw` and are not scaled. **In pantry** matching: a product matches an ingredient when every word of its `nameKey` (singularized; after v1 in the comparison form of 5.5) appears in the ingredient line. Show it only as “Probably in pantry” and let the user toggle it; never decide silently.

**11.6 Safety and quality**

- Sanitize imported text: strip tags; limits: title 200, ingredient 300, step 2,000, at most 100 ingredients and 100 steps.
- After v1 (2026-11-12): a page's JSON-LD is read only up to a limit (`JsonLdLimits`): at most 32 blocks, each at most 1 Mi characters, 3 Mi in all; a block over its limit, or one that would pass the total, is left out whole (a cut block would not parse). The built-in browser's script applies the limits (11.2), the app applies them again to the script's result, which the page's own scripts could change, and leaves a result over 16 Mi characters unread; parsing and extracting run off the main thread. A fetched page's JSON-LD gets the same limits.
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
5. Removed or departed members lose access at once (rules re-check membership on every request) and the app clears the local cache. After v1 the reminder job, which reads with the Admin SDK and so skips the rules, also sends a household's digest only to users it lists as members, and clears a removed member's household pointer as their app would (10.3).
6. On sign-out or account switch call `terminate()` then `clearPersistence()` so another person on the same phone cannot see cached data. Sign-out and account deletion must finish even when the screen that started them closes (after v1: they run in the application scope, and instrumented tests sign out and delete an account through Settings, then check the cache and the server). After v1 (2026-11-08) clearing local data, on sign-out, account deletion and leaving or losing a household, also clears the sync log (9.8, whose keys name the household's collections) on both apps, and on Android the built-in browser's cookies, site storage, saved HTTP sign-ins and cache, and Coil's memory and disk caches of recipe photos (`DeviceCaches`). The web app also deletes its push token (10.2, 18.4). What stays on the phone is listed in PRIVACY.md.
7. TLS only. No cleartext traffic in the app (Android `usesCleartextTraffic=false` in the manifest).
8. No secrets in the app (Firebase configuration values are identifiers, not secrets). The service account key exists only as a GitHub Actions secret. `.gitignore` covers `*.jks`, `keystore.properties`, `local.properties`, `service-account*.json` and `.env*`. Enable GitHub secret scanning and push protection. After v1 (2026-11-10) the reminder, export and restore jobs declare a GitHub environment `production`, with no required reviewers so scheduled runs start on their own. Since 2026-11-22 their secrets (both service account keys and `EXPORT_ENCRYPTION_KEY`) are repository secrets and the environment holds none and has no branch rule (owner's choice, DECISIONS 2026-11-22): only the owner can push branches or start workflows, and workflows run for pull requests from forks get no secrets. In each job, `npm ci --ignore-scripts` and the build run in a step without secrets, and only the step that runs the job gets a key (RUNBOOK, How the jobs get their keys). Since 2026-11-22 a fine-grained GitHub token (this repository only, Actions: read and write) is kept outside GitHub, as a script property of the owner's Apps Script trigger that starts the reminder job on time (10.4). It can start any workflow but cannot approve deployments, so the restore declares a `restore` environment whose required reviewer is the owner, and every restore run waits for that approval.
9. Supply chain: Dependabot or Renovate for Gradle, npm and GitHub Actions; pin Actions to major versions or commit hashes. After v1 (2026-11-10): Actions are pinned to full commit SHAs with the version in a comment; Dependabot covers every npm project, `tools/themes` included; CI and the jobs install npm packages with `--ignore-scripts`; a transitive library with a known advisory is raised with a Gradle constraint until its parent brings the fixed version (Guava, through Firestore). Gradle dependency verification is not used (DECISIONS 2026-11-10). After v1 (2026-11-14): an npm package with an advisory that no parent release fixes is raised with an npm `overrides` entry, each one in DECISIONS with when to remove it: `@grpc/grpc-js` (pinned by the Firebase web SDK) in `web/` and `firebase/`, glob, minimatch, uuid and OpenTelemetry core inside the Firebase CLI and the web build tools; all four npm projects pass `npm audit` except the web build's deepmerge, which has no fixed release (DECISIONS 2026-11-14). `error_prone_annotations` has a Gradle constraint too, so the instrumented tests resolve after Firestore 27.2 (DECISIONS 2026-11-14). After v1 (2026-11-22): CI's web job runs in Playwright's container image, pinned by tag and digest (`mcr.microsoft.com/playwright:v1.65.1-resolute@sha256:…`); Dependabot doesn't update images, so it is raised by hand with `@playwright/test` (RUNBOOK, Dependency updates), and the job warns when the two differ. The `changes` job that picks CI's jobs is plain shell, not a third-party action. After v1 (2026-11-22): the repository's Actions settings allow only GitHub's own actions plus `gradle/actions/setup-gradle` and `gitleaks/gitleaks-action` (not Marketplace "verified creators"), and require every action to be pinned to a full-length commit SHA; a new outside action is added to that list (RUNBOOK, Dependency updates).
10. SHOULD (M7): Firebase App Check with the Play Integrity provider; start in monitor mode and enforce on Firestore only after all family devices pass. Distribution outside Google Play needs the matching Play Integrity settings (see the Firebase App Check Android docs). Use the debug provider for emulators and CI. Setting up the Play Integrity provider may require linking the project through a Google Play Console account, which has a one-time fee; check this at M7, and skip App Check if it would need a paid account, because it is optional.
11. Privacy: collect only email, display name, household data, device tokens and time zone. No analytics, no ads, no third-party SDKs besides Firebase. Provide in-app Delete account and Export data. Crashlytics is optional and carries no personal data. Add a one-page `docs/PRIVACY.md` listing exactly these data. After v1 (2026-11-12): crash reports stay on by default, with the opt-out in Settings, and prod builds turn Firebase Sessions off (`firebase_sessions_enabled=false` in `src/prod`), so nothing is sent at app start; release builds drop verbose, debug and info calls to the system log, the libraries' included (R8 `-assumenosideeffects` in `proguard-rules.pro`), keeping warnings and errors; the invite code goes to the clipboard marked sensitive, labeled "Invite code" (`sensitiveClip`); PRIVACY.md lists every kind of data both apps, the reminder job and the backups keep, the Firebase installation ID included, and what is left after Delete account.
12. Web view hardening as in 11.6.

**12.3 Firestore Security Rules: reference draft.** This is a starting point; the tests in 12.4 define correctness. After v1 the deployed rules also cover `meals` (5.9): members read and write; the date must be YYYY-MM-DD, the slot breakfast, lunch or dinner, the title 1 to 120 characters, servings 1 to 100 or null, at most 20 cooks and a note up to 200 characters; tombstones as elsewhere; `cookedAt` and `cookedBy` are both null, set together by the writer at server time, or left as they were (so another member can still change a meal someone cooked). Limits to respect: at most 10 `exists`, `get` and `getAfter` calls per request (20 for batched writes), and each lookup is billed as a read.

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
          && request.resource.data.platform in ['android', 'web'];
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
          && request.resource.data.keys().hasOnly(['displayName', 'joinCode', 'status', 'requestedAt', 'emailMasked'])
          && emailMaskedOk(request.resource.data)   // after v1: absent, or the sign-in's email masked
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

Notes on the draft: after v1 the deployed rules (`firebase/firestore.rules`) also check tombstones (`deletedAt`, 9.8) on products, stock, items and recipes, recipe names on products (`aliases`, at most 30) and on items (`aliasOf`), opened batches (`openedOn`, a date) and products (`openedShelfLifeDays`, 1 to 365), a list's `aisleOrder` (at most 30), and the Insights `stats` documents (5.7). The reminder job uses the Admin SDK, which bypasses rules. The household creation batch (household, owner member, invite code, user profile update) relies on `getAfter` and `existsAfter`. Always write `expiryDate` explicitly (null when there is none), because the stock rule reads it. To delete a household, the owner deletes every subcollection first, then the owner's own member document and the household document in the same batch. After v1 (2026-11-08) the rules also list each collection's allowed fields and sizes and check who wrote a document (8.1), let members delete only by tombstone, let Insights counts move by one per write and require an ownership transfer to demote the old owner (8.5); each needs one `getAfter` at most, and a stats write evaluates only the day that changed, to stay under the 1,000 expressions a request may evaluate. After v1 (2026-11-09) an item update may not write a second `deletedAt`, a meal's `cookedAt` is set only where it is null, and an item create needs `existsAfter` on its list (8.5 invariant 10).

Joining takes two steps: the person creates a join request with a valid code, then the owner approves it in one batch that sets the request to approved and creates the member document (the rules accept a member document for someone else only in that case). Declining sets the request to declined; the person's app shows the result and deletes the request.

**12.4 Rules test matrix (MUST).** Use the emulator and `@firebase/rules-unit-testing`; run it in CI.

| Actor | Action | Expected |
| --- | --- | --- |
| Signed out | Any read or write | Denied |
| Non-member | Read any household document | Denied |
| Non-member with an active code | Create own join request with that code | Allowed |
| Same user | Wrong or paused code, someone else's uid, or a status other than pending | Denied |
| Requester | Join request with `emailMasked` equal to their sign-in's email masked (after v1), or without it (older builds) | Allowed |
| Requester | Join request with someone else's masked email, a full email, another domain, or an email without one in the sign-in (after v1) | Denied |
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
| Client | Write `lastDigestDate`, `lastExpiredThrough`, `digestAttempts` or (after v1) `nextDigestAt` | Denied |
| Removed member | Any household read | Denied immediately |
| New user | Create household batch (household, owner member, code, own profile update) | Allowed |
| Owner | Transfer ownership without demoting themselves in the same batch (after v1) | Denied |
| Member | Delete a product, batch, item, recipe or meal outright instead of by tombstone (after v1) | Denied |
| Member | Delete a list's items in the batch that deletes the list, or after it (after v1) | Allowed |
| Member | Set an Insights count to any value, or move it by more than one in a write (after v1) | Denied |
| Member | Write a field the apps do not write, or text, lists or numbers beyond 8.1's limits (after v1) | Denied |
| Member | Name someone else as `createdBy` or `checkedBy`, or log an event of an unknown type or with a name over 40 characters (after v1) | Denied |

**12.5 If you ever publish on Google Play.** Add a privacy policy URL, the Data safety form, a web link for account deletion, App Check enforcement, and a fresh review of the free-quota math.

## 13. Quality, CI and release

**13.1 Testing (MUST)**

- **Unit (pure Kotlin, JVM):** status calculation, restock engine, shelf-life learning, ingredient parser, JSON-LD extractor against fixtures, ISO duration parser, invite-code generator and formatter (JUnit, MockK, Turbine for Flows; after v1 hand-written fakes and `kotlinx-coroutines-test`, 7.3). Reminder job: digest builder and time logic (Node tests).
- **Rules tests (emulator):** the matrix in 12.4.
- **Compose UI and screenshot tests:** pantry row, status chip, add-item sheet, grocery list, recipe card; light and dark; font scale 1.0 and 2.0. After v1 (2026-11-13, review CODE-07) every screen and sheet has one at 1.0 and 2.0, light and dark, except the in-app browser's page (a WebView; its bars are covered), sheets and dialogs captured with their own window; the references are committed in `app/src/test/screenshots` at half the device's pixels and compared on the Mac with `./gradlew verifyRoborazziDevDebug` (CLAUDE.md's finishing checks), not in CI, as the web's are. Text that changes with a release or the calendar is fixed in the test, not taken from the build (2026-11-14: About's version line, "1.0.0-beta.9-dev (build 10)" as recorded), so a release leaves the references matching.
- **Instrumented tests (`androidTest` with the Compose test rule and the Firebase emulators):** sign up, create a household, a second user requests to join with the code and the owner approves, add a pantry item, low-stock auto-add, propagation between two clients, an offline write followed by reconnect. After v1: two-phone tests give each simulated phone its own FirebaseApp (`TestPhone`) and release it after each test (listeners stopped, Firestore terminated and its cache deleted; the app is kept, since deleting it breaks Auth's emulator setup for the next one), because every Firestore holds Android network callbacks until it is terminated and an app may hold only about 100; `TestPhoneTest` checks that closing phones gives them back. A test that reads on a phone right after it loses its household first waits for that phone's local data reset (`awaitLocalDataCleared`). Hilt tests leave no session behind either: the application scope is replaced in tests (`TestCoroutinesModule`) and `HiltTestRunner` adds a listener (`ReleaseAppScopes`) that cancels it after every test, since each test gets a new graph whose `SessionManager` otherwise keeps watching the shared default Auth and Firestore and can clear local data under a later test; `AppScopeReleaseTest` checks that a test finds the previous one's scope cancelled. Tests that need the internet or seed benchmark data run only when asked (`@LiveTest` with `live=true`, `@PerfTest` with `perf=true`): `HiltTestRunner` leaves them out of every other run (`notAnnotation`) instead of letting them skip, which the report counted as failures (2026-11-13, review BASE-02); `OptInTestsTest` checks it. After v1 (2026-11-21, review CODE-08) a third kind runs only when asked: a DESIGN acceptance time that only a quiet device can hold (`@TimingTest` with `timing=true`), so far `SharePreviewTimingTest`, M5's share to preview in under 5 s, activity start included (the median of three); the default run keeps `RecipeUiTest`'s 5 s from the activity resuming and 60 s for the whole share. The same review added `GroceryFlowTest` (two phones: quick add with its amount and aisle, Already on the list and Increase, checking off with who checked it, Delete and Clear checked with their Undo, the store order one phone learns sorting the other's aisles, and Add to pantry with its Undo) and `ClearHouseholdDataUiTest` (the owner's two steps from Settings, Cancel and a wrong password clearing nothing, the right one emptying a member's pantry; a member not offered it). A test that taps after typing first closes the keyboard and waits until the control it taps has stopped moving (`closeKeyboard` in `KeyboardSupport.kt`): the keyboard slides the screen while it opens (`imePadding`, resized dialogs), and a tap in that moment does nothing, which is how `JoinAndSignOutUiTest` lost its Continue tap under a full run's load.
- **Manual QA script per milestone:** physical devices (two different brands if possible), offline mode, time zone change, a DST day, notification permission denied, battery saver, 200% font.
- **Performance budgets:** cold start under 2.5 s on a mid-range Android phone, 60 fps scrolling with 500 batches, initial household sync under 2 s on 4G. After v1 (2026-11-13, review PERF-05): a cold start ends when Home shows its cards, not at its first frame (the loading skeleton). Home reports itself drawn then (`ReportDrawnWhen`), so Android records the time to full display and `StartupBenchmark`'s `timeToFullDisplayMs` measures the budget. The first-sync budget stays as it is until the owner measures it on a phone on 4G (QA.md 4); the emulator, with no network delay, can only give a lower bound.
- **Baseline profile:** release builds ship a baseline profile (`app/src/main/baselineProfiles/baseline-prof.txt`) so start-up and the first scroll run precompiled code; `androidx.profileinstaller` installs it on phones that get the APK outside Google Play, where Android compiles it during the next idle maintenance. The `benchmark` module generates it (`BaselineProfileGenerator`) and measures cold start and the first Pantry scroll with and without it (`StartupBenchmark`, `PantryScrollBenchmark`) against the dev benchmark build and the emulators. Regenerate it with `./scripts/baseline-profile.sh` after large UI changes and library updates (docs/RUNBOOK.md). After v1 (2026-11-13, reviews PERF-01, PERF-06, BASE-08): benchmarks and the profile run on one setup, a headless emulator on the Mac's GPU (RUNBOOK "Benchmark setup"); the script refuses software rendering and warns when macOS throttles the emulator. Every step of the profile journey checks that it opened what it should and fails the run otherwise (the benchmark build shows the screens' test tags to UI Automator as resource ids), so a renamed button can't drop a screen from the profile unnoticed. A hand-kept rules file next to the generated one (`crash-reports-prof.txt`) keeps the crash-report and Firebase Sessions code that builds on real Firebase run at every start but the emulator builds don't. After the new look (19.13; after v1, 2026-11-17) the profile was regenerated (17,651 rules after the Pantry search fix below, 17,691 with the stretch warm-up on 2026-11-23, 17,789 with Compose 1.13 on 2026-11-24; the app's own 1,913 → 2,236, with the new design system pieces, Your kitchen, Groceries, Recipes and the sign-in screens); its journey opens Your kitchen from Home's card by its test tag, since the card has had no "See your kitchen" label since step 2. Measured as before, medians of 10 cold starts: to Home's cards 757 ms with the profile and 881 ms without (2026-11-13: 710 ms), the first frame 359 and 388 ms (358 ms); the first Pantry scroll P50 4.8, P90 6.2, P99 8.0 ms with no janky frames and 96 frames an iteration, so the short flings and 21 ms P99 of 2026-11-13 are gone.
- **Frames on every screen** (after v1, 2026-11-17; RUNBOOK "Baseline profile", `--jank`): `JankBenchmark` keeps review 08's scenarios (review 08, B23, B24): the first and steady scrolls of every tab, Your kitchen opened while its charts grow, every tab twice, Add item and Plan's add sheet opened, dragged and closed, and typing "item 12" in Pantry's search and Groceries' quick add, all with the profile, on the perf household that `PantryScrollPerfTest` seeds (500 batches, 150 grocery items, 40 recipes without photos, three weeks of meals, six months of counts). The emulator drifts between sessions: on 2026-11-17 the build from before the look (`98eb650`) was three to four times as janky with the keyboard up as review 08 measured on 2026-11-02, so a number that moves is checked against the previous build run the same day (an A/B), not against an older table. Frame CPU time P50 / P90 / P99 in ms, then the share of janky frames (`frameOverrunMs` above 0) over five iterations:

  | Scenario | Review 08 (`0334b6a`, 2026-11-02) | Before the look (`98eb650`), 2026-11-17 | New look, 2026-11-17 |
  | --- | --- | --- | --- |
  | Pantry, steady scroll | 4.0 / 5.4 / 7.4, 0.0% | — | 4.8 / 6.5 / 8.1, 0.0% |
  | Groceries, first scroll (150 items) | 4.1 / 5.7 / 7.5, 0.0% | — | 4.8 / 6.9 / 9.9, 0.0% |
  | Groceries, steady scroll | 4.0 / 5.5 / 7.5, 0.0% | — | 4.8 / 6.6 / 9.1, 0.0% |
  | Recipes, first scroll (review 08 with 20 photos, now none) | 3.9 / 6.2 / 9.6, 0.8% | — | 4.8 / 7.1 / 12.3, 0.8% |
  | Recipes, steady scroll | 3.8 / 5.7 / 9.7, 0.3% | — | 4.5 / 6.7 / 8.7, 0.0% |
  | Plan, first scroll | 3.6 / 5.4 / 7.5, 0.0% | 4.6 / 6.6 / 10.3, 1.0% | 4.4 / 6.7 / 21.9, 7.2% (20.3% in a second run); with the stretch warm-up (2026-11-23, below): 4.1 / 7.0 / 11.6, 7.1%, against 4.5–4.9 / 7.4–13.6 / 20.4–24.8, 6.3–17.4% for `main` the same day |
  | Home, first scroll | 3.6 / 6.2 / 25.7, 3.4% | — | 4.9 / 8.0 / 25.9, 2.6% |
  | Your kitchen: open, charts grow, fling, back | 11.6 / 19.4 / 26.3, 49.4% | — | 8.1 / 19.8 / 24.5, 46.8% |
  | Every tab twice | 3.6 / 15.6 / 20.2, 12.5% of 1,275 frames | 4.0 / 17.1 / 20.7, 15.0% of 1,331 | 4.5 / 17.8 / 22.2, 18.1% of 1,047 |
  | Add item: open, keyboard, drag, close | 4.0 / 8.4 / 21.4, 5.9% | 4.8 / 11.1 / 28.9, 12.1% | 5.1 / 15.2 / 23.1, 14.5% |
  | Plan's add sheet: open, drag, close | 3.5 / 16.7 / 20.3, 12.5% | — | 4.0 / 8.9 / 19.5, 6.1% |
  | Typing in Pantry's search | 3.9 / 16.2 / 19.7, 17.8% | 17.6 / 19.8 / 28.0, 64.9%, 36 frames an iteration | 18.7 / 32.6 / 34.8, 89.1%, 97 frames; with the fix below (two runs against two without it, alternating): 8.6–9.6 / 18.6–18.7 / 23–26, 23–31%, 54–56 frames, against 94–95% and 96 frames |
  | Typing in Groceries' quick add | 7.7 / 18.8 / 34.7, 26.9% | 10.9 / 20.3 / 33.0, 35.6% | 5.1 / 12.4 / 24.6, 13.0% |

  What the A/B says. Scrolling stays smooth everywhere but Plan. **Groceries' quick add** is about three times less janky than before (B23's suggestions off the main thread), and Plan's add sheet half as janky. **Tab switches** have as many late frames as before (about 190 against 200: each tab is composed for the first time inside the fade), in a fade 100 ms shorter, so their share went up; the late frames come from that first composition (B24's single fade removed the slide, not them). **Your kitchen** draws its first frame faster (P50 11.6 → 8.1 ms, B24) but still misses the deadline on about half its frames while the charts grow. **Pantry's search** with the keyboard up is slow on this emulator for both builds (each frame waits for a buffer, `dequeueBuffer`, while the compositor draws the keyboard), and the new look drew on every vsync while typing. Composition tracing showed why: the rows on screen faded and slid at every letter (the first letter also leaves the default view, so every row got a new key), and rows that a wider search brought back were highlighted for 1.5 s as if another phone had changed them, a bug since the highlight came in. Fixed the same day (6.4): typing draws 54–56 frames an iteration instead of 96, 23–31% of them janky instead of 94–95%, under the 65% of the build before the look; Pantry's steady scroll is unchanged (P99 7.6 ms, no janky frames). **Plan's first scroll** compiled two GPU shaders on the RenderThread (8–11 ms each); Macrobenchmark clears the shader cache before every iteration, and a phone keeps compiled shaders on disk, so people saw it only on the first scroll after an install or update. Fixed on 2026-11-23, next paragraph.

  **Stretch overscroll warm-up** (after v1, 2026-11-23). The traces (RUNBOOK "What a late frame did") put both compiles in the first frame that draws `drawLayer [AndroidEdgeEffectOverscrollEffect]`: from Android 12 a list pulled past its end is drawn into an offscreen layer (the first program) and the layer onto the screen with the stretch shader (the second). Plan's week is short enough for the benchmark's first flings to reach its end, so most iterations stretched and compiled; the look itself draws nothing new there, and every list stretches the same way at its end. `StretchOverscrollWarmUp` (`core/designsystem/motion`) compiles both while nothing moves: ½ s after the start-up screen reports itself drawn it pulls a one-row `LazyColumn` (a group row with a tinted +) through Compose's own overscroll effect for two frames, then leaves the composition. It lies under the tab bar, which draws over it, so nothing shows (screenshots of the bar while it was held stretched for 300 frames match pixel for pixel), and it has no semantics. It must be under an opaque surface smaller than the screen and as wide as the lists: Skia drops whatever a frame drew before a full-screen opaque fill, and a narrower list draws its layer anti-aliased, with other programs (DECISIONS). Measured the same day, alternating with `main` (`c174648`), headless on the Mac's GPU, on battery, five iterations a run:

  | Scenario | `main` | With the warm-up |
  | --- | --- | --- |
  | Plan, first scroll, three runs each | 4.9 / 13.6 / 24.0, 16.3%; 4.7 / 9.8 / 24.8, 17.4%; 4.5 / 7.4 / 20.4, 6.3% | 4.9 / 7.3 / 18.4, 10.9%; 4.8 / 8.3 / 20.8, 7.9%; 5.1 / 8.2 / 20.7, 7.4%; with the new profile 4.1 / 7.0 / 11.6, 7.1% |
  | Plan, iterations that reached the end (stretched) | 12 of 15, each compiling 2 programs (12.8–20.1 ms together): 4.7 / 9.8 / 24.7, 14.6% | 9 of 20, none compiling: 4.3 / 7.0 / 11.5, 7.3% |
  | Plan, iterations that didn't | 4.7 / 8.0 / 19.9, 7.7% | 5.1 / 8.7 / 20.9, 9.4% |
  | Home, first scroll, with the new profile (before it) | 3.0%, 4.6% (4.1%, 5.1%, 5.1%) | 1.8%, 2.6% (5.2%, 6.7%, 6.2%) |
  | Every tab twice, with the new profile (before it) | 23.9%, 22.7%; P90 17.3, 15.2 ms (19.5%, 23.1%) | 22.4%, 18.9%; P90 12.0, 12.7 ms (26.1%, 22.9%) |
  | Cold start to Home's cards, median of 10, with the new profile (before it) | 916 ms (878, 888) | 890 ms (908, 885) |
  | First frame, median of 10, with the new profile (before it) | 430 ms (421, 397) | 426 ms (412, 417) |

  How many iterations reach the end varies from run to run (the flings' length depends on the emulator), hence the split rows. The janky frames left in Plan's first scroll, as in the iterations that don't stretch, wait for a buffer (`dequeueBuffer`, `waitForBufferRelease`) while the emulator's compositor catches up; they are the same in both builds. Home's first scroll also stretches now and then (Home reaching its top); on `main` those iterations compiled one or two programs more, with the warm-up none. The baseline profile was regenerated for the warm-up and the changed tab bar (17,651 → 17,691 rules); before it, Home and tab switches ran that code unprofiled and were a little more janky (the numbers in brackets).

**13.2 CI** (GitHub Actions). On pull requests and main: `./gradlew spotlessCheck lintDebug testDebugUnitTest assembleDebug`, rules tests against the emulator, and reminder job tests. After v1, CI also compiles the `benchmark` module (`:benchmark:compileBenchmarkKotlin`); the benchmarks themselves need an Android device and run by hand. Use path filters, Gradle caching and a concurrency group to protect the 2,000 free private-repository minutes.

After v1 (2026-11-22, reviews EFF-08 and BASE-03; the repository is public, so minutes are free and the point is the wait):
- **Per-job path filters.** A first job, `changes`, compares the change with its base (the pull request's base, or the previous `main` for a push) and each job runs only when its files changed: Android on `app/`, `benchmark/`, `gradle/` and the Gradle files; the rules tests on `firebase/`; the reminder job on `backend/` and `firebase/` (its emulator); the web on `web/`, `firebase/` (hosting headers, emulator) and the Android strings, icons and theme its generated files come from. A change to `ci.yml`, a run started by hand, or a base that can't be fetched runs every job. A skipped job counts as passed; `main`'s ruleset requires no checks.
- **The web job** runs in Playwright's image, with WebKit and its system libraries already installed (12.2, control 9), instead of installing them on every run.
- **The Android job** also compiles the instrumented tests (`compileDevDebugAndroidTestKotlin`), which run only on the Mac; two broke unseen in B21.
- CLAUDE.md's finishing checks list everything CI runs (the reminder job's checks and integration test, `gen:check`, `e2e:preview`, the instrumented tests' compile), so a change can't pass the local list and fail CI.
- Job durations, the median of the last 15 successful pull request runs before the change, then this change's runs:

  | Job | Before (median, p90) | This change (its runs on `2a6f934` and `c3b217c`) |
  | --- | --- | --- |
  | `changes` | — | 4–6 s |
  | Android | 284 s, 362 s | 368 s and 524 s: it also compiles the instrumented tests, and this branch changed the dependency list, so its Gradle cache only partly matched main's (pull requests read the cache `main` writes) |
  | Web | 346 s, 416 s | 350 s and 348 s: pulling the image (29 s) costs what installing WebKit did (about 20 s plus setup) |
  | Rules tests | 46 s, 53 s | 50 s and 42 s |
  | Reminder job | 45 s, 53 s | 43 s and 55 s |
  | Waiting, first job to last | 374 s | 378 s and 535 s |

  So the image saves no time today. It stays because the browser and its system libraries no longer come from
  Ubuntu's mirrors on every run, and the job runs the browser Playwright tested with. The path filters are the gain:
  a web-only change runs about 360 job-seconds instead of 730 (no Android, rules or reminder job), an Android-only
  change skips the rules, reminder and web jobs, and a backend change skips Android and the web.

**13.3 Release and distribution**

- Version in `app/build.gradle.kts`: `versionName` (semantic version) and an incrementing `versionCode`; tag `v1.0.0`. After v1: betas 1.0.0-beta.1 to 1.0.0-beta.11, then 1.0.0 (build 13, 2026-11-21), the first release without a beta number; later releases name their version (`--name`, RUNBOOK).
- **Signing:** create one upload keystore, keep it and `keystore.properties` out of the repository, and back the keystore up somewhere safe (if it is lost, testers must uninstall and reinstall).
- **Distribution:** Firebase App Distribution with tester emails (the human's choice; free). Collect each tester's email address for the invite; a Google account email is the safest choice.
- **Update gate:** on launch read `config/app.minSupportedBuild`; if the installed `versionCode` is lower, show a full-screen “Please update” with a link.
- **Runbook** (`docs/RUNBOOK.md`): rotate the service account key, regenerate an invite code by hand, check quota usage, re-enable the workflow, restore from an export.

**13.4 Backups.** Do not assume the free plan includes managed backups (verify in the Firebase console). v1 provides Settings > Export (JSON of products, stock, lists and recipes, without tombstones; after v1 also the Insights month documents, as an added `stats` array in format version 1, which the restore workflow also writes). In M7 the reminder job SHOULD also save a nightly JSON export per household as a private Actions artifact kept for 14 days. After v1 the repository is public, so the export is encrypted (AES-256 with a key-stretched passphrase in the `EXPORT_ENCRYPTION_KEY` secret) before it is uploaded, the restore workflow decrypts it, and workflow logs never contain user IDs, full household IDs or household contents (docs/RUNBOOK.md). Nor the service account key: the jobs count failed writes instead of throwing them and print errors as a fixed line with their code only (10.3). After v1 (2026-11-08) the reminder job also counts a failed household or user (`failures`) and logs it as `<step> failed: <code>`, without IDs, and the daily prune that keeps the retention in PRIVACY.md runs in the first run of each UTC day. After v1 both exports also include the planned meals (5.9) as an added `meals` array, which the restore workflow writes back. After v1 (2026-11-11) a restored document with a field of another type no longer blanks a screen: both apps read it with that field missing (8.6).

After v1 (2026-11-11):
- **Settings > Export reads from the server** (`Source.SERVER` on Android, `getDocsFromServer` on the web). Offline it says "You're offline" (a snackbar by the button) and saves nothing, instead of a file of whatever the phone had cached. On Android the system's Save dialog opens only once the data is read, so offline there is no empty file either.
- **Settings > Export also holds the person's own profile and the household's activity log** (the last 30 days, 10.3), as added `profile` and `activity` fields in format version 1. The nightly export leaves both out. The restore never writes them, nor the household or its members.
- **The restore stamps every document it writes** with `updatedAt` at the server's time of the restore and `updatedBy: 'restore'` (grocery lists take `updatedAt` only, the one stamp their rules allow). Every phone's changes-only listener (9.8) then shows the restore within a minute. Before, documents came back with their old `updatedAt`, older than a phone's cursor, and a phone could show the household empty until its next daily full read. Members edit restored documents as any other: every field the restore writes is in the rules' allow-lists, which a rules test checks.
- **The restore converts only the known timestamp fields** back to timestamps: `createdAt`, `updatedAt`, `deletedAt`, `checkedAt`, `cookedAt`, `joinedAt`, `requestedAt` and `exportedAt`. Other text stays text, even when it looks like a date-time (a note "2026-12-24T18:00:00Z"). Before, any such text became a timestamp, which the apps could not read.
- **The restore checks the target household first**, in the dry run too. It must exist, and its `ownerUid` must be the exported household's. A target with another owner, or an export without its household document, is refused (`different-owner`) unless the workflow's **different owner** input is ticked (`--different-owner`).
- **The export and restore workflows run their steps with `shell: bash`**, so a step fails when any command in a pipe fails (`pipefail`): an archive is never uploaded, or a restore started, from half a pipe.

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
- **Dropped:** a push notification for the owner when someone asks to join (the owner decided it is not useful enough, October 18, 2026). After v1 (2026-11-10) the waiting screen tells the joiner so, and asks them to let the owner know. Also dropped after v1 (2026-11-13, review R12): Pause joining, since the owner approves every request and New code replaces a leaked code.
- **Later:** meal planner, home-screen widgets (Jetpack Glance), receipt scanning, recipe photo upload (needs file storage), custom storage locations, import from Paprika export files.
- **Released (October 31, 2026):** the web app for iPhone (section 18), milestones W0 to W7, at
  https://spoilnot-prod.web.app for testers after the owner's iPhone QA on https://spoilnot-dev.web.app.
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
| 2026-10-19 | Store order: each grocery list learns its store's aisle order from check-offs | 5.3, 8.2, 12.3, QA |
| 2026-10-19 | Mark opened: a shorter date for opened food, the opened part of a batch in pieces on its own row, and each product remembers how long it keeps once opened | 5.2, 8.2, 12.3, QA |
| 2026-10-19 | I cooked this: a recipe takes what it used off the pantry, soonest-expiring first, with Undo and a "cooked" activity event | 5.5, 5.6, 8.2, QA |
| 2026-10-19 | Use it up on Home: saved recipes that use the most food expiring soon | 5.6, 9.7, QA, RUNBOOK |
| 2026-10-19 | README rewritten from the owner's point of view: the app's purpose and features, and how it was designed and built with Claude Code; the developer commands moved out (they are in CLAUDE.md and the RUNBOOK) | README |
| 2026-10-19 | Quick wins: aisle guess for typed grocery items, rename grocery items, Throw away all expired on Home, compact empty Home cards, the notification prompt after the third dated item, amounts without "pcs", stacked choices at large text sizes, and faster Use it up matching | 5.3, 5.4, 5.6, 6.5, QA |
| 2026-10-19 | Add to pantry is one summary line per item with Add all; tapping a line edits it | 5.3, QA |
| 2026-10-19 | Insights: tap a month for its counts, share used and reasons; TalkBack reads each month on its own | 5.7, QA |
| 2026-10-19 | Recent activity reads only new events (changes-only), about 1,790 reads per household per day instead of 1,930 | 9.7, 9.8, RUNBOOK |
| 2026-10-19 | App tour: a short follow-along tour for new users, replayable from Settings | 5.6, 5.8, QA, PRIVACY |
| 2026-10-19 | Baseline profile regenerated after the quick wins, compact Add to pantry, Insights months and the app tour (16,568 rules). On the emulator: cold start median 435 → 426 ms; first Pantry scroll frame CPU time P90 8.2 → 6.2 ms, P99 20.4 → 9.9 ms | 13.1, RUNBOOK |
| 2026-10-19 | UX polish 1: compact tab title bars, simpler pantry rows (one status, "× 3" amounts), expired backlog folded into a header, a tidier Add item form (stepper beside unit, wrapping date chips, location tiles), rounded tour highlight, number-aware name sorting | 5.2, 5.8, 6.4, QA |
| 2026-10-19 | UX polish 2: recipe cards with a tinted initial and equal heights, empty Recipes with its three ways to add, the grocery list name as the switcher, badge icons and a one-line Back in stock, outlined cards and pill fields everywhere, aligned recipe-to-list rows, a visible swatch check | 5.3, 5.5, 5.6, 6.5, QA |
| 2026-10-19 | Baseline profile regenerated after UX polish 1 and 2 (16,700 rules). On a freshly booted emulator: cold start median 468 → 392 ms; first Pantry scroll frame CPU time P90 10.3 → 5.9 ms, P99 30.3 → 9.9 ms | 13.1, RUNBOOK |
| 2026-10-19 | Feedback after beta 7: no Throw away all button (Home and Pantry), a readable welcome title in Fredoka, four equal date chips that toggle (no No expiry chip), Pantry filters without All that toggle, Add buttons that never shrink, bolder preset color themes | 5.2, 5.6, 6.4, 6.5, QA |
| 2026-10-19 | Clear household data: the owner wipes everything but the household and its members, after a confirmation and their password | 5.1, 5.6, 8.2, QA, RUNBOOK, PRIVACY, SECURITY_REVIEW |
| 2026-10-20 | Ready for a public repository: encrypted nightly exports, logs without user IDs, household IDs or digest contents, restore by ID prefix, a monthly keep-alive for scheduled workflows | 10, 13.4, RUNBOOK, PRIVACY, SECURITY_REVIEW |
| 2026-10-28 | Meal planner, part 1: a Plan tab with the week, breakfast, lunch and dinner, recipes or typed meals, servings, several cooks, notes and Undo; changes-only listener for a window of dates | 5.1, 5.8, 5.9, 6.2, 8.2, 9.7, 9.8, 10.3, 12.3, 13.4, QA, RUNBOOK, PRIVACY, SECURITY_REVIEW |
| 2026-10-28 | Meal planner, part 2: the week's ingredients to a grocery list with the source Meal plan, Cooked (pantry, mark and activity in one Undo), Use it up in the recipe picker, and a Today card on Home | 5.6, 5.9, 8.2, 12.3, QA, SECURITY_REVIEW |
| 2026-10-28 | Meal planner, part 3: move a meal, copy last week, clear a week (with Undo), and the cook's meals in the daily reminder with a Meals I'm cooking setting; a digest with only meals opens the Plan tab; dry-run logs show counts only | 5.4, 5.9, 8.2, 9.7, 10.3, QA, RUNBOOK, PRIVACY, SECURITY_REVIEW |
| 2026-10-29 | Baseline profile regenerated after the meal planner, with the Plan tab and its add sheet in the journey (17,054 rules). On a freshly booted emulator, first Pantry scroll frame CPU time P50 5.3 → 4.9 ms, P90 8.0 → 7.1 ms, P99 19.0 → 28.3 ms (one slow frame; noisy on the emulator). The startup benchmark failed on this emulator ("must not be running prior to cold start"), so no startup numbers this time | 13.1, RUNBOOK |
| 2026-10-29 | Web app for iPhone planned (section 18), and W0: `web/` (Vite, React, TypeScript), colors, words and icons generated from the Android app, the Material 3 component kit, the app shell with five tabs and their back stacks, Firebase on the emulators, the manifest and service worker, unit and WebKit tests, a CI job | 16, 18, Appendix B, RUNBOOK, SECURITY_REVIEW |
| 2026-10-29 | Web app W1: welcome, create account, sign in, password reset, the household gate, Settings (account, household, sign out, delete account) and household settings, on the session state machine ported from the app with its tests; the writer and changes-only listeners move to W2 | 18.8, PRIVACY, SECURITY_REVIEW, QA, RUNBOOK |
| 2026-10-29 | Web app W2a: the pantry list (search, filters, sort and grouping, expired header) with swipe and menu actions, Mark opened and Undo, on the household writer and changes-only listeners ported from the app with their tests; W2 is split in two | 18.8, QA, RUNBOOK |
| 2026-10-29 | Web app W2b: Add item (quick dates, the date picker, shelf-life learning, known products, merging into the same purchase) and the Edit window with Undo; Home's Needs attention, Low stock, This month and Recent activity cards leading to the filtered Pantry and Insights; Insights with the ring, the six months, the streak and the reasons. Web text now scales non-linearly like Android 14. Home's grocery, Use it up and Today cards come with W3 to W5, the notification explainer with web push in W6 | 18.2, 18.8, QA, RUNBOOK |
| 2026-10-29 | Web app W3: the Groceries tab (lists, quick add, guessed aisles and the learned store order, check off, edit, swipe and Clear checked with Undo, Add to pantry) and the restock engine, ported from the app with their tests; Home's grocery card; the remote-change highlight on Pantry and Groceries. Web screens listen again 3 s after a failed load, and only recent additions by others are announced | 18.7, 18.8, QA, RUNBOOK |
| 2026-10-29 | Web app W4: recipes, view only (18.1): the box, detail and scaling, Add to grocery list, I cooked this, cook mode, Edit, Share, Delete with Undo and Home's Use it up card, ported from the app with their tests; no Add recipe on the web, and an empty box says recipes come from the Android app | 18.8, QA, PRIVACY, SECURITY_REVIEW |
| 2026-10-30 | Web app W5: the meal planner (5.9) with Home's Today card, ported from the app with its tests; weeks start on the phone region's first day; the recipe picker's empty message points to the Android app | 18.8, QA |
| 2026-10-30 | Web app W6a: Settings > Appearance (System, Light or Dark, the six themes, units), Export as a download, Clear household data for the owner, About with the open source licenses, and the app tour with coach marks, ported from the app with their tests; W6 is split in two (web push in W6b). The web session no longer flashes the gate right after creating a household | 18.1, 18.3, 18.8, QA, RUNBOOK, PRIVACY, SECURITY_REVIEW |
| 2026-10-30 | Web app W6b: Settings > Notifications, web push (the FCM web token as a `platform: web` device, the service worker showing digests and opening their route), the time zone written at start, and the notification explainer after the third dated item; the rules accept web devices and the reminder job adds a `webpush` part to every digest. The app tour is removed from the web app (owner's choice) | 8.2, 12.3, 18.1, 18.3, 18.4, 18.8, QA, RUNBOOK, PRIVACY, SECURITY_REVIEW |
| 2026-10-30 | Web app W7 (code): Firebase Hosting with a strict Content Security Policy and cache headers, `scripts/deploy-web.sh`, Add to Home Screen help on the welcome screen and Home, the Update snackbar, a test of the built app under the hosting headers in CI, and a Lighthouse check (performance 93, accessibility 100). Deploying is the owner's step | 18.5, 18.6, 18.7, QA, RUNBOOK, PRIVACY, SECURITY_REVIEW |
| 2026-10-31 | Web browser tests: the two-phone Pantry flow gets 90 s like the other long flows; under a full local run (the deploy script's) it timed out three times in a row while its first phone used most of 60 s. Hosting serves a copy of the build inside `firebase/`, which the Firebase CLI requires | 18.6, 18.7 |
| 2026-10-31 | Web app released: rules allowing web devices and the app deployed to spoilnot-dev, the owner's iPhone QA (flows 28–36) passed there, then both deployed to spoilnot-prod (build 3f09a54) | 16, 18.8, QA, RUNBOOK |
| 2026-10-31 | Dependencies: Firebase BoM 35.1.0, datastore 1.3.0, OkHttp 5.5.1, Roborazzi 1.77.0, Gradle 9.8.2, and the Firebase SDK and CLI for the rules tests; AGP stays at 9.4.1 because 9.5.0 cannot configure release variants | 12.2 |
| 2026-10-31 | The web app's PNG icons are lossless palette PNGs (33 KB to 12 KB) from ImgBot, which now leaves generated SVGs and test screenshots alone | 18.2, RUNBOOK |
| 2026-10-31 | The web deploy script retries a failed browser test once, as CI does | 18.6, RUNBOOK |
| 2026-10-31 | App tour: each step opens its tab at its root, so the tour taken from Settings on the Home tab closes Settings instead of highlighting nothing (an instrumented test covers it) | 5.8, QA |
| 2026-10-31 | Crash reports only from builds on real Firebase, and Firebase Sessions off in the emulator builds: their failed uploads to the demo project restarted the app after each benchmark kill, which made `StartupBenchmark` fail. The benchmark helpers retry a tap that a fling swallowed and press Back only when something opened. Measured on a headless software-rendered emulator: cold start median 691 ms without the profile, 623 ms with it | 7.5, 13.1, RUNBOOK |
| 2026-10-31 | Two-phone instrumented tests terminate each simulated phone's Firestore after the test, so the full `connectedDevDebugAndroidTest` run no longer stops after about 50 phones (Android allows about 100 network callbacks per app); `TestPhoneTest` guards it. The household-deletion test waits for the member's local reset before its last read, which could hang the run, the share test allows 60 s from the share to the preview on a busy emulator (the app's own 5 s is unchanged), and the tour test waits for Skip to be saved | 13.1, DECISIONS |
| 2026-11-01 | Reminder digests go only to the household's current members: the job checks each due user's member document (one read per due user) and clears a removed member's household pointer as their app would. The prune and the restore count failed writes instead of throwing them, and every job prints an error as one fixed line with its code | 9.7, 10.1, 10.3, 12.2, 13.4, RUNBOOK, QA, SECURITY_REVIEW |
| 2026-11-01 | Sign-out and account deletion finish even when Settings closes: they run in the session's application scope, their cleanup steps no longer treat cancellation as a failure, and the phone deletes its push token whenever nobody is signed in (after a sign-out or an account deletion, and at a start without a signed-in user). Instrumented tests sign out and delete an account through Settings and check the phone's cache and the server | 10.2, 12.2, 18.4, QA, PRIVACY, SECURITY_REVIEW |
| 2026-10-18 | Baseline profile, macrobenchmarks and `scripts/baseline-profile.sh`. On the emulator: cold start median 457 → 419 ms; first Pantry scroll frame CPU time P90 8.2 → 6.5 ms, P99 13.3 → 10.4 ms | 13.1, 13.2, Appendix B, RUNBOOK |
| 2026-11-08 | Reminder job: three schedule entries an hour (minutes 7, 27, 47), still one digest a day per user; the daily prune runs in the first run of each UTC day, keyed on `jobs/state.lastPruneDate`, which the summary prints; a failed household or user is counted and the run carries on, then exits non-zero. The job reads devices first (at most the 10 newest), caps the stock query at 500, sends in chunks of 500, marks a user done for the day after 3 runs without a delivery, deletes a token only when FCM says it is unregistered or invalid, cuts names to 80 characters and the body to 1,000 bytes, and decides the title by distinct names. quota-sim counts events, housekeeping reads and deletes and has `RUNS` and `WEB` knobs: about 17 households. Plan B (Cloud Functions) closed | 9.7, 9.8, 10.1, 10.2, 10.3, 10.4, 10.5, 10.6, 13.4, RUNBOOK, PRIVACY, SECURITY_REVIEW |
| 2026-11-08 | Instrumented tests cancel each Hilt test's application scope after it (`TestCoroutinesModule`, `ReleaseAppScopes` added by `HiltTestRunner`), so no earlier test's `SessionManager` clears local data and terminates the shared Firestore under a later one; this made `SettingsAccountUiTest.deleteAccountRemovesProfileDevicesMembershipAndSignIn` time out in the full run. `AppScopeReleaseTest` guards it | 13.1, DECISIONS |
| 2026-11-08 | What stays on a phone after sign-out: both apps clear the sync log with the local data, and Android also clears the built-in browser's cookies, site storage and cache and the cached recipe photos. The web app deletes its push token when a user leaves the phone, keeping a flag until FCM confirms so a phone signed out offline deletes it at its next start. Notification links stay on this site (`safeRoute` resolves the route on the app's origin). Both household gates offer Delete account, and a deletion that fails after the gate gave way to Loading shows its error when the gate comes back | 5.1, 6, 10.2, 12.2, 18.4, QA, PRIVACY, SECURITY_REVIEW |
| 2026-11-08 | Firestore rules: each collection accepts only the fields the apps write, with DESIGN 8.1's sizes (names 80, people 40, notes 500, recipe text 11.6's, links http or https up to 2,000, lists bounded), finite numbers, a known event type and a 1 to 40 character author name; `createdBy` is the writer on create and fixed after, `checkedBy` the writer or nobody; members delete only by tombstone (outright: the owner, or a list's items with their list); Insights counts move by one per write; an ownership transfer demotes the old owner in the same batch. Both apps: counts go one step per write, Delete list deletes the list before its items, names are cut to 8.1's lengths on read, Home names authors from the member list, and Android drops non-web recipe links and photos on read. 116 rules tests. The rules deploy after the Android and web releases (RUNBOOK) | 4, 8.1, 8.5, 12.3, 12.4, 18.5, RUNBOOK, SECURITY_REVIEW |
| 2026-11-08 | Deleted things disappear offline: a tombstone counts as deleted while its `deletedAt` is still the phone's estimate, so removed grocery items, Clear checked, Add to pantry, Used up, Throw away and deleted recipes and meals leave the screen at once without a signal, and Add to pantry can no longer be pressed twice. An instrumented test and a WebKit test (`offline.spec.ts`) delete and add to the pantry with the network off; QA flow 40 checks it on the phones | 8.1, 9.8, QA, DECISIONS |
| 2026-11-08 | Web updates: the web app asks for a new version whenever it becomes visible, at most once an hour, and reads `config/app.minWebBuild`; when its build number (`WEB_BUILD`, shown in About) is older, it looks at once and the Update snackbar stays until Update. The owner raises `minWebBuild` after a web deploy that older builds must not keep running. quota-sim counts the web's `config/app` listener | 8.2, 18.5, 18.7, RUNBOOK, QA |
| 2026-11-08 | The floating buttons have names for screen readers: Add item, Add N to pantry, Add recipe and Cook are `SpoilNotFab`, which TalkBack reads as "Add item, button" instead of "Button". A pantry row's spoken sentence now names the row TalkBack activates (Edit), as on Out rows. `AccessibilityChecksTest` covers the 13 main screens and their sheets, menus and dialogs, adds its own rule that every clickable or toggleable node has a name, and with its canary test is skipped below Android 14 | 6.8, QA, DECISIONS |
| 2026-11-09 | Sync cursor from what the server sent: a changes-only listener starts from the newest change its server listener received for that collection (or meal window), kept in the sync log next to the full-read record, less 5 minutes, instead of the newest change in the cache. A phone that wrote to the list, the week or Recent activity while not showing them (the restock engine from the Pantry tab, Plan this recipe) no longer skips the other members' earlier changes until the next daily full read. Each collection is read in full once after the update. An instrumented test (`SyncCursorFlowTest`), a WebKit test (`sync.spec.ts`) and unit tests use a 2-second margin; QA flow 41 is the mixed-household check. Web build 2 (`minWebBuild`) | 9.8, 18.3, 18.7, QA, PRIVACY, RUNBOOK, DECISIONS |
| 2026-11-09 | Restock engine, both apps: an item it adds, or brings back from a tombstone, starts unchecked (Add to pantry's tombstone kept `checked`, so the next dip put the staple in the cart) and is written without `createdBy`, so two phones writing it at once both pass the rules; it evaluates only after both products and stock have been read from the server (or in full before), so a first sync no longer adds every staple; the phone whose change caused a dip writes at once and other phones after 5 s if still needed; a failed listener retries with a backoff. Editing an older staple (Keep at least without automatic add) keeps its Keep at least, on the web too. Undo of Used up keeps the item with Back in stock (accepted) | 5.2, 5.3, 9.6, QA, DECISIONS |
| 2026-11-09 | One phone wins when two finish the same thing (both apps; review findings SYNC-02, WEB-01, SYNC-05, SYNC-07): the rules tombstone a grocery item once, mark a meal cooked once and create items only under a list that exists, so a second Add to pantry (at the same moment, or from a phone that was offline), a second Cooked and an add to a just-deleted list are refused whole instead of adding everything twice, taking the recipe twice or leaving an item no screen shows. The refused phone reads why from the server and says so with the winner's name ("Sam already took these off the list. Nothing was added to the pantry."). Not covered and accepted: Used up on two phones counts twice in Insights. 6 rules tests (122), `OnePhoneWinsFlowTest` (review 04's p02, p03, p07, p09), `race.spec.ts` in WebKit, unit tests for the messages; QA flow 43. Web build 3 (`minWebBuild`). The rules deploy together with 2026-11-08's, after the releases (RUNBOOK) | 5.3, 5.9, 8.5, 9.4, 9.7, 12.3, QA, RUNBOOK, SECURITY_REVIEW, DECISIONS |
| 2026-11-09 | `AccessibilityChecksTest` opens the Plan tab's Add meal sheet from today's button, scrolled into view, instead of the first day's: Plan opens scrolled to today, so on every day but the first day of the week the first day's card was laid out above the visible list and the tap missed (it timed out on Mondays to Saturdays with a Sunday-first locale). Test only; 6.8 unchanged | — |
| 2026-11-09 | Stay usable when the quota runs out, both apps: the session keeps the last household or gate on screen while it listens again after any error but a permission refusal, instead of the loading screen (which also hid the quota banner and lost half-filled forms); the quota banner moved above the root, so it shows on every top-level screen, takes the status bar and turns itself off at midnight Pacific; changes-only listeners keep the cached list, marked Offline, while their server listener is refused for the quota or offline, and retry with a backoff; Android's Groceries shows the Offline banner like the web's, and the web's Groceries keeps its lists when their listener fails instead of loading forever. The web gets `QuotaSignal` (`domain/quota.ts`) and a development-only hook that refuses listeners for the browser tests | 6.7, 7.5, 9.8, 18.1, QA, RUNBOOK |
| 2026-11-09 | Listeners that let go (Android; review findings EFF-01, PERF-02, EFF-03, EFF-05): one shared pantry flow per household (and one per grocery list's items) for every screen, which stops 5 s after the last screen leaves (`SharedByKey`); snapshots mapped and screen state built off the main thread; Pantry's empty-batch clean-up and Groceries' "Sam added Eggs" run only while their screen is collected; the restock engine reads the cache only and starts when an activity starts, so a process kept between opens, or started for a digest, holds no household listener. quota-sim (new `WARM` and `HELD` knobs): with 3 of 7 later opens finding the process alive a household cost about 3,720 reads a day before (9.4 households) and 2,020 now (17.3); with all 7, 6,340 (5.5) and 2,020. Pantry first scroll with 500 batches, Pixel_8 headless with `-gpu host`, median of 3 alternating runs against main 951f0af: quiet, frame CPU P50 / P90 / P99 3.9 / 5.5 / 18.8 ms (main 4.0 / 5.4 / 18.7), unchanged; while another member changes a batch every second, frames late by up to 4.4 ms at P99 (main 66.3 ms) and 3.4% janky (main 4.7%). Unit tests `SharedByKeyTest`, `RestockRunnerTest` (cache only), `SyncLogTest`; QA flow 45. The web port is the next change | 6.4, 8.5, 9.1, 9.6, 9.7, 9.8, QA, RUNBOOK, DECISIONS |
| 2026-11-10 | Listeners that let go on the iPhone web app (review findings EFF-01, EFF-03, EFF-05): `pantry.observe` and each list's items are shared per key (`SharedByKey` in `web/src/domain/shared.ts`), one pair of listeners per phone for every screen, let go 5 s after the last screen leaves; screens listen only while the page is visible (`useObservable` with `pageVisible$`), and while it is hidden the shared listeners and the recipe box stop at once, since iOS may suspend the app before a timer fires; the restock engine reads the cache only (`observeCached`, `observeCachedLists`, `SyncLog.fullSyncs$`) and is started by the signed-in shell instead of at page load. quota-sim's `HELD=1` now covers web phones: four web phones with 3 of 7 later opens resumed cost 35,224 reads a day for 10 households before (9.9 households) and 20,224 now (17.3); with all 7, 55,224 (6.3) and 20,224. Unit tests `shared.test.ts`, the cache-only runner, `SyncLog.fullSyncs$`, `useObservable` while hidden; `listeners.spec.ts` in WebKit; `sync.spec.ts` waits for the list's listeners to stop; QA flow 46. No Android change | 9.1, 9.6, 9.7, 9.8, 18.3, QA, RUNBOOK, DECISIONS |
| 2026-11-10 | CI and the scheduled jobs: npm installs run without lifecycle scripts (`--ignore-scripts`), and in the jobs in a step that has no secrets; the reminder, export and restore jobs use a `production` environment limited to `main` that holds their keys; every action is pinned to a commit SHA with its version in a comment; Dependabot covers `tools/themes`; a Gradle constraint raises Guava (through Firestore) to 33.8.0 | 10.4, 12.2, RUNBOOK, SECURITY_REVIEW |
| 2026-11-10 | Joining and sign-out edges, both apps and the job (review findings SYNC-06, WEB-05, SYNC-09, SYNC-08, UX-13): a missing join request is Expired only when no membership exists (an approved request removed before the joiner opened the app finishes the join); the job prunes pending and declined requests, and approved ones only once the join is finished; the waiting screen says the owner gets no notification, and the gate, confirm and expired texts are clearer; Sign out asks first when changes haven't synced; Clear household data queues the rest after a timed-out chunk and says it finishes online. Also: a deleted account's profile isn't recreated by another phone still signed in to it (that phone signs out), and join requests show a masked email and the time sent (new optional `emailMasked` field in the rules, deployed with the others in the RUNBOOK's order) | 4, 5.1, 8.2, 10.1, 12.3, 12.4, 16, QA, RUNBOOK, PRIVACY, DECISIONS |
| 2026-11-10 | Edits don't undo someone else's change (both apps; review findings SYNC-03, SYNC-04, WEB-03, WEB-07): the pantry Edit window writes only what the person changed, a changed quantity as an increment, and watches its item ("Sam changed this item." with Review, which keeps the draft; it closes with "Sam removed this item"); Move, product edits and the grocery Edit sheet write only changed fields; −1 is always an increment, so an offline phone's last −1 no longer deletes what another member added; Undo of a deletion removes only the tombstone, keeping fields a newer app added, the creator, a check-off and another member's change. Rules: a batch's quantity may go below 0 (two phones taking the last of it). Web: a cache listener sharing its query with a refused server listener listens again (`retryCacheRefusals`), so a new household's pantry no longer stays offline on Groceries when the first listen is refused | 8.1, 8.5, 8.6, 9.2, 9.3, 9.4, DECISIONS, QA, RUNBOOK, SECURITY_REVIEW |
| 2026-11-11 | Restore and Export tell the truth (both apps, the restore and the export workflows; review findings REL-07, REL-12, REL-14, WEB-12): the restore stamps every document with the server's time and `updatedBy: 'restore'`, so phones show it within a minute; it turns only the known timestamp fields back into timestamps, so text that looks like a date-time stays text; it refuses a target household with a different owner unless the new **different owner** input is ticked, and checks that in the dry run; the export and restore steps fail on any pipe error. Settings > Export reads from the server and says "You're offline" instead of saving the phone's copy, and also holds the person's own profile and the activity log | 13.4, DECISIONS, QA, RUNBOOK, PRIVACY, SECURITY_REVIEW |
| 2026-11-11 | Forms keep what you typed, and Add to pantry gets Undo (review findings REL-08, REL-09, LOGIC-03, LOGIC-04, LOGIC-08, LOGIC-10). Android: Add to pantry keeps its rows' edits by item id in the screen's saved state (`AddToPantryDrafts` over `SavedStateHandle`), so rotation, process death and others' check-offs no longer reset them; Plan's Add meal sheet, a meal's cooks, cook mode's ticks, Add to grocery list and I cooked this use saved state (`rememberSaveableList`, `rememberSaveableMap`). Both apps: Add to pantry adds to the item's linked product (`planAdd(…, productId)`), locks the unit to a product with stock and uses the item's amount only in that unit (`AddToPantryRules`); quick add reads the recipe parser's unit words and glued units and ignores a zero amount, and Increase quantity only adds in the same unit; Add to pantry returns an Undo built by `PantryService.undoAdditions` (new `PantryChange.DeleteProduct`, a product tombstone) plus the items' restores, all merges and increments the rules on main accept. Delete list keeps its confirmation. The web's own form-state fixes (its sheet still takes its rows when it opens) are the next change | 5.2, 5.3, 6.1, QA, DECISIONS |
| 2026-11-11 | Bad data skips one document, not the screen (both apps; review findings REL-04, WEB-10, REL-13, REL-11): every field is read on its own with a safe cast, a value of another type reading as missing, and each document maps on its own, so a document with a wrong-typed field is skipped or shown with that field missing instead of blanking the list on every phone; a failed listener listens again after 1, 2, 4 … 32 s instead of ending, and Home, Pantry, Plan, Recipes and Groceries say "Couldn't load." with Retry meanwhile; a change the server refuses later is reported once, by the shell, whichever screen is open, including queued household and notification-setting writes; the pantry's clean-up of empty batches is quiet and isn't retried once refused; Android's cache listeners listen again after a server refusal of the same query, as the web's do since 2026-11-10. Unit tests with wrong types on both apps (`WrongTypeReadTest`, `wrong-types.test.ts`), `LoadRetryTest` and `load.test.ts`, `EmptyBatchCleanupsTest`; instrumented `BadDataFlowTest` (review 05's bad-data documents) and `WriteRefusalUiTest`; `bad-data.spec.ts` in WebKit; QA flow 51. quota-sim unchanged | 6.7, 7.5, 8.5, 8.6, 9.8, 13.4, QA, RUNBOOK, DECISIONS |
| 2026-11-12 | Contrast and time to act (Android, colors on the web; review findings A11Y-02, A11Y-03, UX-17, CODE-11): Android's Undo snackbar stays for the accessibility manager's recommended timeout (`undoWindowMillis`, never under 8 s), which follows "Time to take action" and screen readers, wherever the app offers Undo; the light Fresh, Soon and Today/Tomorrow colors are darker (#28702C, #8F5500, #B03A0A) and the chip and swipe tints are both 12%, so status text is 4.5:1 on its own tint in every theme; Fiery Palette's buttons are teal and Watermelon Sorbet's mint, away from the status hues; `StatusColorsContrastTest` folded into `ColorThemesContrastTest`, which checks every fixed theme light and dark on the blended chip background and the buttons' hue distance; unit test `UndoWindowTest`; QA flow 52. The web takes the new colors and the 12% swipe tint (`npm run gen`); its snackbar timer comes later | 5.2, 6.1, 6.4, QA, DECISIONS |
| 2026-11-12 | Privacy details (Android, docs and npm packages): the invite code is copied marked sensitive, labeled "Invite code"; release builds drop verbose, debug and info calls to the system log, the libraries' included; Firebase Sessions is off in prod, while crash reports stay on by default with the opt-out in Settings; PRIVACY.md rewritten from an inventory of every kind of data both apps, the reminder job and the backups keep, with the installation ID and what is left after Delete account, matching what Export saves; recipe import reads a page's JSON-LD only up to 32 blocks, 1 Mi characters each and 3 Mi in all, in the browser's script and again in the app, and parses the browser's result off the main thread; `npm audit fix` (no `--force`) in `firebase/` and the reminder job | 7.5, 11.2, 11.6, 12.2, QA, RUNBOOK, PRIVACY, SECURITY_REVIEW, import-test-sites |
| 2026-11-12 | iPhone polish and the web's own form state (web only; review findings REL-08 and REL-09 on the web, WEB-14, WEB-15, WEB-08): Add to pantry keeps its rows' edits by item id, so another phone's check-off adds a row and keeps the edited ones, and the open sheet with its edits, like Plan's Add meal sheet (cooks, typed meal) and meal sheet (servings, cooks, note), comes back after iOS reloads a page it discarded (`sessionStorage`); sheets stay above the on-screen keyboard (`visualViewport`) and scroll the focused field into view, and a form sheet taller than the screen keeps its button pinned instead of scrolling it away; a row swipe starting at Safari's left edge is left to Safari's Back; Settings > Appearance has *Week starts on* (Automatic, Sunday, Monday, Saturday) for Plan's week, and browsers without week information know the Saturday regions | 5.9, 6.1, 18.1, 18.2, 18.3, DECISIONS, QA |
| 2026-11-12 | Home tells the truth (Android; the shared data on the web too, the web's Home and Plan screens next): one Needs attention set for Home, Pantry's filter (renamed from Expiring soon) and the digest, usable food first with the expired folded; Low stock merged into the Grocery list card; Use it up leaves out planned recipes; Keep some in stock and how to start; Plan's empty week; check-offs no longer logged | 5.2, 5.4, 5.6, 5.9, 6.7, 8.2, 10.3, 18, QA, DECISIONS |
| 2026-11-12 | Home tells the truth on the iPhone web app (B15's web port): Home lists Needs attention's usable purchases first with the expired folded into one line, and *See all N* counts the whole set and opens Pantry's Needs attention filter; Low stock is part of the Grocery list card (Running low, "and N more"); a new household gets the two "how to start" texts with a kitchen icon instead of checks; Use it up leaves out recipes planned this week, and Home listens to Plan's week with *Week starts on* instead of the region's; Plan's empty week has its sentence and *Copy last week*, and opens there; the picker's Use it up leaves out the recipes the shown week plans; Add item's Keep some in stock says what it does while off. `home_low_stock` and `home_low_stock_none` leave `strings.xml`. Unit tests in `home/models.test.ts`; `home.spec.ts` and `plan.spec.ts` in WebKit; new screenshots `plan_empty_week_*`; QA flows 30 and 56 | 5.6, 5.9, 18.1, QA, DECISIONS |
| 2026-11-13 | Reminders people can count on (Android; review findings UX-02, UX-03, CODE-10; the web port is next): the notification explanation moves from Pantry's Add item to the app shell, under one rule in `feature/notifications/domain` (`NotificationPrompt`): a household that opens with dated food asks at once, so a member who joins or a new phone is asked without adding anything, and a new household asks at its third dated item, whoever adds it; it names the reminder time. While the phone blocks reminders the member wants, Home's Needs attention says "Reminders are off on this phone · Turn on" and Settings says "Off on this phone"; Turn on asks, or opens the system settings once Android no longer asks. Right after a Turn on that works, a local "You're set" notification gives the time and tomorrow's items (`YoureSet`); DESIGN 1's criterion stays. Unit tests `NotificationPromptTest`, `YoureSetTest`, `YoureSetNotificationTest`; Home and Settings screenshots for the blocked state; instrumented `RemindersUiTest` (a fake `NotificationAccess` in every instrumented test, allowed by default); `JoinAndSignOutUiTest` waits up to 10 s for the join dialog; QA flow 57. No new reads: the shell listens to the pantry only until the explanation is answered (Home's listener is the same shared one) and to the member's own profile, which the session already listens to; quota-sim unchanged (20,224) | 1, 5.4, 5.6, 10.2, 10.3, 18.4, QA, DECISIONS |
| 2026-11-13 | Clear words and one rule for confirmations (Android; the copy and Pause joining's removal on the web too, the web's screens next; review findings UX-18, UX-08, UX-09, UX-10, UX-11, UX-12, UX-04, LOGIC-07, R9, R10, R12, R13, R16): review 06's flagged strings (one name for Your kitchen and the cart, digits in Use 1 and Add 1, how much in Already on the list, "Select items to add", "Copied 5 meals from last week", sentence-case aisle headers, and more); the app's own grocery additions say Added automatically, with the reason in Edit, and one Back in stock · Remove line; a one-time hint that aisles follow the store; Add item focuses Name and takes the aisle guess as its category; on Groceries a swipe right checks off and a swipe left deletes, both shown under the row and in the tour; Delete recipe and Clear this week have Undo and no dialog; Start cooking in a bar that covers nothing, one servings control and one source line on a recipe; no All chip in Recipes; "Every day for two weeks" for the expired reminder; Pause joining removed on both apps, with the rules no longer checking a code's `active` (a code an older build paused takes requests again; deploy in the RUNBOOK's order); Best before left as it is (UX-04, accepted). New unit tests for the restock reason, the category guess, the source line and the swipes; instrumented tests for Undo without a dialog and an older build's paused code; rules tests; QA flow 58 | 2, 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 5.7, 5.8, 5.9, 6.1, 6.6, 6.8, 6.9, 10.1, 16, QA, RUNBOOK, SECURITY_REVIEW, DECISIONS |
| 2026-11-13 | Reminders, clear words and one rule for confirmations on the iPhone web app (B16 and B17's web port, and B18's web Undo timer; review findings UX-02, UX-03, CODE-10, UX-18, UX-08, UX-09, UX-10, UX-11, UX-12, A11Y-02, R9, R10, R13, R16): the notification explanation asks from the signed-in shell under the app's rule (`NotificationPrompt`, ported to `web/src/domain/notifications.ts`): a household that opens with dated food asks at once, a new one at its third dated item, whoever adds it, only while iOS can still ask (the installed app, not answered yet), naming the time; Home's Needs attention ends with "Reminders are off on this phone · Turn on" and Settings says "Off on this phone"; a Turn on that works shows "You're set" through the service worker (`YoureSet`). Groceries: a swipe right checks off (or puts back), left deletes, each labelled under the row; Added automatically with the reason in Edit (`RestockEngine.reason`); one "Back in stock · Remove" line; the one-time aisle hint (`aisleHintDone` on the phone); sentence-case aisle and cart headers; "Add 1 more to the 2 on the list…". Add item focuses Name and takes the aisle guess as its category (`guessCategoryForNewProduct`). Recipes: Delete without a dialog, with Undo; Start cooking in a bar under the content (`DetailScaffold`'s `bottomBar`); one servings control and one source line (`SourceUrl.namesSite`); no All chip; "Select items to add" in Recipe's and Plan's add sheets. Plan: Clear this week without a dialog, with Undo, and "Copied 5 meals from last week". Home: Open in Pantry when everything is on the card. The Undo snackbar's timer waits while it has focus or the pointer. Six keys kept for the web leave `strings.xml` (`recipe_delete_title` and `plan_clear_title` stay for `RecipeUiTest`). Unit tests for the prompt rule, You're set, the restock reason, the category guess, the source line and the snackbar timer; WebKit tests for the swipes, Undo without a dialog, Home's line, the Settings row, the aisle hint and the timer; screenshots for Groceries, Settings, Recipes, Recipe detail and Add item; QA flow 59 | 5.2, 5.4, 18.4, QA, DECISIONS |
| 2026-11-13 | Large text, screen readers and keyboard (Android; the web's A11Y-12 and parity next; review findings A11Y-04, UX-14, A11Y-06 to A11Y-11, CODE-07): from 1.5× text chips, badges, steppers and buttons go under names; tab labels stay on one line; lists leave room for the floating button; 48 dp drag handles on every sheet; rows and cards read as one sentence with fixed roles and states; list and settings headers are headings; the tour hides the screen from TalkBack; content before the tab bar for the keyboard, with a focus ring; screenshots for every screen at 2×, with committed references verified on the Mac | 6.2, 6.4, 6.5, 6.8, 13.1, QA, CLAUDE.md, DECISIONS |
| 2026-11-13 | Large text, VoiceOver and the keyboard on the iPhone (B19's web port; review findings A11Y-12, A11Y-04, UX-14, A11Y-06 to A11Y-11): no empty heading on recipe detail and a named cook-mode progress bar; from 1.5× text chips, badges, the stepper, amounts and buttons go under names; tab labels whole on one line; lists leave room for the measured floating button; rows read as one sentence for VoiceOver, single choices are radio buttons, one Favorite toggle, Approve and Decline name the person, the cart header is a heading; one 2 px primary focus ring; `recipe_favorite` and `recipe_unfavorite` leave `strings.xml`; browser tests at 312%, for names and roles, sheets keeping focus and Tab order | 6.2, 6.8, 18.2, 18.7, QA, DECISIONS |
| 2026-11-13 | Recipes, plan and names (both apps; review findings LOGIC-11 to LOGIC-17, WEB-06): servings steppers reach the recipe's own servings (meals keep 1 to 1,000, also in the rules), scaled pieces in I cooked this round up, and a scaled amount under 1/16 reads "a pinch of salt" (`recipe_scaled_pinch`, passed in as `RecipeWords`); Copy last week counts meals of the same name; ingredient lines read comma decimals and thousands, and import reads "1h20m", "1 1/2 hours" and one-string ingredient lists (Android, which imports); ingredient linking compares both singulars, "-ves" with "-f" and drops "extra-virgin"; aisle guesses let "frozen", "canned" and "broth", "sauce", "paste", "dressing", "juice" decide; one written-out whitespace class for name keys, the quick-add field and the ingredient parser on both apps, with stored keys normalised when read; Android's clock reads the phone's current time zone (`CurrentZoneClock`); amounts are written in ASCII digits and typed ones read in the phone's own digits (`Amounts`). Neither the rules nor the reminder job compare name keys. One shared case table, `web/src/domain/parity-cases.json`, read by `ParityCasesTest` and `parity.test.ts`; unit tests for each rule on both apps; rules tests; `LiveImportTest` 10 of 10; QA flow 62 | 5.3, 5.5, 5.9, 8.1, 11.3, 11.5, QA, DECISIONS, RUNBOOK, import-test-sites |
| 2026-11-13 | Benchmark setup and a current profile (Android; review findings PERF-01, BASE-08, PERF-05, PERF-06, BASE-02): benchmarks and the profile run on one setup, Pixel_8 headless on the Mac's GPU (RUNBOOK "Benchmark setup"), and `baseline-profile.sh` refuses SwiftShader and warns about a throttled emulator, a busy guest, low memory or Low Power Mode (`--check` runs only the checks). Home reports itself drawn when its cards show (`ReportDrawnWhen`), and `StartupBenchmark` measures that (`timeToFullDisplayMs`) with every iteration signed in; the first-sync budget is unchanged until the owner measures it on 4G. Every profile journey step checks it opened what it should (the benchmark build exposes test tags as resource ids); this found that Insights had dropped out of the profile when "See insights" became "See your kitchen" (InsightsScreen 2 → 7 rules). A hand-kept `crash-reports-prof.txt` keeps the crash-report and Sessions code that emulator builds don't run. `LiveImportTest` and `PantryScrollPerfTest` leave the default instrumented run (`@LiveTest`, `@PerfTest`). Profile regenerated (17,346 rules). Measured headless on the Mac's GPU, dev benchmark build, medians of 10 cold starts and 5 scroll iterations, committed profile → regenerated: cold start to Home's cards 718 → 710 ms (first frame 350 → 358 ms; without a profile 820–899 ms and 436–444 ms); first Pantry scroll frame CPU time P50 4.9 → 4.7, P90 7.1 → 6.8, P99 20.7 → 20.8 ms (without a profile P50 4.9–5.2, P99 20–45 ms). An alternating A/B against main (`4d182a1`, two runs each, with the profile) gives the same first scroll, P50 4.8 against 4.7–4.8 ms and P99 20.8–21.5 against 21.6–22.1 ms, so exposing test tags costs nothing. Both draw 62–63 frames in most iterations where review 08 had about 95, and P99 is about 21 ms where it had 7.3: something since review 08 makes the benchmark's flings shorter and its slowest frames slower, left for B23 (lists). | 13.1, RUNBOOK, QA, DECISIONS, CLAUDE.md |
| 2026-11-14 | The web's Insights months screenshot no longer fails outside October (review finding BASE-01): `screens.spec.ts` gives the month labels fixed names (May to October, as recorded) before the 200% shots, as it already fixes the invite code, so the label widths stop moving the columns from month to month; the references are unchanged. The deploy script's browser tests pass again without `--skip-tests` | 18.2, DECISIONS |
| 2026-11-14 | Android's About screenshot no longer changes with each release: `AboutHeader` takes the version as parameters (the build's by default) and `NotificationSettingsScreenshotTest` passes the recorded one, 1.0.0-beta.9-dev (build 10), so a release's version bump no longer fails `verifyRoborazziDevDebug` on the four `settings_about` images, as beta.10's did; the references are unchanged | 13.1, DECISIONS |
| 2026-11-14 | Dependencies (replacing Dependabot's open updates): Firebase BoM 35.2.0 (Firestore 27.2.0, Crashlytics 21.1.0, Messaging 26.0.2), Navigation 3 1.2.1, kotlinx-serialization 1.13.0, jsoup 1.24.1, concurrent-futures 1.3.0, KSP 2.3.14, Spotless 8.11.0, Google Services plugin 4.6.0, Gradle 9.9.1, MockK 1.14.12, Robolectric 4.18, Roborazzi 1.79.0, UI Automator 2.4.1, `actions/setup-java` 6.3.0; the Firebase web SDK 13.3.0 and the web tools (Vite 8.3.8, Vitest 5.1.0, Playwright 1.65.1, jsdom 31, ESLint 10.15, Prettier 3.10), firebase-tools 15.36.0 and firebase-admin 14.5.1. npm `overrides` clear the advisories no parent release fixes (`@grpc/grpc-js`, glob, minimatch, uuid, OpenTelemetry core), so every npm project passes `npm audit` but the web build's deepmerge, which has no fix; a Gradle constraint keeps `error_prone_annotations` at 2.52.0 for the instrumented tests. AGP stays at 9.4.1: 9.5.1 still cannot configure release variants | 12.2, RUNBOOK |
| 2026-11-15 | A new look for the whole app, step 1 (Android; the owner's choice of 2026-11-14 from mockups, "Look A"): section 19 specifies it, with its surfaces, shapes, text styles, list groups, status tile, header, cards, buttons, chips, the two additions (Pantry's *Use first* and *Everything else* groups, Plan's row of days) and the order of work; the shared pieces are in `core/designsystem` (`AppSurfaces`, `SpoilNotCorners`, `LeafShape`, `SpoilNotText`, `ListSegment`, `GroupHeading`, `StatusTile`, `ScreenHeader`, `HighlightCard`, `AccentCard`, `LeafBadge`, `ConnectedButtonGroup`, `LabelChip`, `TonalButton`) with their tests. No screen uses them yet, so nothing changes on a phone and the existing screenshots are unchanged | 6, 19, DECISIONS |
| 2026-11-15 | A new look, step 2 (Android; the web's port is next): the shell and Home. Every tab sits on the tinted page under `ScreenHeader` (64 dp, pinned, a round tonal Settings button whose badge number stops at 1.2× text), and pushed screens' bars take the page color; the tab bar's indicator is `primaryContainer`; the floating button is `primary`, 64 dp with 22 dp corners; the main button (`PrimaryButton`) is 56 dp in `ButtonLarge`; "Couldn't load." and Offline are rounded banners first in the content, and the daily-limit banner has rounded bottom corners; every snackbar is `SpoilNotSnackbarHost` with 18 dp corners. Home: today's date above the title; Needs attention as the highlight card (its count badge isn't read, rows have status tiles and the full status for TalkBack, a compact card when empty); Today as tiles; Your kitchen as the `primary` card that opens with a tap anywhere and is read once (no *See your kitchen* button, the friendly sentence only while the month is empty); Use it up as an accent card; the grocery list and Recent activity as plain cards. `UsageRing` takes its colors; `SpoilNotText.SectionTitle` is new. Tests: `ShellScreenshotTest` and `HomeDateTest` (new); Home's screenshot test on the page color, with checks for the badge, the rows' sentences and the card's single reading; the theme gallery on the page color; `ColorThemesContrastTest` for the navigation bar, the Offline banner, the count badge and the ring on its card; `AccessibilityChecksTest` opens Your kitchen from the card. 108 references re-recorded, 4 added. QA flow 63 | 5.6, 19, DECISIONS, QA |
| 2026-11-16 | A new look, steps 1 and 2 on the iPhone web app (the Android steps' port): the surfaces of 19.2 as `--sn-surface-*` from `theme.ts`, 19.4's text styles in `base.css` (sizes 13, 18, 30, 48 and 54 added to the scaled `--sp-*`), and `kit/Look.tsx` with the pieces the shell and Home use; every tab on the tinted page under the new header (the line above the title, the tonal Settings button, the badge number stopping at 1.2× text) and pushed screens' bars on the page color, the tab bar's `primaryContainer` indicator, the `primary` 64 px floating button, `Button`'s `primary` (56 px) where the app has `PrimaryButton`, rounded banners and snackbars; Home as in the app, with the install card as a plain card. The header no longer tints on scroll, so `useOnInnerScroll` is gone. Tests: `theme.test.ts` (the surfaces, rows lighter than the page in every theme), `labels.test.ts` (`homeDate`), `home.spec.ts` and `screens.spec.ts` open Your kitchen from the card, `a11y.spec.ts` checks the status words go under the name at the largest text, and Home's screenshots mask the date line; all 93 references of `screens.spec.ts`, `a11y.spec.ts` and `quota.spec.ts` are re-recorded, since the page color and header change every one (a run that updates only failing ones left some under the 1% threshold showing the old look). `edit-conflict.spec.ts` picks a date other than the item's expiry; its fixed 21st was the expiry itself on the 16th of every month. QA flow 64 | 18.2, 19.13, DECISIONS, QA |
| 2026-11-16 | A new look, step 3 (Android; the web's port is next): Pantry. The header says how many rows the list has ("8 items"); the search field is round on the row color and the filter chips are 44 dp, the location ones with their icon; rows are grouped, each with its status tile and the status in words, then the location and Opened, in its second line ("Tomorrow · Fridge"), an Out staple's "Out" in the Expired color; in the default view the list splits into *Use first* and *Everything else* (`PantryBlocks`), the folded expired line being Use first's first row; headings stay pinned; swipes fill the row's shape; the empty pantry shows a kitchen in a leaf. Add item, Edit and Mark opened: labels above the fields (`SpoilNotTextField`, which `FormTextField` and `EnumDropdown` now use, so every form's one-line fields change), a stepper joining − and + to the amount, the quick dates as a connected group, filled location tiles, `SheetTitle`; Throw away and Move: `DialogTitle` and their choices as rows on the new `dialogRow`. New: `SearchField`, `SpoilNotFilterChip`, `FieldBox`, `FieldMessage`, `SpoilNotText.SheetTitle`, `DialogTitle`, `FieldLabel` and `FieldText`; `ListSegment` takes `onClickLabel`; `remoteHighlight` follows a row's shape; `StatusChip` is removed. The Pantry half of review batch B23 (PERF-07, PERF-08): `java.time` is stable for the Compose compiler, the list has content types, and the remote-change hold reads the scroll state outside composition (`rememberHeldWhileScrolling`). Tests: `PantryBlocksTest`, `FieldsAndFiltersTest`, `HoldWhileScrollingTest` and `PantryTabScreenshotTest` (new); the row tests check the status words and the full wording; `ColorThemesContrastTest` for fields, the stepper, the chosen tile and chip, and a dialog's choices. 69 references re-recorded (only those whose pixels changed: Pantry's, and the forms of Sign in, Sign up, the gate, Groceries' Edit and Add to pantry, Plan's sheets, the recipe editor and the notification settings; the components sheet's rows by their corners' antialiasing), 8 added, 4 removed. QA flow 65 | 5.2, 6.4, 19, DECISIONS, QA |
| 2026-11-16 | A new look, step 3 on the iPhone web app (the Android step's port): Pantry as in the app, with the header's count, the round search field, the filter chips, grouped rows with status tiles and the status in words, *Use first* and *Everything else* (`domain/pantry/blocks.ts`, ported from `PantryBlocks` with its tests), the folded expired line as a row, pinned headings and the empty pantry's leaf; Add item, Edit and Mark opened with labels above the fields, the joined stepper, the quick dates as a connected group and the filled location tiles; Throw away and Move with their choices as rows. `TextField` and `SelectField` take the label-above look everywhere the app's `FormTextField` and `EnumDropdown` have it (`outlined` keeps the rest); new `SearchField`, `SpoilNotFilterChip`, `ConnectedButtonGroup`, the `--sn-surface-dialog-row` surface, four text styles and the `remove_circle_outline` icon; `StatusChip` and the old date tiles are removed. The web half of review batch B23 for Pantry (WEB-11, WEB-13): the list holds another phone's change while it scrolls (`useHeldWhileScrolling`), and rows are memoized with handlers and item objects that keep their identity across snapshots (`keepUnchanged`, `PantryItems.group`). Tests: `blocks.test.ts`, `identity.test.ts` and `holdWhileScrolling.test.tsx` (new); `theme.test.ts` for the dialog rows and fields; `pantry.spec.ts` for the status words, the header's count and the two groups; `a11y.spec.ts` for the amount joining the second line at the largest text; `race.spec.ts` moves the pointer off Cooked's Undo snackbar after the tap (the taller meal sheet put the button under it, and that snackbar waits while the pointer is over it). Screenshot references re-recorded where their pixels changed. QA flow 66 | 18.2, 19.13, DECISIONS, QA |
| 2026-11-16 | A new look, step 4 (Android; the web's port is next): Groceries. The line above the list's name says what is left to buy ("4 to buy") and the name is the screen's title; the quick-add field is the page's round field (`PillField`, which `SearchField` now uses too) with a filled + button; aisles are groups of 64 dp checkbox rows under pinned headings, the amount beside the name, the source a `LabelChip` (Back in stock in the person chip's colors); In cart has its heading in `onSurfaceVariant` with Clear cart beside it and rows on `surfaceContainerHigh`, struck through; swipes fill the row's shape; the aisle hint is a rounded note; the dialogs' titles are `DialogTitle`. Add to pantry: `SheetTitle`, its lines a group of rows on the field color that open in place with Add item's stepper (`QuantityStepper`, now shared, with each line's tag), unit, the field label over the date and the quick dates; Edit: `SheetTitle` and the sheet spacing of 19.11. The Groceries half of review batch B23 (PERF-04, CODE-09): another phone's change waits while the list scrolls (`rememberHeldWhileScrolling`), the list has content types, each row gets only its own facts (`backInStock`, `addedBy`, worked out in the view model) instead of the whole screen state, and the quick-add text and suggestions live in the view model (`quickAddText`, `suggestions`). Tests: `GroceriesTabScreenshotTest` and `GroceryRowFactsTest` (new); `GroceryRowTest` draws its rows as a group with the facts the view model gives them. Screenshot references re-recorded where their pixels changed. QA flow 67 | 5.3, 6.4, 19, DECISIONS, QA |
| 2026-11-16 | A new look, step 4 on the iPhone web app (the Android step's port): Groceries as in the app: what is left to buy above the list's name, the round quick-add field with a filled + (`PillField`, which `SearchField` now uses), aisles as groups of 64 px checkbox rows under pinned headings with the amount beside the name and the source as a `LabelChip` (Back in stock in the person chip's colors), In cart in `onSurfaceVariant` with Clear cart, its rows a step darker and struck through, swipes in the row's shape, the rounded aisle hint and the dialog titles; Add to pantry's lines as a group of rows on the field color with Add item's stepper (`QuantityStepper`, now exported with a test id), the unit, the date's label and the quick dates; Edit with the sheet title. The web half of review batch B23 for Groceries (WEB-11, WEB-13): the list holds another phone's change while it scrolls, rows are memoized with handlers that keep their identity and items kept across snapshots (`useKeepUnchanged`, now shared and keyed), and the quick-add suggestions are memoized. Pinned headings move below the screen header: the list switcher's menu opened under Dairy and eggs' heading, which took its taps. Pantry's old `.sn-group-header` is removed. Tests: `identity.test.ts` (moved, and by any key); the Groceries browser tests that switch lists now pick from the menu over the pinned heading (they caught it). Screenshot references re-recorded where their pixels changed. QA flow 68 | 18.2, 19.13, DECISIONS, QA |
| 2026-11-16 | A new look, step 5 (Android; the web's port is next): Plan. "This week" above the title while this week shows; the week on a round bar of the row color and under it the row of days (19.12): seven buttons, today's filled, each named in full with "Show this day", a tap scrolling to that day (a jump with Remove animations on), sideways scrolling from 1.5× text; the bar and the row stay above the days. Today is the highlight card with a Today badge (under the title from 1.5× text); other days a `SectionTitle` heading over a group of rows, an empty slot a one-line row with its + and a slot with meals a row per meal, the slot's name over the meal's, the first carrying the slot's +. A meal row is one control with its sentence; cooks are person `LabelChip`s. *Go to this week* is a text button between the bar and the row; the empty week's sentence and Copy last week are a plain card with a tonal button. Add meal: `SheetTitle`, chips, *Something else* with its label above, a placeholder and a filled + at its end, Use it up and the recipes as groups on the `field` color, the round search field. The meal's sheet: `SheetTitle`, the servings as `CountStepper`, chips, Note, Save, and Cooked, Move and Open recipe as tonal buttons. Move: `DialogTitle`, `FieldLabel`s and filter chips on `dialogRow` (`SpoilNotFilterChip` takes a `color`). `StepButton` and the new `CountStepper` move to the design system; Mark opened uses `CountStepper` (its screenshots unchanged). *This week's ingredients* and *What did you use?* change with Recipes (step 6). Tests: `PlanWeekTest` (new); `PlanScreenshotTest` draws the whole tab with its header and bar; `ColorThemesContrastTest` for Cooked on nested rows and Move's chips. Screenshot references re-recorded where their pixels changed. QA flow 69 | 5.9, 19, DECISIONS, QA |
| 2026-11-16 | A new look, step 5 on the iPhone web app (the Android step's port): Plan as in the app: "This week" above the title, the week on a round bar and the row of days under it (today's filled, each named in full with "Show this day" as its description, a tap scrolling to that day, sideways from 1.5× text), both above the scrolling days; today as the highlight card with its badge (under the title from 1.5× text), other days a heading over a group of rows, one + per slot on its first meal, each meal one button read as one sentence with person `LabelChip`s; *Go to this week* a text button between the bar and the row; the empty week on a plain card with a tonal button. Add meal: the sheet title, filter chips, *Something else* with its label above, a placeholder and a filled + at its end, Use it up and the recipes as groups on the field color under the round search field. The meal's sheet: `CountStepper` for the servings, tonal Cooked, Move and Open recipe. Move: the dialog title and chips on the dialog row color. `StepButton` and the new `CountStepper` move into the kit (Mark opened uses it, its screenshots pixel-identical); `CookChip` gives way to `LabelChip`. *This week's ingredients* changes with step 6. Tests: `plan.spec.ts` gains the row of days (names, today, a tap bringing a day to the top, another week); its and `screens.spec.ts`'s day pickers look only in the list, since the day buttons share the `plan_day_` test ids' prefix; Plan's screenshots mask the day buttons' numbers and the days' headings, which move every week. Screenshot references re-recorded where their pixels changed. QA flow 70 | 18.2, 19.13, DECISIONS, QA |
| 2026-11-16 | A new look, step 6 (Android; the web's port is next): Recipes, the recipe, cook mode and import (19.12). The box says how many recipes it holds above its title, has the round search field and the chips (Favorites with its star), and shows plain cards with the photo or the letter in a leaf and the star in a round button. A recipe: the hero (the letter in a leaf without a photo) overlapped by the page, round Back, star, Edit and ⋮ over it, the facts as pills, the 48 dp servings stepper beside Ingredients, the checklist and the numbered steps as groups of rows, tonal Add to grocery list and I cooked this, Start cooking in a bar. Cook mode: Back, the name and "Step 1 of 2" as a chip, a bar per step, the step on a card with its number in a leaf, and Back, Ingredients and Next joined. Sheets and dialogs: the add sheet's ways as rows, Paste a link and Change in the dialog style, Add to grocery list (and Plan's week's ingredients) with its lines as groups of rows and label chips, I cooked this with fields of the new look. The editor and the import preview as pushed screens; the browser's round Close, Back and address field, and its bars with rounded tops. New shared pieces: `PushedScaffold`, `BackButton`, `RoundIconButton`, `BottomActionBar`; `PrimaryButton` takes an icon, `StepButton` and `CountStepper` a size (and the stepper a value style and live announcing), `SpoilNotTextField` a `minLines` for multi-line fields. `RecipesTab` is split from `RecipesScreen` for its test; `RecipesViewModel.UiState.total` counts the box. Tests: `RecipeLookTest`, `RecipesTabScreenshotTest` (new); `RecipePagesScreenshotTest` adds the preview and the editor's header. 36 references re-recorded (Recipes' and Plan's week's ingredients), 8 added. QA flow 71 | 5.5, 19, DECISIONS, QA |
| 2026-11-17 | A new look, step 6 on the iPhone web app (the Android step's port): the box with how many recipes above its title, the round search field, Favorites with its star, and plain cards with the photo or the letter in a leaf and a round star; a recipe with its hero (the letter in a leaf without a photo) overlapped by the page, the round Back, star, Edit and ⋮ pinned over it, the facts as pills, the 48 px servings stepper beside Ingredients, the checklist and the numbered steps as groups of rows, tonal Add to grocery list and I cooked this, and Start cooking in the look's bar; cook mode with Back, the name and the step chip at the top, a bar per step (still a named progress bar, A11Y-12), the step on a card with its number in a leaf, and Back, Ingredients and Next joined; Add to grocery list (and Plan's week's ingredients) with its lines as groups of rows on the field color and label chips; I cooked this with fields of the look; Change in the dialog style with the pantry's products as chips; the editor as a pushed screen with multi-line fields of the look. New: `RoundIconButton`, `DetailScaffold`'s `header` ('pushed', 'overlay'), `ListChips` and `SelectAllRow` shared with Plan; the old `.sn-stepper`, `.sn-info-chip` and `.sn-cook__bar` classes are gone. Recipes stays view-only on the web (18.1). Tests: `recipes.spec.ts` checks the count above the title, cook mode's name and step chip, and the step's Back; its Start-cooking check scrolls to I cooked this first (the hero puts it below the fold); `a11y.spec.ts` follows the stepper's and cook mode's new classes. Screenshot references re-recorded where their pixels changed (the household ones' only change was a date mask 2 px wider, so they keep their bytes). QA flow 72 | 18.2, 19.13, DECISIONS, QA |
| 2026-11-17 | A new look, step 7 (Android; the web's port is next): Your kitchen, Settings, Household, Notifications and About (19.12), with review batch B24 (PERF-03, PERF-09, PERF-10). Your kitchen: this month's name above the title, the month as Home's `primary` card with the percentage in `HeroNumber`, the 112 dp ring and the friendly line; the last six months and the reasons on plain cards (the chosen month on `secondaryContainer`, its details in a box of the page's color, thrown away striped and outlined on `tertiaryContainer`); the streak as an accent card with the days large. Settings: the account as the highlight card with the initial in a leaf and Resend email in a nested row; Household and Notifications, Data (Export is the row itself; crash reports moved here as a switch row) and the tour with About as groups of rows with icon tiles; Appearance on a plain card with Theme and Units as connected groups and the colors in a grid of up to four; Sign out a full-width tonal button, Delete account and Clear household data kept as error text buttons. Household, Notifications and About are pushed screens of the look (join requests as the highlight card, members as rows with initials, the invite on a plain card; switch, check and radio rows; the version row and privacy card); Settings' dialogs take `DialogTitle`. B24: the charts grow in the draw phase, with the stripes and the ring's dashes made once per size; a tab switch is one 150 ms fade of the screen on top (shared axis stays for pushed screens), told apart by each entry's tab in its metadata (pushed routes are `PushedRoute`s, which carry their tab); `Motion` holds the durations, easing and transitions; Remove animations is followed while the app runs (`LocalReduceMotion`, watched once by the theme). 6.4 no longer lists a container transform and a spring on check-off. New shared pieces: `PlainCard`, `ActionRow`, `SwitchRow`, `IconTile`, `RowLines`, `OnPrimaryLegend`, `UsageRingColors.onPrimary`, `PushedScaffold`'s `eyebrow`, `HeroNumber` and `AccentNumber`; `Segmented` is removed. Tests: `SettingsScreenshotTest` and `ConnectedButtonGroupTest` (replacing `SegmentedTest`) new; Your kitchen's, Household's, Notifications' and About's screenshots draw the whole page. Screenshot references re-recorded where their pixels changed (Home's and the theme gallery's are byte for byte the same). The Insights and tab-switch benchmarks wait for the run after step 8 (19.13). QA flow 73 | 5.6, 5.7, 6.4, 6.5, 19, DECISIONS, QA |
| 2026-11-17 | A new look, step 7 on the iPhone web app (the Android step's port): Your kitchen with this month above its title, the month as Home's primary card (the percentage large, the counts, the 112 px ring and the friendly line), the months and the reasons on plain cards with the chosen month's details in a box of the page's color, and the streak as an accent card with the days large; Settings with the account as the highlight card (the initial in a leaf, Resend email in a nested row), Household and Notifications, Data and About as groups of rows with icon tiles, Appearance on a plain card with Theme and Units as connected groups and the colors in a grid of up to four, and a tonal Sign out; Household, Notifications and About as pushed screens of the look (join requests as the highlight card, members as rows with initials, switch, check and radio rows, the version row and privacy card); Settings' dialogs take the dialog title. Your kitchen's charts grow with CSS animations, not React state per frame (review WEB-13, the web's part of B24). New in the kit: `ActionRow`, `SwitchRow`, `ChoiceRow`, `IconTile`, `GroupHeading`, `DetailScaffold`'s `eyebrow`, `.t-hero-number` and `.t-accent-number`; `Segmented` removed. The old streak plurals (`insights_streak_days`, `insights_streak_never`) are removed from the shared strings now both apps use the new ones. Tests: `home.spec.ts` checks Your kitchen's month line, the ring's name and the headings, `settings.spec.ts` the account card, a row read as one button and the page's headings, and the kit test covers `ConnectedButtonGroup` in place of `Segmented`. Screenshot references re-recorded where their pixels changed (Home's are byte for byte the same). QA flow 74 | 18.2, 19.13, DECISIONS, QA |
| 2026-11-17 | A new look, step 8 (Android; the web's port is next): Welcome, Sign in, Create account, Reset password, the household gate, the tour and Update required (19.12). Welcome: the jar in a 208 dp leaf, the name in Fredoka (`Wordmark`), the tagline, and Create account and Sign in as 60 dp buttons at the bottom, on the page's color. The signed-out forms are pushed screens with fields 18 dp apart; a wrong email or password shows on the password field (outlined, with its icon and message) and moves the focus there, an email in use or a weak password on its field, anything else under the form; `ErrorText` has the error icon everywhere. The gate: the page's header with "Welcome, Sam" and a round ⋮, Create and Join on plain cards, the join request in flight as the highlight card, the join dialog's title in `DialogTitle`. The tour's card: the dialogs' surface, 28 dp corners, `DialogTitle` and a filled round Next. Update required: its icon in a leaf, the title in `SheetTitle`. `PlainCard` gives fields on it the page's color. Tests: `AuthErrorsTest` (new). Screenshot references re-recorded where their pixels changed (Welcome's also under the quota banner). QA flow 75 | 5.1, 19, DECISIONS, QA |
| 2026-11-17 | A new look, step 8 on the iPhone web app (the Android step's port), the last: Welcome with the jar in a large leaf, the name in Fredoka, the tagline and Create account and Sign in as 60 px buttons, then the line about the Home Screen; Sign in, Create account and Reset password as pushed screens, a field's server error on that field with the focus moved there; the gate with the page's header and round ⋮, Create and Join on plain cards and a request in flight as the highlight card, the join dialog's title in the dialog style. `ErrorText` has the error icon, a field on a plain card takes the page's color (Week starts on's box shows on the Appearance card), and a button's `.t-button-medium` or `.t-button-large` now wins over the button's own weight, so the labels of steps 6 and 7 (Plan, Recipes' sheets, Settings, Household) are as bold as in the app. Tests: `household.spec.ts` checks a wrong password on the password field, focused, after a real failed sign-in. Screenshot references re-recorded where their pixels changed. QA flow 76 | 18.2, 19.13, DECISIONS, QA |
| 2026-11-17 | The new look's baseline profile and benchmarks (Android; 19.13, review 08's B23 and B24 deferred to this run): the profile regenerated (17,346 → 17,651 rules, with the next row's fix), its journey opening Your kitchen by the card's test tag (the "See your kitchen" button it looked for went in step 2); `JankBenchmark` (new) keeps review 08's scenarios for every tab, the sheets and typing; `PantryScrollPerfTest` also seeds 150 grocery items, 40 recipes, three weeks of meals and six months of counts, each only where missing; `baseline-profile.sh --jank` runs it a test at a time and `benchmark-summary.py` prints the janky-frame share. Measured headless on the Mac's GPU, with an A/B against the build before the look (`98eb650`) on the same day: cold start to Home's cards 757 ms (710 ms on 2026-11-13); the first Pantry scroll P99 8.0 ms with 96 frames an iteration (2026-11-13: 21 ms, 62 frames); Groceries' quick add 13% janky against 36% before the look, Plan's add sheet 6% (review 08: 12.5%), tab switches and Add item as before; Your kitchen still about half its frames late while the charts grow. Found: Pantry's search drew on every vsync while typing (fixed the same day, next row), and Plan's first scroll compiles two shaders the old look didn't need (left open). The table is in 13.1 | 13.1, 19.13, RUNBOOK, QA, DECISIONS |
| 2026-11-17 | Pantry's search stops animating the list (Android; found by `JankBenchmark`, 13.1): a new search, filter, sort or grouping replaces the rows at once, the list composed afresh for it (`key` on the query, held with the rows while the list scrolls, `ShownRows`), so no row fades or slides; rows still animate when the food changes. Another phone's changes are told from every batch instead of the rows shown, so a row that a wider search or filter brings back is no longer highlighted for 1.5 s as if another phone had added it (a bug since the highlight came in). Measured the same day, alternating with the build before it: typing "item 12" draws 54–56 frames an iteration instead of 96, 23–31% janky instead of 94–95%; Pantry's steady scroll unchanged. Baseline profile regenerated for the renamed list code. Tests: `PantryListMotionTest` (new) times how long the tab takes to settle: at once after a search, clearing it and a new sort (368, 1,568 and 464 ms before the fix), still animating for a row used up and highlighting another phone's new batch. The web's Pantry has the same highlight tracking and gets it in its port. QA flow 77 | 6.4, 13.1, DECISIONS, QA |
| 2026-11-18 | Pantry's search fix on the iPhone web app (the Android row's port), and Groceries' list switch on both: the web's Pantry tells another phone's changes from every batch, held with the rows and their query while the list scrolls, so a row that a wider search, filter or sort brings back is no longer highlighted (web rows never faded or slid; the highlight was the only animation, three of them on clearing a search in the test). Groceries tracked the open list's rows without knowing the list, and right after a switch the old list's items still show under the new list, so switching lists highlighted every row of the new list for 1.5 s (Android and web; checked by tests first): the tracker now takes a scope, the state says which list its items belong to (`itemsListId` on Android, the items listener's list id on the web), and a new list's first items are a first load. "Sam added Eggs" follows the same list id, so a switch can't announce what was already on the other list (found by reading the code, not by a test: Android, with no time limit on it, would announce every such item someone else made; the web only those made in the last minutes). Tests: `pantry.spec.ts` (a search, a filter and a sort highlight nothing and leave no animation running; another member's new purchase is highlighted with the search empty and while searching), `groceries.spec.ts` and `GroceryListSwitchTest` (switching both ways highlights nothing; another phone's new item still is; both failed before the fix, 1,600 ms to settle on Android), and the trackers' scope in `RemoteChangeTrackerTest` and `remoteChanges.test.ts`. QA flow 78 | 6.4, 18.2, DECISIONS, QA |
| 2026-11-18 | Web test fix: Home's screenshot at the largest text size (`a11y.spec.ts`, `home_font3`) failed on some days. At AX5 the date above the title takes one line on "Monday, Nov 16", the day the reference was recorded, and two on "Wednesday, Nov 18", so the whole page moved about 50 px under the mask. The test now sets the line to "Wednesday, Nov 18", the longest weekday, before the shot and no longer masks it, so the picture also shows the date wrapping whole; the reference is re-recorded with it (the rest of the page is unchanged). The app is unchanged | 18.2, DECISIONS |
| 2026-11-21 | Two flaky browser tests and a third found while checking them (web only). A row swipe let go before React drew its last move was judged on an earlier, shorter distance and sprang back: `useSwipe` now decides on the last move's distance, kept with the drag (`swipeDirection`, unit tested), so a quick swipe on a busy phone acts as far as it went; this failed `iphone.spec.ts`' edge-swipe check-off under a full run's load, and a new `iphone.spec.ts` test sends a whole swipe in one task (it fails on the old code). `notifications.spec.ts` tapped Turn on just after a reload while Home was still on the cache, and the Offline banner going moved the line up under the tap: the test now waits for the server's answer after each reload (`reloadHome`); `pantry.spec.ts`' swipe waits the same way before measuring the row. Its other failure, a run into the 90 s timeout, didn't come back in 8 full runs; the browser tests now stop a click or fill after 30 s (`actionTimeout`), so a hang names its step. The Offline banner showing on every start until the server answers is unchanged in both apps | 18.2, 18.7, DECISIONS |
| 2026-11-21 | The Offline banner waits 2 seconds (both apps): it shows once a screen's data has come from the cache for 2 s without a break and goes as soon as the server answers. Opening a screen and Plan's next and previous week read the cache first and the server a moment later, and the banner showed for that moment, moving everything under it down and back up (a tap could land on the wrong row). Android: `OfflineBanner(offline)` in `core/designsystem` waits (`OFFLINE_BANNER_DELAY_MS`) and Home, Pantry, Plan, Groceries and Recipes pass their flag to it; `OfflineBanner()` still draws it, so the screenshots are unchanged. Web: `OfflineBanner({ offline })` with `useTrueAfter` in `core/hooks.ts`, and a hidden `offline_waiting` mark while it waits. Tests: `OfflineBannerTest` (Robolectric, the test clock) and `useTrueAfter`'s cases in `hooks.test.tsx`; the browser tests wait for the server with `expectFromServer` (support.ts) instead of the banner's absence, and `quota.spec.ts` gives the banner 10 s to show. Checked by hand in the browser: paging Plan's weeks back and forth flashed the banner 4 times in 8 changes before and none after. QA flow 79 | 9.5, 18.7, DECISIONS, QA |
| 2026-11-21 | Four fixes the owner asked for, on both apps where they apply. **Pantry's search field** shows what was typed at once (`PantryViewModel.searchText`); it showed the list's state, rebuilt off the main thread and so a little late, and lost or doubled letters with the cursor behind them ("tem 12i" after typing "item 12" on the emulator; the web's field was fine). **A new search, filter, sort or grouping starts the list at its top** on both apps; with nothing in Use first, taking a filter off left the list on the filtered list's first row. **Food names are saved with every word capitalized** (`NameCase` on both apps, 8.1) for pantry and grocery items however they are added, and the name fields capitalize words on the keyboard. **Groceries' title and profile button** sit where the other tabs have them (19.7): the 48 dp list switcher now takes only the title's line in the header, on both apps. Tests: `NameCaseTest` and `nameCase.test.ts` (new), `PantrySearchAndFiltersTest` and `TabHeaderAlignmentTest` (new; each fails on the old code), the service tests expect capitalized names, `JankBenchmark`'s typing tests check the field shows exactly what was typed, and browser tests for the list's top and Groceries' header. Screenshot references re-recorded where their pixels changed. QA flow 80 | 5.2, 8.1, 19.7, DECISIONS, QA |
| 2026-11-21 | Release 1.0.0 (build 13, commit `744d251`), the first without a beta number, through App Distribution; the iPhone web app from the same commit to spoilnot-prod (its first prod deploy since 2026-10-31, with the review's fixes and the new look); `minSupportedBuild` 13 and `minWebBuild` 3; then the rules held back since 2026-11-08 (8.1, 8.5, 5.1, 5.9) to spoilnot-dev, checked, and spoilnot-prod | 13.3, RUNBOOK, QA |
| 2026-11-22 | Tests and dead code (review batch B28; findings CODE-01 and CODE-08; BASE-01 was fixed by #118). Android: v1's per-product pantry list and product detail save are deleted (`PantryList.build`, `PantryGroup`, `updateProduct`, `ProductEdit`, `PantryLimits.BATCH_LIMIT`); Home's Running low keeps `PantryList.rows`; the cases that still apply move to `PantryItemsTest` and `planEdit`'s tests, and the two-phone rename goes through the Edit window. `TabNavigator.visible` now builds `AppShell`'s visible entries. New tests: a two-phone `GroceryFlowTest`, `ClearHouseholdDataUiTest`, and the opt-in `SharePreviewTimingTest` (`@TimingTest`, `timing=true`). `JoinAndSignOutUiTest` no longer loses its Continue tap to the keyboard sliding in (`closeKeyboard`); the web screens test taps Plan's Next week once the week has come from the server and names a lost tap. Six unused string keys go (`recipe_delete_title`, `plan_clear_title`, `settings_account`, `insights_counts`, `action_export`, the web's `web_not_configured`). No change on screen | 5.2, 13.1, RUNBOOK, DECISIONS |
| 2026-11-22 | Smaller downloads and faster CI (review batch B26: EFF-06, EFF-07, EFF-08, BASE-03, BASE-05, CODE-05). **Android:** the release APK leaves out the libraries' `.proto` sources, Kotlin's reflection metadata and the coroutines debug probes, ships only the ARM native libraries (release only; the benchmark build keeps every ABI), and bundles Manrope and Fredoka as subsets (`scripts/subset-fonts.sh`, the full fonts in `tools/fonts`; `FontSubsetTest` checks every string, code literal and amount against them). The prod release APK went from 5,048,996 to 4,621,214 bytes (−8.5%) and its download from 4,334,885 to 3,967,285 bytes (−8.5%): fonts 170 → 59 KB compressed, `.proto` files 222 KB, x86 libraries 34 KB, metadata 13 KB. Turbine, `ui-tooling` and `ui-tooling-preview` and the instrumented tests' unused `kotlinx-coroutines-test` are removed (MockK is used since the review). **Web:** Firebase, React and the other libraries in chunks of their own, so an app-only release downloads about 113 kB gzipped of code instead of 387 kB; `licenses.json` and the non-Latin font subsets are cached when first used, not precached (21 → 19 entries, 1,615 → 1,445 KiB, 550 → 490 kB gzipped); the size warning at 750 kB no longer shows. Lighthouse (same day, alternating with main's build): performance 90 against 91, accessibility 100, largest contentful paint 3.7 against 3.5 s simulated, 183 against 205 ms observed (18.6). **CI:** each job runs only when its files changed, the web job runs in Playwright's image, and the Android job compiles the instrumented tests; CLAUDE.md's finishing checks list every check CI runs. CI on this change's own runs (every job, since `ci.yml` changed): Android 368–524 s against a 284 s median (the instrumented tests' compile, and a cache that only partly matched), web 348–350 s against 346 s; the saving is in the jobs a single-area change no longer runs (13.2). Tests: `FontSubsetTest`, `runtimeCache.test.ts`, `runtime-cache.spec.ts`. Welcome's screenshots (also under the quota banner) re-recorded: the static Fredoka's glyph edges anti-alias a little differently; every other screenshot matches pixel for pixel. QA flow 81 | 6.4, 7.3, 12.2, 13.1, 13.2, 18.5, 18.6, DECISIONS, QA, RUNBOOK, CLAUDE.md |
| 2026-11-22 | Cooked offline shows at once (both apps): a meal's `cookedAt` is read with the server time estimated, so a meal marked Cooked without a signal shows *Cooked* and *Not cooked yet* at once instead of when the server confirms it, and Cooked can't be pressed again meanwhile (the rules refuse a second one since 2026-11-09, 8.5 invariant 10). New tests also show that removing a cooked meal and Undo keep it cooked, online and offline: unit tests of each app's meal read, two offline tests in `PlanFlowTest`, an offline step in `race.spec.ts` and a Remove-and-Undo test in `offline.spec.ts` in WebKit; QA flow 82 | 5.9, QA, DECISIONS |
| 2026-11-22 | Honest reminder times (both apps): GitHub started 71 of about 500 scheduled reminder runs in a week, so a 9:00 digest came about 3 hours late (0.4 to 7, 10.4). The apps stop promising the hour: Settings' row says "Daily, after 9:00 AM" (was "shortly after"), the time's helper says it comes after the time, often a few hours later, and that nothing is sent after 10:00 PM, so an evening time can be skipped (was "It arrives shortly after this time."), the explanation and You're set say "after" the hour (were "around"), and the battery hint no longer blames the phone for late reminders. The job and the hours offered are unchanged. `NotificationPrefs.LATEST_HOUR` and `NotificationRules.LATEST_HOUR` (22) name the job's limit, with tests; the Notifications page's screenshots (both apps, 1.0× text) and Android's Settings row re-recorded; QA flows 35, 57 and 59 updated and flow 83 | 5.4, 10.2, 10.4, QA, DECISIONS |
| 2026-11-22 | Actions settings: only GitHub's own actions plus `gradle/actions/setup-gradle` and `gitleaks/gitleaks-action` may run, and every action must be pinned to a full-length commit SHA (a repository setting; all 28 references were already pinned; checked by re-running main's CI and secret-scan). The jobs' three keys stay repository secrets, and the docs no longer say the `production` environment holds them (owner's choice) | 10.4, 12.2, RUNBOOK, SECURITY_REVIEW, DECISIONS |
| 2026-11-22 | On-time reminders: a Google Apps Script in the owner's Google account starts the reminder job through `workflow_dispatch` every quarter hour (`backend/reminder/apps-script/trigger.gs`, six tests in `test/trigger.test.ts`), with a fine-grained token (this repository, Actions: read and write) kept as a script property, so a digest arrives about a minute after its time instead of hours later; GitHub's schedule stays as the backup. The restore moves to a `restore` environment whose required reviewer is the owner. The quota simulation's planning figure becomes 120 runs a day (about 14 households, from 17). The apps' wording changes once a day of runs shows the starts on time | 9.7, 10.4, 10.6, 12.2, RUNBOOK, SECURITY_REVIEW, DECISIONS |
| 2026-11-23 | The reminder job reads only the users who may be due (B25, review EFF-04): it keeps `nextDigestAt` on each profile itself and reads those whose time has come, those whose profile changed since its last run (`updatedAt`, which both apps stamp) and once a day everyone with reminders on. Same digests at the same runs; no app, rules or index change. About 18 households fit at 120 runs a day, from about 14.5 | 8.2, 9.7, 10.1, 10.3, 10.4, 10.5, 12.4, RUNBOOK, PRIVACY |
| 2026-11-23 | Plan's first scroll is smooth again (Android; DESIGN 13.1's open item): the two GPU programs it compiled are Android's stretch overscroll, drawn when a list is pulled past its end, which Plan's short week reaches in the benchmark's first flings. `StretchOverscrollWarmUp` compiles them ½ s after the start-up screen reports itself drawn, by stretching a one-row list for two frames under the tab bar, where nothing of it shows. Measured the same day against `main`: no programs compiled on Plan's first scroll in 20 iterations (`main`: 2 in every iteration that reached the end, 12 of 15), P99 11.5 against 24.7 ms and 7.3% against 14.6% janky in those iterations; Home's first scroll, tab switches and cold start unchanged or better. Baseline profile regenerated (17,691 rules). `tapUntil` in the benchmark driver retries a row that went stale while a list settled. RUNBOOK says how to find what a late frame did in the benchmark's traces. Tests: `StretchWarmUpTest` (new). No change on screen | 13.1, 19.11, RUNBOOK, DECISIONS |
| 2026-11-24 | Build updates (Dependabot's open Android updates but Compose): AGP 9.5.2, which configures the release variants again (9.4.1 since 2026-10-15, DECISIONS 2026-10-31 and 2026-11-14), Gradle 9.9.2, Robolectric 4.18.1 and error-prone annotations 2.53.0 (the Gradle constraint). Dependabot no longer skips AGP 9.5.0 and 9.5.1. Compose BOM 2026.11.01 follows in its own change: it needs `compileSdk` 37.1 or later and replaces two APIs the app uses | 12.2, RUNBOOK, DECISIONS |
| 2026-11-24 | Compose BOM 2026.11.01 (Compose 1.13, Material 3 1.5; Dependabot #152): `compileSdk` 37.2, which Compose 1.13 requires (`targetSdk` stays 37); `ExposedDropdownMenu` imported as Material 3 1.5's extension in Groceries' quick add and Pantry's forms; the keyboard focus ring rebuilt on `getFocusedRect`, since Compose 1.13 made `onFocusedBoundsChanged` a no-op, with its first test (`FocusRingTest`: the ring beside the focused button, after a move by code and after Tab, none for touch); the accessibility canary now checks only the app's sheet, since Material 3 1.5's own sheet passes the touch-target check too. Screenshots unchanged; baseline profile regenerated (17,789 rules) | 6.8, 7.6, 13.1, RUNBOOK, DECISIONS |

## 18. Web app for iPhone (after v1)

The owner asked (October 29, 2026) for a version that iPhone users install from the web, with the same look and feel
as the Android app. It is a second front end, a Progressive Web App in `web/`, on the same Firebase project, data
model (section 8) and security rules: an iPhone and an Android phone share one household and see each other's changes
live. The Android app does not change.

**18.1 Scope.** Everything the Android app does, with these exceptions:

- **No adding recipes** (owner's choice): no Add recipe, no import from a link, no built-in browser, no share target
  (iOS does not let web apps receive shares). Saved recipes show exactly as on Android, with Favorite, Edit, Delete,
  the servings stepper, Add to grocery list, I cooked this and planning (none of them creates a recipe).
- **No wallpaper colors**: Settings > Appearance offers the Spoil Not green and the five preset themes (6.4); Material
  You exists only on Android.
- **No crash reports**: there is no Crashlytics on the web, so Settings has no crash reports switch and About no test
  crash; About's privacy summary says so.
- **No app tour** (owner's choice, October 30, 2026): the web app has no tour (5.8) and Settings no Take the app tour
  row. W6a shipped one; W6b removed it.
- **No battery tip** in Settings > Notifications: iPhones have no battery restrictions to lift for web push.
- **The same failure handling** (after v1, 2026-11-09): the session port and the changes-only listeners keep what is
  on screen through any error but a permission refusal (7.5), and `domain/quota.ts` ports `QuotaSignal`, so the quota
  banner of 6.7 shows above the web app's root too. Only `src/data` raises it (`toAppError`). Development builds let
  the browser tests refuse every server listener with a Firestore code (`window.spoilnotTest.refuseListens`), since
  the emulator has no quota; release builds don't have it.
- **Week starts on** (after v1, 2026-11-12): Android takes the first day of Plan's week from the phone, including
  Android 14's own First day of week setting; a web app can't read the iPhone's, and the browser's language only
  gives the region's day. So the web's Settings > Appearance has *Week starts on*: Automatic (the region's day, named:
  "Automatic (Sunday)"), Sunday, Monday or Saturday, kept on the phone like the theme. Browsers without week
  information use CLDR's lists of Sunday and Saturday regions. Since B15's web port (2026-11-12) Home listens to the
  same week as Plan, so Use it up leaves out exactly the recipes Plan shows from today to the end of that week.
- **Installed from Safari** with Share > Add to Home Screen (iOS 16.4 or later, needed for notifications). It opens
  full screen with the Spoil Not icon. It also works in any current browser, but it is designed and tested for iPhone.

**18.2 Look and feel.** The web app copies the Android app, not iOS conventions:

- **Colors**: CSS custom properties generated from `Color.kt`, `PresetColors.kt` and `StatusColors.kt`
  (`web/scripts/gen-theme.mjs`), with the same Material 3 role names, light and dark following the phone, and
  Settings > Appearance like 5.6.
- **Type**: Manrope for everything and Fredoka for the welcome title, self-hosted (no font requests at run time), with
  the Material 3 type scale Compose uses (`headlineSmall` 24/32, `titleMedium` 16/24 at weight 500, `bodyLarge`
  16/24 and so on). Text follows the iPhone's text size setting (`-apple-system-body`) the way Android's font scale
  works since Android 14: non-linearly, with the tables of `FontScaleConverterFactory` (`web/src/theme/textScale.ts`),
  so at 200% body text nearly doubles and headlines grow by about a third. Each size is a CSS variable (`--sp-16`);
  past Android's 200% (iPhone accessibility sizes) the 200% table grows with the scale. From 1.5× (`LARGE_TEXT_SCALE`, as `LARGE_TEXT_FONT_SCALE` in the app) `applyTextScale` marks the page `data-large-text`, and what sits beside a name goes under it (after v1, 2026-11-13, review A11Y-04): status chips and Low/Out badges on Home and Pantry, the join card's Review button, the recipe's servings stepper, I cooked this amounts, cook mode's Ingredients button and Insights' legend; a status chip may take two lines rather than run past its card. Tab lists leave room under the last row for the floating button as measured (`--sn-fab-space`, at least 96 px), and the button grows with the text instead of cutting it.
- **Shapes and spacing**: the Material 3 defaults with 16 dp cards and 28 dp sheets (6.4), the 640 dp content width
  (6.2) and 48 dp touch targets; 1 dp is 1 CSS pixel.
- **The new look (section 19, after v1, 2026-11-16)** follows the app step by step. `theme.ts` sets the surfaces of
  19.2 as `--sn-surface-page`, `-row`, `-nested-row`, `-field` and `-tile` from the same roles in light and dark;
  `base.css` has 19.4's styles as `.t-screen-title`, `.t-row-title` and so on, with `--sp-13`, `-18`, `-30`, `-48`
  and `-54` added to the scaled sizes; `kit/Look.tsx` has the shared pieces (`ListGroup`, `StatusTile`,
  `HighlightCard`, `AccentCard`, `PlainCard`, `LeafBadge`, `LabelChip`), each added with the first screen that uses
  it. A group's corners come from CSS (`:first-child`, `:last-child`). `Button`'s `primary` is `PrimaryButton`, used
  where the app uses it; Start cooking stays a 48 px bar button, as in the app. Step 3 (2026-11-16) adds
  `--sn-surface-dialog-row`, `.t-sheet-title`, `.t-dialog-title`, `.t-field-label` and `.t-field-text` (sizes 17, 26
  and 34), and to `Look.tsx` `SearchField`, `SpoilNotFilterChip` and `ConnectedButtonGroup`. `TextField` puts its
  label above a box on the field color, as `SpoilNotTextField` does, and `SelectField` follows; its `outlined` keeps
  Material's outlined field where the app still has one (Groceries' quick add, Recipes' search and the recipe
  sheets). `FormSheetColumn`'s `look` and `Dialog`'s `titleClassName` give Pantry's sheets and dialogs their titles.
  Pantry's list holds remote changes while it scrolls (`useHeldWhileScrolling`, review WEB-11) and its rows are
  memoized and keep their item objects across snapshots (`keepUnchanged`, WEB-13). Step 4 (2026-11-16) adds
  `PillField`, the round field `SearchField` and Groceries' quick add share, with a filled + (`.sn-pill-add`); the
  Groceries list uses Pantry's groups, pinned headings and list padding, its rows are memoized too, and
  `keepUnchanged` and `useKeepUnchanged` (`domain/identity.ts`, `core/keepUnchanged.ts`) take the item's key. Pinned
  headings sit below the screen header, so a menu opened from it stays on top. Step 5 (2026-11-16) moves `StepButton`
  into the kit with a new `CountStepper` (Mark opened and a meal's servings), as the app moved them into its design
  system; Plan's week bar and row of days stay above the scrolling days (`.sn-plan-controls`), and from 1.5× text the
  row scrolls sideways (`:root[data-large-text]`). A day button scrolls the list to its day's `data-day` (smoothly,
  or at once with Reduce Motion). Move's chips take the dialog row color with `.sn-filter-chip--dialog`. Step 6
  (2026-11-17) adds `RoundIconButton` and `DetailScaffold`'s `header`: 'pushed' (the round Back on its own line over
  the title, the recipe editor) and 'overlay' (the round Back and the actions pinned over a recipe's hero, with no
  height of their own), both with the look's bar for the main button (80 px). `.sn-filter-chip--dialog` and
  `--field` move to `look.css` (a chip on a dialog or a sheet), `TextArea` takes the field look (label above, at least
  four lines), and `CountStepper` can announce its value (`aria-live`). Cook mode keeps its bars a named progress
  bar (`role="progressbar"`, A11Y-12), as the web had it; the chip is announced as the page turns. Recipes is
  view-only on the web (18.1), so the add sheet, Paste a link, the import preview and the browser have no port.
  Step 7 (2026-11-17) moves Your kitchen, Settings, Household, Notifications and About to `header="pushed"`, with
  `DetailScaffold`'s `eyebrow` for Your kitchen's month; adds to the kit `ActionRow`, `SwitchRow`, `ChoiceRow`,
  `IconTile` and `GroupHeading`, and `LeafBadge` takes a size, a letter and the highlight card's colors; adds
  `.t-hero-number` and `.t-accent-number` (and `--sp-70`). Your kitchen's charts grow with CSS animations (the
  ring revealed by a mask that sweeps round, the bars scaled from their base) instead of React state on every frame
  (review WEB-13); the stripes are a CSS gradient. `Segmented` is removed (Theme and Units are connected button
  groups). The web has no crash reports switch and no tour (18.1), so Data has only Export and About is a group of
  its own; Week starts on stays on the Appearance card. Step 8 (2026-11-17) brings Welcome (the jar in a 208 px
  leaf, `.t-wordmark`, 60 px buttons, then the line about the Home Screen), the signed-out forms as `header="pushed"`
  screens with a field's server error on the field and the focus moved there (`TextField`'s and `PasswordField`'s
  `inputRef`), and the gate with the page's header, plain cards and the highlight card for a request in flight.
  `ErrorText` has the error icon; `.sn-plain-card` gives a field on it the page's color (`--sn-surface-field`), which
  also shows Week starts on's box on the Appearance card; and a button's `.t-button-medium` or `.t-button-large` now
  wins over the button's own 500 weight, which it hadn't since step 6. The web has no tour and shows Update required
  as a prompt (18.1), so neither has a port. Android's Pantry search fix (6.4, 2026-11-17) came to the web on
  2026-11-18: `useRemoteChanges` is given every batch, held with the rows and the query that picked them while the
  list scrolls (`useHeldWhileScrolling`, as Android's `ShownRows`), not the batches of the rows shown. Web rows have
  no fade or slide (only a swipe moves them), so a search, filter or sort already replaced them at once; the
  highlight was the one animation it set off. `useRemoteChanges` takes a scope, and Groceries passes the list its
  items belong to (6.4).
- **Icons**: the Material Icons Rounded set (`@material-design-icons/svg`), the same glyphs as `Icons.Rounded` in the
  app, as inline SVG.
- **Components**: a small kit in `web/src/ui/kit/` built to the Material 3 specs the Compose components follow: the
  pinned top app bar, the 80 dp navigation bar with a pill indicator and filled or outlined icons, outlined cards,
  buttons, icon buttons, the extended FAB, outlined text fields, filter chips with a check, checkboxes, radio buttons,
  switches, menus, dialogs, modal bottom sheets with a drag handle, snackbars with Undo and state layers on press.
  After v1 (2026-11-12, WEB-14) sheets stay above the on-screen keyboard like `imePadding` in the app: an iPhone
  doesn't resize the page for the keyboard, it shrinks the visual viewport, so `BottomSheet` follows
  `window.visualViewport` (`resize` and `scroll`), lifts the sheet by the part of the page the keyboard covers
  (`--sn-keyboard`), caps its height at what is left, and scrolls the focused field into view (`block: 'nearest'`),
  so Save, pinned below the fields, stays visible while typing. Nothing changes while pinch-zoomed. A form sheet's
  fields now scroll above its pinned button also when they are taller than the sheet; before, the whole sheet scrolled
  and the button sat below the screen (Add to pantry with every row open, Add item and Add to grocery list at 200%).
- **Swipes**: rows swipe like `SwipeToDismissBox`. In a Safari tab a swipe from the screen's left edge is Safari's
  Back, so after v1 (2026-11-12, WEB-15) a row swipe that starts within 24 px of the left edge is left to Safari;
  the Home Screen app has no edge gesture and keeps every swipe. Letting go is judged on the last move's distance
  (after v1, 2026-11-21), not on the distance last drawn, so a quick swipe on a busy phone acts as far as it went.
  They use native elements (`button`, `input`, `dialog`) so VoiceOver reads them like TalkBack reads the app.
- **Words**: `web/src/strings.generated.ts` is generated from `app/src/main/res/values/strings.xml`
  (`web/scripts/gen-strings.mjs`, plurals with `Intl.PluralRules`), so both apps say the same thing. Web-only text
  (Add to Home Screen help) lives in `web/strings-web.xml` in the same format.
- **Never color alone, Undo for every destructive action, empty states and skeletons**: as section 6.
- **Checked by screenshots**: Playwright screenshots of each screen in light and dark at 100% and 200% text size,
  compared side by side with the Roborazzi images of the same screen when a milestone adds it; after v1
  (2026-11-13) Home, Pantry and Groceries also at the iPhone's largest size, AX5 (312%, `a11y.spec.ts`). Text that
  changes per run or with the calendar is masked; where its width moves the layout, the test first gives it fixed
  text (after v1, 2026-11-14: Insights' month labels, whose widths place the columns at 200%; 2026-11-18: Home's
  date at AX5, which takes one line or two with the weekday, shown as "Wednesday, Nov 18"). CI skips the
  screenshots (Linux WebKit draws fonts differently); they are compared on the Mac.

**18.3 Architecture** (mirrors section 7):

- Vite, React and TypeScript (strict). `web/src/domain/` is the business logic ported from `feature/*/domain` as pure
  TypeScript with an injectable clock, its tests ported case by case from the Kotlin unit tests. `web/src/data/` is
  the only code that imports Firestore (repositories, change mappers, the changes-only listeners of 9.8 with their
  full-read records and cursors in `localStorage`). Components never import Firebase. Session states come from a port of
  `SessionManager`; writes are change plans applied by a `HouseholdWriter` port, and destructive actions return Undo.
  One difference: when the profile loses its household pointer the web session waits 1 s before the gate
  (`POINTER_SETTLE_MS`), because the web SDK can raise the server's older copy of the profile, without the pointer,
  between the acknowledgement of the phone's own write and the newer version; acting on it showed the gate and
  cleared the cache right after creating a household.
- Settings > Appearance (with *Week starts on*, 18.1) and whether the notification explainer was shown live in `localStorage` (`AppPreferences` in
  `web/src/core/preferences.ts`), like the app's DataStore: per phone, not household data. Export downloads the JSON file (Safari asks, then keeps it in
  Files > Downloads), in the app's format with `appVersion` "web <commit>". About lists the open source licenses of
  every run-time package in `package-lock.json` with the text each ships (`public/licenses.json`, written by
  `npm run gen`, like the app's AboutLibraries data).
- Sheet drafts (after v1, 2026-11-12): iOS discards a page left in the background and loads it again when the person
  comes back, which on Android is process death. Add to pantry and Plan's Add meal and meal sheets keep their open
  state and what was picked in `sessionStorage` (`spoilnot.draft.*`, `useSessionDraft` in `web/src/core/drafts.ts`),
  like the app's saved state: written on each change, read when the screen mounts, removed when the sheet closes, the
  screen leaves (a tab switch closes its sheets, as before) or nobody is signed in. Add to pantry keeps edits by item
  id apart from the cart (`ui/grocery/addToPantryDrafts.ts`, as `AddToPantryDrafts` in the app), so another phone's
  check-off adds a row and keeps the edited ones; a restored sheet brings back its list and Plan its week. The other
  sheets already keep their picks by key when data changes under them and are not kept through a reload: the pantry's
  Add item and Edit window, the grocery Edit sheet, quick add's text, a recipe's Add to grocery list, I cooked this,
  cook mode's ticks, the week's ingredients and the dialogs.
- Firestore's persistent cache (IndexedDB) keeps the household on the phone, so the app opens and edits offline and
  syncs later, like 9.5. Safari may clear a web app's storage if it is not opened for weeks; the next open reads it
  again (the daily full read).
- No rules change except accepting `platform: web` devices. Reads count against the same free quota (9.7): a web
  phone costs about the same as an Android phone.
- Listener lifetimes (after v1, 2026-11-10, 9.1): screens listen only while the page is visible (`useObservable` with
  `pageVisible$`); the pantry and each list's items have one pair of listeners per phone, shared by every screen
  (`SharedByKey`), let go 5 s after the last screen leaves, or at once while the page is hidden; the restock engine
  reads the cache only and runs while the signed-in shell is shown (9.6). Only the session's profile and membership
  listeners stay while the page is hidden; the update gate's `config/app` is the shell's, like a screen's.

**18.4 Notifications.** iPhones show web push from Home Screen web apps (iOS 16.4 and later). The permission is asked
from a tap (iOS requires one): the explanation under 5.4's rule, Home's Turn on and Settings > Notifications. The FCM
web token goes to `users/{uid}/devices/{installId}` with `platform: web`; the reminder job adds a `webpush` part with
the same title, body and tag (the route stays in `data`). A VAPID key pair from the Firebase console (Cloud Messaging >
Web Push certificates) is a human step; its public key goes in the web build's config (`VITE_FIREBASE_VAPID_KEY`).

- **Registration** (`FcmDeviceRegistry`, `DeviceSync`): at each start for the signed-in user, and right after the user
  allows notifications, the phone writes its time zone when it changed and, when it has a token, its device document
  (`installId` is a random id kept in `localStorage`). A token needs the permission, the Push API (a Home Screen web
  app), the service worker (not in development) and the VAPID key; `firebase/messaging` loads only then. Sign-out
  deletes the device document, as in the app. The token comes from `FcmPushIdentity` (PushIdentity in the app; a
  fake in `session.test.ts`). When a user leaves the phone (sign-out, account deletion, or Firebase signing them out)
  `DeviceSync` deletes the token (2026-11-08): it sets `spoilnot.push.tokenToDelete` in `localStorage` first and
  clears it once FCM confirms, within 5 s; while it is set, every start deletes the token before registering anyone,
  so a phone signed out offline stops getting digests at its next start online. A start with nobody signed in and no
  flag loads nothing. `deleteToken` is called after `getToken` with the app's service worker, because without one
  FCM's SDK would register its own worker in place of the app's.
- **The service worker shows every push itself** (`web/src/sw.ts`, `core/pushMessages.ts`), without FCM's own
  service worker: iOS ends the subscription of an app that receives a push without showing a notification. A tap
  sends an open window to the route (a message, no reload) or opens the app there; only same-site paths are used, and
  the app acts only on the digest's two routes (`DeepLinks`: the Pantry on Needs attention, Expiring soon before 2026-11-12, or Plan). After v1
  (2026-11-08) `safeRoute` reads the route as a URL on the app's origin, as the browser will, and keeps only its path
  and query when it stays on that origin; anything else opens `/`.
- **When it asks** (after v1, 2026-11-13, review UX-02, UX-03 and CODE-10 on the web): the same rule as 5.4,
  `NotificationPrompt` in `web/src/domain/notifications.ts` with the app's test cases, asked from the signed-in shell
  on whichever tab is open (`ui/notifications/reminders.tsx`), no longer from Pantry's Add item. A household that
  opens with dated food on this phone asks at once; a new one asks at its third dated item, whoever adds it; "opens
  with" is the first pantry read from the server in this run of the app. Where iOS differs, the web keeps its limits:
  it asks only while the system can still ask (`Notification.permission` is `default`), so Safari outside the Home
  Screen waits for the installed app, and a phone whose answer is already in (allowed, or refused: a web app can't
  show the question again) is marked explained without asking. There is no tour to wait for (18.1). The shell listens
  to the household's shared pantry listener only while the question may still come (not answered, the system can
  ask, the daily reminder on). The explanation names the time.
- **When this phone blocks reminders** (2026-11-13): once the explanation was answered, Needs attention ends with
  "Reminders are off on this phone · Turn on" while the permission is not given yet or refused and the daily reminder
  is on, in its full and compact forms; Safari outside the Home Screen has Home's Add to Home Screen card instead.
  Settings' Notifications row says **Off on this phone** (with a crossed-out bell) whenever this phone can't show
  reminders the member wants, Safari included. Turn on asks iOS while it still asks; otherwise it opens Settings >
  Notifications, whose banner says where to turn them on, since a web app can't open the iPhone's Settings.
- **You're set** (2026-11-13): right after a Turn on that works (the explanation, Home's line or Settings' banner),
  the phone shows the local "You're set" notification of 5.4 through the service worker
  (`registration.showNotification`, like Send test notification), with the time and tomorrow's items (`YoureSet`,
  ported with its tests). It opens Pantry on Needs attention, like a digest, and shows nothing while the daily reminder
  is off.
- **Settings > Notifications** has the app's settings and a banner when reminders cannot appear: not allowed yet
  (Turn on), turned off in the iPhone's Settings (where to turn them on; a web app cannot open them), or Safari
  outside the Home Screen (how to add it). Send test notification shows a local notification through the service
  worker.
- **Tests**: Playwright's WebKit has neither the Notification nor the Push API (like Safari outside the Home Screen),
  so the browser tests give the page both to play an installed app; the service worker's handling, the registration
  logic and the job's `webpush` part have unit and integration tests, and a real push to an iPhone is a QA flow.

**18.5 Offline, install and updates.** A service worker caches the app shell so it opens offline; a web manifest
gives the name, the launcher icon (rendered from the adaptive icon) and theme colors. A new version is fetched in the
background and offered with a snackbar for 10 s (Update, which reloads; otherwise it starts with the next launch).
The browser looks for one only when the app is loaded, and a Home Screen app can stay in memory for days, so the app
also asks (`workbox.update()`) whenever it becomes visible, at most once an hour (`UpdateChecks` in
`web/src/domain/updates.ts`). `minSupportedBuild` does not apply to the web app; its counterpart is
`config/app.minWebBuild` (8.2), compared with the build number `WEB_BUILD` in `web/src/core/version.ts`, which a change
raises by one when older web builds must not keep running (one that changes what the app writes or what stored data
means). Settings > About shows it with the commit ("build 1 (3f09a54)"). While the app is open in a household it
listens to `config/app`; when `WEB_BUILD` is older than `minWebBuild` it looks for the new version at once and at every
visit until one waits, and the Update snackbar then stays until Update is tapped (another message, such as an Undo,
shows first and the prompt comes back after it). A missing or unreadable document, or a field that is not a whole
number, never asks for an update. The owner sets `minWebBuild` after the web deploy (docs/RUNBOOK.md). Until then, or for
a build that did not raise `WEB_BUILD`, an app left open keeps running its version until it restarts or its hourly
check finds the new one, so a rules change that refuses what an older web build writes is deployed only after the
new web build has been out for a while (RUNBOOK). In Safari on an iPhone, the
welcome screen says how to install (Share, then Add to Home Screen) and Home shows a card about it until Not now; the
app opened from the Home Screen shows neither.

After v1 (2026-11-22, review EFF-06): the service worker precaches the app's code, styles, icons and the Latin and
Latin Extended font files, not `licenses.json` (read only by Settings > About) or the font subsets for Cyrillic,
Greek, Vietnamese and Hebrew, which the browser fetches only when a name needs them. It caches those when they are
first used instead (`web/src/core/runtimeCache.ts`): the licenses network first, so About shows them offline once read
under the service worker, and the font subsets cache first, since their names are hashed. The precache went from 21
entries, 1,615 KiB (550 kB gzipped) to 19 entries, 1,445 KiB (490 kB gzipped); `runtime-cache.spec.ts` (preview tests)
checks both halves.

**18.6 Hosting and release.** Firebase Hosting (free on the Spark plan): `spoilnot-dev.web.app` for testing and
`spoilnot-prod.web.app` for testers, whose sign-in domains Firebase authorizes by default. Each project needs a
registered Web app for its config (a human approves creating them). `scripts/deploy-web.sh dev|prod` reads that config
from Firebase and the VAPID key from the git-ignored `web/.env.<dev|prod>.local`, runs the checks and the browser
tests (with one retry, as CI), builds for the project, offers to deploy the rules, and deploys; prod only from a main
that matches GitHub and after asking, like the beta script.

- `firebase/firebase.json` serves the build from `firebase/hosting-dist` (a git-ignored copy of `web/dist` that the
  deploy script makes, because Firebase deploys only folders inside `firebase/`; without source maps) with every path falling back to `index.html`, and
  sends on every response: `Content-Security-Policy: default-src 'self'; script-src 'self'; style-src 'self';
  img-src 'self' data: blob: https:; font-src 'self'; connect-src 'self' https://*.googleapis.com; worker-src 'self';
  manifest-src 'self'; frame-src 'none'; object-src 'none'; base-uri 'self'; form-action 'self';
  frame-ancestors 'none'; upgrade-insecure-requests`, `X-Content-Type-Options: nosniff`, `Referrer-Policy:
  same-origin`, a `Permissions-Policy` that turns off camera, microphone, location, payment and USB, and
  `Cross-Origin-Opener-Policy: same-origin` (HSTS is Firebase Hosting's own). Pages, the service worker and
  `licenses.json` are `no-cache`; the hashed files in `/assets` are cached for a year.
- To fit the policy, Auth starts without the pop-up and redirect sign-in (no hidden iframe; email and password need
  neither) and fonts stay files instead of `data:` URLs. `img-src https:` is for recipe photos from their sites.
- Performance (W7, Lighthouse 13.6, mobile preset, the welcome screen of the build served by `vite preview` without
  compression, five runs): performance 92–93, accessibility 100, best practices 100; first contentful paint 0.8 s,
  largest contentful paint 3.2 s (the title, when its font arrives), total blocking time 0 ms, layout shift 0. The
  Firebase SDK is most of the first download; an installed app loads it from the service worker's cache.
- After v1 (2026-11-22, reviews EFF-06 and BASE-05): the code comes in chunks, so a release that changes only the
  app's code leaves the libraries cached. Firebase (Auth and Firestore with its re2js, 660 kB, 196 kB gzipped), React
  (219 kB, 68 kB), the other libraries (rxjs and the rest, 33 kB, 10 kB) and the app's own code (377 kB, 113 kB) each
  have one; Firebase Messaging still loads only when reminders are turned on. Before, one 1,289 kB chunk (387 kB
  gzipped) changed its name with every release, so every iPhone downloaded all of it again; now an app-only release
  downloads about 113 kB of code. The total is the same (the welcome screen still loads every chunk, since Firebase
  Auth decides what it shows); `chunkSizeWarningLimit` is 750 kB, just above the Firebase chunk, so a library update
  that grows it shows up in every build (DECISIONS 2026-11-22). Lighthouse on the same day, same Mac, the build from
  main and this one alternating, five runs each: performance 91 for main's build and 90 for this one, accessibility 100 for both; first contentful paint 0.8 s for both; largest contentful paint 3.5 and 3.7 s (the tagline, when the fonts arrive). Those are Lighthouse's simulated slow 4G, which charges each extra request; the load it watched was faster with the chunks (median first contentful paint 151 → 130 ms, largest contentful paint 205 → 183 ms, the fonts requested at 161 instead of 260 ms), since the browser fetches the four chunks side by side. The fonts' `@font-face` rules stay in the app's one stylesheet, as before: in a stylesheet of their own the first paint was 0.1 s later.

**18.7 Testing.** Vitest for the domain (ported cases) and the generators; React Testing Library for components;
Playwright with WebKit and an iPhone profile against the Firebase emulators, two browser contexts as two phones (like
`TestPhone`), two test files at a time (one emulator serves every browser), 60 s per test and 90 s for the long
flows (two phones in Pantry, recipes, the plan), and after v1 (2026-11-21) 30 s per click or fill, so a hung step
fails by name instead of running into the test's timeout; a tap right after a reload first waits for the Offline
banner (9.5) to go, since its going moves the screen (from 2026-11-21 `expectFromServer`, which also waits out the
banner's 2 s wait, marked by a hidden `offline_waiting`); the screenshots of 18.2; `a11y.spec.ts` (after v1, 2026-11-13) for 312% text, names, roles and headings (role
queries and ARIA snapshots), a sheet keeping focus, and Tab order with the focus ring, using Option-Tab, which reaches
every control as an iPhone keyboard's Full Keyboard Access does (Safari's plain Tab reaches only text fields). Development builds take a shorter cursor
margin for 9.8 from `localStorage` (`spoilnot.test.cursorMarginMs`, after v1), so `sync.spec.ts` waits seconds, not
5 minutes; production builds ignore it. CI runs format, lint, type check, unit tests, the build, a check that the
generated files are current, the Playwright tests, and the built app under the hosting headers
(`playwright.preview.config.ts`: `vite preview` with the headers of `firebase.json`, the service worker installed,
no policy violations, the pantry offline; and after v1 a changed service worker with `minWebBuild` above the build,
whose Update prompt must outlast 10 s and reload into it). The owner runs the web QA flows on a real iPhone before the first web
release.

**18.8 Milestones** (one PR each, merged when CI passes):

| | Milestone | Done when |
| --- | --- | --- |
| W0 | Foundation: `web/` scaffold, tooling and CI, generated colors and strings, fonts, icons, the component kit, the app shell with five tabs, Firebase setup (emulators in development), manifest and service worker | The shell matches the Android one in screenshots (light, dark, 200%) and runs in WebKit |
| W1 | Sign in and household: welcome, create account, sign in, password reset, the household gate (create, join, waiting, approve), Settings (account, household, sign out, delete account), household settings (members, invite code, join requests, remove, transfer, rename, new code, pause joining, leave, delete) | Two contexts form a household against the emulators |
| W2a | Pantry list: the writer, change plans and changes-only listeners (9.8); the pantry list with search, filters, sort and grouping, the expired header, swipe and menu actions (used up, throw away with a reason, one less, one more, move, mark opened), Undo | The pantry list flows pass on WebKit; a second phone follows |
| W2b | Add and edit, Home and Insights: Add item with quick dates and shelf-life learning, the Edit window, Out staples; Home with Needs attention, Low stock, This month and Recent activity (each card leads to the filtered Pantry or Insights), the join-request card; Insights | The add, edit and Home flows of docs/QA.md pass on WebKit |
| W3 | Groceries: lists (switch, new, rename, delete), quick add with suggestions and Already on the list, guessed aisles and the learned store order, check off and In cart, edit, swipe delete and Clear checked with Undo, Add to pantry, the restock engine with Back in stock; Home's grocery card; rows another phone changed are highlighted (6.4, on Pantry too) | The grocery flows pass on WebKit; a second phone's addition is highlighted |
| W4 | Recipes (view only, 18.1): the box (search, Favorites and tag chips, sort, favorite stars), detail with the servings stepper and checklist, Add to grocery list (shopping names, product links, Change), I cooked this, cook mode (screen kept awake), Edit, Share (the iOS share sheet), Delete with Undo, Home's Use it up card; the recipe picker's Use it up comes with Plan | The recipe flows that do not add recipes pass; a stored link opens only when it is http(s) |
| W5 | Plan: everything in 5.9 (the week from the phone region's first day, adding recipes or something else with cooks, servings and notes, Cooked with the pantry, Move, Copy last week, Clear this week, the week's ingredients to a grocery list, Use it up in the picker, Undo throughout), with Home's Today card | The plan flows pass on WebKit |
| W6a | Settings and extras: appearance (System, Light or Dark, the six themes), units, export, clear household data, About and licences, the app tour (removed in W6b, 18.1) | The settings and tour flows pass on WebKit |
| W6b | Notifications: Settings > Notifications, web push (the FCM web token as a `platform: web` device, the job's `webpush`) and the notification explainer of 5.4; the app tour removed | The notification flows pass on WebKit with the push APIs played by the test, the service worker's push handling and the job's `webpush` part are tested, and a push reaches an iPhone in QA (WebKit in Playwright has no Push API) |
| W7 | Release: hosting config, `scripts/deploy-web.sh`, install help, performance check (Lighthouse), docs | The owner's iPhone QA passes; deployed to prod with the owner's OK |

## 19. Visual overhaul (after v1)

**19.1 What it is.** A new look for every screen, chosen by the owner on 2026-11-14 from mockups ("Look A"): a tinted page, rows grouped into rounded blocks instead of outlined cards and dividers, one filled card per screen for what matters most, heavier titles, joined button groups, and a leaf shape taken from the app icon. The mockups, with every screen in light and dark, the states and a components sheet, are on the owner's design canvas (https://claude.ai/artifact/BtRJVW4ojnhaUNNkG3vfp9); where they and this section differ, this section wins.

It replaces 6.4's *Shape and surface* and 6.5's component patterns, one screen at a time (19.13). Until a screen is rebuilt it keeps what section 6 says. Everything else in section 6 holds: the principles (6.1), five tabs and Settings behind the avatar button (6.2), the fixed status colors and "never color alone" (6.4), Manrope, the color themes, motion, the states (6.7), the accessibility rules (6.8) and the copy rules (6.9). Features and data do not change, except the two additions in 19.12.

Colors are always Material 3 roles from the active theme, or the app's `StatusColors`, never fixed values, so the seven color themes and dark mode follow without extra work.

**19.2 Surfaces.** The page is tinted and rows sit one step lighter than it, so a group reads as one block without an outline. The Material roles differ in light and dark, because dark's `surfaceContainerLowest` is darker than the page. `AppSurfaces` (`SpoilNotSurfaces.current`, provided by `SpoilNotTheme`) names them:

| Surface | Used for | Light | Dark |
| --- | --- | --- | --- |
| `page` | Behind a screen's content | `surfaceContainerLow` | `surface` |
| `row` | List rows, plain cards, search and quick-add fields | `surfaceContainerLowest` | `surfaceContainer` |
| `nestedRow` | Rows inside a highlight card | `surfaceContainerLowest` | `surfaceContainerLow` |
| `field` | Text fields and pickers on a sheet | `surfaceContainerLowest` | `surfaceContainerHigh` |
| `tile` | The leading tile of a row without a status | `surfaceContainer` | `surfaceContainerHighest` |
| `dialogRow` | A dialog's choices (step 3) | `surfaceContainerLowest` | `surfaceContainerHighest` |

The navigation bar is `surfaceContainer`; sheets are `surfaceContainerLow` and dialogs `surfaceContainerHigh`, as Material has them. `ColorThemesContrastTest` checks text (4.5:1) on every one of these in every fixed theme, and that rows are lighter than the page, fields than a sheet and a dialog's choices than the dialog (in dark `field` is the dialog's own color, hence `dialogRow`).

**19.3 Shape.** `SpoilNotCorners`: a group of rows has 20 dp outside corners and 6 dp where two rows meet (18 dp outside inside a highlight card); plain and accent cards 24 dp; highlight cards, sheets and dialogs 28 dp; text fields 16 dp; label chips 8 dp; buttons, search fields and the chosen button of a group fully round. **The leaf** (`LeafShape`): two opposite corners fully round and the other two at 15%, at any size. It holds a recipe's letter when there is no photo, a step number, the account's initial and the icon of an accent card.

**19.4 Type.** Manrope, with ExtraBold added to the bundled weights. `SpoilNotText` holds the styles Material's scale lacks; body text and labels keep Material's styles. All are sp and follow the phone's text size.

| Style | Size / line | Weight | Used for |
| --- | --- | --- | --- |
| `ScreenTitle` | 30 / 40 | ExtraBold | A screen's title |
| `ScreenEyebrow` | 14 / 20 | SemiBold | The line above the title |
| `GroupHeading` | 14 / 20 | ExtraBold | The heading of a group of rows |
| `RowTitle` | 16 / 22 | Bold | A row's name |
| `RowSupporting` | 13 / 18 | SemiBold | A row's second line |
| `RowStatus` | 13 / 18 | ExtraBold | The status words in a row |
| `SectionTitle` | 18 / 24 | ExtraBold | A section on the page or a plain card's title, such as Home's Today |
| `CardTitle` | 22 / 28 | ExtraBold | A highlight card's title |
| `BigNumber` | 48 / 54 | ExtraBold | A card's one large number |
| `HeroNumber` | 64 / 70 | ExtraBold | The month's percentage on Your kitchen (step 7) |
| `AccentNumber` | 26 / 32 | ExtraBold | An accent card's number, such as the streak's days; the invite code (step 7) |
| `Wordmark` | 52 / 60 | SemiBold, Fredoka | The app's name on Welcome (step 8) |
| `ButtonLarge` | 16 / 24 | ExtraBold | Main buttons and the floating button |
| `ButtonMedium` | 14 / 20 | Bold | Tonal buttons and connected buttons |
| `ChipLabel` | 12 / 16 | Bold | Label chips |
| `SheetTitle` | 26 / 34 | ExtraBold | A sheet's title (step 3) |
| `DialogTitle` | 24 / 32 | ExtraBold | A dialog's title (step 3) |
| `FieldLabel` | 13 / 18 | Bold | The label above a field or a picker (step 3) |
| `FieldText` | 17 / 24 | SemiBold | What is typed in a field, a picker's value (step 3) |

**19.5 List groups.** Rows that belong together are one group (`ListSegment`, `segmentPosition`, `segmentShape`): each row is a `row` surface, at least 68 dp tall with two lines (64 dp with a checkbox, 56 dp with one line), 2 dp from the next (`ListGroupGap`), with the corners of 19.3 by its place in the group; a single row is rounded all round. No dividers and no outlines. A group may have a heading (`GroupHeading`: `primary`, sentence case, a heading for screen readers). A row that opens something is one control with its sentence (6.8); its ⋮ stays a separate 48 dp stop. From step 7 Settings' kind of row has its own pieces: `ActionRow` (an icon on a 40 dp `secondaryContainer` tile, `IconTile`, the name over its second line, a chevron when it opens a page; one button) and `SwitchRow` (the same with a switch at the end; one switch). Notifications' stages and expired mode are one-line rows with a checkbox or a radio button at the start, the whole row the control.

**19.6 Status.** A row starts with a **status tile** (`StatusTile`): 44 dp (40 dp inside a highlight card), 16 dp corners, the status icon of 6.4 in the status color, on that color at 12% over the row (`STATUS_TINT_ALPHA`). A row without a date gets a neutral tile (`tile`). The row says the status **in words** in its second line, in the status color (`RowStatus`), before the location. So status is still icon plus text and never color alone; the tile is decoration for screen readers, which get the row's sentence. This replaces the status chip on rows and brings back the leading icon that 2026-10-19 removed (DECISIONS). Swipe backgrounds keep their tint, icon and words.

**19.7 Screen header.** `ScreenHeader` replaces the top app bar: a small line (`ScreenEyebrow`, `onSurfaceVariant`) above the title (`ScreenTitle`), actions at the end level with the title, 20 dp from the start and 16 dp from the end. At 1.0× text it is 64 dp, the height of the compact bar it replaces (DECISIONS 2026-10-19 holds), it stays pinned, and it grows with the text. Groceries' title is its list switcher, a 48 dp button that takes only the title's line in the header, so its title and profile button sit where every other tab has them (owner, 2026-11-21: the taller button had put them 4 and 8 dp lower). The line says: Home, today's date ("Saturday, Nov 14"); Pantry, how many rows the unfiltered list has ("6 items"); Groceries, how many are left to buy ("4 to buy"); Recipes, how many recipes ("4 recipes"); Plan, "This week" while the current week shows. Each tab gets its line with its own step (19.13); Home's came with step 2. Pushed screens have a round back button (48 dp, `row` color) on its own line above the title; until their step they keep the small top app bar, on the page's color. From step 6 that is `PushedScaffold` (`BackButton`, `RoundIconButton`; the screen's actions at the end of the back button's line), first on the recipe editor and the import preview; a recipe and cook mode draw their own tops. Step 7 moves Settings, Household (its Rename a round button beside Back), Notifications, About and Your kitchen to it; Your kitchen has a line above its title, this month's name (`PushedScaffold`'s `eyebrow`). The Settings button is a 48 dp `secondaryContainer` circle with a person; the owner's join-request badge keeps its number to 1.2× text, as tab labels do, because Material's badge doesn't grow.

**19.8 Cards.** Three kinds. A **highlight card** (`HighlightCard`: `primaryContainer`, 28 dp corners, title in `CardTitle`) holds the one thing a screen wants read first: Home's Needs attention with its count in a badge, Plan's today, the account in Settings. Its rows are nested rows. Its count badge (`onPrimaryContainer`) isn't read by screen readers: the rows and *See all N* say it. When it has nothing to list it is a compact plain card with one line instead. An **accent card** (`AccentCard`: `tertiaryContainer`, 24 dp) suggests something, with a `LeafBadge` in the swapped colors: Use it up, the streak. **Your kitchen this month** is `primary` with `onPrimary` text: the percentage in `BigNumber`, the counts as a legend (a solid swatch for used, an outlined one for thrown away), and the ring at the end with the used arc in `primaryContainer` and the thrown-away arc in `onPrimary` dashes, so the two are told apart without color. Its title is `onPrimary` too: `primaryContainer` text is under 4.5:1 on four light themes' `primary`. The whole card is the control that opens Your kitchen, and TalkBack reads its title and the ring's sentence once; the friendly sentence shows only while nothing has been counted this month. Your kitchen's own screen starts with the same card (step 7), larger: the percentage in `HeroNumber`, the 112 dp ring, and the friendly sentence always under them; it isn't a control there. Other content sits on plain cards (`PlainCard` from step 7: `row`, 24 dp; a text field on one takes the page's color, step 8) or directly on the page: Your kitchen's months and reasons, Settings' Appearance, Household's invite. On Household the owner's waiting join requests are the highlight card, Approve filled and Decline tonal on each nested row.

**19.9 Buttons.** A screen's or sheet's main button is filled `primary`, fully round, 56 dp (60 dp on Welcome and Sign in, 48 dp inside a card), `ButtonLarge`. A second action is `TonalButton`: `secondaryContainer`, 48 dp. Text buttons are 44 dp, `primary`. Icon buttons are 48 dp and round, on the `row` color when they float over a header. **The floating button** is `primary` with `onPrimary` (it was `primaryContainer`), 64 dp tall, 22 dp corners, `ButtonLarge`, always extended. **A connected group** (`ConnectedButtonGroup`) is a single choice shown as joined buttons: 48 dp, 2 dp apart, outside corners fully round and joined ones 8 dp; the chosen button is fully round, `primary` with `onPrimary`, the others `surfaceContainerHigh` with `onSurfaceVariant`. It serves the theme and units choices, the quick dates and cook mode's Back, Ingredients, Next. Steppers join − and + to their value the same way. From 1.5× text a group is a list of radio buttons, as segmented buttons were (6.5; step 7 replaced their last users, Settings' theme and units, and removed `Segmented`); each button is a radio button for screen readers either way.

**19.10 Chips, fields and controls.** Filter chips are 44 dp, on the `row` color with 12 dp corners, and fully round in `primary` with a check when chosen; with nothing chosen none is filled (there is no All chip, DECISIONS 2026-10-19). Label chips (`LabelChip`) are 24 dp with 8 dp corners: `tertiaryContainer` for where an item came from, `secondaryContainer` for a person. Search and quick-add fields are 56 dp, fully round, on the `row` color. Text fields are 56 dp with 16 dp corners on the `field` color, their label above them; focus draws a 2 dp `primary` border, an error a 2 dp `error` border with the message and an icon under the field. Switches, checkboxes and radio buttons are Material's.

Built in step 3: `SpoilNotTextField` is a `BasicTextField` whose decoration holds the label, the box (`FieldBox`) and the message (`FieldMessage`), because Material 3 can put a label above its field only in the `TextFieldState` version. The label and the message are read with the field, and tapping the label focuses it. `FormTextField` and `EnumDropdown` (a read-only field with a menu) use it, so every form has it from step 3; the recipe editor's multi-line fields and Groceries' and Recipes' search and quick-add fields change in their steps (Groceries' with step 4; the editor's, `minLines` 4 and starting at the top, and Recipes' search with step 6). `SearchField` is the round search field, with the fields' focus border; `SpoilNotFilterChip` is Material's filter chip in these colors and shapes (only location chips and Recipes' Favorites, with its star, have an icon; in a dialog or on a sheet it takes the `dialogRow` or `field` color); a stepper is two `secondaryContainer` buttons joined to its value, 56 dp (`StepButton`, and `CountStepper` for a value that isn't typed; 48 dp beside a recipe's Ingredients).

**19.11 Bars, banners and overlays.** The navigation bar keeps its five labelled tabs; the indicator is `primaryContainer`. After v1 (2026-11-23) the bar also covers the stretch overscroll warm-up for two frames after the start (13.1); nothing of it shows. "Couldn't load." is a rounded banner (20 dp, `errorContainer`) and Offline a rounded banner (16 dp, `surfaceContainerHighest`), first in the screen's content; the daily-limit banner stays full width at the very top with 24 dp bottom corners. The snackbar (`SpoilNotSnackbarHost`, on every screen and the shell) is `inverseSurface` with 18 dp corners and Undo in `inversePrimary`; it shows above the floating button. Sheets and dialogs keep their behavior (6.5); a dialog's choices are a list group. From step 3 a form sheet's title is `SheetTitle`, 20 dp from its sides with 18 dp between fields; a dialog's title is `DialogTitle` and its choices are radio rows on `dialogRow`, 56 dp, with 18 dp outside corners.

**19.12 What changes besides the look.** Each is built with its screen (19.13).

- **Pantry, two groups.** In the default view (Sort: Soonest expiry, Group by: None, no search, no filter) the list has two groups: *Use first*, the Needs attention set of 5.4 (every purchase that has expired or expires within 3 days), and *Everything else*. The line that folds three or more expired purchases (5.2) is the first row of Use first. When nothing needs attention there are no headings. Any other sort, a Group by, a search or a filter shows the list as that choice orders it, without the two groups (built in step 3, `PantryBlocks`). Headings stay pinned while their rows scroll; outside the default view a folded expired line is a group of its own at the top.
- **Groceries** (built in step 4): no change besides the look; the line above the list's name counts what is left to buy.
- **Plan, a row of days** (built in step 5). Under the week switcher, seven buttons in the week's order (Week starts on): the weekday's short name over the day of the month, today's filled. A tap scrolls to that day's card (the last days of the week, which can't reach the top, as far as the list goes); with Remove animations on it jumps. Each is named in full for screen readers ("Saturday, Nov 14, today") and says what a tap does ("Show this day"). From 1.5× text the row scrolls sideways instead of shrinking. The switcher and the row stay above the days while they scroll, so any day is one tap away; *Go to this week* is a text button between them on other weeks, which have no line above the title. Today's card is the highlight card, its *Today* badge under the title from 1.5× text. A meal row is one control read as one sentence ("Dinner: Tacos, 4 servings, cooking: Sam, Cooked"), with **+** its own stop. The sheets follow 19.11: Add meal's *Something else* field has its label above, "Leftovers, eat out…" inside and a filled **+** at its end; its Use it up and recipe rows are groups on the `field` color under the round search field; the meal's servings are a stepper (`CountStepper`, which Mark opened now uses too); Cooked, Move and Open recipe are tonal buttons; Move's chips are on the dialog's row color. *This week's ingredients* and *What did you use?* are built from Recipes' pieces and change with step 6.
- **Home** (built in step 2). Needs attention is the highlight card with its count in a badge and its rows as nested rows with status tiles and the status in words. Today is a section on the page, *Open plan* beside its heading and one tile per meal, two side by side and one per line from 1.5× text. Your kitchen moves above Use it up and opens with a tap anywhere on it; it no longer has a *See your kitchen* button, and its friendly sentence moves to Your kitchen except while the month is empty. Use it up is an accent card whose recipes each open with a tap. The grocery list and Recent activity are plain cards.
- **Recipe** (built in step 6). Without a photo the header is a block with the recipe's letter in a leaf. **Cook mode** gets a back button and the recipe's name at the top, "Step 1 of 2" as a chip beside it, and the step's number in a leaf. Also in step 6: the box says how many recipes it holds above its title; its cards are plain cards with the photo or the letter in a leaf (120 dp, the leaf in the strong color of the block's container color, chosen by the title), the star in a round button over it; Favorites keeps its star. The recipe's hero (220 dp under the status bar) is overlapped by the page with 28 dp corners; Back, the star, Edit and ⋮ are round buttons that stay over it while the page scrolls. The facts are pills (`InfoPill`; the site opens the original), the servings stepper (48 dp) sits beside Ingredients, the checklist and the steps are groups of rows (the step numbers in leaves), Add to grocery list and I cooked this are tonal buttons side by side (one under the other from 1.5× text), and Start cooking is the main button in a bar under the content (`BottomActionBar`). Cook mode's step text is `headlineLarge` bold on a card, a bar per step stands for the progress bar (the chip says it), and Back, Ingredients and Next are joined, 60 dp, Next filled. The add sheet's three ways are a group of rows; Paste a link, Change and Already saved have the dialog style; Add to grocery list (and Plan's week's ingredients) shows its lines as a group of rows on the field color with Probably in pantry and On the list as label chips; I cooked this has its amounts in fields of the new look. The editor and the import preview are pushed screens (`PushedScaffold`: the round Back over the title); the preview shows its facts, ingredients and steps as the recipe will, with Save in a bar. The in-app browser has the round Close and Back around the page's round address field, and its found and not-found bars have rounded tops.
- **Settings** (built in step 7). The account is a highlight card with the name's initial in a leaf, the name and the email, and while the email isn't verified a nested row with *Resend email*. Household and Notifications are a group of `ActionRow`s with chevrons; Appearance is a plain card under its heading, Theme and Units as connected groups and the colors in a grid of up to four swatches (fewer as the text grows, so names wrap between words), the choice with a ring and a check on a dot of the page's text color; Data is a group under its heading, *Export household data* now the row itself (there was an Export button under it) and *Send crash reports* (it was with About) a `SwitchRow`; the tour and About are a group. Sign out is a full-width tonal button at the end, 56 dp; *Delete account* and the owner's *Clear household data*, which the mockup leaves out, stay under it as error-colored text buttons. Household, Notifications and About are pushed screens of the look: Household's members are a group of rows with their initials on tiles and its invite a plain card with the code large and Copy and Share tonal; Notifications' switches, stages and expired mode are groups of rows, *Send test notification* tonal and the blocked banner rounded; About's version is a row, privacy a plain card and the licenses on the page's color. Settings' dialogs (and the gate's, which share them) take `DialogTitle`.
- **Your kitchen** (built in step 7, with review batch B24). This month's name above the title; the month as Home's card, larger; the last six months with the chosen month on a `secondaryContainer` block and its details in a box of the page's color; the streak as an accent card with the days large; the reasons as striped, outlined bars on `tertiaryContainer`.
- **Sign in** (built in step 8). An error outlines its field and shows an icon beside the message: a wrong email or password on the password field, an email the server refuses on Email (on Create account also an email already in use, a weak password on Password), and the focus moves to that field so TalkBack reads it with its error. Any other failure (offline, too many tries) shows under the fields, and every form-level message (`ErrorText`) now has the error icon beside it. Welcome has the jar in a 208 dp leaf, the name in Fredoka (`Wordmark`, 52 sp), the tagline, and Create account and Sign in as 60 dp buttons at the bottom; Sign in, Create account and Reset password are pushed screens (`PushedScaffold`) with fields 18 dp apart. The household gate has the page's header ("Welcome, Sam", a round ⋮), Create and Join on plain cards (a field on a plain card takes the page's color, so it stands out in light) and the join request in flight as the highlight card; the tour's card takes the dialogs' surface and corners; Update required has its icon in a leaf, as Welcome has the jar.

**19.13 Order of work.** One change (pull request) per step, Android first; the iPhone web app (section 18) follows each step with the same look, generated from the Android sources as before.

| Step | What | State |
| --- | --- | --- |
| 1 | This section, and the shared pieces of 19.2 to 19.10 with their tests. No screen changes. | Done 2026-11-15 (Android); on the web the pieces arrive with the screens that use them (18.2) |
| 2 | The app shell (page color, header, navigation bar, floating button, banners, snackbar, main button) and Home | Done 2026-11-15 (Android), 2026-11-16 (web) |
| 3 | Pantry with Add item, its sheets and dialogs, and the two groups | Done 2026-11-16 (Android and web) |
| 4 | Groceries with Add to pantry | Done 2026-11-16 (Android and web) |
| 5 | Plan with the row of days | Done 2026-11-16 (Android and web) |
| 6 | Recipes, the recipe, cook mode, import | Done 2026-11-16 (Android), 2026-11-17 (web) |
| 7 | Your kitchen, Settings, Household, Notifications, About | Done 2026-11-17 (Android and web) |
| 8 | Welcome, Sign in, Sign up, the gate, the tour, Update required | Done 2026-11-17 (Android and web; the web has no tour and no Update required screen, 18.1). The baseline profile and the benchmarks followed the same day (13.1) |

Each step re-records the screenshots it changes (13.1), keeps `AccessibilityChecksTest` and the 2× text layouts passing, and adds its QA flows. After the last step: a new baseline profile and the benchmarks (RUNBOOK); done 2026-11-17, with the numbers in 13.1.

**19.14 Tests for the shared pieces.** `ListGroupTest` (a row's corners by its place), `ColorThemesContrastTest` (19.2's surfaces, status words and tiles on rows, the snackbar and banners, in every fixed theme), and `LookComponentsScreenshotTest` (all the pieces on one page, light and dark, text at 1.0 and 2.0; headings, a row as one control and the connected group's single choice). Step 3 adds `FieldsAndFiltersTest` (a field read with its label and message, tapping the label focuses it, its error, the search field's name and Clear, the filter chip's selected state) and the field, sheet and dialog pairs to `ColorThemesContrastTest`. Step 5 adds `PlanWeekTest` (a day button's full name and what a tap does, a tap scrolling to the day, the row scrolling sideways only from 1.5× text, "This week" and *Go to this week*, a meal read as one sentence with one + per slot, days as headings) and Plan's Cooked and Move's chips to `ColorThemesContrastTest`. Step 6 adds `RecipeLookTest` (cook mode's Back, the recipe's name as a heading and the step chip as the page turns, the step's Back named for what it does and greyed on the first step, Next greyed on the last; a recipe's step read with its number) and `RecipesTabScreenshotTest`; `RecipePagesScreenshotTest` draws the editor under `PushedScaffold` and adds the import preview. Step 8 adds `AuthErrorsTest` (a wrong password on the password field, focused, and said once; offline under the form; an email in use on Email). Step 7 adds `SettingsScreenshotTest` (the owner's page at its top and its end; its rows read as one button each, crash reports as one switch, the tour not as a checkbox) and `ConnectedButtonGroupTest` (joined buttons at 1.0× text, radio buttons at 2.0×, picking in both; it replaces `SegmentedTest`); Your kitchen's, Household's, Notifications' and About's screenshots draw the whole page with its header, and `ColorThemeScreenshotTest` checks the theme and units choices.

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
  web/                    the web app for iPhone (after v1, section 18): src/, scripts/ (generators), tests/e2e/
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
