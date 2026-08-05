package co.zw.nissangtr.customer.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalIndication
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.zw.nissangtr.ui.theme.GtrLogo

/**
 * Customer storefront top strip — icon-over-label actions (same pattern as bottom bar),
 * no rounded/circle chrome. Logo is larger than the action icons.
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
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            ShellLabeledIconButton(
                icon = Icons.Filled.Menu,
                label = "Menu",
                onClick = onOpenMenu,
            )
            GtrLogo(
                modifier = Modifier
                    .height(40.dp)
                    .widthIn(min = 110.dp, max = 140.dp),
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            ShellLabeledIconButton(
                icon = Icons.Filled.AccountCircle,
                label = "My Account",
                onClick = onOpenAccount,
            )
            BadgedBox(
                badge = {
                    if (cartBadgeCount > 0) {
                        Badge { Text(if (cartBadgeCount > 99) "99+" else "$cartBadgeCount") }
                    }
                },
            ) {
                ShellLabeledIconButton(
                    icon = Icons.Filled.ShoppingCart,
                    label = "Cart",
                    onClick = onOpenCart,
                )
            }
            if (signedInEmail != null) {
                ShellLabeledIconButton(
                    icon = Icons.Filled.AccountCircle,
                    label = "Signed in",
                    onClick = onOpenAccount,
                )
            } else {
                ShellLabeledIconButton(
                    icon = Icons.Filled.Login,
                    label = "Sign in",
                    onClick = onSignIn,
                )
            }
        }
    }
}

/** Bottom-nav style: icon above label, flat (no rounded border / circle). */
@Composable
fun ShellLabeledIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val indication = LocalIndication.current
    Column(
        modifier = modifier
            .clickable(
                interactionSource = interaction,
                indication = indication,
                onClick = onClick,
            )
            .padding(horizontal = 6.dp, vertical = 4.dp)
            .widthIn(min = 52.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
