-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Tenants (multi-tenancy support)
CREATE TABLE IF NOT EXISTS tenants (
  id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name         VARCHAR(255) NOT NULL,
  slug         VARCHAR(100) NOT NULL UNIQUE,
  plan         VARCHAR(50)  NOT NULL DEFAULT 'free',
  settings     JSONB        NOT NULL DEFAULT '{}',
  created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

INSERT INTO tenants (id, name, slug, plan)
VALUES ('00000000-0000-0000-0000-000000000001', 'Default', 'default', 'enterprise')
ON CONFLICT DO NOTHING;

-- Users
CREATE TABLE IF NOT EXISTS users (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id     UUID        NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  email         VARCHAR(255) NOT NULL UNIQUE,
  name          VARCHAR(255) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  role          VARCHAR(50)  NOT NULL DEFAULT 'developer',  -- admin | developer | viewer
  created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- APIs (OAS-registered)
CREATE TABLE IF NOT EXISTS apis (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id       UUID         NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  name            VARCHAR(255) NOT NULL,
  title           VARCHAR(255) NOT NULL,
  version         VARCHAR(50)  NOT NULL DEFAULT '1.0.0',
  description     TEXT,
  oas_version     VARCHAR(10)  NOT NULL DEFAULT '3.0',  -- 3.0 | 3.1
  oas_document    JSONB        NOT NULL,
  base_path       VARCHAR(255),
  servers         JSONB        NOT NULL DEFAULT '[]',
  endpoints       JSONB        NOT NULL DEFAULT '[]',
  security_schemes JSONB       NOT NULL DEFAULT '{}',
  tags            TEXT[]       NOT NULL DEFAULT '{}',
  status          VARCHAR(50)  NOT NULL DEFAULT 'active',  -- active | deprecated | archived
  created_by      UUID REFERENCES users(id),
  created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_apis_tenant ON apis(tenant_id);
CREATE INDEX IF NOT EXISTS idx_apis_status ON apis(status);

-- Proxies
CREATE TABLE IF NOT EXISTS proxies (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id     UUID         NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  api_id        UUID         REFERENCES apis(id) ON DELETE SET NULL,
  name          VARCHAR(255) NOT NULL,
  description   TEXT,
  target_url    VARCHAR(500) NOT NULL,
  path_prefix   VARCHAR(255) NOT NULL,
  strip_prefix  BOOLEAN      NOT NULL DEFAULT TRUE,
  version       INTEGER      NOT NULL DEFAULT 1,
  policies      JSONB        NOT NULL DEFAULT '{}',
  routes        JSONB        NOT NULL DEFAULT '[]',
  headers       JSONB        NOT NULL DEFAULT '{}',
  status        VARCHAR(50)  NOT NULL DEFAULT 'active',
  created_by    UUID REFERENCES users(id),
  created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_proxies_tenant ON proxies(tenant_id);
CREATE INDEX IF NOT EXISTS idx_proxies_path ON proxies(path_prefix);

-- Proxy versions (audit trail)
CREATE TABLE IF NOT EXISTS proxy_versions (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  proxy_id      UUID         NOT NULL REFERENCES proxies(id) ON DELETE CASCADE,
  version       INTEGER      NOT NULL,
  snapshot      JSONB        NOT NULL,
  changed_by    UUID REFERENCES users(id),
  change_note   TEXT,
  created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_proxy_versions_unique ON proxy_versions(proxy_id, version);

-- API Keys
CREATE TABLE IF NOT EXISTS api_keys (
  id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id        UUID         NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  proxy_id         UUID         REFERENCES proxies(id) ON DELETE CASCADE,
  name             VARCHAR(255) NOT NULL,
  key_prefix       VARCHAR(12)  NOT NULL,          -- first 12 chars shown in UI
  key_hash         VARCHAR(255) NOT NULL UNIQUE,    -- bcrypt hash
  scopes           TEXT[]       NOT NULL DEFAULT '{}',
  rate_limit       INTEGER      NOT NULL DEFAULT 1000,
  rate_limit_window VARCHAR(20) NOT NULL DEFAULT '1h',
  status           VARCHAR(50)  NOT NULL DEFAULT 'active',
  last_used_at     TIMESTAMPTZ,
  expires_at       TIMESTAMPTZ,
  created_by       UUID REFERENCES users(id),
  created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_api_keys_tenant ON api_keys(tenant_id);
CREATE INDEX IF NOT EXISTS idx_api_keys_prefix ON api_keys(key_prefix);

-- Request logs (partitioned by month for scale)
CREATE TABLE IF NOT EXISTS request_logs (
  id               UUID        NOT NULL DEFAULT gen_random_uuid(),
  tenant_id        UUID        NOT NULL,
  proxy_id         UUID,
  api_key_id       UUID,
  method           VARCHAR(10) NOT NULL,
  path             TEXT        NOT NULL,
  query_params     JSONB,
  status_code      INTEGER,
  latency_ms       INTEGER,
  request_size     INTEGER,
  response_size    INTEGER,
  client_ip        VARCHAR(50),
  user_agent       TEXT,
  error_message    TEXT,
  created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
) PARTITION BY RANGE (created_at);

-- Create initial partitions
CREATE TABLE IF NOT EXISTS request_logs_2025 PARTITION OF request_logs
  FOR VALUES FROM ('2025-01-01') TO ('2026-01-01');

CREATE TABLE IF NOT EXISTS request_logs_2026 PARTITION OF request_logs
  FOR VALUES FROM ('2026-01-01') TO ('2027-01-01');

CREATE INDEX IF NOT EXISTS idx_request_logs_proxy ON request_logs(proxy_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_request_logs_tenant ON request_logs(tenant_id, created_at DESC);

-- Analytics summaries (materialized hourly)
CREATE TABLE IF NOT EXISTS analytics_hourly (
  id           UUID    PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id    UUID    NOT NULL,
  proxy_id     UUID,
  hour         TIMESTAMPTZ NOT NULL,
  total_reqs   INTEGER NOT NULL DEFAULT 0,
  success_reqs INTEGER NOT NULL DEFAULT 0,
  error_reqs   INTEGER NOT NULL DEFAULT 0,
  avg_latency  NUMERIC(10,2),
  p99_latency  INTEGER,
  UNIQUE(tenant_id, proxy_id, hour)
);

CREATE INDEX IF NOT EXISTS idx_analytics_hourly ON analytics_hourly(tenant_id, hour DESC);
