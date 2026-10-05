# Production (doc 10): everything except the state bucket (bootstrap) and the raw bucket (envs/data). The rebuild
# drill destroys and re-applies this root alone.
#
#   cd infra/terraform/envs/prod
#   terraform init -backend-config="bucket=$(terraform -chdir=../../bootstrap output -raw state_bucket)"
#   terraform apply

locals {
  # The Terraform name prefix doc 01 lists among the places a rename touches.
  name = "awardtrace"

  # The host runs on the first size and moves to the second for the one-time backfill (doc 10). The free plan launches
  # only free-tier types, whose arm64 ones are too small for the stack, so both are x86 (ADR 0015).
  host_sizes = ["c7i-flex.large", "m7i-flex.large"]
}

# Not every availability zone offers every instance type. The subnet goes in the first zone that offers both
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

  name              = local.name
  raw_bucket_arn    = data.aws_s3_bucket.raw.arn
  repository_arns   = values(module.registry.repository_arns)
  log_group_arn     = module.observability.log_group_arn
  parameter_path    = "/${local.name}"
  github_repository = var.github_repository
}

module "compute" {
  source = "../../modules/compute"

  name              = local.name
  instance_type     = var.instance_type
  subnet_id         = module.network.subnet_id
  security_group_id = module.network.security_group_id
  instance_profile  = module.iam.host_instance_profile
  alert_email       = var.alert_email
}

# Weekdays, 8:00 to 20:00 Pacific (ADR 0017). The start is 10 minutes early because the stack takes 3 to 5 minutes to
# boot.
module "schedule" {
  source = "../../modules/schedule"

  name        = local.name
  instance_id = module.compute.instance_id
  timezone    = "America/Los_Angeles"
  start_cron  = "cron(50 7 ? * MON-FRI *)"
  stop_cron   = "cron(0 20 ? * MON-FRI *)"
}

module "budget" {
  source = "../../modules/budget"

  name        = local.name
  alert_email = var.alert_email
}
