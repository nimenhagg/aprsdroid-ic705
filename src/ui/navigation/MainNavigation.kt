package org.aprsdroid.app.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import org.aprsdroid.app.R

object MainRoutes {
    const val STATIONS = "stations"
    const val MAP = "map"
    const val MESSAGES = "messages"
    const val PACKETS = "packets"
    const val CHAT = "chat/{call}"

    fun chat(call: String): String = "chat/$call"

    fun isTopLevel(route: String?): Boolean = route == STATIONS || route == MAP || route == MESSAGES || route == PACKETS

    fun normalizeStartDestination(route: String?): String = when (route) {
        MAP -> MAP
        MESSAGES -> MESSAGES
        PACKETS -> PACKETS
        else -> STATIONS
    }
}

private data class MainDestination(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector
)

private val mainDestinations = listOf(
    MainDestination(MainRoutes.STATIONS, R.string.nav_stations, Icons.Default.Radio),
    MainDestination(MainRoutes.MAP, R.string.nav_map, Icons.Default.Map),
    MainDestination(MainRoutes.MESSAGES, R.string.nav_messages, Icons.AutoMirrored.Filled.Chat),
    MainDestination(MainRoutes.PACKETS, R.string.nav_packets, Icons.Default.History)
)

@Composable
fun MainNavigationBar(
    selectedRoute: String?,
    onDestinationSelected: (String) -> Unit
) {
    NavigationBar {
        mainDestinations.forEach { destination ->
            val label = stringResource(destination.labelRes)
            NavigationBarItem(
                selected = selectedRoute == destination.route,
                onClick = { onDestinationSelected(destination.route) },
                icon = {
                    Icon(
                        imageVector = destination.icon,
                        contentDescription = label
                    )
                },
                label = { Text(label) }
            )
        }
    }
}

fun NavHostController.navigateTopLevel(route: String) {
    if (currentDestination?.route == route) return
    navigate(route) {
        // Pop to the graph entry itself, not the graph's start destination.
        // This keeps Stations from being a special "pop-only" case and makes
        // every bottom-navigation destination use the same navigate/restore path.
        popUpTo(graph.id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
