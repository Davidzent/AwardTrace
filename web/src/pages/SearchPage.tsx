import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useNavigate, useSearchParams } from 'react-router';
import { api } from '../api/client';
import { SearchBox } from '../features/search/SearchBox';
import { SearchResults } from '../features/search/SearchResults';
import { readSearch, searchHref } from '../features/search/searchUrl';

/** The URL is the state (doc 08): the page reads the search from it and changes it only by navigating. */
export function SearchPage() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const query = readSearch(params);
  const search = useQuery({
    queryKey: ['awards', 'search', query],
    queryFn: ({ signal }) => api.searchAwards(query, signal),
    // While a new page or sort loads, the previous results stay in place, dimmed, instead of a skeleton.
    placeholderData: keepPreviousData,
  });

  return (
    <>
      <title>{query.q ? `${query.q} · AwardTrace` : 'AwardTrace'}</title>
      <h1 className="visually-hidden">Search awards</h1>
      <SearchBox key={query.q ?? ''} initial={query.q ?? ''} onSearch={(q) => navigate(searchHref(query, { q }))} />
      <SearchResults
        query={query}
        results={search.data}
        error={search.error}
        updating={search.isPlaceholderData}
        onSort={(sort) => navigate(searchHref(query, { sort }))}
        onRetry={() => void search.refetch()}
      />
    </>
  );
}
