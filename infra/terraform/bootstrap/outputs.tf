output "state_bucket" {
  description = "The bucket every other root names in its S3 backend."
  value       = aws_s3_bucket.state.bucket
}
