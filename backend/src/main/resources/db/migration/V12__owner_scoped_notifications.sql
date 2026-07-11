ALTER TABLE user_notifications
    ADD COLUMN source_owner_type TEXT,
    ADD COLUMN source_owner_id UUID,
    ADD COLUMN target_owner_type TEXT,
    ADD COLUMN target_owner_id UUID;

ALTER TABLE notification_outbox
    ADD COLUMN actor_type TEXT NOT NULL DEFAULT 'USER';

CREATE TABLE user_owner_notification_preferences (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    owner_type TEXT NOT NULL,
    owner_id UUID NOT NULL,
    service_key TEXT NOT NULL,
    type_key TEXT NOT NULL,
    enabled BOOLEAN NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, owner_type, owner_id, service_key, type_key),
    CONSTRAINT chk_user_owner_notification_owner_type CHECK (owner_type IN ('USER', 'ORGANIZATION')),
    FOREIGN KEY (service_key, type_key) REFERENCES notification_types(service_key, type_key) ON DELETE CASCADE
);

INSERT INTO notification_types (service_key, type_key, name_i18n, description_i18n, icon, default_enabled, display_order)
VALUES (
    'account',
    'organization_invitation',
    '{"ru":"Приглашения в организации","en":"Organization invitations"}'::jsonb,
    '{"ru":"Когда вас приглашают в организацию","en":"When you are invited to an organization"}'::jsonb,
    'pi pi-building',
    true,
    25
)
ON CONFLICT (service_key, type_key) DO UPDATE SET
    name_i18n = EXCLUDED.name_i18n,
    description_i18n = EXCLUDED.description_i18n,
    icon = EXCLUDED.icon,
    default_enabled = EXCLUDED.default_enabled,
    display_order = EXCLUDED.display_order,
    active = true,
    updated_at = NOW();

CREATE INDEX idx_notifications_recipient_target_owner
    ON user_notifications(recipient_id, target_owner_type, target_owner_id, created_at DESC);

CREATE INDEX idx_notifications_recipient_source_owner
    ON user_notifications(recipient_id, source_owner_type, source_owner_id, created_at DESC);

CREATE INDEX idx_user_owner_notification_preferences_user_owner
    ON user_owner_notification_preferences(user_id, owner_type, owner_id);
