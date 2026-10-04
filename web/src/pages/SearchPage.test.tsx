import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { createMemoryRouter } from 'react-router';
import { RouterProvider } from 'react-router/dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { Schemas } from '../api/client';
import { SearchPage } from './SearchPage';

const RESULTS: Schemas['SearchResults'] = {
  total: 41,
  total_is_capped: false,
  total_obligated: '4867000.00',
  took_ms: 12,
  page: 2,
  size: 20,
  facets: {},
  results: [
    {
      award_id: 'CONT_AWD_HELICOPTER',
      piid: '12024B26C0001',
      description: 'Helicopter services for wildfire suppression',
      description_highlight: '<mark>Helicopter</mark> services for wildfire suppression',
      recipient: { uei: 'AAAAAAAAAAA1', name: 'SKYLINE AVIATION INC' },
      agency: { code: '012', name: 'Department of Agriculture', subtier_name: 'Forest Service' },
      total_obligated: '4812000.00',
      fiscal_year: 2026,
      naics_code: '481211',
      category: { code: 'LOGISTICS_TRANSPORT', label: 'Logistics and transport', source: 'baseline' },
      subaward_count: 2,
    },
  ],
};

function renderAt(path: string, respond: () => Response) {
  const fetch = vi.fn(async () => respond());
  vi.stubGlobal('fetch', fetch);
  const router = createMemoryRouter([{ path: '/', element: <SearchPage /> }], { initialEntries: [path] });
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  );
  return { fetch, router };
}

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe('SearchPage', () => {
  it('searches what the URL holds and shows the results', async () => {
    const { fetch } = renderAt('/?q=helicopter&page=2', () => Response.json(RESULTS));

    expect(await screen.findByText('41 awards')).toBeDefined();
    expect(fetch).toHaveBeenCalledWith('/api/v1/awards/search?q=helicopter&page=2', expect.anything());
    expect(screen.getByRole('link', { name: 'Helicopter services for wildfire suppression' }).getAttribute('href'))
      .toBe('/awards/CONT_AWD_HELICOPTER');
    expect(screen.getByRole('link', { name: 'SKYLINE AVIATION INC' }).getAttribute('href'))
      .toBe('/recipients/AAAAAAAAAAA1');
    expect(screen.getByText('$4.8M')).toBeDefined();
    expect(screen.getByText('2 reported subawards')).toBeDefined();
    expect(screen.getByText('Logistics and transport')).toBeDefined();
    expect(screen.getByRole('link', { name: 'Page 3' }).getAttribute('href')).toBe('/?q=helicopter&page=3');
  });

  it('puts a new keyword in the URL and returns to page 1', async () => {
    const { router } = renderAt('/?q=helicopter&page=2', () => Response.json(RESULTS));

    fireEvent.change(screen.getByLabelText('Search awards'), { target: { value: 'camps' } });
    fireEvent.submit(screen.getByRole('search'));

    await waitFor(() => expect(router.state.location.search).toBe('?q=camps'));
  });

  it('narrows the search when a facet value is checked, and returns to page 1', async () => {
    const facets = { state: [{ value: 'ID', count: 4 }, { value: 'MT', count: 2 }] };
    const { router } = renderAt('/?q=fire&page=3', () => Response.json({ ...RESULTS, facets }));

    fireEvent.click(await screen.findByRole('checkbox', { name: /MT/ }));

    await waitFor(() => expect(router.state.location.search).toBe('?q=fire&state=MT'));
    expect(await screen.findByRole('link', { name: 'Remove filter: MT' })).toBeDefined();
  });

  it('describes a failed search and offers a retry', async () => {
    const problem = {
      type: 'invalid-search-parameters',
      title: 'Invalid search parameters',
      errors: [{ field: 'max_amount', message: 'must not be less than min_amount' }],
    };
    renderAt('/?min_amount=500&max_amount=100', () =>
      Response.json(problem, { status: 400, headers: { 'Content-Type': 'application/problem+json' } }),
    );

    expect(await screen.findByText('Invalid search parameters')).toBeDefined();
    expect(screen.getByText('max_amount: must not be less than min_amount')).toBeDefined();
    expect(screen.getByRole('button', { name: 'Try again' })).toBeDefined();
  });
});
