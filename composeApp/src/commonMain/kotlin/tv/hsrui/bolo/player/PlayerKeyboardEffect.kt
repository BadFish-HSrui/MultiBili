package tv.hsrui.bolo.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.input.key.KeyEvent

@Composable
expect fun PlayerKeyboardEffect(
    onKeyEvent: (KeyEvent) -> Boolean,
    onCancel: () -> Unit,
)
