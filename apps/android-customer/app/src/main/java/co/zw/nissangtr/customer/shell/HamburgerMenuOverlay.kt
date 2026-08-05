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
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Storefront
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
import co.zw.nissangtr.ui.shop.ShopCircleIconButton
import co.zw.nissangtr.ui.shop.ShopHonestEmpty
import co.zw.nissangtr.ui.theme.GtrColors
import co.zw.nissangtr.ui.theme.GtrLogo

/** Fallback part categories when browse RPC returns none (GTR storefront, not third-party brand copy). */
val GtrPartCategories = listOf(
    "Brakes", "Filters", "Engine", "Suspension", "Electrical", "Cooling", "Body", "Drivetrain",
)

/**
 * Optional subcategory hints per category — generic auto-parts terms.
 * Prefer [subcategoriesByCategory] from browse hits when non-empty.
 */
private val DefaultSubcategories: Map<String, List<String>> = mapOf(
    "Brakes" to listOf("Pads", "Discs", "Calipers", "Fluid"),
    "Filters" to listOf("Oil", "Air", "Cabin", "Fuel"),
    "Engine" to listOf("Belts", "Gaskets", "Sensors", "Mounts"),
    "Suspension" to listOf("Shocks", "Bushings", "Arms", "Springs"),
    "Electrical" to listOf("Batteries", "Lighting", "Ignition", "Sensors"),
    "Cooling" to listOf("Radiators", "Hoses", "Thermostats", "Pumps"),
    "Body" to listOf("Mirrors", "Panels", "Trim", "Glass"),
    "Drivetrain" to listOf("Clutch", "CV joints", "Differentials", "Mounts"),
)

sealed class HamburgerMenuAction {
    data class OpenCatalog(val category: String?, val subcategory: String? = null) : HamburgerMenuAction()
    data object OpenDeals : HamburgerMenuAction()
    data object OpenAbout : HamburgerMenuAction()
    data object OpenContact : HamburgerMenuAction()
    data object Close : HamburgerMenuAction()
}

private enum class MenuPane {
    Root,
    CarParts,
    Category,
    Deals,
    About,
    Contact,
}

/**
 * Full-screen hamburger overlay — icon + label + chevron rows; Car Parts → categories → subcategories.
 */
@Composable
fun HamburgerMenuOverlay(
    categories: List<String>,
    subcategoriesByCategory: Map<String, List<String>>,
    onAction: (HamburgerMenuAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pane by remember { mutableStateOf(MenuPane.Root) }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    val resolvedCategories = categories.ifEmpty { GtrPartCategories }

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
                GtrLogo(modifier = Modifier.height(28.dp).padding(start = 4.dp))
                ShopCircleIconButton(
                    imageVector = Icons.Filled.Close,
                    onClick = { onAction(HamburgerMenuAction.Close) },
                    contentDescription = "Close menu",
                )
            }
            HorizontalDivider(color = GtrColors.Mist)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            ) {
                when (pane) {
                    MenuPane.Root -> {
                        MenuRow(
                            icon = Icons.Filled.Build,
                            label = "Car Parts",
                            onClick = { pane = MenuPane.CarParts },
                        )
                        MenuRow(
                            icon = Icons.Filled.LocalOffer,
                            label = "Deals",
                            onClick = { pane = MenuPane.Deals },
                        )
                        MenuRow(
                            icon = Icons.Filled.Storefront,
                            label = "Shop catalog",
                            onClick = {
                                onAction(HamburgerMenuAction.OpenCatalog(category = null))
                            },
                        )
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
                            icon = Icons.Filled.Build,
                            label = "All car parts",
                            onClick = {
                                onAction(HamburgerMenuAction.OpenCatalog(category = null))
                            },
                        )
                        resolvedCategories.forEach { cat ->
                            MenuRow(
                                icon = Icons.Filled.Build,
                                label = cat,
                                onClick = {
                                    selectedCategory = cat
                                    pane = MenuPane.Category
                                },
                            )
                        }
                    }
                    MenuPane.Category -> {
                        val cat = selectedCategory.orEmpty()
                        TextButton(onClick = { pane = MenuPane.CarParts }) {
                            Text("← Car Parts")
                        }
                        Text(
                            cat,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                        MenuRow(
                            icon = Icons.Filled.Build,
                            label = "All $cat",
                            onClick = {
                                onAction(HamburgerMenuAction.OpenCatalog(category = cat))
                            },
                        )
                        val subs = subcategoriesByCategory[cat]
                            ?.takeIf { it.isNotEmpty() }
                            ?: DefaultSubcategories[cat].orEmpty()
                        if (subs.isEmpty()) {
                            ShopHonestEmpty(
                                title = "No subcategories yet",
                                body = "Browse all $cat — deep category tree ships when catalog RPC exposes it.",
                            )
                        } else {
                            subs.forEach { sub ->
                                MenuRow(
                                    icon = Icons.Filled.Build,
                                    label = sub,
                                    onClick = {
                                        onAction(
                                            HamburgerMenuAction.OpenCatalog(
                                                category = cat,
                                                subcategory = sub,
                                            ),
                                        )
                                    },
                                )
                            }
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
                            body = "Genuine Nissan parts · Harare counter & nationwide dispatch. Full company page lives on the web storefront.",
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
                }
            }

            HorizontalDivider(color = GtrColors.Mist)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                TextButton(onClick = { pane = MenuPane.About }) {
                    Icon(Icons.Filled.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(" About")
                }
                TextButton(onClick = { pane = MenuPane.Contact }) {
                    Icon(Icons.Filled.Phone, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(" Contact")
                }
            }
        }
    }
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
            modifier = Modifier.size(22.dp),
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
