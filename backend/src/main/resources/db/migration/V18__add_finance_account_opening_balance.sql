ALTER TABLE finance_accounts
    ADD COLUMN opening_balance NUMERIC(14,2) NOT NULL DEFAULT 0,
    ADD COLUMN opening_balance_date DATE,
    ADD COLUMN opening_balance_memo TEXT;
