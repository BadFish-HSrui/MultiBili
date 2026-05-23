package tv.hsrui.bolo.ui.components.video.actionsBar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.koin.compose.koinInject
import tv.hsrui.bolo.ui.common.snackbar.SnackbarManager
import tv.hsrui.network.feature.video.VideoInfoData
import tv.hsrui.network.feature.video.actions.state.VideoActionsStateResponse
import tv.hsrui.network.feature.video.actions.state.fetchVideoActionsStateFor

@Composable
fun VideoActionsBar(videoInfo: VideoInfoData, modifier: Modifier = Modifier) {
    var actionsState by rememberSerializable { mutableStateOf(VideoActionsStateResponse()) }
    val snackbarManager: SnackbarManager = koinInject()

    LaunchedEffect(Unit) {
        try {
            val result = fetchVideoActionsStateFor(videoInfo.bvid)
            println(result.isFavoured)
            if (actionsState.isSuccess) {
                actionsState = result
            } else {
                snackbarManager.showMessage(actionsState.message)
            }
        } catch (e: Exception) {
            snackbarManager.showMessage(e.message ?: "其他网络错误")
        }
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        LikeButton(
            likeCount = videoInfo.stateCount.like,
            isLiked = actionsState.isLiked,
        )
        CoinButton(
            coinCount = videoInfo.stateCount.coin,
            hasCoin = actionsState.hasCoin
        )
        FavoriteButton(
            favoriteCount = videoInfo.stateCount.favorite,
            isFavoured = actionsState.isFavoured
        )
    }
}