ALTER TABLE users
    ADD COLUMN birth_date DATE,
    ADD COLUMN profile_metadata JSONB NOT NULL DEFAULT '{}';

ALTER TABLE social.privacy_settings
    ADD COLUMN field_visibility JSONB NOT NULL DEFAULT '{"bio":"public","birthday":"private","socialLinks":"public"}';

ALTER TABLE notification_preferences
    ADD COLUMN in_app_birthdays BOOLEAN NOT NULL DEFAULT true;

CREATE INDEX idx_users_birth_date_month_day
    ON users ((EXTRACT(MONTH FROM birth_date)), (EXTRACT(DAY FROM birth_date)))
    WHERE birth_date IS NOT NULL;
