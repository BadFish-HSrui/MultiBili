package tv.hsrui.bolo.ui.components.reply

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import tv.hsrui.bolo.PlatformType
import tv.hsrui.bolo.getPlatform
import tv.hsrui.bolo.ui.components.dialog.ShowInfoDialog

@Composable
fun ShowReplyTextDialog(
    text: String,
    onDismiss: () -> Unit,
) {
    val contentMaxHeight = minOf(LocalWindowInfo.current.containerDpSize.height * 0.6F, 480.dp)

    ShowInfoDialog(
        title = {
            Text(
                text = "选取复制",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        },
        onConfirm = onDismiss,
    ) {
        SelectionContainer(
            modifier = Modifier.heightIn(max = contentMaxHeight).verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

internal fun Modifier.replyTextLongPress(text: String, onLongPress: () -> Unit): Modifier {
    if (text.isEmpty()) return this
    val isDesktop = getPlatform().type == PlatformType.Desktop

    return semantics {
        onLongClick(label = "选取复制正文") {
            onLongPress()
            true
        }
    }.pointerInput(text, onLongPress, isDesktop) {
        if (isDesktop) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (
                        event.type != PointerEventType.Press || !event.buttons.isSecondaryPressed ||
                        event.changes.none { it.type == PointerType.Mouse }
                    ) {
                        continue
                    }
                    // 右键由正文接管，左键继续交给原有链接点击处理。
                    event.changes.forEach { it.consume() }
                    onLongPress()
                    do {
                        val releaseEvent = awaitPointerEvent(PointerEventPass.Initial)
                        releaseEvent.changes.forEach { it.consume() }
                    } while (releaseEvent.changes.any { it.pressed })
                }
            }
            return@pointerInput
        }
        awaitEachGesture {
            // 链接可消费按下事件；楼中楼在 Final 阶段拦截背景事件，因此在 Main 阶段
            // 观察后续事件并自行检查移动距离，不消费普通点击或滚动。
            val down = awaitFirstDown(requireUnconsumed = false)
            val canceled = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Main)
                    val change = event.changes.firstOrNull { it.id == down.id }
                    if (
                        change == null || !change.pressed || event.changes.size != 1 ||
                        change.isConsumed ||
                        (change.position - down.position).getDistance() > viewConfiguration.touchSlop ||
                        change.position.x < 0 || change.position.x > size.width ||
                        change.position.y < 0 || change.position.y > size.height
                    ) {
                        return@withTimeoutOrNull true
                    }
                }
            }
            if (canceled != null) return@awaitEachGesture

            currentEvent.changes.forEach { it.consume() }
            onLongPress()
            // 在子节点收到松手前消费事件，避免长按链接后又触发跳转。
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                event.changes.forEach { it.consume() }
            } while (event.changes.any { it.pressed })
        }
    }
}
