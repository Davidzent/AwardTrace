import { describe, expect, it } from 'vitest';
import { readSearch, searchHref } from './searchUrl';

describe('readSearch', () => {
  it('reads every parameter, repeating lists', () => {
    const query = readSearch(new URLSearchParams('q=helicopter&state=ID&state=VA&fiscal_year=2026&sort=largest&page=3'));

    expect(query).toEqual({ q: 'helicopter', state: ['ID', 'VA'], fiscal_year: [2026], sort: 'largest', page: 3 });
  });

  it('drops blanks, numbers that do not parse, and the default page', () => {
    const query = readSearch(new URLSearchParams('q=%20&state=&fiscal_year=abc&page=1'));

    expect(query).toEqual({});
  });
});

describe('searchHref', () => {
  const query = { q: 'camps', state: ['ID'], page: 4 };

  it('returns to page 1 when anything but the page changes', () => {
    expect(searchHref(query, { sort: 'newest' })).toBe('?q=camps&state=ID&sort=newest');
  });

  it('moves between pages and leaves page 1 out of the address', () => {
    expect(searchHref(query, { page: 5 })).toBe('?q=camps&state=ID&page=5');
    expect(searchHref(query, { page: 1 })).toBe('?q=camps&state=ID');
  });

  it('clears a parameter set to undefined', () => {
    expect(searchHref(query, { q: undefined, state: undefined })).toBe('?');
  });
});
