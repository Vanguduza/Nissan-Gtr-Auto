package com.joker.coolmall.feature.main.view

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.zw.nissangtr.management.gtradapter.GtrOpsRow
import co.zw.nissangtr.management.gtradapter.GtrOpsSpec
import com.joker.coolmall.feature.main.viewmodel.StaffOpsViewModel

@Composable
internal fun StaffOpsLeafRoute(
    href: String,
    viewModel: StaffOpsViewModel = hiltViewModel(key = href),
) {
    LaunchedEffect(href) { viewModel.bind(href) }
    val spec by viewModel.spec.collectAsStateWithLifecycle()
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val selectedId by viewModel.selectedId.collectAsStateWithLifecycle()
    val fields by viewModel.fields.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()

    StaffOpsLeafScreen(
        spec = spec,
        rows = rows,
        selectedId = selectedId,
        fields = fields,
        message = message,
        busy = busy,
        onField = viewModel::updateField,
        onSelect = viewModel::select,
        onAction = viewModel::run,
        onRefresh = viewModel::refresh,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StaffOpsLeafScreen(
    spec: GtrOpsSpec?,
    rows: List<GtrOpsRow>,
    selectedId: String?,
    fields: Map<String, String>,
    message: String?,
    busy: Boolean,
    onField: (String, String) -> Unit,
    onSelect: (String) -> Unit,
    onAction: (String) -> Unit,
    onRefresh: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        if (spec == null) {
            Text("Unknown desk", color = MaterialTheme.colorScheme.error)
            return@Column
        }
        Text(spec.hint, style = MaterialTheme.typography.bodySmall)
        if (spec.neverAutoPo) {
            Text(
                "AI never auto-creates POs.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        spec.fields.forEach { field ->
            OutlinedTextField(
                value = fields[field.key].orEmpty(),
                onValueChange = { onField(field.key, it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                singleLine = true,
                label = { Text(field.label) },
            )
        }
        FlowRow(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TextButton(onClick = onRefresh, enabled = !busy) { Text("Refresh") }
            spec.actions.forEach { action ->
                TextButton(onClick = { onAction(action.id) }, enabled = !busy) {
                    Text(action.label)
                }
            }
        }
        if (message != null) {
            Text(message, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
        }
        rows.forEach { row ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(row.id) }
                    .padding(vertical = 8.dp),
            ) {
                Text(
                    row.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (row.id == selectedId) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                if (row.status.isNotBlank()) {
                    Text(row.status, style = MaterialTheme.typography.labelSmall)
                }
                Text(row.subtitle, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (rows.isEmpty()) {
            Text("No rows", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
        }
    }
}
