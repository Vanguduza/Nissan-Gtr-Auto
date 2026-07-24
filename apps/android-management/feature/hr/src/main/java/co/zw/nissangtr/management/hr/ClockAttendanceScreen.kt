package co.zw.nissangtr.management.hr

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import co.zw.nissangtr.management.rpc.RpcNames

/**
 * Thin HR clock in/out scaffold. Calls [RpcNames.CLOCK_ATTENDANCE] via [RpcClient].
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("HR — Clock in / out", style = MaterialTheme.typography.headlineSmall)
        Text(
            "RPC: ${RpcNames.CLOCK_ATTENDANCE} (p_employee_id, p_event_type, p_notes?)",
            style = MaterialTheme.typography.bodySmall,
        )
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
            Button(
                onClick = { viewModel.clock(AttendanceEventType.CLOCK_IN) },
                enabled = !state.busy,
            ) { Text("Clock in") }
            Button(
                onClick = { viewModel.clock(AttendanceEventType.CLOCK_OUT) },
                enabled = !state.busy,
            ) { Text("Clock out") }
        }
        state.message?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium)
        }
        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        OutlinedButton(onClick = onBack) { Text("Back") }
    }
}
