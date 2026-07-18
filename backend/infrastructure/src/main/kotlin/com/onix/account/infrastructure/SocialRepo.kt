package com.onix.account.infrastructure

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.onix.account.domain.*
import com.onix.account.usecases.BlockRepository
import com.onix.account.usecases.PrivacyRepository
import com.onix.account.usecases.SocialRepository
import java.sql.Connection
import java.sql.Timestamp
import java.util.UUID
import javax.sql.DataSource

class SocialRepo(private val ds: DataSource) : SocialRepository {
    override fun saveSubscription(sub: Subscription) {
        ds.connection.use { conn ->
            try {
                conn.prepareStatement("""
                    INSERT INTO account.subscriptions (id, subscriber_type, subscriber_id, subscribed_to_type, subscribed_to_id, status, is_close_friend, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent()).use { ps ->
                    ps.setObject(1, sub.id)
                    ps.setString(2, sub.subscriberType.name)
                    ps.setObject(3, sub.subscriberId)
                    ps.setString(4, sub.subscribedToType.name)
                    ps.setObject(5, sub.subscribedToId)
                    ps.setString(6, sub.status.name)
                    ps.setBoolean(7, sub.isCloseFriend)
                    ps.setTimestamp(8, Timestamp.from(sub.createdAt))
                    ps.setTimestamp(9, Timestamp.from(sub.updatedAt))
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
                    UPDATE account.subscriptions SET status = ?, is_close_friend = ?, updated_at = ? WHERE id = ?
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
                conn.prepareStatement("DELETE FROM account.subscriptions WHERE id = ?").use { ps ->
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
                    SELECT id, subscriber_type, subscriber_id, subscribed_to_type, subscribed_to_id, status, is_close_friend, created_at, updated_at
                    FROM account.subscriptions WHERE subscriber_type = 'USER' AND subscriber_id = ? AND subscribed_to_type = 'USER' AND subscribed_to_id = ?
            """.trimIndent()).use { ps ->
                ps.setObject(1, subscriberId); ps.setObject(2, subscribedToId)
                ps.executeQuery().use { rs ->
                    if (rs.next()) mapSubscription(rs) else null
                }
            }
        }
    }

    override fun findSubscription(subscriber: OwnerRef, subscribedTo: OwnerRef): Subscription? {
        return ds.connection.use { conn ->
            conn.prepareStatement("""
                SELECT id, subscriber_type, subscriber_id, subscribed_to_type, subscribed_to_id, status, is_close_friend, created_at, updated_at
                FROM account.subscriptions
                WHERE subscriber_type = ? AND subscriber_id = ? AND subscribed_to_type = ? AND subscribed_to_id = ?
            """.trimIndent()).use { ps ->
                ps.setString(1, subscriber.type.name)
                ps.setObject(2, subscriber.id)
                ps.setString(3, subscribedTo.type.name)
                ps.setObject(4, subscribedTo.id)
                ps.executeQuery().use { rs -> if (rs.next()) mapSubscription(rs) else null }
            }
        }
    }

    override fun findBySubscriber(subscriberId: UUID, offset: Int, limit: Int): Pair<List<Subscription>, Int> {
        return ds.connection.use { conn ->
            val count = conn.prepareStatement("SELECT COUNT(*) FROM account.subscriptions WHERE subscriber_type = 'USER' AND subscriber_id = ? AND status = 'ACCEPTED'").use { ps ->
                ps.setObject(1, subscriberId)
                ps.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else 0 }
            }
            val items = conn.prepareStatement("""
                SELECT id, subscriber_type, subscriber_id, subscribed_to_type, subscribed_to_id, status, is_close_friend, created_at, updated_at
                FROM account.subscriptions WHERE subscriber_type = 'USER' AND subscriber_id = ? AND status = 'ACCEPTED'
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

    override fun findBySubscriber(subscriber: OwnerRef, offset: Int, limit: Int): Pair<List<Subscription>, Int> {
        return ds.connection.use { conn ->
            val count = conn.prepareStatement("SELECT COUNT(*) FROM account.subscriptions WHERE subscriber_type = ? AND subscriber_id = ? AND status = 'ACCEPTED'").use { ps ->
                ps.setString(1, subscriber.type.name); ps.setObject(2, subscriber.id)
                ps.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else 0 }
            }
            val items = conn.prepareStatement("""
                SELECT id, subscriber_type, subscriber_id, subscribed_to_type, subscribed_to_id, status, is_close_friend, created_at, updated_at
                FROM account.subscriptions WHERE subscriber_type = ? AND subscriber_id = ? AND status = 'ACCEPTED'
                ORDER BY created_at DESC LIMIT ? OFFSET ?
            """.trimIndent()).use { ps ->
                ps.setString(1, subscriber.type.name); ps.setObject(2, subscriber.id); ps.setInt(3, limit); ps.setInt(4, offset)
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
            val count = conn.prepareStatement("SELECT COUNT(*) FROM account.subscriptions WHERE subscribed_to_type = 'USER' AND subscribed_to_id = ? AND status = 'ACCEPTED'").use { ps ->
                ps.setObject(1, subscribedToId)
                ps.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else 0 }
            }
            val items = conn.prepareStatement("""
                SELECT id, subscriber_type, subscriber_id, subscribed_to_type, subscribed_to_id, status, is_close_friend, created_at, updated_at
                FROM account.subscriptions WHERE subscribed_to_type = 'USER' AND subscribed_to_id = ? AND status = 'ACCEPTED'
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

    override fun findBySubscribedTo(subscribedTo: OwnerRef, offset: Int, limit: Int): Pair<List<Subscription>, Int> {
        return ds.connection.use { conn ->
            val count = conn.prepareStatement("SELECT COUNT(*) FROM account.subscriptions WHERE subscribed_to_type = ? AND subscribed_to_id = ? AND status = 'ACCEPTED'").use { ps ->
                ps.setString(1, subscribedTo.type.name); ps.setObject(2, subscribedTo.id)
                ps.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else 0 }
            }
            val items = conn.prepareStatement("""
                SELECT id, subscriber_type, subscriber_id, subscribed_to_type, subscribed_to_id, status, is_close_friend, created_at, updated_at
                FROM account.subscriptions WHERE subscribed_to_type = ? AND subscribed_to_id = ? AND status = 'ACCEPTED'
                ORDER BY created_at DESC LIMIT ? OFFSET ?
            """.trimIndent()).use { ps ->
                ps.setString(1, subscribedTo.type.name); ps.setObject(2, subscribedTo.id); ps.setInt(3, limit); ps.setInt(4, offset)
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
            val count = conn.prepareStatement("SELECT COUNT(*) FROM account.subscriptions WHERE subscribed_to_type = 'USER' AND subscribed_to_id = ? AND status = 'PENDING'").use { ps ->
                ps.setObject(1, subscribedToId)
                ps.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else 0 }
            }
            val items = conn.prepareStatement("""
                SELECT id, subscriber_type, subscriber_id, subscribed_to_type, subscribed_to_id, status, is_close_friend, created_at, updated_at
                FROM account.subscriptions WHERE subscribed_to_type = 'USER' AND subscribed_to_id = ? AND status = 'PENDING'
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
        id = rs.getObject("id") as UUID,
        subscriberType = runCatching { OwnerType.valueOf(rs.getString("subscriber_type")) }.getOrDefault(OwnerType.USER),
        subscriberId = rs.getObject("subscriber_id") as UUID,
        subscribedToType = runCatching { OwnerType.valueOf(rs.getString("subscribed_to_type")) }.getOrDefault(OwnerType.USER),
        subscribedToId = rs.getObject("subscribed_to_id") as UUID,
        status = SubscriptionStatus.valueOf(rs.getString("status")),
        isCloseFriend = rs.getBoolean("is_close_friend"),
        createdAt = rs.getTimestamp("created_at").toInstant(),
        updatedAt = rs.getTimestamp("updated_at").toInstant()
    )

    override fun countFollowers(userId: UUID): Long {
        return countFollowers(OwnerRef.user(userId))
    }

    override fun countFollowers(owner: OwnerRef): Long {
        return ds.connection.use { conn ->
            conn.prepareStatement("SELECT COUNT(*) FROM account.subscriptions WHERE subscribed_to_type = ? AND subscribed_to_id = ? AND status = 'ACCEPTED'").use { ps ->
                ps.setString(1, owner.type.name); ps.setObject(2, owner.id); ps.executeQuery().use { rs -> if (rs.next()) rs.getLong(1) else 0 }
            }
        }
    }

    override fun countFollowing(userId: UUID): Long {
        return countFollowing(OwnerRef.user(userId))
    }

    override fun countFollowing(owner: OwnerRef): Long {
        return ds.connection.use { conn ->
            conn.prepareStatement("SELECT COUNT(*) FROM account.subscriptions WHERE subscriber_type = ? AND subscriber_id = ? AND status = 'ACCEPTED'").use { ps ->
                ps.setString(1, owner.type.name); ps.setObject(2, owner.id); ps.executeQuery().use { rs -> if (rs.next()) rs.getLong(1) else 0 }
            }
        }
    }
}

class BlockRepo(private val ds: DataSource) : BlockRepository {
    override fun save(block: UserBlock) {
        ds.connection.use { conn ->
            try {
                conn.prepareStatement("""
                    INSERT INTO account.user_blocks (id, blocker_type, blocker_id, blocked_type, blocked_id)
                    VALUES (?, ?, ?, ?, ?)
                    ON CONFLICT DO NOTHING
                """.trimIndent()).use { ps ->
                    ps.setObject(1, com.onix.account.domain.UuidV7.generate())
                    ps.setString(2, block.blockerType.name)
                    ps.setObject(3, block.blockerId)
                    ps.setString(4, block.blockedType.name)
                    ps.setObject(5, block.blockedId)
                    ps.executeUpdate()
                }
                conn.commit()
            } catch (error: Throwable) {
                conn.rollback()
                throw error
            }
        }
    }

    override fun delete(blockerId: UUID, blockedId: UUID) {
        delete(OwnerRef.user(blockerId), OwnerRef.user(blockedId))
    }

    override fun delete(blocker: OwnerRef, blocked: OwnerRef) {
        ds.connection.use { conn ->
            try {
                conn.prepareStatement("""
                    DELETE FROM account.user_blocks
                    WHERE blocker_type = ? AND blocker_id = ? AND blocked_type = ? AND blocked_id = ?
                """.trimIndent()).use { ps ->
                    ps.setString(1, blocker.type.name)
                    ps.setObject(2, blocker.id)
                    ps.setString(3, blocked.type.name)
                    ps.setObject(4, blocked.id)
                    ps.executeUpdate()
                }
                conn.commit()
            } catch (error: Throwable) {
                conn.rollback()
                throw error
            }
        }
    }

    override fun isBlockedEither(a: UUID, b: UUID): Boolean {
        return isBlockedEither(OwnerRef.user(a), OwnerRef.user(b))
    }

    override fun isBlockedEither(a: OwnerRef, b: OwnerRef): Boolean {
        return ds.connection.use { conn ->
            conn.prepareStatement("""
                SELECT 1 FROM account.user_blocks
                WHERE (blocker_type = ? AND blocker_id = ? AND blocked_type = ? AND blocked_id = ?)
                   OR (blocker_type = ? AND blocker_id = ? AND blocked_type = ? AND blocked_id = ?)
                LIMIT 1
            """.trimIndent()).use { ps ->
                ps.setString(1, a.type.name)
                ps.setObject(2, a.id)
                ps.setString(3, b.type.name)
                ps.setObject(4, b.id)
                ps.setString(5, b.type.name)
                ps.setObject(6, b.id)
                ps.setString(7, a.type.name)
                ps.setObject(8, a.id)
                ps.executeQuery().use { rs -> rs.next() }
            }
        }
    }

    override fun findByBlocker(blockerId: UUID): List<UserBlock> {
        return findByBlocker(OwnerRef.user(blockerId))
    }

    override fun findByBlocker(blocker: OwnerRef): List<UserBlock> {
        return ds.connection.use { conn ->
            conn.prepareStatement("""
                SELECT blocker_type, blocker_id, blocked_type, blocked_id, created_at
                FROM account.user_blocks
                WHERE blocker_type = ? AND blocker_id = ?
                ORDER BY created_at DESC
            """.trimIndent()).use { ps ->
                ps.setString(1, blocker.type.name)
                ps.setObject(2, blocker.id)
                ps.executeQuery().use { rs ->
                    val list = mutableListOf<UserBlock>()
                    while (rs.next()) list.add(UserBlock(
                        blockerType = runCatching { OwnerType.valueOf(rs.getString("blocker_type")) }.getOrDefault(OwnerType.USER),
                        blockerId = rs.getObject("blocker_id") as UUID,
                        blockedType = runCatching { OwnerType.valueOf(rs.getString("blocked_type")) }.getOrDefault(OwnerType.USER),
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
        return get(OwnerRef.user(userId))
    }

    override fun get(owner: OwnerRef): PrivacySettings {
        return ds.connection.use { conn ->
            conn.prepareStatement("""
                SELECT owner_type, user_id, is_private, field_visibility
                FROM account.privacy_settings
                WHERE owner_type = ? AND user_id = ?
            """.trimIndent()).use { ps ->
                ps.setString(1, owner.type.name)
                ps.setObject(2, owner.id)
                ps.executeQuery().use { rs ->
                    if (rs.next()) PrivacySettings(
                        ownerType = runCatching { OwnerType.valueOf(rs.getString("owner_type")) }.getOrDefault(OwnerType.USER),
                        userId = rs.getObject("user_id") as UUID,
                        isPrivate = rs.getBoolean("is_private"),
                        fieldVisibility = decodeVisibility(rs.getString("field_visibility"))
                    ) else PrivacySettings(ownerType = owner.type, userId = owner.id)
                }
            }
        }
    }

    override fun save(settings: PrivacySettings) {
        ds.connection.use { conn ->
            try {
                conn.prepareStatement("""
                    INSERT INTO account.privacy_settings (owner_type, user_id, is_private, field_visibility, updated_at)
                    VALUES (?, ?, ?, ?::jsonb, ?)
                    ON CONFLICT (owner_type, user_id) DO UPDATE SET
                        is_private = EXCLUDED.is_private,
                        field_visibility = EXCLUDED.field_visibility,
                        updated_at = EXCLUDED.updated_at
                """.trimIndent()).use { ps ->
                    ps.setString(1, settings.ownerType.name)
                    ps.setObject(2, settings.userId)
                    ps.setBoolean(3, settings.isPrivate)
                    ps.setString(4, json.encodeToString(settings.fieldVisibility.toResponse()))
                    ps.setTimestamp(5, Timestamp.from(settings.updatedAt))
                    ps.executeUpdate()
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
