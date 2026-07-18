-- Generated from the final pre-production schema for account.
-- Data is intentionally reset; historical compatibility DDL is forbidden here.

CREATE SCHEMA IF NOT EXISTS account;

CREATE TABLE account.email_outbox (
    id uuid NOT NULL,
    event_type text NOT NULL,
    payload text NOT NULL,
    status text DEFAULT 'PENDING'::text NOT NULL,
    attempts integer DEFAULT 0 NOT NULL,
    next_attempt_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    last_error text,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    sent_at timestamp with time zone,
    CONSTRAINT email_outbox_attempts_not_null NOT NULL attempts,
    CONSTRAINT email_outbox_created_at_not_null NOT NULL created_at,
    CONSTRAINT email_outbox_event_type_not_null NOT NULL event_type,
    CONSTRAINT email_outbox_expires_at_not_null NOT NULL expires_at,
    CONSTRAINT email_outbox_id_not_null NOT NULL id,
    CONSTRAINT email_outbox_next_attempt_at_not_null NOT NULL next_attempt_at,
    CONSTRAINT email_outbox_payload_not_null NOT NULL payload,
    CONSTRAINT email_outbox_pkey PRIMARY KEY (id),
    CONSTRAINT email_outbox_status_not_null NOT NULL status
);

CREATE TABLE account.notification_outbox (
    id uuid NOT NULL,
    source_event_id text NOT NULL,
    actor_id uuid NOT NULL,
    activity_type character varying(50) NOT NULL,
    entity_type character varying(50),
    entity_id text,
    metadata_json jsonb DEFAULT '{}'::jsonb NOT NULL,
    status text DEFAULT 'PENDING'::text NOT NULL,
    attempts integer DEFAULT 0 NOT NULL,
    next_attempt_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    last_error text,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    processed_at timestamp with time zone,
    actor_type text DEFAULT 'USER'::text NOT NULL,
    CONSTRAINT notification_outbox_activity_type_not_null NOT NULL activity_type,
    CONSTRAINT notification_outbox_actor_id_not_null NOT NULL actor_id,
    CONSTRAINT notification_outbox_actor_type_not_null NOT NULL actor_type,
    CONSTRAINT notification_outbox_attempts_not_null NOT NULL attempts,
    CONSTRAINT notification_outbox_created_at_not_null NOT NULL created_at,
    CONSTRAINT notification_outbox_id_not_null NOT NULL id,
    CONSTRAINT notification_outbox_metadata_json_not_null NOT NULL metadata_json,
    CONSTRAINT notification_outbox_next_attempt_at_not_null NOT NULL next_attempt_at,
    CONSTRAINT notification_outbox_pkey PRIMARY KEY (id),
    CONSTRAINT notification_outbox_source_event_id_key UNIQUE (source_event_id),
    CONSTRAINT notification_outbox_source_event_id_not_null NOT NULL source_event_id,
    CONSTRAINT notification_outbox_status_not_null NOT NULL status
);

CREATE TABLE account.notification_preferences (
    user_id uuid NOT NULL,
    in_app_subscriptions boolean DEFAULT true NOT NULL,
    in_app_publications boolean DEFAULT true NOT NULL,
    in_app_author_mentions boolean DEFAULT true NOT NULL,
    in_app_post_comments boolean DEFAULT true NOT NULL,
    in_app_new_stories boolean DEFAULT true NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    in_app_birthdays boolean DEFAULT true NOT NULL,
    CONSTRAINT notification_preferences_in_app_author_mentions_not_null NOT NULL in_app_author_mentions,
    CONSTRAINT notification_preferences_in_app_birthdays_not_null NOT NULL in_app_birthdays,
    CONSTRAINT notification_preferences_in_app_new_stories_not_null NOT NULL in_app_new_stories,
    CONSTRAINT notification_preferences_in_app_post_comments_not_null NOT NULL in_app_post_comments,
    CONSTRAINT notification_preferences_in_app_publications_not_null NOT NULL in_app_publications,
    CONSTRAINT notification_preferences_in_app_subscriptions_not_null NOT NULL in_app_subscriptions,
    CONSTRAINT notification_preferences_pkey PRIMARY KEY (user_id),
    CONSTRAINT notification_preferences_updated_at_not_null NOT NULL updated_at,
    CONSTRAINT notification_preferences_user_id_not_null NOT NULL user_id
);

CREATE TABLE account.notification_services (
    service_key text NOT NULL,
    name_i18n jsonb NOT NULL,
    description_i18n jsonb DEFAULT '{}'::jsonb NOT NULL,
    icon text DEFAULT 'pi pi-bell'::text NOT NULL,
    display_order integer DEFAULT 1000 NOT NULL,
    active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT notification_services_active_not_null NOT NULL active,
    CONSTRAINT notification_services_created_at_not_null NOT NULL created_at,
    CONSTRAINT notification_services_description_i18n_not_null NOT NULL description_i18n,
    CONSTRAINT notification_services_display_order_not_null NOT NULL display_order,
    CONSTRAINT notification_services_icon_not_null NOT NULL icon,
    CONSTRAINT notification_services_name_i18n_not_null NOT NULL name_i18n,
    CONSTRAINT notification_services_pkey PRIMARY KEY (service_key),
    CONSTRAINT notification_services_service_key_not_null NOT NULL service_key,
    CONSTRAINT notification_services_updated_at_not_null NOT NULL updated_at
);

CREATE TABLE account.notification_types (
    service_key text NOT NULL,
    type_key text NOT NULL,
    name_i18n jsonb NOT NULL,
    description_i18n jsonb DEFAULT '{}'::jsonb NOT NULL,
    icon text DEFAULT 'pi pi-bell'::text NOT NULL,
    default_enabled boolean DEFAULT true NOT NULL,
    display_order integer DEFAULT 1000 NOT NULL,
    active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT notification_types_active_not_null NOT NULL active,
    CONSTRAINT notification_types_created_at_not_null NOT NULL created_at,
    CONSTRAINT notification_types_default_enabled_not_null NOT NULL default_enabled,
    CONSTRAINT notification_types_description_i18n_not_null NOT NULL description_i18n,
    CONSTRAINT notification_types_display_order_not_null NOT NULL display_order,
    CONSTRAINT notification_types_icon_not_null NOT NULL icon,
    CONSTRAINT notification_types_name_i18n_not_null NOT NULL name_i18n,
    CONSTRAINT notification_types_pkey PRIMARY KEY (service_key, type_key),
    CONSTRAINT notification_types_service_key_fkey FOREIGN KEY (service_key) REFERENCES account.notification_services(service_key) ON DELETE CASCADE,
    CONSTRAINT notification_types_service_key_not_null NOT NULL service_key,
    CONSTRAINT notification_types_type_key_not_null NOT NULL type_key,
    CONSTRAINT notification_types_updated_at_not_null NOT NULL updated_at
);

CREATE TABLE account.pending_registrations (
    id uuid NOT NULL,
    email text NOT NULL,
    username text NOT NULL,
    password_hash text NOT NULL,
    first_name character varying(100),
    last_name character varying(100),
    code_hash text NOT NULL,
    code_attempts integer DEFAULT 0 NOT NULL,
    code_created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    preferred_locale character varying(8) DEFAULT 'en'::character varying NOT NULL,
    CONSTRAINT pending_registrations_code_attempts_not_null NOT NULL code_attempts,
    CONSTRAINT pending_registrations_code_created_at_not_null NOT NULL code_created_at,
    CONSTRAINT pending_registrations_code_hash_not_null NOT NULL code_hash,
    CONSTRAINT pending_registrations_created_at_not_null NOT NULL created_at,
    CONSTRAINT pending_registrations_email_not_null NOT NULL email,
    CONSTRAINT pending_registrations_expires_at_not_null NOT NULL expires_at,
    CONSTRAINT pending_registrations_id_not_null NOT NULL id,
    CONSTRAINT pending_registrations_password_hash_not_null NOT NULL password_hash,
    CONSTRAINT pending_registrations_pkey PRIMARY KEY (id),
    CONSTRAINT pending_registrations_preferred_locale_not_null NOT NULL preferred_locale,
    CONSTRAINT pending_registrations_updated_at_not_null NOT NULL updated_at,
    CONSTRAINT pending_registrations_username_not_null NOT NULL username
);

CREATE TABLE account.privacy_settings (
    user_id uuid NOT NULL,
    is_private boolean DEFAULT false NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    field_visibility jsonb DEFAULT '{"bio": "public", "birthday": "private", "socialLinks": "public"}'::jsonb NOT NULL,
    owner_type text DEFAULT 'USER'::text NOT NULL,
    CONSTRAINT privacy_settings_field_visibility_not_null NOT NULL field_visibility,
    CONSTRAINT privacy_settings_is_private_not_null NOT NULL is_private,
    CONSTRAINT privacy_settings_owner_type_not_null NOT NULL owner_type,
    CONSTRAINT privacy_settings_pkey PRIMARY KEY (owner_type, user_id),
    CONSTRAINT privacy_settings_updated_at_not_null NOT NULL updated_at,
    CONSTRAINT privacy_settings_user_id_not_null NOT NULL user_id
);

CREATE TABLE account.rate_limit_counters (
    scope text NOT NULL,
    key_hash text NOT NULL,
    window_start timestamp with time zone NOT NULL,
    window_seconds integer NOT NULL,
    count bigint DEFAULT 0 NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT rate_limit_counters_count_not_null NOT NULL count,
    CONSTRAINT rate_limit_counters_expires_at_not_null NOT NULL expires_at,
    CONSTRAINT rate_limit_counters_key_hash_not_null NOT NULL key_hash,
    CONSTRAINT rate_limit_counters_pkey PRIMARY KEY (scope, key_hash, window_start),
    CONSTRAINT rate_limit_counters_scope_not_null NOT NULL scope,
    CONSTRAINT rate_limit_counters_updated_at_not_null NOT NULL updated_at,
    CONSTRAINT rate_limit_counters_window_seconds_not_null NOT NULL window_seconds,
    CONSTRAINT rate_limit_counters_window_start_not_null NOT NULL window_start
);

CREATE TABLE account.subscriptions (
    id uuid NOT NULL,
    subscriber_id uuid NOT NULL,
    subscribed_to_id uuid NOT NULL,
    status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    is_close_friend boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    subscriber_type text DEFAULT 'USER'::text NOT NULL,
    subscribed_to_type text DEFAULT 'USER'::text NOT NULL,
    CONSTRAINT subscriptions_created_at_not_null NOT NULL created_at,
    CONSTRAINT subscriptions_id_not_null NOT NULL id,
    CONSTRAINT subscriptions_is_close_friend_not_null NOT NULL is_close_friend,
    CONSTRAINT subscriptions_pkey PRIMARY KEY (id),
    CONSTRAINT subscriptions_status_not_null NOT NULL status,
    CONSTRAINT subscriptions_subscribed_to_id_not_null NOT NULL subscribed_to_id,
    CONSTRAINT subscriptions_subscribed_to_type_not_null NOT NULL subscribed_to_type,
    CONSTRAINT subscriptions_subscriber_id_not_null NOT NULL subscriber_id,
    CONSTRAINT subscriptions_subscriber_type_not_null NOT NULL subscriber_type,
    CONSTRAINT subscriptions_updated_at_not_null NOT NULL updated_at
);

CREATE TABLE account.user_blocks (
    id uuid NOT NULL,
    blocker_id uuid NOT NULL,
    blocked_id uuid NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    blocker_type text DEFAULT 'USER'::text NOT NULL,
    blocked_type text DEFAULT 'USER'::text NOT NULL,
    CONSTRAINT user_blocks_blocked_id_not_null NOT NULL blocked_id,
    CONSTRAINT user_blocks_blocked_type_not_null NOT NULL blocked_type,
    CONSTRAINT user_blocks_blocker_id_not_null NOT NULL blocker_id,
    CONSTRAINT user_blocks_blocker_type_not_null NOT NULL blocker_type,
    CONSTRAINT user_blocks_created_at_not_null NOT NULL created_at,
    CONSTRAINT user_blocks_id_not_null NOT NULL id,
    CONSTRAINT user_blocks_pkey PRIMARY KEY (id)
);

CREATE TABLE account.user_notifications (
    id uuid NOT NULL,
    recipient_id uuid NOT NULL,
    type character varying(50) NOT NULL,
    title text NOT NULL,
    body text NOT NULL,
    metadata_json jsonb DEFAULT '{}'::jsonb NOT NULL,
    is_read boolean DEFAULT false NOT NULL,
    actor_id uuid,
    entity_type character varying(20),
    entity_id text,
    source_event_id text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    service_key text DEFAULT 'account'::text NOT NULL,
    type_key text NOT NULL,
    title_i18n jsonb,
    body_i18n jsonb,
    source_owner_type text,
    source_owner_id uuid,
    target_owner_type text,
    target_owner_id uuid,
    CONSTRAINT user_notifications_body_not_null NOT NULL body,
    CONSTRAINT user_notifications_created_at_not_null NOT NULL created_at,
    CONSTRAINT user_notifications_id_not_null NOT NULL id,
    CONSTRAINT user_notifications_is_read_not_null NOT NULL is_read,
    CONSTRAINT user_notifications_metadata_json_not_null NOT NULL metadata_json,
    CONSTRAINT user_notifications_pkey PRIMARY KEY (id),
    CONSTRAINT user_notifications_recipient_id_not_null NOT NULL recipient_id,
    CONSTRAINT user_notifications_service_key_not_null NOT NULL service_key,
    CONSTRAINT user_notifications_title_not_null NOT NULL title,
    CONSTRAINT user_notifications_type_key_not_null NOT NULL type_key,
    CONSTRAINT user_notifications_type_not_null NOT NULL type
);

CREATE TABLE account.users (
    id uuid NOT NULL,
    email text NOT NULL,
    username text NOT NULL,
    password_hash text NOT NULL,
    email_verified boolean DEFAULT false NOT NULL,
    first_name character varying(100),
    last_name character varying(100),
    role text DEFAULT 'USER'::text NOT NULL,
    status text DEFAULT 'ACTIVE'::text NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    birth_date date,
    preferred_locale character varying(8) DEFAULT 'en'::character varying NOT NULL,
    CONSTRAINT users_created_at_not_null NOT NULL created_at,
    CONSTRAINT users_email_not_null NOT NULL email,
    CONSTRAINT users_email_verified_not_null NOT NULL email_verified,
    CONSTRAINT users_id_not_null NOT NULL id,
    CONSTRAINT users_password_hash_not_null NOT NULL password_hash,
    CONSTRAINT users_pkey PRIMARY KEY (id),
    CONSTRAINT users_preferred_locale_not_null NOT NULL preferred_locale,
    CONSTRAINT users_role_not_null NOT NULL role,
    CONSTRAINT users_status_not_null NOT NULL status,
    CONSTRAINT users_updated_at_not_null NOT NULL updated_at,
    CONSTRAINT users_username_not_null NOT NULL username
);

CREATE TABLE account.account_login_failures (
    user_id uuid NOT NULL,
    attempts bigint DEFAULT 0 NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT account_login_failures_attempts_not_null NOT NULL attempts,
    CONSTRAINT account_login_failures_expires_at_not_null NOT NULL expires_at,
    CONSTRAINT account_login_failures_pkey PRIMARY KEY (user_id),
    CONSTRAINT account_login_failures_updated_at_not_null NOT NULL updated_at,
    CONSTRAINT account_login_failures_user_id_fkey FOREIGN KEY (user_id) REFERENCES account.users(id) ON DELETE CASCADE,
    CONSTRAINT account_login_failures_user_id_not_null NOT NULL user_id
);

CREATE TABLE account.audit_logs (
    id uuid NOT NULL,
    user_id uuid,
    action text NOT NULL,
    result text,
    request_id text,
    error_code text,
    ip_address text,
    user_agent text,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT audit_logs_action_not_null NOT NULL action,
    CONSTRAINT audit_logs_created_at_not_null NOT NULL created_at,
    CONSTRAINT audit_logs_id_not_null NOT NULL id,
    CONSTRAINT audit_logs_pkey PRIMARY KEY (id),
    CONSTRAINT audit_logs_user_id_fkey FOREIGN KEY (user_id) REFERENCES account.users(id) ON DELETE SET NULL
);

CREATE TABLE account.organizations (
    id uuid NOT NULL,
    org_name text NOT NULL,
    status text DEFAULT 'ACTIVE'::text NOT NULL,
    created_by_user_id uuid NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT organizations_created_at_not_null NOT NULL created_at,
    CONSTRAINT organizations_created_by_user_id_fkey FOREIGN KEY (created_by_user_id) REFERENCES account.users(id) ON DELETE RESTRICT,
    CONSTRAINT organizations_created_by_user_id_not_null NOT NULL created_by_user_id,
    CONSTRAINT organizations_id_not_null NOT NULL id,
    CONSTRAINT organizations_org_name_not_null NOT NULL org_name,
    CONSTRAINT organizations_pkey PRIMARY KEY (id),
    CONSTRAINT organizations_status_not_null NOT NULL status,
    CONSTRAINT organizations_updated_at_not_null NOT NULL updated_at
);

CREATE TABLE account.organization_invitations (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    invited_user_id uuid NOT NULL,
    invited_by_user_id uuid NOT NULL,
    role text NOT NULL,
    status text DEFAULT 'PENDING'::text NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    expires_at timestamp with time zone,
    CONSTRAINT chk_organization_invitation_role CHECK (role = ANY (ARRAY['OWNER'::text, 'CONTRIBUTOR'::text])),
    CONSTRAINT chk_organization_invitation_status CHECK (status = ANY (ARRAY['PENDING'::text, 'ACCEPTED'::text, 'DECLINED'::text, 'EXPIRED'::text])),
    CONSTRAINT organization_invitations_created_at_not_null NOT NULL created_at,
    CONSTRAINT organization_invitations_id_not_null NOT NULL id,
    CONSTRAINT organization_invitations_invited_by_user_id_fkey FOREIGN KEY (invited_by_user_id) REFERENCES account.users(id) ON DELETE RESTRICT,
    CONSTRAINT organization_invitations_invited_by_user_id_not_null NOT NULL invited_by_user_id,
    CONSTRAINT organization_invitations_invited_user_id_fkey FOREIGN KEY (invited_user_id) REFERENCES account.users(id) ON DELETE CASCADE,
    CONSTRAINT organization_invitations_invited_user_id_not_null NOT NULL invited_user_id,
    CONSTRAINT organization_invitations_organization_id_fkey FOREIGN KEY (organization_id) REFERENCES account.organizations(id) ON DELETE CASCADE,
    CONSTRAINT organization_invitations_organization_id_not_null NOT NULL organization_id,
    CONSTRAINT organization_invitations_pkey PRIMARY KEY (id),
    CONSTRAINT organization_invitations_role_not_null NOT NULL role,
    CONSTRAINT organization_invitations_status_not_null NOT NULL status,
    CONSTRAINT organization_invitations_updated_at_not_null NOT NULL updated_at
);

CREATE TABLE account.organization_members (
    organization_id uuid NOT NULL,
    user_id uuid NOT NULL,
    role text NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT chk_organization_member_role CHECK (role = ANY (ARRAY['OWNER'::text, 'CONTRIBUTOR'::text])),
    CONSTRAINT organization_members_created_at_not_null NOT NULL created_at,
    CONSTRAINT organization_members_organization_id_fkey FOREIGN KEY (organization_id) REFERENCES account.organizations(id) ON DELETE CASCADE,
    CONSTRAINT organization_members_organization_id_not_null NOT NULL organization_id,
    CONSTRAINT organization_members_pkey PRIMARY KEY (organization_id, user_id),
    CONSTRAINT organization_members_role_not_null NOT NULL role,
    CONSTRAINT organization_members_updated_at_not_null NOT NULL updated_at,
    CONSTRAINT organization_members_user_id_fkey FOREIGN KEY (user_id) REFERENCES account.users(id) ON DELETE CASCADE,
    CONSTRAINT organization_members_user_id_not_null NOT NULL user_id
);

CREATE TABLE account.pending_email_changes (
    user_id uuid NOT NULL,
    new_email text NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    CONSTRAINT pending_email_changes_created_at_not_null NOT NULL created_at,
    CONSTRAINT pending_email_changes_expires_at_not_null NOT NULL expires_at,
    CONSTRAINT pending_email_changes_new_email_not_null NOT NULL new_email,
    CONSTRAINT pending_email_changes_pkey PRIMARY KEY (user_id),
    CONSTRAINT pending_email_changes_user_id_fkey FOREIGN KEY (user_id) REFERENCES account.users(id) ON DELETE CASCADE,
    CONSTRAINT pending_email_changes_user_id_not_null NOT NULL user_id
);

CREATE TABLE account.qr_login_challenges (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    scan_token_hash text NOT NULL,
    manual_code_hash text NOT NULL,
    status text DEFAULT 'PENDING'::text NOT NULL,
    attempts integer DEFAULT 0 NOT NULL,
    consumer_device_id text,
    consumer_user_agent text,
    consumer_ip_address text,
    expires_at timestamp with time zone NOT NULL,
    consumed_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT qr_login_challenges_attempts_not_null NOT NULL attempts,
    CONSTRAINT qr_login_challenges_created_at_not_null NOT NULL created_at,
    CONSTRAINT qr_login_challenges_expires_at_not_null NOT NULL expires_at,
    CONSTRAINT qr_login_challenges_id_not_null NOT NULL id,
    CONSTRAINT qr_login_challenges_manual_code_hash_key UNIQUE (manual_code_hash),
    CONSTRAINT qr_login_challenges_manual_code_hash_not_null NOT NULL manual_code_hash,
    CONSTRAINT qr_login_challenges_pkey PRIMARY KEY (id),
    CONSTRAINT qr_login_challenges_scan_token_hash_key UNIQUE (scan_token_hash),
    CONSTRAINT qr_login_challenges_scan_token_hash_not_null NOT NULL scan_token_hash,
    CONSTRAINT qr_login_challenges_status_not_null NOT NULL status,
    CONSTRAINT qr_login_challenges_updated_at_not_null NOT NULL updated_at,
    CONSTRAINT qr_login_challenges_user_id_fkey FOREIGN KEY (user_id) REFERENCES account.users(id) ON DELETE CASCADE,
    CONSTRAINT qr_login_challenges_user_id_not_null NOT NULL user_id
);

CREATE TABLE account.sessions (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    refresh_token_hash text NOT NULL,
    previous_refresh_token_hash text,
    refresh_token_rotated_at timestamp with time zone,
    device_id text,
    user_agent text,
    ip_address text,
    expires_at timestamp with time zone NOT NULL,
    last_used_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    revoked_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    active_owner_type text DEFAULT 'USER'::text NOT NULL,
    active_owner_id uuid NOT NULL,
    CONSTRAINT chk_sessions_active_owner_type CHECK (active_owner_type = ANY (ARRAY['USER'::text, 'ORGANIZATION'::text])),
    CONSTRAINT sessions_active_owner_id_not_null NOT NULL active_owner_id,
    CONSTRAINT sessions_active_owner_type_not_null NOT NULL active_owner_type,
    CONSTRAINT sessions_created_at_not_null NOT NULL created_at,
    CONSTRAINT sessions_expires_at_not_null NOT NULL expires_at,
    CONSTRAINT sessions_id_not_null NOT NULL id,
    CONSTRAINT sessions_last_used_at_not_null NOT NULL last_used_at,
    CONSTRAINT sessions_pkey PRIMARY KEY (id),
    CONSTRAINT sessions_refresh_token_hash_key UNIQUE (refresh_token_hash),
    CONSTRAINT sessions_refresh_token_hash_not_null NOT NULL refresh_token_hash,
    CONSTRAINT sessions_updated_at_not_null NOT NULL updated_at,
    CONSTRAINT sessions_user_id_fkey FOREIGN KEY (user_id) REFERENCES account.users(id) ON DELETE CASCADE,
    CONSTRAINT sessions_user_id_not_null NOT NULL user_id
);

CREATE TABLE account.user_notification_preferences (
    user_id uuid NOT NULL,
    service_key text NOT NULL,
    type_key text NOT NULL,
    enabled boolean NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT user_notification_preferences_enabled_not_null NOT NULL enabled,
    CONSTRAINT user_notification_preferences_pkey PRIMARY KEY (user_id, service_key, type_key),
    CONSTRAINT user_notification_preferences_service_key_not_null NOT NULL service_key,
    CONSTRAINT user_notification_preferences_service_key_type_key_fkey FOREIGN KEY (service_key, type_key) REFERENCES account.notification_types(service_key, type_key) ON DELETE CASCADE,
    CONSTRAINT user_notification_preferences_type_key_not_null NOT NULL type_key,
    CONSTRAINT user_notification_preferences_updated_at_not_null NOT NULL updated_at,
    CONSTRAINT user_notification_preferences_user_id_fkey FOREIGN KEY (user_id) REFERENCES account.users(id) ON DELETE CASCADE,
    CONSTRAINT user_notification_preferences_user_id_not_null NOT NULL user_id
);

CREATE TABLE account.user_notification_service_activations (
    user_id uuid NOT NULL,
    service_key text NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT user_notification_service_activations_created_at_not_null NOT NULL created_at,
    CONSTRAINT user_notification_service_activations_pkey PRIMARY KEY (user_id, service_key),
    CONSTRAINT user_notification_service_activations_service_key_fkey FOREIGN KEY (service_key) REFERENCES account.notification_services(service_key) ON DELETE CASCADE,
    CONSTRAINT user_notification_service_activations_service_key_not_null NOT NULL service_key,
    CONSTRAINT user_notification_service_activations_user_id_fkey FOREIGN KEY (user_id) REFERENCES account.users(id) ON DELETE CASCADE,
    CONSTRAINT user_notification_service_activations_user_id_not_null NOT NULL user_id
);

CREATE TABLE account.user_owner_notification_preferences (
    user_id uuid NOT NULL,
    owner_type text NOT NULL,
    owner_id uuid NOT NULL,
    service_key text NOT NULL,
    type_key text NOT NULL,
    enabled boolean NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT chk_user_owner_notification_owner_type CHECK (owner_type = ANY (ARRAY['USER'::text, 'ORGANIZATION'::text])),
    CONSTRAINT user_owner_notification_preferences_enabled_not_null NOT NULL enabled,
    CONSTRAINT user_owner_notification_preferences_owner_id_not_null NOT NULL owner_id,
    CONSTRAINT user_owner_notification_preferences_owner_type_not_null NOT NULL owner_type,
    CONSTRAINT user_owner_notification_preferences_pkey PRIMARY KEY (user_id, owner_type, owner_id, service_key, type_key),
    CONSTRAINT user_owner_notification_preferences_service_key_not_null NOT NULL service_key,
    CONSTRAINT user_owner_notification_preferences_service_key_type_key_fkey FOREIGN KEY (service_key, type_key) REFERENCES account.notification_types(service_key, type_key) ON DELETE CASCADE,
    CONSTRAINT user_owner_notification_preferences_type_key_not_null NOT NULL type_key,
    CONSTRAINT user_owner_notification_preferences_updated_at_not_null NOT NULL updated_at,
    CONSTRAINT user_owner_notification_preferences_user_id_fkey FOREIGN KEY (user_id) REFERENCES account.users(id) ON DELETE CASCADE,
    CONSTRAINT user_owner_notification_preferences_user_id_not_null NOT NULL user_id
);

CREATE TABLE account.verification_tokens (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    token_hash text NOT NULL,
    purpose text NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    used_at timestamp with time zone,
    attempts integer DEFAULT 0 NOT NULL,
    max_attempts integer DEFAULT 5 NOT NULL,
    locked_at timestamp with time zone,
    consumed_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT verification_tokens_attempts_not_null NOT NULL attempts,
    CONSTRAINT verification_tokens_created_at_not_null NOT NULL created_at,
    CONSTRAINT verification_tokens_expires_at_not_null NOT NULL expires_at,
    CONSTRAINT verification_tokens_id_not_null NOT NULL id,
    CONSTRAINT verification_tokens_max_attempts_not_null NOT NULL max_attempts,
    CONSTRAINT verification_tokens_pkey PRIMARY KEY (id),
    CONSTRAINT verification_tokens_purpose_not_null NOT NULL purpose,
    CONSTRAINT verification_tokens_token_hash_not_null NOT NULL token_hash,
    CONSTRAINT verification_tokens_user_id_fkey FOREIGN KEY (user_id) REFERENCES account.users(id) ON DELETE CASCADE,
    CONSTRAINT verification_tokens_user_id_not_null NOT NULL user_id
);

CREATE INDEX idx_account_login_failures_expires_at ON account.account_login_failures USING btree (expires_at);

CREATE INDEX idx_email_outbox_pending ON account.email_outbox USING btree (status, next_attempt_at);

CREATE INDEX idx_notification_outbox_actor ON account.notification_outbox USING btree (actor_id, created_at);

CREATE INDEX idx_notification_outbox_pending ON account.notification_outbox USING btree (status, next_attempt_at, created_at);

CREATE INDEX idx_notification_types_service ON account.notification_types USING btree (service_key, display_order);

CREATE INDEX idx_organization_invitations_user_status ON account.organization_invitations USING btree (invited_user_id, status, created_at DESC);

CREATE UNIQUE INDEX uq_organization_pending_invitation ON account.organization_invitations USING btree (organization_id, invited_user_id) WHERE (status = 'PENDING'::text);

CREATE INDEX idx_organization_members_user ON account.organization_members USING btree (user_id);

CREATE INDEX idx_organizations_created_by ON account.organizations USING btree (created_by_user_id);

CREATE UNIQUE INDEX uq_organizations_org_name_lower ON account.organizations USING btree (lower(org_name));

CREATE UNIQUE INDEX uq_pending_email_changes_new_email_lower ON account.pending_email_changes USING btree (lower(new_email));

CREATE INDEX idx_pending_registrations_expires_at ON account.pending_registrations USING btree (expires_at);

CREATE UNIQUE INDEX uq_pending_registrations_code_hash ON account.pending_registrations USING btree (code_hash);

CREATE UNIQUE INDEX uq_pending_registrations_email_lower ON account.pending_registrations USING btree (lower(email));

CREATE UNIQUE INDEX uq_pending_registrations_username_lower ON account.pending_registrations USING btree (lower(username));

CREATE INDEX idx_qr_login_challenges_pending ON account.qr_login_challenges USING btree (status, expires_at);

CREATE INDEX idx_qr_login_challenges_user_status ON account.qr_login_challenges USING btree (user_id, status, expires_at);

CREATE INDEX idx_rate_limit_counters_expires_at ON account.rate_limit_counters USING btree (expires_at);

CREATE INDEX idx_sessions_previous_refresh_token_hash ON account.sessions USING btree (previous_refresh_token_hash);

CREATE INDEX idx_sessions_refresh_token_hash ON account.sessions USING btree (refresh_token_hash);

CREATE INDEX idx_sessions_user_id ON account.sessions USING btree (user_id);

CREATE INDEX idx_user_notification_preferences_user ON account.user_notification_preferences USING btree (user_id);

CREATE INDEX idx_user_notification_activations_user ON account.user_notification_service_activations USING btree (user_id);

CREATE INDEX idx_notifications_recipient ON account.user_notifications USING btree (recipient_id, created_at DESC);

CREATE INDEX idx_notifications_recipient_service ON account.user_notifications USING btree (recipient_id, service_key, created_at DESC);

CREATE INDEX idx_notifications_recipient_source_owner ON account.user_notifications USING btree (recipient_id, source_owner_type, source_owner_id, created_at DESC);

CREATE INDEX idx_notifications_recipient_target_owner ON account.user_notifications USING btree (recipient_id, target_owner_type, target_owner_id, created_at DESC);

CREATE UNIQUE INDEX idx_notifications_source_event ON account.user_notifications USING btree (source_event_id) WHERE (source_event_id IS NOT NULL);

CREATE INDEX idx_user_owner_notification_preferences_user_owner ON account.user_owner_notification_preferences USING btree (user_id, owner_type, owner_id);

CREATE INDEX idx_users_birth_date_month_day ON account.users USING btree (EXTRACT(month FROM birth_date), EXTRACT(day FROM birth_date)) WHERE (birth_date IS NOT NULL);

CREATE UNIQUE INDEX uq_users_email_lower ON account.users USING btree (lower(email));

CREATE UNIQUE INDEX uq_users_username_lower ON account.users USING btree (lower(username));

CREATE INDEX idx_verification_challenge_subject ON account.verification_tokens USING btree (user_id, purpose, consumed_at);

CREATE INDEX idx_verification_tokens_token_hash ON account.verification_tokens USING btree (token_hash);

CREATE UNIQUE INDEX uq_verification_active_purpose ON account.verification_tokens USING btree (user_id, purpose) WHERE ((consumed_at IS NULL) AND (used_at IS NULL));

CREATE INDEX idx_subscriptions_subscribed_to ON account.subscriptions USING btree (subscribed_to_id);

CREATE INDEX idx_subscriptions_subscribed_to_owner ON account.subscriptions USING btree (subscribed_to_type, subscribed_to_id);

CREATE INDEX idx_subscriptions_subscriber ON account.subscriptions USING btree (subscriber_id);

CREATE INDEX idx_subscriptions_subscriber_owner ON account.subscriptions USING btree (subscriber_type, subscriber_id);

CREATE UNIQUE INDEX uq_social_subscriptions_owner_pair ON account.subscriptions USING btree (subscriber_type, subscriber_id, subscribed_to_type, subscribed_to_id);

CREATE INDEX idx_user_blocks_blocked ON account.user_blocks USING btree (blocked_id);

CREATE INDEX idx_user_blocks_blocked_owner ON account.user_blocks USING btree (blocked_type, blocked_id);

CREATE INDEX idx_user_blocks_blocker ON account.user_blocks USING btree (blocker_id);

CREATE INDEX idx_user_blocks_blocker_owner ON account.user_blocks USING btree (blocker_type, blocker_id);

CREATE UNIQUE INDEX uq_user_blocks_owner_pair ON account.user_blocks USING btree (blocker_type, blocker_id, blocked_type, blocked_id);

-- Required notification catalog reference data.
INSERT INTO account.notification_services (service_key, name_i18n, description_i18n, icon, display_order, active, created_at, updated_at) VALUES ('account', '{"en": "Account", "ru": "Аккаунт"}', '{"en": "Follows, profile, and account system events", "ru": "Подписки, профиль и системные события аккаунта"}', 'pi pi-user', 10, true, '2026-07-16 15:55:30.5766+00', '2026-07-16 15:55:30.5766+00');
INSERT INTO account.notification_services (service_key, name_i18n, description_i18n, icon, display_order, active, created_at, updated_at) VALUES ('content', '{"en": "Content", "ru": "Контент"}', '{"en": "Posts, stories, comments, and mentions", "ru": "Публикации, истории, комментарии и упоминания"}', 'pi pi-send', 20, true, '2026-07-16 15:55:30.5766+00', '2026-07-16 15:55:30.5766+00');
INSERT INTO account.notification_types (service_key, type_key, name_i18n, description_i18n, icon, default_enabled, display_order, active, created_at, updated_at) VALUES ('account', 'subscription_request', '{"en": "Subscriptions", "ru": "Подписки"}', '{"en": "Follow requests and changes", "ru": "Запросы и изменения подписок"}', 'pi pi-user-plus', true, 10, true, '2026-07-16 15:55:30.580005+00', '2026-07-16 15:55:30.580005+00');
INSERT INTO account.notification_types (service_key, type_key, name_i18n, description_i18n, icon, default_enabled, display_order, active, created_at, updated_at) VALUES ('account', 'subscription_accepted', '{"en": "New followers", "ru": "Новые подписчики"}', '{"en": "When a follow request is accepted or a new follower appears", "ru": "Когда запрос подписки принят или появился новый подписчик"}', 'pi pi-user-plus', true, 20, true, '2026-07-16 15:55:30.580005+00', '2026-07-16 15:55:30.580005+00');
INSERT INTO account.notification_types (service_key, type_key, name_i18n, description_i18n, icon, default_enabled, display_order, active, created_at, updated_at) VALUES ('account', 'birthday_today', '{"en": "Birthdays", "ru": "Дни рождения"}', '{"en": "When visible following accounts have a birthday", "ru": "Когда у видимых подписок день рождения"}', 'pi pi-gift', true, 30, true, '2026-07-16 15:55:30.580005+00', '2026-07-16 15:55:30.580005+00');
INSERT INTO account.notification_types (service_key, type_key, name_i18n, description_i18n, icon, default_enabled, display_order, active, created_at, updated_at) VALUES ('content', 'post_published', '{"en": "Publications", "ru": "Публикации"}', '{"en": "New publications from following", "ru": "Новые публикации подписок"}', 'pi pi-send', true, 10, true, '2026-07-16 15:55:30.580005+00', '2026-07-16 15:55:30.580005+00');
INSERT INTO account.notification_types (service_key, type_key, name_i18n, description_i18n, icon, default_enabled, display_order, active, created_at, updated_at) VALUES ('content', 'story_published', '{"en": "Stories", "ru": "Истории"}', '{"en": "New stories from following", "ru": "Новые истории подписок"}', 'pi pi-bolt', true, 20, true, '2026-07-16 15:55:30.580005+00', '2026-07-16 15:55:30.580005+00');
INSERT INTO account.notification_types (service_key, type_key, name_i18n, description_i18n, icon, default_enabled, display_order, active, created_at, updated_at) VALUES ('content', 'author_mention', '{"en": "Author mentions", "ru": "Упоминания автора"}', '{"en": "When you are added as an author", "ru": "Когда вас добавляют как автора"}', 'pi pi-at', true, 30, true, '2026-07-16 15:55:30.580005+00', '2026-07-16 15:55:30.580005+00');
INSERT INTO account.notification_types (service_key, type_key, name_i18n, description_i18n, icon, default_enabled, display_order, active, created_at, updated_at) VALUES ('content', 'post_comment', '{"en": "Comments", "ru": "Комментарии"}', '{"en": "Comments on posts", "ru": "Комментарии к публикациям"}', 'pi pi-comments', true, 40, true, '2026-07-16 15:55:30.580005+00', '2026-07-16 15:55:30.580005+00');
INSERT INTO account.notification_types (service_key, type_key, name_i18n, description_i18n, icon, default_enabled, display_order, active, created_at, updated_at) VALUES ('account', 'organization_invitation', '{"en": "Organization invitations", "ru": "Приглашения в организации"}', '{"en": "When you are invited to an organization", "ru": "Когда вас приглашают в организацию"}', 'pi pi-building', true, 25, true, '2026-07-16 15:55:30.904082+00', '2026-07-16 15:55:30.904082+00');
