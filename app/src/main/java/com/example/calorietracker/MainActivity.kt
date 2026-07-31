package com.example.calorietracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.calorietracker.ui.HistoryScreen
import com.example.calorietracker.ui.HomeScreen
import com.example.calorietracker.ui.MainViewModel
import com.example.calorietracker.ui.SettingsScreen
import com.example.calorietracker.ui.WeightScreen
import com.example.calorietracker.ui.theme.CalorieTrackerTheme

private data class TabDestination(val route: String, val label: String, val icon: ImageVector)

private val TAB_DESTINATIONS = listOf(
    TabDestination("home", "Heute", Icons.Filled.Home),
    TabDestination("history", "Historie", Icons.Filled.History),
    TabDestination("weight", "Gewicht", Icons.Filled.MonitorWeight),
)

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CalorieTrackerTheme {
                val navController = rememberNavController()
                val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

                Scaffold(
                    bottomBar = {
                        if (TAB_DESTINATIONS.any { it.route == currentRoute }) {
                            NavigationBar {
                                TAB_DESTINATIONS.forEach { tab ->
                                    NavigationBarItem(
                                        selected = currentRoute == tab.route,
                                        onClick = {
                                            navController.navigate(tab.route) {
                                                popUpTo(navController.graph.findStartDestination().id) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                                        label = { Text(tab.label) },
                                    )
                                }
                            }
                        }
                    },
                ) { outerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = "home",
                        modifier = Modifier.padding(outerPadding),
                    ) {
                        composable("home") {
                            HomeScreen(
                                viewModel = viewModel,
                                onOpenSettings = { navController.navigate("settings") },
                            )
                        }
                        composable("history") {
                            HistoryScreen(viewModel = viewModel)
                        }
                        composable("weight") {
                            WeightScreen(viewModel = viewModel)
                        }
                        composable("settings") {
                            SettingsScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() },
                            )
                        }
                    }
                }
            }
        }
    }
}
