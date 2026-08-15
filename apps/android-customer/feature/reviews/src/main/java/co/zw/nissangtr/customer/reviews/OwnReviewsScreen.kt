package co.zw.nissangtr.customer.reviews

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import co.zw.nissangtr.customer.rpc.ProductReview
import co.zw.nissangtr.customer.rpc.RpcClient
import co.zw.nissangtr.ui.shop.ShopDefaultScreen
import co.zw.nissangtr.ui.shop.ShopHonestEmpty
import co.zw.nissangtr.ui.shop.ShopSectionHeader

/**
 * Account hub — lists the signed-in customer's own reviews (web `/account/reviews` parity).
 * PDP submit flow stays on [PdpReviewsScreen].
 */
@Composable
fun OwnReviewsScreen(
    rpc: RpcClient,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReviewsViewModel = viewModel(
        factory = ReviewsViewModel.factory(rpc, camera = null),
    ),
) {
    val state by viewModel.state.collectAsState()

    ShopDefaultScreen(
        title = "My reviews",
        subtitle = null,
        onBack = onBack,
        modifier = modifier,
        loading = state.busy && state.ownReviews.isEmpty(),
    ) {
        ShopSectionHeader(title = "Submitted", actionLabel = null)
        OutlinedButton(
            onClick = viewModel::refreshOwn,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Refresh")
        }
        if (state.ownReviews.isEmpty() && !state.busy) {
            ShopHonestEmpty(
                title = "No reviews yet",
                body = "Submit a review from a product page after you buy a part.",
            )
        } else {
            state.ownReviews.forEach { review ->
                OwnReviewRow(review)
                HorizontalDivider()
            }
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun OwnReviewRow(review: ProductReview) {
    Text(
        "${review.oemPartNumber ?: "Part"} · ${review.rating}/5 · ${review.status}",
        style = MaterialTheme.typography.titleSmall,
    )
    review.body.takeIf { it.isNotBlank() }?.let { body ->
        Text(
            body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
