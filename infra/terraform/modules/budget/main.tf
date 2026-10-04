# The monthly budget (doc 10), account-wide so it also catches costs no tag reaches, such as data transfer. Each
# threshold emails once a month, when actual spend passes it.

terraform {
  required_providers {
    aws = {
      source = "hashicorp/aws"
    }
  }
}

resource "aws_budgets_budget" "monthly" {
  name         = "${var.name}-monthly"
  budget_type  = "COST"
  limit_amount = tostring(var.limit_usd)
  limit_unit   = "USD"
  time_unit    = "MONTHLY"

  # Spend before credits. Counted against it, credits such as the free plan's cancel the whole bill, and the budget
  # would never alert; measured before them, it shows how fast the credits are being used up.
  cost_types {
    include_credit = false
  }

  dynamic "notification" {
    for_each = toset(var.alert_thresholds_usd)

    content {
      comparison_operator        = "GREATER_THAN"
      threshold                  = notification.value
      threshold_type             = "ABSOLUTE_VALUE"
      notification_type          = "ACTUAL"
      subscriber_email_addresses = [var.alert_email]
    }
  }
}
