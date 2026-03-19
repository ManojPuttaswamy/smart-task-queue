# variables.tf
#
# Variables make Terraform configs reusable across environments.
# Instead of hardcoding "smartqueue_pass" everywhere, define it once here.
# Different environments (dev/staging/prod) pass different values.
#
# Usage:
#   terraform apply -var="postgres_password=supersecret"
#   or via terraform.tfvars file (never commit that to Git!)
#   or via environment variables: TF_VAR_postgres_password=supersecret

variable "postgres_db" {
  description = "PostgreSQL database name"
  type        = string
  default     = "smartqueue"
}

variable "postgres_user" {
  description = "PostgreSQL username"
  type        = string
  default     = "smartqueue_user"
}

variable "postgres_password" {
  description = "PostgreSQL password — override in production"
  type        = string
  default     = "smartqueue_pass"
  sensitive   = true  # Terraform won't print this in logs or plan output
}

variable "jwt_secret" {
  description = "JWT signing secret — must be at least 32 characters"
  type        = string
  default     = "smartqueue-super-secret-key-change-in-production-min-32-chars"
  sensitive   = true
}

variable "app_port" {
  description = "Port the Spring Boot app listens on"
  type        = number
  default     = 8080
}

variable "openai_api_key" {
  description = "OpenAI API key for the AI classifier service"
  type        = string
  default     = ""
  sensitive   = true
}