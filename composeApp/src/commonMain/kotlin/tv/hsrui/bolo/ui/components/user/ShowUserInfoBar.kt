package tv.hsrui.bolo.ui.components.user

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import tv.hsrui.bolo.ui.components.error.ShowErrorContent
import tv.hsrui.network.utils.formatCountToString

@Composable
fun ShowUserInfoBar(mid: Long, modifier: Modifier = Modifier, refreshKey: Int = 0) {
    val viewModel = viewModel(key = "UserInfoBar:$mid") { UserInfoBarViewModel(mid) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    DisposableEffect(viewModel, refreshKey) {
        viewModel.loadUserInfo()
        onDispose { viewModel.cancelLoading() }
    }

    when (val state = uiState) {
        UserInfoBarUiState.Loading -> Box(
            modifier = modifier.fillMaxWidth().padding(vertical = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
        is UserInfoBarUiState.Error -> ShowErrorContent(
            message = state.message,
            retry = viewModel::loadUserInfo,
            modifier = modifier,
        )
        is UserInfoBarUiState.Success -> ShowUserInfoLayout(
            face = state.info.face,
            name = state.info.name,
            sign = state.info.sign,
            level = state.info.level,
            levelString = state.info.levelString,
            isVip = state.info.isVip,
            vipTypeString = "大会员",
            modifier = modifier,
            shape = CardDefaults.shape,
        ) {
            Layout(
                modifier = Modifier.fillMaxWidth(),
                content = {
                    listOf(
                        "关注数" to state.following,
                        "粉丝数" to state.follower,
                        "获赞数" to state.likeCount,
                        "播放数" to state.playCount,
                    ).forEach { (label, count) ->
                        Text(
                            text = "$label: ${count?.formatCountToString() ?: "--"}",
                            modifier = Modifier.alpha(0.8F),
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
            ) { measurables, constraints ->
                val itemWidth = measurables.maxOf { it.maxIntrinsicWidth(Constraints.Infinity) }
                val width = constraints.constrainWidth(itemWidth * 4)
                val columns = if (itemWidth <= width / 4) 4 else 2
                val placeables = measurables.map {
                    it.measure(
                        constraints.copy(
                            minWidth = width / columns,
                            maxWidth = width / columns,
                            minHeight = 0,
                        ),
                    )
                }
                val rowHeight = placeables.maxOf { it.height }
                val rowSpacing = 4.dp.roundToPx()
                val rows = placeables.size / columns
                layout(width, constraints.constrainHeight(rowHeight * rows + rowSpacing * (rows - 1))) {
                    placeables.forEachIndexed { index, placeable ->
                        placeable.placeRelative(
                            x = index % columns * (width / columns),
                            y = index / columns * (rowHeight + rowSpacing),
                        )
                    }
                }
            }
        }
    }
}
