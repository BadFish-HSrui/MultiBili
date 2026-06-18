package tv.hsrui.bolo.player.base

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import java.awt.Color
import javax.swing.JPanel

@Composable
actual fun BoloVideoPlayer(
    controller: BoloPlayerController,
    modifier: Modifier
) {
    val state by controller.state.collectAsState()

    DisposableEffect(controller) {
        onDispose {
            controller.release()
        }
    }

    SwingPanel(
        factory = {
            controller.mediaPlayerComponent ?: JPanel().apply {
                background = Color.BLACK
            }
        },
        modifier = modifier
    )
}
