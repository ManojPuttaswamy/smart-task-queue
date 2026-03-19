# Smart Task Queue System

A **production-grade, event-driven job processing platform** built over 21 days to demonstrate real-world backend engineering depth.

The system accepts job submissions via REST API, processes them asynchronously through Kafka, classifies them using an AI service, and provides full observability through Prometheus and Grafana.

---

## Architecture

```
                        ┌─────────────────────────────────────────────────┐
                        │              Smart Task Queue System            │
                        │                                                 │
  Client (Postman)      │   ┌──────────────┐    ┌─────────────────────┐   │
       │                │   │  Spring Boot │    │   Python FastAPI    │   │
       │ POST /jobs     │   │   REST API   │───►│   AI Classifier     │   │
       └───────────────►│   │  Port 8080   │    │   Port 8000         │   │
                        │   └──────┬───────┘    └─────────────────────┘   │
                        │          │                                      │
                        │    ┌─────▼──────┐                               │
                        │    │   Kafka    │  job-events topic             │
                        │    │  Port 9092 │  job-dlq topic                │
                        │    └─────┬──────┘                               │
                        │          │                                      │
                        │    ┌─────▼───────┐    ┌───────────┐             │
                        │    │  Consumer   │───►│ PostgreSQL│             │
                        │    │  (same app) │    │ Port 5432 │             │
                        │    └─────┬───────┘    └───────────┘             │
                        │          │                                      │
                        │    ┌─────▼───────┐    ┌──────────┐              │
                        │    │   Redis     │    │Prometheus│              │
                        │    │  Port 6379  │    │ Port 9090│              │
                        │    └─────────────┘    └──────────┘              │
                        │                            │                    │
                        │                      ┌─────▼──────┐             │
                        │                      │  Grafana   │             │
                        │                      │ Port 3000  │             │
                        │                      └────────────┘             │
                        └─────────────────────────────────────────────────┘
```

---

## Tech Stack

| Technology | Version | Purpose |
|---|---|---|
| Java | 17 | Core language |
| Spring Boot | 3.2.0 | Backend framework |
| PostgreSQL | 15 | Persistent job storage |
| Apache Kafka | 3.6.0 | Async event streaming |
| Redis | 7.2 | Idempotency control + classification cache |
| Python FastAPI | Latest | AI job classification service |
| OpenAI GPT-4o-mini | Latest | Job classification model |
| Prometheus | 2.48.0 | Metrics collection |
| Grafana | 10.2.0 | Metrics visualization |
| Docker Compose | Latest | Local orchestration |
| Kubernetes | Latest | Production deployment manifests |
| Terraform | 1.0+ | Infrastructure as Code |

---

## Key Features

### Event-Driven Architecture
- Jobs submitted via REST API are immediately saved to PostgreSQL and published to Kafka
- Kafka consumer processes jobs asynchronously — HTTP response returns in ~50ms
- Producer and consumer scale independently

### Reliability
- **Idempotency** — Redis prevents duplicate job processing using jobId:eventType keys with 24hr TTL
- **Retry logic** — Failed jobs retry 3 times with exponential backoff (2s, 4s, 8s)
- **Dead Letter Queue** — Jobs that exhaust retries land in job-dlq topic for investigation
- **State machine** — Enforces valid job transitions: PENDING → PROCESSING → COMPLETED/FAILED
- **Optimistic locking** — @Version on JobInstance prevents race conditions

### Security
- **JWT authentication** — Stateless tokens with configurable expiry
- **Multi-tenancy** — Complete tenant isolation — users only see their own jobs
- **RBAC** — Three roles: VIEWER (GET only), OPERATOR (POST jobs), ADMIN (replay, audit logs)
- **Audit logging** — Every action recorded with who did it, old state, new state

### AI Integration
- Python FastAPI service classifies jobs by category and priority
- Confidence threshold — if AI confidence < 0.6, falls back to keyword rules
- Classification runs async — does not block job creation
- Results cached in Redis (1hr TTL) — same title = no repeat API calls

### Observability
- **Distributed tracing** — correlationId flows through all threads, Kafka messages, and into the Python service
- **MDC logging** — Every log line tagged with correlationId — one grep traces a complete job journey
- **Prometheus metrics** — 8 counters, 1 timer (P50/P95/P99), 2 gauges
- **Grafana dashboard** — 9 panels: jobs/min, failure rate, latency P95, DLQ size, cache hit rate

---

## Project Structure

```
smart-task-queue/
├── src/main/java/com/smartqueue/
│   ├── controller/          # REST endpoints
│   ├── service/             # Business logic
│   ├── kafka/               # Producers, consumers, event POJOs
│   ├── entity/              # JPA entities
│   ├── repository/          # Spring Data JPA repositories
│   ├── security/            # JWT filter, auth entry points
│   ├── statemachine/        # Job lifecycle state machine
│   └── config/              # Kafka, Redis, Security configuration
├── ai-classifier/           # Python FastAPI AI service
├── k8s/                     # Kubernetes manifests
├── terraform/               # Infrastructure as Code
├── grafana/                 # Grafana dashboard + provisioning
├── prometheus/              # Prometheus scrape config
├── Dockerfile               # Spring Boot multi-stage Docker build
└── docker-compose.yml       # Full local stack orchestration
```

---

## API Endpoints

### Auth
| Method | Endpoint | Description |
|---|---|---|
| POST | /auth/register | Register a new user |
| POST | /auth/login | Login, returns JWT token |

### Jobs
| Method | Endpoint | Role | Description |
|---|---|---|---|
| POST | /jobs | OPERATOR | Submit a new job |
| GET | /jobs | VIEWER | List all jobs for your tenant |
| GET | /jobs/{jobId} | VIEWER | Get a specific job |

### Admin
| Method | Endpoint | Role | Description |
|---|---|---|---|
| POST | /admin/jobs/{jobId}/replay | ADMIN | Replay a failed job from DLQ |
| GET | /admin/audit-logs | ADMIN | View all audit logs |

---

## Running Locally

### Prerequisites
- Docker Desktop
- Java 17
- Maven
- Python 3.11 (for local AI classifier dev)
- OpenAI API key

### Start Everything

```bash
git clone https://github.com/ManojPuttaswamy/smart-task-queue
cd smart-task-queue

# Add your OpenAI API key
echo "OPENAI_API_KEY=sk-..." > ai-classifier/.env

# Start full stack
docker-compose up -d

# Check all services healthy
docker-compose ps
```

Services:
- API: http://localhost:8080
- Kafka UI: http://localhost:8090
- Prometheus: http://localhost:9090
- Grafana: http://localhost:3000 (admin/admin)

### Quick Test

```bash
# Register
curl -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"pass123","tenantId":"tenant-a","role":"OPERATOR"}'

# Login and get token
TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"pass123"}' | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])")

# Submit a job
curl -X POST http://localhost:8080/jobs \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"title":"Database timeout","description":"Primary DB connection pool exhausted"}'
```

---

## Design Decisions

**Why Kafka instead of direct processing?**
Decouples submission speed from processing speed. The HTTP request returns in ~50ms. Processing happens asynchronously. If the consumer crashes, Kafka retains the message.

**Why Redis for idempotency instead of a DB column?**
Sub-millisecond reads, automatic TTL cleanup, and atomic SETNX operations. A DB column would add 5-20ms per message and require manual cleanup jobs.

**Why optimistic locking instead of pessimistic?**
Each job is processed by one consumer — concurrent writes are rare. Optimistic locking gives safety without the performance cost of DB-level row locks.

**Why a separate Python service for AI?**
Keeps ML dependencies out of the Java classpath. Python is the standard for ML tooling. Services communicate via HTTP — either can be scaled or replaced independently.

**Why correlationId instead of Zipkin/Jaeger?**
Lower complexity for a single-service system. The correlationId approach achieves the same goal without adding distributed tracing infrastructure.

---