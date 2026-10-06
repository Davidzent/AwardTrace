import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useNavigate, useSearchParams } from 'react-router';
import { api, type SearchQuery } from '../api/client';
import { ActiveFilters } from '../features/search/ActiveFilters';
import { FilterPanel } from '../features/search/FilterPanel';
import { FiltersDrawer } from '../features/search/FiltersDrawer';
import { activeFilters, clearFiltersHref } from '../features/search/filters';
import { SearchBox } from '../features/search/SearchBox';
import { SearchHome } from '../features/search/SearchHome';
import { SearchResults } from '../features/search/SearchResults';
import { readSearch, searchHref } from '../features/search/searchUrl';
import { useMediaQuery } from '../lib/useMediaQuery';

/** The URL is the state (doc 08): the page reads the search from it and changes it only by navigating. */
export function SearchPage() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const wide = useMediaQuery('(min-width: 900px)');
  const query = readSearch(params);
  const search = useQuery({
    queryKey: ['awards', 'search', query],
    queryFn: ({ signal }) => api.searchAwards(query, signal),
    // While a new page, sort, or filter loads, the previous results stay in place, dimmed, instead of a skeleton.
    placeholderData: keepPreviousData,
  });
  const change = (changes: SearchQuery) => navigate(searchHref(query, changes));
  const chips = activeFilters(query, search.data?.facets);
  const filters = <FilterPanel query={query} facets={search.data?.facets} onChange={change} />;

  // A bare address is the front page; any parameter, even a sort alone, is a search.
  if (params.size === 0) {
    return (
      <>
        <title>AwardTrace</title>
        <SearchHome
          results={search.data}
          error={search.error}
          onSearch={(q) => change({ q })}
          onRetry={() => void search.refetch()}
        />
      </>
    );
  }
  return (
    <>
      <title>{query.q ? `${query.q} · AwardTrace` : 'AwardTrace'}</title>
      <h1 className="visually-hidden">Search awards</h1>
      <SearchBox key={query.q ?? ''} initial={query.q ?? ''} onSearch={(q) => change({ q })} />
      <div className="search-layout">
        {wide && (
          <aside className="filters" aria-label="Filters">
            {filters}
          </aside>
        )}
        <div className="search-main">
          {!wide && <FiltersDrawer count={chips.length}>{filters}</FiltersDrawer>}
          <ActiveFilters chips={chips} clearHref={clearFiltersHref(query)} />
          <SearchResults
            query={query}
            results={search.data}
            error={search.error}
            updating={search.isPlaceholderData}
            lastFilter={chips.at(-1)}
            onSort={(sort) => change({ sort })}
            onRetry={() => void search.refetch()}
          />
        </div>
      </div>
    </>
  );
}
