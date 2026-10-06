import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { createMemoryRouter } from 'react-router';
import { RouterProvider } from 'react-router/dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { routes } from './routes';

/** The status answers with a source date; every other request never settles, since only the shell is under test. */
function renderAt(path: string) {
  vi.stubGlobal(
    'fetch',
    vi.fn((input: string) =>
      input.endsWith('/status')
        ? Promise.resolve(Response.json({ freshness: { latest_source_modified_at: '2026-10-02T03:11:00Z' } }))
        : new Promise(() => {}),
    ),
  );
  const router = createMemoryRouter(routes, { initialEntries: [path] });
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  );
  return router;
}

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe('Layout', () => {
  it('searches from the header on any page but search', async () => {
    const router = renderAt('/about');

    // The about page loads on first visit, and the layout renders with it.
    fireEvent.change(await screen.findByLabelText('Search awards'), { target: { value: 'fire crews' } });
    fireEvent.submit(screen.getByRole('search'));

    await waitFor(() => expect(router.state.location.pathname + router.state.location.search).toBe('/?q=fire+crews'));
  });

  it('leaves the search page its own search box only', () => {
    renderAt('/');

    expect(screen.getAllByRole('search')).toHaveLength(1);
  });

  it('says where the data comes from and when the source last changed', async () => {
    renderAt('/about');

    expect((await screen.findByText(/last changed/)).textContent).toBe(
      'Data from USAspending.gov, last changed Oct 2, 2026',
    );
    expect(screen.getByRole('contentinfo').textContent).toContain('not affiliated with USAspending.gov');
  });
});
