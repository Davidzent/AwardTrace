import { useQuery } from '@tanstack/react-query';
import { useParams } from 'react-router';
import { api, ApiError } from '../api/client';
import { ProblemMessage } from '../components/ProblemMessage';
import { Skeleton } from '../components/Skeleton';
import { AwardDetail } from '../features/award/AwardDetail';
import styles from '../features/award/AwardDetail.module.css';

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
      <div aria-busy="true">
        <p className="visually-hidden" role="status">
          Loading award
        </p>
        <Skeleton shape="title" />
        <div className={styles.grid}>
          <Skeleton shape="panel" />
          <Skeleton shape="panel" />
        </div>
        <Skeleton shape="panel" />
      </div>
    );
  }
  return <AwardDetail award={award.data} />;
}

/** A 404 points to the header's search box, since the reader most likely wants to find the award another way. */
function AwardNotFound() {
  return (
    <section className="page-message">
      <title>Award not found · AwardTrace</title>
      <h1>No award with this ID</h1>
      <p>
        USAspending may have removed it, or the address may be mistyped. Search for it with the box at the top of the
        page.
      </p>
    </section>
  );
}
