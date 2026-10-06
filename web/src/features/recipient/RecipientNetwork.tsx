import { Link } from 'react-router';
import type { Schemas } from '../../api/client';
import { Money } from '../../components/Money';
import panel from '../../components/Panel.module.css';
import { ProblemMessage } from '../../components/ProblemMessage';
import { formatCount } from '../../lib/format';

type Props = { network: Schemas['RecipientNetworkDetail'] | undefined; error: unknown; onRetry: () => void };

/** Two lists, not a graph drawing: you walk the network by selecting names (doc 08). */
export function RecipientNetwork({ network, error, onRetry }: Props) {
  const primes = network?.primes_above ?? [];
  const subs = network?.subs_below ?? [];
  return (
    <section className={`${panel.panel} network`} aria-labelledby="recipient-network">
      <h2 id="recipient-network">Network (reported subawards)</h2>
      {error ? (
        <ProblemMessage error={error} onRetry={onRetry} />
      ) : !network ? (
        <p className="muted">Loading network</p>
      ) : primes.length === 0 && subs.length === 0 ? (
        <p className="muted">No reported subawards</p>
      ) : (
        <div className="network-sides">
          <NetworkList title="Primes above" partners={primes} />
          <NetworkList title="Subs below" partners={subs} />
        </div>
      )}
    </section>
  );
}

function NetworkList({ title, partners }: { title: string; partners: Schemas['Partner'][] }) {
  return (
    <div>
      <h3>
        {title} ({formatCount(partners.length)})
      </h3>
      {partners.length === 0 ? (
        <p className="muted">None reported</p>
      ) : (
        <ol className="rollup-list" aria-label={title}>
          {partners.map((partner) => (
            <li key={partner.uei}>
              <span className="rollup-label">
                {/* Only a partner with awards here has a recipient page to open. */}
                {partner.has_awards ? <Link to={`/recipients/${partner.uei}`}>{partner.name}</Link> : partner.name}
                <span className="muted rollup-note">
                  {formatCount(partner.subaward_count ?? 0)} {partner.subaward_count === 1 ? 'subaward' : 'subawards'}
                </span>
              </span>
              <Money amount={partner.total_amount} />
            </li>
          ))}
        </ol>
      )}
    </div>
  );
}
