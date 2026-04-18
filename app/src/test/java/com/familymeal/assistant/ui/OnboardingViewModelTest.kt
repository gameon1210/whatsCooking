package com.familymeal.assistant.ui

import com.familymeal.assistant.data.db.entity.DietType
import com.familymeal.assistant.data.repository.MemberRepository
import com.familymeal.assistant.data.repository.SettingsRepository
import com.familymeal.assistant.ui.onboarding.OnboardingViewModel
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class OnboardingViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var memberRepo: MemberRepository
    private lateinit var settingsRepo: SettingsRepository
    private lateinit var vm: OnboardingViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        memberRepo = mockk(relaxed = true)
        settingsRepo = mockk(relaxed = true)
        vm = OnboardingViewModel(memberRepo, settingsRepo)
    }

    @After
    fun teardown() { Dispatchers.resetMain() }

    @Test
    fun `addMember adds to pending list`() {
        vm.addMember("Alice", DietType.Veg, null)
        assertEquals(1, vm.pendingMembers.value.size)
        assertEquals("Alice", vm.pendingMembers.value[0].name)
    }

    @Test
    fun `removeMember removes from pending list`() {
        vm.addMember("Alice", DietType.Veg, null)
        vm.addMember("Bob", DietType.NonVeg, null)
        vm.removeMember(0)
        assertEquals(1, vm.pendingMembers.value.size)
        assertEquals("Bob", vm.pendingMembers.value[0].name)
    }

    @Test
    fun `completeOnboarding saves all pending members`() = runTest {
        vm.addMember("Alice", DietType.Veg, null)
        vm.addMember("Bob", DietType.NonVeg, 1985)
        vm.completeOnboarding()
        coVerify(exactly = 2) { memberRepo.addMember(any()) }
        io.mockk.verify { settingsRepo.markOnboardingComplete() }
    }

    @Test
    fun `canProceed is false when no members added`() {
        assertFalse(vm.canProceed.value)
    }

    @Test
    fun `canProceed is true after adding one member`() {
        vm.addMember("Alice", DietType.Veg, null)
        assertTrue(vm.canProceed.value)
    }
}
