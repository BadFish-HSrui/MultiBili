package tv.hsrui.bolo.player.base

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.skiaCanvas
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.boundsInWindow
import tv.hsrui.bolo.ui.components.error.ShowErrorContent

@Composable
actual fun BoloVideoPlayer(controller: BoloPlayerOutputController, modifier: Modifier) {
    val output = remember(controller) { BoloDesktopVideoOutput() }
    val backend by controller.backend.collectAsState()
    DisposableEffect(controller) {
        controller.outputAttached()
        onDispose {
            output.updateGeometry(Rect.Zero, false)
            controller.outputDetached(output)
        }
    }
    LaunchedEffect(controller, output) {
        output.failures.collect { (failedBackend, source, cause) ->
            output.handleFailure(controller, failedBackend, source, cause)
        }
    }
    LaunchedEffect(backend, output) {
        backend?.let {
            it.bind(output)
            if (output.error == null) controller.outputAttached()
        }
    }
    Box(modifier.background(Color.Black).onSizeChanged { output.size = it }.onGloballyPositioned {
        val position = it.positionInWindow()
        val clipped = it.boundsInWindow()
        output.updateGeometry(Rect(position.x, position.y, position.x + it.size.width, position.y + it.size.height),
            clipped.width > 0 && clipped.height > 0, clipped)
    }) {
        if (output.direct) {
            Canvas(Modifier.matchParentSize()) { drawRect(Color.Transparent, blendMode = BlendMode.Clear) }
        } else {
            Canvas(Modifier.matchParentSize()) {
                drawIntoCanvas { output.draw(it.skiaCanvas, size.width, size.height) }
            }
        }
        output.error?.let { message ->
            ShowErrorContent(message = message, retry = { controller.rebuild() })
        }
    }
}
