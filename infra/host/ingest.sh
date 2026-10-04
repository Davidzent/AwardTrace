#!/bin/bash
# Runs one ingest task (docs 02 and 03) in a one-off container beside the running stack, and exits with its status:
#   sudo /opt/awardtrace/current/infra/host/ingest.sh backfill   once, on the larger instance (doc 10)
#   sudo /opt/awardtrace/current/infra/host/ingest.sh delta      what the weekly awardtrace-ingest timer runs
#   sudo /opt/awardtrace/current/infra/host/ingest.sh replay     the rebuild drill: publish every file in S3 again
set -euo pipefail

task=${1:?usage: ingest.sh backfill|delta|replay}
case $task in
  backfill | delta | replay) ;;
  *)
    echo "Unknown ingest task: ${task}" >&2
    exit 2
    ;;
esac

root=${AWARDTRACE_ROOT:-/opt/awardtrace}
here=$(dirname "$(realpath "$0")")

# One ingest at a time (doc 02), whether the timer or a person starts it.
exec 9> "${root}/ingest.lock"
if ! flock -n 9; then
  echo "Another ingest is running" >&2
  exit 1
fi

# The running stack already holds about 3.5 GiB of the host's 4, so the task gets a smaller heap than the app's;
# streaming files to Kafka needs little.
AWARDTRACE_ROOT=$root exec "${here}/compose.sh" run --rm --no-deps \
  --env JAVA_TOOL_OPTIONS="-Xms128m -Xmx384m" \
  app --spring.profiles.active=ingest --awardtrace.ingest.task="$task"
