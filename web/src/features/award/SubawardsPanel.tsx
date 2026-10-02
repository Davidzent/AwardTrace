import { useInfiniteQuery } from '@tanstack/react-query';
import { api, type Schemas } from '../../api/client';
import { Money } from '../../components/Money';
import { ProblemMessage } from '../../components/ProblemMessage';
import { formatCount, formatDate } from '../../lib/format';

const PAGE_SIZE = 10;

type Props = { awardId: string; summary: Schemas['SubawardSummary'] | undefined };

/** The award's reported subawards from doc 08: the count and total, then the newest, ten more at a time. */
export function SubawardsPanel({ awardId, summary }: Props) {
  const count = summary?.count ?? 0;
  const subawards = useInfiniteQuery({
    queryKey: ['subawards', awardId],
    queryFn: ({ pageParam, signal }) => api.subawards(awardId, { page: pageParam, size: PAGE_SIZE }, signal),
    initialPageParam: 1,
    getNextPageParam: ({ page = 1, size = PAGE_SIZE, total = 0 }) => (page * size < total ? page + 1 : undefined),
    // The summary already says when there is nothing to list.
    enabled: count > 0,
  });
  const items = subawards.data?.pages.flatMap((page) => page.results ?? []) ?? [];

  return (
    <section className="panel" aria-labelledby="award-subawards">
      <h2 id="award-subawards">Subawards (reported)</h2>
      {count === 0 ? (
        <p className="muted">No reported subawards</p>
      ) : (
        <>
          <p>
            {formatCount(count)} {count === 1 ? 'subaward' : 'subawards'}, <Money amount={summary?.total} exact />
          </p>
          {subawards.error && <ProblemMessage error={subawards.error} onRetry={() => void subawards.refetch()} />}
          {subawards.isPending && <p className="muted">Loading subawards</p>}
          <ol className="rollup-list" aria-label="Subawards, newest first">
            {items.map((subaward) => (
              <li key={subaward.subaward_key}>
                <span className="rollup-label">
                  {subaward.sub_recipient?.name}
                  <span className="muted rollup-note">
                    {subaward.action_date && formatDate(subaward.action_date)}
                  </span>
                </span>
                <Money amount={subaward.amount} exact />
              </li>
            ))}
          </ol>
          {subawards.hasNextPage && (
            <button
              type="button"
              className="link-button"
              disabled={subawards.isFetchingNextPage}
              onClick={() => void subawards.fetchNextPage()}
            >
              {subawards.isFetchingNextPage ? 'Loading' : 'Show more'}
            </button>
          )}
        </>
      )}
    </section>
  );
}
