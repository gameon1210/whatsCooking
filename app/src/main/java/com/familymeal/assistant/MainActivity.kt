package com.familymeal.assistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.familymeal.assistant.data.repository.CatalogRepository
import com.familymeal.assistant.data.repository.MealRepository
import com.familymeal.assistant.data.repository.SettingsRepository
import com.familymeal.assistant.data.repository.WeightRepository
import com.familymeal.assistant.ui.navigation.AppNavigation
import com.familymeal.assistant.ui.navigation.Screen
import com.familymeal.assistant.ui.theme.FamilyMealAssistantTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var catalogRepository: CatalogRepository
    @Inject lateinit var weightRepository: WeightRepository
    @Inject lateinit var mealRepository: MealRepository
    @Inject lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Seed catalog and weights on startup (no-ops after first launch)
        lifecycleScope.launch {
            catalogRepository.seedIfEmpty()
            weightRepository.seedIfEmpty()
            mealRepository.reconcilePendingClassifications()
        }

        val startDestination = if (settingsRepository.isOnboardingComplete()) {
            Screen.Home.route
        } else {
            Screen.Onboarding.route
        }

        setContent {
            FamilyMealAssistantTheme {
                AppNavigation(startDestination = startDestination)
            }
        }
    }
}
