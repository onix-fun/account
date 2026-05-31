package profile.infrastructure.storage

import io.minio.MinioClient
import io.minio.PutObjectArgs
import io.ktor.server.config.*
import java.io.ByteArrayInputStream
import java.util.UUID

class S3Client(config: ApplicationConfig) {
    private val endpoint = config.property("s3.endpoint").getString()
    private val publicUrl = (config.propertyOrNull("s3.public_url")?.getString() ?: endpoint).trimEnd('/')
    private val accessKey = config.property("s3.access_key").getString()
    private val secretKey = config.property("s3.secret_key").getString()
    private val bucket = config.property("s3.bucket").getString()

    private val client = MinioClient.builder()
        .endpoint(endpoint)
        .credentials(accessKey, secretKey)
        .build()

    fun uploadAvatar(userId: String, bytes: ByteArray, contentType: String): String {
        val fileName = "$userId/${UUID.randomUUID()}.${extensionFor(contentType)}"
        client.putObject(
            PutObjectArgs.builder()
                .bucket(bucket)
                .`object`(fileName)
                .stream(ByteArrayInputStream(bytes), bytes.size.toLong(), -1)
                .contentType(contentType)
                .build()
        )
        return "$publicUrl/$fileName"
    }

    private fun extensionFor(contentType: String): String {
        return when (contentType.lowercase()) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "jpg"
        }
    }
}
