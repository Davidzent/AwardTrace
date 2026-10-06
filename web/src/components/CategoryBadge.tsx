import { useId } from 'react';
import type { Schemas } from '../api/client';
import { Badge } from './Badge';
import styles from './CategoryBadge.module.css';

const AI_NOTE = 'Assigned by a language model from the description, so it can be wrong.';

type Category = Pick<Schemas['CategoryRef'], 'code' | 'label' | 'source'>;

/**
 * An award's category as a badge, marked AI when the classifier chose it (doc 08). The AI mark is a button that
 * opens its note as a native popover, so it works by mouse, keyboard, and touch, and Escape or a tap outside closes it.
 */
export function CategoryBadge({ category }: { category: Category | undefined }) {
  const noteId = useId();
  if (!category?.code) {
    return null;
  }
  // One element, so a list of facts keeps the two badges together.
  return (
    <span className={styles.category}>
      <Badge>{category.label ?? category.code}</Badge>
      {category.source === 'llm' && (
        <>
          <button type="button" className={styles.ai} popoverTarget={noteId} aria-describedby={noteId}>
            AI
          </button>
          <span id={noteId} popover="auto" className={styles.note}>
            {AI_NOTE}
          </span>
        </>
      )}
    </span>
  );
}
