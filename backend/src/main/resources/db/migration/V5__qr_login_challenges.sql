CREATE TABLE qr_login_challenges (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    scan_token_hash TEXT NOT NULL UNIQUE,
    manual_code_hash TEXT NOT NULL UNIQUE,

    status TEXT NOT NULL DEFAULT 'PENDING',
    attempts INTEGER NOT NULL DEFAULT 0,

    consumer_device_id TEXT,
    consumer_user_agent TEXT,
    consumer_ip_address TEXT,

    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    consumed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_qr_login_challenges_user_status
    ON qr_login_challenges(user_id, status, expires_at);

CREATE INDEX idx_qr_login_challenges_pending
    ON qr_login_challenges(status, expires_at);
