// What the wake function does (ADR 0020), apart from AWS, so a test can run it with stand-ins: index.mjs passes in
// the real calls.

const STATUSES = { pending: 'starting', stopping: 'stopping', 'shutting-down': 'stopping' };

/**
 * Answers the function URL's two requests, given the calls it needs:
 * - instanceState() resolves to the instance's EC2 state, such as "stopped" or "running".
 * - appReady() resolves to whether the app answers its health check.
 * - takeWake(day) resolves to false once the day's wakes are spent, and to true after counting one.
 * - startInstance() starts the instance.
 */
export function route({ instanceState, appReady, takeWake, startInstance }) {
  // A running instance is ready once the app answers; until then it's still starting.
  const statusOf = async (state) => {
    if (state === 'running') {
      return (await appReady()) ? 'ready' : 'starting';
    }
    return STATUSES[state] ?? 'stopped';
  };

  return async (event, now = new Date()) => {
    const request = `${event.requestContext?.http?.method} ${event.rawPath}`;
    if (request === 'GET /status') {
      return reply(200, { status: await statusOf(await instanceState()) });
    }
    if (request === 'POST /wake') {
      // A host that's already up, starting, or stopping is left alone, and the budget with it.
      const state = await instanceState();
      if (state !== 'stopped') {
        return reply(200, { status: await statusOf(state) });
      }
      if (!(await takeWake(now.toISOString().slice(0, 10)))) {
        return reply(429, { status: 'stopped', error: "Today's wakes are spent. Try again tomorrow." });
      }
      await startInstance();
      return reply(202, { status: 'starting' });
    }
    return reply(404, { error: 'Not found' });
  };
}

function reply(statusCode, body) {
  return {
    statusCode,
    // The state changes by the second, so nothing may cache it.
    headers: { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' },
    body: JSON.stringify(body),
  };
}
