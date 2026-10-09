package co.zw.nissangtr.customer.ui

import androidx.compose.runtime.Composable
import co.zw.nissangtr.customer.visual.CustomerStyle
import co.zw.nissangtr.customer.visual.PremiumCustomerTheme
import co.zw.nissangtr.ui.shop.ShopTheme

/**
 * Customer theme: two styles the customer picks in Settings, each in light and dark.
 * - [CustomerStyle.Illustrated]: the preview-locked premium storefront (dark is the locked look).
 * - [CustomerStyle.Pos]: the counter (POS) look: canvas, cards with red lining.
 *
 * All four render the same screens and components; only the palette changes. Shared ShopKit
 * typography is kept.
 */
@Composable
fun CustomerShopTheme(
    style: CustomerStyle = CustomerStyle.Illustrated,
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    ShopTheme(darkTheme = darkTheme) {
        PremiumCustomerTheme(style = style, darkTheme = darkTheme, content = content)
    }
}
