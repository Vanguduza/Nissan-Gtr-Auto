package co.zw.nissangtr.customer.visual

object VehicleArtworkStorage {
    const val PUBLIC_BASE =
        "https://gylrgwqyuiwkyykardwc.supabase.co/storage/v1/object/public/vehicle-artwork/"

    fun publicUrl(assetPath: String): String {
        val filename = assetPath.substringAfterLast("/")
        return PUBLIC_BASE + filename
    }
}


object ProductImageStorage {
    const val PUBLIC_BASE =
        "https://gylrgwqyuiwkyykardwc.supabase.co/storage/v1/object/public/product-images/"

    fun publicUrl(storagePath: String?): String? {
        val path = storagePath?.trim()?.trimStart('/')?.takeIf { it.isNotEmpty() } ?: return null
        return PUBLIC_BASE + path
    }
}
