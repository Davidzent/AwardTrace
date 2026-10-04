variable "region" {
  description = "The AWS region for the whole stack."
  type        = string
  default     = "us-east-1"
}

variable "instance_type" {
  description = "The host's size: t4g.medium normally, t4g.large for the one-time backfill (doc 10)."
  type        = string
  default     = "t4g.medium"

  # The subnet sits in a zone chosen to offer exactly these sizes (main.tf).
  validation {
    condition     = contains(["t4g.medium", "t4g.large"], var.instance_type)
    error_message = "The host runs on t4g.medium or t4g.large; another size may not exist in the subnet's zone."
  }
}

variable "alert_email" {
  description = "Where alerts go: the status-check alarm and the budget. Set it in terraform.tfvars, which git ignores."
  type        = string
}
