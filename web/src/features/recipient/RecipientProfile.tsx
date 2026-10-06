import type { Schemas } from '../../api/client';
import { Money } from '../../components/Money';
import table from '../../components/Table.module.css';
import { formatCount } from '../../lib/format';

/** The recipient header, summary tiles, and rollups from doc 08. */
export function RecipientProfile({ recipient }: { recipient: Schemas['RecipientDetail'] }) {
  const { parent, location, totals } = recipient;
  const years = (recipient.awards_by_fiscal_year ?? []).map((year) => year.fiscal_year);
  const awardCount = totals?.award_count ?? 0;
  return (
    <>
      <header className="award-header">
        <h1>{recipient.name}</h1>
        <p className="muted">
          UEI {recipient.uei}
          {location && ` · ${[location.city, location.state_code, location.country_code].filter(Boolean).join(', ')}`}
          {/* USAspending names a top-level company as its own parent. */}
          {parent && parent.uei !== recipient.uei && ` · Parent: ${parent.name}`}
        </p>
      </header>

      <div className="tiles">
        <p className="tile">
          <strong>{formatCount(awardCount)}</strong> {awardCount === 1 ? 'award' : 'awards'}
        </p>
        <p className="tile">
          <strong>
            <Money amount={totals?.total_obligated} />
          </strong>{' '}
          obligated
        </p>
        <p className="tile">
          Active <strong>{fiscalYears(years)}</strong>
        </p>
      </div>

      <div className="rollups">
        <Rollups title="Top agencies" rows={recipient.top_agencies ?? []} label={(row) => row.name ?? row.code} />
        <Rollups
          title="Top NAICS"
          rows={recipient.top_naics ?? []}
          label={(row) => [row.code, row.name].filter(Boolean).join(' ')}
        />
        <section className="panel" aria-labelledby="by-fiscal-year">
          <h2 id="by-fiscal-year">By fiscal year</h2>
          <table className={table.table}>
            <thead>
              <tr>
                <th scope="col">Year</th>
                <th scope="col" className={table.num}>
                  Awards
                </th>
                <th scope="col" className={table.num}>
                  Obligated
                </th>
              </tr>
            </thead>
            <tbody>
              {(recipient.awards_by_fiscal_year ?? []).map((year) => (
                <tr key={year.fiscal_year}>
                  <td>FY{year.fiscal_year}</td>
                  <td className={table.num}>{formatCount(year.award_count ?? 0)}</td>
                  <td className={table.num}>
                    <Money amount={year.total_obligated} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>
      </div>
    </>
  );
}

type Rollup = Schemas['Rollup'];

type RollupsProps = { title: string; rows: Rollup[]; label: (row: Rollup) => string | undefined };

function Rollups({ title, rows, label }: RollupsProps) {
  const id = title.toLowerCase().replaceAll(' ', '-');
  return (
    <section className="panel" aria-labelledby={id}>
      <h2 id={id}>{title}</h2>
      <ol className="rollup-list">
        {rows.map((row) => (
          <li key={row.code}>
            <span className="rollup-label">{label(row)}</span>
            <Money amount={row.total_obligated} />
          </li>
        ))}
      </ol>
    </section>
  );
}

function fiscalYears(years: (number | undefined)[]): string {
  const known = years.filter((year) => year !== undefined);
  if (known.length === 0) {
    return '—';
  }
  const first = Math.min(...known);
  const last = Math.max(...known);
  return first === last ? `FY${first}` : `FY${first} to FY${last}`;
}
