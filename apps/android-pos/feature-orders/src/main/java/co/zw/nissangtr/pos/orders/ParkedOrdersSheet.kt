package co.zw.nissangtr.pos.orders

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.zw.nissangtr.pos.api.MoneyCents
import co.zw.nissangtr.pos.api.ParkedCartRef
import co.zw.nissangtr.pos.api.QuotationRef
import co.zw.nissangtr.ui.theme.GtrColors
import co.zw.nissangtr.ui.theme.GtrShapes

@Composable
fun ParkedOrdersSheet(
    parked: List<ParkedCartRef>,
    quotations: List<QuotationRef> = emptyList(),
    onResume: (ParkedCartRef) -> Unit,
    onOpenQuotation: (QuotationRef) -> Unit = {},
    onReturns: () -> Unit = {},
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
            text = "Orders",
            style = MaterialTheme.typography.titleMedium,
            color = GtrColors.Chalk,
            fontWeight = FontWeight.Bold,
        )
        Text("Parked carts", color = GtrColors.Silver, style = MaterialTheme.typography.labelLarge)
        if (parked.isEmpty()) {
            Text("No parked carts", color = GtrColors.SilverDim)
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 160.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(parked, key = { it.cartId }) { ref ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(GtrColors.Steel, GtrShapes.small)
                        .clickable { onResume(ref) }
                        .padding(12.dp),
                ) {
                    Text(ref.label, color = GtrColors.Chalk)
                    Text(
                        text = "${ref.currency} ${MoneyCents.centsToMajorString(ref.subtotalCents)}",
                        color = GtrColors.Usd,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        Text("Quotations", color = GtrColors.Silver, style = MaterialTheme.typography.labelLarge)
        if (quotations.isEmpty()) {
            Text("No quotations", color = GtrColors.SilverDim)
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 160.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(quotations, key = { it.id }) { qt ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(GtrColors.Steel, GtrShapes.small)
                        .clickable { onOpenQuotation(qt) }
                        .padding(12.dp),
                ) {
                    Text(qt.documentNumber, color = GtrColors.Chalk)
                    Text("Open QT-", color = GtrColors.SilverDim, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        TextButton(onClick = onReturns) {
            Text("Return → quarantine", color = GtrColors.Primary)
        }
        TextButton(onClick = onDismiss) {
            Text("Close", color = GtrColors.Silver)
        }
    }
}
