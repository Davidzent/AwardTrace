# 0019: The landing page lives on GitHub Pages, and the app moves to its own hostname

| | |
|---|---|
| Status | Accepted |
| Date | 2026-10-05 |

## Context

`awardtrace.zntsns.com` is the address on the README and the resume, and today the EC2 host serves it. The host is stopped about two thirds of the week ([ADR 0017](0017-host-runs-on-weekday-hours.md)), so a reviewer who follows the link in the evening or on a weekend finds nothing, and a page the host serves can't explain the project or say when the app is back. The AWS free plan's credits also run out ([ADR 0015](0015-x86-host-on-the-aws-free-plan.md)), and the project's front door should outlast them.

## Decision

The front door and the app get separate hostnames:

| Hostname | Serves | From |
|---|---|---|
| `awardtrace.zntsns.com` | A static landing page: what AwardTrace is, its results, its architecture, and the app's hours | GitHub Pages |
| `app.awardtrace.zntsns.com` | The app and its API | The EC2 host, through Caddy, as today |

`pages.yml` builds the landing page from `web/` as a second Vite entry, so it shares the app's tokens, typeface, and wordmark, and deploys it with `actions/deploy-pages` on each push to `main` that changes it.

The landing page never requests `/api/*` on the app. The idle stop counts those requests to decide the host is in use ([ADR 0017](0017-host-runs-on-weekday-hours.md)), so opening the landing page must not keep the host awake.

An old deep link, such as `awardtrace.zntsns.com/awards/{id}`, reaches the landing page's 404 page, which sends it to the same path on `app.awardtrace.zntsns.com`.

The cutover runs in this order, so the app answers throughout:

1. At Squarespace, add an `A` record for `app.awardtrace` that points at the Elastic IP.
2. Deploy Caddy answering both names, so it obtains the new certificate while the old name still works.
3. Turn on GitHub Pages with the custom domain `awardtrace.zntsns.com`, and verify the domain in the GitHub account settings.
4. At Squarespace, replace the `awardtrace` `A` record with a `CNAME` for `davidzent.github.io`. Once GitHub has issued its certificate, turn on **Enforce HTTPS**.
5. Remove the old name from Caddy, and point the Host workflow's check, the README, the About page, and the repository's website link at the new names.

## Consequences

- The landing page answers at any hour, costs nothing, and stays up after the AWS account closes.
- Links already on the resume and README land on the landing page, which links to the app.
- The site has two deploy paths: `deploy.yml` for the app, `pages.yml` for the landing page.
- Caddy sends `Strict-Transport-Security` for `awardtrace.zntsns.com`, so a browser that has visited it refuses plain HTTP there. Between step 4 and GitHub issuing its certificate, those browsers show a certificate error. Run step 4 outside the host's hours.
- If Pages is ever turned off while the `CNAME` remains, someone else could claim the name on GitHub. Verifying the domain on the account prevents that.
- The event schemas keep `https://awardtrace.zntsns.com/events/...` as their `$id`. It names each schema and is never fetched, so it doesn't move.
- DNS stays at Squarespace, changed by hand, as it is today.

## Alternatives considered

| Alternative | Why not |
|---|---|
| One hostname, with the host serving the landing page | Down whenever the host is, about two thirds of the week |
| S3 and CloudFront for the landing page | Spends the credits, ends with the AWS account, and adds Terraform for a static page |
| Cloudflare Pages or Netlify | Another account and dashboard for what GitHub Pages does for free beside the code |
| The landing page on a new name, such as `about.awardtrace.zntsns.com`, with the app keeping `awardtrace.zntsns.com` | The address on the resume and README would keep pointing at a host that's often off |
| GitHub Pages without a custom domain, at `davidzent.github.io/AwardTrace` | A second, unbranded address that the existing links don't reach |
