package profile.infrastructure.storage

import profile.shared.ApiErrorCode
import profile.shared.apiError
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

data class ProcessedAvatar(val bytes: ByteArray, val contentType: String)

object AvatarProcessor {
    private const val MAX_DIMENSION = 10_000
    private const val MAX_PIXELS = 25_000_000L
    private const val OUTPUT_SIZE = 1024

    fun process(bytes: ByteArray): ProcessedAvatar {
        val image = runCatching { ImageIO.read(ByteArrayInputStream(bytes)) }.getOrNull()
            ?: apiError(ApiErrorCode.AVATAR_INVALID_IMAGE, "file")
        if (image.width <= 0 || image.height <= 0 || image.width > MAX_DIMENSION || image.height > MAX_DIMENSION ||
            image.width.toLong() * image.height > MAX_PIXELS) apiError(ApiErrorCode.AVATAR_DIMENSIONS_TOO_LARGE, "file")
        val scale = minOf(1.0, OUTPUT_SIZE.toDouble() / maxOf(image.width, image.height))
        val width = maxOf(1, (image.width * scale).toInt()); val height = maxOf(1, (image.height * scale).toInt())
        val alpha = image.colorModel.hasAlpha()
        val output = BufferedImage(width, height, if (alpha) BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB)
        output.createGraphics().apply {
            setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            drawImage(image, 0, 0, width, height, null); dispose()
        }
        val format = if (alpha) "png" else "jpg"
        val stream = ByteArrayOutputStream()
        if (!ImageIO.write(output, format, stream)) apiError(ApiErrorCode.AVATAR_INVALID_IMAGE, "file")
        return ProcessedAvatar(stream.toByteArray(), if (alpha) "image/png" else "image/jpeg")
    }
}
