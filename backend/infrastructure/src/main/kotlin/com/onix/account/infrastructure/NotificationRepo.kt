package com.onix.account.infrastructure

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import com.onix.account.domain.*
import com.onix.account.usecases.NotificationOutboxProcessor
import com.onix.account.usecases.NotificationOutboxRepository
import com.onix.account.usecases.NotificationRepository
import java.sql.Connection
import java.sql.Timestamp
import java.util.UUID
import javax.sql.DataSource

class NotificationRepo(private val ds: DataSource) : NotificationRepository {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override fun save(n: Notification) {
        ds.connection.use { conn ->
            try {
                conn.prepareStatement("""
                    INSERT INTO user_notifications (
                        id, recipient_id, type, service_key, type_key, title, body, title_i18n, body_i18n,
                        metadata_json, is_read, actor_id, source_owner_type, source_owner_id, target_owner_type,
                        target_owner_id, entity_type, entity_id, source_event_id, created_at
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (source_event_id) WHERE source_event_id IS NOT NULL DO NOTHING
                """.trimIndent()).use { ps ->
                    ps.setObject(1, n.id); ps.setObject(2, n.recipientId)
                    ps.setString(3, n.type)
                    ps.setString(4, n.serviceKey)
                    ps.setString(5, n.typeKey)
                    ps.setString(6, n.title); ps.setString(7, n.body)
                    ps.setString(8, n.titleI18nJson)
                    ps.setString(9, n.bodyI18nJson)
                    ps.setString(10, n.metadataJson); ps.setBoolean(11, n.isRead)
                    if (n.actorId != null) ps.setObject(12, n.actorId) else ps.setNull(12, java.sql.Types.OTHER)
                    ps.setString(13, n.sourceOwner?.type?.name)
                    n.sourceOwner?.id?.let { ps.setObject(14, it) } ?: ps.setNull(14, java.sql.Types.OTHER)
                    ps.setString(15, n.targetOwner?.type?.name)
                    n.targetOwner?.id?.let { ps.setObject(16, it) } ?: ps.setNull(16, java.sql.Types.OTHER)
                    ps.setString(17, n.entityType); ps.setString(18, n.entityId)
                    ps.setString(19, n.sourceEventId)
                    ps.setTimestamp(20, Timestamp.from(n.createdAt))
                    ps.executeUpdate()
                }
                conn.commit()
            } catch (error: Throwable) {
                conn.rollback()
                throw error
            }
        }
    }

    override fun existsBySourceEventId(eventId: String): Boolean {
        return ds.connection.use { conn ->
            conn.prepareStatement("SELECT 1 FROM user_notifications WHERE source_event_id = ? LIMIT 1").use { ps ->
                ps.setString(1, eventId)
                ps.executeQuery().use { rs -> rs.next() }
            }
        }
    }

    override fun findByRecipient(recipientId: UUID, offset: Int, limit: Int): Pair<List<Notification>, Int> {
        return ds.connection.use { conn ->
            val count = conn.prepareStatement("SELECT COUNT(*) FROM user_notifications WHERE recipient_id = ?").use { ps ->
                ps.setObject(1, recipientId); ps.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else 0 }
            }
            val items = conn.prepareStatement("""
                SELECT id, recipient_id, type, service_key, type_key, title, body, title_i18n, body_i18n,
                       metadata_json, is_read, actor_id, source_owner_type, source_owner_id, target_owner_type,
                       target_owner_id, entity_type, entity_id, source_event_id, created_at
                FROM user_notifications WHERE recipient_id = ?
                ORDER BY created_at DESC LIMIT ? OFFSET ?
            """.trimIndent()).use { ps ->
                ps.setObject(1, recipientId); ps.setInt(2, limit); ps.setInt(3, offset)
                ps.executeQuery().use { rs ->
                    val list = mutableListOf<Notification>()
                    while (rs.next()) list.add(mapNotification(rs))
                    list
                }
            }
            Pair(items, count)
        }
    }

    override fun countUnread(recipientId: UUID): Int {
        return ds.connection.use { conn ->
            conn.prepareStatement("SELECT COUNT(*) FROM user_notifications WHERE recipient_id = ? AND is_read = false").use { ps ->
                ps.setObject(1, recipientId); ps.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else 0 }
            }
        }
    }

    override fun markRead(id: UUID) {
        ds.connection.use { conn ->
            try {
                conn.prepareStatement("UPDATE user_notifications SET is_read = true WHERE id = ?").use { ps ->
                    ps.setObject(1, id); ps.executeUpdate()
                }
                conn.commit()
            } catch (error: Throwable) {
                conn.rollback()
                throw error
            }
        }
    }

    override fun markAllRead(recipientId: UUID) {
        ds.connection.use { conn ->
            try {
                conn.prepareStatement("UPDATE user_notifications SET is_read = true WHERE recipient_id = ?").use { ps ->
                    ps.setObject(1, recipientId); ps.executeUpdate()
                }
                conn.commit()
            } catch (error: Throwable) {
                conn.rollback()
                throw error
            }
        }
    }

    override fun getPrefs(userId: UUID): NotificationPrefs {
        return ds.connection.use { conn ->
            conn.prepareStatement("SELECT * FROM notification_preferences WHERE user_id = ?").use { ps ->
                ps.setObject(1, userId)
                ps.executeQuery().use { rs ->
                    if (rs.next()) NotificationPrefs(
                        userId = rs.getObject("user_id") as UUID,
                        inAppSubscriptions = rs.getBoolean("in_app_subscriptions"),
                        inAppPublications = rs.getBoolean("in_app_publications"),
                        inAppAuthorMentions = rs.getBoolean("in_app_author_mentions"),
                        inAppPostComments = rs.getBoolean("in_app_post_comments"),
                        inAppNewStories = rs.getBoolean("in_app_new_stories"),
                        inAppBirthdays = rs.getBoolean("in_app_birthdays"),
                        updatedAt = rs.getTimestamp("updated_at").toInstant()
                    ) else NotificationPrefs(userId = userId)
                }
            }
        }
    }

    override fun savePrefs(prefs: NotificationPrefs) {
        ds.connection.use { conn ->
            try {
                conn.prepareStatement("""
                    INSERT INTO notification_preferences (user_id, in_app_subscriptions, in_app_publications, in_app_author_mentions, in_app_post_comments, in_app_new_stories, in_app_birthdays, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (user_id) DO UPDATE SET
                        in_app_subscriptions = EXCLUDED.in_app_subscriptions,
                        in_app_publications = EXCLUDED.in_app_publications,
                        in_app_author_mentions = EXCLUDED.in_app_author_mentions,
                        in_app_post_comments = EXCLUDED.in_app_post_comments,
                        in_app_new_stories = EXCLUDED.in_app_new_stories,
                        in_app_birthdays = EXCLUDED.in_app_birthdays,
                        updated_at = EXCLUDED.updated_at
                """.trimIndent()).use { ps ->
                    ps.setObject(1, prefs.userId)
                    ps.setBoolean(2, prefs.inAppSubscriptions); ps.setBoolean(3, prefs.inAppPublications)
                    ps.setBoolean(4, prefs.inAppAuthorMentions); ps.setBoolean(5, prefs.inAppPostComments)
                    ps.setBoolean(6, prefs.inAppNewStories); ps.setBoolean(7, prefs.inAppBirthdays)
                    ps.setTimestamp(8, Timestamp.from(prefs.updatedAt))
                    ps.executeUpdate()
                }
                savePreference(conn, prefs.userId, "account", "subscription_request", prefs.inAppSubscriptions)
                savePreference(conn, prefs.userId, "account", "subscription_accepted", prefs.inAppSubscriptions)
                savePreference(conn, prefs.userId, "account", "birthday_today", prefs.inAppBirthdays)
                savePreference(conn, prefs.userId, "content", "post_published", prefs.inAppPublications)
                savePreference(conn, prefs.userId, "content", "author_mention", prefs.inAppAuthorMentions)
                savePreference(conn, prefs.userId, "content", "post_comment", prefs.inAppPostComments)
                savePreference(conn, prefs.userId, "content", "story_published", prefs.inAppNewStories)
                conn.commit()
            } catch (error: Throwable) {
                conn.rollback()
                throw error
            }
        }
    }

    override fun registerCatalog(catalog: NotificationServiceCatalog) {
        ds.connection.use { conn ->
            try {
                conn.prepareStatement("""
                    INSERT INTO notification_services (service_key, name_i18n, description_i18n, icon, display_order, active, updated_at)
                    VALUES (?, ?::jsonb, ?::jsonb, ?, ?, true, CURRENT_TIMESTAMP)
                    ON CONFLICT (service_key) DO UPDATE SET
                        name_i18n = EXCLUDED.name_i18n,
                        description_i18n = EXCLUDED.description_i18n,
                        icon = EXCLUDED.icon,
                        display_order = EXCLUDED.display_order,
                        active = true,
                        updated_at = CURRENT_TIMESTAMP
                """.trimIndent()).use { ps ->
                    ps.setString(1, catalog.serviceKey)
                    ps.setString(2, catalog.name.toJson())
                    ps.setString(3, catalog.description.toJson())
                    ps.setString(4, catalog.icon.ifBlank { "pi pi-bell" })
                    ps.setInt(5, catalog.displayOrder)
                    ps.executeUpdate()
                }
                catalog.types.forEach { type ->
                    conn.prepareStatement("""
                        INSERT INTO notification_types (
                            service_key, type_key, name_i18n, description_i18n, icon,
                            default_enabled, display_order, active, updated_at
                        )
                        VALUES (?, ?, ?::jsonb, ?::jsonb, ?, ?, ?, true, CURRENT_TIMESTAMP)
                        ON CONFLICT (service_key, type_key) DO UPDATE SET
                            name_i18n = EXCLUDED.name_i18n,
                            description_i18n = EXCLUDED.description_i18n,
                            icon = EXCLUDED.icon,
                            default_enabled = EXCLUDED.default_enabled,
                            display_order = EXCLUDED.display_order,
                            active = true,
                            updated_at = CURRENT_TIMESTAMP
                    """.trimIndent()).use { ps ->
                        ps.setString(1, catalog.serviceKey)
                        ps.setString(2, type.typeKey)
                        ps.setString(3, type.name.toJson())
                        ps.setString(4, type.description.toJson())
                        ps.setString(5, type.icon.ifBlank { "pi pi-bell" })
                        ps.setBoolean(6, type.defaultEnabled)
                        ps.setInt(7, type.displayOrder)
                        ps.executeUpdate()
                    }
                }
                conn.commit()
            } catch (error: Throwable) {
                conn.rollback()
                throw error
            }
        }
    }

    override fun activateServiceForUser(userId: UUID, serviceKey: String) {
        ds.connection.use { conn ->
            // This is an idempotent registration performed by every authenticated
            // Content request. Under the pool-wide REPEATABLE_READ isolation,
            // concurrent first requests can both miss the row and PostgreSQL
            // aborts all but one transaction with SQLSTATE 40001 even though the
            // INSERT uses ON CONFLICT. READ_COMMITTED gives this single-statement
            // operation the intended upsert semantics.
            conn.transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
            try {
                conn.prepareStatement("""
                    INSERT INTO user_notification_service_activations (user_id, service_key)
                    VALUES (?, ?)
                    ON CONFLICT DO NOTHING
                """.trimIndent()).use { ps ->
                    ps.setObject(1, userId)
                    ps.setString(2, serviceKey)
                    ps.executeUpdate()
                }
                conn.commit()
            } catch (error: Throwable) {
                conn.rollback()
                throw error
            }
        }
    }

    override fun notificationTypeExists(serviceKey: String, typeKey: String): Boolean =
        ds.connection.use { conn ->
            conn.prepareStatement("""
                SELECT 1 FROM notification_types
                WHERE service_key = ? AND type_key = ? AND active = true
                LIMIT 1
            """.trimIndent()).use { ps ->
                ps.setString(1, serviceKey)
                ps.setString(2, typeKey)
                ps.executeQuery().use { it.next() }
            }
        }

    override fun notificationTypeEnabled(userId: UUID, serviceKey: String, typeKey: String, owner: OwnerRef?): Boolean =
        ds.connection.use { conn ->
            conn.prepareStatement("""
                SELECT COALESCE(op.enabled, p.enabled, t.default_enabled) AS enabled
                FROM notification_types t
                LEFT JOIN user_notification_preferences p
                    ON p.user_id = ? AND p.service_key = t.service_key AND p.type_key = t.type_key
                LEFT JOIN user_owner_notification_preferences op
                    ON op.user_id = ?
                    AND op.owner_type = ?
                    AND op.owner_id = ?
                    AND op.service_key = t.service_key
                    AND op.type_key = t.type_key
                WHERE t.service_key = ? AND t.type_key = ? AND t.active = true
            """.trimIndent()).use { ps ->
                ps.setObject(1, userId)
                ps.setObject(2, userId)
                ps.setString(3, owner?.type?.name)
                if (owner != null) ps.setObject(4, owner.id) else ps.setNull(4, java.sql.Types.OTHER)
                ps.setString(5, serviceKey)
                ps.setString(6, typeKey)
                ps.executeQuery().use { rs -> if (rs.next()) rs.getBoolean("enabled") else false }
            }
        }

    override fun getLocalizedSettings(userId: UUID, locale: String, owner: OwnerRef?): List<LocalizedNotificationServiceSettings> {
        val normalizedLocale = if (locale.lowercase().startsWith("ru")) "ru" else "en"
        return ds.connection.use { conn ->
            val rows = conn.prepareStatement("""
                SELECT
                    s.service_key,
                    s.name_i18n AS service_name_i18n,
                    s.description_i18n AS service_description_i18n,
                    s.icon AS service_icon,
                    s.display_order AS service_order,
                    t.type_key,
                    t.name_i18n AS type_name_i18n,
                    t.description_i18n AS type_description_i18n,
                    t.icon AS type_icon,
                    t.display_order AS type_order,
                    COALESCE(op.enabled, p.enabled, t.default_enabled) AS enabled
                FROM user_notification_service_activations a
                JOIN notification_services s ON s.service_key = a.service_key AND s.active = true
                JOIN notification_types t ON t.service_key = s.service_key AND t.active = true
                LEFT JOIN user_notification_preferences p
                    ON p.user_id = a.user_id AND p.service_key = t.service_key AND p.type_key = t.type_key
                LEFT JOIN user_owner_notification_preferences op
                    ON op.user_id = a.user_id
                    AND op.owner_type = ?
                    AND op.owner_id = ?
                    AND op.service_key = t.service_key
                    AND op.type_key = t.type_key
                WHERE a.user_id = ?
                ORDER BY s.display_order, s.service_key, t.display_order, t.type_key
            """.trimIndent()).use { ps ->
                ps.setString(1, owner?.type?.name)
                if (owner != null) ps.setObject(2, owner.id) else ps.setNull(2, java.sql.Types.OTHER)
                ps.setObject(3, userId)
                ps.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            add(SettingsRow(
                                serviceKey = rs.getString("service_key"),
                                serviceName = localized(rs.getString("service_name_i18n"), normalizedLocale),
                                serviceDescription = localized(rs.getString("service_description_i18n"), normalizedLocale),
                                serviceIcon = rs.getString("service_icon"),
                                typeKey = rs.getString("type_key"),
                                typeName = localized(rs.getString("type_name_i18n"), normalizedLocale),
                                typeDescription = localized(rs.getString("type_description_i18n"), normalizedLocale),
                                typeIcon = rs.getString("type_icon"),
                                enabled = rs.getBoolean("enabled")
                            ))
                        }
                    }
                }
            }

            rows.groupBy { it.serviceKey }.map { (serviceKey, grouped) ->
                val first = grouped.first()
                LocalizedNotificationServiceSettings(
                    serviceKey = serviceKey,
                    name = first.serviceName,
                    description = first.serviceDescription,
                    icon = first.serviceIcon,
                    items = grouped.map {
                        LocalizedNotificationTypeSetting(
                            serviceKey = it.serviceKey,
                            typeKey = it.typeKey,
                            name = it.typeName,
                            description = it.typeDescription,
                            icon = it.typeIcon,
                            enabled = it.enabled
                        )
                    }
                )
            }
        }
    }

    override fun savePreference(userId: UUID, serviceKey: String, typeKey: String, enabled: Boolean) {
        ds.connection.use { conn ->
            try {
                savePreference(conn, userId, serviceKey, typeKey, enabled)
                conn.commit()
            } catch (error: Throwable) {
                conn.rollback()
                throw error
            }
        }
    }

    override fun saveOwnerPreference(userId: UUID, owner: OwnerRef, serviceKey: String, typeKey: String, enabled: Boolean) {
        ds.connection.use { conn ->
            try {
                conn.prepareStatement("""
                    INSERT INTO user_owner_notification_preferences (user_id, owner_type, owner_id, service_key, type_key, enabled, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                    ON CONFLICT (user_id, owner_type, owner_id, service_key, type_key) DO UPDATE SET
                        enabled = EXCLUDED.enabled,
                        updated_at = EXCLUDED.updated_at
                """.trimIndent()).use { ps ->
                    ps.setObject(1, userId)
                    ps.setString(2, owner.type.name)
                    ps.setObject(3, owner.id)
                    ps.setString(4, serviceKey)
                    ps.setString(5, typeKey)
                    ps.setBoolean(6, enabled)
                    ps.executeUpdate()
                }
                conn.commit()
            } catch (error: Throwable) {
                conn.rollback()
                throw error
            }
        }
    }

    private fun savePreference(conn: java.sql.Connection, userId: UUID, serviceKey: String, typeKey: String, enabled: Boolean) {
        conn.prepareStatement("""
            INSERT INTO user_notification_preferences (user_id, service_key, type_key, enabled, updated_at)
            VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
            ON CONFLICT (user_id, service_key, type_key) DO UPDATE SET
                enabled = EXCLUDED.enabled,
                updated_at = EXCLUDED.updated_at
        """.trimIndent()).use { ps ->
            ps.setObject(1, userId)
            ps.setString(2, serviceKey)
            ps.setString(3, typeKey)
            ps.setBoolean(4, enabled)
            ps.executeUpdate()
        }
    }

    private fun mapNotification(rs: java.sql.ResultSet) = Notification(
        id = rs.getObject("id") as UUID, recipientId = rs.getObject("recipient_id") as UUID,
        type = rs.getString("type"),
        serviceKey = rs.getString("service_key"),
        typeKey = rs.getString("type_key"),
        title = rs.getString("title"),
        body = rs.getString("body"),
        titleI18nJson = rs.getString("title_i18n"),
        bodyI18nJson = rs.getString("body_i18n"),
        metadataJson = rs.getString("metadata_json"), isRead = rs.getBoolean("is_read"),
        actorId = rs.getObject("actor_id") as? UUID,
        sourceOwner = ownerRef(rs.getString("source_owner_type"), rs.getObject("source_owner_id") as? UUID),
        targetOwner = ownerRef(rs.getString("target_owner_type"), rs.getObject("target_owner_id") as? UUID),
        entityType = rs.getString("entity_type"),
        entityId = rs.getString("entity_id"), sourceEventId = rs.getObject("source_event_id")?.toString(),
        createdAt = rs.getTimestamp("created_at").toInstant()
    )

    private fun ownerRef(type: String?, id: UUID?): OwnerRef? =
        if (type.isNullOrBlank() || id == null) null
        else OwnerRef(runCatching { OwnerType.valueOf(type) }.getOrDefault(OwnerType.USER), id)

    private data class SettingsRow(
        val serviceKey: String,
        val serviceName: String,
        val serviceDescription: String,
        val serviceIcon: String,
        val typeKey: String,
        val typeName: String,
        val typeDescription: String,
        val typeIcon: String,
        val enabled: Boolean
    )

    private fun LocalizedText.toJson(): String = json.encodeToString(
        JsonObject(mapOf("ru" to JsonPrimitive(ru), "en" to JsonPrimitive(en)))
    )

    private fun localized(raw: String?, locale: String): String {
        if (raw.isNullOrBlank()) return ""
        val obj = runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: return ""
        return obj[locale]?.jsonPrimitive?.contentOrNull
            ?: obj["en"]?.jsonPrimitive?.contentOrNull
            ?: obj["ru"]?.jsonPrimitive?.contentOrNull
            ?: ""
    }
}

class NotificationOutboxRepo(private val ds: DataSource) : NotificationOutboxRepository, NotificationOutboxProcessor {
    override fun enqueue(event: UserActivityEvent): Boolean {
        return ds.connection.use { conn ->
            try {
                val inserted = conn.prepareStatement("""
                    INSERT INTO notification_outbox (id, source_event_id, actor_type, actor_id, activity_type, entity_type, entity_id, metadata_json, status, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, 'PENDING', ?)
                    ON CONFLICT (source_event_id) DO NOTHING
                """.trimIndent()).use { ps ->
                    ps.setObject(1, UuidV7.generate())
                    ps.setString(2, event.sourceEventId)
                    ps.setString(3, event.actorType.name)
                    ps.setObject(4, event.actorId)
                    ps.setString(5, event.activityType.name)
                    ps.setString(6, event.entityType)
                    ps.setString(7, event.entityId)
                    ps.setString(8, event.metadataJson)
                    ps.setTimestamp(9, Timestamp.from(event.createdAt))
                    ps.executeUpdate() == 1
                }
                conn.commit()
                inserted
            } catch (error: Throwable) {
                conn.rollback()
                throw error
            }
        }
    }

    override fun processPending(limit: Int, handler: (NotificationOutboxItem) -> Unit): Int {
        var processed = 0
        ds.connection.use { conn ->
            try {
                val rows = conn.prepareStatement("""
                    SELECT id, source_event_id, actor_type, actor_id, activity_type, entity_type, entity_id, metadata_json, attempts, created_at
                    FROM notification_outbox
                    WHERE status = 'PENDING' AND next_attempt_at <= CURRENT_TIMESTAMP
                    ORDER BY created_at
                    LIMIT ?
                    FOR UPDATE SKIP LOCKED
                """.trimIndent()).use { ps ->
                    ps.setInt(1, limit)
                    ps.executeQuery().use { rs ->
                        buildList {
                            while (rs.next()) {
                                add(NotificationOutboxItem(
                                    id = rs.getObject("id") as UUID,
                                    event = UserActivityEvent(
                                        sourceEventId = rs.getString("source_event_id"),
                                        actorType = runCatching { OwnerType.valueOf(rs.getString("actor_type")) }.getOrDefault(OwnerType.USER),
                                        actorId = rs.getObject("actor_id") as UUID,
                                        activityType = UserActivityType.valueOf(rs.getString("activity_type")),
                                        entityType = rs.getString("entity_type"),
                                        entityId = rs.getString("entity_id"),
                                        metadataJson = rs.getString("metadata_json"),
                                        createdAt = rs.getTimestamp("created_at").toInstant()
                                    ),
                                    attempts = rs.getInt("attempts"),
                                    createdAt = rs.getTimestamp("created_at").toInstant()
                                ))
                            }
                        }
                    }
                }

                rows.forEach { row ->
                    runCatching { handler(row) }
                        .onSuccess {
                            markSent(conn, row.id, row.attempts)
                            processed += 1
                        }
                        .onFailure { error ->
                            markFailed(conn, row.id, row.attempts + 1, error.message?.take(500))
                        }
                }
                conn.commit()
            } catch (error: Throwable) {
                conn.rollback()
                throw error
            }
        }
        return processed
    }

    private fun markSent(conn: java.sql.Connection, id: UUID, attempts: Int) {
        conn.prepareStatement("""
            UPDATE notification_outbox
            SET status = 'SENT', attempts = ?, processed_at = CURRENT_TIMESTAMP, last_error = NULL
            WHERE id = ?
        """.trimIndent()).use { ps ->
            ps.setInt(1, attempts)
            ps.setObject(2, id)
            ps.executeUpdate()
        }
    }

    private fun markFailed(conn: java.sql.Connection, id: UUID, attempts: Int, error: String?) {
        val status = if (attempts >= 5) "DEAD" else "PENDING"
        conn.prepareStatement("""
            UPDATE notification_outbox
            SET status = ?, attempts = ?, last_error = ?, next_attempt_at = CURRENT_TIMESTAMP + (? * INTERVAL '1 second')
            WHERE id = ?
        """.trimIndent()).use { ps ->
            ps.setString(1, status)
            ps.setInt(2, attempts)
            ps.setString(3, error)
            ps.setInt(4, 1 shl attempts.coerceAtMost(8))
            ps.setObject(5, id)
            ps.executeUpdate()
        }
    }
}
