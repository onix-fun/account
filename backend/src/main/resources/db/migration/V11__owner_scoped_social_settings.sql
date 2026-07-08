ALTER TABLE social.user_blocks
    ADD COLUMN IF NOT EXISTS blocker_type TEXT NOT NULL DEFAULT 'USER',
    ADD COLUMN IF NOT EXISTS blocked_type TEXT NOT NULL DEFAULT 'USER';

ALTER TABLE social.user_blocks
    DROP CONSTRAINT IF EXISTS user_blocks_blocker_id_blocked_id_key;

CREATE UNIQUE INDEX IF NOT EXISTS uq_user_blocks_owner_pair
    ON social.user_blocks(blocker_type, blocker_id, blocked_type, blocked_id);

CREATE INDEX IF NOT EXISTS idx_user_blocks_blocker_owner
    ON social.user_blocks(blocker_type, blocker_id);

CREATE INDEX IF NOT EXISTS idx_user_blocks_blocked_owner
    ON social.user_blocks(blocked_type, blocked_id);

ALTER TABLE social.privacy_settings
    ADD COLUMN IF NOT EXISTS owner_type TEXT NOT NULL DEFAULT 'USER';

ALTER TABLE social.privacy_settings
    DROP CONSTRAINT IF EXISTS privacy_settings_pkey;

ALTER TABLE social.privacy_settings
    ADD CONSTRAINT privacy_settings_pkey PRIMARY KEY (owner_type, user_id);
