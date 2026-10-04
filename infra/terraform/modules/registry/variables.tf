variable "repositories" {
  description = "The repository names, such as awardtrace/backend."
  type        = list(string)
}

variable "images_kept" {
  description = "How many images each repository keeps; older ones expire."
  type        = number
  default     = 10
}
