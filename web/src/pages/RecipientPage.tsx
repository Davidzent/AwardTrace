import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router';
import { api, ApiError } from '../api/client';
import { ProblemMessage } from '../components/ProblemMessage';
import { RecipientProfile } from '../features/recipient/RecipientProfile';
import { SearchResults } from '../features/search/SearchResults';
import { readSearch, searchHref } from '../features/search/searchUrl';

/**
 * The profile and the recipient's awards load in parallel. The awards take the search page's parameters from the URL,
 * so their sort and page survive a reload or a shared link.
 */
export function RecipientPage() {
  const { uei = '' } = useParams();
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const query = readSearch(params);
  const profile = useQuery({
    queryKey: ['recipient', uei],
    queryFn: ({ signal }) => api.recipient(uei, signal),
  });
  const awards = useQuery({
    queryKey: ['recipient', uei, 'awards', query],
    queryFn: ({ signal }) => api.recipientAwards(uei, query, signal),
    placeholderData: keepPreviousData,
  });

  // 400 means the address holds something that can't be a UEI; to the reader, that is the same as no such recipient.
  if (profile.error instanceof ApiError && [400, 404].includes(profile.error.status)) {
    return (
      <section className="page-message">
        <title>Recipient not found · AwardTrace</title>
        <h1>No recipient with this UEI</h1>
        <p>
          The recipient may have no awards in scope, or the address may be mistyped. Find it from the{' '}
          <Link to="/">search page</Link>.
        </p>
      </section>
    );
  }
  if (profile.error) {
    return <ProblemMessage error={profile.error} onRetry={() => void profile.refetch()} />;
  }

  return (
    <article className="recipient">
      {profile.data ? (
        <>
          <title>{`${profile.data.name} · AwardTrace`}</title>
          <RecipientProfile recipient={profile.data} />
        </>
      ) : (
        <div aria-busy="true">
          <p className="visually-hidden" role="status">
            Loading recipient
          </p>
          <div className="skeleton-block skeleton-title" aria-hidden="true" />
          <div className="skeleton-block skeleton-panel" aria-hidden="true" />
        </div>
      )}
      <section aria-labelledby="recipient-awards" className="recipient-awards">
        <h2 id="recipient-awards">Awards</h2>
        <SearchResults
          query={query}
          results={awards.data}
          error={awards.error}
          updating={awards.isPlaceholderData}
          lastFilter={undefined}
          onSort={(sort) => navigate(searchHref(query, { sort }))}
          onRetry={() => void awards.refetch()}
        />
      </section>
    </article>
  );
}
