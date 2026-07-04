package profile.infrastructure

import profile.domain.*
import profile.usecases.NotificationOutboxProcessor
import profile.usecases.NotificationOutboxRepository
import profile.usecases.NotificationRepository
import java.sql.Timestamp
import java.util.UUID
import javax.sql.DataSource

class NotificationRepo(private val ds: DataSource) : NotificationRepository {
    override fun save(n: Notification) {
        ds.connection.use { conn ->
            try {
                conn.prepareStatement("""
                    INSERT INTO user_notifications (id, recipient_id, type, title, body, metadata_json, is_read, actor_id, entity_type, entity_id, source_event_id, created_at)
                    VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (source_event_id) WHERE source_event_id IS NOT NULL DO NOTHING
                """.trimIndent()).use { ps ->
                    ps.setObject(1, n.id); ps.setObject(2, n.recipientId)
                    ps.setString(3, n.type); ps.setString(4, n.title); ps.setString(5, n.body)
                    ps.setString(6, n.metadataJson); ps.setBoolean(7, n.isRead)
                    if (n.actorId != null) ps.setObject(8, n.actorId) else ps.setNull(8, java.sql.Types.OTHER)
                    ps.setString(9, n.entityType); ps.setString(10, n.entityId)
                    ps.setString(11, n.sourceEventId)
                    ps.setTimestamp(12, Timestamp.from(n.createdAt))
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
                SELECT id, recipient_id, type, title, body, metadata_json, is_read, actor_id, entity_type, entity_id, source_event_id, created_at
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
                conn.commit()
            } catch (error: Throwable) {
                conn.rollback()
                throw error
            }
        }
    }

    private fun mapNotification(rs: java.sql.ResultSet) = Notification(
        id = rs.getObject("id") as UUID, recipientId = rs.getObject("recipient_id") as UUID,
        type = rs.getString("type"), title = rs.getString("title"), body = rs.getString("body"),
        metadataJson = rs.getString("metadata_json"), isRead = rs.getBoolean("is_read"),
        actorId = rs.getObject("actor_id") as? UUID, entityType = rs.getString("entity_type"),
        entityId = rs.getString("entity_id"), sourceEventId = rs.getObject("source_event_id")?.toString(),
        createdAt = rs.getTimestamp("created_at").toInstant()
    )
}

class NotificationOutboxRepo(private val ds: DataSource) : NotificationOutboxRepository, NotificationOutboxProcessor {
    override fun enqueue(event: UserActivityEvent): Boolean {
        return ds.connection.use { conn ->
            try {
                val inserted = conn.prepareStatement("""
                    INSERT INTO notification_outbox (source_event_id, actor_id, activity_type, entity_type, entity_id, metadata_json, status, created_at)
                    VALUES (?, ?, ?, ?, ?, ?::jsonb, 'PENDING', ?)
                    ON CONFLICT (source_event_id) DO NOTHING
                """.trimIndent()).use { ps ->
                    ps.setString(1, event.sourceEventId)
                    ps.setObject(2, event.actorId)
                    ps.setString(3, event.activityType.name)
                    ps.setString(4, event.entityType)
                    ps.setString(5, event.entityId)
                    ps.setString(6, event.metadataJson)
                    ps.setTimestamp(7, Timestamp.from(event.createdAt))
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
                    SELECT id, source_event_id, actor_id, activity_type, entity_type, entity_id, metadata_json, attempts, created_at
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
