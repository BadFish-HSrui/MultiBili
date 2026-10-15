package tv.hsrui.bolo.ui.components.scroll

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Velocity

private class ScrollContainmentConnection(private val orientation: Orientation) : NestedScrollConnection {
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
        if (orientation == Orientation.Horizontal) Offset(available.x, 0f) else Offset(0f, available.y)

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
        if (orientation == Orientation.Horizontal) Velocity(available.x, 0f) else Velocity(0f, available.y)
}

private val horizontalScrollConnection = ScrollContainmentConnection(Orientation.Horizontal)
private val verticalScrollConnection = ScrollContainmentConnection(Orientation.Vertical)

/**
 * 内部控件先滚动，再阻止指定方向的边界余量传给父级。
 * 用于 Lazy 列表的 modifier，或放在 horizontalScroll / verticalScroll 之前。
 * 纯正交滚轮放行；含指定轴的斜向滚轮整体消费，因为指针事件不支持按轴消费。
 */
internal fun Modifier.containScroll(orientation: Orientation): Modifier =
    nestedScroll(if (orientation == Orientation.Horizontal) horizontalScrollConnection else verticalScrollConnection)
        .pointerInput(orientation) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Main)
                    // 控件到边界时可能不接收滚轮，在其处理后、父级处理前拦截剩余事件。
                    if (event.type == PointerEventType.Scroll) {
                        event.changes.forEach { change ->
                            val delta = if (orientation == Orientation.Horizontal) change.scrollDelta.x else change.scrollDelta.y
                            if (delta != 0f) change.consume()
                        }
                    }
                }
            }
        }
