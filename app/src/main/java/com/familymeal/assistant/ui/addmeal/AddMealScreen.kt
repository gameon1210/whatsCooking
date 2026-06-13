package com.familymeal.assistant.ui.addmeal

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.familymeal.assistant.data.db.entity.MealType
import com.familymeal.assistant.ui.common.InputValidators
import java.io.File
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMealScreen(
    onNavigateBack: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onMealSaved: () -> Unit,
    viewModel: AddMealViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val classificationState by viewModel.classificationState.collectAsState()
    val showBanner by viewModel.showApiKeyBanner.collectAsState()
    val activeMembers by viewModel.activeMembers.collectAsState()
    val showPostSaveFeedback by viewModel.showPostSaveFeedback.collectAsState()
    val lastSavedMealName by viewModel.lastSavedMealName.collectAsState()

    var capturedUri by remember { mutableStateOf<Uri?>(null) }
    var mealName by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var selectedMealType by remember { mutableStateOf(defaultMealTypeForNow()) }
    var selectedMemberIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var membersInitialized by rememberSaveable { mutableStateOf(false) }
    var showValidation by remember { mutableStateOf(false) }
    var cameraUri by remember { mutableStateOf(createCameraUri(context)) }
    var cameraPermissionDenied by rememberSaveable { mutableStateOf(false) }
    var didAttemptInitialCapture by rememberSaveable { mutableStateOf(false) }

    // Pre-fill the name with the AI suggestion once available (user can edit)
    LaunchedEffect(classificationState) {
        val state = classificationState
        if (state is ClassificationState.Success && mealName.isBlank()) {
            mealName = state.suggestedName
        }
    }

    // First-load default only — never overwrite a user's selection
    LaunchedEffect(activeMembers) {
        if (!membersInitialized && activeMembers.isNotEmpty()) {
            membersInitialized = true
            selectedMemberIds = activeMembers.map { it.id }.toSet()
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            capturedUri = cameraUri
            cameraPermissionDenied = false
            viewModel.classifyPhoto(cameraUri)
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            cameraPermissionDenied = false
            cameraLauncher.launch(cameraUri)
        } else {
            cameraPermissionDenied = true
        }
    }
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            capturedUri = uri
            viewModel.classifyPhoto(uri)
        }
    }

    fun launchCamera() {
        cameraUri = createCameraUri(context)
        when {
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED -> {
                cameraPermissionDenied = false
                cameraLauncher.launch(cameraUri)
            }
            else -> permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val mealNameError = InputValidators.mealNameError(mealName)
    val memberSelectionError = when {
        activeMembers.isEmpty() -> "Add at least one household member in Settings before saving meals."
        selectedMemberIds.isEmpty() -> "Choose who ate this meal."
        else -> null
    }

    LaunchedEffect(Unit) {
        if (!didAttemptInitialCapture) {
            didAttemptInitialCapture = true
            launchCamera()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Meal") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (showBanner) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Add your AI provider, model, and secret key in Settings > AI setup for automatic meal naming.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = onNavigateToSettings) { Text("Open settings") }
                            TextButton(onClick = { viewModel.dismissApiKeyBanner() }) { Text("Dismiss") }
                        }
                    }
                }
            }

            // Photo preview
            if (capturedUri != null) {
                AsyncImage(
                    model = capturedUri,
                    contentDescription = "Meal photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(16.dp))
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { launchCamera() }) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = "Camera")
                    Spacer(Modifier.width(8.dp))
                    Text(if (capturedUri == null) "Use camera" else "Retake")
                }
                FilledTonalButton(onClick = { galleryLauncher.launch("image/*") }) {
                    Icon(Icons.Default.Image, contentDescription = "Gallery")
                    Spacer(Modifier.width(8.dp))
                    Text("Gallery")
                }
            }
            if (capturedUri == null && cameraPermissionDenied) {
                Text(
                    "Camera permission is required to take a photo. You can retry or pick one from the gallery.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            OutlinedTextField(
                value = mealName,
                onValueChange = { mealName = it },
                label = { Text("Meal name") },
                placeholder = { Text("e.g. Veg pulao") },
                isError = showValidation && mealNameError != null,
                supportingText = {
                    when {
                        showValidation && mealNameError != null -> Text(mealNameError)
                        classificationState is ClassificationState.InFlight ->
                            Text("Identifying meal from photo…")
                        classificationState is ClassificationState.Success ->
                            Text("AI suggestion — edit if it's wrong")
                    }
                },
                trailingIcon = {
                    if (classificationState is ClassificationState.InFlight) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else if (classificationState is ClassificationState.Success) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = "AI suggested",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            Text("Meal type", style = MaterialTheme.typography.labelLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(MealType.entries) { type ->
                    FilterChip(
                        selected = selectedMealType == type,
                        onClick = { selectedMealType = type },
                        label = { Text(type.name) }
                    )
                }
            }

            Text("Who's eating?", style = MaterialTheme.typography.labelLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    val allSelected = activeMembers.isNotEmpty() &&
                        selectedMemberIds.size == activeMembers.size
                    FilterChip(
                        selected = allSelected,
                        onClick = {
                            selectedMemberIds =
                                if (allSelected) emptySet()
                                else activeMembers.map { it.id }.toSet()
                        },
                        label = { Text("Family") }
                    )
                }
                items(activeMembers) { member ->
                    FilterChip(
                        selected = member.id in selectedMemberIds,
                        onClick = {
                            selectedMemberIds =
                                if (member.id in selectedMemberIds) selectedMemberIds - member.id
                                else selectedMemberIds + member.id
                        },
                        label = { Text(member.name) }
                    )
                }
            }
            if (showValidation && memberSelectionError != null) {
                Text(
                    memberSelectionError,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Note (optional)") },
                placeholder = { Text("e.g. kids loved it, easy tiffin") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(4.dp))

            Button(
                onClick = {
                    showValidation = true
                    if (mealNameError != null || memberSelectionError != null) return@Button

                    viewModel.saveMeal(
                        photoUri = capturedUri,
                        mealName = mealName.trim(),
                        mealType = selectedMealType,
                        memberIds = selectedMemberIds.toList(),
                        notes = notes,
                        catalogMealId = null
                    )
                },
                enabled = activeMembers.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Meal")
            }
        }
    }

    // V2: post-save feedback sheet
    if (showPostSaveFeedback) {
        val mealEntryId = viewModel.lastSavedMealId ?: 0L
        PostSaveFeedbackSheet(
            mealName = lastSavedMealName.ifBlank { "your meal" },
            mealEntryId = mealEntryId,
            onFeedback = { id, feedbackType -> viewModel.saveFeedback(id, feedbackType) },
            onDismiss = {
                viewModel.dismissPostSaveFeedback()
                onMealSaved()
            }
        )
    }
}

/** Time-aware default, mirroring Home's context inference (FSD 3.3). */
private fun defaultMealTypeForNow(): MealType {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 5..10 -> MealType.Breakfast
        in 11..15 -> MealType.Lunch
        in 16..18 -> MealType.Snack
        else -> MealType.Dinner
    }
}

private fun createCameraUri(context: Context): Uri {
    // filesDir, not cacheDir — cached photos can be wiped by the OS,
    // which would break history thumbnails.
    val imageDir = File(context.filesDir, "meal_photos").apply { mkdirs() }
    val photoFile = File.createTempFile("meal_photo_", ".jpg", imageDir)
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile)
}
