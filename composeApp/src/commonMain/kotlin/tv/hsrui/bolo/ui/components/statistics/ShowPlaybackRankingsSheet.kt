package tv.hsrui.bolo.ui.components.statistics

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.datetime.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShowPlaybackRankingsSheet(
    onDismissRequest: () -> Unit,
    dateRange: ClosedRange<LocalDate>? = null,
) {
    key(dateRange) {
        val owner = remember {
            object : ViewModelStoreOwner {
                override val viewModelStore = ViewModelStore()
            }
        }
        val model = viewModel(viewModelStoreOwner = owner) { PlaybackRankingsViewModel(dateRange, limit = 20) }
        val state by model.uiState.collectAsStateWithLifecycle()
        DisposableEffect(model, owner) {
            model.load()
            onDispose {
                model.cancelLoading()
                owner.viewModelStore.clear()
            }
        }
        LifecycleEventEffect(Lifecycle.Event.ON_STOP, onEvent = onDismissRequest)
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            sheetState = rememberBottomSheetState(SheetValue.Hidden, setOf(SheetValue.Hidden, SheetValue.Expanded)),
        ) {
            ShowPlaybackRankings(
                state = state,
                onRetry = model::load,
                dateRange = dateRange,
                modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f).padding(horizontal = 16.dp)
                    .nestedScroll(statisticsSheetNestedScrollConnection),
                showRank = false,
                cardShape = MaterialTheme.shapes.medium.copy(bottomStart = CornerSize(0.dp), bottomEnd = CornerSize(0.dp)),
                segmentedButtonAlignment = Alignment.CenterHorizontally,
                fillRemainingHeight = true,
                showTitle = false,
            )
        }
    }
}

// 只消耗列表处理后的竖向余量，防止滚动到边界时带动 Sheet；顶部直接拖动仍由 Sheet 处理。
internal val statisticsSheetNestedScrollConnection = object : NestedScrollConnection {
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
        Offset(0f, available.y)

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
        Velocity(0f, available.y)
}
