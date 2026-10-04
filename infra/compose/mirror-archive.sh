#!/usr/bin/env sh
# Copies the USAspending archive files kept in data/usaspending/ (gitignored) into the local S3Mock, which lists and
# serves them the way files.usaspending.gov/award_data_archive does. An ingest then reads them without downloading:
#
#   ./mvnw spring-boot:run -Dspring-boot.run.profiles=local,ingest \
#       -Dspring-boot.run.arguments="--awardtrace.ingest.task=backfill --awardtrace.ingest.archive-url=http://localhost:9090/usaspending-archive/"
#
# Run from the repository root while the local compose stack is up.
set -eu

bucket=http://localhost:9090/usaspending-archive
for file in data/usaspending/*.zip; do
  [ -e "$file" ] || { echo "No archive files in data/usaspending/" >&2; exit 1; }
  curl -sSf -X PUT --upload-file "$file" "$bucket/$(basename "$file")"
  echo "Mirrored $(basename "$file")"
done
