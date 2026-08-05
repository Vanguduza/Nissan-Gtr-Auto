package co.zw.nissangtr.ui.shop

/**
 * Phase A preview harness — realistic GTR sample merchandising (not empty shells).
 * Debug source set only. Open in Android Studio Preview against `:android-ui`.
 */

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import co.zw.nissangtr.ui.theme.GtrColors

private data class PreviewPart(
    val oem: String,
    val title: String,
    val subtitle: String,
    val priceUsd: String,
    val rating: String,
    val liked: Boolean = false,
)

private val SampleNewest = listOf(
    PreviewPart("16546-4BA0A", "Oil Filter — VR38DETT", "Genuine Nissan · Fits R35 GTR", "USD 28.50", "4.8"),
    PreviewPart("B8800-JF00A", "Front Brake Pad Set", "Akebono OEM · Pair", "USD 142.00", "4.6", liked = true),
    PreviewPart("92110-JF00A", "Radiator Assy", "Genuine · Dual pass", "USD 890.00", "4.9"),
)

private val SampleMostSale = listOf(
    PreviewPart("11910-JF00A", "Spark Plug (Iridium)", "NGK · Set of 6", "USD 96.00", "4.7"),
    PreviewPart("40300-JF00A", "Wheel Bearing Hub", "Front LH · R35", "USD 215.00", "4.5"),
    PreviewPart("21010-JF00A", "Water Pump", "Genuine Nissan", "USD 318.00", "4.4", liked = true),
)

private val SampleCategories = listOf(
    "Engine", "Brakes", "Cooling", "Suspension", "Electrical", "Body",
)

private val SampleBanners = listOf(
    "R35 GTR service kit — oil + filter + plugs · in stock Harare",
    "Core charge on reman turbos — refunded on return",
    "Same-day dispatch CBD · ContiPay / Paynow / EcoCash",
)

@Composable
private fun SamplePartThumb(label: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GtrColors.Mist),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label.take(8),
            style = MaterialTheme.typography.labelMedium,
            color = GtrColors.Steel,
        )
    }
}

@Preview(name = "01 Home rails", showBackground = true, widthDp = 390, heightDp = 820)
@Composable
fun PreviewShopHomeRails() {
    ShopTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ShopLocationRow(
                label = "Current vehicle",
                locationText = "2009 Nissan GT-R · JN1AR5EF9AM230001",
                onClick = {},
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                trailing = {
                    ShopHomeHeaderActions(onNotifications = {}, onSettings = {})
                },
            )
            ShopSearchBar(
                placeholder = "Search OEM, PNC, or part name",
                onClick = {},
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            ShopBannerCarousel(banners = SampleBanners)
            ShopMerchTitleRow(title = "Category", actionLabel = "See all", onAction = {})
            LazyRow(
                contentPadding = PaddingValues(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(SampleCategories) { cat ->
                    ShopCategoryBox(label = cat, onClick = {})
                }
            }
            ShopMerchTitleRow(title = "Most Sale", actionLabel = "See all", onAction = {})
            LazyRow(contentPadding = PaddingValues(horizontal = 8.dp)) {
                items(SampleMostSale) { part ->
                    ShopProductCard(
                        title = part.title,
                        subtitle = part.subtitle,
                        priceLabel = part.priceUsd,
                        onClick = {},
                        liked = part.liked,
                        onLikeClick = {},
                        ratingLabel = part.rating,
                        imageSlot = { SamplePartThumb(part.oem) },
                    )
                }
            }
            ShopMerchTitleRow(title = "Newest", actionLabel = "See all", onAction = {})
            LazyRow(contentPadding = PaddingValues(horizontal = 8.dp)) {
                items(SampleNewest) { part ->
                    ShopProductCard(
                        title = part.title,
                        subtitle = part.subtitle,
                        priceLabel = part.priceUsd,
                        onClick = {},
                        liked = part.liked,
                        onLikeClick = {},
                        ratingLabel = part.rating,
                        imageSlot = { SamplePartThumb(part.oem) },
                    )
                }
            }
        }
    }
}

@Preview(name = "02 PDP primitives", showBackground = true, widthDp = 390, heightDp = 720)
@Composable
fun PreviewShopPdp() {
    ShopTheme {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(GtrColors.Mist),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("16546-4BA0A", style = MaterialTheme.typography.titleMedium)
                }
                Text("Oil Filter — VR38DETT", style = MaterialTheme.typography.headlineSmall)
                Text("Genuine Nissan · OEM 16546-4BA0A", style = MaterialTheme.typography.bodyMedium)
                ShopRatingRow(avgRating = 4.8, reviewCount = 126)
                ShopStatusChip(label = "In stock · Harare WH", background = GtrColors.Accent)
                ShopSectionHeader(title = "Fitment", actionLabel = null)
                Text(
                    "Fits 2007–2024 Nissan GT-R (R35) VR38DETT. Verify garage vehicle before ATC.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                ShopSectionHeader(title = "Core charge", actionLabel = null)
                Text(
                    "No core on this SKU. Reman turbo assemblies show a separate deposit line at cart.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                ShopSectionHeader(title = "Description", actionLabel = null)
                ShopExpandableDescription(
                    text = "Genuine Nissan oil filter for the VR38DETT. Anti-drain back valve, " +
                        "bypass rating matched to factory service bulletin. Change every 5,000 km " +
                        "with 0W-40 under track use. Includes sealing ring. Ships from Harare " +
                        "central warehouse; nationwide ContiPay / Paynow / EcoCash checkout.",
                )
            }
            ShopStickyCtaBar(
                priceLabel = "USD 28.50",
                ctaLabel = "Add to cart",
                onCta = {},
                leading = {
                    IconButton(onClick = {}) {
                        Icon(Icons.Filled.Favorite, contentDescription = "Wishlist", tint = GtrColors.Primary)
                    }
                },
            )
        }
    }
}

@Preview(name = "03 Cart list + proceed", showBackground = true, widthDp = 390, heightDp = 720)
@Composable
fun PreviewShopCart() {
    ShopTheme {
        ShopDefaultScreen(
            title = "Cart",
            scrollable = true,
            bottomBar = {
                ShopProceedButtonBox(
                    totalLabel = "USD 295.00",
                    ctaLabel = "Proceed to checkout",
                    onClick = {},
                )
            },
        ) {
            listOf(
                Triple("Oil Filter — VR38DETT", "OEM 16546-4BA0A · Qty 2", "USD 57.00"),
                Triple("Front Brake Pad Set", "OEM B8800-JF00A · Qty 1", "USD 142.00"),
                Triple("Spark Plug (Iridium)", "OEM 11910-JF00A · Qty 1 set", "USD 96.00"),
            ).forEach { (title, sub, price) ->
                ShopListCard(
                    title = title,
                    subtitle = sub,
                    onClick = {},
                    leading = {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(GtrColors.Mist),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(title.take(3), style = MaterialTheme.typography.labelSmall)
                        }
                    },
                    trailing = {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(price, style = MaterialTheme.typography.titleSmall)
                            IconButton(onClick = {}) {
                                Icon(Icons.Filled.Delete, contentDescription = "Remove")
                            }
                        }
                    },
                    badges = {
                        ShopStatusChip(label = "USD", background = GtrColors.Accent)
                    },
                )
            }
            ShopHonestEmpty(
                title = "Coupons",
                body = "No active promotions on this cart. Deals appear here when merchandised.",
            )
        }
    }
}

@Preview(name = "04 List / order cards", showBackground = true, widthDp = 390, heightDp = 640)
@Composable
fun PreviewShopListCards() {
    ShopTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ShopSectionHeader(title = "Wishlist", actionLabel = "Edit", onAction = {})
            ShopListCard(
                title = "Radiator Assy",
                subtitle = "OEM 92110-JF00A · Genuine · Dual pass",
                onClick = {},
                leading = { ShopCircleBadge(text = "RAD") },
                trailing = {
                    Text("USD 890", style = MaterialTheme.typography.titleSmall)
                },
                badges = {
                    ShopStatusChip(label = "Low stock", background = GtrColors.Warning)
                },
            )
            ShopListCard(
                title = "Water Pump",
                subtitle = "OEM 21010-JF00A · Genuine Nissan",
                onClick = {},
                leading = { ShopCircleBadge(text = "WTR") },
                trailing = {
                    Text("USD 318", style = MaterialTheme.typography.titleSmall)
                },
            )
            ShopSectionHeader(title = "Recent orders", actionLabel = null)
            ShopOrderBox(
                title = "ORD-2026-08421",
                subtitle = "Oil filter ×2 · Brake pads ×1 · Delivered to Borrowdale",
                onClick = {},
                metaLabel = "Paid",
                metaValue = "USD 199.00",
                badges = {
                    ShopStatusChip(label = "Delivered", background = GtrColors.Accent)
                },
                thumbLabel = "ORD",
                expanded = true,
                onToggleExpand = {},
                expandedContent = {
                    Text("ContiPay · USD · 2026-08-02", style = MaterialTheme.typography.bodySmall)
                    ShopStepProgress(
                        steps = listOf("Paid", "Pick", "Ship", "Done"),
                        completedCount = 4,
                    )
                },
            )
            ShopOrderBox(
                title = "ORD-2026-08390",
                subtitle = "Spark plugs set · Awaiting dispatch",
                onClick = {},
                metaLabel = "Open",
                metaValue = "USD 96.00",
                badges = {
                    ShopStatusChip(label = "Packing", background = GtrColors.Steel)
                },
                thumbLabel = "SPL",
                expanded = false,
                onToggleExpand = {},
            )
        }
    }
}

@Preview(name = "05 Splash", showBackground = true, widthDp = 390, heightDp = 780)
@Composable
fun PreviewShopSplash() {
    ShopTheme {
        ShopSplash(
            brand = "Nissan GTR Auto",
            tagline = "Genuine parts · Harare & nationwide",
            onFinished = {},
            holdMs = 86_400_000L,
        )
    }
}

@Preview(name = "06 Bottom nav chrome", showBackground = true, widthDp = 390, heightDp = 120)
@Composable
fun PreviewShopBottomBar() {
    ShopTheme {
        ShopBottomBar(
            tabs = listOf(
                ShopBottomTab("home", "Home", Icons.Filled.Home),
                ShopBottomTab("wish", "Wishlist", Icons.Filled.Favorite),
                ShopBottomTab("cart", "Cart", Icons.Filled.ShoppingCart),
                ShopBottomTab("profile", "Profile", Icons.Filled.Person),
            ),
            selectedKey = "home",
            onSelect = {},
        )
    }
}

@Preview(name = "07 DefaultScreenUI shell", showBackground = true, widthDp = 390, heightDp = 400)
@Composable
fun PreviewShopDefaultScreen() {
    ShopTheme {
        ShopDefaultScreen(
            title = "Search results",
            subtitle = "oil filter · 12 hits",
            onBack = {},
        ) {
            ShopFilterSortBar(onFilter = {}, onSort = {})
            Text(
                "Showing genuine + pattern filters for VR38DETT. Tap a row for PDP.",
                style = MaterialTheme.typography.bodyMedium,
            )
            ShopProductCard(
                title = "Oil Filter — VR38DETT",
                subtitle = "Genuine Nissan · 16546-4BA0A",
                priceLabel = "USD 28.50",
                onClick = {},
                liked = true,
                onLikeClick = {},
                ratingLabel = "4.8",
                modifier = Modifier.width(180.dp),
                imageSlot = { SamplePartThumb("16546") },
            )
        }
    }
}
