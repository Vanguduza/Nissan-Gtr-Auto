package co.zw.nissangtr.ui.shop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import co.zw.nissangtr.ui.theme.GtrColors

/**
 * Coil-backed remote image for catalog PDP / PLP.
 * Host passes URLs from Supabase Storage or CDN — no WebView.
 */
@Composable
fun ShopRemoteImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    placeholderLabel: String? = null,
) {
    if (url.isNullOrBlank()) {
        Box(
            modifier = modifier.background(GtrColors.Mist),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                placeholderLabel ?: "No image",
                style = MaterialTheme.typography.labelSmall,
                color = GtrColors.Steel,
            )
        }
        return
    }
    AsyncImage(
        model = url,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
    )
}

@Composable
fun ShopProductGalleryHero(
    imageUrls: List<String>,
    heroLabel: String?,
    modifier: Modifier = Modifier,
    selectedIndex: Int = 0,
) {
    val urls = imageUrls.filter { it.isNotBlank() }.distinct()
    val heroUrl = urls.getOrNull(selectedIndex.coerceIn(0, (urls.size - 1).coerceAtLeast(0)))
    Box(modifier = modifier) {
        if (heroUrl != null) {
            ShopRemoteImage(
                url = heroUrl,
                contentDescription = heroLabel,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(GtrColors.Mist),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    heroLabel ?: "No image",
                    style = MaterialTheme.typography.headlineMedium,
                    color = GtrColors.Steel,
                )
            }
        }
    }
}
