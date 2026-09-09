package co.zw.nissangtr.customer.shell

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import co.zw.nissangtr.customer.visual.GtrPremiumColors
import co.zw.nissangtr.ui.shop.ShopBottomTab

@Composable
fun CustomerPremiumBottomBar(
    tabs: List<ShopBottomTab>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(
        modifier = modifier.fillMaxWidth().height(72.dp),
        containerColor = GtrPremiumColors.Surface,
        tonalElevation = 0.dp,
    ) {
        tabs.forEach { tab ->
            NavigationBarItem(
                selected = tab.key == selectedKey,
                onClick = { onSelect(tab.key) },
                icon = { Icon(tab.icon, contentDescription = tab.label) },
                label = { Text(tab.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = GtrPremiumColors.Red,
                    selectedTextColor = GtrPremiumColors.Red,
                    indicatorColor = Color.Transparent,
                    unselectedIconColor = GtrPremiumColors.TextSecondary,
                    unselectedTextColor = GtrPremiumColors.TextSecondary,
                ),
            )
        }
    }
}
