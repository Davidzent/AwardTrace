import { useState } from 'react';
import type { Schemas } from '../api/client';
import { formatCount } from '../lib/format';
import { Button } from './Button';
import styles from './FacetGroup.module.css';

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
    <fieldset>
      <legend>{title}</legend>
      <ul className={styles.list}>
        {shown.map(({ value = '', label, count }) => (
          <li key={value}>
            <label className={styles.option}>
              <input
                type="checkbox"
                checked={selected.includes(value)}
                onChange={(event) =>
                  onChange(event.target.checked ? [...selected, value] : selected.filter((other) => other !== value))
                }
              />
              <span className={styles.label}>{display(value, label)}</span>
              {count !== undefined && <span className={styles.count}>{formatCount(count)}</span>}
            </label>
          </li>
        ))}
      </ul>
      {items.length > shown.length && (
        <Button variant="quiet" className={styles.more} onClick={() => setExpanded(true)}>
          Show {items.length - shown.length} more
        </Button>
      )}
      {expanded && items.length > COLLAPSED && (
        <Button variant="quiet" className={styles.more} onClick={() => setExpanded(false)}>
          Show fewer
        </Button>
      )}
    </fieldset>
  );
}
