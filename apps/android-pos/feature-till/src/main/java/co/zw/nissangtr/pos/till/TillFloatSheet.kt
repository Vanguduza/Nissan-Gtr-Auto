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
import co.zw.nissangtr.pos.api.TillFloatRules
import co.zw.nissangtr.ui.theme.GtrColors
import co.zw.nissangtr.ui.theme.GtrShapes

/**
 * Open / close cash-sales till float (CoA **1120**). Never 1110 petty.
 */
@Composable
fun TillFloatSheet(
    openPeriodId: String?,
    openingBalanceHint: String = "0.00",
    busy: Boolean = false,
    error: String? = null,
    onOpen: (openingBalance: Double) -> Unit,
    onClose: (physicalCount: Double) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var amountText by remember { mutableStateOf(openingBalanceHint) }
    val isOpen = !openPeriodId.isNullOrBlank()
    Column(
        modifier = modifier
            .widthIn(max = 420.dp)
            .fillMaxWidth()
            .background(GtrColors.SteelLift, GtrShapes.medium)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = if (isOpen) "Close till float" else "Open till float",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = GtrColors.Chalk,
        )
        Text(
            text = "Account ${TillFloatRules.CASH_SALES_TILL} Cash Sales Till · not ${TillFloatRules.PETTY_CASH} petty",
            style = MaterialTheme.typography.bodySmall,
            color = GtrColors.SilverDim,
        )
        OutlinedTextField(
            value = amountText,
            onValueChange = { amountText = it },
            label = {
                Text(if (isOpen) "Physical count" else "Opening balance")
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = floatFieldColors(),
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
                onClick = {
                    val major = amountText.trim().toDoubleOrNull()
                    if (major == null) return@Button
                    if (isOpen) onClose(major) else onOpen(major)
                },
                enabled = !busy && amountText.trim().toDoubleOrNull() != null,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
                shape = GtrShapes.small,
                colors = ButtonDefaults.buttonColors(
                    containerColor = GtrColors.Primary,
                    contentColor = GtrColors.PrimaryInk,
                ),
            ) {
                Text(
                    if (isOpen) "Close period" else "Open period",
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun floatFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = GtrColors.Chalk,
    unfocusedTextColor = GtrColors.Silver,
    focusedBorderColor = GtrColors.Primary,
    unfocusedBorderColor = GtrColors.SilverDim,
    focusedLabelColor = GtrColors.Silver,
    unfocusedLabelColor = GtrColors.SilverDim,
    cursorColor = GtrColors.Primary,
)
