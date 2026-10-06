/**
 * The "Accuracy on clear rows" of the PSC baseline and of one model, as docs/results.md's table writes them, such as
 * "59.7%". Throws when the table, the column, or either row is missing, so the landing page never ships without them.
 */
export function accuracies(markdown: string, model: string): { baseline: string; classifier: string } {
  const rows = markdown
    .split('\n')
    .filter((line) => line.startsWith('|'))
    .map((line) => line.split('|').slice(1, -1).map((cell) => cell.trim()));
  const column = rows[0]?.indexOf('Accuracy on clear rows') ?? -1;
  const accuracy = (matches: (name: string) => boolean, what: string) => {
    const value = rows.find((row) => matches(row[0] ?? ''))?.[column];
    if (column < 0 || !value) {
      throw new Error(`docs/results.md has no accuracy on clear rows for ${what}`);
    }
    return value;
  };
  return {
    baseline: accuracy((name) => name === 'PSC baseline', 'the PSC baseline'),
    classifier: accuracy((name) => name.startsWith(`[${model}]`), model),
  };
}
