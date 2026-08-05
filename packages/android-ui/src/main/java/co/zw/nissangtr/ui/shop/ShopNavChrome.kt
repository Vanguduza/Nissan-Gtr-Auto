package co.zw.nissangtr.ui.shop

/**
 * Forked from Shopping-By-KMP `presentation/ui/main/MainNav.kt` → [BottomNavigationUI]
 * (MIT © 2023 Mahdi Razzaghi Ghaleh). NavHost wiring stays in each app; this module
 * owns the Card + NavigationBar chrome + KMP item colors (GTR primary).
 */

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

data class ShopBottomTab(
    val key: String,
    val label: String,
    val icon: ImageVector,
)

/** KMP [DefaultNavigationBarItemTheme] with GTR primary. */
@Composable
fun shopNavigationBarItemColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = MaterialTheme.colorScheme.primary,
    unselectedIconColor = MaterialTheme.colorScheme.primary,
    unselectedTextColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
    selectedTextColor = MaterialTheme.colorScheme.primary,
    indicatorColor = MaterialTheme.colorScheme.background,
)

/**
 * KMP [BottomNavigationUI] — elevated Card, 16dp top corners, NavigationBar.
 * Host supplies tabs (Home / Wishlist / Cart / Profile for customer).
 */
@Composable
fun ShopBottomBar(
    tabs: List<ShopBottomTab>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
    ) {
        NavigationBar(
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.background,
            tonalElevation = 8.dp,
        ) {
            tabs.forEach { tab ->
                NavigationBarItem(
                    selected = tab.key == selectedKey,
                    onClick = { onSelect(tab.key) },
                    colors = shopNavigationBarItemColors(),
                    icon = { Icon(tab.icon, contentDescription = tab.label) },
                    label = { Text(tab.label) },
                )
            }
        }
    }
}
