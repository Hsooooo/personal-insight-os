-- finance_cycles
CREATE TABLE finance_cycles (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    label VARCHAR(120) NOT NULL,
    salary_date DATE NOT NULL,
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ,
    status VARCHAR(30) NOT NULL DEFAULT 'OPEN',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, starts_at)
);

-- recurring_bill_templates
CREATE TABLE recurring_bill_templates (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(120) NOT NULL,
    provider VARCHAR(80),
    category VARCHAR(80),
    memo TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, name)
);

-- recurring_bill_template_versions
CREATE TABLE recurring_bill_template_versions (
    id BIGSERIAL PRIMARY KEY,
    template_id BIGINT NOT NULL REFERENCES recurring_bill_templates(id) ON DELETE CASCADE,
    effective_cycle_id BIGINT REFERENCES finance_cycles(id) ON DELETE SET NULL,
    version INTEGER NOT NULL,
    expected_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (template_id, version)
);

-- recurring_bill_template_items
CREATE TABLE recurring_bill_template_items (
    id BIGSERIAL PRIMARY KEY,
    version_id BIGINT NOT NULL REFERENCES recurring_bill_template_versions(id) ON DELETE CASCADE,
    item_name VARCHAR(160) NOT NULL,
    amount NUMERIC(14,2) NOT NULL DEFAULT 0,
    item_type VARCHAR(40) NOT NULL DEFAULT 'BASE',
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- finance_transactions
CREATE TABLE finance_transactions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    cycle_id BIGINT REFERENCES finance_cycles(id) ON DELETE SET NULL,
    linked_template_version_id BIGINT REFERENCES recurring_bill_template_versions(id) ON DELETE SET NULL,
    transaction_at TIMESTAMPTZ NOT NULL,
    transaction_date DATE NOT NULL,
    asset VARCHAR(120),
    category VARCHAR(120),
    subcategory VARCHAR(120),
    description VARCHAR(255),
    amount NUMERIC(14,2) NOT NULL,
    flow_type VARCHAR(40) NOT NULL,
    memo TEXT,
    currency VARCHAR(10) NOT NULL DEFAULT 'KRW',
    source_fingerprint VARCHAR(64) NOT NULL,
    source_row JSONB NOT NULL DEFAULT '{}',
    cashflow_included BOOLEAN NOT NULL DEFAULT TRUE,
    spending_included BOOLEAN NOT NULL DEFAULT TRUE,
    payment_method VARCHAR(80),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, source_fingerprint)
);

CREATE INDEX idx_finance_cycles_user_start ON finance_cycles(user_id, starts_at DESC);
CREATE INDEX idx_finance_transactions_user_date ON finance_transactions(user_id, transaction_at DESC);
CREATE INDEX idx_finance_transactions_cycle ON finance_transactions(cycle_id);
CREATE INDEX idx_recurring_templates_user ON recurring_bill_templates(user_id);
