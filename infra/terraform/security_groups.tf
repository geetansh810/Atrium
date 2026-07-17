# Each hop is scoped to exactly the previous hop's security group (never a
# wider CIDR) — the same "narrowest access that works" posture 08's tenant
# isolation rules apply at the app layer, mirrored here at the network layer.

resource "aws_security_group" "alb" {
  name        = "atrium-${var.environment}-alb"
  description = "Public ALB — 80/443 from the internet"
  vpc_id      = aws_vpc.main.id

  ingress {
    description = "HTTP (redirects to HTTPS once an ACM cert exists, dns.tf)"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }
  ingress {
    description = "HTTPS"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
  tags = { Name = "atrium-${var.environment}-alb" }
}

resource "aws_security_group" "core_api" {
  name        = "atrium-${var.environment}-core-api"
  description = "core-api ECS tasks — 8080 from the ALB only"
  vpc_id      = aws_vpc.main.id

  ingress {
    description     = "from the ALB"
    from_port       = 8080
    to_port         = 8080
    protocol        = "tcp"
    security_groups = [aws_security_group.alb.id]
  }
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"] # RDS/Redis (below) + outbound LLM provider calls
  }
  tags = { Name = "atrium-${var.environment}-core-api" }
}

resource "aws_security_group" "rds" {
  name        = "atrium-${var.environment}-rds"
  description = "Postgres — 5432 from core-api tasks only"
  vpc_id      = aws_vpc.main.id

  ingress {
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.core_api.id]
  }
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
  tags = { Name = "atrium-${var.environment}-rds" }
}

resource "aws_security_group" "redis" {
  name        = "atrium-${var.environment}-redis"
  description = "ElastiCache Redis — 6379 from core-api tasks only"
  vpc_id      = aws_vpc.main.id

  ingress {
    from_port       = 6379
    to_port         = 6379
    protocol        = "tcp"
    security_groups = [aws_security_group.core_api.id]
  }
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
  tags = { Name = "atrium-${var.environment}-redis" }
}
