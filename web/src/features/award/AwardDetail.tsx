import { Link, useLocation, useNavigate } from 'react-router';
import type { Schemas } from '../../api/client';
import { Money } from '../../components/Money';
import { formatDate } from '../../lib/format';
import { ModificationsTable } from './ModificationsTable';

/** USAspending's contract award types. */
const AWARD_TYPES: Record<string, string> = {
  A: 'BPA call',
  B: 'Purchase order',
  C: 'Delivery order',
  D: 'Definitive contract',
};

/** The award detail layout from doc 08. Subawards and the category arrive with the phases that produce them. */
export function AwardDetail({ award }: { award: Schemas['AwardDetail'] }) {
  const { agency, recipient, naics, psc, period_of_performance: period, place_of_performance: place } = award;
  const transactions = award.transactions ?? [];
  return (
    <article className="award">
      <title>{`${award.description ?? award.piid} · AwardTrace`}</title>
      <BackLink />
      <header className="award-header">
        <h1>{award.description ?? 'No description'}</h1>
        <p className="muted">
          PIID {award.piid}
          {award.award_type && ` · ${AWARD_TYPES[award.award_type] ?? award.award_type}`}
          {agency && ` · ${agency.name}${agency.subtier_name ? ` › ${agency.subtier_name}` : ''}`}
        </p>
      </header>

      <div className="award-grid">
        <section className="panel" aria-label="Details">
          <dl className="facts">
            <dt>Total obligated</dt>
            <dd>
              <Money amount={award.total_obligated} exact />
            </dd>
            <dt>Potential value</dt>
            <dd>
              <Money amount={award.potential_total_value} exact />
            </dd>
            <dt>Period of performance</dt>
            <dd>{dateRange(period?.start, period?.end)}</dd>
            <dt>Place of performance</dt>
            <dd>{[place?.state_code, place?.country_code].filter(Boolean).join(', ') || '—'}</dd>
            <dt>NAICS</dt>
            <dd>{code(naics)}</dd>
            <dt>PSC</dt>
            <dd>{code(psc)}</dd>
            <dt>Funding agency</dt>
            <dd>{award.funding_agency?.name ?? '—'}</dd>
            <dt>Fiscal year</dt>
            <dd>{award.fiscal_year ? `FY${award.fiscal_year}` : '—'}</dd>
          </dl>
        </section>

        <section className="panel" aria-labelledby="award-recipient">
          <h2 id="award-recipient">Recipient</h2>
          <p className="recipient-name">{recipient?.name}</p>
          <p className="muted">UEI {recipient?.uei}</p>
          {recipient?.uei && <Link to={`/recipients/${recipient.uei}`}>View recipient →</Link>}
        </section>
      </div>

      <section className="panel" aria-labelledby="award-modifications">
        <h2 id="award-modifications">Modifications ({transactions.length})</h2>
        <ModificationsTable transactions={transactions} truncated={award.transactions_truncated ?? false} />
      </section>

      <footer className="source muted">
        Source: USAspending.gov
        {award.source_modified_at && `, last modified ${formatDate(award.source_modified_at.slice(0, 10))}`}
        {award.usaspending_url && (
          <>
            {' · '}
            <a href={award.usaspending_url}>View on USAspending</a>
          </>
        )}
      </footer>
    </article>
  );
}

/** Back to wherever the reader came from, results and scroll position included; to search if they arrived here. */
function BackLink() {
  const location = useLocation();
  const navigate = useNavigate();
  return location.key === 'default' ? (
    <Link to="/" className="back">
      ← Search awards
    </Link>
  ) : (
    <button type="button" className="link-button back" onClick={() => navigate(-1)}>
      ← Back
    </button>
  );
}

function dateRange(start: string | undefined, end: string | undefined): string {
  if (!start && !end) {
    return '—';
  }
  return `${start ? formatDate(start) : 'Unknown'} to ${end ? formatDate(end) : 'unknown'}`;
}

function code(value: Schemas['Code'] | undefined): string {
  return [value?.code, value?.description].filter(Boolean).join(' ') || '—';
}
