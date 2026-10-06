output "function_url" {
  description = "The function URL the landing page calls, ending in a slash."
  value       = aws_lambda_function_url.wake.function_url
}
