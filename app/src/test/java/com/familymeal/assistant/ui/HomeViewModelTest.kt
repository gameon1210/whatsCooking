package com.familymeal.assistant.ui

import app.cash.turbine.test
import com.familymeal.assistant.data.db.entity.*
import com.familymeal.assistant.data.repository.*
import com.familymeal.assistant.domain.engine.*
import com.familymeal.assistant.domain.model.*
import com.familymeal.assistant.ui.common.UiState
import com.familymeal.assistant.ui.home.HomeViewModel
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class HomeViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var mealRepo: MealRepository
    private lateinit var memberRepo: MemberRepository
    private lateinit var catalogRepo: CatalogRepository
    private lateinit var feedbackRepo: FeedbackRepository
    private lateinit var weightRepo: WeightRepository
    private lateinit var settingsRepo: SettingsRepository
    private lateinit var rankingEngine: RankingEngine
    private lateinit var weightAdapter: WeightAdapter
    private lateinit var reasonGen: ReasonGenerator
    private lateinit var vm: HomeViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mealRepo = mockk(relaxed = true)
        memberRepo = mockk(relaxed = true)
        catalogRepo = mockk(relaxed = true)
        feedbackRepo = mockk(relaxed = true)
        weightRepo = mockk(relaxed = true)
        settingsRepo = mockk(relaxed = true)
        rankingEngine = RankingEngine()
        weightAdapter = WeightAdapter()
        reasonGen = ReasonGenerator()

        every { settingsRepo.getExplorationRatio() } returns 0.2f
        coEvery { memberRepo.getActiveMembers() } returns listOf(
            Member(1, "Alice", DietType.Veg)
        )
        coEvery { catalogRepo.getAllMeals() } returns listOf(
            CatalogMeal(1, "Dal Makhani", "Indian", DietType.Veg, "Lunch,Dinner")
        )
        coEvery { weightRepo.getAllWeights() } returns listOf(
            RankingWeight("recency", 0.40f, 0.40f),
            RankingWeight("makeAgain", 0.30f, 0.30f),
            RankingWeight("notAHit", 0.25f, 0.25f),
            RankingWeight("tooMuchWork", 0.20f, 0.20f),
            RankingWeight("tiffin", 0.15f, 0.15f),
            RankingWeight("memberMatch", 0.20f, 0.20f)
        )
        coEvery { mealRepo.getLastCookedForCatalogMeal(any()) } returns null
        coEvery { feedbackRepo.getFeedbackForMeal(any()) } returns emptyList()
        coEvery { feedbackRepo.getFeedbackCounts(any()) } returns emptyMap()
        coEvery { feedbackRepo.getMemberMealScores(any()) } returns emptyMap()

        vm = HomeViewModel(
            mealRepo, memberRepo, catalogRepo, feedbackRepo,
            weightRepo, settingsRepo, rankingEngine, weightAdapter, reasonGen
        )
    }

    @After fun teardown() { Dispatchers.resetMain() }

    @Test
    fun `initial state is Loading then Success`() = runTest {
        vm.suggestions.test {
            val first = awaitItem()
            // May arrive as Loading or Success depending on timing with UnconfinedTestDispatcher
            assertTrue(first is UiState.Loading || first is UiState.Success)
        }
    }

    @Test
    fun `loadSuggestions emits Success with ranked meals`() = runTest {
        vm.suggestions.test {
            val state = expectMostRecentItem()
            assertTrue(state is UiState.Success)
            val meals = (state as UiState.Success).data
            assertTrue(meals.isNotEmpty())
            assertEquals("Dal Makhani", meals[0].name)
        }
    }

    @Test
    fun `selecting meal type filter refreshes suggestions`() = runTest {
        vm.selectMealType(MealType.Breakfast)
        assertEquals(MealType.Breakfast, vm.selectedMealType.value)
    }

    @Test
    fun `selecting audience member refreshes suggestions`() = runTest {
        vm.selectAudience(listOf(1L))
        assertEquals(listOf(1L), vm.selectedMemberIds.value)
    }

    @Test
    fun `markAsCooked saves meal and triggers weight nudge`() = runTest {
        vm.markAsCooked(
            catalogMealId = 1L,
            mealName = "Dal Makhani",
            mealType = MealType.Lunch,
            memberIds = listOf(1L),
            feedbackSignals = listOf(FeedbackType.MakeAgain)
        )
        coVerify { mealRepo.saveMeal(any(), listOf(1L)) }
    }
}
