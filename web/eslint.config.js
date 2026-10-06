import js from '@eslint/js';
import reactHooks from 'eslint-plugin-react-hooks';
import { defineConfig, globalIgnores } from 'eslint/config';
import globals from 'globals';
import tseslint from 'typescript-eslint';

export default defineConfig([
  // schema.ts is generated from backend/openapi.json.
  globalIgnores(['dist', 'dist-landing', 'test-results', 'playwright-report', 'src/api/schema.ts']),
  {
    files: ['**/*.{ts,tsx}'],
    extends: [js.configs.recommended, tseslint.configs.recommended, reactHooks.configs.flat.recommended],
    languageOptions: { globals: globals.browser },
  },
  {
    // Configuration and Playwright run in Node, not the browser.
    files: ['*.config.ts', 'tests/**/*.ts'],
    languageOptions: { globals: globals.node },
  },
]);
