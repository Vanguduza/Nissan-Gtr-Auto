package co.zw.nissangtr.customer.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import co.zw.nissangtr.customer.rpc.CatalogProduct
import co.zw.nissangtr.customer.rpc.ProductReviewStats
import co.zw.nissangtr.customer.rpc.SelectedFitmentVehicle
import co.zw.nissangtr.customer.visual.GtrPremiumColors
import co.zw.nissangtr.ui.shop.ShopRemoteImage

/**
 * Customer PDP: product photography + friendly commerce copy only.
 * OEM, PNC, EPC diagram and raw fitment lines are deliberately not rendered.
 */
@Composable
fun PremiumCatalogPdp(
    product: CatalogProduct,
    selectedVehicle: SelectedFitmentVehicle?,
    qty: String,
    busy: Boolean,
    reviewStats: ProductReviewStats?,
    liked: Boolean,
    onQtyChange: (String) -> Unit,
    onBack: () -> Unit,
    onAddToCart: () -> Unit,
    onToggleWishlist: () -> Unit,
    onOpenReviews: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val photo = product.imageUrls.firstOrNull { it.isNotBlank() }
    val vehicleLabel = selectedVehicle?.model?.removePrefix("Nissan ")?.trim()
    val compatibility = selectedVehicle?.let { fitsSelectedVehicle(product, it) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(GtrPremiumColors.Background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 88.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .background(GtrPremiumColors.SurfaceRaised),
            ) {
                ShopRemoteImage(
                    url = photo,
                    contentDescription = product.name,
                    modifier = Modifier.fillMaxSize().padding(20.dp),
                    contentScale = ContentScale.Fit,
                    placeholderLabel = "Product photo unavailable",
                )
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        "Back",
                        tint = GtrPremiumColors.TextPrimary,
                    )
                }
                IconButton(
                    onClick = onToggleWishlist,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                ) {
                    Icon(
                        if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        "Wishlist",
                        tint = if (liked) GtrPremiumColors.RedBright else GtrPremiumColors.TextPrimary,
                    )
                }
            }

            Column(Modifier.padding(16.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        product.stock.label(),
                        color = GtrPremiumColors.Success,
                        style = MaterialTheme.typography.labelLarge,
                    )
                    TextButton(onClick = onOpenReviews) {
                        Text(
                            if ((reviewStats?.reviewCount ?: 0) > 0) {
                                "%.1f ★ · %d reviews".format(
                                    reviewStats?.avgRating ?: 0.0,
                                    reviewStats?.reviewCount ?: 0,
                                )
                            } else "Reviews",
                            color = GtrPremiumColors.TextSecondary,
                        )
                    }
                }

                Text(
                    product.name,
                    color = GtrPremiumColors.TextPrimary,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                product.category?.takeIf { it.isNotBlank() }?.let {
                    Text(it, color = GtrPremiumColors.TextSecondary)
                }

                Spacer(Modifier.height(14.dp))
                Text(
                    product.usd?.let { "USD %.2f".format(it) } ?: "Price on request",
                    color = GtrPremiumColors.TextPrimary,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )

                if (selectedVehicle != null) {
                    Spacer(Modifier.height(16.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (compatibility == false) {
                                GtrPremiumColors.RedDark.copy(alpha = .25f)
                            } else {
                                GtrPremiumColors.Success.copy(alpha = .14f)
                            }
                        ),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (compatibility != false) {
                                Icon(
                                    Icons.Filled.CheckCircle,
                                    null,
                                    tint = GtrPremiumColors.Success,
                                    modifier = Modifier.size(22.dp),
                                )
                                Spacer(Modifier.size(8.dp))
                            }
                            Column {
                                Text(
                                    if (compatibility == false) {
                                        "Check compatibility"
                                    } else {
                                        "Fits your vehicle"
                                    },
                                    color = GtrPremiumColors.TextPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    vehicleLabel ?: "Selected Nissan",
                                    color = GtrPremiumColors.TextSecondary,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(22.dp))
                Text(
                    "Product details",
                    color = GtrPremiumColors.TextPrimary,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                product.brand?.takeIf { it.isNotBlank() }?.let {
                    DetailRow("Brand", it)
                }
                product.category?.takeIf { it.isNotBlank() }?.let {
                    DetailRow("Category", it)
                }
                product.specs
                    .filterNot { line ->
                        line.contains("PNC", ignoreCase = true) ||
                            line.contains("Chassis", ignoreCase = true) ||
                            line.contains("Engine", ignoreCase = true) ||
                            line.contains(product.oem, ignoreCase = true)
                    }
                    .take(6)
                    .forEachIndexed { index, value ->
                        DetailRow("Specification ${index + 1}", value)
                    }

                if (product.coreCharge > 0) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "A refundable core deposit of USD %.2f applies.".format(product.coreCharge),
                        color = GtrPremiumColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Spacer(Modifier.height(20.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Quantity",
                        color = GtrPremiumColors.TextPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = qty,
                        onValueChange = onQtyChange,
                        singleLine = true,
                        enabled = !busy,
                        modifier = Modifier.size(width = 92.dp, height = 56.dp),
                    )
                }

                Spacer(Modifier.height(24.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Reviews",
                        color = GtrPremiumColors.TextPrimary,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    TextButton(onClick = onOpenReviews) { Text("See all / write") }
                }
                Text(
                    if ((reviewStats?.reviewCount ?: 0) == 0) {
                        "No reviews yet."
                    } else {
                        "%.1f average from %d reviews.".format(
                            reviewStats?.avgRating ?: 0.0,
                            reviewStats?.reviewCount ?: 0,
                        )
                    },
                    color = GtrPremiumColors.TextSecondary,
                )
            }
        }

        Card(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = GtrPremiumColors.Surface),
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    product.usd?.let { "USD %.2f".format(it) } ?: "On request",
                    color = GtrPremiumColors.TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = onAddToCart,
                    enabled = !busy,
                    colors = ButtonDefaults.buttonColors(containerColor = GtrPremiumColors.Red),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 22.dp),
                    modifier = Modifier.height(48.dp),
                ) {
                    Text(if (busy) "Adding…" else "Add to Cart", color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label,
            color = GtrPremiumColors.TextSecondary,
            modifier = Modifier.width(132.dp),
        )
        Text(
            value,
            color = GtrPremiumColors.TextPrimary,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun fitsSelectedVehicle(product: CatalogProduct, vehicle: SelectedFitmentVehicle): Boolean? {
    if (product.fitmentLines.isEmpty()) return null
    val tokens = listOf(vehicle.generation, vehicle.engine.orEmpty())
        .map { it.trim() }
        .filter { it.length >= 2 }
    if (tokens.isEmpty()) return null
    return product.fitmentLines.any { line ->
        tokens.any { token -> line.contains(token, ignoreCase = true) }
    }
}
