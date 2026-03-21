package com.familymeal.assistant.ui

import app.cash.turbine.test
import com.familymeal.assistant.data.db.entity.*
import com.familymeal.assistant.data.repository.*
import com.familymeal.assistant.ui.common.UiState
import com.familymeal.assistant.ui.history.HistoryFilter
import com.familymeal.assistant.ui.history.HistoryViewModel
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class HistoryViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var mealRepo: MealRepository
    private lateinit var memberRepo: MemberRepository
    private lateinit var feedbackRepo: FeedbackRepository
    private lateinit var vm: HistoryViewModel

    private val meal1 = MealEntry(1, "Dal Makhani", mealType = MealType.Lunch, cookedAt = System.currentTimeMillis() - 86_400_000L)
    private val meal2 = MealEntry(2, "Omelette", mealType = MealType.Breakfast, cookedAt = System.currentTimeMillis() - 2 * 86_400_000L)
    private val alice = Member(1, "Alice", DietType.Veg)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mealRepo = mockk(relaxed = true)
        memberRepo = mockk(relaxed = true)
        feedbackRepo = mockk(relaxed = true)

        every { mealRepo.observeAllMeals() } returns flowOf(listOf(meal1, meal2))
        coEvery { memberRepo.getActiveMembers() } returns listOf(alice)
        coEvery { feedbackRepo.getFeedbackForMeal(any()) } returns emptyList()

        vm = HistoryViewModel(mealRepo, memberRepo, feedbackRepo)
    }

    @After fun teardown() { Dispatchers.resetMain() }

    @Test
    fun `initial state loads all meals`() = runTest {
        vm.meals.test {
            val state = awaitItem()
            assertTrue(state is UiState.Success)
            assertEquals(2, (state as UiState.Success).data.size)
        }
    }

    @Test
    fun `filterByMealType filters to Lunch only`() = runTest {
        vm.setMealTypeFilter(MealType.Lunch)
        vm.meals.test {
            val state = awaitItem()
            assertTrue(state is UiState.Success)
            val meals = (state as UiState.Success).data
            assertTrue(meals.all { it.mealType == MealType.Lunch })
        }
    }

    @Test
    fun `clearFilters restores all meals`() = runTest {
        vm.setMealTypeFilter(MealType.Breakfast)
        vm.clearFilters()
        vm.meals.test {
            val state = awaitItem()
            assertEquals(2, (state as UiState.Success).data.size)
        }
    }

    @Test
    fun `getFeedbackForMeal returns signal list`() = runTest {
        coEvery { feedbackRepo.getFeedbackForMeal(1L) } returns listOf(
            FeedbackSignal(1, mealEntryId = 1L, signalType = FeedbackType.MakeAgain)
        )
        val signals = vm.getFeedbackForMeal(1L)
        assertEquals(1, signals.size)
        assertEquals(FeedbackType.MakeAgain, signals[0].signalType)
    }
}
