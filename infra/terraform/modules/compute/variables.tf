variable "name" {
  description = "The prefix for every resource name, also the value of the host's app tag."
  type        = string
}

variable "instance_type" {
  description = "The host's Graviton instance type."
  type        = string
}

variable "subnet_id" {
  description = "The public subnet the host runs in."
  type        = string
}

variable "security_group_id" {
  description = "The security group that admits 80 and 443 only."
  type        = string
}

variable "instance_profile" {
  description = "The instance profile carrying the host's role."
  type        = string
}

variable "volume_gib" {
  description = "The size of the host's encrypted gp3 volume."
  type        = number
  default     = 40
}

variable "alert_email" {
  description = "Where the status-check alarm sends its email."
  type        = string
}
