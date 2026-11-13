// Recipes on an iPhone, view only (DESIGN 5.5, 18.1, 18.8 W4), against the emulators. Recipes are seeded as the
// Android app saves them: the web does not add recipes.
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

const tab = (page: Page, name: string) => page.getByRole('navigation').getByRole('button', { name }).click();
const cards = (page: Page) => page.getByTestId('recipe_grid').getByTestId('recipe_card');

// A long flow: room for slow machines.
test.setTimeout(90_000);

test('the box, detail and scaling, Add to grocery list, I cooked this, cook mode, edit and delete with Undo', async ({
  page,
}) => {
  const email = uniqueEmail('Sam');
  const household = `Recipes ${Date.now()}`;
  await openApp(page);
  await signUp(page, 'Sam', email);
  await createHousehold(page, household);
  const hid = await householdId(household);
  const uid = await uidOf(email);
  await seedPantry(hid, uid, [
    { id: 'eggs', name: 'Eggs', quantity: 6, expiry: inDays(2) },
    { id: 'rice', name: 'Rice', quantity: 900, unit: 'g', expiry: null, location: 'pantry' },
  ]);
  await seedRecipe(hid, uid, {
    id: 'curry',
    title: 'Green curry',
    servings: 2,
    totalMinutes: 75,
    tags: ['Dinner'],
    ingredients: ['3 large eggs', '500 g rice', '2 tbsp soy sauce', '1 onion, diced'],
    steps: ['Boil the rice.', 'Fry the eggs.'],
  });
  await seedRecipe(hid, uid, {
    id: 'pie',
    title: 'Apple pie',
    tags: ['Baking'],
    favorite: true,
    ingredients: ['3 apples'],
    steps: ['Bake.'],
    sourceUrl: 'https://www.seriouseats.com/apple-pie',
    sourceName: 'Serious Eats',
    createdSecondsAgo: 600,
  });

  // Home suggests the recipe that uses the eggs expiring soon; it opens on the Home tab.
  const useItUp = page.getByTestId('card_home_use_it_up');
  await expect(useItUp).toContainText('Green curry');
  await expect(useItUp).toContainText('Uses Eggs (2 days)');
  await useItUp.getByRole('button', { name: /Green curry/ }).click();
  await expect(page.getByTestId('recipe_title')).toHaveText('Green curry');
  await page.getByRole('button', { name: 'Back' }).click();

  // The box: newest first, search by ingredient, the Favorites chip.
  await tab(page, 'Recipes');
  await expect(cards(page)).toHaveText([/Green curry.*1 hr 15 min/, /Apple pie/]);
  await page.getByTestId('recipe_search').fill('rice');
  await expect(cards(page)).toHaveCount(1);
  await page.getByRole('button', { name: 'Clear search' }).click();
  // No All chip (after v1, R16): tapping the chosen chip again shows everything.
  await expect(page.getByRole('button', { name: 'All', exact: true })).toHaveCount(0);
  const favorites = page.getByRole('button', { name: 'Favorites', exact: true });
  await favorites.click();
  await expect(cards(page)).toHaveText([/Apple pie/]);
  await favorites.click();
  await expect(favorites).toHaveAttribute('aria-pressed', 'false');
  await expect(cards(page)).toHaveCount(2);

  // An imported recipe whose By name is only the site: one source line, the site chip (after v1, R10).
  await cards(page).filter({ hasText: 'Apple pie' }).getByRole('button', { name: 'Apple pie' }).click();
  await expect(page.getByRole('link', { name: 'seriouseats.com' })).toBeVisible();
  await expect(page.getByTestId('screen_recipe_detail')).not.toContainText('By Serious Eats');
  await page.getByRole('banner').getByRole('button', { name: 'Back' }).click();

  // Detail: the stepper scales only what it can.
  await cards(page).filter({ hasText: 'Green curry' }).getByRole('button', { name: 'Green curry' }).click();
  await expect(page.getByTestId('recipe_servings')).toHaveText('2 servings');
  // One servings control (after v1, R9): no chip repeating the stepper's number.
  await expect(page.locator('.sn-info-chips')).not.toContainText('servings');
  // Start cooking is a bar under the content and covers nothing (after v1, UX-12).
  const bar = (await page.getByTestId('recipe_cook').boundingBox())!;
  const cookedThis = (await page.getByTestId('recipe_cooked').boundingBox())!;
  expect(cookedThis.y + cookedThis.height).toBeLessThanOrEqual(bar.y);
  await page.getByRole('button', { name: 'More servings' }).click();
  await expect(page.getByTestId('recipe_servings')).toHaveText('3 servings');
  const lines = page.locator('.sn-checklist__line');
  await expect(lines).toHaveText(['4½ large eggs', '750 g rice', '3 tbsp soy sauce', '1½ onion, diced']);
  await page.getByRole('button', { name: 'Fewer servings' }).click();

  // Add to grocery list: what is probably in the pantry starts unchecked.
  await page.getByTestId('recipe_add_to_list').click();
  const sheet = page.getByTestId('sheet_add_to_list');
  await expect(sheet.getByTestId('candidate')).toHaveCount(4);
  await expect(sheet.getByTestId('candidate').first()).toContainText('Probably in pantry');
  await expect(sheet.getByTestId('recipe_add_items')).toHaveText('Add 2 items');
  // Nothing selected: the button says what to do instead of "Add 0 items" (after v1, UX-18).
  const chosen = sheet.getByRole('checkbox', { checked: true });
  await chosen.first().click();
  await chosen.first().click();
  await expect(sheet.getByTestId('recipe_add_items')).toHaveText('Select items to add');
  await expect(sheet.getByTestId('recipe_add_items')).toBeDisabled();
  await sheet.getByRole('checkbox', { name: '1 onion, diced' }).click();
  await sheet.getByRole('checkbox', { name: '2 tbsp soy sauce' }).click();
  await expect(sheet.getByTestId('recipe_add_items')).toHaveText('Add 2 items');
  await sheet.getByTestId('recipe_add_items').click();
  await expect(page.getByRole('status').filter({ hasText: 'Added to Groceries' })).toBeVisible();

  // I cooked this: eggs and rice come off the pantry, with Undo for all of it.
  await page.getByTestId('recipe_cooked').click();
  const cooked = page.getByTestId('sheet_cooked');
  await expect(cooked.getByTestId('cook_amount_p-eggs')).toHaveValue('3');
  await expect(cooked.getByTestId('cook_amount_p-rice')).toHaveValue('500');
  await cooked.getByTestId('recipe_cooked_use').click();
  await expect(
    page.getByRole('status').filter({ hasText: 'Cooked Green curry. The pantry is updated.' }),
  ).toBeVisible();

  // Cook mode: one step at a time. The snackbar goes first: it waits while the pointer rests on it (A11Y-02), and in
  // Cook mode it sits over Next.
  await expect(page.getByRole('status')).toHaveCount(0, { timeout: 15_000 });
  await page.getByTestId('recipe_cook').click();
  await expect(page.getByTestId('cook_step')).toHaveText('Boil the rice.');
  await page.getByTestId('cook_next').click();
  await expect(page.getByTestId('cook_step')).toHaveText('Fry the eggs.');
  await expect(page.getByTestId('cook_next')).toBeDisabled();
  await page.getByRole('banner').getByRole('button', { name: 'Back' }).click();

  // Edit, then delete with Undo.
  await page.getByTestId('recipe_edit').click();
  await page.getByTestId('recipe_field_title').fill('Green curry with rice');
  await page.getByTestId('recipe_field_steps').fill('Boil the rice.\nFry the eggs.\nServe.');
  await page.getByTestId('recipe_save').click();
  await expect(page.getByTestId('recipe_title')).toHaveText('Green curry with rice');
  await expect(page.locator('.sn-recipe-step')).toHaveCount(3);
  await page.getByTestId('recipe_more').click();
  // No dialog: it goes at once, with Undo (after v1, one rule for confirmations, R13).
  await page.getByTestId('recipe_delete').click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await expect(page.getByTestId('screen_recipes')).toBeVisible();
  await expect(cards(page)).toHaveCount(1);
  // Snackbars come one at a time: the Undo of this one, not I cooked this's.
  await page
    .getByRole('status')
    .filter({ hasText: 'Deleted Green curry with rice' })
    .getByRole('button', { name: 'Undo' })
    .click();
  await expect(cards(page)).toHaveCount(2);

  // What the recipe added and used shows on the other tabs.
  await tab(page, 'Groceries');
  const list = page.getByTestId('grocery_list');
  await expect(list).toContainText('Soy sauce');
  await expect(list).toContainText('Onion');
  await expect(list.locator('[data-testid^="grocery_item_"]').first()).toContainText('Recipe');
  await tab(page, 'Pantry');
  await expect(page.getByTestId('pantry_item_eggs')).toContainText('× 3');
  await expect(page.getByTestId('pantry_item_rice')).toContainText('400 g');
});

test('only web links open: a stored javascript: link shows no Open original', async ({ page }) => {
  const email = uniqueEmail('Sam');
  const household = `Links ${Date.now()}`;
  await openApp(page);
  await signUp(page, 'Sam', email);
  await createHousehold(page, household);
  const hid = await householdId(household);
  const uid = await uidOf(email);
  // Written straight to Firestore, as a modified app could; the rules check sizes, not schemes.
  await seedRecipe(hid, uid, { id: 'bad', title: 'Odd link', sourceUrl: 'javascript:alert(1)', steps: ['Mix.'] });
  await seedRecipe(hid, uid, {
    id: 'good',
    title: 'Good link',
    sourceUrl: 'https://example.com/pie',
    steps: ['Bake.'],
  });
  await tab(page, 'Recipes');
  await page.getByRole('button', { name: 'Odd link', exact: true }).click();
  await expect(page.getByTestId('recipe_title')).toHaveText('Odd link');
  await expect(page.getByTestId('recipe_open_original')).toHaveCount(0);
  await page.getByRole('banner').getByRole('button', { name: 'Back' }).click();
  await page.getByRole('button', { name: 'Good link', exact: true }).click();
  await expect(page.getByTestId('recipe_open_original')).toHaveAttribute('href', 'https://example.com/pie');
  await expect(page.getByRole('link', { name: 'example.com' })).toHaveAttribute('rel', 'noopener noreferrer');
});
