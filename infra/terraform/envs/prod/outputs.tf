output "repository_urls" {
  description = "The image repositories, by name, for tagging and pushing images."
  value       = module.registry.repository_urls
}

output "host_public_ip" {
  description = "The Elastic IP that the app.awardtrace.zntsns.com A record points at."
  value       = module.compute.public_ip
}

output "deploy_role_arn" {
  description = "Set as the repository variable AWS_DEPLOY_ROLE_ARN, which the deploy workflow assumes."
  value       = module.iam.deploy_role_arn
}

output "host_instance_id" {
  description = "The host's instance ID, for aws ssm start-session."
  value       = module.compute.instance_id
}

output "wake_function_url" {
  description = "The wake function's URL, which the landing page calls (ADR 0020)."
  value       = module.wake.function_url
}
