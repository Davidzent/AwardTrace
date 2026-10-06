# Load tests

Three [k6](https://k6.io) scenarios measure the API against its latency targets. Each ramps to 20 requests a second over a minute and holds that rate for five.

| Scenario | Requests | Passes when |
|---|---|---|
| `search.js` | Searches from [searches.json](searches.json), keywords and filters taken from the Agriculture data | p95 under 300 ms, and fewer than 0.1% of requests fail |
| `detail.js` | Award detail for 500 of the newest awards, picked at random | p95 under 150 ms, and fewer than 0.1% of requests fail |
| `mixed.js` | 70% search, 25% detail, and 5% recipient, while a delta file ingests | Both targets above hold |

The scenarios target `https://app.awardtrace.zntsns.com` unless `BASE_URL` says otherwise. The measured latency includes the network between the load generator and the host, so record where the test ran.

## Before you begin

- Install k6, or use its Docker image: `docker run --rm -v "$PWD/loadtest:/scripts" grafana/k6 run /scripts/search.js`.
- Start the host if it's outside its weekday hours, from the landing page or the Host workflow ([ADR 0017](../docs/decisions/0017-host-runs-on-weekday-hours.md)).
- Open a shell on the host with `aws ssm start-session --target <instance ID>`; Terraform's `host_instance_id` output has the ID.

## Exempt the load generator from the rate limits

The API allows each client address 60 searches and 120 other requests a minute, so a single load generator would otherwise get `429` responses within seconds. Exempt its address for the run only:

1. On the load generator, find its public address:

   ```sh
   curl -s https://checkip.amazonaws.com
   ```

2. On the host, recreate the app with that address exempt. The variable is set only for this command, so it ends with the run:

   ```sh
   sudo AWARDTRACE_RATE_LIMIT_EXEMPT_ADDRESSES=203.0.113.5 /opt/awardtrace/current/infra/host/compose.sh up -d app
   ```

3. Wait until `curl -s https://app.awardtrace.zntsns.com/healthz` returns `"status":"UP"`.

## Run a scenario

```sh
k6 run loadtest/search.js
k6 run loadtest/detail.js
```

For `mixed.js`, start a delta ingest on the host a minute into the run, once the rate holds at 20 a second:

```sh
k6 run loadtest/mixed.js
```

```sh
sudo /opt/awardtrace/current/infra/host/ingest.sh delta
```

If USAspending hasn't published a newer monthly file, the delta has nothing to load; say so when you record the result.

k6 prints each threshold as passed or failed, and exits with a nonzero status if any failed. Record the p95 values, the error rate, and where the load generator ran in [docs/results.md](../docs/results.md).

## Afterward

Recreate the app without the exemption:

```sh
sudo /opt/awardtrace/current/infra/host/compose.sh up -d app
```
