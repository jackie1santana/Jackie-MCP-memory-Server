CREATE TABLE IF NOT EXISTS memory_category (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS memory (
    id UUID PRIMARY KEY,
    category VARCHAR(100) NOT NULL,
    scope VARCHAR(100) NOT NULL,
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    memory_type VARCHAR(50) NOT NULL,
    importance INTEGER NOT NULL CHECK (importance BETWEEN 1 AND 10),
    occurred_at TIMESTAMPTZ,
    valid_from TIMESTAMPTZ,
    valid_until TIMESTAMPTZ,
    source VARCHAR(255),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_memory_category_name FOREIGN KEY (category) REFERENCES memory_category(name),
    CONSTRAINT chk_valid_date_range CHECK (
        valid_until IS NULL OR valid_from IS NULL OR valid_until >= valid_from
    )
);

CREATE TABLE IF NOT EXISTS memory_tag (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS memory_tag_map (
    memory_id UUID NOT NULL,
    tag_id BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (memory_id, tag_id),
    CONSTRAINT fk_memory_tag_map_memory FOREIGN KEY (memory_id) REFERENCES memory(id),
    CONSTRAINT fk_memory_tag_map_tag FOREIGN KEY (tag_id) REFERENCES memory_tag(id)
);

CREATE TABLE IF NOT EXISTS memory_settings (
    id BIGSERIAL PRIMARY KEY,
    setting_key VARCHAR(100) NOT NULL UNIQUE,
    setting_value VARCHAR(100) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_memory_scope ON memory(scope);
CREATE INDEX IF NOT EXISTS idx_memory_category ON memory(category);
CREATE INDEX IF NOT EXISTS idx_memory_active ON memory(active);
CREATE INDEX IF NOT EXISTS idx_memory_occurred_at ON memory(occurred_at);
CREATE INDEX IF NOT EXISTS idx_memory_created_at ON memory(created_at);
CREATE INDEX IF NOT EXISTS idx_memory_active_scope_created_at ON memory(active, scope, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_memory_metadata_gin ON memory USING GIN(metadata);

