package co.zw.nissangtr.ui.theme

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import co.zw.nissangtr.ui.R
import co.zw.nissangtr.ui.shop.ShopSectionHeader
import co.zw.nissangtr.ui.shop.ShopStaffContent
import co.zw.nissangtr.ui.shop.ShopStaffScreen
import co.zw.nissangtr.ui.shop.ShopStaffToolbar

/** Official storefront logo (`res/drawable/gtr_logo`). */
@Composable
fun GtrLogo(
    modifier: Modifier = Modifier,
    contentDescription: String? = "Nissan GTR Auto",
) {
    Image(
        painter = painterResource(R.drawable.gtr_logo),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = ContentScale.Fit,
    )
}

/**
 * Legacy shell — use [ShopStaffToolbar].
 * Retained so unmigrated screens compile until Phases B–D finish.
 */
@Deprecated(
    message = "Use ShopStaffToolbar / ShopDefaultScreen. Legacy Gtr* shells removed after all apps migrate off them.",
    replaceWith = ReplaceWith(
        "ShopStaffToolbar(title, modifier, subtitle, showLogo, trailing = trailing)",
        "co.zw.nissangtr.ui.shop.ShopStaffToolbar",
    ),
)
@Composable
fun GtrBrandBar(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    showLogo: Boolean = true,
    trailing: @Composable (() -> Unit)? = null,
) {
    ShopStaffToolbar(
        title = title,
        subtitle = subtitle,
        modifier = modifier,
        showLogo = showLogo,
        trailing = trailing,
    )
}

@Deprecated(
    message = "Use ShopStaffScreen or ShopDefaultScreen. Legacy Gtr* shells removed after all apps migrate.",
    replaceWith = ReplaceWith(
        "ShopStaffScreen(title, modifier, subtitle, showLogo, scrollable, headerTrailing = headerTrailing, content = content)",
        "co.zw.nissangtr.ui.shop.ShopStaffScreen",
    ),
)
@Composable
fun GtrScreen(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    scrollable: Boolean = true,
    showLogo: Boolean = true,
    headerTrailing: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    ShopStaffScreen(
        title = title,
        subtitle = subtitle,
        modifier = modifier,
        scrollable = scrollable,
        showLogo = showLogo,
        headerTrailing = headerTrailing,
        content = content,
    )
}

@Deprecated(
    message = "Use ShopStaffContent or ShopTabBody. Legacy Gtr* shells removed after all apps migrate.",
    replaceWith = ReplaceWith(
        "ShopStaffContent(modifier, scrollable, content = content)",
        "co.zw.nissangtr.ui.shop.ShopStaffContent",
    ),
)
@Composable
fun GtrFeatureBody(
    modifier: Modifier = Modifier,
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    ShopStaffContent(
        modifier = modifier,
        scrollable = scrollable,
        content = content,
    )
}

@Deprecated(
    message = "Use ShopSectionHeader. Legacy Gtr* shells removed after all apps migrate.",
    replaceWith = ReplaceWith(
        "ShopSectionHeader(title = text, actionLabel = null)",
        "co.zw.nissangtr.ui.shop.ShopSectionHeader",
    ),
)
@Composable
fun GtrSectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        ShopSectionHeader(title = text, actionLabel = null)
        HorizontalDivider(color = GtrColors.Mist)
    }
}

/**
 * No [GtrScaffold] ships in this module. Prefer [co.zw.nissangtr.ui.shop.ShopDefaultScreen]
 * or [co.zw.nissangtr.ui.shop.ShopStaffScreen].
 */
@Deprecated(
    message = "GtrScaffold was never a ShopKit primitive. Use ShopDefaultScreen / ShopStaffScreen.",
    level = DeprecationLevel.ERROR,
)
@Composable
fun GtrScaffold(
    @Suppress("UNUSED_PARAMETER") content: @Composable () -> Unit,
): Unit = error("Use ShopDefaultScreen or ShopStaffScreen — GtrScaffold is removed.")
