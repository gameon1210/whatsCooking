package com.familymeal.assistant.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.familymeal.assistant.data.db.AppDatabase
import com.familymeal.assistant.data.db.entity.*
import com.familymeal.assistant.data.repository.FeedbackRepositoryImpl
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class FeedbackRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: FeedbackRepositoryImpl

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), AppDatabase::class.java
        ).allowMainThreadQueries().build()
        repo = FeedbackRepositoryImpl(db, db.feedbackDao())
    }

    @After fun teardown() = db.close()

    @Test
    fun `saveFeedback MakeAgain increments positiveSignals for all meal members`() = runTest {
        val catalogId = db.catalogMealDao().insert(
            CatalogMeal(name = "Test", cuisine = "Indian", dietType = DietType.Veg, mealTypes = "Lunch")
        )
        val memberId = db.memberDao().insertMember(
            Member(name = "Alice", dietType = DietType.Veg)
        )
        val mealId = db.mealEntryDao().insertMealEntry(
            MealEntry(name = "Test", mealType = MealType.Lunch, catalogMealId = catalogId)
        )
        db.mealEntryDao().insertCrossRefs(listOf(MealMemberCrossRef(mealId, memberId)))

        repo.saveFeedback(
            signal = FeedbackSignal(mealEntryId = mealId, signalType = FeedbackType.MakeAgain),
            catalogMealId = catalogId,
            mealMemberIds = listOf(memberId),
            childMemberIds = emptyList()
        )

        val score = db.feedbackDao().getMemberMealScore(memberId, catalogId)
        assertNotNull(score)
        assertEquals(1, score!!.positiveSignals)
        assertEquals(0, score.negativeSignals)
    }

    @Test
    fun `saveFeedback and MemberMealScore update happen in same transaction`() = runTest {
        val catalogId = db.catalogMealDao().insert(
            CatalogMeal(name = "Test2", cuisine = "Italian", dietType = DietType.Veg, mealTypes = "Dinner")
        )
        val memberId = db.memberDao().insertMember(Member(name = "Bob", dietType = DietType.Veg))
        val mealId = db.mealEntryDao().insertMealEntry(
            MealEntry(name = "Test2", mealType = MealType.Dinner, catalogMealId = catalogId)
        )
        repo.saveFeedback(
            FeedbackSignal(mealEntryId = mealId, signalType = FeedbackType.NotAHit),
            catalogMealId = catalogId,
            mealMemberIds = listOf(memberId),
            childMemberIds = emptyList()
        )
        val signals = repo.getFeedbackForMeal(mealId)
        val score = db.feedbackDao().getMemberMealScore(memberId, catalogId)
        assertEquals(1, signals.size)
        assertEquals(1, score!!.negativeSignals)
    }

    @Test
    fun `KidsLiked targets child members when childMemberIds present`() = runTest {
        val catalogId = db.catalogMealDao().insert(
            CatalogMeal(name = "Kids Meal", cuisine = "Indian", dietType = DietType.Veg, mealTypes = "Lunch")
        )
        val adultId = db.memberDao().insertMember(Member(name = "Dad", dietType = DietType.NonVeg))
        val kidId = db.memberDao().insertMember(Member(name = "Kid", dietType = DietType.Veg, birthYear = 2016))
        val mealId = db.mealEntryDao().insertMealEntry(
            MealEntry(name = "Kids Meal", mealType = MealType.Lunch, catalogMealId = catalogId)
        )
        repo.saveFeedback(
            FeedbackSignal(mealEntryId = mealId, signalType = FeedbackType.KidsLiked),
            catalogMealId = catalogId,
            mealMemberIds = listOf(adultId, kidId),
            childMemberIds = listOf(kidId)
        )
        val kidScore = db.feedbackDao().getMemberMealScore(kidId, catalogId)
        val adultScore = db.feedbackDao().getMemberMealScore(adultId, catalogId)
        assertEquals(1, kidScore?.positiveSignals)
        assertNull(adultScore) // adult not targeted
    }
}
