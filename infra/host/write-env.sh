#!/bin/bash
# Writes the root-only env file that Compose reads in production (doc 10). Secrets come from SSM Parameter Store
# through the instance role, so none is in Terraform state, the repository, or an image. Run it before every start:
#   sudo infra/host/write-env.sh [/opt/awardtrace/.env]
#
# Create each secret once, before the first start; PostgreSQL keeps the password it first starts with:
#   aws ssm put-parameter --type SecureString --name /awardtrace/postgres/password --value "$(openssl rand -hex 24)"
#   aws ssm put-parameter --type SecureString --name /awardtrace/elasticsearch/password --value "$(openssl rand -hex 24)"
#
# The Anthropic API key is optional: without it the enricher can't call Claude, and the site keeps its PSC-based
# categories (doc 09). This prompts for it, so it stays out of your shell history:
#   read -rsp "Anthropic API key: " key && MSYS_NO_PATHCONV=1 aws ssm put-parameter --type SecureString \
#     --name /awardtrace/anthropic/api-key --value "$key"; unset key
set -euo pipefail

env_file=${1:-/opt/awardtrace/.env}

token=$(curl -fsS -X PUT http://169.254.169.254/latest/api/token -H 'X-aws-ec2-metadata-token-ttl-seconds: 60')
region=$(curl -fsS -H "X-aws-ec2-metadata-token: ${token}" http://169.254.169.254/latest/meta-data/placement/region)
account=$(aws sts get-caller-identity --region "$region" --query Account --output text)

parameter() {
  aws ssm get-parameter --region "$region" --name "/awardtrace/$1" --with-decryption \
    --query Parameter.Value --output text
}

# GetParameters reports a missing name instead of failing, so a key that was never created reads as empty, while any
# other error still stops the script.
optional_parameter() {
  aws ssm get-parameters --region "$region" --names "/awardtrace/$1" --with-decryption \
    --query 'Parameters[].Value' --output text
}

postgres_password=$(parameter postgres/password)
elasticsearch_password=$(parameter elasticsearch/password)
anthropic_api_key=$(optional_parameter anthropic/api-key)

# Compose reads a single-quoted value literally, so a value can't itself hold a quote or a line break.
line() {
  case $2 in
    *"'"* | *$'\n'*)
      echo "$1 holds a quote or a line break, which the env file can't represent" >&2
      exit 1
      ;;
  esac
  printf "%s='%s'\n" "$1" "$2"
}

mkdir -p "$(dirname "$env_file")"
umask 077
tmp=$(mktemp "${env_file}.XXXXXX")
trap 'rm -f "$tmp"' EXIT
{
  line AWS_REGION "$region"
  line AWARDTRACE_REGISTRY "${account}.dkr.ecr.${region}.amazonaws.com"
  line AWARDTRACE_S3_BUCKET "awardtrace-raw-${account}"
  line POSTGRES_PASSWORD "$postgres_password"
  line ELASTICSEARCH_PASSWORD "$elasticsearch_password"
  line ANTHROPIC_API_KEY "$anthropic_api_key"
} > "$tmp"
mv "$tmp" "$env_file"
trap - EXIT
