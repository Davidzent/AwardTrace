import { expect, test } from '@playwright/test';
import { expectAccessible, waitForPublicSans } from './helpers';

const LANDING = 'http://localhost:5174/';
const FUNCTION_URL = 'https://wake.example/';

/**
 * The landing page (ADR 0019) with its wake button (ADR 0020), whole, at both widths. The dev build has no function URL,
 * so the page's HTML gets a stand-in, answered here as a sleeping host. The rest of the page is static.
 */
test('the landing page explains the project and offers to wake the app', async ({ page }) => {
  await page.route(LANDING, async (route) => {
    const response = await route.fetch();
    const html = (await response.text()).replace('data-wake-url=""', `data-wake-url="${FUNCTION_URL}"`);
    await route.fulfill({ response, body: html });
  });
  await page.route(`${FUNCTION_URL}**`, (route) => route.fulfill({ json: { status: 'stopped' } }));
  await page.goto(LANDING);

  await expect(page.getByRole('status')).toHaveText('The live app is asleep. Starting it takes about 5 minutes.');
  await expect(page.getByRole('button', { name: 'Start it' })).toBeVisible();
  await expect(page.getByText('65.7%')).toBeVisible();
  await expectAccessible(page);

  await page.waitForLoadState('networkidle');
  await waitForPublicSans(page);
  await expect(page).toHaveScreenshot('landing.png', { fullPage: true });
});
