package tv.hsrui.bolo.ui.components.video.actionsBar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import multibili.composeapp.generated.resources.Res
import multibili.composeapp.generated.resources.favorite_icon
import org.jetbrains.compose.resources.painterResource
import tv.hsrui.bolo.ui.theme.BiliColor
import tv.hsrui.network.feature.video.VideoInfoData
import tv.hsrui.network.utils.formatCountToString

@Composable
fun FavoriteButton(
    videoInfo: VideoInfoData,
    isFavorite: Boolean,
    canClick: Boolean,
    reloadState: suspend () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        Surface(
            onClick = {},
            color = Color.Transparent
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    painter = painterResource(Res.drawable.favorite_icon),
                    contentDescription = "点赞",
                    tint = if (isFavorite) BiliColor.ThemeColor else Color.Gray,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = videoInfo.stateCount.favorite.formatCountToString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}