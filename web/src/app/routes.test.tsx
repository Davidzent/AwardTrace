import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render, screen } from '@testing-library/react';
import { createMemoryRouter } from 'react-router';
import { RouterProvider } from 'react-router/dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { routes } from './routes';

function renderAt(path: string) {
  render(
    <QueryClientProvider client={new QueryClient()}>
      <RouterProvider router={createMemoryRouter(routes, { initialEntries: [path] })} />
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  // Pages may fetch; these tests only look at routing, so requests never settle.
  vi.stubGlobal('fetch', vi.fn(() => new Promise(() => {})));
});

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe('routes', () => {
  it('opens the search page at the root', () => {
    renderAt('/');

    expect(screen.getByRole('heading', { level: 1 }).textContent).toBe('Search awards');
    expect(screen.getByRole('link', { name: 'Search' }).getAttribute('aria-current')).toBe('page');
  });

  it('keeps the layout around a page that does not exist', () => {
    renderAt('/no/such/page');

    expect(screen.getByRole('heading', { level: 1 }).textContent).toBe('Page not found');
    expect(screen.getByRole('link', { name: 'AwardTrace' })).toBeDefined();
  });
});
