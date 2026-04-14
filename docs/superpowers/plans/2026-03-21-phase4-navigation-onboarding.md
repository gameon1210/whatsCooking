# Phase 4: Navigation Shell + Onboarding Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wire up the Compose Navigation host with 3 bottom-nav tabs (Home, Add Meal, History) plus Settings (top-bar menu), implement the Onboarding flow (household name + member setup), and create the shared `UiState` sealed class used by all ViewModels.

**Architecture:** Single `NavHost` in `MainActivity`. Bottom nav controlled by `BottomNavBar` composable. Onboarding shows instead of the main shell if `MemberRepository.getActiveMembers()` returns empty. `OnboardingViewModel` is Hilt-injected, drives 2-step flow (name → add members). Catalog and weight seeding happen in a background coroutine on app start.

**Tech Stack:** Navigation Compose 2.8.9, Hilt Navigation Compose, Compose Material3, Coroutines, JUnit 4 + Turbine

**Prerequisite:** Phases 1–3 complete.

---

## File Map

```
app/src/main/java/com/familymeal/assistant/
  ui/
    common/
      UiState.kt
    navigation/
      Screen.kt
      AppNavigation.kt
      BottomNavBar.kt
    onboarding/
      OnboardingScreen.kt
      OnboardingViewModel.kt
  MainActivity.kt              (update to host NavHost)

app/src/test/java/com/familymeal/assistant/
  ui/
    OnboardingViewModelTest.kt
```

---

### Task 1: `UiState` sealed class

**Files:**
- Create: `ui/common/UiState.kt`

- [ ] **Step 1: Create UiState.kt**

```kotlin
package com.familymeal.assistant.ui.common

sealed class UiState<out T> {
    object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val message: String) : UiState<Nothing>()
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/familymeal/assistant/ui/common/UiState.kt
git commit -m "feat: add UiState sealed class"
```

---

### Task 2: Navigation destinations

**Files:**
- Create: `ui/navigation/Screen.kt`

- [ ] **Step 1: Create Screen.kt**

```kotlin
package com.familymeal.assistant.ui.navigation

sealed class Screen(val route: String) {
    object Onboarding : Screen("onboarding")
    object Home : Screen("home")
    object AddMeal : Screen("add_meal")
    object History : Screen("history")
    object Settings : Screen("settings")
    object MemberProfiles : Screen("member_profiles")
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/familymeal/assistant/ui/navigation/Screen.kt
git commit -m "feat: add navigation Screen destinations"
```

---

### Task 3: `BottomNavBar` composable

**Files:**
- Create: `ui/navigation/BottomNavBar.kt`

- [ ] **Step 1: Create BottomNavBar.kt**

```kotlin
package com.familymeal.assistant.ui.navigation

import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState

data class BottomNavItem(val screen: Screen, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@Composable
fun BottomNavBar(navController: NavController) {
    val items = listOf(
        BottomNavItem(Screen.Home, "Home", Icons.Default.Home),
        BottomNavItem(Screen.AddMeal, "Add Meal", Icons.Default.AddAPhoto),
        BottomNavItem(Screen.History, "History", Icons.Default.History)
    )
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    NavigationBar {
        items.forEach { item ->
            NavigationBarItem(
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label) },
                selected = currentRoute == item.screen.route,
                onClick = {
                    if (currentRoute != item.screen.route) {
                        navController.navigate(item.screen.route) {
                            popUpTo(Screen.Home.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
            )
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/familymeal/assistant/ui/navigation/BottomNavBar.kt
git commit -m "feat: add BottomNavBar composable"
```

---

### Task 4: `AppNavigation` + `MainActivity` wiring

**Files:**
- Create: `ui/navigation/AppNavigation.kt`
- Modify: `MainActivity.kt`

- [ ] **Step 1: Create AppNavigation.kt**

Placeholder screens for Home, AddMeal, History, Settings, MemberProfiles are filled in with `Box(Modifier.fillMaxSize())` stubs — they are replaced in Phases 5–8.

```kotlin
package com.familymeal.assistant.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.familymeal.assistant.ui.onboarding.OnboardingScreen

@Composable
fun AppNavigation(startDestination: String) {
    val navController = rememberNavController()
    val showBottomNav = remember(navController) {
        derivedStateOf {
            val route = navController.currentBackStackEntry?.destination?.route
            route in listOf(Screen.Home.route, Screen.AddMeal.route, Screen.History.route)
        }
    }

    Scaffold(
        bottomBar = {
            if (showBottomNav.value) {
                BottomNavBar(navController)
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(padding)
        ) {
            composable(Screen.Onboarding.route) {
                OnboardingScreen(onComplete = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                })
            }
            composable(Screen.Home.route) {
                Box(modifier = Modifier.fillMaxSize()) // Replaced in Phase 5
            }
            composable(Screen.AddMeal.route) {
                Box(modifier = Modifier.fillMaxSize()) // Replaced in Phase 6
            }
            composable(Screen.History.route) {
                Box(modifier = Modifier.fillMaxSize()) // Replaced in Phase 7
            }
            composable(Screen.Settings.route) {
                Box(modifier = Modifier.fillMaxSize()) // Replaced in Phase 8
            }
            composable(Screen.MemberProfiles.route) {
                Box(modifier = Modifier.fillMaxSize()) // Replaced in Phase 8
            }
        }
    }
}
```

- [ ] **Step 2: Update MainActivity.kt to host AppNavigation and seed data**

```kotlin
package com.familymeal.assistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.familymeal.assistant.data.repository.CatalogRepository
import com.familymeal.assistant.data.repository.MealRepository
import com.familymeal.assistant.data.repository.MemberRepository
import com.familymeal.assistant.data.repository.WeightRepository
import com.familymeal.assistant.ui.navigation.AppNavigation
import com.familymeal.assistant.ui.navigation.Screen
import com.familymeal.assistant.ui.theme.FamilyMealAssistantTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var memberRepository: MemberRepository
    @Inject lateinit var catalogRepository: CatalogRepository
    @Inject lateinit var weightRepository: WeightRepository
    @Inject lateinit var mealRepository: MealRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Seed catalog and weights on startup (no-ops after first launch)
        lifecycleScope.launch {
            catalogRepository.seedIfEmpty()
            weightRepository.seedIfEmpty()
            mealRepository.reconcilePendingClassifications()
        }

        setContent {
            FamilyMealAssistantTheme {
                // Determine start destination synchronously isn't possible here;
                // OnboardingViewModel checks member count and redirects if needed.
                AppNavigation(startDestination = Screen.Home.route)
            }
        }
    }
}
```

**Note:** The actual start-destination decision (Home vs Onboarding) is handled by a `SplashViewModel` or by checking inside `HomeScreen` — the simplest MVP approach is to always start at `Screen.Home.route` and have `HomeScreen` redirect to Onboarding if no members exist. Implement this redirect in Phase 5.

- [ ] **Step 3: Build and verify navigation renders**

```bash
./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL. App shows empty shell with bottom nav.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/familymeal/assistant/ui/navigation/ \
        app/src/main/java/com/familymeal/assistant/MainActivity.kt
git commit -m "feat: wire up NavHost, BottomNavBar, and app shell"
```

---

### Task 5: `OnboardingViewModel` — write tests first

**Files:**
- Create: `ui/onboarding/OnboardingViewModel.kt`
- Create: `app/src/test/.../ui/OnboardingViewModelTest.kt`

- [ ] **Step 1: Write failing tests**

```kotlin
package com.familymeal.assistant.ui

import app.cash.turbine.test
import com.familymeal.assistant.data.db.entity.DietType
import com.familymeal.assistant.data.db.entity.Member
import com.familymeal.assistant.data.repository.MemberRepository
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
    private lateinit var vm: OnboardingViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        memberRepo = mockk(relaxed = true)
        vm = OnboardingViewModel(memberRepo)
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
```

- [ ] **Step 2: Run — expect FAIL**

```bash
./gradlew :app:test --tests "*.OnboardingViewModelTest"
```

- [ ] **Step 3: Implement OnboardingViewModel.kt**

```kotlin
package com.familymeal.assistant.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familymeal.assistant.data.db.entity.DietType
import com.familymeal.assistant.data.db.entity.Member
import com.familymeal.assistant.data.repository.MemberRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PendingMember(val name: String, val dietType: DietType, val birthYear: Int?)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val memberRepository: MemberRepository
) : ViewModel() {

    private val _pendingMembers = MutableStateFlow<List<PendingMember>>(emptyList())
    val pendingMembers: StateFlow<List<PendingMember>> = _pendingMembers

    val canProceed: StateFlow<Boolean> = _pendingMembers
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun addMember(name: String, dietType: DietType, birthYear: Int?) {
        _pendingMembers.value = _pendingMembers.value + PendingMember(name, dietType, birthYear)
    }

    fun removeMember(index: Int) {
        _pendingMembers.value = _pendingMembers.value.toMutableList().also { it.removeAt(index) }
    }

    fun completeOnboarding(onDone: () -> Unit = {}) {
        viewModelScope.launch {
            _pendingMembers.value.forEach { pending ->
                memberRepository.addMember(
                    Member(name = pending.name, dietType = pending.dietType, birthYear = pending.birthYear)
                )
            }
            onDone()
        }
    }
}
```

- [ ] **Step 4: Run — expect PASS**

```bash
./gradlew :app:test --tests "*.OnboardingViewModelTest"
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/familymeal/assistant/ui/onboarding/OnboardingViewModel.kt \
        app/src/test/java/com/familymeal/assistant/ui/OnboardingViewModelTest.kt
git commit -m "feat: implement OnboardingViewModel with TDD"
```

---

### Task 6: `OnboardingScreen` composable

**Files:**
- Create: `ui/onboarding/OnboardingScreen.kt`

- [ ] **Step 1: Create OnboardingScreen.kt**

```kotlin
package com.familymeal.assistant.ui.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.familymeal.assistant.data.db.entity.DietType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val members by viewModel.pendingMembers.collectAsState()
    val canProceed by viewModel.canProceed.collectAsState()

    var name by remember { mutableStateOf("") }
    var selectedDiet by remember { mutableStateOf(DietType.Veg) }
    var birthYearText by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Set Up Your Household") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Add household members", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth()
            )

            // Diet type selector
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DietType.values().forEach { diet ->
                    FilterChip(
                        selected = selectedDiet == diet,
                        onClick = { selectedDiet = diet },
                        label = { Text(diet.name) }
                    )
                }
            }

            OutlinedTextField(
                value = birthYearText,
                onValueChange = { birthYearText = it },
                label = { Text("Birth Year (optional)") },
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        viewModel.addMember(name.trim(), selectedDiet, birthYearText.toIntOrNull())
                        name = ""
                        birthYearText = ""
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Add Member")
            }

            Divider()

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(members) { index, member ->
                    ListItem(
                        headlineContent = { Text(member.name) },
                        supportingContent = { Text("${member.dietType.name}${member.birthYear?.let { " · Born $it" } ?: ""}") },
                        trailingContent = {
                            IconButton(onClick = { viewModel.removeMember(index) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove")
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = { viewModel.completeOnboarding(onComplete) },
                enabled = canProceed,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Get Started")
            }
        }
    }
}
```

- [ ] **Step 2: Build and verify**

```bash
./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Update AppNavigation to check for members and redirect to Onboarding if needed**

In `MainActivity.onCreate`, change `startDestination` determination:

```kotlin
// After seedIfEmpty() calls, check member count:
val members = memberRepository.getActiveMembers()
val start = if (members.isEmpty()) Screen.Onboarding.route else Screen.Home.route
```

Then pass `start` to `AppNavigation`. This requires making `setContent` a suspend call or using a `SplashScreen`. Simpler approach for MVP: default to `Screen.Onboarding.route` and redirect to Home from onboarding completion; use `DataStore` or `SharedPreferences` flag `onboarding_complete` to skip on subsequent launches.

Add `onboarding_complete` to `SettingsRepository`:
```kotlin
fun isOnboardingComplete(): Boolean
fun markOnboardingComplete()
```

In `MainActivity`:
```kotlin
val startDestination = if (settingsRepository.isOnboardingComplete()) {
    Screen.Home.route
} else {
    Screen.Onboarding.route
}
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/familymeal/assistant/ui/onboarding/OnboardingScreen.kt
git commit -m "feat: implement OnboardingScreen"
```

---

### Task 7: Full build and navigation smoke test

- [ ] **Step 1: Build debug APK**

```bash
./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Run all tests so far**

```bash
./gradlew :app:test
```

Expected: All tests PASS.

- [ ] **Step 3: Commit any fixes**

```bash
git add -A
git commit -m "fix: resolve navigation + onboarding integration issues"
```
