# The landing page's wake button (ADR 0020): a Lambda function behind a public function URL that reports the host's
# state and starts it, at most wakes_per_day times a UTC day, counted in a DynamoDB table. The function's source is in
# function/, and its tests run in CI.

terraform {
  required_providers {
    aws = {
      source = "hashicorp/aws"
    }
    archive = {
      source = "hashicorp/archive"
    }
  }
}

data "aws_caller_identity" "current" {}

data "aws_region" "current" {}

locals {
  function_name = "${var.name}-wake"
  instance_arn  = "arn:aws:ec2:${data.aws_region.current.region}:${data.aws_caller_identity.current.account_id}:instance/${var.instance_id}"
}

# One item per UTC day, holding that day's count. The function sets expires_at, and DynamoDB deletes the item after it.
resource "aws_dynamodb_table" "wakes" {
  name         = "${var.name}-wakes"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "day"

  attribute {
    name = "day"
    type = "S"
  }

  ttl {
    attribute_name = "expires_at"
    enabled        = true
  }
}

data "archive_file" "function" {
  type        = "zip"
  source_dir  = "${path.module}/function"
  excludes    = ["wake.test.mjs"]
  output_path = "${path.root}/.terraform/${local.function_name}.zip"
}

data "aws_iam_policy_document" "trust" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["lambda.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "function" {
  name               = local.function_name
  assume_role_policy = data.aws_iam_policy_document.trust.json
}

# Read the host's state, start that one instance, count wakes, and write its own logs; nothing else. No KMS permission
# is needed to start the host with its encrypted disk, as in the schedule module.
data "aws_iam_policy_document" "function" {
  # EC2 doesn't scope Describe actions to a resource.
  statement {
    actions   = ["ec2:DescribeInstances"]
    resources = ["*"]
  }

  statement {
    actions   = ["ec2:StartInstances"]
    resources = [local.instance_arn]
  }

  statement {
    actions   = ["dynamodb:UpdateItem"]
    resources = [aws_dynamodb_table.wakes.arn]
  }

  statement {
    actions   = ["logs:CreateLogStream", "logs:PutLogEvents"]
    resources = ["${aws_cloudwatch_log_group.function.arn}:log-stream:*"]
  }
}

resource "aws_iam_role_policy" "function" {
  name   = local.function_name
  role   = aws_iam_role.function.id
  policy = data.aws_iam_policy_document.function.json
}

# Created here rather than by Lambda on first use, so it has a retention period.
resource "aws_cloudwatch_log_group" "function" {
  name              = "/aws/lambda/${local.function_name}"
  retention_in_days = 14
}

resource "aws_lambda_function" "wake" {
  function_name    = local.function_name
  role             = aws_iam_role.function.arn
  runtime          = "nodejs24.x"
  architectures    = ["arm64"]
  handler          = "index.handler"
  filename         = data.archive_file.function.output_path
  source_code_hash = data.archive_file.function.output_base64sha256
  memory_size      = 128
  # The health check waits up to 3 seconds, and the AWS calls take well under one.
  timeout = 10
  # ponytail: no reserved concurrency, because AWS refuses any reservation while the account's concurrency limit is 10
  # (ADR 0020). Request a higher limit, then set reserved_concurrent_executions = 1 to bound how fast anyone can call it.

  environment {
    variables = {
      INSTANCE_ID   = var.instance_id
      TABLE_NAME    = aws_dynamodb_table.wakes.name
      APP_URL       = var.app_url
      WAKES_PER_DAY = tostring(var.wakes_per_day)
    }
  }

  depends_on = [aws_iam_role_policy.function, aws_cloudwatch_log_group.function]
}

resource "aws_lambda_function_url" "wake" {
  function_name      = aws_lambda_function.wake.function_name
  authorization_type = "NONE"

  # Browsers send a page's requests only from these origins; a script can still call it, and the budget bounds that.
  cors {
    allow_origins = var.allowed_origins
    allow_methods = ["GET", "POST"]
    max_age       = 3600
  }
}

# A public URL needs both statements: one for the URL, and one that lets the URL, and only the URL, invoke the function.
resource "aws_lambda_permission" "url" {
  statement_id           = "FunctionUrlAllowPublicAccess"
  action                 = "lambda:InvokeFunctionUrl"
  function_name          = aws_lambda_function.wake.function_name
  principal              = "*"
  function_url_auth_type = "NONE"
}

resource "aws_lambda_permission" "invoke" {
  statement_id             = "FunctionUrlInvokeAllowPublicAccess"
  action                   = "lambda:InvokeFunction"
  function_name            = aws_lambda_function.wake.function_name
  principal                = "*"
  invoked_via_function_url = true
}
