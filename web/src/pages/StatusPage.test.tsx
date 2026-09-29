import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render, screen } from '@testing-library/react';
import { createMemoryRouter } from 'react-router';
import { RouterProvider } from 'react-router/dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { Schemas } from '../api/client';
import { routes } from '../app/routes';

const STATUS: Schemas['Status'] = {
  ingest: {
    last_run: { mode: 'delta', status: 'failed', started_at: '2026-09-27T06:00:00Z', records_published: 0 },
    last_success_at: '2026-09-06T06:40:00Z',
  },
  pipeline: { available: true, lag: { pipeline: 0, indexer: 12 }, dead_letters: 0, outbox_backlog: { count: 0 } },
  index: { available: false, award_row_count: 61204 },
  freshness: { latest_source_modified_at: new Date().toISOString() },
};

function renderStatus(respond: () => Response) {
  vi.stubGlobal('fetch', vi.fn(async () => respond()));
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <RouterProvider router={createMemoryRouter(routes, { initialEntries: ['/status'] })} />
    </QueryClientProvider>,
  );
}

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe('StatusPage', () => {
  it('states each part condition in words', async () => {
    renderStatus(() => Response.json(STATUS));

    expect(await screen.findByRole('region', { name: 'Ingest: Degraded' })).toBeDefined();
    expect(screen.getByRole('region', { name: 'Pipeline: OK' })).toBeDefined();
    expect(screen.getByRole('region', { name: 'Index: Down' })).toBeDefined();
    expect(screen.getByRole('region', { name: 'Freshness: OK' })).toBeDefined();
    expect(screen.getByText('indexer lag')).toBeDefined();
    expect(screen.getByText("Elasticsearch didn't answer.")).toBeDefined();
  });

  it('says the status is unavailable without taking the page down', async () => {
    renderStatus(() => new Response('Bad gateway', { status: 502 }));

    expect((await screen.findByRole('alert')).textContent).toContain('Status unavailable.');
    expect(screen.getByRole('link', { name: 'Search' })).toBeDefined();
  });
});
