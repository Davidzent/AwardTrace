import { cleanup, render, screen } from '@testing-library/react';
import { createMemoryRouter } from 'react-router';
import { RouterProvider } from 'react-router/dom';
import { afterEach, describe, expect, it } from 'vitest';
import { routes } from './routes';

function renderAt(path: string) {
  render(<RouterProvider router={createMemoryRouter(routes, { initialEntries: [path] })} />);
}

afterEach(cleanup);

describe('routes', () => {
  it('opens the search page at the root', () => {
    renderAt('/');

    expect(screen.getByRole('heading', { level: 1 }).textContent).toBe('Search federal contract awards');
    expect(screen.getByRole('link', { name: 'Search' }).getAttribute('aria-current')).toBe('page');
  });

  it('keeps the layout around a page that does not exist', () => {
    renderAt('/no/such/page');

    expect(screen.getByRole('heading', { level: 1 }).textContent).toBe('Page not found');
    expect(screen.getByRole('link', { name: 'AwardTrace' })).toBeDefined();
  });
});
