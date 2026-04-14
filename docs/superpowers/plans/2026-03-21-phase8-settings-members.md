# Phase 8: Settings + Member Profiles Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement `SettingsScreen` (ranking weight sliders with reset, exploration ratio slider, Gemini API key field, navigation to Member Profiles) and `MemberProfilesScreen` (add/edit/deactivate household members). Wire both into navigation. `SettingsViewModel` and `MemberProfilesViewModel` are Hilt-injected.

**Architecture:** `SettingsViewModel` observes `WeightRepository.observeAllWeights()` as a Flow and exposes it as `UiState`. Weight edits call `WeightRepository.updateWeight()` immediately (live update). Exploration ratio is read/written via `SettingsRepository`. API key stored in `EncryptedSharedPreferences` via `SettingsRepository`. `MemberProfilesViewModel` reuses `MemberRepository`.

**Tech Stack:** Jetpack Compose, Material3, Hilt, Coroutines + Flow, JUnit 4 + Turbine + MockK

**Prerequisite:** Phases 1–4 complete.

---

## File Map

```
app/src/main/java/com/familymeal/assistant/
  ui/
    settings/
      SettingsViewModel.kt
      SettingsScreen.kt
    members/
      MemberProfilesViewModel.kt
      MemberProfilesScreen.kt

app/src/test/java/com/familymeal/assistant/
  ui/
    SettingsViewModelTest.kt
    MemberProfilesViewModelTest.kt
```

---

### Task 1: `SettingsViewModel` — write tests first

**Files:**
- Create: `ui/settings/SettingsViewModel.kt`
- Create: `app/src/test/.../ui/SettingsViewModelTest.kt`

- [ ] **Step 1: Write failing tests**

```kotlin
package com.familymeal.assistant.ui

import app.cash.turbine.test
import com.familymeal.assistant.data.db.entity.RankingWeight
import com.familymeal.assistant.data.repository.*
import com.familymeal.assistant.ui.common.UiState
import com.familymeal.assistant.ui.settings.SettingsViewModel
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SettingsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var weightRepo: WeightRepository
    private lateinit var settingsRepo: SettingsRepository
    private lateinit var vm: SettingsViewModel

    private val weights = listOf(
        RankingWeight("recency", 0.40f, 0.40f),
        RankingWeight("makeAgain", 0.30f, 0.30f)
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        weightRepo = mockk(relaxed = true)
        settingsRepo = mockk(relaxed = true)
        every { weightRepo.observeAllWeights() } returns flowOf(weights)
        every { settingsRepo.getExplorationRatio() } returns 0.20f
        every { settingsRepo.getGeminiApiKey() } returns null
        vm = SettingsViewModel(weightRepo, settingsRepo)
    }

    @After fun teardown() { Dispatchers.resetMain() }

    @Test
    fun `weights state loads from repository`() = runTest {
        vm.weights.test {
            val state = awaitItem()
            assertTrue(state is UiState.Success)
            assertEquals(2, (state as UiState.Success).data.size)
        }
    }

    @Test
    fun `updateWeight calls repository`() = runTest {
        val updated = weights[0].copy(value = 0.50f)
        vm.updateWeight(updated)
        coVerify { weightRepo.updateWeight(updated) }
    }

    @Test
    fun `resetWeight calls repository resetToDefault`() = runTest {
        vm.resetWeight("recency")
        coVerify { weightRepo.resetToDefault("recency") }
    }

    @Test
    fun `setExplorationRatio updates settings`() {
        vm.setExplorationRatio(0.25f)
        verify { settingsRepo.setExplorationRatio(0.25f) }
        assertEquals(0.25f, vm.explorationRatio.value, 0.001f)
    }

    @Test
    fun `explorationRatio clamped to 0_10 to 0_30`() {
        vm.setExplorationRatio(0.50f)
        assertEquals(0.30f, vm.explorationRatio.value, 0.001f)
        vm.setExplorationRatio(0.05f)
        assertEquals(0.10f, vm.explorationRatio.value, 0.001f)
    }

    @Test
    fun `saveApiKey calls settingsRepository`() {
        vm.saveApiKey("my-test-key")
        verify { settingsRepo.setGeminiApiKey("my-test-key") }
    }

    @Test
    fun `clearApiKey calls settingsRepository`() {
        vm.clearApiKey()
        verify { settingsRepo.clearGeminiApiKey() }
    }
}
```

- [ ] **Step 2: Run — expect FAIL**

```bash
./gradlew :app:test --tests "*.SettingsViewModelTest"
```

- [ ] **Step 3: Implement SettingsViewModel.kt**

```kotlin
package com.familymeal.assistant.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familymeal.assistant.data.db.entity.RankingWeight
import com.familymeal.assistant.data.repository.SettingsRepository
import com.familymeal.assistant.data.repository.WeightRepository
import com.familymeal.assistant.ui.common.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val weightRepository: WeightRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val weights: StateFlow<UiState<List<RankingWeight>>> = weightRepository.observeAllWeights()
        .map<List<RankingWeight>, UiState<List<RankingWeight>>> { UiState.Success(it) }
        .catch { emit(UiState.Error(it.message ?: "Failed to load weights")) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, UiState.Loading)

    private val _explorationRatio = MutableStateFlow(settingsRepository.getExplorationRatio())
    val explorationRatio: StateFlow<Float> = _explorationRatio

    val apiKey: StateFlow<String?> = MutableStateFlow(settingsRepository.getGeminiApiKey())

    fun updateWeight(weight: RankingWeight) {
        viewModelScope.launch { weightRepository.updateWeight(weight) }
    }

    fun resetWeight(signalName: String) {
        viewModelScope.launch { weightRepository.resetToDefault(signalName) }
    }

    fun setExplorationRatio(ratio: Float) {
        val clamped = ratio.coerceIn(0.10f, 0.30f)
        _explorationRatio.value = clamped
        settingsRepository.setExplorationRatio(clamped)
    }

    fun saveApiKey(key: String) {
        settingsRepository.setGeminiApiKey(key.trim())
    }

    fun clearApiKey() {
        settingsRepository.clearGeminiApiKey()
    }
}
```

- [ ] **Step 4: Run — expect PASS**

```bash
./gradlew :app:test --tests "*.SettingsViewModelTest"
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/familymeal/assistant/ui/settings/SettingsViewModel.kt \
        app/src/test/java/com/familymeal/assistant/ui/SettingsViewModelTest.kt
git commit -m "feat: implement SettingsViewModel with TDD"
```

---

### Task 2: `SettingsScreen` composable

**Files:**
- Create: `ui/settings/SettingsScreen.kt`
- Modify: `ui/navigation/AppNavigation.kt` (replace Settings stub)

- [ ] **Step 1: Create SettingsScreen.kt**

```kotlin
package com.familymeal.assistant.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.familymeal.assistant.data.db.entity.RankingWeight
import com.familymeal.assistant.ui.common.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToMembers: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val weightsState by viewModel.weights.collectAsState()
    val explorationRatio by viewModel.explorationRatio.collectAsState()
    var apiKeyText by remember { mutableStateOf("") }
    var showApiKey by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // --- Members section ---
            item {
                Text("Household", style = MaterialTheme.typography.titleMedium)
            }
            item {
                OutlinedButton(
                    onClick = onNavigateToMembers,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Manage Members")
                }
            }

            // --- Gemini API Key ---
            item { Divider() }
            item {
                Text("AI Meal Naming", style = MaterialTheme.typography.titleMedium)
            }
            item {
                OutlinedTextField(
                    value = apiKeyText,
                    onValueChange = { apiKeyText = it },
                    label = { Text("Gemini API Key") },
                    visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { showApiKey = !showApiKey }) {
                            Icon(
                                if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.saveApiKey(apiKeyText); apiKeyText = "" },
                        enabled = apiKeyText.isNotBlank()
                    ) { Text("Save Key") }
                    OutlinedButton(onClick = { viewModel.clearApiKey(); apiKeyText = "" }) {
                        Text("Clear")
                    }
                }
            }

            // --- Exploration ratio ---
            item { Divider() }
            item {
                Text("Discovery Ratio", style = MaterialTheme.typography.titleMedium)
            }
            item {
                Column {
                    Text(
                        "${(explorationRatio * 100).toInt()}% of suggestions are discoveries",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Slider(
                        value = explorationRatio,
                        onValueChange = { viewModel.setExplorationRatio(it) },
                        valueRange = 0.10f..0.30f,
                        steps = 3
                    )
                }
            }

            // --- Ranking weights ---
            item { Divider() }
            item {
                Text("Ranking Weights", style = MaterialTheme.typography.titleMedium)
            }
            item {
                Text(
                    "These adapt automatically as the app learns. You can reset any weight to its default.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            when (val state = weightsState) {
                is UiState.Loading -> item {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is UiState.Success -> {
                    items(state.data) { weight ->
                        WeightSliderRow(
                            weight = weight,
                            onValueChange = { newValue ->
                                viewModel.updateWeight(weight.copy(value = newValue))
                            },
                            onReset = { viewModel.resetWeight(weight.signalName) }
                        )
                    }
                }
                is UiState.Error -> item {
                    Text(state.message, color = MaterialTheme.colorScheme.error)
                }
            }

            // --- About ---
            item { Divider() }
            item {
                Text("About", style = MaterialTheme.typography.titleMedium)
            }
            item {
                Text("What's Cooking? · v1.0", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun WeightSliderRow(
    weight: RankingWeight,
    onValueChange: (Float) -> Unit,
    onReset: () -> Unit
) {
    val min = 0.1f * weight.defaultValue
    val max = 2.0f * weight.defaultValue

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(weight.signalName, style = MaterialTheme.typography.labelLarge)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("%.2f".format(weight.value), style = MaterialTheme.typography.labelSmall)
                if (weight.value != weight.defaultValue) {
                    TextButton(onClick = onReset, contentPadding = PaddingValues(4.dp)) {
                        Text("Reset", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        Slider(
            value = weight.value.coerceIn(min, max),
            onValueChange = onValueChange,
            valueRange = min..max
        )
    }
}
```

- [ ] **Step 2: Replace Settings stub in AppNavigation**

```kotlin
composable(Screen.Settings.route) {
    SettingsScreen(
        onNavigateBack = { navController.popBackStack() },
        onNavigateToMembers = { navController.navigate(Screen.MemberProfiles.route) }
    )
}
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/familymeal/assistant/ui/settings/SettingsScreen.kt \
        app/src/main/java/com/familymeal/assistant/ui/navigation/AppNavigation.kt
git commit -m "feat: implement SettingsScreen with weight sliders, exploration ratio, API key"
```

---

### Task 3: `MemberProfilesViewModel` — write tests first

**Files:**
- Create: `ui/members/MemberProfilesViewModel.kt`
- Create: `app/src/test/.../ui/MemberProfilesViewModelTest.kt`

- [ ] **Step 1: Write failing tests**

```kotlin
package com.familymeal.assistant.ui

import app.cash.turbine.test
import com.familymeal.assistant.data.db.entity.DietType
import com.familymeal.assistant.data.db.entity.Member
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
    private lateinit var vm: MemberProfilesViewModel

    private val alice = Member(1, "Alice", DietType.Veg)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        memberRepo = mockk(relaxed = true)
        every { memberRepo.observeActiveMembers() } returns flowOf(listOf(alice))
        vm = MemberProfilesViewModel(memberRepo)
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
```

- [ ] **Step 2: Run — expect FAIL**

```bash
./gradlew :app:test --tests "*.MemberProfilesViewModelTest"
```

- [ ] **Step 3: Implement MemberProfilesViewModel.kt**

```kotlin
package com.familymeal.assistant.ui.members

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familymeal.assistant.data.db.entity.DietType
import com.familymeal.assistant.data.db.entity.Member
import com.familymeal.assistant.data.repository.MemberRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MemberProfilesViewModel @Inject constructor(
    private val memberRepository: MemberRepository
) : ViewModel() {

    val members = memberRepository.observeActiveMembers()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun addMember(name: String, dietType: DietType, birthYear: Int?) {
        viewModelScope.launch {
            memberRepository.addMember(Member(name = name, dietType = dietType, birthYear = birthYear))
        }
    }

    fun updateMember(member: Member) {
        viewModelScope.launch { memberRepository.updateMember(member) }
    }

    fun deactivateMember(memberId: Long) {
        viewModelScope.launch { memberRepository.deactivateMember(memberId) }
    }
}
```

- [ ] **Step 4: Run — expect PASS**

```bash
./gradlew :app:test --tests "*.MemberProfilesViewModelTest"
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/familymeal/assistant/ui/members/MemberProfilesViewModel.kt \
        app/src/test/java/com/familymeal/assistant/ui/MemberProfilesViewModelTest.kt
git commit -m "feat: implement MemberProfilesViewModel"
```

---

### Task 4: `MemberProfilesScreen` composable

**Files:**
- Create: `ui/members/MemberProfilesScreen.kt`
- Modify: `ui/navigation/AppNavigation.kt` (replace MemberProfiles stub)

- [ ] **Step 1: Create MemberProfilesScreen.kt**

```kotlin
package com.familymeal.assistant.ui.members

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.familymeal.assistant.data.db.entity.DietType
import com.familymeal.assistant.data.db.entity.Member

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemberProfilesScreen(
    onNavigateBack: () -> Unit,
    viewModel: MemberProfilesViewModel = hiltViewModel()
) {
    val members by viewModel.members.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var editingMember by remember { mutableStateOf<Member?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Household Members") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Member")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(members) { member ->
                ListItem(
                    headlineContent = { Text(member.name) },
                    supportingContent = {
                        Text("${member.dietType.name}${member.birthYear?.let { " · Born $it" } ?: ""}")
                    },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { editingMember = member }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit")
                            }
                            IconButton(onClick = { viewModel.deactivateMember(member.id) }) {
                                Icon(Icons.Default.PersonOff, contentDescription = "Deactivate")
                            }
                        }
                    }
                )
                Divider()
            }
        }
    }

    if (showAddDialog) {
        MemberEditDialog(
            member = null,
            onConfirm = { name, diet, year ->
                viewModel.addMember(name, diet, year)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }

    editingMember?.let { member ->
        MemberEditDialog(
            member = member,
            onConfirm = { name, diet, year ->
                viewModel.updateMember(member.copy(name = name, dietType = diet, birthYear = year))
                editingMember = null
            },
            onDismiss = { editingMember = null }
        )
    }
}

@Composable
private fun MemberEditDialog(
    member: Member?,
    onConfirm: (String, DietType, Int?) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(member?.name ?: "") }
    var selectedDiet by remember { mutableStateOf(member?.dietType ?: DietType.Veg) }
    var birthYearText by remember { mutableStateOf(member?.birthYear?.toString() ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (member == null) "Add Member" else "Edit Member") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") })
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                    label = { Text("Birth Year (optional)") }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim(), selectedDiet, birthYearText.toIntOrNull()) },
                enabled = name.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
```

- [ ] **Step 2: Replace MemberProfiles stub in AppNavigation**

```kotlin
composable(Screen.MemberProfiles.route) {
    MemberProfilesScreen(onNavigateBack = { navController.popBackStack() })
}
```

- [ ] **Step 3: Build and verify**

```bash
./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Run all tests**

```bash
./gradlew :app:test
```

Expected: All tests PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/familymeal/assistant/ui/members/ \
        app/src/main/java/com/familymeal/assistant/ui/navigation/AppNavigation.kt
git commit -m "feat: implement MemberProfilesScreen with add/edit/deactivate"
```

---

### Task 5: Final build + full test suite

- [ ] **Step 1: Run full test suite**

```bash
./gradlew :app:test
```

Expected: All tests in all phases PASS.

- [ ] **Step 2: Final release build check**

```bash
./gradlew :app:assembleRelease
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Tag the MVP completion commit**

```bash
git tag -a v0.1.0-mvp -m "MVP: Family Meal Assistant complete"
```
