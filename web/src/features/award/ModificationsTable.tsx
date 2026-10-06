import type { Schemas } from '../../api/client';
import { Badge } from '../../components/Badge';
import { Money } from '../../components/Money';
import table from '../../components/Table.module.css';
import { formatDate } from '../../lib/format';

type Props = { transactions: Schemas['Modification'][]; truncated: boolean };

/** Every modification, newest first, with exact amounts; a negative one is labeled as a deobligation (doc 08). */
export function ModificationsTable({ transactions, truncated }: Props) {
  return (
    <>
      {/* On a narrow screen the table scrolls sideways; focusable, so the keyboard can scroll it too. */}
      <div className={table.scroll} tabIndex={0} role="region" aria-label="Modifications table">
        <table className={table.table}>
          <caption className="visually-hidden">Modifications, newest first</caption>
          <thead>
            <tr>
              <th scope="col">Date</th>
              <th scope="col">Mod</th>
              <th scope="col" className={table.num}>
                Obligation
              </th>
              <th scope="col">Description</th>
            </tr>
          </thead>
          <tbody>
            {transactions.map((transaction) => (
              <tr key={transaction.transaction_id}>
                <td className={table.nowrap}>{transaction.action_date && formatDate(transaction.action_date)}</td>
                <td>{transaction.modification_number}</td>
                <td className={table.num}>
                  <Money amount={transaction.federal_action_obligation} exact />
                  {transaction.federal_action_obligation?.startsWith('-') && (
                    <Badge className={table.below}>Deobligation</Badge>
                  )}
                </td>
                <td>{transaction.description}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {truncated && <p className="muted">Showing the newest {transactions.length} modifications.</p>}
    </>
  );
}
