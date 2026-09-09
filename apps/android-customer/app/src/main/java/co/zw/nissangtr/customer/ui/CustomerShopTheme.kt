package co.zw.nissangtr.customer.ui

import androidx.compose.runtime.Composable
import co.zw.nissangtr.customer.visual.PremiumCustomerTheme
import co.zw.nissangtr.ui.shop.ShopTheme

/**
 * Preview-locked customer theme.
 *
 * Customer Android is intentionally dark-first. The [darkTheme] argument remains only
 * for source compatibility with the existing Settings flow; customer presentation is
 * always the approved premium dark storefront.
 *
 * Shared ShopKit remains untouched.
 */
@Composable
fun CustomerShopTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    ShopTheme(darkTheme = true) {
        PremiumCustomerTheme(content)
    }
}
