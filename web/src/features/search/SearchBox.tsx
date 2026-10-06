import { useState } from 'react';

type Props = {
  initial: string;
  onSearch: (q: string) => void;
  /** The header's version: the label is read aloud but not shown, and the field is smaller. */
  compact?: boolean;
};

/** Submitting searches; typing alone doesn't (doc 08). Remount it with a key to reset it when the URL changes. */
export function SearchBox({ initial, onSearch, compact = false }: Props) {
  const [value, setValue] = useState(initial);
  return (
    <form
      role="search"
      className={compact ? 'search-box search-box-compact' : 'search-box'}
      onSubmit={(event) => {
        event.preventDefault();
        onSearch(value.trim());
      }}
    >
      <label htmlFor="search-q" className={compact ? 'visually-hidden' : undefined}>
        Search awards
      </label>
      <div className="search-box-row">
        <input
          id="search-q"
          type="search"
          value={value}
          maxLength={200}
          placeholder={compact ? 'Search awards' : 'Descriptions, recipients, or a pasted PIID'}
          onChange={(event) => setValue(event.target.value)}
        />
        <button type="submit">Search</button>
      </div>
    </form>
  );
}
