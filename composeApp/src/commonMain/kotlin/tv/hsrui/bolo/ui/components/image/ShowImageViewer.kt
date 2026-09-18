package tv.hsrui.bolo.ui.components.image

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material.icons.rounded.ZoomOut
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShowImageViewer(
    urls: List<String>,
    initialIndex: Int,
    onDismissRequest: () -> Unit,
) {
    if (urls.isEmpty()) return
    val pager = rememberPagerState(initialPage = initialIndex.coerceIn(urls.indices)) { urls.size }
    val scope = rememberCoroutineScope()
    var scale by remember(urls, pager.currentPage) { mutableFloatStateOf(1f) }
    var offset by remember(urls, pager.currentPage) { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    fun zoomTo(value: Float) {
        scale = value.coerceIn(1f, 5f)
        offset = clampImageOffset(offset, scale, viewport)
    }

    BasicAlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = Modifier.fillMaxSize(),
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) {
        val focusRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
        Surface(
            color = Color.Black,
            contentColor = Color.White,
            modifier = Modifier.fillMaxSize().onPreviewKeyEvent { event ->
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
                HorizontalPager(
                    state = pager,
                    userScrollEnabled = scale == 1f,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                ) { page ->
                    val selected = page == pager.currentPage
                    val transform = rememberTransformableState { centroid, zoom, pan, _ ->
                        if (selected) {
                            val nextScale = (scale * zoom).coerceIn(1f, 5f)
                            val pivot = if (centroid.isSpecified) {
                                centroid - Offset(viewport.width / 2f, viewport.height / 2f)
                            } else Offset.Zero
                            offset = clampImageOffset(
                                pivot + (offset - pivot) * (nextScale / scale) + pan,
                                nextScale,
                                viewport,
                            )
                            scale = nextScale
                        }
                    }
                    Box(
                        Modifier.fillMaxSize().clipToBounds()
                            .onSizeChanged {
                                viewport = it
                                offset = clampImageOffset(offset, scale, it)
                            }
                            .pointerInput(urls, page) {
                                detectTapGestures(onDoubleTap = { position ->
                                    if (scale > 1f) {
                                        scale = 1f
                                        offset = Offset.Zero
                                    } else {
                                        scale = 2f
                                        offset = clampImageOffset(
                                            Offset(viewport.width / 2f, viewport.height / 2f) - position,
                                            scale,
                                            viewport,
                                        )
                                    }
                                })
                            }
                            .transformable(state = transform, canPan = { scale > 1f }),
                    ) {
                        ShowImage(
                            url = urls[page],
                            contentDescription = "图片 ${page + 1}",
                            animationEnabled = selected && !pager.isScrollInProgress,
                            modifier = Modifier.fillMaxSize().graphicsLayer {
                                scaleX = if (selected) scale else 1f
                                scaleY = scaleX
                                translationX = if (selected) offset.x else 0f
                                translationY = if (selected) offset.y else 0f
                            },
                        )
                    }
                }
                Row(Modifier.align(Alignment.CenterHorizontally).padding(vertical = 4.dp)) {
                    IconButton(
                        enabled = pager.currentPage > 0,
                        onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } },
                    ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "上一张") }
                    IconButton(enabled = scale > 1f, onClick = { zoomTo(scale / 1.5f) }) {
                        Icon(Icons.Rounded.ZoomOut, "缩小")
                    }
                    IconButton(onClick = { scale = 1f; offset = Offset.Zero }) {
                        Icon(Icons.Rounded.RestartAlt, "适应窗口")
                    }
                    IconButton(enabled = scale < 5f, onClick = { zoomTo(scale * 1.5f) }) {
                        Icon(Icons.Rounded.ZoomIn, "放大")
                    }
                    IconButton(
                        enabled = pager.currentPage < urls.lastIndex,
                        onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                    ) { Icon(Icons.AutoMirrored.Rounded.ArrowForward, "下一张") }
                }
            }
        }
    }
}

private fun clampImageOffset(offset: Offset, scale: Float, viewport: IntSize): Offset {
    val maxX = viewport.width * (scale - 1f) / 2f
    val maxY = viewport.height * (scale - 1f) / 2f
    return Offset(offset.x.coerceIn(-maxX, maxX), offset.y.coerceIn(-maxY, maxY))
}
