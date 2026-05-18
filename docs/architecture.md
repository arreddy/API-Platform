# Architecture

The API Management Platform is built as a microservices-based system with a developer portal, control plane, gateway, mock server, OAS analyzer, and supporting infrastructure.

## Service topology

- **Developer Portal** (`portal`)
  - React + TypeScript frontend.
  - Provides API registration, proxy management, analytics dashboards, and developer self-service.
  - Communicates with the Control Plane API.

- **Control Plane** (`services/control-plane`)
  - Spring Boot service exposing `/api/v1`.
  - Handles OAS registration, proxy lifecycle, API key management, analytics ingestion, and configuration persistence.
  - Calls the OAS Analyzer after every API registration (best-effort — analysis failure never blocks registration).
  - Stores analysis results in the `oas_insights` table (Flyway migration `V3__oas_insights.sql`).

- **OAS Analyzer** (`services/oas-analyzer`)
  - Stateless Node.js / Express service on port `3004`.
  - `POST /analyze` accepts an OAS document and returns two analysis payloads:
    - **Spectral**: lints the document with `@stoplight/spectral-rulesets` OAS ruleset, returns violations with severity (`error`, `warning`, `info`, `hint`).
    - **AI**: sends the document to OpenAI `gpt-4o` and returns a `score` (0–100), `summary`, `risks`, and `suggestions`. Requires `OPENAI_API_KEY`; gracefully disabled when absent.

- **Gateway** (`services/gateway`)
  - Spring Cloud Gateway.
  - Implements dynamic request routing, API key authentication, rate limiting, and proxy execution.
  - Uses Redis for policy state and sliding window rate limiting.

- **Mock Server** (`services/mock-server`)
  - Spring Boot service that generates mock responses using OAS definitions.
  - Can serve inline mock requests or proxy-based mock endpoints.

## Infrastructure dependencies

- **PostgreSQL** (`postgres`)
  - Primary persistence store for control plane metadata, API keys, proxy configs, analytics events, and OAS analysis results.

- **Redis** (`redis`)
  - Used by the gateway for rate limiting and runtime policy state.

## Runtime ports

| Service | Port |
|---|---|
| Developer Portal | `3003` |
| API Gateway | `3000` |
| Control Plane API | `3001` |
| Mock Server | `3002` |
| OAS Analyzer | `3004` |
| PostgreSQL | `5432` |
| Redis | `6379` |

## Data flow

1. The developer portal sends API registration, proxy creation, and analytics queries to the Control Plane.
2. The Control Plane stores configuration in PostgreSQL and exposes management APIs.
3. After registering an API, the Control Plane calls the OAS Analyzer to lint the OAS document and score it with AI. The result is stored in `oas_insights` and returned in the registration response.
4. The Gateway consults the Control Plane (via internal token) to build routing and enforce policies.
5. The Mock Server reads OAS definitions from the Control Plane to generate fake responses.
6. Gateway logs request data into the Control Plane analytics pipeline.

## OAS analysis pipeline

```
POST /api/v1/apis  (multipart or JSON)
        │
        ▼
OasValidatorService   ← parses & validates OAS with swagger-parser
        │
        ▼
ApiRegistryService    ← saves Api entity to PostgreSQL
        │
        ▼
OasAnalysisService    ← calls OAS Analyzer (best-effort, async)
        │
   ┌────┴────┐
   ▼         ▼
Spectral    OpenAI gpt-4o
(always)    (if OPENAI_API_KEY set)
   │         │
   └────┬────┘
        ▼
  oas_insights (PostgreSQL)
```
