output "log_group_name" {
  description = "The log group's name, which the awslogs driver writes to."
  value       = aws_cloudwatch_log_group.this.name
}

output "log_group_arn" {
  description = "The log group's ARN."
  value       = aws_cloudwatch_log_group.this.arn
}
