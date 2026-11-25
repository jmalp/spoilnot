# 07 Accessibility

Reviewed `0334b6a` on 2026-11-02 (the branch `review/2026-11` at `47b822a`, which adds only review reports).
Platforms: Android first, then the web app for iPhone.

**Method.** DESIGN 6.4, 6.8 and 18.2, the two tests in `app/src/androidTest/.../a11y/`, `core/designsystem/theme`,
`web/src/theme` and the web kit were read first, then the earlier reports (findings already reported are cited by ID).
The dev debug build ran on Pixel_8 (Android 17) against the `demo-spoilnot` emulators with two made-up households
from 06's seed script: the lived-in Riveras (Sam, 150 purchases, 3 lists, 14 recipes, a planned week, Insights) and
an empty one (Morgan, added for this section), plus a new account (Casey) for the gate and the waiting screen.

- **Contrast** was scripted (Appendix B) from the web's generated tokens, which `npm run gen` builds from `Color.kt`,
  `PresetColors.kt` and `StatusColors.kt`, plus a Material You sample built with Material Color Utilities (tonal
  spot, the Android 12+ algorithm) from a blue wallpaper seed. 41 pairs per theme and mode: every text and icon pair
  found in the composables (`colorScheme.*` call sites and the Material 3 defaults of the components used), with
  alpha tints blended as Compose draws them.
- **TalkBack** is on the Pixel_8 image (no download needed). TalkBack was driven with the keyboard (Tab, Enter and
  its Alt+arrow shortcuts) and its "Display speech output" developer option, which prints each utterance on screen.
  Injected touches and swipes don't reach TalkBack's touch explorer, its Alt+arrow navigation stopped after one step,
  and any `uiautomator` connection suspends TalkBack, so the screen-by-screen audit used the accessibility tree that
  TalkBack reads (`uiautomator dump`, TalkBack off: text, description, role, state, bounds) on 40 screens, sheets,
  dialogs and menus, plus Compose semantics printed by a temporary test (Appendix A). What only a real screen reader
  shows is in "Not covered" and the phone checks.
- **Size and reach:** font scale 2.0 at the default display size and at the largest (540 dpi on this 1080 px wide
  screen), landscape, Tab and Enter from the emulator keyboard, and the reduce-motion code paths.
- **Automated coverage:** `AccessibilityChecksTest` was run again, and a temporary test ran the same Accessibility Test
  Framework (ATF) checks on 14 screens it never visits (Appendix A; removed after the run).
- **Web:** axe-core 4.10.3 (installed in the scratchpad, not in `web/package.json`) through the web's own Playwright
  and WebKit with the iPhone 15 profile, on 31 screens, sheets, dialogs and menus, six passes: light, dark, dark Fiery
  Palette, 200% text and the iPhone's AX3 (235%) and AX5 (312%) sizes (Appendix C).

At the end the Firebase emulators (stopped with SIGINT; the export they wrote was removed, so `.emulator-data` is
empty as it was), the web dev server and Pixel_8 were stopped. Font scale 1.0, density 420, rotation 0, TalkBack off
(its speech output option and notification permission put back), accessibility timeout unset; animation scales were
never changed. The dev app was reinstalled (the instrumented run uninstalls it) and cleared. The old prod build on
Pixel_8 was not touched.

## Summary

The groundwork is good: Pantry rows read as one sentence ("Chicken thighs, 2 lb, Expires today, Fridge"), statuses
always have a word and an icon, the Insights charts are described and use stripes and dashes as well as color, the
invite code is spelled out, dialogs and menus keep focus inside, reduce motion is respected, and the web passes axe
on 26 of 31 screens in light mode and 29 in dark (three rules in all). The gaps are concentrated in a few places.
**The four extended floating buttons on Android have no name** (Add item, Add N to pantry, Add recipe, Cook):
Material 3 takes the name from the icon, the app gives the icon none, so TalkBack says only "Button" on the main
action of three tabs (A11Y-01, P1). This is also why 06 saw the
button "missing" from `uiautomator` dumps. **Undo disappears after a fixed 8 seconds** on both platforms, whatever the
screen reader or the phone's "Time to take action" setting (A11Y-02). **Status chips miss 4.5:1 in light mode** in
every theme because of the tint behind them, which the contrast test leaves out (A11Y-03). **At large text Home
breaks names mid-word and at the largest display size hides them** (A11Y-04). `AccessibilityChecksTest` checks 5 of 35
screens, opens no sheet, and passes with the unlabeled button on screen (A11Y-05). 12 findings: 1 P1, 3 P2, 8 P3. No
item had a security angle; nothing moved to the private notes.

## Metrics

### Totals

| Metric | Value | How measured | Target or budget |
| --- | --- | --- | --- |
| Contrast failures, text (4.5:1) | **33 of 392** text pairs, all in light mode: 4 per theme in Spoil Not green and the Wallpaper sample, 5 per preset; **0 in dark mode** | Appendix B, 7 themes × 2 modes × 28 text pairs | 0 (DESIGN 6.4, 6.8) |
| Contrast failures, non-text (3:1) | **42 of 182**, the same 3 in every theme and mode: unselected tile and chip borders, the selected tab's pill. Each control is also told apart by text, a border width or a filled icon, so none is a WCAG failure on its own | Same | — |
| Unlabeled controls | **Android 4**: the extended floating buttons on Pantry, Groceries, Recipes and recipe detail (A11Y-01). **Web 0** (axe `button-name` and `link-name` pass everywhere); 1 unnamed progress bar and 1 empty heading on the web (A11Y-12) | `uiautomator` audit of 40 Android screens; Compose semantics; axe | 0 |
| Weakly labeled controls | **Android 5 kinds**: I cooked this amounts labeled only by their unit, Approve and Decline without the person's name, two "Back" buttons in cook mode, a favorite toggle whose label flips, and the settings badge read twice (A11Y-07, A11Y-08) | Same | — |
| Swipe-only actions | **0**. Pantry's two swipes (Used up, Throw away) are in the row's ⋮ menu, Groceries' swipe (delete) is ⋮ Delete; there are no long presses or drags. No custom accessibility actions anywhere, so a TalkBack user takes the menu (3 steps instead of one action) | `grep` for gestures; menus checked | DESIGN 6.8: every swipe also in a menu, **met** |
| Touch targets under 48 dp | **Android 1 kind on every bottom sheet**: Material 3's drag handle, 32 × 48 dp (ATF fails 6 of 6 sheets checked, A11Y-06). Everything else is 48 dp or more. **Web 0** (axe `target-size` hits were all elements covered by an open menu or a loading grid) | `uiautomator` bounds, ATF, axe | 48 dp (DESIGN 6.8) |
| Screens that clip at 200% | **Android:** the tab bar on every tab (UX-14) and Home's Needs attention rows (names break mid-word, A11Y-04; Low stock rows share the layout); at the largest display size also Home's join card (one syllable per line), the names on Home (gone) and Pantry's status chip ("Tomorro / w"). The floating buttons cover rows on Pantry, Groceries, Recipes and recipe detail (UX-14). **Web:** at 200% the same Home rows; at AX5 also the tab labels ("Ho…"), Home's Review button and chips past the card edge | Screenshots at 2.0 (420 and 540 dpi) and in WebKit at 2.0, 2.35 and 3.12 | Nothing cut off (QA.md) |
| Screens covered by automated checks | **Android 5 of 35** (`AccessibilityChecksTest`: Home, Pantry, recipe detail, Groceries, Settings), **18 of 35** with the temporary test's 13 (A11Y-05). **Web 31 of 31** visited by axe here, **0** in the repository's own tests | Reading the tests; Appendix A; Appendix C | — |

The 35 Android screens and sheets counted: Welcome, Sign in, Sign up, Reset password, Household gate, Waiting for
approval, Update required, Home, Pantry, Plan, Recipes, Groceries, Add item, Edit item, Mark opened, Throw away, Move,
Add meal, Meal sheet, Move meal, This week's ingredients, Recipe detail, Cook mode, I cooked this, Add to grocery
list, Recipe edit, Import (paste and preview), Browser, Add to pantry, Edit grocery item, Settings, Household,
Notifications, About, Insights (the tour overlay comes on top).

### Contrast by theme and mode

41 pairs per row (28 text at 4.5:1, 13 non-text at 3:1). The Wallpaper row is a sample (seed `#3F6FB5`); the real
Material You colors on a phone differ, but every failure in it is one the fixed themes share.

| Theme | Light: text | Light: non-text | Dark: text | Dark: non-text |
| --- | --- | --- | --- | --- |
| Spoil Not green | 4 | 3 | 0 | 3 |
| Wallpaper (Material You sample) | 4 | 3 | 0 | 3 |
| Fiery Palette | 5 | 3 | 0 | 3 |
| Summer Fun | 5 | 3 | 0 | 3 |
| Enchanted Forest | 5 | 3 | 0 | 3 |
| Watermelon Sorbet | 5 | 3 | 0 | 3 |
| Contrast Pop | 5 | 3 | 0 | 3 |

Every pair below WCAG AA:

| Pair | Where | Light | Dark |
| --- | --- | --- | --- |
| Fresh status text on its 12% tint | Status chip (a date), Pantry and Home | 4.18 (green, Wallpaper); 3.97–3.98 (presets) | passes (all chips ≥ 6.6) |
| Soon (2–3 days) text on its tint | Status chip | 4.13–4.15; 3.92–3.96 | passes |
| Today/Tomorrow text on its tint | Status chip | 4.16; 3.94–3.98 | passes |
| Used up label on its 16% tint | Pantry swipe right (seen while swiping) | 3.96; 3.77–3.79 | passes |
| Throw away label on its 16% tint | Pantry swipe left | passes (4.68) on green and Wallpaper; 4.42–4.49 on presets | passes |
| Unselected tile and place borders (`outlineVariant`) | Add item and Edit: date tiles, Location tiles | 1.46–1.54 | 1.83–1.85 |
| Unselected filter chip border (Material 3 default) | Filter rows, cook chips | 1.53–1.62 | 1.98–1.99 |
| Selected tab pill on the bar (`secondaryContainer` on `surfaceContainer`) | Navigation bar | 1.11 (green), 1.38–1.39 | 1.74–2.11 |

Everything else passes in every theme and mode: body and secondary text on surfaces, sheets and dialogs, text and
filled buttons, the extended floating buttons, chips and tonal cards, error text and cards, snackbar text and Undo,
Expired and No date chips (≥ 4.75 and ≥ 6.9), field outlines (`outline`, ≥ 3.85), icons and both chart colors (≥ 5.0
on surface and on the track). The non-text failures don't make a control unidentifiable: tiles and chips are
identified by their text, the selected tile adds a 2 dp `primary` border, a fill and bold text, and the selected tab
has a filled icon. They are listed because 6.8 asks for every pair; they are an Idea, not a finding. **UX-17
measured:** dark Fiery Palette's `primary` (`#FFB693`) is ΔE 13 from the Expired color `#FFB4AB`, dark Watermelon
Sorbet's (`#FFB2BC`) ΔE 10; the other themes' buttons are ΔE 56 to 70 from it.

### Not color alone

| Signal | Besides color | Verdict |
| --- | --- | --- |
| Expiry status (Fresh, Soon, Today/Tomorrow, Expired, No date) | Icon and word on every chip; full sentence for TalkBack | Met. One icon misleads: **Past best before shows the trash icon** ("Check before using", with a bin), the Expired icon, `PantryLabels.kt` `icon()`; part of UX-04 |
| Low, Out, Back in stock, source badges | Words, each source its own icon | Met |
| Pending sync | Cloud-off icon with a description | Met |
| Grocery checked | Checkbox, strike-through, its own In cart section | Met |
| Selected tab, chip, tile, list | Filled icon; check icon; bold text and thick border; radio state | Met |
| Insights: used and thrown away | Solid versus striped bars, solid versus dashed ring, legend with the pattern | Met |
| Cooked, cooks | Check icon and word; person icon and name | Met |
| Join request badge on the avatar | Count and the description "1 join request waiting" | Met |

### TalkBack and semantics, by screen

Labels, roles and states from the tree TalkBack reads; reading order checked where it was driven live.

| Screen | Labels | Roles and states | One sentence | Notes |
| --- | --- | --- | --- | --- |
| Welcome, Sign in, Sign up, Reset | All | Buttons, edit boxes with labels and helper text | — | Clean |
| Household gate, join dialog, Waiting | All | Fine | — | Continue sits under the keyboard (UX-13) |
| Home | All | Buttons; settings badge read twice ("…1 join request waiting. 1") | **No**: each Needs attention row is two stops (name, then "Expired 9 days ago. Throw it away"), each Today meal three (slot, meal, cooks) | A11Y-07, A11Y-08 |
| Pantry | **Add item button unnamed** | Filter chips are checkboxes with state; ⋮ "Options for this batch" | **Yes** ("Milk, 1 piece, Expires today, Fridge"); Out rows "Olive oil, out of stock" | Group headers aren't headings (A11Y-09) |
| Add item, Edit item | All fields labeled | Date tiles are checkboxes though one at a time; Location tiles radio buttons; auto-add a switch | — | Drag handle 32 dp (A11Y-06) |
| Row menu, Throw away, Mark opened | All | Menu and dialog in their own windows: focus stays inside | — | TalkBack read the dialog title first |
| Plan | All ("Add to Breakfast, Monday, Nov 2") | Meals are buttons | Meal: "Overnight oats, 1 serving, You" (day and slot are the stops before it) | Day headings are headings |
| Add meal, Meal sheet, This week's ingredients | All | List picker chips are checkboxes though one list at a time | Ingredient rows: "1/2 cup oats, Adds as Oats × 800 g, Probably in pantry" | "Change what 1/2 cup oats is added as" is good |
| Recipes | **Add recipe button unnamed** | Favorite: "Remove from favorites, checkbox, checked" | Card: "S, Spinach and feta omelette, Total 15 min" (the placeholder letter first) | A11Y-07, A11Y-08 |
| Recipe detail, Cook mode | **Cook button unnamed** | Ingredient checklist with state; two "Back" buttons in cook mode (app bar and previous step) | — | Favorite here is a plain button, not a toggle |
| I cooked this, Add to grocery list | Amount fields labeled by unit only ("1, kg") | Checkboxes with state | Rows: "Flour, You have 1 kg, 8 small flour tortillas" | A11Y-07 |
| Groceries | **Add N to pantry unnamed** | Rows are checkboxes with state; Remove (back in stock) a button | Row: "Bananas, × 6" ("times 6", no unit) | Aisle headers aren't headings |
| Add to pantry, Edit grocery item | All | Fine | "Apples, 6 · Fridge · Nov 23" | — |
| Settings | All | **Take the app tour and About say "not checked"**; theme and mode radios with state | — | Sections aren't headings |
| Household, Notifications, About | All; invite code spelled out ("R V R S K - 7 M 4 P Q") | Approve and Decline without the name | — | Clean otherwise |
| Insights | All, charts described ("June: 18 used, 3 thrown away"; "This month: 4 used, 3 thrown away, 57% used") | Month bars selectable, details in a live region | — | The ring's numbers are also read again as text |
| App tour | Card has a pane title and live region | **Screen behind stays reachable** | — | A11Y-10 |

Live with TalkBack: Tab and Enter work everywhere they were tried and TalkBack speaks each stop ("Pantry. Tab. 2 of
5", "Review. Button", "Not selected. Other. Check box"); the Throw away dialog took focus on its title; TalkBack's own
Alt+arrow and touch exploration couldn't be driven (Not covered).

### Hand-offs from 06

- **Pantry's Add item button and card buttons missing from `uiautomator` dumps:** they are always in the tree, but
  the floating button has no text and no description (`uiautomator` marks it `NAF`, "not accessibility friendly"), so a
  lookup by label can't find it. TalkBack would say "Button" (A11Y-01). The card buttons were present and labeled in
  every dump here; their absence in 06's runs was most likely the dump timing out during the loading skeleton (a
  dump took up to 30 s while the shimmer or a snackbar animated).
- **"Past best before" uses the trash icon:** confirmed (Not color alone table); it belongs to UX-04's decision.
- **UX-14 with TalkBack and at the largest display size:** TalkBack reads the tabs correctly ("Groceries. Tab. 5 of
  5"), since the text is whole; the break is visual only. At the largest display size it gets worse ("Hom / e",
  "Groc / erie / s") and Home's join card wraps one syllable per line (A11Y-04). The floating buttons cover the last
  row's ⋮ and amount on Pantry and Groceries at every size; a keyboard user tabbing to that ⋮ will likely have it under
  the button (A11Y-11).

### Web

| Pass | axe violations (rule: screens) | Text cut off (beyond long names, which ellipsize by design) |
| --- | --- | --- |
| Light, 100% | `color-contrast`: Pantry and its row and sort menus (the status chips behind them: Tomorrow 4.16, 2 days 4.13); `empty-heading`: recipe detail; `aria-progressbar-name`: cook mode | — |
| Dark, 100% | `empty-heading`, `aria-progressbar-name` | — |
| Dark, Fiery Palette | `empty-heading`, `aria-progressbar-name` | — |
| Light, 200% | `empty-heading`, `aria-progressbar-name` | Home's Needs attention names ("Tortil / la…"), tab labels "Recipes", "Groceries" clipped, recipe titles |
| Light, AX3 (235%) | Same two | Every tab label clipped, Plan and Settings titles |
| Light, AX5 (312%) | Same two | Tab labels "Ho…", "Pa…"; Home's Review button text runs out of the button; the Past best before chip runs past the card; "Search pant" |

`target-size` was also reported on the tab bar and the Show button, each time while a menu or the loading grid lay
over them (not reproducible on a settled screen). The kit does well: sheets and dialogs are native `<dialog>`s
opened with `showModal()` (focus stays inside, Escape closes), the snackbar host is `aria-live="polite"` with
`role="status"`, chips use `aria-pressed`, segmented buttons are a `radiogroup`, menus `menu`/`menuitem`, icon
buttons carry `aria-label`, decorative icons are `aria-hidden`, list group headers are headings, and the floating
buttons carry their text (so A11Y-01 is Android only). The snackbar has the same fixed 8 s (A11Y-02).

**`textScale.ts` at the iPhone's sizes** (`-apple-system-body` in px; values in CSS px):

| iPhone size | Body px | Scale | 11 sp | 12 sp | 14 sp | 16 sp | 22 sp | 24 sp | 28 sp | 57 sp |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Default (Large) | 17 | 1.00 | 11.0 | 12.0 | 14.0 | 16.0 | 22.0 | 24.0 | 28.0 | 57.0 |
| xxxLarge | 23 | 1.35 | 14.9 | 16.2 | 19.6 | 20.9 | 25.5 | 26.8 | 28.9 | 57.0 |
| AX1 | 28 | 1.65 | 18.1 | 19.8 | 23.2 | 24.5 | 29.4 | 30.4 | 31.7 | 58.4 |
| AX2 | 33 | 1.94 | 21.4 | 23.3 | 25.5 | 27.4 | 34.1 | 35.1 | 36.4 | 61.3 |
| AX3 | 40 | 2.35 | 25.9 | 28.2 | 30.6 | 32.9 | 41.2 | 42.4 | 43.9 | 72.8 |
| AX4 | 47 | 2.76 | 30.4 | 33.2 | 35.9 | 38.7 | 48.4 | 49.8 | 51.6 | 85.6 |
| AX5 | 53 | 3.12 | 34.3 | 37.4 | 40.5 | 43.6 | 54.6 | 56.1 | 58.2 | 96.5 |

The table matches DESIGN 18.2: non-linear up to 200%, then the 200% table times the scale. Body text (16 sp) at AX5
is 43.6 px, 82% of iOS's own 53 px, which is reasonable; but past 200% headlines grow as fast as body text again
(22 sp reaches 54.6 px), and no test or screenshot covers any size above 200% (A11Y-12).

## Findings

### A11Y-01 · P1 · Confirmed · Android — The four extended floating buttons have no name: TalkBack says only "Button"

- **Where:** `feature/pantry/ui/PantryScreen.kt:133-139` (Add item), `feature/grocery/ui/GroceriesScreen.kt:135-140`
  (Add N to pantry), `feature/recipes/ui/RecipesScreen.kt:112-118` (Add recipe),
  `feature/recipes/ui/RecipeDetailScreen.kt:118-123` (Cook). Each passes `contentDescription = null` to the icon.
  Material 3 1.4.0's `ExtendedFloatingActionButton(text, icon, …)` wraps the text in
  `Row(Modifier.clearAndSetSemantics {})` (`FloatingActionButton.kt:425`); its KDoc says the accessible name "is
  extended from the extended fab's icon".
- **What happens:** the button's node has a role and a click action and nothing else. On Pantry with food in it, the
  only way to add an item is a control TalkBack announces as "Button"; the same after shopping ("Add 3 to pantry"), on
  Recipes and on a recipe ("Cook"). Voice Access commands by name ("Tap Add item") rely on the same missing name, and
  Switch Access shows a nameless item. This is also why 06's label-based dumps "lost" the button.
- **Evidence:** `assets/07/fab-unlabeled.png`. The `uiautomator` node (`NAF="true"`, `text=""`, `content-desc=""`,
  with an empty child where "Add item" is drawn) and the Compose semantics printed by the temporary test:

  ```text
  Node #240 at (l=696.0, t=1938.0, r=1038.0, b=2085.0)px, Tag: 'fab_add_item'
  Focused = 'false'
  Role = 'Button'
  Actions = [OnClick, RequestFocus]
  MergeDescendants = 'true'
  ```

  `fab_add_recipe`, `recipe_cook` and `fab_add_to_pantry` print the same. `AccessibilityChecksTest` passed in the same
  run with this button on its Pantry screen (A11Y-05).
- **Who it affects:** everyone using TalkBack, Voice Access or Switch Access, on the main action of three tabs, every
  day.
- **Suggested fix:** give each icon the button's text as its `contentDescription` (Material's documented way), or
  `Modifier.semantics { contentDescription = text }` on the button; a small `SpoilNotFab(text, icon, onClick)` wrapper
  keeps it from coming back. Add an assertion that every node with a click action has a name to the checks (A11Y-05).
  Effort S. Also needs: a test (`onNodeWithTag("fab_add_item").assert(hasContentDescription("Add item"))` or the
  general rule); no web port (the web's buttons carry their text).

### A11Y-02 · P2 · Confirmed (code) · Likely (TalkBack timing) · Android + web — Undo disappears after a fixed 8 seconds, whatever the screen reader or the phone's timing setting

- **Where:** `core/ui/MessageEffect.kt:15` and `:28-35` (`SnackbarDuration.Indefinite` inside
  `withTimeoutOrNull(UNDO_WINDOW_MS)`, then `dismiss()`); `web/src/ui/kit/Snackbar.tsx:6`, `:50`, `:60` (a plain
  `setTimeout`, not paused on focus or hover). Material 3's own `SnackbarHost` would have stretched a timed snackbar
  through `AccessibilityManager.calculateRecommendedTimeoutMillis` (`SnackbarHost.kt:305-312`), which follows the
  phone's Accessibility > "Time to take action" setting and the timeout accessibility services ask for (the
  accessibility manager reported 10 s with TalkBack on); the app's `Indefinite` plus its own timer skips that.
- - **What happens:** Material announces the snackbar (live region and pane title), so a TalkBack user hears "Milk
  used up" and that there is an Undo, but focus stays where it was. To undo they must move to the snackbar at the
  bottom of the screen and double-tap within 8 s from the moment it appeared. From a Pantry row that is about 10 to 20
  swipes; reaching it in time is unlikely. With "Time to take action" set to 2 minutes the snackbar was gone by the
  next screenshot, about 15 s after the action (06 timed the window at about 8 s). On the web, VoiceOver users have
  the same 8 s and the timer runs on while the Undo button has focus. Every destructive action in the app relies on
  this Undo instead of a confirmation (DESIGN 6.1), and mis-activation is more common with a screen reader or switch.
- **Evidence:** the code above; Material's source; the setting test on Pixel_8
  (`settings put secure accessibility_interactive_ui_timeout_ms 120000`, Used up on Milk, snackbar gone well before
  2 minutes). *Likely* for the swipe count: TalkBack's swipe navigation couldn't be driven here.
- **Who it affects:** screen reader, switch and keyboard users, and anyone who set a longer action time; every
  destructive action (Used up, Throw away, deletes, Clear checked, Delete recipe, Clear this week).
- **Suggested fix:** Android: a timed duration through `calculateRecommendedTimeoutMillis(8_000, containsIcons =
  false, containsText = true, containsControls = true)` instead of a fixed timer (8 s stays the default for everyone
  else, DESIGN 5.2). Web: pause the timer while the snackbar has focus or hover, and give Undo a keyboard shortcut or
  a longer window. Effort S. Also needs: web port, DESIGN 5.2 (the window "at least 8 s, longer with the accessibility
  timeout"), a unit test with a fake timeout.

### A11Y-03 · P2 · Confirmed · Android + web — Status chip text is under 4.5:1 in light mode in every theme

- **Where:** `feature/pantry/ui/PantryLabels.kt:135` (`color.copy(alpha = 0.12f)` behind the chip's text),
  `feature/pantry/ui/PantryComponents.kt:327` (swipe labels on 16%), `web/src/ui/pantry/pantry.css:5-16`;
  `app/src/test/.../theme/ColorThemesContrastTest.kt` checks the status colors on `surface` and
  `surfaceContainerLowest` only, never on the tint they actually sit on.
- **What happens:** the chip's tint darkens its own background, so Fresh, Soon and Today/Tomorrow text drop to 4.13–
  4.18:1 on Spoil Not green and Wallpaper and 3.92–3.98:1 on the five presets (their light surfaces are two tones
  deeper). Expired and No date pass (5.0 and 7.3). Dark mode passes everywhere. The swipe labels are lower still (Used
  up 3.8–4.0:1). The status is also an icon and a word, so nothing depends on color, but the word itself is the part
  people read, at 14 sp.
- **Evidence:** Appendix B; `assets/07/status-chip-contrast-light.png`. axe in WebKit reports the same two chips on
  the web (`color-contrast`, "4.16 (foreground #c2410c, background #efe5d9)", "4.13 (#a05f00 on #ebe8d8)").
- **Who it affects:** everyone in light mode (the default), most for low vision and in bright light, on the app's most
  important text.
- **Suggested fix:** compute the check on the blended chip background in `ColorThemesContrastTest` (and drop the
  separate `StatusColorsContrastTest`, CODE-11), then either darken the three light tones about 10% (for example Soon
  `#8F5500`, Today `#B03A0A`, Fresh `#28702C`, to be verified by the test on every preset) or lower the tint to 6–8%.
  Effort S. Also needs: `npm run gen` for the web, DESIGN 6.4's status table, Roborazzi images.

### A11Y-04 · P2 · Confirmed · Android + web — At 200% text Home breaks names mid-word, and at the largest display size hides them

- **Where:** `feature/home/ui/HomeScreen.kt:378-388` (`AttentionRow`: the name is `weight(1f)`, `maxLines = 2`, and
  the chip takes its full width first), `:391-402` (`LowStockRow`, same layout), `:500-521` (`JoinRequestsCard`: text
  and button side by side); `PantryLabels.kt` `StatusChip` (one line inside a fixed-width pill); the web's
  `ui/home` with the same layout.
- **What happens:** at font scale 2.0 the Needs attention names wrap mid-word and are cut ("Tortill / a chi…",
  "Romaine" on its own line). At the largest display size with 200% text, "Tortilla chips" gets 5 px of width and
  disappears, so the row shows only "Past best before" with no food; "Romain / e" breaks; the join card reads "2 /
  peo / ple / wan / t to / join"; Pantry's chip reads "Tomorro / w". TalkBack still reads the names, so this is about
  people who rely on large text, not screen reader users. The web shows the same rows at 200% ("Tortil / la…"), and at
  AX5 Home's Review label runs out of its button and the chip runs past the card.
- **Evidence:** `assets/07/home-large-text.png` (Android, dark Fiery Palette, 200% at 420 and 540 dpi; the dump puts
  "Tortilla chips" at `[108,1075][113,1359]`), `assets/07/web-ax5.png`.
- **Who it affects:** people who use the largest text or display size, on the first card of the first screen.
- **Suggested fix:** at font scale 1.5 and above put the chip under the name (as Pantry rows already do with a
  `FlowRow`), and stack the join card's button under its text; never let a name go below a minimum width. Add Home at
  2.0 to the Roborazzi tests (CODE-07 lists the gap) and a web screenshot at 3.0. Effort S. Also needs: web port.

### A11Y-05 · P3 · Confirmed · Android — `AccessibilityChecksTest` checks 5 of 35 screens, opens no sheet or dialog, and passes with an unlabeled button on screen

- **Where:** `app/src/androidTest/kotlin/com/parcha/spoilnot/a11y/AccessibilityChecksTest.kt:150-174`.
- **What happens:** the test visits Home, Pantry, a recipe, Groceries and Settings. It never opens a sheet, dialog or
  menu, nor Plan, Recipes, Insights or the signed-out screens. Run again here, it passed while Pantry showed the
  nameless Add item button (A11Y-01): the Accessibility Test Framework did not flag that Compose node. The temporary
  test (Appendix A) ran the same checks on 13 more screens and the row menu: 8 passed (row menu, Throw away, Plan,
  Recipes, Cook mode, Recipe edit, Household, Notifications) and 6 failed, every sheet on the drag handle (A11Y-06).
  Two steps didn't reach their screen (Add meal, Insights), so those two were only audited by hand.
- **Evidence:** the run's log (Appendix A's results); the JUnit report for both classes (2 tests, 0 failures).
- **Who it affects:** future changes: the label and size checks give a false sense of coverage. CODE-12 (needs API
  34) applies too.
- **Suggested fix:** extend the test to the 13 screens (Appendix A is ready to adapt: make it fail instead of log), and
  add a semantics rule of its own that ATF misses: every node with `OnClick` or `ToggleableState` has text or a content
  description. Effort S to M. Also needs: CODE-12's `@SdkSuppress`, QA.md.

### A11Y-06 · P3 · Confirmed · Android — Every bottom sheet's drag handle is a 32 dp wide touch target

- **Where:** every `ModalBottomSheet` with Material's default `BottomSheetDefaults.DragHandle()` (Add item, Edit item,
  Mark opened, I cooked this, Add to grocery list, Add to pantry, Add meal, Meal sheet, This week's ingredients, Edit
  grocery item).
- **What happens:** the handle is exposed as a long-clickable node (expand, collapse, dismiss) of 32 × 48 dp, under
  6.8's 48 dp. ATF fails all 6 sheets the temporary test opened ("This item's width is 32dp", `TouchTargetSizeCheck`).
  The sheets also close by Back or a tap outside, so nobody is stuck.
- **Evidence:** Appendix A's log; `uiautomator` bounds `[498,480][582,607]` on Pixel_8.
- **Who it affects:** people with motor limits who drag sheets by the handle; anyone extending the automated checks
  to sheets (they would fail on this first).
- **Suggested fix:** pass a `dragHandle` that pads Material's handle to 48 dp (`Box(Modifier.sizeIn(minWidth = 48.dp,
  minHeight = 48.dp)) { BottomSheetDefaults.DragHandle() }`) in the app's sheet wrapper. Effort S.

### A11Y-07 · P3 · Confirmed · Android — Several rows and cards don't read as one sentence

- **Where and what** (DESIGN 6.8: "Milk, 2, expires tomorrow, fridge"; Pantry rows do this right):

| Place | TalkBack reads | Suggested |
| --- | --- | --- |
| Home, Needs attention (`HomeScreen.kt:378`) | "Tortilla chips", then "Past its best before date. Check before using" (two stops per row) | One merged row: "Tortilla chips, past its best before date, check before using" |
| Home, Today (`HomeScreen.kt` Today card) | "Breakfast", "Overnight oats", "You" (three stops) | "Breakfast: Overnight oats, you're cooking" |
| Groceries rows (`GroceriesScreen.kt:327`) | "Bananas, × 6" ("times 6"; no unit; "Low stock" and "Back in stock" as bare words) | "Bananas, 6 pieces, added because you're low", as Pantry's `amountDescription` does |
| Add to pantry rows | "Apples, 6 · Fridge · Nov 23" | "Apples, 6 pieces, fridge, use by Nov 23" |
| Recipe cards (`RecipeComponents.kt`) | "S, Spinach and feta omelette, Total 15 min" | Hide the placeholder letter from accessibility |
| I cooked this (`CookedSheet.kt:148-151`) | Each amount field: "1, edit box, kg", read before its own ingredient row (the field sits higher) | Label the field "Amount of Flour to take, kg" and merge the row |
| Insights' month card | The ring's description, then "Used 4", "Thrown away 3" again | Hide the two duplicate lines from accessibility, or the ring's description |

- **Evidence:** the `uiautomator` trees (Metrics table).
- **Who it affects:** TalkBack users, on Home every visit and on Groceries in the shop.
- **Suggested fix:** `Modifier.semantics(mergeDescendants = true) { contentDescription = … }` per row with a
  `row_description`-style string; `clearAndSetSemantics {}` on the placeholder letter. Effort S. Also needs: web port
  (the web reads its rows the same way), strings.

### A11Y-08 · P3 · Confirmed · Android — Some roles and states say the wrong thing

- **Where and what:**
  - Date tiles in Add item and Edit (`PantryForms.kt:602`, `Role.Checkbox`) and the list picker chips in This week's
    ingredients and Add to grocery list are announced as checkboxes, though only one can be chosen: radio buttons in a
    `selectableGroup` say "1 of 4" and that choosing one clears the others.
  - "Take the app tour" and "About" (`SettingsSections.kt:158`, `:166`) are built with `toggleable(false, role =
    Role.Button)`, so TalkBack says "not checked" on two plain links; `clickable(role = Role.Button)` is enough.
  - The favorite toggle on recipe cards is "Remove from favorites, checkbox, checked": a label that flips with the
    state plus a state ("checked … remove") says it twice; "Favorite" with the checked state, or a plain button with
    the flipping label, not both. On recipe detail the same control is a plain button, so the two places differ.
  - Cook mode has two "Back" buttons (leave cook mode, previous step); call the second "Previous step".
  - The settings avatar reads "Open settings, 1 join request waiting. 1": hide the badge's number.
  - Household settings: "Approve" and "Decline" don't name the person; with two requests the buttons are ambiguous out
    of context ("Approve Robin Lee").
- **Evidence:** the `uiautomator` trees; TalkBack's spoken "Not selected. Other. Check box" for a filter chip.
- **Who it affects:** TalkBack users, a little on each of these screens.
- **Suggested fix:** as above, one string or modifier each. Effort S. Also needs: web check (the web's date tiles are
  `aria-pressed` buttons, the same issue in another form).

### A11Y-09 · P3 · Confirmed · Android — List group headers and Settings sections aren't headings, while the web's are

- **Where:** Pantry's sticky group headers (`PantryScreen.kt:298-306`), Groceries' aisle and In cart headers
  (`GroceriesScreen.kt:174`, `:481`), the section titles in Settings (Account, Appearance, Data) and Household (Join
  requests, Invite people, Members). Twelve places use `heading()` (Plan days, Home card titles, recipe detail and
  others); these don't.
- **What happens:** TalkBack's "Headings" navigation can't jump from Fridge to Freezer in a long pantry, or from aisle
  to aisle in the shop; a 150-item pantry has to be swiped item by item. The web marks the same headers as headings
  (`pantry.spec.ts:73` asserts them).
- **Evidence:** `grep heading()`; the `uiautomator` trees.
- **Who it affects:** TalkBack users with long lists.
- **Suggested fix:** `Modifier.semantics { heading() }` on those headers. Effort S.

### A11Y-10 · P3 · Confirmed (tree) · Likely (TalkBack) · Android — The app tour leaves the screen behind it reachable, and its card comes last

- **Where:** `core/designsystem/component/CoachMarks.kt:236-250` (the scrim swallows touches only),
  `:199-206` (the card has a pane title and a live region).
- **What happens:** while the tour's welcome card shows, the accessibility tree still holds Home's Review, Open plan,
  Settings and the five tabs, and the card is the last node. TalkBack announces the card when it appears (pane title),
  but swiping from it goes into the dimmed screen, and a double-tap there acts on it although a finger can't. A screen
  reader user can wander off the tour without knowing it's still open.
- **Evidence:** `uiautomator` tree with the tour open (Review, Open plan, the tabs, then "Welcome to Spoil Not", "Skip
  tour", "Start the tour"). *Likely* for TalkBack's actual order (not driven live).
- **Who it affects:** TalkBack users on their first run on each phone.
- **Suggested fix:** hide the content under the overlay from accessibility while a step shows
  (`Modifier.semantics { hideFromAccessibility() }` on the scaffold, except the highlighted target), or show the tour
  steps in a `Dialog`. Effort S to M. Also needs: web check (the web's tour, not covered here).

### A11Y-11 · P3 · Confirmed · Android — Keyboard: Tab reaches the tab bar before the screen, and focus is hard to see

- **Where:** the app shell (`core/navigation/AppShell.kt`, `TabScaffold`); Material's focus indication.
- - **What happens:** from the top bar, Tab goes to the five tabs and only then into the content (Home: settings,
  Home, Pantry, Plan, Recipes, Groceries, then Review, See all, Open plan). Without TalkBack the focused control is
  marked only by Material's state layer, a faint tint that is hard to find on chips and rows (no outline). Every
  action was reachable with Tab and Enter where it was tried (tabs, filter chips, the search field, the Throw away
  dialog). The floating buttons cover the last row's ⋮ (UX-14), so a focused ⋮ can likely sit under them. Switch
  Access uses the same focus order.
- **Evidence:** Tab walks on Home and Pantry with and without TalkBack (screenshots in the scratchpad).
- **Who it affects:** keyboard users (a Bluetooth keyboard, a Chromebook) and switch users.
- **Suggested fix:** order the shell so content comes before the bar (or `focusProperties` on the bar), add a visible
  focus outline in the design system (2 dp `primary` ring on focus), and bottom padding under lists equal to the
  floating button (UX-14's fix). Effort S to M.

### A11Y-12 · P3 · Confirmed · Web — An empty heading, an unnamed progress bar, and no tests past 200% while iPhones go to 312%

- **Where:** recipe detail's top app bar renders `<h1 class="m3-top-app-bar__title"></h1>` with no text
  (`web/src/ui/kit/AppBars.tsx`, used with an empty title on recipe detail); cook mode's `<div role="progressbar"
  aria-valuemin="1" aria-valuemax="3" aria-valuenow="1">` has no name (`ui/recipes/CookModeScreen.tsx`);
  `tests/e2e/screens.spec.ts` covers text scales 1 and 2 only.
- **What happens:** VoiceOver's headings rotor lists an empty heading first on every recipe; the progress bar is read
  as "1 of 3" with no subject ("Step 1 of 3" is intended). At the iPhone's AX sizes, which `textScale.ts` supports up
  to 3.12, the tab labels shrink to two letters ("Ho…", "Pa…"), Home's Review label overflows its button and chips run
  past the card (`assets/07/web-ax5.png`); nothing tests those sizes.
- **Evidence:** axe (`empty-heading`, `aria-progressbar-name`, every pass); the AX3 and AX5 passes.
- **Who it affects:** VoiceOver users on recipes; iPhone users with the largest text sizes.
- **Suggested fix:** don't render the `<h1>` when the title is empty (or use the recipe's title); `aria-label` the
  progress bar with the step string; at scales above 2, labels only for the selected tab; add `textScale: 3` to the
  screenshot passes for Home, Pantry and Groceries. Effort S.

## Ideas

- **Custom accessibility actions on rows.** Pantry rows could offer Used up, Throw away, Use 1 and Edit directly
  (`customActions`), and grocery rows Delete, so TalkBack users skip the ⋮ menu; the menu stays for everyone else.
- **Borders that show without color.** Raise the unselected tile and chip border from `outlineVariant` to `outline`
  (3:1 or more in every theme) so the controls' shape is visible to low-vision users, not only their text.
- **A "Text size" preview in Settings > Appearance**, using the Roborazzi 2.0 layouts, so people see what changes.
- **One accessibility rule in the design system:** a lint or test that every `Icon` inside a clickable without text
  has a description; it would have caught A11Y-01 at compile time.

## For other sections

- **10 Web:** the web's tour wasn't seen in the axe passes (a new browser context skipped it or the detection missed
  it); check A11Y-10 there. A11Y-04 and A11Y-07 apply to the web's Home and Groceries rows.
- **11/12 Docs:** DESIGN 6.8 says "contrast checked in both themes" while the test misses the chip tint (A11Y-03);
  QA.md's TalkBack script should name the floating buttons and Undo.

## Not covered

- **TalkBack's own navigation:** swipe gestures and touch exploration couldn't be driven (injected input doesn't
  reach TalkBack's touch explorer, its Alt+arrow shortcuts moved once and then stopped, and `uiautomator` suspends
  TalkBack while it runs). Focus order was checked from the tree's geometry and with Tab; what TalkBack says was read
  from its speech output for the stops reached by keyboard. The owner's phone checks cover a real screen reader.
- **Switch Access and Voice Access:** installed on the image but not run; their reach follows the same focusable
  nodes and actions (A11Y-01, A11Y-11).
- **Landscape visuals:** the emulator drew only half the screen after rotating (also in its own framebuffer), so
  landscape was judged from the tree only: content stays in a centered column of about 490 dp and every control is
  present. DESIGN 6.2's "must not break" needs a look on a phone.
- **Animations off on the screen:** checked by reading: `rememberReduceMotion()` drives the shared-axis transition,
  the remote-change highlight, the chart growth and the skeleton, and Compose follows the system animator scale. The
  setting is read once per composition, so turning it on while the app runs applies after a restart.
- **Screens not opened:** Update required, Move and Move meal dialogs, the recipe import and browser screens (the
  imported-recipe flow needs network), and the tour beyond its first card. Add meal and Insights were audited by hand
  but not by ATF.
- **The real Material You palette** of a phone: a sample from a blue seed was used; the failures it shows are the
  same as the fixed themes'.
- **VoiceOver:** Playwright's WebKit has no screen reader; the web was checked with axe and by reading the kit.

## Phone checks for the owner

| # | Phone | Steps | Expected | Result |
| --- | --- | --- | --- | --- |
| 1 | Android, TalkBack on (Settings > Accessibility > TalkBack), eyes closed | Open Pantry; find the button to add food; add "Milk" with a date of +1 week; save | You hear "Add item, button" (A11Y-01 expects only "Button"); the sheet's fields have names | |
| 2 | Android, TalkBack on, eyes closed | On a Pantry row, open its menu (⋮ "Options for this batch"), choose Throw away > Spoiled; then try to Undo | You hear "Milk thrown away" and can reach Undo before it goes (A11Y-02 expects it gone in 8 s); time how long you had | |
| 3 | Android, TalkBack on | Swipe through Home from the top | Each Needs attention row is one sentence with the name and status (A11Y-07 expects two stops) | |
| 4 | iPhone, the web app from the Home Screen, VoiceOver on (Settings > Accessibility > VoiceOver) | Groceries: find Bananas and check it off; then press Add 1 to pantry | VoiceOver reads the name, the amount and "checkbox", then the new state after the double tap; the sheet opens with focus inside it | |
| 5 | Android, Settings > Display > Font size and Display size both at the largest | Look at Home's Needs attention and the bottom bar | Every food name visible and whole (A11Y-04 expects names cut or missing; UX-14 the bar) | |
| 6 | iPhone, Settings > Accessibility > Display & Text Size > Larger Text, the largest size | Open the web app: Home, Pantry, Groceries | Nothing cut off (A11Y-12 expects "Ho…" tab labels and Review overflowing) | |
| 7 | Android, light mode, outdoors or at low brightness | Read Pantry's Tomorrow and 2 days chips | Easy to read (A11Y-03 measured 4.1:1) | |

## Appendix A: the temporary test (removed after the run)

`app/src/androidTest/kotlin/com/parcha/spoilnot/a11y/Review07UncoveredScreensTest.kt`, run with the emulators up:
`./gradlew connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.parcha.spoilnot.a11y.Review07UncoveredScreensTest,com.parcha.spoilnot.a11y.AccessibilityChecksTest`
(BUILD SUCCESSFUL in 7 min, both tests passed: this one logs instead of failing). Results (`adb logcat -s REVIEW07`):

```text
FAIL Add item sheet: View with bounds [498,480][582,607]: This item's width is 32dp. (TouchTargetSizeCheck)
FAIL Edit item sheet: same drag handle
PASS Row menu (2 windows)
FAIL Mark opened sheet: same drag handle
PASS Throw away dialog (2 windows)
PASS Plan
NAV  add meal sheet: sheet_add_meal not shown within 20 s (the first plan_add_ tag was another button)
PASS Recipes
PASS Cook mode
FAIL I cooked this sheet: same drag handle
FAIL Add to grocery list sheet: same drag handle
PASS Recipe edit
FAIL Add to pantry sheet: same drag handle
PASS Household settings
PASS Notifications
NAV  insights: screen_insights not shown within 20 s (See insights not on screen for the new household)
SEMANTICS fab_add_item, fab_add_recipe, recipe_cook, fab_add_to_pantry: Role = 'Button', Actions = [OnClick,
  RequestFocus], MergeDescendants = 'true', no Text, no ContentDescription
```

```kotlin
package com.parcha.spoilnot.a11y

// Imports as in AccessibilityChecksTest, plus: android.util.Log, SemanticsProperties, getOrNull, SemanticsMatcher,
// isRoot, printToString, androidx.test.espresso.Espresso.

/**
 * Review 07 (temporary): the same Accessibility Test Framework checks as AccessibilityChecksTest, on the screens and
 * sheets it does not visit. Logs each result under the tag REVIEW07 instead of failing at the first one.
 */
@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class Review07UncoveredScreensTest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createEmptyComposeRule()

    // The same @Inject fields and @Before householdWithData() as AccessibilityChecksTest (Milk, Yogurt, Rice, "2 kg
    // flour" on the list, a Pancakes recipe with three ingredients and two steps).

    private var scenario: ActivityScenario<MainActivity>? = null
    private val results = mutableListOf<String>()

    private fun str(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    @After
    fun tearDown() {
        Log.i(TAG, "SUMMARY\n" + results.joinToString("\n"))
        scenario?.close()
        FirebaseAuth.getInstance().signOut()
    }

    private fun tagPrefix(prefix: String) = SemanticsMatcher("tag starts with $prefix") {
        it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(prefix) == true
    }

    private fun await(matcher: SemanticsMatcher) = compose.waitUntilAtLeastOneExists(matcher, 20_000)

    private fun tab(label: Int) = compose.onNode(hasText(str(label)) and isSelectable()).performClick()

    private fun back() {
        Espresso.pressBackUnconditionally()
        compose.waitForIdle()
        Thread.sleep(600)
    }

    /** Runs the checks on every window (the screen, and a sheet, dialog or menu on top of it). */
    private fun check(name: String) {
        compose.waitForIdle()
        Thread.sleep(800) // sheets animate in
        val roots = compose.onAllNodes(isRoot()).fetchSemanticsNodes().size
        val failures = (0 until roots).mapNotNull { i ->
            runCatching { compose.onAllNodes(isRoot())[i].tryPerformAccessibilityChecks() }.exceptionOrNull()
        }
        val line = if (failures.isEmpty()) {
            "PASS $name ($roots windows)"
        } else {
            "FAIL $name: " + failures.joinToString(" || ") { (it.message ?: it.toString()).replace("\n", " ") }
        }
        Log.i(TAG, line)
        results += line
    }

    private fun step(name: String, block: () -> Unit) {
        runCatching(block).onFailure {
            val line = "NAV $name: ${it.message?.lineSequence()?.firstOrNull()}"
            Log.i(TAG, line)
            results += line
        }
    }

    private fun printFab(tag: String) {
        val text = runCatching { compose.onNodeWithTag(tag).printToString() }.getOrElse { it.toString() }
        Log.i(TAG, "SEMANTICS $tag\n$text")
        results += "SEMANTICS $tag: ${text.replace("\n", " | ")}"
    }

    @Test
    fun uncoveredScreens() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.enableAccessibilityChecks()
        await(hasTestTag("card_home_attention"))

        step("pantry") {
            tab(R.string.tab_pantry)
            await(hasTestTag("pantry_search"))
            printFab("fab_add_item")
        }
        step("add item sheet") {
            compose.onNodeWithTag("fab_add_item").performClick()
            await(hasTestTag("sheet_add_item"))
            check("Add item sheet")
            back()
        }
        step("edit item sheet") {
            compose.onAllNodes(tagPrefix("pantry_item_"))[0].performClick()
            await(hasTestTag("sheet_edit_item"))
            check("Edit item sheet")
            back()
        }
        step("row menu") {
            compose.onAllNodes(tagPrefix("menu_"))[0].performClick()
            await(hasText(str(R.string.action_mark_opened)))
            check("Row menu")
            compose.onNode(hasText(str(R.string.action_mark_opened))).performClick()
            await(hasTestTag("sheet_opened"))
            check("Mark opened sheet")
            back()
        }
        step("throw away dialog") {
            compose.onAllNodes(tagPrefix("menu_"))[0].performClick()
            await(hasText(str(R.string.action_throw_away)))
            compose.onNode(hasText(str(R.string.action_throw_away))).performClick()
            Thread.sleep(800)
            check("Throw away dialog")
            back()
        }
        step("plan") {
            tab(R.string.tab_plan)
            await(hasTestTag("screen_plan"))
            check("Plan")
        }
        step("add meal sheet") { // fix for reuse: match "plan_add_<day>_<slot>", not "plan_add_items"
            compose.onAllNodes(tagPrefix("plan_add_"))[0].performClick()
            await(hasTestTag("sheet_add_meal"))
            check("Add meal sheet")
            back()
        }
        step("recipes") {
            tab(R.string.tab_recipes)
            await(hasTestTag("recipe_card"))
            check("Recipes")
            printFab("fab_add_recipe")
        }
        step("cook mode") {
            compose.onAllNodes(hasTestTag("recipe_card"))[0].performClick()
            await(hasTestTag("recipe_title"))
            printFab("recipe_cook")
            compose.onNodeWithTag("recipe_cook").performClick()
            await(hasTestTag("screen_cook_mode"))
            check("Cook mode")
            back()
        }
        step("i cooked this") {
            await(hasTestTag("recipe_cooked"))
            compose.onNodeWithTag("recipe_cooked").performClick()
            await(hasTestTag("sheet_cooked"))
            check("I cooked this sheet")
            back()
        }
        step("add to grocery list") {
            compose.onNodeWithTag("recipe_add_to_list").performClick()
            Thread.sleep(800)
            check("Add to grocery list sheet")
            back()
        }
        step("recipe edit") {
            compose.onNodeWithTag("recipe_edit").performClick()
            await(hasTestTag("screen_recipe_edit"))
            check("Recipe edit")
            back()
            back()
        }
        step("groceries add to pantry") {
            tab(R.string.tab_groceries)
            await(tagPrefix("grocery_item_"))
            compose.onAllNodes(tagPrefix("grocery_item_"))[0].performClick()
            await(hasTestTag("fab_add_to_pantry"))
            printFab("fab_add_to_pantry")
            compose.onNodeWithTag("fab_add_to_pantry").performClick()
            await(hasTestTag("sheet_add_to_pantry"))
            check("Add to pantry sheet")
            back()
        }
        step("settings pages") {
            compose.onNode(SemanticsMatcher("settings") {
                it.config.getOrNull(SemanticsProperties.ContentDescription)
                    ?.any { d -> d.startsWith(str(R.string.open_settings)) } == true
            }).performClick()
            await(hasTestTag("screen_settings"))
            compose.onNodeWithTag("row_household").performClick()
            await(hasTestTag("screen_household"))
            check("Household settings")
            back()
            compose.onNodeWithTag("row_notifications").performClick()
            await(hasTestTag("screen_notifications"))
            check("Notifications")
            back()
        }
        step("insights") { // fix for reuse: seed a stats month so Home shows See insights
            back()
            tab(R.string.tab_home)
            await(hasText(str(R.string.insights_see)))
            compose.onNode(hasText(str(R.string.insights_see))).performClick()
            await(hasTestTag("screen_insights"))
            check("Insights")
        }
    }

    private companion object {
        const val TAG = "REVIEW07"
    }
}
```

## Appendix B: the contrast script (scratchpad)

`node contrast.mjs [--json rows.json]`, Node 24, Material Color Utilities from `tools/themes/node_modules`.

```js
import { readFileSync, writeFileSync } from 'node:fs';
import { createRequire } from 'node:module';

const ROOT = '/Users/jmalp/Code/personal/spoilnot';
const mcu = await import(createRequire(`${ROOT}/tools/themes/package.json`).resolve('@material/material-color-utilities'));
const src = readFileSync(`${ROOT}/web/src/theme/tokens.generated.ts`, 'utf8');
const SCHEMES = JSON.parse(src.match(/export const SCHEMES = (\{[\s\S]*?\n\}) as const;/)[1]);
const STATUS = JSON.parse(src.match(/export const STATUS_COLORS = (\{[\s\S]*?\n\}) as const;/)[1]);
const ROLES = Object.keys(SCHEMES.spoilnot.light);

// Sample Material You palette: Android's dynamic scheme (tonal spot) from a blue wallpaper seed.
function tonalSpot(seedHex, dark) {
  const s = new mcu.SchemeTonalSpot(mcu.Hct.fromInt(mcu.argbFromHex(seedHex)), dark, 0);
  return Object.fromEntries(ROLES.filter((r) => mcu.MaterialDynamicColors[r])
    .map((r) => [r, mcu.hexFromArgb(mcu.MaterialDynamicColors[r].getArgb(s))]));
}
const themes = { ...SCHEMES, 'wallpaper (sample)': { light: tonalSpot('#3F6FB5', false), dark: tonalSpot('#3F6FB5', true) } };

const hex = (h) => [1, 3, 5].map((i) => parseInt(h.slice(i, i + 2), 16));
const lin = (c) => { c /= 255; return c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4; };
const lum = (rgb) => 0.2126 * lin(rgb[0]) + 0.7152 * lin(rgb[1]) + 0.0722 * lin(rgb[2]);
const ratio = (a, b) => { const [x, y] = [lum(a), lum(b)].sort((p, q) => q - p); return (x + 0.05) / (y + 0.05); };
const over = (fg, alpha, bg) => fg.map((c, i) => Math.round(alpha * c + (1 - alpha) * bg[i])); // as Compose draws it

const TEXT = 4.5, UI = 3.0;
const S = (tone) => ({ status: tone });
const tint = (fg, alpha, on) => ({ tint: fg, alpha, on });
const PAIRS = [ // [what, fg, bg, min]
  ['Body text', 'onSurface', 'surface', TEXT], ['Secondary text', 'onSurfaceVariant', 'surface', TEXT],
  ['Secondary text in sheets', 'onSurfaceVariant', 'surfaceContainerLow', TEXT],
  ['Secondary text in dialogs', 'onSurfaceVariant', 'surfaceContainerHigh', TEXT],
  ['Unselected tab label and icon', 'onSurfaceVariant', 'surfaceContainer', TEXT],
  ['Selected tab icon', 'onSecondaryContainer', 'secondaryContainer', UI],
  ['Search placeholder', 'onSurfaceVariant', 'surfaceContainerHigh', TEXT],
  ['Offline banner', 'onSurfaceVariant', 'surfaceContainerHighest', TEXT],
  ['Text buttons, links', 'primary', 'surface', TEXT], ['Text buttons in sheets', 'primary', 'surfaceContainerLow', TEXT],
  ['Dialog buttons', 'primary', 'surfaceContainerHigh', TEXT], ['Filled button', 'onPrimary', 'primary', TEXT],
  ['Extended FAB', 'onPrimaryContainer', 'primaryContainer', TEXT],
  ['Selected chip, tonal button', 'onSecondaryContainer', 'secondaryContainer', TEXT],
  ['Recipe placeholder tile', 'onTertiaryContainer', 'tertiaryContainer', TEXT],
  ['Error text', 'error', 'surface', TEXT], ['Error text in sheets', 'error', 'surfaceContainerLow', TEXT],
  ['Error card', 'onErrorContainer', 'errorContainer', TEXT], ['Snackbar text', 'inverseOnSurface', 'inverseSurface', TEXT],
  ['Snackbar Undo', 'inversePrimary', 'inverseSurface', TEXT], ['Tertiary text in sheet', 'tertiary', 'surfaceContainerLow', TEXT],
  ['Cooked label', 'primary', 'surface', TEXT],
  ...['fresh', 'soon', 'urgent', 'expired', 'noDate'].map((t) => [`Status chip text: ${t}`, S(t), tint(S(t), 0.12, 'surface'), TEXT]),
  ['Swipe label: Used up', S('fresh'), tint(S('fresh'), 0.16, 'surface'), TEXT],
  ['Swipe label: Throw away', S('expired'), tint(S('expired'), 0.16, 'surface'), TEXT],
  ['Expired header icon', S('expired'), tint(S('expired'), 0.08, 'surface'), UI],
  ['Text field and checkbox outline', 'outline', 'surface', UI], ['Text field outline in sheets', 'outline', 'surfaceContainerLow', UI],
  ['Unselected date and place tiles', 'outlineVariant', 'surfaceContainerLow', UI],
  ['Unselected filter chip border', 'outlineVariant', 'surface', UI], ['Icons in primary', 'primary', 'surface', UI],
  ['Chart: used', 'primary', 'surface', UI], ['Chart: thrown away', 'tertiary', 'surface', UI],
  ['Chart bar on its track', 'primary', 'surfaceVariant', UI], ['Thrown away bar on its track', 'tertiary', 'surfaceVariant', UI],
  ['Selected tab indicator vs bar', 'secondaryContainer', 'surfaceContainer', UI], ['Switch on: track', 'primary', 'surface', UI],
];
const resolve = (scheme, status, ref) => typeof ref === 'string' ? hex(scheme[ref])
  : ref.status ? hex(status[ref.status]) : over(resolve(scheme, status, ref.tint), ref.alpha, resolve(scheme, status, ref.on));

const rows = [];
for (const [theme, modes] of Object.entries(themes)) for (const mode of ['light', 'dark']) for (const [what, fg, bg, min] of PAIRS) {
  const r = ratio(resolve(modes[mode], STATUS[mode], fg), resolve(modes[mode], STATUS[mode], bg));
  rows.push({ theme, mode, what, ratio: Math.round(r * 100) / 100, min, pass: r >= min });
}
for (const f of rows.filter((r) => !r.pass)) console.log(`${f.theme} ${f.mode} | ${f.what} | ${f.ratio}:1 (needs ${f.min})`);
if (process.argv.includes('--json')) writeFileSync(process.argv[process.argv.indexOf('--json') + 1], JSON.stringify(rows, null, 1));
```

## Appendix C: the web axe walk (scratchpad)

`axe-core@4.10.3` installed in the scratchpad (`npm install axe-core`), Playwright and WebKit from `web/node_modules`,
`npm run dev -- --port 5173` in `web/`, the seeded emulators. `node walk.cjs <light|dark> <textScale> [theme]` signs
in as Sam, opens each screen, sheet, dialog and menu by role (as the e2e tests do), injects axe and runs it with the
WCAG 2.0/2.1/2.2 A and AA tags plus best practices, records elements whose text overflows a clipping box, and saves a
screenshot. The core of it:

```js
const { webkit, devices } = createRequire('/Users/jmalp/Code/personal/spoilnot/web/package.json')('@playwright/test');
const context = await browser.newContext({ ...devices['iPhone 15'], colorScheme: mode });
await context.addInitScript(([s]) => {
  localStorage.setItem('spoilnot.textScale', s);
  localStorage.setItem('spoilnot.installHelpDismissed', 'true');
}, [String(scale)]);
// for each screen:
await page.addScriptTag({ content: fs.readFileSync('node_modules/axe-core/axe.min.js', 'utf8') });
const violations = await page.evaluate(async () => (await window.axe.run(document, {
  runOnly: { type: 'tag', values: ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa', 'best-practice'] },
})).violations);
```

Screens: welcome, sign in, sign up, gate, Home, Pantry, Add item, row menu, Throw away, Mark opened, Edit, sort menu,
Plan, Add meal, meal sheet, This week's ingredients, Recipes, recipe detail, I cooked this, Add to grocery list, cook
mode, recipe edit, Groceries, Add to pantry, item menu, Edit grocery item, Settings, Household, Notifications, About,
Insights (31).
