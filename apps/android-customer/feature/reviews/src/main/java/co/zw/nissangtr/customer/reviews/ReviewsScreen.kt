package co.zw.nissangtr.customer.reviews

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import co.zw.nissangtr.customer.rpc.RpcNames
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Product reviews — submit / list (own + approved by OEM) / stats / photo attach.
 * Photos: Bridge-First [PodCameraBridge] when available, else gallery picker
 * → Storage `review-photos` + [RpcNames.ADD_CUSTOMER_PRODUCT_REVIEW_PHOTO].
 */
@Composable
fun ReviewsScreen(
    rpc: RpcClient,
    camera: PodCameraBridge?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReviewsViewModel = viewModel(factory = ReviewsViewModel.factory(rpc, camera)),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val copied = withContext(Dispatchers.IO) {
                copyUriToCache(context, uri)
            }
            if (copied == null) {
                return@launch
            }
            viewModel.attachPhotoFromPath(copied.first, copied.second)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Reviews", style = MaterialTheme.typography.headlineSmall)
        Text(
            "RPCs: ${RpcNames.SUBMIT_CUSTOMER_PRODUCT_REVIEW}, " +
                "${RpcNames.GET_PRODUCT_REVIEW_STATS}, " +
                RpcNames.ADD_CUSTOMER_PRODUCT_REVIEW_PHOTO,
            style = MaterialTheme.typography.bodySmall,
        )

        Text("PDP stats (OEM)", style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(
            value = state.oem,
            onValueChange = viewModel::onOemChange,
            label = { Text("OEM part number") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !state.busy,
        )
        Button(
            onClick = viewModel::loadPdp,
            enabled = !state.busy && state.oem.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Load stats + approved") }
        state.stats?.let { stats ->
            Text("Avg rating: ${stats.avgRating}", style = MaterialTheme.typography.bodyMedium)
            Text("Approved count: ${stats.reviewCount}", style = MaterialTheme.typography.bodyMedium)
        }

        Text("Approved for OEM", style = MaterialTheme.typography.titleSmall)
        if (state.approved.isEmpty()) {
            Text("None loaded", style = MaterialTheme.typography.bodySmall)
        }
        state.approved.forEach { ReviewRow(it) }

        Text("My reviews", style = MaterialTheme.typography.titleSmall)
        if (state.ownReviews.isEmpty()) {
            Text("No reviews yet", style = MaterialTheme.typography.bodySmall)
        }
        state.ownReviews.forEach { ReviewRow(it) }

        Text("Submit review", style = MaterialTheme.typography.titleSmall)
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
        )
        Button(
            onClick = viewModel::submit,
            enabled = !state.busy && state.oem.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Submit") }

        state.lastSubmittedId?.let { id ->
            Text("Attach photo to last pending", style = MaterialTheme.typography.titleSmall)
            Text(id, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (viewModel.cameraAvailable) {
                    Button(
                        onClick = viewModel::captureWithBridge,
                        enabled = !state.busy,
                    ) { Text("Camera (bridge)") }
                }
                OutlinedButton(
                    onClick = { galleryLauncher.launch("image/*") },
                    enabled = !state.busy,
                ) { Text("Gallery") }
            }
            Text(
                if (viewModel.cameraAvailable) {
                    "Bridge-First camera via bridges/android/pod-camera — not WebView."
                } else {
                    "Camera bridge not attached — gallery only."
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }

        OutlinedButton(onClick = viewModel::refreshOwn, enabled = !state.busy) {
            Text("Refresh my reviews")
        }
        state.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        OutlinedButton(onClick = onBack) { Text("Back") }
    }
}

@Composable
private fun ReviewRow(review: ProductReview) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(review.label, style = MaterialTheme.typography.bodyMedium)
            Text(review.status.rpcValue, style = MaterialTheme.typography.bodySmall)
        }
        Text("★".repeat(review.rating.coerceIn(1, 5)), style = MaterialTheme.typography.bodySmall)
        if (review.body.isNotBlank()) {
            Text(review.body, style = MaterialTheme.typography.bodySmall)
        }
        HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
    }
}

/** Copy gallery Uri into cache for Storage upload (no WebView camera). */
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
