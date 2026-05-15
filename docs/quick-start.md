# Quick Start

## Prerequisites

- Docker & Docker Compose
- Java 21+ and Maven 3.9+ (for backend services)
- Node.js 20+ (for Developer Portal)

## Docker Compose local setup

From the repository root:

```bash
npm run docker:up
```

This starts all services in the background.

### Verify services

- Developer Portal: `http://localhost:3003`
- API Gateway: `http://localhost:3000`
- Control Plane: `http://localhost:3001`
- Mock Server: `http://localhost:3002`

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
