package tv.hsrui.bolo.ui.components.image

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.RotateLeft
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material.icons.rounded.ZoomOut
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.github.panpf.zoomimage.CoilZoomState
import com.github.panpf.zoomimage.rememberCoilZoomState
import com.github.panpf.zoomimage.compose.zoom.ZoomableState
import com.github.panpf.zoomimage.util.IntSizeCompat
import com.github.panpf.zoomimage.zoom.ContentScaleCompat
import com.github.panpf.zoomimage.zoom.ScalesCalculator
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import tv.hsrui.bolo.ui.common.systembar.LightSystemBarContentEffect
import tv.hsrui.bolo.utils.file.ImageSaver
import tv.hsrui.bolo.utils.file.fetchImageForSave
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShowImageViewer(
    urls: List<String>,
    initialIndex: Int,
    previewUrls: List<String> = urls,
    initialShowOriginal: Boolean = true,
    originalSizes: List<IntSize> = emptyList(),
    onDismissRequest: () -> Unit,
) {
    if (urls.isEmpty()) return
    require(previewUrls.size == urls.size) { "预览图片与原图数量必须一致" }
    require(originalSizes.isEmpty() || originalSizes.size == urls.size) { "原图尺寸与图片数量必须一致" }
    val pager = rememberPagerState(initialPage = initialIndex.coerceIn(urls.indices)) { urls.size }
    val scope = rememberCoroutineScope()
    var originalPages by remember(urls) {
        mutableStateOf(if (initialShowOriginal) urls.indices.toSet() else emptySet<Int>())
    }
    val displayUrls = urls.mapIndexed { page, url -> if (page in originalPages) url else previewUrls[page] }
    val zoomStates: List<CoilZoomState> = List(urls.size) { page ->
        key(page, displayUrls[page]) {
            val zoomState = rememberCoilZoomState()
            val originalSize = originalSizes.getOrNull(page) ?: IntSize.Zero
            val scalesCalculator = remember(originalSize) { imageViewerScalesCalculator(originalSize) }
            SideEffect { zoomState.zoomable.setScalesCalculator(scalesCalculator) }
            zoomState
        }
    }
    val currentZoomable = zoomStates[pager.currentPage].zoomable
    val context = LocalPlatformContext.current
    val imageSaver = remember { ImageSaver() }
    val snackbarHostState = remember { SnackbarHostState() }
    var saving by remember { mutableStateOf(false) }

    BasicAlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = Modifier.fillMaxSize(),
        properties = imageViewerDialogProperties(),
    ) {
        LightSystemBarContentEffect(includeNavigationBar = true)
        val focusRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
        Surface(
            color = Color.Black,
            contentColor = Color.White,
            modifier = Modifier.fillMaxSize().pointerInput(onDismissRequest) {
                detectTapGestures(onTap = { onDismissRequest() })
            }.onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyUp) false
                else when (event.key) {
                    Key.Escape -> { onDismissRequest(); true }
                    Key.DirectionLeft -> {
                        scope.launch { pager.animateScrollToPage((pager.currentPage - 1).coerceAtLeast(0)) }
                        true
                    }
                    Key.DirectionRight -> {
                        scope.launch { pager.animateScrollToPage((pager.currentPage + 1).coerceAtMost(urls.lastIndex)) }
                        true
                    }
                    else -> false
                }
            },
        ) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismissRequest, modifier = Modifier.focusRequester(focusRequester)) {
                        Icon(Icons.Rounded.Close, "关闭图片")
                    }
                    Spacer(Modifier.weight(1f))
                    Text("${pager.currentPage + 1} / ${urls.size}")
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    HorizontalPager(
                        state = pager,
                        userScrollEnabled = currentZoomable.transform.scaleX <= currentZoomable.minScale,
                        modifier = Modifier.fillMaxSize(),
                    ) { page ->
                        val selected = page == pager.currentPage
                        val zoomable = zoomStates[page].zoomable
                        var previousMinScale by remember(zoomable) { mutableFloatStateOf(Float.NaN) }
                        LaunchedEffect(
                            selected,
                            zoomable,
                            zoomable.containerSize,
                            zoomable.contentSize,
                            zoomable.baseTransform,
                            zoomable.minScale,
                        ) {
                            previousMinScale = updateImageViewerScale(zoomable, !selected, previousMinScale)
                        }
                        Box(Modifier.fillMaxSize().clipToBounds()) {
                            ShowImage(
                                url = displayUrls[page],
                                contentDescription = "图片 ${page + 1}",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Inside,
                                animationEnabled = selected && !pager.isScrollInProgress,
                                zoomState = zoomStates[page],
                                onTap = onDismissRequest,
                            )
                        }
                    }
                    IconButton(
                        enabled = pager.currentPage > 0,
                        onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } },
                        modifier = Modifier.align(Alignment.CenterStart),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                            "上一张",
                            modifier = Modifier.size(40.dp),
                        )
                    }
                    IconButton(
                        enabled = pager.currentPage < urls.lastIndex,
                        onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                        modifier = Modifier.align(Alignment.CenterEnd),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            "下一张",
                            modifier = Modifier.size(40.dp),
                        )
                    }
                    SnackbarHost(
                        hostState = snackbarHostState,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                if (displayUrls[pager.currentPage] != urls[pager.currentPage]) {
                    TextButton(
                        onClick = { originalPages = originalPages + pager.currentPage },
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        Text("查看原图")
                    }
                }
                Row(Modifier.align(Alignment.CenterHorizontally).padding(vertical = 4.dp)) {
                    IconButton(
                        enabled = currentZoomable.transform.scaleX < currentZoomable.maxScale,
                        onClick = { scope.launch { currentZoomable.scaleBy(1.5f, animated = true) } },
                    ) { Icon(Icons.Rounded.ZoomIn, "放大") }
                    IconButton(
                        enabled = currentZoomable.transform.scaleX > currentZoomable.minScale,
                        onClick = { scope.launch { currentZoomable.scaleBy(1f / 1.5f, animated = true) } },
                    ) { Icon(Icons.Rounded.ZoomOut, "缩小") }
                    IconButton(onClick = { scope.launch { currentZoomable.rotateBy(-90) } }) {
                        Icon(Icons.AutoMirrored.Rounded.RotateLeft, "逆时针旋转")
                    }
                    IconButton(
                        enabled = !saving,
                        onClick = {
                            if (!saving) {
                                val url = urls[pager.currentPage]
                                saving = true
                                snackbarHostState.currentSnackbarData?.dismiss()
                                scope.launch {
                                    val message = try {
                                        if (!imageSaver.requestPermission()) {
                                            "未获得保存图片所需权限"
                                        } else {
                                            val image = fetchImageForSave(url, SingletonImageLoader.get(context), context)
                                            if (imageSaver.saveImage(image)) "图片已保存" else null
                                        }
                                    } catch (error: CancellationException) {
                                        throw error
                                    } catch (error: Exception) {
                                        "图片保存失败：${error.message?.takeIf { it.isNotBlank() } ?: "未知错误"}"
                                    } finally {
                                        saving = false
                                    }
                                    if (message != null) snackbarHostState.showSnackbar(message)
                                }
                            }
                        },
                    ) {
                        Icon(Icons.Rounded.SaveAlt, "保存图片")
                    }
                }
            }
        }
    }
}

private suspend fun updateImageViewerScale(zoomable: ZoomableState, reset: Boolean, previousMinScale: Float): Float {
    // 保留实际解码尺寸用于子采样，只将默认适配补到原图决定的最小倍率。
    val atDefaultSize = previousMinScale.isNaN() || abs(zoomable.transform.scaleX - previousMinScale) < 0.001f
    if (reset) zoomable.reset()
    if (reset || atDefaultSize || zoomable.transform.scaleX < zoomable.minScale) {
        zoomable.scale(zoomable.minScale)
    }
    return zoomable.minScale
}

private fun imageViewerScalesCalculator(originalSize: IntSize): ScalesCalculator {
    if (originalSize.width <= 0 || originalSize.height <= 0) return ScalesCalculator.Dynamic
    return object : ScalesCalculator by ScalesCalculator.Dynamic {
        override fun calculateWithBase(
            containerSize: IntSizeCompat,
            contentSize: IntSizeCompat,
            contentOriginSize: IntSizeCompat,
            contentScale: ContentScaleCompat,
            baseScale: Float,
            initialScale: Float,
        ): ScalesCalculator.Result {
            // 按长短边换算，兼容旋转与预览图等比缩小后的像素取整。
            val originalScale = minOf(
                maxOf(originalSize.width, originalSize.height).toFloat() / maxOf(contentSize.width, contentSize.height),
                minOf(originalSize.width, originalSize.height).toFloat() / minOf(contentSize.width, contentSize.height),
            )
            val defaultScale = minOf(
                originalScale,
                containerSize.width.toFloat() / contentSize.width,
                containerSize.height.toFloat() / contentSize.height,
            )
            return ScalesCalculator.Dynamic.calculateWithBase(
                containerSize = containerSize,
                contentSize = contentSize,
                contentOriginSize = IntSizeCompat(
                    (contentSize.width * originalScale).roundToInt(),
                    (contentSize.height * originalScale).roundToInt(),
                ),
                contentScale = contentScale,
                baseScale = defaultScale,
                initialScale = defaultScale,
            )
        }
    }
}
