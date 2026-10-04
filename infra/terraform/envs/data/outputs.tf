output "raw_bucket" {
  description = "The raw bucket's name, which the app reads as awardtrace.s3.bucket."
  value       = module.storage.bucket
}
