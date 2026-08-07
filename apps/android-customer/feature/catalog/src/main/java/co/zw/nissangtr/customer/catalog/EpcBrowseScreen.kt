package co.zw.nissangtr.customer.catalog

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import co.zw.nissangtr.customer.rpc.EpcDiagramPart
import co.zw.nissangtr.customer.rpc.EpcDiagramResponse
import co.zw.nissangtr.customer.rpc.EpcHotspot
import co.zw.nissangtr.customer.rpc.EpcMaker
import co.zw.nissangtr.customer.rpc.EpcModel
import co.zw.nissangtr.customer.rpc.EpcSection
import co.zw.nissangtr.customer.rpc.EpcVariant
import co.zw.nissangtr.customer.rpc.RpcClient
import co.zw.nissangtr.ui.shop.ShopCircleIconButton
import co.zw.nissangtr.ui.shop.ShopHonestEmpty
import co.zw.nissangtr.ui.shop.ShopListCard
import co.zw.nissangtr.ui.shop.ShopRemoteImage
import co.zw.nissangtr.ui.theme.GtrColors
import kotlinx.coroutines.launch

private sealed class EpcLevel {
    data object Makers : EpcLevel()
    data class Models(val maker: EpcMaker) : EpcLevel()
    data class Variants(val maker: EpcMaker, val model: EpcModel) : EpcLevel()
    data class Sections(
        val maker: EpcMaker,
        val model: EpcModel,
        val variant: EpcVariant,
    ) : EpcLevel()
    data class Diagram(
        val maker: EpcMaker,
        val model: EpcModel,
        val variant: EpcVariant,
        val section: EpcSection,
    ) : EpcLevel()
}

/**
 * Megazip-style hierarchy browse: Maker → Model → Variant → Section → Diagram.
 * Hotspot overlays use normalized 0–1 bbox fractions (native Box — not camera/QR).
 */
@Composable
fun EpcBrowseScreen(
    rpc: RpcClient,
    onBack: () -> Unit,
    onOpenOem: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var level by remember { mutableStateOf<EpcLevel>(EpcLevel.Makers) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var makers by remember { mutableStateOf<List<EpcMaker>>(emptyList()) }
    var models by remember { mutableStateOf<List<EpcModel>>(emptyList()) }
    var variants by remember { mutableStateOf<List<EpcVariant>>(emptyList()) }
    var sections by remember { mutableStateOf<List<EpcSection>>(emptyList()) }
    var diagram by remember { mutableStateOf<EpcDiagramResponse?>(null) }
    val scope = rememberCoroutineScope()

    fun goBack() {
        when (val cur = level) {
            is EpcLevel.Makers -> onBack()
            is EpcLevel.Models -> level = EpcLevel.Makers
            is EpcLevel.Variants -> level = EpcLevel.Models(cur.maker)
            is EpcLevel.Sections -> level = EpcLevel.Variants(cur.maker, cur.model)
            is EpcLevel.Diagram ->
                level = EpcLevel.Sections(cur.maker, cur.model, cur.variant)
        }
    }

    BackHandler { goBack() }

    LaunchedEffect(level) {
        loading = true
        error = null
        try {
            when (val cur = level) {
                is EpcLevel.Makers -> makers = rpc.listCatalogMakers()
                is EpcLevel.Models -> models = rpc.listCatalogModels(cur.maker.slug)
                is EpcLevel.Variants ->
                    variants = rpc.listCatalogVariants(cur.maker.slug, cur.model.slug)
                is EpcLevel.Sections ->
                    sections = rpc.listCatalogSections(
                        cur.maker.slug,
                        cur.model.slug,
                        cur.variant.slug,
                    )
                is EpcLevel.Diagram ->
                    diagram = rpc.getCatalogDiagram(
                        cur.maker.slug,
                        cur.model.slug,
                        cur.variant.slug,
                        cur.section.slug,
                    )
            }
        } catch (t: Throwable) {
            error = t.message ?: "EPC browse failed"
        } finally {
            loading = false
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        ShopCircleIconButton(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            onClick = { goBack() },
            contentDescription = "Back",
            modifier = Modifier.padding(8.dp),
        )
        Text(
            text = when (val cur = level) {
                is EpcLevel.Makers -> "EPC catalog"
                is EpcLevel.Models -> cur.maker.name
                is EpcLevel.Variants -> cur.model.displayName
                is EpcLevel.Sections -> cur.variant.chassisCode
                is EpcLevel.Diagram -> cur.section.name
            },
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        when {
            loading -> Text("Loading…", modifier = Modifier.padding(16.dp))
            error != null -> ShopHonestEmpty(
                title = "EPC unavailable",
                body = error ?: "",
                modifier = Modifier.padding(16.dp),
            )
            level is EpcLevel.Makers -> LazyColumn(
                contentPadding = PaddingValues(16.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(makers, key = { it.slug }) { m ->
                    ShopListCard(
                        title = m.name,
                        subtitle = m.modelCount?.let { "$it models" } ?: "Browse",
                        onClick = { level = EpcLevel.Models(m) },
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
            }
            level is EpcLevel.Models -> LazyColumn(
                contentPadding = PaddingValues(16.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(models.sortedBy { it.sortKey }, key = { it.slug }) { m ->
                    val parent = (level as EpcLevel.Models).maker
                    ShopListCard(
                        title = m.displayName,
                        subtitle = listOfNotNull(m.bodyType, m.yearStart?.toString())
                            .joinToString(" · ")
                            .ifBlank { null },
                        onClick = { level = EpcLevel.Variants(parent, m) },
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
            }
            level is EpcLevel.Variants -> LazyColumn(
                contentPadding = PaddingValues(16.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(variants, key = { it.slug }) { v ->
                    val parent = level as EpcLevel.Variants
                    ShopListCard(
                        title = v.chassisCode,
                        subtitle = listOfNotNull(v.grade, v.yearLabel, v.engineCode)
                            .joinToString(" · ")
                            .ifBlank { null },
                        onClick = {
                            level = EpcLevel.Sections(parent.maker, parent.model, v)
                        },
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
            }
            level is EpcLevel.Sections -> LazyColumn(
                contentPadding = PaddingValues(16.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(sections.sortedBy { it.sortOrder }, key = { it.slug }) { s ->
                    val parent = level as EpcLevel.Sections
                    ShopListCard(
                        title = s.name,
                        subtitle = "Diagram",
                        onClick = {
                            level = EpcLevel.Diagram(
                                parent.maker,
                                parent.model,
                                parent.variant,
                                s,
                            )
                        },
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
            }
            level is EpcLevel.Diagram -> {
                val d = diagram
                if (d == null) {
                    ShopHonestEmpty(
                        title = "No diagram",
                        body = "Section returned empty.",
                        modifier = Modifier.padding(16.dp),
                    )
                } else {
                    EpcDiagramPane(
                        data = d,
                        onOpenOem = onOpenOem,
                        onAddOem = { oem ->
                            scope.launch {
                                runCatching { rpc.addCustomerCartLineByOem(oem) }
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun EpcDiagramPane(
    data: EpcDiagramResponse,
    onOpenOem: (String) -> Unit,
    onAddOem: (String) -> Unit,
) {
    var activeOem by remember { mutableStateOf<String?>(null) }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            if (data.imageUrl.isNullOrBlank()) {
                Text(
                    "Diagram image unavailable — parts list below.",
                    modifier = Modifier.padding(16.dp),
                    color = GtrColors.SilverDim,
                )
            } else {
                EpcHotspotCanvas(
                    imageUrl = data.imageUrl,
                    hotspots = data.hotspots,
                    activeOem = activeOem,
                    onSelectOem = onOpenOem,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .padding(16.dp),
                )
            }
        }
        items(data.parts, key = { it.oemPartNumber }) { part ->
            EpcPartRow(
                part = part,
                active = activeOem == part.oemPartNumber,
                onOpen = { onOpenOem(part.oemPartNumber) },
                onAdd = { onAddOem(part.oemPartNumber) },
                onHover = { activeOem = it },
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun EpcHotspotCanvas(
    imageUrl: String,
    hotspots: List<EpcHotspot>,
    activeOem: String?,
    onSelectOem: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier) {
        val w = maxWidth
        val h = maxHeight
        ShopRemoteImage(
            url = imageUrl,
            contentDescription = "EPC diagram",
            modifier = Modifier.fillMaxSize(),
        )
        hotspots.forEach { hs ->
            val left = w * hs.bboxX.toFloat()
            val top = h * hs.bboxY.toFloat()
            val bw = w * hs.bboxWidth.toFloat()
            val bh = h * hs.bboxHeight.toFloat()
            Box(
                modifier = Modifier
                    .offset(x = left, y = top)
                    .size(width = bw, height = bh)
                    .border(
                        width = 2.dp,
                        color = if (activeOem == hs.oem) {
                            GtrColors.Primary
                        } else {
                            GtrColors.Primary.copy(alpha = 0.55f)
                        },
                    )
                    .clickable { onSelectOem(hs.oem) },
            )
        }
    }
}

@Composable
private fun EpcPartRow(
    part: EpcDiagramPart,
    active: Boolean,
    onOpen: () -> Unit,
    onAdd: () -> Unit,
    onHover: (String?) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                onHover(part.oemPartNumber)
                onOpen()
            }
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            part.oemPartNumber,
            style = MaterialTheme.typography.titleSmall,
            color = if (active) GtrColors.Primary else MaterialTheme.colorScheme.onSurface,
        )
        Text(
            listOfNotNull(part.pncCode, part.categoryName, part.stockDescription)
                .joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = GtrColors.SilverDim,
        )
        if (!part.stockItemId.isNullOrBlank()) {
            TextButton(onClick = onAdd) { Text("Add to cart") }
        }
    }
}
