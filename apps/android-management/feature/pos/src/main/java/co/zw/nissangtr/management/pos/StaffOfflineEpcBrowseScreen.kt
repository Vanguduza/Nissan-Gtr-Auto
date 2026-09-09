package co.zw.nissangtr.management.pos

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import co.zw.nissangtr.management.pos.offline.InMemoryOfflinePosStore
import co.zw.nissangtr.management.pos.offline.SqlCipherOfflinePosStore
import co.zw.nissangtr.ui.shop.ShopHonestEmpty

/** Staff-portal EPC reader. It deliberately uses the tablet-local encrypted bundle only. */
@Composable
fun StaffOfflineEpcBrowseScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val store = remember {
        runCatching { SqlCipherOfflinePosStore.open(context) }
            .getOrElse { InMemoryOfflinePosStore() }
    }
    DisposableEffect(store) { onDispose { store.close() } }
    if (!store.hasEpcCatalog()) {
        ShopHonestEmpty(
            title = "Offline EPC bundle not available",
            body = "From POS → Settings, connect once and refresh the full offline EPC catalog.",
            modifier = modifier,
        )
        return
    }
    PosEpcBrowseScreen(
        source = StoreEpcCatalogSource(store),
        onBack = onBack,
        onSelectOem = { /* staff portal browse is read-only; sales add remains in POS */ },
        modifier = modifier,
    )
}
