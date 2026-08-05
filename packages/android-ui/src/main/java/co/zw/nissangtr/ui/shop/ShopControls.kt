package co.zw.nissangtr.ui.shop

/**
 * Forked from Shopping-By-KMP `presentation/component/Buttons.kt`, FilterDialog,
 * SortDialog, home SearchBox / Location row patterns
 * (MIT © 2023 Mahdi Razzaghi Ghaleh). ProgressBarState loading spinner omitted
 * from DefaultButton — use [loading] flag. Payments: GTR PSPs only.
 */

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import co.zw.nissangtr.ui.theme.GtrColors

private val ShopBorderColor = Color(0xFFDBDBDC)

/**
 * KMP [CircleButton] — 50dp bordered Card circle (not a tinted Box).
 */
@Composable
fun ShopCircleIconButton(
    imageVector: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    tint: Color = MaterialTheme.colorScheme.onBackground,
) {
    Card(
        modifier = modifier.size(50.dp),
        shape = CircleShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, ShopBorderColor),
        onClick = onClick,
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(imageVector, contentDescription = contentDescription, tint = tint)
        }
    }
}

/** KMP [DefaultButton] — extraLarge shape, primary fill, border. */
@Composable
fun ShopPrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.background,
            disabledContentColor = MaterialTheme.colorScheme.primary,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(25.dp)
                    .padding(end = 4.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun ShopSecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.primary,
        ),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun ShopDangerButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = ButtonDefaults.buttonColors(
            containerColor = GtrColors.Danger,
            contentColor = GtrColors.PrimaryInk,
        ),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun ShopSearchBar(
    placeholder: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .height(50.dp)
                .border(1.dp, ShopBorderColor, MaterialTheme.shapes.small)
                .clip(MaterialTheme.shapes.small)
                .background(GtrColors.Chalk)
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                placeholder,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        trailing?.invoke()
    }
}

@Composable
fun ShopLocationRow(
    label: String,
    locationText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f).clickable(onClick = onClick)) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    Icons.Filled.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    locationText,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.invoke()
    }
}

/** KMP cart [ProceedButtonBox]. */
@Composable
fun ShopProceedButtonBox(
    totalLabel: String,
    ctaLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Total", style = MaterialTheme.typography.titleMedium)
                Text(totalLabel, style = MaterialTheme.typography.titleLarge)
            }
            ShopPrimaryButton(
                label = ctaLabel,
                onClick = onClick,
                enabled = enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
            )
        }
    }
}

@Composable
fun ShopAddressPicker(
    addressLine: String,
    onAddressChange: (String) -> Unit,
    latitude: String,
    longitude: String,
    onLatitudeChange: (String) -> Unit,
    onLongitudeChange: (String) -> Unit,
    mapsKeyPresent: Boolean,
    modifier: Modifier = Modifier,
    mapSlot: (@Composable ColumnScope.() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ShopSectionHeader(title = "Delivery address", actionLabel = null)
        Text(
            if (mapsKeyPresent) {
                "Pick a point on the map, then confirm the street address."
            } else {
                "Set GOOGLE_MAPS_API_KEY in local.properties to enable the map picker " +
                    "(Shopping-By-KMP pattern). Lat/lng + line work offline until then."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (mapSlot != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(MaterialTheme.shapes.small)
                    .border(1.dp, GtrColors.Mist, MaterialTheme.shapes.small),
                content = mapSlot,
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(GtrColors.Mist),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (mapsKeyPresent) "Map host not wired in this build" else "Map unavailable — enter coordinates",
                    style = MaterialTheme.typography.bodySmall,
                    color = GtrColors.Steel,
                )
            }
        }
        OutlinedTextField(
            value = addressLine,
            onValueChange = onAddressChange,
            label = { Text("Street / area") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = false,
            minLines = 2,
            shape = MaterialTheme.shapes.small,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = latitude,
                onValueChange = onLatitudeChange,
                label = { Text("Lat") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = MaterialTheme.shapes.small,
            )
            OutlinedTextField(
                value = longitude,
                onValueChange = onLongitudeChange,
                label = { Text("Lng") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = MaterialTheme.shapes.small,
            )
        }
    }
}

enum class ShopSortOption(val label: String) {
    Relevance("Relevance"),
    PriceAsc("Price · low to high"),
    PriceDesc("Price · high to low"),
    NameAsc("Name · A–Z"),
}

data class ShopFilterState(
    val minPrice: Float = 0f,
    val maxPrice: Float = 500f,
    val category: String? = null,
)

@Composable
fun ShopFilterSortBar(
    onFilter: () -> Unit,
    onSort: () -> Unit,
    modifier: Modifier = Modifier,
    sortLabel: String = "Sort",
    filterLabel: String = "Filter",
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(onClick = onFilter, modifier = Modifier.weight(1f)) {
            Text(filterLabel)
        }
        OutlinedButton(onClick = onSort, modifier = Modifier.weight(1f)) {
            Text(sortLabel)
        }
    }
}

/** KMP FilterDialog adapted — USD price range + category chips. */
@Composable
fun ShopFilterDialog(
    state: ShopFilterState,
    categories: List<String>,
    onDismiss: () -> Unit,
    onApply: (ShopFilterState) -> Unit,
    priceCeiling: Float = 500f,
) {
    var draft by remember(state) { mutableStateOf(state) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filter") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "USD ${"%.0f".format(draft.minPrice)} – ${"%.0f".format(draft.maxPrice)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text("Min price", style = MaterialTheme.typography.labelSmall)
                Slider(
                    value = draft.minPrice,
                    onValueChange = { v ->
                        draft = draft.copy(minPrice = v.coerceAtMost(draft.maxPrice))
                    },
                    valueRange = 0f..priceCeiling,
                )
                Text("Max price", style = MaterialTheme.typography.labelSmall)
                Slider(
                    value = draft.maxPrice,
                    onValueChange = { v ->
                        draft = draft.copy(maxPrice = v.coerceAtLeast(draft.minPrice))
                    },
                    valueRange = 0f..priceCeiling,
                )
                Text("Category", style = MaterialTheme.typography.labelSmall)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item {
                        FilterChip(
                            selected = draft.category == null,
                            onClick = { draft = draft.copy(category = null) },
                            label = { Text("Any") },
                        )
                    }
                    items(categories) { cat ->
                        FilterChip(
                            selected = draft.category == cat,
                            onClick = { draft = draft.copy(category = cat) },
                            label = { Text(cat) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(draft) }) { Text("Apply") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
fun ShopSortDialog(
    selected: ShopSortOption,
    onDismiss: () -> Unit,
    onSelect: (ShopSortOption) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sort") },
        text = {
            Column {
                ShopSortOption.entries.forEach { opt ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(opt) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            opt.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (opt == selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

/** GTR payment methods only — never KMP PayPal / Apple Pay / Google Pay stubs. */
enum class ShopGtrPayMethod(val label: String, val subtitle: String) {
    ContiPay("ContiPay", "Card · EcoCash via ContiPay"),
    Paynow("Paynow", "Card · EcoCash via Paynow"),
    EcoCash("EcoCash", "Direct C2B PIN prompt"),
}

@Composable
fun ShopPaymentMethodList(
    selected: ShopGtrPayMethod,
    onSelect: (ShopGtrPayMethod) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        ShopSectionHeader(title = "Pay with", actionLabel = null)
        ShopGtrPayMethod.entries.forEach { method ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { onSelect(method) },
                shape = MaterialTheme.shapes.small,
                color = if (method == selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                } else {
                    MaterialTheme.colorScheme.surface
                },
                tonalElevation = 0.dp,
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(method.label, style = MaterialTheme.typography.titleSmall)
                    Text(
                        method.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
