package tv.hsrui.bolo.ui.common.snackbar

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class SnackbarManager {
    private val _messages =
        MutableSharedFlow<Pair<String, Long>>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()

    fun showMessage(message: String, duration: Long = 1500L) {
        _messages.tryEmit(Pair(message, duration))
    }
}