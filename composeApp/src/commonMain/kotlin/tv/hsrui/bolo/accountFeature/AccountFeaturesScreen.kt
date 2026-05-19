package tv.hsrui.bolo.accountFeature

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import tv.hsrui.bolo.accountFeature.feature.AccountFeature
import tv.hsrui.bolo.navigation.BoloRoute
import tv.hsrui.bolo.navigation.Navigator
import tv.hsrui.bolo.ui.components.topBar.ShowTopBarWithNavigationButton
import tv.hsrui.bolo.ui.theme.BoloShapes
import tv.hsrui.bolo.utils.AppWindowSize
import tv.hsrui.bolo.utils.getNowWindowSize
import tv.hsrui.bolo.utils.isCompact

@Composable
fun AccountFeaturesScreen(modifier: Modifier = Modifier) {
    val windowSize = getNowWindowSize()
    Box {
        Scaffold(
            topBar = {
                ShowTopBarWithNavigationButton(
                    goBackBefore = BoloRoute.AccountFeature.List,
                    title = { Text("账号功能") })
            },
            modifier = modifier.fillMaxSize()
        ) { innerPadding ->
            val features = AccountFeature.entries
            val navigator: Navigator = koinInject()
            var selectedFeature by rememberSaveable { mutableStateOf(AccountFeature.History) }

            LaunchedEffect(Unit) {
                if (windowSize != AppWindowSize.COMPACT && navigator.backStack.last() == BoloRoute.AccountFeature.List) {
                    navigator.navigateTo(BoloRoute.AccountFeature.History)
                }
            }

            /*TODO: 在compose-material3完成适配后，用SegmentedListItem代替Card*/
            LazyColumn(
                modifier = Modifier.padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                item { AccountInfoContent() }

                items(
                    items = features,
                    key = { it.name }
                ) { feature ->
                    Card(
                        onClick = {
                            navigator.navigateTo(feature.route); selectedFeature = feature
                        },
                        shape = BoloShapes.List.Item,
                        colors = if (feature == selectedFeature && !isCompact()) {
                            CardDefaults.cardColors(MaterialTheme.colorScheme.primaryContainer)
                        } else CardDefaults.cardColors(),
                        modifier = Modifier.fillMaxWidth().height(64.dp)
                    ) {
                        Row(
                            Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = feature.icon,
                                contentDescription = null,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            Text(
                                text = feature.title,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }

                item {
                    Card(
                        onClick = { },
                        shape = BoloShapes.List.Bottom,
                        modifier = Modifier.fillMaxWidth().height(64.dp)
                    ) {
                        Row(
                            Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = null,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            Text(
                                text = "应用设置",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }
            }
        }
        if (!isCompact()) {
            VerticalDivider(Modifier.align(Alignment.CenterEnd).fillMaxHeight())
        }
    }
}

