import { describe, expect, it } from 'vitest';
import { activeFilters, clearFiltersHref, selecting } from './filters';

const FACETS = {
  agency: [{ value: '012', label: 'Department of Agriculture', count: 10 }],
  state: [{ value: 'ID', count: 4 }],
};

describe('activeFilters', () => {
  it('names each selected value and links to the search without it', () => {
    const query = { q: 'fire', agency: ['012'], state: ['ID', 'MT'], fiscal_year: [2026], page: 3 };

    expect(activeFilters(query, FACETS)).toEqual([
      { key: 'agency:012', label: 'Department of Agriculture', href: '?q=fire&state=ID&state=MT&fiscal_year=2026' },
      { key: 'state:ID', label: 'ID', href: '?q=fire&agency=012&state=MT&fiscal_year=2026' },
      { key: 'state:MT', label: 'MT', href: '?q=fire&agency=012&state=ID&fiscal_year=2026' },
      { key: 'fiscal_year:2026', label: 'FY2026', href: '?q=fire&agency=012&state=ID&state=MT' },
    ]);
  });

  it('describes ranges, open at either end', () => {
    const chips = activeFilters({ min_amount: '25000', from: '2026-01-01', to: '2026-03-31' }, undefined);

    expect(chips.map((chip) => chip.label)).toEqual([
      'Amount from $25K',
      'Last action from Jan 1, 2026 to Mar 31, 2026',
    ]);
    expect(chips[0]?.href).toBe('?from=2026-01-01&to=2026-03-31');
  });

  it('names the category source chosen', () => {
    expect(activeFilters({ q: 'fire', category_source: 'llm' }, undefined)).toEqual([
      { key: 'category-source', label: 'AI categories only', href: '?q=fire' },
    ]);
  });
});

describe('selecting', () => {
  it('turns fiscal years back into numbers and an empty selection into none', () => {
    expect(selecting('fiscal_year', ['2025', '2026'])).toEqual({ fiscal_year: [2025, 2026] });
    expect(selecting('state', [])).toEqual({ state: undefined });
  });
});

it('clears every filter but keeps the keyword and sort', () => {
  expect(clearFiltersHref({ q: 'fire', sort: 'largest', state: ['ID'], min_amount: '5', page: 2 })).toBe(
    '?q=fire&sort=largest',
  );
});
