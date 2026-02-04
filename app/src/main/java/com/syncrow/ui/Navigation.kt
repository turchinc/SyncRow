package com.syncrow.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.syncrow.ui.workout.WorkoutDashboard
import com.syncrow.ui.workout.WorkoutViewModel
import com.syncrow.ui.home.HomeScreen
import com.syncrow.ui.discovery.DiscoveryScreen
import com.syncrow.ui.profile.ProfileScreen
import com.syncrow.ui.history.HistoryScreen
import com.syncrow.ui.history.WorkoutDetailScreen
import com.syncrow.ui.about.AboutScreen

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Workout : Screen("workout")
    object Discovery : Screen("discovery")
    object Profile : Screen("profile")
    object History : Screen("history")
    object About : Screen("about")
    object WorkoutDetail : Screen("workout_detail/{workoutId}") {
        fun createRoute(workoutId: Long) = "workout_detail/$workoutId"
    }
}

@Composable
fun SyncRowNavGraph(viewModel: WorkoutViewModel, onQuit: () -> Unit) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Screen.Home.route) {
        composable(Screen.Home.route) {
            HomeScreen(
                viewModel = viewModel,
                onStartWorkout = { navController.navigate(Screen.Workout.route) },
                onNavigateToDiscovery = { navController.navigate(Screen.Discovery.route) },
                onNavigateToProfile = { navController.navigate(Screen.Profile.route) },
                onNavigateToHistory = { navController.navigate(Screen.History.route) },
                onNavigateToAbout = { navController.navigate(Screen.About.route) },
                onQuit = onQuit
            )
        }
        composable(Screen.Workout.route) {
            WorkoutDashboard(
                viewModel = viewModel,
                onFinish = { navController.popBackStack() }
            )
        }
        composable(Screen.Discovery.route) {
            DiscoveryScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Profile.route) {
            ProfileScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.History.route) {
            HistoryScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onNavigateToDetail = { id -> navController.navigate(Screen.WorkoutDetail.createRoute(id)) }
            )
        }
        composable(Screen.About.route) {
            AboutScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route = Screen.WorkoutDetail.route,
            arguments = listOf(navArgument("workoutId") { type = NavType.LongType })
        ) { backStackEntry ->
            val workoutId = backStackEntry.arguments?.getLong("workoutId") ?: return@composable
            WorkoutDetailScreen(
                viewModel = viewModel,
                workoutId = workoutId,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
