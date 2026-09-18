package tv.hsrui.bolo.ui.components.image

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale

@Composable
actual fun ShowImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier,
    contentScale: ContentScale,
    animationEnabled: Boolean,
) {
    val factory = remember { SkiaAnimatedImageDecoder.Factory() }
    ShowImage(url, contentDescription, modifier, contentScale, animationEnabled, factory) { image, _, scope ->
        (image as? SkiaAnimatedImage)?.let { SkiaAnimatedImagePainter(it, scope) }
    }
}
