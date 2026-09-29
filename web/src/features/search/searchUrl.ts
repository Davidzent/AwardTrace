import { toSearchParams, type SearchQuery } from '../../api/client';

const TEXT = ['q', 'min_amount', 'max_amount', 'from', 'to', 'category_source', 'sort'] as const;
const LISTS = ['agency', 'category', 'state', 'naics'] as const;

/**
 * The search the URL holds (doc 08), so copying the address reproduces the view. Numbers that don't parse are
 * dropped rather than sent, and page 1 is the default.
 */
export function readSearch(params: URLSearchParams): SearchQuery {
  const query: SearchQuery = {};
  for (const name of TEXT) {
    const value = params.get(name)?.trim();
    if (value) {
      query[name] = value;
    }
  }
  for (const name of LISTS) {
    const values = params.getAll(name).filter(Boolean);
    if (values.length > 0) {
      query[name] = values;
    }
  }
  const years = params.getAll('fiscal_year').map(Number).filter(Number.isInteger);
  if (years.length > 0) {
    query.fiscal_year = years;
  }
  const page = Number(params.get('page'));
  if (Number.isInteger(page) && page > 1) {
    query.page = page;
  }
  return query;
}

/** The address of the search with some parameters changed. Any change but paging returns to page 1. */
export function searchHref(query: SearchQuery, changes: SearchQuery): string {
  const next: SearchQuery = { ...query, page: undefined, ...changes };
  if (next.page === 1) {
    next.page = undefined;
  }
  const params = toSearchParams(next);
  return params.size > 0 ? `?${params}` : '?';
}
