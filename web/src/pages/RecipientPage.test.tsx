import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render, screen } from '@testing-library/react';
import { createMemoryRouter } from 'react-router';
import { RouterProvider } from 'react-router/dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { Schemas } from '../api/client';
import { routes } from '../app/routes';

const PROFILE: Schemas['RecipientDetail'] = {
  uei: 'MN5KRX2W9R46',
  name: 'NOMADIC LAND CAMPS, LLC',
  parent: { uei: 'MN5KRX2W9R46', name: 'NOMADIC LAND CAMPS, LLC' },
  location: { city: 'BOISE', state_code: 'ID', country_code: 'USA' },
  totals: { award_count: 3, total_obligated: '155950.00' },
  top_agencies: [{ code: '012', name: 'Department of Agriculture', award_count: 3, total_obligated: '155950.00' }],
  top_naics: [{ code: '541512', name: 'COMPUTER SYSTEMS DESIGN SERVICES', award_count: 1, total_obligated: '100000.00' }],
  awards_by_fiscal_year: [
    { fiscal_year: 2025, award_count: 1, total_obligated: '950.00' },
    { fiscal_year: 2026, award_count: 2, total_obligated: '155000.00' },
  ],
};

const AWARDS: Schemas['SearchResults'] = {
  total: 1,
  total_obligated: '55000.00',
  took_ms: 3,
  page: 1,
  size: 20,
  results: [{ award_id: 'CONT_AWD_CAMPS', description: 'Nomadic land camps', total_obligated: '55000.00' }],
};

function renderAt(path: string, profile: () => Response) {
  const fetch = vi.fn(async (url: string) => (url.includes('/awards') ? Response.json(AWARDS) : profile()));
  vi.stubGlobal('fetch', fetch);
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <RouterProvider router={createMemoryRouter(routes, { initialEntries: [path] })} />
    </QueryClientProvider>,
  );
  return fetch;
}

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe('RecipientPage', () => {
  it('shows the profile and the recipient awards the URL asks for', async () => {
    const fetch = renderAt('/recipients/MN5KRX2W9R46?sort=largest', () => Response.json(PROFILE));

    expect((await screen.findByRole('heading', { level: 1 })).textContent).toBe('NOMADIC LAND CAMPS, LLC');
    expect(screen.getByText('UEI MN5KRX2W9R46 · BOISE, ID, USA')).toBeDefined();
    expect(screen.getByText('FY2025 to FY2026')).toBeDefined();
    expect(screen.getByText('541512 COMPUTER SYSTEMS DESIGN SERVICES')).toBeDefined();
    expect(await screen.findByRole('link', { name: 'Nomadic land camps' })).toBeDefined();
    expect(fetch).toHaveBeenCalledWith('/api/v1/recipients/MN5KRX2W9R46/awards?sort=largest', expect.anything());
  });

  it('treats a UEI the API rejects as a missing recipient', async () => {
    renderAt('/recipients/abc', () =>
      Response.json({ type: 'invalid-uei', status: 400 }, { status: 400, headers: { 'Content-Type': 'application/problem+json' } }),
    );

    expect((await screen.findByRole('heading', { level: 1 })).textContent).toBe('No recipient with this UEI');
  });
});
