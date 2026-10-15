package tv.hsrui.bolo.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.input.key.KeyEvent
import tv.hsrui.bolo.player.settings.PlayerShortcutBinding

@Composable
actual fun PlayerKeyboardEffect(
    bindings: Map<String, List<PlayerShortcutBinding?>>,
    onKeyEvent: (KeyEvent) -> Boolean,
    onCancel: () -> Unit,
) {
}
