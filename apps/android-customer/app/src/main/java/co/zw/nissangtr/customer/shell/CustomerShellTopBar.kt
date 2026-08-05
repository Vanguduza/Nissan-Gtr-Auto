package co.zw.nissangtr.customer.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import co.zw.nissangtr.ui.shop.ShopCircleIconButton
import co.zw.nissangtr.ui.theme.GtrLogo

/**
 * Customer storefront top strip — menu + GTR logo left; My Account / Cart / Sign-in right.
 * Screen-local (frozen android-ui has no shell strip primitive).
 */
@Composable
fun CustomerShellTopBar(
    signedInEmail: String?,
    cartBadgeCount: Int,
    onOpenMenu: () -> Unit,
    onOpenAccount: () -> Unit,
    onOpenCart: () -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ShopCircleIconButton(
                imageVector = Icons.Filled.Menu,
                onClick = onOpenMenu,
                contentDescription = "Menu",
            )
            GtrLogo(modifier = Modifier.height(28.dp).width(96.dp))
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            TextButton(onClick = onOpenAccount) {
                Text("My Account", style = MaterialTheme.typography.labelMedium)
            }
            BadgedBox(
                badge = {
                    if (cartBadgeCount > 0) {
                        Badge { Text(if (cartBadgeCount > 99) "99+" else "$cartBadgeCount") }
                    }
                },
            ) {
                ShopCircleIconButton(
                    imageVector = Icons.Filled.ShoppingCart,
                    onClick = onOpenCart,
                    contentDescription = "Cart",
                )
            }
            if (signedInEmail != null) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                        .clickable(onClick = onOpenAccount),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        signedInEmail.take(2).uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            } else {
                TextButton(onClick = onSignIn) {
                    Icon(
                        Icons.Filled.AccountCircle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(" Sign in", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
