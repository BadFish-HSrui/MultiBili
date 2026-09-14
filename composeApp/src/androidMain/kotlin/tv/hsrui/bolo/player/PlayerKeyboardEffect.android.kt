package tv.hsrui.bolo.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.input.key.KeyEvent

@Composable
actual fun PlayerKeyboardEffect(
    onKeyEvent: (KeyEvent) -> Boolean,
    onCancel: () -> Unit,
) {
}
