package tv.hsrui.bolo.main

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.ui.graphics.vector.ImageVector

enum class MainTab(val title: String, val icon: ImageVector) {
    HOME("主页", Icons.Rounded.Home),
    REGION("分区", Icons.Rounded.GridView),
    MEDIA("影视番剧", Icons.Rounded.Movie)
}