import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router';
import { api, type Schemas } from '../../api/client';
import { ProblemMessage } from '../../components/ProblemMessage';
import { Skeleton } from '../../components/Skeleton';
import { formatCount, formatDate, formatMoneyShort } from '../../lib/format';
import { ExampleSearches } from './ExampleSearches';
import { SearchBox } from './SearchBox';
import { searchHref } from './searchUrl';
import styles from './SearchHome.module.css';

type Props = {
  /** The search with no keyword or filter, for the totals and the category counts. */
  results: Schemas['SearchResults'] | undefined;
  error: unknown;
  onSearch: (q: string) => void;
  onRetry: () => void;
};

/** The app's front page (doc 16): the search box, example searches, and the categories with their counts. */
export function SearchHome({ results, error, onSearch, onRetry }: Props) {
  const status = useQuery({ queryKey: ['status'], queryFn: ({ signal }) => api.status(signal) });
  const modified = status.data?.freshness?.latest_source_modified_at;
  const agencies = results?.facets?.agency ?? [];
  // The site covers one agency today (ADR 0018); name it while that's true.
  const agency = agencies.length === 1 ? `${agencies[0]?.label} ` : '';
  const categories = results?.facets?.category ?? [];
  return (
    <>
      <section className={styles.hero}>
        <h1>Search federal contracts</h1>
        <p className={styles.lead}>
          {results && (
            <>
              {formatCount(results.total ?? 0)} {agency}awards, {formatMoneyShort(results.total_obligated ?? '0')}{' '}
              obligated.{' '}
            </>
          )}
          {modified && (
            <>
              Source data last changed <time dateTime={modified}>{formatDate(modified.slice(0, 10))}</time>.
            </>
          )}
        </p>
        <SearchBox initial="" onSearch={onSearch} />
        <ExampleSearches />
      </section>
      <section aria-labelledby="categories-title">
        <div className={styles.sectionHeader}>
          <h2 id="categories-title">Browse by category</h2>
          <Link to={searchHref({}, { sort: 'newest' })}>All awards, newest first</Link>
        </div>
        <p className="muted">
          An award&apos;s category is the AI classifier&apos;s where it has one, and otherwise its product code&apos;s.
        </p>
        {error ? (
          <ProblemMessage error={error} onRetry={onRetry} />
        ) : (
          <ul className={styles.tiles} aria-busy={!results || undefined}>
            {results
              ? categories.map((category) => (
                  <li key={category.value}>
                    <Link className={styles.tile} to={searchHref({}, { category: [category.value ?? ''] })}>
                      <span className={styles.tileLabel}>{category.label ?? category.value}</span>{' '}
                      <span className="muted">{formatCount(category.count ?? 0)} awards</span>
                    </Link>
                  </li>
                ))
              : Array.from({ length: 8 }, (_, index) => (
                  <li key={index}>
                    <Skeleton shape="tile" />
                  </li>
                ))}
          </ul>
        )}
      </section>
    </>
  );
}
