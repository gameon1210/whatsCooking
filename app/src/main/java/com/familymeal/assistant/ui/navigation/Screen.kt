package com.familymeal.assistant.ui.navigation

sealed class Screen(val route: String) {
    object Onboarding : Screen("onboarding")
    object Home : Screen("home")
    object AddMeal : Screen("add_meal")
    object History : Screen("history")
    object Settings : Screen("settings")
    object MemberProfiles : Screen("member_profiles")
    object AiSettings : Screen("ai_settings")
    object TiffinPlanner : Screen("tiffin_planner")
    object WeekView : Screen("week_view")
}
