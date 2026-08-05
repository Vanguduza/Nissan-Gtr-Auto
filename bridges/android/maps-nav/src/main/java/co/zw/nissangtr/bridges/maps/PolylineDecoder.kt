package co.zw.nissangtr.bridges.maps

/**
 * Google encoded polyline decoder (Directions API `overview_polyline.points`).
 * Algorithm: https://developers.google.com/maps/documentation/utilities/polylinealgorithm
 */
object PolylineDecoder {
    fun decode(encoded: String): List<MapLatLng> {
        if (encoded.isBlank()) return emptyList()
        val out = ArrayList<MapLatLng>()
        var index = 0
        var lat = 0
        var lng = 0
        while (index < encoded.length) {
            var result = 0
            var shift = 0
            var b: Int
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20 && index < encoded.length)
            val dlat = if ((result and 1) != 0) (result shr 1).inv() else result shr 1
            lat += dlat

            result = 0
            shift = 0
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20 && index < encoded.length)
            val dlng = if ((result and 1) != 0) (result shr 1).inv() else result shr 1
            lng += dlng

            out.add(MapLatLng(lat / 1e5, lng / 1e5))
        }
        return out
    }
}
