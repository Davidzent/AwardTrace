#!/bin/bash
# Starts the production host if it's stopped, waits until SSM can reach it, and prints its instance ID (ADR 0017). The
# deploy and Host workflows run it with the deploy role's credentials. A running host is left as it is.
set -euo pipefail

found=$(aws ec2 describe-instances \
  --filters Name=tag:app,Values=awardtrace Name=instance-state-name,Values=pending,running,stopping,stopped \
  --query 'Reservations[].Instances[].[InstanceId,State.Name]' --output text)
if [[ ! $found =~ ^i-[0-9a-f]+[[:space:]]+[a-z]+$ ]]; then
  echo "::error::Expected one host tagged app=awardtrace, found: ${found:-none}" >&2
  exit 1
fi
read -r instance state <<< "$found"

# The stop schedule or the idle stop may be stopping it; it can start again only once it has stopped.
if [ "$state" = stopping ]; then
  aws ec2 wait instance-stopped --instance-ids "$instance"
  state=stopped
fi
if [ "$state" = stopped ]; then
  echo "Starting ${instance}" >&2
  aws ec2 start-instances --instance-ids "$instance" > /dev/null
fi
aws ec2 wait instance-running --instance-ids "$instance"

# SSM rejects commands until the host's agent registers after boot, which takes a minute or two.
for _ in $(seq 60); do
  status=$(aws ssm describe-instance-information --filters "Key=InstanceIds,Values=${instance}" \
    --query 'InstanceInformationList[0].PingStatus' --output text)
  if [ "$status" = Online ]; then
    echo "$instance"
    exit 0
  fi
  sleep 5
done
echo "::error::${instance} is running, but SSM can't reach it" >&2
exit 1
