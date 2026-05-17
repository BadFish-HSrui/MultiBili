package tv.hsrui.bolo.ui.components.video

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import tv.hsrui.bolo.ui.common.snackbar.SnackbarManager
import tv.hsrui.bolo.ui.components.dialog.ShowConfirmDialog
import tv.hsrui.bolo.ui.theme.BiliColor
import tv.hsrui.bolo.ui.theme.BoloShapes
import tv.hsrui.network.utils.formatToDateTime
import tv.hsrui.network.utils.formatToDuration
import tv.hsrui.network.feature.history.HistoryVideoCard
import tv.hsrui.network.feature.history.HistoryVideoCardExample
import tv.hsrui.network.feature.history.deleteHistory

@Composable
fun ShowHistoryVideoCard(
    videoInfo: HistoryVideoCard,
    onDeleted: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.widthIn(max = 512.dp), shape = BoloShapes.InfoCard.Default) {
        Row {
            Box(modifier = Modifier.aspectRatio(16F / 9F)) {
                AsyncImage(
                    model = videoInfo.coverUrl + "@320w_180h_1c.webp",
                    contentDescription = "视频封面",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                CompositionLocalProvider(
                    LocalContentColor provides Color.White,
                    LocalTextStyle provides TextStyle(fontSize = 12.sp)
                ) {
                    LinearProgressIndicator(
                        progress = { videoInfo.watchProgress.toFloat() / videoInfo.duration.toFloat() },
                        modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter),
                        color = BiliColor.ThemeColor,
                        trackColor = Color.Transparent,
                        drawStopIndicator = {}
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(4.dp)
                            .align(Alignment.TopEnd)
                    ) {
                        Text(
                            text = "${videoInfo.watchProgress.formatToDuration()}/${videoInfo.duration.formatToDuration()}",
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            Box(Modifier.fillMaxSize().padding(4.dp)) {
                Column(Modifier.align(Alignment.TopStart)) {
                    Text(
                        text = videoInfo.title,
                        fontSize = 13.sp,
                        lineHeight = 16.sp,
                        maxLines = if (videoInfo.subtitle.isEmpty()) 2 else 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (videoInfo.subtitle.isNotEmpty()) {
                        Text(
                            text = videoInfo.subtitle,
                            fontSize = 12.sp,
                            lineHeight = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.alpha(0.75F)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.align(Alignment.BottomStart).alpha(0.75F)
                ) {
                    AsyncImage(
                        model = videoInfo.upAvatarUrl + "@64w_64h.webp",
                        contentDescription = null,
                        modifier = Modifier.clip(CircleShape).size(32.dp),
                        contentScale = ContentScale.Crop
                    )
                    Column(modifier = Modifier.padding(start = 4.dp)) {
                        Text(
                            text = videoInfo.upName,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1
                        )
                        Text(
                            text = videoInfo.watchTime.formatToDateTime(),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1
                        )
                    }
                }

                var showDialog by rememberSaveable { mutableStateOf(false) }
                val coroutineScope = rememberCoroutineScope()
                IconButton(
                    onClick = { showDialog = true },
                    modifier = Modifier.size(24.dp).align(Alignment.BottomEnd)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteForever,
                        contentDescription = "删除记录",
                        modifier = Modifier.size(20.dp).alpha(0.5F)
                    )
                }
                if (showDialog) {
                    val snackbarManager: SnackbarManager = koinInject()
                    ShowConfirmDialog(
                        title = { Text("删除历史记录") },
                        onCancel = { showDialog = false },
                        onConfirm = {
                            coroutineScope.launch {
                                try {
                                    val result = deleteHistory(
                                        typeString = videoInfo.typeString,
                                        id = videoInfo.avid
                                    )
                                    if (result.isSuccess) {
                                        onDeleted(videoInfo.avid)
                                    } else {
                                        snackbarManager.showMessage(result.message)
                                    }
                                } catch (e: Exception) {
                                    snackbarManager.showMessage(e.toString())
                                } finally {
                                    showDialog = false
                                }
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null
                            )
                        },
                        text = buildString {
                            appendLine("确认删除:")
                            appendLine(videoInfo.title)
                            appendLine()
                            append("*会同时删除所有设备上的记录*")
                        }
                    )
                }
            }
        }
    }
}

@Preview(heightDp = 128)
@Composable
fun PreviewHistoryVideoCard() {
    ShowHistoryVideoCard(HistoryVideoCardExample, onDeleted = {})
}