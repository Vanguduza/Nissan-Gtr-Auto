package co.zw.nissangtr.pos.ui.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import co.zw.nissangtr.pos.design.icons.PosIcons
import co.zw.nissangtr.pos.design.primitives.GlassTreatment
import co.zw.nissangtr.pos.design.primitives.Money
import co.zw.nissangtr.pos.design.primitives.PosCurrency
import co.zw.nissangtr.pos.design.primitives.PosGlassSurface
import co.zw.nissangtr.pos.design.primitives.PosMoneyFormatter
import co.zw.nissangtr.pos.design.primitives.PosSkeletonCard
import co.zw.nissangtr.pos.design.primitives.PosSkeletonRow
import co.zw.nissangtr.pos.design.primitives.posFocusRing
import co.zw.nissangtr.pos.design.theme.PosDensity
import co.zw.nissangtr.pos.design.theme.PosTheme

/**
 * Component Gallery for Roborazzi screenshot certification (Blueprint §11.3, §11.6).
 * Tests all 20 SYS features across sizes, schemes, and glass capabilities.
 */
@Composable
fun ComponentGallery(
    glassTreatment: GlassTreatment = GlassTreatment.Auto,
    modifier: Modifier = Modifier,
) {
    val palette = PosTheme.palette
    val type = PosTheme.type
    val space = PosTheme.space
    val shape = PosTheme.shape
    val elevation = PosTheme.elevation

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(palette.canvas)
            .verticalScroll(rememberScrollState())
            .padding(space.space5),
        verticalArrangement = Arrangement.spacedBy(space.space6),
    ) {
        // 1. Header Section
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "Nissan GTR Auto — POS Design Gallery",
                    style = type.heading1,
                    color = palette.textPrimary,
                )
                Text(
                    text = "Class: ${PosTheme.geometry.windowClass.name} | Density: ${PosTheme.density.name}",
                    style = type.bodySecondary,
                    color = palette.textMuted,
                )
            }
            // Brand logo pill
            Box(
                modifier = Modifier
                    .clip(shape.pill)
                    .background(palette.brandRed)
                    .padding(horizontal = space.space4, vertical = space.space2),
            ) {
                Text(
                    text = "GENUINE PARTS",
                    style = type.labelAction,
                    color = palette.textOnBrand,
                )
            }
        }

        // 2. Semantic Palette Swatches (SYS-03, SYS-04, SYS-05)
        Text(text = "Semantic Palette (SYS-03, SYS-04, SYS-05)", style = type.heading2, color = palette.textPrimary)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(space.space2),
        ) {
            PaletteSwatch(label = "Brand", color = palette.brandRed, onColor = palette.textOnBrand)
            PaletteSwatch(label = "CTA Hover", color = palette.brandRedPressed, onColor = palette.textOnBrand)
            PaletteSwatch(label = "Canvas", color = palette.canvas, onColor = palette.textPrimary)
            PaletteSwatch(label = "Surface", color = palette.surfacePrimary, onColor = palette.textPrimary)
            PaletteSwatch(label = "Nav", color = palette.navBackground, onColor = palette.textOnBrand)
            PaletteSwatch(label = "Success", color = palette.success, onColor = palette.textOnBrand)
            PaletteSwatch(label = "Warning", color = palette.warning, onColor = palette.textOnBrand)
            PaletteSwatch(label = "Error (D-011)", color = palette.error, onColor = palette.textOnBrand)
            PaletteSwatch(label = "Unknown", color = palette.unknown, onColor = palette.textOnBrand)
            PaletteSwatch(label = "Offline", color = palette.offline, onColor = palette.textOnBrand)
        }

        // 3. Status Family Pills (SYS-05)
        Text(text = "Status Families with Paired Icons (SYS-05)", style = type.heading2, color = palette.textPrimary)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(space.space3),
        ) {
            StatusPill(label = "IN STOCK", color = palette.success, icon = PosIcons.Check)
            StatusPill(label = "LOW STOCK", color = palette.warning, icon = PosIcons.AlertTriangle)
            StatusPill(label = "ERROR #8E0F22", color = palette.error, icon = PosIcons.AlertCircle)
            StatusPill(label = "AMBIGUOUS", color = palette.unknown, icon = PosIcons.HelpCircle)
            StatusPill(label = "OFFLINE CASH", color = palette.offline, icon = PosIcons.WifiOff)
        }

        // 4. Typography Hierarchy (SYS-09)
        Text(text = "Typography Roles & Tabular Numerics (SYS-09)", style = type.heading2, color = palette.textPrimary)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape.md)
                .background(palette.surfacePrimary)
                .border(elevation.borderSubtleWidth, palette.borderSubtle, shape.md)
                .padding(space.space4),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(space.space2)) {
                Text(text = "display.hero: FAST NISSAN SPARES", style = type.displayHero, color = palette.textPrimary)
                Text(text = "heading.1: Cart & Order Breakdown", style = type.heading1, color = palette.textPrimary)
                Text(text = "heading.2: Popular Quick Access Spares", style = type.heading2, color = palette.textPrimary)
                Text(text = "heading.3: Oil Filter Assembly GTR R35", style = type.heading3, color = palette.textPrimary)
                Text(text = "body.primary: Genuine OEM Nissan engine filter element", style = type.bodyPrimary, color = palette.textPrimary)
                Text(text = "body.secondary (ss02): Part 15208-65F0A · Fitment RB26DETT", style = type.bodySecondary, color = palette.textSecondary)
                Text(text = "label.action: ADD TO BASKET", style = type.labelAction, color = palette.brandRed)
                Text(text = "label.meta: Shift Counter #2 · Operator John M.", style = type.labelMeta, color = palette.textMuted)
                Text(
                    text = "numeric.price (tnum): " + PosMoneyFormatter.format(Money.fromMajor(129.50, PosCurrency.USD)),
                    style = type.numericPrice,
                    color = palette.textPrimary,
                )
                Text(
                    text = "numeric.total (tnum): " + PosMoneyFormatter.format(Money.fromMajor(4850.00, PosCurrency.ZIG)),
                    style = type.numericTotal,
                    color = palette.textPrimary,
                )
                Text(text = "mono.reference: TXN-REF-77A920B-ZW", style = type.monoReference, color = palette.textSecondary)
            }
        }

        // 5. Vendored Lucide Icons Grid (SYS-10)
        Text(text = "Vendored Lucide Icons (SYS-10)", style = type.heading2, color = palette.textPrimary)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(space.space4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf(
                PosIcons.Search to "Search",
                PosIcons.Scan to "Scan",
                PosIcons.ShoppingCart to "Cart",
                PosIcons.Car to "Car",
                PosIcons.Wrench to "Wrench",
                PosIcons.Package to "Package",
                PosIcons.Printer to "Printer",
                PosIcons.User to "User",
                PosIcons.Lock to "Lock",
                PosIcons.Tag to "Tag",
                PosIcons.Trash2 to "Trash",
            ).forEach { (icon, name) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(shape.sm)
                            .background(palette.surfacePrimary)
                            .border(elevation.borderSubtleWidth, palette.borderSubtle, shape.sm),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = name,
                            tint = palette.textPrimary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(modifier = Modifier.height(space.space1))
                    Text(text = name, style = type.labelMeta, color = palette.textMuted)
                }
            }
        }

        // 6. Glass Capability Pair (SYS-20)
        Text(text = "Glass Surface Capability Pair (SYS-20)", style = type.heading2, color = palette.textPrimary)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(space.space4),
        ) {
            PosGlassSurface(
                modifier = Modifier
                    .weight(1f)
                    .height(110.dp)
                    .padding(space.space1),
                treatment = glassTreatment,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(space.space4),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "Treatment: ${glassTreatment.name}",
                        style = type.heading3,
                        color = palette.textPrimary,
                    )
                    Text(
                        text = "Backdrop blur (API 31+) or 94% opaque fallback (pre-31)",
                        style = type.bodySecondary,
                        color = palette.textSecondary,
                    )
                }
            }

            // Visible Focus Ring Demonstration (SYS-19)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(110.dp)
                    .clip(shape.md)
                    .background(palette.surfacePrimary)
                    .posFocusRing(shape = shape.md, isFocusedOverride = true)
                    .padding(space.space4),
            ) {
                Column(verticalArrangement = Arrangement.Center, modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = "Focus Contract (SYS-19)",
                        style = type.heading3,
                        color = palette.textPrimary,
                    )
                    Text(
                        text = "Mandatory 1.5 dp visible focus ring (borderFocus)",
                        style = type.bodySecondary,
                        color = palette.textSecondary,
                    )
                }
            }
        }

        // 7. Skeletons Matching Exact Geometry (SYS-21)
        Text(text = "Skeletons Matching Exact Geometry (SYS-21)", style = type.heading2, color = palette.textPrimary)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(space.space3),
        ) {
            PosSkeletonCard(width = 180.dp, height = 110.dp)
            PosSkeletonCard(width = 180.dp, height = 110.dp)
            PosSkeletonCard(width = 180.dp, height = 110.dp)
        }
        PosSkeletonRow(density = PosDensity.Operational)
    }
}

@Composable
private fun PaletteSwatch(
    label: String,
    color: Color,
    onColor: Color,
    modifier: Modifier = Modifier,
) {
    val shape = PosTheme.shape.sm
    val borderSubtle = PosTheme.palette.borderSubtle
    val borderWidth = PosTheme.elevation.borderSubtleWidth

    Column(
        modifier = modifier.width(72.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp, 44.dp)
                .clip(shape)
                .background(color)
                .border(borderWidth, borderSubtle, shape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label.take(6),
                style = PosTheme.type.labelMeta,
                color = onColor,
            )
        }
        Spacer(modifier = Modifier.height(PosTheme.space.space1))
        Text(
            text = label,
            style = PosTheme.type.labelMeta,
            color = PosTheme.palette.textSecondary,
            maxLines = 1,
        )
    }
}

@Composable
private fun StatusPill(
    label: String,
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
) {
    val shape = PosTheme.shape.pill
    val space = PosTheme.space

    Row(
        modifier = modifier
            .clip(shape)
            .background(color.copy(alpha = 0.12f))
            .border(PosTheme.elevation.borderSubtleWidth, color, shape)
            .padding(horizontal = space.space3, vertical = space.space1),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space.space1),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = label,
            style = PosTheme.type.labelMeta,
            color = color,
        )
    }
}
