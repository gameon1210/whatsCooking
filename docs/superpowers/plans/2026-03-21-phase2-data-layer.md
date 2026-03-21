# Phase 2: Data Layer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement all 7 Room entities, 5 DAOs, Room database class, 6 repository interfaces + implementations, Hilt DI modules, and the two seed asset files (`catalog.json`, `ranking_config.json`).

**Architecture:** Room with KSP. All entities in `data/db/entity/`. DAOs in `data/db/dao/`. Repository interfaces in `data/repository/` with `Impl` classes alongside. Hilt binds interfaces to impls via `RepositoryModule`. `FeedbackRepository.saveFeedback()` updates `MemberMealScore` in the same DB transaction.

**Tech Stack:** Room 2.7.1, KSP, Hilt 2.52, Kotlin Coroutines 1.9.0, Gson 2.11.0, JUnit 4, Room in-memory testing

**Prerequisite:** Phase 1 complete (Hilt + KSP configured, package = `com.familymeal.assistant`).

---

## File Map

```
app/src/main/java/com/familymeal/assistant/
  data/
    db/
      AppDatabase.kt
      entity/
        DietType.kt          (enum)
        MealType.kt          (enum)
        FeedbackType.kt      (enum)
        Member.kt
        MealEntry.kt
        MealMemberCrossRef.kt
        CatalogMeal.kt
        FeedbackSignal.kt
        RankingWeight.kt
        MemberMealScore.kt
      dao/
        MemberDao.kt
        MealEntryDao.kt
        CatalogMealDao.kt
        FeedbackDao.kt
        RankingWeightDao.kt
      converters/
        Converters.kt
    repository/
      MealRepository.kt          (interface)
      MealRepositoryImpl.kt
      MemberRepository.kt        (interface)
      MemberRepositoryImpl.kt
      FeedbackRepository.kt      (interface)
      FeedbackRepositoryImpl.kt
      CatalogRepository.kt       (interface)
      CatalogRepositoryImpl.kt
      WeightRepository.kt        (interface)
      WeightRepositoryImpl.kt
      SettingsRepository.kt      (interface)
      SettingsRepositoryImpl.kt
  di/
    DatabaseModule.kt
    RepositoryModule.kt

app/src/main/assets/
  catalog.json
  ranking_config.json

app/src/test/java/com/familymeal/assistant/
  data/
    FeedbackRepositoryTest.kt
    CatalogRepositoryTest.kt
    MealRepositoryTest.kt
    WeightRepositoryTest.kt
```

---

### Task 1: Enum types

**Files:**
- Create: `data/db/entity/DietType.kt`
- Create: `data/db/entity/MealType.kt`
- Create: `data/db/entity/FeedbackType.kt`

- [ ] **Step 1: Create DietType.kt**

```kotlin
package com.familymeal.assistant.data.db.entity

enum class DietType { Veg, Egg, NonVeg, Mixed }
```

- [ ] **Step 2: Create MealType.kt**

```kotlin
package com.familymeal.assistant.data.db.entity

enum class MealType { Breakfast, Lunch, Dinner, Tiffin, Snack }
```

- [ ] **Step 3: Create FeedbackType.kt**

```kotlin
package com.familymeal.assistant.data.db.entity

enum class FeedbackType { MakeAgain, GoodForTiffin, KidsLiked, TooMuchWork, NotAHit }
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/familymeal/assistant/data/db/entity/
git commit -m "feat: add DietType, MealType, FeedbackType enums"
```

---

### Task 2: Room entities

**Files:**
- Create: `data/db/entity/Member.kt`
- Create: `data/db/entity/MealEntry.kt`
- Create: `data/db/entity/MealMemberCrossRef.kt`
- Create: `data/db/entity/CatalogMeal.kt`
- Create: `data/db/entity/FeedbackSignal.kt`
- Create: `data/db/entity/RankingWeight.kt`
- Create: `data/db/entity/MemberMealScore.kt`
- Create: `data/db/converters/Converters.kt`

- [ ] **Step 1: Create Converters.kt** (needed by Room for enum storage)

```kotlin
package com.familymeal.assistant.data.db.converters

import androidx.room.TypeConverter
import com.familymeal.assistant.data.db.entity.DietType
import com.familymeal.assistant.data.db.entity.FeedbackType
import com.familymeal.assistant.data.db.entity.MealType

class Converters {
    @TypeConverter fun fromDietType(value: DietType): String = value.name
    @TypeConverter fun toDietType(value: String): DietType = DietType.valueOf(value)

    @TypeConverter fun fromMealType(value: MealType): String = value.name
    @TypeConverter fun toMealType(value: String): MealType = MealType.valueOf(value)

    @TypeConverter fun fromFeedbackType(value: FeedbackType): String = value.name
    @TypeConverter fun toFeedbackType(value: String): FeedbackType = FeedbackType.valueOf(value)
}
```

- [ ] **Step 2: Create Member.kt**

```kotlin
package com.familymeal.assistant.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "members", indices = [Index("isActive")])
data class Member(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val dietType: DietType,
    val birthYear: Int? = null,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
```

- [ ] **Step 3: Create CatalogMeal.kt** (before MealEntry — MealEntry has FK to it)

```kotlin
package com.familymeal.assistant.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "catalog_meals")
data class CatalogMeal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val cuisine: String,
    val dietType: DietType,
    val mealTypes: String,       // comma-separated MealType names e.g. "Lunch,Dinner"
    val tags: String? = null,    // comma-separated e.g. "quick,festive"
    val isUserAdded: Boolean = false
)
```

- [ ] **Step 4: Create MealEntry.kt**

```kotlin
package com.familymeal.assistant.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "meal_entries",
    foreignKeys = [
        ForeignKey(
            entity = CatalogMeal::class,
            parentColumns = ["id"],
            childColumns = ["catalogMealId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["mealType", "cookedAt"]),
        Index(value = ["cookedAt"]),
        Index(value = ["catalogMealId"])
    ]
)
data class MealEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val photoUri: String? = null,
    val mealType: MealType,
    val cookedAt: Long = System.currentTimeMillis(),
    val catalogMealId: Long? = null,
    val aiSuggestedName: String? = null,
    val classificationPending: Boolean = false,
    val notes: String? = null
)
```

- [ ] **Step 5: Create MealMemberCrossRef.kt**

```kotlin
package com.familymeal.assistant.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "meal_member_cross_refs",
    primaryKeys = ["mealEntryId", "memberId"],
    foreignKeys = [
        ForeignKey(
            entity = MealEntry::class,
            parentColumns = ["id"],
            childColumns = ["mealEntryId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Member::class,
            parentColumns = ["id"],
            childColumns = ["memberId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("memberId")]
)
data class MealMemberCrossRef(
    val mealEntryId: Long,
    val memberId: Long
)
```

- [ ] **Step 6: Create FeedbackSignal.kt**

```kotlin
package com.familymeal.assistant.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "feedback_signals",
    foreignKeys = [
        ForeignKey(
            entity = MealEntry::class,
            parentColumns = ["id"],
            childColumns = ["mealEntryId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("mealEntryId"), Index("memberId")]
)
data class FeedbackSignal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mealEntryId: Long,
    val memberId: Long? = null,
    val signalType: FeedbackType,
    val createdAt: Long = System.currentTimeMillis()
)
```

- [ ] **Step 7: Create RankingWeight.kt**

```kotlin
package com.familymeal.assistant.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ranking_weights")
data class RankingWeight(
    @PrimaryKey val signalName: String,
    val value: Float,
    val defaultValue: Float,
    val lastNudgedAt: Long = 0L
)
```

- [ ] **Step 8: Create MemberMealScore.kt**

```kotlin
package com.familymeal.assistant.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    tableName = "member_meal_scores",
    primaryKeys = ["memberId", "catalogMealId"],
    foreignKeys = [
        ForeignKey(
            entity = Member::class,
            parentColumns = ["id"],
            childColumns = ["memberId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = CatalogMeal::class,
            parentColumns = ["id"],
            childColumns = ["catalogMealId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class MemberMealScore(
    val memberId: Long,
    val catalogMealId: Long,
    val positiveSignals: Int = 0,
    val negativeSignals: Int = 0,
    val timesCooked: Int = 0,
    val lastCookedAt: Long? = null
)
```

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/familymeal/assistant/data/
git commit -m "feat: add all Room entities and type converters"
```

---

### Task 3: Room DAOs

**Files:**
- Create: `data/db/dao/MemberDao.kt`
- Create: `data/db/dao/MealEntryDao.kt`
- Create: `data/db/dao/CatalogMealDao.kt`
- Create: `data/db/dao/FeedbackDao.kt`
- Create: `data/db/dao/RankingWeightDao.kt`

- [ ] **Step 1: Create MemberDao.kt**

```kotlin
package com.familymeal.assistant.data.db.dao

import androidx.room.*
import com.familymeal.assistant.data.db.entity.Member
import kotlinx.coroutines.flow.Flow

@Dao
interface MemberDao {
    @Query("SELECT * FROM members WHERE isActive = 1 ORDER BY createdAt ASC")
    fun observeActiveMembers(): Flow<List<Member>>

    @Query("SELECT * FROM members WHERE isActive = 1 ORDER BY createdAt ASC")
    suspend fun getActiveMembers(): List<Member>

    @Query("SELECT * FROM members WHERE id = :id")
    suspend fun getMemberById(id: Long): Member?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMember(member: Member): Long

    @Update
    suspend fun updateMember(member: Member)

    // Deactivate — never hard delete
    @Query("UPDATE members SET isActive = 0 WHERE id = :id")
    suspend fun deactivateMember(id: Long)
}
```

- [ ] **Step 2: Create MealEntryDao.kt**

```kotlin
package com.familymeal.assistant.data.db.dao

import androidx.room.*
import com.familymeal.assistant.data.db.entity.MealEntry
import com.familymeal.assistant.data.db.entity.MealMemberCrossRef
import com.familymeal.assistant.data.db.entity.MealType
import kotlinx.coroutines.flow.Flow

@Dao
interface MealEntryDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMealEntry(entry: MealEntry): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCrossRefs(refs: List<MealMemberCrossRef>)

    @Update
    suspend fun updateMealEntry(entry: MealEntry)

    @Query("SELECT * FROM meal_entries ORDER BY cookedAt DESC")
    fun observeAllMeals(): Flow<List<MealEntry>>

    @Query("SELECT * FROM meal_entries WHERE mealType = :type ORDER BY cookedAt DESC")
    fun observeMealsByType(type: MealType): Flow<List<MealEntry>>

    @Query("""
        SELECT me.* FROM meal_entries me
        INNER JOIN meal_member_cross_refs mmcr ON me.id = mmcr.mealEntryId
        WHERE mmcr.memberId = :memberId
        ORDER BY me.cookedAt DESC
    """)
    fun observeMealsByMember(memberId: Long): Flow<List<MealEntry>>

    @Query("SELECT * FROM meal_entries WHERE catalogMealId = :catalogMealId ORDER BY cookedAt DESC LIMIT 1")
    suspend fun getLastCookedForCatalogMeal(catalogMealId: Long): MealEntry?

    @Query("SELECT memberId FROM meal_member_cross_refs WHERE mealEntryId = :mealEntryId")
    suspend fun getMemberIdsForMeal(mealEntryId: Long): List<Long>

    @Query("""
        UPDATE meal_entries SET classificationPending = 0
        WHERE classificationPending = 1 AND cookedAt < :cutoffMillis
    """)
    suspend fun reconcilePendingClassifications(cutoffMillis: Long)
}
```

- [ ] **Step 3: Create CatalogMealDao.kt**

```kotlin
package com.familymeal.assistant.data.db.dao

import androidx.room.*
import com.familymeal.assistant.data.db.entity.CatalogMeal
import com.familymeal.assistant.data.db.entity.DietType

@Dao
interface CatalogMealDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(meals: List<CatalogMeal>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(meal: CatalogMeal): Long

    @Query("SELECT * FROM catalog_meals ORDER BY name ASC")
    suspend fun getAllMeals(): List<CatalogMeal>

    @Query("SELECT * FROM catalog_meals WHERE dietType IN (:allowedDietTypes) ORDER BY name ASC")
    suspend fun getMealsByDietTypes(allowedDietTypes: List<String>): List<CatalogMeal>

    @Query("SELECT COUNT(*) FROM catalog_meals")
    suspend fun count(): Int

    @Query("SELECT * FROM catalog_meals WHERE id = :id")
    suspend fun getById(id: Long): CatalogMeal?
}
```

- [ ] **Step 4: Create FeedbackDao.kt**

```kotlin
package com.familymeal.assistant.data.db.dao

import androidx.room.*
import com.familymeal.assistant.data.db.entity.FeedbackSignal
import com.familymeal.assistant.data.db.entity.MemberMealScore

@Dao
interface FeedbackDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertFeedback(signal: FeedbackSignal): Long

    @Query("SELECT * FROM feedback_signals WHERE mealEntryId = :mealEntryId")
    suspend fun getFeedbackForMeal(mealEntryId: Long): List<FeedbackSignal>

    // MemberMealScore upsert
    @Query("SELECT * FROM member_meal_scores WHERE memberId = :memberId AND catalogMealId = :catalogMealId")
    suspend fun getMemberMealScore(memberId: Long, catalogMealId: Long): MemberMealScore?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMemberMealScore(score: MemberMealScore)

    @Query("SELECT * FROM member_meal_scores WHERE memberId = :memberId")
    suspend fun getScoresForMember(memberId: Long): List<MemberMealScore>

    @Query("SELECT * FROM member_meal_scores WHERE catalogMealId = :catalogMealId")
    suspend fun getScoresForCatalogMeal(catalogMealId: Long): List<MemberMealScore>
}
```

- [ ] **Step 5: Create RankingWeightDao.kt**

```kotlin
package com.familymeal.assistant.data.db.dao

import androidx.room.*
import com.familymeal.assistant.data.db.entity.RankingWeight
import kotlinx.coroutines.flow.Flow

@Dao
interface RankingWeightDao {
    @Query("SELECT * FROM ranking_weights ORDER BY signalName ASC")
    fun observeAllWeights(): Flow<List<RankingWeight>>

    @Query("SELECT * FROM ranking_weights ORDER BY signalName ASC")
    suspend fun getAllWeights(): List<RankingWeight>

    @Query("SELECT * FROM ranking_weights WHERE signalName = :name")
    suspend fun getWeight(name: String): RankingWeight?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(weights: List<RankingWeight>)

    @Update
    suspend fun update(weight: RankingWeight)

    @Query("SELECT COUNT(*) FROM ranking_weights")
    suspend fun count(): Int
}
```

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/familymeal/assistant/data/db/dao/
git commit -m "feat: add Room DAOs for all entities"
```

---

### Task 4: Room Database class

**Files:**
- Create: `data/db/AppDatabase.kt`

- [ ] **Step 1: Create AppDatabase.kt**

```kotlin
package com.familymeal.assistant.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.familymeal.assistant.data.db.converters.Converters
import com.familymeal.assistant.data.db.dao.*
import com.familymeal.assistant.data.db.entity.*

@Database(
    entities = [
        Member::class,
        MealEntry::class,
        MealMemberCrossRef::class,
        CatalogMeal::class,
        FeedbackSignal::class,
        RankingWeight::class,
        MemberMealScore::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun memberDao(): MemberDao
    abstract fun mealEntryDao(): MealEntryDao
    abstract fun catalogMealDao(): CatalogMealDao
    abstract fun feedbackDao(): FeedbackDao
    abstract fun rankingWeightDao(): RankingWeightDao
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/familymeal/assistant/data/db/AppDatabase.kt
git commit -m "feat: add AppDatabase with all 7 entities"
```

---

### Task 5: Hilt Database + Repository modules

**Files:**
- Create: `di/DatabaseModule.kt`
- Create: `di/RepositoryModule.kt`

- [ ] **Step 1: Create DatabaseModule.kt**

```kotlin
package com.familymeal.assistant.di

import android.content.Context
import androidx.room.Room
import com.familymeal.assistant.data.db.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "family_meal.db")
            .build()

    @Provides fun provideMemberDao(db: AppDatabase) = db.memberDao()
    @Provides fun provideMealEntryDao(db: AppDatabase) = db.mealEntryDao()
    @Provides fun provideCatalogMealDao(db: AppDatabase) = db.catalogMealDao()
    @Provides fun provideFeedbackDao(db: AppDatabase) = db.feedbackDao()
    @Provides fun provideRankingWeightDao(db: AppDatabase) = db.rankingWeightDao()
}
```

- [ ] **Step 2: Create RepositoryModule.kt** (bindings added per phase as impls are created)

```kotlin
package com.familymeal.assistant.di

import com.familymeal.assistant.data.repository.*
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds @Singleton abstract fun bindMealRepository(impl: MealRepositoryImpl): MealRepository
    @Binds @Singleton abstract fun bindMemberRepository(impl: MemberRepositoryImpl): MemberRepository
    @Binds @Singleton abstract fun bindFeedbackRepository(impl: FeedbackRepositoryImpl): FeedbackRepository
    @Binds @Singleton abstract fun bindCatalogRepository(impl: CatalogRepositoryImpl): CatalogRepository
    @Binds @Singleton abstract fun bindWeightRepository(impl: WeightRepositoryImpl): WeightRepository
    @Binds @Singleton abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository
}
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/familymeal/assistant/di/
git commit -m "feat: add Hilt DatabaseModule and RepositoryModule scaffolds"
```

---

### Task 6: Asset files — `catalog.json` and `ranking_config.json`

**Files:**
- Create: `app/src/main/assets/catalog.json`
- Create: `app/src/main/assets/ranking_config.json`

- [ ] **Step 1: Create `ranking_config.json`**

```json
[
  { "signalName": "recency",     "defaultValue": 0.40 },
  { "signalName": "makeAgain",   "defaultValue": 0.30 },
  { "signalName": "notAHit",     "defaultValue": 0.25 },
  { "signalName": "tooMuchWork", "defaultValue": 0.20 },
  { "signalName": "tiffin",      "defaultValue": 0.15 },
  { "signalName": "memberMatch", "defaultValue": 0.20 }
]
```

- [ ] **Step 2: Create `catalog.json`** (50 meals minimum, pan-world, all diet types)

The JSON structure for each meal:
```json
{
  "name": "string",
  "cuisine": "string",
  "dietType": "Veg|Egg|NonVeg|Mixed",
  "mealTypes": "Breakfast|Lunch|Dinner|Tiffin|Snack (comma-separated)",
  "tags": "optional,comma,separated"
}
```

Create the file with at least 50 entries covering:
- Indian: Dal Makhani (Veg, Lunch/Dinner), Paneer Butter Masala (Veg, Lunch/Dinner), Chicken Biryani (NonVeg, Lunch/Dinner), Masala Dosa (Veg, Breakfast/Tiffin), Poha (Veg, Breakfast/Snack), Upma (Veg, Breakfast), Rajma Chawal (Veg, Lunch/Dinner), Chole Bhature (Veg, Lunch), Palak Paneer (Veg, Lunch/Dinner), Egg Curry (Egg, Lunch/Dinner), Keema Pav (NonVeg, Lunch/Dinner), Idli Sambar (Veg, Breakfast/Tiffin), Vada Pav (Veg, Snack/Tiffin), Pav Bhaji (Veg, Lunch/Snack), Mutton Curry (NonVeg, Lunch/Dinner), Fish Curry (NonVeg, Lunch/Dinner), Aloo Paratha (Veg, Breakfast/Tiffin), Methi Thepla (Veg, Breakfast/Tiffin/Snack), Khichdi (Veg, Lunch/Dinner), Kadhi Chawal (Veg, Lunch/Dinner)
- Italian: Pasta Arrabbiata (Veg), Spaghetti Bolognese (NonVeg), Margherita Pizza (Veg), Pasta Carbonara (Egg), Risotto (Veg/Mixed)
- Mexican: Chicken Tacos (NonVeg), Bean Burritos (Veg), Guacamole (Veg, Snack)
- Japanese: Chicken Ramen (NonVeg), Vegetable Sushi (Veg), Miso Soup (Veg, Breakfast/Snack)
- Chinese: Fried Rice (Mixed), Hakka Noodles (Veg/NonVeg), Kung Pao Chicken (NonVeg), Dim Sum (Mixed)
- Mediterranean: Hummus (Veg, Snack), Falafel (Veg), Greek Salad (Veg), Shakshuka (Egg, Breakfast/Lunch)
- American: Grilled Chicken (NonVeg), Mac and Cheese (Veg), BLT Sandwich (NonVeg, Breakfast/Snack), Pancakes (Egg, Breakfast), Omelette (Egg, Breakfast)
- Other: Avocado Toast (Veg, Breakfast), Quinoa Bowl (Veg, Lunch/Dinner), Thai Green Curry (NonVeg/Veg), Korean Bibimbap (Mixed)

Full JSON sample (first 5 entries shown — write all 50+):
```json
[
  {
    "name": "Dal Makhani",
    "cuisine": "Indian",
    "dietType": "Veg",
    "mealTypes": "Lunch,Dinner",
    "tags": "comfort,slow-cook"
  },
  {
    "name": "Paneer Butter Masala",
    "cuisine": "Indian",
    "dietType": "Veg",
    "mealTypes": "Lunch,Dinner",
    "tags": "rich,crowd-pleaser"
  },
  {
    "name": "Chicken Biryani",
    "cuisine": "Indian",
    "dietType": "NonVeg",
    "mealTypes": "Lunch,Dinner",
    "tags": "festive,one-pot"
  },
  {
    "name": "Masala Dosa",
    "cuisine": "Indian",
    "dietType": "Veg",
    "mealTypes": "Breakfast,Tiffin",
    "tags": "quick,south-indian"
  },
  {
    "name": "Pasta Arrabbiata",
    "cuisine": "Italian",
    "dietType": "Veg",
    "mealTypes": "Lunch,Dinner",
    "tags": "quick,spicy"
  }
]
```

Write all 50+ entries to the file following this structure.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/assets/
git commit -m "feat: add catalog.json (50+ meals) and ranking_config.json"
```

---

### Task 7: Repository interfaces and implementations

**Files:**
- Create: `data/repository/MemberRepository.kt` + `MemberRepositoryImpl.kt`
- Create: `data/repository/MealRepository.kt` + `MealRepositoryImpl.kt`
- Create: `data/repository/CatalogRepository.kt` + `CatalogRepositoryImpl.kt`
- Create: `data/repository/WeightRepository.kt` + `WeightRepositoryImpl.kt`
- Create: `data/repository/FeedbackRepository.kt` + `FeedbackRepositoryImpl.kt`
- Create: `data/repository/SettingsRepository.kt` + `SettingsRepositoryImpl.kt`

- [ ] **Step 1: Create MemberRepository.kt**

```kotlin
package com.familymeal.assistant.data.repository

import com.familymeal.assistant.data.db.entity.Member
import kotlinx.coroutines.flow.Flow

interface MemberRepository {
    fun observeActiveMembers(): Flow<List<Member>>
    suspend fun getActiveMembers(): List<Member>
    suspend fun addMember(member: Member): Long
    suspend fun updateMember(member: Member)
    suspend fun deactivateMember(id: Long)
}
```

- [ ] **Step 2: Create MemberRepositoryImpl.kt**

```kotlin
package com.familymeal.assistant.data.repository

import com.familymeal.assistant.data.db.dao.MemberDao
import com.familymeal.assistant.data.db.entity.Member
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class MemberRepositoryImpl @Inject constructor(
    private val memberDao: MemberDao
) : MemberRepository {
    override fun observeActiveMembers() = memberDao.observeActiveMembers()
    override suspend fun getActiveMembers() = memberDao.getActiveMembers()
    override suspend fun addMember(member: Member) = memberDao.insertMember(member)
    override suspend fun updateMember(member: Member) = memberDao.updateMember(member)
    override suspend fun deactivateMember(id: Long) = memberDao.deactivateMember(id)
}
```

- [ ] **Step 3: Create MealRepository.kt**

```kotlin
package com.familymeal.assistant.data.repository

import com.familymeal.assistant.data.db.entity.MealEntry
import com.familymeal.assistant.data.db.entity.MealMemberCrossRef
import com.familymeal.assistant.data.db.entity.MealType
import kotlinx.coroutines.flow.Flow

interface MealRepository {
    suspend fun saveMeal(entry: MealEntry, memberIds: List<Long>): Long
    suspend fun updateMeal(entry: MealEntry)
    fun observeAllMeals(): Flow<List<MealEntry>>
    fun observeMealsByType(type: MealType): Flow<List<MealEntry>>
    fun observeMealsByMember(memberId: Long): Flow<List<MealEntry>>
    suspend fun getLastCookedForCatalogMeal(catalogMealId: Long): MealEntry?
    suspend fun getMemberIdsForMeal(mealEntryId: Long): List<Long>
    suspend fun reconcilePendingClassifications()
}
```

- [ ] **Step 4: Create MealRepositoryImpl.kt**

```kotlin
package com.familymeal.assistant.data.repository

import com.familymeal.assistant.data.db.dao.MealEntryDao
import com.familymeal.assistant.data.db.entity.MealEntry
import com.familymeal.assistant.data.db.entity.MealMemberCrossRef
import com.familymeal.assistant.data.db.entity.MealType
import javax.inject.Inject

class MealRepositoryImpl @Inject constructor(
    private val mealEntryDao: MealEntryDao
) : MealRepository {

    override suspend fun saveMeal(entry: MealEntry, memberIds: List<Long>): Long {
        val id = mealEntryDao.insertMealEntry(entry)
        val crossRefs = memberIds.map { MealMemberCrossRef(id, it) }
        if (crossRefs.isNotEmpty()) mealEntryDao.insertCrossRefs(crossRefs)
        return id
    }

    override suspend fun updateMeal(entry: MealEntry) = mealEntryDao.updateMealEntry(entry)
    override fun observeAllMeals() = mealEntryDao.observeAllMeals()
    override fun observeMealsByType(type: MealType) = mealEntryDao.observeMealsByType(type)
    override fun observeMealsByMember(memberId: Long) = mealEntryDao.observeMealsByMember(memberId)
    override suspend fun getLastCookedForCatalogMeal(id: Long) = mealEntryDao.getLastCookedForCatalogMeal(id)
    override suspend fun getMemberIdsForMeal(id: Long) = mealEntryDao.getMemberIdsForMeal(id)

    override suspend fun reconcilePendingClassifications() {
        val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        mealEntryDao.reconcilePendingClassifications(cutoff)
    }
}
```

- [ ] **Step 5: Create CatalogRepository.kt**

```kotlin
package com.familymeal.assistant.data.repository

import com.familymeal.assistant.data.db.entity.CatalogMeal
import com.familymeal.assistant.data.db.entity.DietType

interface CatalogRepository {
    suspend fun seedIfEmpty()
    suspend fun getAllMeals(): List<CatalogMeal>
    suspend fun getMealsByDietTypes(allowed: List<DietType>): List<CatalogMeal>
    suspend fun addUserMeal(meal: CatalogMeal): Long
    suspend fun getById(id: Long): CatalogMeal?
}
```

- [ ] **Step 6: Create CatalogRepositoryImpl.kt**

```kotlin
package com.familymeal.assistant.data.repository

import android.content.Context
import com.familymeal.assistant.data.db.dao.CatalogMealDao
import com.familymeal.assistant.data.db.entity.CatalogMeal
import com.familymeal.assistant.data.db.entity.DietType
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class CatalogRepositoryImpl @Inject constructor(
    private val catalogMealDao: CatalogMealDao,
    @ApplicationContext private val context: Context,
    private val gson: Gson
) : CatalogRepository {

    override suspend fun seedIfEmpty() {
        if (catalogMealDao.count() > 0) return
        val json = context.assets.open("catalog.json").bufferedReader().readText()
        val type = object : TypeToken<List<CatalogMeal>>() {}.type
        val meals: List<CatalogMeal> = gson.fromJson(json, type)
        catalogMealDao.insertAll(meals)
    }

    override suspend fun getAllMeals() = catalogMealDao.getAllMeals()

    override suspend fun getMealsByDietTypes(allowed: List<DietType>) =
        catalogMealDao.getMealsByDietTypes(allowed.map { it.name })

    override suspend fun addUserMeal(meal: CatalogMeal) = catalogMealDao.insert(meal)
    override suspend fun getById(id: Long) = catalogMealDao.getById(id)
}
```

- [ ] **Step 7: Create WeightRepository.kt**

```kotlin
package com.familymeal.assistant.data.repository

import com.familymeal.assistant.data.db.entity.RankingWeight
import kotlinx.coroutines.flow.Flow

interface WeightRepository {
    suspend fun seedIfEmpty()
    fun observeAllWeights(): Flow<List<RankingWeight>>
    suspend fun getAllWeights(): List<RankingWeight>
    suspend fun updateWeight(weight: RankingWeight)
    suspend fun resetToDefault(signalName: String)
}
```

- [ ] **Step 8: Create WeightRepositoryImpl.kt**

```kotlin
package com.familymeal.assistant.data.repository

import android.content.Context
import com.familymeal.assistant.data.db.dao.RankingWeightDao
import com.familymeal.assistant.data.db.entity.RankingWeight
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

data class WeightConfig(val signalName: String, val defaultValue: Float)

class WeightRepositoryImpl @Inject constructor(
    private val dao: RankingWeightDao,
    @ApplicationContext private val context: Context,
    private val gson: Gson
) : WeightRepository {

    override suspend fun seedIfEmpty() {
        if (dao.count() > 0) return
        val json = context.assets.open("ranking_config.json").bufferedReader().readText()
        val type = object : TypeToken<List<WeightConfig>>() {}.type
        val configs: List<WeightConfig> = gson.fromJson(json, type)
        dao.insertAll(configs.map { RankingWeight(it.signalName, it.defaultValue, it.defaultValue) })
    }

    override fun observeAllWeights() = dao.observeAllWeights()
    override suspend fun getAllWeights() = dao.getAllWeights()
    override suspend fun updateWeight(weight: RankingWeight) = dao.update(weight)
    override suspend fun resetToDefault(signalName: String) {
        val w = dao.getWeight(signalName) ?: return
        dao.update(w.copy(value = w.defaultValue, lastNudgedAt = System.currentTimeMillis()))
    }
}
```

- [ ] **Step 9: Create FeedbackRepository.kt**

```kotlin
package com.familymeal.assistant.data.repository

import com.familymeal.assistant.data.db.entity.FeedbackSignal
import com.familymeal.assistant.data.db.entity.FeedbackType

interface FeedbackRepository {
    // Saves the feedback AND updates MemberMealScore in one transaction
    suspend fun saveFeedback(
        signal: FeedbackSignal,
        catalogMealId: Long?,
        mealMemberIds: List<Long>,
        childMemberIds: List<Long>  // members with non-null birthYear in the meal's cross-refs
    )
    suspend fun getFeedbackForMeal(mealEntryId: Long): List<FeedbackSignal>
}
```

- [ ] **Step 10: Create FeedbackRepositoryImpl.kt**

```kotlin
package com.familymeal.assistant.data.repository

import androidx.room.withTransaction
import com.familymeal.assistant.data.db.AppDatabase
import com.familymeal.assistant.data.db.dao.FeedbackDao
import com.familymeal.assistant.data.db.entity.FeedbackSignal
import com.familymeal.assistant.data.db.entity.FeedbackType
import com.familymeal.assistant.data.db.entity.MemberMealScore
import javax.inject.Inject

class FeedbackRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val feedbackDao: FeedbackDao
) : FeedbackRepository {

    override suspend fun saveFeedback(
        signal: FeedbackSignal,
        catalogMealId: Long?,
        mealMemberIds: List<Long>,
        childMemberIds: List<Long>
    ) {
        db.withTransaction {
            feedbackDao.insertFeedback(signal)

            if (catalogMealId == null) return@withTransaction

            // Determine which members to update
            val targetMemberIds: List<Long> = when {
                signal.memberId != null -> listOf(signal.memberId)
                signal.signalType == FeedbackType.KidsLiked -> {
                    childMemberIds.ifEmpty { mealMemberIds }
                }
                else -> mealMemberIds
            }

            val now = System.currentTimeMillis()
            for (memberId in targetMemberIds) {
                val existing = feedbackDao.getMemberMealScore(memberId, catalogMealId)
                    ?: MemberMealScore(memberId, catalogMealId)

                val updated = when (signal.signalType) {
                    FeedbackType.MakeAgain, FeedbackType.KidsLiked ->
                        existing.copy(positiveSignals = existing.positiveSignals + 1)
                    FeedbackType.NotAHit, FeedbackType.TooMuchWork ->
                        existing.copy(negativeSignals = existing.negativeSignals + 1)
                    FeedbackType.GoodForTiffin -> existing // no score change; handled by weight nudge
                }
                feedbackDao.upsertMemberMealScore(updated)
            }
        }
    }

    override suspend fun getFeedbackForMeal(mealEntryId: Long) =
        feedbackDao.getFeedbackForMeal(mealEntryId)
}
```

- [ ] **Step 11: Create SettingsRepository.kt**

```kotlin
package com.familymeal.assistant.data.repository

interface SettingsRepository {
    fun getGeminiApiKey(): String?
    fun setGeminiApiKey(key: String)
    fun clearGeminiApiKey()
    fun getExplorationRatio(): Float         // default 0.20
    fun setExplorationRatio(ratio: Float)
    fun isApiKeyBannerDismissed(): Boolean
    fun dismissApiKeyBanner()
    fun isOnboardingComplete(): Boolean
    fun markOnboardingComplete()
}
```

- [ ] **Step 12: Create SettingsRepositoryImpl.kt**

```kotlin
package com.familymeal.assistant.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class SettingsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : SettingsRepository {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val encryptedPrefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "secure_settings",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val prefs: SharedPreferences =
        context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    override fun getGeminiApiKey(): String? = encryptedPrefs.getString(KEY_GEMINI_API, null)
    override fun setGeminiApiKey(key: String) = encryptedPrefs.edit().putString(KEY_GEMINI_API, key).apply()
    override fun clearGeminiApiKey() = encryptedPrefs.edit().remove(KEY_GEMINI_API).apply()

    override fun getExplorationRatio(): Float = prefs.getFloat(KEY_EXPLORATION, 0.20f)
    override fun setExplorationRatio(ratio: Float) = prefs.edit().putFloat(KEY_EXPLORATION, ratio).apply()

    override fun isApiKeyBannerDismissed(): Boolean = prefs.getBoolean(KEY_BANNER_DISMISSED, false)
    override fun dismissApiKeyBanner() = prefs.edit().putBoolean(KEY_BANNER_DISMISSED, true).apply()

    override fun isOnboardingComplete(): Boolean = prefs.getBoolean(KEY_ONBOARDING_DONE, false)
    override fun markOnboardingComplete() = prefs.edit().putBoolean(KEY_ONBOARDING_DONE, true).apply()

    companion object {
        private const val KEY_GEMINI_API = "gemini_api_key"
        private const val KEY_EXPLORATION = "exploration_ratio"
        private const val KEY_BANNER_DISMISSED = "api_key_banner_dismissed"
        private const val KEY_ONBOARDING_DONE = "onboarding_complete"
    }
}
```

- [ ] **Step 13: Add Gson to Hilt**

In `di/DatabaseModule.kt` or a new `AppModule.kt`, provide Gson:

```kotlin
// Add to DatabaseModule or new AppModule
@Provides
@Singleton
fun provideGson(): Gson = Gson()
```

- [ ] **Step 14: Commit all repository code**

```bash
git add app/src/main/java/com/familymeal/assistant/data/repository/ \
        app/src/main/java/com/familymeal/assistant/di/
git commit -m "feat: add all repository interfaces and implementations"
```

---

### Task 8: Repository tests

**Files:**
- Create: `app/src/test/java/com/familymeal/assistant/data/FeedbackRepositoryTest.kt`
- Create: `app/src/test/java/com/familymeal/assistant/data/MealRepositoryTest.kt`
- Create: `app/src/test/java/com/familymeal/assistant/data/CatalogRepositoryTest.kt`

- [ ] **Step 1: Write failing tests for FeedbackRepository**

```kotlin
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

@RunWith(RobolectricTestRunner::class)
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
        // Insert catalog meal + member + meal entry + cross refs
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
        // Verify that signal + score are both present or both absent
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
```

- [ ] **Step 2: Run failing tests** (they fail because DB / repo not wired yet)

```bash
./gradlew :app:test --tests "*.FeedbackRepositoryTest"
```

- [ ] **Step 3: Verify all tests pass** (after task 7 implementations are in place)

```bash
./gradlew :app:test --tests "*.FeedbackRepositoryTest"
```

Expected: All 3 tests PASS.

- [ ] **Step 4: Commit**

```bash
git add app/src/test/java/com/familymeal/assistant/data/
git commit -m "test: add FeedbackRepository integration tests"
```

---

### Task 9: Build verification

- [ ] **Step 1: Full build check**

```bash
./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL — Hilt and Room KSP annotation processing complete, no unresolved references.

- [ ] **Step 2: Run all tests**

```bash
./gradlew :app:test
```

Expected: All tests PASS.

- [ ] **Step 3: Commit if any fixes needed**

```bash
git add -A
git commit -m "fix: resolve any build issues after data layer assembly"
```
