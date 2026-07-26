package com.example.calorietracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.calorietracker.ui.HomeScreen
import com.example.calorietracker.ui.MainViewModel
import com.example.calorietracker.ui.SettingsScreen
import com.example.calorietracker.ui.WeightScreen
import com.example.calorietracker.ui.theme.CalorieTrackerTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CalorieTrackerTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "home") {
                    composable("home") {
                        HomeScreen(
                            viewModel = viewModel,
                            onOpenSettings = { navController.navigate("settings") },
                            onOpenWeight = { navController.navigate("weight") },
                        )
                    }
                    composable("settings") {
                        SettingsScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable("weight") {
                        WeightScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() },
                        )
                    }
                }
            }
        }
    }
}
