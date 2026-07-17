# 10 §2: the one container image (web ships static, no image — see web.tf).
# One repo, shared across staging/prod (tags distinguish them, 10 §2's tag
# scheme) — the deploy workflow pushes here, ECS pulls from here.

resource "aws_ecr_repository" "core_api" {
  count                = var.environment == "prod" ? 1 : 0 # created once, from the prod apply, shared by staging
  name                 = "atrium-core-api"
  image_tag_mutability = "IMMUTABLE" # 10 §2: "Never deploy :latest blind" — enforced, not just a convention

  image_scanning_configuration {
    scan_on_push = true
  }
}

data "aws_ecr_repository" "core_api" {
  count = var.environment == "staging" ? 1 : 0
  name  = "atrium-core-api"
}

locals {
  ecr_repository_url = var.environment == "prod" ? aws_ecr_repository.core_api[0].repository_url : data.aws_ecr_repository.core_api[0].repository_url
}
