package co.zw.nissangtr.customer.visual

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun PremiumScreenHeader(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(GtrPremiumColors.Background)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = GtrPremiumColors.TextPrimary,
                )
            }
        } else {
            Spacer(Modifier.size(8.dp))
        }
        Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
            Text(
                title,
                color = GtrPremiumColors.TextPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            subtitle?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    color = GtrPremiumColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
fun PremiumSurfaceCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val base = modifier
        .fillMaxWidth()
    Card(
        modifier = if (onClick == null) base else base.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = GtrPremiumColors.SurfaceRaised),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, GtrPremiumColors.Border),
        content = { Box(Modifier.padding(14.dp)) { content() } },
    )
}

@Composable
fun PremiumPrimaryButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = GtrPremiumColors.Red,
            contentColor = Color.White,
            disabledContainerColor = GtrPremiumColors.SurfaceSoft,
            disabledContentColor = GtrPremiumColors.TextDisabled,
        ),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.height(48.dp),
    ) {
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun PremiumSecondaryButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        border = BorderStroke(1.dp, GtrPremiumColors.Border),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.height(48.dp),
    ) {
        Text(text, color = GtrPremiumColors.TextPrimary)
    }
}

enum class PremiumMessageKind { Success, Error, Info }

@Composable
fun PremiumMessageBanner(
    text: String,
    kind: PremiumMessageKind,
    modifier: Modifier = Modifier,
) {
    val color = when (kind) {
        PremiumMessageKind.Success -> GtrPremiumColors.Success
        PremiumMessageKind.Error -> GtrPremiumColors.RedBright
        PremiumMessageKind.Info -> GtrPremiumColors.Info
    }
    val icon = when (kind) {
        PremiumMessageKind.Success -> Icons.Filled.CheckCircle
        PremiumMessageKind.Error -> Icons.Filled.ErrorOutline
        PremiumMessageKind.Info -> Icons.Filled.Info
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(color.copy(alpha = .14f), RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Text(
            text,
            color = GtrPremiumColors.TextPrimary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
fun PremiumStatusChip(
    label: String,
    tone: PremiumStatusTone = PremiumStatusTone.Neutral,
    modifier: Modifier = Modifier,
) {
    val color = when (tone) {
        PremiumStatusTone.Success -> GtrPremiumColors.Success
        PremiumStatusTone.Warning -> GtrPremiumColors.Warning
        PremiumStatusTone.Danger -> GtrPremiumColors.RedBright
        PremiumStatusTone.Premium -> GtrPremiumColors.Red
        PremiumStatusTone.Neutral -> GtrPremiumColors.TextSecondary
    }
    Box(
        modifier = modifier
            .background(color.copy(alpha = .14f), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(label, color = color, style = MaterialTheme.typography.labelSmall)
    }
}

enum class PremiumStatusTone { Success, Warning, Danger, Premium, Neutral }

@Composable
fun PremiumAccountRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .background(GtrPremiumColors.SurfaceSoft, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = GtrPremiumColors.TextSecondary, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, color = GtrPremiumColors.TextPrimary, fontWeight = FontWeight.Medium)
            subtitle?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = GtrPremiumColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = GtrPremiumColors.TextSecondary,
        )
    }
}

@Composable
fun PremiumOrderTimeline(
    labels: List<String>,
    activeIndex: Int,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEachIndexed { index, label ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(14.dp)
                        .background(
                            if (index <= activeIndex) GtrPremiumColors.Red else GtrPremiumColors.SurfaceSoft,
                            CircleShape,
                        )
                )
                Text(
                    label,
                    color = if (index <= activeIndex) GtrPremiumColors.TextPrimary else GtrPremiumColors.TextSecondary,
                    modifier = Modifier.padding(start = 10.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
