package co.zw.nissangtr.management.hr

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import co.zw.nissangtr.management.rpc.AttendanceEventType
import co.zw.nissangtr.management.rpc.RpcClient
import co.zw.nissangtr.ui.shop.ShopPrimaryButton
import co.zw.nissangtr.ui.shop.ShopSecondaryButton
import co.zw.nissangtr.ui.shop.ShopStaffPanel
import co.zw.nissangtr.ui.shop.ShopStaffScreen

/**
 * HR clock in/out on ShopKit staff shell. Calls [RpcNames.CLOCK_ATTENDANCE] via [RpcClient].
 * No PAYE / NSSA / statutory tax UI (standing exclusion).
 */
@Composable
fun ClockAttendanceScreen(
    rpc: RpcClient,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ClockAttendanceViewModel = viewModel(
        factory = ClockAttendanceViewModel.factory(rpc),
    ),
) {
    val state by viewModel.state.collectAsState()

    ShopStaffScreen(
        title = "HR — Clock",
        subtitle = "Attendance",
        modifier = modifier,
        onBack = onBack,
    ) {
        ShopStaffPanel(title = "Clock event") {
            OutlinedTextField(
                value = state.employeeId,
                onValueChange = viewModel::onEmployeeIdChange,
                label = { Text("Employee UUID") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.busy,
            )
            OutlinedTextField(
                value = state.notes,
                onValueChange = viewModel::onNotesChange,
                label = { Text("Notes (optional)") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.busy,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ShopPrimaryButton(
                    label = "Clock in",
                    onClick = { viewModel.clock(AttendanceEventType.CLOCK_IN) },
                    enabled = !state.busy,
                    modifier = Modifier.weight(1f),
                )
                ShopSecondaryButton(
                    label = "Clock out",
                    onClick = { viewModel.clock(AttendanceEventType.CLOCK_OUT) },
                    enabled = !state.busy,
                    modifier = Modifier.weight(1f),
                )
            }
            state.message?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
        }
        ShopSecondaryButton(label = "Back", onClick = onBack)
    }
}
