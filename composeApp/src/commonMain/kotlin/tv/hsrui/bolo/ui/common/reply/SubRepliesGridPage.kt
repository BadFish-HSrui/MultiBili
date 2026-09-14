package tv.hsrui.bolo.ui.common.reply

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import tv.hsrui.bolo.ui.common.snackbar.showSnackbarMessage
import tv.hsrui.bolo.ui.components.error.ShowErrorContent
import tv.hsrui.bolo.ui.components.grid.ShowHorizontalCardGrid
import tv.hsrui.bolo.ui.components.reply.ShowReplyInput
import tv.hsrui.bolo.ui.components.reply.ShowSubReply
import tv.hsrui.bolo.utils.OnGridBottomReached
import tv.hsrui.network.feature.reply.ReplyItem
import tv.hsrui.network.feature.reply.send.sendSubReply
import tv.hsrui.network.login.storage.LoginStorage
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun SubRepliesGridPage(
    viewModel: SubRepliesViewModel,
    uiState: SubRepliesUiState,
    upMid: Long,
    modifier: Modifier = Modifier
) {
    val subRepliesGridState = rememberLazyGridState()
    val loginStorage: LoginStorage = koinInject()

    subRepliesGridState.OnGridBottomReached(buffer = 4, isLoading = viewModel.isLoading) {
        viewModel.loadMoreSubReplies()
    }

    PullToRefreshBox(
        isRefreshing = false,
        onRefresh = {
            viewModel.refreshSubReplies()
        }
    ) {
        when (uiState) {
            is SubRepliesUiState.Loading -> {
                Box(
                    modifier = modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            is SubRepliesUiState.Error -> {
                ShowErrorContent(
                    message = uiState.message,
                    retry = { viewModel.refreshSubReplies() }
                )
            }

            is SubRepliesUiState.Success -> {
                val subReplyText = rememberSaveable { mutableStateOf("") }
                var lastSubReplyId by rememberSaveable { mutableStateOf(0L) }
                var replyTarget by remember { mutableStateOf<ReplyItem?>(null) }
                val scope = rememberCoroutineScope()

                Box {
                    ShowHorizontalCardGrid(
                        cards = uiState.subReplies,
                        keySelector = { it.rpid },
                        gridState = subRepliesGridState,
                        noContentPadding = true,
                        noContentSpacing = true,
                        topContent = {
                            ShowSubReply(
                                replyInfo = uiState.rootReply,
                                isUpReply = (uiState.rootReply.userMid == upMid),
                                sendReply = { replyTarget = uiState.rootReply },
                                updateReply = { viewModel.updateReply(it) },
                                isTop = true
                            )
                        },
                        bottomContent = if (!loginStorage.isLoggedIn) {
                            {
                                Text(
                                    text = "查看更多评论需要登录",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 16.dp)
                                )
                            }
                        } else null
                    ) { subReply ->
                        ShowSubReply(
                            replyInfo = subReply,
                            isUpReply = (subReply.userMid == upMid),
                            sendReply = { replyTarget = subReply },
                            updateReply = { viewModel.updateReply(it) }
                        )
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
                                            replySection = viewModel.replySection,
                                            targetReply = target
                                        )
                                        if (result.isSuccess) {
                                            delay(200.milliseconds)
                                            subReplyText.value = ""
                                            showSnackbarMessage("评论发送成功")
                                            viewModel.refreshSubReplies()
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
}