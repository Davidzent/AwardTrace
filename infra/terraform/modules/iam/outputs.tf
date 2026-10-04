output "host_instance_profile" {
  description = "The instance profile the host launches with."
  value       = aws_iam_instance_profile.host.name
}

output "host_role_arn" {
  description = "The host's role."
  value       = aws_iam_role.host.arn
}
