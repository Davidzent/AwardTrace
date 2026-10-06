import { Link } from 'react-router';
import type { Schemas } from '../api/client';
import { formatCount } from '../lib/format';
import { CategoryBadge } from './CategoryBadge';
import { Highlight } from './Highlight';
import { Money } from './Money';
import styles from './ResultCard.module.css';

/** One award in search results and on recipient pages (doc 08): the description, who and from whom, then the facts. */
export function ResultCard({ result }: { result: Schemas['Result'] }) {
  const { recipient, agency } = result;
  return (
    <article className={styles.card}>
      <h2 className={styles.title}>
        <Link to={`/awards/${encodeURIComponent(result.award_id ?? '')}`}>
          {result.description_highlight ? (
            <Highlight html={result.description_highlight} />
          ) : (
            (result.description ?? 'No description')
          )}
        </Link>
      </h2>
      <p className={styles.parties}>
        {recipient?.uei ? <Link to={`/recipients/${recipient.uei}`}>{recipient.name}</Link> : recipient?.name}
        <span aria-hidden="true">{' → '}</span>
        <span className="visually-hidden"> from </span>
        {agency?.subtier_name ?? agency?.name}
      </p>
      <p className={styles.facts}>
        <span className={styles.amount}>
          <Money amount={result.total_obligated} />
        </span>
        {result.fiscal_year !== undefined && <span>FY{result.fiscal_year}</span>}
        {result.naics_code && <span>NAICS {result.naics_code}</span>}
        <CategoryBadge category={result.category} />
        {result.piid && <span className={styles.piid}>PIID {result.piid}</span>}
        {/* Only prime recipients' reports, so an award without any may still have subcontractors. */}
        {(result.subaward_count ?? 0) > 0 && (
          <span>
            {formatCount(result.subaward_count ?? 0)} reported {result.subaward_count === 1 ? 'subaward' : 'subawards'}
          </span>
        )}
      </p>
    </article>
  );
}
