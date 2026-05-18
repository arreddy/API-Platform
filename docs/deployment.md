# Deployment

## Docker Compose Deployment

The repository includes `docker-compose.yml` and a root-level command set for Docker-based deployment.

### Start the platform

```bash
npm run docker:up
```

### Stop the platform

```bash
npm run docker:down
```

### Notes

- The `control-plane`, `gateway`, `mock-server`, `oas-analyzer`, and `portal` services are built from the repo.
- `postgres` and `redis` run as companion infrastructure services.
- Environment variables are injected via `docker-compose.yml` and can be configured using `.env`.
- The `oas-analyzer` service requires `OPENAI_API_KEY` for AI scoring. Spectral linting works without it. Set the key in `.env` (never commit it to the repo):

```bash
echo "OPENAI_API_KEY=sk-proj-..." >> .env
```

## Kubernetes Deployment

The `k8s` directory contains manifests for namespace, secrets, and all services.

### Apply manifests

```bash
npm run k8s:apply
```

### Delete manifests

```bash
npm run k8s:delete
```

### Recommended order

1. `kubectl apply -f k8s/namespace.yaml`
2. `kubectl apply -f k8s/secrets.yaml`
3. `npm run k8s:apply`

> Update secret values in `k8s/secrets.yaml` before deploying.

## Build pipeline

### Build all services locally

```bash
npm run build:all
```

### Docker build

```bash
npm run docker:build
```

## Production readiness notes

- Change default secrets before production.
- Use a managed PostgreSQL and Redis for reliability.
- Secure the gateway and internal service communication with TLS and token auth.
- Store `OPENAI_API_KEY` in a secrets manager (e.g. Kubernetes Secret, AWS SSM). Never hardcode it in `docker-compose.yml` or commit it to source control.
- The OAS Analyzer is stateless — it can be scaled horizontally without coordination.
