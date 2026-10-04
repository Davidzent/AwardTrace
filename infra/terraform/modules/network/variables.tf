variable "name" {
  description = "The prefix for every resource name."
  type        = string
}

variable "availability_zone" {
  description = "The availability zone for the public subnet, one that offers the host's instance type."
  type        = string
}

variable "vpc_cidr" {
  description = "The VPC's address range; the subnet takes a /24 of it."
  type        = string
  default     = "10.0.0.0/16"
}
