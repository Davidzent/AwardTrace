import { Link } from 'react-router';
import type { Schemas } from '../api/client';
import { formatCount } from '../lib/format';
import { Highlight } from './Highlight';
import { Money } from './Money';

/** One award in search results and on recipient pages (doc 08). */
export function ResultCard({ result }: { result: Schemas['Result'] }) {
  const { recipient, agency } = result;
  return (
    <article className="result-card">
      <h2 className="result-title">
        <Link to={`/awards/${encodeURIComponent(result.award_id ?? '')}`}>
          {result.description_highlight ? (
            <Highlight html={result.description_highlight} />
          ) : (
            (result.description ?? 'No description')
          )}
        </Link>
      </h2>
      <p className="result-parties">
        {recipient?.uei ? <Link to={`/recipients/${recipient.uei}`}>{recipient.name}</Link> : recipient?.name}
        <span className="result-arrow" aria-hidden="true">
          {' → '}
        </span>
        <span className="visually-hidden"> from </span>
        {agency?.subtier_name ?? agency?.name}
      </p>
      <p className="result-facts">
        <Money amount={result.total_obligated} />
        {result.fiscal_year !== undefined && <span>FY{result.fiscal_year}</span>}
        {result.naics_code && <span>NAICS {result.naics_code}</span>}
        {result.piid && <span>PIID {result.piid}</span>}
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
