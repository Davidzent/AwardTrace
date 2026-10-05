#!/bin/bash
# Stops the host after 30 minutes without API requests outside its weekday hours (ADR 0017), so a start for a demo or
# a shared link never runs all night. The awardtrace-idle timer runs it every 5 minutes; inside the hours, the stop
# schedule decides instead. Powering off stops the instance, since its shutdown behavior is stop.
set -euo pipefail

root=${AWARDTRACE_ROOT:-/opt/awardtrace}
here=$(dirname "$(realpath "$0")")
# awardtrace-idle.service keeps this directory between runs, and /run empties at boot, so every start begins a fresh
# idle clock.
state=${RUNTIME_DIRECTORY:?run it through awardtrace-idle.service}/state
idle_seconds=$((30 * 60))

# The schedule's hours from infra/terraform/envs/prod/main.tf: weekdays, 7:50 to 20:00 Pacific. Without time zone
# data, date falls back to UTC, so this exits rather than stop the host in the middle of the day.
# ponytail: the hours live in two places; pass them through the env file if they start to change.
read -r day hhmm offset < <(TZ=America/Los_Angeles date '+%u %H%M %z')
case $offset in
  -0700 | -0800) ;;
  *)
    echo "No time zone data for America/Los_Angeles" >&2
    exit 1
    ;;
esac
if ((day <= 5 && 10#$hhmm >= 750 && 10#$hhmm < 2000)); then
  rm -f "$state"
  exit 0
fi

# Never in the middle of an ingest, a classification backfill, or a deploy, which hold these locks while they run.
for lock in ingest.lock enrich.lock deploy.lock; do
  if ! flock -n "${root}/${lock}" true; then
    exit 0
  fi
done

# API requests served since the app started, or -1 while it can't answer. Only routes the API serves count, so
# scanners probing other paths can't keep the host awake.
requests=$(AWARDTRACE_ROOT=$root "${here}/compose.sh" exec -T caddy \
  wget -q -O - http://app:8080/actuator/prometheus < /dev/null 2> /dev/null \
  | awk '/^http_server_requests_seconds_count\{.*uri="\/api\// { sum += $NF } END { printf "%d", sum }') \
  || requests=-1

now=$(date +%s)
if [ -f "$state" ]; then
  read -r last_requests since < "$state"
  if [ "$requests" = "$last_requests" ]; then
    if ((now - since >= idle_seconds)); then
      echo "No API requests for $((idle_seconds / 60)) minutes outside the weekday hours; stopping the host"
      systemctl poweroff
    fi
    exit 0
  fi
fi
echo "$requests $now" > "$state"
