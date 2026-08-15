package co.zw.nissangtr.pos.lookup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.zw.nissangtr.pos.api.FitmentBadge
import co.zw.nissangtr.pos.api.TillItem
import co.zw.nissangtr.ui.theme.GtrColors
import co.zw.nissangtr.ui.theme.GtrShapes
import java.util.Locale

@Composable
fun SpareTile(
    item: TillItem,
    badge: FitmentBadge,
    selected: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimmed = badge == FitmentBadge.NO_FIT
    val borderColor = when {
        selected -> GtrColors.Primary
        else -> GtrColors.SilverDim.copy(alpha = 0.35f)
    }
    Column(
        modifier = modifier
            .heightIn(min = 48.dp)
            .background(GtrColors.SteelLift, GtrShapes.small)
            .border(1.dp, borderColor, GtrShapes.small)
            .clickable(enabled = !dimmed, onClick = onClick)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = item.oemPartNumber,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (dimmed) GtrColors.SilverDim else GtrColors.Chalk,
        )
        Text(
            text = item.description,
            style = MaterialTheme.typography.bodySmall,
            color = GtrColors.Silver,
            maxLines = 2,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val price = item.unitPrice
            val priceLabel = when {
                price == null || price <= 0.0 -> "Needs price"
                else -> String.format(
                    Locale.US,
                    "%s %.2f",
                    item.currency,
                    price,
                )
            }
            Text(
                text = priceLabel,
                style = MaterialTheme.typography.labelLarge,
                color = if (price == null || price <= 0.0) {
                    GtrColors.Warning
                } else {
                    GtrColors.Usd
                },
            )
            FitmentChip(badge = badge)
        }
        Text(
            text = "WH2 ${item.saleableQty}" +
                (item.binCode?.let { " · $it" } ?: ""),
            style = MaterialTheme.typography.labelSmall,
            color = GtrColors.SilverDim,
        )
    }
}

@Composable
fun FitmentChip(
    badge: FitmentBadge,
    modifier: Modifier = Modifier,
) {
    val (label, fg, bg) = when (badge) {
        FitmentBadge.FITS -> Triple("FITS", GtrColors.Chalk, GtrColors.StockIn)
        FitmentBadge.VERIFY -> Triple("VERIFY", GtrColors.Chalk, GtrColors.Warning)
        FitmentBadge.NO_FIT -> Triple("NO FIT", GtrColors.Chalk, GtrColors.Danger)
    }
    Text(
        text = label,
        modifier = modifier
            .background(bg, GtrShapes.extraSmall)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        style = MaterialTheme.typography.labelSmall,
        color = fg,
    )
}

@Composable
fun FacetChipsRow(
    categories: List<String>,
    selectedCategory: String?,
    inStockOnly: Boolean,
    onCategory: (String?) -> Unit,
    onInStockToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "Category",
            style = MaterialTheme.typography.labelSmall,
            color = GtrColors.SilverDim,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            categories.forEach { cat ->
                val selected = cat == selectedCategory
                FacetChip(
                    label = cat,
                    selected = selected,
                    onClick = { onCategory(if (selected) null else cat) },
                )
            }
            FacetChip(
                label = if (inStockOnly) "In stock" else "All stock",
                selected = inStockOnly,
                onClick = onInStockToggle,
                selectedColor = GtrColors.Accent,
            )
        }
    }
}

@Composable
private fun FacetChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    selectedColor: Color = GtrColors.Primary,
) {
    Text(
        text = label,
        modifier = Modifier
            .background(
                if (selected) selectedColor else GtrColors.SteelLift,
                GtrShapes.small,
            )
            .border(
                1.dp,
                if (selected) selectedColor else GtrColors.SilverDim.copy(alpha = 0.4f),
                GtrShapes.small,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        style = MaterialTheme.typography.labelMedium,
        color = if (selected) GtrColors.PrimaryInk else GtrColors.Silver,
    )
}
