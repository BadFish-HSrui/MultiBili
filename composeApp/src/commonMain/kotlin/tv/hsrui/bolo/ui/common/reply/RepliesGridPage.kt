package tv.hsrui.bolo.ui.common.reply

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import tv.hsrui.bolo.ui.common.snackbar.showSnackbarMessage
import tv.hsrui.bolo.ui.components.error.ShowErrorContent
import tv.hsrui.bolo.ui.components.grid.ShowGridFABMenu
import tv.hsrui.bolo.ui.components.grid.ShowHorizontalCardGrid
import tv.hsrui.bolo.ui.components.reply.ShowReplyCard
import tv.hsrui.bolo.ui.components.reply.ShowReplyInput
import tv.hsrui.bolo.utils.OnGridBottomReached
import tv.hsrui.bolo.utils.isMedium
import tv.hsrui.network.feature.reply.ReplyItem
import tv.hsrui.network.feature.reply.ReplySort
import tv.hsrui.network.feature.reply.send.sendRootReply
import tv.hsrui.network.feature.reply.send.sendSubReply
import tv.hsrui.network.login.storage.LoginStorage
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun RepliesGridPage(
    viewModel: RepliesViewModel,
    uiState: RepliesUiState,
    modifier: Modifier = Modifier,
    upMid: Long = 0L
) {
    val sortType by viewModel.sortType.collectAsState()
    val repliesGridState = rememberLazyGridState()
    val staggeredGridState = rememberLazyStaggeredGridState()
    val activeStaggeredGridState = staggeredGridState.takeIf { isMedium() }
    val loginStorage: LoginStorage = koinInject()

    repliesGridState.OnGridBottomReached(
        buffer = 4,
        isLoading = viewModel.isLoading,
        staggeredGridState = activeStaggeredGridState
    ) {
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
                var inputRootReply by remember { mutableStateOf(false) }
                val rootReplyText = rememberSaveable { mutableStateOf("") }
                val subReplyText = rememberSaveable { mutableStateOf("") }
                var lastSubReplyId by rememberSaveable { mutableStateOf(0L) }
                var replyTarget by remember { mutableStateOf<ReplyItem?>(null) }
                val scope = rememberCoroutineScope()
                val subRepliesSurfaceState = rememberSubRepliesSurfaceState()
                val subRepliesBackState = rememberNavigationEventState(NavigationEventInfo.None)
                val latestPredictiveBackProgress = remember { FloatArray(1) }
                val predictiveBackProgress =
                    when (val state = subRepliesBackState.transitionState) {
                        is NavigationEventTransitionState.InProgress -> state.latestEvent.progress
                        is NavigationEventTransitionState.Idle -> null
                    }

                SideEffect {
                    predictiveBackProgress?.let { progress ->
                        latestPredictiveBackProgress[0] = progress
                    }
                }

                NavigationBackHandler(
                    state = subRepliesBackState,
                    isBackEnabled = subRepliesSurfaceState.isVisible,
                    onBackCancelled = {
                        val progress = latestPredictiveBackProgress[0]
                        latestPredictiveBackProgress[0] = 0f
                        scope.launch {
                            subRepliesSurfaceState.cancelPredictiveBack(progress)
                        }
                    },
                    onBackCompleted = {
                        val progress = latestPredictiveBackProgress[0]
                        latestPredictiveBackProgress[0] = 0f
                        scope.launch {
                            subRepliesSurfaceState.dismissFromPredictiveBack(progress)
                        }
                    }
                )

                Box(modifier = modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize()) {
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

                        Box(Modifier.weight(1F)) {
                            ShowHorizontalCardGrid(
                                cards = uiState.replies,
                                keySelector = { it.rpid },
                                gridState = repliesGridState,
                                staggeredGridState = activeStaggeredGridState,
                                topContent = if (uiState.topReply != null) {
                                    {
                                        uiState.topReply.let { topReply ->
                                            ShowReplyCard(
                                                replyInfo = topReply,
                                                isUpReply = (topReply.userMid == upMid),
                                                sendReply = { replyTarget = topReply },
                                                updateReply = { viewModel.updateReply(it) },
                                                onViewClick = {
                                                    latestPredictiveBackProgress[0] = 0f
                                                    scope.launch {
                                                        subRepliesSurfaceState.show(topReply)
                                                    }
                                                },
                                                isTop = true
                                            )
                                        }
                                    }
                                } else null,
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
                            ) { reply ->
                                ShowReplyCard(
                                    replyInfo = reply,
                                    isUpReply = (reply.userMid == upMid),
                                    sendReply = { replyTarget = reply },
                                    onViewClick = {
                                        latestPredictiveBackProgress[0] = 0f
                                        scope.launch {
                                            subRepliesSurfaceState.show(reply)
                                        }
                                    },
                                    updateReply = { viewModel.updateReply(it) }
                                )
                            }
                            ShowGridFABMenu(
                                onBackToTop = {
                                    scope.launch {
                                        if (activeStaggeredGridState != null) {
                                            activeStaggeredGridState.animateScrollToItem(0)
                                        } else {
                                            repliesGridState.animateScrollToItem(0)
                                        }
                                    }
                                },
                                onRefresh = { viewModel.refreshReplies() },
                                modifier = Modifier.align(Alignment.BottomEnd),
                            )
                        }

                        Surface {
                            Box(Modifier.padding(bottom = 32.dp)) {
                                OutlinedTextField(
                                    value = rootReplyText.value,
                                    onValueChange = {},
                                    label = {
                                        Text(
                                            text = viewModel.replyLabelText,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    },
                                    textStyle = MaterialTheme.typography.bodyMedium,
                                    readOnly = true,
                                    singleLine = true,
                                    modifier = Modifier
                                        .padding(horizontal = 16.dp)
                                        .fillMaxWidth()
                                        .height(48.dp)
                                )
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .clickable(onClick = { inputRootReply = true })
                                )
                                HorizontalDivider()
                            }
                        }
                    }

                    SubRepliesSurface(
                        state = subRepliesSurfaceState,
                        predictiveBackProgress = predictiveBackProgress,
                        onDismissRequest = {
                            scope.launch {
                                subRepliesSurfaceState.dismiss()
                            }
                        }
                    ) { viewingReply ->
                        val subRepliesViewModel = viewModel(key = viewingReply.rpid.toString()) {
                            SubRepliesViewModel(
                                replySection = viewModel.replySection,
                                rootReply = viewingReply
                            )
                        }
                        val subRepliesUiState by subRepliesViewModel.uiState.collectAsState()

                        SubRepliesGridPage(
                            viewModel = subRepliesViewModel,
                            uiState = subRepliesUiState,
                            upMid = upMid,
                            modifier = Modifier.fillMaxSize()
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
                                        replySection = viewModel.replySection,
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

                if (inputRootReply) {
                    ShowReplyInput(
                        text = rootReplyText,
                        labelText = viewModel.replyLabelText,
                        onSend = { message ->
                            scope.launch {
                                try {
                                    val result = sendRootReply(
                                        message = message,
                                        replySection = viewModel.replySection
                                    )
                                    if (result.isSuccess) {
                                        delay(200.milliseconds)
                                        rootReplyText.value = ""
                                        showSnackbarMessage("评论发送成功")
                                        viewModel.setSortType(ReplySort.Latest)
                                    } else {
                                        showSnackbarMessage("[${result.code}]: ${result.message}")
                                    }
                                } catch (e: Exception) {
                                    showSnackbarMessage(e.message ?: "其他网络错误")
                                } finally {
                                    inputRootReply = false
                                }
                            }
                        },
                        onDismiss = { inputRootReply = false }
                    )
                }
            }
        }
    }
}
