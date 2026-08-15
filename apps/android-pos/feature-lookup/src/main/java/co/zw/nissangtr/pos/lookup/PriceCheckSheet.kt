package co.zw.nissangtr.pos.lookup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.zw.nissangtr.pos.api.FitmentBadge
import co.zw.nissangtr.pos.api.TillItem
import co.zw.nissangtr.ui.theme.GtrColors
import co.zw.nissangtr.ui.theme.GtrShapes

/**
 * Price-check utility — lookup only; never mutates the cart (no add_cart_line).
 */
@Composable
fun PriceCheckSheet(
    query: String,
    item: TillItem?,
    badge: FitmentBadge?,
    onQueryChange: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(GtrColors.SteelLift, GtrShapes.medium)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "Price check",
            style = MaterialTheme.typography.titleMedium,
            color = GtrColors.Chalk,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Lookup only — does not add to ticket",
            style = MaterialTheme.typography.labelSmall,
            color = GtrColors.SilverDim,
        )
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text("OEM / OE") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = GtrColors.Chalk,
                unfocusedTextColor = GtrColors.Chalk,
                focusedBorderColor = GtrColors.Primary,
                unfocusedBorderColor = GtrColors.SilverDim,
                focusedLabelColor = GtrColors.Silver,
                unfocusedLabelColor = GtrColors.SilverDim,
                cursorColor = GtrColors.Primary,
            ),
        )
        if (item != null) {
            Text(item.oemPartNumber, color = GtrColors.Chalk, fontWeight = FontWeight.SemiBold)
            Text(item.description, color = GtrColors.Silver)
            val price = item.unitPrice
            Text(
                text = when {
                    price == null || price <= 0.0 -> "Needs price"
                    else -> "${item.currency} ${"%.2f".format(price)}"
                },
                color = GtrColors.Usd,
            )
            Text(
                text = "WH2 ${item.saleableQty}" + (item.binCode?.let { " · $it" } ?: ""),
                color = GtrColors.SilverDim,
            )
            badge?.let { FitmentChip(badge = it) }
            item.supersededBy?.let {
                Text("Superseded → use $it", color = GtrColors.Warning)
            }
        } else if (query.isNotBlank()) {
            Text("No match", color = GtrColors.SilverDim)
        }
        TextButton(onClick = onDismiss) {
            Text("Close", color = GtrColors.Silver)
        }
    }
}
