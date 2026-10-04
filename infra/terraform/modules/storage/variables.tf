variable "bucket_name" {
  description = "The raw bucket's name, globally unique."
  type        = string
}

variable "backup_retention_days" {
  description = "How long a database dump under backups/ is kept."
  type        = number
  default     = 14
}
