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
import androidx.compose.material.icons.filled.AirlineSeatReclineNormal
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import co.zw.nissangtr.ui.shop.ShopHonestEmpty
import co.zw.nissangtr.ui.theme.GtrColors
import co.zw.nissangtr.ui.theme.GtrLogo

/**
 * Car Parts category tree for the hamburger — distinct icons per row
 * (layout inspired by industry IA; GTR labels, no third-party brand assets).
 */
data class MenuCategory(
    val label: String,
    val icon: ImageVector,
    val subcategories: List<Pair<String, ImageVector>> = emptyList(),
)

val GtrCarPartCategories: List<MenuCategory> = listOf(
    MenuCategory(
        "Service Parts",
        Icons.Filled.CarRepair,
        listOf(
            "Oil filters" to Icons.Filled.FilterLike,
            "Air filters" to Icons.Filled.FilterLike,
            "Cabin filters" to Icons.Filled.FilterLike,
            "Belts" to Icons.Filled.Settings,
            "Fluids" to Icons.Filled.OilBarrel,
        ),
    ),
    MenuCategory(
        "Braking",
        Icons.Filled.Speed,
        listOf(
            "Brake pads" to Icons.Filled.Speed,
            "Brake discs" to Icons.Filled.Speed,
            "Calipers" to Icons.Filled.Build,
            "Brake fluid" to Icons.Filled.WaterDrop,
        ),
    ),
    MenuCategory(
        "Steering & Suspension",
        Icons.Filled.AirlineSeatReclineNormal,
        listOf(
            "Shock absorbers" to Icons.Filled.Settings,
            "Coil springs" to Icons.Filled.Settings,
            "Control arms" to Icons.Filled.Build,
            "Tie rods" to Icons.Filled.Handyman,
        ),
    ),
    MenuCategory(
        "Engine Parts",
        Icons.Filled.Build,
        listOf(
            "Gaskets" to Icons.Filled.Widgets,
            "Timing belts" to Icons.Filled.Settings,
            "Pulleys" to Icons.Filled.Settings,
            "Sensors" to Icons.Filled.ElectricBolt,
        ),
    ),
    MenuCategory(
        "Transmission",
        Icons.Filled.Settings,
        listOf(
            "Clutch kits" to Icons.Filled.Settings,
            "Flywheels" to Icons.Filled.Settings,
            "Gearbox mounts" to Icons.Filled.Build,
        ),
    ),
    MenuCategory(
        "Electrical",
        Icons.Filled.ElectricBolt,
        listOf(
            "Batteries" to Icons.Filled.BatteryFull,
            "Alternators" to Icons.Filled.ElectricBolt,
            "Starters" to Icons.Filled.ElectricBolt,
            "Ignition" to Icons.Filled.Lightbulb,
        ),
    ),
    MenuCategory(
        "Lighting",
        Icons.Filled.Lightbulb,
        listOf(
            "Headlamp bulbs" to Icons.Filled.Lightbulb,
            "LED kits" to Icons.Filled.Lightbulb,
            "Indicators" to Icons.Filled.Lightbulb,
        ),
    ),
    MenuCategory(
        "Body & Exhaust",
        Icons.Filled.DirectionsCar,
        listOf(
            "Exhaust systems" to Icons.Filled.DirectionsCar,
            "Body panels" to Icons.Filled.DirectionsCar,
            "Mirrors" to Icons.Filled.DirectionsCar,
        ),
    ),
    MenuCategory(
        "Cooling & Heating",
        Icons.Filled.Thermostat,
        listOf(
            "Radiators" to Icons.Filled.Thermostat,
            "Water pumps" to Icons.Filled.WaterDrop,
            "Thermostats" to Icons.Filled.Thermostat,
            "Hoses" to Icons.Filled.WaterDrop,
        ),
    ),
    MenuCategory(
        "Fuel System",
        Icons.Filled.LocalGasStation,
        listOf(
            "Fuel injectors" to Icons.Filled.LocalGasStation,
            "Fuel filters" to Icons.Filled.FilterLike,
            "Throttle bodies" to Icons.Filled.Build,
        ),
    ),
)

/** Shared filter-style icon (Material has no FilterAlt on all AGP sets — use WaterDrop/Widgets fallback via alias). */
private val Icons.Filled.FilterLike: ImageVector
    get() = Widgets

data class RootMenuItem(
    val label: String,
    val icon: ImageVector,
    val action: RootMenuKind,
)

enum class RootMenuKind {
    CarParts,
    EmptySoon, // Accessories, Detailing, Tools, …
    Deals,
}

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
    RootMenuItem("MOT / Service", Icons.Filled.CarRepair, RootMenuKind.EmptySoon),
)

sealed class HamburgerMenuAction {
    /** Opens the All Categories grid (not home / not root menu). */
    data object OpenAllCategories : HamburgerMenuAction()
    data object OpenDeals : HamburgerMenuAction()
    data object OpenAbout : HamburgerMenuAction()
    data object OpenContact : HamburgerMenuAction()
    data object OpenStoreLocator : HamburgerMenuAction()
    data object Close : HamburgerMenuAction()
}

private enum class MenuPane {
    Root,
    CarParts,
    Category,
    Deals,
    About,
    Contact,
    StoreLocator,
}

/**
 * Full-screen hamburger — full IA list; Car Parts → categories with distinct icons;
 * All car parts → categories page; leaf category taps show empty inventory dialog.
 */
@Composable
fun HamburgerMenuOverlay(
    onAction: (HamburgerMenuAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pane by remember { mutableStateOf(MenuPane.Root) }
    var selectedCategory by remember { mutableStateOf<MenuCategory?>(null) }
    var emptyDialogTitle by remember { mutableStateOf<String?>(null) }

    emptyDialogTitle?.let { title ->
        EmptyCatalogDialog(
            title = title,
            onDismiss = { emptyDialogTitle = null },
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                GtrLogo(modifier = Modifier.height(36.dp))
                TextButton(onClick = { onAction(HamburgerMenuAction.Close) }) {
                    Icon(Icons.Filled.Close, contentDescription = "Close menu")
                }
            }
            HorizontalDivider(color = GtrColors.Mist)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 4.dp, vertical = 4.dp),
            ) {
                when (pane) {
                    MenuPane.Root -> {
                        GtrRootMenuItems.forEach { item ->
                            MenuRow(
                                icon = item.icon,
                                label = item.label,
                                onClick = {
                                    when (item.action) {
                                        RootMenuKind.CarParts -> pane = MenuPane.CarParts
                                        RootMenuKind.Deals -> pane = MenuPane.Deals
                                        RootMenuKind.EmptySoon -> emptyDialogTitle = item.label
                                    }
                                },
                            )
                        }
                    }
                    MenuPane.CarParts -> {
                        TextButton(onClick = { pane = MenuPane.Root }) {
                            Text("← Menu")
                        }
                        Text(
                            "Car Parts",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                        MenuRow(
                            icon = Icons.Filled.Widgets,
                            label = "All car parts",
                            onClick = { onAction(HamburgerMenuAction.OpenAllCategories) },
                        )
                        GtrCarPartCategories.forEach { cat ->
                            MenuRow(
                                icon = cat.icon,
                                label = cat.label,
                                onClick = {
                                    selectedCategory = cat
                                    pane = MenuPane.Category
                                },
                            )
                        }
                    }
                    MenuPane.Category -> {
                        val cat = selectedCategory
                        TextButton(onClick = { pane = MenuPane.CarParts }) {
                            Text("← Car Parts")
                        }
                        Text(
                            cat?.label.orEmpty(),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                        MenuRow(
                            icon = cat?.icon ?: Icons.Filled.Build,
                            label = "All ${cat?.label.orEmpty()}",
                            onClick = { emptyDialogTitle = cat?.label ?: "Category" },
                        )
                        cat?.subcategories.orEmpty().forEach { (sub, icon) ->
                            MenuRow(
                                icon = icon,
                                label = sub,
                                onClick = { emptyDialogTitle = sub },
                            )
                        }
                    }
                    MenuPane.Deals -> {
                        TextButton(onClick = { pane = MenuPane.Root }) {
                            Text("← Menu")
                        }
                        ShopHonestEmpty(
                            title = "No deals feed yet",
                            body = "Promo / deals RPC is not wired — we never invent sale SKUs.",
                        )
                    }
                    MenuPane.About -> {
                        TextButton(onClick = { pane = MenuPane.Root }) {
                            Text("← Menu")
                        }
                        ShopHonestEmpty(
                            title = "About Nissan GTR Auto",
                            body = "Genuine Nissan parts · Harare counter & nationwide dispatch.",
                        )
                    }
                    MenuPane.Contact -> {
                        TextButton(onClick = { pane = MenuPane.Root }) {
                            Text("← Menu")
                        }
                        ShopHonestEmpty(
                            title = "Contact",
                            body = "Harare counter · WhatsApp via Live chat · nissangtrauto.co.zw/contact",
                        )
                    }
                    MenuPane.StoreLocator -> {
                        TextButton(onClick = { pane = MenuPane.Root }) {
                            Text("← Menu")
                        }
                        ShopHonestEmpty(
                            title = "Store locator",
                            body = "Harare counter location ships with the storefront map module — no third-party store list.",
                        )
                    }
                }
            }

            HorizontalDivider(color = GtrColors.Mist)
            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                TextButton(onClick = { pane = MenuPane.StoreLocator }) {
                    Icon(Icons.Filled.Store, null, Modifier.size(16.dp))
                    Text(" Store Locator")
                }
                TextButton(onClick = { pane = MenuPane.About }) {
                    Icon(Icons.Filled.Info, null, Modifier.size(16.dp))
                    Text(" About Us")
                }
                TextButton(onClick = { pane = MenuPane.Contact }) {
                    Icon(Icons.Filled.Phone, null, Modifier.size(16.dp))
                    Text(" Contact Us")
                }
            }
        }
    }
}

@Composable
fun EmptyCatalogDialog(
    title: String,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Text("No items added yet. Stock for this category will appear here when catalog listings are published.")
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("OK") }
        },
    )
}

@Composable
private fun MenuRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    HorizontalDivider(color = GtrColors.Mist)
}
