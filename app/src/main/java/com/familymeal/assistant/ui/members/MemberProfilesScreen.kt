package com.familymeal.assistant.ui.members

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.familymeal.assistant.data.db.entity.DietType
import com.familymeal.assistant.data.db.entity.MealEntry
import com.familymeal.assistant.data.db.entity.Member
import com.familymeal.assistant.ui.common.InputValidators

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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
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
                MemberRow(
                    member = member,
                    mealFlow = viewModel.mealsForMember(member.id),
                    onEdit = { editingMember = member },
                    onDeactivate = { viewModel.deactivateMember(member.id) }
                )
                HorizontalDivider()
            }
        }
    }

    if (showAddDialog) {
        MemberEditDialog(
            member = null,
            existingNames = members.map { it.name },
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
            existingNames = members.filter { it.id != member.id }.map { it.name },
            onConfirm = { name, diet, year ->
                viewModel.updateMember(member.copy(name = name, dietType = diet, birthYear = year))
                editingMember = null
            },
            onDismiss = { editingMember = null }
        )
    }
}

@Composable
private fun MemberRow(
    member: Member,
    mealFlow: kotlinx.coroutines.flow.Flow<List<MealEntry>>,
    onEdit: () -> Unit,
    onDeactivate: () -> Unit
) {
    val recentMeals by remember(member.id) { mealFlow }.collectAsState(initial = emptyList())
    val recentSummary = recentMeals.take(3).joinToString(", ") { it.name }

    ListItem(
        headlineContent = { Text(member.name) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${member.dietType.name}${member.birthYear?.let { " · Born $it" } ?: ""}")
                Text(
                    if (recentSummary.isBlank()) "No meal history yet"
                    else "Recent meals: $recentSummary",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        trailingContent = {
            Row {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit")
                }
                IconButton(onClick = onDeactivate) {
                    Icon(Icons.Default.PersonOff, contentDescription = "Deactivate")
                }
            }
        }
    )
}

@Composable
private fun MemberEditDialog(
    member: Member?,
    existingNames: List<String>,
    onConfirm: (String, DietType, Int?) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(member?.name ?: "") }
    var selectedDiet by remember { mutableStateOf(member?.dietType ?: DietType.Veg) }
    var birthYearText by remember { mutableStateOf(member?.birthYear?.toString() ?: "") }
    var showValidation by remember { mutableStateOf(false) }

    val nameError = InputValidators.memberNameError(name, existingNames)
    val birthYearError = InputValidators.birthYearError(birthYearText)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (member == null) "Add Member" else "Edit Member") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    isError = showValidation && nameError != null,
                    supportingText = {
                        if (showValidation && nameError != null) {
                            Text(nameError)
                        }
                    }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                    }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    showValidation = true
                    if (nameError == null && birthYearError == null) {
                        onConfirm(name.trim(), selectedDiet, birthYearText.toIntOrNull())
                    }
                },
                enabled = nameError == null && birthYearError == null
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
