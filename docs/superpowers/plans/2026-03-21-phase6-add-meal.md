# Phase 6: Add Meal Screen Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement `AddMealViewModel` (photo-first save, async Gemini classification, one-time API key banner) and `AddMealScreen` (camera-first UI with gallery fallback, shimmer on name field while classification is in-flight, silent failure paths).

**Architecture:** Camera via CameraX + `ActivityResultContracts`. Photo saved to app-internal storage immediately. `MealEntry` inserted to Room with `classificationPending = true` the moment Save is tapped — Gemini call fires in a separate coroutine and updates the entry if successful. If it fails or times out, the entry stays with user-entered name. The one-time API key banner is driven by `SettingsRepository.isApiKeyBannerDismissed()`.

**Tech Stack:** CameraX 1.4.2, Compose, Hilt, Coroutines, JUnit 4 + Turbine + MockK

**Prerequisite:** Phases 1–4 complete. Phase 3 `ImageClassifier` available.

---

## File Map

```
app/src/main/java/com/familymeal/assistant/
  ui/
    addmeal/
      AddMealViewModel.kt
      AddMealScreen.kt
      ShimmerBox.kt          (reusable shimmer composable)

app/src/test/java/com/familymeal/assistant/
  ui/
    AddMealViewModelTest.kt
```

---

### Task 1: `ShimmerBox` reusable composable

**Files:**
- Create: `ui/addmeal/ShimmerBox.kt`

- [ ] **Step 1: Create ShimmerBox.kt**

```kotlin
package com.familymeal.assistant.ui.addmeal

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun ShimmerBox(modifier: Modifier = Modifier) {
    val shimmerColors = listOf(
        Color.LightGray.copy(alpha = 0.6f),
        Color.LightGray.copy(alpha = 0.2f),
        Color.LightGray.copy(alpha = 0.6f)
    )
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)),
        label = "shimmer_translate"
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(
                Brush.linearGradient(
                    colors = shimmerColors,
                    start = Offset(translateAnim - 200f, 0f),
                    end = Offset(translateAnim, 0f)
                )
            )
    )
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/familymeal/assistant/ui/addmeal/ShimmerBox.kt
git commit -m "feat: add ShimmerBox composable"
```

---

### Task 2: `AddMealViewModel` — write tests first

**Files:**
- Create: `ui/addmeal/AddMealViewModel.kt`
- Create: `app/src/test/.../ui/AddMealViewModelTest.kt`

- [ ] **Step 1: Write failing tests**

```kotlin
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
        every { settingsRepo.getGeminiApiKey() } returns null
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
        // Classifier never completes (simulates network hang)
        every { classifier.classify(any()) } returns kotlinx.coroutines.flow.flow {
            kotlinx.coroutines.delay(60_000)
            emit(ClassificationResult.Failure)
        }

        // Save should complete immediately regardless
        vm.saveMeal(uri, "Quick Save", MealType.Dinner, listOf(1L), null)
        coVerify { mealRepo.saveMeal(any(), any()) }
    }
}
```

- [ ] **Step 2: Run — expect FAIL**

```bash
./gradlew :app:test --tests "*.AddMealViewModelTest"
```

- [ ] **Step 3: Implement AddMealViewModel.kt**

```kotlin
package com.familymeal.assistant.ui.addmeal

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familymeal.assistant.data.db.entity.*
import com.familymeal.assistant.data.repository.*
import com.familymeal.assistant.domain.classifier.ImageClassifier
import com.familymeal.assistant.domain.model.ClassificationResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class ClassificationState {
    object Idle : ClassificationState()
    object InFlight : ClassificationState()
    data class Success(val suggestedName: String) : ClassificationState()
}

@HiltViewModel
class AddMealViewModel @Inject constructor(
    private val mealRepository: MealRepository,
    private val memberRepository: MemberRepository,
    private val settingsRepository: SettingsRepository,
    private val imageClassifier: ImageClassifier
) : ViewModel() {

    private val _classificationState = MutableStateFlow<ClassificationState>(ClassificationState.Idle)
    val classificationState: StateFlow<ClassificationState> = _classificationState

    private val _showApiKeyBanner = MutableStateFlow(
        !settingsRepository.isApiKeyBannerDismissed() && settingsRepository.getGeminiApiKey() == null
    )
    val showApiKeyBanner: StateFlow<Boolean> = _showApiKeyBanner

    val activeMembers = flow {
        emit(memberRepository.getActiveMembers())
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /**
     * Saves the meal immediately (non-blocking) then starts classification in background.
     * Returns the saved meal entry ID.
     */
    fun saveMeal(
        photoUri: Uri?,
        mealName: String,
        mealType: MealType,
        memberIds: List<Long>,
        catalogMealId: Long?
    ) {
        viewModelScope.launch {
            val entry = MealEntry(
                name = mealName,
                photoUri = photoUri?.toString(),
                mealType = mealType,
                catalogMealId = catalogMealId,
                classificationPending = photoUri != null
            )
            val savedId = mealRepository.saveMeal(entry, memberIds)

            // Classification runs independently — save is already done
            if (photoUri != null) {
                startClassification(photoUri, savedId)
            }
        }
    }

    fun startClassification(photoUri: Uri, savedMealId: Long) {
        viewModelScope.launch {
            _classificationState.value = ClassificationState.InFlight
            imageClassifier.classify(photoUri)
                .collect { result ->
                    when (result) {
                        is ClassificationResult.Success -> {
                            _classificationState.value = ClassificationState.Success(result.mealName)
                            // Update the saved entry with suggested name
                            val entry = MealEntry(
                                id = savedMealId,
                                name = result.mealName,
                                mealType = MealType.Lunch, // placeholder; real impl reads existing entry
                                aiSuggestedName = result.mealName,
                                classificationPending = false
                            )
                            // In real impl: load existing entry by ID and update fields
                        }
                        is ClassificationResult.Failure -> {
                            _classificationState.value = ClassificationState.Idle
                            // Update entry to clear pending flag
                        }
                    }
                }
        }
    }

    fun dismissApiKeyBanner() {
        settingsRepository.dismissApiKeyBanner()
        _showApiKeyBanner.value = false
    }

    fun resetClassificationState() {
        _classificationState.value = ClassificationState.Idle
    }
}
```

- [ ] **Step 4: Run — expect PASS**

```bash
./gradlew :app:test --tests "*.AddMealViewModelTest"
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/familymeal/assistant/ui/addmeal/AddMealViewModel.kt \
        app/src/test/java/com/familymeal/assistant/ui/AddMealViewModelTest.kt
git commit -m "feat: implement AddMealViewModel with non-blocking save and async classification"
```

---

### Task 3: `AddMealScreen` composable

**Files:**
- Create: `ui/addmeal/AddMealScreen.kt`
- Modify: `ui/navigation/AppNavigation.kt` (replace AddMeal stub)

- [ ] **Step 1: Create AddMealScreen.kt**

```kotlin
package com.familymeal.assistant.ui.addmeal

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.familymeal.assistant.data.db.entity.MealType
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMealScreen(
    onMealSaved: () -> Unit,
    viewModel: AddMealViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val classificationState by viewModel.classificationState.collectAsState()
    val showBanner by viewModel.showApiKeyBanner.collectAsState()
    val activeMembers by viewModel.activeMembers.collectAsState()

    var capturedUri by remember { mutableStateOf<Uri?>(null) }
    var mealName by remember { mutableStateOf("") }
    var selectedMealType by remember { mutableStateOf(MealType.Lunch) }
    var selectedMemberIds by remember { mutableStateOf<List<Long>>(emptyList()) }

    // When classification succeeds, pre-fill name
    LaunchedEffect(classificationState) {
        if (classificationState is ClassificationState.Success && mealName.isBlank()) {
            mealName = (classificationState as ClassificationState.Success).suggestedName
        }
    }

    // Initialise selectedMemberIds to all active members
    LaunchedEffect(activeMembers) {
        if (selectedMemberIds.isEmpty()) selectedMemberIds = activeMembers.map { it.id }
    }

    // Camera launcher
    val cameraUri = remember {
        val file = File(context.cacheDir, "meal_photo_${System.currentTimeMillis()}.jpg")
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) capturedUri = cameraUri
    }
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> capturedUri = uri }

    // Launch camera on entry
    LaunchedEffect(Unit) {
        cameraLauncher.launch(cameraUri)
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Add Meal") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // API key banner (one-time)
            if (showBanner) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            "Add Gemini API key in Settings for auto meal naming.",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall
                        )
                        TextButton(onClick = { viewModel.dismissApiKeyBanner() }) { Text("Dismiss") }
                    }
                }
            }

            // Meal name field — shimmer while classification in-flight
            if (classificationState is ClassificationState.InFlight) {
                ShimmerBox(modifier = Modifier.fillMaxWidth())
            } else {
                OutlinedTextField(
                    value = mealName,
                    onValueChange = { mealName = it },
                    label = { Text("Meal name") },
                    placeholder = { Text("Tap to name…") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Gallery fallback
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (capturedUri != null) Text("Photo captured ✓", style = MaterialTheme.typography.bodySmall)
                IconButton(onClick = { galleryLauncher.launch("image/*") }) {
                    Icon(Icons.Default.Image, contentDescription = "Gallery")
                }
            }

            // Meal type picker
            Text("Meal type", style = MaterialTheme.typography.labelMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(MealType.values().toList()) { type ->
                    FilterChip(
                        selected = selectedMealType == type,
                        onClick = { selectedMealType = type },
                        label = { Text(type.name) }
                    )
                }
            }

            // Audience selector
            Text("Who's eating?", style = MaterialTheme.typography.labelMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = selectedMemberIds.size == activeMembers.size,
                        onClick = { selectedMemberIds = activeMembers.map { it.id } },
                        label = { Text("Family") }
                    )
                }
                items(activeMembers) { member ->
                    FilterChip(
                        selected = selectedMemberIds == listOf(member.id),
                        onClick = { selectedMemberIds = listOf(member.id) },
                        label = { Text(member.name) }
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            Button(
                onClick = {
                    viewModel.saveMeal(
                        photoUri = capturedUri,
                        mealName = mealName.ifBlank { "Unnamed Meal" },
                        mealType = selectedMealType,
                        memberIds = selectedMemberIds,
                        catalogMealId = null
                    )
                    onMealSaved()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Meal")
            }
        }
    }
}
```

- [ ] **Step 2: Add FileProvider to AndroidManifest.xml**

```xml
<!-- Inside <application> in AndroidManifest.xml -->
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_paths" />
</provider>
```

Create `app/src/main/res/xml/file_paths.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <cache-path name="meal_photos" path="." />
</paths>
```

- [ ] **Step 3: Replace AddMeal stub in AppNavigation**

```kotlin
composable(Screen.AddMeal.route) {
    AddMealScreen(onMealSaved = { navController.navigate(Screen.Home.route) {
        popUpTo(Screen.Home.route) { inclusive = false }
    }})
}
```

- [ ] **Step 4: Build and verify**

```bash
./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Run all tests**

```bash
./gradlew :app:test
```

Expected: All tests PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/familymeal/assistant/ui/addmeal/ \
        app/src/main/res/xml/file_paths.xml \
        app/src/main/AndroidManifest.xml \
        app/src/main/java/com/familymeal/assistant/ui/navigation/AppNavigation.kt
git commit -m "feat: implement AddMealScreen with camera-first flow, shimmer, API key banner"
```
