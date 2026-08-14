package co.zw.nissangtr.catalogapk.ui.jobs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import co.zw.nissangtr.catalogapk.data.model.JobDesiredState
import co.zw.nissangtr.catalogapk.data.model.JobStatus

@Composable
fun JobsScreen(
    onOpenJob: (String) -> Unit,
    viewModel: JobsViewModel = viewModel(),
) {
    val jobs by viewModel.jobs.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Text("Jobs", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Pause is cooperative (current page finishes). Resume re-enqueues the Chaquopy worker.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        )
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(jobs, key = { it.id }) { job ->
                val processing = job.status == JobStatus.PROCESSING.name
                val pausing = processing && job.desiredState == JobDesiredState.PAUSE.name
                val canPause = processing && job.desiredState == JobDesiredState.RUN.name
                val canResume = job.status == JobStatus.PAUSED.name ||
                    job.desiredState == JobDesiredState.PAUSE.name && !processing ||
                    job.status == JobStatus.FAILED.name

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenJob(job.id) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            buildString {
                                append(job.maker)
                                if (job.modelDisplayName.isNotBlank()) {
                                    append(" · ")
                                    append(job.modelDisplayName)
                                }
                                append(" · ")
                                append(job.chassisCodesCsv)
                            },
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(viewModel.statusLabel(job), style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (canPause) {
                                TextButton(onClick = { viewModel.pause(job.id) }) {
                                    Text("Pause")
                                }
                            }
                            if (pausing) {
                                Text(
                                    "Pausing…",
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(top = 12.dp),
                                )
                            }
                            if (canResume) {
                                TextButton(onClick = { viewModel.resume(job.id) }) {
                                    Text("Resume")
                                }
                            }
                            TextButton(onClick = { onOpenJob(job.id) }) {
                                Text("Detail")
                            }
                        }
                    }
                }
            }
        }
    }
}
