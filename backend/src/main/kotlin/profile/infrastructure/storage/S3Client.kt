package profile.infrastructure.storage

import io.minio.MinioClient
import io.minio.PutObjectArgs
import io.minio.RemoveObjectArgs
import io.ktor.server.config.*
import java.io.ByteArrayInputStream
import java.util.UUID

class S3Client(config: ApplicationConfig) {
    private val endpoint = config.property("s3.endpoint").getString()
    private val publicUrl = (config.propertyOrNull("s3.public_url")?.getString() ?: endpoint).removeSuffix("/")
    private val accessKey = config.property("s3.access_key").getString()
    private val secretKey = config.property("s3.secret_key").getString()
    private val bucket = config.property("s3.bucket").getString()

    private val client = MinioClient.builder()
        .endpoint(endpoint)
        .credentials(accessKey, secretKey)
        .build()

    fun uploadAvatar(userId: String, bytes: ByteArray, contentType: String): String {
        val fileName = "$userId/${UUID.randomUUID()}.${extensionFor(contentType)}"
        runCatching {
            client.putObject(
                PutObjectArgs.builder()
                    .bucket(bucket)
                    .`object`(fileName)
                    .stream(ByteArrayInputStream(bytes), bytes.size.toLong(), -1)
                    .contentType(contentType)
                    .build()
            )
        }.getOrThrow()
        val prefix = if (publicUrl.startsWith("/")) publicUrl else "/$publicUrl"
        return "${prefix.removeSuffix("/")}/$fileName".replace("//", "/")
    }

    fun deleteByPublicUrl(url: String?) {
        val prefix = if (publicUrl.startsWith("/")) publicUrl else "/$publicUrl"
        val normalizedPublicUrl = prefix.removeSuffix("/")
        if (url.isNullOrBlank() || !url.startsWith("$normalizedPublicUrl/")) return
        val key = url.removePrefix("$normalizedPublicUrl/").removePrefix("/")
        runCatching {
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).`object`(key).build())
        }
    }

    suspend fun getObject(key: String): java.io.InputStream? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            client.getObject(
                io.minio.GetObjectArgs.builder()
                    .bucket(bucket)
                    .`object`(key)
                    .build()
            )
        }.getOrNull()
    }

    suspend fun getObjectSize(key: String): Long? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            client.statObject(
                io.minio.StatObjectArgs.builder()
                    .bucket(bucket)
                    .`object`(key)
                    .build()
            ).size()
        }.getOrNull()
    }

    private fun extensionFor(contentType: String): String {
        return when (contentType.lowercase()) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "jpg"
        }
    }
}
