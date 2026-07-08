package profile.infrastructure.db

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import profile.domain.Organization
import profile.domain.OrganizationInvitation
import profile.domain.OrganizationInvitationStatus
import profile.domain.OrganizationMember
import profile.domain.OrganizationRole
import profile.domain.SocialLink
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

class OrganizationRepository(private val dataSource: DataSource) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun create(org: Organization): Organization {
        dataSource.connection.use { conn ->
            try {
                val created = conn.prepareStatement("""
                    INSERT INTO organizations (org_name, display_name, bio, social_links, avatar_url, status, created_by_user_id, created_at, updated_at)
                    VALUES (?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?)
                    RETURNING *
                """.trimIndent()).use { stmt ->
                    stmt.setString(1, org.orgName)
                    stmt.setString(2, org.displayName)
                    stmt.setString(3, org.bio)
                    stmt.setString(4, json.encodeToString(org.socialLinks))
                    stmt.setString(5, org.avatarUrl)
                    stmt.setString(6, org.status)
                    stmt.setObject(7, UUID.fromString(org.createdByUserId))
                    stmt.setTimestamp(8, Timestamp.from(org.createdAt))
                    stmt.setTimestamp(9, Timestamp.from(org.updatedAt))
                    val rs = stmt.executeQuery()
                    rs.next()
                    mapOrganization(rs)
                }
                conn.prepareStatement("""
                    INSERT INTO organization_members (organization_id, user_id, role, created_at, updated_at)
                    VALUES (?, ?, 'OWNER', ?, ?)
                """.trimIndent()).use { stmt ->
                    stmt.setObject(1, UUID.fromString(created.id))
                    stmt.setObject(2, UUID.fromString(created.createdByUserId))
                    stmt.setTimestamp(3, Timestamp.from(created.createdAt))
                    stmt.setTimestamp(4, Timestamp.from(created.updatedAt))
                    stmt.executeUpdate()
                }
                conn.commit()
                return created
            } catch (error: Throwable) {
                conn.rollback()
                throw error
            }
        }
    }

    fun update(orgId: String, orgName: String, displayName: String, bio: String?, socialLinks: List<SocialLink>, avatarUrl: String?): Organization {
        dataSource.connection.use { conn ->
            val updated = conn.prepareStatement("""
                UPDATE organizations
                SET org_name = ?, display_name = ?, bio = ?, social_links = ?::jsonb, avatar_url = ?, updated_at = ?
                WHERE id = ? AND status = 'ACTIVE'
                RETURNING *
            """.trimIndent()).use { stmt ->
                stmt.setString(1, orgName)
                stmt.setString(2, displayName)
                stmt.setString(3, bio)
                stmt.setString(4, json.encodeToString(socialLinks))
                stmt.setString(5, avatarUrl)
                stmt.setTimestamp(6, Timestamp.from(Instant.now()))
                stmt.setObject(7, UUID.fromString(orgId))
                val rs = stmt.executeQuery()
                if (!rs.next()) null else mapOrganization(rs)
            }
            conn.commit()
            return updated ?: throw IllegalArgumentException("Organization not found")
        }
    }

    fun findById(id: String): Organization? =
        queryOrganization("SELECT * FROM organizations WHERE id = ? AND status = 'ACTIVE'", UUID.fromString(id))

    fun findByName(name: String): Organization? =
        queryOrganization("SELECT * FROM organizations WHERE LOWER(org_name) = LOWER(?) AND status = 'ACTIVE'", name)

    fun listForUser(userId: String): List<Pair<Organization, OrganizationRole>> {
        dataSource.connection.use { conn ->
            return conn.prepareStatement("""
                SELECT o.*, m.role AS member_role
                FROM organization_members m
                JOIN organizations o ON o.id = m.organization_id
                WHERE m.user_id = ? AND o.status = 'ACTIVE'
                ORDER BY o.display_name ASC
            """.trimIndent()).use { stmt ->
                stmt.setObject(1, UUID.fromString(userId))
                val rs = stmt.executeQuery()
                val result = mutableListOf<Pair<Organization, OrganizationRole>>()
                while (rs.next()) result.add(mapOrganization(rs) to OrganizationRole.valueOf(rs.getString("member_role")))
                result
            }
        }
    }

    fun search(query: String, limit: Int): List<Organization> {
        dataSource.connection.use { conn ->
            return conn.prepareStatement("""
                SELECT * FROM organizations
                WHERE status = 'ACTIVE'
                  AND (LOWER(org_name) LIKE LOWER(?) OR LOWER(display_name) LIKE LOWER(?))
                ORDER BY org_name ASC
                LIMIT ?
            """.trimIndent()).use { stmt ->
                val like = "${query.trim()}%"
                stmt.setString(1, like)
                stmt.setString(2, like)
                stmt.setInt(3, limit.coerceIn(1, 50))
                val rs = stmt.executeQuery()
                val result = mutableListOf<Organization>()
                while (rs.next()) result.add(mapOrganization(rs))
                result
            }
        }
    }

    fun member(orgId: String, userId: String): OrganizationMember? {
        dataSource.connection.use { conn ->
            return conn.prepareStatement("SELECT * FROM organization_members WHERE organization_id = ? AND user_id = ?").use { stmt ->
                stmt.setObject(1, UUID.fromString(orgId))
                stmt.setObject(2, UUID.fromString(userId))
                val rs = stmt.executeQuery()
                if (rs.next()) mapMember(rs) else null
            }
        }
    }

    fun members(orgId: String): List<OrganizationMember> {
        dataSource.connection.use { conn ->
            return conn.prepareStatement("SELECT * FROM organization_members WHERE organization_id = ? ORDER BY role, created_at").use { stmt ->
                stmt.setObject(1, UUID.fromString(orgId))
                val rs = stmt.executeQuery()
                val result = mutableListOf<OrganizationMember>()
                while (rs.next()) result.add(mapMember(rs))
                result
            }
        }
    }

    fun upsertMember(orgId: String, userId: String, role: OrganizationRole) {
        dataSource.connection.use { conn ->
            conn.prepareStatement("""
                INSERT INTO organization_members (organization_id, user_id, role, created_at, updated_at)
                VALUES (?, ?, ?, NOW(), NOW())
                ON CONFLICT (organization_id, user_id) DO UPDATE SET role = EXCLUDED.role, updated_at = NOW()
            """.trimIndent()).use { stmt ->
                stmt.setObject(1, UUID.fromString(orgId))
                stmt.setObject(2, UUID.fromString(userId))
                stmt.setString(3, role.name)
                stmt.executeUpdate()
            }
            conn.commit()
        }
    }

    fun removeMember(orgId: String, userId: String) {
        dataSource.connection.use { conn ->
            conn.prepareStatement("DELETE FROM organization_members WHERE organization_id = ? AND user_id = ?").use { stmt ->
                stmt.setObject(1, UUID.fromString(orgId))
                stmt.setObject(2, UUID.fromString(userId))
                stmt.executeUpdate()
            }
            conn.commit()
        }
    }

    fun ownerCount(orgId: String): Int {
        dataSource.connection.use { conn ->
            return conn.prepareStatement("SELECT COUNT(*) FROM organization_members WHERE organization_id = ? AND role = 'OWNER'").use { stmt ->
                stmt.setObject(1, UUID.fromString(orgId))
                val rs = stmt.executeQuery()
                if (rs.next()) rs.getInt(1) else 0
            }
        }
    }

    fun createInvitation(invitation: OrganizationInvitation): OrganizationInvitation {
        dataSource.connection.use { conn ->
            val created = conn.prepareStatement("""
                INSERT INTO organization_invitations (organization_id, invited_user_id, invited_by_user_id, role, status, created_at, updated_at, expires_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (organization_id, invited_user_id) WHERE status = 'PENDING'
                DO UPDATE SET role = EXCLUDED.role, invited_by_user_id = EXCLUDED.invited_by_user_id, updated_at = NOW()
                RETURNING *
            """.trimIndent()).use { stmt ->
                stmt.setObject(1, UUID.fromString(invitation.organizationId))
                stmt.setObject(2, UUID.fromString(invitation.invitedUserId))
                stmt.setObject(3, UUID.fromString(invitation.invitedByUserId))
                stmt.setString(4, invitation.role.name)
                stmt.setString(5, invitation.status.name)
                stmt.setTimestamp(6, Timestamp.from(invitation.createdAt))
                stmt.setTimestamp(7, Timestamp.from(invitation.updatedAt))
                if (invitation.expiresAt != null) stmt.setTimestamp(8, Timestamp.from(invitation.expiresAt)) else stmt.setTimestamp(8, null)
                val rs = stmt.executeQuery()
                rs.next()
                mapInvitation(rs)
            }
            conn.commit()
            return created
        }
    }

    fun invitationsForUser(userId: String): List<OrganizationInvitation> {
        dataSource.connection.use { conn ->
            return conn.prepareStatement("""
                SELECT * FROM organization_invitations
                WHERE invited_user_id = ? AND status = 'PENDING'
                ORDER BY created_at DESC
            """.trimIndent()).use { stmt ->
                stmt.setObject(1, UUID.fromString(userId))
                val rs = stmt.executeQuery()
                val result = mutableListOf<OrganizationInvitation>()
                while (rs.next()) result.add(mapInvitation(rs))
                result
            }
        }
    }

    fun findInvitation(id: String): OrganizationInvitation? {
        dataSource.connection.use { conn ->
            return conn.prepareStatement("SELECT * FROM organization_invitations WHERE id = ?").use { stmt ->
                stmt.setObject(1, UUID.fromString(id))
                val rs = stmt.executeQuery()
                if (rs.next()) mapInvitation(rs) else null
            }
        }
    }

    fun updateInvitationStatus(id: String, status: OrganizationInvitationStatus) {
        dataSource.connection.use { conn ->
            conn.prepareStatement("UPDATE organization_invitations SET status = ?, updated_at = NOW() WHERE id = ?").use { stmt ->
                stmt.setString(1, status.name)
                stmt.setObject(2, UUID.fromString(id))
                stmt.executeUpdate()
            }
            conn.commit()
        }
    }

    private fun queryOrganization(sql: String, value: Any): Organization? {
        dataSource.connection.use { conn ->
            return conn.prepareStatement(sql).use { stmt ->
                when (value) {
                    is UUID -> stmt.setObject(1, value)
                    else -> stmt.setString(1, value.toString())
                }
                val rs = stmt.executeQuery()
                if (rs.next()) mapOrganization(rs) else null
            }
        }
    }

    private fun mapOrganization(rs: ResultSet) = Organization(
        id = rs.getObject("id").toString(),
        orgName = rs.getString("org_name"),
        displayName = rs.getString("display_name"),
        bio = rs.getString("bio"),
        socialLinks = parseSocialLinks(rs.getString("social_links")),
        avatarUrl = rs.getString("avatar_url"),
        status = rs.getString("status"),
        createdByUserId = rs.getObject("created_by_user_id").toString(),
        createdAt = rs.getTimestamp("created_at").toInstant(),
        updatedAt = rs.getTimestamp("updated_at").toInstant()
    )

    private fun parseSocialLinks(raw: String?): List<SocialLink> =
        if (raw.isNullOrBlank()) emptyList()
        else runCatching { json.decodeFromString<List<SocialLink>>(raw) }.getOrDefault(emptyList())

    private fun mapMember(rs: ResultSet) = OrganizationMember(
        organizationId = rs.getObject("organization_id").toString(),
        userId = rs.getObject("user_id").toString(),
        role = OrganizationRole.valueOf(rs.getString("role")),
        createdAt = rs.getTimestamp("created_at").toInstant(),
        updatedAt = rs.getTimestamp("updated_at").toInstant()
    )

    private fun mapInvitation(rs: ResultSet) = OrganizationInvitation(
        id = rs.getObject("id").toString(),
        organizationId = rs.getObject("organization_id").toString(),
        invitedUserId = rs.getObject("invited_user_id").toString(),
        invitedByUserId = rs.getObject("invited_by_user_id").toString(),
        role = OrganizationRole.valueOf(rs.getString("role")),
        status = OrganizationInvitationStatus.valueOf(rs.getString("status")),
        createdAt = rs.getTimestamp("created_at").toInstant(),
        updatedAt = rs.getTimestamp("updated_at").toInstant(),
        expiresAt = rs.getTimestamp("expires_at")?.toInstant()
    )
}
