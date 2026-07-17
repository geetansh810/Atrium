# 10 §2/§3: web ships as a plain Vite static build (`npm run build`) in S3
# behind CloudFront — no container, no server process. The deploy workflow
# (.github/workflows/deploy.yml) syncs web/dist/ here and invalidates the
# distribution; this file only provisions the bucket + CDN, never uploads.

resource "aws_s3_bucket" "web" {
  bucket = "atrium-${var.environment}-web-${data.aws_caller_identity.current.account_id}"
}

resource "aws_s3_bucket_public_access_block" "web" {
  bucket                  = aws_s3_bucket.web.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# CloudFront reaches the bucket via Origin Access Control, never a public
# bucket policy — the bucket itself stays fully private (above).
resource "aws_cloudfront_origin_access_control" "web" {
  name                              = "atrium-${var.environment}-web"
  origin_access_control_origin_type = "s3"
  signing_behavior                  = "always"
  signing_protocol                  = "sigv4"
}

resource "aws_s3_bucket_policy" "web" {
  bucket = aws_s3_bucket.web.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Sid       = "AllowCloudFrontOAC"
      Effect    = "Allow"
      Principal = { Service = "cloudfront.amazonaws.com" }
      Action    = "s3:GetObject"
      Resource  = "${aws_s3_bucket.web.arn}/*"
      Condition = {
        StringEquals = {
          "AWS:SourceArn" = aws_cloudfront_distribution.web[0].arn
        }
      }
    }]
  })
}

resource "aws_cloudfront_distribution" "web" {
  # count, not a plain resource, only because the bucket policy above needs
  # this distribution's ARN before it exists otherwise — Terraform can't
  # forward-reference across two resources that each depend on the other
  # without one of them being index-addressable; [0] is the whole resource,
  # unconditional in practice (this app always has a web build to serve).
  count   = 1
  enabled = true
  comment = "atrium-${var.environment}-web"

  origin {
    domain_name              = aws_s3_bucket.web.bucket_regional_domain_name
    origin_id                = "s3-web"
    origin_access_control_id = aws_cloudfront_origin_access_control.web.id
  }

  default_root_object = "index.html"

  # A client-side-routed SPA (react-router@7, library mode — 08 §Code style):
  # any path CloudFront can't find in S3 is a real route, not a 404 — serve
  # index.html and let the router take over, exactly like a dev server does.
  custom_error_response {
    error_code         = 404
    response_code      = 200
    response_page_path = "/index.html"
  }

  default_cache_behavior {
    allowed_methods        = ["GET", "HEAD", "OPTIONS"]
    cached_methods         = ["GET", "HEAD"]
    target_origin_id       = "s3-web"
    viewer_protocol_policy = "redirect-to-https"
    compress               = true
    cache_policy_id        = "658327ea-f89d-4fab-a63d-7e88639e58f6" # AWS managed "CachingOptimized"
  }

  price_class = "PriceClass_100" # US/Canada/Europe — cost posture, widen once there's demand elsewhere

  aliases = var.domain_name != "" ? [var.domain_name] : []

  viewer_certificate {
    cloudfront_default_certificate = var.domain_name == ""
    acm_certificate_arn            = var.domain_name != "" ? aws_acm_certificate_validation.cloudfront[0].certificate_arn : null
    ssl_support_method             = var.domain_name != "" ? "sni-only" : null
    minimum_protocol_version       = var.domain_name != "" ? "TLSv1.2_2021" : null
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }
}
