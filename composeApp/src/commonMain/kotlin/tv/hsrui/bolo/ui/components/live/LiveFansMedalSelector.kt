package tv.hsrui.bolo.ui.components.live

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import tv.hsrui.network.liveFeature.danmaku.LiveDanmakuMedalResourcesData
import tv.hsrui.network.liveFeature.message.fansMedal.LiveFansMedalItemData
import tv.hsrui.network.liveFeature.message.fansMedal.LiveFansMedalManager
import tv.hsrui.network.liveFeature.message.fansMedal.LiveFansMedalState

private const val FANS_MEDAL_SCALE = 1.2f

@Composable
fun ShowLiveFansMedalSelector(manager: LiveFansMedalManager, enabled: Boolean, modifier: Modifier = Modifier) {
    val state by manager.state.collectAsState()
    var expanded by remember(manager) { mutableStateOf(false) }
    var completedOperation by remember(manager) { mutableStateOf(state.operationRevision) }
    val scroll = rememberScrollState()
    val threshold = with(LocalDensity.current) { 64.dp.roundToPx() }
    LaunchedEffect(state.operationRevision) {
        if (completedOperation != state.operationRevision) expanded = false
        completedOperation = state.operationRevision
    }
    LaunchedEffect(enabled) { if (!enabled) expanded = false }
    LaunchedEffect(state.listRevision) { scroll.scrollTo(0) }
    LaunchedEffect(
        expanded, state.currentPage, state.hasMore, state.isLoadingList,
        state.isLoadingMore, state.isChanging, state.listError, state.loadMoreError,
    ) {
        val canLoadMore = expanded && state.hasMore && !state.isLoadingList && !state.isLoadingMore &&
            !state.isChanging && state.listError == null && state.loadMoreError == null
        if (!canLoadMore) return@LaunchedEffect
        snapshotFlow { scroll.maxValue != Int.MAX_VALUE && scroll.value >= scroll.maxValue - threshold }
            .distinctUntilChanged().collect { if (it) manager.loadMore() }
    }
    Box(modifier) {
        TextButton(
            onClick = {
                expanded = true
                manager.refresh()
            },
            enabled = enabled && !state.isChanging,
        ) {
            val medal = state.currentMedal
            if (medal != null) {
                ShowLiveFansMedal(
                    medal = medal,
                    guardPainter = rememberLiveDecorationPainter(state.resources.iconUrl(medal)),
                    scale = FANS_MEDAL_SCALE,
                )
            } else Text(
                when {
                    state.isChanging -> "切换中…"
                    state.isCurrentLoaded -> "选择勋章"
                    state.currentError != null -> "勋章加载失败"
                    else -> "加载勋章…"
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            scrollState = scroll,
            modifier = Modifier.widthIn(min = 220.dp, max = 340.dp).heightIn(max = 300.dp),
        ) {
            LiveFansMedalMenuContent(
                state = state,
                enabled = enabled,
                onSelect = { if (it.isWorn) expanded = false else manager.wear(it.medalId) },
                onTakeOff = manager::takeOff,
                onRefresh = manager::refresh,
                onLoadMore = manager::loadMore,
            )
        }
    }
}

@Composable
internal fun ColumnScope.LiveFansMedalMenuContent(
    state: LiveFansMedalState,
    enabled: Boolean,
    onSelect: (LiveFansMedalItemData) -> Unit,
    onTakeOff: () -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
) {
    val canSelect = enabled && !state.isChanging
    val isLoading = state.isLoadingList || state.isLoadingMore
    LiveFansMedalMenuItem(
        item = null,
        resources = state.resources,
        enabled = canSelect,
        selected = state.currentPage > 0 && !state.hasMore && state.items.none { it.isWorn },
        onClick = onTakeOff,
    )
    state.items.forEach { item ->
        LiveFansMedalMenuItem(
            item = item,
            resources = state.resources,
            enabled = canSelect,
            onClick = { onSelect(item) },
        )
    }
    if (isLoading || state.isChanging) {
        Text(
            if (state.isChanging) "正在切换勋章…" else "正在加载勋章…",
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall,
        )
    } else if (state.currentPage > 0 && state.items.isEmpty() && state.listError == null) {
        Text("暂无粉丝勋章", Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
    }
    val error = state.operationError ?: state.listError ?: state.currentError
    if (error != null) {
        Text(
            text = error,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
        DropdownMenuItem(text = { Text("刷新状态") }, onClick = onRefresh, enabled = !state.isChanging)
    }
    state.loadMoreError?.let { message ->
        Text(
            text = message,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
        DropdownMenuItem(text = { Text("重试加载更多") }, onClick = onLoadMore, enabled = !state.isChanging)
    }
}

@Composable
internal fun LiveFansMedalMenuItem(
    item: LiveFansMedalItemData?,
    resources: LiveDanmakuMedalResourcesData,
    enabled: Boolean,
    selected: Boolean = item?.isWorn == true,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        modifier = Modifier.background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .semantics { this.selected = selected },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(108.dp), contentAlignment = Alignment.CenterStart) {
                    ShowLiveFansMedal(
                        medal = item?.medal,
                        guardPainter = rememberLiveDecorationPainter(item?.medal?.let(resources::iconUrl)),
                        scale = FANS_MEDAL_SCALE,
                    )
                }
                Text(
                    item?.anchorName ?: "不佩戴",
                    modifier = Modifier.weight(1f, fill = false).alpha(0.75f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        onClick = onClick,
        enabled = enabled,
    )
}
