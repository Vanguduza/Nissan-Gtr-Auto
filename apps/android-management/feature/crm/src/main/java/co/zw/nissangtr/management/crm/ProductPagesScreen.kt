package co.zw.nissangtr.management.crm

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import co.zw.nissangtr.management.rpc.RpcClient
import co.zw.nissangtr.ui.shop.ShopHonestEmpty
import co.zw.nissangtr.ui.shop.ShopListCard
import co.zw.nissangtr.ui.shop.ShopPrimaryButton
import co.zw.nissangtr.ui.shop.ShopSecondaryButton
import co.zw.nissangtr.ui.shop.ShopStaffPanel
import co.zw.nissangtr.ui.shop.ShopStaffScreen
import java.io.File

/**
 * Staff product pages — price, discount+desc, main image (gallery pick or Storage path).
 * No HTML5 / WebView camera; gallery Intent only (Bridge-First for hardware elsewhere).
 */
@Composable
fun ProductPagesScreen(
    rpc: RpcClient,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProductPagesViewModel = viewModel(factory = ProductPagesViewModel.factory(rpc)),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val gallery = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val cache = File(context.cacheDir, "product-upload-${System.currentTimeMillis()}.jpg")
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                cache.outputStream().use { output -> input.copyTo(output) }
            }
            val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
            viewModel.uploadLocalImage(cache.absolutePath, mime)
        }
    }

    ShopStaffScreen(
        title = "Product pages",
        subtitle = "Price · discount · image",
        modifier = modifier,
        onBack = onBack,
    ) {
        ShopStaffPanel(title = "Find stock item") {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                label = { Text("OEM or title") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.busy,
            )
            ShopSecondaryButton(
                label = "Search",
                onClick = viewModel::refresh,
                enabled = !state.busy,
            )
            if (state.rows.isEmpty()) {
                ShopHonestEmpty(title = "No rows", body = "Try another query or refresh")
            }
            state.rows.forEach { row ->
                ShopListCard(
                    title = row.oemPartNumber,
                    subtitle = "${row.catalogTitle} · ${row.currency} ${row.unitPrice ?: "—"} · qty ${row.qtySaleable}",
                    onClick = { viewModel.select(row) },
                )
            }
        }

        state.selected?.let { sel ->
            ShopStaffPanel(title = "Edit ${sel.oemPartNumber}") {
                Text(
                    "Catalog title / OEM are read-only.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = state.priceText,
                    onValueChange = viewModel::onPriceChange,
                    label = { Text("Unit price (USD)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !state.busy,
                )
                Text("Discount kind", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("none", "percent", "amount").forEach { kind ->
                        FilterChip(
                            selected = state.discountKind == kind,
                            onClick = { viewModel.onDiscountKindChange(kind) },
                            label = { Text(kind) },
                            enabled = !state.busy,
                        )
                    }
                }
                OutlinedTextField(
                    value = state.discountValueText,
                    onValueChange = viewModel::onDiscountValueChange,
                    label = { Text("Discount value") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !state.busy,
                )
                OutlinedTextField(
                    value = state.discountDescription,
                    onValueChange = viewModel::onDiscountDescriptionChange,
                    label = { Text("Discount description") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.busy,
                )
                ShopPrimaryButton(
                    label = "Save price / discount",
                    onClick = viewModel::save,
                    enabled = !state.busy,
                )

                Text("Main image", style = MaterialTheme.typography.titleSmall)
                ShopSecondaryButton(
                    label = "Pick from gallery",
                    onClick = { gallery.launch("image/*") },
                    enabled = !state.busy,
                )
                OutlinedTextField(
                    value = state.imagePathText,
                    onValueChange = viewModel::onImagePathChange,
                    label = { Text("Or Storage path (product-images/…)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !state.busy,
                )
                ShopSecondaryButton(
                    label = "Register path as primary",
                    onClick = viewModel::registerImagePathAsPrimary,
                    enabled = !state.busy,
                )
                state.images.forEach { img ->
                    ShopListCard(
                        title = if (img.isPrimary) "★ ${img.storagePath}" else img.storagePath,
                        subtitle = img.id.take(8),
                        onClick = { viewModel.setPrimary(img.id) },
                    )
                }
            }
        }

        state.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}
