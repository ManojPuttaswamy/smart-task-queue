# outputs.tf
#
# Outputs are values Terraform prints after "terraform apply".
# They're also how one Terraform module passes values to another.
#
# Example use cases:
#   - Print the database connection string after provisioning RDS
#   - Pass the VPC ID from a networking module to an app module
#   - Expose the load balancer URL after creating an EKS cluster
#
# Run "terraform output" anytime to see these values.
# Run "terraform output postgres_host" to get a specific value.

output "network_name" {
  description = "Name of the Docker network all services are connected to"
  value       = docker_network.smartqueue_network.name
}

output "postgres_host" {
  description = "PostgreSQL hostname (use this in Spring Boot datasource URL)"
  value       = docker_container.postgres.name
}

output "postgres_port" {
  description = "PostgreSQL port"
  value       = 5432
}

output "kafka_bootstrap_servers" {
  description = "Kafka bootstrap servers for Spring Boot configuration"
  value       = "${docker_container.kafka.name}:29092"
}

output "redis_host" {
  description = "Redis hostname for Spring Boot configuration"
  value       = docker_container.redis.name
}

output "app_url" {
  description = "Spring Boot app URL"
  value       = "http://localhost:${var.app_port}"
}

output "ai_classifier_url" {
  description = "AI Classifier service URL"
  value       = "http://${docker_container.ai_classifier.name}:8000"
}

output "connection_summary" {
  description = "Summary of all service connection details"
  value = {
    postgres     = "jdbc:postgresql://${docker_container.postgres.name}:5432/${var.postgres_db}"
    kafka        = "${docker_container.kafka.name}:29092"
    redis        = "${docker_container.redis.name}:6379"
    ai_classifier = "http://${docker_container.ai_classifier.name}:8000"
  }
}