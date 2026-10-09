package co.zw.nissangtr.customer.shell

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import co.zw.nissangtr.customer.R

/**
 * Preview-locked top chrome:
 * hamburger LEFT · centered Nissan GTR Auto logo · cart RIGHT.
 *
 * [onOpenAccount] remains for caller source compatibility. Account is intentionally not rendered
 * in the top bar; it belongs to bottom navigation in the approved customer IA.
 */
@Composable
fun CustomerShellTopBar(
    cartBadgeCount: Int,
    onOpenMenu: () -> Unit,
    onOpenAccount: () -> Unit,
    onOpenCart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    @Suppress("UNUSED_VARIABLE")
    val keepSourceCompatible = onOpenAccount

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 8.dp),
    ) {
        IconButton(
            onClick = onOpenMenu,
            modifier = Modifier.align(Alignment.CenterStart).size(48.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Menu,
                contentDescription = "Menu",
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(28.dp),
            )
        }

        Image(
            painter = painterResource(R.drawable.gtr_logo_clear),
            contentDescription = "Nissan GTR Auto",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .align(Alignment.Center)
                .height(52.dp)
                .fillMaxWidth(.52f),
        )

        IconButton(
            onClick = onOpenCart,
            modifier = Modifier.align(Alignment.CenterEnd).size(48.dp),
        ) {
            BadgedBox(
                badge = {
                    if (cartBadgeCount > 0) {
                        Badge(containerColor = MaterialTheme.colorScheme.primary) {
                            Text(
                                text = if (cartBadgeCount > 99) "99+" else "$cartBadgeCount",
                                color = Color.White,
                            )
                        }
                    }
                },
            ) {
                Icon(
                    imageVector = Icons.Filled.ShoppingCart,
                    contentDescription = "Cart",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(27.dp),
                )
            }
        }
    }
}

/* Kept because other shell files may still import these helpers. */
@Composable
fun ShellIconOnlyButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(onClick = onClick, modifier = modifier.size(48.dp)) {
        Icon(icon, contentDescription, tint = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
fun ShellLabeledIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(onClick = onClick, modifier = modifier.size(48.dp)) {
        Icon(icon, label, tint = MaterialTheme.colorScheme.onBackground)
    }
}
