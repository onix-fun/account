-- social schema
CREATE SCHEMA IF NOT EXISTS social;

CREATE TABLE social.subscriptions (
    id UUID PRIMARY KEY,
    subscriber_id UUID NOT NULL,
    subscribed_to_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    is_close_friend BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(subscriber_id, subscribed_to_id)
);

CREATE TABLE social.user_blocks (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    blocker_id UUID NOT NULL,
    blocked_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(blocker_id, blocked_id)
);

CREATE TABLE social.privacy_settings (
    user_id UUID PRIMARY KEY,
    is_private BOOLEAN NOT NULL DEFAULT false,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_subscriptions_subscriber ON social.subscriptions(subscriber_id);
CREATE INDEX idx_subscriptions_subscribed_to ON social.subscriptions(subscribed_to_id);
CREATE INDEX idx_user_blocks_blocker ON social.user_blocks(blocker_id);
CREATE INDEX idx_user_blocks_blocked ON social.user_blocks(blocked_id);
