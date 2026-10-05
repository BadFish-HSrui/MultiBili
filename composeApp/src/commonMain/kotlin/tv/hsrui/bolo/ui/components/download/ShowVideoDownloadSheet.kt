package tv.hsrui.bolo.ui.components.download

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Deselect
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import tv.hsrui.bolo.boloSetting.BoloSettings
import tv.hsrui.bolo.download.DownloadGroup
import tv.hsrui.bolo.download.DownloadManager
import tv.hsrui.bolo.download.DownloadTarget
import tv.hsrui.bolo.download.VideoDownloadUiState
import tv.hsrui.bolo.download.VideoDownloadViewModel
import tv.hsrui.bolo.ui.theme.BoloShapes
import tv.hsrui.bolo.utils.isExpanded
import tv.hsrui.network.login.storage.LoginStorage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShowVideoDownloadSheet(
    group: DownloadGroup,
    mainTitle: String,
    targets: List<DownloadTarget>,
    onDismiss: () -> Unit,
) {
    val settings: BoloSettings = koinInject()
    val login: LoginStorage = koinInject()
    val manager: DownloadManager = koinInject()
    val owner = remember(group, targets) { object : ViewModelStoreOwner {
        override val viewModelStore = ViewModelStore()
    } }
    DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
    val model: VideoDownloadViewModel = viewModel(viewModelStoreOwner = owner) {
        VideoDownloadViewModel(group, mainTitle, targets, settings.playback, login, manager)
    }
    val state by model.state.collectAsStateWithLifecycle()
    val dismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(state.submitted) { if (state.submitted) dismiss() }
    if (isExpanded()) {
        val drawerState = rememberDrawerState(DrawerValue.Closed)
        val scope = rememberCoroutineScope()
        val layoutDirection = LocalLayoutDirection.current
        var opening by remember { mutableStateOf(true) }
        val close: () -> Unit = { scope.launch { drawerState.close() } }
        LaunchedEffect(drawerState) {
            try { drawerState.open() } finally { opening = false }
        }
        LaunchedEffect(opening, drawerState.isClosed, drawerState.isAnimationRunning) {
            if (!opening && drawerState.isClosed && !drawerState.isAnimationRunning) dismiss()
        }
        Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val sheetWidth = (maxWidth * 0.3F).coerceIn(300.dp, 360.dp)
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    ModalNavigationDrawer(
                        drawerState = drawerState,
                        drawerContent = {
                            CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                                ModalDrawerSheet(
                                    modifier = Modifier.width(sheetWidth).fillMaxHeight(),
                                    drawerShape = AbsoluteRoundedCornerShape(topLeft = 16.dp, bottomLeft = 16.dp),
                                    windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.Right),
                                ) {
                                    VideoDownloadSheetContent(mainTitle, state, model, close, Modifier.fillMaxSize())
                                }
                            }
                        },
                    ) {}
                }
            }
        }
    } else {
        ModalBottomSheet(onDismissRequest = onDismiss,
            sheetState = rememberBottomSheetState(SheetValue.Hidden, setOf(SheetValue.Hidden, SheetValue.Expanded))) {
            VideoDownloadSheetContent(mainTitle, state, model, onDismiss, Modifier.fillMaxWidth().fillMaxHeight(0.85f))
        }
    }
}

@Composable
private fun VideoDownloadSheetContent(
    mainTitle: String,
    state: VideoDownloadUiState,
    model: VideoDownloadViewModel,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("下载选项", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleLarge)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(mainTitle, modifier = Modifier.fillMaxWidth(), maxLines = 2, overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
            val source = state.source
            val spec = state.spec
            if (state.isLoading) {
                CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
            } else if (source != null && spec != null) {
                val qualities = source.videoQualities.filter { source.availableVideoCodecs(it).isNotEmpty() }
                val codecs = source.availableVideoCodecs(spec.videoQuality)
                val audio = source.audioQualities.filter { source.getExactAudio(it) != null }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DownloadSpecSelector(spec.videoQuality.shortTitle, qualities.map { it.title }, !state.isSubmitting,
                        Modifier.weight(1f)) { model.selectQuality(qualities[it]) }
                    DownloadSpecSelector(spec.videoCodec.name, codecs.map { it.name }, !state.isSubmitting,
                        Modifier.weight(1f)) { model.selectCodec(codecs[it]) }
                    DownloadSpecSelector(spec.audioQuality?.shortTitle ?: "无音轨", audio.map { it.title },
                        !state.isSubmitting && audio.isNotEmpty(), Modifier.weight(1f)) { model.selectAudio(audio[it]) }
                }
            }
        }
        state.error?.let {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(it, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = model::loadPlayInfo, enabled = !state.isSubmitting) { Text("重试") }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${state.selectedTargets.size} / ${model.targets.size}",
                modifier = Modifier.weight(1f).alpha(0.8F),
                style = MaterialTheme.typography.bodyMedium,
            )
            val allSelected = state.selectedTargets.size == model.targets.size
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 32.dp) {
                IconButton(
                    onClick = { model.selectAll(!allSelected) },
                    enabled = !state.isSubmitting,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = if (allSelected) Icons.Rounded.Deselect else Icons.Rounded.SelectAll,
                        contentDescription = if (allSelected) "取消全选" else "全选",
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(300.dp),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(model.targets, key = { it.key }) { target ->
                val selected = target.key in state.selectedTargets
                Card(modifier = Modifier.fillMaxWidth().height(40.dp)) {
                    Row(
                        modifier = Modifier.fillMaxSize().toggleable(
                            value = selected,
                            enabled = !state.isSubmitting,
                            role = Role.Checkbox,
                            onValueChange = { model.toggleTarget(target.key) },
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = selected,
                            onCheckedChange = null,
                            enabled = !state.isSubmitting,
                            modifier = Modifier.padding(horizontal = 4.dp).size(24.dp),
                        )
                        Text(
                            text = target.subtitle.ifBlank { mainTitle },
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
            OutlinedButton(onClick = onDismiss) { Text("取消") }
            OutlinedButton(onClick = model::submit,
                enabled = state.source != null && state.spec != null && state.selectedTargets.isNotEmpty() && !state.isSubmitting && !state.isLoading) {
                Text(if (state.isSubmitting) "正在创建下载任务…" else "下载")
            }
        }
    }
}

@Composable
private fun DownloadSpecSelector(
    value: String,
    options: List<String>,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            shape = BoloShapes.InfoCard.Compact,
            contentPadding = PaddingValues(0.dp),
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        DropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
            options.forEachIndexed { index, text ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        expanded = false
                        onSelect(index)
                    },
                )
            }
        }
    }
}
