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
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import tv.hsrui.bolo.ui.components.image.ShowImage
import tv.hsrui.bolo.ui.theme.BiliColor
import tv.hsrui.network.liveFeature.danmaku.LiveDanmakuData
import tv.hsrui.network.liveFeature.danmaku.LiveDanmakuEmoticonData

@Composable
fun LiveDanmakuPage(
    viewModel: LiveDanmakuViewModel,
    isVisible: Boolean,
    modifier: Modifier = Modifier,
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
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize().nestedScroll(scrollConnection),
        ) {
            items(state.items, key = { it.key }) { item ->
                LiveDanmakuCard(item.data, isVisible)
            }
        }
        if (isVisible && !following && listState.canScrollForward) {
            ElevatedButton(
                onClick = {
                    following = true
                    scope.launch { listState.scrollToLiveDanmakuEnd(state.items.size) }
                },
                modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp),
            ) {
                Text(
                    text = if (state.unreadCount > 0) "有 ${state.unreadCount} 条新弹幕" else "回到底部",
                    color = BiliColor.Blue,
                )
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
private fun LiveDanmakuCard(data: LiveDanmakuData, isVisible: Boolean) {
    val style = MaterialTheme.typography.bodyMedium
    val density = LocalDensity.current
    val parts = remember(data) { liveDanmakuContent(data) }
    Card(Modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(8.dp)) {
            val maxImageWidth = with(density) { maxWidth.toSp() }
            val imageHeight = with(density) { 56.dp.toSp() }
            val usernameColor = LocalContentColor.current.copy(alpha = 0.75f)
            val inlineContent = mutableMapOf<String, InlineTextContent>()
            val text = buildAnnotatedString {
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
