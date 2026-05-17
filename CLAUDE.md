# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Services and Ports

| Service | Tech | Port |
|---|---|---|
| Control Plane | Spring Boot 3.2, Java 21 | 3001 |
| Gateway | Spring Cloud Gateway (reactive) | 3000 |
| Mock Server | Spring Boot 3.2, Java 21 | 3002 |
| Developer Portal | React 18, Vite, TypeScript | 3003 |
| PostgreSQL | 16 | 5432 |
| Redis | 7 | 6379 |

## Commands

### Run everything (Docker)
```bash
docker compose up --build          # foreground
docker compose up --build -d       # background
docker compose up --build portal   # rebuild only portal
docker compose down -v             # stop + wipe DB volumes
```

### Control Plane / Gateway / Mock Server (Maven, Java 21)
```bash
cd services/control-plane   # (or gateway / mock-server)
mvn package -DskipTests     # build jar
mvn spring-boot:run         # run locally (needs Postgres on localhost:5432)
mvn test                    # run all tests
mvn test -Dtest=AnalyticsServiceTest          # run a single test class
mvn test -Dtest=AnalyticsServiceTest#getSummary_returnsTotals  # run a single test method
mvn verify                  # tests + JaCoCo coverage check (≥70% line coverage enforced)
mvn fmt:format              # apply Google Java Format (run before committing)
```

### Developer Portal
```bash
cd portal
npm ci
npm run dev      # dev server on :3003 with Vite proxy to localhost:3001
npm run build    # tsc + vite build (what Docker uses)
npm run lint     # eslint
```

## Architecture

### Control Plane
The authoritative management API. All entities (`Api`, `Proxy`, `ApiKey`, `RequestLog`) are scoped by `tenantId`. Every repository query must include a `tenantId` filter. The default dev tenant UUID is `00000000-0000-0000-0000-000000000001`.

**Dev mode** (`DEV_MODE=true`, the default): `SecurityConfig` injects a hard-coded `ApiPrincipal` for any unauthenticated request — no JWT needed locally. Disable in production.

**Internal endpoints** (`/_internal/**`): authenticated by `X-Internal-Token` header, not JWT. These are called service-to-service (gateway → control plane, mock server → control plane) and are `permitAll()` in Spring Security but validated manually in the handler.

**Database**: Flyway manages schema via `services/control-plane/src/main/resources/db/migration/`. `ddl-auto: validate` — Hibernate never modifies the schema. `request_logs` is range-partitioned by `created_at` (yearly partitions). Add a new partition (`request_logs_2027`, etc.) each year.

**JSONB columns**: `policies`, `routes`, `headers`, `oas_document`, `servers`, `endpoints`, `security_schemes` are stored as JSONB via `hypersistence-utils`. Mapped as `Map<String,Object>` or `List<Map<String,Object>>` in entities. The datasource URL includes `?stringtype=unspecified` — required for Hibernate/PostgreSQL JSONB compatibility.

**OAS registration flow**: `ApiController` → `OasValidatorService` (parses with swagger-parser, extracts endpoints/servers/tags) → `ApiRegistryService` (stores OAS document as JSONB, derives a slug `name`). The `name` slug must be unique per tenant.

**Proxy versioning**: Every update to a `Proxy` creates an immutable `ProxyVersion` snapshot in `proxy_versions`. `ProxyService.rollback()` restores the snapshot and increments the version counter.

### Gateway
Fully reactive (Spring WebFlux). Routes are loaded dynamically — there is **no static route configuration**.

**Route lifecycle**: `ProxyRegistry` fetches all active proxies from `GET /api/v1/proxies/_internal/active` at startup (blocking, 10s timeout) and re-fetches every 30 seconds. After each refresh it fires `RefreshRoutesEvent`, causing Spring Cloud Gateway to re-subscribe to the `RouteLocator` and pick up new/changed proxies.

**Request pipeline per proxy** (in order):
1. `GatewayConfig.dynamicRouteLocator` — matches longest-prefix, rewrites path (strips `pathPrefix` if `stripPrefix=true`, prepends target base path), sets `proxy` exchange attribute.
2. `ApiKeyAuthFilter` — if `authType=api_key`, extracts `X-Api-Key`, calls `POST /api/v1/keys/_internal/validate`, caches valid results for 60 seconds in-memory. Auth result stored in exchange attribute `authResult`.
3. `RateLimitFilter` — uses Redis sliding window (key: `rl:{proxyId}:{keyId}:{windowSlot}`). Falls back to in-memory counters when Redis is unreachable. Adds `X-RateLimit-Limit` / `X-RateLimit-Remaining` response headers.
4. `RequestLogFilter` (global, `LOWEST_PRECEDENCE`) — buffers log entries, flushes to `POST /api/v1/analytics/_internal/ingest` every 5s or when buffer reaches 100 entries.

### Mock Server
Stateless. Each request to `GET|POST|PUT|DELETE /mock/{proxyId}/**` fetches the proxy's linked OAS document from the control plane (cached 120s via Caffeine), then `MockGeneratorService` finds the matching operation and generates a realistic fake response using Datafaker. Use `?__status=404` to request a specific response code. `POST /mock/inline` accepts an OAS document directly without proxy registration.

### Developer Portal
Single-page React app. In Docker it's a static nginx build — the Vite dev proxy only applies during `npm run dev`.

**API client** (`portal/src/api/client.ts`): a single axios instance with `baseURL: '/api/v1'`. Do **not** set a global `Content-Type` header on the instance — axios auto-sets `multipart/form-data` for `FormData` and `application/json` for plain objects. An explicit instance-level `Content-Type` header will override the multipart boundary and break file uploads.

**nginx** (`portal/nginx.conf`): proxies `/api/` to `http://control-plane:3001`. Requires `client_max_body_size 10m` and `proxy_request_buffering off` on the `/api/` location for multipart OAS uploads to work correctly.

**Vite TypeScript config**: `tsconfig.json` must include `"types": ["vite/client"]` for `import.meta.env` to type-check.

## Testing Conventions (Control Plane)

Controller tests use `@WebMvcTest` + `@WithMockApiPrincipal` (custom annotation in `support/`) which injects a real `ApiPrincipal` into the security context. `TestSecurityConfig` disables the real JWT filter for tests.

Service tests use Mockito (`@ExtendWith(MockitoExtension.class)`) — no Spring context loaded. Repository methods now take `proxyId` as a parameter (can be `null`); mock with `any()` for the `proxyId` slot.

Native SQL queries with optional `proxyId` filtering use `CAST(:proxyId AS TEXT) IS NULL OR proxy_id = :proxyId` — PostgreSQL cannot infer the type of a bare `? IS NULL` placeholder. JPQL queries use `(:proxyId IS NULL OR r.proxyId = :proxyId)` which works fine.

## Key Environment Variables

| Variable | Default | Used by |
|---|---|---|
| `JWT_SECRET` | `dev-secret-change-in-production` | control-plane |
| `INTERNAL_TOKEN` | `internal-dev-token` | all services |
| `DEV_MODE` | `true` | control-plane |
| `CORS_ORIGIN` | `*` | control-plane |
| `CONTROL_PLANE_URL` | `http://localhost:3001` | gateway, mock-server |
| `REDIS_HOST` | `localhost` | gateway |
