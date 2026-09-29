import { afterEach, describe, expect, it, vi } from 'vitest';
import { api, ApiError } from './client';

function respond(status: number, body: string, contentType: string) {
  const fetch = vi.fn(async () => new Response(body, { status, headers: { 'Content-Type': contentType } }));
  vi.stubGlobal('fetch', fetch);
  return fetch;
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('api', () => {
  it('repeats list parameters and leaves out empty ones', async () => {
    const fetch = respond(200, '{"total":0}', 'application/json');

    const results = await api.searchAwards({
      q: 'helicopter',
      state: ['ID', 'VA'],
      fiscal_year: [2026],
      min_amount: '',
      page: 2,
    });

    expect(results.total).toBe(0);
    expect(fetch).toHaveBeenCalledWith(
      '/api/v1/awards/search?q=helicopter&state=ID&state=VA&fiscal_year=2026&page=2',
      {},
    );
  });

  it('escapes identifiers placed in the path', async () => {
    const fetch = respond(200, '{}', 'application/json');

    await api.award('CONT_AWD_A/B');

    expect(fetch).toHaveBeenCalledWith('/api/v1/awards/CONT_AWD_A%2FB', {});
  });

  it('turns problem details into an ApiError', async () => {
    respond(404, '{"type":"award-not-found","status":404,"detail":"No award with ID X"}', 'application/problem+json');

    const error = await api.award('X').catch((reason: unknown) => reason);

    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({ status: 404, message: 'No award with ID X', problem: { type: 'award-not-found' } });
  });

  it('reports a failure without problem details by its status', async () => {
    respond(502, '<html>Bad gateway</html>', 'text/html');

    await expect(api.status()).rejects.toMatchObject({ status: 502, message: 'Request failed with status 502' });
  });
});
