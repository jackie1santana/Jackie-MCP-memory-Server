CREATE TABLE IF NOT EXISTS wholesale_real_estate (
    id BIGSERIAL PRIMARY KEY,
    lead_name VARCHAR(255),
    lead_phone VARCHAR(50),
    lead_email VARCHAR(255),
    property_address VARCHAR(255) NOT NULL,
    property_city VARCHAR(120),
    property_state VARCHAR(50),
    property_zip VARCHAR(20),
    asking_price NUMERIC(14, 2),
    arv NUMERIC(14, 2),
    estimated_rehab_cost NUMERIC(14, 2),
    max_allowable_offer NUMERIC(14, 2),
    offer_price NUMERIC(14, 2),
    status VARCHAR(50) NOT NULL DEFAULT 'new',
    notes TEXT,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_wholesale_price_non_negative CHECK (
        (asking_price IS NULL OR asking_price >= 0)
        AND (arv IS NULL OR arv >= 0)
        AND (estimated_rehab_cost IS NULL OR estimated_rehab_cost >= 0)
        AND (max_allowable_offer IS NULL OR max_allowable_offer >= 0)
        AND (offer_price IS NULL OR offer_price >= 0)
    )
);

CREATE INDEX IF NOT EXISTS idx_wholesale_real_estate_status ON wholesale_real_estate(status);
CREATE INDEX IF NOT EXISTS idx_wholesale_real_estate_active ON wholesale_real_estate(active);
CREATE INDEX IF NOT EXISTS idx_wholesale_real_estate_created_at ON wholesale_real_estate(created_at);
CREATE INDEX IF NOT EXISTS idx_wholesale_real_estate_address ON wholesale_real_estate(property_address);
CREATE INDEX IF NOT EXISTS idx_wholesale_real_estate_metadata_gin ON wholesale_real_estate USING GIN(metadata);

