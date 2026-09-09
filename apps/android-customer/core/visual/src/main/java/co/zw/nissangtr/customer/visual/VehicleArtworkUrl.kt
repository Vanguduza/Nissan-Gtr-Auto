package co.zw.nissangtr.customer.visual

private fun publicStorageBase(bucket: String): String =
    BuildConfig.SUPABASE_URL.trim().trimEnd('/').ifBlank {
        "https://bicyjghgdnzlnjqxzoud.supabase.co"
    } + "/storage/v1/object/public/$bucket/"

object VehicleArtworkStorage {
    fun publicUrl(assetPath: String): String {
        val filename = assetPath.substringAfterLast("/")
        return publicStorageBase("vehicle-artwork") + filename
    }
}

object ProductImageStorage {
    fun publicUrl(storagePath: String?): String? {
        val path = storagePath?.trim()?.trimStart('/')?.takeIf { it.isNotEmpty() } ?: return null
        return publicStorageBase("product-images") + path
    }
}
