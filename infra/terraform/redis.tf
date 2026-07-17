resource "aws_elasticache_subnet_group" "main" {
  name       = "atrium-${var.environment}"
  subnet_ids = aws_subnet.private[*].id
}

resource "aws_elasticache_cluster" "main" {
  cluster_id         = "atrium-${var.environment}"
  engine             = "redis"
  engine_version     = "7.1"
  node_type          = var.redis_node_type
  num_cache_nodes    = 1 # 10 §3: "single node → replica when there are real users"
  port               = 6379
  subnet_group_name  = aws_elasticache_subnet_group.main.name
  security_group_ids = [aws_security_group.redis.id]
  apply_immediately  = var.environment != "prod"

  tags = { Name = "atrium-${var.environment}" }
}
