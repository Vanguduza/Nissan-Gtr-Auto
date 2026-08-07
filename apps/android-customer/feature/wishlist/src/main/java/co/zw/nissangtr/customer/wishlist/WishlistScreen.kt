package co.zw.nissangtr.customer.wishlist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items as lazyRowItems
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.unit.dp
import co.zw.nissangtr.customer.rpc.RpcClient
import co.zw.nissangtr.ui.shop.ShopHonestEmpty
import co.zw.nissangtr.ui.shop.ShopProductCard

/**
 * Wishlist tab — Shopping-By-KMP grid, hearts driven by shared [WishlistStore].
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
    val unusedRpc = rpc
    @Suppress("UNUSED_PARAMETER")
    val unusedBack = onBack

    val wishItems by wishlistStore.items.collectAsState()
    val busy by wishlistStore.busy.collectAsState()
    val error by wishlistStore.error.collectAsState()

    LaunchedEffect(Unit) {
        wishlistStore.refresh()
    }

    val categories = remember(wishItems) {
        listOf("All") + wishItems.mapNotNull {
            it.description?.substringBefore(' ')?.takeIf { s -> s.isNotBlank() }
        }
            .distinct()
            .take(8)
    }
    var selectedCategory by remember { mutableStateOf("All") }
    val filtered = remember(wishItems, selectedCategory) {
        if (selectedCategory == "All") wishItems
        else wishItems.filter {
            it.description?.contains(selectedCategory, ignoreCase = true) == true ||
                it.oemPartNumber.contains(selectedCategory, ignoreCase = true)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = "Wishlist",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(16.dp),
        )

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 8.dp),
        ) {
            lazyRowItems(categories) { cat ->
                FilterChip(
                    selected = selectedCategory == cat,
                    onClick = { selectedCategory = cat },
                    label = { Text(cat) },
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }

        error?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        if (filtered.isEmpty() && !busy) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                ShopHonestEmpty(
                    title = "Wishlist is empty",
                    body = "No saved items.",
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(8.dp),
                contentPadding = PaddingValues(8.dp),
            ) {
                gridItems(filtered, key = { it.id }) { item ->
                    ShopProductCard(
                        title = item.oemPartNumber,
                        subtitle = item.description,
                        priceLabel = "Saved",
                        liked = true,
                        onLikeClick = { wishlistStore.remove(item) },
                        onClick = { onOpenProduct(item.oemPartNumber) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
