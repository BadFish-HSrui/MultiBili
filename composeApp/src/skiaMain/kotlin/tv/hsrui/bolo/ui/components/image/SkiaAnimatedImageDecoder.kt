package tv.hsrui.bolo.ui.components.image

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.skiaCanvas
import androidx.compose.ui.graphics.painter.Painter
import coil3.ImageLoader
import coil3.decode.DecodeResult
import coil3.decode.Decoder
import coil3.decode.ImageSource
import coil3.decode.SkiaImageDecoder
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.ByteString.Companion.encodeUtf8
import okio.Buffer
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data
import org.jetbrains.skia.Image
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.impl.use

internal class SkiaAnimatedImageDecoder(private val source: ImageSource, private val options: Options) : Decoder {
    override suspend fun decode(): DecodeResult = withContext(Dispatchers.Default) {
        val bytes = source.source().readByteArray()
        Data.makeFromBytes(bytes).use { data ->
            Codec.makeFromData(data).use { codec ->
                if (codec.frameCount <= 1) {
                    return@withContext SkiaImageDecoder(
                        ImageSource(Buffer().write(bytes), source.fileSystem), options,
                    ).decode()
                }
                val first = codec.readPixels().apply { setImmutable() }
                DecodeResult(
                    image = SkiaAnimatedImage(bytes, first),
                    isSampled = false,
                )
            }
        }
    }

    class Factory : Decoder.Factory {
        override fun create(result: SourceFetchResult, options: Options, imageLoader: ImageLoader): Decoder? {
            val source = result.source.source()
            val gif = source.rangeEquals(0, "GIF87a".encodeUtf8()) || source.rangeEquals(0, "GIF89a".encodeUtf8())
            val webp = source.rangeEquals(0, "RIFF".encodeUtf8()) && source.rangeEquals(8, "WEBP".encodeUtf8())
            return if (gif || webp) SkiaAnimatedImageDecoder(result.source, options) else null
        }
    }
}

// 缓存只持有不可变原始数据与首帧；每个展示位置拥有独立的动画解码状态。
internal class SkiaAnimatedImage(
    val bytes: ByteArray,
    val firstFrame: Bitmap,
) : coil3.Image {
    override val width: Int get() = firstFrame.width
    override val height: Int get() = firstFrame.height
    override val size: Long get() = bytes.size.toLong() + width.toLong() * height * 4
    override val shareable: Boolean = true
    override fun draw(canvas: Canvas) {
        Image.makeFromBitmap(firstFrame).use { canvas.drawImage(it, 0f, 0f) }
    }
}

internal class SkiaAnimatedImagePainter(
    private val image: SkiaAnimatedImage,
    parentScope: CoroutineScope,
) : Painter(), ImageAnimation {
    private val scope = CoroutineScope(parentScope.coroutineContext + SupervisorJob(parentScope.coroutineContext[Job]))
    private val playing = MutableStateFlow(false)
    private var frame by mutableStateOf<Image?>(Image.makeFromBitmap(image.firstFrame))
    override var failed by mutableStateOf(false)
        private set
    override val painter: Painter get() = this
    override val intrinsicSize = Size(image.width.toFloat(), image.height.toFloat())
    private val paint = Paint()

    init {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            var data: Data? = null
            var codec: Codec? = null
            var bitmap: Bitmap? = null
            var decoded: Image? = null
            try {
                withContext(Dispatchers.Default) {
                    data = Data.makeFromBytes(image.bytes)
                    codec = Codec.makeFromData(data)
                    bitmap = Bitmap().apply { allocPixels(codec.imageInfo) }
                }
                val decoder = codec!!
                val frames = withContext(Dispatchers.Default) { decoder.framesInfo }
                val repeatCount = decoder.repetitionCount
                var index = 0
                var completedRepeats = 0
                while (true) {
                    playing.first { it }
                    delay(frames[index].duration.let { if (it < 20) 100L else it.toLong() })
                    playing.first { it }
                    index++
                    if (index == frames.size) {
                        if (repeatCount >= 0 && completedRepeats >= repeatCount) break
                        completedRepeats++
                        index = 0
                    }
                    withContext(Dispatchers.Default) {
                        // 由 Codec 处理依赖帧、透明混合和 disposal，不自行叠加完整帧。
                        decoder.readPixels(bitmap, index)
                        decoded = Image.makeFromBitmap(bitmap!!)
                    }
                    val previous = frame
                    frame = decoded
                    decoded = null
                    previous?.close()
                }
                awaitCancellation()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                failed = true
            } finally {
                withContext(NonCancellable) {
                    withContext(Dispatchers.Default) {
                        decoded?.close()
                        bitmap?.close()
                        codec?.close()
                        data?.close()
                    }
                    frame?.close()
                    frame = null
                    paint.close()
                }
            }
        }
    }

    override fun setPlaying(playing: Boolean) {
        this.playing.value = playing
    }

    override fun dispose() = scope.cancel()

    override fun DrawScope.onDraw() {
        val current = frame ?: return
        drawIntoCanvas { canvas ->
            canvas.skiaCanvas.drawImageRect(current, Rect.makeWH(size.width, size.height), paint)
        }
    }
}
