CREATE TABLE pending_registrations (
    id UUID PRIMARY KEY DEFAULT uuidv7(),

    email TEXT NOT NULL,
    username TEXT NOT NULL,
    password_hash TEXT NOT NULL,

    first_name VARCHAR(100),
    last_name VARCHAR(100),

    code_hash TEXT NOT NULL,
    code_attempts INTEGER NOT NULL DEFAULT 0,
    code_created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uq_pending_registrations_email_lower
    ON pending_registrations (LOWER(email));

CREATE UNIQUE INDEX uq_pending_registrations_username_lower
    ON pending_registrations (LOWER(username));

CREATE UNIQUE INDEX uq_pending_registrations_code_hash
    ON pending_registrations (code_hash);

CREATE INDEX idx_pending_registrations_expires_at
    ON pending_registrations (expires_at);

CREATE TABLE rate_limit_counters (
    scope TEXT NOT NULL,
    key_hash TEXT NOT NULL,
    window_start TIMESTAMP WITH TIME ZONE NOT NULL,
    window_seconds INTEGER NOT NULL,
    count BIGINT NOT NULL DEFAULT 0,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (scope, key_hash, window_start)
);

CREATE INDEX idx_rate_limit_counters_expires_at
    ON rate_limit_counters (expires_at);

CREATE TABLE account_login_failures (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    attempts BIGINT NOT NULL DEFAULT 0,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_account_login_failures_expires_at
    ON account_login_failures (expires_at);
