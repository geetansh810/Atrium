output "core_api_ecr_repository_url" {
  value = local.ecr_repository_url
}

output "alb_dns_name" {
  value       = aws_lb.main.dns_name
  description = "core-api's default (no-domain-needed) URL: http://<this>"
}

output "cloudfront_domain_name" {
  value       = aws_cloudfront_distribution.web[0].domain_name
  description = "web's default (no-domain-needed) URL: https://<this>"
}

output "route53_name_servers" {
  value       = var.domain_name != "" ? aws_route53_zone.main[0].name_servers : []
  description = "Point your domain registrar at these NS records (10 §8) — only populated once var.domain_name is set."
}

output "rds_endpoint" {
  value     = aws_db_instance.main.address
  sensitive = false
}

output "core_api_secret_arn" {
  value       = aws_secretsmanager_secret.core_api.arn
  description = "Where DATABASE_PASSWORD/ATRIUM_JWT_SECRET/LLM keys actually live — never in state as plaintext beyond what Terraform itself requires."
}

output "sns_alerts_topic_arn" {
  value = aws_sns_topic.alerts.arn
}
