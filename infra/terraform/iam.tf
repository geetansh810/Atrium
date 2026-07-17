# Two roles, standard ECS Fargate split: the EXECUTION role is what the ECS
# agent itself uses to pull the image and resolve secrets before the
# container starts; the TASK role is what core-api's own AWS SDK calls (none
# exist yet — no S3/SES/etc. calls in the app — but every Fargate service
# needs a task role ARN, so this is a real, minimal, currently-unused-by-code
# role rather than reusing the execution role for both, which would over-grant
# the running application the ability to read every secret and pull images).

data "aws_caller_identity" "current" {}

resource "aws_iam_role" "ecs_execution" {
  name = "atrium-${var.environment}-ecs-execution"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ecs-tasks.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy_attachment" "ecs_execution_managed" {
  role       = aws_iam_role.ecs_execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

resource "aws_iam_role_policy" "ecs_execution_secrets" {
  name = "read-core-api-secret"
  role = aws_iam_role.ecs_execution.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["secretsmanager:GetSecretValue"]
      Resource = [aws_secretsmanager_secret.core_api.arn]
    }]
  })
}

resource "aws_iam_role" "ecs_task" {
  name = "atrium-${var.environment}-ecs-task"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ecs-tasks.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}
