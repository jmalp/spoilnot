// Screenshots of every web screen so far (DESIGN 18.2, 18.7), in light and dark at 100% and 200% text, to compare
// with the Android app. Text that differs per run (emails, codes, dates) is masked.
import { expect, test, type Locator, type Page } from '@playwright/test';
import {
  createHousehold,
  defaultListId,
  householdId,
  inDays,
  openApp,
  seedActivity,
  seedGroceries,
  seedMeal,
  seedMember,
  seedPantry,
  seedRecipe,
  seedStats,
  signUp,
  uidOf,
  uniqueEmail,
} from './support';

test.skip(!!process.env.CI, 'Linux WebKit draws fonts differently; screenshots are compared on the Mac');
// Each run walks through every screen so far, with data seeded on the way.
test.setTimeout(180_000);

/** [mask]: test ids, or locators for text without one. */
async function shot(page: Page, name: string, mask: (string | Locator)[] = []) {
  await page.evaluate(() => document.fonts.ready);
  await expect(page).toHaveScreenshot(`${name}.png`, {
    mask: mask.map((m) => (typeof m === 'string' ? page.getByTestId(m) : m)),
  });
}

/** This month and the one before, as 'YYYY-MM'. */
function months(): [string, string] {
  const now = new Date();
  const last = new Date(now.getFullYear(), now.getMonth() - 1, 1);
  const ym = (d: Date) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
  return [ym(now), ym(last)];
}

for (const dark of [false, true]) {
  for (const textScale of [1, 2]) {
    const suffix = `${dark ? 'dark' : 'light'}_font${textScale}`;
    test(`screens ${suffix}`, async ({ page }) => {
      await openApp(page, { dark, textScale });
      await expect(page.getByTestId('screen_welcome')).toBeVisible();
      await shot(page, `welcome_${suffix}`);

      await page.getByRole('button', { name: 'Create account' }).click();
      await page.getByRole('button', { name: 'Create account' }).click(); // empty: shows the field errors
      await shot(page, `sign_up_errors_${suffix}`);
      await page.getByRole('button', { name: 'Back' }).click();

      const email = uniqueEmail('Sam');
      await signUp(page, 'Sam', email);
      await shot(page, `gate_${suffix}`);

      const household = `The Smiths ${suffix} ${Date.now()}`;
      await createHousehold(page, household);
      // Loaded: the skeleton is gone and this month's card has its (empty) counts.
      await expect(page.getByTestId('card_home_insights')).toBeVisible({ timeout: 15_000 });
      await expect(page.getByTestId('home_skeleton')).toHaveCount(0);
      await shot(page, `home_${suffix}`);

      // Dates relative to today, so the chips read the same on every run.
      const hid = await householdId(household);
      const uid = await uidOf(email);
      await seedPantry(hid, uid, [
        { id: 'cheese', name: 'Cheese', expiry: inDays(-3) },
        { id: 'milk', name: 'Milk', expiry: inDays(1) },
        { id: 'yogurt', name: 'Greek yogurt', quantity: 6, expiry: inDays(2) },
        { id: 'eggs', name: 'Eggs', quantity: 12, expiry: inDays(6), location: 'fridge' },
        { id: 'rice', name: 'Rice', quantity: 500, unit: 'g', expiry: null, location: 'pantry', min: 200 },
        { id: 'oil', name: 'Olive oil', quantity: 0, min: 1, location: 'pantry' },
      ]);
      await page.getByRole('navigation').getByRole('button', { name: 'Pantry' }).click();
      await expect(page.getByTestId('pantry_item_rice')).toBeVisible();
      await expect(page.locator('[data-remote]')).toHaveCount(0);
      await shot(page, `pantry_${suffix}`);
      await page.getByTestId('fab_add_item').click();
      await expect(page.getByTestId('sheet_add_item')).toBeVisible();
      await page.waitForTimeout(400); // the sheet's slide-in
      await shot(page, `add_item_${suffix}`);
      await page.keyboard.press('Escape');
      await expect(page.getByTestId('sheet_add_item')).toHaveCount(0);

      // Home's cards and Insights. Thrown away today keeps the streak line the same on every run; month names
      // change with the calendar, so they are masked.
      const [thisMonth, lastMonth] = months();
      await seedStats(hid, uid, thisMonth, { used: 3, thrown: { expired: 1 }, byDay: { [new Date().getDate()]: 1 } });
      await seedStats(hid, uid, lastMonth, { used: 12, thrown: { expired: 3, spoiled: 1 }, byDay: { 3: 4 } });
      await seedActivity(hid, uid, 'Sam', { type: 'stock_added', name: 'Greek yogurt' });
      await page.getByRole('navigation').getByRole('button', { name: 'Home' }).click();
      await expect(page.getByTestId('card_home_insights')).toContainText('3 used');
      await expect(page.getByTestId('card_home_activity')).toContainText('Greek yogurt');
      await page.waitForTimeout(800); // the ring's grow animation
      await shot(page, `home_cards_${suffix}`);
      await page.getByTestId('card_home_insights').getByRole('button', { name: 'See insights' }).click();
      await expect(page.getByTestId('insights_month_details')).toContainText('3 used');
      await page.waitForTimeout(800);
      const monthNames = [
        page.locator('.sn-month-bar__label'),
        page.getByTestId('insights_month_details').locator('.t-title-small'),
      ];
      await shot(page, `insights_${suffix}`, monthNames);
      await expect(page.getByTestId('card_insights_months')).toHaveScreenshot(`insights_months_${suffix}.png`, {
        mask: monthNames,
      });
      await page.getByRole('button', { name: 'Back' }).click();

      await page.getByRole('button', { name: /Open settings/ }).click();
      await expect(page.getByTestId('row_household')).toContainText('The Smiths');
      await page
        .getByTestId('row_household')
        .locator('.m3-list-item__supporting')
        .evaluate((el) => (el.textContent = 'The Smiths · 1 member'));
      await page.getByTestId('account_email').evaluate((el) => (el.textContent = 'sam@example.com'));
      await shot(page, `settings_${suffix}`);

      await page.getByTestId('row_household').click();
      await expect(page.getByTestId('card_members')).toContainText('Sam (you)');
      // A fixed code: random letters wrap differently at 200%.
      await page.getByTestId('invite_code').evaluate((el) => (el.textContent = 'ABCDE-FGHJK'));
      await shot(page, `household_${suffix}`, ['member_since']);

      // Settings > Appearance (the app's settings_colors screenshot) and About.
      await page.getByRole('button', { name: 'Back' }).click();
      // Scrolled to just below the app bar (at 200% the card is taller than the screen), once the household row
      // above it has its summary.
      await expect(page.getByTestId('row_household')).toContainText('1 member');
      await page.getByTestId('card_appearance').evaluate((card) => {
        const screen = card.closest('.sn-screen')!;
        const bar = screen.querySelector('header')!.getBoundingClientRect().bottom;
        screen.scrollTop += card.getBoundingClientRect().top - bar - 8;
      });
      await shot(page, `settings_appearance_${suffix}`);
      await page.getByTestId('row_about').click();
      await expect(page.getByTestId('licenses')).toBeVisible();
      await shot(page, `about_${suffix}`);
      await page.getByRole('button', { name: 'Back' }).click();

      // Groceries: the kinds of rows the app's GroceryRowTest draws, and the Add to pantry sheet.
      await seedMember(hid, 'u-jordan', 'Jordan');
      await seedGroceries(hid, uid, await defaultListId(hid), [
        {
          id: 'low_p-rice',
          name: 'Rice',
          quantity: 1,
          category: 'pantry_staples',
          source: 'low_stock',
          productId: 'p-rice',
        },
        { id: 'eggs', name: 'Eggs', quantity: 2, category: 'dairy', createdBy: 'u-jordan' },
        { id: 'flour', name: 'Flour', quantity: 2, category: 'pantry_staples', source: 'recipe' },
        { id: 'tortillas', name: 'Tortillas', quantity: 2, category: 'bakery', source: 'meal_plan' },
        { id: 'bread', name: 'Bread', quantity: 2, category: 'bakery', checked: true },
      ]);
      await page.getByRole('navigation').getByRole('button', { name: 'Groceries' }).click();
      await expect(page.getByTestId('grocery_item_bread')).toBeVisible();
      await expect(page.getByTestId('grocery_item_low_p-rice')).toContainText('Back in stock');
      // The restock engine adds the out-of-stock olive oil; rows from the server are highlighted for a moment as
      // another phone's changes (DESIGN 6.4).
      await expect(page.getByTestId('grocery_item_low_p-oil')).toContainText('Low stock');
      await expect(page.locator('[data-remote]')).toHaveCount(0);
      await shot(page, `groceries_${suffix}`);
      await page.getByTestId('fab_add_to_pantry').click();
      await expect(page.getByTestId('sheet_add_to_pantry')).toBeVisible();
      await page.waitForTimeout(400); // the sheet's slide-in
      await shot(page, `add_to_pantry_${suffix}`);
      await page.keyboard.press('Escape');

      // Recipes, as the app's RecipeScreenshotTest draws them: a long title with a time and a star, and Pancakes.
      await seedRecipe(hid, uid, {
        id: 'curry',
        title: 'Weeknight chicken curry with rice',
        totalMinutes: 75,
        favorite: true,
      });
      await seedRecipe(hid, uid, {
        id: 'pancakes',
        title: 'Pancakes',
        servings: 4,
        totalMinutes: 20,
        tags: ['Breakfast'],
        ingredients: ['3 large eggs', 'Salt to taste', '1 extra large onion, diced'],
        steps: ['Whisk the eggs.', 'Fry in a hot pan.'],
        createdSecondsAgo: 60,
      });
      await page.getByRole('navigation').getByRole('button', { name: 'Recipes' }).click();
      await expect(page.getByTestId('recipe_card')).toHaveCount(2);
      await expect(page.locator('[data-remote]')).toHaveCount(0);
      await shot(page, `recipes_${suffix}`);
      await page.getByRole('button', { name: 'Pancakes', exact: true }).click();
      await expect(page.getByTestId('recipe_title')).toHaveText('Pancakes');
      await shot(page, `recipe_detail_${suffix}`);
      await page.getByTestId('recipe_add_to_list').click();
      await expect(page.getByTestId('sheet_add_to_list').getByTestId('candidate')).toHaveCount(3);
      await page.waitForTimeout(400); // the sheet's slide-in
      await shot(page, `recipe_add_to_list_${suffix}`);
      await page.keyboard.press('Escape');

      // Plan, as the app's PlanScreenshotTest draws it: a cooked meal, Leftovers, two cooks and a deleted recipe.
      // Next week, so the layout does not change with the weekday; the dates are masked.
      await page.getByRole('navigation').getByRole('button', { name: 'Plan' }).click();
      await page.getByTestId('plan_next_week').click();
      const first = (await page.locator('[data-testid^="plan_day_"]').first().getAttribute('data-testid'))!.slice(9);
      const second = (await page.locator('[data-testid^="plan_day_"]').nth(1).getAttribute('data-testid'))!.slice(9);
      const meals = [
        {
          id: 'm2',
          date: first,
          slot: 'breakfast',
          title: 'Pancakes',
          recipeId: 'pancakes',
          servings: 2,
          cooks: ['u-jordan'],
          cooked: true,
        },
        { id: 'm4', date: first, slot: 'lunch', title: 'Leftovers' },
        {
          id: 'm3',
          date: first,
          slot: 'dinner',
          title: 'Weeknight chicken curry with rice',
          recipeId: 'curry',
          servings: 4,
          cooks: [uid, 'u-jordan'],
        },
        { id: 'm5', date: second, slot: 'dinner', title: 'Old soup', recipeId: 'gone', servings: 3 },
      ] as const;
      for (const m of meals) await seedMeal(hid, uid, { ...m, cooks: 'cooks' in m ? [...m.cooks] : [] });
      await expect(page.getByTestId('plan_days')).toContainText('Old soup');
      await expect(page.locator('[data-remote]')).toHaveCount(0);
      const planDates = [page.getByTestId('plan_week_range'), page.locator('.sn-day-card__header h2')];
      await shot(page, `plan_${suffix}`, planDates);
      await page.getByTestId(`plan_add_${second}_lunch`).click();
      await expect(page.getByTestId('plan_pick_pancakes')).toBeVisible();
      await page.waitForTimeout(400); // the sheet's slide-in
      await shot(page, `plan_add_meal_${suffix}`, [
        page.getByTestId('plan_week_range'),
        page.getByTestId('sheet_add_meal').locator('h2'),
      ]);

      // The app tour from Settings (the app's tour_welcome and tour_home screenshots).
      await page.keyboard.press('Escape');
      await expect(page.getByTestId('sheet_add_meal')).toHaveCount(0);
      await page.getByRole('button', { name: /Open settings/ }).click();
      await page.getByTestId('row_take_tour').click();
      await expect(page.getByTestId('coach_mark_card')).toContainText('Welcome to Spoil Not');
      // Settings behind the card from the top, where the click on the row scrolled it, with the household row loaded.
      await expect(page.getByTestId('row_household')).toContainText('2 members');
      await page.getByTestId('screen_settings').evaluate((el) => (el.scrollTop = 0));
      // Fixed text, as on the Settings screenshot: the household's name has the run's time and wraps differently.
      await page
        .getByTestId('row_household')
        .locator('.m3-list-item__supporting')
        .evaluate((el) => (el.textContent = 'The Smiths · 2 members'));
      await page.getByTestId('account_email').evaluate((el) => (el.textContent = 'sam@example.com'));
      await shot(page, `tour_welcome_${suffix}`);
      await page.getByTestId('coach_mark_next').click();
      await expect(page.getByTestId('coach_mark_card')).toContainText('1 of 10');
      await page.waitForTimeout(300); // the hole follows the card's place on screen
      await shot(page, `tour_home_${suffix}`, [page.locator('.sn-home-card').getByText(/ago|Just now/)]);
    });
  }
}
