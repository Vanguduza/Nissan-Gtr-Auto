package co.zw.nissangtr.customer.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CarRepair
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.OilBarrel
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Widgets
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.zw.nissangtr.customer.visual.CategoryArt
import co.zw.nissangtr.customer.visual.CategoryGlyph
import co.zw.nissangtr.customer.visual.GtrPremiumColors
import co.zw.nissangtr.customer.visual.PremiumEmptyState
import co.zw.nissangtr.customer.visual.PremiumSurfaceCard
import co.zw.nissangtr.ui.theme.GtrLogo

data class MenuCategory(
    val label: String,
    val icon: ImageVector,
    val subcategories: List<Pair<String, ImageVector>> = emptyList(),
)

val GtrCarPartCategories: List<MenuCategory> = listOf(
    MenuCategory("Service Parts", Icons.Filled.CarRepair, listOf(
        "Oil filters" to Icons.Filled.FilterAlt, "Air filters" to Icons.Filled.FilterAlt,
        "Cabin filters" to Icons.Filled.FilterAlt, "Belts" to Icons.Filled.Settings,
        "Fluids" to Icons.Filled.OilBarrel,
    )),
    MenuCategory("Braking", Icons.Filled.Speed, listOf(
        "Brake pads" to Icons.Filled.Speed, "Brake discs" to Icons.Filled.Speed,
        "Calipers" to Icons.Filled.Build, "Brake fluid" to Icons.Filled.WaterDrop,
    )),
    MenuCategory("Steering & Suspension", Icons.Filled.Settings, listOf(
        "Shock absorbers" to Icons.Filled.Settings, "Coil springs" to Icons.Filled.Settings,
        "Control arms" to Icons.Filled.Build, "Tie rods" to Icons.Filled.Handyman,
    )),
    MenuCategory("Engine Parts", Icons.Filled.Build, listOf(
        "Gaskets" to Icons.Filled.Widgets, "Timing belts" to Icons.Filled.Settings,
        "Pulleys" to Icons.Filled.Settings, "Sensors" to Icons.Filled.ElectricBolt,
    )),
    MenuCategory("Transmission", Icons.Filled.Settings, listOf(
        "Clutch kits" to Icons.Filled.Settings, "Flywheels" to Icons.Filled.Settings,
        "Gearbox mounts" to Icons.Filled.Build,
    )),
    MenuCategory("Electrical", Icons.Filled.ElectricBolt, listOf(
        "Batteries" to Icons.Filled.BatteryFull, "Alternators" to Icons.Filled.ElectricBolt,
        "Starters" to Icons.Filled.ElectricBolt, "Ignition" to Icons.Filled.Lightbulb,
    )),
    MenuCategory("Lighting", Icons.Filled.Lightbulb, listOf(
        "Headlamp bulbs" to Icons.Filled.Lightbulb, "LED kits" to Icons.Filled.Lightbulb,
        "Indicators" to Icons.Filled.Lightbulb,
    )),
    MenuCategory("Body & Exhaust", Icons.Filled.DirectionsCar, listOf(
        "Exhaust systems" to Icons.Filled.DirectionsCar, "Body panels" to Icons.Filled.DirectionsCar,
        "Mirrors" to Icons.Filled.DirectionsCar,
    )),
    MenuCategory("Cooling & Heating", Icons.Filled.Thermostat, listOf(
        "Radiators" to Icons.Filled.Thermostat, "Water pumps" to Icons.Filled.WaterDrop,
        "Thermostats" to Icons.Filled.Thermostat, "Hoses" to Icons.Filled.WaterDrop,
    )),
    MenuCategory("Fuel System", Icons.Filled.LocalGasStation, listOf(
        "Fuel injectors" to Icons.Filled.LocalGasStation, "Fuel filters" to Icons.Filled.FilterAlt,
        "Throttle bodies" to Icons.Filled.Build,
    )),
)

data class RootMenuItem(
    val label: String,
    val icon: ImageVector,
    val action: RootMenuKind,
)

enum class RootMenuKind { CarParts, EmptySoon, Deals }

val GtrRootMenuItems: List<RootMenuItem> = listOf(
    RootMenuItem("Car Parts", Icons.Filled.CarRepair, RootMenuKind.CarParts),
    RootMenuItem("Accessories", Icons.Filled.Widgets, RootMenuKind.EmptySoon),
    RootMenuItem("Detailing", Icons.Filled.Spa, RootMenuKind.EmptySoon),
    RootMenuItem("Tools", Icons.Filled.Handyman, RootMenuKind.EmptySoon),
    RootMenuItem("Service Kits", Icons.Filled.CarRepair, RootMenuKind.EmptySoon),
    RootMenuItem("Engine Oils", Icons.Filled.OilBarrel, RootMenuKind.EmptySoon),
    RootMenuItem("Car Batteries", Icons.Filled.BatteryFull, RootMenuKind.EmptySoon),
    RootMenuItem("Wiper Blades", Icons.Filled.WaterDrop, RootMenuKind.EmptySoon),
    RootMenuItem("Deals", Icons.Filled.LocalOffer, RootMenuKind.Deals),
    RootMenuItem("Shop By Brand", Icons.Filled.DirectionsCar, RootMenuKind.EmptySoon),
)

sealed class HamburgerMenuAction {
    data object OpenAllCategories : HamburgerMenuAction()
    data class BrowseCategory(val label: String) : HamburgerMenuAction()
    /** Retained only for binary/source compatibility; this customer overlay never emits it. */
    @Deprecated("Customer EPC browsing retired")
    data object OpenEpcBrowse : HamburgerMenuAction()
    data object OpenDeals : HamburgerMenuAction()
    data object OpenAbout : HamburgerMenuAction()
    data object OpenContact : HamburgerMenuAction()
    data object OpenStoreLocator : HamburgerMenuAction()
    data object Close : HamburgerMenuAction()
}

private enum class MenuPane { Root, CarParts, Category, Deals, About, Contact, StoreLocator }

@Composable
fun HamburgerMenuOverlay(
    onAction: (HamburgerMenuAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pane by remember { mutableStateOf(MenuPane.Root) }
    var selectedCategory by remember { mutableStateOf<MenuCategory?>(null) }
    var emptyDialogTitle by remember { mutableStateOf<String?>(null) }

    emptyDialogTitle?.let { title ->
        EmptyCatalogDialog(title) { emptyDialogTitle = null }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(GtrPremiumColors.Background),
    ) {
        Row(
            Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GtrLogo(modifier = Modifier.height(44.dp).weight(1f))
            IconButton(onClick = { onAction(HamburgerMenuAction.Close) }) {
                Icon(Icons.Filled.Close, "Close menu", tint = GtrPremiumColors.TextPrimary)
            }
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (pane) {
                MenuPane.Root -> {
                    Text(
                        "SHOP",
                        color = GtrPremiumColors.TextSecondary,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(4.dp),
                    )
                    GtrRootMenuItems.forEach { item ->
                        RootRow(item) {
                            when (item.action) {
                                RootMenuKind.CarParts -> pane = MenuPane.CarParts
                                RootMenuKind.Deals -> pane = MenuPane.Deals
                                RootMenuKind.EmptySoon -> emptyDialogTitle = item.label
                            }
                        }
                    }
                }

                MenuPane.CarParts -> {
                    TextButton(onClick = { pane = MenuPane.Root }) {
                        Text("‹ Menu", color = GtrPremiumColors.RedBright)
                    }
                    Text(
                        "Car Parts",
                        color = GtrPremiumColors.TextPrimary,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                    PremiumSurfaceCard(
                        onClick = { onAction(HamburgerMenuAction.OpenAllCategories) },
                    ) {
                        Text(
                            "All car parts",
                            color = GtrPremiumColors.TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    // No EPC row exists here. Customers browse only in-stock commerce categories.
                    GtrCarPartCategories.forEach { cat ->
                        IllustratedMenuCategory(cat) {
                            selectedCategory = cat
                            pane = MenuPane.Category
                        }
                    }
                }

                MenuPane.Category -> {
                    val cat = selectedCategory
                    TextButton(onClick = { pane = MenuPane.CarParts }) {
                        Text("‹ Car Parts", color = GtrPremiumColors.RedBright)
                    }
                    Text(
                        cat?.label.orEmpty(),
                        color = GtrPremiumColors.TextPrimary,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    PremiumSurfaceCard(
                        onClick = {
                            cat?.label?.let { onAction(HamburgerMenuAction.BrowseCategory(it)) }
                        },
                    ) {
                        Text(
                            "Shop all ${cat?.label.orEmpty()}",
                            color = GtrPremiumColors.TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    cat?.subcategories.orEmpty().forEach { (sub, icon) ->
                        RootRow(
                            RootMenuItem(sub, icon, RootMenuKind.EmptySoon),
                        ) {
                            onAction(HamburgerMenuAction.BrowseCategory(sub))
                        }
                    }
                }

                MenuPane.Deals -> {
                    TextButton(onClick = { pane = MenuPane.Root }) {
                        Text("‹ Menu", color = GtrPremiumColors.RedBright)
                    }
                    PremiumEmptyState("No active deals", "New offers will appear here when published.")
                }

                MenuPane.About -> {
                    TextButton(onClick = { pane = MenuPane.Root }) { Text("‹ Menu") }
                    PremiumEmptyState("Nissan GTR Auto", "Parts • Performance • Precision")
                }
                MenuPane.Contact -> {
                    TextButton(onClick = { pane = MenuPane.Root }) { Text("‹ Menu") }
                    PremiumEmptyState("Contact", "Use Account → Support to chat with the parts counter.")
                }
                MenuPane.StoreLocator -> {
                    TextButton(onClick = { pane = MenuPane.Root }) { Text("‹ Menu") }
                    PremiumEmptyState("Store locator", "Harare counter.")
                }
            }

            if (pane == MenuPane.Root) {
                TextButton(onClick = { pane = MenuPane.StoreLocator }) {
                    Icon(Icons.Filled.Store, null, tint = GtrPremiumColors.TextSecondary)
                    Text("  Store Locator", color = GtrPremiumColors.TextPrimary)
                }
                TextButton(onClick = { pane = MenuPane.About }) {
                    Icon(Icons.Filled.Info, null, tint = GtrPremiumColors.TextSecondary)
                    Text("  About Us", color = GtrPremiumColors.TextPrimary)
                }
                TextButton(onClick = { pane = MenuPane.Contact }) {
                    Icon(Icons.Filled.Phone, null, tint = GtrPremiumColors.TextSecondary)
                    Text("  Contact Us", color = GtrPremiumColors.TextPrimary)
                }
            }
        }
    }
}

@Composable
private fun IllustratedMenuCategory(category: MenuCategory, onClick: () -> Unit) {
    val art = when {
        category.label.contains("Service", true) -> CategoryArt.Service
        category.label.contains("Brak", true) -> CategoryArt.Brakes
        category.label.contains("Susp", true) || category.label.contains("Steer", true) -> CategoryArt.Suspension
        category.label.contains("Trans", true) -> CategoryArt.Transmission
        category.label.contains("Elect", true) -> CategoryArt.Electrical
        category.label.contains("Light", true) -> CategoryArt.Lighting
        category.label.contains("Body", true) || category.label.contains("Exhaust", true) -> CategoryArt.Body
        category.label.contains("Cool", true) -> CategoryArt.Cooling
        category.label.contains("Fuel", true) -> CategoryArt.Fuel
        else -> CategoryArt.Engine
    }
    PremiumSurfaceCard(onClick = onClick) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(58.dp)
                    .background(GtrPremiumColors.Red.copy(alpha = .13f), MaterialTheme.shapes.medium),
                contentAlignment = Alignment.Center,
            ) {
                CategoryGlyph(art)
            }
            Text(
                category.label,
                color = GtrPremiumColors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f).padding(start = 12.dp),
            )
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                null,
                tint = GtrPremiumColors.TextSecondary,
            )
        }
    }
}

@Composable
private fun RootRow(item: RootMenuItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(item.icon, null, tint = GtrPremiumColors.Red, modifier = Modifier.size(22.dp))
        Text(
            item.label,
            color = GtrPremiumColors.TextPrimary,
            modifier = Modifier.weight(1f).padding(start = 12.dp),
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            null,
            tint = GtrPremiumColors.TextSecondary,
        )
    }
}

@Composable
fun EmptyCatalogDialog(title: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text("No items are currently published in this section.") },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}
