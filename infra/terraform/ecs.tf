resource "aws_ecs_cluster" "main" {
  name = "atrium-${var.environment}"

  setting {
    name  = "containerInsights"
    value = "enabled"
  }
}

resource "aws_cloudwatch_log_group" "core_api" {
  name              = "/ecs/atrium-${var.environment}/core-api"
  retention_in_days = var.environment == "prod" ? 30 : 7
}

resource "aws_lb" "main" {
  name               = "atrium-${var.environment}"
  internal           = false
  load_balancer_type = "application"
  security_groups    = [aws_security_group.alb.id]
  subnets            = aws_subnet.public[*].id
}

resource "aws_lb_target_group" "core_api" {
  name        = "atrium-${var.environment}-core-api"
  port        = 8080
  protocol    = "HTTP"
  vpc_id      = aws_vpc.main.id
  target_type = "ip" # Fargate has no EC2 instance to register

  health_check {
    path                = "/actuator/health"
    healthy_threshold   = 2
    unhealthy_threshold = 3
    interval            = 15
    timeout             = 5
    matcher             = "200"
  }
}

# HTTP listener: forwards straight to core-api when no cert exists yet
# (var.domain_name blank), or redirects to HTTPS once dns.tf provisions one —
# a real deploy is never left plaintext-only once a domain is in play.
resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.main.arn
  port              = 80
  protocol          = "HTTP"

  dynamic "default_action" {
    for_each = var.domain_name == "" ? [1] : []
    content {
      type             = "forward"
      target_group_arn = aws_lb_target_group.core_api.arn
    }
  }

  dynamic "default_action" {
    for_each = var.domain_name != "" ? [1] : []
    content {
      type = "redirect"
      redirect {
        port        = "443"
        protocol    = "HTTPS"
        status_code = "HTTP_301"
      }
    }
  }
}

resource "aws_lb_listener" "https" {
  count             = var.domain_name != "" ? 1 : 0
  load_balancer_arn = aws_lb.main.arn
  port              = 443
  protocol          = "HTTPS"
  ssl_policy        = "ELBSecurityPolicy-TLS13-1-2-2021-06"
  certificate_arn   = aws_acm_certificate_validation.main[0].certificate_arn

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.core_api.arn
  }
}

resource "aws_ecs_task_definition" "core_api" {
  family                   = "atrium-${var.environment}-core-api"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.core_api_cpu
  memory                   = var.core_api_memory
  execution_role_arn       = aws_iam_role.ecs_execution.arn
  task_role_arn            = aws_iam_role.ecs_task.arn

  container_definitions = jsonencode([{
    name      = "core-api"
    image     = var.container_image
    essential = true
    portMappings = [{
      containerPort = 8080
      protocol      = "tcp"
    }]
    environment = [
      # SPRING_PROFILES_ACTIVE=prod is the ONE flag that switches
      # logback-spring.xml to JSON console output (10 §6) — never set
      # locally, exactly the switch this variable exists for.
      { name = "SPRING_PROFILES_ACTIVE", value = "prod" },
      { name = "ATRIUM_RATE_LIMIT_DEFAULT", value = tostring(var.rate_limit_default_per_minute) },
    ]
    secrets = [
      for key in [
        "DATABASE_URL", "DATABASE_USER", "DATABASE_PASSWORD",
        "ATRIUM_APP_DB_USER", "ATRIUM_APP_DB_PASSWORD", "REDIS_URL",
        "ATRIUM_JWT_SECRET", "ANTHROPIC_API_KEY", "GOOGLE_API_KEY",
        "OPENAI_API_KEY", "ATRIUM_ALERT_WEBHOOK_URL",
        ] : {
        name      = key
        valueFrom = "${aws_secretsmanager_secret.core_api.arn}:${key}::"
      }
    ]
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = aws_cloudwatch_log_group.core_api.name
        "awslogs-region"        = var.aws_region
        "awslogs-stream-prefix" = "core-api"
      }
    }
  }])
}

resource "aws_ecs_service" "core_api" {
  name            = "core-api"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.core_api.arn
  desired_count   = var.core_api_desired_count
  launch_type     = "FARGATE"

  network_configuration {
    subnets         = aws_subnet.private[*].id # no public IP — reached only via the ALB
    security_groups = [aws_security_group.core_api.id]
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.core_api.arn
    container_name   = "core-api"
    container_port   = 8080
  }

  # Flyway runs on core-api's own startup (10 §4 point 4) — deploys are only
  # zero-downtime if the NEW task's migration finishes and passes its health
  # check before the OLD task is drained, which is exactly what a rolling
  # deployment with min-healthy-100%/max-200% gives for free.
  deployment_minimum_healthy_percent = 100
  deployment_maximum_percent         = 200

  depends_on = [aws_lb_listener.http]
}

resource "aws_appautoscaling_target" "core_api" {
  max_capacity       = var.core_api_desired_count * 3
  min_capacity       = var.core_api_desired_count
  resource_id        = "service/${aws_ecs_cluster.main.name}/${aws_ecs_service.core_api.name}"
  scalable_dimension = "ecs:service:DesiredCount"
  service_namespace  = "ecs"
}

resource "aws_appautoscaling_policy" "core_api_cpu" {
  name               = "atrium-${var.environment}-core-api-cpu"
  policy_type        = "TargetTrackingScaling"
  resource_id        = aws_appautoscaling_target.core_api.resource_id
  scalable_dimension = aws_appautoscaling_target.core_api.scalable_dimension
  service_namespace  = aws_appautoscaling_target.core_api.service_namespace

  target_tracking_scaling_policy_configuration {
    predefined_metric_specification {
      predefined_metric_type = "ECSServiceAverageCPUUtilization"
    }
    target_value       = 60
    scale_in_cooldown  = 120
    scale_out_cooldown = 60
  }
}
