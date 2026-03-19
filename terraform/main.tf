# main.tf
#
# Terraform configuration for Smart Task Queue infrastructure.
# Uses the Docker provider — provisions the same stack as docker-compose.yml
# but via Infrastructure as Code (IaC).
#
# Key Terraform concepts demonstrated here:
#   - provider: tells Terraform which API to talk to (Docker, AWS, GCP, etc.)
#   - resource: a piece of infrastructure to create/manage
#   - data source: read existing infrastructure (don't create, just reference)
#   - depends_on: explicit dependency between resources
#   - locals: computed values used within the config

# ── Terraform + Provider Configuration ───────────────────────────────────────
#
# terraform block: declares which providers are needed and their versions.
# Locking versions is critical — without it, "terraform init" could pull
# a breaking provider update and silently change your infrastructure.

terraform {
  required_version = ">= 1.0"

  required_providers {
    docker = {
      source  = "kreuzwerker/docker"
      version = "~> 3.0"   # ~> means: 3.x but not 4.x (minor updates OK)
    }
  }

  # In production: use remote state (S3 + DynamoDB for locking)
  # backend "s3" {
  #   bucket         = "my-terraform-state"
  #   key            = "smartqueue/terraform.tfstate"
  #   region         = "us-east-1"
  #   dynamodb_table = "terraform-locks"
  # }
  #
  # For local dev: state is stored in terraform.tfstate (never commit this!)
}

# ── Provider ──────────────────────────────────────────────────────────────────
#
# The provider is the plugin that knows how to talk to a specific API.
# kreuzwerker/docker speaks to the Docker daemon on your machine.
# On Linux: unix:///var/run/docker.sock
# On Mac with Docker Desktop: unix:///var/run/docker.sock (symlinked)

provider "docker" {
  host = "unix:///var/run/docker.sock"
}

# ── Locals ────────────────────────────────────────────────────────────────────
#
# Locals are computed values — like variables but derived from other values.
# Use them to avoid repeating the same expression multiple times.

locals {
  app_name = "smartqueue"

  # Common labels applied to all resources
  common_labels = {
    project     = "smart-task-queue"
    managed_by  = "terraform"
    environment = "local"
  }
}

# ── Docker Network ────────────────────────────────────────────────────────────
#
# All containers must be on the same network to communicate by service name.
# This is equivalent to the default network Docker Compose creates automatically.
# With Terraform we create it explicitly so we control the name.

resource "docker_network" "smartqueue_network" {
  name = "${local.app_name}_terraform_network"

  # Bridge driver = standard Docker networking for single-host setups
  # Overlay driver = multi-host networking (Docker Swarm / production)
  driver = "bridge"

  labels {
    label = "project"
    value = local.common_labels.project
  }
}

# ── Pull Docker Images ────────────────────────────────────────────────────────
#
# docker_image resources ensure images are pulled before containers start.
# Without this, Terraform would try to start a container before the image exists.

resource "docker_image" "postgres" {
  name = "postgres:15"
}

resource "docker_image" "zookeeper" {
  name = "confluentinc/cp-zookeeper:7.5.0"
}

resource "docker_image" "kafka" {
  name = "confluentinc/cp-kafka:7.5.0"
}

resource "docker_image" "redis" {
  name = "redis:7.2"
}

# ── Volumes ───────────────────────────────────────────────────────────────────
#
# Named volumes persist data across container restarts.
# If you do "terraform destroy", volumes are deleted too (unlike docker-compose down).
# Use "terraform destroy -target=docker_container.postgres" to delete just the
# container without destroying the volume.

resource "docker_volume" "postgres_data" {
  name = "${local.app_name}_postgres_data"
}

resource "docker_volume" "redis_data" {
  name = "${local.app_name}_redis_data"
}

# ── PostgreSQL ────────────────────────────────────────────────────────────────

resource "docker_container" "postgres" {
  name  = "${local.app_name}-postgres"
  image = docker_image.postgres.image_id

  # Restart policy — equivalent to "restart: unless-stopped" in docker-compose
  restart = "unless-stopped"

  # Connect to our network
  networks_advanced {
    name = docker_network.smartqueue_network.name
  }

  # Port mapping: host:container
  ports {
    internal = 5432
    external = 5432
  }

  # Environment variables
  env = [
    "POSTGRES_DB=${var.postgres_db}",
    "POSTGRES_USER=${var.postgres_user}",
    "POSTGRES_PASSWORD=${var.postgres_password}",
  ]

  # Mount the named volume for data persistence
  volumes {
    volume_name    = docker_volume.postgres_data.name
    container_path = "/var/lib/postgresql/data"
  }

  # Health check — Terraform waits until this passes before marking resource healthy
  healthcheck {
    test         = ["CMD-SHELL", "pg_isready -U ${var.postgres_user} -d ${var.postgres_db}"]
    interval     = "10s"
    timeout      = "5s"
    retries      = 5
    start_period = "10s"
  }

  labels {
    label = "project"
    value = local.common_labels.project
  }
}

# ── Zookeeper ─────────────────────────────────────────────────────────────────

resource "docker_container" "zookeeper" {
  name    = "${local.app_name}-zookeeper"
  image   = docker_image.zookeeper.image_id
  restart = "unless-stopped"

  networks_advanced {
    name = docker_network.smartqueue_network.name
  }

  env = [
    "ZOOKEEPER_CLIENT_PORT=2181",
    "ZOOKEEPER_TICK_TIME=2000",
  ]

  labels {
    label = "project"
    value = local.common_labels.project
  }
}

# ── Kafka ─────────────────────────────────────────────────────────────────────

resource "docker_container" "kafka" {
  name    = "${local.app_name}-kafka"
  image   = docker_image.kafka.image_id
  restart = "unless-stopped"

  # depends_on ensures Zookeeper container is created first.
  # Note: this is creation order only — it doesn't wait for Zookeeper
  # to be healthy. For health-aware ordering, use a provisioner or
  # wait_for_ready (Terraform 1.5+).
  depends_on = [docker_container.zookeeper]

  networks_advanced {
    name = docker_network.smartqueue_network.name
  }

  ports {
    internal = 9092
    external = 9092
  }

  env = [
    "KAFKA_BROKER_ID=1",
    # Uses the container name as hostname — works because both containers
    # are on the same Docker network
    "KAFKA_ZOOKEEPER_CONNECT=${local.app_name}-zookeeper:2181",
    "KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://${local.app_name}-kafka:29092,PLAINTEXT_HOST://localhost:9092",
    "KAFKA_LISTENER_SECURITY_PROTOCOL_MAP=PLAINTEXT:PLAINTEXT,PLAINTEXT_HOST:PLAINTEXT",
    "KAFKA_INTER_BROKER_LISTENER_NAME=PLAINTEXT",
    "KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1",
    "KAFKA_AUTO_CREATE_TOPICS_ENABLE=true",
  ]

  labels {
    label = "project"
    value = local.common_labels.project
  }
}

# ── Spring Boot App ───────────────────────────────────────────────────────────
#
# Builds the Spring Boot image from the root Dockerfile and runs it.
# Uses the docker profile so it connects to other containers by name
# instead of localhost.
#
# Note: For active development, comment this out and run locally with:
#   mvn spring-boot:run
# This avoids a slow Docker rebuild on every code change.

resource "docker_image" "app" {
  name = "smart-task-queue-app:latest"

  build {
    context    = "${path.module}/.."
    dockerfile = "Dockerfile"
  }

  # Rebuild if Dockerfile or pom.xml changes
  triggers = {
    dockerfile = filemd5("${path.module}/../Dockerfile")
    pom        = filemd5("${path.module}/../pom.xml")
  }
}

resource "docker_container" "app" {
  name    = "${local.app_name}-app"
  image   = docker_image.app.image_id
  restart = "unless-stopped"

  depends_on = [
    docker_container.postgres,
    docker_container.kafka,
    docker_container.redis,
    docker_container.ai_classifier,
  ]

  networks_advanced {
    name = docker_network.smartqueue_network.name
  }

  ports {
    internal = 8080
    external = 8080
  }

  env = [
    "SPRING_PROFILES_ACTIVE=docker",
    "SPRING_DATASOURCE_URL=jdbc:postgresql://${local.app_name}-postgres:5432/${var.postgres_db}",
    "SPRING_DATASOURCE_USERNAME=${var.postgres_user}",
    "SPRING_DATASOURCE_PASSWORD=${var.postgres_password}",
    "SPRING_KAFKA_BOOTSTRAP_SERVERS=${local.app_name}-kafka:29092",
    "SPRING_DATA_REDIS_HOST=${local.app_name}-redis",
    "CLASSIFIER_URL=http://${local.app_name}-ai-classifier:8000",
    "JWT_SECRET=${var.jwt_secret}",
  ]

  healthcheck {
    test         = ["CMD-SHELL", "curl -f http://localhost:8080/actuator/health || exit 1"]
    interval     = "10s"
    timeout      = "5s"
    retries      = 5
    start_period = "60s"
  }

  labels {
    label = "project"
    value = local.common_labels.project
  }
}


#
# Builds the Python FastAPI image from the local Dockerfile and runs it.
# docker_image with build block = "docker build" then run the container.

resource "docker_image" "ai_classifier" {
  name = "smartqueue-ai-classifier:latest"

  # Build the image from local source — equivalent to "docker build"
  build {
    context    = "${path.module}/../ai-classifier"
    dockerfile = "DockerFile"
  }

  # Force rebuild if source files change
  triggers = {
    dockerfile = filemd5("${path.module}/../ai-classifier/DockerFile")
    main_py    = filemd5("${path.module}/../ai-classifier/main.py")
  }
}

resource "docker_container" "ai_classifier" {
  name    = "${local.app_name}-ai-classifier"
  image   = docker_image.ai_classifier.image_id
  restart = "unless-stopped"

  networks_advanced {
    name = docker_network.smartqueue_network.name
  }

  ports {
    internal = 8000
    external = 8000
  }

  env = [
    "OPENAI_API_KEY=${var.openai_api_key}",
  ]

  healthcheck {
    test     = ["CMD-SHELL", "curl -f http://localhost:8000/health || exit 1"]
    interval = "15s"
    timeout  = "5s"
    retries  = 3
  }

  labels {
    label = "project"
    value = local.common_labels.project
  }
}

# ── Redis ─────────────────────────────────────────────────────────────────────

resource "docker_container" "redis" {
  name    = "${local.app_name}-redis"
  image   = docker_image.redis.image_id
  restart = "unless-stopped"

  networks_advanced {
    name = docker_network.smartqueue_network.name
  }

  ports {
    internal = 6379
    external = 6379
  }

  # Override the default command to enable AOF persistence
  command = ["redis-server", "--appendonly", "yes"]

  volumes {
    volume_name    = docker_volume.redis_data.name
    container_path = "/data"
  }

  healthcheck {
    test     = ["CMD", "redis-cli", "ping"]
    interval = "10s"
    timeout  = "5s"
    retries  = 5
  }

  labels {
    label = "project"
    value = local.common_labels.project
  }
}