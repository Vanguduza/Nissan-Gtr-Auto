package co.zw.nissangtr.pos.lookup

enum class FinderMode(val label: String) {
    SCAN_OEM("SCAN/OEM"),
    SHOP_STOCK("SHOP STOCK"),
    EPC("EPC"),
    VIN_PNC("VIN/PNC"),
}
