package profile.infrastructure

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import profile.domain.*
import profile.usecases.BlockRepository
import profile.usecases.PrivacyRepository
import profile.usecases.SocialRepository
import java.sql.Connection
import java.sql.Timestamp
import java.util.UUID
import javax.sql.DataSource

class SocialRepo(private val ds: DataSource) : SocialRepository {
    override fun saveSubscription(sub: Subscription) {
        ds.connection.use { conn ->
            try {
                conn.prepareStatement("""
                    INSERT INTO social.subscriptions (id, subscriber_id, subscribed_to_id, status, is_close_friend, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                """.trimIndent()).use { ps ->
                    ps.setObject(1, sub.id); ps.setObject(2, sub.subscriberId); ps.setObject(3, sub.subscribedToId)
                    ps.setString(4, sub.status.name); ps.setBoolean(5, sub.isCloseFriend)
                    ps.setTimestamp(6, Timestamp.from(sub.createdAt)); ps.setTimestamp(7, Timestamp.from(sub.updatedAt))
                    ps.executeUpdate()
                }
                conn.commit()
            } catch (error: Throwable) {
                conn.rollback()
                throw error
            }
        }
    }

    override fun updateSubscription(sub: Subscription) {
        ds.connection.use { conn ->
            try {
                conn.prepareStatement("""
                    UPDATE social.subscriptions SET status = ?, is_close_friend = ?, updated_at = ? WHERE id = ?
                """.trimIndent()).use { ps ->
                    ps.setString(1, sub.status.name); ps.setBoolean(2, sub.isCloseFriend)
                    ps.setTimestamp(3, Timestamp.from(sub.updatedAt)); ps.setObject(4, sub.id)
                    ps.executeUpdate()
                }
                conn.commit()
            } catch (error: Throwable) {
                conn.rollback()
                throw error
            }
        }
    }

    override fun deleteSubscription(id: UUID) {
        ds.connection.use { conn ->
            try {
                conn.prepareStatement("DELETE FROM social.subscriptions WHERE id = ?").use { ps ->
                    ps.setObject(1, id); ps.executeUpdate()
                }
                conn.commit()
            } catch (error: Throwable) {
                conn.rollback()
                throw error
            }
        }
    }

    override fun findSubscription(subscriberId: UUID, subscribedToId: UUID): Subscription? {
        return ds.connection.use { conn ->
            conn.prepareStatement("""
                SELECT id, subscriber_id, subscribed_to_id, status, is_close_friend, created_at, updated_at
                FROM social.subscriptions WHERE subscriber_id = ? AND subscribed_to_id = ?
            """.trimIndent()).use { ps ->
                ps.setObject(1, subscriberId); ps.setObject(2, subscribedToId)
                ps.executeQuery().use { rs ->
                    if (rs.next()) Subscription(
                        id = rs.getObject("id") as UUID, subscriberId = rs.getObject("subscriber_id") as UUID,
                        subscribedToId = rs.getObject("subscribed_to_id") as UUID,
                        status = SubscriptionStatus.valueOf(rs.getString("status")),
                        isCloseFriend = rs.getBoolean("is_close_friend"),
                        createdAt = rs.getTimestamp("created_at").toInstant(),
                        updatedAt = rs.getTimestamp("updated_at").toInstant()
                    ) else null
                }
            }
        }
    }

    override fun findBySubscriber(subscriberId: UUID, offset: Int, limit: Int): Pair<List<Subscription>, Int> {
        return ds.connection.use { conn ->
            val count = conn.prepareStatement("SELECT COUNT(*) FROM social.subscriptions WHERE subscriber_id = ? AND status = 'ACCEPTED'").use { ps ->
                ps.setObject(1, subscriberId)
                ps.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else 0 }
            }
            val items = conn.prepareStatement("""
                SELECT id, subscriber_id, subscribed_to_id, status, is_close_friend, created_at, updated_at
                FROM social.subscriptions WHERE subscriber_id = ? AND status = 'ACCEPTED'
                ORDER BY created_at DESC LIMIT ? OFFSET ?
            """.trimIndent()).use { ps ->
                ps.setObject(1, subscriberId); ps.setInt(2, limit); ps.setInt(3, offset)
                ps.executeQuery().use { rs ->
                    val list = mutableListOf<Subscription>()
                    while (rs.next()) list.add(mapSubscription(rs))
                    list
                }
            }
            Pair(items, count)
        }
    }

    override fun findBySubscribedTo(subscribedToId: UUID, offset: Int, limit: Int): Pair<List<Subscription>, Int> {
        return ds.connection.use { conn ->
            val count = conn.prepareStatement("SELECT COUNT(*) FROM social.subscriptions WHERE subscribed_to_id = ? AND status = 'ACCEPTED'").use { ps ->
                ps.setObject(1, subscribedToId)
                ps.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else 0 }
            }
            val items = conn.prepareStatement("""
                SELECT id, subscriber_id, subscribed_to_id, status, is_close_friend, created_at, updated_at
                FROM social.subscriptions WHERE subscribed_to_id = ? AND status = 'ACCEPTED'
                ORDER BY created_at DESC LIMIT ? OFFSET ?
            """.trimIndent()).use { ps ->
                ps.setObject(1, subscribedToId); ps.setInt(2, limit); ps.setInt(3, offset)
                ps.executeQuery().use { rs ->
                    val list = mutableListOf<Subscription>()
                    while (rs.next()) list.add(mapSubscription(rs))
                    list
                }
            }
            Pair(items, count)
        }
    }

    override fun findPendingBySubscribedTo(subscribedToId: UUID, offset: Int, limit: Int): Pair<List<Subscription>, Int> {
        return ds.connection.use { conn ->
            val count = conn.prepareStatement("SELECT COUNT(*) FROM social.subscriptions WHERE subscribed_to_id = ? AND status = 'PENDING'").use { ps ->
                ps.setObject(1, subscribedToId)
                ps.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else 0 }
            }
            val items = conn.prepareStatement("""
                SELECT id, subscriber_id, subscribed_to_id, status, is_close_friend, created_at, updated_at
                FROM social.subscriptions WHERE subscribed_to_id = ? AND status = 'PENDING'
                ORDER BY created_at DESC LIMIT ? OFFSET ?
            """.trimIndent()).use { ps ->
                ps.setObject(1, subscribedToId); ps.setInt(2, limit); ps.setInt(3, offset)
                ps.executeQuery().use { rs ->
                    val list = mutableListOf<Subscription>()
                    while (rs.next()) list.add(mapSubscription(rs))
                    list
                }
            }
            Pair(items, count)
        }
    }

    private fun mapSubscription(rs: java.sql.ResultSet) = Subscription(
        id = rs.getObject("id") as UUID, subscriberId = rs.getObject("subscriber_id") as UUID,
        subscribedToId = rs.getObject("subscribed_to_id") as UUID,
        status = SubscriptionStatus.valueOf(rs.getString("status")),
        isCloseFriend = rs.getBoolean("is_close_friend"),
        createdAt = rs.getTimestamp("created_at").toInstant(),
        updatedAt = rs.getTimestamp("updated_at").toInstant()
    )

    override fun countFollowers(userId: UUID): Long {
        return ds.connection.use { conn ->
            conn.prepareStatement("SELECT COUNT(*) FROM social.subscriptions WHERE subscribed_to_id = ? AND status = 'ACCEPTED'").use { ps ->
                ps.setObject(1, userId); ps.executeQuery().use { rs -> if (rs.next()) rs.getLong(1) else 0 }
            }
        }
    }

    override fun countFollowing(userId: UUID): Long {
        return ds.connection.use { conn ->
            conn.prepareStatement("SELECT COUNT(*) FROM social.subscriptions WHERE subscriber_id = ? AND status = 'ACCEPTED'").use { ps ->
                ps.setObject(1, userId); ps.executeQuery().use { rs -> if (rs.next()) rs.getLong(1) else 0 }
            }
        }
    }
}

class BlockRepo(private val ds: DataSource) : BlockRepository {
    override fun save(block: UserBlock) {
        ds.connection.use { conn ->
            try {
                conn.prepareStatement("INSERT INTO social.user_blocks (blocker_id, blocked_id) VALUES (?, ?) ON CONFLICT DO NOTHING").use { ps ->
                    ps.setObject(1, block.blockerId); ps.setObject(2, block.blockedId); ps.executeUpdate()
                }
                conn.commit()
            } catch (error: Throwable) {
                conn.rollback()
                throw error
            }
        }
    }

    override fun delete(blockerId: UUID, blockedId: UUID) {
        ds.connection.use { conn ->
            try {
                conn.prepareStatement("DELETE FROM social.user_blocks WHERE blocker_id = ? AND blocked_id = ?").use { ps ->
                    ps.setObject(1, blockerId); ps.setObject(2, blockedId); ps.executeUpdate()
                }
                conn.commit()
            } catch (error: Throwable) {
                conn.rollback()
                throw error
            }
        }
    }

    override fun isBlockedEither(a: UUID, b: UUID): Boolean {
        return ds.connection.use { conn ->
            conn.prepareStatement("SELECT 1 FROM social.user_blocks WHERE (blocker_id = ? AND blocked_id = ?) OR (blocker_id = ? AND blocked_id = ?) LIMIT 1").use { ps ->
                ps.setObject(1, a); ps.setObject(2, b); ps.setObject(3, b); ps.setObject(4, a)
                ps.executeQuery().use { rs -> rs.next() }
            }
        }
    }

    override fun findByBlocker(blockerId: UUID): List<UserBlock> {
        return ds.connection.use { conn ->
            conn.prepareStatement("SELECT blocker_id, blocked_id, created_at FROM social.user_blocks WHERE blocker_id = ? ORDER BY created_at DESC").use { ps ->
                ps.setObject(1, blockerId)
                ps.executeQuery().use { rs ->
                    val list = mutableListOf<UserBlock>()
                    while (rs.next()) list.add(UserBlock(
                        blockerId = rs.getObject("blocker_id") as UUID,
                        blockedId = rs.getObject("blocked_id") as UUID,
                        createdAt = rs.getTimestamp("created_at").toInstant()
                    ))
                    list
                }
            }
        }
    }
}

class PrivacyRepo(private val ds: DataSource) : PrivacyRepository {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override fun get(userId: UUID): PrivacySettings {
        return ds.connection.use { conn ->
            conn.prepareStatement("SELECT user_id, is_private, field_visibility FROM social.privacy_settings WHERE user_id = ?").use { ps ->
                ps.setObject(1, userId)
                ps.executeQuery().use { rs ->
                    if (rs.next()) PrivacySettings(
                        userId = rs.getObject("user_id") as UUID,
                        isPrivate = rs.getBoolean("is_private"),
                        fieldVisibility = decodeVisibility(rs.getString("field_visibility"))
                    ) else PrivacySettings(userId = userId)
                }
            }
        }
    }

    override fun save(settings: PrivacySettings) {
        ds.connection.use { conn ->
            try {
                conn.prepareStatement("""
                    INSERT INTO social.privacy_settings (user_id, is_private, field_visibility, updated_at)
                    VALUES (?, ?, ?::jsonb, ?)
                    ON CONFLICT (user_id) DO UPDATE SET
                        is_private = EXCLUDED.is_private,
                        field_visibility = EXCLUDED.field_visibility,
                        updated_at = EXCLUDED.updated_at
                """.trimIndent()).use { ps ->
                    ps.setObject(1, settings.userId); ps.setBoolean(2, settings.isPrivate)
                    ps.setString(3, json.encodeToString(settings.fieldVisibility.toResponse()))
                    ps.setTimestamp(4, Timestamp.from(settings.updatedAt)); ps.executeUpdate()
                }
                conn.commit()
            } catch (error: Throwable) {
                conn.rollback()
                throw error
            }
        }
    }

    private fun decodeVisibility(raw: String?): FieldVisibility {
        if (raw.isNullOrBlank()) return FieldVisibility()
        return runCatching {
            FieldVisibility.fromResponse(json.decodeFromString<FieldVisibilityResponse>(raw))
        }.getOrDefault(FieldVisibility())
    }
}
