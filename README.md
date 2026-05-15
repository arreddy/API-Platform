# API Management Platform

A production-ready, microservices-based API Management Platform supporting full API lifecycle management — from OAS registration to proxying, rate limiting, analytics, and developer self-service.

## Wiki

The project documentation lives under `docs/` and includes:

- [Architecture](docs/architecture.md)
- [Quick Start](docs/quick-start.md)
- [API Reference](docs/api-reference.md)
- [Deployment](docs/deployment.md)

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    Developer Portal                      │
│              React + TypeScript (port 3003)              │
└───────────────────────┬────────────────────────────────-─┘
                        │ REST /api/v1/*
┌───────────────────────▼─────────────────────────────────┐
│                   Control Plane                          │
│          Spring Boot + Java (port 3001)                  │
│  • API registration & OAS validation                     │
│  • Proxy CRUD + version history                          │
│  • API key management                                    │
│  • Analytics ingestion & queries                         │
└──────┬──────────────────────────────┬────────────────────┘
       │ PostgreSQL                   │ Internal REST
┌──────▼──────┐               ┌───────▼──────────────────┐
│  PostgreSQL  │               │        Gateway            │
│  (port 5432) │               │  Spring Cloud Gateway     │
└─────────────┘               │  (port 3000)              │
                               │  • Dynamic proxy routing  │
┌─────────────┐               │  • API key auth           │
│    Redis     │◄──────────────│  • Rate limiting (Redis)  │
│  (port 6379) │               │  • Request logging        │
└─────────────┘               └──────┬──────────────────-─┘
                                      │ HTTP proxy
┌─────────────────────────────────────▼──────────────────-┐
│                  Mock Server (port 3002)                 │
│     Spring Boot — OAS-driven fake response generation    │
└─────────────────────────────────────────────────────────┘
```

## Quick Start

### Prerequisites
- Docker & Docker Compose
- Java 21+ and Maven 3.9+ (for backend services)
- Node.js 20+ (for Developer Portal only)

### 1. Clone and configure

```bash
cp .env.example .env
# Edit .env with your secrets
```

### 2. Start everything with Docker Compose

```bash
npm run docker:up
```

Services start at:
| Service | URL |
|---|---|
| **Developer Portal** | http://localhost:3003 |
| **API Gateway** | http://localhost:3000 |
| **Control Plane API** | http://localhost:3001 |
| **Mock Server** | http://localhost:3002 |

### 3. Local development (without Docker)

```bash
# Start just the infrastructure (DB + Redis)
docker compose up postgres redis -d

# Terminal 1 — Control Plane (Spring Boot) → :3001
cd services/control-plane && mvn spring-boot:run

# Terminal 2 — API Gateway (Spring Cloud Gateway) → :3000
cd services/gateway && mvn spring-boot:run

# Terminal 3 — Mock Server (Spring Boot) → :3002
cd services/mock-server && mvn spring-boot:run

# Terminal 4 — Developer Portal (Vite) → :3003
cd portal && npm install && npm run dev
```

## API Reference

### Control Plane (`/api/v1`)

#### OAS Registration
| Method | Path | Description |
|--------|------|-------------|
| `GET`  | `/apis` | List all APIs |
| `POST` | `/apis` | Register API (upload OAS file or paste JSON/YAML) |
| `GET`  | `/apis/:id` | Get API details |
| `GET`  | `/apis/:id/oas` | Download OAS document (JSON or YAML) |
| `PUT`  | `/apis/:id` | Update API / re-upload OAS |
| `DELETE` | `/apis/:id` | Delete API |
| `POST` | `/apis/validate` | Validate OAS without saving |

#### Proxy Management
| Method | Path | Description |
|--------|------|-------------|
| `GET`  | `/proxies` | List proxies |
| `POST` | `/proxies` | Create proxy |
| `GET`  | `/proxies/:id` | Get proxy details |
| `PUT`  | `/proxies/:id` | Update proxy (auto-versions) |
| `DELETE` | `/proxies/:id` | Deactivate proxy |
| `GET`  | `/proxies/:id/versions` | List version history |
| `POST` | `/proxies/:id/rollback/:version` | Rollback to version |

#### API Keys
| Method | Path | Description |
|--------|------|-------------|
| `GET`  | `/keys` | List API keys |
| `POST` | `/keys` | Generate new key (raw key shown once) |
| `DELETE` | `/keys/:id` | Revoke key |

#### Analytics
| Method | Path | Description |
|--------|------|-------------|
| `GET`  | `/analytics/summary` | Traffic summary with time series |
| `GET`  | `/analytics/requests` | Recent request logs |

### Example: Register an OAS

```bash
# Via file upload
curl -X POST http://localhost:3001/api/v1/apis \
  -F "oas=@petstore.yaml"

# Via JSON body
curl -X POST http://localhost:3001/api/v1/apis \
  -H "Content-Type: application/json" \
  -d '{"oas": {"openapi":"3.0.0","info":{"title":"My API","version":"1.0.0"},"paths":{}}}'
```

### Example: Create a proxy

```bash
curl -X POST http://localhost:3001/api/v1/proxies \
  -H "Content-Type: application/json" \
  -d '{
    "name": "petstore-proxy",
    "targetUrl": "https://petstore3.swagger.io/api/v3",
    "pathPrefix": "/petstore",
    "policies": {
      "rateLimit": { "enabled": true, "requests": 100, "window": "1m" },
      "auth": { "type": "api_key" }
    }
  }'
```

### Example: Update a proxy

```bash
# Update target URL, rate limit policy, and leave a change note (auto-versions)
curl -X PUT http://localhost:3001/api/v1/proxies/<id> \
  -H "Content-Type: application/json" \
  -d '{
    "name": "petstore-proxy",
    "targetUrl": "https://petstore3.swagger.io/api/v3",
    "pathPrefix": "/petstore",
    "stripPrefix": true,
    "policies": {
      "rateLimit": { "enabled": true, "requests": 200, "window": "1m" },
      "auth": { "type": "api_key" }
    },
    "headers": { "X-Forwarded-Host": "api.example.com" },
    "changeNote": "Increase rate limit to 200 req/min"
  }'

# Rollback to a previous version
curl -X POST http://localhost:3001/api/v1/proxies/<id>/rollback/<version>
```

### Example: Get proxies

```bash
# List all proxies (dev mode — no auth header needed)
curl http://localhost:3001/api/v1/proxies

# Filter by status
curl "http://localhost:3001/api/v1/proxies?status=active"

# Get a specific proxy by ID
curl http://localhost:3001/api/v1/proxies/<id>

# List version history for a proxy
curl http://localhost:3001/api/v1/proxies/<id>/versions

# With JWT token (production / non-dev mode)
curl http://localhost:3001/api/v1/proxies \
  -H "Authorization: Bearer <your-jwt-token>"
```

### Example: Use the gateway

```bash
# With API key
curl http://localhost:3000/petstore/pet/1 \
  -H "X-Api-Key: apk_your_key_here"
```

### Mock Server

```bash
# Generate mock response from proxy's OAS
curl http://localhost:3002/mock/<proxyId>/pets

# Inline mock (provide OAS + path + method)
curl -X POST http://localhost:3002/mock/inline \
  -H "Content-Type: application/json" \
  -d '{"oasDocument": {...}, "path": "/pets", "method": "GET"}'
```

## Kubernetes Deployment

```bash
# Create namespace + secrets first
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/secrets.yaml   # Update values first!

# Deploy all services
npm run k8s:apply
```

## Features

### Implemented
- OAS 3.0/3.1 upload, validation, and metadata extraction
- Proxy CRUD with full version history and one-click rollback
- Per-proxy policies: rate limiting, API key auth, JWT auth, CORS
- Dynamic gateway routing with longest-prefix match
- API key generation with bcrypt hashing (raw key shown once)
- Redis-backed sliding window rate limiting (falls back to in-memory)
- Batched request logging to analytics store (PostgreSQL partitioned table)
- Analytics dashboard: time series, P99 latency, status distribution
- OAS-driven mock server with schema-aware fake data generation
- Swagger UI embedded in the developer portal
- Multi-tenancy support (tenant isolation on all resources)
- Developer portal with full CRUD UI for all entities
- Docker Compose for local development
- Kubernetes manifests with HPA for auto-scaling
- GitHub Actions CI/CD pipeline

### Optional Enhancements (not yet implemented)
- AI-assisted policy suggestion (Claude API integration point)
- OAuth2 token introspection for JWT auth
- GraphQL API support
- WebSocket proxy support
- mTLS between gateway and backends
