package co.zw.nissangtr.customer.visual

import androidx.compose.ui.graphics.asImageBitmap

/**
 * Model-family vehicle artwork ships inside the app (`assets/vehicles/`), so it shows offline and
 * does not depend on a Storage bucket. Coil loads `file:///android_asset/...` directly.
 */
object VehicleArtworkStorage {
    fun publicUrl(assetPath: String): String =
        "file:///android_asset/vehicles/" + assetPath.substringAfterLast("/")
}

/**
 * Staff-owned merchandise photos in the `product-images` Storage bucket of the configured
 * Supabase project. [configure] is called once at start-up with the build's SUPABASE_URL; until
 * then (previews, tests) no URL is produced and cards show their placeholder.
 */
object ProductImageStorage {
    @Volatile private var base: String? = null

    fun configure(supabaseUrl: String) {
        base = supabaseUrl.trim().trimEnd('/').takeIf { it.startsWith("https://") }
            ?.let { "$it/storage/v1/object/public/product-images/" }
    }

    fun publicUrl(storagePath: String?): String? {
        val path = storagePath?.trim()?.trimStart('/')?.takeIf { it.isNotEmpty() } ?: return null
        return base?.let { it + path }
    }
}

/**
 * Draws bundled vehicle artwork (`assets/vehicles/`) directly: no loader frame, works offline
 * and in previews. Shows [fallback] when the file is missing or cannot be decoded.
 */
@androidx.compose.runtime.Composable
fun VehicleArtworkImage(
    assetPath: String,
    contentDescription: String?,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    contentScale: androidx.compose.ui.layout.ContentScale = androidx.compose.ui.layout.ContentScale.Fit,
    fallback: @androidx.compose.runtime.Composable () -> Unit = {},
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val file = "vehicles/" + assetPath.substringAfterLast("/")
    val bitmap = androidx.compose.runtime.remember(file) {
        runCatching {
            context.assets.open(file).use { android.graphics.BitmapFactory.decodeStream(it) }
        }.getOrNull()
    }
    if (bitmap == null) {
        fallback()
    } else {
        androidx.compose.foundation.Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
        )
    }
}
