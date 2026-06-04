package tv.hsrui.bolo.ui.common.reply

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import tv.hsrui.bolo.ui.common.snackbar.showSnackbarMessage
import tv.hsrui.bolo.ui.components.error.ShowErrorContent
import tv.hsrui.bolo.ui.components.grid.ShowGridFABMenu
import tv.hsrui.bolo.ui.components.grid.ShowHorizontalCardGrid
import tv.hsrui.bolo.ui.components.reply.ShowReplyCard
import tv.hsrui.bolo.ui.components.reply.ShowReplyInput
import tv.hsrui.bolo.utils.OnGridBottomReached
import tv.hsrui.network.feature.reply.ReplyItem
import tv.hsrui.network.feature.reply.ReplySectionType.Companion.ReplySectionType
import tv.hsrui.network.feature.reply.send.sendSubReply

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RepliesGridPage(
    viewModel: RepliesViewModel,
    uiState: RepliesUiState,
    modifier: Modifier = Modifier
) {
    val sortType by viewModel.sortType.collectAsState()
    val repliesGridState = rememberLazyGridState()
    val subReplyText = remember { mutableStateOf("") }
    var lastSubReplyId by remember { mutableStateOf(0L) }

    repliesGridState.OnGridBottomReached(buffer = 4, isLoading = viewModel.isLoading) {
        viewModel.loadMoreReplies()
    }

    PullToRefreshBox(
        isRefreshing = false,
        onRefresh = {
            viewModel.refreshReplies()
        }
    ) {
        when (uiState) {
            is RepliesUiState.Loading -> {
                Box(
                    modifier = modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            is RepliesUiState.Error -> {
                ShowErrorContent(
                    message = uiState.message,
                    retry = { viewModel.refreshReplies() }
                )
            }

            is RepliesUiState.Success -> {
                var replyTarget by remember { mutableStateOf<ReplyItem?>(null) }
                val scope = rememberCoroutineScope()

                Column {
                    Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                        Spacer(Modifier.weight(1F))
                        Surface(
                            onClick = { viewModel.nextSortType() },
                            modifier = Modifier.height(24.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Sort,
                                    contentDescription = "排序方式",
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = sortType.sortTitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    Box {
                        ShowHorizontalCardGrid(
                            cards = uiState.replies,
                            keySelector = { it.rpid },
                            gridState = repliesGridState
                        ) { reply ->
                            ShowReplyCard(
                                replyInfo = reply,
                                sendReply = { replyTarget = reply },
                                updateReply = { viewModel.updateReply(it) }
                            )
                        }
                        ShowGridFABMenu(
                            onBackToTop = {
                                scope.launch {
                                    repliesGridState.animateScrollToItem(0)
                                }
                            },
                            onRefresh = { viewModel.refreshReplies() },
                            modifier = Modifier.align(Alignment.BottomEnd),
                        )
                    }
                }

                replyTarget?.let { target ->
                    if (lastSubReplyId != target.rpid) {
                        subReplyText.value = ""
                        lastSubReplyId = target.rpid
                    }

                    ShowReplyInput(
                        text = subReplyText,
                        labelText = "回复 @${target.userName}",
                        onSend = { message ->
                            scope.launch {
                                try {
                                    val result = sendSubReply(
                                        message = message,
                                        replySection = ReplySectionType(
                                            target.typeCode,
                                            target.oid
                                        ) ?: error("无法处理的评论类型"),
                                        targetReply = target
                                    )
                                    if (result.isSuccess) {
                                        viewModel.updateReply(target.copy(replyCount = target.replyCount + 1))
                                        subReplyText.value = ""
                                        showSnackbarMessage("评论发送成功")
                                    } else {
                                        showSnackbarMessage("[${result.code}]: ${result.message}")
                                    }
                                } catch (e: Exception) {
                                    showSnackbarMessage(e.message ?: "其他网络错误")
                                } finally {
                                    replyTarget = null
                                }
                            }
                        },
                        onDismiss = { replyTarget = null }
                    )
                }
            }
        }
    }
}