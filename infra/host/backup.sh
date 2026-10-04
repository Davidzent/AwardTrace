#!/bin/bash
# Dumps the database to the raw bucket's backups/ prefix (docs 04 and 10), where a lifecycle rule keeps 14 days. The
# awardtrace-backup timer runs it nightly; it also runs by hand:
#   sudo /opt/awardtrace/current/infra/host/backup.sh
# To restore a dump into the running stack:
#   aws s3 cp s3://<bucket>/backups/<name>.dump - | sudo /opt/awardtrace/current/infra/host/compose.sh \
#     exec -T postgres pg_restore -U awardtrace -d awardtrace --clean --if-exists
# Elasticsearch has no backup; the reindex task rebuilds it from PostgreSQL.
set -euo pipefail

root=${AWARDTRACE_ROOT:-/opt/awardtrace}
here=$(dirname "$(realpath "$0")")
bucket=$(sed -n "s/^AWARDTRACE_S3_BUCKET='\(.*\)'$/\1/p" "${root}/.env")
region=$(sed -n "s/^AWS_REGION='\(.*\)'$/\1/p" "${root}/.env")

compose() {
  AWARDTRACE_ROOT=$root "${here}/compose.sh" "$@" < /dev/null
}

# Right after a boot, Docker may still be starting PostgreSQL.
for attempt in $(seq 60); do
  if compose exec -T postgres pg_isready -q -U awardtrace -d awardtrace; then
    break
  fi
  if ((attempt == 60)); then
    echo "PostgreSQL isn't accepting connections" >&2
    exit 1
  fi
  sleep 5
done

# A dump that fails partway must never look like a backup, so it is written locally and uploaded only once pg_dump
# has succeeded.
dump=$(mktemp -p /var/tmp awardtrace-XXXXXX.dump)
trap 'rm -f "$dump"' EXIT
compose exec -T postgres pg_dump -U awardtrace -d awardtrace --format=custom > "$dump"
key=backups/awardtrace-$(date -u +%Y-%m-%dT%H%M%SZ).dump
aws s3 cp "$dump" "s3://${bucket}/${key}" --region "$region" --only-show-errors
echo "Uploaded s3://${bucket}/${key}, $(stat -c %s "$dump") bytes"
