"""
Python AI Classifier Service

FastAPI service that classifies job tickets using OpenAI.
Receives { title, description } and returns:
  - category: Infrastructure / Database / Application / Network
  - priority: HIGH / MEDIUM / LOW
  - confidence: 0.0 - 1.0
  - recommended_action: string
  - source: "AI" or "FALLBACK"

Design decisions:
  - Pydantic models for request/response validation (FastAPI's native approach)
  - Structured prompt that instructs the model to return ONLY valid JSON
  - Confidence threshold: if < 0.6, use keyword fallback instead
  - Fallback rule engine: keyword matching as safety net
  - Source field: always tells the caller whether AI or fallback was used
"""

import os
import json
import logging
from fastapi import FastAPI, HTTPException, Request
from pydantic import BaseModel
from openai import OpenAI
from dotenv import load_dotenv

load_dotenv()

# ── Logging setup ─────────────────────────────────────────────────────────────
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s"
)
log = logging.getLogger("ai-classifier")

# ── FastAPI app ────────────────────────────────────────────────────────────────
app = FastAPI(
    title="Smart Task Queue — AI Classifier",
    description="Classifies job tickets by category and priority using OpenAI",
    version="1.0.0"
)

# ── OpenAI client ──────────────────────────────────────────────────────────────
client = OpenAI(api_key=os.getenv("OPENAI_API_KEY"))


# ── Pydantic models ────────────────────────────────────────────────────────────

class ClassifyRequest(BaseModel):
    """What the Java backend sends us."""
    title: str
    description: str


class ClassifyResponse(BaseModel):
    """What we return — always this shape, whether AI or fallback."""
    category: str           # Infrastructure / Database / Application / Network
    priority: str           # HIGH / MEDIUM / LOW
    confidence: float       # 0.0 - 1.0
    recommended_action: str
    source: str             # "AI" or "FALLBACK"


# ── Prompt template ────────────────────────────────────────────────────────────

SYSTEM_PROMPT = """You are an expert IT incident classifier for a job processing system.
Your job is to analyze job tickets and classify them accurately.

You MUST respond with ONLY valid JSON — no explanation, no markdown, no code blocks.
Do not include ```json or any other formatting. Just the raw JSON object.

The JSON must have exactly these fields:
{
  "category": "Infrastructure" | "Database" | "Application" | "Network",
  "priority": "HIGH" | "MEDIUM" | "LOW",
  "confidence": <float between 0.0 and 1.0>,
  "recommended_action": "<specific action to take>"
}

Priority guidelines:
- HIGH: system down, data loss risk, security breach, production outage
- MEDIUM: degraded performance, partial failure, non-critical service issue
- LOW: cosmetic issues, minor bugs, maintenance tasks, feature requests

Confidence guidelines:
- 0.9+: very clear category and priority
- 0.7-0.9: reasonably clear but some ambiguity
- 0.5-0.7: unclear, could fit multiple categories
- below 0.5: very ambiguous — fallback will be used instead"""


USER_PROMPT_TEMPLATE = """Classify this job ticket:

Title: {title}
Description: {description}

Respond with ONLY the JSON object."""


# ── Fallback rule engine ───────────────────────────────────────────────────────

def keyword_fallback(title: str, description: str) -> ClassifyResponse:
    """
    Rule-based classification when AI confidence is too low or AI fails.

    Why have a fallback?
    - AI can hallucinate or return malformed JSON
    - OpenAI API can be down or rate-limited
    - Low confidence means the AI itself isn't sure — fallback is safer

    This is intentionally simple — just keyword matching.
    In production you'd have a more sophisticated rule engine.
    """
    text = (title + " " + description).lower()

    # Determine category by keyword matching
    if any(k in text for k in ["database", "db", "sql", "postgres", "mysql", "query", "migration"]):
        category = "Database"
        recommended_action = "Check database logs and connection pool. Run EXPLAIN on slow queries."
    elif any(k in text for k in ["cpu", "memory", "disk", "server", "infrastructure", "hardware", "vm", "kubernetes", "k8s"]):
        category = "Infrastructure"
        recommended_action = "Check system metrics (CPU, memory, disk). Scale resources if needed."
    elif any(k in text for k in ["network", "connection", "timeout", "latency", "dns", "firewall", "ssl", "tls"]):
        category = "Network"
        recommended_action = "Check network connectivity, DNS resolution, and firewall rules."
    else:
        category = "Application"
        recommended_action = "Check application logs for errors and recent deployments."

    # Determine priority by keyword matching
    if any(k in text for k in ["down", "outage", "crash", "critical", "urgent", "production", "data loss", "breach"]):
        priority = "HIGH"
    elif any(k in text for k in ["slow", "degraded", "intermittent", "partial", "warning"]):
        priority = "MEDIUM"
    else:
        priority = "LOW"

    log.info(f"Fallback classification: category={category}, priority={priority}")

    return ClassifyResponse(
        category=category,
        priority=priority,
        confidence=0.5,         # fallback always reports 0.5 — honest about uncertainty
        recommended_action=recommended_action,
        source="FALLBACK"
    )


# ── Core classification logic ──────────────────────────────────────────────────

def classify_with_ai(title: str, description: str, correlation_id: str = "unknown") -> ClassifyResponse:
    """
    Calls OpenAI and parses the response.

    Steps:
    1. Build the prompt with the job details
    2. Call GPT-4o-mini (cheap, fast, good enough for classification)
    3. Parse the JSON response
    4. Validate all required fields are present
    5. Check confidence threshold — if < 0.6, use fallback instead
    """
    log.info(f"Calling OpenAI for classification: title='{title}' [correlationId={correlation_id}]")

    user_prompt = USER_PROMPT_TEMPLATE.format(
        title=title,
        description=description
    )

    response = client.chat.completions.create(
        model="gpt-4o-mini",        # cheap and fast — ideal for classification
        messages=[
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "user",   "content": user_prompt}
        ],
        temperature=0.1,            # low temperature = more deterministic output
        max_tokens=200              # classification response is always small
    )

    raw = response.choices[0].message.content.strip()
    log.info(f"OpenAI raw response: {raw}")

    # Parse and validate the JSON
    data = json.loads(raw)

    # Validate required fields
    required = {"category", "priority", "confidence", "recommended_action"}
    missing = required - set(data.keys())
    if missing:
        raise ValueError(f"AI response missing required fields: {missing}")

    # Validate enum values
    valid_categories = {"Infrastructure", "Database", "Application", "Network"}
    valid_priorities = {"HIGH", "MEDIUM", "LOW"}

    if data["category"] not in valid_categories:
        raise ValueError(f"Invalid category: {data['category']}")
    if data["priority"] not in valid_priorities:
        raise ValueError(f"Invalid priority: {data['priority']}")

    confidence = float(data["confidence"])

    # Confidence threshold check — if AI isn't sure, use fallback
    if confidence < 0.6:
        log.warning(f"AI confidence too low ({confidence:.2f}), using fallback")
        return keyword_fallback(title, description)

    log.info(f"AI classification: category={data['category']}, priority={data['priority']}, confidence={confidence:.2f}")

    return ClassifyResponse(
        category=data["category"],
        priority=data["priority"],
        confidence=confidence,
        recommended_action=data["recommended_action"],
        source="AI"
    )


# ── Endpoints ──────────────────────────────────────────────────────────────────

@app.get("/health")
def health():
    """Health check — used by Docker Compose depends_on."""
    return {"status": "ok", "service": "ai-classifier"}


@app.post("/classify", response_model=ClassifyResponse)
def classify(request: ClassifyRequest, http_request: Request):
    """
    Main classification endpoint.

    extract X-Correlation-ID header from the Java backend.
    This ties Python service logs to the same correlationId used throughout
    the Java app — one ID traces the job across both services.
    """
    # Extract correlationId forwarded by ClassifierClient
    correlation_id = http_request.headers.get("X-Correlation-ID", "unknown")
    log.info(f"POST /classify: title='{request.title}' [correlationId={correlation_id}]")

    try:
        result = classify_with_ai(request.title, request.description, correlation_id)
        return result
    except json.JSONDecodeError as e:
        log.error(f"AI returned invalid JSON: {e}. Using fallback. [correlationId={correlation_id}]")
        return keyword_fallback(request.title, request.description)
    except ValueError as e:
        log.error(f"AI response validation failed: {e}. Using fallback. [correlationId={correlation_id}]")
        return keyword_fallback(request.title, request.description)
    except Exception as e:
        log.error(f"Unexpected error calling OpenAI: {e}. Using fallback. [correlationId={correlation_id}]")
        return keyword_fallback(request.title, request.description)


# ── Entry point ────────────────────────────────────────────────────────────────

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("main:app", host="0.0.0.0", port=8000, reload=True)