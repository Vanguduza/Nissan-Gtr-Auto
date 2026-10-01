package co.zw.nissangtr.pos.ui.sale

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.zw.nissangtr.pos.design.icons.Pin
import co.zw.nissangtr.pos.design.icons.PosIcons
import co.zw.nissangtr.pos.design.theme.PosTheme
import co.zw.nissangtr.pos.domain.model.CatalogPart
import co.zw.nissangtr.pos.domain.model.EpcDiagramDetail
import co.zw.nissangtr.pos.domain.model.EpcImage
import co.zw.nissangtr.pos.domain.model.EpcMissing
import co.zw.nissangtr.pos.domain.model.PopularPin
import co.zw.nissangtr.pos.domain.state.PosIntent
import co.zw.nissangtr.pos.domain.state.PosSaleIntent
import co.zw.nissangtr.pos.ui.R
import co.zw.nissangtr.pos.ui.common.PosIcon
import co.zw.nissangtr.pos.ui.common.PosText
import co.zw.nissangtr.pos.ui.home.EmptyCard
import co.zw.nissangtr.pos.ui.home.SoftButton

/**
 * Diagram + parts, side by side when there is room and stacked otherwise. A callout and its parts
 * row share one selection, so tapping either highlights both (web: `EpcScreen` hover sync).
 */
@Composable
internal fun EpcDiagramDetailView(
    detail: EpcDiagramDetail,
    image: EpcImage?,
    activeOem: String?,
    dispatch: (PosIntent) -> Unit,
) {
    val callouts = remember(detail) {
        detail.hotspots.mapIndexed { i, h -> h.oemKey to i + 1 }.distinctBy { it.first }.toMap()
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val split = maxWidth >= 600.dp
        val diagram = @Composable { m: Modifier -> EpcDiagramCanvas(detail, image, activeOem, dispatch, m) }
        val parts = @Composable { m: Modifier -> EpcPartsList(detail, callouts, activeOem, dispatch, m) }
        if (split) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                diagram(Modifier.weight(1.15f))
                parts(Modifier.weight(1f))
            }
        } else {
            Column {
                diagram(Modifier.fillMaxWidth())
                Spacer(Modifier.size(14.dp))
                parts(Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun EpcDiagramCanvas(
    detail: EpcDiagramDetail,
    image: EpcImage?,
    activeOem: String?,
    dispatch: (PosIntent) -> Unit,
    modifier: Modifier,
) {
    val palette = PosTheme.palette
    val bitmap: ImageBitmap? = remember(image) {
        image?.bytes?.let { bytes -> runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }.getOrNull() }
    }
    Column(
        modifier
            .clip(PosTheme.shape.md)
            .background(palette.canvas)
            .border(1.dp, palette.borderSubtle, PosTheme.shape.md)
            .padding(10.dp),
    ) {
        when {
            bitmap != null -> {
                // Pixel boxes are in source-image pixels: the stored size wins, else the decoded one.
                val w = detail.imageWidth ?: bitmap.width
                val h = detail.imageHeight ?: bitmap.height
                val boxes = detail.hotspots.mapIndexedNotNull { i, spot -> spot.normalizedIn(w, h)?.let { Triple(i + 1, spot, it) } }
                BoxWithConstraints(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1))
                        .clip(PosTheme.shape.sm)
                        .background(palette.surfacePrimary),
                ) {
                    Image(bitmap, contentDescription = detail.diagram.title, contentScale = ContentScale.FillBounds, modifier = Modifier.matchParentSize())
                    boxes.forEach { (n, spot, box) ->
                        val on = spot.oemKey == activeOem?.trim()?.uppercase()
                        val label = stringResource(R.string.pos_epc_callout, n, spot.oemPartNumber)
                        Box(
                            Modifier
                                .offset(maxWidth * box.left.toFloat(), maxHeight * box.top.toFloat())
                                .size(maxWidth * box.width.toFloat(), maxHeight * box.height.toFloat())
                                .clip(PosTheme.shape.xs)
                                .background(palette.brandRed.copy(alpha = if (on) 0.35f else 0.12f))
                                .border(2.dp, palette.brandRed, PosTheme.shape.xs)
                                .clickable(role = Role.Button) { dispatch(PosSaleIntent.EpcSelect(spot.oemPartNumber)) }
                                .semantics {
                                    contentDescription = label
                                    selected = on
                                },
                        ) {
                            PosText(
                                n.toString().padStart(2, '0'),
                                PosTheme.type.labelMeta.copy(fontWeight = FontWeight.Bold),
                                palette.textOnBrand,
                                maxLines = 1,
                                modifier = Modifier.background(palette.brandRed).padding(horizontal = 4.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.size(6.dp))
                PosText(
                    if (boxes.isEmpty()) stringResource(R.string.pos_epc_no_callouts)
                    else stringResource(R.string.pos_epc_callouts_hint, boxes.size),
                    PosTheme.type.labelMeta,
                    palette.textMuted,
                )
            }
            detail.imageUrl != null && image == null -> EmptyCard(stringResource(R.string.pos_loading))
            else -> EmptyCard(stringResource(detail.missing.messageRes(R.string.pos_epc_image_missing)))
        }
    }
}

@Composable
private fun EpcPartsList(
    detail: EpcDiagramDetail,
    callouts: Map<String, Int>,
    activeOem: String?,
    dispatch: (PosIntent) -> Unit,
    modifier: Modifier,
) {
    val palette = PosTheme.palette
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PosText(stringResource(R.string.pos_epc_parts_count, detail.parts.size), PosTheme.type.bodySecondary, palette.textMuted)
        if (detail.parts.isEmpty()) EmptyCard(stringResource(detail.missing.messageRes(R.string.pos_epc_empty)))
        detail.parts.forEach { p ->
            val key = p.oemPartNumber.trim().uppercase()
            val on = key == activeOem?.trim()?.uppercase()
            val n = callouts[key]
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .clip(PosTheme.shape.md)
                    .background(if (on) palette.surfaceElevated else palette.surfacePrimary)
                    .border(if (on) 2.dp else 1.5.dp, if (on) palette.brandRed else palette.brandRed.copy(alpha = 0.7f), PosTheme.shape.md)
                    .clickable(role = Role.Button) { dispatch(PosSaleIntent.EpcSelect(p.oemPartNumber)) }
                    .semantics { selected = on }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PosText(
                    n?.toString()?.padStart(2, '0') ?: "–",
                    PosTheme.type.labelAction.copy(fontWeight = FontWeight.Bold),
                    if (n != null) palette.brandRed else palette.textMuted,
                    maxLines = 1,
                    modifier = Modifier.widthIn(min = 24.dp),
                )
                Column(Modifier.weight(1f)) {
                    PosText(p.name, PosTheme.type.labelAction.copy(fontWeight = FontWeight.SemiBold), palette.textPrimary, maxLines = 2)
                    PosText(
                        listOfNotNull(p.oemPartNumber, p.pncCode?.let { stringResource(R.string.pos_epc_pnc, it) }).joinToString(" · "),
                        PosTheme.type.labelMeta,
                        palette.textMuted,
                        maxLines = 2,
                    )
                }
                val pin = PopularPin.forPart(CatalogPart(null, p.oemPartNumber, p.name, null, null, null))
                Box(
                    Modifier.size(40.dp).clip(PosTheme.shape.sm).clickable(role = Role.Button) { dispatch(PosIntent.Pin(pin)) },
                    contentAlignment = Alignment.Center,
                ) {
                    PosIcon(PosIcons.Pin, tint = palette.textSecondary, size = 16.dp, contentDescription = stringResource(R.string.pos_pin))
                }
                SoftButton(stringResource(R.string.pos_add), null, enabled = true, onClick = { dispatch(PosSaleIntent.EpcAdd(p)) }, modifier = Modifier.width(76.dp))
            }
        }
    }
}

/** Fail-closed live-catalogue states in the cashier's words. */
private fun EpcMissing?.messageRes(fallback: Int): Int = when (this) {
    EpcMissing.Publishing -> R.string.pos_catalog_publishing
    EpcMissing.NotConnected -> R.string.pos_catalog_not_connected
    EpcMissing.Unavailable, null -> fallback
}

