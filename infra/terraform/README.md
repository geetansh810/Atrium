# Atrium AWS infrastructure (M3.5)

Implements `atrium-docs/10-deployment.md` §3's architecture: ALB → ECS
Fargate (core-api) → RDS Postgres + ElastiCache Redis, S3+CloudFront for the
web static build, Secrets Manager for config, CloudWatch alarms → SNS.

**Status as of session 32 (M3.5): written and validated (`terraform fmt` +
`validate`, both clean), never applied.** There is no AWS account or
credentials in the environment this was authored in — provisioning real
cloud infrastructure needs the owner's own AWS account, a card on file, and
explicit intent, not something to do unilaterally from a coding session. This
README is the literal next steps for whoever does run it.

## First-time setup (once per AWS account, before the first `apply`)

1. **State backend** — Terraform's own state needs somewhere to live before
   Terraform can manage anything else, so this one part is created by hand:
   ```
   aws s3api create-bucket --bucket atrium-terraform-state --region us-east-1
   aws s3api put-bucket-versioning --bucket atrium-terraform-state \
     --versioning-configuration Status=Enabled
   aws dynamodb create-table --table-name atrium-terraform-locks \
     --attribute-definitions AttributeName=LockID,AttributeType=S \
     --key-schema AttributeName=LockID,KeyType=HASH \
     --billing-mode PAY_PER_REQUEST
   ```
2. **ECR repo** — created BY this config (`ecr.tf`), but only from the `prod`
   apply (staging reads the same repo via a data source) — so `prod` must be
   applied at least once before `staging` can be.
3. **Domain** (optional, 10 §8) — buy it however you like; `var.domain_name`
   is blank-safe (everything DNS/ACM-related is `count`-gated on it), so you
   can run everything else first and add the domain later.

## Running it

```
cd infra/terraform
terraform init -backend-config="key=prod.tfstate"      # or "key=staging.tfstate"
cp terraform.tfvars.example terraform.tfvars            # fill in container_image at minimum
terraform plan
terraform apply
```

Repeat with a different `-backend-config`/`terraform.tfvars` (`environment =
"staging"`) for the second environment — two independent states, same
config, exactly 10 §1's "staging and prod differ only in size and secrets."

`container_image` has to already exist in ECR before the first `apply` (the
ECS task definition references it directly) — either push one by hand once
(`docker build`/`docker push` per the root `Dockerfile`), or run this after
`.github/workflows/deploy.yml`'s first successful image build.

## What this does NOT do

- Never runs `terraform apply` itself, or any AWS API call requiring
  credentials — see the Status note above.
- No Stripe/billing configuration anywhere (M3.3 is deferred post-pilot, 07
  Rev D) — `secrets.tf`'s JSON has zero payment-related keys.
- Doesn't upload the web build to S3 or push the core-api image to ECR —
  that's `.github/workflows/deploy.yml`'s job, not Terraform's; this only
  provisions the bucket/repo/CDN/cluster for that workflow to push into.
- Doesn't touch `office-realtime` — it never shipped (the office track
  retired 2026-07-15), so there's no Colyseus/WebSocket-sticky-session
  infrastructure here at all, unlike 10-deployment.md's original pre-M3.5
  architecture sketch.

## Known gap worth fixing before a real prod apply

`variables.tf`'s `atrium_app_db_password` documents this in full, but the
short version: `V10__tenant_rls.sql` hard-codes the `atrium_app` Postgres
role's password as the literal string `'atrium_app'` (migrations are never
edited once applied), so this Terraform can't generate a real random
password for it the way it does for `db_master_password`/`jwt_secret` — it's
stuck matching that hard-coded value until a future migration parameterizes
it (e.g. `ALTER ROLE atrium_app PASSWORD :new_password` in a new `V11`, with
the app's own `ATRIUM_APP_DB_PASSWORD` rotated to match in the same change).
Low real-world risk today (the role is only reachable from inside the VPC,
behind two security-group hops), but worth closing before this handles a
paying customer's data.
