package co.zw.nissangtr.pos.till

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.zw.nissangtr.pos.api.TicketCta
import co.zw.nissangtr.pos.api.TicketLine
import co.zw.nissangtr.pos.api.TicketSnapshot
import co.zw.nissangtr.ui.theme.GtrColors
import co.zw.nissangtr.ui.theme.GtrShapes
import java.util.Locale

@Composable
fun TicketPane(
    ticket: TicketSnapshot,
    onPark: () -> Unit,
    onVoid: () -> Unit,
    onPay: () -> Unit,
    modifier: Modifier = Modifier,
    compactBar: Boolean = false,
) {
    if (compactBar) {
        CompactTicketBar(
            ticket = ticket,
            onPark = onPark,
            onVoid = onVoid,
            onPay = onPay,
            modifier = modifier,
        )
        return
    }
    Column(
        modifier = modifier
            .background(GtrColors.SteelLift, GtrShapes.small)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Ticket",
            style = MaterialTheme.typography.titleMedium,
            color = GtrColors.Chalk,
        )
        ticket.lines.forEach { line ->
            TicketLineRow(line = line)
        }
        Spacer(modifier = Modifier.weight(1f, fill = true))
        if (ticket.cta == TicketCta.QUOTE) {
            Text(
                text = "Remove quote-only lines to take payment.",
                style = MaterialTheme.typography.labelSmall,
                color = GtrColors.Warning,
            )
        }
        TicketActionsRow(onPark = onPark, onVoid = onVoid)
        TicketTotals(ticket = ticket)
        PayButton(ticket = ticket, onPay = onPay)
    }
}

@Composable
private fun CompactTicketBar(
    ticket: TicketSnapshot,
    onPark: () -> Unit,
    onVoid: () -> Unit,
    onPay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(GtrColors.SteelLift)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${ticket.itemCount} item(s)",
                color = GtrColors.Silver,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = String.format(
                    Locale.US,
                    "%s %.2f",
                    ticket.currency,
                    ticket.subtotal,
                ),
                color = GtrColors.Usd,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onPark) {
                Text("PARK", color = GtrColors.Silver)
            }
            TextButton(onClick = onVoid) {
                Text("VOID", color = GtrColors.Primary)
            }
        }
        PayButton(ticket = ticket, onPay = onPay)
    }
}

@Composable
private fun TicketLineRow(line: TicketLine) {
    val indent = if (line.isCoreCharge) 12.dp else 0.dp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = indent),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (line.isCoreCharge) {
                    "↳ ${line.description}"
                } else {
                    line.oemPartNumber
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (line.isCoreCharge) GtrColors.SilverDim else GtrColors.Chalk,
            )
            if (!line.isCoreCharge) {
                Text(
                    text = line.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = GtrColors.Silver,
                )
            }
        }
        Text(
            text = String.format(Locale.US, "%.2f", line.unitPrice * line.qty),
            style = MaterialTheme.typography.bodyMedium,
            color = GtrColors.Silver,
        )
    }
}

@Composable
fun TicketActionsRow(
    onPark: () -> Unit,
    onVoid: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(
            onClick = onPark,
            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            shape = GtrShapes.small,
        ) {
            Text("PARK", color = GtrColors.Chalk)
        }
        OutlinedButton(
            onClick = onVoid,
            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            shape = GtrShapes.small,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = GtrColors.Primary),
        ) {
            Text("VOID", color = GtrColors.Primary)
        }
    }
}

@Composable
fun TicketTotals(
    ticket: TicketSnapshot,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Subtotal · ${ticket.itemCount} item(s)",
                color = GtrColors.Silver,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = String.format(Locale.US, "%s %.2f", ticket.currency, ticket.subtotal),
                color = GtrColors.Chalk,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        // No tax row — hard exclusion.
    }
}

@Composable
fun PayButton(
    ticket: TicketSnapshot,
    onPay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = when (ticket.cta) {
        TicketCta.PAY -> String.format(
            Locale.US,
            "PAY %s %.2f",
            ticket.currency,
            ticket.subtotal,
        )
        TicketCta.QUOTE -> String.format(
            Locale.US,
            "QUOTE %s %.2f",
            ticket.currency,
            ticket.subtotal,
        )
    }
    Button(
        onClick = onPay,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        shape = GtrShapes.small,
        colors = ButtonDefaults.buttonColors(
            containerColor = GtrColors.Primary,
            contentColor = GtrColors.PrimaryInk,
        ),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}
