package tv.hsrui.bolo.ui.components.image

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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.github.panpf.zoomimage.CoilZoomState
import com.github.panpf.zoomimage.rememberCoilZoomState
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
    val zoomStates: List<CoilZoomState> = List(urls.size) { rememberCoilZoomState() }
    val currentZoomable = zoomStates[pager.currentPage].zoomable

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
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    HorizontalPager(
                        state = pager,
                        userScrollEnabled = currentZoomable.transform.scaleX <= currentZoomable.minScale,
                        modifier = Modifier.fillMaxSize(),
                    ) { page ->
                        val selected = page == pager.currentPage
                        val zoomable = zoomStates[page].zoomable
                        LaunchedEffect(selected) { if (!selected) zoomable.reset() }
                        Box(Modifier.fillMaxSize().clipToBounds()) {
                            ShowImage(
                                url = urls[page],
                                contentDescription = "图片 ${page + 1}",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Inside,
                                animationEnabled = selected && !pager.isScrollInProgress,
                                zoomState = zoomStates[page],
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
                }
            }
        }
    }
}
