import { Link } from 'react-router';
import type { Schemas, SearchQuery } from '../../api/client';
import { Money } from '../../components/Money';
import { Pager } from '../../components/Pager';
import { ProblemMessage } from '../../components/ProblemMessage';
import { ResultCard } from '../../components/ResultCard';
import { formatCount } from '../../lib/format';
import type { Chip } from './filters';
import { searchHref } from './searchUrl';

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
      <div className="results" aria-busy="true">
        <p className="visually-hidden" role="status">
          Loading results
        </p>
        {Array.from({ length: 5 }, (_, index) => (
          <div key={index} className="skeleton-card" aria-hidden="true" />
        ))}
      </div>
    );
  }

  const total = results.total ?? 0;
  const size = results.size ?? PAGE_SIZE;
  const lastPage = Math.max(1, Math.min(Math.ceil(total / size), Math.floor(RESULT_WINDOW / size)));
  const sort = query.sort ?? (query.q ? 'relevance' : 'newest');
  return (
    <div className={updating ? 'results is-updating' : 'results'}>
      <div className="results-header">
        <p aria-live="polite">
          <strong>
            {formatCount(total)}
            {results.total_is_capped ? '+' : ''} {total === 1 ? 'award' : 'awards'}
          </strong>
          <span className="results-sep"> · </span>
          <Money amount={results.total_obligated} /> obligated
          <span className="results-sep"> · </span>
          <span className="muted">{results.took_ms} ms</span>
        </p>
        <label className="sort">
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
        <div className="empty">
          <p className="problem-title">No awards match.</p>
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
    </div>
  );
}
