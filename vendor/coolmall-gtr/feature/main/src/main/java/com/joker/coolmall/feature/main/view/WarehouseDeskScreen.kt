package com.joker.coolmall.feature.main.view

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.joker.coolmall.core.designsystem.theme.SpacePaddingMedium
import com.joker.coolmall.core.ui.component.appbar.CenterTopAppBar
import com.joker.coolmall.feature.main.R
import com.joker.coolmall.feature.main.component.CommonScaffold

private val warehouseDeskTabs = listOf(
    R.string.warehouse_tab_stock,
    R.string.warehouse_tab_receive,
    R.string.warehouse_tab_transfers,
)

@Composable
internal fun WarehouseDeskRoute() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    WarehouseDeskScreen(
        selectedTab = tab,
        onTabChange = { tab = it },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WarehouseDeskScreen(
    selectedTab: Int,
    onTabChange: (Int) -> Unit,
) {
    CommonScaffold(
        topBar = {
            CenterTopAppBar(R.string.staff_warehouse_tab, showBackIcon = false)
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = SpacePaddingMedium),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                warehouseDeskTabs.forEachIndexed { index, labelRes ->
                    FilterChip(
                        selected = selectedTab == index,
                        onClick = { onTabChange(index) },
                        label = { Text(stringResource(labelRes)) },
                    )
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                when (selectedTab) {
                    1 -> WarehouseReceiveRoute()
                    2 -> WarehouseTransfersRoute()
                    else -> WarehouseMasterStockRoute(embedded = true)
                }
            }
        }
    }
}
