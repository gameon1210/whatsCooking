package com.familymeal.assistant.ui

import app.cash.turbine.test
import com.familymeal.assistant.data.db.entity.DietType
import com.familymeal.assistant.data.db.entity.Member
import com.familymeal.assistant.data.repository.MealRepository
import com.familymeal.assistant.data.repository.MemberRepository
import com.familymeal.assistant.ui.members.MemberProfilesViewModel
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class MemberProfilesViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var memberRepo: MemberRepository
    private lateinit var mealRepo: MealRepository
    private lateinit var vm: MemberProfilesViewModel

    private val alice = Member(1, "Alice", DietType.Veg)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        memberRepo = mockk(relaxed = true)
        mealRepo = mockk(relaxed = true)
        every { memberRepo.observeActiveMembers() } returns flowOf(listOf(alice))
        every { mealRepo.observeMealsByMember(any()) } returns flowOf(emptyList())
        vm = MemberProfilesViewModel(memberRepo, mealRepo)
    }

    @After fun teardown() { Dispatchers.resetMain() }

    @Test
    fun `members loaded from repository`() = runTest {
        vm.members.test {
            val members = awaitItem()
            assertEquals(1, members.size)
            assertEquals("Alice", members[0].name)
        }
    }

    @Test
    fun `addMember calls repository`() = runTest {
        vm.addMember("Bob", DietType.NonVeg, null)
        coVerify { memberRepo.addMember(match { it.name == "Bob" && it.dietType == DietType.NonVeg }) }
    }

    @Test
    fun `deactivateMember calls repository`() = runTest {
        vm.deactivateMember(1L)
        coVerify { memberRepo.deactivateMember(1L) }
    }

    @Test
    fun `updateMember calls repository`() = runTest {
        val updated = alice.copy(name = "Alicia")
        vm.updateMember(updated)
        coVerify { memberRepo.updateMember(updated) }
    }
}
