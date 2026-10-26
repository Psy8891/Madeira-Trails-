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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigation.NavHostController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import pt.vereda.app.R
import pt.vereda.app.VeredaApplication
import pt.vereda.app.ui.explore.ExploreScreen
import pt.vereda.app.ui.map.MapScreen
import pt.vereda.app.ui.map.MapSelection
import java.time.Duration

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
    val repository = (LocalContext.current.applicationContext as VeredaApplication).repository
    val scope = rememberCoroutineScope()

    // Shared between Explore (pick a trail) and Map (show it).
    var selection by remember { mutableStateOf<MapSelection?>(null) }

    // Official status changes during the day: refresh when the app comes back to the front.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        scope.launch { repository.refreshStatusIfOlderThan(Duration.ofMinutes(10)) }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = currentRoute == tab.route,
                        onClick = { navController.navigateToTab(tab) },
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
            composable(Tab.MAP.route) {
                MapScreen(selection = selection, onSelect = { selection = it })
            }
            composable(Tab.EXPLORE.route) {
                ExploreScreen(
                    onOpen = { target ->
                        selection = MapSelection(target, moveCamera = true)
                        navController.navigateToTab(Tab.MAP)
                    },
                )
            }
            composable(Tab.RECORD.route) {
                PlaceholderScreen(title = R.string.record_title, body = R.string.record_placeholder)
            }
            composable(Tab.ME.route) {
                MeScreen()
            }
        }
    }
}

private fun NavHostController.navigateToTab(tab: Tab) {
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
