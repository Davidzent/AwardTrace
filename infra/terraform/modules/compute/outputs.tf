output "instance_id" {
  description = "The host's instance ID, for SSM sessions and commands."
  value       = aws_instance.host.id
}

output "public_ip" {
  description = "The host's Elastic IP."
  value       = aws_eip.host.public_ip
}
