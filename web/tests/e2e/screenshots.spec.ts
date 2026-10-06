import { expect, type Page, test } from '@playwright/test';
import award from './fixtures/award.json' with { type: 'json' };
import network from './fixtures/network.json' with { type: 'json' };
import recipientAwards from './fixtures/recipient-awards.json' with { type: 'json' };
import recipient from './fixtures/recipient.json' with { type: 'json' };
import searchHome from './fixtures/search-home.json' with { type: 'json' };
import searchResults from './fixtures/search-results.json' with { type: 'json' };
import status from './fixtures/status.json' with { type: 'json' };
import subawards from './fixtures/subawards.json' with { type: 'json' };
import { expectAccessible, waitForPublicSans } from './helpers';

/**
 * Each main page, whole, at both widths (doc 16), so a change that breaks a layout fails here even when every flow
 * still works. Every API call answers from tests/e2e/fixtures and the clock stands still, so a screenshot changes only
 * when the page does, whatever data the local stack holds. The fixtures are real responses from the Agriculture data,
 * except the network and the subawards, which the local data lacks, and a few classifier categories added to show
 * the AI mark. Run `npx playwright test screenshots --update-snapshots` after an intended change, review the new
 * images, and commit only those of the pages you meant to change: the update also rewrites images that differ by a
 * few pixels of antialiasing, which the comparison already tolerates.
 */
const PAGES = [
  { name: 'home', path: '/' },
  { name: 'search', path: '/?q=fire' },
  { name: 'award', path: '/awards/CONT_AWD_AG3K25C170001_12H2_-NONE-_-NONE-' },
  { name: 'recipient', path: '/recipients/CKV2L9GZKJK3' },
  { name: 'status', path: '/status' },
];

/** The fixture that answers an API request. */
function fixture(url: URL): unknown {
  const path = url.pathname.replace('/api/v1/', '');
  if (path === 'awards/search') {
    return url.searchParams.has('q') ? searchResults : searchHome;
  }
  if (path === 'status') {
    return status;
  }
  if (path.endsWith('/subawards')) {
    return subawards;
  }
  if (path.startsWith('awards/')) {
    return award;
  }
  if (path.endsWith('/network')) {
    return network;
  }
  return path.endsWith('/awards') ? recipientAwards : recipient;
}

/** Opens a page against the fixtures and the stopped clock, and waits until it has rendered with nothing left to load. */
async function open(page: Page, path: string) {
  await page.clock.setFixedTime(new Date('2026-10-05T16:00:00Z'));
  await page.route('**/api/v1/**', (route) => route.fulfill({ json: fixture(new URL(route.request().url())) }));
  await page.goto(path);
  // A lazy page shows no heading until its code arrives.
  await expect(page.getByRole('heading', { level: 1 })).toBeAttached();
  await expect(page.locator('[aria-busy="true"]')).toHaveCount(0);
  await page.waitForLoadState('networkidle');
}

for (const { name, path } of PAGES) {
  test(`${name} page looks as it did`, async ({ page }) => {
    await open(page, path);
    await waitForPublicSans(page);
    await expect(page).toHaveScreenshot(`${name}.png`, { fullPage: true });
  });
}

// Dark mode's colors are a second set of token values (doc 16); axe checks their contrast on every page.
test.describe('in dark mode', () => {
  test.use({ colorScheme: 'dark' });

  for (const { name, path } of PAGES) {
    test(`${name} page is accessible`, async ({ page }) => {
      await open(page, path);
      await expectAccessible(page);
    });
  }
});
