# Architecture

The API Management Platform is built as a microservices-based system with a developer portal, control plane, gateway, mock server, and supporting infrastructure.

## Service topology

- **Developer Portal** (`portal`)
  - React + TypeScript frontend.
  - Provides API registration, proxy management, analytics dashboards, and developer self-service.
  - Communicates with the Control Plane API.

- **Control Plane** (`services/control-plane`)
  - Spring Boot service exposing `/api/v1`.
  - Handles OAS registration, proxy lifecycle, API key management, analytics ingestion, and configuration persistence.

- **Gateway** (`services/gateway`)
  - Spring Cloud Gateway.
  - Implements dynamic request routing, API key authentication, rate limiting, and proxy execution.
  - Uses Redis for policy state and sliding window rate limiting.

- **Mock Server** (`services/mock-server`)
  - Spring Boot service that generates mock responses using OAS definitions.
  - Can serve inline mock requests or proxy-based mock endpoints.

## Infrastructure dependencies

- **PostgreSQL** (`postgres`)
  - Primary persistence store for control plane metadata, API keys, proxy configs, and analytics events.

- **Redis** (`redis`)
  - Used by the gateway for rate limiting and runtime policy state.

## Runtime ports

| Service | Port |
|---|---|
| Developer Portal | `3003` |
| API Gateway | `3000` |
| Control Plane API | `3001` |
| Mock Server | `3002` |
| PostgreSQL | `5432` |
| Redis | `6379` |

## Data flow

1. The developer portal sends API registration, proxy creation, and analytics queries to the Control Plane.
2. The Control Plane stores configuration in PostgreSQL and exposes management APIs.
3. The Gateway consults the Control Plane (via internal token) to build routing and enforce policies.
4. The Mock Server reads OAS definitions from the Control Plane to generate fake responses.
5. Gateway logs request data into the Control Plane analytics pipeline.
