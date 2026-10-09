package co.zw.nissangtr.catalogapk.ui.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NewSessionScreen(
    onJobsStarted: () -> Unit,
    viewModel: NewSessionViewModel = viewModel(),
) {
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var profileExpanded by remember { mutableStateOf(false) }
    var makerExpanded by remember { mutableStateOf(false) }
    var modelExpanded by remember { mutableStateOf(false) }

    val selectedProfile = profiles.find { it.id == ui.selectedProfileId } ?: profiles.firstOrNull()
    LaunchedEffect(selectedProfile?.id) {
        val id = selectedProfile?.id ?: return@LaunchedEffect
        if (ui.selectedProfileId != id) {
            viewModel.selectProfile(id)
        } else if (ui.makers.isEmpty() && !ui.loadingMakers && ui.error == null) {
            viewModel.refreshMakers()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("New Session", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Makers, models, and chassis come from shipped site catalogs (live fetch only if a custom target has no preset).",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        ExposedDropdownMenuBox(expanded = profileExpanded, onExpandedChange = { profileExpanded = it }) {
            OutlinedTextField(
                value = selectedProfile?.displayName ?: "Select target",
                onValueChange = {},
                readOnly = true,
                label = { Text("Target site") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(profileExpanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
            )
            ExposedDropdownMenu(expanded = profileExpanded, onDismissRequest = { profileExpanded = false }) {
                profiles.forEach { profile ->
                    DropdownMenuItem(
                        text = { Text(profile.displayName) },
                        onClick = {
                            viewModel.selectProfile(profile.id)
                            profileExpanded = false
                        },
                    )
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Makers", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            TextButton(onClick = { viewModel.refreshMakers() }, enabled = !ui.loadingMakers) {
                Text("Refresh")
            }
            if (ui.loadingMakers) CircularProgressIndicator(modifier = Modifier.padding(4.dp))
        }

        ExposedDropdownMenuBox(
            expanded = makerExpanded,
            onExpandedChange = { if (ui.makers.isNotEmpty()) makerExpanded = it },
        ) {
            OutlinedTextField(
                value = ui.selectedMaker?.name ?: if (ui.loadingMakers) "Loading makers…" else "Select maker",
                onValueChange = {},
                readOnly = true,
                enabled = ui.makers.isNotEmpty(),
                label = { Text("Maker") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(makerExpanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
            )
            ExposedDropdownMenu(expanded = makerExpanded, onDismissRequest = { makerExpanded = false }) {
                ui.makers.forEach { maker ->
                    DropdownMenuItem(
                        text = { Text("${maker.name} (${maker.slug})") },
                        onClick = {
                            viewModel.selectMaker(maker)
                            makerExpanded = false
                        },
                    )
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Models", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            if (ui.loadingModels) CircularProgressIndicator()
        }

        ExposedDropdownMenuBox(
            expanded = modelExpanded,
            onExpandedChange = { if (ui.models.isNotEmpty()) modelExpanded = it },
        ) {
            OutlinedTextField(
                value = ui.selectedModel?.displayName
                    ?: when {
                        ui.selectedMaker == null -> "Pick a maker first"
                        ui.loadingModels -> "Loading models…"
                        else -> "Select model"
                    },
                onValueChange = {},
                readOnly = true,
                enabled = ui.models.isNotEmpty(),
                label = { Text("Model") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(modelExpanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
            )
            ExposedDropdownMenu(expanded = modelExpanded, onDismissRequest = { modelExpanded = false }) {
                ui.models.forEach { model ->
                    DropdownMenuItem(
                        text = { Text(model.displayName) },
                        onClick = {
                            viewModel.selectModel(model)
                            modelExpanded = false
                        },
                    )
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Chassis from model", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            if (ui.loadingChassis) CircularProgressIndicator()
            if (ui.chassis.isNotEmpty()) {
                TextButton(onClick = { viewModel.selectAllChassis() }) { Text("Select all") }
            }
        }

        if (ui.chassis.isEmpty() && ui.selectedModel != null && !ui.loadingChassis) {
            Text(
                "No chassis yet — wait for parse or check target paths / FlareSolverr.",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ui.chassis.forEach { item ->
                FilterChip(
                    selected = item.code in ui.selectedChassis,
                    onClick = { viewModel.toggleChassis(item.code) },
                    label = {
                        Text(
                            buildString {
                                append(item.code)
                                if (item.engineCode.isNotBlank()) append(" · ${item.engineCode}")
                            },
                        )
                    },
                )
            }
        }

        ui.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = {
                when {
                    ui.selectedModel != null && ui.selectedMaker != null -> viewModel.selectModel(ui.selectedModel!!)
                    ui.selectedMaker != null -> viewModel.selectMaker(ui.selectedMaker!!)
                    else -> viewModel.refreshMakers()
                }
            }) { Text("Retry discovery") }
        }

        Button(
            onClick = { viewModel.startJobs { onJobsStarted() } },
            enabled = ui.selectedProfileId != null &&
                ui.selectedMaker != null &&
                ui.selectedModel != null &&
                ui.selectedChassis.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Start ${ui.selectedChassis.size} job(s)")
        }
    }
}
