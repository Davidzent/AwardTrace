#!/bin/bash
# Deploys one commit to the host (doc 10). The deploy workflow runs it as root through SSM, from a file rather than
# a pipe, since docker compose exec would read the rest of a piped script as its own input:
#   curl -fsSL -o /tmp/deploy.sh https://raw.githubusercontent.com/Davidzent/AwardTrace/<sha>/infra/host/deploy.sh
#   bash /tmp/deploy.sh <sha>
# It fetches that commit's infra/ folder into /opt/awardtrace/releases/<sha>, writes the env file, and starts the
# commit's images. If the API doesn't answer in time, it starts the previous release again and exits non-zero.
set -euo pipefail

sha=${1:?usage: deploy.sh <full commit sha>}
if [[ ! $sha =~ ^[0-9a-f]{40}$ ]]; then
  echo "Not a full commit SHA: ${sha}" >&2
  exit 2
fi
repo=${AWARDTRACE_REPO:-Davidzent/AwardTrace}
root=${AWARDTRACE_ROOT:-/opt/awardtrace}
units=${AWARDTRACE_SYSTEMD_DIR:-/etc/systemd/system}
# The first start creates the Kafka topics, migrates the schema, and waits for Elasticsearch, so it gets minutes.
health_seconds=${AWARDTRACE_HEALTH_SECONDS:-600}
releases_kept=5

log() {
  printf '%s %s\n' "$(date -u +%FT%TZ)" "$*"
}

mkdir -p "${root}/releases"
exec 9> "${root}/deploy.lock"
if ! flock -n 9; then
  echo "Another deploy is running" >&2
  exit 1
fi

# Fetches a commit's infra/ folder once; a release directory is never changed after it is complete.
fetch() {
  local dir=${root}/releases/$1 tmp
  [ -d "${dir}/infra" ] && return
  tmp=$(mktemp -d "${root}/releases/.fetch.XXXXXX")
  curl -fsSL "https://codeload.github.com/${repo}/tar.gz/$1" \
    | tar -xz -C "$tmp" --strip-components=1 --wildcards '*/infra/*'
  mv "$tmp" "$dir"
}

compose() {
  AWARDTRACE_ROOT=$root AWARDTRACE_RELEASE=$1 "$1/infra/host/compose.sh" "${@:2}"
}

start() {
  compose "$1" pull --quiet
  compose "$1" up -d --remove-orphans
}

# Each release brings the host's timers, such as the nightly backup; their services run the current release's scripts.
install_timers() {
  local unit timer
  for unit in "$1"/infra/host/systemd/*.service "$1"/infra/host/systemd/*.timer; do
    install -m 644 "$unit" "${units}/"
  done
  systemctl daemon-reload
  for timer in "$1"/infra/host/systemd/*.timer; do
    systemctl enable "${timer##*/}"
    systemctl restart "${timer##*/}"
  done
}

# The API answers through the Docker network, from the Caddy container, the way public requests reach it.
answers() {
  local deadline=$((SECONDS + health_seconds))
  until compose "$1" exec -T caddy wget -q -O /dev/null http://app:8080/api/v1/status < /dev/null; do
    if ((SECONDS >= deadline)); then
      return 1
    fi
    sleep 5
  done
}

fetch "$sha"
release=${root}/releases/${sha}
previous=$(readlink -f "${root}/current" || true)

"${release}/infra/host/write-env.sh" "${root}/.env"
registry=$(sed -n "s/^AWARDTRACE_REGISTRY='\(.*\)'$/\1/p" "${root}/.env")
region=$(sed -n "s/^AWS_REGION='\(.*\)'$/\1/p" "${root}/.env")
aws ecr get-login-password --region "$region" | docker login --username AWS --password-stdin "$registry"

log "Starting ${sha}"
start "$release"
if answers "$release"; then
  ln -sfn "releases/${sha}" "${root}/current"
  install_timers "$release"
  log "Deployed ${sha}"
  # Older releases and unused images go; a rollback pulls its images again, and ECR keeps the last ten.
  current=$(readlink -f "${root}/current")
  find "${root}/releases" -mindepth 1 -maxdepth 1 -type d -name '[0-9a-f]*' -printf '%T@ %p\n' \
    | sort -rn | tail -n +$((releases_kept + 1)) | cut -d' ' -f2- \
    | while read -r old; do
      [ "$old" = "$current" ] || rm -rf "$old"
    done
  docker image prune --all --force > /dev/null
  exit 0
fi

log "${sha} didn't answer within ${health_seconds}s"
if [ -n "$previous" ] && [ "$previous" != "$release" ] && [ -d "$previous" ]; then
  log "Rolling back to ${previous##*/}"
  start "$previous"
  if answers "$previous"; then
    log "Rolled back to ${previous##*/}"
  else
    log "The previous release isn't answering either"
  fi
fi
exit 1
