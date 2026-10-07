package tv.hsrui.bolo.ui.common.update

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import tv.hsrui.network.feature.update.AppReleaseData

@Composable
expect fun ShowAppUpdateAction(release: AppReleaseData, modifier: Modifier = Modifier)
