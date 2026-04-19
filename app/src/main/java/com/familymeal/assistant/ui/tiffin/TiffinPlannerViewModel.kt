package com.familymeal.assistant.ui.tiffin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familymeal.assistant.data.db.entity.CatalogMeal
import com.familymeal.assistant.data.db.entity.TiffinPlan
import com.familymeal.assistant.data.repository.CatalogRepository
import com.familymeal.assistant.data.repository.TiffinPlanRepository
import com.familymeal.assistant.ui.common.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
class TiffinPlannerViewModel @Inject constructor(
    private val tiffinPlanRepository: TiffinPlanRepository,
    private val catalogRepository: CatalogRepository
) : ViewModel() {

    // Tomorrow's date at midnight
    val tomorrowMillis: Long = run {
        Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    // Current active tiffin plan
    val activePlan: StateFlow<TiffinPlan?> =
        tiffinPlanRepository.getActivePlan()
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // All catalog meals as suggestions (filtered for tiffin-friendly later)
    private val _catalogState = MutableStateFlow<UiState<List<CatalogMeal>>>(UiState.Loading)
    val catalogState: StateFlow<UiState<List<CatalogMeal>>> = _catalogState

    // Search query for picking a meal
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    val filteredCatalog: StateFlow<List<CatalogMeal>> = combine(
        _catalogState,
        _searchQuery
    ) { state, query ->
        if (state !is UiState.Success) return@combine emptyList()
        if (query.isBlank()) state.data
        else state.data.filter { it.name.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        viewModelScope.launch {
            try {
                val meals = catalogRepository.getAllMeals()
                _catalogState.value = UiState.Success(meals)
            } catch (e: Exception) {
                _catalogState.value = UiState.Error(e.message ?: "Failed to load catalog")
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun pinMeal(catalogMeal: CatalogMeal) {
        viewModelScope.launch {
            val plan = TiffinPlan(
                catalogMealId = catalogMeal.id,
                mealName = catalogMeal.name,
                plannedDate = tomorrowMillis
            )
            tiffinPlanRepository.savePlan(plan)
        }
    }

    fun clearPlan() {
        viewModelScope.launch {
            val planId = activePlan.value?.id ?: return@launch
            tiffinPlanRepository.clearPlan(planId)
        }
    }
}
