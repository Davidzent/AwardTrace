import type { Schemas } from '../api/client';

/** An award's category as a badge (doc 08). The AI marker for classifier categories arrives with Phase 5. */
export function CategoryBadge({ category }: { category: Pick<Schemas['CategoryRef'], 'code' | 'label'> | undefined }) {
  if (!category?.code) {
    return null;
  }
  return <span className="badge">{category.label ?? category.code}</span>;
}
