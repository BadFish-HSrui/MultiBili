package tv.hsrui.bolo.ui.components.image

import android.graphics.drawable.Animatable
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.DrawableImage
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import com.github.panpf.zoomimage.CoilZoomState

@Composable
actual fun ShowImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier,
    contentScale: ContentScale,
    animationEnabled: Boolean,
    zoomState: CoilZoomState?,
    onTap: (() -> Unit)?,
) {
    val factory = remember {
        if (Build.VERSION.SDK_INT >= 28) AnimatedImageDecoder.Factory() else GifDecoder.Factory()
    }
    ShowImage(
        url = url,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        animationEnabled = animationEnabled,
        zoomState = zoomState,
        onTap = onTap,
        decoderFactory = factory,
    ) { image, painter, _ ->
        val drawable = (image as? DrawableImage)?.drawable as? Animatable
        drawable?.let {
            object : ImageAnimation {
                override val painter = painter
                private var active: Boolean? = null
                override fun setPlaying(playing: Boolean) {
                    if (active == playing) return
                    active = playing
                    if (playing && !drawable.isRunning) drawable.start()
                    else if (!playing && drawable.isRunning) drawable.stop()
                }
                override fun dispose() = drawable.stop()
            }
        }
    }
}
