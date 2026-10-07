package co.zw.nissangtr.delivery.jobs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import co.zw.nissangtr.delivery.design.Slopes
import co.zw.nissangtr.delivery.design.SlopesBanner
import co.zw.nissangtr.delivery.design.SlopesGroup
import co.zw.nissangtr.delivery.design.SlopesPrimaryButton
import co.zw.nissangtr.delivery.design.SlopesRow
import co.zw.nissangtr.delivery.design.SlopesSectionHeader
import co.zw.nissangtr.delivery.design.SlopesTextField
import co.zw.nissangtr.delivery.design.SlopesTone
import co.zw.nissangtr.delivery.rpc.DriverCash
import java.time.Instant

/**
 * "Cash to hand in": what the driver still holds per currency, a count-and-hand-in form, and the
 * last hand-ins with what the cashier counted. Stateless apart from the form text.
 */
@Composable
fun DriverCashContent(
    cash: DriverCash?,
    busy: Boolean,
    error: String?,
    onHandIn: (currency: String, declared: String, notes: String) -> Unit,
    now: Instant = Instant.now(),
) {
    val c = Slopes.colors
    if (cash == null) {
        Text("Loading…", style = MaterialTheme.typography.bodyMedium, color = c.secondaryLabel, modifier = Modifier.padding(20.dp))
        return
    }
    if (cash.holding.isEmpty()) {
        SlopesBanner(
            if (cash.handins.any { it.status == "submitted" }) "Your cash is with the cashier to count." else "You are not holding any delivery cash.",
            icon = Icons.Filled.CheckCircle,
            tone = SlopesTone.Success,
        )
    }
    cash.holding.forEach { h ->
        val waiting = cash.waiting(h.currency)
        var declared by rememberSaveable(h.currency, h.amount) { mutableStateOf(String.format(java.util.Locale.US, "%.2f", h.amount)) }
        var notes by rememberSaveable(h.currency) { mutableStateOf("") }
        SlopesSectionHeader("${h.currency.uppercase()} held ${DriverCashGate.age(h, now).orEmpty()}".trim())
        SlopesGroup {
            SlopesRow(
                DriverCashGate.money(h.amount, h.currency),
                subtitle = "${h.count} collection${if (h.count == 1) "" else "s"} not handed in",
                leading = { co.zw.nissangtr.delivery.design.SlopesIconBadge(Icons.Filled.Payments) },
                divider = h.collections.isNotEmpty(),
            )
            h.collections.forEachIndexed { i, col ->
                SlopesRow(
                    listOfNotNull(col.invoiceNumber, col.jobNumber).joinToString(" · ").ifBlank { "Delivery" },
                    trailing = { Text(DriverCashGate.money(col.amount, h.currency), style = MaterialTheme.typography.bodyMedium, color = c.secondaryLabel) },
                    divider = i < h.collections.lastIndex,
                )
            }
        }
        Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
            if (waiting != null) {
                SlopesBanner(DriverCashGate.blockedReason(cash, h.currency).orEmpty(), tone = SlopesTone.Warning, inset = 0.dp)
            } else {
                SlopesTextField(
                    declared,
                    { declared = it },
                    label = "Cash you are handing in (count it)",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                Spacer(Modifier.height(10.dp))
                SlopesTextField(notes, { notes = it }, label = "Note for the cashier (optional)", placeholder = "e.g. one note is torn")
                val differs = DriverCashGate.parseDeclared(declared)?.let { kotlin.math.abs(it - h.amount) > 0.009 } == true
                if (differs) {
                    Spacer(Modifier.height(10.dp))
                    SlopesBanner(
                        "That is not what the app recorded (${DriverCashGate.money(h.amount, h.currency)}). Recount, or explain in the note — a manager reviews any difference.",
                        icon = Icons.Filled.ReportProblem,
                        tone = SlopesTone.Warning,
                        inset = 0.dp,
                    )
                }
                Spacer(Modifier.height(14.dp))
                SlopesPrimaryButton(
                    if (busy) "Handing in…" else "Hand in ${h.currency.uppercase()} cash",
                    { onHandIn(h.currency, declared, notes) },
                    enabled = !busy && DriverCashGate.parseDeclared(declared) != null,
                    icon = Icons.Filled.Payments,
                )
            }
        }
    }
    error?.let {
        SlopesBanner(it, icon = Icons.Filled.ReportProblem, tone = SlopesTone.Danger)
        Spacer(Modifier.height(10.dp))
    }
    if (cash.handins.isNotEmpty()) {
        SlopesSectionHeader("Recent hand-ins")
        SlopesGroup {
            cash.handins.take(5).forEachIndexed { i, h ->
                SlopesRow(
                    "${h.documentNumber ?: "Hand-in"} · ${DriverCashGate.money(h.declaredAmount, h.currency)}",
                    subtitle = DriverCashGate.statusLabel(h),
                    titleColor = if (h.status == "variance_pending") c.danger else null,
                    divider = i < minOf(cash.handins.size, 5) - 1,
                )
            }
        }
    }
}
