output "repository_arns" {
  description = "Each repository's ARN, by name."
  value       = { for name, repository in aws_ecr_repository.this : name => repository.arn }
}

output "repository_urls" {
  description = "Each repository's URL, by name, which images are tagged and pulled with."
  value       = { for name, repository in aws_ecr_repository.this : name => repository.repository_url }
}
