CREATE TABLE notification_outbox (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    source_event_id TEXT NOT NULL UNIQUE,
    actor_id UUID NOT NULL,
    activity_type VARCHAR(50) NOT NULL,
    entity_type VARCHAR(50),
    entity_id TEXT,
    metadata_json JSONB NOT NULL DEFAULT '{}',
    status TEXT NOT NULL DEFAULT 'PENDING',
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMPTZ
);

CREATE INDEX idx_notification_outbox_pending ON notification_outbox(status, next_attempt_at, created_at);
CREATE INDEX idx_notification_outbox_actor ON notification_outbox(actor_id, created_at);
