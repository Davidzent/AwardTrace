import { expect, test } from '@playwright/test';
import { expectAccessible, expectResultsSettled } from './helpers';

test('opens an award from the results, then its recipient, and back', async ({ page }) => {
  await page.goto('/?q=fire&sort=largest');
  await expectResultsSettled(page);
  // A card shows the highlighted fragment, not the whole description, so the award is matched by its address.
  const firstResult = page.getByRole('article').first().getByRole('heading').getByRole('link');
  const href = (await firstResult.getAttribute('href')) ?? '';
  await firstResult.click();

  await expect(page).toHaveURL(href);
  await expect(page.getByRole('heading', { level: 1 })).not.toBeEmpty();
  await expect(page.getByRole('table', { name: 'Modifications, newest first' })).toBeVisible();
  await expectAccessible(page);

  await page.getByRole('link', { name: 'View recipient →' }).click();
  await expect(page).toHaveURL(/\/recipients\/[A-Z0-9]{12}$/);
  await expect(page.getByRole('heading', { name: 'Awards' })).toBeVisible();
  await expectResultsSettled(page);
  await expectAccessible(page);

  await page.goBack();
  await page.getByRole('button', { name: '← Back' }).click();
  await expect(page).toHaveURL(/\?q=fire&sort=largest$/);
});

test('offers a search for an award that does not exist', async ({ page }) => {
  await page.goto('/awards/CONT_AWD_DOES_NOT_EXIST');

  await expect(page.getByRole('heading', { name: 'No award with this ID' })).toBeVisible();
  await expect(page.getByRole('search')).toBeVisible();
  await expectAccessible(page);
});
