import { useState } from 'react';
import { Button } from '../../components/Button';
import styles from './SearchBox.module.css';

type Props = {
  initial: string;
  onSearch: (q: string) => void;
  /** The header's version: the label is read aloud but not shown, and the button names the field. */
  compact?: boolean;
};

/** Submitting searches; typing alone doesn't (doc 08). Remount it with a key to reset it when the URL changes. */
export function SearchBox({ initial, onSearch, compact = false }: Props) {
  const [value, setValue] = useState(initial);
  return (
    <form
      role="search"
      className={compact ? styles.compact : undefined}
      onSubmit={(event) => {
        event.preventDefault();
        onSearch(value.trim());
      }}
    >
      <label htmlFor="search-q" className={compact ? 'visually-hidden' : styles.label}>
        Search awards
      </label>
      <div className={styles.row}>
        <input
          id="search-q"
          className={styles.input}
          type="search"
          value={value}
          maxLength={200}
          placeholder={compact ? 'Search awards' : 'Descriptions, recipients, or a pasted PIID'}
          onChange={(event) => setValue(event.target.value)}
        />
        <Button type="submit" variant="primary" className={styles.submit}>
          Search
        </Button>
      </div>
    </form>
  );
}
