import { Link } from 'react-router';
import type { Schemas, SearchQuery } from '../../api/client';
import { Money } from '../../components/Money';
import { Pager } from '../../components/Pager';
import { ProblemMessage } from '../../components/ProblemMessage';
import { ResultCard } from '../../components/ResultCard';
import { Skeleton } from '../../components/Skeleton';
import { formatCount } from '../../lib/format';
import type { Chip } from './filters';
import { searchHref } from './searchUrl';
import styles from './SearchResults.module.css';

/** The API refuses pages past Elasticsearch's result window (doc 07), so the pager stops there. */
const RESULT_WINDOW = 10_000;
const PAGE_SIZE = 20;

const SORTS = [
  { value: 'relevance', label: 'Relevance' },
  { value: 'newest', label: 'Newest' },
  { value: 'largest', label: 'Largest' },
  { value: 'recipient', label: 'Recipient A–Z' },
];

type Props = {
  query: SearchQuery;
  results: Schemas['SearchResults'] | undefined;
  error: unknown;
  updating: boolean;
  lastFilter: Chip | undefined;
  onSort: (sort: string) => void;
  onRetry: () => void;
};

/** The results header, cards, and pager, with designed loading, empty, and error states (doc 08). */
export function SearchResults({ query, results, error, updating, lastFilter, onSort, onRetry }: Props) {
  if (error) {
    return <ProblemMessage error={error} onRetry={onRetry} />;
  }
  if (!results) {
    return (
      <section aria-label="Results" className={styles.results} aria-busy="true">
        <p className="visually-hidden" role="status">
          Loading results
        </p>
        {Array.from({ length: 5 }, (_, index) => (
          <Skeleton key={index} shape="card" />
        ))}
      </section>
    );
  }

  const total = results.total ?? 0;
  const size = results.size ?? PAGE_SIZE;
  const lastPage = Math.max(1, Math.min(Math.ceil(total / size), Math.floor(RESULT_WINDOW / size)));
  const sort = query.sort ?? (query.q ? 'relevance' : 'newest');
  return (
    // aria-busy tells assistive technology, and tests, that dimmed results are about to be replaced.
    <section
      aria-label="Results"
      className={updating ? `${styles.results} ${styles.updating}` : styles.results}
      aria-busy={updating || undefined}
    >
      <div className={styles.header}>
        <p className={styles.summary} aria-live="polite">
          <strong className={styles.count}>
            {formatCount(total)}
            {results.total_is_capped ? '+' : ''} {total === 1 ? 'award' : 'awards'}
          </strong>
          {' · '}
          <Money amount={results.total_obligated} /> obligated · {results.took_ms}&nbsp;ms
        </p>
        <label className={styles.sort}>
          Sort
          <select value={sort} onChange={(event) => onSort(event.target.value)}>
            {SORTS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </label>
      </div>
      {total === 0 ? (
        <div className={styles.empty}>
          <p className={styles.emptyTitle}>No awards match.</p>
          {lastFilter ? (
            <p>
              Remove the last filter, <Link to={lastFilter.href}>{lastFilter.label}</Link>, or{' '}
              <Link to="/">start over</Link>.
            </p>
          ) : (
            <p>
              Try fewer or different words, or <Link to="/">start over</Link>.
            </p>
          )}
        </div>
      ) : (
        <>
          {results.results?.map((result) => <ResultCard key={result.award_id} result={result} />)}
          <Pager page={results.page ?? 1} lastPage={lastPage} href={(page) => searchHref(query, { page })} />
        </>
      )}
    </section>
  );
}
