variable "name" {
  description = "The prefix for the budget's name."
  type        = string
}

variable "limit_usd" {
  description = "The monthly budget, in dollars: the spend doc 01 plans for."
  type        = number
  default     = 35
}

variable "alert_thresholds_usd" {
  description = "Monthly spend, in dollars, at which an alert is emailed."
  type        = list(number)
  default     = [10, 25, 40]
}

variable "alert_email" {
  description = "Where the budget alerts go."
  type        = string
}
