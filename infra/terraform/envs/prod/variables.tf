variable "region" {
  description = "The AWS region for the whole stack."
  type        = string
  default     = "us-east-1"
}

variable "instance_type" {
  description = "The host's size: c7i-flex.large normally, m7i-flex.large for the one-time backfill (doc 10)."
  type        = string
  default     = "c7i-flex.large"

  # The subnet sits in a zone chosen to offer exactly these sizes (main.tf).
  validation {
    condition     = contains(["c7i-flex.large", "m7i-flex.large"], var.instance_type)
    error_message = "The host runs on c7i-flex.large or m7i-flex.large; another size may not exist in the subnet's zone."
  }
}

variable "github_repository" {
  # GitHub's OIDC subject claim names the owner and repository with their immutable IDs, so a repository deleted and
  # recreated under the same name can't deploy.
  description = "The GitHub repository whose main branch deploys, as owner@owner_id/name@repo_id."
  type        = string
  default     = "Davidzent@37416103/AwardTrace@1393908058"
}

variable "alert_email" {
  description = "Where alerts go: the status-check alarm and the budget. Set it in terraform.tfvars, which git ignores."
  type        = string
}
