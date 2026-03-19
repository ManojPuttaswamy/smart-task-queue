# Terraform — Smart Task Queue Infrastructure

## What is Terraform?

Terraform is Infrastructure as Code (IaC) — you describe your infrastructure
in HCL files and Terraform figures out how to create/update/delete it.

**Key benefits over manual setup:**
- Reproducible: same config = same infrastructure every time
- Version controlled: infrastructure changes are tracked in Git
- Self-documenting: the HCL files ARE the documentation
- Plan before apply: see exactly what will change before it happens

## Core Concepts

### State File (terraform.tfstate)
Terraform tracks what it has created in a state file.
- Never delete this file — Terraform loses track of your infrastructure
- Never commit this to Git — it contains sensitive values
- In production: store in S3 + DynamoDB for team sharing and locking

### Plan vs Apply
```
terraform plan   → shows what WOULD change (dry run, safe to run anytime)
terraform apply  → actually makes the changes
terraform destroy → tears everything down
```

### Provider
The plugin that knows how to talk to an API (Docker, AWS, GCP, Azure, etc.)
We use kreuzwerker/docker — talks to your local Docker daemon.

## Prerequisites

```bash
# Install Terraform
brew install terraform

# Verify
terraform version
```

## Usage

```bash
cd terraform/

# Step 1: Download providers (like npm install)
terraform init

# Step 2: See what will be created (no changes made)
terraform plan

# Step 3: Create the infrastructure
terraform apply

# Step 4: See outputs
terraform output
terraform output connection_summary

# Tear everything down
terraform destroy
```

## Override Variables

```bash
# Pass variables on command line
terraform apply -var="postgres_password=mysecretpass"

# Or create terraform.tfvars (never commit this file!)
cat > terraform.tfvars << EOF
postgres_password = "mysecretpass"
jwt_secret        = "my-production-jwt-secret-min-32-chars"
openai_api_key    = "sk-..."
EOF
terraform apply
```

## Terraform vs Docker Compose

| Feature | Docker Compose | Terraform |
|---|---|---|
| Purpose | Local dev orchestration | Infrastructure as Code |
| State tracking | None (stateless) | terraform.tfstate |
| Plan before apply | No | Yes (terraform plan) |
| Multi-cloud | No | Yes (100+ providers) |
| Drift detection | No | Yes (terraform plan shows drift) |
| Used in production | Rarely | Yes |

## How Terraform State Works

```
Your HCL files  →  terraform plan  →  Diff against state  →  terraform apply
                                            ↑
                                    terraform.tfstate
                                    (what exists now)
```

If someone manually changes infrastructure (e.g., deletes a container),
terraform plan will detect the drift and show it needs to be recreated.
This is called **drift detection** — one of Terraform's most valuable features.

## Production Architecture (AWS)

In a real AWS deployment, you'd replace the Docker provider with AWS:

```hcl
provider "aws" {
  region = "us-east-1"
}

resource "aws_db_instance" "postgres" {
  engine         = "postgres"
  engine_version = "15"
  instance_class = "db.t3.micro"
  ...
}

resource "aws_elasticache_cluster" "redis" {
  engine       = "redis"
  node_type    = "cache.t3.micro"
  ...
}

resource "aws_msk_cluster" "kafka" {
  cluster_name = "smartqueue-kafka"
  ...
}
```

Same HCL concepts — just a different provider and resource types.