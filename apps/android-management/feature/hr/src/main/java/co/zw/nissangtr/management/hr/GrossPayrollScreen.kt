package co.zw.nissangtr.management.hr

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import co.zw.nissangtr.management.rpc.PayrollLineSummary
import co.zw.nissangtr.management.rpc.RpcClient
import co.zw.nissangtr.ui.shop.ShopPrimaryButton
import co.zw.nissangtr.ui.shop.ShopSecondaryButton
import co.zw.nissangtr.ui.shop.ShopStaffPanel
import co.zw.nissangtr.ui.shop.ShopStaffScreen

/**
 * Gross payroll desk: period hours, open lines (gross / deductions / net),
 * and manual deduction add. No PAYE / NSSA / statutory tax UI.
 */
@Composable
fun GrossPayrollScreen(
    rpc: RpcClient,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GrossPayrollViewModel = viewModel(
        factory = GrossPayrollViewModel.factory(rpc),
    ),
) {
    val state by viewModel.state.collectAsState()

    ShopStaffScreen(
        title = "HR — Gross payroll",
        subtitle = "Hours · lines · manual deductions",
        modifier = modifier,
        onBack = onBack,
    ) {
        Text(
            "Payslip math is gross − manual deductions only. No PAYE / NSSA / tax brackets.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        ShopStaffPanel(title = "Hours in period") {
            OutlinedTextField(
                value = state.employeeId,
                onValueChange = viewModel::onEmployeeIdChange,
                label = { Text("Employee UUID") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.busy,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.periodStart,
                    onValueChange = viewModel::onPeriodStartChange,
                    label = { Text("From (YYYY-MM-DD)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    enabled = !state.busy,
                )
                OutlinedTextField(
                    value = state.periodEnd,
                    onValueChange = viewModel::onPeriodEndChange,
                    label = { Text("To (YYYY-MM-DD)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    enabled = !state.busy,
                )
            }
            ShopPrimaryButton(
                label = "Look up hours",
                onClick = viewModel::lookupHours,
                enabled = !state.busy,
            )
            state.hours?.let {
                Text(
                    "%.2f hours in range".format(it),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        ShopStaffPanel(title = "Open payroll lines") {
            if (state.bootLoading) {
                Text("Loading…", style = MaterialTheme.typography.bodyMedium)
            } else if (state.lines.isEmpty()) {
                Text(
                    "No payroll lines visible under RLS.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                state.lines.forEach { line ->
                    PayrollLineRow(
                        line = line,
                        selected = line.id == state.selectedLineId,
                        onClick = { viewModel.selectLine(line.id) },
                    )
                }
            }
            ShopSecondaryButton(
                label = "Refresh lines",
                onClick = viewModel::refreshLines,
                enabled = !state.busy,
            )
        }

        ShopStaffPanel(title = "Manual deduction") {
            if (state.selectedLineId.isNotBlank()) {
                Text(
                    "Line ${state.selectedLineId.take(8)}…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            state.deductions.forEach { d ->
                Text(
                    "• ${d.label}: ${"%.2f".format(d.amount)}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            OutlinedTextField(
                value = state.deductionLabel,
                onValueChange = viewModel::onDeductionLabelChange,
                label = { Text("Label (e.g. Staff advance)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.busy,
            )
            OutlinedTextField(
                value = state.deductionAmount,
                onValueChange = viewModel::onDeductionAmountChange,
                label = { Text("Amount") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.busy,
            )
            ShopPrimaryButton(
                label = "Add deduction",
                onClick = viewModel::addDeduction,
                enabled = !state.busy && state.selectedLineId.isNotBlank(),
            )
        }

        state.message?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium)
        }
        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        ShopSecondaryButton(label = "Back", onClick = onBack)
    }
}

@Composable
private fun PayrollLineRow(
    line: PayrollLineSummary,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
    ) {
        Text(
            "${line.id.take(8)}… · emp ${line.employeeId.take(8)}…",
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
        Text(
            "gross ${"%.2f".format(line.grossAmount)} − ded ${"%.2f".format(line.deductionsAmount)} " +
                "= net ${"%.2f".format(line.netAmount)} ${line.currency.rpcValue}" +
                " · ${"%.1f".format(line.hoursWorked)} h",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
