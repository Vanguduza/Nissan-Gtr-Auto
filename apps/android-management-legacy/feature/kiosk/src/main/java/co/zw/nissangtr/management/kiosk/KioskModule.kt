package co.zw.nissangtr.management.kiosk

/**
 * Tablet kiosk shell — Lock Task / Device Owner hooks, idle lock, Device Admin console.
 * Phone flavor may reuse idle prefs/UI but must not own Device Owner / Magisk / Lock Task.
 */
object KioskModule {
    const val id: String = "kiosk"
}
