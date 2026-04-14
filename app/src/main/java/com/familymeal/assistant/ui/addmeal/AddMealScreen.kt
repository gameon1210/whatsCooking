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

    LaunchedEffect(classificationState) {
        if (classificationState is ClassificationState.Success && mealName.isBlank()) {
            mealName = (classificationState as ClassificationState.Success).suggestedName
        }
    }

    LaunchedEffect(activeMembers) {
        if (selectedMemberIds.isEmpty()) selectedMemberIds = activeMembers.map { it.id }
    }

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

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (capturedUri != null) Text("Photo captured", style = MaterialTheme.typography.bodySmall)
                IconButton(onClick = { galleryLauncher.launch("image/*") }) {
                    Icon(Icons.Default.Image, contentDescription = "Gallery")
                }
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
