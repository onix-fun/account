package com.onix.account.infrastructure.clients

import com.onix.account.domain.SocialLink
import com.onix.account.infrastructure.config.DownstreamConfig
import com.onix.media.contract.*
import com.onix.profile.contract.*
import io.grpc.ManagedChannel
import io.grpc.Metadata
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.grpc.netty.NettyChannelBuilder
import io.grpc.stub.MetadataUtils
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.TimeUnit

data class PublicProfileData(
    val username: String,
    val displayName: String,
    val bio: String,
    val socialLinks: List<SocialLink>,
    val avatarAssetId: String?,
    val revision: Long
)

class ProfileClient(config: DownstreamConfig) : AutoCloseable {
    private val channel = channel(config.profileGrpcTarget)
    private val baseStub = ProfileServiceGrpc.newBlockingStub(channel)
    private val apiKey = config.profileApiKey
    @Volatile private var unavailableUntilMillis = 0L

    fun get(ownerType: String, ownerId: String): PublicProfileData? {
        if (System.currentTimeMillis() < unavailableUntilMillis) return null
        return try {
            stub().getPublicProfile(GetPublicProfileRequest.newBuilder().setOwner(owner(ownerType, ownerId)).build()).toDomain()
        } catch (error: StatusRuntimeException) {
            when (error.status.code) {
                Status.Code.NOT_FOUND -> null
                Status.Code.UNAVAILABLE, Status.Code.DEADLINE_EXCEEDED -> {
                    unavailableUntilMillis = System.currentTimeMillis() + 30_000
                    null
                }
                else -> throw error
            }
        }
    }

    fun update(
        ownerType: String,
        ownerId: String,
        username: String,
        displayName: String,
        bio: String,
        socialLinks: List<SocialLink>
    ): PublicProfileData {
        val current = get(ownerType, ownerId)
        val request = UpdatePublicProfileRequest.newBuilder()
            .setOwner(owner(ownerType, ownerId))
            .setUsername(username)
            .setDisplayName(displayName)
            .setBio(bio)
            .setExpectedRevision(current?.revision ?: 0)
            .addAllSocialLinks(socialLinks.map { link ->
                com.onix.profile.contract.SocialLink.newBuilder().setPlatform(link.label).setUrl(link.url).build()
            })
            .build()
        return stub().updatePublicProfile(request).toDomain()
    }

    fun setAvatar(ownerType: String, ownerId: String, assetId: String): PublicProfileData {
        val current = get(ownerType, ownerId) ?: error("Public profile is not initialized")
        return stub().setAvatar(SetAvatarRequest.newBuilder().setOwner(owner(ownerType, ownerId))
            .setMediaAssetId(assetId).setExpectedRevision(current.revision).build()).toDomain()
    }

    override fun close() {
        channel.shutdownNow()
        channel.awaitTermination(2, TimeUnit.SECONDS)
    }

    private fun stub(): ProfileServiceGrpc.ProfileServiceBlockingStub {
        val headers = Metadata().apply {
            put(Metadata.Key.of("x-onix-internal-token", Metadata.ASCII_STRING_MARSHALLER), apiKey)
            put(Metadata.Key.of("x-onix-service", Metadata.ASCII_STRING_MARSHALLER), "account")
        }
        return baseStub.withDeadlineAfter(700, TimeUnit.MILLISECONDS)
            .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers))
    }

    private fun PublicProfile.toDomain() = PublicProfileData(
        username = username,
        displayName = displayName,
        bio = bio,
        socialLinks = socialLinksList.map { SocialLink(it.platform, it.url) },
        avatarAssetId = avatarAssetId.takeIf(String::isNotBlank),
        revision = revision
    )
}

class MediaAvatarClient(config: DownstreamConfig) : AutoCloseable {
    private val channel = channel(config.mediaGrpcTarget)
    private val baseStub = MediaServiceGrpc.newBlockingStub(channel)
    private val apiKey = config.mediaApiKey
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()

    fun upload(ownerType: String, ownerId: String, bytes: ByteArray, mimeType: String): String {
        val ownerRef = "${ownerType.lowercase()}:$ownerId"
        val begun = stub().beginAssetUpload(BeginAssetUploadRequest.newBuilder()
            .setOwnerRef(ownerRef).setDeclaredKind(MediaKind.MEDIA_KIND_IMAGE).setMimeType(mimeType)
            .setExpectedSize(bytes.size.toLong()).setPartsCount(1).setSourcePolicyId("browser-native-v1").build())
        val uploadUrl = requireNotNull(begun.partsMap[1]) { "Media did not return an upload URL" }
        val response = http.send(
            HttpRequest.newBuilder(URI(uploadUrl)).timeout(Duration.ofSeconds(30))
                .header("Content-Type", mimeType).PUT(HttpRequest.BodyPublishers.ofByteArray(bytes)).build(),
            HttpResponse.BodyHandlers.discarding()
        )
        check(response.statusCode() in 200..299) { "Media upload failed with HTTP ${response.statusCode()}" }
        val etag = response.headers().firstValue("ETag").orElse("").trim('"')
        stub().completeAssetUpload(CompleteAssetUploadRequest.newBuilder()
            .setOwnerRef(ownerRef).setAssetId(begun.source.assetId).setSessionId(begun.sessionId)
            .addParts(UploadPart.newBuilder().setPartNumber(1).setEtag(etag).build()).build())
        return begun.source.assetId
    }

    fun resolve(ownerType: String, ownerId: String, assetId: String?): String? {
        if (assetId.isNullOrBlank()) return null
        return runCatching { stub().resolveSource(ResolveSourceRequest.newBuilder()
            .setOwnerRef("${ownerType.lowercase()}:$ownerId").setAssetId(assetId).build()).url }.getOrNull()
    }

    override fun close() {
        channel.shutdownNow()
        channel.awaitTermination(2, TimeUnit.SECONDS)
    }

    private fun stub(): MediaServiceGrpc.MediaServiceBlockingStub {
        val headers = Metadata().apply {
            put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER), "Bearer $apiKey")
            put(Metadata.Key.of("x-onix-service", Metadata.ASCII_STRING_MARSHALLER), "account")
        }
        return baseStub.withDeadlineAfter(5, TimeUnit.SECONDS)
            .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers))
    }
}

private fun owner(type: String, id: String): com.onix.profile.contract.OwnerRef =
    com.onix.profile.contract.OwnerRef.newBuilder()
        .setType(if (type == "ORGANIZATION") com.onix.profile.contract.OwnerType.OWNER_TYPE_ORGANIZATION else com.onix.profile.contract.OwnerType.OWNER_TYPE_USER)
        .setId(id).build()

private fun channel(target: String): ManagedChannel {
    val normalized = target.removePrefix("http://").removePrefix("https://")
    val parts = normalized.split(":", limit = 2)
    return NettyChannelBuilder.forAddress(parts[0], parts.getOrNull(1)?.toIntOrNull() ?: 9090).usePlaintext().build()
}
