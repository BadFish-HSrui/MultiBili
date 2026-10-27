package tv.hsrui.bolo.view.live.superChat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import tv.hsrui.bolo.ui.components.image.ShowImage
import tv.hsrui.bolo.ui.components.live.ShowLiveFansMedal
import tv.hsrui.bolo.ui.components.live.rememberLiveDecorationPainter
import tv.hsrui.bolo.ui.components.scroll.containScroll
import tv.hsrui.bolo.view.live.danmaku.LiveDanmakuRoleBadge
import tv.hsrui.network.liveFeature.danmaku.LiveDanmakuMedalResourcesData
import tv.hsrui.network.liveFeature.superChat.LiveSuperChatData
import kotlin.math.roundToInt

@Composable
fun LiveSuperChatLayer(
    viewModel: LiveSuperChatViewModel,
    isVisible: Boolean,
    anchorUid: Long,
    medalResources: LiveDanmakuMedalResourcesData,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    DisposableEffect(viewModel, isVisible) {
        viewModel.setVisible(isVisible)
        onDispose { viewModel.setVisible(false) }
    }
    LaunchedEffect(state.items.firstOrNull()?.data?.id, isVisible) {
        if (isVisible && state.items.firstOrNull()?.animateEntry == true) listState.scrollToItem(0)
    }
    if (state.items.isEmpty()) return
    BoxWithConstraints(modifier.fillMaxSize().padding(8.dp).clipToBounds()) {
        val cardHeight = (maxHeight - 40.dp).coerceAtLeast(0.dp)
        Column(Modifier.fillMaxWidth()) {
            LazyRow(
                state = listState,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth().height(32.dp).containScroll(Orientation.Horizontal),
            ) {
                items(state.items, key = { it.data.id }) { item ->
                    LiveSuperChatCapsule(
                        item = item,
                        isVisible = isVisible,
                        onClick = { viewModel.toggle(item.data.id) },
                        onEntered = { viewModel.finishEntry(item.data.id) },
                    )
                }
            }
            state.selectedItem?.let { item ->
                val expansion = remember { MutableTransitionState(false) }
                expansion.targetState = isVisible && state.isExpanded
                AnimatedVisibility(
                    visibleState = expansion,
                    enter = expandVertically(tween(SUPER_CHAT_ANIMATION_MILLIS), expandFrom = Alignment.Top),
                    exit = shrinkVertically(tween(SUPER_CHAT_ANIMATION_MILLIS), shrinkTowards = Alignment.Top),
                ) {
                    key(item.data.id) {
                        LiveSuperChatCard(
                            item = item,
                            isVisible = isVisible,
                            anchorUid = anchorUid,
                            medalResources = medalResources,
                            modifier = Modifier.padding(top = 8.dp).heightIn(max = cardHeight),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveSuperChatCapsule(
    item: LiveSuperChatItem,
    isVisible: Boolean,
    onClick: () -> Unit,
    onEntered: () -> Unit,
) {
    val data = item.data
    val offset = remember(data.id) { Animatable(if (item.animateEntry) -1f else 0f) }
    val density = LocalDensity.current
    LaunchedEffect(data.id, item.animateEntry, isVisible) {
        if (item.animateEntry && isVisible) {
            offset.animateTo(0f, tween(SUPER_CHAT_ANIMATION_MILLIS))
            onEntered()
        } else offset.snapTo(0f)
    }
    val primary = MaterialTheme.colorScheme.primary
    val fallback = if (primary.luminance() > 0.35f) lerp(primary, Color.Black, 0.45f) else primary
    val color = data.bottomColorArgb?.let(::Color) ?: fallback
    val progress by animateFloatAsState(item.progress, tween(if (isVisible) 1_000 else 0, easing = LinearEasing))
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = Color.White,
        modifier = Modifier.width(110.dp).height(32.dp)
            .offset { IntOffset(with(density) { (110.dp.toPx() * offset.value).roundToInt() }, 0) }
            .semantics { contentDescription = "${data.username}，醒目留言 ¥ ${data.amountYuan}" },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxSize().drawBehind {
                drawRect(color.copy(alpha = 0.5f))
                drawRect(color, size = Size(size.width * progress, size.height))
            }.padding(horizontal = 3.dp),
        ) {
            Box(
                Modifier.size(27.dp).clip(CircleShape).background(color),
                contentAlignment = Alignment.Center,
            ) {
                if (data.avatarUrl.isBlank()) Text(data.username.take(1), color = Color.White)
                else ShowImage(
                    url = data.avatarUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    animationEnabled = isVisible,
                    fallbackContent = { Text(data.username.take(1), color = Color.White) },
                )
            }
            Text(
                text = "¥ ${data.amountYuan}",
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LiveSuperChatCard(
    item: LiveSuperChatItem,
    isVisible: Boolean,
    anchorUid: Long,
    medalResources: LiveDanmakuMedalResourcesData,
    modifier: Modifier = Modifier,
) {
    val data = item.data
    val top = data.backgroundColorArgb?.let(::Color) ?: MaterialTheme.colorScheme.surfaceContainerHigh
    val bottom = data.bottomColorArgb?.let(::Color) ?: MaterialTheme.colorScheme.primaryContainer
    val topText = top.superChatContentColor()
    val bodyText = data.messageColorArgb?.let(::Color) ?: bottom.superChatContentColor()
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = top, contentColor = topText),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(topText.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (data.avatarUrl.isBlank()) Text(data.username.take(1))
                    else ShowImage(
                        url = data.avatarUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        animationEnabled = isVisible,
                        fallbackContent = { Text(data.username.take(1)) },
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LiveSuperChatIdentity(data, anchorUid, medalResources)
                    Text(
                        text = "¥ ${data.amountYuan} · ${data.totalSeconds}s · ${item.remainingSeconds}s",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Box(
                Modifier.weight(1f, fill = false).fillMaxWidth().background(bottom)
                    .containScroll(Orientation.Vertical).verticalScroll(rememberScrollState()).padding(12.dp),
            ) {
                Text(data.content, color = bodyText, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun LiveSuperChatIdentity(
    data: LiveSuperChatData,
    anchorUid: Long,
    medalResources: LiveDanmakuMedalResourcesData,
) {
    val user = data.user
    val medal = user.fansMedal?.takeIf {
        user.isMystery != true && it.isLighted && it.level > 0 && it.name.isNotBlank()
    }
    val medalPainter = rememberLiveDecorationPainter(medal?.let(medalResources::iconUrl))
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val medalWidth = maxWidth * 0.5f
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (medal != null) {
                ShowLiveFansMedal(medal, modifier = Modifier.widthIn(max = medalWidth), guardPainter = medalPainter)
            }
            val isAnchor = anchorUid > 0 && user.uid == anchorUid
            if (user.isMystery != true && (isAnchor || user.isRoomAdmin == true)) {
                LiveDanmakuRoleBadge(isAnchor = isAnchor, modifier = Modifier.width(30.dp).height(16.dp))
            }
            Text(
                text = data.username,
                color = LocalContentColor.current.copy(alpha = 0.75f),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private fun Color.superChatContentColor(): Color = if (luminance() > 0.179f) Color.Black else Color.White
