CREATE TABLE organizations (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    org_name TEXT NOT NULL,
    display_name TEXT NOT NULL,
    bio TEXT,
    social_links JSONB NOT NULL DEFAULT '[]'::jsonb,
    avatar_url TEXT,
    status TEXT NOT NULL DEFAULT 'ACTIVE',
    created_by_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uq_organizations_org_name_lower ON organizations (LOWER(org_name));
CREATE INDEX idx_organizations_created_by ON organizations(created_by_user_id);

CREATE TABLE organization_members (
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (organization_id, user_id),
    CONSTRAINT chk_organization_member_role CHECK (role IN ('OWNER', 'CONTRIBUTOR'))
);

CREATE INDEX idx_organization_members_user ON organization_members(user_id);

CREATE TABLE organization_invitations (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    invited_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    invited_by_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    role TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ,
    CONSTRAINT chk_organization_invitation_role CHECK (role IN ('OWNER', 'CONTRIBUTOR')),
    CONSTRAINT chk_organization_invitation_status CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED', 'EXPIRED'))
);

CREATE UNIQUE INDEX uq_organization_pending_invitation
    ON organization_invitations(organization_id, invited_user_id)
    WHERE status = 'PENDING';
CREATE INDEX idx_organization_invitations_user_status ON organization_invitations(invited_user_id, status, created_at DESC);

ALTER TABLE sessions
    ADD COLUMN active_owner_type TEXT NOT NULL DEFAULT 'USER',
    ADD COLUMN active_owner_id UUID;

UPDATE sessions SET active_owner_id = user_id WHERE active_owner_id IS NULL;

ALTER TABLE sessions
    ALTER COLUMN active_owner_id SET NOT NULL,
    ADD CONSTRAINT chk_sessions_active_owner_type CHECK (active_owner_type IN ('USER', 'ORGANIZATION'));

ALTER TABLE social.subscriptions
    ADD COLUMN subscriber_type TEXT NOT NULL DEFAULT 'USER',
    ADD COLUMN subscribed_to_type TEXT NOT NULL DEFAULT 'USER';

ALTER TABLE social.subscriptions
    DROP CONSTRAINT IF EXISTS subscriptions_subscriber_id_subscribed_to_id_key;

CREATE UNIQUE INDEX uq_social_subscriptions_owner_pair
    ON social.subscriptions(subscriber_type, subscriber_id, subscribed_to_type, subscribed_to_id);

CREATE INDEX idx_subscriptions_subscriber_owner
    ON social.subscriptions(subscriber_type, subscriber_id);

CREATE INDEX idx_subscriptions_subscribed_to_owner
    ON social.subscriptions(subscribed_to_type, subscribed_to_id);
