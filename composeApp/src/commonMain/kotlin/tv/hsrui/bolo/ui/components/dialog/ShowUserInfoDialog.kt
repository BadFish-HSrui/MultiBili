package tv.hsrui.bolo.ui.components.dialog

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import tv.hsrui.bolo.navigation.openUserSpace
import tv.hsrui.bolo.ui.components.user.ShowUserInfoBar

@Composable
fun ShowUserInfoDialog(mid: Long, onDismissRequest: () -> Unit) {
    key(mid) {
        val owner = remember {
            object : ViewModelStoreOwner {
                override val viewModelStore = ViewModelStore()
            }
        }
        DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
        LifecycleEventEffect(Lifecycle.Event.ON_STOP, onEvent = onDismissRequest)

        CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
            ShowInfoDialog(
                onConfirm = onDismissRequest,
                scrollableContent = true,
                modifier = Modifier.padding(horizontal = 20.dp).widthIn(max = 640.dp).fillMaxWidth(),
                properties = DialogProperties(usePlatformDefaultWidth = false),
            ) {
                ShowUserInfoBar(
                    mid = mid,
                    modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
                    onUserClick = { openUserSpace(mid) },
                )
            }
        }
    }
}
