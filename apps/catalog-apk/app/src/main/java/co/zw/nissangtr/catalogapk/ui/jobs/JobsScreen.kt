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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
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
import co.zw.nissangtr.catalogapk.data.model.JobDesiredState
import co.zw.nissangtr.catalogapk.data.model.JobEntity
import co.zw.nissangtr.catalogapk.data.model.JobStatus

@Composable
fun JobsScreen(
    onOpenJob: (String) -> Unit,
    onReviewBundle: (String) -> Unit = {},
    viewModel: JobsViewModel = viewModel(),
) {
    val jobs by viewModel.jobs.collectAsStateWithLifecycle()
    val deleteMessage by viewModel.deleteMessage.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<JobEntity?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Text("Jobs", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Pause is cooperative (current page finishes). Resume re-enqueues the Chaquopy worker. " +
                "Delete cancels WorkManager work and removes the job folder.",
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
                val canReview = viewModel.canReview(job)

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
                        if (!job.errorMessage.isNullOrBlank()) {
                            Text(
                                job.errorMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
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
                            if (canReview) {
                                TextButton(onClick = { onReviewBundle(job.id) }) {
                                    Text("Review")
                                }
                            }
                            TextButton(onClick = { pendingDelete = job }) {
                                Text("Delete")
                            }
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { job ->
        val processing = job.status == JobStatus.PROCESSING.name
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete job?") },
            text = {
                Text(
                    buildString {
                        append("Remove ")
                        append(job.maker)
                        append(" · ")
                        append(job.chassisCodesCsv)
                        append(" from the list, cancel its WorkManager work, and delete ")
                        append("catalog-jobs/")
                        append(job.id.take(8))
                        append("… on disk.")
                        if (processing) {
                            append(" The crawl is still PROCESSING — it will be paused/cancelled first.")
                        }
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteJob(job.id)
                        pendingDelete = null
                    },
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
    }

    deleteMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { viewModel.clearDeleteMessage() },
            title = { Text("Job deleted") },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearDeleteMessage() }) { Text("OK") }
            },
        )
    }
}
