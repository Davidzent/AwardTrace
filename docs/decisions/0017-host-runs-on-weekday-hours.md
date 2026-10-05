# 0017: Host runs on weekday hours

| | |
|---|---|
| Status | Accepted |
| Date | 2026-10-04 |

## Context

The host costs about $2.30 a day running and $0.23 a day stopped, paid from the AWS free plan's $100 in credits ([ADR 0015](0015-x86-host-on-the-aws-free-plan.md)). Always on, the credits last about six weeks. The site serves people reviewing a portfolio, who mostly look during working hours.

## Decision

EventBridge Scheduler starts the host at 7:50 and stops it at 20:00, Monday to Friday, Pacific time. The start is 10 minutes early because the stack takes 3 to 5 minutes to boot. Scheduler calls the EC2 API directly, through a role that can only start and stop this instance, and only from the `awardtrace` schedule group.

Outside those hours, the host stops itself after 30 minutes without API requests, so a start for a demo or a shared link never runs all night. A timer on the host checks every 5 minutes, counting only requests to routes the API serves.

A deploy starts a stopped host before it runs. The Host workflow in GitHub's Actions tab starts or stops the host by hand. Both use the deploy role, which can start and stop only the host tagged `app=awardtrace`, and only from `main`; running the workflow takes write access to the repository.

## Consequences

- The host costs about $29 a month instead of $69, so $100 lasts about 3.4 months.
- Outside those hours, the site doesn't answer until someone starts the host, through the Host workflow or the AWS console. A host started then stops on its own once it sits idle.
- Scanners and probes for other paths don't count as requests, so they can't keep the host awake.
- An ingest or a deploy in progress holds off the idle stop. Consumers still catching up with Kafka don't; they resume at the next start.
- The hours live in two places: the schedules in `envs/prod` and `infra/host/idle-stop.sh`. A change has to update both.
- A deploy outside those hours starts the host, and the idle stop stops it again 30 minutes later.
- The nightly backup runs at each weekday start instead, because its timer makes up missed runs at boot. The weekly ingest moves to Mondays at 18:00 UTC, inside the hours in both standard and daylight time, so it runs while the stack is up rather than during the boot.
- The rebuild drill recreates the schedules along with the rest of `envs/prod`.

## Alternatives considered

| Alternative | Why not |
|---|---|
| Always on | $69 a month, so the credits last about six weeks |
| Start the host when someone visits | Needs CloudFront in front of the site, and the first visitor waits 3 to 5 minutes. It can be added on top of the schedule later |
| A Lambda function on a schedule | Code to maintain for two API calls that Scheduler makes itself |
| A CloudWatch alarm that stops the host on low network traffic | Scanners keep the traffic up, so the host would rarely stop |
| Upgrade to the paid plan | Keeps the site up, but the account can then charge a card |
