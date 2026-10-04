terraform {
  required_version = ">= 1.10"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.67"
    }
  }

  # The bucket comes from the bootstrap root at init time, since a backend block can't read variables:
  #   terraform init -backend-config="bucket=$(terraform -chdir=../../bootstrap output -raw state_bucket)"
  backend "s3" {
    key          = "data/terraform.tfstate"
    region       = "us-east-1"
    encrypt      = true
    use_lockfile = true
  }
}

provider "aws" {
  region = var.region

  default_tags {
    tags = {
      Project   = "awardtrace"
      ManagedBy = "terraform"
    }
  }
}
