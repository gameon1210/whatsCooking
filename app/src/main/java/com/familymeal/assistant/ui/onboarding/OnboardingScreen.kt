package com.familymeal.assistant.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.familymeal.assistant.data.db.entity.DietType
import com.familymeal.assistant.ui.common.InputValidators

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
    var showValidation by remember { mutableStateOf(false) }

    val nameError = InputValidators.memberNameError(name, members.map { it.name })
    val birthYearError = InputValidators.birthYearError(birthYearText)

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
                isError = showValidation && nameError != null,
                supportingText = {
                    if (showValidation && nameError != null) {
                        Text(nameError)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DietType.entries.forEach { diet ->
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
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = showValidation && birthYearError != null,
                supportingText = {
                    if (showValidation && birthYearError != null) {
                        Text(birthYearError)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    showValidation = true
                    if (nameError != null || birthYearError != null) return@Button

                    viewModel.addMember(name.trim(), selectedDiet, birthYearText.toIntOrNull())
                    name = ""
                    birthYearText = ""
                    showValidation = false
                },
                enabled = nameError == null && birthYearError == null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Add Member")
            }

            HorizontalDivider()

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(members) { index, member ->
                    ListItem(
                        headlineContent = { Text(member.name) },
                        supportingContent = {
                            Text("${member.dietType.name}${member.birthYear?.let { " · Born $it" } ?: ""}")
                        },
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
