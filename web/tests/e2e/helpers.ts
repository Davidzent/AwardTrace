import AxeBuilder from '@axe-core/playwright';
import { expect, type Page } from '@playwright/test';

/** Axe reports no serious or critical violation of WCAG 2.2 A or AA on the page as it stands (doc 08). */
export async function expectAccessible(page: Page) {
  const results = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa'])
    .analyze();
  const violations = results.violations
    .filter((violation) => violation.impact === 'serious' || violation.impact === 'critical')
    .map((violation) => `${violation.id}: ${violation.nodes.map((node) => node.target.join(' ')).join(', ')}`);
  expect(violations).toEqual([]);
}

/** Results have loaded and aren't being replaced: no skeleton, and no dimmed previous results. */
export async function expectResultsSettled(page: Page) {
  const results = page.getByRole('region', { name: 'Results' });
  await expect(results).toBeVisible();
  await expect(results).not.toHaveAttribute('aria-busy', 'true');
}
