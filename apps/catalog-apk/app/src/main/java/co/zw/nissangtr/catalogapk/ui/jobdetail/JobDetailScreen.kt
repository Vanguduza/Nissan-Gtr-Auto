package co.zw.nissangtr.catalogapk.ui.jobdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import co.zw.nissangtr.catalogapk.data.model.JobStatus
import co.zw.nissangtr.catalogapk.domain.DiskHudReader

@Composable
fun JobDetailScreen(
    onOpenBundles: (String) -> Unit = {},
    onReviewBundle: (String) -> Unit = {},
    onDeleted: () -> Unit = {},
    viewModel: JobDetailViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val deleted by viewModel.deleted.collectAsStateWithLifecycle()
    val job = state.job
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(deleted) {
        if (deleted) onDeleted()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Job Detail", style = MaterialTheme.typography.headlineSmall)
        if (job == null) {
            Text("Job not found")
            return
        }

        MetricCard("Status", "${job.status} (desired: ${job.desiredState})")
        MetricCard("Chassis", job.chassisCodesCsv)
        MetricCard("Out root", job.outRoot)

        state.profile?.let { profile ->
            MetricCard(
                "Cloudflare",
                "${profile.cloudflareMode} · ${profile.flaresolverrUrl}",
            )
        }

        if (state.canReview) {
            Button(
                onClick = { onReviewBundle(job.id) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Review bundle")
            }
        }

        Button(
            onClick = { onOpenBundles(job.id) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Bundle picker / Edge import")
        }

        OutlinedButton(
            onClick = { confirmDelete = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Delete job")
        }

        Text("Quality strip", style = MaterialTheme.typography.titleMedium)
        MetricCard("Sections", state.quality.sections.toString())
        MetricCard("Diagrams", state.quality.diagrams.toString())
        MetricCard("Publishable", state.quality.publishableVariants.toString())
        MetricCard("Uncategorized", state.quality.uncategorized.toString())
        MetricCard("Engine fill", "${state.quality.engineFillPct}%")
        MetricCard(
            "Engine attrs",
            "present ${state.quality.enginePresent} · missing ${state.quality.engineMissing}",
        )
        if (state.quality.isStub) {
            Text(
                "Stub quality artifacts",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Text("Disk HUD", style = MaterialTheme.typography.titleMedium)
        MetricCard("Free space", DiskHudReader.formatBytes(state.diskHud.freeBytes))
        MetricCard("Total", DiskHudReader.formatBytes(state.diskHud.totalBytes))
        MetricCard("HTML dropped", state.diskHud.htmlDropped.toString())
        MetricCard("HTML retained", state.diskHud.htmlRetained.toString())

        job.errorMessage?.let { MetricCard("Error", it) }
    }

    if (confirmDelete && job != null) {
        val processing = job.status == JobStatus.PROCESSING.name
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete job?") },
            text = {
                Text(
                    if (processing) {
                        "This job is PROCESSING. It will be paused/cancelled, then the Room row and " +
                            "catalog-jobs/${job.id.take(8)}… folder will be removed."
                    } else {
                        "Delete the Room row, cancel WorkManager work, and remove the on-disk job tree?"
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        viewModel.deleteJob()
                    },
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun MetricCard(label: String, value: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
