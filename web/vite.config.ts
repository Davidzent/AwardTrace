/// <reference types="vitest/config" />
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

// In production, Caddy serves the app and the API from one origin. The dev server proxies /api to the api role the
// same way, so the app never needs CORS or an API base URL.
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
  test: {
    environment: 'jsdom',
    // Playwright runs tests/e2e.
    include: ['src/**/*.test.{ts,tsx}'],
  },
});
