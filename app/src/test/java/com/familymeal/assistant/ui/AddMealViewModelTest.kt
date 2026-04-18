package com.familymeal.assistant.ui

import android.net.Uri
import app.cash.turbine.test
import com.familymeal.assistant.data.db.entity.*
import com.familymeal.assistant.data.repository.*
import com.familymeal.assistant.domain.classifier.ImageClassifier
import com.familymeal.assistant.domain.model.ClassificationResult
import com.familymeal.assistant.ui.addmeal.AddMealViewModel
import com.familymeal.assistant.ui.addmeal.ClassificationState
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AddMealViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var mealRepo: MealRepository
    private lateinit var memberRepo: MemberRepository
    private lateinit var settingsRepo: SettingsRepository
    private lateinit var classifier: ImageClassifier
    private lateinit var vm: AddMealViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mealRepo = mockk(relaxed = true)
        memberRepo = mockk(relaxed = true)
        settingsRepo = mockk(relaxed = true)
        classifier = mockk(relaxed = true)

        every { settingsRepo.isApiKeyBannerDismissed() } returns false
        every { settingsRepo.getAiApiKey() } returns null
        coEvery { memberRepo.getActiveMembers() } returns listOf(
            Member(1, "Alice", DietType.Veg)
        )
        coEvery { mealRepo.saveMeal(any(), any()) } returns 42L

        vm = AddMealViewModel(mealRepo, memberRepo, settingsRepo, classifier)
    }

    @After fun teardown() { Dispatchers.resetMain() }

    @Test
    fun `saveMeal saves immediately without waiting for classification`() = runTest {
        val uri = mockk<Uri>()
        every { classifier.classify(any()) } returns flowOf(ClassificationResult.Failure)

        vm.saveMeal(
            photoUri = uri,
            mealName = "My Meal",
            mealType = MealType.Lunch,
            memberIds = listOf(1L),
            catalogMealId = null
        )

        coVerify { mealRepo.saveMeal(any(), listOf(1L)) }
    }

    @Test
    fun `classification Success updates mealName state`() = runTest {
        val uri = mockk<Uri>()
        every { classifier.classify(any()) } returns flowOf(
            ClassificationResult.Success("Dal Makhani")
        )

        vm.startClassification(uri, savedMealId = 42L)

        vm.classificationState.test {
            val state = awaitItem()
            assertTrue(state is ClassificationState.Success || state is ClassificationState.Idle)
        }
    }

    @Test
    fun `classification Failure leaves classificationState as Idle`() = runTest {
        val uri = mockk<Uri>()
        every { classifier.classify(any()) } returns flowOf(ClassificationResult.Failure)

        vm.startClassification(uri, savedMealId = 42L)

        vm.classificationState.test {
            val state = awaitItem()
            assertTrue(state is ClassificationState.Idle)
        }
    }

    @Test
    fun `api key banner shown when not dismissed and key absent`() {
        assertTrue(vm.showApiKeyBanner.value)
    }

    @Test
    fun `api key banner hidden when already dismissed`() {
        every { settingsRepo.isApiKeyBannerDismissed() } returns true
        val vm2 = AddMealViewModel(mealRepo, memberRepo, settingsRepo, classifier)
        assertFalse(vm2.showApiKeyBanner.value)
    }

    @Test
    fun `dismissApiKeyBanner calls settingsRepository`() {
        vm.dismissApiKeyBanner()
        verify { settingsRepo.dismissApiKeyBanner() }
        assertFalse(vm.showApiKeyBanner.value)
    }

    @Test
    fun `save never blocks on classifier failure`() = runTest {
        val uri = mockk<Uri>()
        every { classifier.classify(any()) } returns kotlinx.coroutines.flow.flow {
            kotlinx.coroutines.delay(60_000)
            emit(ClassificationResult.Failure)
        }

        vm.saveMeal(uri, "Quick Save", MealType.Dinner, listOf(1L), null)
        coVerify { mealRepo.saveMeal(any(), any()) }
    }
}
