import { describe, expect, it } from 'vitest';
import { accuracies } from './results';

const RESULTS = `# Classification results

| Classifier | Prompt | Accuracy | Accuracy on clear rows | Run |
|---|---|---|---|---|
| PSC baseline | - | 54.0% | 59.7% | 2026-10-05 |
| [claude-haiku-4-5](../eval/results/claude-haiku-4-5-v1.md) | v1 | 66.5% | 65.7% | 2026-10-05 |
| [claude-opus-5-5](../eval/results/claude-opus-5-5-v1.md) | v1 | 76.5% | 77.9% | 2026-10-05 |
`;

describe('accuracies', () => {
  it("reads the baseline's and the model's accuracy on clear rows", () => {
    expect(accuracies(RESULTS, 'claude-haiku-4-5')).toEqual({ baseline: '59.7%', classifier: '65.7%' });
  });

  it('refuses a model or a column the table lacks', () => {
    expect(() => accuracies(RESULTS, 'claude-sonnet-5-5')).toThrow('claude-sonnet-5-5');
    expect(() => accuracies(RESULTS.replace('Accuracy on clear rows', 'Accuracy'), 'claude-haiku-4-5')).toThrow();
  });
});
