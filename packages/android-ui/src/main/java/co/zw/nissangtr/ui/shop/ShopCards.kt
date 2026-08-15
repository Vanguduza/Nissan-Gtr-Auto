package co.zw.nissangtr.ui.shop

/**
 * Forked from Shopping-By-KMP `presentation/component/ProductBox.kt` and
 * `presentation/ui/main/my_orders/MyOrdersScreen.kt` OrderBox density
 * (MIT © 2023 Mahdi Razzaghi Ghaleh). Coil/AsyncImage and Product domain types
 * omitted — host supplies [imageSlot]. Dimensions match KMP ProductBox.
 */

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import co.zw.nissangtr.ui.theme.GtrColors

/**
 * KMP [ProductBox] — width 180 / height 260, image 80% height, like circle top-end,
 * title + star row, price. [imageSlot] replaces Coil AsyncImage.
 */
@Composable
fun ShopProductCard(
    title: String,
    subtitle: String?,
    priceLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.width(180.dp),
    liked: Boolean = false,
    onLikeClick: (() -> Unit)? = null,
    ratingLabel: String? = null,
    cardHeight: Dp = 260.dp,
    imageSlot: (@Composable () -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .height(cardHeight)
            .padding(8.dp)
            .clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.8f)
                    .clip(MaterialTheme.shapes.small),
            ) {
                if (imageSlot != null) {
                    imageSlot()
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(GtrColors.Mist),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            title.take(12),
                            style = MaterialTheme.typography.labelMedium,
                            color = GtrColors.Steel,
                            maxLines = 2,
                        )
                    }
                }
                if (onLikeClick != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(GtrColors.Chalk)
                                .clickable(onClick = onLikeClick)
                                .padding(6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = "Wishlist",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (ratingLabel != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Star,
                            contentDescription = null,
                            tint = GtrColors.Warning,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(ratingLabel, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                priceLabel,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun ShopStatusChip(
    label: String,
    modifier: Modifier = Modifier,
    background: Color = GtrColors.Steel,
    contentColor: Color = GtrColors.PrimaryInk,
) {
    Surface(
        modifier = modifier,
        color = background,
        shape = MaterialTheme.shapes.extraSmall,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            maxLines = 1,
        )
    }
}

@Composable
fun ShopCircleBadge(
    text: String,
    modifier: Modifier = Modifier,
    background: Color = GtrColors.Steel,
    contentColor: Color = GtrColors.PrimaryInk,
) {
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = contentColor)
    }
}

/** Compact bordered list row (wishlist / history). */
@Composable
fun ShopListCard(
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    badges: @Composable (RowScope.() -> Unit)? = null,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, GtrColors.Mist, MaterialTheme.shapes.small)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            leading?.invoke()
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    badges?.invoke(this)
                }
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            trailing?.invoke()
        }
    }
}

/**
 * KMP [OrderBox] density — bordered expandable order/job card.
 */
@Composable
fun ShopOrderBox(
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    metaLabel: String? = null,
    metaValue: String? = null,
    badges: @Composable (RowScope.() -> Unit)? = null,
    thumbLabel: String? = null,
    expanded: Boolean = false,
    onToggleExpand: (() -> Unit)? = null,
    expandedContent: (@Composable ColumnScope.() -> Unit)? = null,
) {
    // Surface(onClick) — reliable card hit target (inner Column.clickable was easy to miss / nest-fight).
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, GtrColors.Mist, MaterialTheme.shapes.medium)
                .padding(vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (badges != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        content = badges,
                    )
                }
            }
            if (metaLabel != null || metaValue != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        metaLabel.orEmpty(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(metaValue.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(55.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(GtrColors.Mist),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        thumbLabel ?: title.take(3).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        color = GtrColors.Steel,
                    )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (subtitle != null) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (onToggleExpand != null) {
                        Text(
                            if (expanded) "Hide details" else "Show details",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable(onClick = onToggleExpand),
                        )
                    }
                }
            }
            if (expanded && expandedContent != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    content = expandedContent,
                )
            }
        }
    }
}
