import { Link } from 'react-router';
import styles from './AboutPage.module.css';

const FLOW = [
  { name: 'USAspending archive', detail: 'Monthly contract files, republished from FPDS' },
  { name: 'S3', detail: 'Every file stored as downloaded, named by its SHA-256' },
  { name: 'Kafka', detail: 'One event per contract transaction' },
  { name: 'Pipeline', detail: 'Writes transactions and recomputes their awards' },
  { name: 'PostgreSQL', detail: 'Awards, transactions, and recipients, plus an outbox' },
  { name: 'Indexer', detail: 'Copies each changed award into the search index' },
  { name: 'Elasticsearch', detail: 'Keyword search, facets, and sorting' },
  { name: 'API and this site', detail: 'Read-only, rate-limited, one origin' },
];

/** How it works, in plain language (doc 08). Each guarantee is one the code enforces today. */
export function AboutPage() {
  return (
    <article className={styles.about}>
      <title>How it works · AwardTrace</title>
      <h1>How AwardTrace works</h1>
      <p className={styles.lead}>
        AwardTrace makes federal contract awards searchable. It reads the public award archive of{' '}
        <a href="https://www.usaspending.gov/">USAspending.gov</a>, keeps every file it reads, and rebuilds awards from
        their individual transactions, so every figure can be traced back to the source.
      </p>

      <section aria-labelledby="about-flow">
        <h2 id="about-flow">From source file to search result</h2>
        <ol className={styles.flow}>
          {FLOW.map((step) => (
            <li key={step.name}>
              <strong>{step.name}</strong>
              <span>{step.detail}</span>
            </li>
          ))}
        </ol>
      </section>

      <section aria-labelledby="about-guarantees">
        <h2 id="about-guarantees">What always holds</h2>
        <dl className={styles.guarantees}>
          <div>
            <dt>The source files are the record</dt>
            <dd>
              The database and the search index are copies. Replaying the stored files rebuilds both, so nothing here
              depends on state that can't be recreated.
            </dd>
          </div>
          <div>
            <dt>Receiving the same data twice changes nothing</dt>
            <dd>
              Every step may repeat after a failure. Writes are versioned, so a repeated event leaves the database and
              the index exactly as they were.
            </dd>
          </div>
          <div>
            <dt>Order doesn't matter</dt>
            <dd>
              A transaction from an older file never replaces one from a newer file, and each award is recomputed from
              its transactions, so late or replayed data can't roll an award back.
            </dd>
          </div>
          <div>
            <dt>A change and its announcement happen together</dt>
            <dd>
              When an award changes, the notice for the search index is written in the same database transaction, so the
              index can't miss a change or hear about one that didn't happen.
            </dd>
          </div>
          <div>
            <dt>Money is exact</dt>
            <dd>
              Amounts are stored and served as exact decimals, never as floating-point numbers. Totals across search
              results are summed by Elasticsearch and rounded to the cent.
            </dd>
          </div>
        </dl>
      </section>

      <section aria-labelledby="about-scope">
        <h2 id="about-scope">What's covered</h2>
        <ul>
          <li>Contract awards with an action on or after October 1, 2024, the start of fiscal year 2025.</li>
          <li>Indefinite-delivery vehicles are left out; the orders placed under them are included.</li>
          <li>Amounts are obligations as agencies report them. They are revised, and sometimes taken back as deobligations.</li>
          <li>
            The <Link to="/status">status page</Link> shows how current the data is and how many awards are loaded.
          </li>
        </ul>
      </section>

      <section aria-labelledby="about-links">
        <h2 id="about-links">More</h2>
        <ul>
          <li>
            <a href="https://awardtrace.zntsns.com/">The AwardTrace home page</a>, with the results and the
            app&apos;s hours
          </li>
          <li>
            <a href="https://github.com/Davidzent/awardtrace">Source code on GitHub</a>
          </li>
          <li>
            <a href="/api/v1/openapi.json">The API's OpenAPI document</a>
          </li>
          <li>
            <a href="https://www.usaspending.gov/">USAspending.gov</a>, the source of every award
          </li>
        </ul>
      </section>
    </article>
  );
}
