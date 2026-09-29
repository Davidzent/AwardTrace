import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render, screen } from '@testing-library/react';
import { createMemoryRouter } from 'react-router';
import { RouterProvider } from 'react-router/dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { Schemas } from '../api/client';
import { routes } from '../app/routes';

const AWARD: Schemas['AwardDetail'] = {
  award_id: 'CONT_AWD_CAMPS',
  piid: '12024B26M0522',
  award_type: 'C',
  description: 'Nomadic land camps for fire crews',
  recipient: { uei: 'MN5KRX2W9R46', name: 'NOMADIC LAND CAMPS, LLC' },
  agency: { code: '012', name: 'Department of Agriculture', subtier_name: 'Forest Service' },
  total_obligated: '55000.00',
  period_of_performance: { start: '2026-07-16', end: '2026-09-30' },
  transactions: [
    { transaction_id: 't2', modification_number: 'P00002', action_date: '2026-08-24', federal_action_obligation: '-27500.00' },
    { transaction_id: 't1', modification_number: '0', action_date: '2026-07-16', federal_action_obligation: '82500.00' },
  ],
  transactions_truncated: false,
  source_modified_at: '2026-09-06T03:11:00Z',
  usaspending_url: 'https://www.usaspending.gov/award/CONT_AWD_CAMPS/',
};

function renderAward(respond: () => Response) {
  vi.stubGlobal('fetch', vi.fn(async () => respond()));
  const router = createMemoryRouter(routes, { initialEntries: ['/awards/CONT_AWD_CAMPS'] });
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  );
}

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe('AwardPage', () => {
  it('shows the award with exact amounts and labels a deobligation', async () => {
    renderAward(() => Response.json(AWARD));

    expect((await screen.findByRole('heading', { level: 1 })).textContent).toBe('Nomadic land camps for fire crews');
    expect(screen.getByText('$55,000.00')).toBeDefined();
    expect(screen.getByText('Jul 16, 2026 to Sep 30, 2026')).toBeDefined();
    expect(screen.getByText('-$27,500.00')).toBeDefined();
    expect(screen.getAllByText('Deobligation')).toHaveLength(1);
    expect(screen.getByRole('link', { name: 'View recipient →' }).getAttribute('href')).toBe('/recipients/MN5KRX2W9R46');
  });

  it('offers a search when the award does not exist', async () => {
    renderAward(() =>
      Response.json(
        { type: 'award-not-found', status: 404, detail: 'No award with ID CONT_AWD_CAMPS' },
        { status: 404, headers: { 'Content-Type': 'application/problem+json' } },
      ),
    );

    expect((await screen.findByRole('heading', { level: 1 })).textContent).toBe('No award with this ID');
    expect(screen.getByRole('search')).toBeDefined();
  });
});
