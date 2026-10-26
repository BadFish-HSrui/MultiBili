package tv.hsrui.bolo.view.live.danmaku

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import tv.hsrui.bolo.ui.components.image.ShowImage
import tv.hsrui.bolo.ui.theme.BiliColor
import tv.hsrui.network.liveFeature.danmaku.LiveDanmakuBubbleData
import tv.hsrui.network.liveFeature.danmaku.LiveDanmakuData
import tv.hsrui.network.liveFeature.danmaku.LiveDanmakuEmoticonData
import tv.hsrui.network.liveFeature.danmaku.LiveDanmakuMedalResourcesData

@Composable
fun LiveDanmakuPage(
    viewModel: LiveDanmakuViewModel,
    isVisible: Boolean,
    modifier: Modifier = Modifier,
    onSend: (() -> Unit)? = null,
    sendEnabled: Boolean = true,
    anchorUid: Long = 0,
) {
    val state by viewModel.uiState.collectAsState()
    val session = state.session
    val listState = remember(session) { LazyListState() }
    val scope = rememberCoroutineScope()
    val visible by rememberUpdatedState(isVisible)
    var following by remember(session) { mutableStateOf(true) }
    var shown by remember(session) { mutableStateOf(false) }
    var anchorKey by remember(session) { mutableStateOf<Long?>(null) }
    val scrollConnection = remember(listState) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (visible && source == NestedScrollSource.UserInput && available.y > 0) following = false
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (visible && source == NestedScrollSource.UserInput && !listState.canScrollForward) following = true
                return Offset.Zero
            }
        }
    }
    DisposableEffect(viewModel, isVisible) {
        viewModel.setVisible(isVisible)
        onDispose { viewModel.setVisible(false) }
    }
    LaunchedEffect(listState, isVisible, state.items) {
        if (!isVisible) {
            following = false
        } else if (state.items.isNotEmpty()) {
            if (!shown || following) {
                shown = true
                following = true
                listState.scrollToLiveDanmakuEnd(state.items.size)
            } else if (anchorKey != null && state.items.none { it.key == anchorKey }) {
                listState.scrollToItem(0)
            }
        }
    }
    LaunchedEffect(listState, isVisible, session) {
        if (!isVisible) return@LaunchedEffect
        snapshotFlow {
            val layout = listState.layoutInfo
            layout.visibleItemsInfo.filter {
                it.offset < layout.viewportEndOffset && it.offset + it.size > layout.viewportStartOffset
            }.mapNotNull { it.key as? Long }.toSet()
        }.collect { keys ->
            anchorKey = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.key as? Long
            viewModel.markVisible(session, keys)
            if (!listState.canScrollForward && !listState.isScrollInProgress) following = true
        }
    }
    Box(modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = 8.dp, top = 8.dp, end = 8.dp, bottom = if (onSend != null) 88.dp else 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize().nestedScroll(scrollConnection),
        ) {
            items(state.items, key = { it.key }) { item ->
                LiveDanmakuCard(
                    data = item.data,
                    isVisible = isVisible,
                    anchorUid = anchorUid,
                    medalResources = state.medalResources,
                    bubbleResources = state.bubbleResources,
                )
            }
        }
        Layout(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
            content = {
                Box(Modifier.layoutId("unread"), contentAlignment = Alignment.Center) {
                    if (isVisible && !following && listState.canScrollForward) {
                        ElevatedButton(
                            onClick = {
                                following = true
                                scope.launch { listState.scrollToLiveDanmakuEnd(state.items.size) }
                            },
                        ) {
                            Text(
                                text = if (state.unreadCount > 0) "有 ${state.unreadCount} 条新弹幕" else "回到底部",
                                color = BiliColor.Blue,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                if (onSend != null) {
                    val enabled = isVisible && sendEnabled
                    val label = if (sendEnabled) "发送弹幕" else "登录后发送弹幕"
                    FloatingActionButton(
                        onClick = { if (enabled) onSend() },
                        containerColor = if (enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (enabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                        modifier = Modifier.layoutId("send").focusProperties { canFocus = enabled }.then(
                            if (enabled) Modifier else Modifier.clearAndSetSemantics {
                                contentDescription = label
                                role = Role.Button
                                disabled()
                            },
                        ),
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = label)
                    }
                }
            },
        ) { measurables, constraints ->
            val looseConstraints = constraints.copy(minWidth = 0, minHeight = 0)
            val send = measurables.firstOrNull { it.layoutId == "send" }?.measure(looseConstraints)
            val sendX = constraints.maxWidth - (send?.width ?: 0)
            val unreadRight = (sendX - if (send != null) 8.dp.roundToPx() else 0).coerceAtLeast(0)
            val unread = measurables.first { it.layoutId == "unread" }
                .measure(looseConstraints.copy(maxWidth = unreadRight))
            val unreadX = minOf((constraints.maxWidth - unread.width) / 2, unreadRight - unread.width)
            val height = constraints.constrainHeight(maxOf(unread.height, send?.height ?: 0))
            layout(constraints.maxWidth, height) {
                unread.place(unreadX, (height - unread.height) / 2)
                send?.place(sendX, (height - send.height) / 2)
            }
        }
    }
}

private suspend fun LazyListState.scrollToLiveDanmakuEnd(count: Int) {
    if (count == 0) return
    scrollToItem(count - 1)
    val last = layoutInfo.visibleItemsInfo.lastOrNull() ?: return
    scrollBy(
        (last.offset + last.size + layoutInfo.afterContentPadding - layoutInfo.viewportEndOffset)
            .coerceAtLeast(0).toFloat(),
    )
}

@Composable
private fun LiveDanmakuCard(
    data: LiveDanmakuData,
    isVisible: Boolean,
    anchorUid: Long,
    medalResources: LiveDanmakuMedalResourcesData,
    bubbleResources: Map<Long, LiveDanmakuBubbleData>,
) {
    val style = MaterialTheme.typography.bodyMedium
    val badgeStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 12.sp)
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val parts = remember(data) { liveDanmakuContent(data) }
    val user = data.user
    val medal = user.fansMedal?.takeIf {
        user.isMystery != true && it.isLighted && it.level > 0 && it.name.isNotBlank()
    }
    val medalPainter = rememberLiveDanmakuImagePainter(medal?.let(medalResources::iconUrl))
    val spacePainter = rememberLiveDanmakuImagePainter(data.spaceUrl.takeIf { user.isMystery != true })
    val bubble = bubbleResources[data.bubbleId].takeIf { user.isMystery != true }
    val bubblePainter = rememberLiveDanmakuImagePainter(bubble?.url)
    val firstLinePainter = rememberLiveDanmakuImagePainter(bubble?.firstLineUrl?.takeIf { bubble.isFleet })
    Card(Modifier.fillMaxWidth()) {
        BoxWithConstraints(
            Modifier.fillMaxWidth()
                .liveDanmakuBubble(data.bubbleColorArgb, bubble, bubblePainter, firstLinePainter)
                .padding(8.dp),
        ) {
            val maxImageWidth = with(density) { maxWidth.toSp() }
            val imageHeight = with(density) { 56.dp.toSp() }
            val usernameColor = LocalContentColor.current.copy(alpha = 0.75f)
            val inlineContent = mutableMapOf<String, InlineTextContent>()
            val badgeLabels = linkedMapOf<String, String>()
            if (user.isMystery != true) {
                if (spacePainter != null) {
                    val width = (20.dp * (spacePainter.intrinsicSize.width / spacePainter.intrinsicSize.height) + 4.dp)
                        .coerceAtMost(maxWidth)
                    badgeLabels["space"] = "身份标识"
                    inlineContent["space"] = InlineTextContent(
                        Placeholder(
                            width = with(density) { width.toSp() },
                            height = with(density) { 20.dp.toSp() },
                            placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
                        ),
                    ) {
                        LiveDanmakuGuardBadge(
                            painter = spacePainter,
                            modifier = Modifier.fillMaxSize().padding(end = 4.dp),
                        )
                    }
                }
                if (medal != null) {
                    val medalStyle = badgeStyle.copy(fontWeight = FontWeight.Normal, letterSpacing = 0.sp)
                    val nameWidth = textMeasurer.measure(medal.name, medalStyle, maxLines = 1).size.width
                    val levelWidth = textMeasurer.measure(
                        text = medal.level.toString(),
                        style = medalStyle.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                    ).size.width
                    val iconWidth = if (medalPainter != null) 14.dp else 0.dp
                    val height = if (medalPainter != null) 20.dp else 16.dp
                    val width = with(density) {
                        (nameWidth + levelWidth).toDp() + 18.dp + iconWidth
                    }.coerceAtMost(maxWidth)
                    badgeLabels["medal"] = "粉丝勋章 ${medal.name} ${medal.level}级"
                    inlineContent["medal"] = InlineTextContent(
                        Placeholder(
                            width = with(density) { width.toSp() },
                            height = with(density) { height.toSp() },
                            placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
                        ),
                    ) {
                        LiveDanmakuFansMedal(
                            medal = medal,
                            modifier = Modifier.fillMaxSize().padding(end = 4.dp),
                            guardPainter = medalPainter,
                        )
                    }
                }
                val isAnchor = anchorUid > 0 && user.uid == anchorUid
                if (isAnchor || user.isRoomAdmin == true) {
                    val label = if (isAnchor) "主播" else "房管"
                    val width = with(density) {
                        textMeasurer.measure(label, badgeStyle, maxLines = 1).size.width.toDp() + 10.dp
                    }.coerceAtMost(maxWidth)
                    badgeLabels["role"] = label
                    inlineContent["role"] = InlineTextContent(
                        Placeholder(
                            width = with(density) { width.toSp() },
                            height = with(density) { 16.dp.toSp() },
                            placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
                        ),
                    ) {
                        LiveDanmakuRoleBadge(
                            isAnchor = isAnchor,
                            modifier = Modifier.fillMaxSize().padding(end = 4.dp),
                        )
                    }
                }
            }
            val text = buildAnnotatedString {
                badgeLabels.forEach { (key, label) ->
                    appendInlineContent(key, label)
                    append('\u200B')
                }
                withStyle(SpanStyle(color = usernameColor)) { append(data.username) }
                append(": ")
                if (data.replyUsername.isNotBlank()) append("@${data.replyUsername} ")
                parts.forEachIndexed { index, (text, emoticon) ->
                    if (emoticon == null) append(text) else {
                        val key = "emoticon_$index"
                        val height = if (data.emoticon != null) imageHeight else style.fontSize
                        val width = (height.value * emoticon.width / emoticon.height)
                            .coerceAtMost(maxImageWidth.value).sp
                        appendInlineContent(key, text.ifEmpty { "表情" })
                        inlineContent[key] = InlineTextContent(
                            Placeholder(width, height, PlaceholderVerticalAlign.TextCenter),
                        ) { LiveDanmakuEmoticon(emoticon, isVisible) }
                    }
                }
            }
            Text(text = text, inlineContent = inlineContent, style = style)
        }
    }
}

@Composable
private fun LiveDanmakuEmoticon(emoticon: LiveDanmakuEmoticonData, isVisible: Boolean) {
    val text = emoticon.text.ifBlank { "表情" }
    ShowImage(
        url = emoticon.url,
        contentDescription = text,
        modifier = Modifier.fillMaxSize(),
        animationEnabled = isVisible,
        fallbackContent = { Text(text, style = MaterialTheme.typography.bodyMedium) },
    )
}

private fun liveDanmakuContent(data: LiveDanmakuData): List<Pair<String, LiveDanmakuEmoticonData?>> {
    data.emoticon?.let { return listOf(data.content to it) }
    if (data.inlineEmoticons.isEmpty()) return listOf(data.content to null)
    val pattern = data.inlineEmoticons.keys.filter(String::isNotEmpty).sortedByDescending(String::length)
        .joinToString("|", transform = Regex::escape)
    if (pattern.isEmpty()) return listOf(data.content to null)
    return buildList {
        var offset = 0
        Regex(pattern).findAll(data.content).forEach { match ->
            if (offset < match.range.first) add(data.content.substring(offset, match.range.first) to null)
            add(match.value to data.inlineEmoticons[match.value])
            offset = match.range.last + 1
        }
        if (offset < data.content.length) add(data.content.substring(offset) to null)
    }
}
