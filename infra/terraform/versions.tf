# M3.5 (10 §3): this whole infra/terraform/ directory is written and
# internally validated (terraform fmt + validate, via the official Docker
# image — no AWS account/credentials exist in the dev environment this was
# authored in) but deliberately NEVER applied. See README.md for what
# running it for real requires.

terraform {
  required_version = ">= 1.6"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
  }

  # A backend can't bootstrap its own storage — create the S3 bucket + DynamoDB
  # lock table BY HAND once (README.md "First-time setup"), then either fill
  # these in or pass them via `terraform init -backend-config=...`.
  backend "s3" {
    # bucket         = "atrium-terraform-state"
    # key            = "atrium.tfstate"        # workspaces (staging/prod) split state, not the key
    # region         = "us-east-1"
    # dynamodb_table = "atrium-terraform-locks"
    # encrypt        = true
  }
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project     = "atrium"
      Environment = var.environment
      ManagedBy   = "terraform"
    }
  }
}

# CloudFront's ACM certificate must live in us-east-1 regardless of
# var.aws_region — a hard CloudFront requirement, hence this alias (used only
# by dns.tf's aws_acm_certificate.cloudfront).
provider "aws" {
  alias  = "us_east_1"
  region = "us-east-1"

  default_tags {
    tags = {
      Project     = "atrium"
      Environment = var.environment
      ManagedBy   = "terraform"
    }
  }
}
