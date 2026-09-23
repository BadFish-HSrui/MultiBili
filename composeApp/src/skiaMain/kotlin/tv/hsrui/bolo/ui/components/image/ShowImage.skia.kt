package tv.hsrui.bolo.ui.components.image

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
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
    val factory = remember { SkiaAnimatedImageDecoder.Factory() }
    ShowImage(
        url = url,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        animationEnabled = animationEnabled,
        zoomState = zoomState,
        onTap = onTap,
        decoderFactory = factory,
    ) { image, _, scope ->
        (image as? SkiaAnimatedImage)?.let { SkiaAnimatedImagePainter(it, scope) }
    }
}
