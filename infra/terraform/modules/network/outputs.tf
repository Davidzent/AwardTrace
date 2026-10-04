output "vpc_id" {
  description = "The VPC's ID."
  value       = aws_vpc.main.id
}

output "subnet_id" {
  description = "The public subnet the host runs in."
  value       = aws_subnet.public.id
}

output "security_group_id" {
  description = "The security group that admits 80 and 443 only."
  value       = aws_security_group.web.id
}
