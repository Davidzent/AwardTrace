import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import page from './index.html?raw';
import { startWakeButton } from './wake';

const FUNCTION_URL = 'https://wake.example/';

/** The landing page's own markup, with fetch answering from `answers`, keyed by method and path, in order. */
function render(answers: Record<string, (() => Response)[]>) {
  document.body.innerHTML = new DOMParser().parseFromString(page, 'text/html').body.innerHTML;
  const fetch = vi.fn(async (url: URL, init?: RequestInit) => {
    const next = answers[`${init?.method ?? 'GET'} ${url.pathname}`]?.shift();
    if (!next) {
      throw new TypeError('Failed to fetch');
    }
    return next();
  });
  vi.stubGlobal('fetch', fetch);
  const root = document.querySelector<HTMLElement>('[data-wake]') as HTMLElement;
  startWakeButton(root, FUNCTION_URL, 10_000);
  return {
    fetch,
    root,
    message: () => root.querySelector('[data-message]')?.textContent,
    button: () => root.querySelector('button') as HTMLButtonElement,
  };
}

const status = (value: string) => () => Response.json({ status: value });

beforeEach(() => {
  vi.useFakeTimers();
});

afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
});

describe('the wake button', () => {
  it('says when the app is up, and offers nothing to start', async () => {
    const { root, message, button } = render({ 'GET /status': [status('ready')] });

    await vi.waitFor(() => expect(root.hidden).toBe(false));
    expect(message()).toBe('The live app is up.');
    expect(root.dataset.state).toBe('ready');
    expect(button().hidden).toBe(true);
  });

  it('wakes a sleeping app, then checks every 10 seconds until it is up', async () => {
    const { fetch, message, button } = render({
      'GET /status': [status('stopped'), status('starting'), status('ready')],
      'POST /wake': [() => Response.json({ status: 'starting' }, { status: 202 })],
    });
    await vi.waitFor(() => expect(message()).toBe('The live app is asleep. Starting it takes about 5 minutes.'));
    expect(button().hidden).toBe(false);

    button().click();
    await vi.waitFor(() => expect(message()).toMatch(/^The live app is starting\./));
    expect(button().hidden).toBe(true);

    await vi.advanceTimersByTimeAsync(10_000);
    expect(message()).toMatch(/^The live app is starting\./);
    await vi.advanceTimersByTimeAsync(10_000);
    expect(message()).toBe('The live app is up.');

    // Once it's up, the page stops asking.
    const calls = fetch.mock.calls.length;
    await vi.advanceTimersByTimeAsync(60_000);
    expect(fetch.mock.calls.length).toBe(calls);
  });

  it("says when the day's wakes are spent", async () => {
    const { message, button } = render({
      'GET /status': [status('stopped')],
      'POST /wake': [() => Response.json({ status: 'stopped' }, { status: 429 })],
    });
    await vi.waitFor(() => expect(button().hidden).toBe(false));

    button().click();

    await vi.waitFor(() => expect(message()).toMatch(/started 6 times today/));
    expect(button().hidden).toBe(true);
  });

  it('stays hidden when the wake function never answers, so the static hours stand alone', async () => {
    const { fetch, root } = render({});

    await vi.waitFor(() => expect(fetch).toHaveBeenCalledOnce());
    await vi.advanceTimersByTimeAsync(60_000);
    expect(root.hidden).toBe(true);
    expect(fetch).toHaveBeenCalledOnce();
  });
});
