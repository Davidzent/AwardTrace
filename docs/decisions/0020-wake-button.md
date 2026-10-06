# 0020: The landing page wakes the host through a Lambda function, six times a day at most

| | |
|---|---|
| Status | Accepted |
| Date | 2026-10-05 |

## Context

The host runs weekdays from 7:50 to 20:00 Pacific and stops itself 30 minutes after the last API request outside those hours ([ADR 0017](0017-host-runs-on-weekday-hours.md)). Today the only ways to start it are the Host workflow, which takes write access to the repository, and the AWS console. The landing page is always on ([ADR 0019](0019-landing-page-on-github-pages.md)), but a page can't start the host itself, and anything it calls has to be cheap, safe to call from anywhere, and unable to keep the host awake.

## Decision

A Lambda function with a public function URL answers two requests:

| Request | What it does |
|---|---|
| `GET /status` | Returns `stopped`, `starting`, `ready`, or `stopping`. It reads the instance's state, and while the instance runs it asks the app whether it's ready |
| `POST /wake` | Takes one wake from the day's budget, then starts the instance if it's stopped. It refuses with `429` once the budget is spent, and starts nothing when the instance isn't stopped |

How it works:

- **Readiness.** Caddy answers `/healthz` by passing it to the app's own health check. The path is outside `/api/`, so the idle stop doesn't count it, and polling it can't keep the host awake. The function checks it, not the landing page, so the page talks to one origin only.
- **The budget.** One DynamoDB item per UTC day counts the day's wakes. The function adds to it with a conditional update that fails at 6, so two wakes at once can't both take the last one. Items expire on their own after a few days.
- **Permissions.** The function's role can describe instances, which AWS doesn't scope by resource, and start only the instance tagged `app=awardtrace`. It can't stop or terminate anything, and it can read and write only the budget table.
- **Browsers.** The function URL allows cross-origin requests from `https://awardtrace.zntsns.com` only.
- **The page.** It calls `GET /status` on load. When the app is stopped, it offers **Start the live app**, says it takes a few minutes, and polls every 10 seconds after a wake until the app is ready.

Terraform creates the function, its role, its URL, and the table in a new `wake` module, and zips the function's source with the `hashicorp/archive` provider. The function is a single Node.js file using the AWS SDK built into the Lambda runtime, so it has no dependencies to install.

## Consequences

- A visitor at any hour reaches the live app in a few minutes without anyone's help.
- A wake that nobody uses ends about 40 minutes later: the boot, the 30-minute idle clock, and up to 5 minutes until the idle timer next runs. Six a day add about 4 host hours, or $0.35, a day. Lambda and DynamoDB stay within their free tiers.
- Anyone can call the function URL from a script; the CORS rule only stops other sites' pages. The budget bounds what that costs. If someone other than visitors ever spends it, add Cloudflare Turnstile to the button, as doc 16 plans.
- A burst of calls could cost Lambda invocations beyond the free tier. Reserving a concurrency of 1 bounds the rate, but AWS refuses any reservation in an account whose concurrency limit is 10, as new accounts' often is; check the limit before applying, and leave the reservation out if it's 10.
- A wake while the host is stopping fails; the page says to try again in a minute.
- The function URL changes if the function is recreated, and the landing page has to be rebuilt with the new one.

## Alternatives considered

| Alternative | Why not |
|---|---|
| No wake button: show the hours, and a demo video outside them | A visitor before 7:50 Pacific, which is 10:50 Eastern, finds the app off with no way in |
| The landing page calls the GitHub API to run the Host workflow | Needs a token with write access to the repository, in a public page |
| API Gateway in front of the function | More resources for what a function URL does with CORS built in |
| The page polls `/healthz` on the app directly | A second origin to allow in Caddy, and the page couldn't tell a stopped host from a slow network |
| A counter in SSM Parameter Store instead of DynamoDB | No atomic conditional update, so two wakes at once could both take the last one |
