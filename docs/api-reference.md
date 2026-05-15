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
