import type { Schemas } from '../api/client';

const AI_NOTE = 'Assigned by a language model from the description, so it can be wrong';

type Category = Pick<Schemas['CategoryRef'], 'code' | 'label' | 'source'>;

/** An award's category as a badge, marked AI when the classifier chose it (doc 08). */
export function CategoryBadge({ category }: { category: Category | undefined }) {
  if (!category?.code) {
    return null;
  }
  // One element, so a list of facts keeps the two badges together.
  return (
    <span>
      <span className="badge">{category.label ?? category.code}</span>
      {category.source === 'llm' && (
        <span className="badge badge-ai" title={AI_NOTE}>
          AI<span className="visually-hidden">: {AI_NOTE}</span>
        </span>
      )}
    </span>
  );
}
