// Large text, VoiceOver and the keyboard (after v1, review A11Y-04 to A11Y-12; DESIGN 6.8, 18.2): the iPhone's largest
// text size (AX5, 312 %), what VoiceOver reads (names, roles, states and headings, from the accessibility tree
// WebKit builds), sheets keeping focus inside while the page behind is inert, and Tab order with a visible ring.
import { expect, test, type Locator, type Page } from '@playwright/test';
import {
  createHousehold,
  defaultListId,
  householdId,
  inDays,
  openApp,
  patchFields,
  seedGroceries,
  seedMeal,
  seedMember,
  seedPantry,
  seedRecipe,
  signUp,
  uidOf,
  uniqueEmail,
} from './support';

test.setTimeout(150_000);

/** The iPhone's largest accessibility size, AX5: 53 px body text against 17 (textScale.ts). */
const AX5 = 3.12;

/** Screenshots are compared on the Mac only, as in screens.spec.ts: Linux WebKit draws fonts differently. */
async function shot(page: Page, name: string) {
  if (process.env.CI) return;
  await page.waitForTimeout(600); // the tab's ripple and any highlight settle
  await expect(page).toHaveScreenshot(name);
}

type Box = { x: number; y: number; width: number; height: number };
const box = async (locator: Locator): Promise<Box> => (await locator.boundingBox())!;
/** [below] starts under [above] (stacked), not beside it. */
async function expectBelow(below: Locator, above: Locator) {
  const [a, b] = [await box(above), await box(below)];
  expect(b.y, `${below} under ${above}: ${JSON.stringify([a, b])}`).toBeGreaterThanOrEqual(a.y + a.height - 1);
}
/** [inner] stays inside [outer] left to right: nothing runs past a card's or the screen's edge. */
async function expectInside(inner: Locator, outer: Locator) {
  const [i, o] = [await box(inner), await box(outer)];
  expect(i.x).toBeGreaterThanOrEqual(o.x - 1);
  expect(i.x + i.width).toBeLessThanOrEqual(o.x + o.width + 1);
}

/** A household with what the large-text and VoiceOver checks look at, signed in on Home. */
async function setUp(page: Page, textScale: number) {
  await openApp(page, { textScale });
  const email = uniqueEmail('Sam');
  await signUp(page, 'Sam', email);
  const household = `Large text ${textScale} ${Date.now()}`;
  await createHousehold(page, household);
  const hid = await householdId(household);
  const uid = await uidOf(email);
  await seedPantry(hid, uid, [
    { id: 'chips', name: 'Tortilla chips', expiry: inDays(1) },
    { id: 'milk', name: 'Milk', quantity: 2, expiry: inDays(2) },
    { id: 'rice', name: 'Rice', quantity: 500, unit: 'g', expiry: null, location: 'pantry', min: 200 },
    { id: 'oil', name: 'Olive oil', quantity: 0, min: 1, location: 'pantry' },
  ]);
  await seedRecipe(hid, uid, {
    id: 'pancakes',
    title: 'Pancakes',
    servings: 4,
    totalMinutes: 20,
    ingredients: ['2 cups milk', '200 g rice'],
    steps: ['Whisk the eggs.', 'Fry in a hot pan.'],
  });
  await seedMeal(hid, uid, { id: 'today', date: inDays(0), slot: 'breakfast', title: 'Pancakes', cooks: [uid] });
  await seedMember(hid, 'u-jordan', 'Jordan');
  await seedGroceries(hid, uid, await defaultListId(hid), [
    { id: 'eggs', name: 'Eggs', quantity: 6, category: 'dairy', createdBy: 'u-jordan' },
    { id: 'bread', name: 'Bread', quantity: 1, category: 'bakery', checked: true },
  ]);
  // Someone waiting for approval: the owner's join card on Home (DESIGN 5.6).
  await patchFields(`households/${hid}/joinRequests/u-robin`, {
    status: 'pending',
    displayName: 'Robin Lee',
    joinCode: 'ABCDE-FGHJK',
    requestedAt: new Date(),
  });
  await expect(page.getByTestId('home_attention_row').first()).toBeVisible({ timeout: 15_000 });
  await expect(page.getByTestId('card_home_join_requests')).toBeVisible({ timeout: 15_000 });
  await page.evaluate(() => document.fonts.ready);
  return { hid, uid };
}

/**
 * Safari's Tab reaches only text fields unless "Press Tab to highlight each item" is on; Option-Tab reaches every
 * control, as Full Keyboard Access does on an iPhone with a keyboard.
 */
const NEXT = 'Alt+Tab';

const tab = (page: Page, name: string) => page.getByRole('navigation').getByRole('button', { name });

test('at the largest text size what sits beside a name goes under it, and nothing is cut off (A11Y-04, A11Y-12)', async ({
  page,
}) => {
  await setUp(page, AX5);

  // Tab labels: one line each, whole, at 1.2x at most (UX-14; at AX5 they read "Ho…").
  for (const name of ['Home', 'Pantry', 'Plan', 'Recipes', 'Groceries']) {
    const label = tab(page, name).locator('.m3-navigation-bar__label');
    const fits = await label.evaluate(
      (el) => el.scrollWidth <= el.clientWidth && el.getBoundingClientRect().height <= 20,
    );
    expect(fits, `${name} on one line, whole`).toBe(true);
  }

  // Home: the chip under the name, inside the card; Running low's badge under the name; Review under its sentence.
  const attention = page.getByTestId('card_home_attention');
  const chips = page.getByTestId('home_attention_row').filter({ hasText: 'Tortilla chips' });
  await expectBelow(chips.getByTestId('status_chip'), chips.locator('.sn-home-row__name'));
  await expectInside(chips.getByTestId('status_chip'), attention);
  const low = page.getByTestId('home_low_row').first();
  await expectBelow(low.locator('.sn-badge'), low.locator('.sn-home-row__name'));
  const join = page.getByTestId('card_home_join_requests');
  await expectBelow(join.getByRole('button', { name: 'Review' }), join.locator('.sn-home-join__text'));
  await expectInside(join.getByRole('button', { name: 'Review' }), join);
  await shot(page, 'home_font3.png');

  // Pantry: the Low badge under the name; the last row clears the floating button (measured, as on Android).
  await tab(page, 'Pantry').click();
  const oil = page.getByTestId('pantry_item_out_p-oil');
  await expect(oil).toBeVisible();
  await expectBelow(oil.locator('.sn-badge'), oil.locator('.sn-pantry-row__name-text'));
  await expect(page.locator('[data-remote]')).toHaveCount(0);
  await shot(page, 'pantry_font3.png');
  const list = page.getByTestId('screen_pantry').locator('.sn-pantry-list');
  await list.evaluate((el) => (el.scrollTop = el.scrollHeight));
  const lastRow = list.locator('.sn-pantry-row').last();
  const fab = await box(page.getByTestId('fab_add_item'));
  const last = await box(lastRow);
  expect(last.y + last.height).toBeLessThanOrEqual(fab.y);

  // Groceries at the largest size.
  await tab(page, 'Groceries').click();
  await expect(page.getByTestId('grocery_item_eggs')).toBeVisible();
  // The restock engine adds the out-of-stock olive oil after its 5 s wait (DESIGN 9.6).
  await expect(page.getByTestId('grocery_item_low_p-oil')).toBeVisible({ timeout: 15_000 });
  await expect(page.locator('[data-remote]')).toHaveCount(0);
  await shot(page, 'groceries_font3.png');

  // Recipe detail: the servings stepper under "Ingredients"; cook mode's Ingredients on a line of its own.
  await tab(page, 'Recipes').click();
  await page.getByTestId('recipe_card').first().getByRole('button').first().click();
  const section = page.locator('.sn-recipe-section').filter({ hasText: 'Ingredients' });
  await expectBelow(section.locator('.sn-stepper'), section.getByRole('heading', { name: 'Ingredients' }));
  await expectInside(section.locator('.sn-stepper'), page.getByTestId('screen_recipe_detail'));
  await page.getByTestId('recipe_cook').click();
  const ingredients = page.locator('.sn-cook__bar').getByRole('button', { name: 'Ingredients' });
  await expectBelow(page.getByTestId('cook_next'), ingredients);
  // I cooked this: each amount under its ingredient.
  await page.getByRole('banner').getByRole('button', { name: 'Back' }).click();
  await page.getByTestId('recipe_cooked').click();
  await expect(page.getByTestId('sheet_cooked')).toBeVisible();
  await page.waitForTimeout(400); // the sheet's slide-in
  const row = page.getByTestId('sheet_cooked').locator('.sn-cook-row').first();
  await expectBelow(row.locator('.sn-cook-row__amount'), row.locator('.sn-cook-row__main'));
});

test('VoiceOver reads rows as one sentence, with the right roles, states and headings (A11Y-07 to A11Y-09, A11Y-12)', async ({
  page,
}) => {
  await setUp(page, 1);

  // Home: one stop per row and per meal; the settings badge's number isn't read twice.
  await expect(page.getByTestId('home_attention_row').filter({ hasText: 'Milk' })).toMatchAriaSnapshot(
    '- text: Milk, Expires in 2 days',
  );
  await expect(page.getByTestId('home_low_row').filter({ hasText: 'Olive oil' })).toMatchAriaSnapshot(
    '- text: Olive oil, Out',
  );
  await expect(page.getByTestId('home_today_meal')).toMatchAriaSnapshot('- text: "Breakfast: Pancakes, cooking: You"');
  await expect(page.getByRole('button', { name: 'Open settings, 1 join request waiting', exact: true })).toBeVisible();

  // Pantry's date tiles are one choice of four (its group headings are checked in pantry.spec.ts).
  await tab(page, 'Pantry').click();
  await page.getByTestId('fab_add_item').click();
  const dates = page.getByRole('radiogroup', { name: 'Expiration date' });
  await expect(dates.getByRole('radio')).toHaveCount(4);
  await dates.getByRole('radio', { name: '+1 week' }).click();
  await expect(dates.getByRole('radio', { name: '+1 week' })).toBeChecked();
  // A sheet is modal: Tab stays inside it and the page behind can't be reached (the web has no tour, DESIGN 18.1;
  // the app's tour hides the screen behind it, A11Y-10). Its drag handle is a 44 pt target at least.
  const sheet = page.getByTestId('sheet_add_item');
  const handle = await box(sheet.locator('.m3-sheet__handle'));
  expect(handle.width).toBeGreaterThanOrEqual(44);
  expect(handle.height).toBeGreaterThanOrEqual(44);
  for (let i = 0; i < 25; i++) {
    await page.keyboard.press(NEXT);
    // In the sheet, or past the page to Safari's own controls (the body): never the screen or tabs behind it.
    const where = await page.evaluate(() => {
      const el = document.activeElement;
      return !el || el === document.body ? 'browser' : el.closest('dialog') ? 'sheet' : 'behind';
    });
    expect(where).not.toBe('behind');
  }
  await page.keyboard.press('Escape');

  // Groceries: the row's checkbox reads the whole row; the cart header is a heading that says expanded or collapsed.
  await tab(page, 'Groceries').click();
  await expect(page.getByRole('checkbox', { name: 'Eggs, 6 pieces, added by Jordan', exact: true })).not.toBeChecked();
  const cart = page.getByRole('heading', { name: /In cart/ });
  await expect(cart.getByRole('button')).toHaveAttribute('aria-expanded', /true|false/);
  await expect(page.getByRole('heading', { name: 'Dairy' })).toBeVisible();

  // Recipes: the card reads its title and time; the star is one name, "Favorite", with its state.
  await tab(page, 'Recipes').click();
  const card = page.getByTestId('recipe_card').first();
  await expect(card.getByRole('button', { name: 'Pancakes, Total 20 min', exact: true })).toBeVisible();
  await expect(card.getByRole('button', { name: 'Favorite', exact: true })).toHaveAttribute('aria-pressed', 'false');
  await card.getByRole('button', { name: 'Pancakes, Total 20 min' }).click();
  // Recipe detail has no empty heading (axe's empty-heading, A11Y-12): its title is in the content.
  await expect(page.getByTestId('recipe_title')).toBeVisible();
  const empty = await page
    .locator('h1, h2, h3, h4, h5, h6')
    .evaluateAll((all) => all.filter((h) => !h.textContent?.trim()).map((h) => h.outerHTML));
  expect(empty).toEqual([]);
  const favorite = page.getByRole('button', { name: 'Favorite', exact: true });
  await favorite.click();
  await expect(favorite).toHaveAttribute('aria-pressed', 'true');
  // I cooked this: the amount says what it is for.
  await page.getByTestId('recipe_cooked').click();
  await expect(
    page
      .getByTestId('sheet_cooked')
      .getByRole('textbox', { name: /^Amount of .+ to use, in / })
      .first(),
  ).toBeVisible();
  await page.keyboard.press('Escape');
  // Cook mode: the progress bar has a name, and the second Back says what it does (aria-progressbar-name, A11Y-12).
  await page.getByTestId('recipe_cook').click();
  await expect(page.getByRole('progressbar', { name: 'Step 1 of 2' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Back to the previous step' })).toBeDisabled();
  await page.getByTestId('cook_next').click();
  await expect(page.getByRole('progressbar', { name: 'Step 2 of 2' })).toBeVisible();
});

test('the keyboard reaches the screen before the tab bar, with a visible focus ring (A11Y-11)', async ({ page }) => {
  await setUp(page, 1);
  await tab(page, 'Pantry').click();
  await expect(page.getByTestId('pantry_item_milk')).toBeVisible();
  // Start at the top of the screen (the tap on the Pantry tab left focus in the tab bar): the app bar's first button.
  await page.getByTestId('screen_pantry').getByRole('banner').getByRole('button').first().focus();

  // Walk forward with Tab and note where focus goes.
  const stops: { where: 'screen' | 'tabs' | 'other'; ring: string }[] = [];
  for (let i = 0; i < 40; i++) {
    await page.keyboard.press(NEXT);
    stops.push(
      await page.evaluate(() => {
        const el = document.activeElement as HTMLElement;
        const style = getComputedStyle(el);
        return {
          where: el.closest('nav') ? 'tabs' : el.closest('[data-testid="screen_pantry"]') ? 'screen' : 'other',
          // A text field shows focus with its own 2 px outline box (Material's focused field), not the ring.
          ring: el.tagName === 'INPUT' ? 'field' : `${style.outlineStyle} ${style.outlineWidth}`,
        } as const;
      }),
    );
  }
  const firstScreen = stops.findIndex((s) => s.where === 'screen');
  const firstTabs = stops.findIndex((s) => s.where === 'tabs');
  expect(firstScreen).toBeGreaterThanOrEqual(0);
  expect(firstTabs).toBeGreaterThan(firstScreen);
  // Every row and control on the way shows the 2 px ring.
  for (const stop of stops.filter((s) => s.where !== 'other')) expect(['solid 2px', 'field']).toContain(stop.ring);
  // A row's ring is drawn inside it, where its clipped row doesn't cut it off.
  const row = page.getByTestId('pantry_item_milk').locator('.sn-pantry-row__main');
  await row.focus();
  await page.keyboard.press(`Shift+${NEXT}`);
  await page.keyboard.press(NEXT);
  expect(await row.evaluate((el) => getComputedStyle(el).outlineOffset)).toBe('-2px');
});
