package tv.hsrui.bolo.boloSetting.setting.general

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import tv.hsrui.bolo.PlatformType
import tv.hsrui.bolo.boloSetting.BoloSettings
import tv.hsrui.bolo.boloSetting.AppThemeMode
import tv.hsrui.bolo.getPlatform
import tv.hsrui.bolo.ui.common.snackbar.SnackbarManager
import tv.hsrui.bolo.ui.components.dialog.ShowConfirmDialog
import tv.hsrui.bolo.ui.components.topBar.ShowTopBarWithNavigationButton
import tv.hsrui.bolo.ui.theme.DefaultColorSpec
import tv.hsrui.bolo.ui.theme.DefaultPaletteStyle
import tv.hsrui.bolo.ui.theme.DefaultSeedColorRgb
import tv.hsrui.bolo.utils.calculateWithoutBottom
import tv.hsrui.bolo.utils.isExpanded
import tv.hsrui.bolo.utils.url.isSystemLinkHandlingEnabled
import tv.hsrui.bolo.utils.url.openSystemLinkSettings
import tv.hsrui.bolo.utils.url.openUrl
import tv.hsrui.bolo.utils.url.setSystemLinkHandlingEnabled

@Composable
fun GeneralSettingsScreen(modifier: Modifier = Modifier) {
    val settings: BoloSettings = koinInject()
    val snackbar: SnackbarManager = koinInject()
    val scope = rememberCoroutineScope()
    var showColorDialog by remember { mutableStateOf(false) }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { if (!isExpanded()) ShowTopBarWithNavigationButton(title = { Text("通用设置") }) },
    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(innerPadding.calculateWithoutBottom()),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 1000.dp).fillMaxWidth(),
                contentPadding = PaddingValues(8.dp),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("界面设置", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(8.dp))
                        Card(Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("应用主题", style = MaterialTheme.typography.bodyLarge)
                                Spacer(Modifier.weight(1f))
                                SingleChoiceSegmentedButtonRow(modifier = Modifier.widthIn(max = 220.dp)) {
                                    AppThemeMode.entries.forEachIndexed { index, mode ->
                                        SegmentedButton(
                                            selected = mode == settings.general.themeMode,
                                            onClick = { settings.general.themeMode = mode },
                                            shape = SegmentedButtonDefaults.itemShape(
                                                index = index,
                                                count = AppThemeMode.entries.size,
                                            ),
                                            modifier = Modifier.padding(vertical = 8.dp),
                                        ) {
                                            Text(mode.title, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                                        }
                                    }
                                }
                            }
                            HorizontalDivider()
                            Row(
                                modifier = Modifier.fillMaxWidth().height(64.dp)
                                    .clickable(role = Role.Button) { showColorDialog = true }
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("应用色彩", style = MaterialTheme.typography.bodyLarge)
                                Spacer(Modifier.width(8.dp))
                                Row(
                                    modifier = Modifier.weight(1f),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        modifier = Modifier.size(24.dp)
                                            .background(Color(settings.general.seedColorRgb or 0xFF000000.toInt()))
                                            .border(1.dp, Color.Black),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    val colorSpecYear = when (settings.general.colorSpec) {
                                        ColorSpec.SpecVersion.SPEC_2021 -> "2021"
                                        ColorSpec.SpecVersion.SPEC_2025 -> "2025"
                                    }
                                    Text(
                                        text = "${formatSeedColorRgb(settings.general.seedColorRgb).removePrefix("#")} · " +
                                            "$colorSpecYear · ${settings.general.paletteStyle.name}",
                                        modifier = Modifier.weight(1f, fill = false),
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                        Text("功能设置", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(8.dp))
                        Card(Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().height(64.dp).toggleable(
                                    value = settings.general.searchSuggestionsEnabled,
                                    role = Role.Switch,
                                    onValueChange = { settings.general.searchSuggestionsEnabled = it },
                                ).padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("启用搜索建议", style = MaterialTheme.typography.bodyLarge)
                                Spacer(Modifier.weight(1f))
                                Switch(checked = settings.general.searchSuggestionsEnabled, onCheckedChange = null)
                            }
                            HorizontalDivider()
                            Row(
                                modifier = Modifier.fillMaxWidth().height(64.dp).toggleable(
                                    value = settings.general.searchTrendingEnabled,
                                    role = Role.Switch,
                                    onValueChange = { settings.general.searchTrendingEnabled = it },
                                ).padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("显示热搜", style = MaterialTheme.typography.bodyLarge)
                                Spacer(Modifier.weight(1f))
                                Switch(checked = settings.general.searchTrendingEnabled, onCheckedChange = null)
                            }
                        }
                        Text("外部链接", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(8.dp))
                        Card(Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().height(64.dp).toggleable(
                                    value = settings.general.clipboardLinkRecognitionEnabled,
                                    role = Role.Switch,
                                    onValueChange = { settings.general.clipboardLinkRecognitionEnabled = it },
                                ).padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("识别剪贴板链接", style = MaterialTheme.typography.bodyLarge)
                                Spacer(Modifier.weight(1f))
                                Switch(checked = settings.general.clipboardLinkRecognitionEnabled, onCheckedChange = null)
                            }
                            if (getPlatform().type == PlatformType.Android) {
                                HorizontalDivider()
                                Row(
                                    modifier = Modifier.fillMaxWidth().height(64.dp).toggleable(
                                        value = settings.general.systemLinkHandlingEnabled,
                                        role = Role.Switch,
                                        onValueChange = { enabled ->
                                            if (setSystemLinkHandlingEnabled(enabled)) {
                                                settings.general.systemLinkHandlingEnabled = enabled
                                                if (enabled && !openSystemLinkSettings()) {
                                                    snackbar.showMessage("无法打开系统设置")
                                                }
                                            } else {
                                                settings.general.systemLinkHandlingEnabled = isSystemLinkHandlingEnabled()
                                                snackbar.showMessage("系统链接设置失败")
                                            }
                                        },
                                    ).padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text("接收系统链接跳转", style = MaterialTheme.typography.bodyLarge)
                                    Spacer(Modifier.weight(1f))
                                    Switch(checked = settings.general.systemLinkHandlingEnabled, onCheckedChange = null)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showColorDialog) {
        var draftSeedColorRgb by remember { mutableStateOf(settings.general.seedColorRgb) }
        var draftColorSpec by remember { mutableStateOf(settings.general.colorSpec) }
        var draftPaletteStyle by remember { mutableStateOf(settings.general.paletteStyle) }
        var seedColorInput by remember { mutableStateOf(formatSeedColorRgb(draftSeedColorRgb)) }
        var seedColorInputError by remember { mutableStateOf(false) }
        var lastValidatedInput by remember { mutableStateOf<String?>(null) }
        var seedColorInputFocused by remember { mutableStateOf(false) }
        var styleMenuExpanded by remember { mutableStateOf(false) }
        var dismissing by remember { mutableStateOf(false) }
        val windowSize = LocalWindowInfo.current.containerDpSize
        val contentWidth = minOf(windowSize.width * 0.8F, 320.dp)

        DisposableEffect(settings.general) {
            onDispose { settings.general.clearColorPreview() }
        }
        LaunchedEffect(settings.general, draftSeedColorRgb, draftColorSpec, draftPaletteStyle) {
            if (!dismissing) {
                settings.general.previewColorSettings(draftSeedColorRgb, draftColorSpec, draftPaletteStyle)
            }
        }

        fun validateSeedColorInput(): Boolean {
            if (dismissing) return false
            if (lastValidatedInput == seedColorInput) return !seedColorInputError
            val rgb = parseSeedColorRgb(seedColorInput)
            seedColorInputError = rgb == null
            if (rgb != null) {
                draftSeedColorRgb = rgb
                seedColorInput = formatSeedColorRgb(rgb)
            }
            lastValidatedInput = seedColorInput
            return rgb != null
        }

        ShowConfirmDialog(
            onCancel = {
                dismissing = true
                settings.general.clearColorPreview()
                showColorDialog = false
            },
            onConfirm = {
                if (validateSeedColorInput()) {
                    settings.general.applyColorSettings(draftSeedColorRgb, draftColorSpec, draftPaletteStyle)
                    dismissing = true
                    showColorDialog = false
                }
            },
            dismissOnClickOutside = false,
        ) {
            val focusManager = LocalFocusManager.current
            val seedColorInteractionSource = remember { MutableInteractionSource() }
            val seedColorFieldColors = OutlinedTextFieldDefaults.colors()
            Column(
                modifier = Modifier.width(contentWidth)
                    .heightIn(max = windowSize.height * 0.65F)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = "应用色彩",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 12.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("SeedColor", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.weight(1f))
                    Box(
                        modifier = Modifier.size(24.dp)
                            .background(Color(draftSeedColorRgb or 0xFF000000.toInt()))
                            .border(1.dp, Color.Black)
                            .semantics { contentDescription = "颜色预览 ${formatSeedColorRgb(draftSeedColorRgb)}" },
                    )
                    Spacer(Modifier.width(8.dp))
                    BasicTextField(
                        value = seedColorInput,
                        onValueChange = {
                            seedColorInput = it
                            seedColorInputError = false
                            lastValidatedInput = null
                            parseSeedColorRgb(it)?.let { rgb -> draftSeedColorRgb = rgb }
                        },
                        modifier = Modifier.size(width = 100.dp, height = 36.dp)
                            .semantics {
                                contentDescription = "SeedColor"
                                if (seedColorInputError) error("请输入六位十六进制 RGB 值，如 #FF9D9D")
                            }
                            .onFocusChanged {
                                val lostFocus = seedColorInputFocused && !it.isFocused
                                seedColorInputFocused = it.isFocused
                                if (lostFocus) validateSeedColorInput()
                            }
                            .onPreviewKeyEvent {
                                if (it.key == Key.Enter || it.key == Key.NumPadEnter) {
                                    if (it.type == KeyEventType.KeyDown) {
                                        validateSeedColorInput()
                                        focusManager.clearFocus()
                                    }
                                    true
                                } else false
                            },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            textAlign = TextAlign.Center,
                            color = when {
                                seedColorInputError -> seedColorFieldColors.errorTextColor
                                seedColorInputFocused -> seedColorFieldColors.focusedTextColor
                                else -> seedColorFieldColors.unfocusedTextColor
                            },
                        ),
                        cursorBrush = SolidColor(
                            if (seedColorInputError) seedColorFieldColors.errorCursorColor else seedColorFieldColors.cursorColor,
                        ),
                        interactionSource = seedColorInteractionSource,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            autoCorrectEnabled = false,
                            keyboardType = KeyboardType.Ascii,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = {
                            validateSeedColorInput()
                            focusManager.clearFocus()
                        }),
                        decorationBox = { innerTextField ->
                            OutlinedTextFieldDefaults.DecorationBox(
                                value = seedColorInput,
                                innerTextField = innerTextField,
                                enabled = true,
                                singleLine = true,
                                visualTransformation = VisualTransformation.None,
                                interactionSource = seedColorInteractionSource,
                                isError = seedColorInputError,
                                colors = seedColorFieldColors,
                                contentPadding = PaddingValues(0.dp),
                            )
                        },
                    )
                }
                if (seedColorInputError) {
                    Text(
                        text = "请输入六位十六进制 RGB 值，如 #FF9D9D",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("ColorSpec", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.weight(1f))
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.widthIn(max = 220.dp)) {
                        ColorSpec.SpecVersion.entries.forEachIndexed { index, spec ->
                            SegmentedButton(
                                selected = draftColorSpec == spec,
                                onClick = {
                                    focusManager.clearFocus()
                                    draftColorSpec = spec
                                    if (draftPaletteStyle !in settings.general.availablePaletteStyles(spec)) {
                                        draftPaletteStyle = PaletteStyle.TonalSpot
                                    }
                                },
                                shape = SegmentedButtonDefaults.itemShape(index, ColorSpec.SpecVersion.entries.size),
                                modifier = Modifier.padding(vertical = 8.dp),
                            ) {
                                Text(
                                    text = when (spec) {
                                        ColorSpec.SpecVersion.SPEC_2021 -> "2021"
                                        ColorSpec.SpecVersion.SPEC_2025 -> "2025"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth().height(64.dp).clickable(role = Role.Button) {
                        focusManager.clearFocus()
                        styleMenuExpanded = true
                    },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Style", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.weight(1f))
                    Box {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(draftPaletteStyle.name, style = MaterialTheme.typography.bodyMedium)
                            Icon(Icons.Rounded.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = styleMenuExpanded,
                            onDismissRequest = { styleMenuExpanded = false },
                        ) {
                            settings.general.availablePaletteStyles(draftColorSpec).forEach { style ->
                                DropdownMenuItem(
                                    text = { Text(style.name) },
                                    onClick = {
                                        draftPaletteStyle = style
                                        styleMenuExpanded = false
                                    },
                                    trailingIcon = {
                                        if (style == draftPaletteStyle) {
                                            Icon(Icons.Rounded.Check, contentDescription = null)
                                        }
                                    },
                                    modifier = Modifier.semantics { selected = style == draftPaletteStyle },
                                )
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(
                        onClick = {
                            draftSeedColorRgb = DefaultSeedColorRgb
                            draftColorSpec = DefaultColorSpec
                            draftPaletteStyle = DefaultPaletteStyle
                            seedColorInput = formatSeedColorRgb(DefaultSeedColorRgb)
                            seedColorInputError = false
                            lastValidatedInput = seedColorInput
                            styleMenuExpanded = false
                            focusManager.clearFocus()
                        },
                    ) {
                        Text("恢复默认")
                    }
                    TextButton(onClick = { scope.launch { openUrl("https://materialkolor.com/") } }) {
                        Text("预览网页")
                    }
                }
            }
        }
    }
}

private fun parseSeedColorRgb(input: String): Int? {
    val hex = input.trim().removePrefix("#")
    return if (hex.length == 6 && hex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
        hex.toIntOrNull(16)
    } else null
}

private fun formatSeedColorRgb(rgb: Int): String = "#" + rgb.toString(16).uppercase().padStart(6, '0')
