package tv.hsrui.bolo.livePlayer.controls

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scrim
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import kotlinx.coroutines.launch
import tv.hsrui.bolo.PlatformType
import tv.hsrui.bolo.boloSetting.setting.live.LiveDanmakuSettingsContent
import tv.hsrui.bolo.boloSetting.setting.live.LiveSettings
import tv.hsrui.bolo.getPlatform
import tv.hsrui.bolo.player.controls.BoloPlayerShortcutSettingsSheet
import tv.hsrui.bolo.player.controls.PlayerGestureSwitch
import tv.hsrui.bolo.player.controls.PlayerRebuildCard
import tv.hsrui.bolo.ui.components.dialog.ShowInfoDialog

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LivePlayerSettingsSheet(
    isOpen: Boolean,
    settings: LiveSettings,
    supportsDeviceGestures: Boolean,
    rebuildEnabled: Boolean,
    onRebuild: () -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val pager = rememberPagerState { 2 }
    val scope = rememberCoroutineScope()
    val direction = LocalLayoutDirection.current
    val platform = getPlatform()
    val isDesktop = platform.type == PlatformType.Desktop
    val isIpad = platform.type == PlatformType.Ios && !platform.isPhone
    val scrimAlpha by animateFloatAsState(if (isOpen) 1f else 0f)
    var infoMessage by remember(isOpen) { mutableStateOf<String?>(null) }
    var showShortcutSettings by remember(isOpen) { mutableStateOf(false) }
    LaunchedEffect(isOpen) { if (isOpen) drawer.open() else drawer.close() }
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = (isOpen || !drawer.isClosed) && infoMessage == null && !showShortcutSettings,
        onBackCompleted = onDismissRequest,
    )
    if (!isOpen && drawer.isClosed && !drawer.isAnimationRunning) return
    if (isOpen) infoMessage?.let { message ->
        ShowInfoDialog(onConfirm = { infoMessage = null }) {
            Text(
                text = message,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 12.dp).alpha(0.8F),
            )
        }
    }
    if (isDesktop && isOpen && showShortcutSettings) {
        BoloPlayerShortcutSettingsSheet(settings.shortcuts, onDismissRequest = { showShortcutSettings = false })
    }
    BoxWithConstraints(modifier.fillMaxSize().clipToBounds()) {
        val sheetWidth = min(300.dp, maxWidth * .85f)
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            ModalNavigationDrawer(
                drawerState = drawer,
                gesturesEnabled = false,
                scrimColor = Color.Unspecified,
                drawerContent = {
                    CompositionLocalProvider(LocalLayoutDirection provides direction) {
                        ModalDrawerSheet(
                            modifier = Modifier.width(sheetWidth).fillMaxHeight(),
                            drawerShape = AbsoluteRoundedCornerShape(topLeft = 16.dp, bottomLeft = 16.dp),
                            windowInsets = if (isIpad) WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.Right)
                                else WindowInsets(),
                        ) {
                            PrimaryTabRow(selectedTabIndex = pager.currentPage) {
                                listOf("播放器", "弹幕设置").forEachIndexed { index, title ->
                                    Tab(
                                        selected = pager.currentPage == index,
                                        enabled = isOpen,
                                        onClick = { scope.launch { pager.animateScrollToPage(index) } },
                                        text = { Text(title, maxLines = 1, style = MaterialTheme.typography.labelMedium) },
                                    )
                                }
                            }
                            HorizontalPager(
                                state = pager,
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                userScrollEnabled = isOpen,
                                verticalAlignment = Alignment.Top,
                            ) { page ->
                                val isActive = isOpen && pager.currentPage == page && !pager.isScrollInProgress
                                Column(
                                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                                        .padding(start = 24.dp, top = 8.dp, end = 24.dp, bottom = 24.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    if (page == 0) {
                                        Card(Modifier.fillMaxWidth()) {
                                            Column(Modifier.padding(4.dp).animateContentSize()) {
                                                PlayerGestureSwitch(
                                                    label = "启用暂停手势",
                                                    checked = settings.pauseGestureEnabled,
                                                    enabled = isActive,
                                                    onCheckedChange = { settings.pauseGestureEnabled = it },
                                                )
                                                if (isDesktop) {
                                                    if (settings.pauseGestureEnabled) {
                                                        HorizontalDivider(thickness = 1.dp)
                                                        PlayerGestureSwitch(
                                                            label = "双击暂停",
                                                            checked = settings.desktopDoubleClickPauseEnabled,
                                                            enabled = isActive,
                                                            onCheckedChange = { settings.desktopDoubleClickPauseEnabled = it },
                                                        )
                                                    }
                                                    HorizontalDivider(thickness = 1.dp)
                                                    PlayerGestureSwitch(
                                                        label = "默认窗口内全屏",
                                                        checked = settings.desktopDefaultWindowFullscreenEnabled,
                                                        enabled = isActive,
                                                        onCheckedChange = { settings.desktopDefaultWindowFullscreenEnabled = it },
                                                        onInfoClick = {
                                                            infoMessage = "双击或使用切换全屏快捷键使播放区域填满窗口，而不是切换系统全屏，开启双击暂停后双击时不生效。"
                                                        },
                                                    )
                                                } else if (supportsDeviceGestures) {
                                                    HorizontalDivider(thickness = 1.dp)
                                                    PlayerGestureSwitch(
                                                        label = "启用亮度调节手势",
                                                        checked = settings.brightnessGestureEnabled,
                                                        enabled = isActive,
                                                        onCheckedChange = { settings.brightnessGestureEnabled = it },
                                                    )
                                                    HorizontalDivider(thickness = 1.dp)
                                                    PlayerGestureSwitch(
                                                        label = "启用音量调节手势",
                                                        checked = settings.volumeGestureEnabled,
                                                        enabled = isActive,
                                                        onCheckedChange = { settings.volumeGestureEnabled = it },
                                                    )
                                                }
                                            }
                                        }
                                        if (isDesktop) {
                                            Card(
                                                onClick = { showShortcutSettings = true },
                                                enabled = isActive,
                                                modifier = Modifier.fillMaxWidth().height(40.dp),
                                            ) {
                                                Box(Modifier.fillMaxSize().padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                                                    Text("快捷键设置", style = MaterialTheme.typography.bodyMedium)
                                                }
                                            }
                                        }
                                        PlayerRebuildCard(enabled = isActive && rebuildEnabled, onRebuild = onRebuild)
                                    } else {
                                        LiveDanmakuSettingsContent(
                                            settings = settings.danmaku,
                                            isActive = isActive,
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
            ) {
                CompositionLocalProvider(LocalLayoutDirection provides direction) {
                    Scrim(contentDescription = "关闭播放器设置", onClick = onDismissRequest, alpha = { scrimAlpha })
                }
            }
        }
    }
}
