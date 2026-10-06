// node --test infra/terraform/modules/wake/function/wake.test.mjs
import assert from 'node:assert/strict';
import { test } from 'node:test';
import { route } from './wake.mjs';

const request = (method, rawPath) => ({ rawPath, requestContext: { http: { method } } });

/** The function with stand-ins for AWS: an instance in the given state, and a budget of six wakes a day. */
function stubbed(state, { ready = false } = {}) {
  const calls = { started: 0, wakes: new Map() };
  const handler = route({
    instanceState: async () => state,
    appReady: async () => ready,
    takeWake: async (day) => {
      const taken = calls.wakes.get(day) ?? 0;
      if (taken >= 6) {
        return false;
      }
      calls.wakes.set(day, taken + 1);
      return true;
    },
    startInstance: async () => {
      calls.started += 1;
    },
  });
  return { handler, calls };
}

const status = async (handler, event, now) => JSON.parse((await handler(event, now)).body).status;

test('reports the app ready only once it answers its health check', async () => {
  assert.equal(await status(stubbed('stopped').handler, request('GET', '/status')), 'stopped');
  assert.equal(await status(stubbed('pending').handler, request('GET', '/status')), 'starting');
  assert.equal(await status(stubbed('running').handler, request('GET', '/status')), 'starting');
  assert.equal(await status(stubbed('running', { ready: true }).handler, request('GET', '/status')), 'ready');
  assert.equal(await status(stubbed('stopping').handler, request('GET', '/status')), 'stopping');
});

test('refuses the seventh wake of a UTC day, and allows one the next day', async () => {
  const { handler, calls } = stubbed('stopped');
  const day = new Date('2026-10-05T23:00:00Z');
  for (let wake = 1; wake <= 6; wake += 1) {
    assert.equal((await handler(request('POST', '/wake'), day)).statusCode, 202);
  }

  const refused = await handler(request('POST', '/wake'), day);
  assert.equal(refused.statusCode, 429);
  assert.equal(calls.started, 6);
  assert.equal((await handler(request('POST', '/wake'), new Date('2026-10-06T00:30:00Z'))).statusCode, 202);
});

test("doesn't start an instance that isn't stopped, or spend a wake on it", async () => {
  for (const state of ['running', 'pending', 'stopping']) {
    const { handler, calls } = stubbed(state);
    const response = await handler(request('POST', '/wake'));
    assert.equal(response.statusCode, 200);
    assert.equal(calls.started, 0);
    assert.equal(calls.wakes.size, 0);
  }
});

test('answers nothing else, and lets no response be cached', async () => {
  const { handler } = stubbed('stopped');
  assert.equal((await handler(request('GET', '/wake'))).statusCode, 404);
  assert.equal((await handler(request('POST', '/status'))).statusCode, 404);
  assert.equal((await handler(request('GET', '/status'))).headers['Cache-Control'], 'no-store');
});
