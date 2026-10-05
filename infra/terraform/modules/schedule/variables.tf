variable "name" {
  description = "The prefix for every resource name, also the schedule group's name."
  type        = string
}

variable "instance_id" {
  description = "The host the schedules start and stop."
  type        = string
}

variable "timezone" {
  description = "The IANA time zone of the cron expressions, such as America/Los_Angeles."
  type        = string
}

variable "start_cron" {
  description = "When to start the host, as an EventBridge Scheduler cron expression in the time zone."
  type        = string
}

variable "stop_cron" {
  description = "When to stop the host, as an EventBridge Scheduler cron expression in the time zone."
  type        = string
}
