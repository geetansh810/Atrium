variable "aws_region" {
  type        = string
  default     = "us-east-1"
  description = "AWS region for every resource in this config."
}

variable "environment" {
  type        = string
  description = "\"staging\" or \"prod\" (10 §1) — identical shape, different size/secrets. Use a separate terraform workspace or tfvars file per environment; never both in one state."
  validation {
    condition     = contains(["staging", "prod"], var.environment)
    error_message = "environment must be \"staging\" or \"prod\"."
  }
}

variable "domain_name" {
  type        = string
  default     = ""
  description = "Root domain for this environment, e.g. app.example.com (10 §8). Blank (the default — no domain has been purchased yet) skips Route53/ACM/the CloudFront custom domain entirely; the ALB and CloudFront default hostnames still work without it."
}

variable "container_image" {
  type        = string
  description = "Full core-api image URI+tag already pushed to ECR, e.g. <account>.dkr.ecr.<region>.amazonaws.com/atrium-core-api:m3.5-abc1234 (10 §2 tag scheme). Supplied by the deploy workflow (.github/workflows/deploy.yml), not hand-typed."
}

variable "core_api_desired_count" {
  type        = number
  default     = 2
  description = "10 §3 — 2 tasks minimum."
}

variable "core_api_cpu" {
  type        = number
  default     = 512 # 0.5 vCPU
  description = "Fargate task CPU units. 10 §3 cost posture: smallest practical size at launch."
}

variable "core_api_memory" {
  type    = number
  default = 1024 # 1 GB
}

variable "db_instance_class" {
  type    = string
  default = "db.t4g.micro" # 10 §3 cost posture
}

variable "db_name" {
  type    = string
  default = "atrium"
}

variable "db_master_username" {
  type    = string
  default = "atrium"
}

variable "db_master_password" {
  type        = string
  sensitive   = true
  default     = ""
  description = "The Flyway bootstrap role's password (needs real DDL rights — 08 §Security rule 6). Blank = a random one is generated and stored only in Secrets Manager."
}

variable "atrium_app_db_password" {
  type        = string
  sensitive   = true
  default     = "atrium_app"
  description = <<-EOT
    core-api's own runtime datasource password for the least-privilege
    atrium_app role (08 §Security rule 6). NOT randomizable the way
    db_master_password is: V10__tenant_rls.sql hard-codes CREATE ROLE
    atrium_app LOGIN PASSWORD 'atrium_app' (migrations are never edited once
    applied), so this variable's value MUST stay the literal string
    "atrium_app" until a future migration parameterizes it — a real,
    already-flagged gap (see this file's own comment in V10, and CLAUDE.md).
    Changing this default without also shipping a new migration that ALTERs
    the role's password will make core-api fail to authenticate to Postgres.
  EOT
}

variable "redis_node_type" {
  type    = string
  default = "cache.t4g.micro"
}

variable "jwt_secret" {
  type        = string
  sensitive   = true
  default     = ""
  description = "ATRIUM_JWT_SECRET (08 §Config — MUST override the insecure dev fallback in any shared env). Blank = a random 48-char secret is generated and stored only in Secrets Manager."
}

variable "anthropic_api_key" {
  type      = string
  sensitive = true
  default   = ""
}

variable "google_api_key" {
  type      = string
  sensitive = true
  default   = ""
}

variable "openai_api_key" {
  type      = string
  sensitive = true
  default   = ""
}

variable "alert_email" {
  type        = string
  default     = ""
  description = "Subscribed to the SNS alerts topic (10 §3/§6 — CloudWatch alarms). Blank = the topic exists with no subscriber yet, alarms fire into the void until someone subscribes."
}

variable "alert_webhook_url" {
  type        = string
  sensitive   = true
  default     = ""
  description = "ATRIUM_ALERT_WEBHOOK_URL — core-api's own AlertingAppender (common.AlertingAppender, 10 §6). A separate, complementary path from the SNS topic above, not a duplicate of it."
}

variable "rate_limit_default_per_minute" {
  type        = number
  default     = 300
  description = "ATRIUM_RATE_LIMIT_DEFAULT (08 §Security rule 7). Company-scoped, ordinary authenticated traffic."
}
