terraform {
  # 1.10 adds native S3 state locking (use_lockfile), which every other root relies on.
  required_version = ">= 1.10"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.67"
    }
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
