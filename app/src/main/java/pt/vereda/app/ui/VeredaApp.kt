package pt.vereda.app.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import pt.vereda.app.R
import pt.vereda.app.ui.explore.ExploreScreen
import pt.vereda.app.ui.map.MapScreen

private enum class Tab(val route: String, @StringRes val label: Int, @DrawableRes val icon: Int) {
    MAP("map", R.string.tab_map, R.drawable.ic_tab_map),
    EXPLORE("explore", R.string.tab_explore, R.drawable.ic_tab_explore),
    RECORD("record", R.string.tab_record, R.drawable.ic_tab_record),
    ME("me", R.string.tab_me, R.drawable.ic_tab_me),
}

/** App shell: four bottom tabs (spec: Screens and navigation). */
@Composable
fun VeredaApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = currentRoute == tab.route,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(painterResource(tab.icon), contentDescription = null) },
                        label = { Text(stringResource(tab.label)) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Tab.MAP.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(Tab.MAP.route) { MapScreen() }
            composable(Tab.EXPLORE.route) { ExploreScreen() }
            composable(Tab.RECORD.route) {
                PlaceholderScreen(title = R.string.record_title, body = R.string.record_placeholder)
            }
            composable(Tab.ME.route) {
                PlaceholderScreen(title = R.string.me_title, body = R.string.me_placeholder)
            }
        }
    }
}
