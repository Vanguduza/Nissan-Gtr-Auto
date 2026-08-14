package co.zw.nissangtr.catalogapk.ui.bundles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import co.zw.nissangtr.catalogapk.ui.bundles.BundlePickerViewModel.Companion.key

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BundlePickerScreen(
    viewModel: BundlePickerViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    var projectExpanded by remember { mutableStateOf(false) }
    val selectedProject = projects.firstOrNull { it.id == state.selectedProjectId }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Bundle picker", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Import only gate-passing variants via JWT Edge (no service role in APK).",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        ExposedDropdownMenuBox(
            expanded = projectExpanded,
            onExpandedChange = { projectExpanded = it },
        ) {
            OutlinedTextField(
                value = selectedProject?.name ?: "Select Supabase project",
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(projectExpanded) },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
            )
            ExposedDropdownMenu(
                expanded = projectExpanded,
                onDismissRequest = { projectExpanded = false },
            ) {
                projects.forEach { project ->
                    DropdownMenuItem(
                        text = { Text(project.name) },
                        onClick = {
                            viewModel.selectProject(project.id)
                            projectExpanded = false
                        },
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { viewModel.selectAllPublishable() }) {
                Text("Select publishable")
            }
            Button(
                onClick = { viewModel.importSelected() },
                enabled = !state.importing,
            ) {
                Text(if (state.importing) "Importing…" else "Import selected")
            }
        }

        state.message?.let {
            Text(it, color = MaterialTheme.colorScheme.primary)
        }

        val rows = state.snapshot?.variants.orEmpty()
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(rows, key = { key(it) }) { row ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Checkbox(
                        checked = key(row) in state.selected,
                        onCheckedChange = { viewModel.toggle(row) },
                        enabled = row.publishable,
                    )
                    Column {
                        Text(
                            "${row.chassisCode.ifBlank { "—" }} · ${row.modelSlug}/${row.variantSlug}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            if (row.publishable) {
                                "publishable · exploded ${row.explodedPassing}"
                            } else {
                                "blocked"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (row.publishable) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                        )
                    }
                }
            }
        }
    }
}
