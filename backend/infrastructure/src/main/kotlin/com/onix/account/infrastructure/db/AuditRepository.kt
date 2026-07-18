package com.onix.account.infrastructure.db

import com.onix.account.domain.UuidV7
import java.util.UUID
import javax.sql.DataSource

class AuditRepository(private val dataSource: DataSource) {
    fun record(userId: String?, action: String, result: String, requestId: String? = null, errorCode: String? = null, ip: String? = null, userAgent: String? = null) {
        dataSource.connection.use { conn ->
            conn.prepareStatement("INSERT INTO audit_logs(id,user_id,action,result,request_id,error_code,ip_address,user_agent) VALUES (?,?,?,?,?,?,?,?)").use {
                it.setObject(1, UuidV7.generate()); it.setObject(2, userId?.let(UUID::fromString)); it.setString(3, action); it.setString(4, result)
                it.setString(5, requestId); it.setString(6, errorCode); it.setString(7, ip); it.setString(8, userAgent); it.executeUpdate()
            }; conn.commit()
        }
    }
}
