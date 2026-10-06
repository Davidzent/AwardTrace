import { Link, useLocation, useNavigate } from 'react-router';
import type { Schemas } from '../../api/client';
import { Badge } from '../../components/Badge';
import { Button } from '../../components/Button';
import { CategoryBadge } from '../../components/CategoryBadge';
import { Money } from '../../components/Money';
import panel from '../../components/Panel.module.css';
import { formatDate } from '../../lib/format';
import { ModificationsTable } from './ModificationsTable';
import styles from './AwardDetail.module.css';
import { SubawardsPanel } from './SubawardsPanel';

/** USAspending's contract award types. */
const AWARD_TYPES: Record<string, string> = {
  A: 'BPA call',
  B: 'Purchase order',
  C: 'Delivery order',
  D: 'Definitive contract',
};

/** The award detail layout from doc 08. */
export function AwardDetail({ award }: { award: Schemas['AwardDetail'] }) {
  const { agency, recipient, naics, psc, period_of_performance: period, place_of_performance: place } = award;
  const transactions = award.transactions ?? [];
  return (
    <article>
      <title>{`${award.description ?? award.piid} · AwardTrace`}</title>
      <BackLink />
      <header className={styles.header}>
        <h1>{award.description ?? 'No description'}</h1>
        <p className={styles.meta}>
          PIID <span className={styles.piid}>{award.piid}</span>
          {award.award_type && ` · ${AWARD_TYPES[award.award_type] ?? award.award_type}`}
          {agency && ` · ${agency.name}${agency.subtier_name ? ` › ${agency.subtier_name}` : ''}`}
        </p>
      </header>

      <div className={styles.grid}>
        <div className={styles.column}>
          <section className={panel.panel} aria-label="Details">
            <dl className={styles.figures}>
              <div>
                <dt>Total obligated</dt>
                <dd>
                  <Money amount={award.total_obligated} exact />
                </dd>
              </div>
              <div>
                <dt>Potential value</dt>
                <dd>
                  <Money amount={award.potential_total_value} exact />
                </dd>
              </div>
            </dl>
            <dl className={styles.facts}>
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
          <section className={panel.panel} aria-labelledby="award-category">
            <h2 id="award-category">Category</h2>
            <CategoryBlock category={award.category} />
          </section>
        </div>

        <div className={styles.column}>
          <section className={panel.panel} aria-labelledby="award-recipient">
            <h2 id="award-recipient">Recipient</h2>
            <p className={styles.recipientName}>{recipient?.name}</p>
            <p className="muted">UEI {recipient?.uei}</p>
            {recipient?.uei && <Link to={`/recipients/${recipient.uei}`}>View recipient →</Link>}
          </section>
          {award.award_id && <SubawardsPanel awardId={award.award_id} summary={award.subaward_summary} />}
        </div>
      </div>

      <section className={panel.panel} aria-labelledby="award-modifications">
        <h2 id="award-modifications">Modifications ({transactions.length})</h2>
        <ModificationsTable transactions={transactions} truncated={award.transactions_truncated ?? false} />
      </section>

      <footer className={styles.source}>
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

/**
 * The classifier's category, with its confidence, beside the one the product code gives, so the reader can compare
 * them (doc 08). The site shows the classifier's where it has one.
 */
function CategoryBlock({ category }: { category: Schemas['CategoryDetail'] | undefined }) {
  const ai = category?.source === 'llm';
  const baseline = ai ? category.baseline_label : category?.label;
  return (
    <dl className={styles.categories}>
      <div>
        <dt>AI classifier</dt>
        <dd>
          {ai ? (
            <>
              <CategoryBadge category={category} />
              {typeof category.confidence === 'number' && (
                <span className="muted">Confidence {category.confidence.toFixed(2)}</span>
              )}
            </>
          ) : (
            <span className="muted">No category</span>
          )}
        </dd>
      </div>
      <div>
        <dt>Product code (PSC)</dt>
        <dd>{baseline ? <Badge>{baseline}</Badge> : '—'}</dd>
      </div>
    </dl>
  );
}

/** Back to wherever the reader came from, results and scroll position included; to search if they arrived here. */
function BackLink() {
  const location = useLocation();
  const navigate = useNavigate();
  return location.key === 'default' ? (
    <Link to="/" className={styles.back}>
      ← Search awards
    </Link>
  ) : (
    <Button variant="quiet" className={styles.back} onClick={() => navigate(-1)}>
      ← Back
    </Button>
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
