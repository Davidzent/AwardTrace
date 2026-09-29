import { expect, test } from '@playwright/test';
import { expectAccessible, expectResultsSettled } from './helpers';

test('searches by keyword, narrows by a facet, and keeps it all in the URL', async ({ page }) => {
  await page.goto('/');
  await page.getByLabel('Search awards').fill('fire');
  await page.getByRole('button', { name: 'Search', exact: true }).click();

  await expect(page).toHaveURL(/\?q=fire$/);
  await expectResultsSettled(page);
  await expectAccessible(page);

  // Narrow screens keep the filters in a drawer.
  const drawer = page.getByRole('button', { name: /^Filters/ });
  if (await drawer.isVisible()) {
    await drawer.click();
  }
  const state = page.getByRole('group', { name: 'State' }).getByRole('checkbox').first();
  const stateName = (await state.locator('xpath=..').innerText()).split(/\s/)[0] ?? '';
  // The checkbox follows the URL, so it shows as checked once the navigation it starts has committed.
  await state.click();
  await expect(page).toHaveURL(new RegExp(`state=${stateName}`));
  await expect(page.getByRole('group', { name: 'State' }).getByRole('checkbox', { name: stateName })).toBeChecked();
  if (await drawer.isVisible()) {
    await expectAccessible(page);
    await page.getByRole('button', { name: 'Show results' }).click();
  }

  await expect(page.getByRole('link', { name: `Remove filter: ${stateName}` })).toBeVisible();
  await expectResultsSettled(page);
  await expectAccessible(page);

  // The address alone reproduces the view.
  await page.reload();
  await expect(page.getByRole('link', { name: `Remove filter: ${stateName}` })).toBeVisible();
});

test('pages through results with links', async ({ page }) => {
  await page.goto('/?q=fire');
  await page.getByRole('link', { name: 'Next' }).click();

  await expect(page).toHaveURL(/page=2/);
  await expectResultsSettled(page);
});
