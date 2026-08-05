package co.zw.nissangtr.customer.reviews

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import co.zw.nissangtr.bridges.podcamera.PodCameraBridge
import co.zw.nissangtr.customer.rpc.ProductReview
import co.zw.nissangtr.customer.rpc.RpcClient
import co.zw.nissangtr.ui.shop.ShopDefaultScreen
import co.zw.nissangtr.ui.shop.ShopHonestEmpty
import co.zw.nissangtr.ui.shop.ShopRatingRow
import co.zw.nissangtr.ui.shop.ShopSectionHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * PDP-scoped reviews — list approved + submit with Bridge-First photo attach.
 * Reviews live on the product page, not the Account hub.
 */
@Composable
fun PdpReviewsScreen(
    rpc: RpcClient,
    camera: PodCameraBridge?,
    oem: String,
    stockItemId: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReviewsViewModel = viewModel(
        factory = ReviewsViewModel.factory(rpc, camera, oem, stockItemId),
    ),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sharp = MaterialTheme.shapes.extraSmall

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val copied = withContext(Dispatchers.IO) { copyUriToCache(context, uri) }
            if (copied != null) {
                viewModel.attachPhotoFromPath(copied.first, copied.second)
            }
        }
    }

    ShopDefaultScreen(
        title = "Reviews",
        subtitle = oem,
        onBack = onBack,
        modifier = modifier,
    ) {
        Text(
            "Customer reviews for this part — submit from here, not Account.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            ShopRatingRow(
                avgRating = state.stats?.avgRating ?: 0.0,
                reviewCount = state.stats?.reviewCount ?: 0,
            )
            OutlinedButton(onClick = viewModel::loadPdp, enabled = !state.busy) {
                Text("Refresh")
            }
        }

        ShopSectionHeader(title = "Approved reviews", actionLabel = null)
        if (state.approved.isEmpty() && !state.busy) {
            ShopHonestEmpty(
                title = "No approved reviews yet",
                body = "Be the first to review this OEM after purchase.",
            )
        } else {
            state.approved.forEach { ReviewRow(it) }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        ShopSectionHeader(title = "Write a review", actionLabel = null)
        Text("Rating: ${state.rating}", style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = state.rating.toFloat(),
            onValueChange = { viewModel.onRatingChange(it.toInt()) },
            valueRange = 1f..5f,
            steps = 3,
            enabled = !state.busy,
        )
        OutlinedTextField(
            value = state.body,
            onValueChange = viewModel::onBodyChange,
            label = { Text("Comment (optional)") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            enabled = !state.busy,
            shape = sharp,
        )
        Button(
            onClick = viewModel::submit,
            enabled = !state.busy && oem.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            shape = sharp,
        ) { Text("Submit review") }

        state.lastSubmittedId?.let {
            ShopSectionHeader(title = "Attach photo", actionLabel = null)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (viewModel.cameraAvailable) {
                    Button(
                        onClick = viewModel::captureWithBridge,
                        enabled = !state.busy,
                        shape = sharp,
                    ) { Text("Camera (bridge)") }
                }
                OutlinedButton(
                    onClick = { galleryLauncher.launch("image/*") },
                    enabled = !state.busy,
                    shape = sharp,
                ) { Text("Gallery") }
            }
            Text(
                if (viewModel.cameraAvailable) {
                    "Bridge-First camera via bridges/android/pod-camera — not WebView."
                } else {
                    "Camera bridge not attached — gallery only."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        state.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun ReviewRow(review: ProductReview) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(review.label, style = MaterialTheme.typography.bodyMedium)
            Text(
                review.status.rpcValue,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text("★".repeat(review.rating.coerceIn(1, 5)), style = MaterialTheme.typography.bodySmall)
        if (review.body.isNotBlank()) {
            Text(review.body, style = MaterialTheme.typography.bodySmall)
        }
        HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
    }
}

private fun copyUriToCache(context: Context, uri: Uri): Pair<String, String>? {
    return try {
        val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
        val ext = when {
            mime.contains("png") -> "png"
            mime.contains("webp") -> "webp"
            else -> "jpg"
        }
        val out = File(context.cacheDir, "review-photo-${System.currentTimeMillis()}.$ext")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(out).use { output -> input.copyTo(output) }
        } ?: return null
        Pair(out.absolutePath, mime)
    } catch (_: Exception) {
        null
    }
}
