ALTER TABLE finance_transactions
    ADD COLUMN time_adjusted BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN time_adjusted_at TIMESTAMPTZ;

DROP INDEX IF EXISTS idx_finance_transactions_user_date;
CREATE INDEX idx_finance_transactions_user_date ON finance_transactions(user_id, transaction_at ASC, id ASC);
