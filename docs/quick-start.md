# Quick Start

## Prerequisites

- Docker & Docker Compose
- Java 21+ and Maven 3.9+ (for backend services)
- Node.js 20+ (for Developer Portal and OAS Analyzer)

## Docker Compose local setup

From the repository root:

```bash
npm run docker:up
```

This starts all services in the background.

To enable AI analysis, set `OPENAI_API_KEY` in a `.env` file at the repo root before starting:

```bash
echo "OPENAI_API_KEY=sk-proj-..." > .env
npm run docker:up
```

### Verify services

- Developer Portal: `http://localhost:3003`
- API Gateway: `http://localhost:3000`
- Control Plane: `http://localhost:3001`
- Mock Server: `http://localhost:3002`
- OAS Analyzer: `http://localhost:3004/health`

### Stop services

```bash
npm run docker:down
```

## Native local development

### Start infrastructure only

```bash
npm run dev:infra
```

### Run the Control Plane

```bash
npm run dev:cp
```

### Run the Gateway

```bash
npm run dev:gateway
```

### Run the Mock Server

```bash
npm run dev:mock
```

### Run the OAS Analyzer

```bash
cd services/oas-analyzer
npm ci
OPENAI_API_KEY=sk-proj-... npm run dev
```

Spectral linting works without `OPENAI_API_KEY`. Set it to also get AI scoring.

### Run the Developer Portal

```bash
npm run dev:portal
```

### Install portal dependencies

```bash
npm run install:portal
```

## Build commands

### Build all services

```bash
npm run build:all
```

### Build each service

```bash
npm run build:cp
npm run build:gw
npm run build:mock
npm run build:portal
```

## Notes

- The repo root `package.json` is the monorepo launcher for Docker and local development workflows.
- The portal is powered by Vite and TypeScript.
- Backends use Maven and Spring Boot.
- The OAS Analyzer (`services/oas-analyzer`) is a standalone Node.js service — it can be started independently of the Java services.
- Never commit `OPENAI_API_KEY` to source control. Use `.env` (already gitignored) or export it in your shell.
