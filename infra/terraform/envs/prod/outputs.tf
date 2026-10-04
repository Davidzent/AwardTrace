output "repository_urls" {
  description = "The image repositories, by name, for tagging and pushing images."
  value       = module.registry.repository_urls
}

output "host_public_ip" {
  description = "The Elastic IP that the awardtrace.zntsns.com A record points at."
  value       = module.compute.public_ip
}

output "host_instance_id" {
  description = "The host's instance ID, for aws ssm start-session."
  value       = module.compute.instance_id
}
