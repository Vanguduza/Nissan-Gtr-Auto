package co.zw.nissangtr.customer.wishlist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items as rowItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import co.zw.nissangtr.customer.rpc.RpcClient
import co.zw.nissangtr.customer.rpc.WishlistItem
import co.zw.nissangtr.customer.visual.GtrPremiumColors
import co.zw.nissangtr.customer.visual.PremiumEmptyState
import co.zw.nissangtr.customer.visual.PremiumMessageBanner
import co.zw.nissangtr.customer.visual.PremiumMessageKind
import co.zw.nissangtr.customer.visual.PremiumScreenHeader
import co.zw.nissangtr.customer.visual.R

/**
 * Wishlist is customer-friendly: OEM remains the internal open/remove key but is never rendered.
 */
@Composable
fun WishlistScreen(
    rpc: RpcClient,
    wishlistStore: WishlistStore,
    onBack: () -> Unit = {},
    onOpenProduct: (oem: String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    @Suppress("UNUSED_PARAMETER")
    val keepRpc = rpc
    val wishItems by wishlistStore.items.collectAsState()
    val busy by wishlistStore.busy.collectAsState()
    val error by wishlistStore.error.collectAsState()

    LaunchedEffect(Unit) { wishlistStore.refresh() }

    val categories = remember(wishItems) {
        listOf("All") + wishItems.mapNotNull {
            it.description?.substringBefore(' ')?.takeIf { s -> s.isNotBlank() }
        }.distinct().take(8)
    }
    var selectedCategory by remember { mutableStateOf("All") }
    val filtered = remember(wishItems, selectedCategory) {
        if (selectedCategory == "All") wishItems
        else wishItems.filter {
            it.description?.contains(selectedCategory, ignoreCase = true) == true
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(GtrPremiumColors.Background),
    ) {
        PremiumScreenHeader(
            title = "Wishlist",
            subtitle = if (wishItems.isEmpty()) null else "${wishItems.size} saved item(s)",
            onBack = onBack,
        )

        if (categories.size > 1) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowItems(categories) { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat },
                        label = { Text(cat) },
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        error?.let {
            PremiumMessageBanner(
                text = it,
                kind = PremiumMessageKind.Error,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        if (filtered.isEmpty() && !busy) {
            Box(
                Modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                PremiumEmptyState(
                    title = "Wishlist is empty",
                    body = "Save parts you want to come back to.",
                    art = R.drawable.gtr_empty_state_wishlist_empty,
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(filtered, key = { it.id }) { item ->
                    WishlistCard(
                        item = item,
                        onRemove = { wishlistStore.remove(item) },
                        onOpen = { onOpenProduct(item.oemPartNumber) },
                    )
                }
            }
        }
    }
}

@Composable
private fun WishlistCard(
    item: WishlistItem,
    onRemove: () -> Unit,
    onOpen: () -> Unit,
) {
    Card(
        onClick = onOpen,
        colors = CardDefaults.cardColors(containerColor = GtrPremiumColors.SurfaceRaised),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, GtrPremiumColors.Border),
    ) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(132.dp)
                    .background(GtrPremiumColors.SurfaceSoft),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Product photo\nunavailable",
                    color = GtrPremiumColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.align(Alignment.TopEnd),
                ) {
                    Icon(
                        Icons.Filled.Favorite,
                        contentDescription = "Remove from wishlist",
                        tint = GtrPremiumColors.RedBright,
                    )
                }
            }
            Column(Modifier.padding(10.dp)) {
                Text(
                    item.description?.trim()?.takeIf { it.isNotEmpty() } ?: "Saved part",
                    color = GtrPremiumColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Saved",
                    color = GtrPremiumColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
