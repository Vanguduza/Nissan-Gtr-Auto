package com.joker.coolmall.feature.main.view

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.zw.nissangtr.management.gtradapter.GtrPendingTransfer
import co.zw.nissangtr.management.gtradapter.GtrStockItemOption
import co.zw.nissangtr.management.gtradapter.GtrWarehouseOption
import com.joker.coolmall.core.ui.component.button.AppButton
import com.joker.coolmall.feature.main.R
import com.joker.coolmall.feature.main.viewmodel.WarehouseTransfersViewModel

@Composable
internal fun WarehouseTransfersRoute(
    viewModel: WarehouseTransfersViewModel = hiltViewModel(),
) {
    val warehouses by viewModel.warehouses.collectAsStateWithLifecycle()
    val fromId by viewModel.fromId.collectAsStateWithLifecycle()
    val toId by viewModel.toId.collectAsStateWithLifecycle()
    val oemQuery by viewModel.oemQuery.collectAsStateWithLifecycle()
    val item by viewModel.item.collectAsStateWithLifecycle()
    val qty by viewModel.qty.collectAsStateWithLifecycle()
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    WarehouseTransfersScreen(
        warehouses = warehouses,
        fromId = fromId,
        toId = toId,
        oemQuery = oemQuery,
        item = item,
        qty = qty,
        notes = notes,
        pending = pending,
        busy = busy,
        message = message,
        onOemChange = viewModel::updateOemQuery,
        onSearch = viewModel::searchOem,
        onSelectFrom = viewModel::selectFrom,
        onSelectTo = viewModel::selectTo,
        onQtyChange = viewModel::updateQty,
        onNotesChange = viewModel::updateNotes,
        onCreate = viewModel::createTransfer,
        onApprove = viewModel::approve,
        onReject = viewModel::reject,
    )
}

@Composable
internal fun WarehouseTransfersScreen(
    warehouses: List<GtrWarehouseOption>,
    fromId: String?,
    toId: String?,
    oemQuery: String,
    item: GtrStockItemOption?,
    qty: String,
    notes: String,
    pending: List<GtrPendingTransfer>,
    busy: Boolean,
    message: String?,
    onOemChange: (String) -> Unit,
    onSearch: () -> Unit,
    onSelectFrom: (String) -> Unit,
    onSelectTo: (String) -> Unit,
    onQtyChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onCreate: () -> Unit,
    onApprove: (String) -> Unit,
    onReject: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = stringResource(R.string.warehouse_transfers_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.warehouse_from_label),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 12.dp),
        )
        WarehouseChipRow(warehouses, fromId, onSelectFrom)
        Text(
            text = stringResource(R.string.warehouse_to_label),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 8.dp),
        )
        WarehouseChipRow(warehouses, toId, onSelectTo)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = oemQuery,
                onValueChange = onOemChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text(stringResource(R.string.warehouse_search_oem)) },
            )
            TextButton(onClick = onSearch, enabled = !busy) {
                Text(stringResource(R.string.warehouse_search))
            }
        }
        if (item != null) {
            Text(
                text = "${item.oemPartNumber} · ${item.description}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        OutlinedTextField(
            value = qty,
            onValueChange = onQtyChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            singleLine = true,
            label = { Text(stringResource(R.string.warehouse_qty)) },
        )
        OutlinedTextField(
            value = notes,
            onValueChange = onNotesChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            label = { Text(stringResource(R.string.warehouse_notes)) },
        )
        Spacer(modifier = Modifier.height(12.dp))
        AppButton(
            text = stringResource(R.string.warehouse_create_transfer),
            onClick = onCreate,
            enabled = !busy,
            loading = busy,
        )
        if (message != null) {
            Text(
                text = message,
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Text(
            text = stringResource(R.string.warehouse_pending_transfers),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 12.dp),
        )
        if (pending.isEmpty()) {
            Text(
                text = stringResource(R.string.warehouse_pending_empty),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        pending.forEach { row ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                Text(
                    text = row.documentNumber ?: row.id,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(
                        R.string.warehouse_transfer_row_fmt,
                        row.fromWarehouseId ?: "—",
                        row.toWarehouseId ?: "—",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { onApprove(row.id) }, enabled = !busy) {
                        Text(stringResource(R.string.warehouse_approve))
                    }
                    TextButton(onClick = { onReject(row.id) }, enabled = !busy) {
                        Text(stringResource(R.string.warehouse_reject))
                    }
                }
            }
        }
    }
}

@Composable
private fun WarehouseChipRow(
    warehouses: List<GtrWarehouseOption>,
    selectedId: String?,
    onSelect: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        warehouses.forEach { wh ->
            FilterChip(
                selected = wh.id == selectedId,
                onClick = { onSelect(wh.id) },
                label = { Text(wh.code) },
            )
        }
    }
}
