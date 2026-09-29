import { useState } from 'react';
import type { Schemas } from '../api/client';
import { formatCount } from '../lib/format';

const COLLAPSED = 5;

type Props = {
  title: string;
  values: Schemas['FacetValue'][];
  selected: string[];
  display: (value: string, label: string | undefined) => string;
  onChange: (selected: string[]) => void;
};

/**
 * Real checkboxes with counts, and "Show more" (doc 08). Selected values come first and stay listed even when they
 * fall outside the counts the API returned, so a selection can always be undone here.
 */
export function FacetGroup({ title, values, selected, display, onChange }: Props) {
  const [expanded, setExpanded] = useState(false);
  const counted = new Map(values.map((item) => [item.value ?? '', item]));
  const items = [
    ...selected.map((value) => counted.get(value) ?? { value }),
    ...values.filter((item) => !selected.includes(item.value ?? '')),
  ];
  const shown = expanded ? items : items.slice(0, Math.max(COLLAPSED, selected.length));

  return (
    <fieldset className="facet">
      <legend>{title}</legend>
      <ul>
        {shown.map(({ value = '', label, count }) => (
          <li key={value}>
            <label>
              <input
                type="checkbox"
                checked={selected.includes(value)}
                onChange={(event) =>
                  onChange(event.target.checked ? [...selected, value] : selected.filter((other) => other !== value))
                }
              />
              <span className="facet-label">{display(value, label)}</span>
              {count !== undefined && <span className="facet-count">{formatCount(count)}</span>}
            </label>
          </li>
        ))}
      </ul>
      {items.length > shown.length && (
        <button type="button" className="link-button" onClick={() => setExpanded(true)}>
          Show {items.length - shown.length} more
        </button>
      )}
      {expanded && items.length > COLLAPSED && (
        <button type="button" className="link-button" onClick={() => setExpanded(false)}>
          Show fewer
        </button>
      )}
    </fieldset>
  );
}
