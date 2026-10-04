# The host's instance role (doc 10): read and write raw/ and backups/, read the /awardtrace/* parameters, pull the
# two images, write to its log group, and register with SSM. Nothing else, including pushing images or deleting from
# the raw bucket. The deploy role is in deploy.tf.

terraform {
  required_providers {
    aws = {
      source = "hashicorp/aws"
    }
  }
}

data "aws_caller_identity" "current" {}

data "aws_region" "current" {}

data "aws_iam_policy_document" "host_trust" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["ec2.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "host" {
  name               = "${var.name}-host"
  assume_role_policy = data.aws_iam_policy_document.host_trust.json
}

resource "aws_iam_instance_profile" "host" {
  name = "${var.name}-host"
  role = aws_iam_role.host.name
}

locals {
  parameter_arn_prefix = "arn:aws:ssm:${data.aws_region.current.region}:${data.aws_caller_identity.current.account_id}:parameter${var.parameter_path}"
}

data "aws_iam_policy_document" "host" {
  statement {
    sid       = "ListRawAndBackups"
    actions   = ["s3:ListBucket"]
    resources = [var.raw_bucket_arn]

    condition {
      test     = "StringLike"
      variable = "s3:prefix"
      values   = ["raw/", "raw/*", "backups/", "backups/*"]
    }
  }

  # No s3:DeleteObject: the source of truth is never deleted, and backups expire by lifecycle rule. An overwrite
  # leaves the previous version behind, since the bucket is versioned.
  statement {
    sid     = "ReadAndWriteRawAndBackups"
    actions = ["s3:GetObject", "s3:PutObject", "s3:AbortMultipartUpload"]
    resources = [
      "${var.raw_bucket_arn}/raw/*",
      "${var.raw_bucket_arn}/backups/*",
    ]
  }

  # SecureStrings use the AWS managed key aws/ssm, whose key policy lets the account decrypt through SSM, so no KMS
  # permission is needed.
  statement {
    sid       = "ReadParameters"
    actions   = ["ssm:GetParameter", "ssm:GetParameters", "ssm:GetParametersByPath"]
    resources = [local.parameter_arn_prefix, "${local.parameter_arn_prefix}/*"]
  }

  statement {
    sid       = "AuthenticateToRegistry"
    actions   = ["ecr:GetAuthorizationToken"]
    resources = ["*"]
  }

  statement {
    sid       = "PullImages"
    actions   = ["ecr:BatchCheckLayerAvailability", "ecr:BatchGetImage", "ecr:GetDownloadUrlForLayer"]
    resources = var.repository_arns
  }

  statement {
    sid       = "WriteLogs"
    actions   = ["logs:CreateLogStream", "logs:PutLogEvents"]
    resources = ["${var.log_group_arn}:log-stream:*"]
  }

  # What the SSM agent needs for Session Manager and Run Command, and no more. The managed policy
  # AmazonSSMManagedInstanceCore would also grant ssm:GetParameter on every parameter in the account.
  statement {
    sid = "RegisterWithSsm"
    actions = [
      "ssm:UpdateInstanceInformation",
      "ssmmessages:CreateControlChannel",
      "ssmmessages:CreateDataChannel",
      "ssmmessages:OpenControlChannel",
      "ssmmessages:OpenDataChannel",
      "ec2messages:AcknowledgeMessage",
      "ec2messages:DeleteMessage",
      "ec2messages:FailMessage",
      "ec2messages:GetEndpoint",
      "ec2messages:GetMessages",
      "ec2messages:SendReply",
    ]
    resources = ["*"]
  }
}

resource "aws_iam_role_policy" "host" {
  name   = "${var.name}-host"
  role   = aws_iam_role.host.id
  policy = data.aws_iam_policy_document.host.json
}
