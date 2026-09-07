package co.zw.nissangtr.customer.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import co.zw.nissangtr.ui.shop.ShopWarmTheme

/**
 * Customer-app presentation wrapper. The warm commerce treatment is shared with the
 * operator POS kiosk so customer and counter experiences remain visibly one product.
 */
@Composable
fun CustomerShopTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    ShopWarmTheme(darkTheme = darkTheme, content = content)
}
