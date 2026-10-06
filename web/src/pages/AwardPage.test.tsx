import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, fireEvent, render, screen, within } from '@testing-library/react';
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
  category: { code: 'OTHER', label: 'Other', source: 'baseline', baseline_code: 'OTHER', baseline_label: 'Other' },
  source_modified_at: '2026-09-06T03:11:00Z',
  usaspending_url: 'https://www.usaspending.gov/award/CONT_AWD_CAMPS/',
};

/** Twelve subawards, newest first, served a page at a time. */
function subawardPage(url: URL): Schemas['SubawardPage'] {
  const page = Number(url.searchParams.get('page'));
  const size = Number(url.searchParams.get('size'));
  const all = Array.from({ length: 12 }, (_, index) => ({
    subaward_key: `S${index + 1}`,
    sub_recipient: { uei: 'E2QCEKQXLN48', name: `VENDOR ${index + 1}` },
    amount: '100.00',
    action_date: '2026-08-01',
  }));
  return { total: all.length, total_is_capped: false, page, size, results: all.slice((page - 1) * size, page * size) };
}

function renderAward(respond: (url: URL) => Response) {
  vi.stubGlobal('fetch', vi.fn(async (input: string) => respond(new URL(input, 'http://localhost'))));
  // jsdom lays nothing out, so the chart never learns its width and draws no plot; its table view still works.
  vi.stubGlobal('ResizeObserver', class { observe() {} disconnect() {} });
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
    const [classifier, productCode] = within(screen.getByRole('region', { name: 'Category' })).getAllByRole('definition');
    expect(classifier?.textContent).toBe('No category');
    expect(productCode?.textContent).toBe('Other');
    expect(screen.queryByText('AI')).toBeNull();
    expect(screen.getByRole('link', { name: 'View recipient →' }).getAttribute('href')).toBe('/recipients/MN5KRX2W9R46');
  });

  it("shows the classifier's category with its confidence beside the PSC-based one", async () => {
    const category = {
      code: 'NATURAL_RESOURCES',
      label: 'Natural resources and environment',
      source: 'llm',
      confidence: 0.91,
      model: 'claude-haiku-4-5',
      prompt_version: 'v1',
      baseline_code: 'OTHER',
      baseline_label: 'Other',
    };
    renderAward(() => Response.json({ ...AWARD, category }));

    const categories = within(await screen.findByRole('region', { name: 'Category' }));
    const [classifier, productCode] = categories.getAllByRole('definition');
    expect(classifier?.textContent).toContain('Natural resources and environment');
    expect(within(classifier as HTMLElement).getByRole('button', { name: 'AI' })).toBeDefined();
    expect(classifier?.textContent).toContain('Confidence 0.91');
    expect(productCode?.textContent).toBe('Other');
  });

  it('shows the running total as a table on request, and no chart when modifications are missing', async () => {
    renderAward(() => Response.json(AWARD));

    fireEvent.click(await screen.findByRole('button', { name: 'Show as table' }));

    const rows = within(screen.getByRole('table', { name: 'Obligations over time, oldest first' })).getAllByRole('row');
    expect(rows.map((row) => row.textContent)).toEqual([
      'DateChangeRunning total',
      'Jul 16, 2026+$82,500.00$82,500.00',
      'Aug 24, 2026-$27,500.00$55,000.00',
    ]);
    expect(screen.getByRole('button', { name: 'Show as chart' })).toBeDefined();

    cleanup();
    renderAward(() => Response.json({ ...AWARD, transactions_truncated: true }));
    expect(await screen.findByRole('heading', { level: 1 })).toBeDefined();
    expect(screen.queryByRole('heading', { name: 'Obligations over time' })).toBeNull();
  });

  it('says when no subawards are reported, without asking for them', async () => {
    renderAward(() => Response.json({ ...AWARD, subaward_summary: { count: 0, total: '0.00' } }));

    expect(await screen.findByText('No reported subawards')).toBeDefined();
    const requested = vi.mocked(fetch).mock.calls.map(([input]) => String(input));
    expect(requested.filter((url) => url.includes('/subawards'))).toEqual([]);
  });

  it('lists the newest subawards and shows more on request', async () => {
    renderAward((url) =>
      Response.json(
        url.pathname.endsWith('/subawards') ? subawardPage(url) : { ...AWARD, subaward_summary: { count: 12, total: '1200.00' } },
      ),
    );

    const list = await screen.findByRole('list', { name: 'Subawards, newest first' });
    expect(screen.getByText(/12 subawards/).textContent).toBe('12 subawards, $1,200.00');
    expect(await within(list).findAllByRole('listitem')).toHaveLength(10);

    fireEvent.click(screen.getByRole('button', { name: 'Show more' }));

    expect(await within(list).findByText('VENDOR 12')).toBeDefined();
    expect(within(list).getAllByRole('listitem')).toHaveLength(12);
    expect(screen.queryByRole('button', { name: 'Show more' })).toBeNull();
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
