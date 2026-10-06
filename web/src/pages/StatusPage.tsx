import { useQuery } from '@tanstack/react-query';
import { api } from '../api/client';
import { Button } from '../components/Button';
import problemStyles from '../components/ProblemMessage.module.css';
import { Skeleton } from '../components/Skeleton';
import { StatusTile } from '../components/StatusTile';
import {
  enrichmentCondition,
  freshnessCondition,
  indexCondition,
  ingestCondition,
  pipelineCondition,
} from '../features/status/conditions';
import { formatCount, formatMoney, formatTime } from '../lib/format';
import { useNow } from '../lib/useNow';

/** Polls every 15 seconds, the time the API caches the status for (doc 07). Polling pauses while the tab is hidden. */
export function StatusPage() {
  const status = useQuery({
    queryKey: ['status'],
    queryFn: ({ signal }) => api.status(signal),
    refetchInterval: 15_000,
  });
  const now = useNow();
  const { ingest, pipeline, index, enrichment, freshness } = status.data ?? {};
  const run = ingest?.last_run;

  return (
    <section className="status">
      <title>Pipeline status · AwardTrace</title>
      <div className="status-header">
        <h1>Pipeline status</h1>
        <p className="muted" aria-live="polite">
          {status.dataUpdatedAt > 0 && `Updated ${ago(now - status.dataUpdatedAt)}`}{' '}
          <Button onClick={() => void status.refetch()} busy={status.isFetching}>
            Refresh
          </Button>
        </p>
      </div>

      {status.error && (
        <p className={problemStyles.problem} role="alert">
          <strong>Status unavailable.</strong>{' '}
          {status.data ? 'Showing the last status received.' : 'The rest of the site keeps working.'}
        </p>
      )}

      {!status.data && !status.error && (
        <div className="status-grid" aria-busy="true">
          <p className="visually-hidden" role="status">
            Loading status
          </p>
          {Array.from({ length: 5 }, (_, i) => (
            <Skeleton key={i} shape="panel" />
          ))}
        </div>
      )}

      {status.data && (
        <div className="status-grid">
          <StatusTile title="Ingest" condition={ingestCondition(ingest)}>
            {run ? (
              <dl className="facts">
                <dt>Last run</dt>
                <dd>
                  {run.mode}, {run.status}
                </dd>
                <dt>Started</dt>
                <dd>{run.started_at && formatTime(run.started_at)}</dd>
                <dt>Published</dt>
                <dd>{formatCount(run.records_published ?? 0)} records</dd>
                <dt>Last success</dt>
                <dd>{ingest?.last_success_at ? formatTime(ingest.last_success_at) : 'None yet'}</dd>
              </dl>
            ) : (
              <p>No ingest has run yet.</p>
            )}
          </StatusTile>

          <StatusTile title="Pipeline" condition={pipelineCondition(pipeline, now)}>
            {pipeline?.available ? (
              <dl className="facts">
                {Object.entries(pipeline.lag ?? {}).map(([group, lag]) => (
                  <Fact key={group} term={`${group} lag`} value={formatCount(lag)} />
                ))}
                <Fact term="Dead letters" value={formatCount(pipeline.dead_letters ?? 0)} />
                <Fact term="Outbox backlog" value={formatCount(pipeline.outbox_backlog?.count ?? 0)} />
              </dl>
            ) : (
              <p>Kafka didn't answer in time.</p>
            )}
          </StatusTile>

          <StatusTile title="Index" condition={indexCondition(index)}>
            <dl className="facts">
              {index?.available && (
                <>
                  <Fact term="Alias" value={`awards → ${index.alias_target}`} />
                  <Fact term="Documents" value={formatCount(index.document_count ?? 0)} />
                </>
              )}
              <Fact term="Award rows" value={formatCount(index?.award_row_count ?? 0)} />
            </dl>
            {!index?.available && <p>Elasticsearch didn't answer.</p>}
          </StatusTile>

          <StatusTile title="Enrichment" condition={enrichmentCondition(enrichment)}>
            {enrichment?.enabled ? (
              <dl className="facts">
                <Fact term="Breaker" value={(enrichment.breaker_state ?? '').toLowerCase().replace('_', '-')} />
                <Fact term="Coverage" value={percent(enrichment.coverage_pct)} />
                <Fact term="Cache hit rate" value={percent(enrichment.cache_hit_rate_pct)} />
                <Fact term="Spent today" value={formatMoney(enrichment.spend_today_usd ?? '0')} />
                <Fact term="Daily cap" value={formatMoney(enrichment.daily_cap_usd ?? '0')} />
              </dl>
            ) : (
              <p>The classifier isn't running, so new descriptions keep their PSC-based category.</p>
            )}
          </StatusTile>

          <StatusTile title="Freshness" condition={freshnessCondition(freshness, now)}>
            <dl className="facts">
              <Fact
                term="Source modified"
                value={freshness?.latest_source_modified_at ? formatTime(freshness.latest_source_modified_at) : '—'}
              />
            </dl>
          </StatusTile>
        </div>
      )}
    </section>
  );
}

function Fact({ term, value }: { term: string; value: string }) {
  return (
    <>
      <dt>{term}</dt>
      <dd>{value}</dd>
    </>
  );
}

/** 97.4%, or a dash for the null the API sends before there's anything to measure. */
function percent(value: number | undefined): string {
  return typeof value === 'number' ? `${value.toFixed(1)}%` : '—';
}

function ago(milliseconds: number): string {
  const seconds = Math.max(0, Math.round(milliseconds / 1000));
  return seconds < 60 ? `${seconds} s ago` : `${Math.round(seconds / 60)} min ago`;
}
