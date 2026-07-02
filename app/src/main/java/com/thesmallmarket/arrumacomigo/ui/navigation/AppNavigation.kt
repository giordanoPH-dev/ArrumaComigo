package com.thesmallmarket.arrumacomigo.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.MeetingRoom
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.thesmallmarket.arrumacomigo.ui.components.CelebrationOverlay
import com.thesmallmarket.arrumacomigo.ui.history.HistoryScreen
import com.thesmallmarket.arrumacomigo.ui.people.PeopleScreen
import com.thesmallmarket.arrumacomigo.ui.rooms.RoomsScreen
import com.thesmallmarket.arrumacomigo.ui.tasks.TaskEditScreen
import com.thesmallmarket.arrumacomigo.ui.today.TodayScreen

enum class TopDestination(val route: String, val label: String, val icon: ImageVector) {
    TODAY("today", "Hoje", Icons.Rounded.CalendarMonth),
    ROOMS("rooms", "Cômodos", Icons.Rounded.MeetingRoom),
    PEOPLE("people", "Pessoas", Icons.Rounded.Group),
    HISTORY("history", "Balanço", Icons.Rounded.BarChart),
}

private fun taskEditRoute(taskId: Long, roomId: Long) = "task_edit/$taskId/$roomId"

@Composable
fun ArrumaComigoApp(widthSizeClass: WindowWidthSizeClass) {
    val navController = rememberNavController()
    val expanded = widthSizeClass != WindowWidthSizeClass.Compact
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val onSelectTop: (TopDestination) -> Unit = { dest ->
        navController.navigate(dest.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Box(Modifier.fillMaxSize()) {
        if (expanded) {
            Row(Modifier.fillMaxSize()) {
                AppNavigationRail(currentRoute, onSelectTop)
                // Sem Scaffold neste ramo: o padding evita o conteúdo (snackbar, botões)
                // atrás da barra de navegação do sistema no modo edge-to-edge.
                Box(Modifier.fillMaxSize().navigationBarsPadding()) {
                    AppNavHost(navController, twoPane = true)
                }
            }
        } else {
            Scaffold(
                bottomBar = { AppBottomBar(currentRoute, onSelectTop) },
                containerColor = MaterialTheme.colorScheme.background,
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding)) {
                    AppNavHost(navController, twoPane = false)
                }
            }
        }
        // Confete em tela cheia (+ vibração e plim) a cada conclusão, acima de tudo.
        CelebrationOverlay(Modifier.fillMaxSize())
    }
}

@Composable
private fun AppNavHost(navController: NavHostController, twoPane: Boolean) {
    NavHost(navController = navController, startDestination = TopDestination.TODAY.route) {
        composable(TopDestination.TODAY.route) {
            TodayScreen(onTaskClick = { id -> navController.navigate(taskEditRoute(id, 0)) })
        }
        composable(TopDestination.ROOMS.route) {
            RoomsScreen(
                twoPane = twoPane,
                onAddTask = { roomId -> navController.navigate(taskEditRoute(0, roomId)) },
                onEditTask = { id -> navController.navigate(taskEditRoute(id, 0)) },
            )
        }
        composable(TopDestination.PEOPLE.route) { PeopleScreen() }
        composable(TopDestination.HISTORY.route) { HistoryScreen() }
        composable("task_edit/{taskId}/{roomId}") { entry ->
            val taskId = entry.arguments?.getString("taskId")?.toLongOrNull() ?: 0L
            val roomId = entry.arguments?.getString("roomId")?.toLongOrNull() ?: 0L
            TaskEditScreen(
                taskId = taskId,
                presetRoomId = roomId,
                onDone = { navController.popBackStack() },
            )
        }
    }
}

@Composable
private fun AppNavigationRail(currentRoute: String?, onSelect: (TopDestination) -> Unit) {
    NavigationRail(containerColor = MaterialTheme.colorScheme.background) {
        TopDestination.entries.forEach { dest ->
            NavigationRailItem(
                selected = currentRoute == dest.route,
                onClick = { onSelect(dest) },
                icon = { Icon(dest.icon, contentDescription = dest.label) },
                label = { Text(dest.label) },
            )
        }
    }
}

@Composable
private fun AppBottomBar(currentRoute: String?, onSelect: (TopDestination) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.background) {
        TopDestination.entries.forEach { dest ->
            NavigationBarItem(
                selected = currentRoute == dest.route,
                onClick = { onSelect(dest) },
                icon = { Icon(dest.icon, contentDescription = dest.label) },
                label = { Text(dest.label) },
            )
        }
    }
}
