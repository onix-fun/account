package profile.infrastructure.events

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

class EmailEventConsumer(private val dataSource: DataSource, private val publisher: EventPublisher, private val sender: SmtpEmailSender) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun start() {
        Thread({
            while (!Thread.currentThread().isInterrupted) {
                runCatching { processBatch() }.onFailure { log.error("Outbox worker failed", it) }
                Thread.sleep(1_000)
            }
        }, "email-outbox-worker").apply { isDaemon = true; start() }
    }

    internal fun processBatch() {
        dataSource.connection.use { conn ->
            val rows = conn.prepareStatement("SELECT id,event_type,payload,attempts,expires_at FROM email_outbox WHERE status='PENDING' AND next_attempt_at<=CURRENT_TIMESTAMP ORDER BY created_at LIMIT 10 FOR UPDATE SKIP LOCKED").use {
                val rs=it.executeQuery(); buildList { while(rs.next()) add(arrayOf(rs.getObject("id").toString(),rs.getString("event_type"),rs.getString("payload"),rs.getInt("attempts"),rs.getTimestamp("expires_at").toInstant())) }
            }
            rows.forEach { row ->
                val id=row[0] as String; val attempts=row[3] as Int; val expires=row[4] as Instant
                if (expires.isBefore(Instant.now())) update(conn,id,"DEAD",attempts+1,"expired")
                else runCatching { deliver(row[1] as String, publisher.decrypt(row[2] as String)) }
                    .onSuccess { update(conn,id,"SENT",attempts,null) }
                    .onFailure { update(conn,id,if(attempts+1>=5)"DEAD" else "PENDING",attempts+1,it.message?.take(500)) }
            }
            conn.commit()
        }
    }

    private fun deliver(type: String, payload: String) = when(type) {
        "email.verify" -> Json.decodeFromString<VerificationEmailPayload>(payload).let {
            sender.sendCode(it.email, it.code, EmailCodePurpose.VERIFY_EMAIL, it.locale)
        }
        "email.email_change" -> Json.decodeFromString<VerificationEmailPayload>(payload).let {
            sender.sendCode(it.email, it.code, EmailCodePurpose.CHANGE_EMAIL, it.locale)
        }
        "email.password_reset" -> Json.decodeFromString<PasswordResetEmailPayload>(payload).let {
            sender.sendCode(it.email, it.code, EmailCodePurpose.RESET_PASSWORD, it.locale)
        }
        "email.security_notice" -> Json.decodeFromString<SecurityNotificationPayload>(payload).let {
            sender.sendSecurityNotification(it.email, it.type, it.locale)
        }
        else -> Unit
    }
    private fun update(conn: java.sql.Connection,id:String,status:String,attempts:Int,error:String?) {
        conn.prepareStatement("UPDATE email_outbox SET status=?,attempts=?,last_error=?,next_attempt_at=CURRENT_TIMESTAMP + (? * INTERVAL '1 second'),sent_at=CASE WHEN ?='SENT' THEN CURRENT_TIMESTAMP ELSE sent_at END WHERE id=?").use {
            it.setString(1,status);it.setInt(2,attempts);it.setString(3,error);it.setInt(4,(1 shl attempts.coerceAtMost(8)));it.setString(5,status);it.setObject(6,UUID.fromString(id));it.executeUpdate()
        }
    }
}
