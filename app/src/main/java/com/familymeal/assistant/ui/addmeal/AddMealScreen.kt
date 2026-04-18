package com.familymeal.assistant.ui.addmeal

import android.Manifest
import android.content.Context
import android.net.Uri
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.familymeal.assistant.data.db.entity.MealType
import com.familymeal.assistant.ui.common.InputValidators
import java.io.File

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

    var capturedUri by remember { mutableStateOf<Uri?>(null) }
    var mealName by remember { mutableStateOf("") }
    var selectedMealType by remember { mutableStateOf(MealType.Lunch) }
    var selectedMemberIds by remember { mutableStateOf<List<Long>>(emptyList()) }
    var showValidation by remember { mutableStateOf(false) }
    var cameraUri by remember { mutableStateOf(createCameraUri(context)) }
    var cameraPermissionDenied by rememberSaveable { mutableStateOf(false) }
    var didAttemptInitialCapture by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(classificationState) {
        if (classificationState is ClassificationState.Success && mealName.isBlank()) {
            mealName = (classificationState as ClassificationState.Success).suggestedName
        }
    }

    LaunchedEffect(activeMembers) {
        if (selectedMemberIds.isEmpty() && activeMembers.isNotEmpty()) {
            selectedMemberIds = activeMembers.map { it.id }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            capturedUri = cameraUri
            cameraPermissionDenied = false
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
    ) { uri -> capturedUri = uri }

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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (showBanner) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Add your AI provider, model, and secret key in Settings > AI setup for automatic meal naming.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = onNavigateToSettings) { Text("Open settings") }
                            TextButton(onClick = { viewModel.dismissApiKeyBanner() }) { Text("Dismiss") }
                        }
                    }
                }
            }

            if (classificationState is ClassificationState.InFlight) {
                ShimmerBox(modifier = Modifier.fillMaxWidth())
            } else {
                OutlinedTextField(
                    value = mealName,
                    onValueChange = { mealName = it },
                    label = { Text("Meal name") },
                    placeholder = { Text("Tap to name…") },
                    isError = showValidation && mealNameError != null,
                    supportingText = {
                        if (showValidation && mealNameError != null) {
                            Text(mealNameError)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { launchCamera() }) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = "Camera")
                    Spacer(Modifier.width(8.dp))
                    Text(if (capturedUri == null) "Use camera" else "Retake")
                }
                IconButton(onClick = { galleryLauncher.launch("image/*") }) {
                    Icon(Icons.Default.Image, contentDescription = "Gallery")
                }
            }
            if (capturedUri != null) {
                Text("Photo captured", style = MaterialTheme.typography.bodySmall)
            } else if (cameraPermissionDenied) {
                Text(
                    "Camera permission is required to take a photo. You can retry or pick one from the gallery.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Text("Meal type", style = MaterialTheme.typography.labelMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(MealType.entries) { type ->
                    FilterChip(
                        selected = selectedMealType == type,
                        onClick = { selectedMealType = type },
                        label = { Text(type.name) }
                    )
                }
            }

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
            if (showValidation && memberSelectionError != null) {
                Text(
                    memberSelectionError,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(Modifier.weight(1f))

            Button(
                onClick = {
                    showValidation = true
                    if (mealNameError != null || memberSelectionError != null) return@Button

                    viewModel.saveMeal(
                        photoUri = capturedUri,
                        mealName = mealName.trim(),
                        mealType = selectedMealType,
                        memberIds = selectedMemberIds,
                        catalogMealId = null
                    )
                    onMealSaved()
                },
                enabled = activeMembers.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Meal")
            }
        }
    }
}

private fun createCameraUri(context: Context): Uri {
    val imageDir = File(context.cacheDir, "meal_photos").apply { mkdirs() }
    val photoFile = File.createTempFile("meal_photo_", ".jpg", imageDir)
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile)
}
