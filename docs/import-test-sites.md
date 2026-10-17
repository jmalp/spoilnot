# Recipe import test sites

DESIGN 11.6 and the M5 acceptance check: recipe import must work on at least 9 of these 10 sites. Each site has a
trimmed structure snapshot in `app/src/test/resources/recipes/snapshots/` that `SiteSnapshotTest` imports on every
build. A snapshot keeps the page's real JSON-LD shape (types, nesting, `@graph`, lists, durations and counts) with all
text and links replaced by synthetic placeholders, so no recipe text is stored in the repository.

| # | Site | Test page | Plain fetch (Oct 2026) | Path in the app | JSON-LD shape |
| --- | --- | --- | --- | --- | --- |
| 1 | Allrecipes | allrecipes.com/recipe/10813/best-chocolate-chip-cookies/ | Blocked (HTTP 402) | Built-in browser | Array with one `["Recipe", "NewsArticle"]`, HowToStep list |
| 2 | BBC Good Food | bbcgoodfood.com/recipes/easy-pancakes | OK | Fetch | Top-level Recipe, image list, HowToStep list |
| 3 | Serious Eats | seriouseats.com/the-food-lab-best-chocolate-chip-cookie-recipe | Blocked (HTTP 402) | Built-in browser | Array with one Recipe, HowToStep list |
| 4 | Budget Bytes | budgetbytes.com/one-pot-creamy-cajun-chicken-pasta/ | Blocked (HTTP 403, challenge page) | Built-in browser | `@graph` (Yoast) with a WP Recipe Maker Recipe |
| 5 | RecipeTin Eats | recipetineats.com/chicken-chasseur/ | OK | Fetch | `@graph` with HowToSection groups |
| 6 | Simply Recipes | simplyrecipes.com/recipes/homemade_pizza/ | Blocked (HTTP 402) | Built-in browser | Array with one Recipe, HowToSection groups |
| 7 | Food Network | foodnetwork.com/recipes/alton-brown/the-chewy-recipe-1909046 | Blocked (HTTP 403) | Built-in browser | Array of Recipe and BreadcrumbList; durations like `P0Y0M0DT0H30M0.000S` |
| 8 | Bon Appétit | bonappetit.com/recipe/bas-best-chocolate-chip-cookies | OK | Fetch | Top-level Recipe; times as plain text ("20 minutes") |
| 9 | King Arthur Baking | kingarthurbaking.com/recipes/classic-sandwich-bread-recipe | OK | Fetch | `@graph` with one Recipe, ImageObject |
| 10 | Minimalist Baker | minimalistbaker.com/easy-vegan-fried-rice/ | OK | Fetch | `@graph` with a WP Recipe Maker Recipe |

"Plain fetch" is a request from a computer (curl) with the app's User-Agent. Blocked pages go to the built-in
browser automatically (DESIGN 11.2), which reads the same JSON-LD from the live page. On the phone, OkHttp got
through on all but one of them; see the results below.

Alternates if a site changes or disappears: Love and Lemons, Smitten Kitchen, Delish, Epicurious. NYT Cooking is
excluded because of its paywall.

## Updating a snapshot

Use `scripts/recipe-snapshot.js`. It keeps `@type`, `@context`, durations and numbers, replaces every other string
and every link with placeholders, and drops reviews, videos and nutrition.

1. If the page can be fetched: save it and run `node scripts/recipe-snapshot.js page.html > snapshot.html`. If it
   is blocked, open it in a browser, paste the script's `anonymize` function into the console and follow the usage
   comment at the top of the script.
2. Save the result as `snapshots/<site>.html` and update the expected counts in `SiteSnapshotTest`.

## M5 acceptance results (2026-10-17)

`LiveImportTest` on the Pixel 8 emulator (API 37), using the app's fetch and, when that fails, the app's web view:

```
./gradlew connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.live=true \
  -Pandroid.testInstrumentationRunnerArguments.class=com.parcha.spoilnot.feature.recipes.LiveImportTest
```

| # | Site | Path | Time | Ingredients (quantity parsed) | Steps | Servings | Total |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | Allrecipes | Fetch | 0.8 s | 11 (11) | 9 | 48 | 30 min |
| 2 | BBC Good Food | Fetch | 0.7 s | 6 (4) | 5 | 12 | 30 min |
| 3 | Serious Eats | Built-in browser | 6.2 s | 11 (10) | 6 | 28 | 75 min |
| 4 | Budget Bytes | Fetch | 0.7 s | 17 (17) | 6 | 4 | 30 min |
| 5 | RecipeTin Eats | Fetch | 0.8 s | 19 (18) | 10 (4 groups) | 4 | 60 min |
| 6 | Simply Recipes | Fetch | 0.8 s | 25 (6) | 13 (2 groups) | 6 | 150 min |
| 7 | Food Network | Fetch | 2.5 s | 11 (11) | 6 | 2 | 110 min |
| 8 | Bon Appétit | Fetch | 0.7 s | 10 (10) | 6 | 16 | 40 min |
| 9 | King Arthur Baking | Fetch | 0.7 s | 7 (7) | 7 | 16 | 162 min |
| 10 | Minimalist Baker | Fetch | 0.7 s | 12 (12) | 10 | 4 | 75 min |

**10 of 10 imported** (target 9 of 10). Every result had a title and an image. Unparsed quantities are lines without
one, such as Simply Recipes' optional pizza toppings ("Pesto", "Ham, thinly sliced"); they keep their text and are
not scaled.

Share sheet: `RecipeUiTest` sends the same intent Chrome's share sheet does. Share to preview took 1.6 s on an idle
host (target under 5 s).
