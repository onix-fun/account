CREATE TABLE user_notifications (
    id UUID PRIMARY KEY,
    recipient_id UUID NOT NULL,
    type VARCHAR(50) NOT NULL,
    title TEXT NOT NULL,
    body TEXT NOT NULL,
    metadata_json JSONB NOT NULL DEFAULT '{}',
    is_read BOOLEAN NOT NULL DEFAULT false,
    actor_id UUID,
    entity_type VARCHAR(20),
    entity_id TEXT,
    source_event_id TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE notification_preferences (
    user_id UUID PRIMARY KEY,
    in_app_subscriptions BOOLEAN NOT NULL DEFAULT true,
    in_app_publications BOOLEAN NOT NULL DEFAULT true,
    in_app_author_mentions BOOLEAN NOT NULL DEFAULT true,
    in_app_post_comments BOOLEAN NOT NULL DEFAULT true,
    in_app_new_stories BOOLEAN NOT NULL DEFAULT true,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notifications_recipient ON user_notifications(recipient_id, created_at DESC);
CREATE UNIQUE INDEX idx_notifications_source_event ON user_notifications(source_event_id) WHERE source_event_id IS NOT NULL;
