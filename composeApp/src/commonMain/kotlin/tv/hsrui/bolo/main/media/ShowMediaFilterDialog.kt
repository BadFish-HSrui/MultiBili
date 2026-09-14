package tv.hsrui.bolo.main.media

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import tv.hsrui.bolo.ui.components.dialog.ShowConfirmDialog
import tv.hsrui.network.feature.media.MediaFilterSelection

@Composable
fun ShowMediaFilterDialog(
    uiState: MediaFilterUiState,
    selection: MediaFilterSelection,
    onCancel: () -> Unit,
    onConfirm: (MediaFilterSelection) -> Unit,
    onRetry: () -> Unit,
) {
    var draft by rememberSaveable(
        selection,
        stateSaver = Saver<MediaFilterSelection, String>(
            save = { Json.encodeToString(it) },
            restore = { Json.decodeFromString<MediaFilterSelection>(it) },
        ),
    ) { mutableStateOf(selection) }
    val windowSize = LocalWindowInfo.current.containerDpSize
    val contentMaxWidth = minOf(windowSize.width * 0.8f, 480.dp)
    val contentMaxHeight = minOf(windowSize.height * 0.65f, 640.dp)

    ShowConfirmDialog(
        onCancel = onCancel,
        onConfirm = {
            if (uiState is MediaFilterUiState.Success) {
                onConfirm(draft.normalized(uiState.conditions))
            }
        },
        confirmEnabled = uiState is MediaFilterUiState.Success,
    ) {
        Column(
            modifier = Modifier.widthIn(max = contentMaxWidth)
                .fillMaxWidth().heightIn(max = contentMaxHeight),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "筛选条件",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = { draft = MediaFilterSelection() },
                    enabled = uiState is MediaFilterUiState.Success,
                ) {
                    Text("重置")
                }
            }
            when (uiState) {
                MediaFilterUiState.Loading -> CircularProgressIndicator(
                    modifier = Modifier.padding(vertical = 24.dp),
                )
                is MediaFilterUiState.Error -> Column(
                    modifier = Modifier.weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()).padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(uiState.message)
                    TextButton(onClick = onRetry) { Text("重试") }
                }
                is MediaFilterUiState.Success -> LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (uiState.conditions.sortOptions.isNotEmpty()) {
                        item(key = "order") {
                            Column {
                                Text("排序", style = MaterialTheme.typography.titleSmall)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(
                                        selected = draft.order == null,
                                        onClick = { draft = draft.copy(order = null, ascending = false) },
                                        label = { Text("默认") },
                                    )
                                    uiState.conditions.sortOptions.forEach { option ->
                                        FilterChip(
                                            selected = draft.order == option.value,
                                            onClick = {
                                                draft = draft.copy(
                                                    order = option.value,
                                                    ascending = option.supportsAscending &&
                                                        (draft.ascending || !option.supportsDescending),
                                                )
                                            },
                                            label = { Text(option.title) },
                                        )
                                    }
                                }
                            }
                        }
                        val selectedOrder = uiState.conditions.sortOptions.firstOrNull { it.value == draft.order }
                        if (selectedOrder != null) {
                            item(key = "direction") {
                                Column {
                                    Text("排序方向", style = MaterialTheme.typography.titleSmall)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        FilterChip(
                                            selected = !draft.ascending,
                                            enabled = selectedOrder.supportsDescending,
                                            onClick = { draft = draft.copy(ascending = false) },
                                            label = { Text("降序") },
                                        )
                                        FilterChip(
                                            selected = draft.ascending,
                                            enabled = selectedOrder.supportsAscending,
                                            onClick = { draft = draft.copy(ascending = true) },
                                            label = { Text("升序") },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    items(uiState.conditions.filters, key = { "filter:${it.parameterName}" }) { group ->
                        Column {
                            Text(group.title, style = MaterialTheme.typography.titleSmall)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                group.options.forEach { option ->
                                    FilterChip(
                                        selected = if (option.isAll) {
                                            draft.values[group.parameterName] == null
                                        } else {
                                            draft.values[group.parameterName] == option.value
                                        },
                                        onClick = {
                                            draft = draft.copy(
                                                values = if (option.isAll) {
                                                    draft.values - group.parameterName
                                                } else {
                                                    draft.values + (group.parameterName to option.value)
                                                },
                                            )
                                        },
                                        label = { Text(option.title) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
