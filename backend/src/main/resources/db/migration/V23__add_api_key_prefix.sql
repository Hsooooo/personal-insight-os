ALTER TABLE api_keys ADD COLUMN IF NOT EXISTS key_prefix VARCHAR(32);

CREATE INDEX IF NOT EXISTS idx_api_keys_key_prefix ON api_keys(key_prefix);
