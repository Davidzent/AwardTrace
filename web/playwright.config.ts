import { defineConfig } from '@playwright/test';

/**
 * End-to-end flows against the running stack (doc 12): the api role on port 8080 with awards loaded, and this app's
 * dev server, which Playwright starts unless one is already running. Tests drive the installed Google Chrome, so
 * there is no browser to download; GitHub's hosted runners have it as well.
 */
export default defineConfig({
  testDir: 'tests/e2e',
  fullyParallel: true,
  // Ten Chrome windows at once starve each other: lazy pages miss their timeouts and text paints in the fallback font
  // after Public Sans has loaded, which fails the screenshots. Four keep every run stable for a few seconds more.
  workers: 4,
  reporter: 'list',
  use: {
    baseURL: 'http://localhost:5173',
    channel: 'chrome',
    trace: 'retain-on-failure',
  },
  // Every page works at 360 pixels (doc 08); at that width the filter column becomes a drawer.
  projects: [
    { name: 'desktop', use: { viewport: { width: 1280, height: 800 } } },
    { name: 'mobile', use: { viewport: { width: 360, height: 780 }, hasTouch: true } },
  ],
  webServer: {
    command: 'npm run dev',
    url: 'http://localhost:5173',
    reuseExistingServer: true,
  },
});
