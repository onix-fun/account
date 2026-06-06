package profile.infrastructure.db

import java.util.UUID
import javax.sql.DataSource

class AuditRepository(private val dataSource: DataSource) {
    fun record(userId: String?, action: String, result: String, requestId: String? = null, errorCode: String? = null, ip: String? = null, userAgent: String? = null) {
        dataSource.connection.use { conn ->
            conn.prepareStatement("INSERT INTO audit_logs(user_id,action,result,request_id,error_code,ip_address,user_agent) VALUES (?,?,?,?,?,?,?)").use {
                it.setObject(1, userId?.let(UUID::fromString)); it.setString(2, action); it.setString(3, result)
                it.setString(4, requestId); it.setString(5, errorCode); it.setString(6, ip); it.setString(7, userAgent); it.executeUpdate()
            }; conn.commit()
        }
    }
}
