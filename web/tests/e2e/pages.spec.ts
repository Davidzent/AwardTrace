import { expect, test } from '@playwright/test';
import { expectAccessible } from './helpers';

test('states the condition of each part of the pipeline in words', async ({ page }) => {
  await page.goto('/status');

  for (const part of ['Ingest', 'Pipeline', 'Index', 'Freshness']) {
    await expect(page.getByRole('region', { name: new RegExp(`^${part}: (OK|Degraded|Down|Unknown)$`) })).toBeVisible();
  }
  await expect(page.getByText(/^Updated \d+ s ago/)).toBeVisible();
  await expectAccessible(page);
});

test('explains how the site works', async ({ page }) => {
  await page.goto('/about');

  await expect(page.getByRole('heading', { name: 'How AwardTrace works' })).toBeVisible();
  await expectAccessible(page);
});

test('keeps the layout around an unknown address', async ({ page }) => {
  await page.goto('/no/such/page');

  await expect(page.getByRole('heading', { name: 'Page not found' })).toBeVisible();
  await expect(page.getByRole('navigation', { name: 'Main' })).toBeVisible();
  await expectAccessible(page);
});
