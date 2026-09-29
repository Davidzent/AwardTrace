import { useQuery } from '@tanstack/react-query';
import { useNavigate, useParams } from 'react-router';
import { api, ApiError } from '../api/client';
import { ProblemMessage } from '../components/ProblemMessage';
import { AwardDetail } from '../features/award/AwardDetail';
import { SearchBox } from '../features/search/SearchBox';
import { searchHref } from '../features/search/searchUrl';

export function AwardPage() {
  const { awardId = '' } = useParams();
  const award = useQuery({
    queryKey: ['award', awardId],
    queryFn: ({ signal }) => api.award(awardId, signal),
  });

  if (award.error instanceof ApiError && award.error.status === 404) {
    return <AwardNotFound />;
  }
  if (award.error) {
    return <ProblemMessage error={award.error} onRetry={() => void award.refetch()} />;
  }
  if (!award.data) {
    return (
      <div className="award" aria-busy="true">
        <p className="visually-hidden" role="status">
          Loading award
        </p>
        <div className="skeleton-block skeleton-title" aria-hidden="true" />
        <div className="award-grid" aria-hidden="true">
          <div className="skeleton-block skeleton-panel" />
          <div className="skeleton-block skeleton-panel" />
        </div>
        <div className="skeleton-block skeleton-panel" aria-hidden="true" />
      </div>
    );
  }
  return <AwardDetail award={award.data} />;
}

/** A 404 shows a search box, since the reader most likely wants to find the award another way (doc 08). */
function AwardNotFound() {
  const navigate = useNavigate();
  return (
    <section className="page-message">
      <title>Award not found · AwardTrace</title>
      <h1>No award with this ID</h1>
      <p>USAspending may have removed it, or the address may be mistyped. Search for it instead.</p>
      <SearchBox initial="" onSearch={(q) => navigate(`/${searchHref({}, { q })}`)} />
    </section>
  );
}
