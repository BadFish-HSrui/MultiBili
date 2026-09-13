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
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.skiaCanvas
import androidx.compose.ui.layout.onSizeChanged
import tv.hsrui.bolo.ui.components.error.ShowErrorContent

@Composable
actual fun BoloVideoPlayer(controller: BoloPlayerController, modifier: Modifier) {
    val output = remember(controller) { BoloDesktopVideoOutput() }
    val backend by controller.backend.collectAsState()
    DisposableEffect(controller) {
        controller.outputAttached()
        onDispose { controller.release() }
    }
    LaunchedEffect(controller, output) {
        output.failures.collect { (failedBackend, cause) ->
            output.handleFailure(controller, failedBackend, cause)
        }
    }
    LaunchedEffect(backend, output) { backend?.bind(output) }
    Box(modifier.background(Color.Black).onSizeChanged { output.size = it }) {
        if (output.direct) {
            SwingPanel(factory = { output.panel }, modifier = Modifier.matchParentSize())
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
