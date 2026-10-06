/// <reference types="node" />
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { defineConfig } from 'vite';
import { accuracies } from './landing/results.ts';

/** The model whose categories the site shows (ADR 0013). */
const SITE_MODEL = 'claude-haiku-4-5';

/**
 * The landing page (ADR 0019): plain HTML and CSS from landing/, sharing the app's tokens, typeface, and mark, built to
 * dist-landing/ for GitHub Pages. The classifier's results are written in from docs/results.md at build time, so the
 * page needs nothing running and never disagrees with the results.
 */
export default defineConfig({
  root: 'landing',
  // Relative URLs work at the custom domain and at the repository's github.io path alike.
  base: './',
  publicDir: '../public',
  build: {
    outDir: '../dist-landing',
    emptyOutDir: true,
    rolldownOptions: {
      // 404.html sends old links to the app on to its new hostname (ADR 0019).
      input: ['index.html', '404.html'].map((page) => fileURLToPath(new URL(`landing/${page}`, import.meta.url))),
    },
  },
  plugins: [
    {
      name: 'awardtrace-results',
      transformIndexHtml(html) {
        const results = readFileSync(new URL('../docs/results.md', import.meta.url), 'utf8');
        const { baseline, classifier } = accuracies(results, SITE_MODEL);
        return html
          .replaceAll('{{baseline}}', baseline)
          .replaceAll('{{classifier}}', classifier)
          // The wake function's URL (ADR 0020), from Terraform's wake_function_url output; empty leaves the button off.
          .replaceAll('{{wakeUrl}}', process.env.WAKE_FUNCTION_URL ?? '');
      },
    },
  ],
});
