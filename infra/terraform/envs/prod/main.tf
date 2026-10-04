# Production (doc 10): everything except the state bucket (bootstrap) and the raw bucket (envs/data). The rebuild
# drill destroys and re-applies this root alone.
#
#   cd infra/terraform/envs/prod
#   terraform init -backend-config="bucket=$(terraform -chdir=../../bootstrap output -raw state_bucket)"
#   terraform apply

locals {
  # The Terraform name prefix doc 01 lists among the places a rename touches.
  name = "awardtrace"

  # The host runs on the first size and moves to the second for the one-time backfill (doc 10).
  host_sizes = ["t4g.medium", "t4g.large"]
}

# Not every availability zone offers every Graviton instance type. The subnet goes in the first zone that offers both
# sizes, so resizing the host never moves it, which would replace the whole stack.
data "aws_ec2_instance_type_offerings" "host" {
  for_each = toset(local.host_sizes)

  location_type = "availability-zone"

  filter {
    name   = "instance-type"
    values = [each.value]
  }
}

module "network" {
  source = "../../modules/network"

  name = local.name
  availability_zone = sort(setintersection(
    [for offerings in data.aws_ec2_instance_type_offerings.host : offerings.locations]...
  ))[0]
}

module "registry" {
  source = "../../modules/registry"

  repositories = ["${local.name}/backend", "${local.name}/web"]
}

module "observability" {
  source = "../../modules/observability"

  log_group_name = "/${local.name}/prod"
}

data "aws_caller_identity" "current" {}

# Created by envs/data, which this root never manages, so the rebuild drill can't destroy it.
data "aws_s3_bucket" "raw" {
  bucket = "${local.name}-raw-${data.aws_caller_identity.current.account_id}"
}

module "iam" {
  source = "../../modules/iam"

  name            = local.name
  raw_bucket_arn  = data.aws_s3_bucket.raw.arn
  repository_arns = values(module.registry.repository_arns)
  log_group_arn   = module.observability.log_group_arn
  parameter_path  = "/${local.name}"
}
