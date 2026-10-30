package tv.hsrui.bolo.ui.common.player.danmaku

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import org.koin.compose.koinInject
import tv.hsrui.bolo.player.VideoPlayerViewModel
import tv.hsrui.bolo.player.settings.BoloPlayerSettings
import tv.hsrui.bolo.ui.common.snackbar.SnackbarManager
import tv.hsrui.network.login.storage.LoginStorage

enum class PlayerDanmakuTab {
    List,
    Filters,
}

@Composable
fun PlayerDanmakuPage(
    viewModelStoreOwner: ViewModelStoreOwner,
    playerViewModel: VideoPlayerViewModel?,
    mediaKey: Pair<Long, Long>?,
    modifier: Modifier = Modifier,
) {
    val settings: BoloPlayerSettings = koinInject()
    val loginStorage: LoginStorage = koinInject()
    val snackbarManager: SnackbarManager = koinInject()
    val filterViewModel = viewModel(viewModelStoreOwner = viewModelStoreOwner) {
        DanmakuFilterViewModel(
            readFilters = { settings.danmaku.filterList },
            writeFilters = { settings.danmaku.filterList = it },
            isLoggedIn = { loginStorage.isLoggedIn },
            showMessage = { snackbarManager.showMessage(it) },
        )
    }
    var selectedTab by rememberSaveable { mutableStateOf(PlayerDanmakuTab.List) }
    // 列表状态随媒体重置，屏蔽管理状态随播放路由保留。
    val filterStateHolder = rememberSaveableStateHolder()
    val listStateHolder = key(mediaKey) { rememberSaveableStateHolder() }
    val currentPlayer = playerViewModel?.takeIf { (it.avid to it.cid) == mediaKey }
    val loadedItems = currentPlayer?.loadedDanmaku?.collectAsState()?.value.orEmpty()

    Column(modifier.fillMaxSize()) {
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .widthIn(max = 400.dp)
                .fillMaxWidth()
                .align(Alignment.CenterHorizontally),
        ) {
            PlayerDanmakuTab.entries.forEachIndexed { index, tab ->
                SegmentedButton(
                    selected = selectedTab == tab,
                    onClick = { selectedTab = tab },
                    shape = SegmentedButtonDefaults.itemShape(index, PlayerDanmakuTab.entries.size),
                    icon = {},
                ) {
                    Text(
                        text = when (tab) {
                            PlayerDanmakuTab.List -> "弹幕列表"
                            PlayerDanmakuTab.Filters -> "屏蔽管理"
                        },
                        maxLines = 1,
                    )
                }
            }
        }
        when (selectedTab) {
            PlayerDanmakuTab.List -> key(mediaKey) {
                listStateHolder.SaveableStateProvider(PlayerDanmakuTab.List.name) {
                    DanmakuListPage(
                        items = loadedItems,
                        viewModel = filterViewModel,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            PlayerDanmakuTab.Filters -> filterStateHolder.SaveableStateProvider(PlayerDanmakuTab.Filters.name) {
                DanmakuFilterPage(viewModel = filterViewModel, modifier = Modifier.weight(1f))
            }
        }
    }
}
