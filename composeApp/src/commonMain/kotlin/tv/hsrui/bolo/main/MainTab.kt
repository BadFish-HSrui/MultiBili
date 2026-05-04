package tv.hsrui.bolo.main

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.ui.graphics.vector.ImageVector

enum class MainTab(val title: String, val icon: ImageVector) {
    HOME("主页", Icons.Filled.Home),
    REGION("分区", Icons.Filled.GridView)
}