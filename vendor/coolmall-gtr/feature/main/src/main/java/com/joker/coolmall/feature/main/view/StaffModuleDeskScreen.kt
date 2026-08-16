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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.zw.nissangtr.management.gtradapter.StaffNavTree
import com.joker.coolmall.core.designsystem.theme.SpacePaddingMedium
import com.joker.coolmall.core.ui.component.appbar.CenterTopAppBar
import com.joker.coolmall.feature.main.component.CommonScaffold
import com.joker.coolmall.feature.main.viewmodel.StaffHubViewModel

@Composable
internal fun StaffModuleDeskRoute(
    moduleId: String,
    onBack: () -> Unit,
    hubViewModel: StaffHubViewModel = hiltViewModel(),
) {
    val roles by hubViewModel.rolesLabel.collectAsStateWithLifecycle()
    val module = StaffNavTree.MODULES.find { it.id == moduleId } ?: return
    val leaves = StaffNavTree.filterLeaves(
        module,
        roles.split(",").map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { listOf("admin") },
    )
    var tab by rememberSaveable(moduleId) { mutableIntStateOf(0) }
    val safeTab = tab.coerceIn(0, (leaves.size - 1).coerceAtLeast(0))

    StaffModuleDeskScreen(
        title = module.label,
        leafLabels = leaves.map { it.label },
        selectedTab = safeTab,
        onTabChange = { tab = it },
        onBack = onBack,
        href = leaves.getOrNull(safeTab)?.href,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StaffModuleDeskScreen(
    title: String,
    leafLabels: List<String>,
    selectedTab: Int,
    onTabChange: (Int) -> Unit,
    onBack: () -> Unit,
    href: String?,
) {
    CommonScaffold(
        topBar = {
            CenterTopAppBar(
                titleText = title,
                showBackIcon = true,
                onBackClick = onBack,
            )
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
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                leafLabels.forEachIndexed { index, label ->
                    FilterChip(
                        selected = selectedTab == index,
                        onClick = { onTabChange(index) },
                        label = { Text(label) },
                    )
                }
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (href) {
                    "/staff/warehouse/receive" -> WarehouseReceiveRoute()
                    "/staff/warehouse/transfers" -> WarehouseTransfersRoute()
                    "/staff/warehouse/master-stock" -> WarehouseMasterStockRoute(embedded = true)
                    null -> Text("No leaves")
                    else -> StaffOpsLeafRoute(href)
                }
            }
        }
    }
}
