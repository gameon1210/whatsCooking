package com.familymeal.assistant.ui.members

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
                HorizontalDivider()
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
