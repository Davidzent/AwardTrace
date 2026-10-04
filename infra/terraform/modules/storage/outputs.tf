output "bucket" {
  description = "The raw bucket's name."
  value       = aws_s3_bucket.raw.bucket
}

output "arn" {
  description = "The raw bucket's ARN."
  value       = aws_s3_bucket.raw.arn
}
