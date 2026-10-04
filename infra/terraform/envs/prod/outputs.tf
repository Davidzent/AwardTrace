output "repository_urls" {
  description = "The image repositories, by name, for tagging and pushing images."
  value       = module.registry.repository_urls
}
