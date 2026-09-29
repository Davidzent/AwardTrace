import { describe, expect, it } from 'vitest';
import { pageItems } from './Pager';

describe('pageItems', () => {
  it('lists every page when there are few', () => {
    expect(pageItems(1, 1)).toEqual([1]);
    expect(pageItems(2, 4)).toEqual([1, 2, 3, 4]);
  });

  it('collapses long runs on either side of the current page', () => {
    expect(pageItems(1, 125)).toEqual([1, 2, null, 125]);
    expect(pageItems(60, 125)).toEqual([1, null, 59, 60, 61, null, 125]);
    expect(pageItems(125, 125)).toEqual([1, null, 124, 125]);
  });

  it('shows a single hidden page instead of a gap', () => {
    expect(pageItems(4, 10)).toEqual([1, 2, 3, 4, 5, null, 10]);
  });
});
