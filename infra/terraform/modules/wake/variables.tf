variable "name" {
  description = "The prefix for every resource name."
  type        = string
}

variable "instance_id" {
  description = "The host the function reports on and starts."
  type        = string
}

variable "app_url" {
  description = "The app's origin, such as https://app.awardtrace.zntsns.com, whose /healthz says it's ready."
  type        = string
}

variable "allowed_origins" {
  description = "The origins whose pages may call the function URL from a browser."
  type        = list(string)
}

variable "wakes_per_day" {
  description = "How many times a UTC day the function may start the host."
  type        = number
}
