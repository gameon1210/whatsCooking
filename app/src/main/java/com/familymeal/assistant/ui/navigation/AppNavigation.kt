package com.familymeal.assistant.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.familymeal.assistant.ui.addmeal.AddMealScreen
import com.familymeal.assistant.ui.history.HistoryScreen
import com.familymeal.assistant.ui.home.HomeScreen
import com.familymeal.assistant.ui.members.MemberProfilesScreen
import com.familymeal.assistant.ui.onboarding.OnboardingScreen
import com.familymeal.assistant.ui.settings.SettingsScreen

@Composable
fun AppNavigation(startDestination: String) {
    val navController = rememberNavController()
    val showBottomNav = remember(navController) {
        derivedStateOf {
            val route = navController.currentBackStackEntry?.destination?.route
            route in listOf(Screen.Home.route, Screen.AddMeal.route, Screen.History.route)
        }
    }

    Scaffold(
        bottomBar = {
            if (showBottomNav.value) {
                BottomNavBar(navController)
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(padding)
        ) {
            composable(Screen.Onboarding.route) {
                OnboardingScreen(onComplete = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                })
            }
            composable(Screen.Home.route) {
                HomeScreen(onNavigateToSettings = { navController.navigate(Screen.Settings.route) })
            }
            composable(Screen.AddMeal.route) {
                AddMealScreen(onMealSaved = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = false }
                    }
                })
            }
            composable(Screen.History.route) {
                HistoryScreen()
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToMembers = { navController.navigate(Screen.MemberProfiles.route) }
                )
            }
            composable(Screen.MemberProfiles.route) {
                MemberProfilesScreen(onNavigateBack = { navController.popBackStack() })
            }
        }
    }
}
