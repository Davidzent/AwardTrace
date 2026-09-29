import { describe, expect, it } from 'vitest';
import { freshnessCondition, indexCondition, ingestCondition, pipelineCondition } from './conditions';

const NOW = Date.parse('2026-09-29T12:00:00Z');

describe('conditions', () => {
  it('reads a failed ingest run as degraded, not down', () => {
    expect(ingestCondition({ last_run: { status: 'failed' } })).toBe('Degraded');
    expect(ingestCondition({ last_run: { status: 'running' } })).toBe('OK');
    expect(ingestCondition({})).toBe('Unknown');
  });

  it('flags dead letters and a stuck relay, but not lag', () => {
    const healthy = { available: true, lag: { pipeline: 90_000 }, dead_letters: 0, outbox_backlog: { count: 0 } };
    expect(pipelineCondition(healthy, NOW)).toBe('OK');
    expect(pipelineCondition({ ...healthy, dead_letters: 3 }, NOW)).toBe('Degraded');
    expect(
      pipelineCondition({ ...healthy, outbox_backlog: { count: 4, oldest_created_at: '2026-09-29T11:50:00Z' } }, NOW),
    ).toBe('Degraded');
    expect(pipelineCondition({ available: false }, NOW)).toBe('Down');
  });

  it('compares indexed documents with live award rows', () => {
    expect(indexCondition({ available: true, document_count: 61204, award_row_count: 61204 })).toBe('OK');
    expect(indexCondition({ available: true, document_count: 0, award_row_count: 61204 })).toBe('Degraded');
    expect(indexCondition({ available: false, award_row_count: 61204 })).toBe('Down');
  });

  it('expects a source change within 45 days', () => {
    expect(freshnessCondition({ latest_source_modified_at: '2026-09-01T00:00:00Z' }, NOW)).toBe('OK');
    expect(freshnessCondition({ latest_source_modified_at: '2026-07-01T00:00:00Z' }, NOW)).toBe('Degraded');
  });
});
