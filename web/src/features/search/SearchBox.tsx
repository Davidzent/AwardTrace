import { useState } from 'react';

/** Submitting searches; typing alone doesn't (doc 08). Remount it with a key to reset it when the URL changes. */
export function SearchBox({ initial, onSearch }: { initial: string; onSearch: (q: string) => void }) {
  const [value, setValue] = useState(initial);
  return (
    <form
      role="search"
      className="search-box"
      onSubmit={(event) => {
        event.preventDefault();
        onSearch(value.trim());
      }}
    >
      <label htmlFor="search-q">Search awards</label>
      <div className="search-box-row">
        <input
          id="search-q"
          type="search"
          value={value}
          maxLength={200}
          placeholder="Descriptions, recipients, or a pasted PIID"
          onChange={(event) => setValue(event.target.value)}
        />
        <button type="submit">Search</button>
      </div>
    </form>
  );
}
