package tv.hsrui.bolo.utils.file

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import io.ktor.http.Url
import io.ktor.http.decodeURLPart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

data class ImageSaveData(
    val bytes: ByteArray,
    val fileName: String,
    val mimeType: String,
)

expect class ImageSaver() {
    suspend fun requestPermission(): Boolean
    /** 保存成功返回 true，用户取消系统文件窗口返回 false。 */
    suspend fun saveImage(image: ImageSaveData): Boolean
}

internal suspend fun fetchImageForSave(
    url: String,
    imageLoader: ImageLoader,
    context: PlatformContext,
): ImageSaveData = withContext(Dispatchers.IO) {
    val options = Options(
        context = context,
        diskCachePolicy = imageLoader.defaults.diskCachePolicy,
        networkCachePolicy = imageLoader.defaults.networkCachePolicy,
        extras = imageLoader.defaults.extras,
    )
    val data = imageLoader.components.map(url, options)
    var index = 0
    var result: SourceFetchResult? = null
    while (result == null) {
        currentCoroutineContext().ensureActive()
        val (fetcher, factoryIndex) = checkNotNull(imageLoader.components.newFetcher(data, options, imageLoader, index)) {
            "无法获取原始图片数据"
        }
        val fetched = fetcher.fetch()
        check(fetched == null || fetched is SourceFetchResult) { "无法获取原始图片数据" }
        result = fetched
        index = factoryIndex + 1
    }
    val bytes = result.source.use { source ->
        currentCoroutineContext().ensureActive()
        source.source().readByteArray()
    }
    currentCoroutineContext().ensureActive()
    val (extension, mimeType) = imageFormat(bytes)
    ImageSaveData(bytes, imageFileName(url, extension), mimeType)
}

private fun imageFormat(bytes: ByteArray): Pair<String, String> {
    fun matches(offset: Int, vararg values: Int): Boolean =
        bytes.size >= offset + values.size && values.indices.all { bytes[offset + it].toInt() and 0xff == values[it] }

    fun text(offset: Int, value: String): Boolean =
        bytes.size >= offset + value.length && value.indices.all { bytes[offset + it].toInt() == value[it].code }

    return when {
        bytes.size >= 4 && matches(0, 0xff, 0xd8, 0xff) -> "jpg" to "image/jpeg"
        bytes.size >= 33 && matches(0, 137, 80, 78, 71, 13, 10, 26, 10) && text(12, "IHDR") -> "png" to "image/png"
        bytes.size >= 14 && (text(0, "GIF87a") || text(0, "GIF89a")) -> "gif" to "image/gif"
        bytes.size >= 30 && text(0, "RIFF") && text(8, "WEBP") &&
            (text(12, "VP8 ") || text(12, "VP8L") || text(12, "VP8X")) -> "webp" to "image/webp"
        bytes.size >= 26 && text(0, "BM") -> "bmp" to "image/bmp"
        bytes.size >= 8 && (matches(0, 73, 73, 42, 0) || matches(0, 77, 77, 0, 42)) -> "tif" to "image/tiff"
        bytes.size >= 24 && text(4, "ftyp") -> {
            val boxSize = (0..3).fold(0L) { size, index -> (size shl 8) or (bytes[index].toLong() and 0xff) }
            check(boxSize in 24..bytes.size.toLong()) { "图片数据无效" }
            val brands = listOf(8) + (16 until boxSize.toInt() step 4)
            when {
                brands.any { text(it, "avif") || text(it, "avis") } -> "avif" to "image/avif"
                brands.any { text(it, "heic") || text(it, "heix") || text(it, "hevc") || text(it, "hevx") } -> "heic" to "image/heic"
                brands.any { text(it, "mif1") || text(it, "msf1") } -> "heif" to "image/heif"
                else -> error("不支持的图片格式")
            }
        }
        else -> error("不支持的图片格式或图片数据无效")
    }
}

private fun imageFileName(url: String, extension: String): String {
    val segment = Url(url).encodedPath.substringAfterLast('/').decodeURLPart()
        .replace(Regex("(?i)(\\.(?:jpe?g|png|gif|webp|bmp|avif|heic|heif|tiff?))@.+$"), "$1")
    val stem = segment.substringBeforeLast('.', segment)
        .map { if (it.isISOControl() || it in "<>:\"/\\|?*") '_' else it }
        .joinToString("").trimEnd(' ', '.')
    var start = stem.length
    repeat(12) {
        if (start > 0) {
            start--
            if (stem[start].isLowSurrogate() && start > 0 && stem[start - 1].isHighSurrogate()) start--
        }
    }
    var name = stem.substring(start).ifEmpty { "MultiBili" }
    if (Regex("(?i)^(con|prn|aux|nul|com[1-9¹²³]|lpt[1-9¹²³])(?:\\.|$)").containsMatchIn(name)) name = "_${name.drop(1)}"
    return "$name.$extension"
}
