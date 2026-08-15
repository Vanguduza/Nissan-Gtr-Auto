package co.zw.nissangtr.catalogapk.ui.review

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import co.zw.nissangtr.catalogapk.domain.BundleDiagramRow
import java.io.File

@Composable
fun BundleReviewScreen(
    onOpenSection: (sectionKey: String) -> Unit,
    onOpenDiagram: (diagramKey: String) -> Unit,
    onBack: () -> Unit = {},
    viewModel: BundleReviewViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val model = state.model

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Bundle review", style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = onBack) { Text("Back") }
        }
        Text(
            "Offline local review of crawl outputs (categories → diagrams → fitments).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        when {
            state.loading -> Text("Loading…")
            state.error != null -> Text(state.error!!, color = MaterialTheme.colorScheme.error)
            model == null -> Text("No bundle data")
            else -> {
                Text(
                    buildString {
                        append(model.maker)
                        append(" · sections ")
                        append(model.sections.size)
                        append(" · diagrams ")
                        append(model.diagrams.size)
                        append(" · PNGs ")
                        append(model.diagramPngCount)
                        if (model.gateOk) append(" · gate OK")
                        if (model.qualityPublishable) append(" · publishable")
                    },
                    style = MaterialTheme.typography.labelMedium,
                )

                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (model.sections.isEmpty() && model.diagrams.isEmpty()) {
                        item {
                            Text(
                                "No catalog_sections / catalog_diagrams in this job out dir yet.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    items(model.sections, key = { it.key }) { section ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenSection(section.key) },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            ),
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(section.name, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "${section.modelSlug}/${section.variantSlug} · ${section.diagramCount} diagrams",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                    if (model.sections.isEmpty() && model.diagrams.isNotEmpty()) {
                        items(model.diagrams, key = { it.key }) { diagram ->
                            DiagramListRow(diagram) { onOpenDiagram(diagram.key) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SectionDiagramsScreen(
    onOpenDiagram: (diagramKey: String) -> Unit,
    onBack: () -> Unit = {},
    viewModel: SectionDiagramsViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                state.section?.name ?: "Diagrams",
                style = MaterialTheme.typography.headlineSmall,
            )
            TextButton(onClick = onBack) { Text("Back") }
        }
        state.section?.let {
            Text(
                "${it.modelSlug}/${it.variantSlug}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        when {
            state.loading -> Text("Loading…")
            state.error != null -> Text(state.error!!, color = MaterialTheme.colorScheme.error)
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(state.diagrams, key = { it.key }) { diagram ->
                    DiagramListRow(diagram) { onOpenDiagram(diagram.key) }
                }
            }
        }
    }
}

@Composable
fun DiagramDetailScreen(
    onBack: () -> Unit = {},
    viewModel: DiagramDetailViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val diagram = state.diagram

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                diagram?.title ?: "Diagram",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onBack) { Text("Back") }
        }

        when {
            state.loading -> Text("Loading…")
            state.error != null -> Text(state.error!!, color = MaterialTheme.colorScheme.error)
            diagram == null -> Text("Diagram not found")
            else -> {
                Text(
                    "${diagram.diagramKind} · hotspots ${diagram.hotspotCount} · ${diagram.sectionSlug}",
                    style = MaterialTheme.typography.labelMedium,
                )
                val png = diagram.localPngPath?.let { File(it) }
                if (png != null && png.isFile) {
                    AsyncImage(
                        model = png,
                        contentDescription = diagram.title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1.2f),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    Text(
                        "PNG not found on disk" +
                            if (diagram.storagePath.isNotBlank()) " (${diagram.storagePath})" else "",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Text(
                    "Fitments (${state.fitments.size})",
                    style = MaterialTheme.typography.titleMedium,
                )
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(state.fitments, key = { "${it.oem}|${it.pnc}|${it.callout}|${it.bboxLabel}" }) { row ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            ),
                        ) {
                            Column(Modifier.padding(10.dp)) {
                                Text(
                                    row.oem.ifBlank { "—" },
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                val meta = listOfNotNull(
                                    row.pnc.takeIf { it.isNotBlank() }?.let { "PNC $it" },
                                    row.callout.takeIf { it.isNotBlank() }?.let { "callout $it" },
                                    row.quantity.takeIf { it.isNotBlank() }?.let { "qty $it" },
                                    row.bboxLabel.takeIf { it.isNotBlank() },
                                ).joinToString(" · ")
                                if (meta.isNotBlank()) {
                                    Text(meta, style = MaterialTheme.typography.labelSmall)
                                }
                                if (row.description.isNotBlank()) {
                                    Text(row.description, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                    if (state.fitments.isEmpty()) {
                        item {
                            Text(
                                "No part_fitment / catalog_diagram_parts rows for this diagram.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagramListRow(diagram: BundleDiagramRow, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(diagram.title, style = MaterialTheme.typography.titleSmall)
            Text(
                buildString {
                    append(diagram.diagramKind)
                    append(" · hotspots ")
                    append(diagram.hotspotCount)
                    if (diagram.localPngPath != null) append(" · PNG")
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
