# API Reference

## Control Plane API

The Control Plane is the central management API for API and proxy lifecycle operations.

### Base path

`http://localhost:3001/api/v1`

### OAS registration

| Method | Path | Description |
|---|---|---|
| `GET` | `/apis` | List all registered APIs |
| `POST` | `/apis` | Register an API via OAS upload or JSON/YAML body |
| `GET` | `/apis/:id` | Get metadata for a specific API |
| `GET` | `/apis/:id/oas` | Download the OpenAPI document |
| `PUT` | `/apis/:id` | Update an API or re-upload its OAS |
| `DELETE` | `/apis/:id` | Delete an API |
| `POST` | `/apis/validate` | Validate an OAS document without saving |
| `GET` | `/apis/:id/insights` | Get the latest Spectral + AI analysis for an API |
| `POST` | `/apis/:id/analyze` | Re-run Spectral + AI analysis and update stored insights |

#### Registration response

`POST /apis` returns the saved API summary, a suggested proxy config, and the analysis insights (if the OAS Analyzer is reachable):

```json
{
  "api": { "id": "...", "name": "pet-api", "status": "active", ... },
  "suggested": { "targetUrl": "https://...", "pathPrefix": "/pet-api", "routes": [...] },
  "insights": {
    "apiId": "...",
    "spectral": {
      "violations": [{ "code": "...", "message": "...", "severity": "warning", "path": "..." }],
      "errorCount": 0,
      "warningCount": 3,
      "infoCount": 0,
      "hintCount": 1
    },
    "ai": {
      "score": 72,
      "summary": "The API has reasonable structure but lacks...",
      "risks": [{ "severity": "high", "category": "security", "description": "...", "recommendation": "..." }],
      "suggestions": [{ "category": "documentation", "description": "...", "impact": "..." }]
    },
    "analyzedAt": "2026-05-17T08:00:00Z"
  }
}
```

`insights` is `null` when the OAS Analyzer is unreachable or `OPENAI_API_KEY` is not set for the AI portion.

### Proxy management

| Method | Path | Description |
|---|---|---|
| `GET` | `/proxies` | List all proxies |
| `POST` | `/proxies` | Create a new proxy |
| `GET` | `/proxies/:id` | Get proxy details |
| `PUT` | `/proxies/:id` | Update proxy configuration |
| `DELETE` | `/proxies/:id` | Deactivate a proxy |
| `GET` | `/proxies/:id/versions` | List proxy version history |
| `POST` | `/proxies/:id/rollback/:version` | Roll back proxy to a previous version |

### API keys

| Method | Path | Description |
|---|---|---|
| `GET` | `/keys` | List API keys |
| `POST` | `/keys` | Generate a new API key |
| `DELETE` | `/keys/:id` | Revoke an API key |

### Analytics

| Method | Path | Description |
|---|---|---|
| `GET` | `/analytics/summary` | Get traffic summary and metrics |
| `GET` | `/analytics/requests` | Get recent request log data |

## OAS Analyzer API

The OAS Analyzer runs on port `3004` and is called internally by the Control Plane. It can also be called directly.

### Base path

`http://localhost:3004`

### Endpoints

| Method | Path | Description |
|---|---|---|
| `GET` | `/health` | Health check — returns `{ "status": "ok" }` |
| `POST` | `/analyze` | Analyze an OAS document with Spectral and AI |

### Analyze request

```bash
curl -X POST http://localhost:3004/analyze \
  -H "Content-Type: application/json" \
  -d '{"oasContent": "openapi: 3.0.0\ninfo:\n  title: My API\n  version: 1.0.0\npaths: {}"}'
```

`oasContent` can be a YAML string, JSON string, or a pre-parsed JSON object. Documents longer than 48 000 characters are truncated before being sent to OpenAI.

### Analyze response

```json
{
  "spectral": {
    "violations": [
      {
        "code": "info-contact",
        "message": "Info object should contain `contact` object.",
        "severity": "warning",
        "path": "info",
        "start": { "line": 1, "character": 0 }
      }
    ],
    "errorCount": 0,
    "warningCount": 1,
    "infoCount": 0,
    "hintCount": 0
  },
  "ai": {
    "score": 55,
    "summary": "The API is minimal and missing several production-readiness elements.",
    "risks": [],
    "suggestions": []
  }
}
```

## Gateway usage

The gateway exposes proxy routes configured by the Control Plane.

Example request with API key:

```bash
curl http://localhost:3000/petstore/pet/1 \
  -H "X-Api-Key: apk_your_key_here"
```

The gateway enforces configured policies such as rate limiting, CORS, and API key authentication.

## Mock Server

The mock server provides API mock responses from OAS definitions.

### Mock endpoints

- `GET http://localhost:3002/mock/<proxyId>/...`
- `POST http://localhost:3002/mock/inline`

### Example inline mock request

```bash
curl -X POST http://localhost:3002/mock/inline \
  -H "Content-Type: application/json" \
  -d '{"oasDocument": {...}, "path": "/pets", "method": "GET"}'
```
