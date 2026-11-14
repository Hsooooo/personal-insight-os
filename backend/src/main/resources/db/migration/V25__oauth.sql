-- OAuth 2.1 Authorization Server for MCP clients (public clients + PKCE S256)

-- Dynamically registered clients (RFC 7591). Client ID Metadata Document clients are not stored here.
CREATE TABLE oauth_clients (
    id                          BIGSERIAL PRIMARY KEY,
    client_id                   VARCHAR(255)  NOT NULL UNIQUE,
    client_name                 VARCHAR(255),
    redirect_uris               TEXT          NOT NULL, -- JSON array
    grant_types                 VARCHAR(255)  NOT NULL, -- space separated
    scope                       VARCHAR(255),
    token_endpoint_auth_method  VARCHAR(50)   NOT NULL DEFAULT 'none',
    registration_ip             VARCHAR(45),
    created_at                  TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

-- Pending /authorize requests waiting for user consent (short TTL)
CREATE TABLE oauth_authorization_requests (
    id              VARCHAR(64)   PRIMARY KEY,
    client_id       VARCHAR(2048) NOT NULL,
    client_name     VARCHAR(255),
    redirect_uri    VARCHAR(2048) NOT NULL,
    code_challenge  VARCHAR(128)  NOT NULL,
    scope           VARCHAR(255)  NOT NULL,
    state           VARCHAR(2048),
    resource        VARCHAR(2048) NOT NULL,
    expires_at      TIMESTAMPTZ   NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_oauth_authz_requests_expires_at ON oauth_authorization_requests(expires_at);

-- A user's authorization of a client; refresh tokens hang off a grant
CREATE TABLE oauth_grants (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    client_id     VARCHAR(2048) NOT NULL,
    client_name   VARCHAR(255),
    scope         VARCHAR(255)  NOT NULL,
    resource      VARCHAR(2048) NOT NULL,
    revoked_at    TIMESTAMPTZ,
    last_used_at  TIMESTAMPTZ,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_oauth_grants_user_id ON oauth_grants(user_id);

-- Authorization codes (stored as SHA-256 hex, single use)
CREATE TABLE oauth_authorization_codes (
    id              BIGSERIAL PRIMARY KEY,
    code_hash       VARCHAR(64)   NOT NULL UNIQUE,
    user_id         BIGINT        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    client_id       VARCHAR(2048) NOT NULL,
    client_name     VARCHAR(255),
    redirect_uri    VARCHAR(2048) NOT NULL,
    code_challenge  VARCHAR(128)  NOT NULL,
    scope           VARCHAR(255)  NOT NULL,
    resource        VARCHAR(2048) NOT NULL,
    grant_id        BIGINT        REFERENCES oauth_grants(id) ON DELETE CASCADE,
    expires_at      TIMESTAMPTZ   NOT NULL,
    used_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_oauth_codes_expires_at ON oauth_authorization_codes(expires_at);

-- Opaque refresh tokens (stored as SHA-256 hex). Rotated on every use; used_at marks rotated tokens for reuse detection.
CREATE TABLE oauth_refresh_tokens (
    id          BIGSERIAL PRIMARY KEY,
    token_hash  VARCHAR(64)  NOT NULL UNIQUE,
    grant_id    BIGINT       NOT NULL REFERENCES oauth_grants(id) ON DELETE CASCADE,
    expires_at  TIMESTAMPTZ  NOT NULL,
    used_at     TIMESTAMPTZ,
    revoked_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_oauth_refresh_tokens_grant_id ON oauth_refresh_tokens(grant_id);
CREATE INDEX idx_oauth_refresh_tokens_expires_at ON oauth_refresh_tokens(expires_at);
