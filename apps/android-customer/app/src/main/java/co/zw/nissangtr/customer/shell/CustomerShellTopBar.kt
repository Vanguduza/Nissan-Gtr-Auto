package co.zw.nissangtr.customer.shell

import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.zw.nissangtr.customer.R

/**
 * Large logo flush LEFT (transparent asset); icon-only Menu / Account / Cart on the RIGHT.
 * Sign-in lives in Settings / Account.
 */
@Composable
fun CustomerShellTopBar(
    cartBadgeCount: Int,
    onOpenMenu: () -> Unit,
    onOpenAccount: () -> Unit,
    onOpenCart: () -> Unit,
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
        Image(
            painter = painterResource(R.drawable.gtr_logo_clear),
            contentDescription = "Nissan GTR Auto",
            contentScale = ContentScale.Fit,
            alignment = Alignment.CenterStart,
            modifier = Modifier
                .height(76.dp)
                .widthIn(max = 220.dp)
                .padding(start = 2.dp)
                .background(Color.Transparent),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            ShellIconOnlyButton(
                icon = Icons.Filled.Menu,
                contentDescription = "Menu",
                onClick = onOpenMenu,
            )
            ShellIconOnlyButton(
                icon = Icons.Filled.AccountCircle,
                contentDescription = "My Account",
                onClick = onOpenAccount,
            )
            BadgedBox(
                badge = {
                    if (cartBadgeCount > 0) {
                        Badge { Text(if (cartBadgeCount > 99) "99+" else "$cartBadgeCount") }
                    }
                },
            ) {
                ShellIconOnlyButton(
                    icon = Icons.Filled.ShoppingCart,
                    contentDescription = "Cart",
                    onClick = onOpenCart,
                )
            }
        }
    }
}

@Composable
fun ShellIconOnlyButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val indication = LocalIndication.current
    Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        tint = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .clickable(
                interactionSource = interaction,
                indication = indication,
                onClick = onClick,
            )
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .size(26.dp),
    )
}

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
