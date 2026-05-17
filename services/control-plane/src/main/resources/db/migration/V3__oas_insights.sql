-- OAS analysis results: Spectral linting + AI quality insights per API
CREATE TABLE oas_insights (
    id                    UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    api_id                UUID         NOT NULL REFERENCES apis(id) ON DELETE CASCADE,
    tenant_id             UUID         NOT NULL REFERENCES tenants(id),
    spectral_violations   JSONB        NOT NULL DEFAULT '[]',
    spectral_error_count  INTEGER      NOT NULL DEFAULT 0,
    spectral_warning_count INTEGER     NOT NULL DEFAULT 0,
    spectral_info_count   INTEGER      NOT NULL DEFAULT 0,
    spectral_hint_count   INTEGER      NOT NULL DEFAULT 0,
    ai_score              INTEGER,
    ai_summary            TEXT,
    ai_risks              JSONB        NOT NULL DEFAULT '[]',
    ai_suggestions        JSONB        NOT NULL DEFAULT '[]',
    analyzed_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE(api_id)
);

CREATE INDEX IF NOT EXISTS idx_oas_insights_api    ON oas_insights(api_id);
CREATE INDEX IF NOT EXISTS idx_oas_insights_tenant ON oas_insights(tenant_id);
