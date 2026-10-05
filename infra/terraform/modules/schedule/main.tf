# When the host runs (ADR 0017): EventBridge Scheduler starts it before weekday hours and stops it after. Scheduler
# calls the EC2 API itself through universal targets, so no function or host-side code is involved.

terraform {
  required_providers {
    aws = {
      source = "hashicorp/aws"
    }
  }
}

data "aws_caller_identity" "current" {}

data "aws_region" "current" {}

locals {
  instance_arn = "arn:aws:ec2:${data.aws_region.current.region}:${data.aws_caller_identity.current.account_id}:instance/${var.instance_id}"

  actions = {
    start = { cron = var.start_cron, api = "startInstances" }
    stop  = { cron = var.stop_cron, api = "stopInstances" }
  }
}

# A group of their own, so the role's trust policy can name it: only these schedules can assume the role.
resource "aws_scheduler_schedule_group" "host" {
  name = var.name
}

data "aws_iam_policy_document" "trust" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["scheduler.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "aws:SourceAccount"
      values   = [data.aws_caller_identity.current.account_id]
    }

    condition {
      test     = "StringEquals"
      variable = "aws:SourceArn"
      values   = [aws_scheduler_schedule_group.host.arn]
    }
  }
}

resource "aws_iam_role" "scheduler" {
  name               = "${var.name}-scheduler"
  assume_role_policy = data.aws_iam_policy_document.trust.json
}

# No KMS permission is needed to start the host with its encrypted disk: the AWS managed EBS key's own policy lets any
# principal in the account use it through EC2.
data "aws_iam_policy_document" "start_stop" {
  statement {
    actions   = ["ec2:StartInstances", "ec2:StopInstances"]
    resources = [local.instance_arn]
  }
}

resource "aws_iam_role_policy" "scheduler" {
  name   = "${var.name}-scheduler"
  role   = aws_iam_role.scheduler.id
  policy = data.aws_iam_policy_document.start_stop.json
}

resource "aws_scheduler_schedule" "host" {
  for_each = local.actions

  name                         = "${var.name}-${each.key}"
  group_name                   = aws_scheduler_schedule_group.host.name
  schedule_expression          = each.value.cron
  schedule_expression_timezone = var.timezone

  flexible_time_window {
    mode = "OFF"
  }

  target {
    arn      = "arn:aws:scheduler:::aws-sdk:ec2:${each.value.api}"
    role_arn = aws_iam_role.scheduler.arn
    input    = jsonencode({ InstanceIds = [var.instance_id] })

    # A start or stop that can't happen within 15 minutes is dropped, rather than retried for a day and run at an hour
    # nobody chose.
    retry_policy {
      maximum_event_age_in_seconds = 900
      maximum_retry_attempts       = 3
    }
  }
}
