# 10 §3/§6: CloudWatch alarms -> SNS -> email. The awslogs driver (ecs.tf)
# already makes core-api's JSON console output (logback-spring.xml, prod
# profile) the CloudWatch log data directly — no separate shipping agent —
# so the metric filter below just pattern-matches the JSON it's already
# receiving. This is the AWS-side complement to common.AlertingAppender's
# own webhook path (10 §6); the two are independent and can point at the
# same eventual destination (e.g. SNS -> a Slack integration) without being
# redundant with each other.

resource "aws_sns_topic" "alerts" {
  name = "atrium-${var.environment}-alerts"
}

resource "aws_sns_topic_subscription" "alerts_email" {
  count     = var.alert_email != "" ? 1 : 0
  topic_arn = aws_sns_topic.alerts.arn
  protocol  = "email"
  endpoint  = var.alert_email
}

# core-api's own ERROR-level log lines, counted straight out of the JSON
# CloudWatch already has (logstash-logback-encoder renders {"level":"ERROR",...}).
resource "aws_cloudwatch_log_metric_filter" "core_api_errors" {
  name           = "atrium-${var.environment}-core-api-errors"
  log_group_name = aws_cloudwatch_log_group.core_api.name
  pattern        = "{ $.level = \"ERROR\" }"

  metric_transformation {
    name      = "CoreApiErrorCount"
    namespace = "Atrium/${var.environment}"
    value     = "1"
    unit      = "Count"
  }
}

resource "aws_cloudwatch_metric_alarm" "core_api_errors" {
  alarm_name          = "atrium-${var.environment}-core-api-errors"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 1
  metric_name         = aws_cloudwatch_log_metric_filter.core_api_errors.metric_transformation[0].name
  namespace           = aws_cloudwatch_log_metric_filter.core_api_errors.metric_transformation[0].namespace
  period              = 60 # 10 §M3.5 Done-when: "a triggered error alerts within 1 min"
  statistic           = "Sum"
  threshold           = 0
  treat_missing_data  = "notBreaching"
  alarm_description   = "core-api logged at least one ERROR line in the last minute."
  alarm_actions       = [aws_sns_topic.alerts.arn]
  ok_actions          = [aws_sns_topic.alerts.arn]
}

resource "aws_cloudwatch_metric_alarm" "alb_5xx" {
  alarm_name          = "atrium-${var.environment}-alb-5xx-rate"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  metric_name         = "HTTPCode_Target_5XX_Count"
  namespace           = "AWS/ApplicationELB"
  period              = 60
  statistic           = "Sum"
  threshold           = 5
  treat_missing_data  = "notBreaching"
  alarm_description   = "core-api's ALB target group is returning 5xx responses."
  alarm_actions       = [aws_sns_topic.alerts.arn]
  ok_actions          = [aws_sns_topic.alerts.arn]

  dimensions = {
    LoadBalancer = aws_lb.main.arn_suffix
    TargetGroup  = aws_lb_target_group.core_api.arn_suffix
  }
}

resource "aws_cloudwatch_metric_alarm" "rds_storage" {
  alarm_name          = "atrium-${var.environment}-rds-low-storage"
  comparison_operator = "LessThanThreshold"
  evaluation_periods  = 1
  metric_name         = "FreeStorageSpace"
  namespace           = "AWS/RDS"
  period              = 300
  statistic           = "Average"
  threshold           = 2147483648 # 2 GiB
  alarm_description   = "Postgres is running low on free storage (RDS storage autoscaling should absorb this, but worth knowing before it does)."
  alarm_actions       = [aws_sns_topic.alerts.arn]

  dimensions = {
    DBInstanceIdentifier = aws_db_instance.main.id
  }
}
