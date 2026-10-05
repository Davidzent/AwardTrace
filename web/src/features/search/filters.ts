import type { Schemas, SearchQuery } from '../../api/client';
import { formatDate, formatMoneyShort } from '../../lib/format';
import { searchHref } from './searchUrl';

export type FacetName = 'agency' | 'category' | 'state' | 'naics' | 'fiscal_year';
export type Facets = Schemas['SearchResults']['facets'];

/** The multi-select facets, in the order the filter column lists them (doc 08). */
export const FACETS: { name: FacetName; title: string; chip: (value: string, label: string | undefined) => string }[] = [
  { name: 'agency', title: 'Agency', chip: (value, label) => label ?? value },
  { name: 'category', title: 'Category', chip: (value, label) => label ?? value },
  { name: 'state', title: 'State', chip: (value) => value },
  { name: 'naics', title: 'NAICS', chip: (value) => `NAICS ${value}` },
  { name: 'fiscal_year', title: 'Fiscal year', chip: (value) => `FY${value}` },
];

/** Where the category came from (doc 08); All sends no category_source. */
export const CATEGORY_SOURCES: { value: string | undefined; label: string; chip: string }[] = [
  { value: undefined, label: 'All', chip: '' },
  { value: 'llm', label: 'AI only', chip: 'AI categories only' },
  { value: 'baseline', label: 'PSC-based only', chip: 'PSC-based categories only' },
];

export function selected(query: SearchQuery, name: FacetName): string[] {
  return (query[name] ?? []).map(String);
}

/** The query change that selects exactly these values of one facet. */
export function selecting(name: FacetName, values: string[]): SearchQuery {
  const change: SearchQuery = {};
  if (name === 'fiscal_year') {
    change.fiscal_year = values.length > 0 ? values.map(Number) : undefined;
  } else {
    change[name] = values.length > 0 ? values : undefined;
  }
  return change;
}

/** A removable filter: its label and the address of the same search without it. */
export type Chip = { key: string; label: string; href: string };

export function activeFilters(query: SearchQuery, facets: Facets): Chip[] {
  const chips: Chip[] = [];
  for (const facet of FACETS) {
    const values = selected(query, facet.name);
    for (const value of values) {
      const label = facets?.[facet.name]?.find((item) => item.value === value)?.label;
      chips.push({
        key: `${facet.name}:${value}`,
        label: facet.chip(value, label),
        href: searchHref(query, selecting(facet.name, values.filter((other) => other !== value))),
      });
    }
  }
  if (query.min_amount || query.max_amount) {
    chips.push({
      key: 'amount',
      label: range('Amount', query.min_amount && formatMoneyShort(query.min_amount), query.max_amount && formatMoneyShort(query.max_amount)),
      href: searchHref(query, { min_amount: undefined, max_amount: undefined }),
    });
  }
  if (query.from || query.to) {
    chips.push({
      key: 'last-action',
      label: range('Last action', query.from && formatDate(query.from), query.to && formatDate(query.to)),
      href: searchHref(query, { from: undefined, to: undefined }),
    });
  }
  if (query.category_source) {
    chips.push({
      key: 'category-source',
      label: CATEGORY_SOURCES.find((source) => source.value === query.category_source)?.chip ?? query.category_source,
      href: searchHref(query, { category_source: undefined }),
    });
  }
  return chips;
}

/** The same search with every filter removed; the keyword and sort stay. */
export function clearFiltersHref(query: SearchQuery): string {
  return searchHref(query, {
    agency: undefined,
    category: undefined,
    state: undefined,
    naics: undefined,
    fiscal_year: undefined,
    min_amount: undefined,
    max_amount: undefined,
    from: undefined,
    to: undefined,
    category_source: undefined,
  });
}

function range(name: string, from: string | undefined, to: string | undefined): string {
  return [name, from && `from ${from}`, to && `to ${to}`].filter(Boolean).join(' ');
}
