package co.zw.nissangtr.pos.till

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.zw.nissangtr.ui.theme.GtrColors
import co.zw.nissangtr.ui.theme.GtrShapes

/**
 * Counter returns → [post_pos_refund]. Quarantine routing is server-side.
 * Never direct exchange / WH2 restock from this UI.
 */
@Composable
fun ReturnsSheet(
    busy: Boolean = false,
    error: String? = null,
    onSubmit: (invoiceId: String, reason: String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var invoiceId by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    Column(
        modifier = modifier
            .widthIn(max = 420.dp)
            .fillMaxWidth()
            .background(GtrColors.SteelLift, GtrShapes.medium)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Return → quarantine",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = GtrColors.Chalk,
        )
        Text(
            text = "Posts post_pos_refund. Stock goes to quarantine WH — not WH2 restock or exchange.",
            style = MaterialTheme.typography.bodySmall,
            color = GtrColors.SilverDim,
        )
        OutlinedTextField(
            value = invoiceId,
            onValueChange = { invoiceId = it },
            label = { Text("Invoice id") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = returnsFieldColors(),
        )
        OutlinedTextField(
            value = reason,
            onValueChange = { reason = it },
            label = { Text("Reason") },
            singleLine = false,
            modifier = Modifier.fillMaxWidth(),
            colors = returnsFieldColors(),
        )
        error?.let {
            Text(it, color = GtrColors.Danger, style = MaterialTheme.typography.bodySmall)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = GtrColors.Silver)
            }
            Button(
                onClick = { onSubmit(invoiceId.trim(), reason.trim()) },
                enabled = !busy && invoiceId.isNotBlank() && reason.isNotBlank(),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
                shape = GtrShapes.small,
                colors = ButtonDefaults.buttonColors(
                    containerColor = GtrColors.Primary,
                    contentColor = GtrColors.PrimaryInk,
                ),
            ) {
                Text("Post refund", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun returnsFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = GtrColors.Chalk,
    unfocusedTextColor = GtrColors.Silver,
    focusedBorderColor = GtrColors.Primary,
    unfocusedBorderColor = GtrColors.SilverDim,
    focusedLabelColor = GtrColors.Silver,
    unfocusedLabelColor = GtrColors.SilverDim,
    cursorColor = GtrColors.Primary,
)
