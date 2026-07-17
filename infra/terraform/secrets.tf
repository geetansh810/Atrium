# 10 §5: "Secrets Manager → injected as env vars into ECS task definitions."
# One JSON secret, one entry per env var — ecs.tf's task definition maps
# each JSON key to a container secret individually (ECS supports
# valueFrom: "<secret-arn>:<json-key>::" for exactly this).

resource "random_password" "jwt_secret" {
  count   = var.jwt_secret == "" ? 1 : 0
  length  = 48
  special = false
}

locals {
  jwt_secret = var.jwt_secret != "" ? var.jwt_secret : random_password.jwt_secret[0].result
}

resource "aws_secretsmanager_secret" "core_api" {
  name        = "atrium/${var.environment}/core-api"
  description = "core-api env vars (10 §5) — DB creds, JWT secret, LLM provider keys, alert webhook. No Stripe/billing keys — M3.3 is deferred post-pilot (07 Rev D)."
}

resource "aws_secretsmanager_secret_version" "core_api" {
  secret_id = aws_secretsmanager_secret.core_api.id
  secret_string = jsonencode({
    DATABASE_URL             = "jdbc:postgresql://${aws_db_instance.main.address}:5432/${var.db_name}"
    DATABASE_USER            = var.db_master_username
    DATABASE_PASSWORD        = local.db_master_password
    ATRIUM_APP_DB_USER       = "atrium_app"
    ATRIUM_APP_DB_PASSWORD   = var.atrium_app_db_password # MUST stay "atrium_app" — see variables.tf
    REDIS_URL                = "redis://${aws_elasticache_cluster.main.cache_nodes[0].address}:6379"
    ATRIUM_JWT_SECRET        = local.jwt_secret
    ANTHROPIC_API_KEY        = var.anthropic_api_key
    GOOGLE_API_KEY           = var.google_api_key
    OPENAI_API_KEY           = var.openai_api_key
    ATRIUM_ALERT_WEBHOOK_URL = var.alert_webhook_url
  })
}
