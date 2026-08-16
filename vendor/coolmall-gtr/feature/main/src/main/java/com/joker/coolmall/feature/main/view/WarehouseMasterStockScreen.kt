package com.joker.coolmall.feature.main.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
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
import co.zw.nissangtr.management.gtradapter.MasterStockRow
import com.joker.coolmall.core.designsystem.theme.SpacePaddingMedium
import com.joker.coolmall.core.ui.component.appbar.CenterTopAppBar
import com.joker.coolmall.feature.main.R
import com.joker.coolmall.feature.main.component.CommonScaffold
import com.joker.coolmall.feature.main.viewmodel.WarehouseMasterStockViewModel

@Composable
internal fun WarehouseMasterStockRoute(
    embedded: Boolean = false,
    viewModel: WarehouseMasterStockViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val chassis by viewModel.chassis.collectAsStateWithLifecycle()
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    WarehouseMasterStockScreen(
        query = query,
        chassis = chassis,
        rows = rows,
        loading = loading,
        error = error,
        onQueryChange = viewModel::updateQuery,
        onChassisChange = viewModel::updateChassis,
        onSearch = viewModel::search,
        embedded = embedded,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WarehouseMasterStockScreen(
    query: String,
    chassis: String,
    rows: List<MasterStockRow>,
    loading: Boolean,
    error: String?,
    onQueryChange: (String) -> Unit,
    onChassisChange: (String) -> Unit,
    onSearch: () -> Unit,
    embedded: Boolean = false,
) {
    val body: @Composable (PaddingValues) -> Unit = { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .then(
                    if (embedded) Modifier else Modifier.padding(SpacePaddingMedium),
                ),
        ) {
            Text(
                text = stringResource(R.string.warehouse_master_stock_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text(stringResource(R.string.warehouse_search_oem)) },
                )
                OutlinedTextField(
                    value = chassis,
                    onValueChange = onChassisChange,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text(stringResource(R.string.warehouse_search_chassis)) },
                )
                TextButton(onClick = onSearch) {
                    Text(stringResource(R.string.warehouse_search))
                }
            }
            if (loading) {
                Text(stringResource(R.string.warehouse_loading))
            }
            if (error != null) {
                Text(error, color = MaterialTheme.colorScheme.error)
            }
            LazyColumn(
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(rows, key = { it.stockItemId }) { row ->
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(row.oemPartNumber, style = MaterialTheme.typography.titleMedium)
                        Text(row.description, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            stringResource(
                                R.string.warehouse_qty_fmt,
                                row.qtyTotal,
                                row.qtyWh1,
                                row.qtyWh2,
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
    if (embedded) {
        body(PaddingValues())
    } else {
        CommonScaffold(
            topBar = {
                CenterTopAppBar(R.string.warehouse_master_stock_title, showBackIcon = false)
            },
            content = body,
        )
    }
}
