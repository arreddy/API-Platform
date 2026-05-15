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

- The `control-plane`, `gateway`, `mock-server`, and `portal` services are built from the repo.
- `postgres` and `redis` run as companion infrastructure services.
- Environment variables are injected via `docker-compose.yml` and can be configured using `.env`.

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
