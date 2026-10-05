import type { SearchQuery } from '../../api/client';
import { FacetGroup } from '../../components/FacetGroup';
import { RangeFilter } from '../../components/RangeFilter';
import { CATEGORY_SOURCES, FACETS, type Facets, selected, selecting } from './filters';

type Props = {
  query: SearchQuery;
  facets: Facets;
  onChange: (change: SearchQuery) => void;
};

/**
 * Every filter of the search page. The ranges and the category source stay usable while results load; a facet appears
 * once it has values or a selection.
 */
export function FilterPanel({ query, facets, onChange }: Props) {
  return (
    <div className="filter-panel">
      {FACETS.map((facet) => {
        const values = facets?.[facet.name] ?? [];
        const chosen = selected(query, facet.name);
        if (values.length === 0 && chosen.length === 0) {
          return null;
        }
        return (
          <FacetGroup
            key={facet.name}
            title={facet.title}
            values={values}
            selected={chosen}
            display={facet.name === 'naics' ? (value, label) => (label ? `${value} ${label}` : value) : facet.chip}
            onChange={(next) => onChange(selecting(facet.name, next))}
          />
        );
      })}
      <fieldset className="facet">
        <legend>Category source</legend>
        <ul>
          {CATEGORY_SOURCES.map(({ value, label }) => (
            <li key={label}>
              <label>
                <input
                  type="radio"
                  name="category_source"
                  checked={query.category_source === value}
                  onChange={() => onChange({ category_source: value })}
                />
                <span className="facet-label">{label}</span>
              </label>
            </li>
          ))}
        </ul>
      </fieldset>
      <RangeFilter
        key={`amount:${query.min_amount}:${query.max_amount}`}
        legend="Amount"
        kind="money"
        min={query.min_amount}
        max={query.max_amount}
        onApply={(min, max) => onChange({ min_amount: min, max_amount: max })}
      />
      <RangeFilter
        key={`last-action:${query.from}:${query.to}`}
        legend="Last action"
        kind="date"
        min={query.from}
        max={query.to}
        onApply={(from, to) => onChange({ from, to })}
      />
    </div>
  );
}
