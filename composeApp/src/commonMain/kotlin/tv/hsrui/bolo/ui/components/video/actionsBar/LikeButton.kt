package tv.hsrui.bolo.ui.components.video.actionsBar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import multibili.composeapp.generated.resources.Res
import multibili.composeapp.generated.resources.like_icon
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject
import tv.hsrui.bolo.ui.common.snackbar.SnackbarManager
import tv.hsrui.bolo.ui.theme.BiliColor
import tv.hsrui.network.feature.video.VideoInfoData
import tv.hsrui.network.feature.video.actions.like.VideoLikeAction
import tv.hsrui.network.feature.video.actions.like.modifyVideoLike
import tv.hsrui.network.utils.formatCountToString

@Composable
fun LikeButton(
    videoInfo: VideoInfoData,
    isLiked: Boolean,
    reloadState: suspend () -> Unit,
    modifier: Modifier = Modifier
) {
    val action = if (isLiked) VideoLikeAction.UnLike else VideoLikeAction.Like
    val snackbarManager: SnackbarManager = koinInject()
    val scope = rememberCoroutineScope()

    Box(modifier = modifier) {
        Surface(
            onClick = {
                scope.launch {
                    try {
                        val result = modifyVideoLike(avid = videoInfo.avid, action = action)
                        if (!result.isSuccess) {
                            snackbarManager.showMessage("[${result.code}]: ${result.message}")
                        }
                    } catch (e: Exception) {
                        snackbarManager.showMessage(e.toString())
                    } finally {
                        reloadState()
                    }
                }
            },
            color = Color.Transparent
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    painter = painterResource(Res.drawable.like_icon),
                    contentDescription = "点赞",
                    tint = if (isLiked) BiliColor.ThemeColor else Color.Gray,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = videoInfo.stateCount.like.formatCountToString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}