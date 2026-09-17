package tv.hsrui.bolo.favorite

import tv.hsrui.bolo.navigation.openVideoList
import tv.hsrui.bolo.view.video.VideoPlaybackRequest
import tv.hsrui.network.feature.video.list.VideoListType
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import tv.hsrui.bolo.navigation.openFavoriteFolder
import tv.hsrui.bolo.ui.theme.BoloShapes
import tv.hsrui.network.feature.favorite.FavoriteFolderInfoData

@Composable
internal fun FavoriteFolderCard(
    folder: FavoriteFolderInfoData,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = { openFavoriteFolder(folder.id) },
        enabled = folder.id > 0,
        shape = BoloShapes.InfoCard.Default,
        modifier = modifier
            .fillMaxWidth()
            .height(88.dp)
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(16F / 9F)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(32.dp)
                )
                AsyncImage(
                    model = folder.coverUrl,
                    contentDescription = "收藏夹封面",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Box(
                modifier = Modifier
                    .weight(1F)
                    .fillMaxHeight()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (folder.isPrivate) {
                            Icon(
                                imageVector = Icons.Rounded.Lock,
                                contentDescription = "私密收藏夹",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            text = folder.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1F)
                        )
                    }
                    Text(
                        text = "${folder.mediaCount} 个内容",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                    )
                }
            }
            IconButton(
                onClick = { openVideoList(VideoPlaybackRequest.VideoList(VideoListType.Favorite, folder.id)) },
                enabled = folder.id > 0 && folder.mediaCount > 0,
                modifier = Modifier.align(Alignment.CenterVertically),
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = "播放收藏夹", modifier = Modifier.size(24.dp))
            }
        }
    }
}
