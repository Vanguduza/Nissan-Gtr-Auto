package co.zw.nissangtr.customer.reviews

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import co.zw.nissangtr.bridges.podcamera.PodCameraBridge
import co.zw.nissangtr.customer.rpc.ProductReview
import co.zw.nissangtr.customer.rpc.RpcClient
import co.zw.nissangtr.customer.visual.GtrPremiumColors
import co.zw.nissangtr.customer.visual.PremiumEmptyState
import co.zw.nissangtr.customer.visual.PremiumMessageBanner
import co.zw.nissangtr.customer.visual.PremiumMessageKind
import co.zw.nissangtr.customer.visual.PremiumPrimaryButton
import co.zw.nissangtr.customer.visual.PremiumScreenHeader
import co.zw.nissangtr.customer.visual.PremiumSecondaryButton
import co.zw.nissangtr.customer.visual.PremiumStatusChip
import co.zw.nissangtr.customer.visual.PremiumStatusTone
import co.zw.nissangtr.customer.visual.PremiumSurfaceCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * PDP-scoped review surface. Internal OEM identity is still passed into the ViewModel but is never
 * shown to the customer.
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

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val copied = withContext(Dispatchers.IO) { copyUriToCache(context, uri) }
            copied?.let { viewModel.attachPhotoFromPath(it.first, it.second) }
        }
    }

    Column(modifier.fillMaxSize().background(GtrPremiumColors.Background)) {
        PremiumScreenHeader(
            title = "Reviews",
            subtitle = "Customer experiences with this product",
            onBack = onBack,
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                PremiumSurfaceCard {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Overall rating",
                            color = GtrPremiumColors.TextSecondary,
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Text(
                            if ((state.stats?.reviewCount ?: 0) == 0) {
                                "No ratings yet"
                            } else {
                                "%.1f ★".format(state.stats?.avgRating ?: 0.0)
                            },
                            color = GtrPremiumColors.TextPrimary,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "${state.stats?.reviewCount ?: 0} approved review(s)",
                            color = GtrPremiumColors.TextSecondary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            if (state.approved.isEmpty() && !state.busy) {
                item {
                    PremiumEmptyState(
                        title = "No reviews yet",
                        body = "Be the first customer to share your experience.",
                    )
                }
            } else {
                items(state.approved, key = { it.id }) { review ->
                    ReviewCard(review)
                }
            }

            item {
                PremiumSurfaceCard {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "Write a review",
                            color = GtrPremiumColors.TextPrimary,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "${state.rating} / 5",
                            color = GtrPremiumColors.RedBright,
                            fontWeight = FontWeight.Bold,
                        )
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
                            label = { Text("Tell other customers about this product") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            enabled = !state.busy,
                        )
                        PremiumPrimaryButton(
                            text = if (state.busy) "Submitting…" else "Submit review",
                            onClick = viewModel::submit,
                            enabled = !state.busy && oem.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            state.lastSubmittedId?.let {
                item {
                    PremiumSurfaceCard {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "Add a real product photo",
                                color = GtrPremiumColors.TextPrimary,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "Photos help other customers understand the actual item.",
                                color = GtrPremiumColors.TextSecondary,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (viewModel.cameraAvailable) {
                                    PremiumPrimaryButton(
                                        "Camera",
                                        viewModel::captureWithBridge,
                                        enabled = !state.busy,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                PremiumSecondaryButton(
                                    "Gallery",
                                    { galleryLauncher.launch("image/*") },
                                    enabled = !state.busy,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }

            item {
                PremiumSecondaryButton(
                    text = "Refresh reviews",
                    onClick = viewModel::loadPdp,
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            state.message?.let { item { PremiumMessageBanner(it, PremiumMessageKind.Success) } }
            state.error?.let { item { PremiumMessageBanner(it, PremiumMessageKind.Error) } }
        }
    }
}

@Composable
private fun ReviewCard(review: ProductReview) {
    PremiumSurfaceCard {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Text(
                    review.description?.trim()?.takeIf { it.isNotEmpty() } ?: "Verified customer",
                    color = GtrPremiumColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                PremiumStatusChip(
                    review.status.rpcValue.replaceFirstChar { it.uppercase() },
                    PremiumStatusTone.Neutral,
                )
            }
            Text(
                "★".repeat(review.rating.coerceIn(1, 5)),
                color = GtrPremiumColors.RedBright,
            )
            if (review.body.isNotBlank()) {
                Text(review.body, color = GtrPremiumColors.TextSecondary)
            }
        }
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
