CREATE TABLE users (
    id UUID PRIMARY KEY,

    email TEXT NOT NULL UNIQUE,
    username TEXT NOT NULL UNIQUE,

    password_hash TEXT NOT NULL,

    email_verified BOOLEAN NOT NULL DEFAULT FALSE,

    first_name VARCHAR(100),
    last_name VARCHAR(100),
    avatar_url TEXT,
    bio VARCHAR(500),

    role TEXT NOT NULL DEFAULT 'USER',
    status TEXT NOT NULL DEFAULT 'ACTIVE',

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE sessions (
    id UUID PRIMARY KEY,

    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    refresh_token_hash TEXT NOT NULL UNIQUE,

    device_id TEXT,
    user_agent TEXT,
    ip_address TEXT,

    expires_at TIMESTAMP NOT NULL,
    last_used_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at TIMESTAMP,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE verification_tokens (
    id UUID PRIMARY KEY,

    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    token_hash TEXT NOT NULL UNIQUE,

    purpose TEXT NOT NULL,

    expires_at TIMESTAMP NOT NULL,
    used_at TIMESTAMP,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE audit_logs (
    id UUID PRIMARY KEY,

    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    action TEXT NOT NULL,

    ip_address TEXT,
    user_agent TEXT,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_sessions_user_id ON sessions(user_id);
CREATE INDEX idx_sessions_refresh_token_hash ON sessions(refresh_token_hash);
CREATE INDEX idx_verification_tokens_token_hash ON verification_tokens(token_hash);
