import { useState } from 'react';

type Props = {
  legend: string;
  kind: 'money' | 'date';
  min: string | undefined;
  max: string | undefined;
  onApply: (min: string | undefined, max: string | undefined) => void;
};

const AMOUNT = /^\d+(\.\d{1,2})?$/;

/**
 * A minimum and a maximum, applied together. Money accepts $ and thousands separators; the check that the minimum
 * isn't above the maximum happens here, before the API would refuse it. Remount it with a key when the URL changes.
 */
export function RangeFilter({ legend, kind, min, max, onApply }: Props) {
  const [from, setFrom] = useState(min ?? '');
  const [to, setTo] = useState(max ?? '');
  const [error, setError] = useState<string>();
  const id = legend.toLowerCase().replaceAll(' ', '-');

  function apply() {
    const low = normalize(from);
    const high = normalize(to);
    if (kind === 'money' && [low, high].some((amount) => amount && !AMOUNT.test(amount))) {
      setError('Enter amounts in dollars, such as 25000 or 1,250.50.');
    } else if (low && high && (kind === 'money' ? Number(low) > Number(high) : low > high)) {
      setError('The minimum is above the maximum.');
    } else {
      setError(undefined);
      onApply(low || undefined, high || undefined);
    }
  }

  function normalize(value: string): string {
    return kind === 'money' ? value.replaceAll(/[$,\s]/g, '') : value;
  }

  return (
    <fieldset className="range">
      <legend>{legend}</legend>
      <form
        onSubmit={(event) => {
          event.preventDefault();
          apply();
        }}
      >
        <div className="range-fields">
          <label>
            {kind === 'money' ? 'Min' : 'From'}
            <input
              type={kind === 'date' ? 'date' : 'text'}
              inputMode={kind === 'money' ? 'decimal' : undefined}
              value={from}
              aria-invalid={error ? true : undefined}
              aria-describedby={error ? `${id}-error` : undefined}
              onChange={(event) => setFrom(event.target.value)}
            />
          </label>
          <label>
            {kind === 'money' ? 'Max' : 'To'}
            <input
              type={kind === 'date' ? 'date' : 'text'}
              inputMode={kind === 'money' ? 'decimal' : undefined}
              value={to}
              aria-invalid={error ? true : undefined}
              aria-describedby={error ? `${id}-error` : undefined}
              onChange={(event) => setTo(event.target.value)}
            />
          </label>
        </div>
        {error && (
          <p id={`${id}-error`} className="range-error" role="alert">
            {error}
          </p>
        )}
        <button type="submit">Apply</button>
      </form>
    </fieldset>
  );
}
