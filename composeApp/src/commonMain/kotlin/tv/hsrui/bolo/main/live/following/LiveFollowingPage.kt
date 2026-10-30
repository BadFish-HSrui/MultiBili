package tv.hsrui.bolo.main.live.following

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.Flow
import org.koin.compose.koinInject
import tv.hsrui.bolo.ui.common.liveRoomsPage.LiveRoomsGridPage
import tv.hsrui.network.login.storage.LoginStorage

@Composable
fun LiveFollowingPage(
    modifier: Modifier = Modifier,
    reselectEvents: Flow<Unit>? = null,
    isSelected: Boolean = true,
) {
    val loginStorage: LoginStorage = koinInject()
    val accountMid by loginStorage.currentUserMidFlow.collectAsState(
        initial = if (loginStorage.isLoggedIn) loginStorage.cookies.dedeUserID else 0L,
    )
    key(accountMid) {
        val owner = remember {
            object : ViewModelStoreOwner {
                override val viewModelStore = ViewModelStore()
            }
        }
        DisposableEffect(owner) {
            onDispose { owner.viewModelStore.clear() }
        }
        val followingViewModel = viewModel(viewModelStoreOwner = owner) { LiveFollowingViewModel(accountMid) }
        LiveRoomsGridPage(
            viewModel = followingViewModel,
            modifier = modifier,
            reselectEvents = reselectEvents,
            isSelected = isSelected,
            showStatistics = false,
            showOfflineStatus = true,
        )
    }
}