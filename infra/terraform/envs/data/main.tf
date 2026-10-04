# The raw bucket, in a state of its own (doc 10). The rebuild drill destroys envs/prod and nothing here, so the
# source of truth survives it; prevent_destroy in envs/prod would make that destroy fail instead. envs/prod finds the
# bucket by name.
#
#   cd infra/terraform/envs/data
#   terraform init -backend-config="bucket=$(terraform -chdir=../../bootstrap output -raw state_bucket)"
#   terraform apply

data "aws_caller_identity" "current" {}

module "storage" {
  source = "../../modules/storage"

  bucket_name = "awardtrace-raw-${data.aws_caller_identity.current.account_id}"
}
