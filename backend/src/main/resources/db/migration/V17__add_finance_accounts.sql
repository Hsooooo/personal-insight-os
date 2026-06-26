-- finance_accounts
CREATE TABLE finance_accounts (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(120) NOT NULL,
    account_type VARCHAR(40) NOT NULL DEFAULT 'OTHER',
    role VARCHAR(40) NOT NULL DEFAULT 'OTHER',
    institution VARCHAR(120),
    memo TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, name)
);

-- finance_account_aliases
CREATE TABLE finance_account_aliases (
    id BIGSERIAL PRIMARY KEY,
    account_id BIGINT NOT NULL REFERENCES finance_accounts(id) ON DELETE CASCADE,
    alias_name VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (account_id, alias_name)
);

ALTER TABLE finance_transactions
    ADD COLUMN account_id BIGINT REFERENCES finance_accounts(id) ON DELETE SET NULL;

CREATE INDEX idx_finance_accounts_user ON finance_accounts(user_id);
CREATE INDEX idx_finance_account_aliases_account ON finance_account_aliases(account_id);
CREATE INDEX idx_finance_transactions_account ON finance_transactions(account_id);
