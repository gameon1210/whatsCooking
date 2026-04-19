package com.familymeal.assistant.ui.weekview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familymeal.assistant.data.db.entity.MealPin
import com.familymeal.assistant.data.db.entity.MealType
import com.familymeal.assistant.data.repository.CatalogRepository
import com.familymeal.assistant.data.repository.MealPinRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class DaySlot(
    val label: String,   // "Mon 21 Apr"
    val epochMillis: Long,
    val pins: List<MealPin>
)

@HiltViewModel
class WeekViewViewModel @Inject constructor(
    private val mealPinRepository: MealPinRepository,
    private val catalogRepository: CatalogRepository
) : ViewModel() {

    // Week starts at today midnight
    private val weekStart: Long = run {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    private val weekEnd: Long = weekStart + 7 * 86_400_000L

    val weekSlots: StateFlow<List<DaySlot>> =
        mealPinRepository.getPinsForWeek(weekStart, weekEnd)
            .map { pins ->
                (0 until 7).map { dayOffset ->
                    val dayStart = weekStart + dayOffset * 86_400_000L
                    val dayEnd = dayStart + 86_400_000L
                    val dayPins = pins.filter { it.slotDate in dayStart until dayEnd }
                    val cal = Calendar.getInstance()
                    cal.timeInMillis = dayStart
                    val dayNames = arrayOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
                    val months = arrayOf("Jan","Feb","Mar","Apr","May","Jun",
                        "Jul","Aug","Sep","Oct","Nov","Dec")
                    val label = "${dayNames[cal.get(Calendar.DAY_OF_WEEK) - 1]} " +
                        "${cal.get(Calendar.DAY_OF_MONTH)} " +
                        months[cal.get(Calendar.MONTH)]
                    DaySlot(label, dayStart, dayPins)
                }
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun pinMeal(mealName: String, catalogMealId: Long, slotDate: Long, mealType: MealType) {
        viewModelScope.launch {
            mealPinRepository.upsertPin(
                MealPin(
                    catalogMealId = catalogMealId,
                    mealName = mealName,
                    slotDate = slotDate,
                    mealType = mealType
                )
            )
        }
    }

    fun removePin(pin: MealPin) {
        viewModelScope.launch {
            mealPinRepository.clearPin(pin.id)
        }
    }
}
