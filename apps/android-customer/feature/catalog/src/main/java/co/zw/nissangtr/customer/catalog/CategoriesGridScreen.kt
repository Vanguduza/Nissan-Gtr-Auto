package co.zw.nissangtr.customer.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.AirlineSeatReclineNormal
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CarRepair
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import co.zw.nissangtr.ui.theme.GtrColors

data class CatalogCategoryCard(
    val label: String,
    val icon: ImageVector,
)

/** All-categories page cards — large icon tiles (layout reference only). */
val DefaultCatalogCategoryCards: List<CatalogCategoryCard> = listOf(
    CatalogCategoryCard("Service Parts", Icons.Filled.CarRepair),
    CatalogCategoryCard("Braking", Icons.Filled.Speed),
    CatalogCategoryCard("Steering & Suspension", Icons.Filled.Settings),
    CatalogCategoryCard("Engine Parts", Icons.Filled.Build),
    CatalogCategoryCard("Transmission", Icons.Filled.Settings),
    CatalogCategoryCard("Electrical", Icons.Filled.ElectricBolt),
    CatalogCategoryCard("Lighting", Icons.Filled.Lightbulb),
    CatalogCategoryCard("Body & Exhaust", Icons.Filled.DirectionsCar),
    CatalogCategoryCard("Cooling & Heating", Icons.Filled.Thermostat),
    CatalogCategoryCard("Fuel System", Icons.Filled.LocalGasStation),
)

@Deprecated("Use DefaultCatalogCategoryCards", ReplaceWith("DefaultCatalogCategoryCards.map { it.label }"))
val DefaultCatalogCategories: List<String> = DefaultCatalogCategoryCards.map { it.label }

/**
 * Categories grid — large cards with red accent bar. Category tap shows empty inventory dialog
 * (does not navigate home / search).
 */
@Composable
fun CategoriesGridScreen(
    categories: List<CatalogCategoryCard> = DefaultCatalogCategoryCards,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var emptyTitle by remember { mutableStateOf<String?>(null) }
    val list = categories.ifEmpty { DefaultCatalogCategoryCards }

    emptyTitle?.let { title ->
        AlertDialog(
            onDismissRequest = { emptyTitle = null },
            title = { Text(title) },
            text = {
                Text("No items added yet. Stock for this category will appear here when catalog listings are published.")
            },
            confirmButton = {
                TextButton(onClick = { emptyTitle = null }) { Text("OK") }
            },
        )
    }

    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Spacer(modifier = Modifier.size(8.dp))
            Text("All Car Parts", style = MaterialTheme.typography.titleLarge)
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(list, key = { it.label }) { cat ->
                CategoryCard(card = cat, onClick = { emptyTitle = cat.label })
            }
        }
    }
}

@Composable
private fun CategoryCard(card: CatalogCategoryCard, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.9f)
            .border(1.dp, GtrColors.Mist, MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.small)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(MaterialTheme.colorScheme.primary),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                card.label,
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(GtrColors.Mist, MaterialTheme.shapes.small),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = card.icon,
                    contentDescription = null,
                    tint = GtrColors.Steel,
                    modifier = Modifier.size(56.dp),
                )
            }
        }
    }
}
