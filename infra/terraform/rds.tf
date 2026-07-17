resource "aws_db_subnet_group" "main" {
  name       = "atrium-${var.environment}"
  subnet_ids = aws_subnet.private[*].id
  tags       = { Name = "atrium-${var.environment}" }
}

resource "random_password" "db_master" {
  count   = var.db_master_password == "" ? 1 : 0
  length  = 32
  special = false
}

locals {
  db_master_password = var.db_master_password != "" ? var.db_master_password : random_password.db_master[0].result
}

resource "aws_db_instance" "main" {
  identifier     = "atrium-${var.environment}"
  engine         = "postgres"
  engine_version = "16"
  instance_class = var.db_instance_class

  allocated_storage     = 20
  max_allocated_storage = 100 # storage autoscaling — cost posture, avoids a manual resize later
  storage_type          = "gp3"
  storage_encrypted     = true

  db_name  = var.db_name
  username = var.db_master_username
  password = local.db_master_password
  port     = 5432

  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.rds.id]

  # 10 §3: "Multi-AZ off at start" — a real availability tradeoff, not an
  # oversight: a single-AZ outage takes core-api's database down until AWS
  # recovers that AZ or someone manually restores the latest automated
  # snapshot. Flip to true once real usage justifies the doubled RDS cost.
  multi_az = false

  backup_retention_period = 7 # 10 §3: "automated backups 7d"
  backup_window           = "03:00-04:00"
  maintenance_window      = "mon:04:30-mon:05:30"
  # PITR (10 §3) is exactly what backup_retention_period > 0 enables in
  # RDS — there is no separate on/off flag; continuous backup to S3 runs
  # automatically once retention is non-zero.

  # RDS's own master user is never a true Postgres superuser (unlike the
  # local docker-compose/Testcontainers bootstrap role) — so
  # V10__tenant_rls.sql's CREATE ROLE atrium_app runs correctly on this
  # master user with zero extra role plumbing, exactly as 08 §Security rule
  # 6 documents. See variables.tf's atrium_app_db_password for the one real
  # gap this migration currently has (a hard-coded role password).

  skip_final_snapshot       = var.environment != "prod" # staging: fast teardown; prod: always snapshot on delete
  final_snapshot_identifier = var.environment == "prod" ? "atrium-prod-final-snapshot" : null
  deletion_protection       = var.environment == "prod"

  tags = { Name = "atrium-${var.environment}" }
}
