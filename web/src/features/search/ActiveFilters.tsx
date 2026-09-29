import { Link } from 'react-router';
import type { Chip } from './filters';

/** Removable chips for every active filter, and "Clear all" (doc 08). */
export function ActiveFilters({ chips, clearHref }: { chips: Chip[]; clearHref: string }) {
  if (chips.length === 0) {
    return null;
  }
  return (
    <div className="active-filters">
      <span className="muted">Active:</span>
      <ul>
        {chips.map((chip) => (
          <li key={chip.key}>
            <Link to={chip.href} className="chip" aria-label={`Remove filter: ${chip.label}`}>
              {chip.label}
              <span aria-hidden="true"> ×</span>
            </Link>
          </li>
        ))}
      </ul>
      <Link to={clearHref}>Clear all</Link>
    </div>
  );
}
