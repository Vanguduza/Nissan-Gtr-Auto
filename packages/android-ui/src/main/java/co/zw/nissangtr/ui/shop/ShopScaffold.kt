package co.zw.nissangtr.ui.shop

/**
 * Forked from Shopping-By-KMP `presentation/component/DefaultScreenUI.kt`
 * (MIT © 2023 Mahdi Razzaghi Ghaleh). Android Compose adaptation — no KMP
 * ProgressBarState / NetworkState / UIComponent queue; overlays are optional
 * host callbacks. Visual structure preserved: Scaffold + circle toolbar buttons
 * + chalk body. GTR brand tokens replace KMP `#FF4747` / Lato.
 */

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import co.zw.nissangtr.ui.theme.LocalGtrExtras

/**
 * KMP [DefaultScreenUI] toolbar row — circle start/end actions + centered title.
 * Not a steel Jetsnack bar.
 */
@Composable
fun ShopTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        if (onBack != null) {
            ShopCircleIconButton(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                onClick = onBack,
                contentDescription = "Back",
            )
        } else {
            Spacer(modifier = Modifier.size(50.dp))
        }
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Box(
            modifier = Modifier.width(50.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        }
    }
}

/**
 * Full-page shell forked from KMP [DefaultScreenUI]:
 * Scaffold → optional toolbar → chalk content box → optional sticky bottom /
 * loading / offline overlays (domain queue omitted; host supplies flags).
 */
@Composable
fun ShopDefaultScreen(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    scrollable: Boolean = true,
    loading: Boolean = false,
    networkFailed: Boolean = false,
    onTryAgain: (() -> Unit)? = null,
    topBarActions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val extras = LocalGtrExtras.current
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ShopTopBar(
                title = title,
                subtitle = subtitle,
                onBack = onBack,
                actions = topBarActions,
            )
        },
        bottomBar = { bottomBar?.invoke() },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center,
        ) {
            val bodyMod = Modifier
                .fillMaxSize()
                .padding(extras.screenPadding)
                .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
            Column(
                modifier = bodyMod,
                verticalArrangement = Arrangement.spacedBy(extras.sectionGap),
                content = content,
            )
            if (networkFailed) {
                ShopFailedNetworkScreen(onTryAgain = onTryAgain ?: {})
            }
            if (loading) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/** Body-only under app Scaffold bottom nav — KMP tab content, no second toolbar. */
@Composable
fun ShopTabBody(
    modifier: Modifier = Modifier,
    scrollable: Boolean = true,
    contentPadding: PaddingValues? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val extras = LocalGtrExtras.current
    val pad = contentPadding ?: PaddingValues(extras.screenPadding)
    val bodyMod = modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .padding(pad)
        .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
    Column(
        modifier = bodyMod,
        verticalArrangement = Arrangement.spacedBy(extras.sectionGap),
        content = content,
    )
}

/** KMP [FailedNetworkScreen] — offline overlay inside DefaultScreenUI. */
@Composable
fun ShopFailedNetworkScreen(
    onTryAgain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "You are currently offline, please reconnect and try again.",
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))
        ShopPrimaryButton(
            label = "Try Again",
            onClick = onTryAgain,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
        )
    }
}

@Composable
fun ShopIconAction(
    imageVector: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
) {
    ShopCircleIconButton(
        imageVector = imageVector,
        onClick = onClick,
        contentDescription = contentDescription,
        modifier = modifier,
        tint = if (tint == Color.Unspecified) {
            MaterialTheme.colorScheme.onBackground
        } else {
            tint
        },
    )
}

@Composable
fun ShopOutlinedActionRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}
