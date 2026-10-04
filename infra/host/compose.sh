#!/bin/bash
# Runs docker compose against the production stack, as deployed, so every command sees the same files, env file, and
# image tag. For example:
#   sudo /opt/awardtrace/current/infra/host/compose.sh ps
#   sudo /opt/awardtrace/current/infra/host/compose.sh logs -f app
# AWARDTRACE_RELEASE points it at another release directory; the deploy script uses that to start a new one.
set -euo pipefail

root=${AWARDTRACE_ROOT:-/opt/awardtrace}
release=$(realpath "${AWARDTRACE_RELEASE:-${root}/current}")
# A release directory is named after its commit, which is also its images' tag.
export IMAGE_TAG=${release##*/}

exec docker compose --env-file "${root}/.env" \
  -f "${release}/infra/compose/compose.yml" -f "${release}/infra/compose/compose.prod.yml" "$@"
