# The one host that runs the whole stack (doc 10), its stable address, and the alarm that emails when it is unhealthy.

terraform {
  required_providers {
    aws = {
      source = "hashicorp/aws"
    }
  }
}

data "aws_ami" "al2023" {
  most_recent = true
  owners      = ["amazon"]

  filter {
    name   = "name"
    values = ["al2023-ami-2023.*-kernel-*-arm64"]
  }

  filter {
    name   = "architecture"
    values = ["arm64"]
  }
}

resource "aws_instance" "host" {
  ami                    = data.aws_ami.al2023.id
  instance_type          = var.instance_type
  subnet_id              = var.subnet_id
  vpc_security_group_ids = [var.security_group_id]
  iam_instance_profile   = var.instance_profile
  user_data              = file("${path.module}/user-data.sh")

  # The host throttles at its CPU baseline instead of billing surplus credits, so its monthly cost stays fixed
  # (goal G5). Switch to unlimited alongside the backfill resize if throttling slows the backfill.
  credit_specification {
    cpu_credits = "standard"
  }

  metadata_options {
    http_tokens = "required"
    # Containers reach the instance role through IMDS one network hop further away than the host itself.
    http_put_response_hop_limit = 2
  }

  # Docker's volumes for Kafka, PostgreSQL, and Elasticsearch live here.
  root_block_device {
    volume_type = "gp3"
    volume_size = var.volume_gib
    encrypted   = true
  }

  tags = {
    Name = "${var.name}-prod"
    # The deploy role may run commands only on instances with this tag.
    app = var.name
  }

  lifecycle {
    # A newer AMI would replace the host and its data. Take one deliberately, through the rebuild drill.
    ignore_changes = [ami]
  }
}

resource "aws_eip" "host" {
  domain   = "vpc"
  instance = aws_instance.host.id

  tags = {
    Name = "${var.name}-prod"
  }
}

resource "aws_sns_topic" "alerts" {
  name = "${var.name}-alerts"
}

# AWS emails a confirmation link first; alerts arrive only after it is followed.
resource "aws_sns_topic_subscription" "email" {
  topic_arn = aws_sns_topic.alerts.arn
  protocol  = "email"
  endpoint  = var.alert_email
}

resource "aws_cloudwatch_metric_alarm" "status" {
  alarm_name          = "${var.name}-host-status"
  alarm_description   = "The host failed an EC2 status check two minutes running."
  namespace           = "AWS/EC2"
  metric_name         = "StatusCheckFailed"
  dimensions          = { InstanceId = aws_instance.host.id }
  statistic           = "Maximum"
  period              = 60
  evaluation_periods  = 2
  threshold           = 1
  comparison_operator = "GreaterThanOrEqualToThreshold"
  # A stopped host reports nothing; that is a choice to save money, not an outage.
  treat_missing_data = "missing"
  alarm_actions      = [aws_sns_topic.alerts.arn]
  ok_actions         = [aws_sns_topic.alerts.arn]
}
