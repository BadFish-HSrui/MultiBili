package tv.hsrui.bolo.player.base

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import java.awt.BorderLayout
import java.awt.Color
import javax.swing.JPanel

@Composable
actual fun BoloVideoPlayer(controller: BoloPlayerController, modifier: Modifier) {
    val host = remember { JPanel(BorderLayout()).apply { background = Color.BLACK } }
    val backend by controller.backend.collectAsState()
    DisposableEffect(controller) {
        controller.outputAttached()
        onDispose { controller.release() }
    }
    LaunchedEffect(backend, host) { backend?.bind(host) }
    SwingPanel(factory = { host }, modifier = modifier)
}
