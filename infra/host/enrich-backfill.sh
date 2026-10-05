#!/bin/bash
# Classifies every description no classification covers, through Message Batches at half price (doc 09), in a one-off
# container beside the running stack, and exits with its status. The argument caps the run's spend in US dollars: a
# batch whose worst case could take the run past it isn't submitted. A run takes hours, longer than an SSM session stays
# open, so start it as a transient unit and follow its log:
#   sudo systemd-run --unit=awardtrace-backfill /opt/awardtrace/current/infra/host/enrich-backfill.sh 9
#   sudo journalctl -fu awardtrace-backfill
# The host's 20:00 Pacific stop (ADR 0017) ends a run that's still going. Its batches stay recorded, and running it
# again collects them before it submits more, so none is paid for twice.
set -euo pipefail

cap=${1:?usage: enrich-backfill.sh <cap in US dollars>}
if [[ ! $cap =~ ^[0-9]+(\.[0-9]{1,2})?$ ]]; then
  echo "Not a dollar amount: ${cap}" >&2
  exit 2
fi

root=${AWARDTRACE_ROOT:-/opt/awardtrace}
here=$(dirname "$(realpath "$0")")

# One backfill at a time, and idle-stop.sh doesn't stop the host while it runs.
exec 9> "${root}/enrich.lock"
if ! flock -n 9; then
  echo "Another backfill is running" >&2
  exit 1
fi

# The running stack already holds about 3.5 GiB of the host's 4, so the task gets an ingest task's smaller heap.
AWARDTRACE_ROOT=$root exec "${here}/compose.sh" run --rm --no-deps \
  --env JAVA_TOOL_OPTIONS="-Xms128m -Xmx384m" \
  app --spring.profiles.active=enricher --awardtrace.enrichment.task=backfill \
  --awardtrace.enrichment.backfill.cap-usd="$cap"
