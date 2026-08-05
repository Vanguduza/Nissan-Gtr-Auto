package co.zw.nissangtr.customer.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import co.zw.nissangtr.ui.shop.ShopCircleIconButton
import co.zw.nissangtr.ui.theme.GtrColors

/** Default GTR storefront categories — layout-inspired only; no proprietary GSF assets. */
val DefaultCatalogCategories = listOf(
    "Brakes", "Filters", "Engine", "Suspension", "Electrical", "Cooling", "Body", "Drivetrain",
)

private fun categoryIcon(name: String): ImageVector = when {
    name.contains("brake", ignoreCase = true) -> Icons.Filled.Speed
    name.contains("filter", ignoreCase = true) -> Icons.Filled.FilterAlt
    name.contains("engine", ignoreCase = true) -> Icons.Filled.Build
    name.contains("suspension", ignoreCase = true) -> Icons.Filled.Settings
    name.contains("electric", ignoreCase = true) -> Icons.Filled.ElectricBolt
    name.contains("cool", ignoreCase = true) -> Icons.Filled.Thermostat
    name.contains("body", ignoreCase = true) -> Icons.Filled.DirectionsCar
    name.contains("drive", ignoreCase = true) -> Icons.Filled.Build
    else -> Icons.Filled.Build
}

/**
 * Categories grid — large image/icon cards (GSF layout reference, GTR brand).
 */
@Composable
fun CategoriesGridScreen(
    categories: List<String> = DefaultCatalogCategories,
    onBack: () -> Unit,
    onCategory: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val list = categories.ifEmpty { DefaultCatalogCategories }
    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShopCircleIconButton(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                onClick = onBack,
                contentDescription = "Back",
            )
            Spacer(modifier = Modifier.size(12.dp))
            Text("Categories", style = MaterialTheme.typography.titleLarge)
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(list, key = { it }) { cat ->
                CategoryCard(label = cat, onClick = { onCategory(cat) })
            }
        }
    }
}

@Composable
private fun CategoryCard(label: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.95f)
            .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(GtrColors.Mist, MaterialTheme.shapes.small),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = categoryIcon(label),
                contentDescription = null,
                tint = GtrColors.Steel,
                modifier = Modifier.size(56.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
