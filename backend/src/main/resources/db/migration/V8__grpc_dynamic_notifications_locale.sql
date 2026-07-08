ALTER TABLE users
    ADD COLUMN preferred_locale VARCHAR(8) NOT NULL DEFAULT 'en';

ALTER TABLE pending_registrations
    ADD COLUMN preferred_locale VARCHAR(8) NOT NULL DEFAULT 'en';

CREATE TABLE notification_services (
    service_key TEXT PRIMARY KEY,
    name_i18n JSONB NOT NULL,
    description_i18n JSONB NOT NULL DEFAULT '{}',
    icon TEXT NOT NULL DEFAULT 'pi pi-bell',
    display_order INTEGER NOT NULL DEFAULT 1000,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE notification_types (
    service_key TEXT NOT NULL REFERENCES notification_services(service_key) ON DELETE CASCADE,
    type_key TEXT NOT NULL,
    name_i18n JSONB NOT NULL,
    description_i18n JSONB NOT NULL DEFAULT '{}',
    icon TEXT NOT NULL DEFAULT 'pi pi-bell',
    default_enabled BOOLEAN NOT NULL DEFAULT true,
    display_order INTEGER NOT NULL DEFAULT 1000,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (service_key, type_key)
);

CREATE TABLE user_notification_service_activations (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    service_key TEXT NOT NULL REFERENCES notification_services(service_key) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, service_key)
);

CREATE TABLE user_notification_preferences (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    service_key TEXT NOT NULL,
    type_key TEXT NOT NULL,
    enabled BOOLEAN NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, service_key, type_key),
    FOREIGN KEY (service_key, type_key) REFERENCES notification_types(service_key, type_key) ON DELETE CASCADE
);

ALTER TABLE user_notifications
    ADD COLUMN service_key TEXT NOT NULL DEFAULT 'account',
    ADD COLUMN type_key TEXT,
    ADD COLUMN title_i18n JSONB,
    ADD COLUMN body_i18n JSONB;

UPDATE user_notifications
SET service_key = CASE
        WHEN type IN ('post_published', 'story_published', 'author_mention', 'post_comment') THEN 'content'
        ELSE 'account'
    END,
    type_key = type,
    type = CASE
        WHEN type IN ('post_published', 'story_published', 'author_mention', 'post_comment') THEN 'content.' || type
        ELSE 'account.' || type
    END
WHERE type NOT LIKE '%.%';

UPDATE user_notifications
SET type_key = COALESCE(type_key, split_part(type, '.', 2));

ALTER TABLE user_notifications
    ALTER COLUMN type_key SET NOT NULL;

INSERT INTO notification_services (service_key, name_i18n, description_i18n, icon, display_order)
VALUES
    ('account', '{"ru":"Аккаунт","en":"Account"}'::jsonb, '{"ru":"Подписки, профиль и системные события аккаунта","en":"Follows, profile, and account system events"}'::jsonb, 'pi pi-user', 10),
    ('content', '{"ru":"Контент","en":"Content"}'::jsonb, '{"ru":"Публикации, истории, комментарии и упоминания","en":"Posts, stories, comments, and mentions"}'::jsonb, 'pi pi-send', 20)
ON CONFLICT (service_key) DO UPDATE SET
    name_i18n = EXCLUDED.name_i18n,
    description_i18n = EXCLUDED.description_i18n,
    icon = EXCLUDED.icon,
    display_order = EXCLUDED.display_order,
    active = true,
    updated_at = NOW();

INSERT INTO notification_types (service_key, type_key, name_i18n, description_i18n, icon, default_enabled, display_order)
VALUES
    ('account', 'subscription_request', '{"ru":"Подписки","en":"Subscriptions"}'::jsonb, '{"ru":"Запросы и изменения подписок","en":"Follow requests and changes"}'::jsonb, 'pi pi-user-plus', true, 10),
    ('account', 'subscription_accepted', '{"ru":"Новые подписчики","en":"New followers"}'::jsonb, '{"ru":"Когда запрос подписки принят или появился новый подписчик","en":"When a follow request is accepted or a new follower appears"}'::jsonb, 'pi pi-user-plus', true, 20),
    ('account', 'birthday_today', '{"ru":"Дни рождения","en":"Birthdays"}'::jsonb, '{"ru":"Когда у видимых подписок день рождения","en":"When visible following accounts have a birthday"}'::jsonb, 'pi pi-gift', true, 30),
    ('content', 'post_published', '{"ru":"Публикации","en":"Publications"}'::jsonb, '{"ru":"Новые публикации подписок","en":"New publications from following"}'::jsonb, 'pi pi-send', true, 10),
    ('content', 'story_published', '{"ru":"Истории","en":"Stories"}'::jsonb, '{"ru":"Новые истории подписок","en":"New stories from following"}'::jsonb, 'pi pi-bolt', true, 20),
    ('content', 'author_mention', '{"ru":"Упоминания автора","en":"Author mentions"}'::jsonb, '{"ru":"Когда вас добавляют как автора","en":"When you are added as an author"}'::jsonb, 'pi pi-at', true, 30),
    ('content', 'post_comment', '{"ru":"Комментарии","en":"Comments"}'::jsonb, '{"ru":"Комментарии к публикациям","en":"Comments on posts"}'::jsonb, 'pi pi-comments', true, 40)
ON CONFLICT (service_key, type_key) DO UPDATE SET
    name_i18n = EXCLUDED.name_i18n,
    description_i18n = EXCLUDED.description_i18n,
    icon = EXCLUDED.icon,
    default_enabled = EXCLUDED.default_enabled,
    display_order = EXCLUDED.display_order,
    active = true,
    updated_at = NOW();

INSERT INTO user_notification_service_activations (user_id, service_key)
SELECT user_id, 'account' FROM notification_preferences
UNION
SELECT user_id, 'content' FROM notification_preferences;

INSERT INTO user_notification_preferences (user_id, service_key, type_key, enabled)
SELECT user_id, 'account', 'subscription_request', in_app_subscriptions FROM notification_preferences
ON CONFLICT DO NOTHING;

INSERT INTO user_notification_preferences (user_id, service_key, type_key, enabled)
SELECT user_id, 'account', 'subscription_accepted', in_app_subscriptions FROM notification_preferences
ON CONFLICT DO NOTHING;

INSERT INTO user_notification_preferences (user_id, service_key, type_key, enabled)
SELECT user_id, 'account', 'birthday_today', in_app_birthdays FROM notification_preferences
ON CONFLICT DO NOTHING;

INSERT INTO user_notification_preferences (user_id, service_key, type_key, enabled)
SELECT user_id, 'content', 'post_published', in_app_publications FROM notification_preferences
ON CONFLICT DO NOTHING;

INSERT INTO user_notification_preferences (user_id, service_key, type_key, enabled)
SELECT user_id, 'content', 'story_published', in_app_new_stories FROM notification_preferences
ON CONFLICT DO NOTHING;

INSERT INTO user_notification_preferences (user_id, service_key, type_key, enabled)
SELECT user_id, 'content', 'author_mention', in_app_author_mentions FROM notification_preferences
ON CONFLICT DO NOTHING;

INSERT INTO user_notification_preferences (user_id, service_key, type_key, enabled)
SELECT user_id, 'content', 'post_comment', in_app_post_comments FROM notification_preferences
ON CONFLICT DO NOTHING;

CREATE INDEX idx_notification_types_service ON notification_types(service_key, display_order);
CREATE INDEX idx_user_notification_activations_user ON user_notification_service_activations(user_id);
CREATE INDEX idx_user_notification_preferences_user ON user_notification_preferences(user_id);
CREATE INDEX idx_notifications_recipient_service ON user_notifications(recipient_id, service_key, created_at DESC);
