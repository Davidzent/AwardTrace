variable "log_group_name" {
  description = "The log group's name, such as /awardtrace/prod."
  type        = string
}

variable "retention_days" {
  description = "How long log events are kept."
  type        = number
  default     = 14
}
