package tv.hsrui.bolo.main.region

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.ScrollMoveMode
import sh.calvin.reorderable.rememberReorderableLazyGridState
import tv.hsrui.network.feature.region.Region

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShowRegionOrderSheet(
    regionOrder: List<Region>,
    onCancel: () -> Unit,
    onConfirm: (List<Region>) -> Unit,
) {
    var draft by rememberSaveable(
        regionOrder,
        stateSaver = listSaver<List<Region>, Int>(
            save = { regions -> regions.map { it.tid } },
            restore = { ids ->
                (ids.mapNotNull { id -> Region.entries.firstOrNull { it.tid == id } } + regionOrder).distinct()
            },
        ),
    ) { mutableStateOf(regionOrder) }
    val gridState = rememberLazyGridState()
    val reorderableState = rememberReorderableLazyGridState(
        lazyGridState = gridState,
        scrollMoveMode = ScrollMoveMode.INSERT,
    ) { from, to ->
        draft = draft.toMutableList().apply { add(to.index, removeAt(from.index)) }
    }
    fun moveRegion(region: Region, offset: Int): Boolean {
        val from = draft.indexOf(region)
        val to = from + offset
        if (from < 0 || to !in draft.indices || reorderableState.isAnyItemDragging) return false
        draft = draft.toMutableList().apply { add(to, removeAt(from)) }
        return true
    }

    ModalBottomSheet(
        onDismissRequest = onCancel,
        sheetState = rememberBottomSheetState(SheetValue.Hidden, setOf(SheetValue.Hidden, SheetValue.Expanded)),
        sheetGesturesEnabled = !reorderableState.isAnyItemDragging,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f)
                .padding(horizontal = 16.dp).padding(bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("分区顺序", style = MaterialTheme.typography.titleLarge)
            LazyVerticalGrid(
                columns = GridCells.Adaptive(96.dp),
                state = gridState,
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(draft, key = { _, region -> region.tid }) { index, region ->
                    ReorderableItem(reorderableState, key = region.tid) { isDragging ->
                        Surface(
                            modifier = Modifier.fillMaxWidth().height(40.dp).draggableHandle()
                                .semantics(mergeDescendants = true) {
                                    stateDescription = "第 ${index + 1} 项，共 ${draft.size} 项"
                                    customActions = buildList {
                                        if (index > 0) {
                                            add(CustomAccessibilityAction("向前移动") { moveRegion(region, -1) })
                                        }
                                        if (index < draft.lastIndex) {
                                            add(CustomAccessibilityAction("向后移动") { moveRegion(region, 1) })
                                        }
                                    }
                                },
                            shape = InputChipDefaults.shape,
                            color = if (isDragging) MaterialTheme.colorScheme.secondaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = BorderStroke(
                                1.dp,
                                if (isDragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            ),
                            shadowElevation = if (isDragging) 4.dp else 0.dp,
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
                                Text(
                                    region.title,
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onCancel) { Text("取消") }
                OutlinedButton(
                    onClick = { onConfirm(draft) },
                    enabled = !reorderableState.isAnyItemDragging,
                ) { Text("确认") }
            }
        }
    }
}
