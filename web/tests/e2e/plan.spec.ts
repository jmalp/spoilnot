// The meal planner on an iPhone (DESIGN 5.9, 18.8 W5), against the emulators.
import { expect, test, type Page } from '@playwright/test';
import {
  createHousehold,
  householdId,
  inDays,
  openApp,
  seedPantry,
  seedRecipe,
  signUp,
  uidOf,
  uniqueEmail,
} from './support';

// A long flow: room for slow machines.
test.setTimeout(90_000);

const tab = (page: Page, name: string) => page.getByRole('navigation').getByRole('button', { name }).click();
const status = (page: Page, text: string) => page.getByRole('status').filter({ hasText: text });

test('plan meals, change and cook one, move with Undo, copy and clear weeks, shop for the week, and Today on Home', async ({
  page,
}) => {
  const email = uniqueEmail('Sam');
  const household = `Plan ${Date.now()}`;
  await openApp(page);
  await signUp(page, 'Sam', email);
  await createHousehold(page, household);
  const hid = await householdId(household);
  const uid = await uidOf(email);
  await seedPantry(hid, uid, [{ id: 'eggs', name: 'Eggs', quantity: 6, expiry: inDays(20) }]);
  await seedRecipe(hid, uid, {
    id: 'tacos',
    title: 'Tacos',
    servings: 2,
    ingredients: ['2 eggs', '2 tortillas'],
    steps: ['Fry.'],
  });

  await tab(page, 'Plan');
  const today = inDays(0);
  const todayCard = page.getByTestId(`plan_day_${today}`);
  await expect(todayCard).toContainText('Today');

  // Tacos for dinner, cooked by me; Leftovers for lunch.
  await page.getByTestId(`plan_add_${today}_dinner`).click();
  const add = page.getByTestId('sheet_add_meal');
  await add.getByTestId(`plan_cook_${uid}`).click();
  await add.getByTestId('plan_pick_tacos').click();
  const tacos = todayCard.getByRole('button', { name: /Tacos/ });
  await expect(tacos).toContainText('2 servings');
  await expect(tacos).toContainText('You');
  await page.getByTestId(`plan_add_${today}_lunch`).click();
  await page.getByTestId('plan_other_field').fill('Leftovers');
  await page.getByTestId('plan_add_other').click();
  const leftovers = todayCard.getByRole('button', { name: /Leftovers/ });
  await expect(leftovers).toBeVisible();

  // Home lists today's meals, breakfast first, and opens the plan.
  await tab(page, 'Home');
  const todayHome = page.getByTestId('card_home_today');
  await expect(todayHome.locator('.sn-today-meal')).toHaveText([/Lunch.*Leftovers/, /Dinner.*Tacos.*You/]);
  await todayHome.getByRole('button', { name: 'Open plan' }).click();
  await expect(page.getByTestId('screen_plan')).toBeVisible();

  // Change the servings and note, then Cooked: 3 eggs come off the pantry (2 eggs for 2, scaled to 3).
  await tacos.click();
  await page.getByRole('button', { name: 'More servings' }).click();
  await expect(page.getByTestId('plan_servings')).toHaveText('3');
  await page.getByLabel('Note').fill('Double batch');
  await page.getByTestId('plan_save_meal').click();
  await expect(tacos).toContainText('3 servings');
  await tacos.click();
  await page.getByTestId('plan_cook_meal').click();
  const cooked = page.getByTestId('sheet_cooked');
  await expect(cooked.getByTestId('cook_amount_p-eggs')).toHaveValue('3');
  await cooked.getByTestId('recipe_cooked_use').click();
  await expect(status(page, 'Cooked Tacos')).toBeVisible();
  await expect(tacos).toContainText('Cooked');

  // Move Leftovers to another day of the week, then Undo.
  const firstDay = (await page.locator('[data-testid^="plan_day_"]').first().getAttribute('data-testid'))!.slice(9);
  const days = page.locator('[data-testid^="plan_day_"]');
  const target = firstDay !== today ? firstDay : (await days.nth(1).getAttribute('data-testid'))!.slice(9);
  await leftovers.click();
  await page.getByTestId('plan_move_meal').click();
  await page.getByTestId(`plan_move_day_${target}`).click();
  await page.getByTestId('plan_move_confirm').click();
  await expect(page.getByTestId(`plan_day_${target}`)).toContainText('Leftovers');
  // Snackbars come one at a time: the Undo of this one.
  await status(page, 'Moved Leftovers').getByRole('button', { name: 'Undo' }).click();
  await expect(todayCard).toContainText('Leftovers');

  // Next week: copy last week (Cooked is not copied), shop for it, clear it with Undo.
  await page.getByTestId('plan_next_week').click();
  await expect(page.getByTestId('plan_this_week')).toBeVisible();
  await page.getByTestId('plan_menu').click();
  await page.getByTestId('plan_copy_last_week').click();
  await expect(status(page, "Copied last week's meals (2)")).toBeVisible();
  const nextWeek = page.getByTestId('plan_days');
  await expect(nextWeek).toContainText('Tacos');
  await expect(nextWeek).not.toContainText('Cooked');

  await page.getByTestId('plan_shop').click();
  const shop = page.getByTestId('sheet_week_grocery');
  await expect(shop).toContainText('Tacos');
  await expect(shop.getByTestId('candidate')).toHaveCount(2);
  await expect(shop.getByTestId('plan_add_items')).toHaveText('Add 1 item');
  await shop.getByTestId('plan_add_items').click();
  // Its "Added to Groceries" waits behind the copy's Undo; the Groceries tab shows the item at the end.
  await expect(shop).toHaveCount(0);

  await page.getByTestId('plan_menu').click();
  await page.getByTestId('plan_clear_week').click();
  await expect(page.getByRole('dialog')).toContainText('Removes all 2 meals');
  await page.getByTestId('plan_clear_confirm').click();
  await expect(nextWeek).not.toContainText('Tacos');
  await status(page, 'Cleared this week').getByRole('button', { name: 'Undo' }).click();
  await expect(nextWeek).toContainText('Tacos');
  await page.getByTestId('plan_this_week').click();
  await expect(page.getByTestId('plan_this_week')).toHaveCount(0);

  // What the plan did shows on the other tabs.
  await tab(page, 'Groceries');
  await expect(page.getByTestId('grocery_list')).toContainText('Tortillas');
  await expect(page.getByTestId('grocery_list')).toContainText('Meal plan');
  await tab(page, 'Pantry');
  await expect(page.getByTestId('pantry_item_eggs')).toContainText('× 3');
});
