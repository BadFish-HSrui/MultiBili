package tv.hsrui.bolo.boloSetting.setting.general

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import tv.hsrui.bolo.PlatformType
import tv.hsrui.bolo.boloSetting.BoloSettings
import tv.hsrui.bolo.boloSetting.AppThemeMode
import tv.hsrui.bolo.getPlatform
import tv.hsrui.bolo.ui.common.snackbar.SnackbarManager
import tv.hsrui.bolo.ui.components.topBar.ShowTopBarWithNavigationButton
import tv.hsrui.bolo.utils.calculateWithoutBottom
import tv.hsrui.bolo.utils.isExpanded
import tv.hsrui.bolo.utils.url.isSystemLinkHandlingEnabled
import tv.hsrui.bolo.utils.url.openSystemLinkSettings
import tv.hsrui.bolo.utils.url.setSystemLinkHandlingEnabled

@Composable
fun GeneralSettingsScreen(modifier: Modifier = Modifier) {
    val settings: BoloSettings = koinInject()
    val snackbar: SnackbarManager = koinInject()
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
}
