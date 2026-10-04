variable "name" {
  description = "The prefix for every resource name."
  type        = string
}

variable "raw_bucket_arn" {
  description = "The raw bucket, whose raw/ and backups/ prefixes the host reads and writes."
  type        = string
}

variable "repository_arns" {
  description = "The image repositories the host pulls from."
  type        = list(string)
}

variable "log_group_arn" {
  description = "The log group the host's containers write to."
  type        = string
}

variable "github_repository" {
  description = "The GitHub repository, as owner/name, whose workflows may assume the deploy role."
  type        = string
}

variable "deploy_branch" {
  description = "The only branch whose workflows may assume the deploy role."
  type        = string
  default     = "main"
}

variable "parameter_path" {
  description = "The SSM Parameter Store path holding the host's secrets, with a leading slash."
  type        = string

  validation {
    condition     = startswith(var.parameter_path, "/") && !endswith(var.parameter_path, "/")
    error_message = "The path starts with a slash and doesn't end with one, such as /awardtrace."
  }
}
