package co.zw.nissangtr.catalogapk.ui.jobdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import co.zw.nissangtr.catalogapk.domain.DiskHudReader

@Composable
fun JobDetailScreen(
    onOpenBundles: (String) -> Unit = {},
    viewModel: JobDetailViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val job = state.job

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

        Button(
            onClick = { onOpenBundles(job.id) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Bundle picker / Edge import")
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
