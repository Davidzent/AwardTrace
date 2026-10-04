# The log group every container writes to through Docker's awslogs driver (docs 10 and 11). Metrics stay in the app
# and only logs ship, since each CloudWatch custom metric costs about $0.30 a month.

terraform {
  required_providers {
    aws = {
      source = "hashicorp/aws"
    }
  }
}

resource "aws_cloudwatch_log_group" "this" {
  name              = var.log_group_name
  retention_in_days = var.retention_days
}
