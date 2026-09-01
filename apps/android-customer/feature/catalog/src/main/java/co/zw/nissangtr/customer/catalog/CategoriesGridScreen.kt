package co.zw.nissangtr.customer.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CarRepair
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import co.zw.nissangtr.customer.visual.CategoryArt
import co.zw.nissangtr.customer.visual.GtrPremiumColors
import co.zw.nissangtr.customer.visual.PremiumScreenHeader
import co.zw.nissangtr.customer.visual.R

data class CatalogCategoryCard(
    val label: String,
    val icon: ImageVector,
)

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

@Composable
fun CategoriesGridScreen(
    categories: List<CatalogCategoryCard> = DefaultCatalogCategoryCards,
    onBack: () -> Unit,
    onCategoryClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val list = categories.ifEmpty { DefaultCatalogCategoryCards }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(GtrPremiumColors.Background),
    ) {
        PremiumScreenHeader(
            title = "All Car Parts",
            subtitle = "Browse parts currently available in stock",
            onBack = onBack,
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(list, key = { it.label }) { cat ->
                IllustratedCategoryCard(cat.label) { onCategoryClick(cat.label) }
            }
        }
    }
}

@Composable
private fun IllustratedCategoryCard(label: String, onClick: () -> Unit) {
    val art = artForCategory(label)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = GtrPremiumColors.SurfaceRaised),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, GtrPremiumColors.Border),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(GtrPremiumColors.Red.copy(alpha = .12f), RoundedCornerShape(12.dp))
                    .padding(8.dp),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.foundation.Image(
                    painter = painterResource(art.drawable),
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.Fit,
                )
            }
            Text(
                label,
                color = GtrPremiumColors.TextPrimary,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
            )
            Text(
                "Shop in-stock parts",
                color = GtrPremiumColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun artForCategory(label: String): CategoryArt = when {
    label.contains("brak", true) -> CategoryArt.Brakes
    label.contains("susp", true) || label.contains("steer", true) -> CategoryArt.Suspension
    label.contains("trans", true) -> CategoryArt.Transmission
    label.contains("electric", true) -> CategoryArt.Electrical
    label.contains("light", true) -> CategoryArt.Lighting
    label.contains("body", true) || label.contains("exhaust", true) -> CategoryArt.Body
    label.contains("cool", true) || label.contains("heat", true) -> CategoryArt.Cooling
    label.contains("fuel", true) -> CategoryArt.Fuel
    else -> CategoryArt.Engine
}
