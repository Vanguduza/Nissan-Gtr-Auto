package co.zw.nissangtr.catalogapk.ui.targets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import co.zw.nissangtr.catalogapk.data.model.SiteProfileEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TargetsScreen(
    viewModel: TargetsViewModel = viewModel(),
) {
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<SiteProfileEntity?>(null) }
    var adding by remember { mutableStateOf(false) }
    var baseUrl by remember { mutableStateOf("") }
    var makerHub by remember { mutableStateOf("/parts/{maker_slug}") }
    var partsHub by remember { mutableStateOf("/parts") }
    var previewUrl by remember { mutableStateOf("") }
    var cloudflareMode by remember { mutableStateOf("auto") }
    var flaresolverrUrl by remember { mutableStateOf("http://127.0.0.1:8191/v1") }
    var displayName by remember { mutableStateOf("") }
    var engine by remember { mutableStateOf("custom") }
    var engineExpanded by remember { mutableStateOf(false) }

    fun openEdit(profile: SiteProfileEntity) {
        editing = profile
        adding = false
        val state = viewModel.loadEdit(profile.id)
        baseUrl = state.baseUrl
        makerHub = state.makerHub
        partsHub = state.partsHub
        previewUrl = state.previewUrl
        cloudflareMode = state.cloudflareMode
        flaresolverrUrl = state.flaresolverrUrl
        displayName = profile.displayName
        engine = profile.engine
    }

    fun openAdd() {
        editing = null
        adding = true
        displayName = ""
        engine = "custom"
        baseUrl = "https://"
        makerHub = "/parts/{maker_slug}"
        partsHub = "/parts"
        cloudflareMode = "auto"
        flaresolverrUrl = "http://127.0.0.1:8191/v1"
        previewUrl = ""
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { openAdd() }) {
                Icon(Icons.Default.Add, contentDescription = "Add target")
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Scrape Targets", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Presets plus your custom sites. Paths use {maker_slug} / {model_slug} placeholders. CF mode auto uses FlareSolverr when challenged.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(profiles, key = { it.id }) { profile ->
                    Card(
                        onClick = { openEdit(profile) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text(profile.displayName, style = MaterialTheme.typography.titleMedium)
                                Text("${profile.engine} · ${if (profile.isPreset) "preset" else "custom"}")
                                Text(profile.baseUrl, style = MaterialTheme.typography.bodySmall)
                                Text("CF: ${profile.cloudflareMode}", color = MaterialTheme.colorScheme.primary)
                            }
                            if (!profile.isPreset) {
                                IconButton(onClick = { viewModel.deleteCustom(profile.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (adding || editing != null) {
        val title = if (adding) "Add scrape target" else "Edit ${editing!!.displayName}"
        AlertDialog(
            onDismissRequest = { adding = false; editing = null },
            title = { Text(title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (adding) {
                        OutlinedTextField(
                            value = displayName,
                            onValueChange = { displayName = it },
                            label = { Text("Display name") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        ExposedDropdownMenuBox(expanded = engineExpanded, onExpandedChange = { engineExpanded = it }) {
                            OutlinedTextField(
                                value = engine,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Engine") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(engineExpanded) },
                                modifier = Modifier.menuAnchor().fillMaxWidth(),
                            )
                            ExposedDropdownMenu(expanded = engineExpanded, onDismissRequest = { engineExpanded = false }) {
                                listOf("megazip", "partsouq", "custom").forEach { e ->
                                    DropdownMenuItem(text = { Text(e) }, onClick = { engine = e; engineExpanded = false })
                                }
                            }
                        }
                    }
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = {
                            baseUrl = it
                            previewUrl = viewModel.previewMakerHub(baseUrl, makerHub)
                        },
                        label = { Text("Base URL") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = partsHub,
                        onValueChange = { partsHub = it },
                        label = { Text("Parts / makers hub path") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = makerHub,
                        onValueChange = {
                            makerHub = it
                            previewUrl = viewModel.previewMakerHub(baseUrl, makerHub)
                        },
                        label = { Text("Maker hub template") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = cloudflareMode,
                        onValueChange = { cloudflareMode = it },
                        label = { Text("Cloudflare mode (auto|always|off)") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = flaresolverrUrl,
                        onValueChange = { flaresolverrUrl = it },
                        label = { Text("FlareSolverr URL") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("Preview: $previewUrl", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (adding) {
                            viewModel.addTarget(
                                displayName, engine, baseUrl, makerHub, partsHub, cloudflareMode, flaresolverrUrl,
                            ) {
                                adding = false
                            }
                        } else {
                            viewModel.saveEdit(
                                editing!!.id, baseUrl, makerHub, partsHub, cloudflareMode, flaresolverrUrl,
                            )
                            editing = null
                        }
                    },
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { adding = false; editing = null }) { Text("Cancel") }
            },
        )
    }
}
