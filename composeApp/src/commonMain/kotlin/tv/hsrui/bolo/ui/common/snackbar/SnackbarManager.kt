package tv.hsrui.bolo.ui.common.snackbar

import androidx.compose.material3.SnackbarDuration
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class SnackbarManager {
    private val _messages =
        MutableSharedFlow<Pair<String, SnackbarDuration>>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()

    fun showMessage(message: String, duration: SnackbarDuration = SnackbarDuration.Short) {
        _messages.tryEmit(Pair(message, duration))
    }
}