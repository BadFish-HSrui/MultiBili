package tv.hsrui.bolo.player.base

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import java.awt.BorderLayout
import java.awt.Color
import javax.swing.JPanel

@Composable
actual fun BoloVideoPlayer(
    controller: BoloPlayerController,
    modifier: Modifier
) {
    val host = remember {
        JPanel(BorderLayout()).apply {
            background = Color.BLACK
        }
    }

    DisposableEffect(controller, host) {
        controller.bindVideo(host)
        onDispose {
            controller.unbindVideo(host)
            controller.release()
        }
    }

    SwingPanel(
        factory = { host },
        update = { controller.bindVideo(it) },
        modifier = modifier
    )
}
