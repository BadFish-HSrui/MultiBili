package tv.hsrui.bolo.ui.components.image

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import coil3.Image
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.decode.Decoder
import coil3.request.ImageRequest
import kotlinx.coroutines.CoroutineScope

@Composable
expect fun ShowImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    animationEnabled: Boolean = true,
)

internal interface ImageAnimation {
    val painter: Painter
    val failed: Boolean get() = false
    fun setPlaying(playing: Boolean)
    fun dispose()
}

@Composable
internal fun ShowImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier,
    contentScale: ContentScale,
    animationEnabled: Boolean,
    decoderFactory: Decoder.Factory,
    createAnimation: (Image, Painter, CoroutineScope) -> ImageAnimation?,
) {
    var retry by remember(url) { mutableStateOf(0) }
    key(url, retry) {
        val context = LocalPlatformContext.current
        val scope = rememberCoroutineScope()
        val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
        val windowFocused = LocalWindowInfo.current.isWindowFocused
        var visible by remember { mutableStateOf(false) }
        var state by remember { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }
        var animation by remember { mutableStateOf<ImageAnimation?>(null) }
        val playing by rememberUpdatedState(
            animationEnabled && visible && windowFocused && lifecycle.isAtLeast(Lifecycle.State.RESUMED)
        )
        val request = remember(url, decoderFactory, context) {
            ImageRequest.Builder(context)
                .data(url)
                .memoryCacheKey("bolo-image:$url")
                .decoderFactory(decoderFactory)
                .build()
        }
        DisposableEffect(animation) {
            val current = animation
            onDispose { current?.dispose() }
        }
        SideEffect { animation?.setPlaying(playing) }

        Box(
            modifier = modifier.onGloballyPositioned { visible = !it.boundsInWindow().isEmpty },
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = request,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
                transform = { incoming ->
                    if (incoming is AsyncImagePainter.State.Success) {
                        val controller = createAnimation(incoming.result.image, incoming.painter, scope)
                        animation = controller
                        incoming.copy(painter = controller?.painter ?: incoming.painter)
                    } else incoming
                },
                onState = {
                    state = it
                    animation?.setPlaying(playing)
                },
            )
            when {
                animation?.failed == true -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("图片加载失败")
                    TextButton(onClick = { retry++ }) { Text("重试") }
                }
                state is AsyncImagePainter.State.Error -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("图片加载失败")
                    TextButton(onClick = { retry++ }) { Text("重试") }
                }
                state is AsyncImagePainter.State.Empty || state is AsyncImagePainter.State.Loading -> CircularProgressIndicator()
            }
        }
    }
}
